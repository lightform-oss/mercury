package com.lightform.mercury

import java.nio.charset.StandardCharsets

import cats.implicits._
import com.lightform.mercury.ServerSpec.{
  HelloDie,
  HelloDieWorse,
  HelloRequest,
  MiddlewareRequest,
  NameEmpty,
  NoParams,
  TestNotification
}
import com.lightform.mercury.json.{ErrorRegistry, JsonSupport, Reader, Writer}
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures

import scala.collection.immutable.Seq
import scala.util.{Failure, Random, Success, Try}

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
  implicit def middlewareNotificationReader: Reader[Json, MiddlewareRequest]

  def appendNumToValsField(i: Int, json: Json): Json

  def emptyObject: Json
  def emptyObjectWithEmptyArray: Json

  val helper = new HandlerHelper[Try, Json, Unit, Unit]

  val helloHandler = helper.transaction(HelloRequest)(
    (req, _, _) =>
      Success(
        if (req.name.isEmpty)
          Left(ExpectedError(1, "name field was empty", NameEmpty))
        else Right(s"Hello ${req.name}!")
      )
  )

  val dieHandler =
    helper.transaction(HelloDie)(
      (_, _, _) => Failure(new Exception("Boom"))
    )
  val dieWorseHandler =
    helper.transaction(HelloDieWorse)(
      (_, _, _) => throw new Exception("KA BOOM")
    )

  val noParamHandler =
    helper.transaction(NoParams.definition)((_, _, _) => Success(Right("OK")))

  "The server" should {

    val server =
      new TestServer[Json](
        Seq(helloHandler, dieHandler, dieWorseHandler, noParamHandler)
      )

    {
      import com.lightform.mercury.json.ErrorRegistry.implicits.expectAll

      "return -32700 on garbage input" in {
        val garbage = new Array[Byte](200)
        Random.nextBytes(garbage)
        val (json, _) =
          server._handle(garbage.toIndexedSeq, (), ()).success.value.value

        val response =
          jsonSupport.responseReader[String, String].read(json).success.value
        response match {
          case ResultResponse(_, _) => fail()
          case ErrorResponse(error: UnexpectedError, _) =>
            error.code shouldEqual -32700
          case ErrorResponse(_: ExpectedError[Any], _) => fail()
        }
      }

      "return -32600 on invalid JSON-RPC requests" in {
        val (json, _) = server
          ._handle(jsonSupport.stringify(emptyObject), (), ())
          .success
          .value
          .value
        val response =
          jsonSupport.responseReader[String, String].read(json).success.value
        response match {
          case ResultResponse(_, _) => fail()
          case ErrorResponse(error: UnexpectedError, _) =>
            error.code shouldEqual -32600
          case ErrorResponse(_: ExpectedError[Any], _) => fail()
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
          .success
          .value
          .value
        val response =
          jsonSupport.responseReader[String, String].read(json).success.value
        response match {
          case ResultResponse(_, _) => fail()
          case ErrorResponse(error: UnexpectedError, _) =>
            error.code shouldEqual -32601
          case ErrorResponse(_: ExpectedError[Any], _) => fail()
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
          .success
          .value
          .value
        val response =
          jsonSupport.responseReader[String, String].read(json).success.value
        response match {
          case ResultResponse(_, _) => fail()
          case ErrorResponse(error: UnexpectedError, _) =>
            error.code shouldEqual -32602
          case ErrorResponse(_: ExpectedError[Any], _) => fail()
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
          .success
          .value
          .value
        val response =
          jsonSupport.responseReader[String, String].read(json).success.value
        response match {
          case ResultResponse(_, _) => fail()
          case ErrorResponse(error: UnexpectedError, _) =>
            error.code shouldEqual -32000
          case ErrorResponse(_: ExpectedError[Any], _) => fail()
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
          .success
          .value
          .value
        val response =
          jsonSupport.responseReader[String, None.type].read(json).success.value
        response match {
          case ResultResponse(_, _) => fail()
          case ErrorResponse(error: UnexpectedError, _) =>
            error.code shouldEqual -32000
          case ErrorResponse(_: ExpectedError[String], _) => fail()
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
          .success
          .value
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
          (_, _, _) =>
            Try {
              sideEffect = true
            }
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
          .success
          .value

        maybe shouldEqual None

        sideEffect shouldEqual true
      }

      "handle requests with no params" in {
        // need to make sure the json support library isn't writing in a null for the params
        // looking at you play json 😞
        val request =
          if (jsonSupport.mediaType.startsWith("application/json")) {
            s"""
             |{
             |"jsonrpc":"2.0", "method": "${NoParams.definition.method}", "id": 12345
             |}
             |""".stripMargin.getBytes(StandardCharsets.UTF_8).toIndexedSeq
          } else
            jsonSupport.stringify(
              jsonSupport
                .requestWriter[NoParams.type]
                .writeSome(
                  Request(
                    NoParams.definition.method,
                    NoParams,
                    Some(Right(12345))
                  )
                )
            )

        val (json, _) = server
          ._handle(request, (), ())
          .success
          .value
          .value
        val response =
          jsonSupport.responseReader[String, String].read(json).success.value
        response match {
          case ResultResponse(result, id) =>
            id.value shouldEqual Right(12345)
            result shouldEqual "OK"
          case ErrorResponse(error, _) =>
            println(error.expectedData)
            fail(error.toException)
        }
      }
    }
  }

  "Middleware" should {
    "run in order" in {
      val middlewareHandler = helper.notification(MiddlewareRequest) {
        case (MiddlewareRequest(vals), _, _) =>
          vals shouldEqual Seq(1, 2, 3)
          Success(())
      }

      val server = new TestServer[Json](
        Seq(middlewareHandler),
        Seq(
          {
            case ((req, _, _), inner) =>
              val newRequest =
                req.copy(params = req.params.map(appendNumToValsField(1, _)))
              inner(newRequest, (), ())
          }, {
            case ((req, _, _), inner) =>
              val newRequest =
                req.copy(params = req.params.map(appendNumToValsField(2, _)))
              inner(newRequest, (), ())
          }, {
            case ((req, _, _), inner) =>
              val newRequest =
                req.copy(params = req.params.map(appendNumToValsField(3, _)))
              inner(newRequest, (), ())
          }
        )
      )

      server
        ._handle(
          jsonSupport.stringify(
            jsonSupport
              .requestWriter[Json]
              .writeSome(Request(MiddlewareRequest.method, emptyObjectWithEmptyArray, None))
          ),
          (),
          ()
        )
        .success
        .value

  }
  }
}

object ServerSpec {

  case class TestNotification()
  implicit object TestNotification
      extends NotificationMethodDefinition[TestNotification] {
    val method = "testNotification"
  }

  object NameEmpty {
    implicit def errorRegistry[Js]: ErrorRegistry[Js, NameEmpty.type] =
      ErrorRegistry.single(1)(Reader.forObject(NameEmpty))
    implicit def writer[Js]: Writer[Js, NameEmpty.type] =
      Writer.empty[Js, NameEmpty.type]
  }

  case class HelloRequest(name: String)
  implicit object HelloRequest extends IdMethodDefinition[HelloRequest] {
    type Result    = String
    type ErrorData = NameEmpty.type

    val method = "hello"
  }

  object HelloDie extends IdMethodDefinition[HelloRequest] {
    type Result    = String
    type ErrorData = String

    val method = "die"
  }

  object HelloDieWorse extends IdMethodDefinition[HelloRequest] {
    type Result    = String
    type ErrorData = String

    val method = "dieWorse"
  }

  object NoParams {
    implicit val definition = new IdMethodDefinition[NoParams.type] {
      type Result    = String
      type ErrorData = None.type

      val method = "noParams"
    }

    implicit def reader[J]: Reader[J, NoParams.type] = _ => Success(NoParams)
    implicit def writer[J]: Writer[J, NoParams.type] = _ => None
  }

  case class MiddlewareRequest(vals: Seq[Int])
  implicit object MiddlewareRequest
      extends NotificationMethodDefinition[MiddlewareRequest] {
    def method: String = "middleware"
  }
}
