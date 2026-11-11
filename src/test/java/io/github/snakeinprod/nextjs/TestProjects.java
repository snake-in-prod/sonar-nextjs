package io.github.snakeinprod.nextjs;

import io.github.snakeinprod.nextjs.model.Project;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

/** Projects for tests: a fixture directory, or files in memory. */
public final class TestProjects {

  private TestProjects() {
  }

  /** Every file under the directory (node_modules included, as the sensor's read function would see them). */
  public static Map<String, String> files(Path dir) {
    Map<String, String> out = new TreeMap<>();
    try (Stream<Path> s = Files.walk(dir)) {
      for (Path p : s.filter(Files::isRegularFile).toList()) {
        out.put(dir.relativize(p).toString().replace('\\', '/'), Files.readString(p, StandardCharsets.UTF_8));
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return out;
  }

  /** A project of these files; {@code paths()} lists them all, node_modules left out as a scanner would. */
  public static Project of(Map<String, String> files) {
    return new Project() {
      @Override
      public Set<String> paths() {
        Set<String> out = new java.util.TreeSet<>(files.keySet());
        out.removeIf(p -> p.contains("node_modules/"));
        return out;
      }

      @Override
      public String read(String path) {
        return files.get(path);
      }
    };
  }
}
