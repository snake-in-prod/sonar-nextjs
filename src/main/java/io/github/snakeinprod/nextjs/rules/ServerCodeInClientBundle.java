package io.github.snakeinprod.nextjs.rules;

import io.github.snakeinprod.nextjs.model.Directive;
import io.github.snakeinprod.nextjs.model.EnvRead;
import io.github.snakeinprod.nextjs.model.Import;
import io.github.snakeinprod.nextjs.model.Module;
import io.github.snakeinprod.nextjs.model.NextModel;
import io.github.snakeinprod.nextjs.parse.Chain;
import io.github.snakeinprod.nextjs.parse.Node;
import io.github.snakeinprod.nextjs.parse.Nodes;
import io.github.snakeinprod.nextjs.parse.Token;
import io.github.snakeinprod.nextjs.parse.TokenKind;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Client components should not import server-only code. A "use client" module that imports, directly or through
 * modules without a directive, "server-only", a Node.js built-in the browser has no fallback for (with or without
 * "node:"), or a database client; or, in itself or a module it imports directly, Node crypto APIs (createHmac...) or
 * a read of a secret-looking server environment variable, in code the bundle keeps.
 *
 * <p>Precision: only static imports count ({@code require} and {@code import()} are often guarded), and only imports
 * whose names the module uses as values (an import used for types only is erased by the compiler). Other "use client"
 * modules are not entered (they get their own issue), nor "use server" modules (the client gets references only). In
 * the imported module, crypto and env reads inside exported declarations the client does not import are left out:
 * tree shaking drops them.
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
  /** A server env name that says it holds a secret or a database location (a bare _URI or _DSN may be any endpoint). */
  private static final Pattern SECRET_ENV = Pattern.compile(
    "(.*_)?(SECRET|PASSWORD|PASSWD|PRIVATE|TOKEN|CREDENTIALS?|SERVICE_ROLE|DATABASE|DB|KEY)(_.*)?"
      + "|(.*_)?(MONGO|MONGODB|POSTGRES|POSTGRESQL|PG|MYSQL|REDIS|KV)_(URI|URL|DSN)(_.*)?");
  /** Before a name in a type: {@code x: T}, {@code extends T}, {@code typeof t}, {@code A | T}. */
  private static final Set<String> TYPE_BEFORE = Set.of(":", "extends", "implements", "typeof", "keyof", "as", "satisfies", "|",
    "&", "is", "infer", "type", "interface", "readonly", "unique", "declare", ".", "?.", "#");
  /** Keywords that can come right before a JSX element. */
  private static final Set<String> JSX_KEYWORDS = Set.of("return", "yield", "await", "case", "default", "else", "do", "in", "of",
    "void", "throw", "new");
  /** Keywords starting a top-level declaration. */
  private static final Set<String> DECLARATIONS = Set.of("const", "let", "var", "function", "class");
  /** Before a "function" or "class" keyword that is an expression, not a declaration. */
  private static final Set<String> IN_EXPRESSION = Set.of("=", "(", ",", ":", "?", "=>", "return", "||", "&&", "??", "!");

  private static final String CRYPTO = "Node.js crypto (";

  private record Finding(String what, Module through) {
  }

  private final Map<Module, Optional<Finding>> reached = new HashMap<>();
  // the walks from each client import cross the same modules: their imports are looked at once
  private final Map<Import, Boolean> loadedImports = new IdentityHashMap<>();
  private final Map<Import, Optional<String>> reasons = new IdentityHashMap<>();
  private final Map<String, Set<String>> configEnv = new HashMap<>();

  @Override
  public void check(RuleContext ctx) {
    Module m = ctx.module();
    if (!m.has(Directive.USE_CLIENT)) {
      return;
    }
    for (Import i : m.imports()) {
      if (!isLoaded(ctx.model(), m, i)) {
        continue;
      }
      String own = reason(m, i);
      if (own != null) {
        ctx.report(i.token(), "Remove this import from the client component: it pulls " + own + " into the browser bundle.");
        continue;
      }
      Module t = ctx.model().target(i);
      if (t != null && entered(t)) {
        reach(ctx.model(), t).or(() -> direct(ctx.model(), t, i)).ifPresent(f -> ctx.report(i.token(),
          "Remove this import from the client component: it pulls " + f.what() + " into the browser bundle through "
            + f.through().path() + "."));
      }
    }
  }

  private static boolean entered(Module t) {
    return !t.has(Directive.USE_CLIENT) && !t.has(Directive.USE_SERVER);
  }

  /**
   * The first server-only package reached from {@code start} through loaded imports, breadth first. Node crypto counts
   * only in the imported module (see {@link #direct}): further down, tree shaking may drop the code, while server-only,
   * Node-only built-ins and database drivers fail wherever they are.
   */
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
      for (Import i : v.imports()) {
        if (out.isPresent() || !isLoaded(model, v, i)) {
          continue;
        }
        String r = reason(v, i);
        if (r != null && !r.startsWith(CRYPTO)) {
          out = Optional.of(new Finding(r, v));
        }
        Module t = model.target(i);
        if (t != null && entered(t) && seen.add(t)) {
          queue.add(t);
        }
      }
    }
    reached.put(start, out);
    return out;
  }

  /**
   * Node crypto or a secret env read in the module {@code t} the client imports through {@code i}, in code the bundle
   * keeps: a top-level statement that is not an exported declaration, or the declaration of a name the client imports.
   */
  private Optional<Finding> direct(NextModel model, Module t, Import i) {
    Set<String> names = i.reExport() || i.names().isEmpty() || i.names().containsValue("*") ? null : Set.copyOf(i.names().values());
    for (Import j : t.imports()) {
      if (isLoaded(model, t, j)) {
        for (Map.Entry<Token, String> u : cryptoUses(t, j).entrySet()) {
          if (kept(t, u.getKey(), names)) {
            return Optional.of(new Finding(CRYPTO + u.getValue() + ")", t));
          }
        }
      }
    }
    for (EnvRead e : ServerEnvInClient.serverReads(model, t, configEnv)) {
      if (SECRET_ENV.matcher(e.name()).matches() && kept(t, e.token(), names)) {
        return Optional.of(new Finding("code reading the server environment variable " + e.name(), t));
      }
    }
    return Optional.empty();
  }

  /** A top-level statement: the name it declares (null for other code), its export names, whether a function. */
  private record Stmt(String declared, Set<String> exported, boolean function) {

    /** Kept by tree shaking when the module is imported for {@code names}, private function declarations aside. */
    boolean keptFor(Set<String> names) {
      return exported.isEmpty() ? declared == null || !function : exported.stream().anyMatch(names::contains);
    }
  }

  /**
   * The token is in code the bundle keeps when the module is imported for {@code names} (null: for everything): side
   * effect code, a private constant, the declaration of an imported name, or a private function that one of those uses.
   */
  // ponytail: one level of private function calls; a helper called through another helper counts as dropped
  private static boolean kept(Module t, Token use, Set<String> names) {
    if (names == null) {
      return true;
    }
    Stmt s = statementOf(t, use);
    if (s.keptFor(names) || !s.exported().isEmpty()) {
      return s.keptFor(names);
    }
    for (Token k : t.file().tokens()) {
      if (k.isIdent(s.declared())) {
        Stmt r = statementOf(t, k);
        if (!s.declared().equals(r.declared()) && r.keptFor(names)) {
          return true;
        }
      }
    }
    return false;
  }

  /** The top-level statement holding {@code use}. */
  private static Stmt statementOf(Module t, Token use) {
    String declared = null;
    boolean function = false;
    boolean exportDefault = false;
    boolean pending = false;
    Token before = null;
    for (Node k : t.file().root().children()) {
      Token tok = Nodes.leaf(k);
      if (ValueFlow.within(use, k.first(), k.last())) {
        if (pending && tok != null && tok.kind() == TokenKind.IDENT) {
          declared = tok.text();
        }
        break;
      }
      if (tok == null) {
        pending = false;
      } else if (tok.is(";") || tok.isIdent("import") || tok.isIdent("export")) {
        declared = null;
        exportDefault = false;
        pending = false;
      } else if (tok.isIdent("default") && before != null && before.isIdent("export")) {
        exportDefault = true;
      } else if (tok.kind() == TokenKind.IDENT && DECLARATIONS.contains(tok.text())
        && (before == null || !IN_EXPRESSION.contains(before.text()))) {
        declared = null;
        function = tok.text().equals("function");
        pending = true;
      } else if (pending && tok.kind() == TokenKind.IDENT) {
        declared = tok.text();
        pending = false;
      }
      before = tok;
    }
    Set<String> out = new HashSet<>();
    if (exportDefault) {
      out.add("default");
    }
    String local = declared;
    t.exports().forEach((name, token) -> {
      if (token.text().equals(local)) {
        out.add(name);
      }
    });
    return new Stmt(declared, out, function);
  }

  private boolean isLoaded(NextModel model, Module m, Import i) {
    return loadedImports.computeIfAbsent(i, k -> loaded(model, m, k));
  }

  private String reason(Module m, Import i) {
    return reasons.computeIfAbsent(i, k -> Optional.ofNullable(packageReason(m, k))).orElse(null);
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
      return cryptoUses(m, i).values().stream().findFirst().map(n -> CRYPTO + n + ")").orElse(null);
    }
    if (i.packageName() != null && DB_PACKAGES.contains(i.packageName()) || DRIZZLE_DRIVER.matcher(spec).matches()) {
      return "the database client \"" + spec + "\"";
    }
    if (spec.contains("prisma") && i.names().containsValue("PrismaClient")) {
      return "the database client PrismaClient";
    }
    return null;
  }

  /** Uses of Node crypto APIs through a "crypto" import, to the API name, in order. */
  private static Map<Token, String> cryptoUses(Module m, Import i) {
    Map<Token, String> out = new LinkedHashMap<>();
    String spec = i.spec();
    if (!spec.equals("crypto") && !spec.equals("node:crypto")) {
      return out;
    }
    for (Map.Entry<String, String> n : i.names().entrySet()) {
      if (NODE_CRYPTO.contains(n.getValue())) {
        valueUses(m, i, n.getKey(), false).forEach(t -> out.putIfAbsent(t, n.getValue()));
      }
      if (n.getValue().equals("default") || n.getValue().equals("*")) {
        for (Chain c : m.file().chains()) {
          List<String> names = c.names();
          if (names.size() > 1 && names.get(0).equals(n.getKey()) && NODE_CRYPTO.contains(names.get(1))) {
            out.putIfAbsent(c.first(), names.get(1));
          }
        }
      }
    }
    return out;
  }

  /**
   * The import is in the bundle when the module is: a static import or re-export, not type-only, whose names the
   * module uses as values (or a side effect import), and that the target does not export only as types.
   */
  static boolean loaded(NextModel model, Module m, Import i) {
    if (i.typeOnly() || i.dynamic() || isCall(m, i)) {
      return false;
    }
    if (i.reExport() || i.names().isEmpty()) {
      return true;
    }
    Module t = model.target(i);
    return i.names().entrySet().stream()
      .anyMatch(n -> (t == null || !t.typeExports().contains(n.getValue())) && valueUse(m, i, n.getKey()));
  }

  /** {@code require('x')}: the specifier is an argument. */
  private static boolean isCall(Module m, Import i) {
    List<Token> t = m.file().tokens();
    int k = m.file().indexOf(i.token());
    return k > 0 && t.get(k - 1).is("(");
  }

  /**
   * {@code local} appears outside the import statement in a value position: called, read, passed, rendered as JSX.
   * Positions that are or may be types are not values: after ":", "extends", "typeof", "|", inside generics.
   */
  private static boolean valueUse(Module m, Import i, String local) {
    return !valueUses(m, i, local, true).isEmpty();
  }

  /** The value uses, or only the first one. */
  private static List<Token> valueUses(Module m, Import i, String local, boolean first) {
    List<Token> out = new ArrayList<>();
    List<Token> t = m.file().tokens();
    int spec = m.file().indexOf(i.token());
    int start = spec;
    while (start > 0 && !t.get(start).isIdent("import") && !t.get(start).isIdent("export")) {
      start--;
    }
    for (int k = 0; k < t.size() && !(first && !out.isEmpty()); k++) {
      if (k >= start && k <= spec || !t.get(k).isIdent(local)) {
        continue;
      }
      Token prev = k > 0 ? t.get(k - 1) : null;
      Token next = k + 1 < t.size() ? t.get(k + 1) : null;
      if (prev != null && (TYPE_BEFORE.contains(prev.text())
        // the return type of a function type member: getId: (item: Item) => ObjectId;
        || prev.is("=>") && next != null && next.is(";")
        || next != null && next.is(":") && (prev.is("{") || prev.is(","))
        || prev.is("<") && k > 1 && t.get(k - 2).kind() == TokenKind.IDENT && !JSX_KEYWORDS.contains(t.get(k - 2).text())
        || prev.is(",") && next != null && (next.is(">") || next.is(","))
        || prev.is("=") && k > 2 && t.get(k - 3).isIdent("type"))) {
        continue;
      }
      out.add(t.get(k));
    }
    return out;
  }
}
