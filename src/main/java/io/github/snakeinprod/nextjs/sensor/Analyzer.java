package io.github.snakeinprod.nextjs.sensor;

import io.github.snakeinprod.nextjs.model.Module;
import io.github.snakeinprod.nextjs.model.NextModel;
import io.github.snakeinprod.nextjs.parse.Token;
import io.github.snakeinprod.nextjs.rules.NextRule;
import io.github.snakeinprod.nextjs.rules.RuleCatalog;
import io.github.snakeinprod.nextjs.rules.RuleContext;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Runs rules on the modules of a model. Shared by the sensor, the command line scanner and the tests, so that all three
 * see the same results. A line with a NOSONAR comment gets no issue.
 */
public final class Analyzer {

  /** An issue; lines 1-based, columns 0-based, {@code endColumn} exclusive. */
  public record Issue(String path, String rule, int line, int column, int endLine, int endColumn, String message) {
  }

  private final NextModel model;
  private final Map<String, NextRule> rules;

  /** {@code rules}: rule key to a fresh instance, run in this order. */
  public Analyzer(NextModel model, Map<String, NextRule> rules) {
    this.model = model;
    this.rules = rules;
  }

  /** Every rule of the catalog, active in the profile or not. */
  public static Analyzer all(NextModel model) {
    Map<String, NextRule> rules = new LinkedHashMap<>();
    for (RuleCatalog.Spec s : RuleCatalog.ALL) {
      rules.put(s.key(), s.factory().get());
    }
    return new Analyzer(model, rules);
  }

  public NextModel model() {
    return model;
  }

  /** The issues of one module, sorted by position; none in a test file. Throws only on an internal error of a rule; callers skip the module. */
  public List<Issue> analyze(Module module) {
    if (module.isTest()) {
      return List.of();
    }
    Set<Integer> nosonar = new HashSet<>();
    for (Token c : module.file().comments()) {
      if (c.text().contains("NOSONAR")) {
        nosonar.add(c.line());
      }
    }
    List<Issue> issues = new ArrayList<>();
    for (Map.Entry<String, NextRule> e : rules.entrySet()) {
      String key = e.getKey();
      e.getValue().check(new RuleContext() {
        @Override
        public Module module() {
          return module;
        }

        @Override
        public NextModel model() {
          return model;
        }

        @Override
        public void report(Token from, Token to, String message) {
          if (!nosonar.contains(from.line())) {
            issues.add(new Issue(module.path(), key, from.line(), from.column(), to.endLine(), to.endColumn(), message));
          }
        }
      });
    }
    issues.sort((a, b) -> a.line() != b.line() ? Integer.compare(a.line(), b.line()) : Integer.compare(a.column(), b.column()));
    return List.copyOf(issues);
  }
}
