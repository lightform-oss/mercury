package com.lightform.mercury

package object json {

  case class BasicError(code: Int, message: String)
  type ErrorRegistry[Json, E] = PartialFunction[BasicError, Reader[Json, E]]

}
