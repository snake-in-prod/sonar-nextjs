package io.github.snakeinprod.nextjs.metrics;

import java.util.stream.Stream;
import org.sonar.api.ce.measure.Component;
import org.sonar.api.ce.measure.Measure;
import org.sonar.api.ce.measure.MeasureComputer;
import org.sonar.api.measures.Metric;

/** Sums the integer nx_* measures from files to directories and the project, then derives nx_client_ratio. */
public class NxAggregator implements MeasureComputer {

  @Override
  public MeasureComputerDefinition define(MeasureComputerDefinitionContext def) {
    String[] outputs = Stream.concat(NxMetrics.SUMMED.stream().map(Metric::getKey), Stream.of(NxMetrics.CLIENT_RATIO.getKey()))
      .toArray(String[]::new);
    return def.newDefinitionBuilder().setOutputMetrics(outputs).build();
  }

  @Override
  public void compute(MeasureComputerContext ctx) {
    if (ctx.getComponent().getType() == Component.Type.FILE) {
      return;
    }
    for (Metric<Integer> m : NxMetrics.SUMMED) {
      int sum = 0;
      boolean any = false;
      for (Measure child : ctx.getChildrenMeasures(m.getKey())) {
        sum += child.getIntValue();
        any = true;
      }
      if (any && ctx.getMeasure(m.getKey()) == null) {
        ctx.addMeasure(m.getKey(), sum);
      }
    }
    int client = value(ctx, NxMetrics.CLIENT_MODULES);
    int all = client + value(ctx, NxMetrics.SERVER_MODULES) + value(ctx, NxMetrics.SHARED_MODULES);
    if (all > 0 && ctx.getMeasure(NxMetrics.CLIENT_RATIO.getKey()) == null) {
      ctx.addMeasure(NxMetrics.CLIENT_RATIO.getKey(), ratio(client, all));
    }
  }

  /** Percent with one decimal. */
  static double ratio(int part, int all) {
    return Math.round(1000.0 * part / all) / 10.0;
  }

  private static int value(MeasureComputerContext ctx, Metric<Integer> metric) {
    Measure m = ctx.getMeasure(metric.getKey());
    return m == null ? 0 : m.getIntValue();
  }
}
