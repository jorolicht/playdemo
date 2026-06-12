package shared.model

import shared.basic.Pickle.*

case class Selection(
  tourney:     Option[Tourney]     = None,
  competition: Option[Competition] = None,
  stage:       Option[Stage]       = None
) derives ReadWriter
