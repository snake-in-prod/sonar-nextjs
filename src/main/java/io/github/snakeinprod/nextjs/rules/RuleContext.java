package io.github.snakeinprod.nextjs.rules;

import io.github.snakeinprod.nextjs.model.Module;
import io.github.snakeinprod.nextjs.model.NextModel;
import io.github.snakeinprod.nextjs.parse.ParsedFile;
import io.github.snakeinprod.nextjs.parse.Token;

/** What a rule sees: the module it checks, the whole model, and a way to report on the module's tokens. */
public interface RuleContext {

  /** The module being checked: its file, role, directives, imports, route, exports, Server Actions. */
  Module module();

  /** Every module of the project, to follow imports and look up routes and the Next.js version. */
  NextModel model();

  /** Shortcut for {@code module().file()}. */
  default ParsedFile file() {
    return module().file();
  }

  /** Reports an issue on the checked module, from {@code from} to {@code to} (both included). */
  void report(Token from, Token to, String message);

  default void report(Token at, String message) {
    report(at, at, message);
  }
}
