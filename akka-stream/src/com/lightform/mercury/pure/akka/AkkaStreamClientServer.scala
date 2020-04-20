package com.lightform.mercury.pure.akka

import akka.stream.QueueOfferResult.{Dropped, QueueClosed}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.{Materializer, OverflowStrategy}
import cats.implicits._
import com.lightform.mercury.Server.Middleware
import com.lightform.mercury.json.{ErrorRegistry, JsonSupport, Reader}
import com.lightform.mercury.pure.akka.AkkaStreamClientServer.ncores
import com.lightform.mercury.util.{Timer, generateId}
import com.lightform.mercury._
import com.typesafe.scalalogging.LazyLogging

import scala.collection.immutable.{IndexedSeq, Seq}
import scala.collection.concurrent.TrieMap
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future, Promise, TimeoutException}
import scala.language.postfixOps
import scala.util.{Failure, Success}

class AkkaStreamClientServer[Json, CCtx](
    connectionContext: CCtx,
    protected val handlers: Seq[Handler[Future, Json, CCtx, Unit]],
    override protected val middleware: Seq[
      Middleware[Future, Json, CCtx, Unit]
    ] = Nil,
    parallelism: Int = ncores * 3,
    bufferSize: Int,
    val defaultTimeout: FiniteDuration
)(
    implicit ec: ExecutionContext,
    mat: Materializer,
    jsonSupport: JsonSupport[Json]
) extends Server[Future, Json, Unit, CCtx, Unit]
    with Client[Future, Json]
    with PureTransport[Future, Json]
    with LazyLogging {

  import jsonSupport._

  private val incoming = Sink.foreachAsync(parallelism)(onMessageReceived)

  private val (outQueue, outSource) = Source
    .queue[Json](bufferSize, OverflowStrategy.backpressure)
    .mapAsync(ncores)(json => Future(stringify(json)))
    .preMaterialize()

  private val responseCompletions =
    TrieMap.empty[Either[String, Long], ResponseCompletion[Json, _, _, _]]

  val flow = Flow.fromSinkAndSourceMat(incoming, outSource)(Keep.left)

  def transact[P, E, R](params: P, timeout: FiniteDuration)(
      implicit method: IdMethodDefinition.Aux[P, E, R],
      paramWriter: JsonWriter[P],
      resultReader: JsonReader[R],
      registry: JsonErrorRegistry[E]
  ): Future[Either[E, R]] = {
    val completion = ResponseCompletion(method, Promise[Response[E, R]])
    val id = generateId
    responseCompletions.put(id, completion)
    val request = Request(method.method, params, Some(id))
    val requestJson = requestWriter[P].writeSome(request)
    enqueue(requestJson)
    Timer.schedule(timeout)(responseCompletions.remove(id))
    val response = completion.promise.future
    Timer
      .withTimeout(
        response,
        timeout
      )
      .recover {
        case e: TimeoutException =>
          ErrorResponse(UnexpectedError(-1, e.getMessage), request.id)
      }
      .flatMap(_.toEitherF[Future])
  }

  def notify[P](params: P, timeout: FiniteDuration)(
      implicit method: NotificationMethodDefinition[P],
      paramWriter: JsonWriter[P]
  ) = {
    val request = Request(method.method, params, None)
    val requestJson = requestWriter[P].writeSome(request)
    Timer.withTimeout(enqueue(requestJson), timeout)

  }

  private def onMessageReceived(jsonString: IndexedSeq[Byte]): Future[Unit] =
    parse(jsonString) match {
      case Success(json) if jsonIsResponse(json) => onResponseReceived(json)
      case Success(json)                         => onRequestReceived(json)
      case Failure(e) =>
        val response = ErrorResponse(
          UnexpectedError.fromData(-32700, "Parse error", e.getMessage),
          None
        )

        val responseJson = responseWriter[String, Nothing].writeSome(response)

        enqueue(responseJson)
    }

  private def onRequestReceived(json: Json) =
    handle(json, connectionContext, ())
      .flatMap {
        case Some((json, _)) => enqueue(json)
        case None            => Future.successful(())
      }

  private def onResponseReceived(json: Json) = {
    val maybeId = idLens(json)
    if (maybeId.isEmpty) {
      logger.warn(s"Received response with no id: $json")
    }

    val maybeCompletion = maybeId.flatMap { id =>
      val maybeComp = responseCompletions.get(id)
      if (maybeComp.isEmpty) {
        logger.info(
          s"Received response with id ${id.idToString} for a message that wasn't sent (or had timed out)"
        )
      }
      maybeComp.map(id -> _)
    }

    maybeCompletion
      .map {
        case (id, c) =>
          c.responseReader.read(json) match {
            case Success(response)  => c.complete(response)
            case Failure(exception) => c.promise.failure(exception)
          }
          responseCompletions.remove(id)
          c.promise.future
            .map(_ => ())
      }
      .getOrElse(Future.successful(()))
  }

  private def enqueue(json: Json) =
    outQueue
      .offer(json)
      .andThen {
        case Success(e @ (Dropped | QueueClosed)) =>
          logger.error(
            s"Unable to send request or response because $e. You may need to increase bufferSize."
          )
      }
      .map(_ => ())

  def start =
    throw new NotImplementedError(
      "The akka stream transport cannot be started, you need to materialize a stream that uses the provided flow."
    )
}

object AkkaStreamClientServer {

  private val ncores = Runtime.getRuntime.availableProcessors

  def apply[Json: JsonSupport, CCtx](
      connectionContext: CCtx,
      handlers: Seq[Handler[Future, Json, CCtx, Unit]],
      middleware: Seq[Middleware[Future, Json, CCtx, Unit]] = Nil,
      parallelism: Int = ncores * 3,
      bufferSize: Int = 300,
      requestTimeout: FiniteDuration = 5 seconds
  )(
      implicit ec: ExecutionContext,
      mat: Materializer
  ) =
    new AkkaStreamClientServer[Json, CCtx](
      connectionContext,
      handlers,
      middleware,
      parallelism,
      bufferSize,
      requestTimeout
    )

  def handlerHelper[Json: JsonSupport, CCtx](implicit ec: ExecutionContext) =
    new HandlerHelper[Future, Json, CCtx, Unit]
}

private case class ResponseCompletion[Json, P, E, R](
    method: IdMethodDefinition.Aux[P, E, R],
    promise: Promise[Response[E, R]]
)(
    implicit val resultReader: Reader[Json, R],
    val registry: ErrorRegistry[Json, E]
) {
  type Error = E
  type Result = R

  def responseReader(
      implicit jsonSupport: JsonSupport[Json]
  ): jsonSupport.JsonReader[Response[Error, Result]] =
    jsonSupport.responseReader[Error, Result]

  def complete(response: Response[Error, Result]) = promise.success(response)
}
