package com.lightform.mercury.json.playjson

import com.lightform.mercury.ServerSpec
import com.lightform.mercury.ServerSpec.HelloRequest
import com.lightform.mercury.json.Reader
import play.api.libs.json.{JsValue, Json}

class PlayJsonServerSpec extends ServerSpec[JsValue] {
  lazy val jsonSupport = PlayJsonSupport
  lazy val helloReader = readsReader(Json.format[HelloRequest])
  lazy val helloWriter = writesWriter(Json.format[HelloRequest])
  lazy val stringReader = implicitly[Reader[JsValue, String]]

  val emptyObject = Json.obj()
}
