package io.github.snakeinprod.nextjs.rules;

import io.github.snakeinprod.nextjs.js.JsTrees;
import io.github.snakeinprod.nextjs.model.Module;
import io.github.snakeinprod.nextjs.model.ServerAction;
import io.github.snakeinprod.nextjs.parse.Chain;
import io.github.snakeinprod.nextjs.parse.Node;
import io.github.snakeinprod.nextjs.parse.Nodes;
import io.github.snakeinprod.nextjs.parse.Token;
import io.github.snakeinprod.nextjs.parse.TokenKind;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * redirect(), permanentRedirect() (next/navigation), NextResponse.redirect() (next/server), Response.redirect(),
 * res.redirect() and res.writeHead(30x, { Location }) of a Pages Router API route,
 * push() and replace() of a router from useRouter() (next/navigation, next/router) or of the next/router singleton (an
 * absolute URL leaves the app),
 * {@code window.location.href = ...} and {@code window.location.assign()} / {@code replace()} whose target is a value
 * from searchParams, params, form data, the request body of an endpoint ({@code await request.json()}, {@code req.body})
 * or a Server Action argument ({@code values.callbackUrl}) as it is (see {@link ValueFlow.Direct}), also held in React state
 * ({@code setNext(searchParams.get('next'))}), with no check of that value before the redirect in the function:
 * compared, tested with a method ({@code startsWith}, {@code match}), or passed to a function whose name says it checks
 * ({@code isSafeUrl}, {@code allowed.includes}), or a comparison with a server secret before it. A value built by an
 * unknown helper is not followed.
 */
public class OpenRedirect implements NextRule {

  static final Pattern CHECK = Pattern.compile("(?i).*(safe|valid|allow|check|verify|sanitiz|trust|relative|internal|external"
    + "|local|same|origin|whitelist|permit|includes|has|test|match|canparse|startswith|switch).*");

  private static final Set<String> EQUALITY = Set.of("===", "!==", "==", "!=");
  private static final Set<String> NAVIGATE = Set.of("push", "replace");
  private static final Set<String> LOCATION_CALLS = Set.of("assign", "replace");
  /** What an action library handler gets from the client: next-safe-action, zsa. */
  private static final Set<String> BUILDER_INPUTS = Set.of("parsedInput", "clientInput", "input");

  @Override
  public void check(RuleContext ctx) {
    Module m = ctx.module();
    Set<String> redirects = ValueFlow.imported(m, "next/navigation", Set.of("redirect", "permanentRedirect"));
    Set<String> responses = ValueFlow.imported(m, "next/server", Set.of("NextResponse"));
    responses.add("Response");
    Set<String> useRouter = ValueFlow.imported(m, "next/navigation", Set.of("useRouter"));
    useRouter.addAll(ValueFlow.imported(m, "next/router", Set.of("useRouter")));
    Set<String> routers = routers(ctx.file().tokens(), useRouter);
    // import Router from 'next/router': the singleton router
    routers.addAll(ValueFlow.imported(m, "next/router", Set.of("default")));
    // res.redirect() / res.writeHead() of a Pages Router API route, or of a module typed with NextApiResponse
    boolean apiResponse = m.route() != null && m.route().kind().equals("api")
      || m.imports().stream().anyMatch(i -> i.spec().equals("next") && i.names().containsValue("NextApiResponse"));
    List<List<Node>> targets = new ArrayList<>();
    List<Node.Group> ranges = new ArrayList<>();
    for (Chain c : ctx.file().chains()) {
      List<String> names = c.names();
      int n = names.size();
      Chain.Call call = null;
      if (n == 1 && redirects.contains(names.get(0))) {
        call = c.callAfter(0);
      } else if (n == 2 && responses.contains(names.get(0)) && names.get(1).equals("redirect")) {
        call = c.callAfter(1);
      } else if (n == 2 && NAVIGATE.contains(names.get(1))
        && (routers.contains(names.get(0)) && c.callAfter(0) == null || useRouter.contains(names.get(0)) && c.callAfter(0) != null)) {
        // router.push(next), useRouter().replace(next): an absolute URL leaves the app
        call = c.callAfter(1);
      } else if (apiResponse && n == 2 && c.callAfter(0) == null && !responses.contains(names.get(0)) && c.callAfter(1) != null
        && (names.get(1).equals("redirect") || names.get(1).equals("writeHead"))) {
        // res.redirect(next), res.redirect(302, next), res.writeHead(302, { Location: next })
        List<Node> target = apiTarget(names.get(1), c.callAfter(1).items());
        if (target != null) {
          targets.add(target);
          ranges.add(c.callAfter(1).args());
        }
      } else if (n == 3 && names.get(0).equals("window") && names.get(1).equals("location") && LOCATION_CALLS.contains(names.get(2))) {
        call = c.callAfter(2);
      } else if (names.get(0).equals("window") && !c.endsWithCall() && Nodes.isLeaf(c.after(), "=")
        && (n == 2 && names.get(1).equals("location") || n == 3 && names.get(1).equals("location") && names.get(2).equals("href"))) {
        // window.location.href = next
        List<Node> kids = c.container().children();
        int from = kids.indexOf(c.after()) + 1;
        List<Node> value = kids.subList(from, ValueFlow.expressionEnd(kids, from));
        if (!value.isEmpty()) {
          targets.add(value);
          ranges.add(new Node.Group(null, null, value));
        }
      }
      if (call != null && !call.items().isEmpty()) {
        targets.add(call.items().get(0));
        ranges.add(call.args());
      }
    }
    if (targets.isEmpty()) {
      return;
    }
    boolean body = apiResponse || m.route() != null && m.route().isEndpoint();
    List<ValueFlow> flows = ValueFlow.flows(ctx.file().root(), f -> {
      Map<String, List<List<String>>> seeds = new HashMap<>(ValueFlow.Direct.formDataParams(f));
      Set<String> objects = new HashSet<>();
      actionInputs(m, f, seeds, objects);
      return new ValueFlow(f, new ValueFlow.Direct(false, m.route() != null, body, objects), seeds, true);
    });
    for (List<Node> target : targets) {
      ValueFlow flow = ValueFlow.at(flows, target.get(0).first());
      List<List<String>> keys = flow == null ? null : flow.source.keys(target, flow.tainted, flow.locals);
      if (keys != null && !secretGate(flow.tokens, target.get(0).first()) && !flow.checked(keys, ranges, true, true, s -> CHECK.matcher(s).matches(), target.get(0).first())) {
        ctx.report(target.get(0).first(), target.get(target.size() - 1).last(),
          "Check this redirect target from the request against an allow-list or the same origin: it can send users to any site.");
      }
    }
  }

  /**
   * The inputs of the Server Actions in this scope (a wrapped action: the parameters the caller fills): a destructured
   * field ({@code { callbackUrl }}) is one, and so is a name destructured from a parameter in the body
   * ({@code const { next } = data}); a plain parameter goes to {@code objects}, its fields are inputs but the parameter
   * itself is not ({@code redirect(path)} of an action called with fixed paths). An action library handler's
   * {@code parsedInput}, {@code clientInput} or {@code input} is such a parameter.
   */
  private static void actionInputs(Module m, JsTrees.Fn scope, Map<String, List<List<String>>> seeds, Set<String> objects) {
    Token from = ValueFlow.first(scope);
    Token to = scope.body().last();
    for (ServerAction a : m.serverActions()) {
      JsTrees.Fn fn = a.fn();
      if (!ValueFlow.within(ValueFlow.first(fn), from, to)) {
        continue;
      }
      List<List<Node>> params = fn.param() != null ? List.of(List.of(new Node.Leaf(fn.param()))) : fn.params().items();
      if (a.builder() != null) {
        // ({ parsedInput }), ({ parsedInput: data }), ({ parsedInput: { next } }): the client's input, a schema keeps any string
        if (!params.isEmpty() && !params.get(0).isEmpty() && params.get(0).get(0) instanceof Node.Group g && g.is("{")) {
          for (List<Node> item : g.items()) {
            Token key = item.isEmpty() ? null : Nodes.leaf(item.get(0));
            if (key != null && BUILDER_INPUTS.contains(key.text())) {
              input(item.size() > 2 && Nodes.isLeaf(item.get(1), ":") ? item.get(2) : item.get(0), seeds, objects);
            }
          }
        }
        continue;
      }
      for (int i = 0; i < params.size(); i++) {
        List<Node> item = params.get(i);
        if (!item.isEmpty() && (a.wrapper() == null || a.wrapper().clientArg(i))) {
          input(Nodes.isLeaf(item.get(0), "...") && item.size() > 1 ? item.get(1) : item.get(0), seeds, objects);
        }
      }
    }
    if (objects.isEmpty()) {
      return;
    }
    for (ValueFlow.Decl d : ValueFlow.declarations(ValueFlow.scopeOf(scope))) {
      List<Node> init = ValueFlow.Direct.trim(d.init());
      if (!d.plain() && init.size() == 1 && Nodes.leaf(init.get(0)) != null && objects.contains(Nodes.leaf(init.get(0)).text())) {
        d.names().forEach(t -> seeds.putIfAbsent(t.text(), List.of(List.of(t.text()))));
      }
    }
  }

  /** A parameter: its destructured names are inputs, a plain name is an object whose fields are. */
  private static void input(Node p, Map<String, List<List<String>>> seeds, Set<String> objects) {
    if (p instanceof Node.Group g && g.is("{")) {
      ValueFlow.names(g).forEach(t -> seeds.putIfAbsent(t.text(), List.of(List.of(t.text()))));
    } else if (Nodes.leaf(p) != null && Nodes.leaf(p).kind() == TokenKind.IDENT) {
      objects.add(Nodes.leaf(p).text());
    }
  }

  /**
   * A comparison with a server secret before the redirect ({@code req.query.secret !== process.env.PREVIEW_SECRET}):
   * only a caller who knows the secret gets there, an attacker cannot build the link.
   */
  private static boolean secretGate(List<Token> toks, Token until) {
    for (int i = 0; i + 4 < toks.size() && ValueFlow.pos(toks.get(i)) < ValueFlow.pos(until); i++) {
      if (toks.get(i).isIdent("process") && toks.get(i + 1).is(".") && toks.get(i + 2).isIdent("env") && toks.get(i + 3).is(".")
        && (i > 0 && EQUALITY.contains(toks.get(i - 1).text()) || i + 5 < toks.size() && EQUALITY.contains(toks.get(i + 5).text()))) {
        return true;
      }
    }
    return false;
  }

  /** The target of {@code res.redirect([status,] url)} or the Location of {@code res.writeHead(status, { Location: url })}. */
  private static List<Node> apiTarget(String method, List<List<Node>> items) {
    if (method.equals("redirect")) {
      return items.size() == 1 ? items.get(0) : items.size() == 2 ? items.get(1) : null;
    }
    if (items.size() < 2 || items.get(1).size() != 1 || !(items.get(1).get(0) instanceof Node.Group g) || !g.is("{")) {
      return null;
    }
    for (List<Node> e : g.items()) {
      Token key = e.isEmpty() ? null : Nodes.leaf(e.get(0));
      if (key != null && e.size() > 2 && Nodes.isLeaf(e.get(1), ":") && JsTrees.unquote(key.text()).equalsIgnoreCase("location")) {
        return e.subList(2, e.size());
      }
    }
    return null;
  }

  /** Names only ever assigned the result of useRouter(): {@code const router = useRouter()}. */
  private static Set<String> routers(List<Token> toks, Set<String> useRouter) {
    Set<String> out = new HashSet<>();
    Set<String> other = new HashSet<>();
    for (int i = 0; i + 2 < toks.size(); i++) {
      if (toks.get(i).kind() == TokenKind.IDENT && toks.get(i + 1).is("=")) {
        boolean router = useRouter.contains(toks.get(i + 2).text()) && i + 3 < toks.size() && toks.get(i + 3).is("(");
        (router ? out : other).add(toks.get(i).text());
      }
    }
    out.removeAll(other);
    return out;
  }
}
