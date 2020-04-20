package com.lightform.mercury

import com.lightform.mercury.json.{ErrorRegistry, Reader, Writer}

import scala.concurrent.duration.FiniteDuration

/**
  * For the given request and transport parameters, provide a transport-specific
  * hint as to how the message should be sent.
  * This can be things like a pub/sub topic, HTTP request headers, etc.
  * @tparam TransportParams
  * @tparam Hint
  */
trait ClientTransportRequestHint[TransportParams, Hint] {
  def hint(request: Request[Any], transportParams: TransportParams): Hint
}

/**
  * For the given request and transport parameters, provide a transport-specific
  * hint as to how to receive the response.
  * This is typically only for transports like pub/sub where there is no
  * one-to-one socket or transaction concept.
  *
  * @tparam TransportParams
  * @tparam Hint
  */
trait ClientTransportResponseHint[TransportParams, Hint] {
  def hint(request: Request[Any], transportParams: TransportParams): Hint
}

sealed trait RequestMethods

trait PureTransport[F[_], Json] extends RequestMethods {
  this: Client[F, Json] =>

  def transact[P, E, R](params: P, timeout: FiniteDuration = defaultTimeout)(
      implicit
      method: IdMethodDefinition.Aux[P, E, R],
      paramWriter: JsonWriter[P],
      resultReader: JsonReader[R],
      registry: JsonErrorRegistry[E]
  ): F[Either[E, R]]

  def notify[P](params: P, timeout: FiniteDuration = defaultTimeout)(
      implicit
      method: NotificationMethodDefinition[P],
      paramWriter: JsonWriter[P]
  ): F[Unit]
}

trait NoTransportParams[F[_], Json, ReqHint, ResHint] extends RequestMethods {
  this: Client[F, Json] =>
  type ReqH = ClientTransportRequestHint[Unit, ReqHint]
  type ResH = ClientTransportResponseHint[Unit, ResHint]

  def transact[P, E, R](params: P, timeout: FiniteDuration = defaultTimeout)(
      implicit method: IdMethodDefinition.Aux[P, E, R],
      paramWriter: JsonWriter[P],
      resultReader: JsonReader[R],
      registry: JsonErrorRegistry[E],
      transportHint: ReqH
  ): F[Either[E, R]]

  def notify[P](params: P, timeout: FiniteDuration = defaultTimeout)(
      implicit method: NotificationMethodDefinition[P],
      paramWriter: JsonWriter[P],
      transportHint: ReqH
  ): F[Unit]
}

trait TransportParams[F[_], Json, ReqHint, ResHint] extends RequestMethods {
  this: Client[F, Json] =>
  type ReqH[P] = ClientTransportRequestHint[P, ReqHint]
  type ResH[P] = ClientTransportResponseHint[P, ResHint]

  def transact[P, TP, E, R](
      params: P,
      transportParams: TP,
      timeout: FiniteDuration = defaultTimeout
  )(
      implicit method: IdMethodDefinition.Aux[P, E, R],
      paramWriter: JsonWriter[P],
      resultReader: JsonReader[R],
      errorRegistry: JsonErrorRegistry[E],
      transportReqHint: ReqH[TP],
      transportResHint: ResH[TP]
  ): F[Either[E, R]]

  def notify[P, TP](
      params: P,
      transportParams: TP,
      timeout: FiniteDuration = defaultTimeout
  )(
      implicit method: NotificationMethodDefinition[P],
      paramWriter: JsonWriter[P],
      transportHint: ReqH[TP]
  ): F[Unit]
}

trait Client[F[_], Json] { this: RequestMethods =>
  type JsonWriter[A] = Writer[Json, A]
  type JsonReader[A] = Reader[Json, A]
  type JsonErrorRegistry[A] = ErrorRegistry[Json, A]

  protected def defaultTimeout: FiniteDuration
}
