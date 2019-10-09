package com.lightform.mercury.pure.websockets.akka

import java.nio.charset.StandardCharsets

import akka.Done
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Keep}
import akka.util.ByteString

import scala.collection.immutable.IndexedSeq
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

package object s {
  def websocketFlow(
      flow: Flow[IndexedSeq[Byte], IndexedSeq[Byte], Future[Done]],
      parallelism: Int,
      timeout: FiniteDuration
  )(implicit mat: Materializer, ec: ExecutionContext): Flow[Message, Message, Future[Done]] =
    Flow[Message]
      .mapAsync(parallelism) {
        case m: TextMessage =>
          m.toStrict(timeout)
            .map(_.text.getBytes(StandardCharsets.UTF_8).toIndexedSeq)
        case m: BinaryMessage =>
          m.toStrict(timeout).map(_.data)
      }
      .viaMat(flow)(Keep.right)
      .map {
        case byteString: ByteString => byteString
        case other                  => ByteString(other: _*)
      }
      .map(BinaryMessage(_))
}
