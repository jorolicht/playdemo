package services

import shared.model.*
import shared.basic.*
import scala.collection.mutable.ArrayBuffer

/**
 * ClickTTMapper handles the translation from ClickTT XML models to internal domain models.
 */
object ClickTTMapper:

  /**
   * Maps a CttTournament to the internal models and populates the DB services.
   */
  def mapAndImport(ctt: CttTournament): Either[AppError, Tourney] =
    try
      // 1. Map Tourney
      val tourney = Tourney(
        id = 0,  // New entry, DB will assign ID
        name = ctt.name,
        organizer = base.Global.user.map(_.org).getOrElse("ClickTT Import"),
        startDate = formatDate(ctt.startDate),
        endDate = formatDate(ctt.endDate),
        ident = ctt.tournamentId,
        typ = TourneyTyp.TableTennis,
        address = ctt.locations.headOption.map { loc =>
          Address(
            description = loc.name.getOrElse(""),
            street = loc.street.getOrElse(""),
            zip = loc.zipCode.getOrElse(""),
            city = loc.city.getOrElse(""),
            country = "DE"
          )
        },
        version = 1
      )

      // 2. Import Players and Clubs first (Multi-pass)
      // Collect all unique persons from all competitions
      val allCttPersons = ctt.competitions.flatMap(_.players).flatMap(_.persons)
      val personMap = importPersons(allCttPersons)

      // 3. Map Competitions and Pants
      val mappedComps = ctt.competitions.zipWithIndex.map { case (cttComp, idx) =>
        val compId = CompId(idx + 1)
        
        // Construct Name: ttr-remarks + " " + age-group + " " + type
        val nameParts = Seq(cttComp.ttrRemarks, Some(cttComp.ageGroup), Some(cttComp.typ)).flatten
        val compName = nameParts.mkString(" ").trim

        val comp = Competition(
          id = compId,
          name = compName,
          typ = if (cttComp.typ.toLowerCase.contains("doppel")) CompTyp.DOUBLE else CompTyp.SINGLE,
          startDate = cttComp.startDate, // Format from XML: yyyy-MM-dd HH:mm
          status = CompStatus.UNKN,
          lowLevel = cttComp.ttrFrom,
          upperLevel = cttComp.ttrTo,
          cttInfo = Some(CompCTT(
            ageGroup = cttComp.ageGroup,
            ratingRemark = cttComp.ttrRemarks.getOrElse(""),
            ratingLowLevel = cttComp.ttrFrom.getOrElse(0),
            ratingUpperLevel = cttComp.ttrTo.getOrElse(0),
            sex = cttComp.sex.getOrElse(0),
            maxPersons = cttComp.maxPersons.getOrElse(0),
            entryFee = cttComp.entryFee.getOrElse(""),
            ageFrom = cttComp.ageFrom.map(_.toString).getOrElse(""),
            ageTo = cttComp.ageTo.map(_.toString).getOrElse(""),
            preliminaryRoundMode = cttComp.preliminaryRoundPlaymode.getOrElse(""),
            finalRoundMode = cttComp.finalRoundPlaymode.getOrElse(""),
            manualFinalRankings = cttComp.manualFinalRankings
          ))
        )

        // Map CttPlayers to Pants
        cttComp.players.foreach { cttPlayer =>
          val mappedPant = mapPlayerToPant(cttPlayer, personMap)
          mappedPant.foreach { p =>
            p.ident = cttPlayer.id // aus CttCompetition.player.id
            comp.pants += p
          }
        }
        comp
      }

      // Update Tourney in DB and trigger bulk sync
      TourneyDB.update(tourney)
      tourney.syncClubs()
      tourney.syncPlayers()
      tourney.syncCompetitions(mappedComps)

      Right(tourney)
    catch
      case e: Exception => 
        base.Logging.error(s"Mapping failed: ${e.getMessage}")
        Left(AppError("mapping.failed", e.getMessage))

  private def formatDate(dateStr: String): Int =
    // Expects yyyy-MM-dd, returns yyyymmdd
    try dateStr.replace("-", "").take(8).toInt
    catch { case _: Exception => 0 }

  /**
   * Imports persons into PlayerDB and returns a map of LicenseNr -> Player
   */
  private def importPersons(persons: Seq[CttPerson]): Map[String, Player] =
    val licenseMap = collection.mutable.Map[String, Player]()
    
    persons.foreach { p =>
      val lic = p.licenceNr
      if (lic.nonEmpty && !licenseMap.contains(lic)) {
        // Check if player already exists in DB
        TourneyDB.tourney.players.find(_.meta.licenceNr.contains(lic)) match
          case Some(existing) => 
            licenseMap(lic) = existing
          case None =>
            // Ensure Club exists
            val clubName = p.clubName.getOrElse("Unbekannter Verein")
            val club = TourneyDB.tourney.clubs.find(_.name == clubName).getOrElse {
              TourneyDB.tourney.addClub(clubName, checkSimilarity = false, doSync = false) match
                case Right(c) => c
                case Left(_) => Club(ClubId(0), clubName, Club.normalize(clubName))
            }

            // Create Player
            val playerMeta = PlayerMeta(
              internalNr = Some(p.internalNr),
              licenceNr = Some(lic),
              clubNr = p.clubNr,
              clubFedNick = p.clubFederationNickname,
              ttr = p.ttr,
              ttrMatchCnt = p.ttrMatchCount,
              nationality = p.nationality,
              foreignerEqState = p.foreignerEqState,
              region = p.region,
              subRegion = p.subRegion
            )

            val sex = p.sex match {
              case 1 => Sex.Male
              case 2 => Sex.Female
              case _ => Sex.Unknown
            }

            val bYear = try Some(p.birthyear.toInt) catch { case _:Exception => None }

            TourneyDB.tourney.addPlayer(p.firstname, p.lastname, club.id.toInt, bYear, doSync = false) match {
              case Right(player) => 
                // Update meta data which addPlayer doesn't set
                val updatedPlayer = player.copy(sex = sex, meta = playerMeta)
                TourneyDB.tourney.updatePlayer(updatedPlayer, doSync = false)
                licenseMap(lic) = updatedPlayer
              case Left(err) => 
                base.Logging.error(s"Failed to add player: ${err.msg}")
            }
      }
    }
    TourneyDB.tourney.syncPlayers()
    licenseMap.toMap

  private def mapPlayerToPant(cttPlayer: CttPlayer, personMap: Map[String, Player]): Option[Pant] =
    val persons = cttPlayer.persons
    if (persons.isEmpty) return None

    if (cttPlayer.typ == "Einzel" || persons.length == 1) {
      val p = persons.head
      personMap.get(p.licenceNr).map { player =>
        Pant(
          id = SNO.single(player.id),
          name = player.displayName,
          club = TourneyDB.tourney.clubs.find(_.id.toInt == player.clubId).map(_.name).getOrElse(""),
          rating = player.meta.ttr.getOrElse(0),
          status = PantStatus.REGI
        )
      }
    } else {
      // Double
      val p1Opt = persons.headOption.flatMap(p => personMap.get(p.licenceNr))
      val p2Opt = persons.lift(1).flatMap(p => personMap.get(p.licenceNr))
      
      for {
        player1 <- p1Opt
        player2 <- p2Opt
      } yield {
        val club1 = TourneyDB.tourney.clubs.find(_.id.toInt == player1.clubId).map(_.name).getOrElse("")
        val club2 = TourneyDB.tourney.clubs.find(_.id.toInt == player2.clubId).map(_.name).getOrElse("")
        
        Pant(
          id = SNO.double(player1.id, player2.id),
          name = s"${player1.lastName} / ${player2.lastName}",
          club = if (club1 == club2) club1 else s"$club1, $club2",
          rating = (player1.meta.ttr.getOrElse(0) + player2.meta.ttr.getOrElse(0)) / 2,
          status = PantStatus.REGI
        )
      }
    }
