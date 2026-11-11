package io.github.snakeinprod.nextjs.model;

/**
 * A Next.js convention file.
 *
 * @param router  "app", "pages" or "middleware"
 * @param kind    App Router: page, layout, route, loading, error, global-error, not-found, template, default. Pages
 *                Router: page, api, app, document, error. "middleware" for middleware.ts / proxy.ts
 * @param url     the URL path, route groups, parallel slots and interception markers removed ("/blog/[slug]"); null
 *                for middleware
 * @param dynamic the URL has a dynamic or catch-all segment
 */
public record Route(String router, String kind, String url, boolean dynamic) {

  /** A page or an endpoint: App Router page or route handler, Pages Router page or API route. */
  public boolean isRoute() {
    return kind.equals("page") || kind.equals("route") || kind.equals("api");
  }

  /** A route handler or an API route. */
  public boolean isEndpoint() {
    return kind.equals("route") || kind.equals("api");
  }
}
