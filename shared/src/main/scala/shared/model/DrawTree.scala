package shared.model

import scala.collection.mutable.{ Set, Map }

case class DrawTree[T](
  val name: String, 
  val size: Int, 
  var count: Int, 
  skipValue: T,
  var leftBound: (Int,Int) = (0,0),             
  var rightBound: (Int,Int) = (0,0), 
  val leftFree:  Set[Int] = Set[Int](),         
  val rightFree:  Set[Int] = Set[Int](),
  val leftOccu: Map[T,Int] = Map[T,Int](),      
  val rightOccu: Map[T,Int] = Map[T,Int](), 
  var leftTree: Option[DrawTree[T]] = None,     
  var rightTree: Option[DrawTree[T]] = None
):

  def getOcc(tree: DrawTree[T], pos: Int, item: T): Int = 
    if (tree.leftFree.contains(pos)) tree.leftTree match { 
      case None     => tree.leftOccu(item) 
      case Some(lT) => tree.leftOccu(item) + lT.getOcc(lT, pos, item) 
    } else if (tree.rightFree.contains(pos)) tree.rightTree match { 
      case None     => tree.rightOccu(item) 
      case Some(rT) => tree.rightOccu(item) + rT.getOcc(rT, pos, item)
    } else {
      println(s"ERROR: getOcc -> out of bound ${name} ${pos}"); size
    }

  def check4BestDrawPos(tree: DrawTree[T], pos1: Int, item1: T, pos2: Int, item2: T): Boolean = {
    val occEven = tree.getOcc(tree, pos1, item1) + tree.getOcc(tree, pos2, item2)
    val occOdd  = tree.getOcc(tree, pos2, item1) + tree.getOcc(tree, pos1, item2)
    println(s"INFO: check4BestDrawPos -> occEven: ${occEven} occOdd: ${occOdd}")
    (occEven <= occOdd) 
  }

  // addItem recursiv to all sub trees  
  def addItem(tree: DrawTree[T], pos: Int, item: T): Unit = {
    count = count + 1
    if (tree.leftFree.contains(pos)) {
      if (!(item == skipValue)) tree.leftOccu(item) = tree.leftOccu(item) + 1 
      tree.leftFree -= pos
      tree.leftTree match { case None => {}; case Some(lT) => lT.addItem(lT, pos, item) }
    } else if (tree.rightFree.contains(pos)) {
      if (!(item == skipValue)) tree.rightOccu(item) = tree.rightOccu(item) + 1
      tree.rightFree -= pos
      tree.rightTree match { case None => {}; case Some(rT) => rT.addItem(rT, pos, item) }
    } else println(s"ERROR: addItem -> no pos ${pos} for item ${item}") 
  }

object DrawTree:      
  def blanks(cnt :Int) = " " * 2 * cnt
  def treePrint[T](tree: Option[DrawTree[T]], level: Int = 0):Unit = tree match {
    case None    => {}
    case Some(t) => {
      println(s"${blanks(level)} ${t.name} size: ${t.size} count: ${t.count} leftFree: ${t.leftFree.mkString(":")} rightFree: ${t.rightFree.mkString(":")} leftOccu: ${t.leftOccu.mkString(" : ")} rightOccu: ${t.rightOccu.mkString(" : ")}")
      treePrint(t.leftTree, level + 1)
      treePrint(t.rightTree, level + 1)
    }
  }

  def isPowerOfTwo(number: Int): Boolean = 
    if (number == 0) false
    else if ((number & (number - 1)) == 0) true
    else false

  def init[T](lBound:Int, uBound: Int, skipValue: T): Option[DrawTree[T]] = {
    val size = uBound - lBound + 1
    if (!isPowerOfTwo(size)) println(s"ERROR: DrawTree.init -> not power of two lBound:${lBound} uBound:${uBound}")
    if (uBound-lBound > 2) {
      val middle = lBound + (uBound-lBound)/2
      val lFree = Set[Int]().addAll(lBound to middle)  
      val rFree = Set[Int]().addAll((middle+1) to uBound)
      Some(DrawTree(s"Range_${lBound}-${uBound}", size, 0, skipValue, (lBound, middle), (middle + 1, uBound),  
                    lFree, rFree, Map[T,Int]().withDefaultValue(0), Map[T,Int]().withDefaultValue(0), init(lBound, middle, skipValue), init(middle+1, uBound, skipValue)))
    } else None
  }

  def get[T](lBound:Int, uBound: Int, skipValue: T): DrawTree[T] = {
    init[T](lBound, uBound, skipValue).getOrElse(DrawTree(s"Dummy_${lBound}-${uBound}", uBound-lBound+1, 0, skipValue))  
  }
