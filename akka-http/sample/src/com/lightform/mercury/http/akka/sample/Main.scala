package com.lightform.mercury.http.akka.sample

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpHeader, HttpRequest, StatusCode}
import akka.stream.ActorMaterializer
import cats.implicits._
import com.lightform.mercury.http.akka.AkkaHttpServer
import com.lightform.mercury.json.playjson._
import com.lightform.mercury.sample.pets.{
  CreatePet,
  GetPet,
  ListPets,
  PetStoreServer,
  PetStoreService
}
import com.lightform.mercury.{
  ErrorResponse,
  ExpectedError,
  HandlerHelper,
  ResultResponse,
  ServerTransportHint
}
import com.typesafe.scalalogging.LazyLogging
import play.api.libs.json.JsValue

import scala.collection.immutable.Seq
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

object Main extends App with LazyLogging {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  val petServer: PetStoreService = new PetStoreServer

  val helper = new HandlerHelper[Future, JsValue, Unit, HttpRequest]

  val listPetsHandler = helper.transaction(ListPets)(
    (p, _, _) =>
      petServer
        .listPets(p.limit)
        .map(_.toRight(ExpectedError(100, "pets busy", None)))
  )

  val createPetHandler = helper.transaction(CreatePet)(
    (p, _, _) => petServer.createPet(p.newPetName, p.newPetTag).map(Right(_))
  )

  val getPetHandler = helper.transaction(GetPet)(
    (p, _, _) =>
      petServer
        .getPet(p.petId)
        .map(_.toRight(ExpectedError(404, "no such pet", None)))
  )

  val handlers = Seq(listPetsHandler, createPetHandler, getPetHandler)

  implicit val hint: ServerTransportHint[(StatusCode, Seq[HttpHeader])] =
    (_, response) =>
      response match {
        case ResultResponse(_, _) => (200, Nil)
        case ErrorResponse(error, _) if 400 <= error.code && error.code < 600 =>
          (error.code, Nil)
        case ErrorResponse(_, _) => (400, Nil)
      }

  val httpServer = new AkkaHttpServer(handlers)

  logger.info("starting")
  Await.result(httpServer.start(), Duration.Inf)
}
