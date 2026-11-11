package io.github.snakeinprod.nextjs.rules;

import io.github.snakeinprod.nextjs.RuleVerifier;
import org.junit.jupiter.api.Test;

/** Server Actions and route handlers open to anyone or fed unchecked input. */
class NextjsRulesBatch2Test {

  @Test
  void server_action_without_auth() {
    RuleVerifier.verify("ServerActionWithoutAuth");
  }

  @Test
  void server_action_without_validation() {
    RuleVerifier.verify("ServerActionWithoutValidation");
  }

  @Test
  void route_handler_without_auth() {
    RuleVerifier.verify("RouteHandlerWithoutAuth");
  }

  @Test
  void route_handler_guarded_by_middleware() {
    RuleVerifier.verify("RouteHandlerWithoutAuthMiddleware", new RouteHandlerWithoutAuth());
  }
}
