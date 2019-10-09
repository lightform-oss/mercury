package com.lightform

import cats.MonadError

package object mercury {
  type MonadException[F[_]] = MonadError[F, Throwable]

  implicit class IdSyntax(val id: Either[String, Long]) extends AnyVal {
    def idToString = id.map(_.toString).left.map(_.toString).merge
  }
}
