package io.github.snakeinprod.nextjs.rules;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.snakeinprod.nextjs.RuleVerifier;
import org.junit.jupiter.api.Test;

class NextjsRulesBatch1Test {

  @Test
  void server_code_in_client_bundle() {
    RuleVerifier.verify("ServerCodeInClientBundle");
  }

  @Test
  void server_env_in_client() {
    RuleVerifier.verify("ServerEnvInClient");
  }

  @Test
  void secret_in_public_env() {
    RuleVerifier.verify("SecretInPublicEnv");
  }

  @Test
  void rule_descriptions() {
    for (String key : new String[] {"ServerCodeInClientBundle", "ServerEnvInClient", "SecretInPublicEnv"}) {
      assertThat(RuleCatalog.spec(key).html()).as(key).contains("<h2>Noncompliant code example</h2>", "<h2>Compliant solution</h2>");
    }
  }
}
