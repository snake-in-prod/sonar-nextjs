package io.github.snakeinprod.nextjs.rules;

import io.github.snakeinprod.nextjs.model.Module;
import io.github.snakeinprod.nextjs.parse.Chain;
import io.github.snakeinprod.nextjs.parse.Node;
import io.github.snakeinprod.nextjs.parse.Nodes;
import io.github.snakeinprod.nextjs.parse.Token;
import io.github.snakeinprod.nextjs.parse.TokenKind;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * redirect(), permanentRedirect() (next/navigation), NextResponse.redirect() (next/server), Response.redirect(),
 * push() and replace() of a router from useRouter() (next/navigation, next/router: an absolute URL leaves the app),
 * {@code window.location.href = ...} and {@code window.location.assign()} / {@code replace()} whose target is a value
 * from searchParams, params or form data as it is (see {@link ValueFlow.Direct}), with no check of that value elsewhere
 * in the function: compared, tested with a method ({@code startsWith}, {@code match}), or passed to a function whose
 * name says it checks ({@code isSafeUrl}, {@code allowed.includes}). A value built by an unknown helper is not
 * followed.
 */
public class OpenRedirect implements NextRule {

  static final Pattern CHECK = Pattern.compile("(?i).*(safe|valid|allow|check|verify|sanitiz|trust|relative|internal|external"
    + "|local|same|origin|whitelist|permit|includes|has|test|match|canparse|startswith|switch).*");

  private static final Set<String> NAVIGATE = Set.of("push", "replace");
  private static final Set<String> LOCATION_CALLS = Set.of("assign", "replace");

  @Override
  public void check(RuleContext ctx) {
    Module m = ctx.module();
    Set<String> redirects = ValueFlow.imported(m, "next/navigation", Set.of("redirect", "permanentRedirect"));
    Set<String> responses = ValueFlow.imported(m, "next/server", Set.of("NextResponse"));
    responses.add("Response");
    Set<String> useRouter = ValueFlow.imported(m, "next/navigation", Set.of("useRouter"));
    useRouter.addAll(ValueFlow.imported(m, "next/router", Set.of("useRouter")));
    Set<String> routers = routers(ctx.file().tokens(), useRouter);
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
    ValueFlow.Direct direct = new ValueFlow.Direct(false, m.route() != null);
    List<ValueFlow> flows = ValueFlow.flows(ctx.file().root(), f -> new ValueFlow(f, direct, ValueFlow.Direct.formDataParams(f)));
    for (List<Node> target : targets) {
      ValueFlow flow = ValueFlow.at(flows, target.get(0).first());
      List<List<String>> keys = flow == null ? null : direct.keys(target, flow.tainted, flow.locals);
      if (keys != null && !flow.checked(keys, ranges, true, true, s -> CHECK.matcher(s).matches())) {
        ctx.report(target.get(0).first(), target.get(target.size() - 1).last(),
          "Check this redirect target from the request against an allow-list or the same origin: it can send users to any site.");
      }
    }
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
