package com.lightform.mercury

import com.lightform.mercury.ServerSpec.{HelloRequest, MiddlewareRequest}
import com.lightform.mercury.json.Reader
import com.lightform.mercury.json.playjson._
import play.api.libs.json.{JsArray, JsNumber, JsObject, JsValue, Json}

class PlayJsonServerSpec extends ServerSpec[JsValue] {
  lazy val jsonSupport = PlayJsonSupport
  lazy val helloReader = readsReader(Json.format[HelloRequest])
  lazy val helloWriter = writesWriter(Json.format[HelloRequest])
  lazy val stringReader = implicitly[Reader[JsValue, String]]

  val emptyObject = Json.obj()

  lazy val middlewareNotificationReader = readsReader(
    Json.format[MiddlewareRequest]
  )

  def appendNumToValsField(i: Int, json: JsValue) = {
    val obj = json.as[JsObject]
    val arr = obj.value.getOrElse("vals", JsArray()).as[JsArray] :+ JsNumber(i)
    obj + ("vals" -> arr)
  }
}
