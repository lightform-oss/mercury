package com.lightform.mercury.mqtt

import org.eclipse.paho.client.mqttv3._
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Try

package object paho {

  implicit class FutureSyntax[A](val future: Future[A]) extends AnyVal {
    def tryMap[B](f: A => Try[B])(implicit ec: ExecutionContext) =
      future.transform(_.flatMap(f))
  }

  class Qos private (val value: Int) extends AnyVal {
    override def toString = String.valueOf(value)
  }

  object Qos {
    def apply(int: Int): Qos = int match {
      case 0 => atMostOnce
      case 1 => atLeastOnce
      case 2 => exactlyOnce
      case other =>
        throw new IllegalArgumentException(
          s"MQTT QOS value must be 0, 1, or 2. Not $other."
        )
    }

    val atMostOnce = new Qos(0)
    val atLeastOnce = new Qos(1)
    val exactlyOnce = new Qos(2)

    import scala.language.implicitConversions
    implicit def value(qos: Qos): Int = qos.value
  }

  case class MqttMessageCtx(topic: String, qos: Qos, retain: Boolean)

  private[paho] def actionListener(
      f: IMqttActionListener => Unit
  ): Future[IMqttToken] = {
    val promise = Promise[IMqttToken]
    val listener = new IMqttActionListener {
      def onSuccess(asyncActionToken: IMqttToken) =
        promise.success(asyncActionToken)
      def onFailure(asyncActionToken: IMqttToken, exception: Throwable) =
        promise.failure(exception)
    }
    f(listener)
    promise.future
  }

  def pahoClient(
      broker: String,
      clientId: String,
      connectionOptions: MqttConnectOptions
  )(
      implicit ec: ExecutionContext
  ) = {
    val promise = Promise[IMqttAsyncClient]
    val persistence = new MemoryPersistence
    try {
      val client = new MqttAsyncClient(broker, clientId, persistence)
      actionListener(
        listener => client.connect(connectionOptions, null, listener)
      ).map(_ => client)
        .onComplete(promise.complete)
    } catch {
      case mqtt: MqttException => promise.failure(mqtt)
    }
    promise.future
  }
}
