# scala-json-path

`scala-json-path` is a Scala library for usage of [JSONPath](https://goessner.net/articles/JsonPath/). This library
provides a direct ADT for modeling JSONPaths with support for serialization and parsing. In addition, evaluation of
JSONPaths may be done on any type of `Json` which provides support via `JsonSupport` API.

## Usage

### Direct Modeling

JSONPaths may be defined using the ADT API directly, or via a simple DSL.

```scala
scala> import com.quincyjo.jsonpath.JsonPath

scala> JsonPath(JsonPath.Root, JsonPath.Property("foobar"), JsonPath.Property(5), JsonPath.Property(JsonPath.Slice.takeRight(3)), JsonPath.Union("1", 1))
val res0: com.quincyjo.jsonpath.JsonPath = $.foobar[5][-3:]["1",1]

scala> JsonPath.$ / "foobar" / 5 / JsonPath.Slice.takeRight(3) / JsonPath.Union("1", 1)
val res1: com.quincyjo.jsonpath.JsonPath = $.foobar[5][-3:]["1",1]
```

### Parsing

Parsing is provided via `JsonPathReader`, which reads `JsonPath`s from strings. A direct API is also provided via
the `parser` package object. Parse results are exposed via the sum of `Parsed[T]` and `ParseError`.

```scala
scala> import com.quincyjo.jsonpath

scala> jsonpath.parser.parse("$.foobar[5][-3:][\"1\",1]")
val res0:
  com.quincyjo.jsonpath.parser.ParseResult[com.quincyjo.jsonpath.JsonPath] = Parsed($.foobar[5][-3:]["1",1])
```

### Literals

Literal strings are provided via `jsonpath.literal` package.

```scala
scala> import com.quincyjo.jsonpath.literal.JsonPathStringContext

scala> jsonPath"""@[1:2:3]["foobar"]"""
val res0: com.quincyjo.jsonpath.JsonPath = @[1:2:3].foobar
```

### Evaluation

Evaluation of a `JsonPath` is performed by a `JsonPathEvaluator`, which is implemented generically via the `JsonSupport`
API. Once a `JsonSupport` has been defined for your JSON library of choice, an evaluator may be defined simply as so:

```scala
import JsonBean.JsonBeanSupport // Implicit instance of JsonSupport[JsonBean]

final case object JsonBeanEvaluator extends JsonPathEvaluator[JsonBean]
```

Evaluation returns a `List` of the matching attributes to the path in the given JSON.

```scala
scala> val json = JsonBean.obj("foobar" -> JsonBean.arr(JsonBean.string("deadbeef"), JsonBean.True, JsonBean.number(42)))
val json: JsonBean = { "foobar": ["deadbeef" , true , 42 ] }

scala> JsonBeanEvaluator.evaluate(jsonPath"""$$.foobar""", json)
val res0: List[JsonBean] = List(["deadbeef", true, 42])

scala> JsonBeanEvaluator.evaluate(jsonPath"""$$["foobar"].*""", json)
val res1: List[JsonBean] = List("deadbeef", true, 42)

scala> JsonBeanEvaluator.evaluate(jsonPath"""$$.foobar[-1:]""", json)
val res2: List[JsonBean] = List(42)

scala> JsonBeanEvaluator.evaluate(jsonPath"""$$..*""", json)
val res3: List[JsonBean] = List(["deadbeef", true, 42] , "deadbeef" , true , 42)
```

## Expressions

Expressions have their own AST which can be used to describe expressions in either JSONPath scripts or filter.
Expressions are evaluated against a JsonPath context and return a result in the same JSON type as the path is run
against.

### Values

- **JsonString**: `JsonString("foobar")`
- **JsonBoolean**: `JsonBoolean(false)`
- **JsonNumber**: `JsonNumber(42)`
- **JsonPathValue**: `JsonPathValue(JsonPath.$ / "foobar")`
- **JsonNull**: `JsonNull`

### Operators

The following operators are supported. These operators are implemented according to Javascript evaluation rules.

- Not
- Equal
- NotEqual
- GreaterThan
- GreaterThanOrEqualTo
- LessThan
- LessThanOrEqualTo
- Plus
- Minus
- Multiply
- Divide
- Or
- And

```scala
scala> JsonPath.$ / Filter(LessThan(JsonPathValue(JsonPath.`@` / "price"), JsonNumber(10)))
val res0: com.quincyjo.jsonpath.JsonPath = $[?(@.price < 10)]

scala> val json = JsonBean.arr(Seq.tabulate(6) { n =>
     |   JsonBean.obj("keep" -> (if (n % 2 == 0) JsonBean.True else JsonBean.False))
     | }: _*)
val json: JsonBean = [ { "keep": true }, { "keep": false }, { "keep": true }, { "keep": false }, { "keep": true }, { "keep": false } ]

scala> val jsonPath = JsonPathParser.parse("$[?(@.keep)]").get
val jsonPath: com.quincyjo.jsonpath.JsonPath = $[?(@.keep)]

scala> JsonBeanEvaluator.evaluate(jsonPath, json)
val res1: List[JsonBean] = List({ "keep": true }, { "keep": true }, { "keep": true })
```

## Variance from Core Library

There are a few small variances from the behavior of the core Javascript library in some edge cases.
These variances in behavior are intentional, as they remove ambiguity and are more precise.

### Quoted Strings

In the core library, quotes around strings, namely in attribute selection, are only respected at either end of a bracket
selector (`[<selector>]`). Furthermore, these quotations do not have to be closed or even match. If the opening
branch (`[`) of a selector is followed immediately by either a single quote (`'`) or double quote (`"`), the quote is
dropped. And similarly for a quote immediately preceding a closing bracket (`]`) of a selector.

For example:

```
Core Library:
["foobar"] -> .foobar
[foobar"] -> .foobar
["foobar] -> .foobar
["foobar'] -> .foobar

[""foobar"'] -> ["foobar"]

This Library:
["foobar"] -> .foobar
['foobar'] -> .foobar
[foobar"] -> invalid, parse fails
["foobar] -> invalid, parse fails
["foobar'] -> invalid, parse fails

["\"foobar\""] or ['"foobar"'] -> ["\"foobar\""] as in an object with an attribute '"foobar"' 
```

Serialization also respects quotes, and will escape quotes that are nested in the string. As the library always
serializaed strings in double quotes, only double quotes within a selector will be escaped.

```scala
scala > JsonPath.Property("\"Proper Noun\"")
val res0: com.quincyjo.jsonpath.JsonPath.Property = ["\"Proper Noun\""]
```

### Union String Quotes

As quotes are only respected (or rather ignored) if immediately following a selector opening bracket (`[`]) or a
selector closing bracket (`]`), individual attributes of a union selector or not quoted. In this library, each
individual
selector within the union has its own quotations and escaps handled individually.