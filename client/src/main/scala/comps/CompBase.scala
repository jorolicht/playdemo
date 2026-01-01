package comps

import base.Messages
import base.Logging
import sourcecode.FullName

import shared.model.AppError


abstract class CompBase(using fn: FullName):
  def name: String = fn.value.split('.').last
  export Logging.debug
  export Logging.error
  export Logging.warn
  export Logging.info

  def render(param: String = ""): Boolean

  def gM(key: String, inserts: String*)  = 
    if key.startsWith("+") then 
      Messages.getMsg(s"${name}.${key.drop(1)}", inserts*)
    else  
      Messages.getMsg(key, inserts*)
  def getErr(err: AppError) = Messages.getErr(err)