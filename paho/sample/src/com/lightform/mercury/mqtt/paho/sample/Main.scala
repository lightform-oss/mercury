package com.lightform.mercury.mqtt.paho.sample

import com.lightform.mercury.json.playjson._
import com.lightform.mercury.mqtt.paho.{
  MqttMessageCtx,
  PahoMqttClient,
  PahoMqttServer,
  Qos
}
import com.lightform.mercury.sample.broker
import com.lightform.mercury.sample.pets.{
  CreatePet,
  GetPet,
  ListPets,
  PetStoreServer,
  PetStoreService
}
import com.lightform.mercury.{
  ClientTransportRequestHint,
  ClientTransportResponseHint,
  ExpectedError,
  ServerTransportHint
}
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import play.api.libs.json.JsValue

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

object ServerExample extends App {
  implicit val serverHint: ServerTransportHint[MqttMessageCtx] =
    (_, response) => {
      val topic = response.id match {
        case Some(id) => s"json-rpc/servers/$serverId/reply/$id"
        case None     => s"json-rpc/servers/$serverId/reply"
      }
      MqttMessageCtx(topic, Qos.atLeastOnce, retain = false)
    }

  val petServer: PetStoreService = new PetStoreServer

  val connectionOptions = new MqttConnectOptions()

  val (help, serverBuilder) =
    PahoMqttServer(
      broker,
      "testDevice",
      s"json-rpc/servers/$serverId",
      1,
      connectionOptions
    )

  val listPetsHandler = help.transaction(ListPets)(
    (p, _, _) =>
      petServer
        .listPets(p.limit)
        .map(_.toRight(ExpectedError(100, "pets busy", None)))
  )
  val createPetHandler = help.transaction(CreatePet)(
    (p, _, _) => petServer.createPet(p.newPetName, p.newPetTag).map(Right(_))
  )
  val getPetHandler = help.transaction(GetPet)(
    (p, _, _) =>
      petServer
        .getPet(p.petId)
        .map(_.toRight(ExpectedError(404, "no such pet", None)))
  )

  val handlers = Seq(listPetsHandler, createPetHandler, getPetHandler)

  Await.result(
    serverBuilder(handlers).flatMap(_.start),
    Duration.Inf
  )
}

object ClientExample extends App {
  implicit val clientTransportRequestHint
      : ClientTransportRequestHint[Unit, MqttMessageCtx] =
    (_, _) =>
      MqttMessageCtx(
        s"json-rpc/servers/$serverId",
        Qos.atLeastOnce,
        retain = false
      )

  implicit val clientTransportResponseHint
      : ClientTransportResponseHint[Unit, MqttMessageCtx] =
    (request, _) => {
      val topic = request.id match {
        case Some(id) => s"json-rpc/servers/$serverId/reply/$id"
        case None     => s"json-rpc/servers/$serverId/reply"
      }
      MqttMessageCtx(topic, Qos.atLeastOnce, false)
    }

  val connectionOptions = new MqttConnectOptions()
  val client = Await.result(
    PahoMqttClient[JsValue](broker, "clientid", 1 minute, connectionOptions),
    Duration.Inf
  )

  val petClient = new PetStoreService {
    def listPets(limit: Option[Int]) =
      client.transact(ListPets(limit), ()).map(_.toOption)

    def createPet(newPetName: String, newPetTag: Option[String]) =
      client.transact(CreatePet(newPetName, newPetTag), ()).flatMap {
        case Right(unit) => Future.successful(unit)
        case Left(error) => Future.failed(error.toException)
      }

    def getPet(petId: Int) =
      client.transact(GetPet(petId), ()).flatMap {
        case Right(pet)                       => Future.successful(Some(pet))
        case Left(error) if error.code == 404 => Future.successful(None)
        case Left(error)                      => Future.failed(error.toException)
      }
  }

  Await.ready(petClient.createPet("newbie", None), Duration.Inf)
  val pets = Await.result(petClient.listPets(None), Duration.Inf)
  println(pets)
  println(
    Await.result(petClient.getPet(pets.get.head.id), Duration.Inf)
  )
  System.exit(0)
}
