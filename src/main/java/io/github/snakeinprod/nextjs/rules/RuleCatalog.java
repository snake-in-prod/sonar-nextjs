package io.github.snakeinprod.nextjs.rules;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Supplier;

/**
 * Every rule with its metadata. The same rules go to two repositories, nextjs-js (language js) and nextjs-ts
 * (language ts). {@code active}: in the built-in "Next.js" profile. A rule's longer description, when it has one, is
 * {@code /rules/<Key>.html} on the classpath and replaces the generated one.
 */
public final class RuleCatalog {

  private RuleCatalog() {
  }

  public enum Type { VULNERABILITY, BUG, CODE_SMELL }

  public record Spec(String key, Type type, String severity, boolean active, String name, String description, String example,
    Supplier<NextRule> factory) {

    public String html() {
      try (InputStream in = RuleCatalog.class.getResourceAsStream("/rules/" + key + ".html")) {
        if (in != null) {
          return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
      } catch (IOException e) {
        // fall back to the generated description
      }
      return "<p>" + escape(description) + "</p>\n<h2>Noncompliant code example</h2>\n<pre>\n" + escape(example) + "\n</pre>\n";
    }

    private static String escape(String s) {
      return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
  }

  private static Spec spec(String key, Type type, String severity, boolean active, String name, String description, String example,
    Supplier<NextRule> factory) {
    return new Spec(key, type, severity, active, name, description, example, factory);
  }

  public static final List<Spec> ALL = List.of(
    spec("ServerCodeInClientBundle", Type.VULNERABILITY, "CRITICAL", true, "Client components should not import server-only code",
      "A \"use client\" module that imports, directly or through other modules, server-only, a Node.js built-in (fs, child_process, net, node:*), a database client (prisma, drizzle, pg, mysql2, mongodb, kysely...) or a module reading non-public environment variables. That code is bundled for the browser, where it leaks server details or breaks the build.",
      "'use client';\nimport { db } from '@/lib/db'; // Noncompliant: lib/db imports a database client", ServerCodeInClientBundle::new),
    spec("SecretInPublicEnv", Type.VULNERABILITY, "CRITICAL", true, "Secrets should not be exposed through NEXT_PUBLIC_ variables",
      "NEXT_PUBLIC_ variables are inlined into the browser bundle. A name that says secret, password, private key, service role or server token means a secret is shipped to every visitor. Publishable, anon and public keys and ids are fine.",
      "const key = process.env.NEXT_PUBLIC_API_SECRET; // Noncompliant", SecretInPublicEnv::new),
    spec("ServerEnvInClient", Type.BUG, "MAJOR", true, "Client code should only read NEXT_PUBLIC_ environment variables",
      "Next.js inlines only NEXT_PUBLIC_ variables into the browser bundle. Any other process.env read in client code is undefined in the browser. NODE_ENV is allowed.",
      "'use client';\nconst url = process.env.API_URL; // Noncompliant: undefined in the browser", ServerEnvInClient::new),
    spec("ServerActionWithoutAuth", Type.VULNERABILITY, "CRITICAL", true, "Server Actions that change data should check the session",
      "An exported \"use server\" function is a public HTTP endpoint. One that writes (a database call, a POST/PUT/PATCH/DELETE fetch, revalidatePath/revalidateTag, cookies().set) without checking the session (auth(), getServerSession, currentUser, getUser, getSession or a project helper that does) can be called by anyone.",
      "'use server';\nexport async function deletePost(id) { // Noncompliant\n  await db.post.delete({ where: { id } });\n}", ServerActionWithoutAuth::new),
    spec("ServerActionWithoutValidation", Type.VULNERABILITY, "MAJOR", true, "Server Action inputs should be validated before use",
      "Server Action arguments and FormData come from the client unchecked. Passing them to a database call or a redirect without a schema parse (zod, valibot, yup, arktype) or explicit checks lets a caller send any shape or value.",
      "'use server';\nexport async function save(formData) {\n  await db.user.update({ data: { role: formData.get('role') } }); // Noncompliant\n}", ServerActionWithoutValidation::new),
    spec("RouteHandlerWithoutAuth", Type.VULNERABILITY, "MAJOR", true, "Route handlers that change data should check the caller",
      "An app/**/route.(ts|js) exporting POST, PUT, PATCH or DELETE with no session or token check lets anyone change data.",
      "export async function DELETE(req) { // Noncompliant\n  await db.item.deleteMany();\n}", RouteHandlerWithoutAuth::new),
    spec("MiddlewareMatcherGap", Type.VULNERABILITY, "MAJOR", true, "A middleware that checks the session should match every protected route",
      "A middleware (or Next 16 proxy) that checks the session runs only for the paths its config.matcher matches (and a Clerk middleware protects only what its createRouteMatcher lists). Pages and route handlers under dashboard, admin, settings or account that the matcher leaves out and that check nothing themselves (no session call, layout check, auth hook or guard) are open to anyone.",
      "export default auth;\nexport const config = { matcher: ['/dashboard/:path*'] }; // Noncompliant: app/settings/page.tsx checks nothing", MiddlewareMatcherGap::new),
    spec("PersonalizedCachedFetch", Type.VULNERABILITY, "CRITICAL", true, "Cached data should not depend on the user",
      "An unstable_cache callback or a \"use cache\" function that depends on cookies(), headers() or the session without taking the value as an argument or key part is cached once and served to every user.",
      "const session = await auth();\nconst getPlan = unstable_cache(async () => db.plan.find({ where: { userId: session.user.id } }), ['plan']); // Noncompliant", PersonalizedCachedFetch::new),
    spec("OpenRedirect", Type.VULNERABILITY, "MAJOR", true, "Redirect targets from the request should be checked",
      "redirect(), permanentRedirect(), NextResponse.redirect(), Response.redirect(), res.redirect() / res.writeHead() of a Pages Router API route, router.push() / replace() of useRouter() or window.location with a target from searchParams, params, form data, the Pages Router query, the request body of an endpoint or a Server Action argument (also a field of an object literal or of what a zod schema parsed, unless the schema restricts it), not checked against an allow-list or the same origin, lets an attacker send users anywhere from a trusted link.",
      "redirect(searchParams.next); // Noncompliant", OpenRedirect::new),
    spec("UnsafeHtmlFromInput", Type.VULNERABILITY, "CRITICAL", true, "dangerouslySetInnerHTML should not render unsanitized input",
      "dangerouslySetInnerHTML with a value from params, searchParams, form data, the Pages Router query or a fetch response, not sanitized (DOMPurify, sanitize-html, xss), is cross-site scripting.",
      "<div dangerouslySetInnerHTML={{ __html: searchParams.q }} /> // Noncompliant", UnsafeHtmlFromInput::new),
    spec("AsyncClientComponent", Type.BUG, "MAJOR", true, "Client components should not be async functions",
      "An async default export in a \"use client\" file: client components cannot be async, React fails at render.",
      "'use client';\nexport default async function Profile() { // Noncompliant", AsyncClientComponent::new),
    spec("ClientHookInServerComponent", Type.BUG, "MAJOR", true, "Server components should not call client hooks",
      "useState, useEffect, useReducer, useRef, useContext, useLayoutEffect, useRouter (next/navigation) and other client hooks in an app/ module rendered on the server (no \"use client\", reached from a server module) fail at build or render.",
      "// app/page.tsx\nexport default function Page() {\n  const [open, setOpen] = useState(false); // Noncompliant", ClientHookInServerComponent::new),
    spec("SyncParamsAccess", Type.BUG, "MAJOR", true, "params and searchParams should be awaited in Next.js 15 and later",
      "Since Next.js 15, params and searchParams of pages, layouts, route handlers and generateMetadata are promises. Reading them without await or React.use gives undefined values (and warnings until they are removed).",
      "export default function Page({ params }) {\n  return <h1>{params.slug}</h1>; // Noncompliant", SyncParamsAccess::new),
    spec("MetadataExportedTwice", Type.BUG, "MAJOR", true, "A module should not export both metadata and generateMetadata",
      "Next.js refuses a page or layout that exports both the metadata object and generateMetadata.",
      "export const metadata = { title: 'Blog' };\nexport async function generateMetadata() {} // Noncompliant", MetadataExportedTwice::new),
    spec("SearchParamsWithoutSuspense", Type.BUG, "MINOR", false, "useSearchParams should be wrapped in a Suspense boundary",
      "useSearchParams() in a client component used by a statically rendered page without a Suspense boundary makes the whole page render on the client, or fails the build.",
      "'use client';\nconst q = useSearchParams(); // Noncompliant: no <Suspense> above it", SearchParamsWithoutSuspense::new),
    spec("EdgeRuntimeNodeImport", Type.BUG, "MAJOR", true, "Edge runtime code should not import Node.js-only modules",
      "A route, page, layout or middleware running on the Edge runtime (export const runtime = 'edge', or middleware without runtime: 'nodejs') that imports, directly or through project modules, a Node.js built-in the Edge runtime lacks (fs, child_process, net, crypto...) or a package that needs Node.js (bcrypt, pg, mysql2, mongoose, nodemailer, sharp, jsonwebtoken, PrismaClient without a driver adapter). It fails at build or when the code runs.",
      "// middleware.ts\nimport jwt from 'jsonwebtoken'; // Noncompliant: needs Node.js crypto", EdgeRuntimeNodeImport::new),
    spec("ImageFillWithoutSizes", Type.CODE_SMELL, "MINOR", false, "next/image with fill should set sizes",
      "An Image with fill and no sizes downloads the largest image for every viewport.",
      "<Image src={src} alt=\"\" fill /> // Noncompliant", ImageFillWithoutSizes::new));

  public static final List<String> LANGUAGES = List.of("js", "ts");

  /** Repository key for a SonarQube language key: nextjs-js or nextjs-ts. */
  public static String repository(String language) {
    return "nextjs-" + language;
  }

  /** The spec of a rule key, or null. */
  public static Spec spec(String key) {
    return ALL.stream().filter(s -> s.key().equals(key)).findFirst().orElse(null);
  }
}
