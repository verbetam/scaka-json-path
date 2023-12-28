package com.quincyjo.jsonpath.parser

import com.quincyjo.jsonpath.JsonPath
import com.quincyjo.jsonpath.JsonPath._
import com.quincyjo.jsonpath.parser.ExpressionParser.BalancedExpressionParser
import com.quincyjo.jsonpath.parser.models._
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks

class JsonPathReaderSpec
    extends AnyFlatSpecLike
    with Matchers
    with TableDrivenPropertyChecks
    with ParseResultValues {

  "it" should "parse all basic path nodes" in {
    val cases = Table(
      "input" -> "expected",
      "$" -> $,
      "$.foo" -> $ / "foo",
      "$[0]" -> $ / 0,
      "$[0].foo" -> $ / 0 / "foo",
      "$[foo,bar]" -> $ / Union("foo", "bar"),
      "$[1,2]" -> $ / Union(1, 2),
      "$[foo,2]" -> $ / Union("foo", 2),
      "$[1,bar]" -> $ / Union(1, "bar"),
      "$[:1]" -> $ / Slice.take(1),
      "$[1:]" -> $ / Slice.drop(1),
      "$[::3]" -> $ / Slice.everyN(3),
      "$[1:2]" -> $ / Slice(1, 2),
      "$[1:2:3]" -> $ / Slice(1, 2, 3),
      "$.*.deadbeef" -> $ / Wildcard / "deadbeef",
      "$.store.book[*].author" -> $ / "store" / "book" / Wildcard / "author",
      "$..author" -> $ / RecursiveDescent(Attribute("author")),
      "$.store.*" -> $ / "store" / Wildcard,
      "$.store..price" -> $ / "store" / RecursiveDescent(Attribute("price")),
      "$..book[2]" -> $ / RecursiveDescent(Attribute("book")) / 2,
      "$..book[-1:]" -> $ / RecursiveDescent(Attribute("book")) / Slice
        .takeRight(1),
      "$..book[0,1]" -> $ / RecursiveDescent(Attribute("book")) / Union(0, 1),
      "$..book[:2]" -> $ / RecursiveDescent(Attribute("book")) / Slice.take(2),
      "$..*" -> $ / RecursiveDescent(Wildcard)
    )

    forAll(cases) { (input, expected) =>
      JsonPathReader(input).parseInput().value should be(expected)
    }
  }

  it should "handle quoted strings" in {
    val cases = Table(
      "input" -> "expected",
      "$['foo']" -> $ / "foo",
      "$[\"foo\"]" -> $ / "foo",
      "$[\"foo\",\"bar\",deadbeef]" -> $ / Union("foo", "bar", "deadbeef"),
      "$['foo','bar',deadbeef]" -> $ / Union("foo", "bar", "deadbeef"),
      "$['foo',\"bar\",deadbeef]" -> $ / Union("foo", "bar", "deadbeef")
    )

    forAll(cases) { (input, expected) =>
      JsonPathReader(input).parseInput().value should be(expected)
    }
  }

  it should "handle quoted strings with escaped nested quotes" in {
    val cases = Table(
      "input" -> "expected",
      "$['ain\\'t that neat']" -> $ / "ain't that neat",
      "$[\"\\\"Proper Noun\\\"\"]" -> $ / "\"Proper Noun\""
    )

    forAll(cases) { (input, expected) =>
      JsonPathReader(input).parseInput().value should be(expected)
    }
  }

  it should "parse expressions according to the configured expression parser" in {
    val cases = Table(
      "input" -> "expected",
      "$[(@.foobar>3)]" -> $ / ScriptExpression(
        LiteralExpression("@.foobar>3")
      ),
      "$[?(!!@.length >= 5 && @[5].isValid)]" -> $ / FilterExpression(
        LiteralExpression("!!@.length >= 5 && @[5].isValid")
      )
    )

    forAll(cases) { (input, expected) =>
      JsonPathReader(input).parseInput().value should be(expected)
    }
  }

  "take" should "read up to the first parse error" in {
    val cases = Table(
      "input" -> "expected",
      "$.foobar" -> $ / "foobar",
      "$.foobar > 5" -> $ / "foobar",
      "$ > 5" -> $,
      "@[:-1]" -> `@` / Slice.dropRight(1),
      "['foobar']" -> JsonPath.empty / "foobar"
    )

    forAll(cases) { (input, expected) =>
      val result = JsonPathReader(input).take().value
      result.value should be(expected)
    }
  }

  "BalancedExpressionParser" should "parse basic literal expressions" in {
    val cases = Table(
      "input" -> "expected",
      "(@.foo.bar[0])" -> LiteralExpression("@.foo.bar[0]"),
      "(@.predicate>3 && (@.right < 5 || @.right > 10))" -> LiteralExpression(
        "@.predicate>3 && (@.right < 5 || @.right > 10)"
      )
    )

    forAll(cases) { (input, expected) =>
      BalancedExpressionParser.getValueAsExpression(input, 0).value match {
        case ValueAt(expression, index, raw) =>
          expression should be(expected)
          index should be(0)
          raw should be(input)
      }
    }
  }

  it should "parse within bounds of the script expression" in {
    val cases = Table(
      "input" -> "expected",
      "(@.foo.bar[0])].foobar" -> LiteralExpression("@.foo.bar[0]"),
      "(@.foo.bar[0]),0]" -> LiteralExpression("@.foo.bar[0]")
    )

    forAll(cases) { (input, expected) =>
      BalancedExpressionParser.getValueAsExpression(input, 0).value match {
        case ValueAt(expression, index, raw) =>
          expression should be(expected)
          index should be(0)
          raw should be(s"(${expected.value})")
      }
    }
  }

  it should "parse according to the provided right" in {
    val cases = Table(
      ("input", "right", "expected"),
      ("foobar[(@.foo.bar[0])]", 7, LiteralExpression("@.foo.bar[0]")),
      ("deadbeef[?(@.foo.bar[0]),0]", 10, LiteralExpression("@.foo.bar[0]"))
    )

    forAll(cases) { (input, givenIndex, expected) =>
      BalancedExpressionParser
        .getValueAsExpression(input, givenIndex)
        .value match {
        case ValueAt(expression, index, raw) =>
          expression should be(expected)
          index should be(givenIndex)
          raw should be(s"(${expected.value})")
      }
    }
  }
}
