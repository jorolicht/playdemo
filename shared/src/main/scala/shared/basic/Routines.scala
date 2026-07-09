package shared.basic

import scala.util.{Try, Success, Failure}
import scala.collection.mutable.ArrayBuffer
import scala.util.matching.Regex
import scala.math.Numeric.Implicits.*

import scala.concurrent.Future
import scala.reflect.ClassTag
import java.util.Random
import cats.syntax.either.*

// ---------------------------------------------------------------------------
// Custom upickle bundle for nullable Option support
// ---------------------------------------------------------------------------
object Pickle extends upickle.AttributeTagged:
  override def objectTypeKeyReadMap(s: CharSequence): CharSequence =
    s.toString match
      case "shared.model.MEntryKo"   => "MEntryKo"
      case "shared.model.MEntryGr"   => "MEntryGr"
      case "shared.model.MEntryBase" => "MEntryBase"
      case other                     => other

  override implicit def OptionWriter[T: Writer]: Writer[Option[T]] =
    writer[ujson.Value].comap[Option[T]] {
      case Some(v) => writeJs(v)
      case None    => ujson.Null
    }

  override implicit def OptionReader[T: Reader]: Reader[Option[T]] =
    reader[ujson.Value].map[Option[T]] {
      case ujson.Null => None
      case x => Some(read[T](x))
    }

  given mapRW: ReadWriter[scala.collection.mutable.Map[shared.model.SNO, String]] =
    summon[ReadWriter[scala.collection.mutable.Map[String, String]]].bimap[scala.collection.mutable.Map[shared.model.SNO, String]](
      m => {
        val res = scala.collection.mutable.Map[String, String]()
        if (m != null) {
          m.foreach { case (sno, id) => res(sno.asInstanceOf[String]) = id }
        }
        res
      },
      m => {
        val res = scala.collection.mutable.Map[shared.model.SNO, String]()
        if (m != null) {
          m.foreach { case (sStr, id) => res(shared.model.SNO.fromString(sStr)) = id }
        }
        res
      }
    )

type EiErr[T] = Either[Error, T]
type FuEiErr[T] = Future[Either[AppError, T]]

extension (str: String)
  def toTuple(sep: String=":"): Tuple2[String, String] = 
    val x = str.split(sep)
    if x.length != 2 then ("","") else (x(0),x(1))

  def toError(func: String): AppError = 
    try Pickle.read[AppError](str)
    catch { case e: Throwable => AppError("err00006.parseJson", e.getMessage, str.take(10)).add(func) }

  def to[T]()(using r: Pickle.Reader[T]): Either[AppError, T] = {
    try if str == "" then Left(AppError("err00006.parseJson", "empty string")) else Right(Pickle.read[T](str))
    catch { case e: Throwable => Left(AppError("err00006.parseJson", e.getMessage, str.take(10))) }
  }    


def parseJson[T: Pickle.Reader](x: String): Either[AppError, T] =
  // println(s"parseJson: parsing string: ${x}") 
  if x.isEmpty then
    Left(AppError("err00006.parseJson", "empty string"))
  else
    Either
      .catchNonFatal(Pickle.read[T](x))
      .leftMap(e =>
        AppError("err00006.parseJson", e.getMessage, x.take(10))
      )

inline def toJson[T](x: T)(using w: Pickle.Writer[T]): String = Pickle.write[T](x)  

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

val MetadataSeparator = "·"

private val emailRegex =
  """^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9-]+(?:\.[a-zA-Z0-9-]+)+$""".r

// ---------------------------------------------------------------------------
// Email validation
// ---------------------------------------------------------------------------

/**
 * Basic email validation.
 * Ensures:
 *  - not null
 *  - not empty
 *  - valid regex
 *  - TLD length >= 2
 */
def validEmail(e: String): Boolean =
  Option(e)
    .map(_.trim)
    .filter(_.nonEmpty)
    .exists { email =>
      emailRegex.matches(email) &&
      email.split("@").lastOption.exists { domain =>
        domain.split('.').lastOption.exists(_.length >= 2)
      }
    }

// ---------------------------------------------------------------------------
// Functional helpers
// ---------------------------------------------------------------------------

inline def ite[A](cond: Boolean, ifTrue: => A, ifFalse: => A): A =
  if cond then ifTrue else ifFalse

def getOrDefault(value: String, default: => String): String =
  if value.isEmpty then default else value

// ---------------------------------------------------------------------------
// Metadata helpers
// ---------------------------------------------------------------------------

private def splitMD(s: String): Array[String] =
  if s == null then Array.empty else s.split(MetadataSeparator, -1)

def genMD(values: String*): String =
  values.mkString(MetadataSeparator)

def getMDStr(s: String, index: Int): String =
  splitMD(s).lift(index).getOrElse("")

def getMDInt(s: String, index: Int): Int =
  splitMD(s).lift(index).flatMap(_.toIntOption).getOrElse(0)

def getMDLong(s: String, index: Int): Long =
  splitMD(s).lift(index).flatMap(_.toLongOption).getOrElse(0L)

def getMDBool(s: String, index: Int): Boolean =
  splitMD(s).lift(index).exists(_.toBooleanOption.getOrElse(false))

def getMDIntOption(s: String, index: Int): Option[Int] =
  splitMD(s).lift(index).flatMap(_.toIntOption)

def getMDLongArr(s: String): Array[Long] =
  splitMD(s).flatMap(_.toLongOption)

def getMDIntArr(s: String): Array[Int] =
  splitMD(s).flatMap(_.toIntOption)

def setMD(s: String, value: Any, index: Int): String =
  val arr = splitMD(s).toBuffer
  while arr.length <= index do arr += ""
  arr(index) = value.toString
  arr.mkString(MetadataSeparator)

def setMDOption[T](s: String, value: Option[T], index: Int): String =
  setMD(s, value.getOrElse("?"), index)

// ---------------------------------------------------------------------------
// Date helpers (yyyyMMdd Int format)
// ---------------------------------------------------------------------------

def int2ymd(date: Int): (Int, Int, Int) =
  (date / 10000, (date / 100) % 100, date % 100)

def int2date(date: Int, lang: String, fmt: Int = 0): String =
  val (year, month, day) = int2ymd(date)

  val monthsDe =
    Array("", "Jan", "Feb", "Mär", "Apr", "Mai", "Jun",
          "Jul", "Aug", "Sep", "Okt", "Nov", "Dez")

  val monthsEn =
    Array("", "Jan", "Feb", "Mar", "Apr", "May", "Jun",
          "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

  lang match
    case "de" =>
      fmt match
        case 0 => s"$day. ${monthsDe.lift(month).getOrElse("")} $year"
        case 1 => f"$day%02d.$month%02d.$year"
        case _ => s"$day-$month-$year"
    case "en" =>
      f"$month%02d-$day%02d-$year"
    case _ =>
      s"$day ${monthsEn.lift(month).getOrElse("")} $year"

def int2time(time: Int, lang: String): String =
  val hour = time / 100
  val min  = time % 100
  val hh   = f"$hour%02d"
  val mm   = f"$min%02d"

  lang match
    case "de" => s"$hh:$mm"
    case _ =>
      val (h12, suffix) =
        if hour == 0 then (12, "AM")
        else if hour < 12 then (hour, "AM")
        else if hour == 12 then (12, "PM")
        else (hour - 12, "PM")

      f"$h12%02d:$mm $suffix"

/**
 * Parses common date formats into an Int of form YYYYMMDD.
 *
 * Supported formats:
 *   dd.MM.yyyy
 *   MM/dd/yyyy
 *   yyyy-MM-dd
 *
 * Returns 0 if parsing fails.
 */
def date2Int(date: String): Int =
  val trimmed = date.trim

  trimmed match
    // dd.MM.yyyy
    case s"$d.$m.$y" if d.forall(_.isDigit) && m.forall(_.isDigit) && y.length == 4 =>
      buildDate(y, m, d)

    // MM/dd/yyyy
    case s"$m/$d/$y" if d.forall(_.isDigit) && m.forall(_.isDigit) && y.length == 4 =>
      buildDate(y, m, d)

    // yyyy-MM-dd
    case s"$y-$m-$d" if y.length == 4 && m.forall(_.isDigit) && d.forall(_.isDigit) =>
      buildDate(y, m, d)

    case _ =>
      0


private def buildDate(y: String, m: String, d: String): Int =
  (
    for
      year  <- y.toIntOption
      month <- m.toIntOption
      day   <- d.toIntOption
    yield year * 10000 + month * 100 + day
  ).getOrElse(0)

// ---------------------------------------------------------------------------
// String helpers
// ---------------------------------------------------------------------------

def splitName(name: String): (String, String) =
  name.split(",", 2) match
    case Array(a, b) => (a.trim, b.trim)
    case _           => (name.trim, "")

def urify(name: String): String =
  name
    .toLowerCase
    .replaceAll("[^a-z0-9]", "")

// ---------------------------------------------------------------------------
// Random
// ---------------------------------------------------------------------------

def randomString(len: Int = 6): String =
  val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
  val rand  = Random()
  (1 to len)
    .map(_ => chars(rand.nextInt(chars.length)))
    .mkString  

// ---------------------------------------------------------------------------
// Either helpers
// ---------------------------------------------------------------------------

def seqEither[A, B](s: Seq[Either[A, B]]): Either[A, Seq[B]] =
  s.foldRight(Right(Nil): Either[A, List[B]]) {
    case (Right(v), Right(acc)) => Right(v :: acc)
    case (Left(err), _)         => Left(err)
    case (_, Left(err))         => Left(err)
  }


// ---------------------------------------------------------------------------
// Mutable helpers (only if really needed)
// ---------------------------------------------------------------------------

def swap[T](buffer: ArrayBuffer[T], i: Int, j: Int): Unit =
  val tmp = buffer(i)
  buffer(i) = buffer(j)
  buffer(j) = tmp



// 1. & 2. Erweiterungen für Tupel (Zusammengefasst in einen Extension-Block)
extension [A: Numeric, B: Numeric](t: (A, B))

  // Hinweis: Ihre ursprüngliche swap-Methode hat den Parameter p vertauscht, 
  // aber t ignoriert. Wenn Sie t (die Instanz selbst) swappen möchten:
  def swap: (B, A) = (t._2, t._1)

  // Wenn Sie die Methode so beibehalten wollen, dass sie ein anderes Tupel p swappt:
  def swapOther(p: (A, B)): (B, A) = (p._2, p._1)

  def + (p: (A, B)): (A, B) = 
    (p._1 + t._1, p._2 + t._2)


// 3. Sortierung (View Bound `<%` ersetzt durch Context Bound `Ordering`)
class OrderTupleBySecond[X, Y: Ordering] extends Ordering[(X, Y)]:
  def compare(x: (X, Y), y: (X, Y)): Int =
    summon[Ordering[Y]].compare(x._2, y._2)  
