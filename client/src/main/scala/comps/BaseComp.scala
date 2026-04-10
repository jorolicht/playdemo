package comps

import base.Messages
import base.Logging
import sourcecode.FullName

import shared.basic.AppError
import shared.DomTypes.HtmlId
import shared.DomTypes.genId
import base.JsWrapper

val compsMap = List(Navbar, Sidebar).map(c => c.name -> c).toMap

abstract class BaseComp(using fn: FullName):
  export Logging.debug
  export Logging.error
  export Logging.warn
  export Logging.info
  export shared.PageNameTyp.PageName
  export shared.PageNameTyp
  export shared.DomTypes.HtmlId
  export shared.DomTypes.genId
  export shared.basic.AppError

  def name: PageName
  def render(param: String = ""): Boolean
  def handleEvent(elem: org.scalajs.dom.raw.HTMLElement, event: org.scalajs.dom.Event): Unit = {}
  
  def id(name: HtmlId) = s"id=${name.id}"
  def gM(key: String, inserts: String*)  = 
    if key.startsWith("+") then 
      Messages.getMsg(s"${name}.${key.drop(1)}", inserts*)
    else  
      Messages.getMsg(key, inserts*)
  def getErr(err: AppError) = Messages.getErr(err)