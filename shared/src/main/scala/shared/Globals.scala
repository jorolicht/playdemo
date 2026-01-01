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


object GlobalIds:
  final val name = "GlobalIds" 
  val AppParamId                : HtmlId = HtmlId.fromName(name)
  val AppContentId              : HtmlId = HtmlId.fromName(name)
  val SidebarContentId          : HtmlId = HtmlId.fromName(name)
  val MessagesId                : HtmlId = HtmlId.fromName(name)
  val JavascriptEnabledInfoId   : HtmlId = HtmlId.fromName(name) 
  val FooterId                  : HtmlId = HtmlId.fromName(name)
  val DoLoginId                 : HtmlId = HtmlId.fromName(name)
  val DoLogoutId                : HtmlId = HtmlId.fromName(name)
  val DoForgotId                : HtmlId = HtmlId.fromName(name)
  val ShowLoginId               : HtmlId = HtmlId.fromName(name)
  val DoRegisterId              : HtmlId = HtmlId.fromName(name)
  val PasswordId                : HtmlId = HtmlId.fromName(name)
  val EmailId                   : HtmlId = HtmlId.fromName(name)
  val AuthContentId             : HtmlId = HtmlId.fromName(name) 

