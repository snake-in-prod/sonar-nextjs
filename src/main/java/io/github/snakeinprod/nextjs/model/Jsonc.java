package io.github.snakeinprod.nextjs.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** JSON with comments and trailing commas, as tsconfig.json and jsconfig.json allow. */
final class Jsonc {

  private Jsonc() {
  }

  /** The object, or null when the text is null, not JSON or not an object. */
  static JsonObject parse(String text) {
    if (text == null) {
      return null;
    }
    StringBuilder sb = new StringBuilder(text.length());
    int n = text.length();
    boolean inString = false;
    for (int i = 0; i < n; i++) {
      char c = text.charAt(i);
      char d = i + 1 < n ? text.charAt(i + 1) : '\0';
      if (inString) {
        sb.append(c);
        if (c == '\\' && i + 1 < n) {
          sb.append(d);
          i++;
        } else if (c == '"') {
          inString = false;
        }
      } else if (c == '"') {
        inString = true;
        sb.append(c);
      } else if (c == '/' && d == '/') {
        while (i < n && text.charAt(i) != '\n') {
          i++;
        }
        sb.append('\n');
      } else if (c == '/' && d == '*') {
        int end = text.indexOf("*/", i + 2);
        i = end < 0 ? n : end + 1;
        sb.append(' ');
      } else if (c == ',' && closesNext(text, i + 1)) {
        sb.append(' ');
      } else {
        sb.append(c);
      }
    }
    try {
      JsonElement e = JsonParser.parseString(sb.toString());
      return e.isJsonObject() ? e.getAsJsonObject() : null;
    } catch (RuntimeException e) {
      return null;
    }
  }

  /** The next character after whitespace and comments closes an object or array: the comma before it is trailing. */
  private static boolean closesNext(String text, int i) {
    int n = text.length();
    while (i < n) {
      char c = text.charAt(i);
      if (Character.isWhitespace(c)) {
        i++;
      } else if (c == '/' && i + 1 < n && text.charAt(i + 1) == '/') {
        int end = text.indexOf('\n', i);
        i = end < 0 ? n : end;
      } else if (c == '/' && i + 1 < n && text.charAt(i + 1) == '*') {
        int end = text.indexOf("*/", i + 2);
        i = end < 0 ? n : end + 2;
      } else {
        return c == '}' || c == ']';
      }
    }
    return false;
  }

  /** A string member of an object, or null. */
  static String string(JsonObject o, String key) {
    JsonElement e = o == null ? null : o.get(key);
    return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
  }

  /** An object member of an object, or null. */
  static JsonObject object(JsonObject o, String key) {
    JsonElement e = o == null ? null : o.get(key);
    return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
  }
}
