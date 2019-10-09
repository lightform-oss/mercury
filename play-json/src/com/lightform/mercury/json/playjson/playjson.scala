package com.lightform.mercury.json

import play.api.libs.json.JsValue

package object playjson extends PlayJsonDefinitions {

  implicit val playJsonSupport: JsonSupport[JsValue] = PlayJsonSupport
}
