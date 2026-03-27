package addon

import shared.model.*
import shared.basic.*
import services.CompetitionDB
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

/**
 * TestCompetition provides addon console commands to test CompetitionDB functionality.
 */
object TestCompetition:

  def exec(group: String, number: Int, param: String): Future[Either[AppError, String]] =
    number match
      case 1 => testCompetition_add(group, number, param)
      case 2 => testCompetition_delete(group, number, param)
      case 3 => testCompetition_list(group, number, param)
      case 4 => testCompetition_sync(group, number, param)
      case _ =>
        addOutput(s"FAILED: ${group}-Test:${number} param:${param} unknown test number")
        Future(Left(AppError("unknown test number")))

  /**
   * Test 1: Add a competition. Param format: "name,type,startDate"
   * Example: "Herren A,single,2026-06-01"
   */
  def testCompetition_add(group: String, number: Int, param: String): Future[Either[AppError, String]] =
    val parts = param.split(",")
    if (parts.length < 3) {
      addOutput("Param must be 'name,type,startDate'")
      return Future(Left(AppError("invalid.param")))
    }

    val name = parts(0).trim
    val typStr = parts(1).trim
    val startDate = parts(2).trim
    val typ = CompTyp.fromString(typStr)

    CompetitionDB.add(name, typ, startDate) match
      case Left(err) =>
        addOutput(s"Error adding competition '$name': ${err.msg}")
        Future(Left(err))
      case Right(comp) =>
        addOutput(s"Added competition: ${comp.name} (ID: ${comp.id.value}, Type: ${comp.typ}, Date: ${comp.startDate})")
        Future(Right(s"FINISHED: ${group}-Test:${number}"))

  /**
   * Test 2: Delete a competition. Param: compId
   */
  def testCompetition_delete(group: String, number: Int, param: String): Future[Either[AppError, String]] =
    try
      val id = CompId.fromInt(param.toInt)
      CompetitionDB.delete(id) match
        case Left(err) =>
          addOutput(s"Error deleting competition ID $param: ${err.msg}")
          Future(Left(err))
        case Right(comp) =>
          addOutput(s"Deleted (soft delete) competition: ${comp.name} (ID: ${comp.id.value}, Deleted: ${comp.deleted})")
          Future(Right(s"FINISHED: ${group}-Test:${number}"))
    catch
      case _: Exception =>
        addOutput(s"Invalid ID: $param")
        Future(Left(AppError("invalid.id")))

  /**
   * Test 3: List competitions.
   */
  def testCompetition_list(group: String, number: Int, param: String): Future[Either[AppError, String]] =
    val activeComps = CompetitionDB.competitions.filter(c => c != null)
    addOutput(s"Competitions in DB (${activeComps.length}):")
    activeComps.foreach { c =>
      addOutput(s"- [${c.id.value}] ${c.name} (Type: ${c.typ}, Date: ${c.startDate}, Deleted: ${c.deleted})")
    }
    Future(Right(s"FINISHED: ${group}-Test:${number}"))

  /**
   * Test 4: Sync competitions with server.
   */
  def testCompetition_sync(group: String, number: Int, param: String): Future[Either[AppError, String]] =
    addOutput(s"Syncing competitions (${CompetitionDB.pendingEvents.length} pending events)...")
    CompetitionDB.sync().map {
      case Left(err) =>
        addOutput(s"Error syncing competitions: ${err.msg}")
        Left(err)
      case Right(_) =>
        addOutput(s"Competitions synced successfully. New timestamp: ${CompetitionDB.timestamp}")
        Right(s"FINISHED: ${group}-Test:${number}")
    }
