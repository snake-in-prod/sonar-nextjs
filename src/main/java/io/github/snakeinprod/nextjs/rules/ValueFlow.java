package io.github.snakeinprod.nextjs.rules;

import io.github.snakeinprod.nextjs.js.JsLexer;
import io.github.snakeinprod.nextjs.js.JsTrees;
import io.github.snakeinprod.nextjs.model.Import;
import io.github.snakeinprod.nextjs.model.Module;
import io.github.snakeinprod.nextjs.parse.Chain;
import io.github.snakeinprod.nextjs.parse.Chains;
import io.github.snakeinprod.nextjs.parse.Node;
import io.github.snakeinprod.nextjs.parse.Nodes;
import io.github.snakeinprod.nextjs.parse.Token;
import io.github.snakeinprod.nextjs.parse.TokenKind;
import io.github.snakeinprod.nextjs.parse.TreeBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Follows values through the local variables of one function (a scope: an outermost function literal of the file,
 * nested functions included) for OpenRedirect, UnsafeHtmlFromInput and PersonalizedCachedFetch. A name is tainted
 * when every declaration or assignment of it in the scope has a tainted initializer and no function of the scope has a
 * parameter of that name ({@code items.map((c) => c.html)} beside {@code const c = searchParams.get('c')}); names are
 * not scoped further. On request, a React state is assigned by each call of its setter. Each tainted name keeps its
 * keys: the token texts of the expressions it came from ("searchParams . get ( 'next' )"), so that a check on any of
 * them can be found.
 */
final class ValueFlow {

  /** Keys of an expression when it is tainted, null when it is not. */
  interface Source {
    /** {@code locals}: names declared in the scope with a plain {@code const name = ...}. */
    List<List<String>> keys(List<Node> expr, Map<String, List<List<String>>> tainted, Set<String> locals);
  }

  private static final Set<String> DECLARE = Set.of("const", "let", "var");
  /** Words that continue an expression on the next line, or cannot end one: no statement boundary around them. */
  private static final Set<String> CONTINUES = Set.of("as", "satisfies", "in", "instanceof", "of", "await", "typeof", "new",
    "void", "delete", "yield", "return", "throw", "keyof");

  final Node.Group scope;
  final List<Token> tokens;
  final Map<String, List<List<String>>> tainted;
  final Set<String> locals = new HashSet<>();

  ValueFlow(JsTrees.Fn fn, Source source, Map<String, List<List<String>>> seeds) {
    this(fn, source, seeds, false);
  }

  /** {@code state}: follow React state through its setter calls. */
  ValueFlow(JsTrees.Fn fn, Source source, Map<String, List<List<String>>> seeds, boolean state) {
    this.scope = new Node.Group(null, null, fn.container().children().subList(fn.start(), fn.end()));
    this.tokens = Nodes.tokens(scope);
    List<Decl> decls = state ? state(scope, declarations(scope)) : declarations(scope);
    decls.stream().filter(Decl::plain).forEach(d -> locals.add(d.names().get(0).text()));
    this.tainted = taint(decls, source, seeds, parameters(scope), locals);
  }

  /** Names bound by the parameters of the functions in the scope: values the flow cannot see. */
  private static Set<String> parameters(Node.Group scope) {
    Set<String> out = new HashSet<>();
    for (JsTrees.Fn f : JsTrees.functions(scope)) {
      if (f.params() == null) {
        out.add(f.param().text());
        continue;
      }
      for (List<Node> item : f.params().items()) {
        int k = !item.isEmpty() && Nodes.isLeaf(item.get(0), "...") ? 1 : 0;
        if (k < item.size()) {
          names(item.get(k)).forEach(t -> out.add(t.text()));
        }
      }
    }
    return out;
  }

  /** Local names of these exports of a package: {@code import { redirect as go } from 'next/navigation'} gives go. */
  static Set<String> imported(Module m, String spec, Set<String> exported) {
    Set<String> out = new HashSet<>();
    for (Import i : m.imports()) {
      if (i.spec().equals(spec) && !i.typeOnly()) {
        i.names().forEach((local, name) -> {
          if (exported.contains(name)) {
            out.add(local);
          }
        });
      }
    }
    return out;
  }

  /** Every scope of the file with its flow. */
  static List<ValueFlow> flows(Node.Group root, java.util.function.Function<JsTrees.Fn, ValueFlow> flow) {
    List<ValueFlow> out = new ArrayList<>();
    for (JsTrees.Fn f : scopes(root)) {
      out.add(flow.apply(f));
    }
    return out;
  }

  // ---------------------------------------------------------------- scopes

  /** The function literals of the file that are not inside another one. */
  static List<JsTrees.Fn> scopes(Node.Group root) {
    List<JsTrees.Fn> all = JsTrees.functions(root);
    List<JsTrees.Fn> out = new ArrayList<>();
    for (JsTrees.Fn f : all) {
      boolean inner = false;
      for (JsTrees.Fn g : all) {
        if (g != f && pos(first(g)) <= pos(first(f)) && pos(f.body().last()) <= pos(g.body().last())) {
          inner = true;
          break;
        }
      }
      if (!inner) {
        out.add(f);
      }
    }
    return out;
  }

  static Token first(JsTrees.Fn f) {
    return f.container().children().get(f.start()).first();
  }

  static long pos(Token t) {
    return t == null ? Long.MAX_VALUE : (long) t.line() * 1_000_000L + t.column();
  }

  /** The token lies within [from, to]. */
  static boolean within(Token t, Token from, Token to) {
    return pos(t) >= pos(from) && pos(t) <= pos(to);
  }

  /** The scope holding this token, or null. */
  static ValueFlow at(List<ValueFlow> flows, Token t) {
    for (ValueFlow f : flows) {
      if (within(t, f.scope.first(), f.scope.last())) {
        return f;
      }
    }
    return null;
  }

  // ---------------------------------------------------------------- declarations

  /**
   * React state: {@code const [html, setHtml] = useState(...)} becomes one declaration of html per setter call
   * ({@code setHtml(data.html)} as {@code html = data.html}), so that html is tainted when every value set is. The
   * initial value and constants set ({@code setHtml('')} on an error) are left out, and a state never set otherwise is
   * no declaration.
   */
  private static List<Decl> state(Node.Group scope, List<Decl> decls) {
    List<Decl> out = new ArrayList<>();
    Map<String, Token> setters = new HashMap<>();
    for (Decl d : decls) {
      List<Node> init = Direct.trim(d.init());
      boolean useState = d.names().size() == 2 && !d.plain() && !init.isEmpty() && init.get(init.size() - 1) instanceof Node.Group g
        && g.is("(") && (Nodes.isIdent(init.get(0), "useState") || init.size() > 2 && Nodes.isIdent(init.get(2), "useState"));
      if (useState) {
        setters.put(d.names().get(1).text(), d.names().get(0));
      } else {
        out.add(d);
      }
    }
    if (setters.isEmpty()) {
      return decls;
    }
    for (Chain c : Chains.in(scope)) {
      Token value = setters.get(c.firstName());
      Chain.Call call = c.names().size() == 1 ? c.callAfter(0) : null;
      if (value != null && call != null && call.items().size() == 1 && !constant(call.items().get(0))) {
        out.add(new Decl(List.of(value), call.items().get(0), true));
      }
    }
    return out;
  }

  /** A literal: a string without interpolation, a number, null, undefined, a boolean, an empty array or object. */
  private static boolean constant(List<Node> e) {
    if (e.size() != 1) {
      return false;
    }
    Token t = Nodes.leaf(e.get(0));
    if (t == null) {
      return ((Node.Group) e.get(0)).children().isEmpty() && !((Node.Group) e.get(0)).is("(");
    }
    return t.kind() == TokenKind.NUMBER || t.kind() == TokenKind.STRING && interpolations(t).parts().isEmpty()
      || Set.of("null", "undefined", "true", "false").contains(t.text());
  }

  /** {@code plain}: the target is one name, not a pattern. */
  record Decl(List<Token> names, List<Node> init, boolean plain) {
  }

  /** {@code const|let|var target [: Type] = init} and {@code name = init} statements, in any block of the scope. */
  static List<Decl> declarations(Node.Group scope) {
    List<Decl> out = new ArrayList<>();
    collect(scope, true, out);
    return out;
  }

  private static void collect(Node.Group g, boolean block, List<Decl> out) {
    List<Node> kids = g.children();
    for (int i = 0; i < kids.size(); i++) {
      Node n = kids.get(i);
      if (n instanceof Node.Leaf l && l.token().kind() == TokenKind.IDENT && DECLARE.contains(l.token().text())) {
        int j = i + 1;
        while (j < kids.size()) {
          List<Token> names = names(kids.get(j));
          if (names.isEmpty()) {
            break;
          }
          int k = j + 1;
          if (k < kids.size() && Nodes.isLeaf(kids.get(k), ":")) {
            k = typeEnd(kids, k + 1);
          }
          if (k >= kids.size() || !Nodes.isLeaf(kids.get(k), "=")) {
            break;
          }
          int end = expressionEnd(kids, k + 1);
          out.add(new Decl(names, kids.subList(k + 1, end), kids.get(j) instanceof Node.Leaf));
          if (end < kids.size() && Nodes.isLeaf(kids.get(end), ",")) {
            j = end + 1;
          } else {
            break;
          }
        }
      } else if (block && n instanceof Node.Leaf l && l.token().kind() == TokenKind.IDENT && i + 1 < kids.size()
        && Nodes.isLeaf(kids.get(i + 1), "=") && (i == 0 || Nodes.isLeaf(kids.get(i - 1), ";")
        || kids.get(i - 1).last() != null && kids.get(i - 1).last().line() < l.token().line())) {
        int end = expressionEnd(kids, i + 2);
        out.add(new Decl(List.of(l.token()), kids.subList(i + 2, end), true));
      }
    }
    for (int i = 0; i < kids.size(); i++) {
      if (kids.get(i) instanceof Node.Group sub) {
        Node prev = i > 0 ? kids.get(i - 1) : null;
        boolean isBlock = sub.is("{") && (prev == null || prev instanceof Node.Group p && p.is("(") || Nodes.isLeaf(prev, "=>")
          || Nodes.isLeaf(prev, ";") || Nodes.isIdent(prev, "else") || Nodes.isIdent(prev, "try")
          || Nodes.isIdent(prev, "finally") || Nodes.isIdent(prev, "do"));
        collect(sub, isBlock, out);
      }
    }
  }

  /** Names bound by a declaration target: a name, or the names of an object or array pattern. */
  static List<Token> names(Node target) {
    Token t = Nodes.leaf(target);
    if (t != null) {
      return t.kind() == TokenKind.IDENT && !CONTINUES.contains(t.text()) ? List.of(t) : List.of();
    }
    Node.Group g = (Node.Group) target;
    if (!g.is("{") && !g.is("[")) {
      return List.of();
    }
    List<Token> out = new ArrayList<>();
    for (List<Node> item : g.items()) {
      List<Node> part = item;
      for (int i = 0; i < part.size(); i++) {
        if (Nodes.isLeaf(part.get(i), "=")) {
          part = part.subList(0, i);
          break;
        }
      }
      if (!part.isEmpty() && Nodes.isLeaf(part.get(0), "...")) {
        part = part.subList(1, part.size());
      }
      if (g.is("{") && part.size() > 2 && Nodes.isLeaf(part.get(1), ":")) {
        out.addAll(names(part.get(2)));
      } else if (!part.isEmpty()) {
        out.addAll(names(part.get(0)));
      }
    }
    return out;
  }

  /** After a type annotation: the index of the top-level "=", "," or ";" ending it ("<" ">" counted). */
  private static int typeEnd(List<Node> kids, int k) {
    int angle = 0;
    for (; k < kids.size(); k++) {
      Node n = kids.get(k);
      if (Nodes.isLeaf(n, "<")) {
        angle++;
      } else if (Nodes.isLeaf(n, ">")) {
        angle--;
      } else if (Nodes.isLeaf(n, ">>")) {
        angle -= 2;
      } else if (angle <= 0 && (Nodes.isLeaf(n, "=") || Nodes.isLeaf(n, ",") || Nodes.isLeaf(n, ";"))) {
        return k;
      }
    }
    return k;
  }

  /** End (exclusive) of the expression starting at {@code from}: a top-level ";" or ",", or a new statement on a later line. */
  static int expressionEnd(List<Node> kids, int from) {
    for (int k = from; k < kids.size(); k++) {
      Node n = kids.get(k);
      if (n instanceof Node.Leaf l && l.token().kind() == TokenKind.OP && (l.token().is(";") || l.token().is(","))) {
        return k;
      }
      if (k > from && newStatement(kids.get(k - 1), n)) {
        return k;
      }
    }
    return kids.size();
  }

  private static boolean newStatement(Node prev, Node next) {
    if (prev.last() == null || next.first() == null || next.first().line() <= prev.last().line()) {
      return false;
    }
    Token p = Nodes.leaf(prev);
    boolean ends = p == null || p.kind() == TokenKind.STRING || p.kind() == TokenKind.NUMBER
      || p.kind() == TokenKind.IDENT && !CONTINUES.contains(p.text());
    Token t = Nodes.leaf(next);
    boolean starts = t != null && (t.kind() == TokenKind.STRING || t.kind() == TokenKind.NUMBER
      || t.kind() == TokenKind.IDENT && !Set.of("as", "satisfies", "in", "instanceof", "of").contains(t.text()));
    return ends && starts;
  }

  // ---------------------------------------------------------------- taint

  private static Map<String, List<List<String>>> taint(List<Decl> decls, Source source, Map<String, List<List<String>>> seeds,
    Set<String> parameters, Set<String> locals) {
    Map<String, List<Decl>> byName = new LinkedHashMap<>();
    for (Decl d : decls) {
      for (Token n : d.names()) {
        byName.computeIfAbsent(n.text(), k -> new ArrayList<>()).add(d);
      }
    }
    Map<String, List<List<String>>> tainted = new HashMap<>(seeds);
    boolean changed = true;
    while (changed) {
      changed = false;
      for (Map.Entry<String, List<Decl>> e : byName.entrySet()) {
        if (tainted.containsKey(e.getKey()) || parameters.contains(e.getKey())) {
          continue;
        }
        List<List<String>> keys = new ArrayList<>();
        keys.add(List.of(e.getKey()));
        boolean all = true;
        for (Decl d : e.getValue()) {
          List<List<String>> k = source.keys(d.init(), tainted, locals);
          if (k == null) {
            all = false;
            break;
          }
          keys.addAll(k);
        }
        if (all) {
          tainted.put(e.getKey(), keys);
          changed = true;
        }
      }
    }
    return tainted;
  }

  // ---------------------------------------------------------------- template literals

  /** The code inside each {@code ${...}} of a template literal token, parsed; with whether the first one starts it. */
  record Interpolations(List<List<Node>> parts, boolean leading) {
  }

  static Interpolations interpolations(Token t) {
    String s = t.text();
    List<List<Node>> parts = new ArrayList<>();
    boolean leading = false;
    if (t.kind() != TokenKind.STRING || !s.startsWith("`")) {
      return new Interpolations(parts, false);
    }
    for (int i = 1; i < s.length() - 1; i++) {
      char c = s.charAt(i);
      if (c == '\\') {
        i++;
      } else if (c == '$' && s.charAt(i + 1) == '{') {
        int depth = 0;
        int j = i + 1;
        for (; j < s.length(); j++) {
          if (s.charAt(j) == '{') {
            depth++;
          } else if (s.charAt(j) == '}' && --depth == 0) {
            break;
          }
        }
        if (parts.isEmpty() && i == 1) {
          leading = true;
        }
        String code = s.substring(i + 2, Math.min(j, s.length()));
        parts.add(TreeBuilder.build(JsLexer.lex("t.ts", "ts", code).tokens()).children());
        i = j;
      }
    }
    return new Interpolations(parts, leading);
  }

  // ---------------------------------------------------------------- checks

  private static final Set<String> COMPARE = Set.of("===", "!==", "==", "!=", "in");

  /**
   * One of the keys is used outside every sink (the calls or objects that use such values) in a check: compared ({@code ===}, {@code in}), the receiver of a method
   * that is not a plain getter ({@code next.startsWith('/')}) when {@code methods}, the receiver of a method or inside
   * the arguments of a call whose name passes {@code callee} ({@code isSafe(next)}, {@code DOMPurify.sanitize(html)}).
   * With {@code until}, only checks before that token count: one after the sink ({@code next === '/x' && <Hint />} in the
   * returned JSX) cannot guard it.
   */
  boolean checked(List<List<String>> keys, List<Node.Group> sinks, boolean compare, boolean methods, Predicate<String> callee) {
    return checked(keys, sinks, compare, methods, callee, null);
  }

  boolean checked(List<List<String>> keys, List<Node.Group> sinks, boolean compare, boolean methods, Predicate<String> callee,
    Token until) {
    List<Token> toks = new ArrayList<>();
    for (Token t : tokens) {
      if (!t.is("!")) {
        toks.add(t);
      }
    }
    for (List<String> key : keys) {
      if (key.isEmpty()) {
        continue;
      }
      for (int i = 0; i + key.size() <= toks.size(); i++) {
        if (until != null && pos(toks.get(i)) >= pos(until)) {
          break;
        }
        if (!matches(toks, i, key) || inSink(toks.get(i), sinks) || i > 0 && accessor(toks.get(i - 1))) {
          continue;
        }
        int a = i + key.size();
        Token before = i > 0 ? toks.get(i - 1) : null;
        int p = a;
        while (p + 1 < toks.size() && accessor(toks.get(p)) && toks.get(p + 1).kind() == TokenKind.IDENT
          && !(p + 2 < toks.size() && toks.get(p + 2).is("("))) {
          // url.origin !== ...: a property of the value compared
          p += 2;
        }
        if (compare && (before != null && COMPARE.contains(before.text()) || p < toks.size() && COMPARE.contains(toks.get(p).text()))) {
          return true;
        }
        Token after = a < toks.size() ? toks.get(a) : null;
        if (after != null && accessor(after) && a + 2 < toks.size() && toks.get(a + 2).is("(")) {
          String m = toks.get(a + 1).text();
          if (callee.test(m) || methods && !Direct.PASSTHROUGH.contains(m) && !Direct.GETTERS.contains(m)) {
            return true;
          }
        }
        Token call = enclosingCall(toks, i);
        if (call != null && callee.test(call.text())) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean inSink(Token t, List<Node.Group> sinks) {
    for (Node.Group g : sinks) {
      if (within(t, g.first(), g.last())) {
        return true;
      }
    }
    return false;
  }

  private static boolean matches(List<Token> toks, int i, List<String> key) {
    for (int k = 0; k < key.size(); k++) {
      if (!norm(toks.get(i + k).text()).equals(key.get(k))) {
        return false;
      }
    }
    return true;
  }

  static String norm(String s) {
    return s.equals("?.") ? "." : s;
  }

  private static boolean accessor(Token t) {
    return t.is(".") || t.is("?.");
  }

  /** The name before the nearest unclosed "(" around token i, when it is one: the callee. */
  private static Token enclosingCall(List<Token> toks, int i) {
    int depth = 0;
    for (int k = i - 1; k >= 0; k--) {
      String s = toks.get(k).text();
      if (toks.get(k).kind() != TokenKind.OP) {
        continue;
      }
      if (s.equals(")") || s.equals("]") || s.equals("}")) {
        depth++;
      } else if (s.equals("(") || s.equals("[") || s.equals("{")) {
        if (depth == 0) {
          return s.equals("(") && k > 0 && toks.get(k - 1).kind() == TokenKind.IDENT ? toks.get(k - 1) : null;
        }
        depth--;
      }
    }
    return null;
  }

  // ---------------------------------------------------------------- request values

  /**
   * An expression that is a value from the request as it is: searchParams, params or form data (or, for HTML, a fetch
   * response), a variable holding one, through getters ({@code .get('x')}, {@code .json()}), fallbacks
   * ({@code x ?? '/'}), casts, {@code await}, {@code String()}, {@code decodeURIComponent()} and {@code new URL()}. A
   * template literal counts when its first interpolation starts it and is such a value (anywhere for HTML), a
   * concatenation when its first operand is (any operand for HTML). Any other call or operator means the value was
   * transformed: not followed.
   */
  static final class Direct implements Source {

    static final Set<String> ROOTS = Set.of("searchParams", "params", "formData", "useSearchParams", "useParams");
    static final Set<String> GETTERS = Set.of("get", "getAll", "json", "text", "formData", "useSearchParams", "useParams", "then",
      "fetch");
    static final Set<String> PASSTHROUGH = Set.of("toString", "trim", "toLowerCase", "toUpperCase", "valueOf", "at");
    private static final Set<String> OBJECTS = Set.of("fetch", "json", "text", "then");
    private static final Set<String> WRAPPERS = Set.of("String", "decodeURIComponent", "decodeURI");
    private static final Set<String> FORM_GETTERS = Set.of("get", "getAll");

    private final boolean html;
    private final boolean route;

    /** {@code route}: a Next.js convention file, where "params" are the route params; elsewhere a "params" is any argument. */
    Direct(boolean html, boolean route) {
      this.html = html;
      this.route = route;
    }

    /** Names of parameters typed FormData ({@code data: FormData}) in the scope: tainted from the start. */
    static Map<String, List<List<String>>> formDataParams(JsTrees.Fn fn) {
      List<Token> toks = Nodes.tokens(fn.container().children().subList(fn.start(), fn.end()));
      Map<String, List<List<String>>> out = new HashMap<>();
      for (int i = 0; i + 2 < toks.size(); i++) {
        if (toks.get(i).kind() == TokenKind.IDENT && toks.get(i + 1).is(":") && toks.get(i + 2).isIdent("FormData")) {
          out.put(toks.get(i).text(), List.of(List.of(toks.get(i).text())));
        }
      }
      return out;
    }

    @Override
    public List<List<String>> keys(List<Node> expr, Map<String, List<List<String>>> tainted, Set<String> locals) {
      List<Node> e = trim(expr);
      if (e.isEmpty()) {
        return null;
      }
      List<List<Node>> parts = split(e, Set.of("??", "||"));
      if (parts.size() > 1) {
        return keys(parts.get(0), tainted, locals);
      }
      parts = split(e, Set.of("+"));
      if (parts.size() > 1) {
        if (!html) {
          return keys(parts.get(0), tainted, locals);
        }
        for (List<Node> p : parts) {
          List<List<String>> k = keys(p, tainted, locals);
          if (k != null) {
            return k;
          }
        }
        return null;
      }
      return operand(e, tainted, locals);
    }

    private List<List<String>> operand(List<Node> e, Map<String, List<List<String>>> tainted, Set<String> locals) {
      Token t = Nodes.leaf(e.get(0));
      if (e.size() == 1 && t != null && t.kind() == TokenKind.STRING) {
        Interpolations in = interpolations(t);
        for (int i = 0; i < in.parts().size(); i++) {
          if (!html && (i > 0 || !in.leading())) {
            break;
          }
          List<List<String>> k = keys(in.parts().get(i), tainted, locals);
          if (k != null) {
            return k;
          }
        }
        return null;
      }
      if (e.size() == 3 && Nodes.isIdent(e.get(0), "new") && Nodes.isIdent(e.get(1), "URL") && e.get(2) instanceof Node.Group g
        && g.is("(") && !g.items().isEmpty()) {
        return keys(g.items().get(0), tainted, locals);
      }
      if (e.size() == 2 && t != null && WRAPPERS.contains(t.text()) && e.get(1) instanceof Node.Group g && g.is("(")
        && !g.items().isEmpty()) {
        return keys(g.items().get(0), tainted, locals);
      }
      List<List<String>> inherited = new ArrayList<>();
      boolean source;
      String root = null;
      List<String> names = new ArrayList<>();
      if (e.get(0) instanceof Node.Group g && g.is("(")) {
        List<List<String>> inner = keys(g.children(), tainted, locals);
        if (inner == null) {
          return null;
        }
        inherited.addAll(inner);
        source = true;
      } else if (t != null && t.kind() == TokenKind.IDENT) {
        root = t.text();
        names.add(root);
        source = tainted.containsKey(root);
        if (source) {
          inherited.addAll(tainted.get(root));
        }
      } else {
        return null;
      }
      String last = root;
      String lastKeyName = root;
      int keyEnd = e.size();
      for (int i = 1; i < e.size(); i++) {
        Node n = e.get(i);
        if (Nodes.isLeaf(n, ".") || Nodes.isLeaf(n, "?.")) {
          Token name = i + 1 < e.size() ? Nodes.leaf(e.get(i + 1)) : null;
          if (name == null || name.kind() != TokenKind.IDENT) {
            return null;
          }
          if (PASSTHROUGH.contains(name.text()) && keyEnd == e.size()) {
            keyEnd = i;
          }
          last = name.text();
          names.add(last);
          if (keyEnd == e.size()) {
            lastKeyName = last;
          }
          i++;
        } else if (Nodes.isLeaf(n, "!") || n instanceof Node.Group x && x.is("[")) {
          continue;
        } else if (n instanceof Node.Group call && call.is("(")) {
          if (last == null || !(PASSTHROUGH.contains(last) || GETTERS.contains(last) && (!last.equals("then") || simpleThen(call))
            && (!last.equals("fetch") || html && i == 1))) {
            return null;
          }
        } else {
          return null;
        }
      }
      for (int k = 0; k < names.size(); k++) {
        String n = names.get(k);
        // a variable named formData is FormData when read with get(); formData.title is form state; a local
        // const params = usePathname() is not the route params
        source |= ROOTS.contains(n) && (route && !(k == 0 && locals.contains(n)) || !n.equals("params"))
          && !(k == 0 && n.equals("formData") && (names.size() < 2 || !FORM_GETTERS.contains(names.get(1))));
      }
      source |= html && "fetch".equals(root);
      if (!source) {
        return null;
      }
      // the value itself (not a property of it) keeps the keys of where it came from
      List<List<String>> keys = keyEnd == 1 ? inherited : new ArrayList<>();
      // a whole request object or response (searchParams, request.formData(), res.json()) is no key: a check on one
      // of its properties checks nothing about another
      if (lastKeyName != null && !ROOTS.contains(lastKeyName) && !OBJECTS.contains(lastKeyName)
        && (keyEnd > 1 || tainted.containsKey(root))) {
        List<String> key = new ArrayList<>();
        for (Token k : Nodes.tokens(e.subList(0, keyEnd))) {
          if (!k.is("!")) {
            key.add(norm(k.text()));
          }
        }
        keys.add(key);
      }
      return keys;
    }

    /** {@code .then(r => r.json())}: the callback only reads the body. */
    private static boolean simpleThen(Node.Group call) {
      for (Chain c : Chains.in(call)) {
        List<String> names = c.names();
        for (int i = 0; i < names.size(); i++) {
          if (c.callAfter(i) != null && !names.get(i).equals("json") && !names.get(i).equals("text")) {
            return false;
          }
        }
      }
      return true;
    }

    /** Without a leading await, a trailing "as Type" / "satisfies Type" and a trailing "!". */
    static List<Node> trim(List<Node> e) {
      int from = 0;
      while (from < e.size() && Nodes.isIdent(e.get(from), "await")) {
        from++;
      }
      int to = e.size();
      for (int i = from; i < to; i++) {
        if (Nodes.isIdent(e.get(i), "as") || Nodes.isIdent(e.get(i), "satisfies")) {
          to = i;
          break;
        }
      }
      while (to > from && Nodes.isLeaf(e.get(to - 1), "!")) {
        to--;
      }
      return e.subList(from, to);
    }

    private static List<List<Node>> split(List<Node> e, Set<String> ops) {
      List<List<Node>> out = new ArrayList<>();
      int from = 0;
      for (int i = 0; i < e.size(); i++) {
        Token t = Nodes.leaf(e.get(i));
        if (t != null && t.kind() == TokenKind.OP && ops.contains(t.text())) {
          out.add(e.subList(from, i));
          from = i + 1;
        }
      }
      out.add(e.subList(from, e.size()));
      return out;
    }
  }
}
