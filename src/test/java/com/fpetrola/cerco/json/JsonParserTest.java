package com.fpetrola.cerco.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class JsonParserTest {

  @Test
  void readsNestedMembersAndEscapes() {
    JsonValue value = JsonParser.parse(
        "{\"tool_name\":\"Bash\",\"tool_input\":{\"command\":\"echo \\\"hi\\\" > a.txt\\n\",\"n\":3,\"ok\":true,"
            + "\"none\":null,\"list\":[1,\"two\",{\"u\":\"\\u0041\"}]}}");
    assertEquals(Optional.of("Bash"), value.string("tool_name"));
    assertEquals(Optional.of("echo \"hi\" > a.txt\n"), value.string("tool_input", "command"));
    assertEquals(Optional.of(3.0), value.member("tool_input").flatMap(v -> v.member("n")).flatMap(JsonValue::asNumber));
    assertTrue(value.flag("tool_input", "ok"));
    assertTrue(value.member("tool_input").flatMap(v -> v.member("none")).orElseThrow().isNull());
    assertEquals(3, value.member("tool_input").flatMap(v -> v.member("list")).orElseThrow().elements().size());
    assertEquals(Optional.of("A"), value.member("tool_input").flatMap(v -> v.member("list")).orElseThrow()
        .elements().get(2).string("u"));
  }

  @Test
  void absentPathsAnswerEmptyInsteadOfFailing() {
    JsonValue value = JsonParser.parse("{\"a\":{\"b\":1}}");
    assertEquals(Optional.empty(), value.string("a", "b"));
    assertEquals(Optional.empty(), value.string("x", "y"));
    assertEquals(false, value.flag("a", "missing"));
  }

  @Test
  void refusesMalformedInput() {
    assertThrows(JsonException.class, () -> JsonParser.parse("{\"a\":}"));
    assertThrows(JsonException.class, () -> JsonParser.parse("{\"a\":1} trailing"));
    assertThrows(JsonException.class, () -> JsonParser.parse("[1,2"));
    assertThrows(JsonException.class, () -> JsonParser.parse("\"unterminated"));
  }

  @Test
  void quotesWhatJsonCannotHoldRaw() {
    assertEquals("\"a \\\"b\\\" \\\\ \\n\\t\\u0001\"", new JsonQuote("a \"b\" \\ \n\t\u0001").quoted());
  }
}
