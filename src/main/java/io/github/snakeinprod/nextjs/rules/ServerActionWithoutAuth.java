package io.github.snakeinprod.nextjs.rules;

/** Server Actions that change data should check the session. Not implemented yet: reports nothing. */
public class ServerActionWithoutAuth implements NextRule {

  @Override
  public void check(RuleContext ctx) {
    // implemented in a later step
  }
}
