package org.eclipse.eripgrep.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

import org.junit.jupiter.api.Test;

class JsonTest {

  @Test
  void parsesValues() {
    assertEquals(12L, Json.parse("12"));
    assertEquals(-1.5, Json.parse("-1.5"));
    assertEquals(1e3, Json.parse("1e3"));
    assertEquals(true, Json.parse(" true "));
    assertEquals(false, Json.parse("false"));
    assertNull(Json.parse("null"));
    assertEquals("a", Json.parse("\"a\""));
    assertEquals(List.of(), Json.parse("[]"));
    assertEquals(Map.of(), Json.parse("{ }"));
  }

  @Test
  void parsesNestedStructures() {
    Object value = Json.parse("{\"a\": [1, {\"b\": null}, \"c\"], \"d\": {\"e\": true}}");
    Map<String, Object> object = Json.object(value);
    assertEquals(List.of("a", "d"), List.copyOf(object.keySet()));
    List<Object> array = Json.array(object.get("a"));
    assertEquals(1L, array.get(0));
    assertTrue(Json.object(array.get(1)).containsKey("b"));
    assertEquals(true, Json.object(object.get("d")).get("e"));
  }

  @Test
  void parsesEscapes() {
    assertEquals("a\"b\\c/d\n\t\r\b\f\u00e9\u20ac", Json.parse("\"a\\\"b\\\\c\\/d\\n\\t\\r\\b\\f\\u00e9\\u20AC\""));
  }

  @Test
  void rejectsInvalidJson() {
    for (String json : List.of("", "{", "[1,", "{\"a\"}", "{a:1}", "\"abc", "\"\\x\"", "\"\\u12\"", "\"\\uzzzz\"", "tru", "1 2", "@", "[1 2]", "\"a\\")) {
      assertThrows(IllegalArgumentException.class, () -> Json.parse(json), json);
    }
  }

  @Test
  void accessorsHaveDefaults() {
    assertEquals(Map.of(), Json.object("a"));
    assertEquals(List.of(), Json.array(null));
    assertEquals("x", Json.string(1L, "x"));
    assertEquals(7, Json.number("a", 7));
    assertEquals(3, Json.number(3.9, 7));
    assertTrue(Json.bool(null, true));
  }

  @Test
  void writesWhatItParses() {
    Map<String, Object> object = new LinkedHashMap<>();
    object.put("text", "a\"b\\c\n\t\r\u0001\u00e9");
    object.put("flag", true);
    object.put("count", 3L);
    object.put("nothing", null);
    object.put("list", List.of("x", 1L));
    String json = Json.write(object);
    assertEquals("{\"text\":\"a\\\"b\\\\c\\n\\t\\r\\u0001\u00e9\",\"flag\":true,\"count\":3,\"nothing\":null,\"list\":[\"x\",1]}", json);
    assertEquals(object, Json.parse(json));
  }
}
