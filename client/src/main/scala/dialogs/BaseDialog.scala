package dialogs

import org.scalajs.dom.Event
import org.scalajs.dom.raw.HTMLElement
import shared.DialogTypes.DialogId


// dlgMap maps dialog names to dialog objects   
val dlgMap = List(DlgMsgbox, DlgPrompt, DlgClickTT, DlgCompetition, DlgRoundStart, DlgAddSingle, DlgAddDouble)
                    .map(dlg => dlg.name -> dlg).toMap

object Ids:
  import shared.DialogTypes.DialogId
  val DlgMsgboxId:  DialogId = DialogId.myName
  val DlgPromptId:  DialogId = DialogId.myName
  val DlgClickTTId: DialogId = DialogId.myName


abstract class BaseDialog extends comps.BaseComp:
  
  override def handleEvent(elem: HTMLElement, event: Event): Unit = {}
