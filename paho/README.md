# MQTT transport support for JSON-RPC

## Usage

### Client

A client can be obtained with 

```scala
val eventualClient: Future[PahoMqttClient[MyJson]] = PahoMqttClient[MyJson](brokerUri, clientId, timeout, connectionOptions)
val client = Await(eventualClient, Duration.Inf)
```

Note that a future is returned, the future completes once the connection to the MQTT broker is successfully established.

To send notifications you need to define a `ClientTransportRequestHint[A, MqttMessageCtx]` in implicit scope at the request call site.
This tells the client which topic and qos to use when sending the request. 
One common use for the hint is to pass a server ID with your request, and to have the hint use that in the request topic.
For example if you wanted to send commands to some IoT device

```scala
implicit val clientRequestHint: ClientTransportRequestHint[String, MqttMessageCtx] =
  (_, deviceId) => MqttMessageCtx(s"devices/$deviceId/requests", qos = 1, retain = false)

client.notify(RebootDevice("now"), "device5")
```

To send requests that have a response you also need to define a `ClientTransportResponseHint[A, MqttMessageCtx]`.
This works just like the request hint, but instead of the request topic you supply the response topic. 
The response topic needs to be different from the request topic, otherwise you'd be receiving your own requests.   
The best practice for this is to use the request ID in the response topic.

### Server

Before creating a server you need to define a `ServerTransportHint[MqttMessageCtx]`.
This tells the server which topic to subscribe to for incoming requests and to which topic to publish responses.
The best practice for this is to use the request ID in the response topic.

Invoking `PahoMqttServer.apply` returns a handler helper to help construct request handlers and a builder function that will returns a server from those handlers.
Creating a server might look like this

```scala
val connectionOptions = new MqttConnectOptions()
val (helper, builder) = PahoMqttServer(broker, clientId, connectionOptions)

val handlers = ??? // use the helper to create handlers

val eventualServer: Future[PahoMqttServer[MyJson]] = builder(handlers) // the future completes when the connection to the MQTT broker has been established

Await(eventualServer.flatMap(_.start), Duration.Inf) // the future completes when the subscription to the request topic has been established 
```

## [Example](sample/src/com/lightform/mercury/mqtt/paho/sample/Main.scala)
