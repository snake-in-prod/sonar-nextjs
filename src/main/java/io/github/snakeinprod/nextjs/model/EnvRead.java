package io.github.snakeinprod.nextjs.model;

import io.github.snakeinprod.nextjs.parse.Token;

/** A {@code process.env.NAME} or {@code process.env["NAME"]} read; {@code token} is the name's token. */
public record EnvRead(String name, Token token) {
}
