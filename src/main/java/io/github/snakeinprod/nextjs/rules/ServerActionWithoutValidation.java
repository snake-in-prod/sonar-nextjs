package io.github.snakeinprod.nextjs.rules;

import io.github.snakeinprod.nextjs.js.JsTrees;
import io.github.snakeinprod.nextjs.model.Module;
import io.github.snakeinprod.nextjs.model.ServerAction;
import io.github.snakeinprod.nextjs.parse.Chain;
import io.github.snakeinprod.nextjs.parse.Node;
import io.github.snakeinprod.nextjs.parse.Nodes;
import io.github.snakeinprod.nextjs.parse.Token;
import io.github.snakeinprod.nextjs.parse.TokenKind;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Server Action inputs should be validated before use. A Server Action passing one of its arguments (FormData
 * included), or a value built from one, to a database call or as the whole target of redirect(). Silent when the action
 * calls anything that looks like a validation (parse, safeParse, validate, assert, check...) or tests an input in a
 * condition, a comparison, typeof or an allow-list. An action built with an action library is validated by a
 * {@code .schema}, {@code .inputSchema} or {@code .input} step of its chain, one built by a wrapper when the wrapper
 * parses a schema; the wrapped function's parameters the wrapper fills itself (the user) are no input.
 */
public class ServerActionWithoutValidation implements NextRule {

  private static final Pattern VALIDATOR = Pattern.compile("(?i).*(parse|valid|assert|check|saniti|schema|verify|guard|ensure|zfd).*");
  private static final Set<String> COERCE = Set.of("Number", "parseInt", "parseFloat", "Boolean", "BigInt", "Math", "Date");
  private static final Set<String> COMPARE = Set.of("===", "!==", "==", "!=", "<", ">", "<=", ">=", "?", "instanceof");
  private static final Set<String> CONDITIONS = Set.of("if", "while", "switch");
  private static final Set<String> ALLOW_LIST = Set.of("includes", "has", "test", "match", "startsWith", "endsWith", "some", "find");

  private static final Set<String> BUILDER_RAW = Set.of("clientInput", "bindArgsClientInputs");
  private static final Set<String> BUILDER_PARSED = Set.of("parsedInput", "input");

  private ServerActionWithoutAuth.Flow flow;

  @Override
  public void check(RuleContext ctx) {
    Module m = ctx.module();
    if (m.serverActions().isEmpty()) {
      return;
    }
    flow = ServerActionWithoutAuth.Flow.of(flow, ctx.model());
    for (ServerAction a : m.serverActions()) {
      if (a.wrapper() == null || !a.wrapper().validated()) {
        check(ctx, m, a.fn(), a.builder() != null ? builderInputs(a) : a.wrapper() != null ? wrappedInputs(a) : params(a.fn()));
      }
    }
  }

  private static Set<String> params(JsTrees.Fn fn) {
    Set<String> inputs = new HashSet<>();
    for (Token t : fn.simpleParams()) {
      inputs.add(t.text());
    }
    if (fn.params() != null) {
      for (List<Node> item : fn.params().items()) {
        if (item.get(0) instanceof Node.Group g && g.is("{")) {
          inputs.addAll(ServerActionWithoutAuth.Flow.patternNames(g));
        }
      }
    }
    return inputs;
  }

  /** The parameters of a wrapped function the wrapper fills from the caller's arguments, not those it adds (the user). */
  private static Set<String> wrappedInputs(ServerAction a) {
    Set<String> inputs = new HashSet<>();
    JsTrees.Fn fn = a.fn();
    if (fn.param() != null) {
      if (a.wrapper().clientArg(0)) {
        inputs.add(fn.param().text());
      }
      return inputs;
    }
    List<List<Node>> items = fn.params().items();
    for (int i = 0; i < items.size(); i++) {
      List<Node> item = items.get(i);
      if (a.wrapper().clientArg(i)) {
        inputs.addAll(ServerActionWithoutAuth.Flow.patternNames(item.get(Nodes.isLeaf(item.get(0), "...") && item.size() > 1 ? 1 : 0)));
      }
    }
    return inputs;
  }

  /**
   * The raw inputs an action library handler destructures: {@code clientInput}, {@code bindArgsClientInputs}, and
   * {@code parsedInput} / {@code input} when the chain has no schema step; {@code ctx} and the rest come from the server.
   */
  private static Set<String> builderInputs(ServerAction a) {
    Set<String> raw = new HashSet<>(BUILDER_RAW);
    if (!a.builder().validated()) {
      raw.addAll(BUILDER_PARSED);
    }
    Set<String> inputs = new HashSet<>();
    List<List<Node>> params = a.fn().params() == null ? List.of() : a.fn().params().items();
    if (params.isEmpty() || !(params.get(0).get(0) instanceof Node.Group g && g.is("{"))) {
      return inputs;
    }
    for (List<Node> item : g.items()) {
      Token key = Nodes.leaf(item.get(0));
      if (key == null || !raw.contains(key.text())) {
        continue;
      }
      if (item.size() >= 3 && Nodes.isLeaf(item.get(1), ":")) {
        inputs.addAll(ServerActionWithoutAuth.Flow.patternNames(item.get(2)));
      } else {
        inputs.add(key.text());
      }
    }
    return inputs;
  }

  private void check(RuleContext ctx, Module m, JsTrees.Fn fn, Set<String> inputs) {
    List<Chain> chains = ServerActionWithoutAuth.Flow.chains(fn);
    if (inputs.isEmpty() || chains.stream().anyMatch(c -> ServerActionWithoutAuth.Flow.calls(c, n -> !n.startsWith("revalidate")
      && VALIDATOR.matcher(n).matches()))) {
      return;
    }
    List<ServerActionWithoutAuth.Flow.Decl> decls = ServerActionWithoutAuth.Flow.decls(fn.body());
    for (int pass = 0; pass < 4; pass++) {
      for (ServerActionWithoutAuth.Flow.Decl d : decls) {
        if (!inputs.containsAll(d.names()) && !coerced(d.init()) && ServerActionWithoutAuth.Flow.mentions(d.init(), inputs) != null) {
          inputs.addAll(d.names());
        }
      }
    }
    if (checked(fn.body(), inputs) || chains.stream().anyMatch(c -> allowListed(c, inputs))) {
      return;
    }
    Set<Token> reported = new HashSet<>();
    for (Chain c : chains) {
      if (flow.isDbCall(m, c)) {
        List<Node> kids = c.container().children();
        Token t = firstInput(kids.subList(c.from() + 1, c.to() < kids.size() && isTemplate(kids.get(c.to())) ? c.to() + 1 : c.to()), inputs);
        if (t != null && reported.add(t)) {
          ctx.report(t, "Validate \"" + t.text() + "\" with a schema (zod, valibot...) or explicit checks before passing it to the database.");
        }
      } else if ((c.path().equals("redirect") || c.path().equals("permanentRedirect")) && c.segments().size() == 2 && c.endsWithCall()) {
        List<List<Node>> args = c.callAfter(0).items();
        Token t = args.isEmpty() ? null : wholeInput(args.get(0), inputs);
        if (t != null) {
          ctx.report(t, "Validate \"" + t.text() + "\" against an allow-list or explicit checks before redirecting to it.");
        }
      }
    }
  }

  private static boolean isTemplate(Node n) {
    Token t = Nodes.leaf(n);
    return t != null && t.kind() == TokenKind.STRING && t.text().startsWith("`");
  }

  /** {@code Number(x)}, {@code parseInt(x)}: a coerced value, not the raw input. */
  private static boolean coerced(List<Node> init) {
    int i = !init.isEmpty() && Nodes.isIdent(init.get(0), "await") ? 1 : 0;
    return i < init.size() && Nodes.leaf(init.get(i)) != null && COERCE.contains(Nodes.leaf(init.get(i)).text());
  }

  /** An input in an if / while / switch condition, a comparison, a ternary test or after typeof. */
  private static boolean checked(Node.Group body, Set<String> inputs) {
    boolean[] found = {false};
    Nodes.groups(body, g -> {
      List<Node> k = g.children();
      for (int i = 0; i < k.size() && !found[0]; i++) {
        if (k.get(i) instanceof Node.Leaf l && CONDITIONS.contains(l.token().text()) && i + 1 < k.size()
          && k.get(i + 1) instanceof Node.Group cond && cond.is("(") && ServerActionWithoutAuth.Flow.mentions(List.of(cond), inputs) != null) {
          found[0] = true;
        }
        Token t = Nodes.leaf(k.get(i));
        if (t != null && t.kind() == TokenKind.IDENT && inputs.contains(t.text()) && !(i > 0 && (Nodes.isLeaf(k.get(i - 1), ".")
          || Nodes.isLeaf(k.get(i - 1), "?.")))) {
          int end = i + 1;
          while (end + 1 < k.size() && (Nodes.isLeaf(k.get(end), ".") || Nodes.isLeaf(k.get(end), "?.")) || end < k.size()
            && k.get(end) instanceof Node.Group sub && !sub.is("{")) {
            end += k.get(end) instanceof Node.Group ? 1 : 2;
          }
          found[0] |= i > 0 && (Nodes.isIdent(k.get(i - 1), "typeof") || isCompare(k.get(i - 1)))
            || end < k.size() && isCompare(k.get(end));
        }
      }
    });
    return found[0];
  }

  private static boolean isCompare(Node n) {
    Token t = Nodes.leaf(n);
    return t != null && (t.kind() == TokenKind.OP || t.isIdent("instanceof")) && COMPARE.contains(t.text());
  }

  /** {@code allowed.includes(x)}, {@code /re/.test(x)}: an input checked against an allow-list or a pattern. */
  private static boolean allowListed(Chain c, Set<String> inputs) {
    List<Chain.Segment> s = c.segments();
    for (int i = 0; i + 1 < s.size(); i++) {
      if (s.get(i) instanceof Chain.Name n && ALLOW_LIST.contains(n.token().text()) && s.get(i + 1) instanceof Chain.Call call
        && ServerActionWithoutAuth.Flow.mentions(List.of(call.args()), inputs) != null) {
        return true;
      }
    }
    return false;
  }

  /** The first input used as a value in these nodes (a template literal's {@code ${x}} too), coerced ones left out. */
  private static Token firstInput(List<Node> nodes, Set<String> inputs) {
    for (int i = 0; i < nodes.size(); i++) {
      Node n = nodes.get(i);
      Token t = Nodes.leaf(n);
      if (t != null && COERCE.contains(t.text()) && i + 1 < nodes.size() && nodes.get(i + 1) instanceof Node.Group) {
        i++;
      } else if (t != null && t.kind() == TokenKind.IDENT && inputs.contains(t.text())
        && !(i > 0 && (Nodes.isLeaf(nodes.get(i - 1), ".") || Nodes.isLeaf(nodes.get(i - 1), "?.")))
        && !(i + 1 < nodes.size() && Nodes.isLeaf(nodes.get(i + 1), ":"))) {
        return t;
      } else if (t != null && t.kind() == TokenKind.STRING && t.text().startsWith("`")) {
        for (String name : inputs) {
          if (Pattern.compile("\\$\\{[^}]*\\b" + Pattern.quote(name) + "\\b").matcher(t.text()).find()) {
            return t;
          }
        }
      } else if (n instanceof Node.Group g) {
        Token inner = firstInput(g.children(), inputs);
        if (inner != null) {
          return inner;
        }
      }
    }
    return null;
  }

  /** The argument is an input, or a member chain on one ({@code formData.get('next')}), optionally {@code as string}. */
  private static Token wholeInput(List<Node> arg, Set<String> inputs) {
    Token first = Nodes.leaf(arg.get(0));
    if (first == null || first.kind() != TokenKind.IDENT || !inputs.contains(first.text())) {
      return null;
    }
    for (int i = 1; i < arg.size(); i++) {
      Node n = arg.get(i);
      if (Nodes.isIdent(n, "as")) {
        break;
      }
      Token t = Nodes.leaf(n);
      if (t != null && t.kind() == TokenKind.OP && !t.is(".") && !t.is("?.") && !t.is("!")) {
        return null;
      }
    }
    return first;
  }
}
