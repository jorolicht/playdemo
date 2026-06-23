package base

import org.scalajs.logging._

object Logging extends JsWrapper:
  // Register with shared logger delegate
  shared.basic.Log.setErrorLogger(msg => Logging.error(msg))
  shared.basic.Log.setInfoLogger(msg => Logging.info(msg))
  shared.basic.Log.setDebugLogger(msg => Logging.debug(msg))

  def debug(msg: => String) = logger.debug(msg)
  def info(msg: => String)  = logger.info(msg)
  def warn(msg: => String)  = logger.warn(msg)
  def error(msg: => String) = logger.error(msg)

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

