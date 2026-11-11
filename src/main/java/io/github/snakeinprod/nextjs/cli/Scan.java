package io.github.snakeinprod.nextjs.cli;

import io.github.snakeinprod.nextjs.metrics.NxMetrics;
import io.github.snakeinprod.nextjs.metrics.RoutesJson;
import io.github.snakeinprod.nextjs.model.Module;
import io.github.snakeinprod.nextjs.model.NextModel;
import io.github.snakeinprod.nextjs.model.Project;
import io.github.snakeinprod.nextjs.sensor.Analyzer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.sonar.api.measures.Metric;

/**
 * Runs every rule on a directory without SonarQube, for checking rules on real apps:
 * {@code java -cp target/classes:<gson.jar> io.github.snakeinprod.nextjs.cli.Scan <dir> [--routes] [--modules]}. Prints
 * one issue per line ({@code path:line:column rule message}), then the measures and a count per rule; {@code --routes}
 * adds the routes JSON, {@code --modules} every module with its role and route.
 */
public final class Scan {

  /** Directories never read: dependencies, build output, hidden directories. */
  private static final Set<String> SKIPPED_DIRS = Set.of("node_modules", "dist", "build", "out", "coverage");

  private Scan() {
  }

  public static void main(String[] args) throws IOException {
    Path root = Path.of(args[0]).toAbsolutePath().normalize();
    List<String> flags = List.of(args).subList(1, args.length);
    Set<String> paths = new TreeSet<>();
    try (Stream<Path> files = Files.walk(root)) {
      files.filter(Files::isRegularFile).forEach(p -> {
        String rel = root.relativize(p).toString().replace('\\', '/');
        for (String part : rel.split("/")) {
          if (part.startsWith(".") || SKIPPED_DIRS.contains(part)) {
            return;
          }
        }
        paths.add(rel);
      });
    }
    NextModel model = NextModel.build(project(root, paths));
    if (!model.hasNext()) {
      System.out.println("== no package.json declares a \"next\" dependency");
      return;
    }
    model.skipped().forEach(s -> System.err.println("SKIPPED " + s));
    Analyzer analyzer = Analyzer.all(model);
    Map<String, Integer> perRule = new TreeMap<>();
    Map<String, Integer> totals = new java.util.LinkedHashMap<>();
    Map<String, List<Analyzer.Issue>> all = new HashMap<>();
    for (Module m : model.modules()) {
      List<Analyzer.Issue> issues;
      try {
        issues = analyzer.analyze(m);
      } catch (RuntimeException e) {
        System.err.println("SKIPPED " + m.path() + ": " + e);
        continue;
      }
      all.put(m.path(), issues);
      for (Analyzer.Issue i : issues) {
        System.out.println(i.path() + ":" + i.line() + ":" + i.column() + " " + i.rule() + " " + i.message());
        perRule.merge(i.rule(), 1, Integer::sum);
      }
      for (Map.Entry<Metric<Integer>, Integer> e : NxMetrics.fileValues(m, issues).entrySet()) {
        totals.merge(e.getKey().getKey(), e.getValue(), Integer::sum);
      }
      if (flags.contains("--modules")) {
        System.out.println("-- " + m.path() + " " + m.role() + (m.reachedFromServer() ? " server-reached" : "")
          + (m.directives().isEmpty() ? "" : " " + m.directives()) + (m.route() == null ? "" : " " + m.route()));
      }
    }
    model.apps().forEach(a -> System.out.println("== next app " + (a.dir().isEmpty() ? "." : a.dir()) + " major " + a.major()));
    System.out.println("== " + model.modules().size() + " modules");
    totals.forEach((k, v) -> System.out.println("== " + k + " " + v));
    perRule.forEach((k, v) -> System.out.println("== " + k + " " + v));
    if (flags.contains("--routes")) {
      System.out.println(RoutesJson.build(model, all));
    }
  }

  private static Project project(Path root, Set<String> paths) {
    return new Project() {
      @Override
      public Set<String> paths() {
        return paths;
      }

      @Override
      public String read(String path) {
        Path p = root.resolve(path).normalize();
        try {
          return p.startsWith(root) && Files.isRegularFile(p) ? Files.readString(p, StandardCharsets.UTF_8) : null;
        } catch (IOException | UncheckedIOException e) {
          return null;
        }
      }
    };
  }
}
