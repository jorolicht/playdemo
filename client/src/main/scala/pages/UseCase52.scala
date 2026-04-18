package pages

import org.scalajs.dom
import org.scalajs.dom.{FileReader, Event}
import base.Logging
import services.ClickTTParser

object UseCase52 extends BasePage with base.JsWrapper:
  def name = PageNameTyp("UseCase52") 
  
  def render(param: String = ""): Boolean = 
    setMain(s"""
      <div class='container mt-5'>
        <div class='card shadow-sm'>
          <div class='card-header bg-primary text-white'>
            <h5 class='mb-0'>ClickTT XML Import</h5>
          </div>
          <div class='card-body'>
            <div class='mb-3'>
              <label for='fileInput' class='form-label'>ClickTT XML-Datei auswählen</label>
              <input class='form-control' type='file' id='fileInput' accept='.xml'>
            </div>
            <div id='importResult' class='mt-3'></div>
          </div>
        </div>
      </div>
    """)

    val input = dom.document.getElementById("fileInput").asInstanceOf[dom.html.Input]
    if (input != null) {
      input.onchange = { (_: Event) =>
        if (input.files.length > 0) {
          val file = input.files.item(0)
          val reader = new FileReader()

          reader.onload = { (_: Event) =>
            val xmlString = reader.result.asInstanceOf[String]
            ClickTTParser.parse(xmlString) match {
              case Right(tournament) =>
                Logging.info(s"Successfully imported tournament: ${tournament.name}")
                dom.document.getElementById("importResult").innerHTML = s"""
                  <div class='alert alert-success'>
                    <strong>Erfolg!</strong> Turnier '${tournament.name}' wurde erfolgreich eingelesen.<br>
                    Anzahl Wettbewerbe: ${tournament.competitions.length}
                  </div>
                """
                println(s"Parsed Tournament: $tournament")
              case Left(err) =>
                Logging.error(s"Import failed: $err")
                dom.document.getElementById("importResult").innerHTML = s"""
                  <div class='alert alert-danger'>
                    <strong>Fehler!</strong> $err
                  </div>
                """
            }
          }
          reader.readAsText(file)
        }
      }
    }
    true
