package com.lightform.mercury

import cats.implicits._
import com.lightform.mercury.ServerSpec.{
  HelloDie,
  HelloDieWorse,
  HelloRequest,
  TestNotification
}
import com.lightform.mercury.json.{JsonSupport, Reader, Writer}
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Random, Success}

trait ServerSpec[Json]
    extends WordSpec
    with Matchers
    with ScalaFutures
    with EitherValues
    with OptionValues
    with TryValues {
  implicit def jsonSupport: JsonSupport[Json]
  implicit def helloReader: Reader[Json, HelloRequest]
  implicit def helloWriter: Writer[Json, HelloRequest]
  implicit def stringWriter = jsonSupport.stringWriter
  implicit def stringReader: Reader[Json, String]
  implicit val testNotificationReader = Reader[Json, TestNotification](
    _ => Success(TestNotification())
  )

  def emptyObject: Json

  val helper = new HandlerHelper[Future, Json, Unit, Unit]

  val helloHandler = helper.transaction(HelloRequest)(
    (req, _, _) => Future.successful(Right(s"Hello ${req.name}!"))
  )

  val dieHandler =
    helper.transaction(HelloDie)(
      (_, _, _) => Future.failed(new Exception("Boom"))
    )
  val dieWorseHandler =
    helper.transaction(HelloDieWorse)(
      (_, _, _) => throw new Exception("KA BOOM")
    )

  "The server" should {

    val server =
      new TestServer[Json](Seq(helloHandler, dieHandler, dieWorseHandler))

    "return -32700 on garbage input" in {
      val garbage = new Array[Byte](200)
      Random.nextBytes(garbage)
      val (json, _) =
        server._handle(garbage.toIndexedSeq, (), ()).futureValue.value
      val response =
        jsonSupport.responseReader[String, String].read(json).success.value
      response match {
        case ResultResponse(_, _)    => fail()
        case ErrorResponse(error, _) => error.code shouldEqual -32700
      }
    }

    "return -32600 on invalid JSON-RPC requests" in {
      val (json, _) = server
        ._handle(jsonSupport.stringify(emptyObject), (), ())
        .futureValue
        .value
      val response =
        jsonSupport.responseReader[String, String].read(json).success.value
      response match {
        case ResultResponse(_, _)    => fail()
        case ErrorResponse(error, _) => error.code shouldEqual -32600
      }
    }

    "return -32601 on non-existent methods" in {
      val (json, _) = server
        ._handle(
          jsonSupport.stringify(
            jsonSupport
              .requestWriter[Json]
              .writeSome(Request("notRegistered", emptyObject, None))
          ),
          (),
          ()
        )
        .futureValue
        .value
      val response =
        jsonSupport.responseReader[String, String].read(json).success.value
      response match {
        case ResultResponse(_, _)    => fail()
        case ErrorResponse(error, _) => error.code shouldEqual -32601
      }
    }

    "return -32602 on invalid request parameters" in {
      val (json, _) = server
        ._handle(
          jsonSupport.stringify(
            jsonSupport
              .requestWriter[Json]
              .writeSome(
                Request(HelloRequest.method, emptyObject, Some(Right(1)))
              )
          ),
          (),
          ()
        )
        .futureValue
        .value
      val response =
        jsonSupport.responseReader[String, String].read(json).success.value
      response match {
        case ResultResponse(_, _)    => fail()
        case ErrorResponse(error, _) => error.code shouldEqual -32602
      }
    }

    "return -32000 if the handler returns an exception" in {
      val (json, _) = server
        ._handle(
          jsonSupport.stringify(
            jsonSupport
              .requestWriter[HelloRequest]
              .writeSome(
                Request(HelloDie.method, HelloRequest("die"), Some(Right(1)))
              )
          ),
          (),
          ()
        )
        .futureValue
        .value
      val response =
        jsonSupport.responseReader[String, String].read(json).success.value
      response match {
        case ResultResponse(_, _)    => fail()
        case ErrorResponse(error, _) => error.code shouldEqual -32000
      }
    }

    "return -32000 if the handler throws an exception" in {
      val (json, _) = server
        ._handle(
          jsonSupport.stringify(
            jsonSupport
              .requestWriter[HelloRequest]
              .writeSome(
                Request(
                  HelloDieWorse.method,
                  HelloRequest("die"),
                  Some(Right(1))
                )
              )
          ),
          (),
          ()
        )
        .futureValue
        .value
      val response =
        jsonSupport.responseReader[String, None.type].read(json).success.value
      response match {
        case ResultResponse(_, _)    => fail()
        case ErrorResponse(error, _) => error.code shouldEqual -32000
      }
    }

    "return successfully on valid input" in {
      val (json, _) = server
        ._handle(
          jsonSupport.stringify(
            jsonSupport
              .requestWriter[HelloRequest]
              .writeSome(
                Request(
                  HelloRequest.method,
                  HelloRequest("World"),
                  Some(Right(12345))
                )
              )
          ),
          (),
          ()
        )
        .futureValue
        .value
      val response =
        jsonSupport.responseReader[String, String].read(json).success.value
      response match {
        case ResultResponse(result, id) =>
          id.value shouldEqual Right(12345)
          result shouldEqual "Hello World!"
        case ErrorResponse(error, _) => fail(error.toException)
      }
    }

    "execute notifications" in {
      var sideEffect = false

      val notificationHandler = helper.notification(TestNotification)(
        (_, _, _) => Future { sideEffect = true }
      )

      val srv = new TestServer[Json](Seq(notificationHandler))

      val maybe = srv
        ._handle(
          jsonSupport.stringify(
            jsonSupport
              .requestWriter[Json]
              .writeSome(
                Request(
                  TestNotification.method,
                  emptyObject,
                  Some(Right(12345))
                )
              )
          ),
          (),
          ()
        )
        .futureValue

      maybe shouldEqual None

      sideEffect shouldEqual true
    }
  }
}

object ServerSpec {

  case class TestNotification()
  implicit object TestNotification
      extends NotificationMethodDefinition[TestNotification] {
    val method = "testNotification"
  }

  case class HelloRequest(name: String)
  implicit object HelloRequest extends IdMethodDefinition[HelloRequest] {
    type Result = String
    type ErrorData = String

    val method = "hello"
  }

  object HelloDie extends IdMethodDefinition[HelloRequest] {
    type Result = String
    type ErrorData = String

    val method = "die"
  }

  object HelloDieWorse extends IdMethodDefinition[HelloRequest] {
    type Result = String
    type ErrorData = String

    val method = "dieWorse"
  }
}
