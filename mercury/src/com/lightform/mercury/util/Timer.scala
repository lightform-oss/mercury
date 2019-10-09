package com.lightform.mercury.util

import java.util.{TimerTask, Timer => JTimer}

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future, Promise, TimeoutException}
import scala.util.Try

object Timer {
  private val timer = new JTimer(true)

  def schedule[A](delay: FiniteDuration)(task: => A): Future[A] = {
    val promise = Promise[A]
    val timerTask = new TimerTask {
      def run() = promise.tryComplete(Try(task))
    }
    timer.schedule(timerTask, delay.toMillis)
    promise.future
  }

  // credit http://justinhj.github.io/2017/07/16/future-with-timeout.html
  def withTimeout[A](future: Future[A], timeout: FiniteDuration)(
      implicit ec: ExecutionContext
  ): Future[A] = {

    // Promise will be fulfilled with either the callers Future or the timer task if it times out
    val promise = Promise[A]

    // and a Timer task to handle timing out

    val timerTask = new TimerTask() {
      def run() = promise.tryFailure(new TimeoutException())
    }

    // Set the timeout to check in the future
    timer.schedule(timerTask, timeout.toMillis)

    future
      .map { a =>
        if (promise.trySuccess(a)) {
          timerTask.cancel()
        }
      }
      .recover {
        case e: Exception =>
          if (promise.tryFailure(e)) {
            timerTask.cancel()
          }
      }

    promise.future
  }
}
