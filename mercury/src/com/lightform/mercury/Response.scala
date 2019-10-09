package com.lightform.mercury

import com.lightform.mercury.json.{JsonSupport, Reader, Writer}

import scala.collection.immutable.IndexedSeq
import scala.util.{Failure, Success}

sealed trait Response[+ErrorData, +Result] {
  def id: Option[Either[String, Long]]

  def toEither: Either[Error[ErrorData], Result]

  final val jsonrpc = "2.0"
}

case class ResultResponse[+Result](
    result: Result,
    id: Option[Either[String, Long]]
) extends Response[Nothing, Result] {
  def toEither = Right(result)
}

sealed trait Error[+ErrorData] { this: Exception =>
  def code: Int
  def message: String
  def expectedData: Option[ErrorData]

  def toException: Exception = this
}

object Error {
  def unapply[E](arg: Error[E]) =
    Some(arg.code, arg.message, arg.expectedData)
}

case class ExpectedError[+ErrorData](
    code: Int,
    message: String,
    data: ErrorData
) extends Exception(s"$code: $message")
    with Error[ErrorData] {
  def expectedData = Some(data)
}

class UnexpectedErrorDataExtractor[Json] private[mercury] (
    val data: Option[IndexedSeq[Byte]]
)(implicit jsonSupport: JsonSupport[Json]) {

  def as[A](implicit reader: Reader[Json, A]) =
    data
      .map(Success(_))
      .getOrElse(
        Failure(
          new NoSuchElementException("Unexpected error does not have any data")
        )
      )
      .flatMap(jsonSupport.parse)
      .flatMap(reader.read)
}

case class UnexpectedError(
    code: Int,
    message: String,
    data: Option[IndexedSeq[Byte]]
) extends Exception(s"$code: $message")
    with Error[Nothing] {
  val expectedData = None
  def unexpectedData[Json](implicit jsonSupport: JsonSupport[Json]) =
    new UnexpectedErrorDataExtractor[Json](data)
}

object UnexpectedError {
  def fromData[Json, A](code: Int, message: String, data: A)(
      implicit writer: Writer[Json, A],
      jsonSupport: JsonSupport[Json]
  ) =
    UnexpectedError(
      code,
      message,
      writer.write(data).map(jsonSupport.stringify)
    )
}

case class ErrorResponse[+ErrorData](
    error: Error[ErrorData],
    id: Option[Either[String, Long]]
) extends Response[ErrorData, Nothing] {
  def toEither = Left(error)
}
