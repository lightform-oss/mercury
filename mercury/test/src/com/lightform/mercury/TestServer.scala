package com.lightform.mercury

import com.lightform.mercury.json.JsonSupport

import scala.concurrent.{ExecutionContext, Future}
import cats.implicits._

import scala.collection.immutable.IndexedSeq

class TestServer[Json: JsonSupport](val handlers: Seq[Handler[Future, Json, Unit, Unit, Unit]])(implicit ec: ExecutionContext) extends Server[Future, Json, Unit, Unit, Unit] {

  def _handle(
              jsonString: IndexedSeq[Byte],
              connectionCtx: Unit,
              requestCtx: Unit
            ) = handle(jsonString, connectionCtx, requestCtx)

  def start: Future[Unit] = Future.successful(())
}
