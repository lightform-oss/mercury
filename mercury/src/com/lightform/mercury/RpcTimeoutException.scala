package com.lightform.mercury

import java.util.concurrent.TimeoutException

class RpcTimeoutException(message: String) extends TimeoutException(message)

// thrown on the server when method handlers don't return a timely response
class RpcServerTimeoutException(
    val requestId: Option[Either[String, Long]] = None,
    message: String = """
      |The JSON-RPC method handler was unable
      | to produce a timely result to the request.
      |""".stripMargin.replace("\n", "")
) extends RpcTimeoutException(message: String)

// thrown on the client when no response is received from the server in a timely manner
class RpcClientTimeoutException(
    message: String = """
      |The JSON-RPC server was unable to
      | produce a timely response to the request
      | and the client has stopped waiting.
      |""".stripMargin.replace("\n", "")
) extends RpcTimeoutException(message: String)
