package com.lightform.mercury.json.playjson

import com.lightform.mercury._
import com.lightform.mercury.json._
import com.lightform.mercury.json.playjson.PlayJsonSupport.{
  JsonReader,
  JsonWriter
}
import com.typesafe.scalalogging.LazyLogging
import play.api.libs.json._

import scala.collection.immutable.IndexedSeq
import scala.util.{Failure, Success, Try}

object PlayJsonSupport extends PlayJsonSupport

trait PlayJsonSupport extends JsonSupport[JsValue] with PlayJsonDefinitions {
  val mediaType = "application/json"

  def optionWriter[A](implicit writer: JsonWriter[A]) =
    implicitly[JsonWriter[Option[A]]]

  def requestWriter[P](implicit writer: JsonWriter[P]) =
    Writer[JsValue, Request[P]](
      request =>
        Json.obj(
          "jsonrpc" -> request.jsonrpc,
          "method" -> request.method,
          "params" -> writer.write(request.params),
          "id" -> request.id
        )
    )

  def requestReader[P](implicit reader: JsonReader[P]) =
    Reader[JsValue, Request[P]](
      js =>
        if ((js \ "jsonrpc").validate[String].contains("2.0")) {
          for {
            method <- JsResult.toTry((js \ "method").validate[String])
            params <- reader.read((js \ "params").toOption)
            maybeId = (js \ "id").validate[Either[String, Long]].asOpt
          } yield Request(method, params, maybeId)
        } else {
          Failure(
            new Exception("JSON-RPC request was not protocol version 2.0")
          )
        }
    )

  def responseReader[ErrorData, Result](
      implicit registry: JsonErrorRegistry[ErrorData],
      resultReader: JsonReader[Result]
  ) = { maybeJson: Option[JsValue] =>
    Try(maybeJson.get)
      .flatMap(
        json =>
          JsResult.toTry(
            if ((json \ "error").isDefined)
              errorResponseReads[ErrorData].reads(json)
            else resultResponseReads[Result].reads(json)
          )
      )
  }

  def responseWriter[ErrorData, Result](
      implicit errorWriter: JsonWriter[ErrorData],
      resultWriter: NAJsonWriter[Result]
  ) =
    writesWriter(
      responseWrites[ErrorData, Result](
        writerWrites[ErrorData],
        writerWrites[Result]
      )
    )

  def parse(string: IndexedSeq[Byte]) = Try(Json.parse(string.toArray))
  def stringify(json: JsValue) = Json.toBytes(json).toIndexedSeq

  val stringWriter = implicitly[NonAbsentWriter[JsValue, String]]

  def idLens: JsValue => Option[Either[String, Long]] =
    js => (js \ "id").validateOpt[Either[String, Long]].asOpt.flatten

  def methodLens: JsValue => Option[String] =
    js => (js \ "method").validate[String].asOpt

  def jsonIsResponse(json: JsValue) =
    (json \ "result").isDefined || (json \ "error").isDefined
}

trait PlayJsonDefinitions extends LowPriorityDefinitions with LazyLogging {
  implicit val idFormat: Format[Either[String, Long]] =
    Format[Either[String, Long]](
      Reads[Either[String, Long]](
        js =>
          js.validate[String]
            .map(Left(_))
            .orElse(js.validate[Long].map(Right(_)))
      ),
      Writes[Either[String, Long]](_.fold(Json.toJson(_), Json.toJson(_)))
    )

  val basicErrorFormat = Json.format[BasicError]

  // expected
  def exErrorReads[E: Reads] = Json.reads[ExpectedError[E]]

  // unexpected
  val uxErrorReads = Reads(
    js =>
      for {
        code <- (js \ "code").validate[Int]
        message <- (js \ "message").validate[String]
        dataJs <- (js \ "data").validateOpt[JsValue]
      } yield UnexpectedError(
        code,
        message,
        dataJs.map(PlayJsonSupport.stringify)
      )
  )

  implicit def errorReads[E](
      implicit registry: ErrorRegistry[JsValue, E]
  ): Reads[Error[E]] =
    json =>
      basicErrorFormat
        .reads(json)
        .flatMap(
          error =>
            registry.lift(error) match {
              case None         => uxErrorReads.reads(json)
              case Some(reader) => exErrorReads(readerReads(reader)).reads(json)
            }
        )

  implicit def errorWrites[E: Writes]: OWrites[Error[E]] = {
    case e: ExpectedError[E] => Json.writes[ExpectedError[E]].writes(e)
    case e: UnexpectedError =>
      val data = e.data.map(
        bin =>
          PlayJsonSupport.parse(bin) match {
            case Success(js) => js
            case Failure(e) =>
              val strData = Try(new String(bin.toArray))
              logger
                .warn(s"Unable to parse unexpected error body as JSON${strData
                  .map(s => s": `$s`")
                  .getOrElse("")}", e)
              JsString(strData.getOrElse(bin.toString))
          }
      )

      Json.obj(
        "code" -> e.code,
        "message" -> e.message,
        "data" -> data
      )
  }

  def resultResponseReads[Result: Reads] =
    Reads(
      js =>
        for {
          result <- (js \ "result").validate[Result]
          id <- (js \ "id").validateOpt[Either[String, Long]]
        } yield ResultResponse(result, id)
    )

  def errorResponseReads[ErrorData](
      implicit registry: ErrorRegistry[JsValue, ErrorData]
  ) = Reads(
    js =>
      for {
        error <- (js \ "error").validate[Error[ErrorData]]
        id <- (js \ "id").validateOpt[Either[String, Long]]
      } yield ErrorResponse(error, id)
  )

  def resultResponseWrites[Result: Writes] = Json.writes[ResultResponse[Result]]
  def errorResponseWrites[ErrorData: Writes] =
    Json.writes[ErrorResponse[ErrorData]]

  def responseWrites[ErrorData: Writes, Result: Writes]
      : OWrites[Response[ErrorData, Result]] = response => {
    val json = response match {
      case response: ErrorResponse[ErrorData] =>
        errorResponseWrites[ErrorData].writes(response)
      case response: ResultResponse[Result] =>
        resultResponseWrites[Result].writes(response)
    }
    json + ("jsonrpc", JsString(response.jsonrpc))
  }

  implicit def readsReader[A: Reads]: Reader[JsValue, A] =
    Reader[JsValue, A](js => JsResult.toTry(js.validate[A]))

  implicit def writesWriter[A: Writes]: NonAbsentInvariantWriter[JsValue, A] =
    Json.toJson
}

trait LowPriorityDefinitions {
  private[playjson] implicit def writerWrites[A](
      implicit writer: JsonWriter[A]
  ): Writes[A] =
    a => writer.write(a).getOrElse(JsNull)

  private[playjson] implicit def readerReads[A](
      implicit reader: JsonReader[A]
  ): Reads[A] =
    Reads[A](
      reader
        .read(_)
        .fold(
          {
            case JsResult.Exception(error) => error
            case _                         => JsError()
          },
          JsSuccess(_)
        )
    )
}
