package io.github.snakeinprod.nextjs.rules;

import io.github.snakeinprod.nextjs.js.JsTrees;
import io.github.snakeinprod.nextjs.model.Import;
import io.github.snakeinprod.nextjs.model.Module;
import io.github.snakeinprod.nextjs.model.NextModel;
import io.github.snakeinprod.nextjs.parse.Node;
import io.github.snakeinprod.nextjs.parse.Nodes;
import io.github.snakeinprod.nextjs.parse.Token;
import io.github.snakeinprod.nextjs.parse.TokenKind;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * next-auth's {@code authorized} callback when it decides by pathname which routes need a session: Auth.js v5
 * ({@code callbacks.authorized({ auth, request: { nextUrl } })} in the config, the middleware a plain
 * {@code export { auth as middleware }}, {@code export default auth} or {@code NextAuth(config).auth}) and next-auth 4's
 * {@code withAuth({ callbacks: { authorized: ({ token, req }) => ... } })}. {@link #denies(String)} runs the callback
 * for a visitor without a session on a URL: a small evaluator of literal shapes (const, if / else, return, ternaries,
 * {@code && || ?? ! +}, {@code === !==}, {@code startsWith / endsWith / includes}, arrays of literals with
 * {@code some / every / includes}, template literals of names, top-level or imported literal constants); anything
 * else is unknown.
 */
final class AuthorizedCallback {

  private static final Set<String> MW_NAMES = Set.of("middleware", "proxy", "default");
  private static final java.util.regex.Pattern SUBSTITUTION = java.util.regex.Pattern.compile("\\$\\{\\s*([A-Za-z_$][\\w$]*)\\s*}");
  private static final Set<String> PROTECTING = Set.of("redirect", "rewrite", "Response", "json", "status");

  final Token at;
  final Module module;
  private final NextModel model;
  private final List<Token> params;
  private final List<Token> body;
  private final boolean expression;

  private AuthorizedCallback(Token at, Module module, NextModel model, List<Token> params, List<Token> body, boolean expression) {
    this.at = at;
    this.module = module;
    this.model = model;
    this.params = params;
    this.body = body;
    this.expression = expression;
  }

  /**
   * The callback deciding by pathname for this middleware, or null when there is none, the middleware wraps its own
   * function around Auth.js's {@code auth} (the callback's false is then ignored), a withAuth middleware function
   * redirects or answers itself, or more than one callback is found.
   */
  static AuthorizedCallback of(NextModel model, Module mw) {
    List<Token> t = mw.file().tokens();
    Token at = null;
    List<Module> scope = new ArrayList<>();
    scope.add(mw);
    for (int i = 0; i + 1 < t.size() && at == null; i++) {
      at = t.get(i).isIdent("withAuth") && t.get(i + 1).is("(") ? t.get(i) : null;
    }
    if (at == null) {
      at = plainAuthExport(t);
      if (at == null) {
        return null;
      }
      for (Module m : model.imported(mw)) {
        scope.add(m);
        scope.addAll(model.imported(m));
      }
    }
    List<Module> where = new ArrayList<>();
    List<Fn> found = new ArrayList<>();
    for (Module m : new java.util.LinkedHashSet<>(scope)) {
      Nodes.groups(m.file().root(), g -> {
        List<Node> k = g.children();
        for (int i = 0; i + 2 < k.size(); i++) {
          if (Nodes.isIdent(k.get(i), "callbacks") && Nodes.isLeaf(k.get(i + 1), ":") && k.get(i + 2) instanceof Node.Group cb && cb.is("{")) {
            Fn fn = callback(cb);
            if (fn != null) {
              where.add(m);
              found.add(fn);
            }
          }
        }
      });
    }
    if (found.size() != 1 || found.get(0) == Fn.OTHER) {
      return null;
    }
    Module m = where.get(0);
    Fn fn = found.get(0);
    if (fn.body.stream().noneMatch(x -> x.isIdent("pathname"))) {
      return null;
    }
    if (m == mw) {
      // withAuth: its middleware function runs for authorized requests only, unless it protects on its own
      Set<Token> inside = new java.util.HashSet<>(fn.body);
      if (t.stream().anyMatch(x -> x.kind() == TokenKind.IDENT && PROTECTING.contains(x.text()) && !inside.contains(x))) {
        return null;
      }
    }
    return new AuthorizedCallback(at, m, model, fn.params, fn.body, fn.expression);
  }

  /** The callback's parameters (with their parentheses), its body (without braces), whether that is an expression. */
  private record Fn(List<Token> params, List<Token> body, boolean expression) {
    static final Fn OTHER = new Fn(List.of(), List.of(), false);
  }

  /**
   * The function of the authorized key of a callbacks object, {@link Fn#OTHER} when the key holds something else (a
   * function by name), null when there is no such key.
   */
  private static Fn callback(Node.Group cb) {
    List<Node> k = cb.children();
    for (int i = 0; i + 1 < k.size(); i++) {
      if (!Nodes.isIdent(k.get(i), "authorized")) {
        continue;
      }
      if (k.get(i + 1) instanceof Node.Group p && p.is("(") && i + 2 < k.size() && k.get(i + 2) instanceof Node.Group b && b.is("{")) {
        return new Fn(Nodes.tokens(p), Nodes.tokens(b.children()), false);
      }
      JsTrees.Fn f = Nodes.isLeaf(k.get(i + 1), ":") && i + 2 < k.size() ? JsTrees.functionAt(cb, i + 2) : null;
      if (f == null || f.params() == null) {
        return Fn.OTHER;
      }
      return new Fn(Nodes.tokens(f.params()), Nodes.tokens(f.body().children()), f.expression());
    }
    return null;
  }

  /**
   * The token of a plain Auth.js middleware export ({@code export { auth as middleware }}, {@code export default auth},
   * {@code export default NextAuth(config).auth}, {@code export const { auth: middleware } = NextAuth(config)},
   * {@code export const middleware = auth}), or null; null as well when the file calls {@code auth(...)}.
   */
  private static Token plainAuthExport(List<Token> t) {
    Token out = null;
    for (int i = 0; i < t.size(); i++) {
      Token x = t.get(i);
      String next = i + 1 < t.size() ? t.get(i + 1).text() : ";";
      if (!x.isIdent("auth")) {
        continue;
      }
      if (next.equals("(")) {
        return null;
      }
      if ((next.equals("as") || next.equals(":")) && i + 2 < t.size() && MW_NAMES.contains(t.get(i + 2).text()) && exported(t, i)) {
        out = x;
      }
      boolean end = next.equals(";") || i + 1 < t.size() && t.get(i + 1).line() != x.line();
      if (end && i >= 2 && (t.get(i - 1).isIdent("default") && t.get(i - 2).isIdent("export")
        || t.get(i - 1).is("=") && MW_NAMES.contains(t.get(i - 2).text()) && i >= 4 && t.get(i - 4).isIdent("export"))) {
        out = x;
      }
      // export default NextAuth(authConfig).auth
      if (end && i >= 7 && t.get(i - 1).is(".") && t.get(i - 2).is(")") && t.get(i - 4).is("(") && t.get(i - 5).isIdent("NextAuth")
        && (t.get(i - 6).isIdent("default") || t.get(i - 6).is("=") && MW_NAMES.contains(t.get(i - 7).text()))) {
        out = x;
      }
    }
    return out;
  }

  /** The token at i is inside {@code export { ... }} or {@code export const { ... }}. */
  private static boolean exported(List<Token> t, int i) {
    for (int k = i - 1; k >= 0 && k >= i - 40; k--) {
      if (t.get(k).is("{")) {
        return k >= 1 && t.get(k - 1).isIdent("export") || k >= 2 && t.get(k - 1).isIdent("const") && t.get(k - 2).isIdent("export");
      }
      if (t.get(k).is("}") || t.get(k).is(";")) {
        return false;
      }
    }
    return false;
  }

  // ------------------------------------------------------------ evaluation

  /** The callback turns a visitor without a session away from this URL ({@code [x]} segments as "x1"); null when unknown. */
  Boolean denies(String url) {
    List<String> paths = new ArrayList<>(List.of(""));
    for (String seg : url.split("/")) {
      if (seg.isEmpty()) {
        continue;
      }
      List<String> grown = new ArrayList<>();
      for (String p : paths) {
        grown.add(p + "/" + (seg.startsWith("[") ? "x1" : seg));
        if (seg.startsWith("[[")) {
          grown.add(p);
        }
      }
      paths = grown;
    }
    boolean out = false;
    for (String p : paths) {
      Boolean d = deniesPath(p.isEmpty() ? "/" : p);
      if (d == null) {
        return null;
      }
      out |= d;
    }
    return out;
  }

  private Boolean deniesPath(String path) {
    try {
      Eval e = new Eval(params, path);
      e.bindParams();
      Object r = e.run(body, expression);
      if (r == V.RESPONSE) {
        return true;
      }
      if (r == V.NEXT) {
        return false;
      }
      return !truthy(r);
    } catch (Unknown u) {
      return null;
    }
  }

  /** The evaluator met something it does not follow. */
  private static final class Unknown extends RuntimeException {
    private static final Unknown INSTANCE = new Unknown();

    private Unknown() {
      super(null, null, false, false);
    }
  }

  private static Unknown unknown() {
    return Unknown.INSTANCE;
  }

  /**
   * Values besides Boolean, String and List: no session, the arguments, the request, its nextUrl, an unknown value, a
   * redirect or JSON response (the visitor is turned away), NextResponse.next(), and a few globals.
   */
  private enum V { NULL, ARGS, REQ, URL, OPAQUE, RESPONSE, NEXT, CONSOLE, GLOBAL, BOOLEAN, ENCODE }

  private record Lambda(List<Token> tokens, int param, int start, boolean expression, Map<String, Object> env) {
  }

  private static boolean truthy(Object v) {
    if (v instanceof Boolean b) {
      return b;
    }
    if (v instanceof String s) {
      return !s.isEmpty();
    }
    if (v == V.NULL) {
      return false;
    }
    if (v == V.OPAQUE) {
      throw unknown();
    }
    return true;
  }

  private final class Eval {
    private List<Token> t;
    private int p;
    private Map<String, Object> env = new HashMap<>();
    private final String path;
    private Object returned;
    private boolean done;
    private Module scope = module;

    Eval(List<Token> params, String path) {
      this.t = params;
      this.path = path;
    }

    void bindParams() {
      p = 0;
      if (t.isEmpty() || !t.get(0).is("(")) {
        throw unknown();
      }
      p = 1;
      if (peek(")")) {
        return;
      }
      bind(V.ARGS, true);
      if (!peek(")") && !peek(":")) {
        throw unknown();
      }
    }

    Object run(List<Token> tokens, boolean expr) {
      t = tokens;
      p = 0;
      if (expr) {
        Object v = expr(true);
        if (p < t.size()) {
          throw unknown();
        }
        return v;
      }
      statements(true);
      if (p < t.size()) {
        throw unknown();
      }
      return done ? returned : V.NULL;
    }

    // -------------------------------------------------------- tokens

    private boolean peek(String s) {
      return p < t.size() && t.get(p).is(s) && t.get(p).kind() != TokenKind.STRING;
    }

    private boolean peekIdent(String s) {
      return p < t.size() && t.get(p).isIdent(s);
    }

    private Token next() {
      if (p >= t.size()) {
        throw unknown();
      }
      return t.get(p++);
    }

    private void expect(String s) {
      if (!peek(s)) {
        throw unknown();
      }
      p++;
    }

    private boolean newLine() {
      return p >= t.size() || p > 0 && t.get(p).line() != t.get(p - 1).endLine();
    }

    // -------------------------------------------------------- statements

    private void statements(boolean live) {
      while (p < t.size() && !peek("}")) {
        statement(live && !done);
      }
    }

    private void statement(boolean live) {
      if (peek(";")) {
        p++;
      } else if (peek("{")) {
        p++;
        statements(live);
        expect("}");
      } else if (peekIdent("const") || peekIdent("let") || peekIdent("var")) {
        p++;
        do {
          if (peek("{")) {
            int at = p;
            skipBraces();
            expect("=");
            Object v = expr(live);
            int end = p;
            p = at;
            bind(live ? v : V.OPAQUE, live);
            p = end;
          } else {
            Token name = next();
            if (name.kind() != TokenKind.IDENT) {
              throw unknown();
            }
            if (peek(":")) {
              // const name: Type = ...
              while (!peek("=")) {
                next();
              }
            }
            expect("=");
            Object v = expr(live);
            if (live) {
              env.put(name.text(), v);
            } else {
              env.putIfAbsent(name.text(), V.OPAQUE);
            }
          }
        } while (peek(",") && p++ >= 0);
        endStatement();
      } else if (peekIdent("if")) {
        p++;
        expect("(");
        Object c = expr(live);
        expect(")");
        boolean yes = live && truthy(c);
        statement(live && yes);
        if (peekIdent("else")) {
          p++;
          statement(live && !yes && !done);
        }
      } else if (peekIdent("return")) {
        p++;
        Object v = peek(";") || peek("}") || newLine() ? V.NULL : expr(live);
        endStatement();
        if (live) {
          returned = v;
          done = true;
        }
      } else if (peekIdent("console") || p + 1 < t.size() && t.get(p + 1).is(".")
        && (env.get(t.get(p).text()) instanceof String || env.get(t.get(p).text()) == V.OPAQUE)) {
        // console.log(...), url.searchParams.set(...): nothing the decision depends on
        skipStatement();
      } else {
        throw unknown();
      }
    }

    private void endStatement() {
      if (peek(";")) {
        p++;
      } else if (!peek("}") && !newLine()) {
        throw unknown();
      }
    }

    private void skipStatement() {
      int depth = 0;
      int start = p;
      while (p < t.size()) {
        Token x = t.get(p);
        if (depth == 0 && (x.is(";") || x.is("}") || p > start && newLine() && !t.get(p - 1).is(".") && !x.is("."))) {
          break;
        }
        depth += x.kind() == TokenKind.OP && "([{".contains(x.text()) ? 1 : x.kind() == TokenKind.OP && ")]}".contains(x.text()) ? -1 : 0;
        p++;
      }
      if (peek(";")) {
        p++;
      }
    }

    private void skipBraces() {
      int depth = 0;
      do {
        Token x = next();
        depth += x.is("{") ? 1 : x.is("}") ? -1 : 0;
      } while (depth > 0);
    }

    /** An object pattern (or a name) at p bound to the members of v: {@code { auth, request: { nextUrl } }}. */
    private void bind(Object v, boolean live) {
      Token x = next();
      if (x.kind() == TokenKind.IDENT) {
        if (live) {
          env.put(x.text(), v);
        }
        return;
      }
      if (!x.is("{")) {
        throw unknown();
      }
      while (!peek("}")) {
        Token key = next();
        if (key.kind() != TokenKind.IDENT) {
          throw unknown();
        }
        Object member = live ? member(v, key.text(), false) : V.OPAQUE;
        if (peek(":")) {
          p++;
          bind(member, live);
        } else if (live) {
          env.put(key.text(), member);
        }
        if (peek(",")) {
          p++;
        } else if (!peek("}")) {
          throw unknown();
        }
      }
      p++;
    }

    // -------------------------------------------------------- expressions

    private Object expr(boolean live) {
      Object c = or(live);
      if (!peek("?")) {
        return c;
      }
      p++;
      boolean yes = live && truthy(c);
      Object a = expr(live && yes);
      expect(":");
      Object b = expr(live && !yes);
      return yes ? a : b;
    }

    private Object or(boolean live) {
      Object l = and(live);
      while (peek("||") || peek("??")) {
        boolean nullish = next().is("??");
        boolean right = live && (nullish ? l == V.NULL : !truthy(l));
        Object r = and(right);
        if (right) {
          l = r;
        }
      }
      return l;
    }

    private Object and(boolean live) {
      Object l = equality(live);
      while (peek("&&")) {
        p++;
        boolean right = live && truthy(l);
        Object r = equality(right);
        if (right) {
          l = r;
        }
      }
      return l;
    }

    private Object equality(boolean live) {
      Object l = additive(live);
      while (peek("===") || peek("!==") || peek("==") || peek("!=")) {
        boolean not = next().text().startsWith("!");
        Object r = additive(live);
        if (live) {
          if (l == V.OPAQUE || r == V.OPAQUE) {
            throw unknown();
          }
          l = Objects.equals(l, r) != not;
        }
      }
      return l;
    }

    private Object additive(boolean live) {
      Object l = unary(live);
      while (peek("+")) {
        p++;
        Object r = unary(live);
        l = l instanceof String a && r instanceof String b ? a + b : V.OPAQUE;
      }
      return l;
    }

    private Object unary(boolean live) {
      if (peek("!")) {
        p++;
        Object v = unary(live);
        return live ? !truthy(v) : V.OPAQUE;
      }
      return postfix(live);
    }

    private Object postfix(boolean live) {
      Object v = primary(live);
      while (true) {
        if (peek(".") || peek("?.")) {
          boolean optional = next().is("?.");
          Token name = next();
          if (name.kind() != TokenKind.IDENT) {
            throw unknown();
          }
          if (peek("(")) {
            List<Object> args = args(live);
            v = live ? call(v, name.text(), args, optional) : V.OPAQUE;
          } else {
            v = live ? member(v, name.text(), optional) : V.OPAQUE;
          }
        } else if (peek("(") && !newLine()) {
          List<Object> args = args(live);
          v = live ? call(v, null, args, false) : V.OPAQUE;
        } else if ((peekIdent("as") || peekIdent("satisfies")) && p + 1 < t.size() && t.get(p + 1).kind() == TokenKind.IDENT) {
          p += 2;
          if (peek("[") || peek("<") || peek(".")) {
            throw unknown();
          }
        } else {
          return v;
        }
      }
    }

    private List<Object> args(boolean live) {
      expect("(");
      List<Object> out = new ArrayList<>();
      while (!peek(")")) {
        out.add(expr(live));
        if (peek(",")) {
          p++;
        } else if (!peek(")")) {
          throw unknown();
        }
      }
      p++;
      return out;
    }

    private Object primary(boolean live) {
      Token x = next();
      if (x.kind() == TokenKind.STRING) {
        if ("'\"`".indexOf(x.text().charAt(0)) < 0) {
          throw unknown();
        }
        return live ? template(JsTrees.unquote(x.text())) : V.OPAQUE;
      }
      if (x.is("(")) {
        int open = p - 1;
        if (arrowAfterParens(open)) {
          p = open;
          return lambda(live);
        }
        Object v = expr(live);
        expect(")");
        return v;
      }
      if (x.is("{")) {
        // an object literal: { error: 'Unauthorized' }
        p--;
        skipBraces();
        return V.OPAQUE;
      }
      if (x.is("[")) {
        List<Object> items = new ArrayList<>();
        while (!peek("]")) {
          if (peek("...")) {
            throw unknown();
          }
          items.add(expr(live));
          if (peek(",")) {
            p++;
          } else if (!peek("]")) {
            throw unknown();
          }
        }
        p++;
        return items;
      }
      if (x.kind() != TokenKind.IDENT) {
        throw unknown();
      }
      if (peek("=>")) {
        p--;
        return lambda(live);
      }
      switch (x.text()) {
        case "true":
          return true;
        case "false":
          return false;
        case "null":
        case "undefined":
          return V.NULL;
        case "new":
          Token type = next();
          List<Object> args = args(live);
          if (!type.isIdent("URL") || args.isEmpty()) {
            throw unknown();
          }
          return args.get(0) instanceof String s ? s : V.OPAQUE;
        case "Boolean":
          return V.BOOLEAN;
        case "encodeURIComponent":
          return V.ENCODE;
        case "Response":
        case "NextResponse":
          return V.GLOBAL;
        case "console":
          return V.CONSOLE;
        case "function":
        case "async":
        case "await":
        case "typeof":
        case "this":
          throw unknown();
        default:
          if (env.containsKey(x.text())) {
            return env.get(x.text());
          }
          return live ? constant(scope, x.text(), 0) : V.OPAQUE;
      }
    }

    private boolean arrowAfterParens(int open) {
      int depth = 0;
      for (int k = open; k < t.size(); k++) {
        depth += t.get(k).is("(") ? 1 : t.get(k).is(")") ? -1 : 0;
        if (depth == 0) {
          return k + 1 < t.size() && t.get(k + 1).is("=>");
        }
      }
      return false;
    }

    /** {@code x => ...} or {@code (x) => ...} at p: skipped, kept as a value. */
    private Object lambda(boolean live) {
      int param;
      if (peek("(")) {
        p++;
        param = p;
        if (next().kind() != TokenKind.IDENT) {
          throw unknown();
        }
        if (peek(":")) {
          while (!peek(")")) {
            next();
          }
        }
        expect(")");
      } else {
        param = p++;
      }
      expect("=>");
      int start = p;
      boolean exprBody = !peek("{");
      if (exprBody) {
        expr(false);
      } else {
        skipBraces();
      }
      return new Lambda(t, param, start, exprBody, new HashMap<>(env));
    }

    private Object apply(Object f, Object arg) {
      if (!(f instanceof Lambda l)) {
        throw unknown();
      }
      Eval e = new Eval(l.tokens, path);
      e.t = l.tokens;
      e.env = new HashMap<>(l.env);
      e.env.put(l.tokens.get(l.param).text(), arg);
      e.p = l.start;
      if (l.expression) {
        return e.expr(true);
      }
      e.p++;
      e.statements(true);
      return e.done ? e.returned : V.NULL;
    }

    private Object member(Object v, String name, boolean optional) {
      if (v == V.NULL) {
        if (optional) {
          return V.NULL;
        }
        throw unknown();
      }
      if (v == V.ARGS) {
        return switch (name) {
          case "auth", "token" -> V.NULL;
          case "request", "req" -> V.REQ;
          default -> throw unknown();
        };
      }
      if (v == V.REQ) {
        return name.equals("nextUrl") ? V.URL : V.OPAQUE;
      }
      if (v == V.URL) {
        return name.equals("pathname") ? path : V.OPAQUE;
      }
      if (v == V.OPAQUE) {
        return V.OPAQUE;
      }
      throw unknown();
    }

    private Object call(Object recv, String name, List<Object> args, boolean optional) {
      if (recv == V.NULL && optional) {
        return V.NULL;
      }
      if (recv == V.CONSOLE) {
        return V.NULL;
      }
      if (recv == V.BOOLEAN && name == null && args.size() == 1) {
        return truthy(args.get(0));
      }
      if (recv == V.ENCODE && name == null) {
        return V.OPAQUE;
      }
      if (recv == V.GLOBAL && ("redirect".equals(name) || "json".equals(name))) {
        return V.RESPONSE;
      }
      if (recv == V.GLOBAL && "next".equals(name)) {
        return V.NEXT;
      }
      Object a = args.isEmpty() ? null : args.get(0);
      if (recv instanceof String s && name != null) {
        if (name.equals("toLowerCase") && args.isEmpty()) {
          return s.toLowerCase(Locale.ROOT);
        }
        if (args.size() == 1 && a instanceof String x) {
          switch (name) {
            case "startsWith":
              return s.startsWith(x);
            case "endsWith":
              return s.endsWith(x);
            case "includes":
              return s.contains(x);
            default:
              break;
          }
        }
      }
      if (recv instanceof List<?> list && name != null && args.size() == 1) {
        switch (name) {
          case "includes":
            if (a == V.OPAQUE) {
              throw unknown();
            }
            return list.contains(a);
          case "some":
            for (Object o : list) {
              if (truthy(apply(a, o))) {
                return true;
              }
            }
            return false;
          case "every":
            for (Object o : list) {
              if (!truthy(apply(a, o))) {
                return false;
              }
            }
            return true;
          default:
            break;
        }
      }
      throw unknown();
    }

    /** A template literal with plain names as substitutions ({@code `${path}/`}) when they hold strings, else unknown. */
    private Object template(String text) {
      java.util.regex.Matcher m = SUBSTITUTION.matcher(text);
      StringBuilder out = new StringBuilder();
      while (m.find()) {
        Object v;
        try {
          v = env.containsKey(m.group(1)) ? env.get(m.group(1)) : constant(scope, m.group(1), 0);
        } catch (Unknown u) {
          v = V.OPAQUE;
        }
        if (!(v instanceof String str)) {
          return V.OPAQUE;
        }
        m.appendReplacement(out, java.util.regex.Matcher.quoteReplacement(str));
      }
      m.appendTail(out);
      return out.indexOf("${") >= 0 ? V.OPAQUE : out.toString();
    }

    /** A top-level {@code const name = <literal>} of the module, or of the project module it imports name from. */
    private Object constant(Module m, String name, int depth) {
      List<Node> k = m.file().root().children();
      for (int i = 1; i + 2 < k.size(); i++) {
        if (Nodes.isIdent(k.get(i), name) && Nodes.isIdent(k.get(i - 1), "const") && Nodes.isLeaf(k.get(i + 1), "=")) {
          Eval e = new Eval(List.of(), path);
          e.t = Nodes.tokens(JsTrees.initializer(k, i + 2));
          e.scope = m;
          Object v = e.expr(true);
          if (e.p < e.t.size() || v instanceof Lambda) {
            throw unknown();
          }
          return v;
        }
      }
      for (Import i : m.imports()) {
        String imported = i.names().get(name);
        Module target = imported == null || i.typeOnly() || depth > 1 ? null : model.target(i);
        if (target != null && !imported.equals("default") && !imported.equals("*")) {
          return constant(target, imported, depth + 1);
        }
      }
      throw unknown();
    }
  }
}
