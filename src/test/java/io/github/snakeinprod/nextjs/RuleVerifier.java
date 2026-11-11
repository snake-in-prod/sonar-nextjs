package io.github.snakeinprod.nextjs;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.snakeinprod.nextjs.js.JsLexer;
import io.github.snakeinprod.nextjs.model.Module;
import io.github.snakeinprod.nextjs.model.NextModel;
import io.github.snakeinprod.nextjs.parse.Token;
import io.github.snakeinprod.nextjs.rules.NextRule;
import io.github.snakeinprod.nextjs.rules.RuleCatalog;
import io.github.snakeinprod.nextjs.sensor.Analyzer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Checks one rule against a fixture: {@code src/test/resources/fixtures/<Key>.<ts|tsx|js|jsx>}, a single file at the
 * project root, or a directory {@code fixtures/<Key>/} holding a small app (app/, components/, lib/...). A fixture
 * without a package.json gets one declaring next 15. Every file of the fixture is analysed. Every line with a comment
 * containing {@code Noncompliant} must get exactly one issue (or N with {@code Noncompliant N}); {@code {{text}}}
 * after it must be part of the message. No other line may get an issue. The marks are blanked out before analysis, so
 * a rule that reads comments sees only the fixture's own.
 */
public final class RuleVerifier {

  private static final Pattern MARK = Pattern.compile("Noncompliant(?:\\s+(\\d+))?(?:\\s*\\{\\{(.*?)}})?");
  private static final Path BASE = Path.of("src/test/resources/fixtures");
  private static final String DEFAULT_PACKAGE = "{ \"dependencies\": { \"next\": \"15.0.0\" } }";

  private RuleVerifier() {
  }

  /** A catalog rule against its fixture. */
  public static void verify(String key) {
    RuleCatalog.Spec spec = RuleCatalog.spec(key);
    assertThat(spec).as("rule " + key + " in the catalog").isNotNull();
    verify(key, spec.factory().get());
  }

  /** Any rule against the fixture named {@code key}; returns the model, for further checks. */
  public static NextModel verify(String key, NextRule rule) {
    Map<String, String> files = new TreeMap<>();
    Path dir = BASE.resolve(key);
    if (Files.isDirectory(dir)) {
      files.putAll(TestProjects.files(dir));
    } else {
      try (Stream<Path> s = Files.list(BASE)) {
        s.filter(p -> p.getFileName().toString().matches(Pattern.quote(key) + "\\.[cm]?[jt]sx?"))
          .forEach(p -> files.put(p.getFileName().toString(), content(p)));
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
    assertThat(files).as("fixture for " + key).isNotEmpty();
    files.putIfAbsent("package.json", DEFAULT_PACKAGE);
    Map<String, List<Token>> marks = new HashMap<>();
    Map<String, String> blanked = new TreeMap<>(files);
    for (Map.Entry<String, String> e : files.entrySet()) {
      if (e.getKey().matches(".*\\.[cm]?[jt]sx?")) {
        List<Token> comments = JsLexer.lex(e.getKey(), e.getKey().matches(".*\\.[cm]?tsx?") ? "ts" : "js", e.getValue()).comments()
          .stream().filter(c -> MARK.matcher(c.text()).find()).toList();
        marks.put(e.getKey(), comments);
        blanked.put(e.getKey(), blank(e.getValue(), comments));
      }
    }
    NextModel model = NextModel.build(TestProjects.of(blanked));
    assertThat(model.skipped()).as("files the model could not read").isEmpty();
    Analyzer analyzer = new Analyzer(model, Map.of(key, rule));
    List<String> problems = new ArrayList<>();
    for (Module m : model.modules()) {
      compare(m.path(), marks.getOrDefault(m.path(), List.of()), analyzer.analyze(m), problems);
    }
    assertThat(problems).as(key).isEmpty();
    return model;
  }

  private static void compare(String path, List<Token> marks, List<Analyzer.Issue> issues, List<String> problems) {
    Map<Integer, Integer> expected = new TreeMap<>();
    Map<Integer, String> messages = new TreeMap<>();
    for (Token c : marks) {
      Matcher m = MARK.matcher(c.text());
      if (m.find()) {
        expected.merge(c.line(), m.group(1) == null ? 1 : Integer.parseInt(m.group(1)), Integer::sum);
        if (m.group(2) != null) {
          messages.put(c.line(), m.group(2));
        }
      }
    }
    Map<Integer, List<String>> actual = new TreeMap<>();
    for (Analyzer.Issue i : issues) {
      actual.computeIfAbsent(i.line(), l -> new ArrayList<>()).add(i.message());
    }
    Set<Integer> lines = new TreeSet<>(expected.keySet());
    lines.addAll(actual.keySet());
    for (int line : lines) {
      int want = expected.getOrDefault(line, 0);
      List<String> got = actual.getOrDefault(line, List.of());
      if (want != got.size()) {
        problems.add(path + ":" + line + " expected " + want + " issue(s), got " + got.size() + " " + got);
      } else if (messages.containsKey(line) && got.stream().noneMatch(g -> g.contains(messages.get(line)))) {
        problems.add(path + ":" + line + " message should contain \"" + messages.get(line) + "\", got " + got);
      }
    }
  }

  /** The source with each comment replaced by spaces, lines and columns kept. */
  private static String blank(String source, List<Token> comments) {
    String[] lines = source.split("\n", -1);
    for (Token c : comments) {
      for (int l = c.line(); l <= c.endLine(); l++) {
        String s = lines[l - 1];
        int from = l == c.line() ? c.column() : 0;
        int to = l == c.endLine() ? Math.min(c.endColumn(), s.length()) : s.length();
        lines[l - 1] = s.substring(0, from) + " ".repeat(Math.max(0, to - from)) + s.substring(to);
      }
    }
    return String.join("\n", lines);
  }

  private static String content(Path p) {
    try {
      return Files.readString(p, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
