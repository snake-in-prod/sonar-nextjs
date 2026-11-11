package io.github.snakeinprod.nextjs.rules;

import io.github.snakeinprod.nextjs.js.JsTrees;
import io.github.snakeinprod.nextjs.model.Import;
import io.github.snakeinprod.nextjs.parse.Node;
import io.github.snakeinprod.nextjs.parse.Nodes;
import io.github.snakeinprod.nextjs.parse.Token;
import io.github.snakeinprod.nextjs.parse.TokenKind;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A JSX element of the default export of next/image with {@code fill} (or {@code fill={true}}) and no {@code sizes}.
 * Silent when the props cannot all be seen: a spread ({@code {...props}}), a computed {@code fill={x}}, or an attribute
 * whose value is a JSX element.
 */
public class ImageFillWithoutSizes implements NextRule {

  @Override
  public void check(RuleContext ctx) {
    Set<String> names = new HashSet<>();
    for (Import i : ctx.module().imports()) {
      if (i.spec().equals("next/image") && !i.typeOnly()) {
        i.names().forEach((local, imported) -> {
          if (imported.equals("default")) {
            names.add(local);
          }
        });
      }
    }
    if (names.isEmpty()) {
      return;
    }
    Nodes.groups(ctx.file().root(), g -> {
      List<Node> kids = g.children();
      for (int i = 0; i + 1 < kids.size(); i++) {
        Token name = Nodes.leaf(kids.get(i + 1));
        if (Nodes.isLeaf(kids.get(i), "<") && name != null && name.kind() == TokenKind.IDENT && names.contains(name.text())
          && fillWithoutSizes(kids, i + 2)) {
          ctx.report(name, "Set \"sizes\" on this fill Image: without it the browser assumes 100vw and downloads an oversized image.");
        }
      }
    });
  }

  /** Reads the attributes from {@code k} to the end of the tag. */
  private static boolean fillWithoutSizes(List<Node> kids, int k) {
    boolean fill = false;
    while (k < kids.size()) {
      Node n = kids.get(k);
      if (Nodes.isLeaf(n, "/>") || Nodes.isLeaf(n, ">")) {
        return fill;
      }
      Token attr = Nodes.leaf(n);
      if (attr == null || attr.kind() != TokenKind.IDENT) {
        // a spread {...props}, a type argument or anything else this scan does not follow
        return false;
      }
      Node value = null;
      if (k + 2 < kids.size() && Nodes.isLeaf(kids.get(k + 1), "=")) {
        value = kids.get(k + 2);
        if (Nodes.isLeaf(value, "<")) {
          return false;
        }
        k += 3;
      } else {
        k++;
      }
      // an image served as it is (unoptimized, or an SVG, which next/image never resizes) has no srcset to size
      Token literal = value == null ? null : Nodes.leaf(value);
      if (attr.is("sizes") || attr.is("unoptimized")
        || attr.is("src") && literal != null && literal.kind() == TokenKind.STRING && JsTrees.unquote(literal.text()).toLowerCase(Locale.ROOT).endsWith(".svg")) {
        return false;
      }
      if (attr.is("fill")) {
        if (value == null || value instanceof Node.Group g && g.is("{") && g.children().size() == 1
          && Nodes.isIdent(g.children().get(0), "true")) {
          fill = true;
        } else {
          return false;
        }
      }
    }
    return false;
  }
}
