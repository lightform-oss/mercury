package com.lightform.mercury.mqtt.paho

import com.lightform.mercury._
import com.lightform.mercury.json._
import com.lightform.mercury.util.{Timer, generateId}
import org.eclipse.paho.client.mqttv3._

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future, Promise, TimeoutException}
import scala.util.Failure
import cats.implicits._

/**
  *
  * @param paho should be pre-connected to the broker
  * @param ec
  * @param jsonSupport
  * @tparam Json
  */
class PahoMqttClient[Json] private (
    private val paho: IMqttAsyncClient,
    protected val defaultTimeout: FiniteDuration
)(implicit ec: ExecutionContext, jsonSupport: JsonSupport[Json])
    extends Client[Future, Json]
    with TransportParams[Future, Json, MqttMessageCtx, MqttMessageCtx] {

  import jsonSupport._

  def notify[P, TP](params: P, transportParams: TP, timeout: FiniteDuration)(
      implicit method: NotificationMethodDefinition[P],
      paramWriter: Writer[Json, P],
      transportHint: ClientTransportRequestHint[TP, MqttMessageCtx]
  ) = {
    val request = generateRequest(params)
    Timer.withTimeout(publish(request, transportParams).map(_ => ()), timeout)
  }

  def transact[P, TP, E, R](
      params: P,
      transportParams: TP,
      timeout: FiniteDuration
  )(
      implicit method: IdMethodDefinition.Aux[P, E, R],
      paramWriter: JsonWriter[P],
      resultReader: JsonReader[R],
      errorRegistry: JsonErrorRegistry[E],
      transportReqHint: ReqH[TP],
      transportResHint: ResH[TP]
  ) = {
    val request = generateRequest(params)
    val hint = transportResHint.hint(request, transportParams)

    val eventualResponse = {
      val (subscribeComplete, eventualReplyMessage) =
        subscribeSingle(hint.topic, hint.qos, timeout)

      val subscribedAndPublished = for {
        _ <- subscribeComplete
        _ <- publish(request, transportParams)
        msg <- eventualReplyMessage
      } yield msg

      subscribedAndPublished
        .tryMap(msg => jsonSupport.parse(msg.getPayload.toIndexedSeq))
        .tryMap(jsonSupport.responseReader[E, R].read)
    }

    eventualResponse
      .recover {
        case e: TimeoutException =>
          ErrorResponse(UnexpectedError(-1, e.getMessage), request.id)
      }
      .flatMap(_.toEitherF[Future])
  }

  private def singleSubscribeListener(
      timeout: FiniteDuration
  )(f: IMqttMessageListener => IMqttToken): Future[MqttMessage] = {
    val messagePromise = Promise[MqttMessage]
    val listener = new IMqttMessageListener {
      def messageArrived(topic: String, message: MqttMessage) = {
        messagePromise.success(message)
        paho.unsubscribe(topic)
      }
    }

    val token = f(listener)
    val existingCallback = token.getActionCallback
    token.setActionCallback(new IMqttActionListener {
      def onSuccess(asyncActionToken: IMqttToken) =
        existingCallback.onSuccess(asyncActionToken)
      def onFailure(asyncActionToken: IMqttToken, exception: Throwable) = {
        messagePromise.failure(exception)
        existingCallback.onFailure(asyncActionToken, exception)
      }
    })

    Timer.withTimeout(
      messagePromise.future,
      timeout
    )
  }

  /**
    *
    * @param topicFilter
    * @param qos
    * @param timeout
    * @return a future which completes when the subscribe completes, and a future which completes when the message is received
    */
  private def subscribeSingle(
      topicFilter: String,
      qos: Int,
      timeout: FiniteDuration
  ) = {
    val subscribePromise = Promise[Unit]

    val eventualMessage = singleSubscribeListener(timeout) { l =>
      val token = paho.subscribe(topicFilter, qos, l)

      token.setActionCallback(new IMqttActionListener {
        def onSuccess(asyncActionToken: IMqttToken) =
          subscribePromise.success(())
        def onFailure(asyncActionToken: IMqttToken, exception: Throwable) =
          subscribePromise.failure(exception)
      })

      token
    }.andThen {
      case Failure(_: TimeoutException) => paho.unsubscribe(topicFilter)
    }

    (subscribePromise.future, eventualMessage)
  }

  private def generateRequest[P](
      params: P
  )(implicit method: MethodDefinition[P]) = Request(
    method.method,
    params,
    method match {
      case _: IdMethodDefinition[P]           => Some(generateId)
      case _: NotificationMethodDefinition[P] => None
    }
  )

  private def publish[P: JsonWriter, TP](
      request: Request[P],
      transportParams: TP
  )(implicit transportHint: ReqH[TP]) = {

    val requestJson = requestWriter[P].writeSome(request)

    val hint = transportHint.hint(request, transportParams)

    val requestMessage = new MqttMessage(
      jsonSupport.stringify(requestJson).toArray
    )
    requestMessage.setQos(hint.qos)

    actionListener(
      listener => paho.publish(hint.topic, requestMessage, null, listener)
    )
  }
}

object PahoMqttClient {

  /**
    *
    * @param broker uri of the broker, should have the same format as here https://www.eclipse.org/paho/files/javadoc/org/eclipse/paho/client/mqttv3/MqttClient.html#MqttClient-java.lang.String-java.lang.String-
    * @param clientId
    * @param transactionTimeout
    * @param ec
    * @tparam Json
    * @return
    */
  def apply[Json: JsonSupport](
      broker: String,
      clientId: String,
      transactionTimeout: FiniteDuration,
      connectOptions: MqttConnectOptions
  )(
      implicit ec: ExecutionContext
  ) =
    pahoClient(broker, clientId, connectOptions).map(
      new PahoMqttClient[Json](_, transactionTimeout)
    )
}
