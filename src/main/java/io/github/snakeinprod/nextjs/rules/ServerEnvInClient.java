package io.github.snakeinprod.nextjs.rules;

/** Client code should only read NEXT_PUBLIC_ environment variables. Not implemented yet: reports nothing. */
public class ServerEnvInClient implements NextRule {

  @Override
  public void check(RuleContext ctx) {
    // implemented in a later step
  }
}
