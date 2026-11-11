package io.github.snakeinprod.nextjs.rules;

/** Client components should not import server-only code. Not implemented yet: reports nothing. */
public class ServerCodeInClientBundle implements NextRule {

  @Override
  public void check(RuleContext ctx) {
    // implemented in a later step
  }
}
