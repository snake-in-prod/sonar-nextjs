package io.github.snakeinprod.nextjs.rules;

import io.github.snakeinprod.nextjs.model.Module;
import io.github.snakeinprod.nextjs.parse.Chain;
import io.github.snakeinprod.nextjs.parse.Node;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * redirect(), permanentRedirect() (next/navigation) and NextResponse.redirect() (next/server) whose target is a value
 * from searchParams, params or form data as it is (see {@link ValueFlow.Direct}), with no check of that value elsewhere
 * in the function: compared, tested with a method ({@code startsWith}, {@code match}), or passed to a function whose
 * name says it checks ({@code isSafeUrl}, {@code allowed.includes}). A value built by an unknown helper is not
 * followed.
 */
public class OpenRedirect implements NextRule {

  static final Pattern CHECK = Pattern.compile("(?i).*(safe|valid|allow|check|verify|sanitiz|trust|relative|internal|external"
    + "|local|same|origin|whitelist|permit|includes|has|test|match|canparse|startswith|switch).*");

  @Override
  public void check(RuleContext ctx) {
    Module m = ctx.module();
    Set<String> redirects = ValueFlow.imported(m, "next/navigation", Set.of("redirect", "permanentRedirect"));
    Set<String> responses = ValueFlow.imported(m, "next/server", Set.of("NextResponse"));
    if (redirects.isEmpty() && responses.isEmpty()) {
      return;
    }
    List<Chain.Call> sinks = new ArrayList<>();
    List<Node.Group> ranges = new ArrayList<>();
    for (Chain c : ctx.file().chains()) {
      List<String> names = c.names();
      Chain.Call call = names.size() == 1 && redirects.contains(names.get(0)) ? c.callAfter(0)
        : names.size() == 2 && responses.contains(names.get(0)) && names.get(1).equals("redirect") ? c.callAfter(1) : null;
      if (call != null && !call.items().isEmpty()) {
        sinks.add(call);
        ranges.add(call.args());
      }
    }
    ValueFlow.Direct direct = new ValueFlow.Direct(false, m.route() != null);
    List<ValueFlow> flows = ValueFlow.flows(ctx.file().root(), f -> new ValueFlow(f, direct, ValueFlow.Direct.formDataParams(f)));
    for (Chain.Call call : sinks) {
      ValueFlow flow = ValueFlow.at(flows, call.args().first());
      List<Node> target = call.items().get(0);
      List<List<String>> keys = flow == null ? null : direct.keys(target, flow.tainted, flow.locals);
      if (keys != null && !flow.checked(keys, ranges, true, true, s -> CHECK.matcher(s).matches())) {
        ctx.report(target.get(0).first(), target.get(target.size() - 1).last(),
          "Check this redirect target from the request against an allow-list or the same origin: it can send users to any site.");
      }
    }
  }
}
