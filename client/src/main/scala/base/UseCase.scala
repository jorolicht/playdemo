package base

import org.scalajs.dom.Event
import org.scalajs.dom.raw.HTMLElement

import shared.model.AppError

case class UseCaseParam(
  name:      String,
  gMP:       (String,Seq[String])=>String,
  gM:        (String,Seq[String])=>String
)  

/** UseCase - defines name and methods to get messages (with name as prefix)
 *            render method have to be defined
 */  
abstract class UseCase:
  val name = this.getClass.getSimpleName.split('$')(0)

  def render(param: String = ""): Boolean
  def event(elem: HTMLElement, event: Event): Unit = {}
  // get messages with/without prefix
  def gMP(key: String, inserts: String*) = Messages.getMsg(s"${name}.${key}", inserts*)
  def gM(key: String, inserts: String*)  = Messages.getMsg(key, inserts*)
  def getErr(err: AppError) = Messages.getErr(err)

  given ucp:UseCaseParam = UseCaseParam(name, gMP, gM)
  