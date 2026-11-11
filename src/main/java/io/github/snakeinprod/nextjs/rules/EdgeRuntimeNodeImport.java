package io.github.snakeinprod.nextjs.rules;

import io.github.snakeinprod.nextjs.model.Import;
import io.github.snakeinprod.nextjs.model.Module;
import io.github.snakeinprod.nextjs.model.NextModel;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Edge runtime code should not import Node.js-only modules. A module running on the Edge runtime ({@code export const
 * runtime = 'edge'}, {@code config = { runtime: 'edge' }}, or middleware.ts without {@code runtime: 'nodejs'}) that
 * imports, directly or through project modules, a Node.js built-in the Edge runtime lacks, Node crypto, or a package
 * that needs Node.js (native addons, TCP database drivers, PrismaClient without a driver adapter or Accelerate).
 *
 * <p>Precision: only static imports whose names the module uses as values count ({@link ServerCodeInClientBundle#loaded}):
 * type-only imports are erased, {@code require} and {@code import()} are often guarded by {@code NEXT_RUNTIME}. The
 * built-ins the Edge runtime has (buffer, events, util, assert, async_hooks) and ones bundlers polyfill (path, url,
 * stream...) are left out, as are drivers with edge builds (postgres, @neondatabase/serverless, @libsql/client...).
 */
public class EdgeRuntimeNodeImport implements NextRule {

  private static final Set<String> NODE_ONLY = Set.of("fs", "fs/promises", "child_process", "net", "tls", "dgram", "dns",
    "dns/promises", "cluster", "worker_threads", "os", "http", "https", "http2", "zlib", "module", "readline", "repl",
    "inspector", "v8", "vm", "perf_hooks", "crypto");
  private static final Set<String> NODE_PACKAGES = Set.of("bcrypt", "argon2", "pg", "pg-pool", "mysql", "mysql2", "mongoose",
    "mongodb", "sqlite3", "better-sqlite3", "nodemailer", "sharp", "ioredis", "firebase-admin", "jsonwebtoken", "typeorm",
    "sequelize");
  private static final Pattern DRIZZLE_NODE = Pattern.compile("drizzle-orm/(node-postgres|mysql2|better-sqlite3)(/.*)?");
  /** Signs that a PrismaClient runs with a driver adapter or Accelerate, which work on the Edge runtime. */
  private static final Pattern PRISMA_EDGE = Pattern.compile("@prisma/adapter-.*|.*prisma.*/edge|.*/wasm|@prisma/extension-accelerate");

  private record Finding(String what, List<Module> through) {
  }

  private final Map<Module, Optional<Finding>> reached = new HashMap<>();

  @Override
  public void check(RuleContext ctx) {
    Module m = ctx.module();
    // a runtime export outside a Next.js app (a React Router app keeping one) means nothing
    if (!m.runtime().equals("edge") || m.route() == null && !m.inAppDir()) {
      return;
    }
    NextModel model = ctx.model();
    for (Import i : m.imports()) {
      if (!ServerCodeInClientBundle.loaded(model, m, i)) {
        continue;
      }
      String own = reason(m, i);
      if (own != null) {
        ctx.report(i.token(), "Remove this import or use the Node.js runtime: " + own + " does not run on the Edge runtime.");
        continue;
      }
      Module t = model.target(i);
      if (t != null && t != m) {
        reach(model, t).ifPresent(f -> ctx.report(i.token(), "Remove this import or use the Node.js runtime: it loads " + f.what()
          + ", which does not run on the Edge runtime, through " + String.join(" -> ", f.through().stream().map(Module::path).toList())
          + "."));
      }
    }
  }

  /** The first Node-only import reached from {@code start} through loaded imports, breadth first, with the modules on the way. */
  private Optional<Finding> reach(NextModel model, Module start) {
    Optional<Finding> cached = reached.get(start);
    if (cached != null) {
      return cached;
    }
    Map<Module, Module> parent = new LinkedHashMap<>();
    parent.put(start, null);
    Deque<Module> queue = new ArrayDeque<>(List.of(start));
    Optional<Finding> out = Optional.empty();
    while (!queue.isEmpty() && out.isEmpty()) {
      Module v = queue.poll();
      for (Import i : v.imports()) {
        if (!ServerCodeInClientBundle.loaded(model, v, i)) {
          continue;
        }
        String r = reason(v, i);
        if (r != null) {
          List<Module> path = new ArrayList<>();
          for (Module p = v; p != null; p = parent.get(p)) {
            path.add(p);
          }
          Collections.reverse(path);
          out = Optional.of(new Finding(r, path));
          break;
        }
        Module t = model.target(i);
        if (t != null && !parent.containsKey(t)) {
          parent.put(t, v);
          queue.add(t);
        }
      }
    }
    reached.put(start, out);
    return out;
  }

  /** What Node-only code the import brings, or null. */
  private static String reason(Module m, Import i) {
    if (i.resolved() != null) {
      return null;
    }
    String spec = i.spec();
    String builtin = spec.startsWith("node:") ? spec.substring(5) : spec;
    if (NODE_ONLY.contains(builtin)) {
      return "the Node.js module \"" + spec + "\"";
    }
    String pkg = i.packageName();
    if (pkg != null && NODE_PACKAGES.contains(pkg) || DRIZZLE_NODE.matcher(spec).matches()) {
      return "\"" + spec + "\"";
    }
    if (spec.equals("@prisma/client") && i.names().containsValue("PrismaClient") && !prismaEdge(m)) {
      return "PrismaClient without a driver adapter or Accelerate";
    }
    return null;
  }

  private static boolean prismaEdge(Module m) {
    return m.imports().stream().anyMatch(j -> PRISMA_EDGE.matcher(j.spec()).matches())
      || m.file().tokens().stream().anyMatch(t -> t.isIdent("adapter") || t.isIdent("withAccelerate"));
  }
}
