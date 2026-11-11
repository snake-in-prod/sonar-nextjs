package io.github.snakeinprod.nextjs.model;

import com.google.gson.JsonObject;
import io.github.snakeinprod.nextjs.js.JsLexer;
import io.github.snakeinprod.nextjs.parse.Lexed;
import io.github.snakeinprod.nextjs.parse.ParsedFile;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The module graph of a Next.js project: every JS/TS file with its directives, resolved imports, Next.js convention and
 * role. Built once per analysis by {@link #build}; rules read it through {@link io.github.snakeinprod.nextjs.rules.RuleContext}.
 */
public final class NextModel {

  /** A package.json declaring a "next" dependency: its directory ("" or "apps/web/") and the major version, 0 if unknown. */
  public record NextApp(String dir, int major) {
  }

  private static final Pattern SOURCE = Pattern.compile(".*\\.(js|jsx|ts|tsx|mjs|cjs|mts|cts)$");
  private static final Pattern DECLARATION = Pattern.compile(".*\\.d\\.[cm]?ts$");
  private static final Pattern MAJOR = Pattern.compile("(\\d+)\\.");
  private static final Set<String> APP_CONVENTIONS = Set.of("page", "layout", "route", "loading", "error", "global-error",
    "not-found", "template", "default");
  private static final Pattern CONVENTION_FILE = Pattern.compile("([^/.]+)\\.(js|jsx|ts|tsx)");

  private final Map<String, Module> modules;
  private final List<NextApp> apps;
  private final Map<Module, Set<Module>> importers = new HashMap<>();
  private final List<String> skipped;

  private NextModel(Map<String, Module> modules, List<NextApp> apps, List<String> skipped) {
    this.modules = modules;
    this.apps = apps;
    this.skipped = skipped;
  }

  /**
   * Reads every source file of the project. A file that cannot be read or parsed is left out and listed in
   * {@link #skipped()}.
   */
  public static NextModel build(Project project) {
    Set<String> paths = new java.util.TreeSet<>();
    for (String p : project.paths()) {
      if (SOURCE.matcher(p).matches() && !DECLARATION.matcher(p).matches() && !p.contains("node_modules/")) {
        paths.add(p);
      }
    }
    Resolver resolver = new Resolver(paths, project::read);
    Map<String, Module> modules = new TreeMap<>();
    List<String> skipped = new ArrayList<>();
    for (String p : paths) {
      try {
        String source = project.read(p);
        if (source == null) {
          skipped.add(p);
          continue;
        }
        modules.put(p, ModuleReader.read(parse(p, source), resolver));
      } catch (RuntimeException e) {
        skipped.add(p + ": " + e);
      }
    }
    NextModel model = new NextModel(modules, apps(paths, project), List.copyOf(skipped));
    model.link();
    return model;
  }

  /** Lexes and builds the token tree of one file; the language is "ts" for TypeScript extensions, else "js". */
  public static ParsedFile parse(String path, String source) {
    String language = path.matches(".*\\.[cm]?tsx?$") ? "ts" : "js";
    Lexed l = JsLexer.lex(path, language, source);
    return new ParsedFile(path, language, source, l.tokens(), l.comments());
  }

  // ---------------------------------------------------------------- queries

  /** Every module, sorted by path. */
  public Collection<Module> modules() {
    return modules.values();
  }

  /** The module of a project path, or null. */
  public Module module(String path) {
    return modules.get(path);
  }

  /** The module an import resolves to, or null (a package, a built-in, or a file the model did not read). */
  public Module target(Import i) {
    return i.resolved() == null ? null : modules.get(i.resolved());
  }

  /** The modules this one imports, type-only imports left out, in source order without repeats. */
  public List<Module> imported(Module m) {
    Set<Module> out = new LinkedHashSet<>();
    for (Import i : m.imports()) {
      Module t = i.typeOnly() ? null : target(i);
      if (t != null) {
        out.add(t);
      }
    }
    return List.copyOf(out);
  }

  /** The modules that import this one, type-only imports left out. */
  public Set<Module> importers(Module m) {
    return importers.getOrDefault(m, Set.of());
  }

  /**
   * Every module reachable from {@code start} through non-type imports, {@code start} included, breadth first.
   * {@code enter} decides whether to go into a module (and on from it); {@code start} is always entered.
   */
  public Set<Module> reachable(Module start, Predicate<Module> enter) {
    Set<Module> seen = new LinkedHashSet<>(List.of(start));
    Deque<Module> queue = new ArrayDeque<>(List.of(start));
    while (!queue.isEmpty()) {
      for (Module t : imported(queue.poll())) {
        if (!seen.contains(t) && enter.test(t)) {
          seen.add(t);
          queue.add(t);
        }
      }
    }
    return seen;
  }

  /** The client modules a route pulls into the browser: CLIENT modules reachable from it, "use server" modules not entered. */
  public Set<Module> clientModulesOf(Module route) {
    Set<Module> out = new LinkedHashSet<>();
    for (Module m : reachable(route, t -> !t.has(Directive.USE_SERVER))) {
      if (m.role() == Role.CLIENT) {
        out.add(m);
      }
    }
    return out;
  }

  /** Some package.json of the project declares a "next" dependency. Without one the sensor does nothing. */
  public boolean hasNext() {
    return !apps.isEmpty();
  }

  public List<NextApp> apps() {
    return apps;
  }

  /**
   * The Next.js major version of the app holding the module (the nearest package.json above it declaring "next"), else
   * the highest one of the project; 0 when unknown.
   */
  public int nextMajor(Module m) {
    NextApp app = appOf(m.path());
    return app != null ? app.major() : apps.stream().mapToInt(NextApp::major).max().orElse(0);
  }

  /** Files left out: unreadable, or a parser error (path and reason). */
  public List<String> skipped() {
    return skipped;
  }

  private NextApp appOf(String path) {
    NextApp best = null;
    for (NextApp a : apps) {
      if (path.startsWith(a.dir()) && (best == null || a.dir().length() > best.dir().length())) {
        best = a;
      }
    }
    return best;
  }

  // ---------------------------------------------------------------- building

  private static List<NextApp> apps(Set<String> paths, Project project) {
    List<NextApp> out = new ArrayList<>();
    for (String dir : Resolver.directories(paths)) {
      JsonObject json = Jsonc.parse(project.read(dir + "package.json"));
      String version = null;
      for (String deps : List.of("dependencies", "devDependencies", "peerDependencies")) {
        String v = Jsonc.string(Jsonc.object(json, deps), "next");
        version = version == null ? v : version;
      }
      if (version == null) {
        continue;
      }
      int major = major(version);
      // "latest", "canary", "workspace:*", "catalog:": the installed version, here or hoisted above
      for (String d = dir; major == 0; d = Resolver.dir(d.substring(0, d.length() - 1))) {
        major = major(Jsonc.string(Jsonc.parse(project.read(d + "node_modules/next/package.json")), "version"));
        if (d.isEmpty()) {
          break;
        }
      }
      out.add(new NextApp(dir, major));
    }
    return List.copyOf(out);
  }

  static int major(String version) {
    Matcher m = version == null ? null : MAJOR.matcher(version + ".");
    return m != null && m.find() ? Integer.parseInt(m.group(1)) : 0;
  }

  /** Routes, reverse edges and roles, once every module is read. */
  private void link() {
    for (Module m : modules.values()) {
      route(m);
      for (Module t : imported(m)) {
        importers.computeIfAbsent(t, k -> new LinkedHashSet<>()).add(m);
      }
    }
    importers.replaceAll((k, v) -> Set.copyOf(v));
    // client: "use client" modules and what they import; a "use server" module imported there is a reference, not code
    for (Module m : modules.values()) {
      if (m.has(Directive.USE_CLIENT)) {
        for (Module c : reachable(m, t -> !t.has(Directive.USE_SERVER))) {
          c.role = Role.CLIENT;
        }
      }
    }
    // server: entries and what they import, up to the "use client" boundaries
    for (Module m : modules.values()) {
      boolean entry = m.has(Directive.USE_SERVER) || m.route() != null && m.route().router().equals("app")
        || m.route() != null && (m.route().kind().equals("api") || m.route().kind().equals("middleware"))
        || m.inAppDir() && m.role() != Role.CLIENT;
      if (entry && !m.has(Directive.USE_CLIENT) && !m.reachedFromServer) {
        for (Module s : reachable(m, t -> !t.has(Directive.USE_CLIENT) && !t.reachedFromServer)) {
          s.reachedFromServer = true;
        }
      }
    }
    for (Module m : modules.values()) {
      if (m.role != Role.CLIENT && m.reachedFromServer) {
        m.role = Role.SERVER;
      }
    }
  }

  private void route(Module m) {
    NextApp app = appOf(m.path());
    if (app == null) {
      return;
    }
    String rest = m.path().substring(app.dir().length());
    for (String root : List.of("app/", "src/app/")) {
      if (rest.startsWith(root)) {
        m.appDir = true;
        m.route = appRoute(rest.substring(root.length()));
        return;
      }
    }
    for (String root : List.of("pages/", "src/pages/")) {
      if (rest.startsWith(root)) {
        m.route = pagesRoute(rest.substring(root.length()));
        return;
      }
    }
    if (rest.matches("(src/)?(middleware|proxy)\\.(js|ts|mjs|mts)")) {
      m.route = new Route("middleware", "middleware", null, false);
    }
  }

  /** A file under app/: a convention file outside private folders, or null. */
  private static Route appRoute(String rel) {
    String[] parts = rel.split("/");
    Matcher f = CONVENTION_FILE.matcher(parts[parts.length - 1]);
    if (!f.matches() || !APP_CONVENTIONS.contains(f.group(1))) {
      return null;
    }
    List<String> url = new ArrayList<>();
    boolean dynamic = false;
    for (int i = 0; i < parts.length - 1; i++) {
      String s = parts[i];
      if (s.startsWith("_")) {
        return null;
      }
      if (s.startsWith("@") || s.startsWith("(") && s.endsWith(")")) {
        continue;
      }
      s = s.replaceFirst("^(\\(\\.{1,3}\\))+", "");
      dynamic |= s.startsWith("[");
      url.add(s);
    }
    return new Route("app", f.group(1), "/" + String.join("/", url), dynamic);
  }

  /** A file under pages/: an API route, _app / _document / _error, or a page. */
  private static Route pagesRoute(String rel) {
    if (!SOURCE.matcher(rel).matches()) {
      return null;
    }
    String stem = rel.substring(0, rel.lastIndexOf('.'));
    String url = "/" + (stem.equals("index") ? "" : stem.endsWith("/index") ? stem.substring(0, stem.length() - 6) : stem);
    boolean dynamic = stem.contains("[");
    if (stem.startsWith("api/") || stem.equals("api")) {
      return new Route("pages", "api", url, dynamic);
    }
    if (Set.of("_app", "_document", "_error").contains(stem)) {
      return new Route("pages", stem.substring(1), url, false);
    }
    return new Route("pages", "page", url, dynamic);
  }
}
