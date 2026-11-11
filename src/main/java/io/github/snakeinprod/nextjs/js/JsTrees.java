package io.github.snakeinprod.nextjs.js;

import io.github.snakeinprod.nextjs.parse.Chain;
import io.github.snakeinprod.nextjs.parse.Chains;
import io.github.snakeinprod.nextjs.parse.Node;
import io.github.snakeinprod.nextjs.parse.Nodes;
import io.github.snakeinprod.nextjs.parse.Token;
import io.github.snakeinprod.nextjs.parse.TokenKind;
import java.util.ArrayList;
import java.util.List;

/** Helpers over JS / TS token trees for the model and the rules. */
public final class JsTrees {

  private JsTrees() {
  }

  /**
   * A function literal: an arrow function or a function expression, starting at {@code start} in {@code container}
   * and ending before {@code end}. {@code body} is the "{}" group, or for an expression body a group with null brackets
   * holding the nodes after "=>" ({@code expression} true). {@code params} is the "()" group, or null for a single
   * parameter without parentheses ({@code param}).
   */
  public record Fn(Node.Group container, int start, int end, boolean async, Node.Group params, Token param, Node.Group body,
    boolean expression) {

    /** Parameter names that are plain identifiers (typed or not), in order; destructured parameters are left out. */
    public List<Token> simpleParams() {
      if (param != null) {
        return List.of(param);
      }
      List<Token> out = new ArrayList<>();
      for (List<Node> item : params.items()) {
        Token t = Nodes.leaf(item.get(0));
        if (t != null && t.kind() == TokenKind.IDENT && (item.size() == 1 || Nodes.isLeaf(item.get(1), ":"))) {
          out.add(t);
        }
      }
      return out;
    }

    public int paramCount() {
      return param != null ? 1 : params.items().size();
    }
  }

  /** Every function literal in the tree, nested ones included. */
  public static List<Fn> functions(Node.Group root) {
    List<Fn> out = new ArrayList<>();
    Nodes.groups(root, g -> {
      for (int i = 0; i < g.children().size(); i++) {
        Fn f = functionAt(g, i);
        if (f != null) {
          out.add(f);
          if (f.async()) {
            i++;
          }
        }
      }
    });
    return out;
  }

  /** The function literal starting at child {@code i} of {@code g}, or null. */
  public static Fn functionAt(Node.Group g, int i) {
    List<Node> kids = g.children();
    int s = i;
    boolean async = false;
    if (Nodes.isIdent(kids.get(i), "async") && i + 1 < kids.size() && !Nodes.isLeaf(kids.get(i + 1), "=>")) {
      async = true;
      i++;
    }
    Node first = kids.get(i);
    if (Nodes.isIdent(first, "function")) {
      int j = i + 1;
      if (j < kids.size() && Nodes.isLeaf(kids.get(j), "*")) {
        j++;
      }
      if (j < kids.size() && Nodes.leaf(kids.get(j)) != null && Nodes.leaf(kids.get(j)).kind() == TokenKind.IDENT) {
        j++;
      }
      if (j >= kids.size() || !(kids.get(j) instanceof Node.Group params) || !params.is("(")) {
        return null;
      }
      j++;
      // TypeScript return type
      while (j < kids.size() && kids.get(j) instanceof Node.Leaf l && !l.token().is(",") && !l.token().is(";")) {
        j++;
      }
      if (j < kids.size() && kids.get(j) instanceof Node.Group body && body.is("{")) {
        return new Fn(g, s, j + 1, async, params, null, body, false);
      }
      return null;
    }
    int arrow;
    Node.Group params = null;
    Token param = null;
    if (first instanceof Node.Group p && p.is("(")) {
      arrow = i + 1;
      if (arrow < kids.size() && Nodes.isLeaf(kids.get(arrow), ":")) {
        // TypeScript return type: (x): Promise<void> => ..., (x): Promise<Item[]> => ...
        while (arrow < kids.size() && (kids.get(arrow) instanceof Node.Leaf l && !l.token().is("=>") && !l.token().is(",")
          && !l.token().is(";") || kids.get(arrow) instanceof Node.Group a && a.is("["))) {
          arrow++;
        }
      }
      params = p;
    } else if (first instanceof Node.Leaf l && l.token().kind() == TokenKind.IDENT && !Chains.keywords("js").contains(l.token().text())) {
      arrow = i + 1;
      param = l.token();
    } else {
      return null;
    }
    if (arrow >= kids.size() || !Nodes.isLeaf(kids.get(arrow), "=>")) {
      return null;
    }
    int b = arrow + 1;
    if (b < kids.size() && kids.get(b) instanceof Node.Group body && body.is("{")) {
      return new Fn(g, s, b + 1, async, params, param, body, false);
    }
    // ponytail: an expression body ends at "," or ";" only, so a body relying on ASI runs to the end of its block
    int e = b;
    while (e < kids.size() && !(kids.get(e) instanceof Node.Leaf l && l.token().kind() == TokenKind.OP
      && (l.token().is(",") || l.token().is(";")))) {
      e++;
    }
    if (e == b) {
      return null;
    }
    return new Fn(g, s, e, async, params, param, new Node.Group(null, null, List.copyOf(kids.subList(b, e))), true);
  }

  /** The chain is preceded by "await". */
  public static boolean isAwaited(Chain c) {
    return c.precededBy("await");
  }

  /**
   * The chain is preceded by "return", or it is the whole expression body of an arrow function (in the file tree, or
   * as the whole of an {@link Fn#body()} expression group).
   */
  public static boolean isReturned(Chain c) {
    if (c.precededBy("return")) {
      return true;
    }
    Node after = c.after();
    boolean ends = after == null || Nodes.isLeaf(after, ",") || Nodes.isLeaf(after, ";");
    if (c.precededBy("=>") && ends) {
      return true;
    }
    Node.Group g = c.container();
    return g.open() == null && c.from() == 0 && c.to() == g.children().size();
  }

  /**
   * The name of a function literal: {@code function name()}, {@code const name = () => ...} (a type annotation
   * allowed), {@code name: () => ...}; null when it has none.
   */
  public static Token nameOf(Fn f) {
    List<Node> kids = f.container().children();
    int i = f.start();
    if (Nodes.isIdent(kids.get(i), "async")) {
      i++;
    }
    if (Nodes.isIdent(kids.get(i), "function")) {
      int j = i + 1 < kids.size() && Nodes.isLeaf(kids.get(i + 1), "*") ? i + 2 : i + 1;
      Token t = j < kids.size() ? Nodes.leaf(kids.get(j)) : null;
      return t != null && t.kind() == TokenKind.IDENT ? t : null;
    }
    int k = f.start() - 1;
    if (k < 1 || !(Nodes.isLeaf(kids.get(k), "=") || Nodes.isLeaf(kids.get(k), ":"))) {
      return null;
    }
    if (Nodes.isLeaf(kids.get(k), ":")) {
      Token t = Nodes.leaf(kids.get(k - 1));
      return t != null && (t.kind() == TokenKind.IDENT || t.kind() == TokenKind.STRING) ? t : null;
    }
    // const name: Type<A, B> = ...: back to the token after const / let / var
    for (int j = k - 1; j >= 1 && j >= k - 12; j--) {
      if (Nodes.isLeaf(kids.get(j), ";") || Nodes.isLeaf(kids.get(j), "=")) {
        break;
      }
      if (Nodes.isIdent(kids.get(j - 1), "const") || Nodes.isIdent(kids.get(j - 1), "let") || Nodes.isIdent(kids.get(j - 1), "var")) {
        Token t = Nodes.leaf(kids.get(j));
        return t != null && t.kind() == TokenKind.IDENT ? t : null;
      }
    }
    Token t = Nodes.leaf(kids.get(k - 1));
    return t != null && t.kind() == TokenKind.IDENT ? t : null;
  }

  /** The nodes of an expression from {@code from} to the end of its statement (";", ",", or a new line without an operator). */
  public static List<Node> initializer(List<Node> kids, int from) {
    List<Node> init = new ArrayList<>();
    for (int x = from; x < kids.size(); x++) {
      Node n = kids.get(x);
      if (Nodes.isLeaf(n, ";") || Nodes.isLeaf(n, ",")) {
        break;
      }
      if (!init.isEmpty()) {
        Node prev = init.get(init.size() - 1);
        if (n.first().line() > prev.last().line() && !isOp(prev) && !isOp(n)) {
          break;
        }
      }
      init.add(n);
    }
    return init;
  }

  private static boolean isOp(Node n) {
    Token t = Nodes.leaf(n);
    return t != null && t.kind() == TokenKind.OP;
  }

  /** The text of a string token without its quotes ('a', "a", `a`). */
  public static String unquote(String s) {
    return s.length() >= 2 && "'\"`".indexOf(s.charAt(0)) >= 0 && s.charAt(s.length() - 1) == s.charAt(0)
      ? s.substring(1, s.length() - 1) : s;
  }
}
