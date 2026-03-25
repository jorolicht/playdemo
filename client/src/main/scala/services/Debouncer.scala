package services

import scala.scalajs.js.timers._

/**
 * Trait providing debouncing logic for scheduled tasks.
 * If called multiple times within the 'delay' period, the timer restarts.
 */
trait Debouncer:
  private var syncHandle: Option[SetTimeoutHandle] = None

  /**
   * Executes the given 'block' function after 'delay' ms.
   * If called again while the timer is running, the timer restarts.
   */
  def debounce(delay: Int = 500)(block: => Unit): Unit =
    syncHandle.foreach(clearTimeout)
    syncHandle = Some(setTimeout(delay) {
      block
      syncHandle = None
    })

  /**
   * Immediately cancels any currently running timer.
   */
  def cancelSync(): Unit =
    syncHandle.foreach(clearTimeout)
    syncHandle = None
