package io.github.snakeinprod.nextjs;

import org.junit.jupiter.api.Test;

class NextjsRulesBatch5Test {

  @Test
  void metadata_exported_twice() {
    RuleVerifier.verify("MetadataExportedTwice");
  }

  @Test
  void search_params_without_suspense() {
    RuleVerifier.verify("SearchParamsWithoutSuspense");
  }

  @Test
  void image_fill_without_sizes() {
    RuleVerifier.verify("ImageFillWithoutSizes");
  }
}
