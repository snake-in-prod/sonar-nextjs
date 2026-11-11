package io.github.snakeinprod.nextjs.rules;

import io.github.snakeinprod.nextjs.model.EnvRead;
import io.github.snakeinprod.nextjs.model.Role;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Secrets should not be exposed through NEXT_PUBLIC_ variables. A NEXT_PUBLIC_ name whose last word says secret or
 * password, or that ends with a private key, service role or server token, is shipped to every browser. Names saying
 * publishable, anon or public are fine, and so are names where the secret word only qualifies something else
 * (NEXT_PUBLIC_WEBHOOK_SECRET_HEADER).
 */
public class SecretInPublicEnv implements NextRule {

  private static final Set<String> SECRET_WORDS = Set.of("SECRET", "PASSWORD", "PASSWD", "PASSPHRASE");
  /** Endings that are a secret whatever comes before. */
  private static final Pattern SECRET_END = Pattern.compile(".*(PRIVATE_?KEY|SERVICE_?ROLE(_?KEY)?|SERVER_?TOKEN|SECRET_?(ACCESS_)?(KEY|TOKEN))");
  private static final Pattern PUBLIC_WORD = Pattern.compile("(^|.*_)(PUBLISHABLE|ANON|PUBLIC)(_.*|$)");

  @Override
  public void check(RuleContext ctx) {
    boolean server = ctx.module().role() == Role.SERVER;
    for (EnvRead e : ctx.module().envReads()) {
      if (!isSecret(e.name())) {
        continue;
      }
      // server code keeps the value out of the browser, but the name invites client code to read it
      ctx.report(e.token(), server
        ? e.name() + " is named for the browser, so any client code reading it ships it to every visitor: rename it without"
          + " NEXT_PUBLIC_ to keep this secret on the server."
        : e.name() + " is inlined into the browser bundle: keep this secret in a variable without NEXT_PUBLIC_ and use it"
          + " only on the server.");
    }
  }

  static boolean isSecret(String name) {
    if (!name.startsWith("NEXT_PUBLIC_")) {
      return false;
    }
    String rest = name.substring("NEXT_PUBLIC_".length()).toUpperCase(java.util.Locale.ROOT);
    if (PUBLIC_WORD.matcher(rest).matches()) {
      return false;
    }
    List<String> words = List.of(rest.split("_"));
    return SECRET_WORDS.contains(words.get(words.size() - 1)) || SECRET_END.matcher(rest).matches();
  }
}
