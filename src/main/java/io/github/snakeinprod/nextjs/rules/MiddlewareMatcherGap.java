package io.github.snakeinprod.nextjs.rules;

import io.github.snakeinprod.nextjs.js.JsTrees;
import io.github.snakeinprod.nextjs.model.Import;
import io.github.snakeinprod.nextjs.model.Module;
import io.github.snakeinprod.nextjs.model.NextModel;
import io.github.snakeinprod.nextjs.model.Route;
import io.github.snakeinprod.nextjs.parse.Chain;
import io.github.snakeinprod.nextjs.parse.Token;
import io.github.snakeinprod.nextjs.parse.TokenKind;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * A middleware (or Next 16 proxy) that checks the session and limits itself with a literal {@code config.matcher} guards
 * only what the matcher matches. Pages and route handlers under protected-looking segments (dashboard, admin,
 * settings, account) that the matcher leaves out and that check nothing themselves (no session call, cookie, header
 * or token read, no auth hook or guard component, no redirect, neither in the module nor a module it imports, and no
 * enclosing layout or Pages Router _app that checks the session and redirects or guards) are open. Reported once per middleware, on the matcher, when the matcher
 * covers at least one protected-looking route (it is meant as a guard). Matchers follow Next.js semantics
 * (path-to-regexp: {@code :p}, {@code :p*}, {@code :p+}, {@code :p?}, {@code (regex)}); a matcher that is not literal
 * or not understood counts as matching everything.
 */
public class MiddlewareMatcherGap implements NextRule {

  static final int LISTED = 5;
  private static final Pattern PROTECTED = Pattern.compile("dashboard|admin|settings|account|accounts");
  private static final Pattern PUBLIC = Pattern.compile("auth|login|log-in|signin|sign-in|signup|sign-up|register|logout"
    + "|signout|sign-out|webhooks?|public|_next|static|callback|forgot|reset|verify|invite|share|onboarding");
  private static final Pattern CLIENT_AUTH = Pattern.compile("use[A-Z]?\\w*(Session|User|Auth|Viewer|Account|Me|Clerk)\\w*");
  private static final Pattern GUARD = Pattern.compile("SignedIn|SignedOut|Protect|RedirectToSignIn|withPageAuthRequired"
    + "|\\w*(Auth|Session|Login|Protected|Private)\\w*(Guard|Gate|Check|Wrapper|Required|Boundary|Route|Layout)\\w*"
    + "|(with|require|ensure)\\w*(Auth|Session|User|Login)\\w*");
  private static final Pattern ACTION = Pattern.compile("redirect|permanentRedirect|notFound|unauthorized|forbidden"
    + "|(require|ensure|assert|protect|must)\\w*");
  private static final Pattern WORD = Pattern.compile("[A-Za-z0-9_-]+");

  private ServerActionWithoutAuth.Flow flow;
  private final Map<Module, Boolean> direct = new HashMap<>();
  private final Map<Module, Boolean> evidence = new HashMap<>();

  @Override
  public void check(RuleContext ctx) {
    Module mw = ctx.module();
    if (mw.route() == null || !mw.route().kind().equals("middleware") || !RouteHandlerWithoutAuth.checksCaller(mw)) {
      return;
    }
    Token at = matcherKey(mw);
    List<String> sources = at == null ? null : sources(mw, at);
    List<Pattern> patterns = sources == null ? null : compile(sources);
    if (patterns == null) {
      return;
    }
    flow = ServerActionWithoutAuth.Flow.of(flow, ctx.model());
    NextModel model = ctx.model();
    NextModel.NextApp app = model.appOf(mw.path());
    List<String> words = new ArrayList<>(new LinkedHashSet<>(sources.stream().flatMap(s -> WORD.matcher(s).results().map(java.util.regex.MatchResult::group)).toList()));
    boolean guards = sources.stream().anyMatch(MiddlewareMatcherGap::looksProtected);
    Set<String> open = new TreeSet<>();
    for (Module m : model.modules()) {
      Route r = m.route();
      if (r == null || !r.isRoute() || r.url() == null || model.appOf(m.path()) != app || !looksProtected(r.url())) {
        continue;
      }
      if (covered(patterns, r.url(), words)) {
        guards = true;
      } else if (!protects(model, m)) {
        open.add(r.url());
      }
    }
    if (guards && !open.isEmpty()) {
      List<String> list = new ArrayList<>(open);
      String shown = String.join(", ", list.subList(0, Math.min(LISTED, list.size())))
        + (list.size() > LISTED ? " and " + (list.size() - LISTED) + " more" : "");
      ctx.report(at, "This middleware checks the session but its matcher leaves out " + list.size()
        + (list.size() == 1 ? " route that checks" : " routes that check") + " nothing themselves (" + shown
        + "): add them to the matcher or check the session in them.");
    }
  }

  /** A static segment of the URL is dashboard, admin, settings or account, and none is a public one (login, auth...). */
  static boolean looksProtected(String url) {
    boolean prot = false;
    for (String s : url.toLowerCase(Locale.ROOT).split("/")) {
      if (s.startsWith("[")) {
        continue;
      }
      if (PUBLIC.matcher(s).find()) {
        return false;
      }
      prot |= PROTECTED.matcher(s).matches();
    }
    return prot;
  }

  // ------------------------------------------------------------ the route's own checks

  /** The route, an enclosing layout (App Router) or _app (Pages Router), or a module one of them imports checks the caller. */
  private boolean protects(NextModel model, Module m) {
    if (has(model, m)) {
      return true;
    }
    String path = m.path();
    if (m.route().router().equals("app")) {
      if (m.route().kind().equals("route")) {
        return false;
      }
      for (int slash = path.lastIndexOf('/'); slash > 0; slash = path.lastIndexOf('/', slash - 1)) {
        String dir = path.substring(0, slash + 1);
        for (Module x : model.modules()) {
          if (x.route() != null && (x.route().kind().equals("layout") || x.route().kind().equals("template"))
            && x.path().startsWith(dir) && x.path().indexOf('/', dir.length()) < 0 && gates(x)) {
            return true;
          }
        }
        if (dir.endsWith("/app/") || dir.equals("app/")) {
          break;
        }
      }
      return false;
    }
    for (Module x : model.modules()) {
      if (x.route() != null && x.route().kind().equals("app") && model.appOf(x.path()) == model.appOf(path) && gates(x)) {
        return true;
      }
    }
    return false;
  }

  /**
   * A layout or _app that checks the caller and acts on it: a redirect, notFound(), a guard component or a
   * require / ensure / assert / protect helper. One that only reads the session for a provider protects nothing.
   */
  private boolean gates(Module m) {
    if (!direct(m)) {
      return false;
    }
    for (Chain c : m.file().chains()) {
      if (ServerActionWithoutAuth.Flow.calls(c, n -> ACTION.matcher(n).matches())) {
        return true;
      }
    }
    return m.file().tokens().stream().anyMatch(t -> t.kind() == TokenKind.IDENT && GUARD.matcher(t.text()).matches());
  }

  /** The module or a project module it imports checks the caller. */
  private boolean has(NextModel model, Module m) {
    Boolean cached = evidence.get(m);
    if (cached != null) {
      return cached;
    }
    boolean out = direct(m);
    if (!out) {
      for (Import i : m.imports()) {
        Module t = i.typeOnly() ? null : model.target(i);
        if (t != null && direct(t)) {
          out = true;
          break;
        }
      }
    }
    evidence.put(m, out);
    return out;
  }

  /**
   * A session call, a cookies() / headers() read, an auth hook, a redirect, a guard component or wrapper, a token or
   * signature check, or a function that checks the session through project helpers (or calls one the model cannot see).
   */
  private boolean direct(Module m) {
    return direct.computeIfAbsent(m, x -> {
      for (Chain c : x.file().chains()) {
        if (ServerActionWithoutAuth.Flow.calls(c, n -> ServerActionWithoutAuth.Flow.isSessionCall(n) || n.equals("cookies")
          || n.equals("headers") || n.equals("redirect") || n.equals("permanentRedirect") || CLIENT_AUTH.matcher(n).matches())) {
          return true;
        }
      }
      for (Token t : x.file().tokens()) {
        if (t.kind() == TokenKind.IDENT && GUARD.matcher(t.text()).matches()) {
          return true;
        }
      }
      for (JsTrees.Fn fn : x.functions().values()) {
        if (RouteHandlerWithoutAuth.checksToken(fn, ServerActionWithoutAuth.Flow.chains(fn))
          || flow.session(x, fn) != ServerActionWithoutAuth.Flow.Check.NO) {
          return true;
        }
      }
      return false;
    });
  }

  // ------------------------------------------------------------ the matcher

  /** The {@code matcher} key of the config, or null. */
  private static Token matcherKey(Module mw) {
    List<Token> t = mw.file().tokens();
    for (int i = 0; i + 2 < t.size(); i++) {
      if ((t.get(i).isIdent("matcher") || t.get(i).is("'matcher'") || t.get(i).is("\"matcher\"")) && t.get(i + 1).is(":")) {
        return t.get(i);
      }
    }
    return null;
  }

  /**
   * The literal sources of the matcher: a string, or an array of strings and {@code { source, has, missing }} objects.
   * Null when any part is not a literal (a variable, a spread, a template with substitutions, a concatenation).
   */
  static List<String> sources(Module mw, Token key) {
    List<Token> t = mw.file().tokens();
    int i = t.indexOf(key) + 2;
    List<String> out = new ArrayList<>();
    if (i >= t.size()) {
      return null;
    }
    if (literal(t.get(i))) {
      out.add(JsTrees.unquote(t.get(i).text()));
      return i + 1 < t.size() && (t.get(i + 1).is(",") || t.get(i + 1).is("}")) ? out : null;
    }
    if (!t.get(i).is("[")) {
      return null;
    }
    for (int j = i + 1; j < t.size(); j++) {
      Token x = t.get(j);
      if (x.is("]")) {
        return out;
      } else if (literal(x) && j + 1 < t.size() && (t.get(j + 1).is(",") || t.get(j + 1).is("]"))) {
        out.add(JsTrees.unquote(x.text()));
        j++;
        if (t.get(j).is("]")) {
          return out;
        }
      } else if (x.is("{")) {
        String source = null;
        int depth = 0;
        for (; j < t.size(); j++) {
          depth += t.get(j).is("{") || t.get(j).is("[") ? 1 : t.get(j).is("}") || t.get(j).is("]") ? -1 : 0;
          if (depth == 1 && (t.get(j).isIdent("source") || t.get(j).is("'source'") || t.get(j).is("\"source\""))
            && j + 2 < t.size() && t.get(j + 1).is(":")) {
            source = literal(t.get(j + 2)) ? JsTrees.unquote(t.get(j + 2).text()) : null;
            if (source == null) {
              return null;
            }
          }
          if (depth == 0) {
            break;
          }
        }
        if (source == null || j + 1 >= t.size()) {
          return null;
        }
        out.add(source);
        if (t.get(j + 1).is(",")) {
          j++;
        }
      } else {
        return null;
      }
    }
    return null;
  }

  private static boolean literal(Token t) {
    return t.kind() == TokenKind.STRING && !t.text().contains("${") && "'\"`".indexOf(t.text().charAt(0)) >= 0;
  }

  /** Each source as a regular expression, or null when one does not start with "/" or cannot be read. */
  static List<Pattern> compile(List<String> sources) {
    List<Pattern> out = new ArrayList<>();
    for (String s : sources) {
      String re = s.equals("/") ? "^/$" : toRegex(s);
      if (re == null) {
        return null;
      }
      try {
        out.add(Pattern.compile(re, Pattern.CASE_INSENSITIVE));
      } catch (PatternSyntaxException e) {
        return null;
      }
    }
    return out;
  }

  /**
   * path-to-regexp 6 as Next.js compiles a matcher source (delimiter "/", strict, anchored, case-insensitive):
   * {@code :name} is {@code [^/]+?}, a "/" or "." right before a parameter or group is its prefix, made optional with
   * {@code *} and {@code ?}. Null for what Next.js rejects or this does not follow ({@code {...}} groups).
   */
  static String toRegex(String source) {
    if (!source.startsWith("/")) {
      return null;
    }
    StringBuilder re = new StringBuilder("^");
    StringBuilder text = new StringBuilder();
    int i = 0;
    int n = source.length();
    while (i < n) {
      char c = source.charAt(i);
      String name = null;
      String pattern = null;
      int next = i;
      char prefix = 0;
      if ((c == '/' || c == '.') && i + 1 < n && (source.charAt(i + 1) == ':' || source.charAt(i + 1) == '(')) {
        prefix = c;
        next = i + 1;
      }
      if (next < n && source.charAt(next) == ':') {
        int j = next + 1;
        while (j < n && (Character.isLetterOrDigit(source.charAt(j)) || source.charAt(j) == '_')) {
          j++;
        }
        if (j == next + 1) {
          return null;
        }
        name = source.substring(next + 1, j);
        next = j;
      }
      if (next < n && source.charAt(next) == '(') {
        int depth = 1;
        int j = next + 1;
        if (j < n && source.charAt(j) == '?') {
          return null;
        }
        for (; j < n && depth > 0; j++) {
          char x = source.charAt(j);
          if (x == '\\') {
            j++;
          } else if (x == '(') {
            depth++;
          } else if (x == ')') {
            depth--;
          }
        }
        if (depth != 0) {
          return null;
        }
        pattern = source.substring(next + 1, j - 1);
        next = j;
      }
      if (name == null && pattern == null) {
        if (c == '\\' && i + 1 < n) {
          text.append(source.charAt(i + 1));
          i += 2;
        } else if ("*+?{}".indexOf(c) >= 0 || c == ')') {
          return null;
        } else {
          text.append(c);
          i++;
        }
        continue;
      }
      re.append(Pattern.quote(text.toString()));
      text.setLength(0);
      String p = pattern == null ? "[^/]+?" : pattern;
      char mod = next < n && "*+?".indexOf(source.charAt(next)) >= 0 ? source.charAt(next++) : 0;
      String pre = prefix == 0 ? "" : Pattern.quote(String.valueOf(prefix));
      if (!pre.isEmpty()) {
        if (mod == '*' || mod == '+') {
          re.append("(?:").append(pre).append("((?:").append(p).append(")(?:").append(pre).append("(?:").append(p).append("))*))")
            .append(mod == '*' ? "?" : "");
        } else {
          re.append("(?:").append(pre).append("(").append(p).append("))").append(mod == '?' ? "?" : "");
        }
      } else {
        re.append("((?:").append(p).append(")").append(mod == 0 ? "" : String.valueOf(mod)).append(")");
      }
      i = next;
    }
    re.append(Pattern.quote(text.toString())).append("$");
    return re.toString().replace("\\Q\\E", "");
  }

  /**
   * Some pattern matches the URL, with every dynamic segment tried as a sample value and as each word of the sources
   * ({@code [locale]} as "en" for {@code /(en|de)/:path*}); an optional catch-all also as nothing. Too many
   * combinations count as covered.
   */
  static boolean covered(List<Pattern> patterns, String url, List<String> words) {
    List<String> paths = new ArrayList<>(List.of(""));
    for (String seg : url.split("/")) {
      if (seg.isEmpty()) {
        continue;
      }
      List<String> values = new ArrayList<>();
      if (seg.startsWith("[")) {
        values.add("/x1");
        if (seg.startsWith("[[")) {
          values.add("");
        }
        if (seg.contains("...")) {
          values.add("/x1/x2");
        }
        words.forEach(w -> values.add("/" + w));
      } else {
        values.add("/" + seg);
      }
      List<String> grown = new ArrayList<>();
      for (String p : paths) {
        for (String v : values) {
          grown.add(p + v);
        }
      }
      paths = grown;
      if (paths.size() > 512) {
        return true;
      }
    }
    for (String p : paths) {
      String path = p.isEmpty() ? "/" : p;
      if (patterns.stream().anyMatch(re -> re.matcher(path).matches())) {
        return true;
      }
    }
    return false;
  }
}
