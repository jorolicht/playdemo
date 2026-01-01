package shared

import scala.quoted.*
import shared.DomTypes.HtmlId


object BoxValueTypes:
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


// object PageTypes:
//   opaque type PageId = String
  
//   object PageId:
//     def apply(s: String): PageId = s

//     extension (id: PageId)
//       def asString: String = id

//     // Dieses Makro liest den Namen der Definition aus, der es zugewiesen wird
//     inline def myName: PageId = ${ useMyNameImpl }

//     private def useMyNameImpl(using Quotes): Expr[PageId] =
//       import quotes.reflect.*
//       // Greift den Namen des Symbols der umschließenden Definition
//       val name = Symbol.spliceOwner.name
//       Expr(name).asInstanceOf[Expr[PageId]]

//     // Die Methode nimmt jetzt einen Präfix-String entgegen
//     inline def fromName(inline suffix: String): PageId = 
//       ${ fromNameImpl('suffix) }

//     private def fromNameImpl(suffixExpr: Expr[String])(using Quotes): Expr[PageId] =
//       import quotes.reflect.*
//       // 1. Hole den Variablennamen zur Kompilierzeit
//       val varName = Symbol.spliceOwner.name.trim
      
//       // 2. Kombiniere Suffix und Name in einer neuen Expression
//       '{ ${Expr(varName)} + $suffixExpr   }.asInstanceOf[Expr[PageId]]



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
  opaque type HtmlId = String
  
  object HtmlId:
    def apply(s: String): HtmlId = s

    extension (id: HtmlId)
      def asString: String = id
    
    // Dieses Makro liest den Namen der Definition aus, der es zugewiesen wird
    inline def myName: HtmlId = ${ useMyNameImpl }

    private def useMyNameImpl(using Quotes): Expr[HtmlId] =
      import quotes.reflect.*
      // Greift den Namen des Symbols der umschließenden Definition
      val name = Symbol.spliceOwner.name
      Expr(name).asInstanceOf[Expr[HtmlId]]


    // Die Methode nimmt jetzt einen Präfix-String entgegen
    inline def fromName(inline prefix: String): HtmlId = 
      ${ fromNameImpl('prefix) }

    private def fromNameImpl(prefixExpr: Expr[String])(using Quotes): Expr[HtmlId] =
      import quotes.reflect.*
      // 1. Hole den Variablennamen zur Kompilierzeit
      val varName = Symbol.spliceOwner.name.trim
      
      // 2. Kombiniere Präfix und Name in einer neuen Expression
      '{ $prefixExpr + "_" + ${Expr(varName)} }.asInstanceOf[Expr[HtmlId]]
   