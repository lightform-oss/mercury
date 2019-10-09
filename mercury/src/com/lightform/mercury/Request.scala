package com.lightform.mercury

case class Request[+Params](
    method: String,
    params: Params,
    id: Option[Either[String, Long]]
) {
  val jsonrpc = "2.0"
}
