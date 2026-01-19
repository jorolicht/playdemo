package shared

// import shared.PageTypes.PageId
import shared.DomTypes.HtmlId
import shared.DomTypes.genId
import shared.BoxValueTypes.BoxValue


object BoxValues:
  val Cancel:   BoxValue = BoxValue.myName
  val Ok:       BoxValue = BoxValue.myName
  val Abort:    BoxValue = BoxValue.myName
  val Retry:    BoxValue = BoxValue.myName
  val Ignore:   BoxValue = BoxValue.myName
  val Yes:      BoxValue = BoxValue.myName
  val No:       BoxValue = BoxValue.myName
  val Close:    BoxValue = BoxValue.myName  


object MainIds:
  final val name = "Main"
  val WordpressId : HtmlId = genId(name)
  val NavbarId    : HtmlId = genId(name)
  val ParamId     : HtmlId = genId(name)
  val ContentId   : HtmlId = genId(name)
  val SidebarId   : HtmlId = genId(name)
  val FooterId    : HtmlId = genId(name)
  val JScriptId   : HtmlId = genId(name)


object AuthIds:
  final val name = "Auth"  
  val LoginContentId            : HtmlId = genId(name)
  val DoForgotId                : HtmlId = genId(name)
  val DoLoginId                 : HtmlId = genId(name)
  val DoRegisterId              : HtmlId = genId(name)
  val PasswordId                : HtmlId = genId(name)
  val EmailId                   : HtmlId = genId(name)
  val AuthContentId             : HtmlId = genId(name)
