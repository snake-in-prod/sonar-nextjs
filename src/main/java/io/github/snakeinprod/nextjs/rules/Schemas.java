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
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reads zod object schemas ({@code z.object({ next: z.string().optional() })}, zod-form-data's {@code zfd.formData},
 * {@code Base.extend({...})}, {@code .merge(Other)}) declared in the module or one import away (re-exports followed):
 * whether a field of what the schema parses can be any string the client sent. A field is such a string when it is
 * {@code z.string()}, {@code z.coerce.string()}, {@code z.url()} or {@code zfd.text()} with only steps that keep any
 * string ({@code .optional()}, {@code .default()}, {@code .min()}, {@code .trim()}, {@code .url()}, ...) and no
 * {@code refine}, {@code superRefine}, {@code transform} or {@code pipe} calling a check ({@code startsWith},
 * {@code isSafePath}, {@code origin}, {@code hostname}). {@code .startsWith()}, {@code .regex()}, enums, literals,
 * unions, other types, helpers and fields the schema does not declare (zod strips them) are not; nor is a field an
 * object-level refine of the schema names.
 */
final class Schemas {

  private static final Set<String> KEEP = Set.of("optional", "nullable", "nullish", "default", "prefault", "catch", "trim", "min",
    "max", "length", "nonempty", "describe", "meta", "toLowerCase", "toUpperCase", "normalize", "url", "brand", "readonly", "or");
  private static final Set<String> REFINES = Set.of("refine", "superRefine", "transform", "check", "pipe");
  private static final Set<String> OBJECTS = Set.of("object", "strictObject", "looseObject", "formData", "extend", "safeExtend");
  private static final Pattern HOST = Pattern.compile("(?i).*host.*");
  private static final int DEPTH = 6;

  private final NextModel model;

  Schemas(NextModel model) {
    this.model = model;
  }

  /**
   * TRUE: the field at {@code path} of what {@code schema} (nodes in module m) parses is any string; FALSE: it is not;
   * null: the schema is not found.
   */
  Boolean free(Module m, List<Node> schema, List<String> path) {
    if (path.isEmpty() || resolveRoot(m, ValueFlow.Direct.trim(schema)) == null) {
      return null;
    }
    return field(m, ValueFlow.Direct.trim(schema), path, 0);
  }

  /** The schema expression can be read: a z/zfd call chain, or a name declared in the module or one import away. */
  private Object resolveRoot(Module m, List<Node> e) {
    Chain c = Chain.of(e);
    if (c == null) {
      return null;
    }
    if (c.root.equals("z") || c.root.equals("zfd")) {
      return c;
    }
    return c.rootCall ? null : declared(m, c.root);
  }

  private boolean field(Module m, List<Node> e, List<String> path, int depth) {
    if (depth > DEPTH) {
      return false;
    }
    if (path.isEmpty()) {
      return string(m, e, depth);
    }
    Chain c = Chain.of(e);
    if (c == null || c.rootCall) {
      return false;
    }
    String name = path.get(0);
    // an object-level check naming the field: .refine((d) => d.next.startsWith('/'))
    for (Step s : c.steps) {
      if (REFINES.contains(s.name) && s.args != null && Nodes.tokens(s.args).stream().anyMatch(t -> t.isIdent(name))) {
        return false;
      }
    }
    boolean zod = c.root.equals("z") || c.root.equals("zfd");
    // z.union([a, b]), z.discriminatedUnion('kind', [a, b]), a.or(b): the client picks the member
    for (List<Node> member : members(c, zod)) {
      if (field(m, member, path, depth + 1)) {
        return true;
      }
    }
    for (int i = c.steps.size() - 1; i >= 0; i--) {
      Step s = c.steps.get(i);
      if (s.args == null) {
        continue;
      }
      List<List<Node>> items = s.args.items();
      if (s.name.equals("omit") && !items.isEmpty() && Nodes.tokens(items.get(0)).stream().anyMatch(t -> t.isIdent(name))) {
        return false;
      }
      if (OBJECTS.contains(s.name) && !items.isEmpty() && items.get(0).size() == 1 && items.get(0).get(0) instanceof Node.Group g
        && g.is("{")) {
        List<Node> value = property(g, name);
        if (value != null) {
          return field(m, value, path.subList(1, path.size()), depth + 1);
        }
      }
      if (s.name.equals("merge") && items.size() == 1 && field(m, items.get(0), path, depth + 1)) {
        return true;
      }
      if (zod && i == 0) {
        return false;
      }
    }
    if (zod) {
      return false;
    }
    Decl d = declared(m, c.root);
    return d != null && field(d.module, d.init, path, depth + 1);
  }

  /** Any string: z.string(), z.coerce.string(), z.url(), zfd.text() or a schema name holding one, then steps that keep it. */
  private boolean string(Module m, List<Node> e, int depth) {
    Chain c = Chain.of(e);
    if (c == null || c.rootCall || depth > DEPTH) {
      return false;
    }
    for (List<Node> member : members(c, c.root.equals("z"))) {
      if (string(m, member, depth + 1)) {
        return true;
      }
    }
    int from;
    if (c.root.equals("z")) {
      int k = !c.steps.isEmpty() && c.steps.get(0).name.equals("coerce") && c.steps.get(0).args == null ? 1 : 0;
      String base = k < c.steps.size() && c.steps.get(k).args != null ? c.steps.get(k).name : "";
      if (!(base.equals("string") || k == 0 && (base.equals("url") || base.equals("httpUrl")))) {
        return false;
      }
      from = k + 1;
    } else if (c.root.equals("zfd")) {
      if (c.steps.isEmpty() || !c.steps.get(0).name.equals("text") || c.steps.get(0).args == null) {
        return false;
      }
      List<List<Node>> items = c.steps.get(0).args.items();
      if (!items.isEmpty() && !string(m, items.get(0), depth + 1)) {
        return false;
      }
      from = 1;
    } else {
      Decl d = declared(m, c.root);
      if (d == null || !string(d.module, d.init, depth + 1)) {
        return false;
      }
      from = 0;
    }
    for (Step s : c.steps.subList(from, c.steps.size())) {
      if (REFINES.contains(s.name)) {
        if (s.args == null || Nodes.tokens(s.args).stream().anyMatch(t -> t.kind() == TokenKind.IDENT
          && (OpenRedirect.CHECK.matcher(t.text()).matches() || HOST.matcher(t.text()).matches()))) {
          return false;
        }
      } else if (!KEEP.contains(s.name)) {
        return false;
      }
    }
    return true;
  }

  /** The members of a union: the list of {@code z.union([...])} or {@code z.discriminatedUnion(key, [...])}. */
  private static List<List<Node>> members(Chain c, boolean zod) {
    List<List<Node>> out = new ArrayList<>();
    if (zod && !c.steps.isEmpty() && c.steps.get(0).args != null
      && (c.steps.get(0).name.equals("union") || c.steps.get(0).name.equals("discriminatedUnion"))) {
      List<List<Node>> items = c.steps.get(0).args.items();
      List<Node> list = items.isEmpty() ? List.of() : items.get(items.size() - 1);
      if (list.size() == 1 && list.get(0) instanceof Node.Group g && g.is("[")) {
        out.addAll(g.items());
      }
    }
    return out;
  }

  /** The expression is a zod schema: a z or zfd chain, or a name declared as one in the module or one import away. */
  boolean isSchema(Module m, List<Node> e) {
    return isSchema(m, ValueFlow.Direct.trim(e), 0);
  }

  private boolean isSchema(Module m, List<Node> e, int depth) {
    Chain c = Chain.of(e);
    if (c == null || c.rootCall || depth > DEPTH) {
      return false;
    }
    if (c.root.equals("z") || c.root.equals("zfd")) {
      return true;
    }
    Decl d = declared(m, c.root);
    return d != null && isSchema(d.module, d.init, depth + 1);
  }

  /** The value of a property of an object literal: {@code name: value}, or {@code name} alone. */
  static List<Node> property(Node.Group g, String name) {
    for (List<Node> item : g.items()) {
      Token key = item.isEmpty() ? null : Nodes.leaf(item.get(0));
      if (key == null || !(key.kind() == TokenKind.IDENT || key.kind() == TokenKind.STRING) || !JsTrees.unquote(key.text()).equals(name)) {
        continue;
      }
      if (item.size() > 2 && Nodes.isLeaf(item.get(1), ":")) {
        return item.subList(2, item.size());
      }
      if (item.size() == 1 && key.kind() == TokenKind.IDENT) {
        return item;
      }
    }
    return null;
  }

  // ---------------------------------------------------------------- declarations

  private record Decl(Module module, List<Node> init) {
  }

  /** The initializer of {@code const name = ...} in the module, or of the export an import of that name points to. */
  private Decl declared(Module m, String name) {
    List<Node> init = initializer(m.file().root(), name);
    if (init != null) {
      return new Decl(m, init);
    }
    for (Import i : m.imports()) {
      String imported = i.typeOnly() || i.reExport() ? null : i.names().get(name);
      Module t = imported == null ? null : model.target(i);
      NextModel.Export x = t == null ? null : model.exportOf(t, imported);
      List<Node> found = x == null ? null : initializer(x.module().file().root(), x.local());
      if (found != null) {
        return new Decl(x.module(), found);
      }
    }
    return null;
  }

  /** The initializer of the first {@code const|let|var name [: Type] = ...} of the file, top level first. */
  private static List<Node> initializer(Node.Group root, String name) {
    List<Node> top = initializerIn(root, name);
    if (top != null) {
      return top;
    }
    List<List<Node>> out = new ArrayList<>();
    Nodes.groups(root, g -> {
      if (out.isEmpty() && g != root) {
        List<Node> found = initializerIn(g, name);
        if (found != null) {
          out.add(found);
        }
      }
    });
    return out.isEmpty() ? null : out.get(0);
  }

  private static List<Node> initializerIn(Node.Group g, String name) {
    List<Node> kids = g.children();
    for (int i = 1; i + 1 < kids.size(); i++) {
      if (Nodes.isIdent(kids.get(i), name) && (Nodes.isIdent(kids.get(i - 1), "const") || Nodes.isIdent(kids.get(i - 1), "let")
        || Nodes.isIdent(kids.get(i - 1), "var"))) {
        int j = i + 1;
        while (j < kids.size() && j < i + 12 && !Nodes.isLeaf(kids.get(j), "=") && !Nodes.isLeaf(kids.get(j), ";")) {
          j++;
        }
        return j < kids.size() && Nodes.isLeaf(kids.get(j), "=") ? JsTrees.initializer(kids, j + 1) : null;
      }
    }
    return null;
  }

  // ---------------------------------------------------------------- chains

  /** {@code .name} or {@code .name(args)} (type arguments allowed). */
  private record Step(String name, Node.Group args) {
  }

  /** A name, then steps: {@code z.string().min(1)}; {@code rootCall} when the name itself is called ({@code helper()}). */
  private record Chain(String root, boolean rootCall, List<Step> steps) {

    static Chain of(List<Node> e) {
      Token root = e.isEmpty() ? null : Nodes.leaf(e.get(0));
      if (root == null || root.kind() != TokenKind.IDENT) {
        return null;
      }
      int i = JsTrees.typeArguments(e, 1);
      boolean call = i < e.size() && e.get(i) instanceof Node.Group g && g.is("(");
      i = call ? i + 1 : 1;
      List<Step> steps = new ArrayList<>();
      while (i + 1 < e.size() && (Nodes.isLeaf(e.get(i), ".") || Nodes.isLeaf(e.get(i), "?."))) {
        Token name = Nodes.leaf(e.get(i + 1));
        if (name == null || name.kind() != TokenKind.IDENT) {
          return null;
        }
        int j = JsTrees.typeArguments(e, i + 2);
        if (j < e.size() && e.get(j) instanceof Node.Group g && g.is("(")) {
          steps.add(new Step(name.text(), g));
          i = j + 1;
        } else {
          steps.add(new Step(name.text(), null));
          i += 2;
        }
      }
      return i == e.size() ? new Chain(root.text(), call, steps) : null;
    }
  }
}
