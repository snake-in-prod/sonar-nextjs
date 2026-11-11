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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Route handlers that change data should check the caller. An app/**&#47;route.(ts|js) exporting POST, PUT, PATCH or
 * DELETE that writes (as for Server Actions) and checks neither the session nor a token (a header, a cookie, a secret
 * from the environment, a signature). Left out: auth, webhook, cron and other public-by-design paths, handlers calling
 * a helper the model cannot see, apps without sign-in (no auth library, no sign-in route), and handlers whose app has
 * a middleware checking the session with a matcher covering them.
 */
public class RouteHandlerWithoutAuth implements NextRule {

  private static final List<String> METHODS = List.of("POST", "PUT", "PATCH", "DELETE");
  private static final Pattern PUBLIC_PATH = Pattern.compile("auth|webhook|cron|login|signin|sign-in|signup|sign-up|register"
    + "|logout|password|reset|verif|callback|contact|newsletter|subscribe|waitlist|feedback|revalidate|trpc|graphql|inngest"
    + "|uploadthing|track|analytic|view|health|/og/|captcha|otp/|success");
  private static final Pattern TOKEN_TEXT = Pattern.compile("(?i).*(authorization|bearer|api-key|apikey|secret|token|signature).*");
  private static final Pattern TOKEN_CALL = Pattern.compile("(?i).*(verify|signature|constructEvent|timingSafeEqual|webhook"
    + "|authenticat|authoriz|session).*");
  private static final Pattern AUTH_SPEC = Pattern.compile("(?i).*(auth|clerk|kinde|lucia|iron-session|jose|supabase|session|jwt).*");

  private static final Pattern SIGN_IN_SPEC = Pattern.compile("(?i).*(auth|clerk|kinde|lucia|iron-session|jose|supabase|session|jwt"
    + "|jsonwebtoken|bcrypt|argon2|passport|firebase|appwrite|hanko|stytch|descope|workos).*");

  private static final Pattern SIGN_IN_URL = Pattern.compile("/(log-?in|sign-?in|sign-?up|register|auth)[^/]*(/|$)");

  private ServerActionWithoutAuth.Flow flow;
  private final Map<String, Boolean> signIn = new HashMap<>();
  /** The middleware modules, in model order, and whether each checks the caller: looked up once, not per handler. */
  private List<Module> middlewares;
  private final Map<Module, Boolean> guards = new HashMap<>();

  @Override
  public void check(RuleContext ctx) {
    Module m = ctx.module();
    Route r = m.route();
    if (r == null || !r.router().equals("app") || !r.kind().equals("route")
      || PUBLIC_PATH.matcher(m.path().toLowerCase(Locale.ROOT)).find()) {
      return;
    }
    flow = ServerActionWithoutAuth.Flow.of(flow, ctx.model());
    if (!appHasSignIn(m) || middlewareChecks(ctx.model(), m, r)) {
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
  static boolean checksToken(JsTrees.Fn fn, List<Chain> chains) {
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

  /**
   * Some module of the app imports an auth, session, token or password hashing library, or is a sign-in or sign-up
   * route: an app without sign-in is public by design. (A session-like call is not enough: getAccountAddress is not
   * a session check.)
   */
  private boolean appHasSignIn(Module m) {
    NextModel.NextApp app = flow.model().appOf(m.path());
    String dir = app == null ? "" : app.dir();
    return signIn.computeIfAbsent(dir, d -> flow.model().modules().stream().anyMatch(x -> x.path().startsWith(d)
      && (x.route() != null && x.route().url() != null && SIGN_IN_URL.matcher(x.route().url().toLowerCase(Locale.ROOT)).find()
      || x.imports().stream().anyMatch(i -> !i.typeOnly() && i.resolved() == null && SIGN_IN_SPEC.matcher(i.spec()).matches()))));
  }

  /**
   * A middleware of the same app that imports an auth library or checks the session, and whose matcher (when it is a
   * literal) covers the handler: it guards the handler.
   */
  private boolean middlewareChecks(NextModel model, Module handler, Route route) {
    String app = "";
    for (NextModel.NextApp a : model.apps()) {
      if (handler.path().startsWith(a.dir()) && a.dir().length() >= app.length()) {
        app = a.dir();
      }
    }
    if (middlewares == null) {
      middlewares = model.modules().stream().filter(x -> x.route() != null && x.route().kind().equals("middleware")).toList();
    }
    for (Module mw : middlewares) {
      if (mw.path().startsWith(app) && matches(mw, route.url()) && guards.computeIfAbsent(mw, RouteHandlerWithoutAuth::checksCaller)) {
        return true;
      }
    }
    return false;
  }

  /** The middleware imports an auth library or checks the session. */
  static boolean checksCaller(Module mw) {
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
    return false;
  }

  /**
   * The middleware runs for this URL: no literal {@code matcher}, a pattern with a regular expression, a pattern with a
   * parameter ({@code /api/:path*}) whose static prefix holds the URL, or a plain path equal to it.
   */
  static boolean matches(Module mw, String url) {
    List<Token> t = mw.file().tokens();
    for (int i = 0; i + 2 < t.size(); i++) {
      if (!(t.get(i).isIdent("matcher") || t.get(i).is("'matcher'") || t.get(i).is("\"matcher\"")) || !t.get(i + 1).is(":")) {
        continue;
      }
      List<String> patterns = new ArrayList<>();
      if (t.get(i + 2).kind() == TokenKind.STRING) {
        patterns.add(JsTrees.unquote(t.get(i + 2).text()));
      } else if (t.get(i + 2).is("[")) {
        int depth = 0;
        for (int j = i + 2; j < t.size(); j++) {
          depth += t.get(j).is("[") || t.get(j).is("{") ? 1 : t.get(j).is("]") || t.get(j).is("}") ? -1 : 0;
          if (depth == 0) {
            break;
          }
          if (t.get(j).kind() == TokenKind.STRING && JsTrees.unquote(t.get(j).text()).startsWith("/")) {
            patterns.add(JsTrees.unquote(t.get(j).text()));
          }
        }
      }
      if (patterns.isEmpty()) {
        return true;
      }
      for (String p : patterns) {
        int close = p.indexOf(')');
        if (p.startsWith("/((?!") && close > 5) {
          // '/((?!api|_next/static).*)': every path but those
          if (Arrays.stream(p.substring(5, close).split("\\|")).noneMatch(a -> !a.isEmpty() && url.substring(1).startsWith(a.replace("\\", "")))) {
            return true;
          }
          continue;
        }
        int cut = indexOfAny(p, ":*(");
        if (cut < 0 ? url.equals(p) : p.indexOf('(') >= 0 || url.startsWith(p.substring(0, cut).replaceAll("/$", ""))) {
          return true;
        }
      }
      return false;
    }
    return true;
  }

  private static int indexOfAny(String s, String chars) {
    for (int i = 0; i < s.length(); i++) {
      if (chars.indexOf(s.charAt(i)) >= 0) {
        return i;
      }
    }
    return -1;
  }
}
