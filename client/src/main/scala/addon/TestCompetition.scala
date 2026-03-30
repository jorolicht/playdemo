package addon

import shared.model.*
import shared.basic.*
import services.CompetitionDB
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.collection.mutable.ArrayBuffer

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
      case 5 => testCompetition_addPants(group, number, param)
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
        addOutput(s"Added competition: ${comp.name} (ID: ${comp.id.value}, Type: ${comp.typ}, Date: ${comp.startDate}, Version: ${comp.version})")
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
          addOutput(s"Deleted (soft delete) competition: ${comp.name} (ID: ${comp.id.value}, Deleted: ${comp.deleted}, Version: ${comp.version})")
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
      val pantsInfo = if (c.pants.isEmpty) "no participants" else s"${c.pants.length} participants"
      addOutput(s"- [${c.id.value}] ${c.name} (Type: ${c.typ}, Date: ${c.startDate}, Pants: $pantsInfo, Deleted: ${c.deleted}, Version: ${c.version})")
      c.pants.take(3).foreach { p =>
        addOutput(s"  * ${p.name} (${p.club})")
      }
      if (c.pants.length > 3) addOutput("  * ...")
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
        addOutput(s"Competitions synced successfully.")
        Right(s"FINISHED: ${group}-Test:${number}")
    }

  /**
   * Test 5: Add participants to competition. Param: "compId,name1;club1,name2;club2,..."
   * Example: "1,Max Mustermann;TTC Test,Erika Musterfrau;TTV Beispiel"
   */
  def testCompetition_addPants(group: String, number: Int, param: String): Future[Either[AppError, String]] =
    val parts = param.split(",")
    if (parts.length < 2) {
      addOutput("Param must be 'compId,name1;club1,name2;club2,...'")
      return Future(Left(AppError("invalid.param")))
    }

    try
      val id = CompId.fromInt(parts(0).trim.toInt)
      val compIdx = id.value - 1
      
      if (compIdx < 0 || compIdx >= CompetitionDB.MaxComps || CompetitionDB.competitions(compIdx) == null) {
         addOutput(s"Competition ID ${id.value} not found.")
         return Future(Left(AppError("competition.notFound")))
      }

      val comp = CompetitionDB.competitions(compIdx)
      val newPants = parts.drop(1).map { pStr =>
        val pParts = pStr.split(";")
        val name = pParts(0).trim
        val club = if (pParts.length > 1) pParts(1).trim else ""
        Pant(
          id = SNO.fromString(s"P${comp.pants.length + 1}"),
          name = name,
          club = club,
          status = PantStatus.REDY
        )
      }

      val updatedComp = comp.copy()
      updatedComp.pants ++= newPants
      
      CompetitionDB.update(updatedComp) match
        case Left(err) =>
          addOutput(s"Error updating competition: ${err.msg}")
          Future(Left(err))
        case Right(c) =>
          addOutput(s"Added ${newPants.length} participants to competition ${c.name} (ID: ${c.id.value}, Total: ${c.pants.length}, Version: ${c.version})")
          Future(Right(s"FINISHED: ${group}-Test:${number}"))

    catch
      case _: Exception =>
        addOutput(s"Invalid parameters: $param")
        Future(Left(AppError("invalid.param")))
