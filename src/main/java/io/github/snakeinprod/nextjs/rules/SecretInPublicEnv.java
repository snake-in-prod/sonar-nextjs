package io.github.snakeinprod.nextjs.rules;

/** Secrets should not be exposed through NEXT_PUBLIC_ variables. Not implemented yet: reports nothing. */
public class SecretInPublicEnv implements NextRule {

  @Override
  public void check(RuleContext ctx) {
    // implemented in a later step
  }
}
