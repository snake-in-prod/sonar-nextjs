package io.github.snakeinprod.nextjs;

import org.sonar.api.web.page.Context;
import org.sonar.api.web.page.Page;
import org.sonar.api.web.page.PageDefinition;

/** The project page "Next.js" ({@code static/nextjs.js}). */
public class NextjsPage implements PageDefinition {

  @Override
  public void define(Context context) {
    context.addPage(Page.builder("nextjs/nextjs").setName("Next.js").setScope(Page.Scope.COMPONENT)
      .setComponentQualifiers(Page.Qualifier.PROJECT).build());
  }
}
