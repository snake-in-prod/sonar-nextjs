package io.github.snakeinprod.nextjs.rules;

import io.github.snakeinprod.nextjs.js.JsTrees;
import io.github.snakeinprod.nextjs.model.Module;
import io.github.snakeinprod.nextjs.model.Route;
import io.github.snakeinprod.nextjs.parse.Chain;
import io.github.snakeinprod.nextjs.parse.Chains;
import io.github.snakeinprod.nextjs.parse.Node;
import io.github.snakeinprod.nextjs.parse.Nodes;
import io.github.snakeinprod.nextjs.parse.Token;
import io.github.snakeinprod.nextjs.parse.TokenKind;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * params and searchParams should be awaited in Next.js 15 and later. Checks the default export of App Router pages,
 * layouts and default.js, their generateMetadata / generateViewport, and the HTTP method exports of route handlers
 * (second argument), when the app's package.json declares next 15 or later. A read is {@code params.x},
 * {@code params[x]}, {@code const { x } = params}, {@code { params: { x } }} in the signature, {@code props.params.x},
 * or {@code ${params.x}} in a template literal. Awaiting, {@code use(params)}, {@code params.then}, and passing the
 * value on are fine. A name redeclared in the body or a parameter of a nested function shadowing it is not followed.
 * One issue per name and function, on the first read.
 */
public class SyncParamsAccess implements NextRule {

  private static final Set<String> NAMES = Set.of("params", "searchParams");
  private static final Set<String> COMPONENTS = Set.of("page", "layout", "default");
  private static final List<String> METADATA = List.of("generateMetadata", "generateViewport");
  private static final List<String> METHODS = List.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
  private static final Set<String> PROMISE = Set.of("then", "catch", "finally");
  private static final Pattern TEMPLATE_EXPR = Pattern.compile("\\$\\{[^}]*");

  private record Read(Token at, String name) {
  }

  @Override
  public void check(RuleContext ctx) {
    Module m = ctx.module();
    Route r = m.route();
    if (r == null || !r.router().equals("app") || ctx.model().nextMajor(m) < 15) {
      return;
    }
    if (COMPONENTS.contains(r.kind())) {
      check(ctx, exported(m, "default"), 0);
      for (String name : METADATA) {
        check(ctx, exported(m, name), 0);
      }
    } else if (r.kind().equals("route")) {
      for (String method : METHODS) {
        check(ctx, exported(m, method), 1);
      }
    }
  }

  private static JsTrees.Fn exported(Module m, String name) {
    Token t = m.exports().get(name);
    return t == null ? null : m.functions().get(t.text());
  }

  private static void check(RuleContext ctx, JsTrees.Fn fn, int index) {
    if (fn == null) {
      return;
    }
    // local name -> "params" / "searchParams"
    Map<String, String> roots = new LinkedHashMap<>();
    String holder = null;
    List<Read> reads = new ArrayList<>();
    if (fn.param() != null) {
      holder = index == 0 ? fn.param().text() : null;
    } else if (fn.params().items().size() > index) {
      List<Node> item = fn.params().items().get(index);
      if (item.get(0) instanceof Node.Group g && g.is("{")) {
        destructure(g, roots, reads);
      } else if (ident(item.get(0)) && (item.size() == 1 || Nodes.isLeaf(item.get(1), ":") || Nodes.isLeaf(item.get(1), "="))) {
        holder = Nodes.leaf(item.get(0)).text();
      }
    }
    Node.Group body = fn.body();
    List<Token> tokens = Nodes.tokens(body);
    roots.keySet().removeIf(n -> redeclared(tokens, n));
    if (holder != null && redeclared(tokens, holder)) {
      holder = null;
    }
    if (roots.isEmpty() && holder == null && reads.isEmpty()) {
      return;
    }
    List<JsTrees.Fn> nested = JsTrees.functions(body);
    for (Chain c : Chains.in(body, Chains.keywords(ctx.module().language()))) {
      String local = c.firstName();
      List<Chain.Segment> s = c.segments();
      int rest;
      String name;
      Token at;
      if (local.equals(holder) && s.size() == 1) {
        // const { params } = props
        if (c.precededBy("=") && c.from() >= 2 && c.container().children().get(c.from() - 2) instanceof Node.Group g && g.is("{")
          && !shadowed(nested, local, c.first())) {
          destructure(g, roots, reads);
        }
        continue;
      }
      if (roots.containsKey(local)) {
        name = roots.get(local);
        at = c.first();
        rest = 1;
      } else if (local.equals(holder) && s.size() > 1 && s.get(1) instanceof Chain.Name n && NAMES.contains(n.token().text())) {
        name = n.token().text();
        at = n.token();
        rest = 2;
      } else {
        continue;
      }
      if (shadowed(nested, local, c.first())) {
        continue;
      }
      if (s.size() > rest) {
        if (s.get(rest) instanceof Chain.Index || s.get(rest) instanceof Chain.Name n && !PROMISE.contains(n.token().text())) {
          reads.add(new Read(at, name));
        }
      } else if (destructured(c)) {
        reads.add(new Read(at, name));
      }
    }
    for (Token t : tokens) {
      if (t.kind() == TokenKind.STRING && t.text().startsWith("`") && !shadowedAny(nested, roots, holder, t)) {
        templateReads(t, roots, holder, reads);
      }
    }
    reads.sort(Comparator.comparingInt((Read x) -> x.at().line()).thenComparingInt(x -> x.at().column()));
    Set<String> done = new HashSet<>();
    for (Read x : reads) {
      if (done.add(x.name())) {
        ctx.report(x.at(), "Await \"" + x.name() + "\" or unwrap it with React.use before reading it: it is a promise since Next.js 15.");
      }
    }
  }

  /** {@code { params, searchParams: sp, params: { slug } }}: plain and renamed ones become roots, nested patterns are reads. */
  private static void destructure(Node.Group g, Map<String, String> roots, List<Read> reads) {
    for (List<Node> p : g.items()) {
      Token k = Nodes.leaf(p.get(0));
      if (k == null || k.kind() != TokenKind.IDENT || !NAMES.contains(k.text())) {
        continue;
      }
      if (p.size() == 1 || Nodes.isLeaf(p.get(1), "=")) {
        roots.put(k.text(), k.text());
      } else if (Nodes.isLeaf(p.get(1), ":") && p.size() > 2) {
        if (p.get(2) instanceof Node.Group d && (d.is("{") || d.is("["))) {
          reads.add(new Read(k, k.text()));
        } else if (p.size() == 3 && ident(p.get(2))) {
          roots.put(Nodes.leaf(p.get(2)).text(), k.text());
        }
      }
    }
  }

  /** {@code const { x } = <chain>}: the chain alone after "=" with a destructuring pattern before it. */
  private static boolean destructured(Chain c) {
    if (!c.precededBy("=") || c.from() < 2) {
      return false;
    }
    Node after = c.after();
    return c.container().children().get(c.from() - 2) instanceof Node.Group g && (g.is("{") || g.is("["))
      && (after == null || Nodes.isLeaf(after, ";") || Nodes.isLeaf(after, ",") || after.first().line() > c.last().line());
  }

  /** {@code ${params.x}} or {@code ${props.params.x}} inside a template literal. */
  private static void templateReads(Token t, Map<String, String> roots, String holder, List<Read> reads) {
    Matcher expr = TEMPLATE_EXPR.matcher(t.text());
    while (expr.find()) {
      String e = expr.group();
      for (Map.Entry<String, String> r : roots.entrySet()) {
        if (Pattern.compile("(?<![\\w$.])" + Pattern.quote(r.getKey()) + "\\s*(\\?\\.|\\.|\\[)(?!\\s*(then|catch|finally)\\b)").matcher(e).find()) {
          reads.add(new Read(t, r.getValue()));
        }
      }
      if (holder != null) {
        Matcher h = Pattern.compile("(?<![\\w$.])" + Pattern.quote(holder) + "\\s*\\??\\.\\s*(params|searchParams)\\s*(\\?\\.|\\.|\\[)(?!\\s*(then|catch|finally)\\b)")
          .matcher(e);
        while (h.find()) {
          reads.add(new Read(t, h.group(1)));
        }
      }
    }
  }

  /** {@code const name}, {@code let name}, {@code var name} or {@code name = ...} in the body: not followed. */
  private static boolean redeclared(List<Token> tokens, String name) {
    for (int i = 0; i < tokens.size(); i++) {
      Token t = tokens.get(i);
      if (!t.isIdent(name)) {
        continue;
      }
      Token prev = i > 0 ? tokens.get(i - 1) : null;
      Token next = i + 1 < tokens.size() ? tokens.get(i + 1) : null;
      Token value = i + 2 < tokens.size() ? tokens.get(i + 2) : null;
      // name = ..., but not a JSX attribute name={...} / name="..."
      boolean assigned = next != null && next.is("=") && (prev == null || !prev.is(".") && !prev.is("?.")) && value != null
        && !value.is("{") && value.kind() != TokenKind.STRING;
      if (prev != null && (prev.isIdent("const") || prev.isIdent("let") || prev.isIdent("var")) || assigned) {
        return true;
      }
    }
    return false;
  }

  /** The token is inside a nested function with a parameter of this name. */
  private static boolean shadowed(List<JsTrees.Fn> nested, String name, Token at) {
    for (JsTrees.Fn f : nested) {
      boolean declares = f.param() != null ? f.param().isIdent(name) : Nodes.tokens(f.params()).stream().anyMatch(t -> t.isIdent(name));
      if (declares && inside(f, at)) {
        return true;
      }
    }
    return false;
  }

  private static boolean shadowedAny(List<JsTrees.Fn> nested, Map<String, String> roots, String holder, Token at) {
    return roots.keySet().stream().anyMatch(n -> shadowed(nested, n, at)) || holder != null && shadowed(nested, holder, at);
  }

  private static boolean inside(JsTrees.Fn f, Token at) {
    Token from = f.container().children().get(f.start()).first();
    Token to = f.container().children().get(f.end() - 1).last();
    return before(from, at) && before(at, to);
  }

  private static boolean before(Token a, Token b) {
    return a.line() < b.line() || a.line() == b.line() && a.column() <= b.column();
  }

  private static boolean ident(Node n) {
    Token t = Nodes.leaf(n);
    return t != null && t.kind() == TokenKind.IDENT;
  }
}
