package shared

// import shared.PageTypes.PageId
import shared.DomTypes.HtmlId
import shared.DomTypes.genId
//import shared.BoxValueTypes.BoxValue


enum BoxButton(val value: String):
  case Cancel extends BoxButton("Cancel")
  case Ok     extends BoxButton("Ok")
  case Abort  extends BoxButton("Abort")
  case Retry  extends BoxButton("Retry")
  case Ignore extends BoxButton("Ignore")
  case Yes    extends BoxButton("Yes")
  case No     extends BoxButton("No")
  case Close  extends BoxButton("Close")

  def msgCode: String =
    s"btn.${value.toLowerCase}"

  def getId: HtmlId =
    HtmlId(s"BoxButton_$value")


object MainIds:
  final val name = "Main"
  val WordpressId  : HtmlId = genId(name)
  val DynContentId : HtmlId = genId(name)
  val NavbarId     : HtmlId = genId(name)
  val ContextHeaderId: HtmlId = genId(name)
  val ParamId      : HtmlId = genId(name)
  val ContentId    : HtmlId = genId(name)
  val SidebarId    : HtmlId = genId(name)
  val FooterId     : HtmlId = genId(name)
  val JScriptId    : HtmlId = genId(name)


object AuthIds:
  final val name = "Auth"  
  val LoginContentId            : HtmlId = genId(name)
  val DoForgotId                : HtmlId = genId(name)
  val DoLoginId                 : HtmlId = genId(name)
  val DoRegisterId              : HtmlId = genId(name)
  val PasswordId                : HtmlId = genId(name)
  val EmailId                   : HtmlId = genId(name)
  val AuthContentId             : HtmlId = genId(name)
