package io.github.snakeinprod.nextjs;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.snakeinprod.nextjs.rules.NextProfiles;
import io.github.snakeinprod.nextjs.rules.NextRulesDefinition;
import io.github.snakeinprod.nextjs.rules.RuleCatalog;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sonar.api.issue.impact.Severity;
import org.sonar.api.issue.impact.SoftwareQuality;
import org.sonar.api.rules.RuleType;
import org.sonar.api.server.profile.BuiltInQualityProfilesDefinition;
import org.sonar.api.server.rule.RulesDefinition;
import org.sonar.api.web.page.Context;
import org.sonar.api.web.page.Page;

class PluginTest {

  @Test
  void catalog() {
    List<String> keys = RuleCatalog.ALL.stream().map(RuleCatalog.Spec::key).toList();
    assertThat(keys).hasSize(16);
    assertThat(new HashSet<>(keys)).hasSameSizeAs(keys);
    assertThat(RuleCatalog.ALL).allSatisfy(s -> assertThat(s.factory().get()).isNotNull());
    assertThat(RuleCatalog.ALL.stream().filter(s -> !s.active()).map(RuleCatalog.Spec::key))
      .containsExactlyInAnyOrder("SearchParamsWithoutSuspense", "ImageFillWithoutSizes", "MiddlewareMatcherGap");
  }

  @Test
  void two_repositories_with_types_and_impacts() {
    RulesDefinition.Context context = new RulesDefinition.Context();
    new NextRulesDefinition().define(context);
    assertThat(context.repositories()).extracting(RulesDefinition.Repository::key).containsExactlyInAnyOrder("nextjs-js", "nextjs-ts");
    RulesDefinition.Repository ts = context.repository("nextjs-ts");
    assertThat(ts.language()).isEqualTo("ts");
    assertThat(ts.rules()).hasSize(16);
    RulesDefinition.Rule secret = ts.rule("SecretInPublicEnv");
    assertThat(secret.type()).isEqualTo(RuleType.VULNERABILITY);
    assertThat(secret.defaultImpacts()).containsEntry(SoftwareQuality.SECURITY, Severity.HIGH);
    assertThat(secret.tags()).contains("nextjs");
    assertThat(ts.rule("AsyncClientComponent").defaultImpacts()).containsEntry(SoftwareQuality.RELIABILITY, Severity.MEDIUM);
    assertThat(ts.rule("ImageFillWithoutSizes").defaultImpacts()).containsEntry(SoftwareQuality.MAINTAINABILITY, Severity.LOW);
    assertThat(ts.rule("ServerActionWithoutAuth").htmlDescription()).contains("<h2>Noncompliant code example</h2>");
  }

  @Test
  void built_in_profile_for_js_and_ts() {
    BuiltInQualityProfilesDefinition.Context context = new BuiltInQualityProfilesDefinition.Context();
    new NextProfiles().define(context);
    for (String language : List.of("js", "ts")) {
      BuiltInQualityProfilesDefinition.BuiltInQualityProfile p = context.profile(language, "Next.js");
      assertThat(p.isDefault()).isFalse();
      assertThat(p.rules()).hasSize(13).allMatch(r -> r.repoKey().equals("nextjs-" + language));
    }
  }

  @Test
  void page_script_registers_the_page_key() throws IOException {
    Context context = new Context();
    new NextjsPage().define(context);
    String key = context.getPages().stream().map(Page::getKey).findFirst().orElseThrow();
    // SonarQube serves the page from static/<plugin key>/<page>.js -> static/<page>.js in the jar
    try (InputStream in = getClass().getResourceAsStream("/static/" + key.substring(key.indexOf('/') + 1) + ".js")) {
      assertThat(in).isNotNull();
      String js = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      assertThat(js).contains("window.registerExtension('" + key + "'");
      // SonarQube places extension pages in a two-column grid
      assertThat(js).contains("options.el.style.gridColumn = '1 / -1'", "options.el.style.width = '100%'")
        .doesNotContain("container-type", "innerHTML", "outerHTML", "insertAdjacentHTML", "document.write", "eval(");
      // charts are inline SVG built node by node, on the branch or pull request being viewed
      assertThat(js).contains("createElementNS", "options.branchLike", "nx_routes_json", "api/' + path", "'measures/component'",
        "'issues/search'", "'qualitygates/project_status'", "'nextjs-js'", "'nextjs-ts'", "aria-label");
      // a pull request holds only its changed files: the page says so and drops the whole-app route counts from the tiles
      assertThat(js).contains("var whole = scope.pullRequest === undefined", "sub: whole && routes.length", "(whole ? ' · '");
      // ... unless the analysis saved the whole app's totals: the tiles and the split prefer them, and the note says so
      assertThat(js).contains("'nx_app_json'", "JSON.parse(values.nx_app_json)", "values[k] = app[k]", "whole = true",
        "issues by rule and the quality gate");
      assertThat(js.chars().filter(c -> c == '{').count()).isEqualTo(js.chars().filter(c -> c == '}').count());
      assertThat(js.chars().filter(c -> c == '(').count()).isEqualTo(js.chars().filter(c -> c == ')').count());
    }
  }
}
