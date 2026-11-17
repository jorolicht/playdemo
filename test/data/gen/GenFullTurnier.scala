//> using scala 3.3.7
//> using dep com.lihaoyi::upickle:3.3.1
//> using dep org.typelevel::shapeless3-deriving:3.4.0
//> using dep com.lihaoyi::sourcecode:0.4.2
//> using dep org.typelevel::cats-core:2.13.0

/**
 * Erzeugt die Testdatei `test/data/FullTurnier.json`: ein vollständiges Turnier mit lustigen Namen
 * ("1. Internationale Netzroller-Open 2028", 11.–16.11.2028) und sechs Wettbewerben:
 *
 *  - Herren A Einzel: 126 Spieler gemeldet, 99 teilnehmend
 *  - Damen A Doppel:  48 Doppel gemeldet, 42 teilnehmend
 *  - Herren B Team2:  36 2er-Mannschaften gemeldet, 18 teilnehmend
 *  - Damen B Team3:   20 3er-Mannschaften gemeldet, 16 teilnehmend
 *  - Herren C Team4:  16 4er-Mannschaften gemeldet, 12 teilnehmend
 *  - Damen C Team6:    8 6er-Mannschaften gemeldet, 4 teilnehmend
 *
 * Jahrgang, E-Mail und WhatsApp sind bei ca. 75 % der Spieler gesetzt. Das Turnier wird mit den Modellklassen aus
 * `shared` aufgebaut und im Format des Demo-Turniers (`{"tourney":{"version":1,"tourney":{...}}}`) geschrieben, das
 * "Ablage -> Turnier laden" einliest. Der Zufallsgenerator hat einen festen Startwert, die Datei ist reproduzierbar.
 *
 * Aufruf (aus dem Projektverzeichnis, nach `sbt compile` wegen der generierten `SportFiles.scala`):
 * {{{
 * scala-cli run test/data/gen/GenFullTurnier.scala shared/src/main/scala \
 *   shared/.jvm/target/scala-3.3.7/src_managed/main/shared/sport/SportFiles.scala -- test/data/FullTurnier.json
 * }}}
 */
import scala.collection.mutable.ArrayBuffer
import scala.util.Random
import shared.model.*
import shared.basic.Pickle.*

/**
 * Erzeugt das Testturnier und gibt eine Kontrollübersicht aus.
 *
 * @param out Zieldatei, z. B. `test/data/FullTurnier.json`
 */
@main def gen(out: String): Unit =
  val rnd = new Random(2028)
  val maleFirst = Seq("Anton","Bodo","Carlo","Detlef","Egon","Fritz","Gustl","Horst","Igor","Jupp","Kuno","Lutz","Moritz","Norbert","Olaf","Paul","Quirin","Rudi","Sepp","Theo","Udo","Volker","Willi","Xaver","Yannick","Zacharias","Bernd","Dieter","Ewald","Ferdi","Gerd","Hugo","Jochen","Klaus","Ludger","Manni","Otto","Pit","Rolf","Siggi")
  val femaleFirst = Seq("Ada","Berta","Carla","Doris","Elli","Frieda","Gerda","Hanna","Ida","Jutta","Karla","Lotte","Mia","Nele","Olga","Paula","Resi","Susi","Trudi","Uschi","Vroni","Wanda","Yvonne","Zenzi","Anni","Biggi","Conny","Dörte","Erna","Fanny","Gabi","Hilde","Inge","Kati","Liesl","Moni","Rita","Sabse","Tilda","Ulla")
  val last = Seq("Topspin","Schnitt","Kantenball","Netzroller","Rückhand","Vorhand","Aufschlag","Schupf","Block","Konter","Flip","Lob","Smash","Unterschnitt","Seitenschnitt","Ballonball","Tischkante","Schlägerholz","Noppe","Belag","Zelluloid","Plastikball","Satzball","Matchball","Doppelfehler","Ballwechsel","Linkshänder","Penholder","Shakehand","Kurzkurz","Langlang","Zitterhand","Schmetterling","Bananenflip","Chopper","Abwehrmauer","Spinmeister","Netzkante","Tischplatte","Schweißband","Ballkiste","Zählgerät","Hallenschuh","Trikotwechsel","Pingpong","Ballgefühl","Rotationsmann","Kantenglück","Aufsetzer","Hinterhand")
  val clubNames = Seq("TTC Schmetterball 1928","SV Kantenball","TSV Netzroller","DJK Topspin 08","TuS Zelluloid","SC Rückhand Rasant","TTV Noppen-Power","FC Plastikball","Post SV Schupfgold","SpVgg Satzball","TTF Ballonfahrer","SG Kurz-Kurz","TTC Linkshänder-Liga","VfB Tischkante","ESV Penholder","TV Shakehand 1860","TTG Bananenflip","SV Chopper-Club","TSG Aufschlag-Ass","TTC Rotation Rüsselsheim")
  val teamPrefix = Seq("Die wilden","Die flotten","Die schnellen","Die rotierenden","Die frechen","Die eisernen","Die tanzenden","Die fliegenden","Die sanften","Die listigen")
  val teamNoun = Seq("Netzroller","Kantenkönige","Schupfer","Topspinner","Ballonfahrer","Rückhandrebellen","Plastikbomber","Satzsammler","Aufschlagasse","Zelluloid-Zombies","Noppen-Ninjas","Schmetterlinge","Chopper","Blockwarte","Lobbyisten","Konterbanditen")
  def umlaut(s: String) = s.toLowerCase.replace("ä","ae").replace("ö","oe").replace("ü","ue").replace("ß","ss")

  val t = Tourney(
    wpId = 0, name = "1. Internationale Netzroller-Open 2028", organizer = "TTC Schmetterball 1928 e.V.",
    startDate = 20281111, endDate = 20281116, ident = "NRO2028", category = CompCategory.TT,
    contact = Some(Contact(lastname = "Topspin", firstname = "Toni", phone = "+49 931 4711 0815", email = "turnierleitung@netzroller-open.example.com")),
    address = Some(Address(description = "Dreifachsporthalle am Kantenweg", country = "Deutschland", zip = "97070", city = "Würzburg", street = "Kantenweg 11")),
    version = 1)
  clubNames.foreach(n => t.addClub(n, checkSimilarity = false, doSync = false))
  val clubIds = t.clubs.map(_.id.toInt).toSeq

  val usedNames = scala.collection.mutable.Set[(String, String)]()
  var nextId = 1
  def newPlayer(female: Boolean, ttrRange: (Int, Int), club: Int): Player =
    var n = (("", ""))
    while { n = ((if female then femaleFirst else maleFirst)(rnd.nextInt(40)), last(rnd.nextInt(last.size))); usedNames.contains(n) } do ()
    usedNames += n
    val (fn, ln) = n
    val p = Player(
      id = PlayerId(nextId), firstName = fn, lastName = ln, clubId = club,
      birthYear = if rnd.nextDouble() < 0.75 then Some(1950 + rnd.nextInt(62)) else None,
      email     = if rnd.nextDouble() < 0.75 then Some(s"${umlaut(fn)}.${umlaut(ln)}@example.com") else None,
      whatsApp  = if rnd.nextDouble() < 0.75 then Some(f"+49 15${rnd.nextInt(10)} ${rnd.nextInt(10000000)}%07d") else None,
      sex = if female then Sex.Female else Sex.Male,
      meta = PlayerMeta(ttr = Some(ttrRange._1 + rnd.nextInt(ttrRange._2 - ttrRange._1 + 1)), ttrMatchCnt = Some(20 + rnd.nextInt(400))))
    nextId += 1
    t.players += p
    p
  def clubName(id: Int) = t.clubs.find(_.id.toInt == id).map(_.name).getOrElse("")
  def pant(sno: SNO, name: String, club: String, rating: Int, by: String, active: Boolean, clubId: Int = 0) =
    Pant(id = sno, name = name, club = club, rating = rating, birthYear = by, active = active,
         status = if active then PantStatus.PLAY else PantStatus.REGI, clubId = clubId)

  def comp(id: Int, name: String, typ: CompTyp, day: Int, ttr: (Int, Int), sexId: Int, ageGroup: String, klasse: String) =
    val c = Competition(id = CompId(id), name = name, typ = typ, category = CompCategory.TT,
      startDate = f"2028-11-$day%02d ${if id % 2 == 1 then "09:00" else "13:00"}", status = CompStatus.CFG,
      lowLevel = Some(ttr._1), upperLevel = Some(ttr._2), scoring = "tt_002",
      cttInfo = Some(CompCTT(ageGroup = ageGroup, ratingRemark = klasse, ratingLowLevel = ttr._1, ratingUpperLevel = ttr._2, sex = sexId)))
    t.competitions(id - 1) = c
    c
  val A = (1600, 2300); val B = (1300, 1599); val C = (900, 1299)

  // 1) Herren A Einzel: 126 gemeldet, 99 teilnehmend
  val c1 = comp(1, "Herren A Einzel", CompTyp.SINGLE, 11, A, 1, "Herren", "A-Klasse")
  (0 until 126).foreach { i =>
    val p = newPlayer(false, A, clubIds(rnd.nextInt(clubIds.size)))
    c1.pants1Stage += pant(SNO.single(p.id), p.displayName, clubName(p.clubId), p.meta.ttr.get, p.birthYear.map(_.toString).getOrElse(""), i < 99, p.clubId)
  }
  // 2) Damen A Doppel: 48 gemeldet, 42 teilnehmend
  val c2 = comp(2, "Damen A Doppel", CompTyp.DOUBLE, 12, A, 2, "Damen", "A-Klasse")
  (0 until 48).foreach { i =>
    val club = clubIds(rnd.nextInt(clubIds.size))
    val p1 = newPlayer(true, A, club)
    val p2 = newPlayer(true, A, if rnd.nextDouble() < 0.7 then club else clubIds(rnd.nextInt(clubIds.size)))
    val cl = if p1.clubId == p2.clubId then clubName(p1.clubId) else s"${clubName(p1.clubId)}, ${clubName(p2.clubId)}"
    c2.pants1Stage += pant(SNO.double(p1.id, p2.id), s"${p1.lastName} / ${p2.lastName}", cl, (p1.meta.ttr.get + p2.meta.ttr.get) / 2, "", i < 42)
  }
  // 3-6) Mannschaften
  val teamNames = rnd.shuffle(for p <- teamPrefix; n <- teamNoun yield s"$p $n").iterator
  def teams(c: Competition, count: Int, active: Int, size: Int, extra: Double, female: Boolean, ttr: (Int, Int)): Unit =
    (1 to count).foreach { k =>
      val club = clubIds(rnd.nextInt(clubIds.size))
      val n    = size + (if rnd.nextDouble() < extra then 1 else 0)
      val ps   = (1 to n).map(_ => newPlayer(female, ttr, if rnd.nextDouble() < 0.85 then club else clubIds(rnd.nextInt(clubIds.size))))
      val team = Team(k, teamNames.next(), club, ps.map(_.id))
      c.teams += team
      c.pants1Stage += pant(team.sno, team.name, clubName(club), team.rating(size, t.players.toSeq), "", k <= active)
    }
  teams(comp(3, "Herren B Team2", CompTyp.TEAM2, 13, B, 1, "Herren", "B-Klasse"), 36, 18, 2, 0.35, false, B)
  teams(comp(4, "Damen B Team3",  CompTyp.TEAM3, 14, B, 2, "Damen",  "B-Klasse"), 20, 16, 3, 0.0,  true,  B)
  teams(comp(5, "Herren C Team4", CompTyp.TEAM4, 15, C, 1, "Herren", "C-Klasse"), 16, 12, 4, 0.0,  false, C)
  teams(comp(6, "Damen C Team6",  CompTyp.TEAM6, 16, C, 2, "Damen",  "C-Klasse"),  8,  4, 6, 0.0,  true,  C)

  val json = ujson.Obj("tourney" -> ujson.Obj("version" -> 1, "tourney" -> writeJs(t)))
  java.nio.file.Files.writeString(java.nio.file.Path.of(out), ujson.write(json, indent = 2))
  // Kontrolle: wieder einlesen
  val back = read[Tourney](ujson.write(json("tourney")("tourney")))
  println(s"Spieler ${back.players.size}, Vereine ${back.clubs.size}")
  back.competitions.filter(_ != null).foreach { c =>
    println(f"${c.name}%-18s ${c.typ}%-6s gemeldet ${c.pants1Stage.size}%3d, teilnehmend ${c.pants1Stage.count(_.active)}%3d, Mannschaften ${c.teams.size}")
  }
  val ps = back.players.toSeq
  println(f"Jahrgang ${ps.count(_.birthYear.isDefined) * 100.0 / ps.size}%.0f%%, E-Mail ${ps.count(_.email.isDefined) * 100.0 / ps.size}%.0f%%, WhatsApp ${ps.count(_.whatsApp.isDefined) * 100.0 / ps.size}%.0f%%")
