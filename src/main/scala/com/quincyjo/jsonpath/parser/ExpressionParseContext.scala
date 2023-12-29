package com.quincyjo.jsonpath.parser

import cats.data.OptionT
import cats.implicits._
import com.quincyjo.jsonpath.JsonPath
import com.quincyjo.jsonpath.parser.ExpressionParseContext.ExpressionToken
import com.quincyjo.jsonpath.parser.ExpressionParseContext.ExpressionToken._
import com.quincyjo.jsonpath.parser.models.ParserToken._
import com.quincyjo.jsonpath.parser.models._

import scala.util.Try
import scala.util.chaining.scalaUtilChainingOps

final case class ExpressionParseContext private (
    input: String,
    index: Int = 0,
    currentTokenResult: OptionT[ParseResult, ExpressionToken] =
      OptionT.none[ParseResult, ExpressionToken]
) extends ParseContext[ExpressionToken] {

  def nextToken(): ExpressionParseContext = {
    val newIndex = nextIndex
    ExpressionParseContext(
      input,
      newIndex.getOrElse(index),
      OptionT.liftF(newIndex.flatMap(tokenAt))
    )
  }

  def tokenAt(i: Int): ParseResult[ExpressionToken] =
    OptionT
      .fromOption[ParseResult](input.lift(i))
      .orElseF(ParseError("Unexpected end of input.", i, input))
      .collect {
        case '!' =>
          if (input.lift(i + 1).contains('=')) NotEqual
          else Not
        case '<' =>
          if (input.lift(i + 1).contains('=')) LessThanOrEqualTo
          else LessThan
        case '>' =>
          if (input.lift(i + 1).contains('=')) GreaterThanOrEqualTo
          else GreaterThan
        case '=' if input.lift(i + 1).contains('=') =>
          Equal
        case '&' if input.lift(i + 1).contains('&') =>
          And
        case '|' if input.lift(i + 1).contains('|') =>
          Or
        case '$'            => Root
        case '@'            => Current
        case '('            => OpenParenthesis
        case ')'            => CloseParenthesis
        case '+'            => Plus
        case '-'            => Minus
        case '*'            => Multiply
        case '/'            => Divide
        case '\'' | '"'     => ValueString
        case c if c.isDigit => ValueNumber
        case 't' | 'f'      => ValueBoolean
      }
      .getOrElseF(
        ParseError(s"Unexpected character '${input(i)}'", i, input)
      )

  override def value(): ParseResult[ValueAt[Any]] =
    valueAs[Any] {
      case ValueBoolean   => valueAsBoolean
      case ValueString    => valueAsString
      case ValueNumber    => valueAsNumber
      case Root | Current => valueAsJsonPath
    }

  def valueAsBoolean: ParseResult[ValueAt[Boolean]] =
    valueAs[Boolean] { case ValueBoolean =>
      if (input.startsWith("true")) Parsed(ValueAt(true, 0, "true"))
      else if (input.startsWith("false")) Parsed(ValueAt(false, 0, "false"))
      else
        ParseError(
          s"Expected boolean value but was '${input.substring(index).takeWhile(_.isLetter)}'",
          index,
          input
        )
    }

  def valueAsString: ParseResult[ValueAt[String]] =
    valueAs { case ValueString =>
      parseQuotedString(index)
    }

  def valueAsNumber: ParseResult[ValueAt[BigDecimal]] =
    valueAs[BigDecimal] { case ValueNumber =>
      var hasReadDecimal = false
      val end = Option(
        input.indexWhere(
          c =>
            !c.isDigit || c == '.' && {
              (hasReadDecimal).tap(_ => hasReadDecimal = true)
            },
          index
        )
      ).filter(_ > index)
      val raw =
        end.fold(input.substring(index))(input.substring(index, _))
      Try(BigDecimal(raw)).fold(
        throwable => ParseError(throwable.getMessage, index, input),
        number => Parsed(ValueAt(number, index, raw))
      )
    }

  def valueAsJsonPath: ParseResult[ValueAt[JsonPath]] =
    valueAs[JsonPath] { case ExpressionToken.Root | ExpressionToken.Current =>
      JsonPathParser.take(input.substring(index))
        .map {
          _.copy(
            index = index
          )
        }
    }
}

object ExpressionParseContext {

  def apply(string: String): ExpressionParseContext =
    new ExpressionParseContext(string)

  sealed trait ExpressionToken extends ParserToken

  object ExpressionToken {

    sealed trait OperatorToken extends ExpressionToken

    sealed trait BinaryToken extends OperatorToken

    object Not extends ExpressionToken with SymbolToken with OperatorToken {
      override def symbol: String = "!"
    }

    case object And extends ExpressionToken with SymbolToken with BinaryToken {
      override def symbol: String = "&&"
    }

    case object Or extends ExpressionToken with SymbolToken with BinaryToken {
      override def symbol: String = "||"
    }

    case object Equal
        extends ExpressionToken
        with SymbolToken
        with BinaryToken {
      override def symbol: String = "=="
    }

    case object NotEqual
        extends ExpressionToken
        with SymbolToken
        with BinaryToken {
      override def symbol: String = "!="
    }

    case object LessThan
        extends ExpressionToken
        with SymbolToken
        with BinaryToken {
      override def symbol: String = "<"
    }

    case object LessThanOrEqualTo
        extends ExpressionToken
        with SymbolToken
        with BinaryToken {
      override def symbol: String = "<="
    }

    case object GreaterThan
        extends ExpressionToken
        with SymbolToken
        with BinaryToken {
      override def symbol: String = ">"
    }

    case object GreaterThanOrEqualTo
        extends ExpressionToken
        with SymbolToken
        with BinaryToken {
      override def symbol: String = ">="
    }

    case object Plus extends ExpressionToken with SymbolToken with BinaryToken {
      override def symbol: String = "+"
    }

    case object Minus
        extends ExpressionToken
        with SymbolToken
        with BinaryToken {
      override def symbol: String = "-"
    }

    case object Multiply
        extends ExpressionToken
        with SymbolToken
        with BinaryToken {
      override def symbol: String = "*"
    }

    case object Divide
        extends ExpressionToken
        with SymbolToken
        with BinaryToken {
      override def symbol: String = "/"
    }

    case object Root extends ExpressionToken with SymbolToken with ValueToken {
      override def symbol: String = "$"
    }

    case object Current
        extends ExpressionToken
        with SymbolToken
        with ValueToken {
      override def symbol: String = "@"
    }

    case object OpenParenthesis
        extends ExpressionToken
        with SymbolToken
        with OperatorToken {
      override def symbol: String = "("
    }

    case object CloseParenthesis extends ExpressionToken with SymbolToken {
      override def symbol: String = ")"
    }

    case object ValueString extends ExpressionToken with ValueToken

    case object ValueBoolean extends ExpressionToken with ValueToken

    case object ValueNumber extends ExpressionToken with ValueToken
  }
}
