package io.github.snakeinprod.nextjs.rules;

import io.github.snakeinprod.nextjs.js.JsTrees;
import io.github.snakeinprod.nextjs.model.Import;
import io.github.snakeinprod.nextjs.model.Module;
import io.github.snakeinprod.nextjs.model.NextModel;
import io.github.snakeinprod.nextjs.model.Route;
import io.github.snakeinprod.nextjs.parse.Chain;
import io.github.snakeinprod.nextjs.parse.Nodes;
import io.github.snakeinprod.nextjs.parse.Token;
import io.github.snakeinprod.nextjs.parse.TokenKind;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Route handlers that change data should check the caller. An app/**&#47;route.(ts|js) exporting POST, PUT, PATCH or
 * DELETE that writes (as for Server Actions) and checks neither the session nor a token (a header, a cookie, a secret
 * from the environment, a signature). Left out: auth, webhook, cron and other public-by-design paths, handlers calling
 * a helper the model cannot see, and apps whose middleware checks the session.
 */
public class RouteHandlerWithoutAuth implements NextRule {

  private static final List<String> METHODS = List.of("POST", "PUT", "PATCH", "DELETE");
  private static final Pattern PUBLIC_PATH = Pattern.compile("auth|webhook|cron|login|signin|sign-in|signup|sign-up|register"
    + "|logout|password|reset|verif|callback|contact|newsletter|subscribe|waitlist|feedback|revalidate|trpc|graphql|inngest"
    + "|uploadthing|track|analytic|view|health|/og/|captcha");
  private static final Pattern TOKEN_TEXT = Pattern.compile("(?i).*(authorization|bearer|api-key|apikey|secret|token|signature).*");
  private static final Pattern TOKEN_CALL = Pattern.compile("(?i).*(verify|signature|constructEvent|timingSafeEqual|webhook).*");
  private static final Pattern AUTH_SPEC = Pattern.compile("(?i).*(auth|clerk|kinde|lucia|iron-session|jose|supabase|session|jwt).*");

  private ServerActionWithoutAuth.Flow flow;

  @Override
  public void check(RuleContext ctx) {
    Module m = ctx.module();
    Route r = m.route();
    if (r == null || !r.router().equals("app") || !r.kind().equals("route")
      || PUBLIC_PATH.matcher(m.path().toLowerCase(Locale.ROOT)).find()) {
      return;
    }
    flow = ServerActionWithoutAuth.Flow.of(flow, ctx.model());
    if (middlewareChecks(ctx.model(), m)) {
      return;
    }
    for (String method : METHODS) {
      Token t = m.exports().get(method);
      JsTrees.Fn fn = t == null ? null : m.functions().get(t.text());
      if (fn == null) {
        continue;
      }
      List<Chain> chains = ServerActionWithoutAuth.Flow.chains(fn);
      if (flow.writes(m, chains) && !checksToken(fn, chains) && flow.session(m, fn) == ServerActionWithoutAuth.Flow.Check.NO) {
        ctx.report(t, "Check the session or a token in this " + method + " handler before it changes data: anyone can call it.");
      }
    }
  }

  /** A header, a cookie, a secret or token, or a signature check. */
  private static boolean checksToken(JsTrees.Fn fn, List<Chain> chains) {
    for (Chain c : chains) {
      List<String> names = c.names();
      if (names.contains("headers") || names.contains("cookies") || ServerActionWithoutAuth.Flow.calls(c, n -> TOKEN_CALL.matcher(n).matches())
        || names.size() >= 3 && names.get(0).equals("process") && names.get(1).equals("env")
        && names.get(2).matches("(?i).*(secret|token|key|password).*")) {
        return true;
      }
    }
    for (Token t : Nodes.tokens(fn.body())) {
      if (t.kind() == TokenKind.STRING && TOKEN_TEXT.matcher(t.text()).matches()) {
        return true;
      }
    }
    return false;
  }

  /** A middleware of the same app that imports an auth library or checks the session: it guards the handlers. */
  private boolean middlewareChecks(NextModel model, Module handler) {
    String app = "";
    for (NextModel.NextApp a : model.apps()) {
      if (handler.path().startsWith(a.dir()) && a.dir().length() >= app.length()) {
        app = a.dir();
      }
    }
    for (Module mw : model.modules()) {
      if (mw.route() == null || !mw.route().kind().equals("middleware") || !mw.path().startsWith(app)) {
        continue;
      }
      for (Import i : mw.imports()) {
        if (AUTH_SPEC.matcher(i.spec()).matches()) {
          return true;
        }
      }
      for (Chain c : mw.file().chains()) {
        if (ServerActionWithoutAuth.Flow.calls(c, ServerActionWithoutAuth.Flow::isSessionCall)) {
          return true;
        }
      }
    }
    return false;
  }
}
