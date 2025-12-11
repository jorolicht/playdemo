package addon

import org.rogach.scallop._

import base._
import base.Messages._
import dialog.DlgPrompt

import org.scalajs.dom

import scala.scalajs.js
import scala.scalajs.js.annotation._
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import upickle.default._
import shared.model.AppError
import shared.IdsConsole.*

def addOutput(text: String) = DlgPrompt.add(text)

object Addon extends JsWrapper: 
 
  @JSExportTopLevel("startConsole")
  def startConsole() = dom.window.setTimeout(() => { console() }, 200)

  /** console - entry point of addon
   */  
  def console(): Future[Boolean] = 
    val command = getData(gE2(ClickId), "command", "")
    if (command != "") setData(gE2(ClickId), "command", "")

    //Future[Either[AppError, String]] =
    val dlgResult = DlgPrompt.show(command).map {
      case Left(err)    => Future(Left(err))
      case Right(input) => execute(input)
    }
    val restart = dlgResult.flatten.map {
      case Left(err)     => println(s"DlgResult Error: ${err}"); !err.is("dlg.cancel")
      case Right(result) => println(result); true 
    }
    restart.map {
      case false => false
      case true  => startConsole(); true
    }


  // execute console/addon command
  def execute(cmd: String): Future[Either[AppError, String]] =    
    class ConfMain(arguments: Seq[String]) extends ScallopConf(List("log", "test")) {
      override def onError(e: Throwable): Unit = e match { case _ => addOutput(getFullHelpString()) } 
      banner("""Usage: <command> <arguments>
              | 
              |  Commands:
              |    log  - set log level
              |    test - start tests
              |
              |""".stripMargin)
      footer(s"\n${getMsg("addon.footer")}") 
      verify()
    }

    val args  = cmd.split("\\s+")     // split on all white spaces 
    val args1 = args.patch(0, Nil, 1) // scala patch: replace position 0 with the value Nil and the length 1
    args(0).toLowerCase match
      case "log"   => cmdLog(args1)
      case "test"  => cmdTest(args1)
      case _       => 
        val conf = new ConfMain(Seq())
        addOutput(conf.getFullHelpString())
        Future(Left(AppError("Invalid command"))) 


  /** log command
   * 
   */ 
  def cmdLog(args: Array[String]): Future[Either[AppError, String]] = {

    class ConfLog(arguments: Seq[String]) extends ScallopConf(arguments) {
      override def onError(e: Throwable): Unit = e match { case _ => addOutput(getFullHelpString()) }

      //version(getMsg("addon.version"))
      banner("""Usage: log --level [off|error|warn|info|debug|show]
               |setting the log level, output displayed on javascript console
               |
               |""".stripMargin)
      footer(s"\n${getMsg("addon.footer")}") 
      val level = choice(name="level", choices= Seq("off", "error", "warn", "info", "debug"))
      verify()
    }    

    try
      val conf = new ConfLog(args)
      val level = conf.level.getOrElse("show") 
      level match 
        case "error" | "warn" | "info" | "debug"  => Logging.setLogLevel(level)
        case "off"   => Logging.setLogLevel("")
        case "show"  => addOutput(s"Current LogLevel: ${Logging.getLogLevel().getOrElse("UNKNOWN")}")
        case _       => Future(Left(AppError("command.invalid"))) 
    catch { case _:Exception => Future(Left(AppError("command.invalid"))) } 
    Future(Right(s"FINISHED: cmdLog"))
  }


  /** test command
   * 
   */ 
  def cmdTest(args: Array[String]): Future[Either[AppError, String]] = 

    class ConfTest(arguments: Seq[String]) extends ScallopConf(arguments) {  
      override def onError(e: Throwable): Unit = e match { case _ => addOutput(getFullHelpString()) }
      banner("""Usage: test --group [basic | dialog | auth | html] --number <number> --param <value> 
               |select a test group and specify the test number and the param for the test
               |
               |""".stripMargin)

      val group  = choice(name="group", choices=Seq("basic", "dialog", "auth", "html"))
      val number = opt[Int](name="number")
      val param  = opt[String](name="param")  
      verify()
    }

    try
      val conf   = new ConfTest(args)
      val group  = conf.group.getOrElse("basic")
      val number = conf.number.getOrElse(0)
      val param  = conf.param.getOrElse("")

      group match
        case "basic"     => TestBasic.exec(group, number, param)
        case "dialog"    => TestDialog.exec(group, number, param)
        case "auth"      => TestAuth.exec(group, number, param)
        case "html"      => TestHtml.exec(group, number, param)
        case _           => Future(Left(AppError("command.invalid"))) 
    catch { case _:Exception => Future(Left(AppError("command.invalid"))) }