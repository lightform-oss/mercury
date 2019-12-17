package com.lightform.mercury.json

object ErrorRegistry {
  def apply[Json, E](
      readers: Map[Int, Reader[Json, E]]
  ): ErrorRegistry[Json, E] = Function.unlift(e => readers.get(e.code))

  def single[Json, E](
      code: Int
  )(implicit reader: Reader[Json, E]): ErrorRegistry[Json, E] = {
    case e if e.code == code => reader
  }

  def expectAll[Json, E](
      implicit reader: Reader[Json, E]
  ): ErrorRegistry[Json, E] = {
    // The error codes from and including -32768 to -32000 are reserved for pre-defined errors.
    case e if -32768 >= e.code && e.code >= -32000 => reader
  }

  object implicits {
    implicit def expectAll[Json, E](
        implicit reader: Reader[Json, E]
    ): ErrorRegistry[Json, E] = ErrorRegistry.expectAll
  }

  implicit def none[Json]: ErrorRegistry[Json, None.type] =
    PartialFunction.empty

  def abstractReader[Js, A, B <: A](reader: Reader[Js, B]): Reader[Js, A] =
    maybeJson => reader.read(maybeJson)

  def abstractRegistry[Js, A, B <: A](
      registry: ErrorRegistry[Js, B]
  ) = registry.andThen(abstractReader[Js, A, B](_))

  def combine[A] = new CombineInterim2[A](())

  implicit class Syntax[Js, E](val registry: ErrorRegistry[Js, E])
      extends AnyVal {

    def upcast[A >: E]: ErrorRegistry[Js, A] = abstractRegistry(registry)

    def combine[C <: E](second: ErrorRegistry[Js, C]): ErrorRegistry[Js, E] =
      registry.orElse(abstractRegistry(second))
  }

  class CombineInterim2[A] private[ErrorRegistry] (val dummy: Unit)
      extends AnyVal {
    def apply[Js, B <: A, C <: A](
        first: ErrorRegistry[Js, B],
        second: ErrorRegistry[Js, C]
    ): ErrorRegistry[Js, A] =
      abstractRegistry[Js, A, B](first)
        .orElse(abstractRegistry[Js, A, C](second))
  }
}
