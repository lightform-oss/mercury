package com.lightform.mercury.sample

import java.util.concurrent.atomic.AtomicInteger

import com.lightform.mercury.IdMethodDefinition
import com.lightform.mercury.IdMethodDefinition
import play.api.libs.json.Json

import scala.collection.concurrent.TrieMap
import scala.concurrent.Future

package object pets {
  case class Pet(id: Int, name: String, tag: Option[String])
  object Pet {
    implicit val format = Json.format[Pet]
  }

  case class ListPets(limit: Option[Int])
  implicit object ListPets extends IdMethodDefinition[ListPets] {
    type Result = Seq[Pet]
    type ErrorData = None.type

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

  case class GetPet(petId: Int)
  implicit object GetPet extends IdMethodDefinition[GetPet] {
    type Result = Pet
    type ErrorData = None.type

    val method = "get_pet"
    implicit val format = Json.format[GetPet]
  }

  trait PetStoreService {
    def listPets(limit: Option[Int]): Future[Option[Seq[Pet]]]
    def createPet(newPetName: String, newPetTag: Option[String]): Future[Unit]
    def getPet(petId: Int): Future[Option[Pet]]
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
  }
}
