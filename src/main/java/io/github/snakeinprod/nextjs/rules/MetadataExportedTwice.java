package io.github.snakeinprod.nextjs.rules;

import io.github.snakeinprod.nextjs.model.Import;
import io.github.snakeinprod.nextjs.model.Module;
import io.github.snakeinprod.nextjs.model.Route;
import io.github.snakeinprod.nextjs.parse.Token;

/**
 * An App Router page or layout that exports both {@code metadata} and {@code generateMetadata}, declared in the file
 * or re-exported by name ({@code export { generateMetadata } from './seo'}). {@code export * from} is left out: the
 * names it brings are not visible here.
 */
public class MetadataExportedTwice implements NextRule {

  @Override
  public void check(RuleContext ctx) {
    Module m = ctx.module();
    Route r = m.route();
    if (r == null || !r.router().equals("app") || !(r.kind().equals("page") || r.kind().equals("layout"))) {
      return;
    }
    Token metadata = exported(m, "metadata");
    Token generate = exported(m, "generateMetadata");
    if (metadata != null && generate != null) {
      ctx.report(generate, "Export either \"metadata\" or \"generateMetadata\" from this " + r.kind()
        + ", not both: Next.js refuses the file.");
    }
  }

  /** The token of the local name exported as {@code name}, or the specifier of a re-export of it; null when none. */
  private static Token exported(Module m, String name) {
    Token t = m.exports().get(name);
    if (t != null) {
      return t;
    }
    for (Import i : m.imports()) {
      if (i.reExport() && !i.typeOnly() && i.names().containsKey(name)) {
        return i.token();
      }
    }
    return null;
  }
}
