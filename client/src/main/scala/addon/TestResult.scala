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
    genDemoResults() match
      case Left(errMsg) =>
        addOutput(errMsg)
        Future(Left(AppError(errMsg)))
      case Right(msg) =>
        addOutput(msg)
        Future.successful(Right(s"FINISHED: ${group}-Test:${number}"))

  /**
   * Generiert Demo-Ergebnisse für die ausgewählte Stage (wie der Button "DEMO-Ergebnisse"); auch als globale
   * Funktion `genDemoResults()` in der Browser-Konsole verfügbar (nur mit APP_ADDON=true).
   *
   * @return Erfolgsmeldung, Left mit Fehlermeldung außerhalb von StageInput bzw. ohne ausgewählte Stage
   */
  def genDemoResults(): Either[String, String] =
    if (dom.window.sessionStorage.getItem("tourney_last_page") != "StageInput")
      Left("Fehler: Sie müssen sich im UseCase StageInput befinden!")
    else if (Global.currentSelection.stage.isEmpty)
      Left("Fehler: Keine Stage ausgewählt!")
    else
      StageInput.setRandomScores()
      Right("Demo-Ergebnisse erfolgreich eingetragen.")
