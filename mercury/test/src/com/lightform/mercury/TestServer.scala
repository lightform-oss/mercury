package com.lightform.mercury

import cats.implicits._
import com.lightform.mercury.Server.Middleware
import com.lightform.mercury.json.JsonSupport

import scala.collection.immutable.{IndexedSeq, Seq}
import scala.util.{Success, Try}

class TestServer[Json: JsonSupport](
    val handlers: Seq[Handler[Try, Json, Unit, Unit]],
    override val middleware: Seq[Middleware[Try, Json, Unit, Unit]] = Nil
) extends Server[Try, Json, Unit, Unit, Unit] {

  def _handle(
      jsonString: IndexedSeq[Byte],
      connectionCtx: Unit,
      requestCtx: Unit
  ) = handle(jsonString, connectionCtx, requestCtx)

  def start: Try[Unit] = Success(())
}
