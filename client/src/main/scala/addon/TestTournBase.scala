package addon

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import java.time.{LocalTime, LocalDate, Clock}

import shared.model._
import base._
import upickle.default._

object TestTournBase:

  def exec(group: String, number: Int, param: String): Future[Either[AppError, String]] =
    number match
      case 1 => testPickle(group, number, param)
      case _ =>
        addOutput(s"FAILED: ${group}-Test:${number} param:${param} unknown test number")
        Future(Left(AppError("unknown test number")))

  // http://localhost:9555/main/Console?param=test_--group_tournbase_--number_1
  def testPickle(group: String, number: Int, param: String): Future[Either[AppError, String]] =
    val originalTournBase = TournBase(
      name = "Test Tournament",
      organizer = "Test Organizer",
      orgDir = "test-organizer",
      startDate = LocalDate.now(Clock.systemUTC()),
      endDate = LocalDate.now(Clock.systemUTC()).plusDays(1),
      ident = "test-ident",
      typ = TourneyTyp.TT,
      privat = false,
      contact = Contact("Lastname", "Firstname", "123456789", "test@test.com"),
      address = Address("Test Description", "Test Country", "12345", "Test City", "Test Street"),
      id = 1L
    )

    val json = write(originalTournBase)
    addOutput(s"Pickled TournBase: $json")

    val unpickledTournBase = read[TournBase](json)
    addOutput(s"Unpickled TournBase: $unpickledTournBase")

    if (originalTournBase == unpickledTournBase) {
      addOutput("SUCCESS: Original and unpickled TournBase are equal.")
      Future(Right(s"FINISHED: ${group}-Test:${number} param:${param}"))
    } else {
      addOutput("FAILURE: Original and unpickled TournBase are NOT equal.")
      Future(Left(AppError("Pickle test failed")))
    }
