package com.lightform.mercury.json

import com.lightform.mercury.{ErrorResponse, UnexpectedError}
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.{Matchers, OptionValues, TryValues, WordSpec}

import scala.language.implicitConversions

trait JsonSpec[Json]
    extends WordSpec
    with Matchers
    with LazyLogging
    with OptionValues
    with TryValues {

  implicit def jsonSupport: JsonSupport[Json]
  implicit def stringWriter = jsonSupport.stringWriter
  implicit def stringReader: Reader[Json, String]
  implicit def intReader: Reader[Json, Int]

  def getFromPath(json: Json, path: Either[String, Int]*): Option[Json]
  implicit def eitherizeInt(int: Int) = Right(int)
  implicit def eitherizeString(string: String) = Left(string)

  "Json support" should {
    "correctly handle the weirdness of UnexpectedError writing and parsing" in {
      val dataString = "Some unexpected error data"
      val error = UnexpectedError.fromData(-1, "the message", dataString)
      val response = ErrorResponse(error, None)

      val writtenResponse = jsonSupport.responseWriter.writeSome(response)

      val extractedDataString =
        getFromPath(writtenResponse, "error", "data").value
      stringReader
        .read(extractedDataString)
        .success
        .value shouldEqual dataString

      val readError =
        jsonSupport
          .responseReader[Int, Int]
          .read(writtenResponse)
          .success
          .value match {
          case ErrorResponse(e: UnexpectedError, _) => e
          case o =>
            logger.info(o.toString)
            fail()
        }

      readError
        .unexpectedData[Json]
        .as[String]
        .success
        .value shouldEqual dataString
    }
  }
}
