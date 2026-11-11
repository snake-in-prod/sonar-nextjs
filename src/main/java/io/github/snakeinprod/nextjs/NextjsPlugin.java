package io.github.snakeinprod.nextjs;

import io.github.snakeinprod.nextjs.metrics.NxAggregator;
import io.github.snakeinprod.nextjs.metrics.NxMetrics;
import io.github.snakeinprod.nextjs.rules.NextProfiles;
import io.github.snakeinprod.nextjs.rules.NextRulesDefinition;
import io.github.snakeinprod.nextjs.sensor.NextjsSensor;
import org.sonar.api.Plugin;

/** Entry point: rules, the built-in profile, the sensor, the metrics and the page. No settings. */
public class NextjsPlugin implements Plugin {

  @Override
  public void define(Context context) {
    context.addExtensions(NextRulesDefinition.class, NextProfiles.class, NxMetrics.class, NextjsSensor.class, NxAggregator.class,
      NextjsPage.class);
  }
}
