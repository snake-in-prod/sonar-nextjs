package io.github.snakeinprod.nextjs.rules;

import io.github.snakeinprod.nextjs.RuleVerifier;
import org.junit.jupiter.api.Test;

class NextjsRulesBatch4Test {

  @Test
  void async_client_component() {
    RuleVerifier.verify("AsyncClientComponent");
  }

  @Test
  void client_hook_in_server_component() {
    RuleVerifier.verify("ClientHookInServerComponent");
  }

  @Test
  void sync_params_access() {
    RuleVerifier.verify("SyncParamsAccess");
  }

  @Test
  void edge_runtime_node_import() {
    RuleVerifier.verify("EdgeRuntimeNodeImport");
    RuleVerifier.verify("EdgeRuntimeNodeImportNodeMiddleware", new EdgeRuntimeNodeImport());
  }
}
