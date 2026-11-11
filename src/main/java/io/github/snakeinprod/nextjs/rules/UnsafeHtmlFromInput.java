package io.github.snakeinprod.nextjs.rules;

/** dangerouslySetInnerHTML should not render unsanitized input. Not implemented yet: reports nothing. */
public class UnsafeHtmlFromInput implements NextRule {

  @Override
  public void check(RuleContext ctx) {
    // implemented in a later step
  }
}
