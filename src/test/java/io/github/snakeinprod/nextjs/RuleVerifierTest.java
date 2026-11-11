package io.github.snakeinprod.nextjs;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.snakeinprod.nextjs.model.Directive;
import io.github.snakeinprod.nextjs.model.Import;
import io.github.snakeinprod.nextjs.model.Module;
import io.github.snakeinprod.nextjs.model.Role;
import io.github.snakeinprod.nextjs.parse.Token;
import io.github.snakeinprod.nextjs.rules.NextRule;
import org.junit.jupiter.api.Test;

/** The verifier itself, with two small rules written the way real rules are. */
class RuleVerifierTest {

  /** Reports every debugger statement: a single file fixture. */
  static final class DemoDebugger implements NextRule {
    @Override
    public void check(io.github.snakeinprod.nextjs.rules.RuleContext ctx) {
      for (Token t : ctx.file().tokens()) {
        if (t.isIdent("debugger")) {
          ctx.report(t, "Remove this debugger statement.");
        }
      }
    }
  }

  /** Reports imports of a server-only module from client modules: a directory fixture that needs the model. */
  static final class DemoServerOnlyInClient implements NextRule {
    @Override
    public void check(io.github.snakeinprod.nextjs.rules.RuleContext ctx) {
      Module m = ctx.module();
      if (m.role() != Role.CLIENT) {
        return;
      }
      for (Import i : m.imports()) {
        Module target = ctx.model().target(i);
        if (target != null && target.has(Directive.SERVER_ONLY)) {
          ctx.report(i.token(), "Do not import \"" + i.spec() + "\" here: it is server-only.");
        }
      }
    }
  }

  @Test
  void single_file_fixture() {
    RuleVerifier.verify("DemoDebugger", new DemoDebugger());
  }

  @Test
  void directory_fixture_with_the_model() {
    RuleVerifier.verify("DemoServerOnlyInClient", new DemoServerOnlyInClient());
  }

  @Test
  void a_missing_issue_fails() {
    assertThatThrownBy(() -> RuleVerifier.verify("DemoDebugger", ctx -> { }))
      .hasMessageContaining("DemoDebugger.ts:3 expected 1 issue(s), got 0");
  }
}
