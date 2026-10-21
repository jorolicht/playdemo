package pages

import base.*
import base.Messages.gM

/**
 * Hinweisseite, wenn auf der aktuellen WordPress-Seite kein Turnier angezeigt werden kann.
 *
 * Parameter:
 *  - "comingSoon": eingebettetes Plugin, Seite ohne Turnier, Benutzer ohne Schreibrechte
 *  - "notFound":   Full Web Site, Seite (nicht Homepage) ohne gültiges Turnier
 */
object TourneyNotice extends BasePage with JsWrapper:
  def name = PageNameTyp("TourneyNotice")

  /**
   * Rendert die Hinweisseite.
   *
   * @param param Art des Hinweises ("comingSoon" oder "notFound").
   * @return Boolean true on render.
   */
  def render(param: String = ""): Boolean =
    if (param == "comingSoon")
      setMain(cviews.pages.html.TourneyNotice("bi-hourglass-split", gM("tourneyNotice.comingSoonTitle"), gM("tourneyNotice.comingSoonInfo")))
    else
      setMain(cviews.pages.html.TourneyNotice("bi-exclamation-triangle", gM("tourneyNotice.notFoundTitle"), gM("tourneyNotice.notFoundInfo")))
    true
