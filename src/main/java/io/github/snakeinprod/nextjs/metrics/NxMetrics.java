package io.github.snakeinprod.nextjs.metrics;

import io.github.snakeinprod.nextjs.model.Module;
import io.github.snakeinprod.nextjs.model.Role;
import io.github.snakeinprod.nextjs.sensor.Analyzer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.sonar.api.measures.Metric;
import org.sonar.api.measures.Metrics;

/**
 * The nx_* measures. Integer ones are saved on files and summed to directories and the project by
 * {@link NxAggregator}, which also derives nx_client_ratio. nx_routes_json is saved on the project only.
 */
public class NxMetrics implements Metrics {

  public static final String DOMAIN = "Next.js";

  public static final Metric<Integer> ROUTES = intMetric("nx_routes", "Routes",
    "App Router pages and route handlers, Pages Router pages and API routes", Metric.DIRECTION_NONE);
  public static final Metric<Integer> CLIENT_MODULES = intMetric("nx_client_modules", "Client modules",
    "Modules in the browser bundle: \"use client\" modules and what they import", Metric.DIRECTION_WORST);
  public static final Metric<Integer> SERVER_MODULES = intMetric("nx_server_modules", "Server modules",
    "Modules that run on the server only: server components, route handlers, Server Actions and what they import",
    Metric.DIRECTION_NONE);
  public static final Metric<Integer> SHARED_MODULES = intMetric("nx_shared_modules", "Shared modules",
    "Modules neither client nor server: Pages Router pages, modules no entry imports", Metric.DIRECTION_NONE);
  public static final Metric<Double> CLIENT_RATIO = new Metric.Builder("nx_client_ratio", "Client ratio", Metric.ValueType.PERCENT)
    .setDescription("Client modules / all modules, in %").setDomain(DOMAIN).setDirection(Metric.DIRECTION_WORST)
    .setQualitative(false).setBestValue(0.0).setWorstValue(100.0).create();
  public static final Metric<Integer> SERVER_ACTIONS = intMetric("nx_server_actions", "Server Actions",
    "Exported functions of \"use server\" modules and inline \"use server\" functions", Metric.DIRECTION_NONE);
  public static final Metric<Integer> UNPROTECTED_ENDPOINTS = new Metric.Builder("nx_unprotected_endpoints", "Unprotected endpoints",
    Metric.ValueType.INT).setDescription("Server Actions and route handlers that change data without checking the caller (whether "
      + "or not their rules are active)").setDomain(DOMAIN).setDirection(Metric.DIRECTION_WORST).setQualitative(true)
    .setBestValue(0.0).create();
  public static final Metric<Integer> DYNAMIC_ROUTES = intMetric("nx_dynamic_routes", "Dynamic routes",
    "Routes with a dynamic or catch-all segment", Metric.DIRECTION_NONE);
  public static final Metric<String> ROUTES_JSON = new Metric.Builder("nx_routes_json", "Routes (JSON)", Metric.ValueType.DATA)
    .setDescription("JSON: every route and Server Action with its kind, runtime, client modules and issues").setDomain(DOMAIN)
    .setQualitative(false).setHidden(true).create();

  /** Integer measures saved on files and summed up. */
  public static final List<Metric<Integer>> SUMMED = List.of(ROUTES, CLIENT_MODULES, SERVER_MODULES, SHARED_MODULES, SERVER_ACTIONS,
    UNPROTECTED_ENDPOINTS, DYNAMIC_ROUTES);

  /** The rules whose issues count as unprotected endpoints. */
  public static final Set<String> AUTH_RULES = Set.of("ServerActionWithoutAuth", "RouteHandlerWithoutAuth");

  private static Metric<Integer> intMetric(String key, String name, String description, int direction) {
    return new Metric.Builder(key, name, Metric.ValueType.INT).setDescription(description).setDomain(DOMAIN).setDirection(direction)
      .setQualitative(false).create();
  }

  /** The file measures of a module; {@code issues} are all the module's issues, every rule included. */
  public static Map<Metric<Integer>, Integer> fileValues(Module m, List<Analyzer.Issue> issues) {
    Map<Metric<Integer>, Integer> out = new LinkedHashMap<>();
    boolean route = m.route() != null && m.route().isRoute();
    out.put(ROUTES, route ? 1 : 0);
    out.put(CLIENT_MODULES, m.role() == Role.CLIENT ? 1 : 0);
    out.put(SERVER_MODULES, m.role() == Role.SERVER ? 1 : 0);
    out.put(SHARED_MODULES, m.role() == Role.SHARED ? 1 : 0);
    out.put(SERVER_ACTIONS, m.serverActions().size());
    out.put(UNPROTECTED_ENDPOINTS, (int) issues.stream().filter(i -> AUTH_RULES.contains(i.rule())).count());
    out.put(DYNAMIC_ROUTES, route && m.route().dynamic() ? 1 : 0);
    return out;
  }

  @Override
  @SuppressWarnings("rawtypes")
  public List<Metric> getMetrics() {
    return List.of(ROUTES, CLIENT_MODULES, SERVER_MODULES, SHARED_MODULES, CLIENT_RATIO, SERVER_ACTIONS, UNPROTECTED_ENDPOINTS,
      DYNAMIC_ROUTES, ROUTES_JSON);
  }
}
