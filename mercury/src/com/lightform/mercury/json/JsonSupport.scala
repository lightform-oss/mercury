package com.lightform.mercury.json

import com.lightform.mercury.{Request, Response}

import scala.collection.immutable.IndexedSeq
import scala.util.Try

trait JsonSupport[Json] {
  type JsonReader[A] = Reader[Json, A]
  type JsonWriter[A] = Writer[Json, A]
  type NAJsonWriter[A] = NonAbsentWriter[Json, A]
  type JsonErrorRegistry[A] = ErrorRegistry[Json, A]

  /**
    * Used in case the transport cares about the type of bytes it's being given,
    * for example in HTTP content type headers.
    * @return the MIME type of this encoding, usually application/json
    */
  def mediaType: String

  implicit def stringWriter: NonAbsentWriter[Json, String]
  implicit def optionWriter[A](
      implicit writer: JsonWriter[A]
  ): JsonWriter[Option[A]]

  def requestWriter[P](
      implicit writer: JsonWriter[P]
  ): NonAbsentWriter[Json, Request[P]]

  def requestReader[P](implicit reader: JsonReader[P]): JsonReader[Request[P]]

  def responseReader[ErrorData, Result](
      implicit errorRegistry: ErrorRegistry[Json, ErrorData],
      resultReader: JsonReader[Result]
  ): JsonReader[Response[ErrorData, Result]]

  def responseWriter[ErrorData, Result](
      implicit errorWriter: JsonWriter[ErrorData],
      resultWriter: NAJsonWriter[Result]
  ): NonAbsentWriter[Json, Response[ErrorData, Result]]

  // Given some json, return the "id" field if it exists and has the right type
  def idLens: Json => Option[Either[String, Long]]

  // Given some json, return the "method" field if it exists and has the right type
  def methodLens: Json => Option[String]

  // Returns true if the json contains a "result" or "error" field
  def jsonIsResponse(json: Json): Boolean

  def stringify(json: Json): IndexedSeq[Byte]

  def parse(string: IndexedSeq[Byte]): Try[Json]
}
