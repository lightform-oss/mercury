package com.lightform.mercury.json.ninny

import com.lightform.mercury.ServerSpec
import com.lightform.mercury.ServerSpec._
import io.github.kag0.ninny.ast._
import io.github.kag0.ninny._
import Auto._
import com.lightform.mercury.json.Reader
import com.lightform.mercury.json.Writer

class NinnyServerSpec extends ServerSpec[JsonValue] {
  lazy val jsonSupport  = NinnySupport
  import jsonSupport._
  lazy val helloReader  = _.to[HelloRequest]
  lazy val helloWriter  = _.toJson
  lazy val stringReader = _.to[String]

  val emptyObject               = obj()
  val emptyObjectWithEmptyArray = obj("vals" -> arr())

  lazy val middlewareNotificationReader = _.to[MiddlewareRequest]

  def appendNumToValsField(i: Int, json: JsonValue) = {
    val obj        = json.to[JsonObject].get
    val maybeArray = obj.values.getOrElse("vals", arr())

    val array = maybeArray.asInstanceOf[JsonArray] :+ JsonNumber(i)
    obj + ("vals" -> array)
  }

  case class Example(foo: String, bar: Int)

  implicitly[Reader[JsonValue, Example]]
  implicitly[Writer[JsonValue, Example]]
}
