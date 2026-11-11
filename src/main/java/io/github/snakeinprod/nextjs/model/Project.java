package io.github.snakeinprod.nextjs.model;

import java.util.Set;

/** The files the model reads: the sensor, the command line scanner and the tests each give one. */
public interface Project {

  /** Paths relative to the base directory, "/" separated, of the project's source files. Others are ignored. */
  Set<String> paths();

  /**
   * Content of a file under the base directory, or null when it does not exist. Also asked for files that
   * {@link #paths()} does not list: package.json, tsconfig.json, jsconfig.json, node_modules/next/package.json.
   */
  String read(String path);
}
