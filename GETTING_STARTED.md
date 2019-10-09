# Getting Started

## Modelling

Regardless of if you want to use JSON-RPC as a client or server, you need to model the requests and responses in your API.  
There are four things we need to define for a request that expects a response.

1. Request parameters
2. Result type
3. Error type
4. Method definition

As a simple example let's use the typical petstore API and a request to get a specific pet.  
A request might look like this:

```scala
case class GetPet(petId: Int)
```

For a response we might expect... a pet

```scala
case class Pet(id: Int, name: String)
```

The only way this could go wrong is if there is no pet with that id, so an error might look like this:

```scala
case object NoSuchPet
```

Now we just need to tie these all together in a method definition

```scala
implicit object GetPet extends IdMethodDefinition[GetPet] {
  type Result = Pet
  type ErrorData = NoSuchPet.type
  val method = "get_pet"
}
```

If we don't expect a response to our request, our model is even simpler. We just use `NotificationMethodDefinition` in place of `IdMethodDefinition` and don't define the result or error types.

```scala
case class SendLove(petId: Int)
implicit object SendLove extends NotificationMethodDefinition[SendLove] {
  val method = "send_love"
}
```

#### The last step

There is technically one more step for your models, which is to define mappings from your classes to JSON and back.
What you need to do depends on which JSON library you're using.
In Play! JSON this is as simple as adding an `implicit val format = Json.format[MyClass]` to the companion objects of your classes.
With Circe it's even easier, you don't have to do anything in full auto mode.

## Client

Setting up the client varies with what transport layer you're using. 
Check out the documentation for your client/transport layer for more details (for example, [here](paho/README.md)).  

For the simplest transports (one to one bidirectional connections like websockets) implementing `PureTransport`, you just need do do whatever the transport requires in order to establish a connection.  
For more complicated transports (like MQTT or HTTP) you need to define a `ClientTransportRequestHint` (which gives the client any transport-specific information it needs to know in order to send the request) and a `ClientTransportResponseHint` (which gives the client transport-specific information it might need on how to retreive the response).

Aside from transport hints, client usage is simple. You just pass a request parameters object to the `notify` method to send a notification method, or to the `transact` method to receive a response.

```scala
// The type of F depends on the client implementation. It could be a Future to be awaited or an IO monad to be executed.
val response: F[Either[Error[NoSuchPet], Pet]] = client.transact(GetPet(1))
val notification: F[Unit] = client.notify(SendLove(1))
```

## Server

Setting up the server varies with what transport layer you're using. 
Check out the documentation for your server/transport layer for more details (for example, [here](paho/README.md)).  

The essential part of setting up the server is defining request handlers. 
Handlers are either of type `IdHandler` for requests that need responses, 
or `NotificationHandler` for notifications.
Both types essentially tie a method definition together with some logic to handle a request.
Since the handler classes have quite a few type parameters which would be tedious to repeat for each method, there is a `HandlerHelper` class which allows you to specify the types just once.
Most server implementations will provide a helper for you so that you don't even need to specify the types once.  
Handlers can be defined like

```scala
val getPetHandler = helper.transaction(GetPet)(
  (request, connectionContext, requestContext) => 
    petDb.getOption(request.petId).map {
      case Some(pet) => Right(pet)
      case None => Left(Error(404, s"Pet ${request.petId} does not exist.", NoSuchPet))
    }
)

val sendLoveHandler = helper.notification(SendLove)(
  (request, connectionContext, requestContext) =>
    println(s"Pet ${request.petId} is loved!").pure[F]
    // The exact type of F depends on the server implementation. It could be a Future, or an IO monad, etc.
)
```
