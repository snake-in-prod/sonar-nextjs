# Next.js rules for SonarQube

A SonarQube plugin that catches Next.js mistakes across the client/server boundary: secrets or server code reaching
the browser, Server Actions and route handlers open to anyone, per-user data cached for everyone, App Router APIs
misused. It builds a module graph of the app (client, server and shared modules and their imports) and checks it.

Work in progress: the model, the rule catalog, the measures and the sensor are in place; the rules report nothing yet.

## Install

Build with `./mvnw verify` (Java 21) and copy `target/sonar-nextjs-*.jar` to `extensions/plugins/` of SonarQube 10.12
or later. The plugin analyses js and ts files of projects where a package.json declares a `next` dependency.

## Rules

Repositories `nextjs-js` and `nextjs-ts`, built-in profile "Next.js" for both languages. SonarQube allows one
built-in profile of a name per language, so the rules cannot join "Sonar way": activate them in your own profile, or
extend "Next.js".

| Rule | Type | Severity | In profile |
|---|---|---|---|
| ServerCodeInClientBundle | Vulnerability | Critical | yes |
| SecretInPublicEnv | Vulnerability | Critical | yes |
| ServerEnvInClient | Bug | Major | yes |
| ServerActionWithoutAuth | Vulnerability | Critical | yes |
| ServerActionWithoutValidation | Vulnerability | Major | yes |
| RouteHandlerWithoutAuth | Vulnerability | Major | no |
| PersonalizedCachedFetch | Vulnerability | Critical | yes |
| OpenRedirect | Vulnerability | Major | yes |
| UnsafeHtmlFromInput | Vulnerability | Critical | yes |
| AsyncClientComponent | Bug | Major | yes |
| ClientHookInServerComponent | Bug | Major | yes |
| SyncParamsAccess | Bug | Major | yes |
| MetadataExportedTwice | Bug | Major | yes |
| SearchParamsWithoutSuspense | Bug | Minor | no |
| ImageFillWithoutSizes | Code smell | Minor | no |

## Measures

nx_routes, nx_client_modules, nx_server_modules, nx_shared_modules, nx_client_ratio, nx_server_actions,
nx_unprotected_endpoints, nx_dynamic_routes, and nx_routes_json for the project page "Next.js".

## Command line

```
./mvnw -q compile dependency:build-classpath -Dmdep.outputFile=cp.txt
java -cp target/classes:$(cat cp.txt) io.github.snakeinprod.nextjs.cli.Scan <app dir> [--routes] [--modules]
```

See [docs/DESIGN.md](docs/DESIGN.md) for how it works and how to write a rule.

## License

Apache-2.0
