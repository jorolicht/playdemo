package shared

// import shared.PageTypes.PageId
import shared.DomTypes.HtmlId
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
  val NavbarId    : HtmlId = HtmlId.fromName(name)
  val ParamId     : HtmlId = HtmlId.fromName(name)
  val ContentId   : HtmlId = HtmlId.fromName(name)
  val SidebarId   : HtmlId = HtmlId.fromName(name)
  val FooterId    : HtmlId = HtmlId.fromName(name)
  val JScriptId   : HtmlId = HtmlId.fromName(name)


object AuthIds:
  final val name = "Auth"  
  val LoginContentId            : HtmlId = HtmlId.fromName(name)
  val DoForgotId                : HtmlId = HtmlId.fromName(name)
  val DoLoginId                 : HtmlId = HtmlId.fromName(name)
  val DoRegisterId              : HtmlId = HtmlId.fromName(name)
  val PasswordId                : HtmlId = HtmlId.fromName(name)
  val EmailId                   : HtmlId = HtmlId.fromName(name)
  val AuthContentId             : HtmlId = HtmlId.fromName(name)


