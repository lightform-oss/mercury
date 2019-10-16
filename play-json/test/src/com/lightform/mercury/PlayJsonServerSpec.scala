package com.lightform.mercury

import com.lightform.mercury.ServerSpec.HelloRequest
import com.lightform.mercury.json.Reader
import com.lightform.mercury.json.playjson._
import play.api.libs.json.{JsValue, Json}

class PlayJsonServerSpec extends ServerSpec[JsValue] {
  lazy val jsonSupport = PlayJsonSupport
  lazy val helloReader = readsReader(Json.format[HelloRequest])
  lazy val helloWriter = writesWriter(Json.format[HelloRequest])
  lazy val stringReader = implicitly[Reader[JsValue, String]]

  val emptyObject = Json.obj()
}
