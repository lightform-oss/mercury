# HTTP transport for JSON-RPC with Akka HTTP

This module provides an HTTP transport using [Akka HTTP](https://doc.akka.io/docs/akka-http/current/) as the server.
However you need not have any knowledge of Akka HTTP in order to use it, it can be used completely stand alone.

## Usage

### Server

The server can be used in one of three ways depending how much of Akka HTTP you want to get involved with.

#### Stand alone

Stand alone usage is the simplest and easiest way to get a server up and running with no involvement with the transport layer.
Pretty much just call `start` on the server.

```scala

implicit val system = ActorSystem()
implicit val materializer = ActorMaterializer()
implicit val executionContext = system.dispatcher

val handlers: Seq[Handler[Future, YourJson, Unit, HttpRequest]]  = ???

implicit val hint: ServerTransportHint[(StatusCode, Seq[HttpHeader])] = (_, _) => (200, Nil)

val server = new AkkaHttpServer(handlers)
Await.result(
  server.start("localhost", 8080), 
Duration.Inf)
```

#### [Routing DSL](https://doc.akka.io/docs/akka-http/current/introduction.html#routing-dsl-for-http-servers)

The routing DSL approach is useful if you already have an Akka HTTP server serving other routes and you want to make RPC available on another route.

```scala
implicit val system = ActorSystem()
implicit val materializer = ActorMaterializer()
implicit val executionContext = system.dispatcher

val handlers: Seq[Handler[Future, YourJson, Unit, HttpRequest]]  = ???

implicit val hint: ServerTransportHint[(StatusCode, Seq[HttpHeader])] = (_, _) => (200, Nil)

val rpc = new AkkaHttpServer(handlers)

val allMyRoutes: Route = 
  nonRpcRoutes ~ 
  path("rpc") {
    post {
      rpc.route
    }
  }
```

#### [Low-level server API](https://doc.akka.io/docs/akka-http/current/introduction.html#low-level-http-server-apis)

The low-level server API is useful if you don't have other Akka HTTP code, 
but you do want to do advanced configuration on the server for things like HTTPS.

See the Akka HTTP documentation for more information on configuring the HTTP server.

This might look like

```scala
implicit val system = ActorSystem()
implicit val materializer = ActorMaterializer()
implicit val executionContext = system.dispatcher

val handlers: Seq[Handler[Future, YourJson, Unit, HttpRequest]]  = ???

implicit val hint: ServerTransportHint[(StatusCode, Seq[HttpHeader])] = (_, _) => (200, Nil)

val rpc = new AkkaHttpServer(handlers)

val https = ConnectionContext.https(sslContext)
Await.result(
  Http().bindAndHandleAsync(rpc.apply, "localhost", 8080, connectionContext = https), 
Duration.Inf)
```
