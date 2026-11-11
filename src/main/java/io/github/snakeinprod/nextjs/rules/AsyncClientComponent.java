package io.github.snakeinprod.nextjs.rules;

import io.github.snakeinprod.nextjs.js.JsTrees;
import io.github.snakeinprod.nextjs.model.Directive;
import io.github.snakeinprod.nextjs.model.Module;
import io.github.snakeinprod.nextjs.model.Route;
import io.github.snakeinprod.nextjs.parse.Nodes;
import io.github.snakeinprod.nextjs.parse.Token;

/**
 * Client components should not be async functions: the async default export of a "use client" module. Only when it
 * is a component: a PascalCase name, a page/layout-like convention file, or an anonymous export in a .jsx/.tsx file
 * (a lower-case async default export of a .ts file is a fetcher, not a component).
 */
public class AsyncClientComponent implements NextRule {

  @Override
  public void check(RuleContext ctx) {
    Module m = ctx.module();
    Token d = m.exports().get("default");
    if (!m.has(Directive.USE_CLIENT) || d == null) {
      return;
    }
    // "default" for an anonymous default export, else the local name
    JsTrees.Fn f = m.functions().get(d.text());
    if (f == null || !f.async()) {
      return;
    }
    Token name = JsTrees.nameOf(f);
    Route r = m.route();
    boolean convention = r != null && !r.isEndpoint() && !r.kind().equals("middleware");
    boolean component = convention || (name != null ? Character.isUpperCase(name.text().charAt(0)) : m.path().matches(".*\\.[jt]sx"));
    if (!component) {
      return;
    }
    Token async = Nodes.leaf(f.container().children().get(f.start()));
    ctx.report(async, name != null ? name : async,
      "Remove \"async\" from this client component: React cannot render async client components; fetch the data in a server component or a hook.");
  }
}
