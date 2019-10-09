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

  case class MqttMessageCtx(topic: String, qos: Int, retain: Boolean)

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
      actionListener(listener => client.connect(connectionOptions, listener))
        .map(_ => client)
        .onComplete(promise.complete)
    } catch {
      case mqtt: MqttException => promise.failure(mqtt)
    }
    promise.future
  }
}
