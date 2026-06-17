package addon

import shared.model.*
import shared.basic.*
import services.{TourneyDB, CompetitionDB}
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.collection.mutable.ArrayBuffer
import scala.util.control.NonFatal

/**
 * TestCompetition provides addon console commands to test Competition functionality.
 */
object TestCompetition:

  def exec(group: String, number: Int, param: String): Future[Either[AppError, String]] =
    number match
      case 1 => testCompetition_add(group, number, param)
      case 2 => testCompetition_delete(group, number, param)
      case 3 => testCompetition_list(group, number, param)
      case 4 => testCompetition_sync(group, number, param)
      case 5 => testCompetition_addPants(group, number, param)
      case 6 => testCompetition_jsonDecode(group, number, param)
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

    TourneyDB.tourney.addCompetition(name, typ, CompCategory.TT, startDate) match
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
      TourneyDB.tourney.deleteCompetition(id) match
        case Left(err) =>
          addOutput(s"Error deleting competition ID $param: ${err.msg}")
          Future(Left(err))
        case Right(_) =>
          addOutput(s"Deleted (soft delete) competition ID $param successfully")
          Future(Right(s"FINISHED: ${group}-Test:${number}"))
    catch
      case _: Exception =>
        addOutput(s"Invalid ID: $param")
        Future(Left(AppError("invalid.id")))

  /**
   * Test 3: List competitions.
   */
  def testCompetition_list(group: String, number: Int, param: String): Future[Either[AppError, String]] =
    val activeComps = TourneyDB.tourney.competitions.filter(c => c != null)
    addOutput(s"Competitions in DB (${activeComps.length}):")
    activeComps.foreach { c =>
      val pantsInfo = if (c.pants1Stage.isEmpty) "no participants" else s"${c.pants1Stage.length} participants"
      addOutput(s"- [${c.id.value}] ${c.name} (Type: ${c.typ}, Date: ${c.startDate}, Pants: $pantsInfo, Deleted: ${c.deleted}, Version: ${c.version})")
      c.pants1Stage.take(3).foreach { p =>
        addOutput(s"  * ${p.name} (${p.club})")
      }
      if (c.pants1Stage.length > 3) addOutput("  * ...")
    }
    Future(Right(s"FINISHED: ${group}-Test:${number}"))

  /**
   * Test 4: Sync competitions with server.
   */
  def testCompetition_sync(group: String, number: Int, param: String): Future[Either[AppError, String]] =
    addOutput(s"Syncing competitions...")
    TourneyDB.tourney.syncCompetitions()
    Future.successful(Right(s"FINISHED: ${group}-Test:${number}"))

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
      val t = TourneyDB.tourney
      
      if (compIdx < 0 || compIdx >= 64 || t.competitions(compIdx) == null) {
         addOutput(s"Competition ID ${id.value} not found.")
         return Future(Left(AppError("competition.notFound")))
      }

      val comp = t.competitions(compIdx)
      val newPants = parts.drop(1).map { pStr =>
        val pParts = pStr.split(";")
        val name = pParts(0).trim
        val club = if (pParts.length > 1) pParts(1).trim else ""
        Pant(
          id = SNO.fromString(s"P${comp.pants1Stage.length + 1}"),
          name = name,
          club = club,
          status = PantStatus.REDY
        )
      }

      val updatedComp = comp.copy()
      updatedComp.pants1Stage ++= newPants
      
      t.updateCompetition(updatedComp) match
        case Left(err) =>
          addOutput(s"Error updating competition: ${err.msg}")
          Future(Left(err))
        case Right(c) =>
          addOutput(s"Added ${newPants.length} participants to competition ${c.name} (ID: ${c.id.value}, Total: ${c.pants1Stage.length}, Version: ${c.version})")
          Future(Right(s"FINISHED: ${group}-Test:${number}"))

    catch
      case _: Exception =>
        addOutput(s"Invalid parameters: $param")
        Future(Left(AppError("invalid.param")))

  /**
   * Test 6: Test JSON decoding of a Competition object.
   */
  def testCompetition_jsonDecode(group: String, number: Int, param: String): Future[Either[AppError, String]] =
    import shared.basic.Pickle.*
    
    val json = """{"id":1,"version":4,"name":"Wettbewerb 1","typ":"SINGLE","category":"TT","startDate":"2026-04-14","status":"RUN","startStage":null,"activ":true,"webRegister":true,"lowLevel":0,"upperLevel":2500,"cttInfo":{"ageGroup":"Herren","ratingRemark":"A-Klasse","ratingLowLevel":0,"ratingUpperLevel":2500,"sex":1,"maxPersons":64,"entryFee":"15,00 \u20ac","ageFrom":"","ageTo":"","preliminaryRoundMode":"Gruppen","finalRoundMode":"KO-System","manualFinalRankings":false},"pants":[],"deleted":false}"""
    
    addOutput(s"Starting JSON decode test...")
    try
      val comp = read[Competition](json)
      addOutput(s"Successfully decoded competition: ${comp.name} (ID: ${comp.id.value})")
      addOutput(s"- Typ: ${comp.typ}")
      addOutput(s"- Status: ${comp.status}")
      addOutput(s"- StartDate: ${comp.startDate}")
      addOutput(s"- AgeGroup: ${comp.cttInfo.map(_.ageGroup).getOrElse("N/A")}")
      addOutput(s"- EntryFee: ${comp.cttInfo.map(_.entryFee).getOrElse("N/A")}")
      Future(Right(s"FINISHED: ${group}-Test:${number}"))
    catch
      case ex: Throwable =>
        val msg = s"Failed to decode competition: ${ex.getClass.getName}: ${ex.getMessage}"
        addOutput(msg)
        println(msg)
        ex.printStackTrace()
        Future(Left(AppError("decode.failed", msg)))
