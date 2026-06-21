package services

import scala.scalajs.js.timers._

/**
 * Trait providing debouncing logic for scheduled tasks.
 * If called multiple times within the 'delay' period, the timer restarts.
 */
trait Debouncer:
  private var syncHandle: Option[SetTimeoutHandle] = None
  private var pendingAction: Option[() => Unit] = None

  /**
   * Executes the given 'block' function after 'delay' ms.
   * If called again while the timer is running, the timer restarts.
   */
  def debounce(delay: Int = 500)(block: => Unit): Unit =
    syncHandle.foreach(clearTimeout)
    pendingAction = Some(() => block)
    syncHandle = Some(setTimeout(delay) {
      pendingAction.foreach(_())
      pendingAction = None
      syncHandle = None
    })

  /**
   * Immediately cancels any currently running timer.
   */
  def cancelSync(): Unit =
    syncHandle.foreach(clearTimeout)
    syncHandle = None
    pendingAction = None

  /**
   * Immediately executes any pending action synchronously and cancels the timer.
   */
  def flushSync(): Unit =
    pendingAction.foreach { action =>
      syncHandle.foreach(clearTimeout)
      action()
      pendingAction = None
      syncHandle = None
    }
