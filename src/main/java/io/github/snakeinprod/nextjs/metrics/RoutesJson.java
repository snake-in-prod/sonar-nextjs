package io.github.snakeinprod.nextjs.metrics;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.snakeinprod.nextjs.model.Module;
import io.github.snakeinprod.nextjs.model.NextModel;
import io.github.snakeinprod.nextjs.model.ServerAction;
import io.github.snakeinprod.nextjs.sensor.Analyzer;
import java.util.List;
import java.util.Map;

/**
 * nx_routes_json: an array with one object per route (App Router page or route handler, Pages Router page or API route)
 * and per Server Action: {@code path} (URL path, or file#function for an action), {@code kind} (page, route or action),
 * {@code file}, {@code runtime} (node or edge), {@code dynamic}, {@code clientModules} (client modules it pulls in) and
 * {@code issues} (issues in its file).
 */
public final class RoutesJson {

  private RoutesJson() {
  }

  public static String build(NextModel model, Map<String, List<Analyzer.Issue>> issues) {
    JsonArray out = new JsonArray();
    for (Module m : model.modules()) {
      int count = issues.getOrDefault(m.path(), List.of()).size();
      if (m.route() != null && m.route().isRoute()) {
        out.add(entry(m.route().url(), m.route().isEndpoint() ? "route" : "page", m, m.route().dynamic(),
          model.clientModulesOf(m).size(), count));
      }
      for (ServerAction a : m.serverActions()) {
        out.add(entry(m.path() + "#" + a.name().text(), "action", m, false, 0, count));
      }
    }
    return out.toString();
  }

  private static JsonObject entry(String path, String kind, Module m, boolean dynamic, int clients, int issues) {
    JsonObject o = new JsonObject();
    o.addProperty("path", path);
    o.addProperty("kind", kind);
    o.addProperty("file", m.path());
    o.addProperty("runtime", m.runtime());
    o.addProperty("dynamic", dynamic);
    o.addProperty("clientModules", clients);
    o.addProperty("issues", issues);
    return o;
  }
}
