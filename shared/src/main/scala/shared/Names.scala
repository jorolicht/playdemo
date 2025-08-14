package shared

// definition of global HTML id attributes
object Ids extends NameOf: 
  val Main_Content:String              = nameOf(Main_Content)
  val Main_Messages:String             = nameOf(Main_Messages)
  val Main_JavascriptEnableInfo:String = nameOf(Main_JavascriptEnableInfo)

  val Home_toggleSidebar:String        = nameOf(Home_toggleSidebar)
  val Home_Sidebar:String              = nameOf(Home_Sidebar)

  val Auth_doLogin:String              = nameOf(Auth_doLogin)
  val Auth_doLogout:String             = nameOf(Auth_doLogout)
  val Auth_doForgot:String             = nameOf(Auth_doForgot)
  val Auth_showLogin:String            = nameOf(Auth_showLogin)
  val Auth_doRegister:String           = nameOf(Auth_doRegister)
  
  val Auth_Password:String             = nameOf(Auth_Password)
  val Auth_Email:String                = nameOf(Auth_Email)
  val Auth_LoggedInAs:String           = nameOf(Auth_LoggedInAs)
  val Auth_LoginInfo:String            = nameOf(Auth_LoginInfo)  
  val Auth_Content:String              = nameOf(Auth_Content)

  val DlgPrompt_Load:String            = nameOf(DlgPrompt_Load)
  val DlgMsgbox_Load:String            = nameOf(DlgMsgbox_Load)

  val Console_show:String              = nameOf(Console_show)
  val Console_click:String             = nameOf(Console_click)


object UCs extends NameOf: 

  val ChatExample:String               = nameOf(ChatExample)
  val UseCase1Sub2:String              = nameOf(UseCase1Sub2)
  val UseCase2:String                  = nameOf(UseCase2)

