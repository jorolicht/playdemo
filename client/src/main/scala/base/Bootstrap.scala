package base

import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal

object Bootstrap:

  @js.native
  @JSGlobal("bootstrap.Modal")
  class Modal(elem: js.Object) extends js.Any: 
    def hide():Unit = js.native
    def show():Unit = js.native  

  @js.native
  @JSGlobal("bootstrap.Collapse")
  class Collapse(elem: js.Object) extends js.Any: 
    def hide():Unit   = js.native
    def show():Unit   = js.native    
    def toggle():Unit = js.native  
