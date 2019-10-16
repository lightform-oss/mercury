package com.lightform.mercury.http.akka

import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{
  HttpEntity,
  HttpHeader,
  HttpRequest,
  HttpResponse,
  StatusCode
}
import akka.http.scaladsl.server.Route
import com.lightform.mercury.{Handler, Server, ServerTransportHint}
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.util.ByteString

import scala.collection.immutable.Seq
import scala.concurrent.{ExecutionContext, Future}
import cats.implicits._
import com.lightform.mercury.json.JsonSupport

import scala.concurrent.duration._

/**
  * The server can be used three ways
  * 1. as a standalone server using myServer.start()
  * 2. with the routing DSL using myServer.route https://doc.akka.io/docs/akka-http/current/routing-dsl/index.html
  * 3. with the core server API using Http().bindAndHandleAsync(myServer.apply, interface, port) https://doc.akka.io/docs/akka-http/current/server-side/low-level-api.html
  *    this is useful over the first option when you want to configure TLS or other properties of akka.http.scaladsl.Http
  *
  * @param handlers
  * @param timeout the duration that the server will wait for a request body to be sent from the client
  */
class AkkaHttpServer[Json](
    protected val handlers: Seq[Handler[Future, Json, Unit, HttpRequest]],
    timeout: FiniteDuration = 5 seconds
)(
    implicit hint: ServerTransportHint[(StatusCode, Seq[HttpHeader])],
    jsonSupport: JsonSupport[Json],
    ec: ExecutionContext
) extends Server[
      Future,
      Json,
      (StatusCode, Seq[HttpHeader]),
      Unit,
      HttpRequest
    ] { server =>

  def apply(
      request: HttpRequest
  )(implicit mat: Materializer): Future[HttpResponse] =
    for {
      strictReq <- request.toStrict(timeout)
      data <- strictReq.entity.toStrict(timeout).map(_.data)
      response <- handle(data, (), strictReq)
    } yield response match {
      case None => HttpResponse()
      case Some((json, (status, headers))) =>
        HttpResponse(
          status,
          headers,
          HttpEntity(jsonSupport.stringify(json) match {
            case bs: ByteString => bs
            case other          => ByteString(other: _*)
          })
        )
    }

  val route = Route(ctx => ctx.complete(apply(ctx.request)(ctx.materializer)))

  object Ignition {
    def apply(interface: String = "0.0.0.0", port: Int = 8080)(
        implicit sys: ActorSystem,
        mat: Materializer
    ) = Http().bindAndHandleAsync(server.apply, interface, port)
  }

  val start = Ignition
}
