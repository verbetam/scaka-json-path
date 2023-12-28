package com.quincyjo.jsonpath.parser.models

import cats.data.OptionT
import cats.implicits._
import com.quincyjo.jsonpath.parser.models.ParserToken.{SymbolToken, ValueToken}

import scala.annotation.tailrec
import scala.util.chaining.scalaUtilChainingOps

abstract class ParseContext[Token <: ParserToken] {

  def input: String
  def index: Int
  def currentTokenResult: OptionT[ParseResult, Token]

  private var currentValue: Option[ValueAt[_]] = None

  def currentToken: Option[Token] =
    currentTokenResult.value.getOrElse(None)

  def nextToken(): ParseContext[Token]

  def hasNext: Boolean =
    currentTokenResult.value.isSuccess &&
      nextIndex.map(_ < input.length).getOrElse(false)

  def peek: ParseResult[Option[Token]] =
    OptionT
      .whenF(hasNext)(nextIndex)
      .semiflatMap(tokenAt)
      .value

  def value(): ParseResult[ValueAt[Any]]

  protected def tokenAt(i: Int): ParseResult[Token]

  def nextIndex: ParseResult[Int] =
    OptionT
      .fromOption[ParseResult](currentValue)
      .map(v => input.indexWhere(!_.isWhitespace, index + v.raw.length))
      .orElse(
        currentTokenResult
          .semiflatMap {
            case _: ValueToken =>
              value().map { value =>
                input.indexWhere(!_.isWhitespace, index + value.raw.length)
              }
            case token: SymbolToken =>
              Parsed(input.indexWhere(!_.isWhitespace, index + token.length))
          }
      )
      .semiflatMap {
        case n if n == -1 || n >= input.length =>
          ParseError("Unexpected end of input.", index, input)
        case i => Parsed(i)
      }
      .getOrElse(0)

  protected def valueAs[T](
      pf: PartialFunction[Token, ParseResult[ValueAt[T]]]
  ): ParseResult[ValueAt[T]] =
    currentTokenResult
      .semiflatMap { token =>
        pf.lift(token)
          .getOrElse(ParseError(s"Unexpected token $token", index, input))
      }
      .getOrElseF(
        ParseError(
          s"Unexpected end of input.",
          index,
          input
        )
      )
      .tap(_.map(v => currentValue = Some(v)))

  protected def parseQuotedString(index: Int): ParseResult[ValueAt[String]] =
    input
      .lift(index)
      .map {
        case quote @ ('\'' | '"') =>
          @tailrec
          def go(
              i: Int,
              rawBuilder: StringBuilder,
              valueBuilder: StringBuilder
          ): (StringBuilder, StringBuilder) = {
            if (i < input.length) {
              val c = input.charAt(i)
              if (c == quote && !rawBuilder.lastOption.contains('\\'))
                (rawBuilder.addOne(quote), valueBuilder)
              else if (c == quote) {
                valueBuilder.update(valueBuilder.length() - 1, quote)
                go(i + 1, rawBuilder.addOne(quote), valueBuilder)
              } else
                go(i + 1, rawBuilder.addOne(c), valueBuilder.addOne(c))
            } else (rawBuilder, valueBuilder)
          }

          go(
            index + 1,
            new StringBuilder().addOne(quote),
            new StringBuilder()
          ) match {
            case (raw, value) =>
              if (raw.length > 1 && raw.endsWith(quote.toString))
                Parsed(ValueAt(value.result(), index, raw.result()))
              else ParseError("Unclosed quotation.", index, input)
          }

        case nonQuote =>
          ParseError(s"Expected JSON string but was '$nonQuote'", index, input)
      }
      .getOrElse(ParseError("Unexpected end of input.", index, input))
}
