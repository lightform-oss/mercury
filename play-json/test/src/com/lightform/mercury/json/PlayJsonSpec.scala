package com.lightform.mercury.json

import com.lightform.mercury.json.playjson.PlayJsonSupport
import play.api.libs.json.{JsDefined, JsUndefined, JsValue}

class PlayJsonSpec extends JsonSpec[JsValue] {
  val jsonSupport = PlayJsonSupport
  val intReader = PlayJsonSupport.readsReader[Int]
  val stringReader = PlayJsonSupport.readsReader[String]

  def getFromPath(
      json: JsValue,
      path: Either[String, Int]*
  ): Option[JsValue] = path match {
    case _ if path.isEmpty => Some(json)
    case Left(p) +: ps =>
      json \ p match {
        case JsDefined(js) => getFromPath(js, ps: _*)
        case JsUndefined() => None
      }
    case Right(p) +: ps =>
      json \ p match {
        case JsDefined(js) => getFromPath(js, ps: _*)
        case JsUndefined() => None
      }
  }
}
