package shared.basic

/**
 * Shared logging abstraction that delegates to platform-specific loggers.
 * Allows shared code to log errors and messages dynamically.
 */
object Log:
  private var errorFn: String => Unit = (msg: String) => println(s"ERROR: $msg")
  private var infoFn: String => Unit = (msg: String) => println(s"INFO: $msg")
  private var debugFn: String => Unit = (msg: String) => println(s"DEBUG: $msg")

  /**
   * Sets the error logging delegate function.
   *
   * @param fn The logging function to use for error messages.
   */
  def setErrorLogger(fn: String => Unit): Unit =
    errorFn = fn

  /**
   * Sets the info logging delegate function.
   *
   * @param fn The logging function to use for info messages.
   */
  def setInfoLogger(fn: String => Unit): Unit =
    infoFn = fn

  /**
   * Sets the debug logging delegate function.
   *
   * @param fn The logging function to use for debug messages.
   */
  def setDebugLogger(fn: String => Unit): Unit =
    debugFn = fn

  /**
   * Logs an error message.
   *
   * @param msg The message to log.
   */
  def error(msg: => String): Unit = errorFn(msg)

  /**
   * Logs an info message.
   *
   * @param msg The message to log.
   */
  def info(msg: => String): Unit = infoFn(msg)

  /**
   * Logs a debug message.
   *
   * @param msg The message to log.
   */
  def debug(msg: => String): Unit = debugFn(msg)
