package io.github.snakeinprod.nextjs.model;

import io.github.snakeinprod.nextjs.js.JsTrees;
import io.github.snakeinprod.nextjs.parse.Token;

/**
 * A Server Action: an exported function of a "use server" module, or ({@code inline}) a function whose body starts
 * with "use server". {@code name} is the function's name token, or its first token when it has no name. An exported
 * value of a "use server" module built by an action library ({@code export const x = client.schema(s).action(fn)})
 * is one too: {@code builder} is its chain and {@code fn} the handler; else {@code builder} is null.
 */
public record ServerAction(Token name, JsTrees.Fn fn, boolean inline, ActionBuilder builder) {

  /** The name to show: the function's name, or "inline (line N)" for an anonymous inline action. */
  public String label() {
    return inline && JsTrees.nameOf(fn) == null ? "inline (line " + name.line() + ")" : name.text();
  }
}
