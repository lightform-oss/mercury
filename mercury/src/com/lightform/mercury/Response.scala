package com.lightform.mercury

import cats.MonadError
import com.lightform.mercury.json.{JsonSupport, Reader, Writer}

import scala.collection.immutable.IndexedSeq
import scala.util.{Failure, Success}
import cats.implicits._

sealed trait Response[+ErrorData, +Result] {
  def id: Option[Either[String, Long]]

  def toEither: Either[Error[ErrorData], Result]

  def toEitherF[F[+_]](
      implicit F: MonadError[F, UnexpectedError]
  ): F[Either[ErrorData, Result]] = toEither match {
    case Right(r) => Right(r).pure[F]
    case Left(e)  => e.toF[F].map(Left(_))
  }

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

  def toF[F[+_]](implicit F: MonadError[F, UnexpectedError]): F[ErrorData]
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

  override def toF[F[+_]](implicit F: MonadError[F, UnexpectedError]) =
    F.pure(data)
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
    data: Option[IndexedSeq[Byte]] = None
) extends Exception(s"$code: $message")
    with Error[Nothing] {
  val expectedData = None
  def unexpectedData[Json](implicit jsonSupport: JsonSupport[Json]) =
    new UnexpectedErrorDataExtractor[Json](data)

  override def toF[F[+_]](implicit F: MonadError[F, UnexpectedError]) =
    F.raiseError(this)
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

  implicit def monadErrorFromThrowable[F[_]](
      implicit F: MonadError[F, Throwable]
  ): MonadError[F, UnexpectedError] = new MonadError[F, UnexpectedError] {
    def pure[A](x: A): F[A] = F.pure(x)

    def flatMap[A, B](fa: F[A])(f: A => F[B]): F[B] = F.flatMap(fa)(f)

    def tailRecM[A, B](a: A)(f: A => F[Either[A, B]]): F[B] = F.tailRecM(a)(f)

    def raiseError[A](e: UnexpectedError): F[A] = F.raiseError(e)

    def handleErrorWith[A](fa: F[A])(f: UnexpectedError => F[A]): F[A] =
      F.handleErrorWith(fa) {
        case e: UnexpectedError => f(e)
        case _                  => fa
      }
  }
}

case class ErrorResponse[+ErrorData](
    error: Error[ErrorData],
    id: Option[Either[String, Long]]
) extends Response[ErrorData, Nothing] {
  def toEither = Left(error)
}
