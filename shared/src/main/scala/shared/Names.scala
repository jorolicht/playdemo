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
  

enum IdGlobal extends NamedId:
  case AppParamId, AppContentId, MessagesId, JavascriptEnabledInfoId, ToggleSidebarId, SidebarId, NavbarId, FooterId,
       DoLoginId, DoLogoutId, DoForgotId, ShowLoginId, DoRegisterId, PasswordId, EmailId, 
       LoggedInAsId, LoginInfoId, AuthContentId,
       ClickConsoleId

  override def name: String = IdGlobal.Prefix + "_" + this.toString  

object IdGlobal:
  import scala.util.Try
  final val Prefix: String = "IdGlobal"
  def fromId(id: String): Option[IdGlobal] = 
    if (id.startsWith(Prefix)) then Try(IdGlobal.valueOf(id.stripPrefix(Prefix))).toOption else None

 


