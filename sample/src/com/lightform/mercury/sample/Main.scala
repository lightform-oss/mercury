package com.lightform.mercury.sample

import cats.implicits._
import com.lightform.mercury.{
  ClientTransportRequestHint,
  ClientTransportResponseHint,
  ServerTransportHint
}
import com.lightform.mercury.json.playjson.PlayJsonSupport
import com.typesafe.scalalogging.LazyLogging
import com.lightform.mercury.json.playjson._
import com.lightform.mercury.mqtt.paho._
import com.lightform.mercury.{
  ClientTransportRequestHint,
  ClientTransportResponseHint,
  ServerTransportHint,
  _
}
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import play.api.libs.json.JsValue

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

object ClientExample extends App with LazyLogging with PlayJsonSupport {

  val timeout = 1 minute
  val connectionOptions = new MqttConnectOptions()
  val client = Await.result(
    PahoMqttClient[JsValue](broker, "clientid", timeout, connectionOptions),
    Duration.Inf
  )

  implicit val lfMqttHint: ClientTransportRequestHint[String, MqttMessageCtx] =
    (_, deviceId) =>
      MqttMessageCtx(
        s"v/1/devices/$deviceId/rpc",
        1,
        retain = false
      )

  implicit val lfMqttResHint
      : ClientTransportResponseHint[String, MqttMessageCtx] =
    (request, deviceId) =>
      MqttMessageCtx(
        request.id
          .map(
            id =>
              s"v/1/devices/$deviceId/rpc/reply/${id.fold(identity, _.toString)}"
          )
          .getOrElse(
            throw new Exception(
              s"id missing when trying to compose a reply for ${request.method} request"
            )
          ),
        1,
        retain = false
      )

  val response = client.transact(TestRequest("param1Value"), "testDevice")

  val note = client.notify(TestNotification("notification param"), "testDevice")
  try {
    logger.info(Await.result(response.zip(note), Duration.Inf).toString)
  } catch { case e: Throwable => logger.error(e.getMessage, e) }

  System.exit(0)
}

object ServerExample extends App with LazyLogging with PlayJsonSupport {
  implicit val lfMqttResponseHint: ServerTransportHint[MqttMessageCtx] =
    (_, response) => {
      val topic = response.id match {
        case Some(id) =>
          s"v/1/devices/testDevice/rpc/reply/${id.idToString}"
        case None => "v/1/devices/testDevice/rpc/reply"
      }
      MqttMessageCtx(topic, 1, false)
    }

  val connectionOptions = new MqttConnectOptions()
  val (help, serverBuilder) =
    PahoMqttServer(
      broker,
      "testDevice",
      "v/1/devices/testDevice/rpc",
      1,
      connectionOptions
    )
  val testHandler = help.transaction(TestRequest)((_, _, _) => {
    Future.successful(Right(TestResult("value")))
  })
  val noteHandler =
    help.notification(TestNotification)((params, _, requestCtx) => {
      Future.successful(logger.info(s"got $params $requestCtx notification"))
    })
  val eventualServer = serverBuilder(Seq(testHandler, noteHandler))

  Await.result(eventualServer.flatMap(_.start), Duration.Inf)
}
