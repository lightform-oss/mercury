package com.lightform.mercury

import cats.data.EitherT
import cats.implicits._
import cats.{Applicative, Monad, MonadError}
import com.lightform.mercury.json.{JsonSupport, NonAbsentWriter, Reader, Writer}
import com.typesafe.scalalogging.LazyLogging

import scala.util.{Failure, Success}

sealed trait Handler[F[_], Json, CCtx, RCtx] {
  def method: MethodDefinition[_]

  def handle(
      json: Request[Option[Json]],
      connectionCtx: CCtx,
      requestCtx: RCtx
  ): F[Option[Response[Option[Json], Json]]]
}

class NotificationHandler[F[_], P, Json, ConnectionCtx, RequestCtx](
    val method: NotificationMethodDefinition[P]
)(flow: (P, ConnectionCtx, RequestCtx) => F[Unit])(
    implicit
    M: MonadError[F, Throwable],
    paramReader: Reader[Json, P]
) extends Handler[F, Json, ConnectionCtx, RequestCtx]
    with LazyLogging {

  def handle(
      request: Request[Option[Json]],
      connectionCtx: ConnectionCtx,
      requestCtx: RequestCtx
  ) =
    _handle(request, connectionCtx, requestCtx).map(_ => None)

  def _handle(
      request: Request[Option[Json]],
      connectionCtx: ConnectionCtx,
      requestCtx: RequestCtx
  ) = {
    paramReader.read(request.params) match {
      case Success(params) => flow(params, connectionCtx, requestCtx)
      case Failure(exception) =>
        logger.debug("Error reading request paramerters", exception)
        ().pure[F]
    }
  }
}

class IdHandler[F[_]: Monad, P, +D <: IdMethodDefinition[P], Json, ConnectionCtx, RequestCtx](
    val method: D
)(
    flow: (
        P,
        ConnectionCtx,
        RequestCtx
    ) => F[Either[Error[D#ErrorData], D#Result]]
)(
    implicit paramReader: Reader[Json, P],
    errorDataWriter: Writer[Json, D#ErrorData],
    resultWriter: NonAbsentWriter[Json, D#Result],
    jsonSupport: JsonSupport[Json]
) extends Handler[F, Json, ConnectionCtx, RequestCtx] {

  import jsonSupport._

  def handle(
      json: Request[Option[Json]],
      connectionCtx: ConnectionCtx,
      requestCtx: RequestCtx
  ) =
    _handle(json, connectionCtx, requestCtx).map(Some(_))

  def _handle(
      jsonRequest: Request[Option[Json]],
      connectionCtx: ConnectionCtx,
      requestCtx: RequestCtx
  ) =
    (
      for {
        params <- EitherT.fromEither[F](
          paramReader
            .read(jsonRequest.params)
            .toEither
            .leftMap(
              e =>
                ErrorResponse(
                  UnexpectedError.fromData(
                    -32602,
                    "Invalid params",
                    e.getMessage
                  ),
                  jsonRequest.id
                )
            )
        )

        result <- EitherT(flow(params, connectionCtx, requestCtx))
          .leftMap(
            e =>
              ErrorResponse(
                e match {
                  case ExpectedError(code, message, data) =>
                    ExpectedError(code, message, errorDataWriter.write(data))
                  case e: UnexpectedError => e
                },
                jsonRequest.id
              )
          )

      } yield ResultResponse(resultWriter.writeSome(result), jsonRequest.id)
    ).fold(identity, identity)
}

class HandlerHelper[F[_]: Monad: Applicative, Json: JsonSupport, ConnectionCtx, RequestCtx] {
  def transaction[P](method: IdMethodDefinition[P])(
      flow: (
          P,
          ConnectionCtx,
          RequestCtx
      ) => F[Either[Error[method.ErrorData], method.Result]]
  )(
      implicit paramReader: Reader[Json, P],
      errorDataWriter: Writer[Json, method.ErrorData],
      resultWriter: NonAbsentWriter[Json, method.Result]
  ) =
    new IdHandler[F, P, method.type, Json, ConnectionCtx, RequestCtx](
      method
    )(flow)

  def notification[P](
      method: NotificationMethodDefinition[P]
  )(flow: (P, ConnectionCtx, RequestCtx) => F[Unit])(
      implicit M: MonadError[F, Throwable],
      paramReader: Reader[Json, P]
  ) =
    new NotificationHandler[F, P, Json, ConnectionCtx, RequestCtx](
      method
    )(
      flow
    )
}
