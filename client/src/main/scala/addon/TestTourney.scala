package addon

import shared.model.*
import shared.basic.*
import services.*
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

/**
 * TestTourney provides addon console commands to test TourneyDB functionality.
 */
object TestTourney:

  def exec(group: String, number: Int, param: String): Future[Either[AppError, String]] =
    number match
      case 1 => testTourney_update(group, number, param)
      case 2 => testTourney_load(group, number, param)
      case 3 => testTourney_sync(group, number, param)
      case 4 => testTourney_init(group, number, param)
      case _ =>
        addOutput(s"FAILED: ${group}-Test:${number} param:${param} unknown test number")
        Future(Left(AppError("unknown test number")))

  /**
   * Test 1: Update tournament name. Param: "newName"
   */
  def testTourney_update(group: String, number: Int, param: String): Future[Either[AppError, String]] =
    val name = if (param.isEmpty) "Test Tournament" else param
    val current = TourneyDB.tourney.getOrElse(
      Tourney(
        0, // id
        name = name,
        organizer = "Test Org",
        startDate = 20260101,
        endDate = 20260101,
        ident = "123",
        version = 0,
        typ = TourneyTyp.TableTennis
      )
    )
    val updated = current.copy(name = name)
    TourneyDB.update(updated)
    addOutput(s"Updated tournament name to: ${updated.name}. Sync triggered.")
    Future(Right(s"FINISHED: ${group}-Test:${number}"))

  /**
   * Test 2: Load tournament data.
   */
  def testTourney_load(group: String, number: Int, param: String): Future[Either[AppError, String]] =
    TourneyDB.load().map {
      case Left(err) =>
        addOutput(s"Error loading tournament: ${err.msg}")
        Left(err)
      case Right(ver) =>
        val info = TourneyDB.tourney.map(_.name).getOrElse("No tournament loaded")
        addOutput(s"Tournament loaded: $info (Version: $ver)")
        Right(s"FINISHED: ${group}-Test:${number}")
    }

  /**
   * Test 3: Sync tournament data.
   */
  def testTourney_sync(group: String, number: Int, param: String): Future[Either[AppError, String]] =
    addOutput("Syncing tournament data...")
    TourneyDB.sync().map {
      case Left(err) =>
        addOutput(s"Error syncing tournament: ${err.msg}")
        Left(err)
      case Right(_) =>
        addOutput(s"Tournament synced successfully. New version: ${TourneyDB.version}")
        Right(s"FINISHED: ${group}-Test:${number}")
    }

  /**
   * Test 4: Initialize all data from server.
   */
  def testTourney_init(group: String, number: Int, param: String): Future[Either[AppError, String]] =
    addOutput("Initialisiere alle Datenbanken vom Server...")
    TourneyDB.init().map {
      case Left(err) =>
        addOutput(s"Fehler bei der Initialisierung: ${err.msgCode} (${err.in1})")
        Left(err)
      case Right(_) =>
        addOutput("Alle Daten erfolgreich geladen:")
        addOutput(s"- Turnier: ${TourneyDB.tourney.map(_.name).getOrElse("keines")}")
        addOutput(s"- Wettbewerbe: ${CompetitionDB.competitions.count(_ != null)}")
        addOutput(s"- Vereine: ${ClubDB.clubs.length}")
        addOutput(s"- Spieler: ${PlayerDB.players.length}")
        addOutput(s"- Runden: ${RoundDB.rounds.count(_ != null)}")
        Right(s"FINISHED: ${group}-Test:${number}")
    }
