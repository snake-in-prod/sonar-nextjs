package io.github.snakeinprod.nextjs.rules;

/** Server components should not call client hooks. Not implemented yet: reports nothing. */
public class ClientHookInServerComponent implements NextRule {

  @Override
  public void check(RuleContext ctx) {
    // implemented in a later step
  }
}
