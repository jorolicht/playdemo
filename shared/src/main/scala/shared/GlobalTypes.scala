package shared

import scala.quoted.*
import sourcecode.Name

object PageNameTyp:
  opaque type PageName = String

  def apply(value: String): PageName =
    value.trim

  extension (u: PageName)
    def value: String = u



object DialogTypes:
  opaque type DialogId = String
  
  object DialogId:
    def apply(s: String): DialogId = s

    extension (id: DialogId)
      def asString: String = id

    // Dieses Makro liest den Namen der Definition aus, der es zugewiesen wird
    inline def myName: DialogId = ${ useMyNameImpl }

    private def useMyNameImpl(using Quotes): Expr[DialogId] =
      import quotes.reflect.*
      // Greift den Namen des Symbols der umschließenden Definition
      val name = Symbol.spliceOwner.name
      Expr(name).asInstanceOf[Expr[DialogId]]

    // Die Methode nimmt jetzt einen Präfix-String entgegen
    inline def fromName(inline suffix: String): DialogId = 
      ${ fromNameImpl('suffix) }

    private def fromNameImpl(suffixExpr: Expr[String])(using Quotes): Expr[DialogId] =
      import quotes.reflect.*
      // 1. Hole den Variablennamen zur Kompilierzeit
      val varName = Symbol.spliceOwner.name.trim
      
      // 2. Kombiniere Suffix und Name in einer neuen Expression
      '{ ${Expr(varName)} + $suffixExpr   }.asInstanceOf[Expr[DialogId]]



object DomTypes:
  import PageNameTyp.PageName
  import scala.annotation.targetName
  case class HtmlId(id: String)

  // Die Extension-Methode
  extension (htmlId: HtmlId)
    def addPrefix(suffix: String): HtmlId = HtmlId(suffix + "_" + htmlId.id)
    def pref = htmlId.id.takeWhile(_ != '_')

  @targetName("genIdFromString")
  def genId(prefix: String="")(using name: Name): HtmlId = 
    val x = name.value
    if (prefix == "") then HtmlId(name.value) else HtmlId(x).addPrefix(prefix)

  @targetName("genIdFromPageName")
  def genId(prefix: PageName)(using name: Name): HtmlId = 
    val x = name.value
    HtmlId(x).addPrefix(prefix.value)   

   