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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A JSX element of the default export of next/image with {@code fill} (or {@code fill={true}}) and no {@code sizes}.
 * Silent when the props cannot all be seen: a spread ({@code {...props}}), a computed {@code fill={x}}, or an attribute
 * whose value is a JSX element. Silent too when the image is served as it is (unoptimized, the app's next.config sets
 * {@code images.unoptimized}, or an SVG source: a literal, an import of a .svg file, or a constant holding one), and
 * when the image is full width: walking out through the enclosing JSX elements, one with a viewport height or width
 * (h-screen, 100vh, w-screen) comes before any width limit (w-64, max-w-md, size-8, grid-cols-2, col-span-2).
 */
public class ImageFillWithoutSizes implements NextRule {

  /** h-screen, min-h-screen, w-screen, h-dvh, h-[100vh], or a style value "100vh"; not max-h-[70vh]. */
  private static final Pattern VIEWPORT = Pattern.compile("(?:min-)?[hw]-(?:screen|[dsl]v[hw]|\\[[^\\]]*\\d[dsl]?v[hw][^\\]]*\\])|\\d+[dsl]?v[hw]");
  private static final Set<String> FULL = Set.of("w-full", "w-screen", "w-auto", "w-[100%]", "w-[100vw]", "w-dvw", "w-svw", "w-lvw",
    "max-w-full", "max-w-none", "size-full", "basis-full", "col-span-full", "grid-cols-1", "grid-cols-none");

  private final Map<String, Boolean> unoptimizedApps = new HashMap<>();

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
    if (names.isEmpty() || unoptimizedApp(ctx.model(), ctx.module())) {
      return;
    }
    walk(ctx, names, ctx.file().root(), new ArrayList<>());
  }

  /** {@code path} holds the enclosing groups and the index of the child leading to {@code g}. */
  private static void walk(RuleContext ctx, Set<String> names, Node.Group g, List<Object[]> path) {
    List<Node> kids = g.children();
    for (int i = 0; i < kids.size(); i++) {
      if (kids.get(i) instanceof Node.Group child) {
        path.add(new Object[] {g, i});
        walk(ctx, names, child, path);
        path.remove(path.size() - 1);
        continue;
      }
      Token name = i + 1 < kids.size() ? Nodes.leaf(kids.get(i + 1)) : null;
      if (Nodes.isLeaf(kids.get(i), "<") && name != null && name.kind() == TokenKind.IDENT && names.contains(name.text())
        && fillWithoutSizes(ctx, kids, i + 2) && !fullWidth(path, g, i)) {
        ctx.report(name, "Set \"sizes\" on this fill Image: without it the browser assumes 100vw and downloads an oversized image.");
      }
    }
  }

  /** Reads the attributes from {@code k} to the end of the tag. */
  private static boolean fillWithoutSizes(RuleContext ctx, List<Node> kids, int k) {
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
      if (attr.is("sizes") || attr.is("unoptimized") || attr.is("src") && value != null && svg(ctx, value)) {
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

  /** A src that is an SVG: "x.svg", {"x.svg"}, {logo} imported from a .svg file, or {a} / {A.b} holding an SVG path. */
  private static boolean svg(RuleContext ctx, Node value) {
    List<Node> v = value instanceof Node.Group g && g.is("{") ? g.children() : List.of(value);
    if (v.size() == 1 && Nodes.leaf(v.get(0)) instanceof Token t && t.kind() == TokenKind.STRING) {
      return svgPath(t);
    }
    boolean name = v.size() == 1 || v.size() == 3 && Nodes.isLeaf(v.get(1), ".");
    if (!name || !(Nodes.leaf(v.get(0)) instanceof Token base) || base.kind() != TokenKind.IDENT
      || !(Nodes.leaf(v.get(v.size() - 1)) instanceof Token key) || key.kind() != TokenKind.IDENT) {
      return false;
    }
    Module holder = ctx.module();
    for (Import i : ctx.module().imports()) {
      if (i.names().containsKey(base.text())) {
        if (i.spec().toLowerCase(Locale.ROOT).endsWith(".svg")) {
          return true;
        }
        holder = ctx.model().target(i);
      }
    }
    // ponytail: matches the first "const a = '...svg'" (or "b: '...svg'" for A.b) of the holding module, scopes ignored
    String bind = v.size() == 1 ? "=" : ":";
    if (holder != null) {
      List<Token> t = holder.file().tokens();
      for (int i = 1; i + 2 < t.size(); i++) {
        if (t.get(i).isIdent(key.text()) && t.get(i + 1).is(bind) && t.get(i + 2).kind() == TokenKind.STRING
          && (v.size() == 3 || t.get(i - 1).isIdent("const") || t.get(i - 1).isIdent("let"))) {
          return svgPath(t.get(i + 2));
        }
      }
    }
    return false;
  }

  private static boolean svgPath(Token string) {
    return JsTrees.unquote(string.text()).toLowerCase(Locale.ROOT).endsWith(".svg");
  }

  /** The app's next.config sets images.unoptimized to anything but false. */
  private boolean unoptimizedApp(NextModel model, Module m) {
    NextModel.NextApp app = model.appOf(m.path());
    if (app == null) {
      return false;
    }
    return unoptimizedApps.computeIfAbsent(app.dir(), dir -> {
      for (String ext : List.of("js", "mjs", "cjs", "ts", "mts")) {
        Module c = model.module(dir + "next.config." + ext);
        List<Token> t = c == null ? List.of() : c.file().tokens();
        for (int i = 0; i + 2 < t.size(); i++) {
          if (t.get(i).isIdent("unoptimized") && t.get(i + 1).is(":") && !t.get(i + 2).isIdent("false")) {
            return true;
          }
        }
      }
      return false;
    });
  }

  /**
   * Walking out from the image through the enclosing JSX elements, one with a viewport height or width comes before
   * any element whose classes limit the width.
   */
  private static boolean fullWidth(List<Object[]> path, Node.Group g, int index) {
    List<Object[]> levels = new ArrayList<>(path);
    levels.add(new Object[] {g, index});
    for (int l = levels.size() - 1; l >= 0; l--) {
      List<Node> kids = ((Node.Group) levels.get(l)[0]).children();
      List<Integer> open = openTags(kids, (Integer) levels.get(l)[1]);
      for (int o = open.size() - 1; o >= 0; o--) {
        String strings = tagStrings(kids, open.get(o));
        if (limitsWidth(strings)) {
          return false;
        }
        if (viewport(strings)) {
          return true;
        }
      }
    }
    return false;
  }

  /** Start indexes of the JSX elements opened and not closed before {@code end}, outermost first. */
  private static List<Integer> openTags(List<Node> kids, int end) {
    List<Integer> open = new ArrayList<>();
    for (int k = 0; k < end; k++) {
      if (Nodes.isLeaf(kids.get(k), "</")) {
        if (!open.isEmpty()) {
          open.remove(open.size() - 1);
        }
      } else if (Nodes.isLeaf(kids.get(k), "<")) {
        int j = k + 1;
        while (j < end && !Nodes.isLeaf(kids.get(j), ">") && !Nodes.isLeaf(kids.get(j), "/>") && !Nodes.isLeaf(kids.get(j), "<")) {
          j++;
        }
        if (j < end && Nodes.isLeaf(kids.get(j), ">")) {
          open.add(k);
          k = j;
        } else if (j < end && Nodes.isLeaf(kids.get(j), "/>")) {
          k = j;
        }
      }
    }
    return open;
  }

  /** The string literals of the opening tag starting at {@code start}, attribute expressions included. */
  private static String tagStrings(List<Node> kids, int start) {
    StringBuilder out = new StringBuilder();
    for (int k = start + 1; k < kids.size() && !Nodes.isLeaf(kids.get(k), ">"); k++) {
      for (Token t : Nodes.tokens(kids.get(k))) {
        if (t.kind() == TokenKind.STRING) {
          out.append(' ').append(JsTrees.unquote(t.text()));
        }
      }
    }
    return out.toString();
  }

  private static boolean viewport(String strings) {
    for (String c : strings.trim().split("\\s+")) {
      if (VIEWPORT.matcher(c.substring(c.lastIndexOf(':') + 1).replace("!", "")).matches()) {
        return true;
      }
    }
    return false;
  }

  /** A Tailwind class fixing or capping the width: w-64, md:w-1/2, max-w-md, size-8, basis-1/3, col-span-2, grid-cols-3. */
  private static boolean limitsWidth(String strings) {
    for (String c : strings.trim().split("\\s+")) {
      String u = c.substring(c.lastIndexOf(':') + 1).replace("!", "");
      if (!FULL.contains(u) && !u.startsWith("max-w-screen")
        && (u.startsWith("w-") || u.startsWith("max-w-") || u.startsWith("size-") || u.startsWith("basis-")
          || u.startsWith("col-span-") || u.startsWith("grid-cols-") || u.startsWith("columns-") || u.equals("container"))) {
        return true;
      }
    }
    return false;
  }
}
