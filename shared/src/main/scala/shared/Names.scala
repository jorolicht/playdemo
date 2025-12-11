package shared


trait NamedId {
  // Diese Methode liefert den String-Namen des Enum-Falls (z.B. "AppParam").
  // Sie wird von jedem Enum-Fall überschrieben, der dieses Trait verwendet.
  def name: String
}


enum UCsGlobal extends NamedId:
  case Home, Auth, ChatExample, UseCase1Sub2, UseCase2, UseCase31, UseCase32, 
       UseCase41, UseCase42, UseCase511, UseCase512, UseCase52, UseCase53

  override def name: String = this.toString 
  

enum IdsGlobal extends NamedId:
  case AppParamId, AppContentId, MessagesId, JavascriptEnabledInfoId, ToggleSidebarId, SidebarId, NavbarId 
  override def name: String = IdsGlobal.Prefix + this.toString  

object IdsGlobal:
  import scala.util.Try
  final val Prefix: String = "IdsGlobal"
  def fromId(id: String): Option[IdsGlobal] = 
    if (id.startsWith(Prefix)) then Try(IdsGlobal.valueOf(id.stripPrefix(Prefix))).toOption else None


enum IdsMsgbox extends NamedId:
  case LoadId, ModalId, TitleId, BodyId, CloseId 
  override def name: String = "IdsMsgbox" + this.toString  

enum BtnMsgbox extends NamedId:
  case Cancel, Ok, Abort, Retry, Ignore, Yes, No, Close
  def msgCode = "btn.msgbox." + this.toString.toLowerCase
  def name    = "BtnMsgbox" + this.toString


enum IdsConsole extends NamedId:
  case ConsoleId, ShowId, ClickId
  override def name: String = IdsConsole.Prefix + this.toString  

object IdsConsole:
  import scala.util.Try
  final val Prefix: String = "IdsConsole"
  def fromId(id: String): Option[IdsConsole] = 
    if (id.startsWith(Prefix)) then  Try(IdsConsole.valueOf(id.stripPrefix(Prefix))).toOption else None  


enum IdsAuth extends NamedId:
  case  DoLoginId, DoLogoutId, DoForgotId, ShowLoginId, DoRegisterId,
        PasswordId, EmailId, LoggedInAsId, LoginInfoId, ContentId 
  override def name: String = "IdsAuth" + this.toString  

object IdsAuth:
  import scala.util.Try
  final val Prefix: String = "IdsAuth"
  def fromId(id: String): Option[IdsAuth] = 
    if (id.startsWith(Prefix)) then Try(IdsAuth.valueOf(id.stripPrefix(Prefix))).toOption else None    



enum IdsPrompt extends NamedId:
  case LoadId, ModalId, ResultId, ResultContentId, InputId,         
       CloseId, ClearId, ExecuteId, CancelId, ToggleId  
  override def name: String = IdsPrompt.Prefix + this.toString  

object IdsPrompt:
  import scala.util.Try
  final val Prefix: String = "IdsPrompt"
  def fromId(id: String): Option[IdsPrompt] = 
    if (id.startsWith(Prefix)) then  Try(IdsPrompt.valueOf(id.stripPrefix(Prefix))).toOption else None    


