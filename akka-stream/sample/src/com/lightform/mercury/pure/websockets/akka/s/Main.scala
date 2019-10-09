package com.lightform.mercury.pure.websockets.akka.s

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws._
import akka.http.scaladsl.model.{
  HttpMethods,
  HttpRequest,
  HttpResponse,
  StatusCodes
}
import akka.stream.ActorMaterializer
import com.lightform.mercury.ExpectedError
import com.lightform.mercury.json.playjson._
import com.lightform.mercury.pure.akka.AkkaStreamClientServer
import com.lightform.mercury.sample.pets._
import play.api.libs.json.JsValue

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.io.StdIn
import scala.language.postfixOps

object ServerExample extends App {
  implicit val system = ActorSystem("my-system")
  implicit val materializer = ActorMaterializer()
  // needed for the future flatMap/onComplete in the end
  import system.dispatcher

  val petServer: PetStoreService = new PetStoreServer

  val helper = AkkaStreamClientServer.handlerHelper[JsValue, Unit]

  val listPets = helper.transaction(ListPets)(
    (p, _, _) =>
      petServer
        .listPets(p.limit)
        .map(_.toRight(ExpectedError(100, "pets busy", None)))
  )
  val createPet = helper.transaction(CreatePet)(
    (p, _, _) => petServer.createPet(p.newPetName, p.newPetTag).map(Right(_))
  )
  val getPet = helper.transaction(GetPet)(
    (p, _, _) =>
      petServer
        .getPet(p.petId)
        .map(_.toRight(ExpectedError(404, "no such pet", None)))
  )

  val parallelism = 50
  val timeout = 5 seconds

  val petFlow = websocketFlow(
    AkkaStreamClientServer(
      (),
      Seq(listPets, createPet, getPet),
      parallelism = parallelism,
      requestTimeout = timeout
    ).flow,
    parallelism,
    timeout
  )

  val requestHandler: HttpRequest => HttpResponse = {
    case req @ HttpRequest(HttpMethods.GET, _, _, _, _) =>
      req.header[UpgradeToWebSocket] match {
        case Some(upgrade) => upgrade.handleMessages(petFlow)
        case None =>
          HttpResponse(400, entity = "Not a valid websocket request!")
      }
  }

  val bindingFuture =
    Http().bindAndHandleSync(requestHandler, "localhost", 8080)

  println(s"Server online at ws://localhost:8080/\nPress RETURN to stop...")
  StdIn.readLine() // let it run until user presses return
  bindingFuture
    .flatMap(_.unbind()) // trigger unbinding from the port
    .onComplete(_ => system.terminate()) // and shutdown when done
}

object ClientExample extends App {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  val parallelism = 50
  val timeout = 5 seconds

  val client = AkkaStreamClientServer(
    (),
    Nil,
    parallelism = parallelism,
    requestTimeout = timeout
  )

  val petFlow = websocketFlow(client.flow, parallelism, timeout)

  val (upgradeResponse, closed) =
    Http()
      .singleWebSocketRequest(WebSocketRequest("ws://localhost:8080"), petFlow)

  val connected = upgradeResponse.map { upgrade =>
    // just like a regular http request we can access response status which is available via upgrade.response.status
    // status code 101 (Switching Protocols) indicates that server support WebSockets
    if (upgrade.response.status == StatusCodes.SwitchingProtocols) {
      Done
    } else {
      throw new RuntimeException(
        s"Connection failed: ${upgrade.response.status}"
      )
    }
  }

  closed.foreach(_ => println("closed"))

  val petClient = Await.result(
    connected.map(
      _ =>
        new PetStoreService {
          def listPets(limit: Option[Int]) =
            client.transact(ListPets(limit)).map(_.toOption)

          def createPet(newPetName: String, newPetTag: Option[String]) =
            client.transact(CreatePet(newPetName, newPetTag)).flatMap {
              case Right(unit) => Future.successful(unit)
              case Left(error) => Future.failed(error.toException)
            }

          def getPet(petId: Int) =
            client.transact(GetPet(petId)).flatMap {
              case Right(pet)                       => Future.successful(Some(pet))
              case Left(error) if error.code == 404 => Future.successful(None)
              case Left(error)                      => Future.failed(error.toException)
            }
        }
    ),
    Duration.Inf
  )

  Await.ready(petClient.createPet("newbie", None), Duration.Inf)
  val pets = Await.result(petClient.listPets(None), Duration.Inf)
  println(pets)
  println(
    Await.result(petClient.getPet(pets.get.head.id), Duration.Inf)
  )
  System.exit(0)
}
