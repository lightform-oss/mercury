package com.lightform.mercury.json

import com.lightform.mercury.json.Writer.Combine
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
      val error = UnexpectedError.fromData(-32000, "the message", dataString)
      val response = ErrorResponse(error, None)

      val writtenResponse = jsonSupport.responseWriter.writeSome(response)

      val extractedDataString =
        getFromPath(writtenResponse, "error", "data").value

      stringReader
        .read(extractedDataString)
        .success
        .value shouldEqual dataString

      import ErrorRegistry.implicits.expectAll

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

  "Writers" should {

    // this test should be redundant as long as type erasure warnings are set as errors
    "combine" in {
      trait A

      object B extends A {
        implicit val writer: Writer[Json, B.type] = _ => stringWriter.write("B")
      }

      object C extends A {
        implicit val writer: Writer[Json, C.type] = _ => stringWriter.write("C")
      }

      object D extends A {
        implicit val writer = Writer.empty[Json, D.type]
      }

      object E extends A {
        implicit val writer = Writer.empty[Json, E.type]
      }

      val combined = Combine[Json, A] {
        case B => B
        case C => C
        case D => D
        case E => E
      }

      combined.write(B).value shouldEqual stringWriter.writeSome("B")
      combined.write(C).value shouldEqual stringWriter.writeSome("C")
      combined.write(D) shouldEqual None
      combined.write(E) shouldEqual None
    }
  }
}
