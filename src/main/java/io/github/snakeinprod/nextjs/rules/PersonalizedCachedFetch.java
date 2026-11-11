package io.github.snakeinprod.nextjs.rules;

import io.github.snakeinprod.nextjs.js.JsTrees;
import io.github.snakeinprod.nextjs.model.Directive;
import io.github.snakeinprod.nextjs.model.Module;
import io.github.snakeinprod.nextjs.parse.Chain;
import io.github.snakeinprod.nextjs.parse.Node;
import io.github.snakeinprod.nextjs.parse.Nodes;
import io.github.snakeinprod.nextjs.parse.Token;
import io.github.snakeinprod.nextjs.parse.TokenKind;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User-specific values in cached data. A value is user-specific when it comes from cookies() or headers() (next/headers),
 * or from the session: auth(), getServerSession(), currentUser(), or getSession() / getUser() / getCurrentUser()
 * without arguments; and through any variable computed from one, in the same function. Three forms:
 * <ul>
 *   <li>a fetch with {@code cache: 'force-cache'} or {@code next: { revalidate }} (not 0) in an inline options object
 *   whose URL, headers or body use such a value;</li>
 *   <li>an unstable_cache callback that reads one, or uses one from the enclosing function that is neither an argument
 *   nor in the key parts;</li>
 *   <li>a "use cache" function (or file) that reads one.</li>
 * </ul>
 * Values passed through helpers defined elsewhere are not followed.
 */
public class PersonalizedCachedFetch implements NextRule {

  private static final Set<String> SESSION = Set.of("auth", "getServerSession", "currentUser");
  private static final Set<String> SESSION_NO_ARGS = Set.of("getSession", "getUser", "getCurrentUser");

  @Override
  public void check(RuleContext ctx) {
    Module m = ctx.module();
    Set<String> requestApis = ValueFlow.imported(m, "next/headers", Set.of("cookies", "headers"));
    Set<String> caches = ValueFlow.imported(m, "next/cache", Set.of("unstable_cache"));
    if (caches.isEmpty() && ctx.file().tokens().stream().noneMatch(t -> t.isIdent("fetch"))) {
      checkUseCache(ctx, m, requestApis);
      return;
    }
    ValueFlow.Source source =(expr, tainted) -> references(expr, tainted.keySet(), requestApis) ? List.of() : null;
    List<ValueFlow> flows = ValueFlow.flows(ctx.file().root(), f -> new ValueFlow(f, source, Map.of()));
    for (Chain c : ctx.file().chains()) {
      ValueFlow flow = ValueFlow.at(flows, c.first());
      Set<String> tainted = flow == null ? Set.of() : flow.tainted.keySet();
      Chain.Call call = c.callAfter(0);
      if (call == null) {
        continue;
      }
      if (c.firstName().equals("fetch") && cachedAndPersonal(call, tainted, requestApis)) {
        ctx.report(c.first(), "Do not cache this fetch: its URL, headers or body depend on the user's cookies, headers or session, "
          + "so one user's response is stored for everyone. Use cache: 'no-store'.");
      } else if (caches.contains(c.firstName()) && c.names().size() == 1) {
        checkUnstableCache(ctx, m, c, call, tainted, requestApis);
      }
    }
    checkUseCache(ctx, m, requestApis);
  }

  private static void checkUseCache(RuleContext ctx, Module m, Set<String> requestApis) {
    Set<JsTrees.Fn> cached = new HashSet<>(m.functionsWithDirective("use cache"));
    if (m.has(Directive.USE_CACHE)) {
      cached.addAll(m.functions().values());
    }
    Set<Token> reported = new HashSet<>();
    for (JsTrees.Fn f : cached) {
      for (Token t : sourceCalls(f.body(), requestApis)) {
        if (reported.add(t)) {
          ctx.report(t, "Do not read cookies, headers or the session in a \"use cache\" function: the result is cached for "
            + "every user. Read them outside and pass the values as arguments.");
        }
      }
    }
  }

  private static boolean cachedAndPersonal(Chain.Call call, Set<String> tainted, Set<String> apis) {
    List<List<Node>> args = call.items();
    if (args.size() < 2 || args.get(1).size() != 1 || !(args.get(1).get(0) instanceof Node.Group options) || !options.is("{")) {
      return false;
    }
    boolean cached = false;
    boolean personal = references(args.get(0), tainted, apis);
    for (List<Node> property : options.items()) {
      if (Nodes.isIdent(property.get(0), "cache") && property.size() == 3) {
        Token v = Nodes.leaf(property.get(2));
        cached |= v != null && JsTrees.unquote(v.text()).equals("force-cache");
      } else if (Nodes.isIdent(property.get(0), "next") && property.size() == 3 && property.get(2) instanceof Node.Group next) {
        for (List<Node> p : next.items()) {
          cached |= Nodes.isIdent(p.get(0), "revalidate") && p.size() > 2 && !(p.size() == 3 && Nodes.isLeaf(p.get(2), "0"));
        }
      } else {
        personal |= references(property, tainted, apis);
      }
    }
    return cached && personal;
  }

  private static void checkUnstableCache(RuleContext ctx, Module m, Chain c, Chain.Call call, Set<String> tainted, Set<String> apis) {
    List<List<Node>> args = call.items();
    if (args.isEmpty()) {
      return;
    }
    List<Node> fnArg = args.get(0);
    JsTrees.Fn fn = JsTrees.functionAt(call.args(), call.args().children().indexOf(fnArg.get(0)));
    if (fn == null && fnArg.size() == 1 && Nodes.leaf(fnArg.get(0)) != null) {
      fn = m.functions().get(Nodes.leaf(fnArg.get(0)).text());
      tainted = Set.of();
    }
    if (fn == null) {
      return;
    }
    if (!sourceCalls(fn.body(), apis).isEmpty()) {
      ctx.report(c.first(), "Do not read cookies, headers or the session inside unstable_cache: the result is cached for every user. "
        + "Read them outside and pass the values as arguments.");
      return;
    }
    Set<String> local = new HashSet<>();
    for (Token t : fn.params() != null ? Nodes.tokens(fn.params()) : List.of(fn.param())) {
      local.add(t.text());
    }
    for (ValueFlow.Decl d : ValueFlow.declarations(fn.body())) {
      d.names().forEach(n -> local.add(n.text()));
    }
    Set<String> closure = new HashSet<>(tainted);
    closure.removeAll(local);
    boolean keyed = args.size() > 1 && references(args.get(1), tainted, Set.of());
    if (!keyed && references(fn.body().children(), closure, Set.of())) {
      ctx.report(c.first(), "Pass the user-specific value used in this unstable_cache callback as an argument or a key part: "
        + "otherwise one user's result is served to everyone.");
    }
  }

  /** The nodes use a tainted name, or call cookies(), headers() or a session function, template literals included. */
  static boolean references(List<Node> nodes, Set<String> tainted, Set<String> apis) {
    List<Token> toks = Nodes.tokens(nodes);
    for (int i = 0; i < toks.size(); i++) {
      Token t = toks.get(i);
      if (t.kind() == TokenKind.STRING) {
        for (List<Node> part : ValueFlow.interpolations(t).parts()) {
          if (references(part, tainted, apis)) {
            return true;
          }
        }
      } else if (t.kind() == TokenKind.IDENT && !(i > 0 && (toks.get(i - 1).is(".") || toks.get(i - 1).is("?.")))
        && !(i + 1 < toks.size() && toks.get(i + 1).is(":") && (i == 0 || toks.get(i - 1).is("{") || toks.get(i - 1).is(",")))
        && (tainted.contains(t.text()) || isSourceCall(toks, i, apis))) {
        return true;
      } else if (t.kind() == TokenKind.IDENT && isSourceCall(toks, i, apis) && SESSION_NO_ARGS.contains(t.text())) {
        return true;
      }
    }
    return false;
  }

  private static boolean isSourceCall(List<Token> toks, int i, Set<String> apis) {
    Token t = toks.get(i);
    if (i + 1 >= toks.size() || !toks.get(i + 1).is("(")) {
      return false;
    }
    return apis.contains(t.text()) || SESSION.contains(t.text())
      || SESSION_NO_ARGS.contains(t.text()) && i + 2 < toks.size() && toks.get(i + 2).is(")");
  }

  /** The cookies(), headers() and session calls in a function body. */
  private static List<Token> sourceCalls(Node.Group body, Set<String> apis) {
    List<Token> toks = Nodes.tokens(body);
    List<Token> out = new ArrayList<>();
    for (int i = 0; i < toks.size(); i++) {
      boolean member = i > 0 && (toks.get(i - 1).is(".") || toks.get(i - 1).is("?."));
      if (toks.get(i).kind() == TokenKind.IDENT && isSourceCall(toks, i, apis)
        && (!member || SESSION_NO_ARGS.contains(toks.get(i).text()))) {
        out.add(toks.get(i));
      }
    }
    return out;
  }
}
