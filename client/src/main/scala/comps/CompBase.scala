package comps

import base.Messages
import base.Logging
import sourcecode.FullName

import shared.model.AppError
import shared.PageNameTyp.PageName
import shared.DomTypes.HtmlId

abstract class CompBase(using fn: FullName):
  export Logging.debug
  export Logging.error
  export Logging.warn
  export Logging.info

  def name: PageName
  def render(param: String = ""): Boolean
  
  def id(name: HtmlId) = s"id=${name.id}"
  def gM(key: String, inserts: String*)  = 
    if key.startsWith("+") then 
      Messages.getMsg(s"${name}.${key.drop(1)}", inserts*)
    else  
      Messages.getMsg(key, inserts*)
  def getErr(err: AppError) = Messages.getErr(err)