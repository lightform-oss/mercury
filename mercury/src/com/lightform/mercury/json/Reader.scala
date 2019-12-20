package com.lightform.mercury.json

import scala.util.{Failure, Success, Try}

trait Reader[Json, A] {
  def read(json: Json): Try[A] = read(Some(json))

  // json is optional to allow for reading models with optional fields
  def read(maybeJson: Option[Json]): Try[A]
}

object Reader {

  def apply[Json, A](f: Json => Try[A]): Reader[Json, A] = {
    case None =>
      Failure(new NoSuchElementException("Expected JSON field was missing"))
    case Some(js) => f(js)
  }

  def read[Json, A](json: Json)(implicit reader: Reader[Json, A]) =
    reader.read(json)

  def forObject[Json, A](obj: A): Reader[Json, A] = _ => Success(obj)

  implicit def identity[Json]: Reader[Json, Json] = js => Try(js.get)

  implicit def unit[Json]: Reader[Json, Unit] = forObject(())

  implicit def none[Json]: Reader[Json, None.type] = forObject(None)

  implicit def option[Json, A](
      implicit reader: Reader[Json, A]
  ): Reader[Json, Option[A]] = {
    case Some(json) => reader.read(json).map(Some(_))
    case None       => Success(None)
  }
}
