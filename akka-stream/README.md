# Akka Stream based transport for JSON-RPC

This module isn't exactly a transport itself, 
but rather works with any transport that can be used with an Akka `Flow`; 
such as websockets or TCP with a message framing layer 
(it can also work with alpakka libraries to form some more exotic transports). 

## Usage

This module supports bi-directional communication such that both ends of the connection can act as the client AND the server.
Thus the setup for both is the same.

### Server

For server-only you can immediately call the `flow` method and discard the `AkkaStreamClientServer` instance. 
The server will be started when a stream is materialized with the flow.

```scala
val helper = AkkaStreamClientServer.handlerHelper[YourJson, Unit]
val handlers: Seq[Handler[Future, YourJson, Unit, Unit]] = ???

val flow: Flow[String, String, Future[Done]] = AkkaStreamClientServer((), handlers).flow
```

> The above example uses `Unit` for the connection context. 
> If you want to provide some value for the connection context (such as authentication data) to handlers, you can pass your value in the first parameter of `AkkaStreamClientServer.apply`.

### Client

A client-only setup can be built the same way as a server, but pass `Nil` for the handlers.
This way 

```scala
val client = AkkaStreamClientServer((), Nil)
val flow = client.flow // client won't be ready to use until the flow is materialized
```

## [Example](sample/src/com/lightform/mercury/pure/websockets/akka/s/Main.scala)
