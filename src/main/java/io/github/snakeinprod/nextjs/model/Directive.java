package io.github.snakeinprod.nextjs.model;

/**
 * File-level markers: a {@code "use client"}, {@code "use server"} or {@code "use cache"} directive at the top of the
 * file, or an {@code import "server-only"} / {@code import "client-only"} anywhere in it.
 */
public enum Directive {
  USE_CLIENT, USE_SERVER, USE_CACHE, SERVER_ONLY, CLIENT_ONLY
}
