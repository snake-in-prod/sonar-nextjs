package io.github.snakeinprod.nextjs.model;

import io.github.snakeinprod.nextjs.js.JsTrees;
import io.github.snakeinprod.nextjs.parse.Node;
import io.github.snakeinprod.nextjs.parse.Nodes;
import io.github.snakeinprod.nextjs.parse.Token;
import io.github.snakeinprod.nextjs.parse.TokenKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * A builder chain of a Server Action library: {@code actionClient.schema(s).action(async ({ parsedInput }) => ...)}
 * (next-safe-action), {@code createServerAction().input(s).handler(...)} (zsa), {@code baseClient.use(mw)}.
 * {@code client} is the name the chain starts from ({@code actionClient}), null when it starts with a call
 * ({@code createSafeActionClient(...)}, then the first step); {@code steps} are the calls in order.
 */
public record ActionBuilder(Token client, List<Step> steps) {

  /** Calls that end a chain into a Server Action. */
  static final Set<String> TERMINAL = Set.of("action", "stateAction", "handler");
  private static final Set<String> SCHEMA = Set.of("schema", "inputSchema", "input");

  /** One call of the chain: {@code .use(...)}, or the factory call it starts with. */
  public record Step(Token name, Node.Group args) {

    /** Function literals passed right in the arguments ({@code .use(async ({ next }) => ...)}). */
    public List<JsTrees.Fn> functions() {
      return JsTrees.functions(args).stream().filter(f -> f.container() == args).toList();
    }
  }

  /** The chain validates its input with a schema: a {@code .schema}, {@code .inputSchema} or {@code .input} step. */
  public boolean validated() {
    return steps.stream().anyMatch(s -> SCHEMA.contains(s.name().text()));
  }

  /** The handler of a Server Action: the function given to the last step when that is action, stateAction or handler. */
  JsTrees.Fn handler() {
    Step last = steps.get(steps.size() - 1);
    List<JsTrees.Fn> fns = last.functions();
    return TERMINAL.contains(last.name().text()) && !fns.isEmpty() ? fns.get(0) : null;
  }

  /** The builder a top-level {@code const|let|var name = ...} holds, or null. */
  static ActionBuilder declared(Node.Group root, String name) {
    List<Node> kids = root.children();
    for (int i = 1; i + 1 < kids.size(); i++) {
      if (Nodes.isIdent(kids.get(i), name) && (Nodes.isIdent(kids.get(i - 1), "const") || Nodes.isIdent(kids.get(i - 1), "let")
        || Nodes.isIdent(kids.get(i - 1), "var"))) {
        int j = i + 1;
        // a type annotation: const x: Client = ...
        while (j < kids.size() && j < i + 12 && !Nodes.isLeaf(kids.get(j), "=") && !Nodes.isLeaf(kids.get(j), ";")) {
          j++;
        }
        return j < kids.size() && Nodes.isLeaf(kids.get(j), "=") ? of(JsTrees.initializer(kids, j + 1)) : null;
      }
    }
    return null;
  }

  /** The chain these nodes are: a name or a call, then {@code .name(...)} calls (type arguments allowed); null if not one. */
  static ActionBuilder of(List<Node> init) {
    Token base = init.isEmpty() ? null : Nodes.leaf(init.get(0));
    if (base == null || base.kind() != TokenKind.IDENT) {
      return null;
    }
    List<Step> steps = new ArrayList<>();
    int i = 1;
    Token client = base;
    if (i < init.size() && init.get(i) instanceof Node.Group g && g.is("(")) {
      steps.add(new Step(base, g));
      client = null;
      i++;
    }
    while (i + 2 < init.size() && (Nodes.isLeaf(init.get(i), ".") || Nodes.isLeaf(init.get(i), "?."))) {
      Token name = Nodes.leaf(init.get(i + 1));
      int j = typeArguments(init, i + 2);
      if (name == null || name.kind() != TokenKind.IDENT || j >= init.size() || !(init.get(j) instanceof Node.Group g && g.is("("))) {
        return null;
      }
      steps.add(new Step(name, g));
      i = j + 1;
    }
    return i == init.size() && !steps.isEmpty() ? new ActionBuilder(client, List.copyOf(steps)) : null;
  }

  /** Skips {@code <...>} type arguments starting at i; returns the index after them, or i when there are none. */
  private static int typeArguments(List<Node> init, int i) {
    if (i >= init.size() || !Nodes.isLeaf(init.get(i), "<")) {
      return i;
    }
    int depth = 0;
    for (int j = i; j < init.size(); j++) {
      Token t = Nodes.leaf(init.get(j));
      if (t != null && t.kind() == TokenKind.OP && t.text().matches("[<>]+")) {
        depth += t.text().chars().map(c -> c == '<' ? 1 : c == '>' ? -1 : 0).sum();
        if (depth <= 0) {
          return j + 1;
        }
      }
    }
    return i;
  }
}
