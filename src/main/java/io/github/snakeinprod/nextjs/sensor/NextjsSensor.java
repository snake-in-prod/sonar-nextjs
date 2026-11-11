package io.github.snakeinprod.nextjs.sensor;

import io.github.snakeinprod.nextjs.metrics.NxMetrics;
import io.github.snakeinprod.nextjs.metrics.RoutesJson;
import io.github.snakeinprod.nextjs.model.Module;
import io.github.snakeinprod.nextjs.model.NextModel;
import io.github.snakeinprod.nextjs.model.Project;
import io.github.snakeinprod.nextjs.rules.RuleCatalog;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.TextRange;
import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.SensorDescriptor;
import org.sonar.api.batch.sensor.issue.NewIssue;
import org.sonar.api.measures.Metric;
import org.sonar.api.rule.RuleKey;

/**
 * Builds the model of the project's js and ts files once, runs the rules on every module, saves the issues of the
 * active ones and the nx_* measures. Does nothing when no package.json of the project declares a "next" dependency.
 */
public class NextjsSensor implements Sensor {

  private static final Logger LOG = LoggerFactory.getLogger(NextjsSensor.class);

  @Override
  public void describe(SensorDescriptor descriptor) {
    descriptor.name("Next.js").onlyOnLanguages(RuleCatalog.LANGUAGES.toArray(String[]::new));
  }

  @Override
  public void execute(SensorContext context) {
    FileSystem fs = context.fileSystem();
    Map<String, InputFile> byPath = new HashMap<>();
    for (InputFile f : fs.inputFiles(fs.predicates().hasLanguages(RuleCatalog.LANGUAGES))) {
      byPath.put(f.relativePath(), f);
    }
    Path base = fs.baseDir().toPath().normalize();
    NextModel model = NextModel.build(new Project() {
      @Override
      public Set<String> paths() {
        return byPath.keySet();
      }

      @Override
      public String read(String path) {
        InputFile f = byPath.get(path);
        Path p = base.resolve(path).normalize();
        try {
          return f != null ? f.contents()
            : p.startsWith(base) && Files.isRegularFile(p) ? Files.readString(p, StandardCharsets.UTF_8) : null;
        } catch (IOException | UncheckedIOException e) {
          return null;
        }
      }
    });
    if (!model.hasNext()) {
      LOG.info("Next.js: no package.json declares a \"next\" dependency, nothing to analyse");
      return;
    }
    model.skipped().forEach(s -> LOG.warn("Next.js: skipped {}", s));
    Analyzer analyzer = Analyzer.all(model);
    Map<String, List<Analyzer.Issue>> saved = new HashMap<>();
    Map<String, Integer> totals = new HashMap<>();
    int failed = 0;
    for (Module m : model.modules()) {
      InputFile f = byPath.get(m.path());
      if (f == null) {
        continue;
      }
      List<Analyzer.Issue> issues;
      try {
        issues = analyzer.analyze(m);
      } catch (RuntimeException e) {
        failed++;
        LOG.warn("Next.js: skipped {}: {}", m.path(), e.toString());
        issues = List.of();
      }
      saved.put(m.path(), save(context, f, issues));
      for (Map.Entry<Metric<Integer>, Integer> e : NxMetrics.fileValues(m, issues).entrySet()) {
        context.<Integer>newMeasure().on(f).forMetric(e.getKey()).withValue(e.getValue()).save();
        totals.merge(e.getKey().getKey(), e.getValue(), Integer::sum);
      }
    }
    context.<String>newMeasure().on(context.project()).forMetric(NxMetrics.ROUTES_JSON).withValue(RoutesJson.build(model, saved))
      .save();
    // a pull request keeps the measures of its changed files only: the whole app's numbers for the page
    context.<String>newMeasure().on(context.project()).forMetric(NxMetrics.APP_JSON).withValue(NxMetrics.appJson(totals)).save();
    LOG.info("Next.js: {} modules analysed, {} failed", model.modules().size(), failed);
  }

  /** Saves the issues of active rules; returns them. */
  private static List<Analyzer.Issue> save(SensorContext context, InputFile f, List<Analyzer.Issue> found) {
    List<Analyzer.Issue> out = new ArrayList<>();
    for (Analyzer.Issue i : found) {
      RuleKey key = RuleKey.of(RuleCatalog.repository(f.language()), i.rule());
      if (context.activeRules().find(key) == null) {
        continue;
      }
      NewIssue issue = context.newIssue().forRule(key);
      issue.at(issue.newLocation().on(f).at(range(f, i)).message(i.message())).save();
      out.add(i);
    }
    return out;
  }

  private static TextRange range(InputFile f, Analyzer.Issue i) {
    try {
      int endLine = Math.min(i.endLine(), f.lines());
      int endColumn = i.endLine() > f.lines() ? f.selectLine(f.lines()).end().lineOffset() : i.endColumn();
      TextRange r = f.newRange(i.line(), i.column(), endLine, endColumn);
      return r.start().equals(r.end()) ? f.selectLine(i.line()) : r;
    } catch (IllegalArgumentException e) {
      return f.selectLine(Math.max(1, Math.min(i.line(), f.lines())));
    }
  }
}
