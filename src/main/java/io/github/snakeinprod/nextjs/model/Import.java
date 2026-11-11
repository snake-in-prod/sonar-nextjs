package io.github.snakeinprod.nextjs.model;

import io.github.snakeinprod.nextjs.parse.Token;
import java.util.Map;

/**
 * One import of a module: {@code import ... from 'x'}, {@code import 'x'}, {@code export ... from 'x'},
 * {@code require('x')} or {@code import('x')}.
 *
 * @param spec     the module specifier as written ("@/lib/db", "./x", "fs", "node:fs")
 * @param resolved the project file it resolves to, or null (a package, a Node built-in, or not found)
 * @param token    the specifier's string token, to report on
 * @param names    local name to imported name: "default" for a default import, "*" for a namespace; for a re-export
 *                 the exported name to the imported one ({@code export * from} gives "*" to "*"). Empty for a side
 *                 effect import, {@code require} and {@code import()}
 * @param typeOnly {@code import type} / {@code export type}, or only type specifiers: not in any bundle
 * @param reExport an {@code export ... from}
 * @param dynamic  an {@code import()} call
 */
public record Import(String spec, String resolved, Token token, Map<String, String> names, boolean typeOnly, boolean reExport,
  boolean dynamic) {

  /** The package of a bare specifier: "@scope/a" for "@scope/a/b", "fs" for "node:fs/promises"; null for a path. */
  public String packageName() {
    if (spec.startsWith(".") || spec.startsWith("/")) {
      return null;
    }
    String s = spec.startsWith("node:") ? spec.substring(5) : spec;
    String[] parts = s.split("/");
    return s.startsWith("@") && parts.length > 1 ? parts[0] + "/" + parts[1] : parts[0];
  }
}
