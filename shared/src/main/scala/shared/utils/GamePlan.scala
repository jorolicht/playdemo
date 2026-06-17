package shared.utils
  
case class GroupPlan (
  val nameCode : String,
  val size:      Int,  
  val noRounds:  Int,
  val noMatches: Int,
  val rounds:    Array[Array[(Int,Int)]]
) 

object GroupPlan {

  def getBye(pair: (Int,Int), value: Int) = if (value == pair._1) pair._2 else pair._1
  
  def rotateLeft[A](seq: Seq[A], i: Int): Seq[A] = {
    val size = seq.size
    seq.drop(i % size) ++ seq.take(i % size)
  }

  def rotateUntilByeIsHead(seq: Array[(Int,Int)], bye: Int ): Array[(Int,Int)] = {
    val index = seq.indexWhere(elem => (elem._1 == bye) || (elem._2 == bye))
    rotateLeft(seq, index).to(Array)
  } 

  def rotateUntilLastIsNotHead(seq: Array[(Int,Int)], last: (Int,Int)): Array[(Int,Int)] = {
    val index = seq.indexWhere(elem => (elem._1 != last._1) && (elem._1 != last._2)  && (elem._2 != last._1) && (elem._2 != last._2) )
    rotateLeft(seq, index).to(Array)
  } 

  def get(size: Int): GroupPlan = {
    val even    = size + size%2                        
    val noMpR   = even/2                                                            
    val rnds    = even - 1                           
    val rsArray = Array.ofDim[Int](rnds, noMpR)
    val rs2Array = Array.ofDim[Int](rnds, noMpR)

    val rsPair  = Array.ofDim[(Int,Int)](rnds, noMpR)
    val rsPair1 = Array.ofDim[(Int,Int)](rnds, noMpR)
    val rsPair2 = Array.ofDim[(Int,Int)](rnds, noMpR)

    // fill array as Richard Schurig stated
    var cnt = 1
    for (i<-0 until rnds; j<-0 until noMpR) {
      rsArray(i)(j) = if ((cnt % (rnds)) == 0) rnds else (cnt % (rnds))
      cnt = cnt +1 
    }
    
    // construct 2nd table
    for (i<-0 until rnds; j<-0 until noMpR) { rs2Array(i)(j) = if (j!=0) rsArray((i+1)%rnds)(noMpR-j-1) else even }
  
    // generate paaring, swap first column entry on every second row
    for (i<-0 until rnds; j<-0 until noMpR) {
      rsPair(i)(j) = if ((i%2)==1 && j==0)  (rs2Array(i)(j), rsArray(i)(j)) else (rsArray(i)(j), rs2Array(i)(j))
    }

    // step 1 - GENERATE PAIRING change rounds (first keep first .. last goes 2nd)
    for (i<-0 until rnds) { rsPair1(i) = rsPair(if (i==0) 0 else rnds-i) }

    // step 2 - OPTIMIZE: fix time to next game for each player/team
    if (size%2 == 1) { 
      // odd sized groups
      for (i<-0 until rnds)
        // first line  
        if (i == 0) rsPair2(0) = rsPair1(0).drop(1) ++ rsPair1(0).take(1)
        else        rsPair2(i) = rotateUntilByeIsHead(rsPair1(i).drop(1), getBye(rsPair1(i-1)(0), even) ) ++ rsPair1(i).take(1)
    } else {          
      // even sized groups
      for (i<-0 until rnds) 
        // keep first line
        if (i == 0) rsPair2(0) = rsPair1(0) 
        else        rsPair2(i) = rotateUntilLastIsNotHead(rsPair1(i), rsPair2(i-1)(noMpR-1) )
    }

    if (size%2==0) { 
      val noMatches = rnds * noMpR
      val resArray  = Array.ofDim[(Int,Int)](rnds, noMpR)
      GroupPlan (s"gameplan.group.${size}", size, rnds, noMatches, rsPair2)
    } else { 
      val noMpROdd = noMpR-1
      val noMatches = rnds * noMpROdd
      val resArray  = Array.ofDim[(Int,Int)](rnds, noMpROdd)      
      for (i<-0 until rnds) resArray(i) = rsPair2(i).dropRight(1)
      GroupPlan (s"gameplan.group.${size}", size, rnds, noMatches, resArray) 
    }
  }  

}