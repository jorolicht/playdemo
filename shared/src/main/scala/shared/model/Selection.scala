package shared.model

import shared.basic.Pickle.*

case class Selection(
  tourney:     Option[Tourney]     = None,
  competition: Option[Competition] = None,
  round:       Option[Round]       = None
) derives ReadWriter
