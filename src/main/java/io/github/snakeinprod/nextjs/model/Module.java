package io.github.snakeinprod.nextjs.model;

import io.github.snakeinprod.nextjs.js.JsTrees;
import io.github.snakeinprod.nextjs.parse.ParsedFile;
import io.github.snakeinprod.nextjs.parse.Token;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One JS/TS file of the project and what the model knows about it. Built by {@link NextModel#build}; the role, the
 * route and {@link #reachedFromServer()} are set once every module is read.
 */
public final class Module {

  private final ParsedFile file;
  private final Set<Directive> directives;
  private final List<Import> imports;
  private final Map<String, Token> exports;
  private final Map<String, JsTrees.Fn> functions;
  private final Map<String, List<JsTrees.Fn>> functionDirectives;
  List<ServerAction> serverActions;
  /** Exported values of a "use server" module built by calling a function with a function, for the model to confirm. */
  final List<Wrapped> wrapped;
  private final List<EnvRead> envReads;
  private final String runtimeExport;
  Route route;
  boolean appDir;
  Role role = Role.SHARED;
  boolean reachedFromServer;
  boolean test;

  Module(ParsedFile file, Set<Directive> directives, List<Import> imports, Map<String, Token> exports,
    Map<String, JsTrees.Fn> functions, Map<String, List<JsTrees.Fn>> functionDirectives, List<ServerAction> serverActions,
    List<Wrapped> wrapped, List<EnvRead> envReads, String runtimeExport) {
    this.file = file;
    this.directives = Set.copyOf(directives);
    this.imports = List.copyOf(imports);
    this.exports = Map.copyOf(exports);
    this.functions = Map.copyOf(functions);
    this.functionDirectives = Map.copyOf(functionDirectives);
    this.serverActions = List.copyOf(serverActions);
    this.wrapped = List.copyOf(wrapped);
    this.envReads = List.copyOf(envReads);
    this.runtimeExport = runtimeExport;
  }

  /** Path relative to the base directory, "/" separated. */
  public String path() {
    return file.path();
  }

  /** SonarQube language key: "js" or "ts". */
  public String language() {
    return file.language();
  }

  /** Tokens, comments, the token tree and its chains. */
  public ParsedFile file() {
    return file;
  }

  public Set<Directive> directives() {
    return directives;
  }

  public boolean has(Directive d) {
    return directives.contains(d);
  }

  /** Every import, re-export, require and import() of the file, in source order. */
  public List<Import> imports() {
    return imports;
  }

  /**
   * Exported name to the token of the local name it exports: {@code export function f} and {@code export const f}
   * give f to f, {@code export { a as b }} b to a, {@code export default function f} "default" to f, and an anonymous
   * default export "default" to the {@code default} keyword. Re-exports ({@code export ... from}) are imports.
   */
  public Map<String, Token> exports() {
    return exports;
  }

  private Set<String> typeExports;

  /**
   * Names exported by {@code export type X} or {@code export interface X} and not as a value too
   * ({@code export const X = z.object(...)} beside {@code export type X}): an import of only these is erased.
   */
  public Set<String> typeExports() {
    if (typeExports == null) {
      Set<String> types = new java.util.HashSet<>();
      Set<String> values = new java.util.HashSet<>();
      List<Token> t = file.tokens();
      for (int i = 1; i + 1 < t.size(); i++) {
        if (!t.get(i - 1).isIdent("export") || t.get(i + 1).kind() != io.github.snakeinprod.nextjs.parse.TokenKind.IDENT) {
          continue;
        }
        if (t.get(i).isIdent("type") || t.get(i).isIdent("interface")) {
          types.add(t.get(i + 1).text());
        } else if (VALUE_KEYWORDS.contains(t.get(i).text())) {
          values.add(t.get(i + 1).text());
        }
      }
      types.removeAll(values);
      typeExports = Set.copyOf(types);
    }
    return typeExports;
  }

  private static final Set<String> VALUE_KEYWORDS = Set.of("const", "let", "var", "function", "class", "enum", "abstract");

  /**
   * Top-level named functions: {@code function f() {}}, {@code const f = () => ...}, {@code const f = function () {}},
   * exported or not, by name. An anonymous default export function is under "default".
   */
  public Map<String, JsTrees.Fn> functions() {
    return functions;
  }

  /** Functions, nested ones included, whose body starts with this directive ("use server", "use cache"). */
  public List<JsTrees.Fn> functionsWithDirective(String directive) {
    return functionDirectives.getOrDefault(directive, List.of());
  }

  /** {@code export const name = callee(..., fn, ...)}: fn is argument {@code arg} of the call. */
  record Wrapped(Token name, Token callee, int arg, JsTrees.Fn fn) {
  }

  public List<ServerAction> serverActions() {
    return serverActions;
  }

  /** The action library chain a top-level {@code const name = ...} of this module holds, or null. */
  public ActionBuilder builder(String name) {
    return ActionBuilder.declared(file.root(), name);
  }

  /** A test, mock, story or end-to-end file: no role, no route, not measured, no issues. */
  public boolean isTest() {
    return test;
  }

  public List<EnvRead> envReads() {
    return envReads;
  }

  /** The Next.js convention this file is, or null. */
  public Route route() {
    return route;
  }

  /** Under an App Router directory (app/ or src/app/ of a Next.js package). */
  public boolean inAppDir() {
    return appDir;
  }

  public Role role() {
    return role;
  }

  /**
   * A server entry reaches this module through imports that do not cross a "use client" module: it runs as server
   * code, as a server component when it renders. A client module can be reached from the server too, when a server
   * module imports a file without a directive that a client module also imports.
   */
  public boolean reachedFromServer() {
    return reachedFromServer;
  }

  /**
   * "edge" when the module exports {@code runtime = 'edge'} or is middleware.ts (without {@code runtime = 'nodejs'}),
   * else "node".
   */
  public String runtime() {
    if (runtimeExport != null) {
      return runtimeExport.equals("edge") || runtimeExport.equals("experimental-edge") ? "edge" : "node";
    }
    return route != null && route.kind().equals("middleware") && path().matches("(.*/)?middleware\\.[^/]+") ? "edge" : "node";
  }

  @Override
  public String toString() {
    return path() + " " + role;
  }
}
