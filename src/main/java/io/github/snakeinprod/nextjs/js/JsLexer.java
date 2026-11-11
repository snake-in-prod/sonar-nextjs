package io.github.snakeinprod.nextjs.js;

import io.github.snakeinprod.nextjs.parse.Lexed;
import io.github.snakeinprod.nextjs.parse.Token;
import io.github.snakeinprod.nextjs.parse.TokenKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * JavaScript / TypeScript lexer, JSX and TSX included. Template literals and regex literals are one STRING token each;
 * JSX tags are OP / IDENT / STRING tokens, {@code {...}} inside JSX is lexed as code, JSX text is OTHER. Never throws.
 */
public final class JsLexer {

  private enum Jsx { NONE, HEURISTIC, FULL }

  private static final String[] OPERATORS = {">>>=", "...", "===", "!==", "**=", "<<=", ">>=", ">>>", "&&=", "||=", "??=",
    "=>", "==", "!=", "<=", ">=", "&&", "||", "??", "?.", "**", "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "++", "--",
    "<<", ">>"};
  private static final String PUNCT = "()[]{};,<>+-*/%&|^!~?:=.@#";
  private static final Set<String> REGEX_AFTER_WORD = Set.of("return", "typeof", "case", "do", "else", "in", "of", "new",
    "delete", "void", "throw", "yield", "await", "instanceof");
  private static final Set<String> JSX_AFTER_OP = Set.of("(", ",", "=", ":", "[", "!", "&", "|", "?", "{", "}", ";", "=>",
    "&&", "||", "??");

  private final String src;
  private final int n;
  private final Jsx jsx;
  private final List<Token> tokens = new ArrayList<>();
  private final List<Token> comments = new ArrayList<>();
  private int pos;
  private int line = 1;
  private int col;

  private JsLexer(String src, Jsx jsx) {
    this.src = src;
    this.n = src.length();
    this.jsx = jsx;
  }

  /** {@code language} is "js" or "ts"; JSX is lexed in .jsx / .tsx files and, when it clearly is JSX, in .js files. */
  public static Lexed lex(String path, String language, String source) {
    String p = path.toLowerCase(Locale.ROOT);
    Jsx mode = p.endsWith(".jsx") || p.endsWith(".tsx") ? Jsx.FULL : "ts".equals(language) ? Jsx.NONE : Jsx.HEURISTIC;
    JsLexer l = new JsLexer(source, mode);
    l.code(false);
    return new Lexed(List.copyOf(l.tokens), List.copyOf(l.comments));
  }

  /** Lexes code; when {@code nested} (inside JSX braces) stops after the unmatched "}" and returns false at end of input. */
  private boolean code(boolean nested) {
    int depth = 0;
    while (pos < n) {
      char c = src.charAt(pos);
      char d = peek(1);
      // U+FEFF (a byte order mark) is whitespace in JavaScript
      if (Character.isWhitespace(c) || c == '﻿') {
        adv();
      } else if (c == '/' && (d == '/' || d == '*')) {
        comment();
      } else if (c == '#' && d == '!' && pos == 0) {
        lineComment();
      } else if (c == '\'' || c == '"') {
        int[] s = mark();
        skipString(c);
        emit(TokenKind.STRING, s);
      } else if (c == '`') {
        int[] s = mark();
        skipTemplate();
        emit(TokenKind.STRING, s);
      } else if (c == '/' && regexAllowed() && regex()) {
        // emitted
      } else if (Character.isDigit(c) || c == '.' && Character.isDigit(d)) {
        number();
      } else if (identStart(c) || c == '#' && identStart(d)) {
        int[] s = mark();
        adv();
        while (pos < n && identPart(src.charAt(pos))) {
          adv();
        }
        emit(TokenKind.IDENT, s);
      } else if (c == '<' && jsxAllowed() && tryJsx()) {
        // emitted
      } else if (c == '{') {
        depth++;
        single(TokenKind.OP);
      } else if (c == '}') {
        single(TokenKind.OP);
        if (depth == 0 && nested) {
          return true;
        }
        depth = Math.max(0, depth - 1);
      } else {
        operator();
      }
    }
    return !nested;
  }

  private void operator() {
    for (String op : OPERATORS) {
      if (src.startsWith(op, pos) && !(op.equals("?.") && Character.isDigit(peek(2)))) {
        int[] s = mark();
        for (int i = 0; i < op.length(); i++) {
          adv();
        }
        emit(TokenKind.OP, s);
        return;
      }
    }
    if (PUNCT.indexOf(src.charAt(pos)) >= 0) {
      single(TokenKind.OP);
    } else {
      int[] s = mark();
      int cp = src.codePointAt(pos);
      for (int i = 0; i < Character.charCount(cp); i++) {
        adv();
      }
      emit(TokenKind.OTHER, s);
    }
  }

  private void comment() {
    if (peek(1) == '/') {
      lineComment();
      return;
    }
    int[] s = mark();
    adv();
    adv();
    while (pos < n && !(src.charAt(pos) == '*' && peek(1) == '/')) {
      adv();
    }
    if (pos < n) {
      adv();
      adv();
    }
    comments.add(token(TokenKind.OTHER, s));
  }

  private void lineComment() {
    int[] s = mark();
    while (pos < n && src.charAt(pos) != '\n' && src.charAt(pos) != '\r') {
      adv();
    }
    comments.add(token(TokenKind.OTHER, s));
  }

  /** A '...' or "..." string; an unterminated one ends at the line end. */
  private void skipString(char quote) {
    adv();
    while (pos < n) {
      char c = src.charAt(pos);
      if (c == '\\') {
        adv();
        if (pos < n) {
          adv();
        }
      } else if (c == quote) {
        adv();
        return;
      } else if (c == '\n' || c == '\r') {
        return;
      } else {
        adv();
      }
    }
  }

  private void skipTemplate() {
    adv();
    while (pos < n) {
      char c = src.charAt(pos);
      if (c == '\\') {
        adv();
        if (pos < n) {
          adv();
        }
      } else if (c == '`') {
        adv();
        return;
      } else if (c == '$' && peek(1) == '{') {
        adv();
        adv();
        skipInterpolation();
      } else {
        adv();
      }
    }
  }

  /** The code of a ${...}, up to and including its closing brace. */
  private void skipInterpolation() {
    int depth = 0;
    while (pos < n) {
      char c = src.charAt(pos);
      if (c == '\'' || c == '"') {
        skipString(c);
      } else if (c == '`') {
        skipTemplate();
      } else if (c == '/' && peek(1) == '/') {
        while (pos < n && src.charAt(pos) != '\n') {
          adv();
        }
      } else if (c == '/' && peek(1) == '*') {
        adv();
        adv();
        while (pos < n && !(src.charAt(pos) == '*' && peek(1) == '/')) {
          adv();
        }
        if (pos < n) {
          adv();
          adv();
        }
      } else if (c == '}' && depth == 0) {
        adv();
        return;
      } else {
        if (c == '{') {
          depth++;
        } else if (c == '}') {
          depth--;
        }
        adv();
      }
    }
  }

  private boolean regexAllowed() {
    Token prev = tokens.isEmpty() ? null : tokens.get(tokens.size() - 1);
    if (prev == null) {
      return true;
    }
    return switch (prev.kind()) {
      case OP -> !Set.of(")", "]", "++", "--").contains(prev.text());
      case IDENT -> REGEX_AFTER_WORD.contains(prev.text());
      default -> false;
    };
  }

  /** A regex literal on one line, flags included; false (nothing consumed) when the line has no closing slash. */
  private boolean regex() {
    int[] s = mark();
    adv();
    boolean inClass = false;
    while (pos < n) {
      char c = src.charAt(pos);
      if (c == '\n' || c == '\r') {
        break;
      }
      if (c == '\\') {
        adv();
        if (pos < n && src.charAt(pos) != '\n' && src.charAt(pos) != '\r') {
          adv();
        }
        continue;
      }
      adv();
      if (c == '[') {
        inClass = true;
      } else if (c == ']') {
        inClass = false;
      } else if (c == '/' && !inClass) {
        while (pos < n && identPart(src.charAt(pos))) {
          adv();
        }
        emit(TokenKind.STRING, s);
        return true;
      }
    }
    reset(s);
    return false;
  }

  private void number() {
    int[] s = mark();
    boolean radix = src.charAt(pos) == '0' && "xXoObB".indexOf(peek(1)) >= 0;
    boolean dot = false;
    if (radix) {
      adv();
    } else {
      dot = src.charAt(pos) == '.';
    }
    adv();
    while (pos < n) {
      char c = src.charAt(pos);
      char prev = src.charAt(pos - 1);
      if (Character.isLetterOrDigit(c) || c == '_') {
        adv();
      } else if (c == '.' && !radix && !dot && prev != 'e' && prev != 'E') {
        dot = true;
        adv();
      } else if ((c == '+' || c == '-') && !radix && (prev == 'e' || prev == 'E') && Character.isDigit(peek(1))) {
        adv();
      } else {
        break;
      }
    }
    emit(TokenKind.NUMBER, s);
  }

  // ---------------------------------------------------------------- JSX

  private boolean jsxAllowed() {
    if (jsx == Jsx.NONE) {
      return false;
    }
    Token prev = tokens.isEmpty() ? null : tokens.get(tokens.size() - 1);
    boolean context = prev == null || prev.kind() == TokenKind.OP && JSX_AFTER_OP.contains(prev.text())
      || prev.isIdent("return") || prev.isIdent("default") || prev.isIdent("yield");
    if (!context) {
      return false;
    }
    char next = peek(1);
    if (next == '>') {
      return true;
    }
    if (!identStart(next)) {
      return false;
    }
    int i = pos + 1;
    while (i < n && (identPart(src.charAt(i)) || "-.:".indexOf(src.charAt(i)) >= 0)) {
      i++;
    }
    char after = i < n ? src.charAt(i) : ' ';
    if (jsx == Jsx.HEURISTIC) {
      return Character.isWhitespace(after) || after == '>' || after == '/';
    }
    // TSX generic arrow functions: <T,>(x) => x and <T extends U>(x) => x
    if (after == ',') {
      return false;
    }
    int j = i;
    while (j < n && Character.isWhitespace(src.charAt(j))) {
      j++;
    }
    return !(j > i && src.startsWith("extends", j));
  }

  /** Lexes a JSX element; on anything unexpected restores the state and returns false so "<" becomes an operator. */
  private boolean tryJsx() {
    int[] s = mark();
    int t = tokens.size();
    int c = comments.size();
    if (element()) {
      return true;
    }
    // ponytail: rollback re-lexes the failed span, quadratic only for deeply nested broken JSX
    reset(s);
    tokens.subList(t, tokens.size()).clear();
    comments.subList(c, comments.size()).clear();
    return false;
  }

  private boolean element() {
    single(TokenKind.OP);
    if (pos < n && src.charAt(pos) == '>') {
      single(TokenKind.OP);
      return children(null);
    }
    String name = tagName();
    if (name == null) {
      return false;
    }
    while (true) {
      skipTagSpace();
      if (pos >= n) {
        return false;
      }
      char c = src.charAt(pos);
      if (c == '/' && peek(1) == '>') {
        int[] s = mark();
        adv();
        adv();
        emit(TokenKind.OP, s);
        return true;
      } else if (c == '>') {
        single(TokenKind.OP);
        return children(name);
      } else if (c == '{') {
        single(TokenKind.OP);
        if (!code(true)) {
          return false;
        }
      } else if (identStart(c)) {
        attrName();
        skipTagSpace();
        if (pos < n && src.charAt(pos) == '=') {
          single(TokenKind.OP);
          skipTagSpace();
          if (!attrValue()) {
            return false;
          }
        }
      } else {
        return false;
      }
    }
  }

  private boolean attrValue() {
    if (pos >= n) {
      return false;
    }
    char c = src.charAt(pos);
    if (c == '"' || c == '\'') {
      int[] s = mark();
      adv();
      while (pos < n && src.charAt(pos) != c) {
        adv();
      }
      if (pos >= n) {
        return false;
      }
      adv();
      emit(TokenKind.STRING, s);
      return true;
    }
    if (c == '{') {
      single(TokenKind.OP);
      return code(true);
    }
    return c == '<' && element();
  }

  private boolean children(String name) {
    while (pos < n) {
      char c = src.charAt(pos);
      if (c == '{') {
        single(TokenKind.OP);
        if (!code(true)) {
          return false;
        }
      } else if (c == '<' && peek(1) == '/') {
        int[] s = mark();
        adv();
        adv();
        emit(TokenKind.OP, s);
        skipSpace();
        String closing = pos < n && identStart(src.charAt(pos)) ? tagName() : null;
        skipSpace();
        if (pos >= n || src.charAt(pos) != '>') {
          return false;
        }
        single(TokenKind.OP);
        return Objects.equals(name, closing);
      } else if (c == '<') {
        if (!element()) {
          return false;
        }
      } else {
        text();
      }
    }
    return false;
  }

  /** JSX text up to the next "{" or "<", as one OTHER token without surrounding whitespace. */
  private void text() {
    skipSpace();
    int[] s = mark();
    int[] end = s;
    while (pos < n && src.charAt(pos) != '{' && src.charAt(pos) != '<') {
      boolean ws = Character.isWhitespace(src.charAt(pos));
      adv();
      if (!ws) {
        end = mark();
      }
    }
    if (end != s) {
      tokens.add(new Token(TokenKind.OTHER, src.substring(s[0], end[0]), s[1], s[2], end[1], end[2]));
    }
  }

  private String tagName() {
    if (pos >= n || !identStart(src.charAt(pos))) {
      return null;
    }
    int[] s = mark();
    while (pos < n && (identPart(src.charAt(pos)) || "-.:".indexOf(src.charAt(pos)) >= 0)) {
      adv();
    }
    emit(TokenKind.IDENT, s);
    return src.substring(s[0], pos);
  }

  private void attrName() {
    int[] s = mark();
    while (pos < n && (identPart(src.charAt(pos)) || src.charAt(pos) == '-' || src.charAt(pos) == ':')) {
      adv();
    }
    emit(TokenKind.IDENT, s);
  }

  private void skipTagSpace() {
    while (pos < n) {
      char c = src.charAt(pos);
      if (Character.isWhitespace(c)) {
        adv();
      } else if (c == '/' && (peek(1) == '/' || peek(1) == '*')) {
        comment();
      } else {
        return;
      }
    }
  }

  private void skipSpace() {
    while (pos < n && Character.isWhitespace(src.charAt(pos))) {
      adv();
    }
  }

  // ---------------------------------------------------------------- positions

  private char peek(int ahead) {
    return pos + ahead < n ? src.charAt(pos + ahead) : '\0';
  }

  private void adv() {
    char c = src.charAt(pos++);
    if (c == '\n' || c == '\r' && (pos >= n || src.charAt(pos) != '\n')) {
      line++;
      col = 0;
    } else {
      col++;
    }
  }

  private int[] mark() {
    return new int[] {pos, line, col};
  }

  private void reset(int[] m) {
    pos = m[0];
    line = m[1];
    col = m[2];
  }

  private void single(TokenKind kind) {
    int[] s = mark();
    adv();
    emit(kind, s);
  }

  private void emit(TokenKind kind, int[] s) {
    tokens.add(token(kind, s));
  }

  private Token token(TokenKind kind, int[] s) {
    return new Token(kind, src.substring(s[0], pos), s[1], s[2], line, col);
  }

  private static boolean identStart(char c) {
    return Character.isJavaIdentifierStart(c);
  }

  private static boolean identPart(char c) {
    return Character.isJavaIdentifierPart(c) && !Character.isIdentifierIgnorable(c);
  }
}
