package io.github.snakeinprod.nextjs.js;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.github.snakeinprod.nextjs.model.NextModel;
import io.github.snakeinprod.nextjs.parse.Chains;
import io.github.snakeinprod.nextjs.parse.Lexed;
import io.github.snakeinprod.nextjs.parse.ParsedFile;
import io.github.snakeinprod.nextjs.parse.Token;
import io.github.snakeinprod.nextjs.parse.TokenKind;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class JsLexerTest {

  private static List<Token> tokens(String path, String src) {
    return JsLexer.lex(path, path.matches(".*\\.tsx?$") ? "ts" : "js", src).tokens();
  }

  private static List<String> texts(String path, String src) {
    return tokens(path, src).stream().map(Token::text).toList();
  }

  private static List<String> ofKind(String path, String src, TokenKind kind) {
    return tokens(path, src).stream().filter(t -> t.kind() == kind).map(Token::text).toList();
  }

  @Test
  void template_literal_with_nested_templates_is_one_string() {
    String tpl = "`a ${b ? `x${\"}\"}` : '{'} c ${ {k: `}`}.k }`";
    assertThat(texts("a.js", "const s = " + tpl + "; f(s);"))
      .containsExactly("const", "s", "=", tpl, ";", "f", "(", "s", ")", ";");
  }

  @Test
  void multi_line_template_positions() {
    Token t = tokens("a.js", "x = `one\ntwo ${y}\nthree`;").get(2);
    assertThat(t.kind()).isEqualTo(TokenKind.STRING);
    assertThat(List.of(t.line(), t.column(), t.endLine(), t.endColumn())).containsExactly(1, 4, 3, 6);
  }

  @Test
  void regex_versus_division() {
    String src = "a = b / c / d;\nr = /ab+c\\/[/]/gi.test(x);\nx = (y) / 2 / z;\nif (q) {} /foo/.test(y);\n"
      + "return typeof /x/;\nn = arr[0] / 2;\nm = i++ / 2;";
    assertThat(ofKind("a.js", src, TokenKind.STRING)).containsExactly("/ab+c\\/[/]/gi", "/foo/", "/x/");
    assertThat(ofKind("a.js", src, TokenKind.OP)).filteredOn("/"::equals).hasSize(6);
  }

  @Test
  void slash_without_closing_on_the_line_is_an_operator() {
    assertThat(texts("a.js", "x = (/ 2\n);")).containsExactly("x", "=", "(", "/", "2", ")", ";");
  }

  @Test
  void numbers_identifiers_and_operators() {
    String src = "1_000 0xFF 1n .5 1e3 1e-3 2.5e+10 0b1010n";
    assertThat(ofKind("a.js", src, TokenKind.NUMBER)).containsExactly("1_000", "0xFF", "1n", ".5", "1e3", "1e-3", "2.5e+10", "0b1010n");
    assertThat(texts("a.ts", "a?.b?.(x)?.[0] ?? c ... === !== => ** >>>= x ? .5 : 1"))
      .containsExactly("a", "?.", "b", "?.", "(", "x", ")", "?.", "[", "0", "]", "??", "c", "...", "===", "!==", "=>", "**", ">>>=",
        "x", "?", ".5", ":", "1");
    assertThat(ofKind("a.ts", "@Component class A { #count = 1; $el; _x; ñame; }", TokenKind.IDENT))
      .containsExactly("Component", "class", "A", "#count", "$el", "_x", "ñame");
    assertThat(texts("a.ts", "@Component")).containsExactly("@", "Component");
  }

  @Test
  void comments_in_odd_places() {
    String src = "#!/usr/bin/env node\na /* c1 */ . /* c2 */ b // c3\ns = 'http://x' + \"/* no */\" + /\\/\\//;\n"
      + "t = `${/* inside template, not a comment token */ x}`;\n/** doc */";
    Lexed l = JsLexer.lex("a.js", "js", src);
    assertThat(l.comments()).extracting(Token::text).containsExactly("#!/usr/bin/env node", "/* c1 */", "/* c2 */", "// c3", "/** doc */");
    assertThat(l.tokens()).extracting(Token::text).contains("'http://x'", "\"/* no */\"", "/\\/\\//");
  }

  @Test
  void jsx_text_with_apostrophes_and_nested_braces() {
    String src = "const el = <p className=\"a\" {...rest}>Don't {cond ? <b>it's</b> : {a: 1}.a} ok</p>;\nconst z = 'q';";
    assertThat(ofKind("a.jsx", src, TokenKind.STRING)).containsExactly("\"a\"", "'q'");
    assertThat(ofKind("a.jsx", src, TokenKind.OTHER)).containsExactly("Don't", "it's", "ok");
    assertThat(texts("a.jsx", src)).endsWith("</", "p", ">", ";", "const", "z", "=", "'q'", ";");
  }

  @Test
  void jsx_fragments_comments_in_tags_and_self_closing() {
    String src = "return (<>\n<div // c4\n id='x' /* c5 */ />{/* c6 */}\n<A.B data-x={1}/></>);";
    Lexed l = JsLexer.lex("a.tsx", "ts", src);
    assertThat(l.comments()).extracting(Token::text).containsExactly("// c4", "/* c5 */", "/* c6 */");
    assertThat(l.tokens()).extracting(Token::text).containsExactly("return", "(", "<", ">", "<", "div", "id", "=", "'x'", "/>", "{", "}",
      "<", "A.B", "data-x", "=", "{", "1", "}", "/>", "</", ">", ")", ";");
  }

  @Test
  void tsx_generics_are_not_jsx() {
    String src = "const f = <T,>(x: T) => x;\nconst g = <T extends object>(x: T) => x;\nconst el = <div/>;";
    List<String> t = texts("a.tsx", src);
    assertThat(t).filteredOn("/>"::equals).hasSize(1);
    assertThat(t).startsWith("const", "f", "=", "<", "T", ",", ">", "(", "x", ":", "T", ")", "=>", "x", ";");
    assertThat(ofKind("a.tsx", src, TokenKind.OTHER)).isEmpty();
  }

  @Test
  void ts_never_lexes_jsx() {
    String src = "const h = <T>(x: T) => x;\nif (a < b && c > d) { y = <Foo>bar; }\nconst s = 'it''s';";
    List<String> t = texts("a.ts", src);
    assertThat(t).startsWith("const", "h", "=", "<", "T", ">", "(", "x", ":", "T", ")", "=>", "x", ";");
    assertThat(t).contains("<", "Foo", ">", "bar");
    assertThat(ofKind("a.ts", src, TokenKind.OTHER)).isEmpty();
  }

  @Test
  void js_lexes_jsx_only_when_it_clearly_is_jsx() {
    assertThat(ofKind("a.js", "if (a <b) { x = 1 } const el = <div>hi there</div>;", TokenKind.OTHER)).containsExactly("hi there");
    assertThat(texts("a.js", "for (let i = 0; i <n; i++) {}")).contains("<", "n");
    assertThat(ofKind("a.js", "x = <div className='a'>it's</div>", TokenKind.OTHER)).containsExactly("it's");
  }

  @Test
  void broken_jsx_falls_back_to_operators() {
    List<String> t = texts("a.jsx", "x = <div>never closed {1}\nconst y = 'a';");
    assertThat(t).startsWith("x", "=", "<", "div", ">", "never", "closed");
    assertThat(t).contains("'a'");
  }

  @Test
  void never_throws() {
    for (String src : List.of("`abc ${", "'unterminated\nx", "/* open", "<div attr=", "<", "x = /[/", "§ \u0000 \\ 😀",
      "${}}}", "<a>{</a>", "0x", ".")) {
      assertThatCode(() -> JsLexer.lex("a.tsx", "ts", src)).doesNotThrowAnyException();
      assertThatCode(() -> JsLexer.lex("a.js", "js", src)).doesNotThrowAnyException();
    }
    assertThat(ofKind("a.js", "a § b", TokenKind.OTHER)).containsExactly("§");
  }

  @Test
  void shortcuts_give_what_the_full_scans_give() {
    String src = "import a from 'a';\nconst x = process.env.A ?? process?.env['B'];\nfoo.process.env.C; process!.env.D;"
      + "\nexport default () => <div className=\"c\">{process.env.E}\t text </div>;";
    ParsedFile f = NextModel.parse("a.tsx", src);
    Set<String> keywords = Chains.keywords("ts");
    assertThat(Chains.in(f.root(), keywords, "process"))
      .isEqualTo(Chains.in(f.root(), keywords).stream().filter(c -> c.firstName().equals("process")).toList())
      .hasSize(4);
    for (int i = 0; i < f.tokens().size(); i++) {
      assertThat(f.indexOf(f.tokens().get(i))).isEqualTo(i);
    }
    assertThat(f.indexOf(new Token(TokenKind.IDENT, "nope", 1, 0, 1, 4))).isEqualTo(-1);
  }
}
