package io.github.snakeinprod.nextjs.model;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.snakeinprod.nextjs.TestProjects;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NextModelTest {

  private static NextModel fixture(String name) {
    return NextModel.build(TestProjects.of(TestProjects.files(Path.of("src/test/resources/model", name))));
  }

  @Test
  void roles_follow_the_client_and_server_boundaries() {
    NextModel m = fixture("basic");
    assertThat(m.hasNext()).isTrue();
    assertThat(m.skipped()).isEmpty();
    assertThat(role(m, "src/components/Counter.tsx")).isEqualTo(Role.CLIENT);
    assertThat(role(m, "src/components/format.ts")).isEqualTo(Role.CLIENT);
    // a "use server" module imported by a client one is a reference, not client code
    assertThat(role(m, "src/actions/save.ts")).isEqualTo(Role.SERVER);
    assertThat(role(m, "src/lib/db.ts")).isEqualTo(Role.SERVER);
    assertThat(role(m, "src/lib/helpers.ts")).isEqualTo(Role.SERVER);
    assertThat(role(m, "src/app/page.tsx")).isEqualTo(Role.SERVER);
    assertThat(role(m, "src/app/_components/Header.tsx")).isEqualTo(Role.SERVER);
    assertThat(role(m, "src/app/api/users/route.ts")).isEqualTo(Role.SERVER);
    assertThat(role(m, "src/middleware.ts")).isEqualTo(Role.SERVER);
    assertThat(role(m, "src/pages/api/hello.ts")).isEqualTo(Role.SERVER);
    // type-only imports bundle nothing; Pages Router pages and unimported modules are neither
    assertThat(role(m, "src/lib/types.ts")).isEqualTo(Role.SHARED);
    assertThat(role(m, "src/lib/unused.ts")).isEqualTo(Role.SHARED);
    assertThat(role(m, "src/pages/about.tsx")).isEqualTo(Role.SHARED);
    assertThat(m.module("src/components/Counter.tsx").reachedFromServer()).isFalse();
    assertThat(m.module("src/lib/db.ts").reachedFromServer()).isTrue();
  }

  @Test
  void directives_imports_and_resolution() {
    NextModel m = fixture("basic");
    Module counter = m.module("src/components/Counter.tsx");
    assertThat(counter.directives()).containsExactly(Directive.USE_CLIENT);
    assertThat(counter.imports()).extracting(Import::spec, Import::resolved).containsExactly(
      org.assertj.core.groups.Tuple.tuple("react", null),
      org.assertj.core.groups.Tuple.tuple("@/lib/types", "src/lib/types.ts"),
      org.assertj.core.groups.Tuple.tuple("./format", "src/components/format.ts"),
      org.assertj.core.groups.Tuple.tuple("@/actions/save", "src/actions/save.ts"));
    assertThat(counter.imports().get(0).names()).isEqualTo(Map.of("useState", "useState"));
    assertThat(counter.imports().get(1).typeOnly()).isTrue();
    assertThat(counter.imports().get(0).token().line()).isEqualTo(3);
    assertThat(m.imported(counter)).extracting(Module::path).containsExactly("src/components/format.ts", "src/actions/save.ts");
    assertThat(counter.exports()).containsOnlyKeys("default");
    assertThat(counter.exports().get("default").text()).isEqualTo("Counter");
    assertThat(counter.envReads()).extracting(EnvRead::name).containsExactly("NEXT_PUBLIC_FLAG");

    Module db = m.module("src/lib/db.ts");
    assertThat(db.directives()).containsExactly(Directive.SERVER_ONLY);
    // ".js" written for a ".ts" source; a relative path from app/
    assertThat(db.imports()).extracting(Import::resolved).containsExactly(null, null, "src/lib/helpers.ts");
    assertThat(db.imports().get(1).packageName()).isEqualTo("@prisma/client");
    assertThat(db.imports().get(1).names()).isEqualTo(Map.of("PrismaClient", "PrismaClient"));
    assertThat(db.envReads()).extracting(EnvRead::name).containsExactly("DATABASE_URL", "DB_FALLBACK");
    assertThat(m.module("src/app/api/users/route.ts").imports().get(0).resolved()).isEqualTo("src/lib/db.ts");
    assertThat(m.importers(db)).extracting(Module::path)
      .containsExactlyInAnyOrder("src/app/page.tsx", "src/app/api/users/route.ts", "src/actions/save.ts");
  }

  @Test
  void routes_and_runtime() {
    NextModel m = fixture("basic");
    assertThat(route(m, "src/app/page.tsx")).isEqualTo(new Route("app", "page", "/", false));
    assertThat(route(m, "src/app/layout.tsx")).isEqualTo(new Route("app", "layout", "/", false));
    assertThat(route(m, "src/app/(marketing)/blog/[slug]/page.tsx")).isEqualTo(new Route("app", "page", "/blog/[slug]", true));
    assertThat(route(m, "src/app/docs/[[...path]]/page.tsx")).isEqualTo(new Route("app", "page", "/docs/[[...path]]", true));
    assertThat(route(m, "src/app/api/users/route.ts")).isEqualTo(new Route("app", "route", "/api/users", false));
    assertThat(route(m, "src/app/@modal/default.tsx")).isEqualTo(new Route("app", "default", "/", false));
    assertThat(route(m, "src/app/_components/Header.tsx")).isNull();
    assertThat(m.module("src/app/_components/Header.tsx").inAppDir()).isTrue();
    assertThat(route(m, "src/pages/about.tsx")).isEqualTo(new Route("pages", "page", "/about", false));
    assertThat(route(m, "src/pages/api/hello.ts")).isEqualTo(new Route("pages", "api", "/api/hello", false));
    assertThat(route(m, "src/middleware.ts").kind()).isEqualTo("middleware");
    assertThat(m.module("src/middleware.ts").runtime()).isEqualTo("edge");
    assertThat(m.module("src/app/inline/page.tsx").runtime()).isEqualTo("edge");
    assertThat(m.module("src/app/page.tsx").runtime()).isEqualTo("node");
    assertThat(m.nextMajor(m.module("src/app/page.tsx"))).isEqualTo(15);
    assertThat(m.clientModulesOf(m.module("src/app/page.tsx"))).extracting(Module::path)
      .containsExactly("src/components/Counter.tsx", "src/components/format.ts");
  }

  @Test
  void server_actions_exported_and_inline() {
    NextModel m = fixture("basic");
    Module save = m.module("src/actions/save.ts");
    assertThat(save.directives()).containsExactly(Directive.USE_SERVER);
    assertThat(save.serverActions()).extracting(a -> a.name().text(), ServerAction::inline)
      .containsExactly(org.assertj.core.groups.Tuple.tuple("save", false), org.assertj.core.groups.Tuple.tuple("remove", false));
    assertThat(save.functions()).containsOnlyKeys("save", "remove");
    Module inline = m.module("src/app/inline/page.tsx");
    assertThat(inline.serverActions()).extracting(a -> a.name().text(), ServerAction::inline)
      .containsExactly(org.assertj.core.groups.Tuple.tuple("subscribe", true));
    assertThat(inline.functionsWithDirective("use server")).hasSize(1);
    assertThat(inline.exports()).containsOnlyKeys("runtime", "default");
    assertThat(m.module("src/components/Counter.tsx").serverActions()).isEmpty();
  }

  @Test
  void action_library_chains_are_server_actions() {
    NextModel m = NextModel.build(TestProjects.of(Map.of(
      "package.json", "{\"dependencies\":{\"next\":\"15.0.0\"}}",
      "lib/safe-action.ts", "export const client = createSafeActionClient().use(async ({ next }) => next());",
      "app/actions.ts", String.join("\n",
        "'use server';",
        "import { client } from '@/lib/safe-action';",
        "export const save = client",
        "  .metadata({ name: 'save' })",
        "  .bindArgsSchemas<[id: z.ZodString]>([z.string()])",
        "  .inputSchema(schema)",
        "  .action(async ({ parsedInput, ctx }): Promise<Item[]> => []);",
        "export const run = createServerAction().handler(async () => 1);",
        "export const plain = client.action(async () => 1);",
        "export const config = { a: 1 };",
        "export const schema = z.object({ a: z.string() });"))));
    Module actions = m.module("app/actions.ts");
    assertThat(actions.serverActions()).extracting(a -> a.name().text()).containsExactly("save", "run", "plain");
    ServerAction save = actions.serverActions().get(0);
    assertThat(save.builder().client().text()).isEqualTo("client");
    assertThat(save.builder().steps()).extracting(s -> s.name().text())
      .containsExactly("metadata", "bindArgsSchemas", "inputSchema", "action");
    assertThat(save.builder().validated()).isTrue();
    assertThat(save.fn().async()).isTrue();
    ServerAction run = actions.serverActions().get(1);
    assertThat(run.builder().client()).isNull();
    assertThat(run.builder().validated()).isFalse();
    assertThat(actions.serverActions().get(2).builder().validated()).isFalse();
    // the client in another module, for the rules to follow
    ActionBuilder client = m.module("lib/safe-action.ts").builder("client");
    assertThat(client.steps()).extracting(s -> s.name().text()).containsExactly("createSafeActionClient", "use");
    assertThat(client.steps().get(1).functions()).hasSize(1);
    assertThat(m.module("lib/safe-action.ts").serverActions()).isEmpty();
  }

  @Test
  void test_files_get_no_role_no_route_and_no_issues() {
    String page = "export default function P() { return null; }";
    Map<String, String> files = new java.util.HashMap<>(Map.of(
      "package.json", "{\"dependencies\":{\"next\":\"15.0.0\"}}",
      "app/page.tsx", page,
      "app/__tests__/page.tsx", "'use client';\nimport P from '../page';\nexport default P;",
      "app/page.test.tsx", "import P from './page';",
      "pages/__mocks__/user.ts", "export const u = 1;",
      "components/Button.stories.tsx", "'use client';\nimport { helper } from '../lib/helper';",
      "lib/helper.ts", "export const helper = 1;",
      "lib/db.spec.ts", "'use server';\nexport async function act(x) { await db.post.delete({ where: { id: x } }); }",
      "e2e/home.ts", "import P from '../app/page';",
      "tests/playwright/login.ts", "export const a = 1;"));
    NextModel m = NextModel.build(TestProjects.of(files));
    for (String p : List.of("app/__tests__/page.tsx", "app/page.test.tsx", "pages/__mocks__/user.ts", "components/Button.stories.tsx",
      "lib/db.spec.ts", "e2e/home.ts", "tests/playwright/login.ts")) {
      Module t = m.module(p);
      assertThat(t.isTest()).as(p).isTrue();
      assertThat(t.role()).as(p).isEqualTo(Role.SHARED);
      assertThat(t.reachedFromServer()).as(p).isFalse();
      assertThat(t.route()).as(p).isNull();
      assertThat(io.github.snakeinprod.nextjs.metrics.NxMetrics.fileValues(t, List.of()).values()).as(p).containsOnly(0);
      assertThat(io.github.snakeinprod.nextjs.sensor.Analyzer.all(m).analyze(t)).as(p).isEmpty();
    }
    // a story importing a module does not make it client code; the page keeps its role
    assertThat(m.module("lib/helper.ts").role()).isEqualTo(Role.SHARED);
    assertThat(m.module("lib/helper.ts").isTest()).isFalse();
    assertThat(m.module("app/page.tsx").role()).isEqualTo(Role.SERVER);
    assertThat(m.module("app/page.tsx").route()).isNotNull();
  }

  @Test
  void monorepo_packages_base_url_and_installed_version() {
    NextModel m = fixture("monorepo");
    assertThat(m.apps()).containsExactly(new NextModel.NextApp("apps/web/", 16));
    Module page = m.module("apps/web/app/page.jsx");
    assertThat(page.imports()).extracting(Import::resolved).containsExactly("packages/ui/src/button.tsx",
      "packages/ui/src/index.ts", "apps/web/lib/title.js", "apps/web/lib/chart.jsx");
    assertThat(page.imports().get(3).dynamic()).isTrue();
    assertThat(page.route()).isEqualTo(new Route("app", "page", "/", false));
    Module index = m.module("packages/ui/src/index.ts");
    assertThat(index.imports()).allMatch(Import::reExport);
    assertThat(index.imports().get(1).names()).isEqualTo(Map.of("*", "*"));
    // packages/ui is outside the app: its modules get their role from the imports
    assertThat(m.module("packages/ui/src/card.tsx").role()).isEqualTo(Role.SERVER);
    assertThat(m.module("packages/ui/src/button.tsx").role()).isEqualTo(Role.CLIENT);
    assertThat(m.nextMajor(m.module("packages/ui/src/card.tsx"))).isEqualTo(16);
    assertThat(m.clientModulesOf(page)).extracting(Module::path)
      .containsExactlyInAnyOrder("packages/ui/src/button.tsx", "apps/web/lib/chart.jsx");
  }

  @Test
  void no_next_dependency() {
    NextModel m = NextModel.build(TestProjects.of(Map.of("package.json", "{\"dependencies\":{\"react\":\"19\"}}",
      "app/page.tsx", "export default function P() { return null; }")));
    assertThat(m.hasNext()).isFalse();
    assertThat(m.module("app/page.tsx").route()).isNull();
  }

  @Test
  void import_and_export_forms() {
    String src = String.join("\n",
      "import D, { a as b, type T, c } from './x';",
      "import * as ns from './x'",
      "import type { U } from './x';",
      "import './side';",
      "export { e as f } from './x';",
      "export type { V } from './x';",
      "const r = require('./x');",
      "export { local as renamed, other };",
      "export default async function () {}",
      "export class K {}",
      "export const v = 1, w = 2;",
      "foo.import('nope'); obj.export;");
    NextModel m = NextModel.build(TestProjects.of(Map.of("a.ts", src, "x.ts", "", "side.js", "")));
    Module a = m.module("a.ts");
    assertThat(a.imports()).extracting(Import::spec).containsExactly("./x", "./x", "./x", "./side", "./x", "./x", "./x");
    assertThat(a.imports().get(0).names()).isEqualTo(Map.of("D", "default", "b", "a", "c", "c"));
    assertThat(a.imports().get(1).names()).isEqualTo(Map.of("ns", "*"));
    assertThat(a.imports()).extracting(Import::typeOnly).containsExactly(false, false, true, false, false, true, false);
    assertThat(a.imports().get(3).resolved()).isEqualTo("side.js");
    assertThat(a.imports().get(4).names()).isEqualTo(Map.of("f", "e"));
    assertThat(a.exports()).containsOnlyKeys("renamed", "other", "default", "K", "v");
    assertThat(a.exports().get("renamed").text()).isEqualTo("local");
    assertThat(a.functions()).containsOnlyKeys("default");
  }

  @Test
  void directive_prologue_only_at_the_top() {
    NextModel m = NextModel.build(TestProjects.of(Map.of(
      "a.js", "// header\n'use strict'; \"use client\"\nimport x from 'y';",
      "b.js", "import x from 'y';\n'use client';",
      "c.js", "async function act() { 'use cache'; }\nconst f = async () => { \"use server\" }")));
    assertThat(m.module("a.js").directives()).containsExactly(Directive.USE_CLIENT);
    assertThat(m.module("b.js").directives()).isEmpty();
    assertThat(m.module("c.js").functionsWithDirective("use cache")).hasSize(1);
    assertThat(m.module("c.js").serverActions()).extracting(a -> a.name().text()).containsExactly("f");
  }

  @Test
  void tsconfig_paths_longest_prefix_exact_match_and_package_extends() {
    Map<String, String> files = Map.of(
      "tsconfig.json", "{ \"extends\": \"@repo/config/next\", \"compilerOptions\": { \"baseUrl\": \"src\", "
        + "\"paths\": { \"@/*\": [\"*\"], \"@/ui/*\": [\"components/ui/*\"], \"config\": [\"conf/main\"] } } }",
      "node_modules/@repo/config/next.json", "{ \"compilerOptions\": { \"paths\": { \"~/*\": [\"./x/*\"] } } }",
      "src/components/ui/button.ts", "", "src/ui/button.ts", "", "src/conf/main.ts", "", "src/lib/a.ts", "",
      "app.ts", "import '@/ui/button'; import 'config'; import 'lib/a'; import '@/lib/a'; import '~/nope';");
    Module app = NextModel.build(TestProjects.of(files)).module("app.ts");
    assertThat(app.imports()).extracting(Import::resolved)
      .containsExactly("src/components/ui/button.ts", "src/conf/main.ts", "src/lib/a.ts", "src/lib/a.ts", null);
  }

  @Test
  void jsonc_comments_and_trailing_commas() {
    assertThat(Jsonc.parse("{ // c\n \"a\": \"//not a comment\", /* b */ \"b\": [1, 2,], }").toString())
      .isEqualTo("{\"a\":\"//not a comment\",\"b\":[1,2]}");
    assertThat(Jsonc.parse("not json")).isNull();
    assertThat(Jsonc.parse(null)).isNull();
  }

  @Test
  void major_versions() {
    assertThat(NextModel.major("^15.1.0")).isEqualTo(15);
    assertThat(NextModel.major("15.0.0-canary.3")).isEqualTo(15);
    assertThat(NextModel.major("~14")).isEqualTo(14);
    assertThat(NextModel.major("latest")).isZero();
    assertThat(NextModel.major(null)).isZero();
  }

  private static Role role(NextModel m, String path) {
    return m.module(path).role();
  }

  private static Route route(NextModel m, String path) {
    return m.module(path).route();
  }
}
