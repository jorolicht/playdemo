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
      case 5 => testTourney_apiCreate(group, number, param)
      case 6 => testTourney_createFull(group, number, param)
      case _ =>
        addOutput(s"FAILED: ${group}-Test:${number} param:${param} unknown test number")
        Future.successful(Left(AppError("unknown test number")))

  /**
   * Test 1: Update tournament name. Param: "newName"
   */
  def testTourney_update(group: String, number: Int, param: String): Future[Either[AppError, String]] =
    val name = if (param.isEmpty) "Test Tournament" else param
    val current = if (TourneyDB.tourney.id != 0) TourneyDB.tourney else
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
    val updated = current.copy(name = name)
    TourneyDB.update(updated)
    addOutput(s"Updated tournament name to: ${updated.name}. Sync triggered.")
    Future.successful(Right(s"FINISHED: ${group}-Test:${number}"))

  /**
   * Test 2: Load tournament data.
   */
  def testTourney_load(group: String, number: Int, param: String): Future[Either[AppError, String]] =
    val id = try param.toInt catch { case _: Exception => TourneyDB.tourney.id }
    TourneyDB.load(id).map {
      case Left(err) =>
        addOutput(s"Error loading tournament: ${err.msg}")
        Left(err)
      case Right(ver) =>
        val info = if (TourneyDB.tourney.id != 0) TourneyDB.tourney.name else "No tournament loaded"
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
    val id = try param.toInt catch { case _: Exception => TourneyDB.tourney.id }
    TourneyDB.init(id).map {
      case Left(err) =>
        addOutput(s"Fehler bei der Initialisierung: ${err.msgCode} (${err.in1})")
        Left(err)
      case Right(_) =>
        addOutput("Alle Daten erfolgreich geladen:")
        addOutput(s"- Turnier: ${if (TourneyDB.tourney.id != 0) TourneyDB.tourney.name else "keines"}")
        addOutput(s"- Wettbewerbe: ${CompetitionDB.competitions.count(_ != null)}")
        addOutput(s"- Vereine: ${TourneyDB.tourney.clubs.length}")
        addOutput(s"- Spieler: ${TourneyDB.tourney.players.length}")
        addOutput(s"- Runden: ${RoundDB.rounds.count(_ != null)}")
        Right(s"FINISHED: ${group}-Test:${number}")
    }

  /**
   * Test 5: Create a new tournament post using the API.
   * Param: "name,startDate" (e.g., "My Tourney,20260515")
   */
  def testTourney_apiCreate(group: String, number: Int, param: String): Future[Either[AppError, String]] =
    val cleanParam = param.stripPrefix("\"").stripSuffix("\"")
    val parts = cleanParam.split(",")
    val name = if (parts.length > 0 && parts(0).nonEmpty) parts(0).trim else "Freisinger Meisterschaften"
    val dateStr = if (parts.length > 1 && parts(1).nonEmpty) parts(1).trim else "20220402"
    val date = try dateStr.toInt catch { case _: Exception => 20220402 }
    
    val t = Tourney(
      id = 0,
      name = name,
      organizer = "TTV Freising",
      startDate = date,
      endDate = date,
      ident = "i36eIvYBUqxYW1OUCKu2pT5v50i4mxM0",
      typ = TourneyTyp.TableTennis
    )

    addOutput(s"Creating tournament via API: ${t.name} ($date)...")
    TourneyDB.apiCreate(t).map {
      case Left(err) =>
        addOutput(s"Error creating tournament: ${err.msgCode}")
        Left(err)
      case Right(slug) =>
        addOutput(s"Tournament created successfully! Slug: $slug")
        Right(s"FINISHED: ${group}-Test:${number}")
    }

  /**
   * Test 6: Create a full tournament post using all fields from ClickTTDemo.xml.
   * Param: "name,startDate"
   */
  def testTourney_createFull(group: String, number: Int, param: String): Future[Either[AppError, String]] =
    val cleanParam = param.stripPrefix("\"").stripSuffix("\"")
    val parts = cleanParam.split(",")
    val name = if (parts.length > 0 && parts(0).nonEmpty) parts(0).trim else "100. Internationale Freisinger Meisterschaften"
    val dateStr = if (parts.length > 1 && parts(1).nonEmpty) parts(1).trim else "20220402"
    val date = try dateStr.toInt catch { case _: Exception => 20220402 }

    val t = Tourney(
      id = 0,
      name = name,
      organizer = "TTV Freising",
      startDate = date,
      endDate = date,
      ident = "i36eIvYBUqxYW1OUCKu2pT5v50i4mxM0",
      typ = TourneyTyp.TableTennis,
      address = Some(Address("Luitpoldhalle", "Luitpoldanlage 1", "85356", "Freising", "DE"))
    )

    addOutput(s"Creating FULL tournament via API: ${t.name}...")
    TourneyDB.apiCreate(t).map {
      case Left(err) =>
        addOutput(s"Error creating tournament: ${err.msgCode}")
        Left(err)
      case Right(slug) =>
        addOutput(s"Full Tournament created! Slug: $slug")
        Right(s"FINISHED: ${group}-Test:${number}")
    }
