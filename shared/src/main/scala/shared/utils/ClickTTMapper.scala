package shared.utils

import shared.model.*
import shared.basic.*
import scala.collection.mutable.ArrayBuffer

/**
 * ClickTTMapper handles the translation from ClickTT XML models to internal domain models.
 * This is a shared utility used by both the Play server and the Scala.js client.
 */
object ClickTTMapper:

  /**
   * Maps a CttTournament to a target Tourney object.
   * Note: This method populates the provided Tourney object's buffers.
   */
  def mapToTourney(ctt: CttTournament, target: Tourney): Either[AppError, Unit] =
    try
      // 1. Update Tourney Basic Data
      target.name = ctt.name
      target.startDate = formatDate(ctt.startDate)
      target.endDate = formatDate(ctt.endDate)
      target.ident = ctt.tournamentId
      target.category = CompCategory.TT
      
      ctt.locations.headOption.foreach { loc =>
        target.address = Some(Address(
          description = loc.name.getOrElse(""),
          street = loc.street.getOrElse(""),
          zip = loc.zipCode.getOrElse(""),
          city = loc.city.getOrElse(""),
          country = "DE"
        ))
      }

      // 2. Import Players and Clubs first (Multi-pass)
      // Collect all unique persons from all competitions
      val allCttPersons = ctt.competitions.flatMap(_.players).flatMap(_.persons)
      val personMap = importPersons(allCttPersons, target)

      // 3. Map Competitions and Pants
      ctt.competitions.zipWithIndex.foreach { case (cttComp, idx) =>
        val compId = CompId(idx + 1)
        
        // Construct Name: ttr-remarks + " " + age-group + " " + type
        val nameParts = Seq(cttComp.ttrRemarks, Some(cttComp.ageGroup), Some(cttComp.typ)).flatten
        val compName = nameParts.mkString(" ").trim

        val comp = Competition(
          id = compId,
          name = compName,
          typ = if (cttComp.typ.toLowerCase.contains("doppel")) CompTyp.DOUBLE else CompTyp.SINGLE,
          category = CompCategory.TT,
          startDate = cttComp.startDate,
          status = CompStatus.CFG,
          startStage = None,
          activ = true,
          webRegister = false,
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
          )),
          pants1Stage = ArrayBuffer(),
          deleted = false,
          version = 1
        )

        // Map CttPlayers to Pants
        cttComp.players.foreach { cttPlayer =>
          val mappedPant = mapPlayerToPant(cttPlayer, personMap, target)
          mappedPant.foreach { p =>
            p.ident = cttPlayer.id
            comp.pants1Stage += p
          }
        }

        // Add to local buffer and mark as dirty
        if (idx < target.competitions.length) {
          target.competitions(idx) = comp
          if (!target.dirtyCompetition.exists(_.id == comp.id)) target.dirtyCompetition += comp
        }
      }

      Right(())
    catch
      case e: Exception => 
        Left(AppError("mapping.failed", e.getMessage))

  private def formatDate(dateStr: String): Int =
    try dateStr.replace("-", "").take(8).toInt
    catch { case _: Exception => 0 }

  /**
   * Imports persons into target Tourney and returns a map of LicenseNr -> Player
   */
  private def importPersons(persons: Seq[CttPerson], target: Tourney): Map[String, Player] =
    val licenseMap = collection.mutable.Map[String, Player]()
    
    persons.foreach { p =>
      val lic = p.licenceNr
      if (lic.nonEmpty && !licenseMap.contains(lic)) {
        // Check if player already exists in the target tourney
        target.players.find(_.meta.licenceNr.contains(lic)) match
          case Some(existing) => 
            licenseMap(lic) = existing
          case None =>
            // Ensure Club exists
            val clubName = p.clubName.getOrElse("Unbekannter Verein")
            val club = target.clubs.find(_.name == clubName).getOrElse {
              target.addClub(clubName, checkSimilarity = false, doSync = false) match
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

            target.addPlayer(p.firstname, p.lastname, club.id.toInt, bYear, doSync = false) match {
              case Right(player) => 
                val updatedPlayer = player.copy(sex = sex, meta = playerMeta)
                target.updatePlayer(updatedPlayer, doSync = false)
                licenseMap(lic) = updatedPlayer
              case Left(_) => // Should not happen in bulk
            }
      }
    }
    licenseMap.toMap

  private def mapPlayerToPant(cttPlayer: CttPlayer, personMap: Map[String, Player], target: Tourney): Option[Pant] =
    val persons = cttPlayer.persons
    if (persons.isEmpty) return None

    if (cttPlayer.typ == "Einzel" || persons.length == 1) {
      val p = persons.head
      personMap.get(p.licenceNr).map { player =>
        Pant(
          id = SNO.single(player.id),
          name = player.displayName,
          club = target.clubs.find(_.id.toInt == player.clubId).map(_.name).getOrElse(""),
          rating = player.meta.ttr.getOrElse(0),
          birthYear = player.birthYear.map(_.toString).getOrElse(""),
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
        val club1 = target.clubs.find(_.id.toInt == player1.clubId).map(_.name).getOrElse("")
        val club2 = target.clubs.find(_.id.toInt == player2.clubId).map(_.name).getOrElse("")
        
        Pant(
          id = SNO.double(player1.id, player2.id),
          name = s"${player1.lastName} / ${player2.lastName}",
          club = if (club1 == club2) club1 else s"$club1, $club2",
          rating = (player1.meta.ttr.getOrElse(0) + player2.meta.ttr.getOrElse(0)) / 2,
          birthYear = "", // Or combined if needed
          status = PantStatus.REGI
        )
      }
    }
