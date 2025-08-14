package base

import org.scalajs.logging._

object Logging extends JsWrapper:
  var logger:Logger = org.scalajs.logging.NullLogger

  def setLogLevel(value: String) =
    value.toLowerCase() match 
      case "error" => setLocalStorage("LogLevel", "error");  logger = new org.scalajs.logging.ScalaConsoleLogger(Level.Error)
      case "warn"  => setLocalStorage("LogLevel", "warn");   logger = new org.scalajs.logging.ScalaConsoleLogger(Level.Warn)
      case "info"  => setLocalStorage("LogLevel", "info");   logger = new org.scalajs.logging.ScalaConsoleLogger(Level.Info) 
      case "debug" => setLocalStorage("LogLevel", "debug");  logger = new org.scalajs.logging.ScalaConsoleLogger(Level.Debug)
      case _       => setLocalStorage("LogLevel", "");       logger = org.scalajs.logging.NullLogger    

  def getLogLevel():Option[String] = 
    getLocalStorage("LogLevel") match {
       case "error" => Some("error")
       case "warn"  => Some("warn")
       case "info"  => Some("info")
       case "debug" => Some("debug")
       case _       => None
    }

