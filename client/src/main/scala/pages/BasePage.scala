package pages

import org.scalajs.dom.Event
import org.scalajs.dom.raw.HTMLElement
import base.Logging.* 
import comps.Sidebar


import shared.basic.AppError
import shared.PageNameTyp.PageName

// pagesMap maps page names to page objecpts   
val pagesMap = List(pages.Home, Auth, Console, PgError,
                    ChatExample, 
                    UseCase2, UseCase31, UseCase32, UseCase41, UseCase42,
                    UseCase511, UseCase512, UseCase52, UseCase53)
                    .map(pg => pg.name -> pg).toMap              


def loadPage(pageName: PageName, param: String): Unit =
  error(s"loadPage -> ${pagesMap.mkString(":")}")
  try
    debug(s"loadPage -> pageName:${pageName} param:${param}")
    if pagesMap(pageName).render(param) then
      Sidebar.setNavLink(pageName.value)
    else   
      error(s"loadPage -> page:${pageName} param:${param}")
  catch
    case e: Exception => error(s"loadPage -> page:${pageName} param:${param} not found")


abstract class BasePage extends comps.CompBase:
  def event(elem: HTMLElement, event: Event): Unit = {}
