package io.github.snakeinprod.nextjs.rules;

import io.github.snakeinprod.nextjs.model.Directive;
import io.github.snakeinprod.nextjs.model.EnvRead;
import io.github.snakeinprod.nextjs.model.Module;
import io.github.snakeinprod.nextjs.model.NextModel;
import io.github.snakeinprod.nextjs.model.Role;
import io.github.snakeinprod.nextjs.parse.Node;
import io.github.snakeinprod.nextjs.parse.Nodes;
import io.github.snakeinprod.nextjs.parse.Token;
import io.github.snakeinprod.nextjs.parse.TokenKind;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Client code should only read NEXT_PUBLIC_ environment variables: any other {@code process.env.X} is undefined in the
 * browser. Checks "use client" modules and modules only client modules import, inside a Next.js app. Silent for
 * modules that test {@code typeof window} / {@code typeof document} (code that knows where it runs), t3-env schemas,
 * names next.config declares under {@code env}, and every name when next.config uses DefinePlugin or a computed env.
 */
public class ServerEnvInClient implements NextRule {

  /** Inlined by Next.js into every bundle. */
  private static final Set<String> BUILT_IN = Set.of("NODE_ENV", "NEXT_RUNTIME", "NEXT_MINIMAL", "NEXT_DEPLOYMENT_ID", "TURBOPACK");
  /** Marks "every name is allowed" in the next.config cache. */
  private static final String ANY = "*";

  private final Map<String, Set<String>> configEnv = new HashMap<>();

  @Override
  public void check(RuleContext ctx) {
    Module m = ctx.module();
    if (m.role() != Role.CLIENT || !m.has(Directive.USE_CLIENT) && m.reachedFromServer() || appDir(ctx.model(), m) == null) {
      return;
    }
    for (EnvRead e : serverReads(ctx.model(), m, configEnv)) {
      ctx.report(e.token(), "process.env." + e.name() + " is undefined in the browser: read it on the server and pass the value"
        + " down, or rename it NEXT_PUBLIC_" + e.name() + " if it is not a secret.");
    }
  }

  /**
   * The non-public env reads of a module that the browser bundle leaves undefined: none when the module guards with
   * {@code typeof window}/{@code typeof document} or builds a t3-env schema, and none of the names next.config inlines.
   * {@code cache} keeps the next.config env names per app directory.
   */
  static List<EnvRead> serverReads(NextModel model, Module m, Map<String, Set<String>> cache) {
    if (m.envReads().isEmpty() || guarded(m) || m.imports().stream().anyMatch(i -> i.spec().startsWith("@t3-oss/"))) {
      return List.of();
    }
    String app = appDir(model, m);
    Set<String> config = app == null ? Set.of() : cache.computeIfAbsent(app, a -> configEnv(model, a));
    if (config.contains(ANY)) {
      return List.of();
    }
    List<EnvRead> out = new ArrayList<>();
    for (EnvRead e : m.envReads()) {
      String n = e.name();
      if (!n.startsWith("NEXT_PUBLIC_") && !n.startsWith("__NEXT") && !BUILT_IN.contains(n) && !config.contains(n)) {
        out.add(e);
      }
    }
    return out;
  }

  /** The directory of the Next.js app holding the module, or null. */
  static String appDir(NextModel model, Module m) {
    String best = null;
    for (NextModel.NextApp a : model.apps()) {
      if (m.path().startsWith(a.dir()) && (best == null || a.dir().length() > best.length())) {
        best = a.dir();
      }
    }
    return best;
  }

  /** {@code typeof window} or {@code typeof document}: the module knows which side it runs on. */
  private static boolean guarded(Module m) {
    List<Token> t = m.file().tokens();
    for (int i = 0; i + 1 < t.size(); i++) {
      if (t.get(i).isIdent("typeof") && (t.get(i + 1).isIdent("window") || t.get(i + 1).isIdent("document"))) {
        return true;
      }
    }
    return false;
  }

  /** Keys of the {@code env: { ... }} object of the app's next.config; {@link #ANY} when it cannot be read literally. */
  private static Set<String> configEnv(NextModel model, String app) {
    Set<String> out = new HashSet<>();
    for (String ext : List.of("js", "mjs", "cjs", "ts", "mts")) {
      Module c = model.module(app + "next.config." + ext);
      if (c == null) {
        continue;
      }
      if (c.file().tokens().stream().anyMatch(t -> t.isIdent("DefinePlugin"))) {
        return Set.of(ANY);
      }
      Nodes.groups(c.file().root(), g -> {
        if (!g.is("{")) {
          return;
        }
        for (List<Node> item : g.items()) {
          if (item.isEmpty() || !Nodes.isIdent(item.get(0), "env")) {
            continue;
          }
          if (item.size() == 3 && Nodes.isLeaf(item.get(1), ":") && item.get(2) instanceof Node.Group o && o.is("{")) {
            for (List<Node> kv : o.items()) {
              Token key = kv.isEmpty() ? null : Nodes.leaf(kv.get(0));
              if (key != null && (key.kind() == TokenKind.IDENT || key.kind() == TokenKind.STRING)
                && (kv.size() == 1 || Nodes.isLeaf(kv.get(1), ":"))) {
                out.add(key.kind() == TokenKind.STRING ? key.text().substring(1, key.text().length() - 1) : key.text());
              } else if (!kv.isEmpty()) {
                out.add(ANY); // a spread or a computed key
              }
            }
          } else if (item.size() == 1 || Nodes.isLeaf(item.get(1), ":")) {
            out.add(ANY); // env: someObject, or the shorthand env
          }
        }
      });
    }
    return out.contains(ANY) ? Set.of(ANY) : out;
  }
}
