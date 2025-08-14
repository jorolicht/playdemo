package base

import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal
import org.scalajs.dom
import org.scalajs.dom.html.Input
import org.scalajs.dom.raw.HTMLElement

import shared.Ids._

trait JsWrapper:

  /** getLocalStorage
    *
    * @param name of storage element
    */
  def getLocalStorage(name: String, default: String =""): String = 
    try 
      val s = dom.window.localStorage.getItem(s"${Global.localStoragePrefix}${name}") 
      if (s == null) || (s == "" ) then default else s
    catch { case _: Throwable => error(s"getLocalStorage -> name:${Global.localStoragePrefix}${name}"); "" }

  /** setLocalStorage
    *
    * @param name of storage element
    * @param content new valueo storage element
    */
  def setLocalStorage(name: String, content: String): Unit = 
    try dom.window.localStorage.setItem(s"${Global.localStoragePrefix}${name}", content)
    catch { case _: Throwable => error(s"setLocalStorage -> name:${Global.localStoragePrefix}${name} content:${content.take(10)}") }
    
  def displayProperty(visible: Boolean): String = if (visible) "block" else "none"

  def gE(id: String, withWarning: Boolean=true):HTMLElement = 
    try 
      val elem = dom.document.getElementById(id).asInstanceOf[HTMLElement]
      if (elem == null && withWarning) warn(s"gE -> id:${id} null")
      elem
    catch { case _: Throwable => error(s"gE -> id:${id}"); null } 


  def setVisible(elem: HTMLElement, visible: Boolean) = 
    try elem.style.setProperty("display", displayProperty(visible))
    catch { case _: Throwable => error(s"setVisible -> elem:${elem} visible:${visible}") }


  /** setHtml
    *
    * @param elem    - HTMLElement
    * @param content - html content of element
    */
  def setHtml[C](elem: HTMLElement, content: => C = ""): Unit = {
    val value = try content match 
      case _:String => content.asInstanceOf[String]
      case _        => content.toString
    catch { case _: Throwable => error(s"setHtml -> elem.id:${elem.id} content unknown"); "unknwon" }
    
    try    elem.innerHTML = value
    catch { case _: Throwable => 
      if   (elem == null) error(s"setHtml -> elem:null content: ${value.take(10)}") 
      else error(s"setHtml -> elem.id:${elem.id} content: ${value.take(10)}") 
    }
  }

  /** setMain
    *
    * @param content - html content or string content
    */
  def setMain[C](content: => C = ""): Boolean = 
    val elem = gE(Main_Content)
    val value = try content match 
      case _:String => content.asInstanceOf[String]
      case _        => content.toString
    catch { case _: Throwable => error(s"setMain -> content unknown"); "unknwon" }
    
    try   { elem.innerHTML = value; true }
    catch { case _: Throwable => 
      if   (elem == null) error(s"setMain -> elem:null content: ${value.take(10)}") 
      else error(s"setMain -> content: ${value.take(10)}")
      false
    }  


  def insertHtml[C](elem: HTMLElement, pos: String, content: C): Unit = 
    try content match 
      case _:play.twirl.api.Html => elem.insertAdjacentHTML(pos, content.toString)
      case _:String              => elem.insertAdjacentHTML(pos, content.asInstanceOf[String])
    catch { case _: Throwable => error(s"insertHtml -> elem:${elem.id} pos:${pos} content:${content.toString.take(10)}") } 

  /**
    * getData read data attribute from Html element 
    *
    * @param elem
    * @param name
    * @param default
    * @return
    */
  def getData[A](elem: HTMLElement, name: String, default: A): A = 
    try default match 
      case _:Int    => elem.getAttribute(s"data-${name}").toIntOption.getOrElse(default.asInstanceOf[Int]).asInstanceOf[A]
      case _:Long   => elem.getAttribute(s"data-${name}").toLongOption.getOrElse(default.asInstanceOf[Long]).asInstanceOf[A]
      case _:String => elem.getAttribute(s"data-${name}").asInstanceOf[A]
      case _        => { info(s"getData -> elem:${elem.id} name:${name} default: ${default}"); default }
    catch { case _: Throwable => error(s"getData -> elem:${elem.id} name:${name} default:${default}"); default }

  def setData[A](elem: HTMLElement, attr: String, value: A) = 
    try elem.setAttribute(s"data-${attr}", value.toString)
    catch { case _: Throwable => error(s"setData -> elem:${elem.id} attribute:${attr} value:${value}") }
    

  def addClass(elem: HTMLElement, _class: String*): Unit = 
    try _class.foreach(cValue => elem.classList.add(cValue))
    catch { case _: Throwable => error(s"addClass -> elem:${elem.id} class:${_class}") } 


  def removeClass(elem: HTMLElement, _class: String*): Unit = 
    try _class.foreach(cValue => elem.classList.remove(cValue))
    catch { case _: Throwable => error(s"removeClass -> elem:${elem.id} class:${_class}") } 

  def changeClass(elem: HTMLElement, check: Boolean, _class: String*): Unit =   
    if (check) addClass(elem, _class:_*) else removeClass(elem, _class:_*) 

  def toggleClass(elem: HTMLElement, _class: String): Unit = 
    try elem.classList.toggle(_class)
    catch { case _: Throwable => error(s"toggleClass -> elem:${elem.id} class:${_class}") }   


  /**
    * getInput read input value from Html input element 
    *
    * @param name
    * @param defVal
    * @return
    */
  def getInput[R](elem: HTMLElement, defVal: R = ""): R = 
    try defVal match
      case _:Boolean => elem.asInstanceOf[Input].checked.asInstanceOf[R]
      case _:Int     => elem.asInstanceOf[Input].value.toIntOption.getOrElse(defVal).asInstanceOf[R]
      case _:Long    => elem.asInstanceOf[Input].value.toLongOption.getOrElse(defVal).asInstanceOf[R]
      case _:String  => { val in = elem.asInstanceOf[Input].value.asInstanceOf[R]; if (in=="") defVal else in }  
      case _         => error(s"getInput -> elem: ${elem} defVal: ${defVal}"); defVal
    catch { case _: Throwable => error(s"getInput -> elem:${elem} defVal:${defVal}"); defVal }


  def setInput(elem: HTMLElement, text: String): Unit =
    try elem.asInstanceOf[Input].value = text
    catch { case _: Throwable => error(s"setInput -> elem:${elem.id}") }


  def setNavLink(uc: String) =
    val navLinkNodes = gE("sidebar").querySelectorAll("[data-usecase]")
    for( i <- 0 to navLinkNodes.length-1)
      val elem = navLinkNodes.item(i).asInstanceOf[HTMLElement]
      changeClass(elem, uc==getData(elem, "usecase", "") , "bg-primary")
    println(s"set navlink: ${uc} ")


  /**
   * Liest den Wert eines spezifischen Cookies aus dem Browser.
   * @param name Der Name des Cookies (z.B. "jwt_token").
   * @return Der Wert des Cookies als Option[String].
   */
  def getCookie(name: String): Option[String] = {
    // Greift auf das document.cookie Property zu
    val x = dom.document.cookie.split(';')
    println(s"Cookie Result: ${x.mkString(":")}")
    dom.document.cookie.split(';').map(_.trim).collectFirst {
      case s if s.startsWith(s"$name=") => s.substring(s"$name=".length)
    }
  }   