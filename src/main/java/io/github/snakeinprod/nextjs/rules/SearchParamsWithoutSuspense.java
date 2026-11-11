package io.github.snakeinprod.nextjs.rules;

import io.github.snakeinprod.nextjs.js.JsTrees;
import io.github.snakeinprod.nextjs.model.Import;
import io.github.snakeinprod.nextjs.model.Module;
import io.github.snakeinprod.nextjs.model.NextModel;
import io.github.snakeinprod.nextjs.model.Role;
import io.github.snakeinprod.nextjs.model.Route;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * {@code useSearchParams()} of next/navigation in a client module that an App Router page or layout renders, with no
 * Suspense boundary in between, when that page (or a page under that layout) is statically rendered.
 * <p>
 * Rendering is followed through JSX: module A renders module B when A imports B and has a {@code <Local} tag of the
 * imported name. A module that mentions Suspense anywhere stops the walk, and so do dynamic imports (next/dynamic
 * brings its own boundary) and barrels. A loading file wraps the page of its segment and every segment below it, but not
 * the layout of its own segment. A page is static when its route has no dynamic segment (or exports
 * generateStaticParams), and neither it, its layouts nor the server modules they import call cookies(), headers(), a
 * session helper, read searchParams, call next-intl/server without setRequestLocale, or opt out with force-dynamic,
 * no-store or revalidate 0. The last step into the calling module counts only when a rendered export calls the hook
 * (itself or through a component of its module). Silent in an app whose next.config sets
 * missingSuspenseWithCSRBailout to false: client rendering is chosen there.
 */
public class SearchParamsWithoutSuspense implements NextRule {

  private static final Set<String> DYNAMIC_CALLS = Set.of("cookies", "headers", "draftMode", "connection", "unstable_noStore",
    "noStore", "auth", "getServerSession", "currentUser", "getSession", "getUser", "getAuth", "getToken");
  private static final Set<String> DYNAMIC_STRINGS = Set.of("force-dynamic", "no-store", "force-no-store");

  private Map<Module, Boolean> dynamicCache;
  private Map<Module, Map<Module, Set<String>>> rendersCache;
  private Map<Module, Optional<String>> staticCache;
  private Map<Module, Boolean> isStaticCache;
  /** The App Router modules of the model, in model order: the loops below look at nothing else. */
  private List<Module> appModules;
  private final Map<String, Boolean> optedOut = new HashMap<>();

  @Override
  public void check(RuleContext ctx) {
    Module m = ctx.module();
    if (m.role() != Role.CLIENT) {
      return;
    }
    List<Token> calls = hookCalls(m);
    if (calls.isEmpty() || optedOut(ctx.model(), m)) {
      return;
    }
    if (dynamicCache == null) {
      dynamicCache = new HashMap<>();
      rendersCache = new HashMap<>();
      staticCache = new HashMap<>();
      isStaticCache = new HashMap<>();
      appModules = ctx.model().modules().stream().filter(x -> x.route() != null && x.route().router().equals("app")).toList();
    }
    NextModel model = ctx.model();
    String page = null;
    Set<String> names = hookExports(m, calls);
    for (Module entry : appModules) {
      Route r = entry.route();
      if (r == null || !r.router().equals("app") || !(r.kind().equals("page") || r.kind().equals("layout"))) {
        continue;
      }
      Optional<String> staticPage = staticCache.computeIfAbsent(entry, e -> Optional.ofNullable(staticPageAround(model, e)));
      if (staticPage.isPresent() && rendersWithoutSuspense(model, entry, m, names)) {
        page = staticPage.get();
        break;
      }
    }
    if (page == null) {
      return;
    }
    for (Token t : calls) {
      ctx.report(t, "Wrap the component calling useSearchParams() in <Suspense>: the static page \"" + page
        + "\" renders it without a boundary and bails out to client rendering.");
    }
  }

  /** Calls of useSearchParams imported from next/navigation, by its local name. */
  private static List<Token> hookCalls(Module m) {
    Set<String> local = new HashSet<>();
    for (Import i : m.imports()) {
      if (i.spec().equals("next/navigation")) {
        i.names().forEach((l, imported) -> {
          if (imported.equals("useSearchParams")) {
            local.add(l);
          }
        });
      }
    }
    List<Token> out = new ArrayList<>();
    if (!local.isEmpty()) {
      for (Chain c : m.file().chains()) {
        if (local.contains(c.firstName()) && c.callAfter(0) != null && !c.precededBy(".")) {
          out.add(c.first());
        }
      }
    }
    return out;
  }

  /**
   * For a page: its URL when it is static and no loading file wraps it. For a layout: the URL of a static page under it,
   * when no loading file wraps the layout. Else null.
   */
  private String staticPageAround(NextModel model, Module entry) {
    String dir = dir(entry.path());
    boolean layout = entry.route().kind().equals("layout");
    for (Module other : appModules) {
      Route r = other.route();
      if (r != null && r.router().equals("app") && r.kind().equals("loading")) {
        String d = dir(other.path());
        if (!layout && d.equals(dir) || under(dir, d)) {
          return null;
        }
      }
      // a layout above mentioning Suspense may wrap its children in one
      if (r != null && r.router().equals("app") && r.kind().equals("layout") && other != entry && mentionsSuspense(other)) {
        String d = dir(other.path());
        if (!layout && d.equals(dir) || under(dir, d)) {
          return null;
        }
      }
    }
    if (!layout) {
      return isStatic(model, entry) ? entry.route().url() : null;
    }
    for (Module p : appModules) {
      Route r = p.route();
      if (r != null && r.router().equals("app") && r.kind().equals("page") && (dir(p.path()).equals(dir) || under(dir(p.path()), dir))
        && isStatic(model, p)) {
        return r.url();
      }
    }
    return null;
  }

  private boolean isStatic(NextModel model, Module page) {
    return isStaticCache.computeIfAbsent(page, p -> computeStatic(model, p));
  }

  private boolean computeStatic(NextModel model, Module page) {
    if (page.route().dynamic() && !page.exports().containsKey("generateStaticParams")) {
      return false;
    }
    String dir = dir(page.path());
    List<Module> scope = new ArrayList<>(List.of(page));
    for (Module l : appModules) {
      Route r = l.route();
      if (r != null && r.router().equals("app") && r.kind().equals("layout")) {
        String d = dir(l.path());
        if (d.equals(dir) || under(dir, d)) {
          scope.add(l);
        }
      }
    }
    for (Module s : scope) {
      for (Module reached : model.reachable(s, t -> t.role() != Role.CLIENT)) {
        if (reached.role() != Role.CLIENT && dynamicCache.computeIfAbsent(reached, SearchParamsWithoutSuspense::dynamic)) {
          return false;
        }
      }
    }
    return true;
  }

  /** The module calls a dynamic API or a session helper, reads searchParams, or opts out of static rendering. */
  private static boolean dynamic(Module m) {
    // next-intl/server reads the locale from the request headers unless this module set it
    boolean intl = m.imports().stream().anyMatch(i -> i.spec().equals("next-intl/server")
      && i.names().values().stream().anyMatch(n -> !n.endsWith("etRequestLocale")));
    if (intl && m.file().tokens().stream().noneMatch(t -> t.isIdent("setRequestLocale") || t.isIdent("unstable_setRequestLocale"))) {
      return true;
    }
    for (Chain c : m.file().chains()) {
      if (DYNAMIC_CALLS.contains(c.firstName()) && c.callAfter(0) != null && !c.precededBy(".")) {
        return true;
      }
    }
    List<Token> tokens = m.file().tokens();
    for (int i = 0; i < tokens.size(); i++) {
      Token t = tokens.get(i);
      if (t.isIdent("searchParams") || t.kind() == TokenKind.STRING && DYNAMIC_STRINGS.contains(JsTrees.unquote(t.text()))) {
        return true;
      }
      if (t.isIdent("revalidate") && i + 2 < tokens.size() && (tokens.get(i + 1).is("=") || tokens.get(i + 1).is(":"))
        && tokens.get(i + 2).is("0")) {
        return true;
      }
    }
    return false;
  }

  /**
   * {@code from} renders {@code to}, directly or through other components, with no Suspense on the way; the last step
   * renders one of {@code names} (null: any name).
   */
  private boolean rendersWithoutSuspense(NextModel model, Module from, Module to, Set<String> names) {
    if (from == to) {
      return !mentionsSuspense(to);
    }
    Set<Module> seen = new HashSet<>();
    Deque<Module> queue = new ArrayDeque<>(List.of(from));
    while (!queue.isEmpty()) {
      Module a = queue.poll();
      if (!seen.add(a) || mentionsSuspense(a)) {
        continue;
      }
      for (Map.Entry<Module, Set<String>> e : rendersCache.computeIfAbsent(a, x -> renders(model, x)).entrySet()) {
        if (e.getKey() != to) {
          queue.add(e.getKey());
        } else if (!mentionsSuspense(to) && (names == null || e.getValue().stream().anyMatch(names::contains))) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * The exported names whose function calls the hook, itself or through a top-level function of its module that does
   * (a component it renders, a custom hook); null when a call sits outside the top-level functions (a class, a memo or
   * forwardRef wrapper).
   */
  private static Set<String> hookExports(Module m, List<Token> calls) {
    Map<String, List<Token>> bodies = new HashMap<>();
    m.functions().forEach((name, fn) -> bodies.put(name, Nodes.tokens(fn.body())));
    Set<String> calling = new HashSet<>();
    for (Token call : calls) {
      String in = bodies.entrySet().stream().filter(e -> e.getValue().contains(call)).map(Map.Entry::getKey).findFirst().orElse(null);
      if (in == null) {
        return null;
      }
      calling.add(in);
    }
    boolean grew = true;
    while (grew) {
      grew = false;
      for (Map.Entry<String, List<Token>> e : bodies.entrySet()) {
        List<Token> t = e.getValue();
        // a function naming one that calls the hook (renders it, or calls it as a custom hook) calls it too
        if (!calling.contains(e.getKey()) && t.stream().anyMatch(x -> x.kind() == TokenKind.IDENT && calling.contains(x.text()))) {
          grew = calling.add(e.getKey());
        }
      }
    }
    Set<String> out = new HashSet<>();
    m.exports().forEach((exported, local) -> {
      if (calling.contains(local.text()) || exported.equals("default") && calling.contains("default")) {
        out.add(exported);
      }
    });
    return out;
  }

  /** The app's next.config sets missingSuspenseWithCSRBailout to false. */
  private boolean optedOut(NextModel model, Module m) {
    NextModel.NextApp app = model.appOf(m.path());
    return app != null && optedOut.computeIfAbsent(app.dir(), dir -> {
      for (String ext : List.of("js", "mjs", "cjs", "ts", "mts")) {
        Module c = model.module(dir + "next.config." + ext);
        List<Token> t = c == null ? List.of() : c.file().tokens();
        for (int i = 0; i + 2 < t.size(); i++) {
          if (t.get(i).isIdent("missingSuspenseWithCSRBailout") && t.get(i + 1).is(":") && t.get(i + 2).isIdent("false")) {
            return true;
          }
        }
      }
      return false;
    });
  }

  /** The project modules whose imported names this module renders as JSX tags, with those names. Static imports only. */
  private static Map<Module, Set<String>> renders(NextModel model, Module m) {
    Set<String> tags = new HashSet<>();
    Nodes.groups(m.file().root(), g -> {
      List<Node> kids = g.children();
      for (int i = 0; i + 1 < kids.size(); i++) {
        Token name = Nodes.leaf(kids.get(i + 1));
        if (Nodes.isLeaf(kids.get(i), "<") && name != null && name.kind() == TokenKind.IDENT) {
          tags.add(name.text());
        }
      }
    });
    Map<Module, Set<String>> out = new LinkedHashMap<>();
    for (Import i : m.imports()) {
      Module t = i.typeOnly() || i.dynamic() || i.reExport() ? null : model.target(i);
      // ponytail: barrels (a target that only re-exports) are not followed, the walk stays silent past them
      if (t != null) {
        i.names().forEach((local, imported) -> {
          if (!imported.equals("*") && tags.contains(local)) {
            out.computeIfAbsent(t, x -> new HashSet<>()).add(imported);
          }
        });
      }
    }
    return out;
  }

  private final Map<Module, Boolean> suspense = new HashMap<>();

  private boolean mentionsSuspense(Module m) {
    return suspense.computeIfAbsent(m, x -> x.file().tokens().stream().anyMatch(t -> t.kind() == TokenKind.IDENT && t.text().contains("Suspense")));
  }

  private static String dir(String path) {
    int slash = path.lastIndexOf('/');
    return slash < 0 ? "" : path.substring(0, slash);
  }

  /** {@code dir} is strictly below {@code ancestor}. */
  private static boolean under(String dir, String ancestor) {
    return ancestor.isEmpty() ? !dir.isEmpty() : dir.startsWith(ancestor + "/");
  }
}
