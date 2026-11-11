package io.github.snakeinprod.nextjs.model;

import io.github.snakeinprod.nextjs.js.JsTrees;
import io.github.snakeinprod.nextjs.parse.Token;

/**
 * A Server Action: an exported function of a "use server" module, or ({@code inline}) a function whose body starts
 * with "use server". {@code name} is the function's name token, or its first token when it has no name.
 */
public record ServerAction(Token name, JsTrees.Fn fn, boolean inline) {
}
