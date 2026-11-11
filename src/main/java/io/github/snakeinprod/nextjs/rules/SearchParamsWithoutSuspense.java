package io.github.snakeinprod.nextjs.rules;

/** useSearchParams should be wrapped in a Suspense boundary. Not implemented yet: reports nothing. */
public class SearchParamsWithoutSuspense implements NextRule {

  @Override
  public void check(RuleContext ctx) {
    // implemented in a later step
  }
}
