package com.lightform.mercury.json

trait Writer[Json, -A] {
  // return an option to allow the distinction between json null and absent field
  def write(a: A): Option[Json]
}

trait NonAbsentWriter[Json, -A] extends Writer[Json, A] {
  def writeSome(a: A): Json

  def write(a: A) = Some(writeSome(a))
}

object Writer extends LowPriorityWriters {

  def apply[Json, A](f: A => Json): NonAbsentWriter[Json, A] = a => f(a)

  class WriterPartiallyTyped[Json] {
    def write[A](a: A)(implicit writer: Writer[Json, A]) = writer.write(a)

    def writeSome[A](a: A)(implicit writer: NonAbsentWriter[Json, A]) =
      writer.writeSome(a)
  }

  def apply[Json] = new WriterPartiallyTyped[Json]
}

trait LowPriorityWriters {
  //implicit def nothingWriter[Json]: Writer[Json, Nothing] = _ => None

  implicit def identityWriter[Json]: NonAbsentWriter[Json, Json] = js => js

  /*
  implicit def optionWriter[Json, A](
      implicit writer: Writer[Json, A]
  ): Writer[Json, Option[A]] = {
    case Some(a) => writer.write(a)
    case None    => None
  }

   */

  implicit def unitWriter[Json](
      implicit seqWriter: NonAbsentWriter[Json, Seq[Int]]
  ): NonAbsentWriter[Json, Unit] =
    Writer[Json, Unit](_ => seqWriter.writeSome(Seq.empty))

  implicit def noneWriter[Json]: Writer[Json, None.type] = _ => None
}
