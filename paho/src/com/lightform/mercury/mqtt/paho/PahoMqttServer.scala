package com.lightform.mercury.mqtt.paho

import cats.implicits._
import com.lightform.mercury.json._
import com.lightform.mercury.{
  Handler,
  HandlerHelper,
  Server,
  ServerTransportHint
}
import com.typesafe.scalalogging.LazyLogging
import org.eclipse.paho.client.mqttv3.{
  IMqttAsyncClient,
  IMqttMessageListener,
  MqttConnectOptions,
  MqttMessage
}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class PahoMqttServer[Json] private (
    rpcTopic: String,
    qos: Int,
    paho: IMqttAsyncClient,
    protected val handlers: Seq[
      Handler[Future, Json, MqttMessageCtx, Unit, MqttMessageCtx]
    ]
)(
    implicit ec: ExecutionContext,
    jsonSupport: JsonSupport[Json],
    transportHint: ServerTransportHint[MqttMessageCtx]
) extends Server[Future, Json, MqttMessageCtx, Unit, MqttMessageCtx]
    with LazyLogging {

  private object Listener extends IMqttMessageListener {
    def messageArrived(topic: String, message: MqttMessage) = {
      val requestCtx = MqttMessageCtx(topic, message.getQos, message.isRetained)
      logger.debug("got " + message.getPayload + " on " + requestCtx)

      handle(message.getPayload.toIndexedSeq, (), requestCtx).onComplete {
        case Failure(e)    => logger.error(e.getMessage, e)
        case Success(None) => // do nothing, it was a notification
        case Success(Some((json, hint))) =>
          logger.debug(s"sending back $json with $hint")
          paho.publish(
            hint.topic,
            jsonSupport.stringify(json).toArray,
            hint.qos,
            hint.retain
          )
      }
    }
  }

  def start =
    actionListener(
      l => paho.subscribe(rpcTopic, qos, Listener).setActionCallback(l)
    ).map(_ => ())
}

object PahoMqttServer {
  def apply[Json: JsonSupport](
      broker: String,
      clientId: String,
      requestTopic: String,
      qos: Int,
      connectionOptions: MqttConnectOptions
  )(
      implicit ec: ExecutionContext,
      transportHint: ServerTransportHint[MqttMessageCtx]
  ) = {
    (
      new HandlerHelper[Future, Json, MqttMessageCtx, Unit, MqttMessageCtx],
      (handlers: Seq[
        Handler[Future, Json, MqttMessageCtx, Unit, MqttMessageCtx]
      ]) =>
        pahoClient(broker, clientId, connectionOptions)
          .map(
            paho => new PahoMqttServer[Json](requestTopic, qos, paho, handlers)
          )
    )
  }
}
