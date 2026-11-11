package io.github.snakeinprod.nextjs.parse;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Finds {@link Chain}s in a token tree. */
public final class Chains {

  private Chains() {
  }

  /** Words that never start a chain in any language ({@code if (x)} is not a call of "if"). */
  private static final Set<String> COMMON = Set.of("if", "else", "while", "for", "switch", "catch", "return", "await", "yield",
    "new", "class", "try", "finally", "throw", "import", "case", "do", "async", "const", "var", "final", "static", "break",
    "continue", "super", "in", "is", "as", "default", "extends");

  private static final Set<String> JS = union(COMMON, Set.of("typeof", "function", "void", "delete", "instanceof", "let",
    "export", "implements", "of", "from"));
  /** Keywords that never start a chain for a language key ("js", "ts"). */
  public static Set<String> keywords(String language) {
    return JS;
  }

  private static Set<String> union(Set<String> a, Set<String> b) {
    java.util.Set<String> out = new java.util.HashSet<>(a);
    out.addAll(b);
    return Set.copyOf(out);
  }

  private static final Set<String> ACCESSORS = Set.of(".", "?.", "..", "?..", "!.");

  /** Every chain in the tree, nested ones (inside arguments, lambdas, blocks) included, in source order of their start. */
  public static List<Chain> in(Node.Group root) {
    return in(root, COMMON);
  }

  /** As {@link #in(Node.Group)}, with the keywords of one language. */
  public static List<Chain> in(Node.Group root, Set<String> keywords) {
    List<Chain> out = new ArrayList<>();
    collect(root, out, keywords);
    out.sort((a, b) -> a.first().line() != b.first().line() ? Integer.compare(a.first().line(), b.first().line())
      : Integer.compare(a.first().column(), b.first().column()));
    return out;
  }

  /** The chains that start directly in this group's children (not nested). */
  public static List<Chain> direct(Node.Group group) {
    return direct(group, COMMON);
  }

  public static List<Chain> direct(Node.Group group, Set<String> keywords) {
    List<Chain> out = new ArrayList<>();
    List<Node> kids = group.children();
    int i = 0;
    while (i < kids.size()) {
      int end = chainEnd(group, i, keywords);
      if (end > i) {
        out.add(build(group, i, end));
        i = end;
      } else {
        i++;
      }
    }
    return out;
  }

  private static void collect(Node.Group g, List<Chain> out, Set<String> keywords) {
    out.addAll(direct(g, keywords));
    for (Node c : g.children()) {
      if (c instanceof Node.Group sub) {
        collect(sub, out, keywords);
      }
    }
  }

  /** End index (exclusive) of a chain starting at {@code i}, or {@code i} when none starts there. */
  private static int chainEnd(Node.Group g, int i, Set<String> keywords) {
    List<Node> kids = g.children();
    Token t = Nodes.leaf(kids.get(i));
    if (t == null || t.kind() != TokenKind.IDENT || keywords.contains(t.text())) {
      return i;
    }
    if (i > 0 && kids.get(i - 1) instanceof Node.Leaf prev && ACCESSORS.contains(prev.token().text())) {
      // the middle of a chain that started before something this scanner could not follow
      return i;
    }
    int j = i + 1;
    while (j < kids.size()) {
      Node n = kids.get(j);
      if (n instanceof Node.Group sub && (sub.is("(") || sub.is("["))) {
        j++;
      } else if (n instanceof Node.Leaf l && l.token().is("!") && j + 1 < kids.size() && Nodes.leaf(kids.get(j + 1)) != null
        && ACCESSORS.contains(Nodes.leaf(kids.get(j + 1)).text())) {
        // TypeScript non-null assertion: x!.y
        j++;
      } else if (n instanceof Node.Leaf l && ACCESSORS.contains(l.token().text()) && j + 1 < kids.size()
        && Nodes.leaf(kids.get(j + 1)) != null && Nodes.leaf(kids.get(j + 1)).kind() == TokenKind.IDENT) {
        j += 2;
      } else {
        break;
      }
    }
    return j;
  }

  private static Chain build(Node.Group g, int from, int to) {
    List<Chain.Segment> segs = new ArrayList<>();
    List<Node> kids = g.children();
    String accessor = "";
    for (int k = from; k < to; k++) {
      Node n = kids.get(k);
      if (n instanceof Node.Group sub) {
        segs.add(sub.is("(") ? new Chain.Call(sub) : new Chain.Index(sub));
      } else {
        Token t = ((Node.Leaf) n).token();
        if (t.kind() == TokenKind.IDENT) {
          segs.add(new Chain.Name(t, accessor));
          accessor = "";
        } else if (!t.is("!")) {
          accessor = t.text();
        }
      }
    }
    return new Chain(List.copyOf(segs), g, from, to);
  }
}
