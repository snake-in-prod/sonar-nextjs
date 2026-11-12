package io.github.snakeinprod.nextjs.rules;

import io.github.snakeinprod.nextjs.js.JsTrees;
import io.github.snakeinprod.nextjs.model.ActionBuilder;
import io.github.snakeinprod.nextjs.model.Directive;
import io.github.snakeinprod.nextjs.model.Import;
import io.github.snakeinprod.nextjs.model.Module;
import io.github.snakeinprod.nextjs.model.NextModel;
import io.github.snakeinprod.nextjs.model.ServerAction;
import io.github.snakeinprod.nextjs.parse.Chain;
import io.github.snakeinprod.nextjs.parse.Chains;
import io.github.snakeinprod.nextjs.parse.Node;
import io.github.snakeinprod.nextjs.parse.Nodes;
import io.github.snakeinprod.nextjs.parse.Token;
import io.github.snakeinprod.nextjs.parse.TokenKind;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Server Actions that change data should check the session. An exported function of a "use server" module that
 * writes (a database write, a POST/PUT/PATCH/DELETE fetch without credentials in its headers or query string, revalidatePath / revalidateTag) and never
 * checks the session, itself or through the project helpers it calls. Sign-in, sign-up, password, contact, newsletter,
 * guest cart, error monitoring and view-counter actions are public by design and left out; so is an action calling a helper the model cannot see.
 * An action built with an action library is checked when its client middleware or procedure checks the session too,
 * one built by a wrapper ({@code withAuth(async (...) => ...)}) when the wrapper does. An app without sign-in is left
 * out, unless it has an /admin page: then its actions updating or deleting a record are reported. A getter's writes
 * that only store what it read on the server ({@link #withoutSyncs}) do not count.
 * {@link Flow} holds what the endpoint rules share.
 */
public class ServerActionWithoutAuth implements NextRule {

  /** Action or module names of flows that are public by design. */
  static final Pattern PUBLIC_ACTION = Pattern.compile("signin|signup|login|logout|signout|register|password|forgot|reset"
    + "|verif|confirm|subscribe|newsletter|waitlist|contact|feedback|captcha|turnstile|increment|view|track|visit|analytic"
    + "|otp|magiclink|authenticate|cart|basket|sentry|telemetry");
  /** A getter that revalidates refreshes the cache after reading: only a database or fetch write counts. */
  private static final Pattern GETTER = Pattern.compile("(get|fetch|list|find|load|read|search)[A-Z]");
  private static final Set<String> PUBLIC_CALLS = Set.of("signIn", "signUp", "signOut", "register", "createUserWithEmailAndPassword");
  /** Calls that change or remove an existing record. */
  private static final Pattern CHANGE = Pattern.compile("(update|delete|remove|destroy|upsert)\\w*|find\\w*And(Update|Delete|Remove|Replace)|replaceOne");
  private static final Set<String> VALUE_FREE = Set.of("delete", "deleteMany", "deleteOne", "deleteFrom", "destroy", "findOneAndDelete",
    "findByIdAndDelete", "findByIdAndRemove");

  private Flow flow;

  @Override
  public void check(RuleContext ctx) {
    Module m = ctx.module();
    if (!m.has(Directive.USE_SERVER) || isPublic(m.path())) {
      return;
    }
    flow = Flow.of(flow, ctx.model());
    boolean signIn = flow.appChecksSession(m);
    if (!signIn && !flow.appHasAdminPage(m)) {
      return;
    }
    for (ServerAction a : m.serverActions()) {
      if (a.inline() || isPublic(a.name().text())) {
        continue;
      }
      List<Chain> chains = Flow.chains(a.fn());
      boolean getter = GETTER.matcher(a.name().text()).lookingAt();
      if (getter) {
        chains = withoutSyncs(m, a.fn(), chains);
      }
      // without sign-in the admin pages are guarded in the browser at most: an action changing an existing record is
      // open; creating one (a booking, a guestbook entry) is what visitors do
      if (!signIn && chains.stream().noneMatch(c -> Flow.calls(c, n -> CHANGE.matcher(n).matches()))) {
        continue;
      }
      if (flow.writes(m, chains, !getter) && chains.stream().noneMatch(c -> Flow.calls(c, PUBLIC_CALLS::contains))
        && flow.session(m, a.fn()) == Flow.Check.NO && (a.builder() == null || flow.builder(m, a.builder()) == Flow.Check.NO)
        && (a.wrapper() == null || flow.session(a.wrapper().module(), a.wrapper().fn()) == Flow.Check.NO)) {
        ctx.report(a.name(), "Check the session in \"" + a.name().text() + "\" before it changes data: anyone can call a Server Action.");
      }
    }
  }

  /**
   * The chains without the database writes that only store what the action read on the server: a getter mirroring an
   * account status it fetched ({@code tx.update(payments).set({ ready: account.ready }).where(...)}) changes nothing the
   * caller chooses. A write is such a sync when, its where clauses left out, it names no parameter of the action nor a
   * local built from one without an await (a value read from elsewhere is the server's). Deletes are never syncs.
   */
  private List<Chain> withoutSyncs(Module m, JsTrees.Fn fn, List<Chain> chains) {
    Set<String> caller = new HashSet<>();
    if (fn.param() != null) {
      caller.add(fn.param().text());
    } else {
      for (List<Node> item : fn.params().items()) {
        int k = !item.isEmpty() && Nodes.isLeaf(item.get(0), "...") ? 1 : 0;
        if (k < item.size()) {
          ValueFlow.names(item.get(k)).forEach(t -> caller.add(t.text()));
        }
      }
    }
    List<Flow.Decl> decls = Flow.decls(fn.body());
    for (int pass = 0; pass < 3; pass++) {
      for (Flow.Decl d : decls) {
        if (Flow.mentions(d.init(), caller) != null && Nodes.tokens(d.init()).stream().noneMatch(t -> t.isIdent("await"))) {
          caller.addAll(d.names());
        }
      }
    }
    List<Chain> out = new ArrayList<>();
    for (Chain c : chains) {
      if (!flow.isDbCall(m, c) || !sync(c, caller)) {
        out.add(c);
      }
    }
    return out;
  }

  /** A write chain (or a transaction whose every write is one) storing no value of the caller; false for a non-write. */
  private static boolean sync(Chain c, Set<String> caller) {
    List<String> names = c.names();
    for (int i = 1; i < names.size(); i++) {
      if (Flow.TRANSACTIONS.contains(names.get(i)) && c.callAfter(i) != null) {
        List<Chain> writes = Chains.in(c.callAfter(i).args()).stream().filter(w -> w.names().stream().skip(1).anyMatch(Flow.WRITES::contains)).toList();
        return !writes.isEmpty() && writes.stream().allMatch(w -> sync(w, caller));
      }
    }
    boolean write = false;
    List<Node> values = new ArrayList<>();
    for (int i = 0; i < names.size(); i++) {
      String n = names.get(i);
      if (VALUE_FREE.contains(n) || n.startsWith("$")) {
        return false;
      }
      write |= i > 0 && Flow.WRITES.contains(n);
      Chain.Call call = c.callAfter(i);
      if (call != null && !n.equals("where")) {
        for (List<Node> item : call.items()) {
          for (Node x : item) {
            if (x instanceof Node.Group g && g.is("{")) {
              // { where: {...}, data: {...} }: the where clause picks the record
              g.items().stream().filter(e -> !(e.size() > 1 && Nodes.isIdent(e.get(0), "where") && Nodes.isLeaf(e.get(1), ":"))).forEach(values::addAll);
            } else {
              values.add(x);
            }
          }
        }
      }
    }
    return write && Flow.mentions(values, caller) == null;
  }

  private static boolean isPublic(String name) {
    String n = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z/().]", "");
    return PUBLIC_ACTION.matcher(n).find() || n.matches("(.*[/(])?auth[/).].*");
  }

  /** Database clients, writes and session checks, shared by the Server Action and route handler rules. */
  static final class Flow {

    enum Check { YES, NO, UNKNOWN }

    record Decl(List<String> names, List<Node> init) {
    }

    private static final Set<String> KEYWORDS = Chains.keywords("js");
    private static final Pattern DB_SPEC = Pattern.compile("@prisma/client(/.*)?|.*generated/prisma(/.*)?|\\.prisma/client"
      + "|drizzle-orm/(?!.*-core)(?!zod)[a-z0-9-]+|pg|postgres|mysql2(/promise)?|mysql|mongodb|mongoose|@vercel/postgres|kysely"
      + "|@planetscale/database|@neondatabase/serverless|better-sqlite3|sqlite3?|@libsql/client|knex|sequelize|typeorm"
      + "|@electric-sql/pglite|@xata\\.io/client|edgedb|gel");
    static final Set<String> WRITES = Set.of("create", "createMany", "createManyAndReturn", "update", "updateMany",
      "updateManyAndReturn", "upsert", "delete", "deleteMany", "insert", "insertInto", "insertOne", "insertMany", "updateTable",
      "deleteFrom", "updateOne", "replaceOne", "deleteOne", "findOneAndUpdate", "findOneAndDelete", "findOneAndReplace",
      "findByIdAndUpdate", "findByIdAndDelete", "findByIdAndRemove", "bulkWrite", "bulkCreate", "destroy", "$executeRaw",
      "$executeRawUnsafe", "increment", "decrement", "save");
    /** Writes only when the transaction they run writes: {@code db.transaction(async (tx) => tx.select()...)} reads. */
    static final Set<String> TRANSACTIONS = Set.of("transaction", "$transaction");
    private static final Set<String> SQL_CALLS = Set.of("query", "execute", "$queryRaw", "$queryRawUnsafe", "unsafe", "run", "exec");
    private static final Pattern WRITE_SQL = Pattern.compile("(?is)^[`'\"]\\s*(insert|update|delete|replace|upsert|merge|truncate"
      + "|drop|alter)\\b.*");
    private static final Set<String> SESSION_CALLS = Set.of("auth", "getServerSession", "getServerAuthSession", "currentUser",
      "getUser", "getSession", "getAuth", "validateRequest", "getKindeServerSession", "getIronSession", "getToken", "protect",
      "authorize", "authenticate", "getClaims", "getAccessToken", "session", "getServerUser", "withAuth", "unstable_update");
    private static final Pattern SESSION_CALL = Pattern.compile("(get|require|verify|check|validate|ensure|assert|fetch|load|is|has"
      + "|must|with|protect|authorize|authenticate)([A-Z][a-z0-9]*)*(Auth|Session|User|Viewer|Admin|Role|Roles|Permission"
      + "|Permissions|Account|Member|Owner|Identity|Principal|Me|LoggedIn|SignedIn|Authenticated|Authorized|Authed|Access)([A-Z][a-z0-9]*)*"
      + "|auth([A-Z]\\w*)?|current[A-Z]\\w*|session([A-Z]\\w*)?");
    private static final Pattern PATH_LIKE = Pattern.compile("[./~#].*|@/.*|(src|lib|app|server|utils|actions|services|helpers"
      + "|data|db|features|modules)/.*");
    private static final Set<String> FETCH_WRITES = Set.of("post", "put", "patch", "delete");
    private static final Pattern ID = Pattern.compile("id|[a-z]\\w*I[dD]");
    private static final Pattern ADMIN_URL = Pattern.compile("(.*/)?admin(/.*)?");
    private static final Pattern URL_CREDENTIAL = Pattern.compile("(?i)[?&][a-z_-]*(key|token)=");

    private final NextModel model;
    private final Map<Module, Set<String>> db = new HashMap<>();
    private final Map<String, Boolean> appAuth = new HashMap<>();

    private Flow(NextModel model) {
      this.model = model;
    }

    /** The cached flow of this model, or a new one. */
    static Flow of(Flow cached, NextModel model) {
      return cached != null && cached.model == model ? cached : new Flow(model);
    }

    NextModel model() {
      return model;
    }

    /** Every chain of a function body, nested functions included. */
    static List<Chain> chains(JsTrees.Fn fn) {
      return Chains.in(fn.body(), KEYWORDS);
    }

    /** Some name of the chain right before a call satisfies the test. */
    static boolean calls(Chain c, java.util.function.Predicate<String> test) {
      List<Chain.Segment> s = c.segments();
      for (int i = 0; i + 1 < s.size(); i++) {
        if (s.get(i) instanceof Chain.Name n && s.get(i + 1) instanceof Chain.Call && test.test(n.token().text())) {
          return true;
        }
      }
      return false;
    }

    /** Some module of the app holding m (the project when none) calls a session helper: an app without sign-in is public by design. */
    boolean appChecksSession(Module m) {
      NextModel.NextApp app = model.appOf(m.path());
      String dir = app == null ? "" : app.dir();
      return appAuth.computeIfAbsent(dir, d -> model.modules().stream()
        .anyMatch(x -> x.path().startsWith(d) && x.file().chains().stream().anyMatch(Flow::callsSession)));
    }

    /** Some page of the app holding m (the project when none) is under /admin. */
    boolean appHasAdminPage(Module m) {
      NextModel.NextApp app = model.appOf(m.path());
      String dir = app == null ? "" : app.dir();
      return model.modules().stream().anyMatch(x -> x.path().startsWith(dir) && x.route() != null && x.route().kind().equals("page")
        && ADMIN_URL.matcher(x.route().url()).matches());
    }

    /** A session helper called without an id: {@code getUser(userId)} looks a record up, it reads no session. */
    static boolean callsSession(Chain c) {
      List<Chain.Segment> s = c.segments();
      for (int i = 0; i + 1 < s.size(); i++) {
        if (s.get(i) instanceof Chain.Name n && s.get(i + 1) instanceof Chain.Call call && isSessionCall(n.token().text())
          && (call.items().isEmpty() || !isId(call.items().get(0)))) {
          return true;
        }
      }
      return false;
    }

    /** {@code userId}, {@code params.id}, {@code input.userId}. */
    private static boolean isId(List<Node> arg) {
      List<Token> t = Nodes.tokens(arg);
      if (t.size() % 2 == 0) {
        return false;
      }
      for (int i = 0; i < t.size(); i++) {
        if (i % 2 == 0 ? t.get(i).kind() != TokenKind.IDENT : !t.get(i).is(".") && !t.get(i).is("?.")) {
          return false;
        }
      }
      return ID.matcher(t.get(t.size() - 1).text()).matches();
    }

    static boolean isSessionCall(String name) {
      return SESSION_CALLS.contains(name) || SESSION_CALL.matcher(name).matches();
    }

    // ------------------------------------------------------------ database clients

    /** Local names of the module bound to a database client: imported from a driver, from a project module exporting one, or built from one. */
    Set<String> dbNames(Module m) {
      Set<String> out = db.get(m);
      if (out != null) {
        return out;
      }
      out = new HashSet<>();
      db.put(m, out);
      for (Import i : m.imports()) {
        if (i.typeOnly() || i.reExport()) {
          continue;
        }
        Module t = model.target(i);
        for (Map.Entry<String, String> e : i.names().entrySet()) {
          if (DB_SPEC.matcher(i.spec()).matches() || t != null && !e.getValue().equals("*") && exportsDb(t, e.getValue(), 0)) {
            out.add(e.getKey());
          }
        }
      }
      List<Decl> decls = decls(m.file().root());
      for (int pass = 0; pass < 3; pass++) {
        for (Decl d : decls) {
          if (!out.containsAll(d.names()) && (mentions(d.init(), out) != null || requiresDb(d.init()))) {
            out.addAll(d.names());
          }
        }
      }
      return out;
    }

    private boolean exportsDb(Module t, String name, int depth) {
      if (depth > 4) {
        return false;
      }
      Token local = t.exports().get(name);
      if (local != null) {
        if (local.isIdent("default")) {
          List<Node> kids = t.file().root().children();
          for (int i = 0; i < kids.size(); i++) {
            if (Nodes.leaf(kids.get(i)) == local) {
              return mentions(JsTrees.initializer(kids, i + 1), dbNames(t)) != null;
            }
          }
          return false;
        }
        return dbNames(t).contains(local.text());
      }
      for (Import i : t.imports()) {
        String imported = i.names().containsKey(name) ? i.names().get(name) : i.names().containsKey("*") ? name : null;
        if (i.reExport() && !i.typeOnly() && imported != null) {
          Module next = model.target(i);
          if (DB_SPEC.matcher(i.spec()).matches() || next != null && exportsDb(next, imported, depth + 1)) {
            return true;
          }
        }
      }
      return false;
    }

    private static boolean requiresDb(List<Node> init) {
      for (Node n : init) {
        if (n instanceof Node.Group g && !g.children().isEmpty() && Nodes.leaf(g.children().get(0)) != null
          && Nodes.leaf(g.children().get(0)).kind() == TokenKind.STRING && DB_SPEC.matcher(JsTrees.unquote(Nodes.leaf(g.children().get(0)).text())).matches()) {
          return Nodes.tokens(init).stream().anyMatch(t -> t.isIdent("require"));
        }
      }
      return false;
    }

    /** A call on a database client of the module: {@code db.post.create(...)}, {@code sql`...`}. */
    boolean isDbCall(Module m, Chain c) {
      return c.segments().size() >= 2 && dbNames(m).contains(c.firstName())
        || c.segments().size() == 1 && dbNames(m).contains(c.firstName()) && isTemplate(c.after());
    }

    /**
     * The chains write: a database write, a credential-less POST/PUT/PATCH/DELETE fetch, or revalidatePath /
     * revalidateTag after an awaited call (a helper that wrote). Revalidation alone only refreshes the cache.
     */
    boolean writes(Module m, List<Chain> chains) {
      return writes(m, chains, true);
    }

    /** {@link #writes(Module, List)}, revalidation after an awaited call counting only when {@code revalidation} is set. */
    boolean writes(Module m, List<Chain> chains, boolean revalidation) {
      boolean revalidates = false;
      boolean awaits = false;
      for (Chain c : chains) {
        if (isDbCall(m, c) && isDbWrite(c) || isFetchWrite(c)) {
          return true;
        }
        if (c.segments().size() == 2 && c.endsWithCall() && (c.firstName().equals("revalidatePath") || c.firstName().equals("revalidateTag"))) {
          revalidates = true;
        } else {
          awaits |= JsTrees.isAwaited(c) && c.calls().size() > 0;
        }
      }
      return revalidation && revalidates && awaits;
    }

    private static boolean isDbWrite(Chain c) {
      List<String> names = c.names();
      for (int i = 1; i < names.size(); i++) {
        if (WRITES.contains(names.get(i))) {
          return true;
        }
        if (TRANSACTIONS.contains(names.get(i)) && c.callAfter(i) != null) {
          List<Token> t = Nodes.tokens(c.callAfter(i).items().stream().flatMap(List::stream).toList());
          for (int j = 1; j < t.size(); j++) {
            if (t.get(j - 1).is(".") && WRITES.contains(t.get(j).text())) {
              return true;
            }
          }
        }
      }
      if (isTemplate(c.after()) && WRITE_SQL.matcher(Nodes.leaf(c.after()).text()).matches()) {
        return true;
      }
      for (int i = 0; i < names.size(); i++) {
        Chain.Call call = c.callAfter(i);
        if (SQL_CALLS.contains(names.get(i)) && call != null && !call.items().isEmpty()) {
          for (Token t : Nodes.tokens(call.items().get(0))) {
            if (t.kind() == TokenKind.STRING && WRITE_SQL.matcher(t.text()).matches()) {
              return true;
            }
          }
        }
      }
      return false;
    }

    private static boolean isFetchWrite(Chain c) {
      if (c.segments().size() != 2 || !c.firstName().equals("fetch") || !c.endsWithCall()) {
        return false;
      }
      List<List<Node>> items = c.callAfter(0).items();
      if (items.size() < 2) {
        return false;
      }
      // a key or token in the query string is a credential too: fetch(`${API}?key=${process.env.KEY}`, ...)
      if (Nodes.tokens(items.get(0)).stream().anyMatch(u -> u.kind() == TokenKind.STRING && URL_CREDENTIAL.matcher(u.text()).find())) {
        return false;
      }
      List<Token> t = Nodes.tokens(items.get(1));
      boolean write = false;
      for (int i = 0; i < t.size(); i++) {
        String s = JsTrees.unquote(t.get(i).text()).toLowerCase(Locale.ROOT);
        if (s.equals("authorization") || s.equals("cookie") || s.contains("api-key") || s.contains("token")) {
          return false;
        }
        write |= s.equals("method") && i + 2 < t.size() && t.get(i + 1).is(":") && t.get(i + 2).kind() == TokenKind.STRING
          && FETCH_WRITES.contains(JsTrees.unquote(t.get(i + 2).text()).toLowerCase(Locale.ROOT));
      }
      return write;
    }

    private static boolean isTemplate(Node n) {
      Token t = Nodes.leaf(n);
      return t != null && t.kind() == TokenKind.STRING && t.text().startsWith("`");
    }

    // ------------------------------------------------------------ session checks

    /**
     * Whether the function checks the session: a session call or a read of cookies() / headers() in its body, or in a
     * function of the project it calls. UNKNOWN when it calls a project helper the model cannot see.
     */
    Check session(Module m, JsTrees.Fn fn) {
      return session(m, fn, 0, new HashSet<>());
    }

    private Check session(Module m, JsTrees.Fn fn, int depth, Set<JsTrees.Fn> seen) {
      if (!seen.add(fn)) {
        return Check.NO;
      }
      if (depth > 4) {
        return Check.UNKNOWN;
      }
      List<Chain> chains = chains(fn);
      for (Chain c : chains) {
        if (callsSession(c) || calls(c, n -> n.equals("cookies") || n.equals("headers"))) {
          return Check.YES;
        }
      }
      Check result = Check.NO;
      for (Chain c : chains) {
        if (c.segments().size() < 2 || !(c.segments().get(1) instanceof Chain.Call)) {
          continue;
        }
        Check r = helper(m, c.firstName(), fn, depth, seen);
        if (r == Check.YES) {
          return r;
        }
        // a function using a database client (const getSession = async () => ... db ...) counts as one, so it is
        // followed when visible; an unseen database helper is no session check
        if (r == Check.UNKNOWN && !dbNames(m).contains(c.firstName())) {
          result = r;
        }
      }
      return result;
    }

    private Check helper(Module m, String name, JsTrees.Fn caller, int depth, Set<JsTrees.Fn> seen) {
      JsTrees.Fn local = m.functions().get(name);
      if (local != null && local != caller) {
        return session(m, local, depth + 1, seen);
      }
      for (Import i : m.imports()) {
        String imported = i.names().get(name);
        if (imported == null || i.typeOnly()) {
          continue;
        }
        Module t = model.target(i);
        if (t == null) {
          return PATH_LIKE.matcher(i.spec()).matches() ? Check.UNKNOWN : Check.NO;
        }
        NextModel.Found f = model.exportedFunction(t, imported);
        return f == null ? Check.UNKNOWN : session(f.module(), f.fn(), depth + 1, seen);
      }
      return Check.NO;
    }

    /**
     * Whether an action library chain checks the session: a function given to one of its steps does
     * ({@code .use(async ({ next }) => { await auth(); ... })}, a procedure's handler), or the client it starts from
     * ({@code authActionClient.schema(s).action(...)}) or a middleware or procedure it is given by name
     * ({@code .use(requireUser)}, {@code createServerActionProcedure(authed)}) does, followed through imports.
     */
    Check builder(Module m, ActionBuilder b) {
      return builder(m, b, 0, new HashSet<>());
    }

    private Check builder(Module m, ActionBuilder b, int depth, Set<JsTrees.Fn> seen) {
      if (depth > 6) {
        return Check.UNKNOWN;
      }
      Check result = Check.NO;
      List<String> names = new ArrayList<>();
      if (b.client() != null) {
        names.add(b.client().text());
      }
      for (int s = 0; s < b.steps().size(); s++) {
        ActionBuilder.Step step = b.steps().get(s);
        for (JsTrees.Fn f : step.functions()) {
          result = either(result, session(m, f, depth + 1, seen));
        }
        if (step.name().is("use") || s == 0 && b.client() == null) {
          for (List<Node> item : step.args().items()) {
            Token t = item.size() == 1 ? Nodes.leaf(item.get(0)) : null;
            if (t != null && t.kind() == TokenKind.IDENT) {
              names.add(t.text());
            }
          }
        }
      }
      for (String n : names) {
        result = either(result, client(m, n, depth + 1, seen));
      }
      return result;
    }

    /** A client, procedure or middleware by its local name: a builder or a function of the module, or imported. */
    private Check client(Module m, String name, int depth, Set<JsTrees.Fn> seen) {
      ActionBuilder local = m.builder(name);
      if (local != null) {
        return builder(m, local, depth, seen);
      }
      JsTrees.Fn fn = m.functions().get(name);
      if (fn != null) {
        return session(m, fn, depth, seen);
      }
      for (Import i : m.imports()) {
        String imported = i.names().get(name);
        if (imported == null || i.typeOnly()) {
          continue;
        }
        Module t = model.target(i);
        if (t == null) {
          return PATH_LIKE.matcher(i.spec()).matches() ? Check.UNKNOWN : Check.NO;
        }
        NextModel.Export e = model.exportOf(t, imported);
        return e == null ? Check.UNKNOWN : client(e.module(), e.local(), depth + 1, seen);
      }
      return Check.NO;
    }

    private static Check either(Check a, Check b) {
      return a == Check.YES || b == Check.YES ? Check.YES : a == Check.UNKNOWN || b == Check.UNKNOWN ? Check.UNKNOWN : Check.NO;
    }

    // ------------------------------------------------------------ declarations

    /** {@code const|let|var <pattern> = <initializer>}, or {@code let <name>: <type>} without one, anywhere in the tree. */
    static List<Decl> decls(Node.Group root) {
      List<Decl> out = new ArrayList<>();
      Nodes.groups(root, g -> {
        List<Node> k = g.children();
        for (int i = 0; i + 2 < k.size(); i++) {
          if (!(Nodes.isIdent(k.get(i), "const") || Nodes.isIdent(k.get(i), "let") || Nodes.isIdent(k.get(i), "var"))) {
            continue;
          }
          List<String> names = patternNames(k.get(i + 1));
          int j = i + 2;
          while (j < k.size() && j < i + 16 && !Nodes.isLeaf(k.get(j), "=") && !Nodes.isLeaf(k.get(j), ";")) {
            j++;
          }
          if (!names.isEmpty() && j < k.size() && Nodes.isLeaf(k.get(j), "=")) {
            out.add(new Decl(names, JsTrees.initializer(k, j + 1)));
          } else if (!names.isEmpty() && Nodes.isLeaf(k.get(i + 2), ":")) {
            // let prisma: PrismaClient; assigned later: the type tells
            out.add(new Decl(names, k.subList(i + 3, j)));
          }
        }
      });
      return out;
    }

    /** Names bound by a declaration pattern: {@code x}, {@code { a, b: c, ...d }}, {@code [a, b]}. */
    static List<String> patternNames(Node pattern) {
      List<String> out = new ArrayList<>();
      Token t = Nodes.leaf(pattern);
      if (t != null) {
        if (t.kind() == TokenKind.IDENT && !KEYWORDS.contains(t.text())) {
          out.add(t.text());
        }
        return out;
      }
      Node.Group g = (Node.Group) pattern;
      if (!g.is("{") && !g.is("[")) {
        return out;
      }
      for (List<Node> item : g.items()) {
        Node n = item.size() >= 3 && Nodes.isLeaf(item.get(1), ":") ? item.get(2)
          : Nodes.isLeaf(item.get(0), "...") && item.size() > 1 ? item.get(1) : item.get(0);
        Token name = Nodes.leaf(n);
        if (name != null && name.kind() == TokenKind.IDENT) {
          out.add(name.text());
        }
      }
      return out;
    }

    /** The first token using one of the names as a value (not a property after "." nor an object key), or null. */
    static Token mentions(List<Node> nodes, Set<String> names) {
      List<Token> t = Nodes.tokens(nodes);
      for (int i = 0; i < t.size(); i++) {
        Token x = t.get(i);
        if (x.kind() == TokenKind.IDENT && names.contains(x.text()) && (i == 0 || !t.get(i - 1).is(".") && !t.get(i - 1).is("?."))) {
          return x;
        }
      }
      return null;
    }
  }
}
