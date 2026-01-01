package pages

import org.scalajs.dom.Event
import org.scalajs.dom.raw.HTMLElement
import base.Logging.* 
import comps.Sidebar


import shared.model.AppError

// pagesMap maps page names to page objects   
val pagesMap = List(pages.Home, Auth, Console, PgError,
                    ChatExample, 
                    UseCase2, UseCase31, UseCase32, UseCase41, UseCase42,
                    UseCase511, UseCase512, UseCase52, UseCase53)
                    .map(pg => pg.name -> pg).toMap

enum PageIds:
  case HomePid, AuthPid, ConsolePid, PgErrorPid, ChatExamplePid,
       UseCase2Pid, UseCase31Pid, UseCase32Pid,
       UseCase41Pid, UseCase42Pid,
       UseCase511Pid, UseCase512Pid, UseCase52Pid, UseCase53Pid                 


def loadPage(pageName: String, param: String): Unit =
  error(s"loadPage -> ${pagesMap.mkString(":")}")
  try
    debug(s"loadPage -> pageName:${pageName} param:${param}")
    if pagesMap(pageName).render(param) then
      Sidebar.setNavLink(pageName)
    else   
      error(s"loadPage -> page:${pageName} param:${param}")
  catch
    case e: Exception => error(s"loadPage -> page:${pageName} param:${param} not found")


abstract class BasePage extends comps.CompBase:
  def event(elem: HTMLElement, event: Event): Unit = {}
