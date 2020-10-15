package com.lightform.mercury.json.ninny

import com.lightform.mercury.json.JsonSupport

import io.github.kag0.ninny._

import io.github.kag0.ninny.ast._
import scala.util.Try
import com.lightform.mercury._
import com.lightform.mercury.Request
import com.lightform.mercury.json.NonAbsentWriter
import scala.util.{Success, Failure}
import com.lightform.mercury.json.Reader
import com.lightform.mercury.json.ErrorRegistry
import com.lightform.mercury.ErrorResponse
import com.lightform.mercury.json.BasicError
import java.{util => ju}
import com.typesafe.scalalogging.LazyLogging
import java.nio.charset.StandardCharsets.UTF_8
import scala.collection.compat.immutable.ArraySeq
import scala.collection.immutable.IndexedSeq
import com.lightform.mercury.json.Writer

trait NinnySupport extends JsonSupport[JsonValue] with TypeclassConversions with LazyLogging  {

  val mediaType = "application/json"

  def stringWriter: NonAbsentWriter[JsonValue, String] =
    implicitly[ToSomeJson[String]].toSome

  private implicit val idToJson: ToSomeJson[Either[String, Long]] = {
    case Left(s)  => s.toSomeJson
    case Right(l) => l.toDouble.toSomeJson
  }

  private implicit val idFromJson = FromJson.fromSome {
    case json: JsonNumber => json.to[Long].map(Right(_))
    case JsonString(s)    => Success(Left(s))
    case _ =>
      Failure(
        new IllegalArgumentException("Expected string or number for request id")
      )
  }

  override def requestWriter[P](
      implicit writer: JsonWriter[P]
  ): NonAbsentWriter[JsonValue, Request[P]] =
    request =>
      obj(
        "jsonrpc" -> request.jsonrpc,
        "method"  -> request.method,
        "params"  -> writer.write(request.params),
        "id"      -> request.id
      )

  def requestReader[P](
      implicit reader: JsonReader[P]
  ) = Reader[JsonValue, Request[P]](
    json =>
      for {
        rpcVersion <- json.jsonrpc.to[String]
        _ <- if (rpcVersion == "2.0") Success(())
        else
          Failure(
            new IllegalArgumentException(
              "JSON-RPC request was not protocol version 2.0"
            )
          )
        method <- json.method.to[String]
        params <- reader.read(json.params.maybeJson)
        id     <- json.id.to[Option[Either[String, Long]]]

      } yield Request(method, params, id)
  )

  private implicit val unexpectedErrorFromJson = FromJson.fromSome(
    json =>
      for {
        code    <- json.code.to[Int]
        message <- json.message.to[String]
        data = json.data.maybeJson.map(stringify)
      } yield UnexpectedError(code, message, data)
  )

  private implicit val expectedErrorFromJson =
    FromJson.auto[ExpectedError[Unit]]

  private implicit val basicErrorFromJson = FromJson.auto[BasicError]

  private implicit def errorFromJson[E](
      implicit registry: ErrorRegistry[JsonValue, E]
  ): FromJson[Error[E]] =
    FromJson.fromSome(
      json =>
        json
          .to[BasicError]
          .flatMap(
            registry.lift(_) match {
              case None => json.to[UnexpectedError]
              case Some(reader) =>
                for {
                  error <- json.to[ExpectedError[Unit]]
                  data  <- reader.read(json / "data")
                } yield error.copy(data = data)
            }
          )
    )

  private implicit def errorResponseFromJson[E](
      implicit registry: ErrorRegistry[JsonValue, E]
  ): FromJson[ErrorResponse[E]] =
    FromJson.fromSome(
      json =>
        for {
          error <- json.error.to[Error[E]]
          id    <- json.id.to[Option[Either[String, Long]]]
        } yield ErrorResponse(error, id)
    )

  private implicit val resultResponseFromJson =
    FromJson.auto[ResultResponse[Unit]]

  def responseReader[E, Result](
      implicit errorRegistry: ErrorRegistry[JsonValue, E],
      resultReader: JsonReader[Result]
  ) =
    (maybeJson: Option[JsonValue]) =>
      Try(maybeJson.get).flatMap(
        json =>
          if ((json / "error").isDefined)
            json.to[ErrorResponse[E]]
          else
            for {
              response <- json.to[ResultResponse[Unit]]
              result   <- resultReader.read(json / "result")
            } yield response.copy(result = result)
      )

  private implicit val unexpectedErrorToJson: ToSomeJson[UnexpectedError] = e =>
    obj(
      "code"    -> e.code,
      "message" -> e.message,
      "data" -> e.data.map(
        bin =>
          parse(bin).getOrElse {
            val array = bin.toArray
            val hopefullyString = Try(new String(array, UTF_8))
            val string = hopefullyString.getOrElse(
              ju.Base64.getUrlEncoder.withoutPadding.encodeToString(array)
            )
            logger.warn(
              s"Unable to parse unexpected error body as JSON $string"
            )
            JsonString(string)
          }
      )
    )

  private implicit def expectedErrorToJson[E: ToJson] =
    ToJson.auto[ExpectedError[E]]

  private implicit def errorToJson[E: ToJson]: ToSomeJson[Error[E]] = {
    case e: ExpectedError[E] => e.toSomeJson
    case e: UnexpectedError  => e.toSomeJson
  }

  private implicit def resultResponseToJson[R: ToSomeJson] =
    ToJson.auto[ResultResponse[R]]

  private implicit def errorResponseToJson[E: ToJson] =
    ToJson.auto[ErrorResponse[E]]

  def responseWriter[ErrorData, Result](
      implicit errorWriter: JsonWriter[ErrorData],
      resultWriter: NAJsonWriter[Result]
  ): NonAbsentWriter[JsonValue, Response[ErrorData, Result]] = {
    case r: ResultResponse[Result] =>
      implicit val toJson: ToSomeJson[Result] = resultWriter.writeSome
      r.toSomeJson
    case r: ErrorResponse[ErrorData] =>
      implicit val toJson: ToJson[ErrorData] = errorWriter.write
      r.toSomeJson
  }

  def idLens = _.id.to[Option[Either[String, Long]]].toOption.flatten

  def methodLens = _.method.to[String].toOption

  def jsonIsResponse(json: JsonValue) =
    (json / "result").isDefined || (json / "error").isDefined

  def stringify(json: JsonValue) = {
    val str = Json.render(json)
    val array = str.getBytes
    ArraySeq.unsafeWrapArray(array)
  }

  def parse(string: IndexedSeq[Byte]) = Json.parse(new String(string.toArray))
}

object NinnySupport extends NinnySupport

trait TypeclassConversions {
  implicit def toJsonReader[A: ToJson]: Writer[JsonValue, A] = _.toJson
  implicit def fromJsonWriter[A: FromJson]: Reader[JsonValue, A] = _.to[A]
}
