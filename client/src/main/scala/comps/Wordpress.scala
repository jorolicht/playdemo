package comps

import shared.DomTypes.HtmlId


object Wordpress extends CompBase:

  val ToggleSidebarId: HtmlId = HtmlId.fromName(name)
  val AuthContentId: HtmlId = HtmlId.fromName(name)
  val SidebarContentId: HtmlId = HtmlId.fromName(name)
  val AppContentId: HtmlId = HtmlId.fromName(name)

  def render(param: String = ""): Boolean = true

