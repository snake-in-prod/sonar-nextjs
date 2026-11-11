# Design

One plugin, key `nextjs`, for SonarQube 10.12 and later (built against plugin API 14.2). It catches Next.js mistakes
across the client/server boundary: secrets or server code reaching the browser, Server Actions and route handlers open
to anyone, per-user data cached for everyone, App Router APIs misused.

The core is a module graph of the app. Rules are checks on it.

## Flow

```
js/ts files + package.json / tsconfig.json / jsconfig.json  (model.Project: paths + read)
  -> NextModel.build: each file -> ParsedFile -> ModuleReader -> Module (directives, imports, exports, functions,
     Server Actions, env reads); imports resolved by Resolver; then routes, reverse edges and roles (NextModel.link)
  -> sensor.Analyzer: every rule on every module -> issues
  -> NxMetrics.fileValues -> nx_* on files -> NxAggregator sums to directories and the project, derives nx_client_ratio
  -> RoutesJson -> nx_routes_json on the project
```

`NextjsSensor` (SonarQube), `cli.Scan` (command line) and the tests (`RuleVerifier`) all go through `NextModel.build`
and `Analyzer`, so they see the same results. The sensor runs on js and ts, builds the model once and does nothing
when no package.json of the project declares a `next` dependency. The analyzer runs every rule; the sensor saves the
issues of the rules active in the quality profile. nx_unprotected_endpoints counts the issues of
ServerActionWithoutAuth and RouteHandlerWithoutAuth whether or not they are active.

Every parsed file stays in memory for the analysis (rules follow imports into other modules).

## Parsing

No grammar and no external parser, as in the sibling plugins. `js.JsLexer` turns JS/TS/JSX/TSX into `Token`s
(IDENT, NUMBER, STRING, OP, OTHER for JSX text); comments come separately. `parse.TreeBuilder` groups tokens by
brackets into a `Node` tree; `parse.Chains` finds member and call chains (`cookies().get("id")`,
`process.env.API_URL`, `NextResponse.redirect(url)`). `js.JsTrees` finds function literals (`Fn`: function
declarations and expressions, arrow functions), their names (`nameOf`) and whether a chain is awaited or returned.
The lexer never throws; a broken file still gives a tree.

## The model (package `model`)

### Modules

`Module` is one file:

| method | what |
|---|---|
| `path()`, `language()` | path relative to the base directory, "js" or "ts" |
| `file()` | the `ParsedFile`: `tokens()`, `comments()`, `root()` (token tree), `chains()`, `lines()` |
| `directives()`, `has(Directive)` | `USE_CLIENT`, `USE_SERVER`, `USE_CACHE` (directive prologue at the top of the file), `SERVER_ONLY`, `CLIENT_ONLY` (`import "server-only"` / `"client-only"`) |
| `imports()` | every `import`, `export ... from`, `require()` and `import()`, see below |
| `exports()` | exported name to the token of the local name (`export function f`, `export const f`, `export { a as b }`, `export default ...` as "default") |
| `functions()` | top-level named functions (`function f`, `const f = () =>`), an anonymous default export as "default" |
| `functionsWithDirective("use server")` | functions, nested ones too, whose body starts with that directive ("use cache" too) |
| `serverActions()` | exported functions of a "use server" module, and inline "use server" functions (`inline()`) |
| `envReads()` | `process.env.X` and `process.env["X"]`: the name and its token |
| `route()` | the Next.js convention, or null |
| `inAppDir()` | under app/ or src/app/ of a Next.js package |
| `role()`, `reachedFromServer()` | see Roles |
| `runtime()` | "edge" (`export const runtime = 'edge'`, middleware.ts) or "node" |

`Import(spec, resolved, token, names, typeOnly, reExport, dynamic)`: `spec` as written, `resolved` the project file or
null (a package, a Node built-in, not found), `token` the specifier string to report on, `names` local to imported
name ("default", "*" for a namespace; for a re-export exported to imported), `packageName()` for bare specifiers
("@scope/a" for "@scope/a/b", "fs" for "node:fs").

### Resolution

`Resolver`, in TypeScript's order: relative paths; with the nearest tsconfig.json or jsconfig.json (comments and
trailing commas allowed, "extends" followed, relative or from node_modules, arrays too) `compilerOptions.paths`
(longest prefix wins, exact patterns first; relative to `baseUrl` when set, else to the config that declares them)
and then `baseUrl`; then workspace packages by the name in their package.json (the entry from "exports", "module",
"main", "source", or `src/index`; a sub path through "exports" or as `sub` / `src/sub`). Each candidate is tried as
written, with a JS/TS extension, as a directory index, and from ".js" to the ".ts" source.

### Next.js conventions

A Next.js app is a directory with a package.json declaring `next` (`NextModel.apps()`): its major version comes from
the declared range, or, for `latest`, `canary`, `workspace:*`, from `node_modules/next/package.json` there or above
(`nextMajor(module)`: the nearest app above the module, else the highest of the project; 0 when unknown).

Under an app directory: `app/` or `src/app/` convention files (page, layout, route, loading, error, global-error,
not-found, template, default) give an App Router `Route` with the URL path, route groups `(x)`, parallel slots `@x`
and interception markers `(.)` removed, private folders `_x` left out, `dynamic` for `[x]`, `[...x]`, `[[...x]]`.
`pages/` or `src/pages/` files give Pages Router routes: `api` under pages/api, `app` / `document` / `error` for
`_app` / `_document` / `_error`, else `page`. `middleware.ts` (and Next 16's `proxy.ts`) at the app root or in src/ is
`middleware`. `Route.isRoute()`: a page or an endpoint; `isEndpoint()`: route handler or API route.

### Roles

- CLIENT: a "use client" module, or one it reaches through imports. A "use server" module is not entered: a client
  module importing it gets a reference to the actions, not their code. Type-only imports never count.
- SERVER: not client, and a server entry or reached from one through imports that stop at "use client" modules.
  Entries: App Router convention files, other app/ modules that are not client, route handlers, API routes,
  middleware, "use server" modules.
- SHARED: neither. Pages Router pages are not entries: their getServerSideProps imports are removed from the browser
  bundle, which an import graph cannot tell apart, so they and what only they import are shared.

A module can be client and reached from the server at once (a file without a directive imported by both a client and
a server module): `role()` is CLIENT, `reachedFromServer()` is true. Rules about server components ask
`reachedFromServer()`; rules about the browser bundle ask `role() == Role.CLIENT`.

### Graph queries (`NextModel`)

`modules()`, `module(path)`, `target(import)` (the module an import resolves to), `imported(module)` (non-type
imports, resolved, in order), `importers(module)`, `reachable(start, enter)` (breadth first, `enter` decides whether
to go into a module), `clientModulesOf(route)`, `nextMajor(module)`, `hasNext()`, `apps()`, `skipped()`.

## Writing a rule

A rule is a class in `rules/` implementing `NextRule`:

```java
public class ServerEnvInClient implements NextRule {
  @Override
  public void check(RuleContext ctx) {
    Module m = ctx.module();
    if (m.role() != Role.CLIENT) {
      return;
    }
    for (EnvRead e : m.envReads()) {
      if (!e.name().startsWith("NEXT_PUBLIC_") && !e.name().equals("NODE_ENV")) {
        ctx.report(e.token(), "...");
      }
    }
  }
}
```

- `check` runs once per module of the project, whatever its role: the rule picks the modules it checks.
- `ctx.module()` is the module, `ctx.model()` the whole graph (follow `imports()` with `model.target(i)`, walk with
  `model.reachable`, read `model.nextMajor(m)`), `ctx.file()` the parsed file.
- `ctx.report(token, message)` or `ctx.report(from, to, message)` reports on the checked module only. A line with a
  NOSONAR comment gets no issue.
- One rule instance per analysis: a rule may cache what it computes from the model in its fields (for example which
  modules check the session), keyed by module.
- Register the rule's metadata in `RuleCatalog` (key, type, severity, active in the "Next.js" profile, name,
  description, example, factory). A longer description can go in `src/main/resources/rules/<Key>.html`.

### Fixtures

Each rule has a fixture: `src/test/resources/fixtures/<Key>.<ts|tsx|js|jsx>` (one file at the project root), or a
directory `fixtures/<Key>/` holding a small app (`app/...`, `components/...`, `lib/...`, its own package.json and
tsconfig.json when it needs them). Without a package.json the verifier adds one declaring next 15. A comment
`Noncompliant` marks a line that must get one issue, `Noncompliant 2` two, `Noncompliant {{text}}` checks the
message; every other line must stay silent. A test calls `RuleVerifier.verify("<Key>")`.
`RuleVerifierTest` shows both kinds with two small demo rules.

### Quality bar

An issue must be a real defect in at least 9 of 10 cases on real public apps (`cli.Scan` runs every rule on a
directory without SonarQube). When a pattern is ambiguous, stay silent. Messages say what is wrong and what to do, in
one sentence.

## Measures

On files: nx_routes, nx_client_modules, nx_server_modules, nx_shared_modules, nx_server_actions,
nx_unprotected_endpoints, nx_dynamic_routes (routes with a dynamic segment). `NxAggregator` sums them and derives
nx_client_ratio (client modules / all modules, %). nx_routes_json, on the project: one object per route and per
Server Action with `path` (URL path, or file#function), `kind` (page, route, action), `file`, `runtime`, `dynamic`,
`clientModules`, `issues`.

## Page

`static/nextjs.js`, registered as `nextjs/nextjs`. SonarQube mounts extension pages in a two-column grid: the page sets
`options.el.style.gridColumn = '1 / -1'` and `width = '100%'`, and uses no CSS container queries. Plain DOM, charts as
inline SVG built with `createElementNS`, no innerHTML with data.

## Conventions

- Java 21, gson is the only (shaded) dependency, no reflection, no network.
- One-line commit messages, no co-author lines, author `snake-in-prod`.
- Public repository: generic names only.
