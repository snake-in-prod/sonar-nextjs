package io.github.snakeinprod.nextjs.model;

import io.github.snakeinprod.nextjs.js.JsTrees;
import io.github.snakeinprod.nextjs.parse.Chain;
import io.github.snakeinprod.nextjs.parse.Chains;
import io.github.snakeinprod.nextjs.parse.Node;
import io.github.snakeinprod.nextjs.parse.Nodes;
import io.github.snakeinprod.nextjs.parse.ParsedFile;
import io.github.snakeinprod.nextjs.parse.Token;
import io.github.snakeinprod.nextjs.parse.TokenKind;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reads directives, imports, exports, functions, Server Actions and env reads from a parsed file. */
final class ModuleReader {

  private static final Map<String, Directive> PROLOGUE = Map.of("use client", Directive.USE_CLIENT, "use server",
    Directive.USE_SERVER, "use cache", Directive.USE_CACHE);
  private static final Set<String> IMPORT_CLAUSE = Set.of("type", "*", "as", ",", "from");

  private ModuleReader() {
  }

  static Module read(ParsedFile file, Resolver resolver) {
    Node.Group root = file.root();
    Set<Directive> directives = EnumSet.noneOf(Directive.class);
    List<Token> tokens = file.tokens();
    // the directive prologue: string statements before any other code
    for (int i = 0; i < tokens.size() && tokens.get(i).kind() == TokenKind.STRING; i++) {
      Directive d = PROLOGUE.get(JsTrees.unquote(tokens.get(i).text()));
      if (d != null) {
        directives.add(d);
      }
      if (i + 1 < tokens.size() && tokens.get(i + 1).is(";")) {
        i++;
      }
    }
    List<Import> imports = imports(file, resolver);
    for (Import i : imports) {
      if (i.spec().equals("server-only")) {
        directives.add(Directive.SERVER_ONLY);
      } else if (i.spec().equals("client-only")) {
        directives.add(Directive.CLIENT_ONLY);
      }
    }
    Map<String, Token> exports = exports(root);
    List<JsTrees.Fn> all = JsTrees.functions(root);
    Map<String, JsTrees.Fn> functions = new LinkedHashMap<>();
    Map<String, List<JsTrees.Fn>> withDirective = new HashMap<>();
    List<ServerAction> actions = new ArrayList<>();
    List<Module.Wrapped> wrapped = new ArrayList<>();
    for (JsTrees.Fn f : all) {
      if (f.container() == root) {
        Token name = JsTrees.nameOf(f);
        if (name != null) {
          functions.putIfAbsent(name.text(), f);
        } else if (f.start() > 0 && Nodes.isIdent(root.children().get(f.start() - 1), "default")) {
          functions.putIfAbsent("default", f);
        }
      }
      if (!f.expression() && !f.body().children().isEmpty() && Nodes.leaf(f.body().children().get(0)) != null
        && Nodes.leaf(f.body().children().get(0)).kind() == TokenKind.STRING) {
        String d = JsTrees.unquote(Nodes.leaf(f.body().children().get(0)).text());
        if (d.startsWith("use ")) {
          withDirective.computeIfAbsent(d, k -> new ArrayList<>()).add(f);
        }
      }
    }
    if (directives.contains(Directive.USE_SERVER)) {
      for (Map.Entry<String, Token> e : exports.entrySet()) {
        JsTrees.Fn f = functions.get(e.getKey().equals("default") && !functions.containsKey(e.getValue().text()) ? "default"
          : e.getValue().text());
        if (f != null) {
          Token name = JsTrees.nameOf(f);
          actions.add(new ServerAction(name != null ? name : e.getValue(), f, false, null, null));
        } else {
          ActionBuilder b = e.getValue().isIdent("default") ? defaultBuilder(root, e.getValue())
            : ActionBuilder.declared(root, e.getValue().text());
          if (b != null && b.handler() != null) {
            actions.add(new ServerAction(e.getValue(), b.handler(), false, b, null));
          } else if (b != null && b.client() == null && b.steps().size() == 1) {
            Module.Wrapped w = wrapped(e.getValue(), b.steps().get(0), functions);
            if (w != null) {
              wrapped.add(w);
            }
          }
        }
      }
    }
    for (JsTrees.Fn f : withDirective.getOrDefault("use server", List.of())) {
      if (actions.stream().anyMatch(a -> a.fn() == f)) {
        continue;
      }
      Token name = JsTrees.nameOf(f);
      actions.add(new ServerAction(name != null ? name : f.container().children().get(f.start()).first(), f, true, null, null));
    }
    actions.sort((a, b) -> compare(a.name(), b.name()));
    List<EnvRead> env = new ArrayList<>();
    // the "process" chains only: the others are built when a rule asks for them
    boolean process = false;
    for (int i = 0; i < tokens.size() && !process; i++) {
      process = tokens.get(i).isIdent("process");
    }
    for (Chain c : process ? Chains.in(root, Chains.keywords(file.language()), "process") : List.<Chain>of()) {
      List<Chain.Segment> s = c.segments();
      if (s.size() >= 3 && c.firstName().equals("process") && s.get(1) instanceof Chain.Name n && n.token().is("env")) {
        if (s.get(2) instanceof Chain.Name v) {
          env.add(new EnvRead(v.token().text(), v.token()));
        } else if (s.get(2) instanceof Chain.Index x && x.index().children().size() == 1 && Nodes.leaf(x.index().children().get(0)) != null
          && Nodes.leaf(x.index().children().get(0)).kind() == TokenKind.STRING) {
          Token t = Nodes.leaf(x.index().children().get(0));
          env.add(new EnvRead(JsTrees.unquote(t.text()), t));
        }
      }
    }
    return new Module(file, directives, imports, exports, functions, withDirective, actions, wrapped, env, runtime(tokens));
  }

  /** The call after {@code export default}, as a builder chain, or null. */
  private static ActionBuilder defaultBuilder(Node.Group root, Token def) {
    List<Node> kids = root.children();
    for (int i = 0; i < kids.size(); i++) {
      if (Nodes.leaf(kids.get(i)) == def) {
        return ActionBuilder.of(JsTrees.initializer(kids, i + 1));
      }
    }
    return null;
  }

  /** The first argument of the call that is a function literal or a top-level function of the module, or null. */
  private static Module.Wrapped wrapped(Token name, ActionBuilder.Step call, Map<String, JsTrees.Fn> functions) {
    List<Node> kids = call.args().children();
    int arg = 0;
    for (int i = 0; i < kids.size(); i++) {
      if (Nodes.isLeaf(kids.get(i), ",")) {
        arg++;
        continue;
      }
      if (i > 0 && !Nodes.isLeaf(kids.get(i - 1), ",")) {
        continue;
      }
      JsTrees.Fn f = JsTrees.functionAt(call.args(), i);
      Token t = Nodes.leaf(kids.get(i));
      if (f == null && t != null && (i + 1 == kids.size() || Nodes.isLeaf(kids.get(i + 1), ","))) {
        f = functions.get(t.text());
      }
      if (f != null) {
        return new Module.Wrapped(name, call.name(), arg, f);
      }
    }
    return null;
  }

  static int compare(Token a, Token b) {
    return a.line() != b.line() ? Integer.compare(a.line(), b.line()) : Integer.compare(a.column(), b.column());
  }

  /** {@code export const runtime = 'edge'}, or {@code runtime: 'nodejs'} in {@code export const config = {...}}. */
  private static String runtime(List<Token> t) {
    for (int i = 0; i + 4 < t.size(); i++) {
      if (t.get(i).isIdent("export") && t.get(i + 1).isIdent("const") && t.get(i + 2).isIdent("runtime") && t.get(i + 3).is("=")
        && t.get(i + 4).kind() == TokenKind.STRING) {
        return JsTrees.unquote(t.get(i + 4).text());
      }
      if (t.get(i).isIdent("export") && t.get(i + 1).isIdent("const") && t.get(i + 2).isIdent("config")) {
        String r = configRuntime(t, i + 3);
        if (r != null) {
          return r;
        }
      }
    }
    return null;
  }

  /** The top-level {@code runtime: '...'} of the object literal after {@code =} from {@code k} (a type may come first). */
  private static String configRuntime(List<Token> t, int k) {
    for (int depth = 0; k < t.size() && (depth > 0 || !t.get(k).is("=") && !t.get(k).is(";")); k++) {
      depth += t.get(k).is("{") || t.get(k).is("(") || t.get(k).is("[") ? 1 : t.get(k).is("}") || t.get(k).is(")") || t.get(k).is("]") ? -1 : 0;
    }
    if (k + 1 >= t.size() || !t.get(k).is("=") || !t.get(k + 1).is("{")) {
      return null;
    }
    int depth = 0;
    for (int j = k + 1; j + 2 < t.size(); j++) {
      Token x = t.get(j);
      if (x.is("{") || x.is("(") || x.is("[")) {
        depth++;
      } else if (x.is("}") || x.is(")") || x.is("]")) {
        if (--depth == 0) {
          return null;
        }
      } else if (depth == 1 && (x.isIdent("runtime") || x.kind() == TokenKind.STRING && JsTrees.unquote(x.text()).equals("runtime"))
        && t.get(j + 1).is(":") && t.get(j + 2).kind() == TokenKind.STRING) {
        return JsTrees.unquote(t.get(j + 2).text());
      }
    }
    return null;
  }

  // ---------------------------------------------------------------- imports

  private static List<Import> imports(ParsedFile file, Resolver resolver) {
    List<Import> out = new ArrayList<>();
    List<Node> kids = file.root().children();
    for (int i = 0; i < kids.size(); i++) {
      boolean isImport = Nodes.isIdent(kids.get(i), "import");
      boolean isExport = Nodes.isIdent(kids.get(i), "export");
      if (!isImport && !isExport || i > 0 && Nodes.isLeaf(kids.get(i - 1), ".")) {
        continue;
      }
      // the clause up to "from" + string (or, for an import, a string right away)
      int j = i + 1;
      while (j < kids.size()) {
        Node n = kids.get(j);
        Token t = Nodes.leaf(n);
        if (t != null && t.kind() == TokenKind.STRING) {
          break;
        }
        boolean clause = n instanceof Node.Group g && g.is("{") || t != null && (IMPORT_CLAUSE.contains(t.text())
          || t.kind() == TokenKind.IDENT && !t.is("import") && !t.is("export") && (isImport || j > i + 1 || !isDeclaration(t)));
        if (!clause) {
          break;
        }
        j++;
      }
      Token spec = j < kids.size() ? Nodes.leaf(kids.get(j)) : null;
      boolean sideEffect = isImport && j == i + 1;
      if (spec == null || spec.kind() != TokenKind.STRING || !sideEffect && !Nodes.isIdent(kids.get(j - 1), "from")) {
        continue;
      }
      List<Node> clause = sideEffect ? List.of() : kids.subList(i + 1, j - 1);
      Map<String, String> names = new LinkedHashMap<>();
      boolean typeOnly = names(clause, names);
      out.add(make(file, resolver, spec, names, typeOnly, isExport, false));
      i = j;
    }
    // a require() or import() call needs one of these tokens: most files have none and skip the walk
    List<Token> tokens = file.tokens();
    boolean calls = false;
    for (int i = 0; i < tokens.size() && !calls; i++) {
      Token t = tokens.get(i);
      calls = t.isIdent("require") || t.isIdent("import") && i + 1 < tokens.size() && tokens.get(i + 1).is("(");
    }
    if (calls) {
      Nodes.groups(file.root(), g -> {
        List<Node> k = g.children();
        for (int i = 0; i + 1 < k.size(); i++) {
          boolean req = Nodes.isIdent(k.get(i), "require");
          boolean dyn = Nodes.isIdent(k.get(i), "import");
          if ((req || dyn) && !(i > 0 && Nodes.isLeaf(k.get(i - 1), ".")) && k.get(i + 1) instanceof Node.Group call && call.is("(")
            && !call.children().isEmpty() && Nodes.leaf(call.children().get(0)) != null
            && Nodes.leaf(call.children().get(0)).kind() == TokenKind.STRING) {
            out.add(make(file, resolver, Nodes.leaf(call.children().get(0)), Map.of(), false, false, dyn));
          }
        }
      });
    }
    out.sort((a, b) -> compare(a.token(), b.token()));
    return out;
  }

  private static final Set<String> DECLARATIONS = Set.of("const", "let", "var", "function", "class", "default", "async", "enum",
    "interface", "abstract", "declare", "namespace");

  /** "export const x" or "export default": not a re-export clause. */
  private static boolean isDeclaration(Token t) {
    return DECLARATIONS.contains(t.text());
  }

  private static Import make(ParsedFile file, Resolver resolver, Token spec, Map<String, String> names, boolean typeOnly,
    boolean reExport, boolean dynamic) {
    String s = JsTrees.unquote(spec.text());
    String resolved = s.contains("${") ? null : resolver.resolve(file.path(), s);
    return new Import(s, resolved != null && resolved.equals(file.path()) ? null : resolved, spec, Map.copyOf(names), typeOnly,
      reExport, dynamic);
  }

  /** Fills local (or exported) name to imported name; returns whether the clause imports types only. */
  private static boolean names(List<Node> clause, Map<String, String> names) {
    int k = 0;
    boolean typeOnly = false;
    if (!clause.isEmpty() && Nodes.isIdent(clause.get(0), "type") && clause.size() > 1 && !Nodes.isLeaf(clause.get(1), ",")) {
      typeOnly = true;
      k = 1;
    }
    boolean typeSpecifiers = false;
    for (; k < clause.size(); k++) {
      Node n = clause.get(k);
      if (n instanceof Node.Group g) {
        for (List<Node> item : g.items()) {
          if (item.size() > 1 && Nodes.isIdent(item.get(0), "type") && !Nodes.isIdent(item.get(1), "as")) {
            typeSpecifiers = true;
            continue;
          }
          String imported = text(item.get(0));
          String local = item.size() > 2 && Nodes.isIdent(item.get(1), "as") ? text(item.get(2)) : imported;
          names.put(local, imported);
        }
      } else if (Nodes.isLeaf(n, "*")) {
        if (k + 2 < clause.size() && Nodes.isIdent(clause.get(k + 1), "as")) {
          names.put(text(clause.get(k + 2)), "*");
          k += 2;
        } else {
          names.put("*", "*");
        }
      } else if (Nodes.leaf(n) != null && Nodes.leaf(n).kind() == TokenKind.IDENT) {
        names.put(Nodes.leaf(n).text(), "default");
      }
    }
    return typeOnly || typeSpecifiers && names.isEmpty();
  }

  private static String text(Node n) {
    Token t = Nodes.leaf(n);
    return t == null ? Nodes.text(n) : JsTrees.unquote(t.text());
  }

  // ---------------------------------------------------------------- exports

  private static Map<String, Token> exports(Node.Group root) {
    Map<String, Token> out = new LinkedHashMap<>();
    List<Node> kids = root.children();
    for (int i = 0; i + 1 < kids.size(); i++) {
      if (!Nodes.isIdent(kids.get(i), "export") || i > 0 && Nodes.isLeaf(kids.get(i - 1), ".")) {
        continue;
      }
      int j = i + 1;
      if (Nodes.isIdent(kids.get(j), "default")) {
        Token def = Nodes.leaf(kids.get(j));
        int k = j + 1;
        while (k < kids.size() && (Nodes.isIdent(kids.get(k), "async") || Nodes.isIdent(kids.get(k), "function")
          || Nodes.isLeaf(kids.get(k), "*") || Nodes.isIdent(kids.get(k), "class"))) {
          k++;
        }
        Token name = k > j + 1 && k < kids.size() ? Nodes.leaf(kids.get(k)) : null;
        // export default Foo;  export default function Foo() {}  export default () => ...
        if (name == null && k == j + 1 && k < kids.size()) {
          Token only = Nodes.leaf(kids.get(k));
          boolean alone = only != null && only.kind() == TokenKind.IDENT && (k + 1 >= kids.size() || Nodes.isLeaf(kids.get(k + 1), ";")
            || Nodes.leaf(kids.get(k + 1)) != null && Nodes.leaf(kids.get(k + 1)).line() > only.line());
          name = alone ? only : null;
        }
        out.put("default", name != null && name.kind() == TokenKind.IDENT ? name : def);
        continue;
      }
      if (kids.get(j) instanceof Node.Group g && g.is("{")) {
        int after = j + 1;
        if (after < kids.size() && Nodes.isIdent(kids.get(after), "from")) {
          continue;
        }
        for (List<Node> item : g.items()) {
          int a = item.size() > 1 && Nodes.isIdent(item.get(0), "type") ? 1 : 0;
          Token local = Nodes.leaf(item.get(a));
          if (local != null) {
            String exported = item.size() > a + 2 && Nodes.isIdent(item.get(a + 1), "as") ? text(item.get(a + 2)) : local.text();
            out.put(exported, local);
          }
        }
        continue;
      }
      while (j < kids.size() && (Nodes.isIdent(kids.get(j), "async") || Nodes.isIdent(kids.get(j), "declare")
        || Nodes.isIdent(kids.get(j), "abstract"))) {
        j++;
      }
      if (j + 1 >= kids.size()) {
        continue;
      }
      Token kw = Nodes.leaf(kids.get(j));
      if (kw == null) {
        continue;
      }
      if (kw.is("function") || kw.is("class") || kw.is("enum") || kw.is("interface") || kw.is("type") || kw.is("namespace")) {
        int k = j + 1 < kids.size() && Nodes.isLeaf(kids.get(j + 1), "*") ? j + 2 : j + 1;
        Token name = k < kids.size() ? Nodes.leaf(kids.get(k)) : null;
        if (name != null && name.kind() == TokenKind.IDENT) {
          out.put(name.text(), name);
        }
      } else if ((kw.is("const") || kw.is("let") || kw.is("var")) && Nodes.leaf(kids.get(j + 1)) != null
        && Nodes.leaf(kids.get(j + 1)).kind() == TokenKind.IDENT) {
        // ponytail: the first declarator only; export const a = 1, b = 2 and destructuring are left out
        out.put(Nodes.leaf(kids.get(j + 1)).text(), Nodes.leaf(kids.get(j + 1)));
      }
    }
    return out;
  }
}
