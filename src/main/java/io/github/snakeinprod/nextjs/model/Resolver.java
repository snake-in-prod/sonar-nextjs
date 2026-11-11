package io.github.snakeinprod.nextjs.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Resolves import specifiers to project files, in TypeScript's order: relative paths; then, with the nearest
 * tsconfig.json or jsconfig.json (its "extends" chain followed), compilerOptions.paths and baseUrl; then workspace
 * packages, by the name in their package.json. A path resolves as written, with a JS or TS extension, as a directory
 * index, or from ".js" to the ".ts" source.
 */
final class Resolver {

  private static final Pattern JS_FILE = Pattern.compile(".*\\.[cm]?jsx?");
  private static final Pattern DECLARATION = Pattern.compile(".*\\.d\\.[cm]?ts");
  static final List<String> EXTENSIONS = List.of(".ts", ".tsx", ".js", ".jsx", ".mjs", ".cjs", ".mts", ".cts");

  /** Effective compiler options of one config file: directories end with "/" ("" is the base directory). */
  private record Config(String baseUrl, Map<String, List<String>> paths, String pathsBase) {
  }

  private final Set<String> paths;
  private final Function<String, String> read;
  private final Map<String, Config> configs = new HashMap<>();
  /** Results by importing directory and specifier: a monorepo imports the same few specifiers from thousands of files. */
  private final Map<String, String> resolved = new HashMap<>();
  private final Map<String, JsonObject> packageJsons = new HashMap<>();
  private Map<String, String> packages;

  Resolver(Set<String> paths, Function<String, String> read) {
    this.paths = new HashSet<>(paths);
    this.read = read;
  }

  /** The project file {@code spec} names when imported from {@code from}, or null. */
  String resolve(String from, String spec) {
    String dir = dir(from);
    String key = dir + "\0" + spec;
    if (!resolved.containsKey(key)) {
      resolved.put(key, resolveFrom(dir, spec));
    }
    return resolved.get(key);
  }

  private String resolveFrom(String dir, String spec) {
    if (spec.startsWith(".") || spec.startsWith("/")) {
      return file(join(spec.startsWith("/") ? "" : dir, spec));
    }
    Config c = config(dir);
    if (c != null) {
      String r = withConfig(c, spec);
      if (r != null) {
        return r;
      }
    }
    return workspacePackage(spec);
  }

  private String withConfig(Config c, String spec) {
    if (c.paths() != null) {
      String best = null;
      String captured = null;
      for (String pattern : c.paths().keySet()) {
        int star = pattern.indexOf('*');
        if (star < 0) {
          if (pattern.equals(spec)) {
            best = pattern;
            captured = "";
            break;
          }
          continue;
        }
        String prefix = pattern.substring(0, star);
        String suffix = pattern.substring(star + 1);
        if (spec.startsWith(prefix) && spec.endsWith(suffix) && spec.length() >= prefix.length() + suffix.length()
          && (best == null || prefix.length() > best.indexOf('*'))) {
          best = pattern;
          captured = spec.substring(prefix.length(), spec.length() - suffix.length());
        }
      }
      if (best != null) {
        String base = c.baseUrl() != null ? c.baseUrl() : c.pathsBase();
        for (String target : c.paths().get(best)) {
          String r = file(join(base, target.replace("*", captured)));
          if (r != null) {
            return r;
          }
        }
      }
    }
    return c.baseUrl() == null ? null : file(join(c.baseUrl(), spec));
  }

  /** The candidates of a path without extension, the first that is a project file. */
  String file(String base) {
    if (base == null) {
      return null;
    }
    if (paths.contains(base)) {
      return base;
    }
    for (String ext : EXTENSIONS) {
      if (paths.contains(base + ext)) {
        return base + ext;
      }
    }
    for (String ext : EXTENSIONS) {
      if (paths.contains(base + "/index" + ext)) {
        return base + "/index" + ext;
      }
    }
    if (JS_FILE.matcher(base).matches()) {
      String stem = base.substring(0, base.lastIndexOf('.'));
      for (String ext : List.of(".ts", ".tsx", ".mts", ".cts")) {
        if (paths.contains(stem + ext)) {
          return stem + ext;
        }
      }
    }
    return null;
  }

  // ---------------------------------------------------------------- tsconfig / jsconfig

  /** The config of the nearest tsconfig.json or jsconfig.json at or above {@code dir}, or null. */
  private Config config(String dir) {
    if (configs.containsKey(dir)) {
      return configs.get(dir);
    }
    Config c = null;
    boolean found = false;
    for (String name : List.of("tsconfig.json", "jsconfig.json")) {
      String text = read.apply(dir + name);
      if (text != null) {
        c = load(dir + name, text, 0);
        found = true;
        break;
      }
    }
    if (!found && !dir.isEmpty()) {
      c = config(dir(dir.substring(0, dir.length() - 1)));
    }
    configs.put(dir, c);
    return c;
  }

  private Config load(String file, String text, int depth) {
    JsonObject o = Jsonc.parse(text);
    if (o == null || depth > 10) {
      return null;
    }
    Config parent = null;
    JsonElement ext = o.get("extends");
    List<String> parents = new ArrayList<>();
    if (ext != null && ext.isJsonPrimitive()) {
      parents.add(ext.getAsString());
    } else if (ext != null && ext.isJsonArray()) {
      for (JsonElement e : (JsonArray) ext) {
        if (e.isJsonPrimitive()) {
          parents.add(e.getAsString());
        }
      }
    }
    for (String p : parents) {
      // later entries of an array override earlier ones
      Config pc = extended(file, p, depth);
      if (pc != null) {
        parent = parent == null ? pc : new Config(pc.baseUrl() != null ? pc.baseUrl() : parent.baseUrl(),
          pc.paths() != null ? pc.paths() : parent.paths(), pc.paths() != null ? pc.pathsBase() : parent.pathsBase());
      }
    }
    JsonObject options = Jsonc.object(o, "compilerOptions");
    String baseUrlText = Jsonc.string(options, "baseUrl");
    String baseUrl = baseUrlText != null ? asDir(join(dir(file), baseUrlText)) : parent == null ? null : parent.baseUrl();
    JsonObject pathsJson = Jsonc.object(options, "paths");
    Map<String, List<String>> map = parent == null ? null : parent.paths();
    String pathsBase = parent == null ? null : parent.pathsBase();
    if (pathsJson != null) {
      map = new LinkedHashMap<>();
      pathsBase = dir(file);
      for (Map.Entry<String, JsonElement> e : pathsJson.entrySet()) {
        List<String> targets = new ArrayList<>();
        if (e.getValue().isJsonArray()) {
          for (JsonElement t : e.getValue().getAsJsonArray()) {
            if (t.isJsonPrimitive()) {
              targets.add(t.getAsString());
            }
          }
        }
        map.put(e.getKey(), targets);
      }
    }
    return new Config(baseUrl, map, pathsBase);
  }

  /** An "extends" entry: a relative path, or a package config under node_modules of this directory or one above. */
  private Config extended(String file, String spec, int depth) {
    List<String> candidates = new ArrayList<>();
    if (spec.startsWith(".")) {
      String p = join(dir(file), spec);
      if (p != null) {
        candidates.addAll(List.of(p, p + ".json"));
      }
    } else {
      for (String d = dir(file);; d = dir(d.substring(0, d.length() - 1))) {
        candidates.addAll(List.of(d + "node_modules/" + spec, d + "node_modules/" + spec + ".json",
          d + "node_modules/" + spec + "/tsconfig.json"));
        if (d.isEmpty()) {
          break;
        }
      }
    }
    for (String c : candidates) {
      String text = c.endsWith(".json") ? read.apply(c) : null;
      if (text != null) {
        return load(c, text, depth + 1);
      }
    }
    return null;
  }

  // ---------------------------------------------------------------- workspace packages

  /** {@code @scope/name[/sub]} or {@code name[/sub]} of a project package: its entry, or the sub path in it. */
  private String workspacePackage(String spec) {
    int n = spec.startsWith("@") ? 2 : 1;
    String[] parts = spec.split("/", n + 1);
    if (parts.length < n) {
      return null;
    }
    String dir = packages().get(n == 2 ? parts[0] + "/" + parts[1] : parts[0]);
    if (dir == null) {
      return null;
    }
    if (!packageJsons.containsKey(dir)) {
      packageJsons.put(dir, Jsonc.parse(read.apply(dir + "package.json")));
    }
    JsonObject json = packageJsons.get(dir);
    List<String> candidates = new ArrayList<>();
    if (parts.length > n) {
      JsonObject exports = Jsonc.object(json, "exports");
      if (exports != null) {
        condition(exports.get("./" + parts[n]), candidates);
      }
      candidates.add(parts[n]);
      candidates.add("src/" + parts[n]);
    } else {
      candidates.addAll(entries(json));
      candidates.addAll(List.of("src/index", "index"));
    }
    for (String c : candidates) {
      String r = file(join(dir, c));
      if (r != null && !DECLARATION.matcher(r).matches()) {
        return r;
      }
    }
    return null;
  }

  /** The entries a package.json names, in order: "exports" (a string or the "." entry), "module", "main", "source". */
  private static List<String> entries(JsonObject o) {
    List<String> out = new ArrayList<>();
    if (o == null) {
      return out;
    }
    JsonElement exports = o.get("exports");
    if (exports != null && exports.isJsonObject() && exports.getAsJsonObject().has(".")) {
      exports = exports.getAsJsonObject().get(".");
    }
    condition(exports, out);
    for (String k : List.of("module", "main", "source")) {
      String v = Jsonc.string(o, k);
      if (v != null) {
        out.add(v);
      }
    }
    return out;
  }

  /** A string export, or the import, default and types conditions of an object, nested ones too. */
  private static void condition(JsonElement e, List<String> out) {
    if (e != null && e.isJsonPrimitive()) {
      out.add(e.getAsString());
    } else if (e != null && e.isJsonObject()) {
      for (String k : List.of("import", "default", "types")) {
        condition(e.getAsJsonObject().get(k), out);
      }
    }
  }

  /** Package name to its directory ("" or "a/b/"): every directory above a project file with a named package.json. */
  private Map<String, String> packages() {
    if (packages == null) {
      packages = new HashMap<>();
      // sorted: a package above another of the same name wins
      for (String dir : directories(paths)) {
        String name = Jsonc.string(Jsonc.parse(read.apply(dir + "package.json")), "name");
        if (name != null) {
          packages.putIfAbsent(name, dir);
        }
      }
    }
    return packages;
  }

  // ---------------------------------------------------------------- paths

  /** Every directory holding or above a project file, "" included, sorted, node_modules left out. */
  static Set<String> directories(Set<String> paths) {
    Set<String> dirs = new TreeSet<>(Set.of(""));
    for (String p : paths) {
      for (int i = p.indexOf('/'); i >= 0; i = p.indexOf('/', i + 1)) {
        String d = p.substring(0, i + 1);
        if (!d.contains("node_modules/")) {
          dirs.add(d);
        }
      }
    }
    return dirs;
  }

  /** The directory of a path with a trailing "/", "" at the base directory. */
  static String dir(String path) {
    return path.substring(0, path.lastIndexOf('/') + 1);
  }

  private static String asDir(String path) {
    return path == null ? null : path.isEmpty() ? "" : path + "/";
  }

  /** {@code dir} + {@code relative} with "." and ".." resolved, or null when it leaves the base directory. */
  static String join(String dir, String relative) {
    List<String> parts = new ArrayList<>();
    for (String s : (dir + relative).split("/")) {
      if (s.equals("..")) {
        if (parts.isEmpty()) {
          return null;
        }
        parts.remove(parts.size() - 1);
      } else if (!s.isEmpty() && !s.equals(".")) {
        parts.add(s);
      }
    }
    return String.join("/", parts);
  }
}
