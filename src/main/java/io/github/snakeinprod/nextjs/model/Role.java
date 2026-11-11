package io.github.snakeinprod.nextjs.model;

/**
 * Where a module runs. CLIENT: a "use client" module or one it reaches through imports (it is in the browser bundle,
 * even when server modules import it too). SERVER: not client, and a server entry (an app/ module, a route handler, an
 * API route, middleware, a "use server" module) or reached from one. SHARED: neither, e.g. Pages Router pages and
 * modules nothing imports.
 */
public enum Role {
  CLIENT, SERVER, SHARED
}
