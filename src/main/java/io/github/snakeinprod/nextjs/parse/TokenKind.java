package io.github.snakeinprod.nextjs.parse;

/**
 * Token kinds shared by all languages. Keywords are {@link #IDENT}: rules compare the text. Brackets and every other
 * punctuation or operator ({@code .}, {@code ?.}, {@code =>}, {@code ==}, {@code @}) are {@link #OP}. JSX text and
 * other text a rule never reads is {@link #OTHER}.
 */
public enum TokenKind {
  IDENT, NUMBER, STRING, OP, OTHER
}
