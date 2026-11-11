package io.github.snakeinprod.nextjs.model;

import io.github.snakeinprod.nextjs.js.JsTrees;
import io.github.snakeinprod.nextjs.parse.Chain;
import io.github.snakeinprod.nextjs.parse.Chains;
import io.github.snakeinprod.nextjs.parse.Node;
import io.github.snakeinprod.nextjs.parse.Nodes;
import io.github.snakeinprod.nextjs.parse.Token;
import io.github.snakeinprod.nextjs.parse.TokenKind;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A function wrapping a Server Action ({@code export const x = withAuth(async (...) => ...)}): {@code fn} in
 * {@code module} returns the async function {@code returned}, which makes {@code call}, the call of the wrapped
 * function.
 */
public record Wrapper(Module module, JsTrees.Fn fn, JsTrees.Fn returned, Chain.Call call) {

  private static final Pattern SCHEMA_PARSE = Pattern.compile("(safe)?[pP]arse(Async)?|validate\\w*");

  /**
   * The wrapper of a local name of the module, given the wrapped function as argument {@code arg}: a visible function
   * whose parameter there is called inside an async function it returns; null when it is not one.
   */
  static Wrapper of(NextModel model, Module m, String name, int arg) {
    NextModel.Found w = model.function(m, name);
    Token p = w == null ? null : param(w.fn(), arg);
    if (p == null) {
      return null;
    }
    for (JsTrees.Fn r : returned(w.fn())) {
      for (Chain c : r.async() ? Chains.in(r.body()) : List.<Chain>of()) {
        if (c.firstName().equals(p.text()) && c.segments().size() >= 2 && c.segments().get(1) instanceof Chain.Call call) {
          return new Wrapper(w.module(), w.fn(), r, call);
        }
      }
    }
    return null;
  }

  /** The wrapper parses a schema: {@code schema.safeParse(...)}, {@code parse(schema, ...)}, {@code validate...(...)}. */
  public boolean validated() {
    for (Chain c : Chains.in(fn.body())) {
      List<Chain.Segment> s = c.segments();
      for (int i = 0; i + 1 < s.size() && !c.firstName().equals("JSON"); i++) {
        if (s.get(i) instanceof Chain.Name n && s.get(i + 1) instanceof Chain.Call && SCHEMA_PARSE.matcher(n.token().text()).matches()) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Parameter i of the wrapped function gets what the caller sent: argument i of the call uses a parameter of the
   * returned function ({@code action(user, ...args)}: i ≥ 1), or a spread before it does.
   */
  public boolean clientArg(int i) {
    Set<String> names = paramNames(returned);
    List<List<Node>> args = call.items();
    for (int k = 0; k < args.size() && k <= i; k++) {
      boolean spread = Nodes.isLeaf(args.get(k).get(0), "...");
      if ((k == i || spread) && Nodes.tokens(args.get(k)).stream().anyMatch(t -> names.contains(t.text()))) {
        return true;
      }
    }
    return false;
  }

  private static Set<String> paramNames(JsTrees.Fn f) {
    Set<String> out = new HashSet<>();
    if (f.param() != null) {
      out.add(f.param().text());
      return out;
    }
    for (List<Node> item : f.params().items()) {
      Token t = Nodes.leaf(item.get(Nodes.isLeaf(item.get(0), "...") && item.size() > 1 ? 1 : 0));
      if (t != null && t.kind() == TokenKind.IDENT) {
        out.add(t.text());
      }
    }
    return out;
  }

  /** The plain (typed, optional or defaulted) parameter at index i, or null. */
  private static Token param(JsTrees.Fn f, int i) {
    if (f.param() != null) {
      return i == 0 ? f.param() : null;
    }
    List<List<Node>> items = f.params().items();
    Token t = i < items.size() ? Nodes.leaf(items.get(i).get(0)) : null;
    return t != null && t.kind() == TokenKind.IDENT ? t : null;
  }

  /** Functions the body returns: its expression body, or after a {@code return} anywhere in it. */
  private static List<JsTrees.Fn> returned(JsTrees.Fn f) {
    List<JsTrees.Fn> out = new ArrayList<>();
    if (f.expression()) {
      JsTrees.Fn r = f.body().children().isEmpty() ? null : JsTrees.functionAt(f.body(), 0);
      if (r != null) {
        out.add(r);
      }
      return out;
    }
    Nodes.groups(f.body(), g -> {
      List<Node> k = g.children();
      for (int i = 0; i + 1 < k.size(); i++) {
        JsTrees.Fn r = Nodes.isIdent(k.get(i), "return") ? JsTrees.functionAt(g, i + 1) : null;
        if (r != null) {
          out.add(r);
        }
      }
    });
    return out;
  }
}
