package services

import java.util.concurrent.ConcurrentHashMap
import scala.collection.mutable.ListBuffer
import shared.model.{MatchboardEntry, Matchboard}

object MatchboardStore {

  case class SlugData(
    var tourneyName: String = "Turnier",
    val matchboardDB: ListBuffer[MatchboardEntry] = ListBuffer(),
    val refereeDB: ListBuffer[String] = ListBuffer(),
    var lastActivity: Long = System.currentTimeMillis()
  )

  private val store = new ConcurrentHashMap[String, SlugData]()

  // Touch last activity and return the data
  private def touch(slug: String): SlugData = {
    var data = store.get(slug)
    if (data == null) {
      data = SlugData()
      store.put(slug, data)
    }
    data.lastActivity = System.currentTimeMillis()
    data
  }

  def getOrCreate(slug: String): SlugData = touch(slug)

  def get(slug: String): Option[SlugData] = {
    val data = store.get(slug)
    if (data != null) {
      data.lastActivity = System.currentTimeMillis()
      Some(data)
    } else {
      None
    }
  }

  // Referee results queue management
  def addRefereeResult(slug: String, result: String): Unit = {
    val data = touch(slug)
    data.refereeDB.append(result)
  }

  def getPendingRefereeResults(slug: String): Seq[String] = {
    val data = store.get(slug)
    if (data != null) {
      data.refereeDB.toSeq
    } else {
      Seq.empty
    }
  }

  def clearRefereeResults(slug: String): Unit = {
    val data = store.get(slug)
    if (data != null) {
      data.refereeDB.clear()
    }
  }

  // Cleanup method to evict inactive slugs (inactive > 3 hours)
  def cleanup(maxAgeMs: Long = 3 * 3600 * 1000L): Unit = {
    val now = System.currentTimeMillis()
    val it = store.entrySet().iterator()
    while (it.hasNext) {
      val entry = it.next()
      if (now - entry.getValue.lastActivity > maxAgeMs) {
        it.remove()
      }
    }
  }
}
