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
   * Automatically schedules demo table assignments and random results for up to 3 ready matches,
   * then recursively checks and completes new playable matches until the entire stage is finished.
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
      case Some(initialStage) =>
        
        // Define recursive batch execution loop
        def runNextBatch(): Unit = {
          Global.currentSelection.stage match {
            case None =>
              addOutput("Automatisierung gestoppt: Keine Stage ausgewählt.")
            case Some(stage) =>
              val allMatches = stage.matches.toSeq
              val unfinishedMatches = allMatches.filter(m => !m.finished && m.result.trim.isEmpty && m.status != MEntry.MS_FIX)
              
              if (unfinishedMatches.isEmpty) {
                addOutput("Alle Spiele dieser Stage sind beendet!")
              } else {
                val readyMatches = allMatches.filter(m => m.status == MEntry.MS_READY && m.result.trim.isEmpty)
                
                if (readyMatches.isEmpty) {
                  // Check if there are matches currently running
                  val runningMatches = allMatches.filter(m => m.status == MEntry.MS_RUN && m.result.trim.isEmpty)
                  if (runningMatches.nonEmpty) {
                    addOutput("Warte auf laufende Spiele...")
                    // Check again in 1 second
                    dom.window.setTimeout(() => runNextBatch(), 1000)
                  } else {
                    addOutput("Automatisierung beendet: Keine weiteren spielbereiten Spiele vorhanden (weitere Runden ggf. blockiert oder beendet).")
                  }
                } else {
                  val matchesToRun = readyMatches.take(3)
                  addOutput(s"Starte nächste Charge: ${matchesToRun.length} spielbereite(s) Spiel(e)...")

                  var completedCount = 0

                  matchesToRun.foreach { m =>
                    val gameNo = m.gameNo
                    val tableVal = (1 + scala.util.Random.nextInt(12)).toString
                    
                    // 1. Assign table in DOM and dispatch change
                    val tableInput = dom.document.getElementById(s"table_$gameNo").asInstanceOf[dom.html.Input]
                    if (tableInput != null) {
                      tableInput.value = tableVal
                      tableInput.dispatchEvent(new dom.Event("change"))
                      addOutput(s"Spiel $gameNo: Tisch $tableVal zugewiesen.")
                    }

                    // 2. Schedule result entry after 1 second (1000 ms)
                    dom.window.setTimeout(() => {
                      val winSets = stage.noWinSets
                      var aWins = 0
                      var bWins = 0
                      var setIdx = 1
                      
                      while (aWins < winSets && bWins < winSets) {
                        val aWinsSet = scala.util.Random.nextBoolean()
                        val score = if (aWinsSet) {
                          aWins += 1
                          (3 + scala.util.Random.nextInt(6)).toString
                        } else {
                          bWins += 1
                          s"-${3 + scala.util.Random.nextInt(6)}"
                        }
                        
                        val setInput = dom.document.getElementById(s"input_${gameNo}_$setIdx").asInstanceOf[dom.html.Input]
                        if (setInput != null) {
                          setInput.value = score
                          setInput.dispatchEvent(new dom.Event("input"))
                        }
                        setIdx += 1
                      }

                      // Click save button (green checkmark)
                      val saveBtn = dom.document.getElementById(s"${StageInput.SaveMatchBtn.id}-$gameNo").asInstanceOf[dom.html.Button]
                      if (saveBtn != null) {
                        saveBtn.click()
                        addOutput(s"Spiel $gameNo: Ergebnis gespeichert.")
                      }

                      completedCount += 1
                      if (completedCount == matchesToRun.length) {
                        // Wait 800ms for AJAX and local selection updates to settle, then run next batch
                        dom.window.setTimeout(() => {
                          runNextBatch()
                        }, 800)
                      }
                    }, 1000)
                  }
                }
              }
          }
        }

        // Start the recursive waterfall
        runNextBatch()
        Future(Right("Automatischer Durchlauf gestartet"))
    }
