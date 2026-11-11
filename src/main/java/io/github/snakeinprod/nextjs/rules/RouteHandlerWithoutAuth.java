package io.github.snakeinprod.nextjs.rules;

/** Route handlers that change data should check the caller. Not implemented yet: reports nothing. */
public class RouteHandlerWithoutAuth implements NextRule {

  @Override
  public void check(RuleContext ctx) {
    // implemented in a later step
  }
}
