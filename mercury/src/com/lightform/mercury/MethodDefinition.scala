package com.lightform.mercury

sealed trait MethodDefinition[+Params] {
  type Result
  type ErrorData

  def method: String
}

trait IdMethodDefinition[+P] extends MethodDefinition[P]

object IdMethodDefinition {
  type Aux[P, E, R] = IdMethodDefinition[P] {
    type Result = R
    type ErrorData = E
  }
}

trait NotificationMethodDefinition[+P] extends MethodDefinition[P] {
  final type Result = Nothing
  final type ErrorData = Nothing
}
