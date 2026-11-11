package io.github.snakeinprod.nextjs.parse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * One parsed file: its tokens (comments left out), its comments, the token tree, and extra structures attached with
 * {@link #put}.
 */
public final class ParsedFile {

  private static final Pattern LINE_BREAK = Pattern.compile("\r\n|\r|\n");

  private final String path;
  private final String language;
  private final String source;
  private List<String> lines;
  private final List<Token> tokens;
  private final List<Token> comments;
  private final Node.Group root;
  private final Map<Class<?>, Object> extras = new HashMap<>();

  public ParsedFile(String path, String language, String source, List<Token> tokens, List<Token> comments) {
    this.path = path;
    this.language = language;
    this.source = source;
    this.tokens = List.copyOf(tokens);
    this.comments = List.copyOf(comments);
    this.root = TreeBuilder.build(tokens);
  }

  /** Path relative to the project base directory, "/" separated. */
  public String path() {
    return path;
  }

  /** Language key: "js" or "ts". */
  public String language() {
    return language;
  }

  public List<String> lines() {
    if (lines == null) {
      lines = List.of(LINE_BREAK.split(source, -1));
    }
    return lines;
  }

  public List<Token> tokens() {
    return tokens;
  }

  public List<Token> comments() {
    return comments;
  }

  public Node.Group root() {
    return root;
  }

  /** As {@code tokens().indexOf(t)}, by binary search on the position: tokens are in source order. */
  public int indexOf(Token t) {
    int lo = 0;
    int hi = tokens.size() - 1;
    while (lo <= hi) {
      int mid = (lo + hi) >>> 1;
      Token m = tokens.get(mid);
      int c = m.line() != t.line() ? Integer.compare(m.line(), t.line()) : Integer.compare(m.column(), t.column());
      if (c == 0) {
        return m.equals(t) ? mid : tokens.indexOf(t);
      }
      if (c < 0) {
        lo = mid + 1;
      } else {
        hi = mid - 1;
      }
    }
    return tokens.indexOf(t);
  }

  public <T> void put(Class<T> type, T value) {
    extras.put(type, value);
  }

  public <T> T get(Class<T> type) {
    return type.cast(extras.get(type));
  }

  /** Every chain of the file, see {@link Chains#in}. */
  public List<Chain> chains() {
    @SuppressWarnings("unchecked")
    List<Chain> cached = (List<Chain>) extras.get(Chain.class);
    if (cached == null) {
      cached = Chains.in(root, Chains.keywords(language));
      extras.put(Chain.class, cached);
    }
    return cached;
  }
}
