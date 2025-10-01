package usecases

import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement
import java.time.LocalDate

import cviews.usecases.html
import shared.model._
import services._
import base._
import upickle.default._
import scala.concurrent.ExecutionContext.Implicits.global

object Tourney extends UseCase with JsWrapper with ComWrapper:

  def render(param: String = ""): Boolean =
    setMain(html.Tourney())
    true

  override def event(elem: HTMLElement, event: dom.Event): Unit =
    elem.id match
      case "tourney-submit" =>
        val tourney = TournBase(
          name = getInput(gE("tourney-name")),
          organizer = getInput(gE("tourney-organizer")),
          orgDir = getInput(gE("tourney-orgDir")),
          startDate = LocalDate.parse(getInput(gE("tourney-startDate"))),
          endDate = LocalDate.parse(getInput(gE("tourney-endDate"))),
          ident = getInput(gE("tourney-ident")),
          typ = TourneyTyp.fromInt(getInput[String](gE("tourney-typ")).toInt),
          privat = getInput(gE("tourney-privat"), false),
          contact = Contact(
            lastname = getInput(gE("contact-lastname")),
            firstname = getInput(gE("contact-firstname")),
            phone = getInput(gE("contact-phone")),
            email = getInput(gE("contact-email"))
          ),
          address = Address(
            description = getInput(gE("address-description")),
            country = getInput(gE("address-country")),
            zip = getInput(gE("address-zip")),
            city = getInput(gE("address-city")),
            street = getInput(gE("address-street"))
          )
        )
        sendTourney(tourney)
      case _ =>
        error(s"event -> invalid id/key: ${elem.id}")

  def sendTourney(tourney: TournBase): Unit =
    ajaxPost[String]("/tourney", List(), write(tourney)).map {
      case Left(err) =>
        println(s"Error: $err")
      case Right(res) =>
        println(s"Success: $res")
    }
