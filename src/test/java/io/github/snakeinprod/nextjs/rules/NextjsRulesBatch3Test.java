package io.github.snakeinprod.nextjs.rules;

import io.github.snakeinprod.nextjs.RuleVerifier;
import org.junit.jupiter.api.Test;

class NextjsRulesBatch3Test {

  @Test
  void personalized_cached_fetch() {
    RuleVerifier.verify("PersonalizedCachedFetch");
  }

  @Test
  void open_redirect() {
    RuleVerifier.verify("OpenRedirect");
  }

  @Test
  void unsafe_html_from_input() {
    RuleVerifier.verify("UnsafeHtmlFromInput");
  }
}
