package addon

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import org.scalajs.dom
import base.*
import shared.basic.AppError
import shared.model.*
import pages.Stage.StageInput

/**
 * Test group "result" for demo automation in the StageInput view.
 */
object TestResult extends JsWrapper:

  def exec(group: String, number: Int, param: String): Future[Either[AppError, String]] =
    number match 
      case 1 => testResult_genDemoResults(group, number, param)
      case _ => 
        addOutput(s"FAILED: ${group}-Test:${number} param:${param} unknown test number")
        Future(Left(AppError("unknown test number")))

  /**
   * Generiert Demo-Ergebnisse über StageInput.setRandomScores.
   *
   * @param group The test group name.
   * @param number The test number.
   * @param param Custom parameter string.
   */
  def testResult_genDemoResults(group: String, number: Int, param: String): Future[Either[AppError, String]] =
    // Verify we are in the StageInput page
    val lastPage = dom.window.sessionStorage.getItem("tourney_last_page")
    if (lastPage != "StageInput") {
      val errMsg = "Fehler: Sie müssen sich im UseCase StageInput befinden!"
      addOutput(errMsg)
      return Future(Left(AppError(errMsg)))
    }

    Global.currentSelection.stage match {
      case None =>
        val errMsg = "Fehler: Keine Stage ausgewählt!"
        addOutput(errMsg)
        Future(Left(AppError(errMsg)))
      case Some(_) =>
        StageInput.setRandomScores()
        addOutput("Demo-Ergebnisse erfolgreich eingetragen.")
        Future.successful(Right(s"FINISHED: ${group}-Test:${number}"))
    }
