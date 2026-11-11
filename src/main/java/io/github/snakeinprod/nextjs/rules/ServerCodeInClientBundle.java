package io.github.snakeinprod.nextjs.rules;

import io.github.snakeinprod.nextjs.model.Directive;
import io.github.snakeinprod.nextjs.model.EnvRead;
import io.github.snakeinprod.nextjs.model.Import;
import io.github.snakeinprod.nextjs.model.Module;
import io.github.snakeinprod.nextjs.model.NextModel;
import io.github.snakeinprod.nextjs.parse.Chain;
import io.github.snakeinprod.nextjs.parse.Token;
import io.github.snakeinprod.nextjs.parse.TokenKind;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Client components should not import server-only code. A "use client" module that imports, directly or through
 * modules without a directive, "server-only", a Node.js built-in the browser has no fallback for (with or without
 * "node:"), or a database client; or, in itself or a module it imports directly, Node crypto APIs (createHmac...) or
 * a read of a secret-looking server environment variable.
 *
 * <p>Precision: only static imports count ({@code require} and {@code import()} are often guarded), and only imports
 * whose names the module uses as values (an import used for types only is erased by the compiler). Other "use client"
 * modules are not entered (they get their own issue), nor "use server" modules (the client gets references only).
 */
public class ServerCodeInClientBundle implements NextRule {

  /** Node built-ins without a browser fallback in Next.js (path, buffer, events, util, url... have one). */
  private static final Set<String> NODE_ONLY = Set.of("fs", "fs/promises", "child_process", "net", "tls", "dgram", "dns",
    "dns/promises", "cluster", "worker_threads", "module", "readline", "repl", "inspector", "v8", "async_hooks", "http2",
    "perf_hooks");
  private static final Set<String> NODE_CRYPTO = Set.of("createHmac", "createCipheriv", "createDecipheriv", "createSign",
    "createVerify", "createPrivateKey", "privateDecrypt", "privateEncrypt", "scrypt", "scryptSync", "pbkdf2", "pbkdf2Sync",
    "timingSafeEqual", "generateKeyPair", "generateKeyPairSync");
  private static final Set<String> DB_PACKAGES = Set.of("pg", "pg-pool", "postgres", "mysql", "mysql2", "mongodb", "mongoose",
    "@vercel/postgres", "kysely", "@neondatabase/serverless", "better-sqlite3", "sqlite3", "@libsql/client",
    "@planetscale/database", "ioredis", "redis", "typeorm", "sequelize", "knex", "firebase-admin");
  private static final Pattern DRIZZLE_DRIVER = Pattern.compile("drizzle-orm/(node-postgres|postgres-js|neon-http|neon-serverless"
    + "|mysql2|planetscale-serverless|better-sqlite3|libsql|vercel-postgres|d1|bun-sqlite|pglite|xata-http|tidb-serverless"
    + "|aws-data-api.*|singlestore|mysql-proxy|pg-proxy|sqlite-proxy|durable-sqlite|neon)(/.*)?");
  /** A server env name that says it holds a secret or a database location. */
  private static final Pattern SECRET_ENV = Pattern.compile(
    "(.*_)?(SECRET|PASSWORD|PASSWD|PRIVATE|TOKEN|CREDENTIALS?|SERVICE_ROLE|DATABASE|DB|KEY|URI|DSN)(_.*)?");
  /** Before a name in a type: {@code x: T}, {@code extends T}, {@code typeof t}, {@code A | T}. */
  private static final Set<String> TYPE_BEFORE = Set.of(":", "extends", "implements", "typeof", "keyof", "as", "satisfies", "|",
    "&", "is", "infer", "type", "interface", "readonly", "unique", "declare", ".", "?.", "#");
  /** Keywords that can come right before a JSX element. */
  private static final Set<String> JSX_KEYWORDS = Set.of("return", "yield", "await", "case", "default", "else", "do", "in", "of",
    "void", "throw", "new");

  private static final String CRYPTO = "Node.js crypto (";

  private record Finding(String what, Module through) {
  }

  private final Map<Module, Optional<Finding>> reached = new HashMap<>();
  private final Map<String, Set<String>> configEnv = new HashMap<>();

  @Override
  public void check(RuleContext ctx) {
    Module m = ctx.module();
    if (!m.has(Directive.USE_CLIENT)) {
      return;
    }
    for (Import i : m.imports()) {
      if (!loaded(m, i)) {
        continue;
      }
      String own = packageReason(m, i);
      if (own != null) {
        ctx.report(i.token(), "Remove this import from the client component: it pulls " + own + " into the browser bundle.");
        continue;
      }
      Module t = ctx.model().target(i);
      if (t != null && entered(t)) {
        reach(ctx.model(), t).ifPresent(f -> ctx.report(i.token(), "Remove this import from the client component: it pulls "
          + f.what() + " into the browser bundle through " + f.through().path() + "."));
      }
    }
  }

  private static boolean entered(Module t) {
    return !t.has(Directive.USE_CLIENT) && !t.has(Directive.USE_SERVER);
  }

  /** The first server-only thing reached from {@code start} through loaded imports, breadth first. */
  private Optional<Finding> reach(NextModel model, Module start) {
    Optional<Finding> cached = reached.get(start);
    if (cached != null) {
      return cached;
    }
    Set<Module> seen = new HashSet<>(List.of(start));
    Deque<Module> queue = new ArrayDeque<>(List.of(start));
    Optional<Finding> out = Optional.empty();
    while (!queue.isEmpty() && out.isEmpty()) {
      Module v = queue.poll();
      // ponytail: Node crypto and env reads only one hop deep; further down, tree shaking may drop the code (no import
      // graph can tell), while server-only, Node-only built-ins and database drivers fail wherever they are
      boolean direct = v == start;
      for (Import i : v.imports()) {
        if (out.isPresent() || !loaded(v, i)) {
          continue;
        }
        String r = packageReason(v, i);
        if (r != null && (direct || !r.startsWith(CRYPTO))) {
          out = Optional.of(new Finding(r, v));
        }
        Module t = model.target(i);
        if (t != null && entered(t) && seen.add(t)) {
          queue.add(t);
        }
      }
      if (out.isEmpty() && direct) {
        for (EnvRead e : ServerEnvInClient.serverReads(model, v, configEnv)) {
          if (SECRET_ENV.matcher(e.name()).matches()) {
            out = Optional.of(new Finding("code reading the server environment variable " + e.name(), v));
            break;
          }
        }
      }
    }
    reached.put(start, out);
    return out;
  }

  /** What a package import brings that must stay on the server, or null. */
  private static String packageReason(Module m, Import i) {
    String spec = i.spec();
    if (spec.equals("server-only")) {
      return "\"server-only\"";
    }
    // Next.js maps "node:x" to the same browser fallbacks as "x"
    String builtin = spec.startsWith("node:") ? spec.substring(5) : spec;
    if (NODE_ONLY.contains(builtin)) {
      return "the Node.js module \"" + spec + "\"";
    }
    if (builtin.equals("crypto")) {
      for (Map.Entry<String, String> n : i.names().entrySet()) {
        if (NODE_CRYPTO.contains(n.getValue()) && valueUse(m, i, n.getKey())) {
          return CRYPTO + n.getValue() + ")";
        }
        if (n.getValue().equals("default") || n.getValue().equals("*")) {
          for (Chain c : m.file().chains()) {
            List<String> names = c.names();
            if (names.size() > 1 && names.get(0).equals(n.getKey()) && NODE_CRYPTO.contains(names.get(1))) {
              return CRYPTO + names.get(1) + ")";
            }
          }
        }
      }
      return null;
    }
    if (i.packageName() != null && DB_PACKAGES.contains(i.packageName()) || DRIZZLE_DRIVER.matcher(spec).matches()) {
      return "the database client \"" + spec + "\"";
    }
    if (spec.contains("prisma") && i.names().containsValue("PrismaClient")) {
      return "the database client PrismaClient";
    }
    return null;
  }

  /**
   * The import is in the bundle when the module is: a static import or re-export, not type-only, whose names the
   * module uses as values (or a side effect import).
   */
  private static boolean loaded(Module m, Import i) {
    if (i.typeOnly() || i.dynamic() || isCall(m, i)) {
      return false;
    }
    if (i.reExport() || i.names().isEmpty()) {
      return true;
    }
    return i.names().keySet().stream().anyMatch(local -> valueUse(m, i, local));
  }

  /** {@code require('x')}: the specifier is an argument. */
  private static boolean isCall(Module m, Import i) {
    List<Token> t = m.file().tokens();
    int k = t.indexOf(i.token());
    return k > 0 && t.get(k - 1).is("(");
  }

  /**
   * {@code local} appears outside the import statement in a value position: called, read, passed, rendered as JSX.
   * Positions that are or may be types are not values: after ":", "extends", "typeof", "|", inside generics.
   */
  static boolean valueUse(Module m, Import i, String local) {
    List<Token> t = m.file().tokens();
    int spec = t.indexOf(i.token());
    int start = spec;
    while (start > 0 && !t.get(start).isIdent("import") && !t.get(start).isIdent("export")) {
      start--;
    }
    for (int k = 0; k < t.size(); k++) {
      if (k >= start && k <= spec || !t.get(k).isIdent(local)) {
        continue;
      }
      Token prev = k > 0 ? t.get(k - 1) : null;
      Token next = k + 1 < t.size() ? t.get(k + 1) : null;
      if (prev == null) {
        return true;
      }
      if (TYPE_BEFORE.contains(prev.text())
        || next != null && next.is(":") && (prev.is("{") || prev.is(","))
        || prev.is("<") && k > 1 && t.get(k - 2).kind() == TokenKind.IDENT && !JSX_KEYWORDS.contains(t.get(k - 2).text())
        || prev.is(",") && next != null && (next.is(">") || next.is(","))
        || prev.is("=") && k > 2 && t.get(k - 3).isIdent("type")) {
        continue;
      }
      return true;
    }
    return false;
  }
}
