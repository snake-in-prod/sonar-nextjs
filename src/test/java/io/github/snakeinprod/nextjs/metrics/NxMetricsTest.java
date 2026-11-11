package io.github.snakeinprod.nextjs.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.snakeinprod.nextjs.TestProjects;
import io.github.snakeinprod.nextjs.model.Module;
import io.github.snakeinprod.nextjs.model.NextModel;
import io.github.snakeinprod.nextjs.sensor.Analyzer;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.sonar.api.measures.Metric;

class NxMetricsTest {

  private final NextModel model = NextModel.build(TestProjects.of(TestProjects.files(Path.of("src/test/resources/model/basic"))));

  @Test
  void file_values_sum_to_the_project() {
    Analyzer analyzer = Analyzer.all(model);
    Map<String, Integer> totals = new HashMap<>();
    for (Module m : model.modules()) {
      List<Analyzer.Issue> issues = analyzer.analyze(m);
      for (Map.Entry<Metric<Integer>, Integer> e : NxMetrics.fileValues(m, issues).entrySet()) {
        totals.merge(e.getKey().getKey(), e.getValue(), Integer::sum);
      }
    }
    // unprotected: save and remove of src/actions/save.ts, POST of /api/users
    // pages /, /blog/[slug], /docs/[[...path]], /inline, route /api/users, Pages Router /about and /api/hello
    assertThat(totals).containsEntry("nx_routes", 7).containsEntry("nx_dynamic_routes", 2).containsEntry("nx_server_actions", 3)
      .containsEntry("nx_client_modules", 2).containsEntry("nx_shared_modules", 3).containsEntry("nx_unprotected_endpoints", 3);
    assertThat(totals.get("nx_client_modules") + totals.get("nx_server_modules") + totals.get("nx_shared_modules"))
      .isEqualTo(model.modules().size());
    assertThat(NxAggregator.ratio(2, 19)).isEqualTo(10.5);
  }

  @Test
  void unprotected_endpoints_count_the_auth_rules() {
    Module m = model.module("src/actions/save.ts");
    List<Analyzer.Issue> issues = List.of(new Analyzer.Issue(m.path(), "ServerActionWithoutAuth", 5, 0, 5, 1, "x"),
      new Analyzer.Issue(m.path(), "OpenRedirect", 6, 0, 6, 1, "x"));
    assertThat(NxMetrics.fileValues(m, issues)).containsEntry(NxMetrics.UNPROTECTED_ENDPOINTS, 1);
  }

  @Test
  void routes_json() {
    JsonArray routes = JsonParser.parseString(RoutesJson.build(model, Map.of())).getAsJsonArray();
    JsonObject home = find(routes, "/");
    assertThat(home.get("kind").getAsString()).isEqualTo("page");
    assertThat(home.get("clientModules").getAsInt()).isEqualTo(2);
    assertThat(home.get("runtime").getAsString()).isEqualTo("node");
    assertThat(find(routes, "/api/users").get("kind").getAsString()).isEqualTo("route");
    assertThat(find(routes, "/inline").get("runtime").getAsString()).isEqualTo("edge");
    assertThat(find(routes, "/blog/[slug]").get("dynamic").getAsBoolean()).isTrue();
    assertThat(find(routes, "src/actions/save.ts#remove").get("kind").getAsString()).isEqualTo("action");
    assertThat(routes).hasSize(10);
  }

  private static JsonObject find(JsonArray routes, String path) {
    for (var e : routes) {
      if (e.getAsJsonObject().get("path").getAsString().equals(path)) {
        return e.getAsJsonObject();
      }
    }
    throw new AssertionError("no route " + path);
  }
}
