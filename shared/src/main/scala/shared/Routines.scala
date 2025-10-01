package shared

package object Routines:
  import scala.collection.mutable.ArrayBuffer
  import scala.util.{Try, Success, Failure}

  private val emailRegex =
    """^[a-zA-Z0-9\.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$""".r

  def validEmail(e: String): Boolean = e match
    case null => false
    case e if e.trim.isEmpty => false
    case e if emailRegex.findFirstMatchIn(e).isDefined =>
      try
        val domain = e.split("@")(1).split('.')
        (domain.last.length >= 2) & (domain.size >= 2)
      catch case _: Throwable => false
    case _ => false

  def ite[A](cond: Boolean, value1: A, value2: A): A =
    if cond then value1 else value2

  def getMDLongArr(s: String): Array[Long] =
    try s.split('·').map(_.toLong) catch case _: Throwable => Array()

  def getMDLongArrDef(s: String, default: Long = 0L): Array[Long] =
    try s.split('·').map(_.toLong) catch case _: Throwable => Array(default)

  def getMDIntArr(s: String): Array[Int] =
    try s.split('·').map(_.toInt) catch case _: Throwable => Array()

  def getMDStr(s: String, index: Int): String =
    try s.split("·")(index) catch case _: Throwable => ""

  def getMDInt(s: String, index: Int): Int =
    try s.split("·")(index).toInt catch case _: Throwable => 0

  def getMDLong(s: String, index: Int): Long =
    try s.split("·")(index).toLong catch case _: Throwable => 0L

  def getMDBool(s: String, index: Int): Boolean =
    try s.split("·")(index).toBoolean catch case _: Throwable => false

  def getMDIntOption(s: String, index: Int): Option[Int] =
    try s.split("·")(index).toIntOption catch case _: Throwable => None

  def setMDOption[U](s: String, value: Option[U], index: Int): String =
    value match
      case None       => setMD(s, "?", index)
      case Some(uVal) => setMD(s, uVal, index)

  def setMD[U](s: String, value: U, index: Int): String =
    val sArr = s.split('·')
    if index < sArr.length then
      sArr(index) = s"$value"
      sArr.mkString("·")
    else
      val nArr = Array.fill(index + 1)("")
      Array.copy(sArr, 0, nArr, 0, sArr.length)
      nArr(index) = s"$value"
      nArr.mkString("·")

  def genMD(args: String*): String = args.mkString("·")

  def int2ymd(date: Int): (Int, Int, Int) =
    try (date / 10000, (date / 100) % 100, date % 100)
    catch case _: Throwable => (1970, 1, 1)

  def ymdHM(startDate: String): (Int, Int, Int, Int, Int) =
    val datetime = """(\d\d\d\d\d\d\d\d)#(\d\d\d\d)""".r
    startDate match
      case datetime(ymdValue, timeValue) =>
        val ymd  = ymdValue.toIntOption.getOrElse(19700101)
        val time = timeValue.toIntOption.getOrElse(1200)
        (ymd / 10000, (ymd / 100) % 100, ymd % 100, time / 100, time % 100)
      case _ => (1970, 1, 1, 12, 0)

  def parseStartTime(startDate: String): String =
    val datim1 = """(\d\d\d\d)-(\d\d)-(\d\d)[ ]*(\d\d):(\d\d)""".r
    val datim2 = """(\d\d).(\d\d).(\d\d\d\d)[ ]*(\d\d):(\d\d)""".r
    startDate match
      case datim1(y, m, d, hh, mm) => s"$y$m$d#$hh$mm"
      case datim2(d, m, y, hh, mm) => s"$y$m$d#$hh$mm"
      case _                       => startDate

  def int2date(date: Int, lang: String, fmt: Int = 0): String =
    val moArDe = Array("", "Jan","Feb","Mär","Apr","Mai","Jun","Jul","Aug","Sep","Okt","Nov","Dez")
    val moArEn = Array("", "Jan","Feb","Mar","Apr","May","June","July","Aug","Sept","Oct","Nov","Dec")
    def daySuffix(day: Int) = day match
      case 1 => "st"; case 2 => "nd"; case 3 => "rd"; case _ => "th"

    try
      val year  = date / 10000
      val month = (date / 100) % 100
      val day   = date % 100
      lang match
        case "de" =>
          if fmt == 0 then s"$day. ${moArDe(month)} $year"
          else if fmt == 1 then s"$day.$month.$year"
          else s"$day-$month-$year"
        case "en" =>
          "%02d".format(month) + "-" + "%02d".format(day) + "-" + year
        case _ =>
          day + daySuffix(day) + " " + moArEn(month) + " " + year
    catch case _: Throwable => ""

  def int2time(time: Int, lang: String): String =
    try
      val hour = time / 100
      val hh   = "%02d".format(hour)
      val mm   = "%02d".format(time % 100)
      lang match
        case "de" => s"$hh:$mm"
        case _ =>
          if      hour <= 11 then s"$hh:$mm AM"
          else if hour == 12 then s"$hh:$mm PM"
          else if hour <= 23 then "%02d".format(hour - 12) + s":$mm PM"
          else "xx:xx"
    catch case _: Throwable => ""

  def date2Int(date: String): Int =
    val DatePatternDE     = """(\d{1,2}).(\d{1,2}).(\d{4})""".r
    val DatePatternEN     = """(\d{1,2})/(\d{1,2})/(\d{4})""".r
    val DatePatternDEInv  = """(\d{4}).(\d{2}).(\d{2}).""".r
    val DatePatternENInv  = """(\d{4})/(\d{2})/(\d{2})""".r
    val DatePatternNorway = """(\d{4})-(\d{2})-(\d{2})""".r
    date match
      case DatePatternDE(d, m, y)     => y.toInt * 10000 + m.toInt * 100 + d.toInt
      case DatePatternEN(m, d, y)     => y.toInt * 10000 + m.toInt * 100 + d.toInt
      case DatePatternDEInv(y, m, d)  => y.toInt * 10000 + m.toInt * 100 + d.toInt
      case DatePatternENInv(y, m, d)  => y.toInt * 10000 + m.toInt * 100 + d.toInt
      case DatePatternNorway(y, m, d) => y.toInt * 10000 + m.toInt * 100 + d.toInt
      case _ => 0

  def splitName(name: String): (String, String) =
    name.split(",") match
      case Array(a, b) => (a.trim, b.trim)
      case _           => (name, "")

  def urify(name: String): String =
    val replacements = Map(
      ":"->"",";"->""," "->"",","->"","_"->"","ö"->"oe","ü"->"ue","ä"->"ae",
      "Ö"->"Oe","Ü"->"Ue","Ä"->"Ae","\" "->"","ß"->"ss","\\"->"","e."->"",
      "V."->"","$"->"","%"->"","#"->"","§"->"","&"->"","+ "->"","-"->"",
      "*"->"","/"->"","("->""," )"->""," ]"->""," ["->"","@"->"att",
      "'"->"","!"->"","?"->"","<"->""," >"->"","{"->"","}"->""
    )
    replacements.foldLeft(name)((a,b) => a.replace(b._1,b._2))
      .toLowerCase.replace(".","")

  def randomString(len: Int = 6): String =
    val rand  = new scala.util.Random(System.nanoTime)
    val upper = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    val lower = "abcdefghijklmnopqrstuvwxyz"
    val num   = "0123456789"
    val abc   = upper + lower + num
    val sb    = new StringBuilder(len)
    var start = 0
    if len > 3 then
      start = 3
      sb.append(upper(rand.nextInt(upper.length)))
      sb.append(lower(rand.nextInt(lower.length)))
      sb.append(num(rand.nextInt(num.length)))
    for i <- start until len do sb.append(abc(rand.nextInt(abc.length)))
    sb.toString

  def getOrDefault(value: String, defValue: => String): String =
    if value == "" then defValue else value

  def seqEither[A, B](s: Seq[Either[A, B]]): Either[A, Seq[B]] =
    s.foldRight(Right(Nil): Either[A, List[B]]) { (e, acc) =>
      for xs <- acc.right; x <- e.right yield x :: xs
    }

  trait ToEither[A, B]:
    def toEither: Either[A, B]

  implicit def tryToEither[A](t: Try[A]): ToEither[Throwable, A] =
    new ToEither[Throwable, A]:
      def toEither = t.map(Right(_)).recover { case e => Left(e) }.get

  def swap[T](x: ArrayBuffer[T], i: Int, j: Int): Unit =
    val tmp = x(i)
    x(i) = x(j)
    x(j) = tmp

