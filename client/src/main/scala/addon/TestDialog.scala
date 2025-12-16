package addon

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

import shared.model.AppError

object TestDialog:

  def exec(group: String, number: Int, param: String): Future[Either[AppError, String]] =
    number match 
      case 1 => testBasic_Msgbox(group, number, param)
      case _ => 
        addOutput(s"FAILED: ${group}-Test:${number} param:${param} unknown test number")
        Future(Left(AppError("unknonw test number")))


  def testBasic_Msgbox(group: String, number: Int, param: String): Future[Either[AppError, String]] =
    import dialog.DlgMsgbox
    import dialog.BtnMsgbox.*                
    DlgMsgbox.show("body text", "title text", List(Cancel, Ok)).map { x => addOutput(x.name); Right(x.name) }