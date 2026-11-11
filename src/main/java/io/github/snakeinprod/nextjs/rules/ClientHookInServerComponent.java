package io.github.snakeinprod.nextjs.rules;

import io.github.snakeinprod.nextjs.model.Directive;
import io.github.snakeinprod.nextjs.model.Import;
import io.github.snakeinprod.nextjs.model.Module;
import io.github.snakeinprod.nextjs.model.NextModel;
import io.github.snakeinprod.nextjs.model.Role;
import io.github.snakeinprod.nextjs.parse.Chain;
import io.github.snakeinprod.nextjs.parse.Nodes;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Server components should not call client hooks: a call of a client-only hook imported from react, react-dom or
 * next/navigation in an app/ module rendered on the server (no "use client", not reached from a client module). Only
 * modules Next.js really puts in the server graph: App Router convention files and the modules they reach through
 * server modules (an app/ file imported by nobody, or only by tests, stories or such orphans, may be used from
 * elsewhere). One issue per module, on the first call: the fix is one "use client" line.
 */
public class ClientHookInServerComponent implements NextRule {

  /** use, useId, useMemo, useCallback, useDebugValue work in server components and are left out. */
  private static final Map<String, Set<String>> HOOKS = Map.of(
    "react", Set.of("useState", "useEffect", "useReducer", "useRef", "useContext", "useLayoutEffect", "useInsertionEffect",
      "useImperativeHandle", "useSyncExternalStore", "useTransition", "useDeferredValue", "useOptimistic", "useActionState",
      "useEffectEvent"),
    "react-dom", Set.of("useFormStatus", "useFormState"),
    "next/navigation", Set.of("useRouter", "usePathname", "useSearchParams", "useParams", "useSelectedLayoutSegment",
      "useSelectedLayoutSegments", "useServerInsertedHTML"));

  private static final Pattern TEST_PATH = Pattern.compile(".*(\\.(test|spec|stories)\\.[^/]+|__tests__/.*)");

  private NextModel serverGraphModel;
  private Set<Module> serverGraph;

  @Override
  public void check(RuleContext ctx) {
    Module m = ctx.module();
    if (!m.inAppDir() || m.has(Directive.USE_CLIENT) || m.role() != Role.SERVER || !inServerGraph(ctx, m)) {
      return;
    }
    Map<String, String> hooks = new HashMap<>();
    Map<String, Set<String>> namespaces = new HashMap<>();
    for (Import i : m.imports()) {
      Set<String> known = HOOKS.get(i.spec());
      if (known == null || i.typeOnly()) {
        continue;
      }
      i.names().forEach((local, imported) -> {
        if (known.contains(imported)) {
          hooks.put(local, imported);
        } else if (imported.equals("*") || imported.equals("default")) {
          namespaces.put(local, known);
        }
      });
    }
    if (hooks.isEmpty() && namespaces.isEmpty()) {
      return;
    }
    for (Chain c : ctx.file().chains()) {
      String hook = null;
      if (hooks.containsKey(c.firstName()) && called(c, 0)) {
        hook = hooks.get(c.firstName());
      } else if (namespaces.containsKey(c.firstName()) && c.names().size() > 1 && namespaces.get(c.firstName()).contains(c.names().get(1))
        && called(c, 1)) {
        hook = c.names().get(1);
      }
      if (hook != null) {
        ctx.report(c.first(), "Add \"use client\" to this module or move " + hook
          + " into a client component: client hooks fail in server components.");
        return;
      }
    }
  }

  /** The name at {@code index} is called: {@code useState(0)}, or {@code useState<T>(...)} (the chain stops at "<"). */
  private static boolean called(Chain c, int index) {
    return c.callAfter(index) != null || c.names().size() == index + 1 && !c.endsWithCall() && Nodes.isLeaf(c.after(), "<");
  }

  /** Modules reached from an App Router convention file through server modules (tests and stories left out). */
  private boolean inServerGraph(RuleContext ctx, Module m) {
    if (serverGraph == null || serverGraphModel != ctx.model()) {
      serverGraphModel = ctx.model();
      serverGraph = new HashSet<>();
      for (Module r : ctx.model().modules()) {
        if (r.route() != null && r.route().router().equals("app") && r.role() == Role.SERVER) {
          serverGraph.addAll(ctx.model().reachable(r, x -> x.role() == Role.SERVER
            && !TEST_PATH.matcher(x.path()).matches()));
        }
      }
    }
    return serverGraph.contains(m);
  }
}
