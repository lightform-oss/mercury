package com.lightform.mercury.sample

import java.util.concurrent.atomic.AtomicInteger

import com.lightform.mercury.IdMethodDefinition
import com.lightform.mercury.json.{BasicError, ErrorRegistry, Reader, Writer}
import play.api.libs.json.Json

import scala.collection.concurrent.TrieMap
import scala.concurrent.Future
import scala.util.Random

package object pets {
  case class Pet(id: Int, name: String, tag: Option[String])
  object Pet {
    implicit val format = Json.format[Pet]
  }

  case object PetsBusy {
    implicit def writer[Js] = Writer.empty[Js, PetsBusy.type]
    implicit def registry[Js]: ErrorRegistry[Js, PetsBusy.type] = {
      case BasicError(100, _) => Reader.forObject(PetsBusy)
    }
  }

  case class ListPets(limit: Option[Int])
  implicit object ListPets extends IdMethodDefinition[ListPets] {
    type Result = Seq[Pet]
    type ErrorData = PetsBusy.type

    val method = "list_pets"
    implicit val format = Json.format[ListPets]
  }

  case class CreatePet(newPetName: String, newPetTag: Option[String])
  implicit object CreatePet extends IdMethodDefinition[CreatePet] {
    type Result = Unit
    type ErrorData = None.type

    val method = "create_pets"
    implicit val format = Json.format[CreatePet]
  }

  case object NoSuchPet extends FeedPetError {
    def writer[Js] = Writer.empty[Js, NoSuchPet.type]
    implicit def registry[Js] =
      ErrorRegistry[Js, NoSuchPet.type](Map(404 -> Reader.forObject(this)))
  }

  case class GetPet(petId: Int)
  implicit object GetPet extends IdMethodDefinition[GetPet] {
    type Result = Pet
    type ErrorData = NoSuchPet.type

    val method = "get_pet"
    implicit val format = Json.format[GetPet]
  }

  case object PetAsleep extends FeedPetError {
    implicit def registry[Js] =
      ErrorRegistry[Js, PetAsleep.type](Map(109 -> Reader.forObject(this)))

    implicit def writer[Js] = Writer.empty[Js, PetAsleep.type]
  }

  sealed trait FeedPetError
  object FeedPetError {
    implicit def registry[Js]: ErrorRegistry[Js, FeedPetError] =
      ErrorRegistry.combine(PetAsleep.registry, NoSuchPet.registry)

    implicit def writer[Js]: Writer[Js, FeedPetError] =
      Writer.combine(PetAsleep.writer, NoSuchPet.writer)
  }

  case class FeedPet(petId: Int)
  implicit object FeedPet extends IdMethodDefinition[FeedPet] {
    type Result = Unit
    type ErrorData = FeedPetError

    val method = "feed_pet"
    implicit val format = Json.format[FeedPet]
  }

  trait PetStoreService {
    def listPets(limit: Option[Int]): Future[Option[Seq[Pet]]]
    def createPet(newPetName: String, newPetTag: Option[String]): Future[Unit]
    def getPet(petId: Int): Future[Option[Pet]]
    def feedPet(petId: Int): Future[Either[FeedPetError, Unit]]
  }

  class PetStoreServer extends PetStoreService {
    val serialId = new AtomicInteger(0)
    val pets = TrieMap.empty[Int, Pet]

    def listPets(limit: Option[Int]) =
      Future.successful(
        Some(
          pets.values
            .take(
              limit
                .filter(_ <= 100)
                .getOrElse(100)
            )
            .toList
        )
      )

    def createPet(newPetName: String, newPetTag: Option[String]) =
      Future.successful {
        val id = serialId.incrementAndGet
        pets.put(id, Pet(id, newPetName, newPetTag))
      }

    def getPet(petId: Int) = Future.successful(pets.get(petId))

    def feedPet(petId: Int): Future[Either[FeedPetError, Unit]] =
      if (!pets.contains(petId)) Future.successful(Left(NoSuchPet))
      else if (Random.nextBoolean) Future.successful(Left(PetAsleep))
      else Future.successful(Right(()))
  }
}
