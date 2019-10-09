package com.lightform.mercury

import play.api.libs.json.Json

package object sample {

  val broker = "tcp://test.mosquitto.org:1883"

  case class TestRequest(param1: String)

  implicit object TestRequest extends IdMethodDefinition[TestRequest] {
    type Result = TestResult
    type ErrorData = String
    type Params = TestRequest
    val method = "testRequest"

    implicit val format = Json.format[TestRequest]
  }

  case class TestResult(value: String)
  object TestResult {
    implicit val format = Json.format[TestResult]
  }

  case class TestNotification(param: String)

  implicit object TestNotification
      extends NotificationMethodDefinition[TestNotification] {
    type Params = TestNotification
    val method = "testNotification"

    implicit val format = Json.format[TestNotification]
  }
}
