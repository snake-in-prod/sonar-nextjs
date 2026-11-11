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
    assertThat(keys).hasSize(15);
    assertThat(new HashSet<>(keys)).hasSameSizeAs(keys);
    assertThat(RuleCatalog.ALL).allSatisfy(s -> assertThat(s.factory().get()).isNotNull());
    assertThat(RuleCatalog.ALL.stream().filter(s -> !s.active()).map(RuleCatalog.Spec::key))
      .containsExactlyInAnyOrder("RouteHandlerWithoutAuth", "SearchParamsWithoutSuspense", "ImageFillWithoutSizes");
  }

  @Test
  void two_repositories_with_types_and_impacts() {
    RulesDefinition.Context context = new RulesDefinition.Context();
    new NextRulesDefinition().define(context);
    assertThat(context.repositories()).extracting(RulesDefinition.Repository::key).containsExactlyInAnyOrder("nextjs-js", "nextjs-ts");
    RulesDefinition.Repository ts = context.repository("nextjs-ts");
    assertThat(ts.language()).isEqualTo("ts");
    assertThat(ts.rules()).hasSize(15);
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
      assertThat(p.rules()).hasSize(12).allMatch(r -> r.repoKey().equals("nextjs-" + language));
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
        .doesNotContain("container-type", "innerHTML", "eval(");
      assertThat(js.chars().filter(c -> c == '{').count()).isEqualTo(js.chars().filter(c -> c == '}').count());
      assertThat(js.chars().filter(c -> c == '(').count()).isEqualTo(js.chars().filter(c -> c == ')').count());
    }
  }
}
