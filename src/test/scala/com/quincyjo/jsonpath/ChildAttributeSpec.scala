package com.quincyjo.jsonpath

import com.quincyjo.jsonpath.JsonPath.Attribute
import org.scalatest.LoneElement
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks

class ChildAttributeSpec
    extends AnyFlatSpecLike
    with Matchers
    with LoneElement
    with TableDrivenPropertyChecks {

  "isSimple" should "f determine dot-chainable" in {
    val cases = Table(
      "name" -> "expected",
      "foobar" -> true,
      "abc123" -> true,
      "1" -> false,
      "123abc" -> false
    )

    forAll(cases) { case (name, expected) =>
      Attribute(name).isSimple should be(expected)
    }
  }

  "quotedName" should "wrap simple names in double quotes" in {
    Attribute("foobar").quotedName should be("\"foobar\"")
  }

  it should "escape double quotes in names" in {
    println(Attribute("\"foobar\"").quotedName)
    Attribute("\"foobar\"").quotedName should be("\"\\\"foobar\\\"\"")
  }

  "toString" should "be just the name for simple names" in {
    Attribute("foobar").toString should be("foobar")
  }

  it should "be quoted for complex names" in {
    Attribute("\"foobar\"").toString should be("\"\\\"foobar\\\"\"")
  }

  "apply" should "select an object's attribute by name" in {
    val givenAttributeName = "testAttributeName"
    val givenAttributeValue = JsonBean.string("Test attribute value!")
    val givenJson = JsonBean.obj(
      givenAttributeName -> givenAttributeValue
    )
    Attribute(givenAttributeName)(givenJson).loneElement should be(
      givenAttributeValue
    )
  }

  it should "return none if the given attribute does not exist" in {
    val givenAttributeName = "testAttributeName"
    val givenJson = JsonBean.obj()

    Attribute(givenAttributeName)(givenJson) should be(empty)
  }

  it should "return none if the given json is not an object" in {
    val givenAttributeName = "testAttributeName"
    val cases = Table(
      "json",
      JsonBean.number(1),
      JsonBean.True,
      JsonBean.string("foobar"),
      JsonBean.arr()
    )

    forAll(cases) { givenJson =>
      Attribute(givenAttributeName)(givenJson) should be(empty)
    }
  }
}
