package shared

import scala.quoted.*
import sourcecode.Name


object BoxValueTypes:
  import DomTypes.HtmlId
  opaque type BoxValue = String

  object BoxValue:
    def apply(s: String): BoxValue = s

    extension (bv: BoxValue)
      def asString: String = bv
      def msgCode: String = "btn." + bv.toLowerCase
      def getId: HtmlId = HtmlId("BoxValue_" + bv)

    // Dieses Makro liest den Namen der Definition aus, der es zugewiesen wird
    inline def myName: BoxValue = ${ useMyNameImpl }

    private def useMyNameImpl(using Quotes): Expr[BoxValue] =
      import quotes.reflect.*
      // Greift den Namen des Symbols der umschließenden Definition
      val name = Symbol.spliceOwner.name
      Expr(name).asInstanceOf[Expr[BoxValue]]


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

  case class HtmlId(id: String)

  // Die Extension-Methode
  extension (htmlId: HtmlId)
    def addPrefix(suffix: String): HtmlId = HtmlId(suffix + "_" + htmlId.id)
    def pref = htmlId.id.takeWhile(_ != '_')

  def genId(prefix: String="")(using name: Name): HtmlId = 
    val x = name.value
    if (prefix == "") then HtmlId(name.value) else HtmlId(x).addPrefix(prefix) 


  // opaque type HtmlId = String
  
  // object HtmlId:
  //   def apply(s: String): HtmlId = s

  //   extension (id: HtmlId)
  //     def asString: String = id
    
  //   // Dieses Makro liest den Namen der Definition aus, der es zugewiesen wird
  //   inline def myName: HtmlId = ${ useMyNameImpl }

  //   private def useMyNameImpl(using Quotes): Expr[HtmlId] =
  //     import quotes.reflect.*
  //     // Greift den Namen des Symbols der umschließenden Definition
  //     val name = Symbol.spliceOwner.name
  //     Expr(name).asInstanceOf[Expr[HtmlId]]


  //   // Die Methode nimmt jetzt einen Präfix-String entgegen
  //   inline def fromName(inline prefix: String): HtmlId = 
  //     ${ fromNameImpl('prefix) }

  //   // private def fromNameImpl(prefixExpr: Expr[String])(using Quotes): Expr[HtmlId] =
  //   //   import quotes.reflect.*
  //   //   // 1. Hole den Variablennamen zur Kompilierzeit
  //   //   val varName = Symbol.spliceOwner.name.trim
      
  //   //   // 2. Kombiniere Präfix und Name in einer neuen Expression
  //   //   '{ $prefixExpr + "_" + ${Expr(varName)} }.asInstanceOf[Expr[HtmlId]]


  //   private def fromNameImpl(prefixExpr: Expr[String])(using Quotes): Expr[HtmlId] =
  //     import quotes.reflect.*

  //     // Wir wandern den Baum hoch, bis wir eine Definition (Variable/Methode) finden
  //     def findName(sym: Symbol): String =
  //       if (sym.isNoSymbol) "unknown"
  //       else if (sym.isValDef || sym.isDefDef) sym.name
  //       else findName(sym.owner)
  //     val varName = findName(Symbol.spliceOwner).trim
  //     '{ $prefixExpr + "_" + ${Expr(varName)} }.asInstanceOf[Expr[HtmlId]]
   