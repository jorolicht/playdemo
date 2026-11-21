package shared.format

import scala.collection.mutable.ArrayBuffer

/**
 * Maximum-Weight-Matching in allgemeinen (nicht bipartiten) Graphen nach Edmonds (Blossom-Algorithmus) in der
 * O(n³)-Formulierung von Galil ("Efficient Algorithms for Finding Maximum Matching in Graphs", 1986) mit
 * Dualvariablen, wie sie auch in der verbreiteten Referenzimplementierung von J. van Rantwijk verwendet wird.
 *
 * Gewichte sind ganzzahlig (Long); alle Zwischenwerte bleiben ganzzahlig, die Rechnung ist damit exakt.
 * Verwendung im Schweizer System: [[SwissMatcher.computePairings]].
 */
object MaxWeightMatching:

  /**
   * Kante des Graphen.
   *
   * @param i      Knoten 1 (0-basiert)
   * @param j      Knoten 2 (0-basiert, ungleich i)
   * @param weight Gewicht (größer = besser)
   */
  case class Edge(i: Int, j: Int, weight: Long)

  /**
   * Berechnet ein Matching mit maximalem Gesamtgewicht.
   *
   * @param edges          Kanten (je Knotenpaar höchstens eine)
   * @param maxCardinality true: unter allen Matchings mit maximaler Kantenzahl das schwerste
   *                       (bei vollständigem Graphen mit gerader Knotenzahl: perfektes Matching)
   * @return mate(v) = Partner von Knoten v oder -1 (Länge = Anzahl Knoten)
   */
  def compute(edges: Seq[Edge], maxCardinality: Boolean = true): Array[Int] =
    if edges.isEmpty then return Array.empty
    val es       = edges.toArray
    val nedge    = es.length
    val nvertex  = es.map(e => math.max(e.i, e.j)).max + 1
    val maxW     = math.max(0L, es.map(_.weight).max)

    // endpoint(p): Knoten am Kantenende p (Kante p/2, Ende p%2); neighbend(v): Kantenenden, die von v wegführen
    val endpoint  = Array.tabulate(2 * nedge)(p => if p % 2 == 0 then es(p / 2).i else es(p / 2).j)
    val neighbend = Array.fill(nvertex)(ArrayBuffer.empty[Int])
    for k <- 0 until nedge do
      neighbend(es(k).i) += 2 * k + 1
      neighbend(es(k).j) += 2 * k

    val mate             = Array.fill(nvertex)(-1)          // Kantenende zum Partner, -1 = frei
    val label            = Array.fill(2 * nvertex)(0)       // 0 frei, 1 = S, 2 = T (Knoten bzw. äußere Blüte)
    val labelend         = Array.fill(2 * nvertex)(-1)
    val inblossom        = Array.tabulate(nvertex)(identity)
    val blossomparent    = Array.fill(2 * nvertex)(-1)
    val blossomchilds    = Array.fill[ArrayBuffer[Int]](2 * nvertex)(null)
    val blossombase      = Array.tabulate(2 * nvertex)(i => if i < nvertex then i else -1)
    val blossomendps     = Array.fill[ArrayBuffer[Int]](2 * nvertex)(null)
    val bestedge         = Array.fill(2 * nvertex)(-1)
    val blossombestedges = Array.fill[ArrayBuffer[Int]](2 * nvertex)(null)
    val unusedblossoms   = ArrayBuffer.from(nvertex until 2 * nvertex)
    val dualvar          = Array.tabulate(2 * nvertex)(i => if i < nvertex then maxW else 0L)
    val allowedge        = Array.fill(nedge)(false)
    val queue            = ArrayBuffer.empty[Int]

    def slack(k: Int): Long = dualvar(es(k).i) + dualvar(es(k).j) - 2 * es(k).weight

    /** Zyklischer Zugriff wie Pythons negative Indizes. */
    def at(buf: ArrayBuffer[Int], j: Int): Int = buf(((j % buf.length) + buf.length) % buf.length)

    def blossomLeaves(b: Int): Seq[Int] =
      if b < nvertex then Seq(b)
      else blossomchilds(b).toSeq.flatMap(t => if t < nvertex then Seq(t) else blossomLeaves(t))

    def assignLabel(w: Int, t: Int, p: Int): Unit =
      val b = inblossom(w)
      label(w) = t; label(b) = t
      labelend(w) = p; labelend(b) = p
      bestedge(w) = -1; bestedge(b) = -1
      if t == 1 then queue ++= blossomLeaves(b)
      else if t == 2 then
        val base = blossombase(b)
        assignLabel(endpoint(mate(base)), 1, mate(base) ^ 1)

    /** Sucht von v und w aus die gemeinsame Basis (neue Blüte) oder -1 (augmentierender Pfad). */
    def scanBlossom(v0: Int, w0: Int): Int =
      var v = v0; var w = w0
      val path = ArrayBuffer.empty[Int]
      var base = -1
      var done = false
      while !done && (v != -1 || w != -1) do
        var b = inblossom(v)
        if (label(b) & 4) != 0 then
          base = blossombase(b); done = true
        else
          path += b
          label(b) = 5
          if labelend(b) == -1 then v = -1
          else
            v = endpoint(labelend(b))
            b = inblossom(v)
            v = endpoint(labelend(b))
          if w != -1 then { val t = v; v = w; w = t }
      path.foreach(b => label(b) = 1)
      base

    def addBlossom(base: Int, k: Int): Unit =
      var v = es(k).i; var w = es(k).j
      val bb = inblossom(base)
      var bv = inblossom(v)
      var bw = inblossom(w)
      val b = unusedblossoms.remove(unusedblossoms.length - 1)
      blossombase(b) = base
      blossomparent(b) = -1
      blossomparent(bb) = b
      val path  = ArrayBuffer.empty[Int]
      val endps = ArrayBuffer.empty[Int]
      while bv != bb do
        blossomparent(bv) = b
        path += bv
        endps += labelend(bv)
        v = endpoint(labelend(bv))
        bv = inblossom(v)
      path += bb
      val rpath = path.reverse; val rendps = endps.reverse
      path.clear(); path ++= rpath; endps.clear(); endps ++= rendps
      endps += 2 * k
      while bw != bb do
        blossomparent(bw) = b
        path += bw
        endps += (labelend(bw) ^ 1)
        w = endpoint(labelend(bw))
        bw = inblossom(w)
      blossomchilds(b) = path
      blossomendps(b) = endps
      label(b) = 1
      labelend(b) = labelend(bb)
      dualvar(b) = 0
      for lv <- blossomLeaves(b) do
        if label(inblossom(lv)) == 2 then queue += lv
        inblossom(lv) = b
      val bestedgeto = Array.fill(2 * nvertex)(-1)
      for cbv <- path do
        val nblists: Seq[Seq[Int]] =
          if blossombestedges(cbv) == null then blossomLeaves(cbv).map(lv => neighbend(lv).toSeq.map(_ / 2))
          else Seq(blossombestedges(cbv).toSeq)
        for nblist <- nblists; kk <- nblist do
          var i = es(kk).i; var j = es(kk).j
          if inblossom(j) == b then { val t = i; i = j; j = t }
          val bj = inblossom(j)
          if bj != b && label(bj) == 1 && (bestedgeto(bj) == -1 || slack(kk) < slack(bestedgeto(bj))) then
            bestedgeto(bj) = kk
        blossombestedges(cbv) = null
        bestedge(cbv) = -1
      blossombestedges(b) = ArrayBuffer.from(bestedgeto.filter(_ != -1))
      bestedge(b) = -1
      for kk <- blossombestedges(b) do
        if bestedge(b) == -1 || slack(kk) < slack(bestedge(b)) then bestedge(b) = kk

    def expandBlossom(b: Int, endstage: Boolean): Unit =
      for s <- blossomchilds(b) do
        blossomparent(s) = -1
        if s < nvertex then inblossom(s) = s
        else if endstage && dualvar(s) == 0 then expandBlossom(s, endstage)
        else blossomLeaves(s).foreach(lv => inblossom(lv) = s)
      if !endstage && label(b) == 2 then
        val childs = blossomchilds(b); val endps = blossomendps(b)
        val entrychild = inblossom(endpoint(labelend(b) ^ 1))
        var j = childs.indexOf(entrychild)
        val (jstep, endptrick) =
          if (j & 1) != 0 then { j -= childs.length; (1, 0) } else (-1, 1)
        var p = labelend(b)
        while j != 0 do
          label(endpoint(p ^ 1)) = 0
          label(endpoint(at(endps, j - endptrick) ^ endptrick ^ 1)) = 0
          assignLabel(endpoint(p ^ 1), 2, p)
          allowedge(at(endps, j - endptrick) / 2) = true
          j += jstep
          p = at(endps, j - endptrick) ^ endptrick
          allowedge(p / 2) = true
          j += jstep
        var bv = at(childs, j)
        label(endpoint(p ^ 1)) = 2; label(bv) = 2
        labelend(endpoint(p ^ 1)) = p; labelend(bv) = p
        bestedge(bv) = -1
        j += jstep
        while at(childs, j) != entrychild do
          bv = at(childs, j)
          if label(bv) == 1 then j += jstep
          else
            val leaves = blossomLeaves(bv)
            val v = leaves.find(lv => label(lv) != 0).getOrElse(leaves.last)
            if label(v) != 0 then
              label(v) = 0
              label(endpoint(mate(blossombase(bv)))) = 0
              assignLabel(v, 2, labelend(v))
            j += jstep
      label(b) = -1; labelend(b) = -1
      blossomchilds(b) = null; blossomendps(b) = null
      blossombase(b) = -1
      blossombestedges(b) = null
      bestedge(b) = -1
      unusedblossoms += b

    def augmentBlossom(b: Int, v: Int): Unit =
      var t = v
      while blossomparent(t) != b do t = blossomparent(t)
      if t >= nvertex then augmentBlossom(t, v)
      val childs = blossomchilds(b); val endps = blossomendps(b)
      val i = childs.indexOf(t)
      var j = i
      val (jstep, endptrick) =
        if (i & 1) != 0 then { j -= childs.length; (1, 0) } else (-1, 1)
      while j != 0 do
        j += jstep
        t = at(childs, j)
        val p = at(endps, j - endptrick) ^ endptrick
        if t >= nvertex then augmentBlossom(t, endpoint(p))
        j += jstep
        t = at(childs, j)
        if t >= nvertex then augmentBlossom(t, endpoint(p ^ 1))
        mate(endpoint(p)) = p ^ 1
        mate(endpoint(p ^ 1)) = p
      blossomchilds(b) = childs.drop(i) ++ childs.take(i)
      blossomendps(b) = endps.drop(i) ++ endps.take(i)
      blossombase(b) = blossombase(blossomchilds(b)(0))

    def augmentMatching(k: Int): Unit =
      for (s0, p0) <- Seq((es(k).i, 2 * k + 1), (es(k).j, 2 * k)) do
        var s = s0; var p = p0
        var go = true
        while go do
          val bs = inblossom(s)
          if bs >= nvertex then augmentBlossom(bs, s)
          mate(s) = p
          if labelend(bs) == -1 then go = false
          else
            val t = endpoint(labelend(bs))
            val bt = inblossom(t)
            s = endpoint(labelend(bt))
            val j = endpoint(labelend(bt) ^ 1)
            if bt >= nvertex then augmentBlossom(bt, j)
            mate(j) = labelend(bt)
            p = labelend(bt) ^ 1

    // ---- Hauptschleife: je Stufe höchstens eine Augmentierung ----
    var stage = 0
    var finished = false
    while !finished && stage < nvertex do
      stage += 1
      java.util.Arrays.fill(label, 0)
      java.util.Arrays.fill(bestedge, -1)
      for b <- nvertex until 2 * nvertex do blossombestedges(b) = null
      java.util.Arrays.fill(allowedge, false)
      queue.clear()
      for v <- 0 until nvertex do
        if mate(v) == -1 && label(inblossom(v)) == 0 then assignLabel(v, 1, -1)
      var augmented = false
      var stageDone = false
      while !stageDone do
        while queue.nonEmpty && !augmented do
          val v = queue.remove(queue.length - 1)
          val it = neighbend(v).iterator
          while it.hasNext && !augmented do
            val p = it.next()
            val k = p / 2
            val w = endpoint(p)
            if inblossom(v) != inblossom(w) then
              var kslack = 0L
              if !allowedge(k) then
                kslack = slack(k)
                if kslack <= 0 then allowedge(k) = true
              if allowedge(k) then
                if label(inblossom(w)) == 0 then assignLabel(w, 2, p ^ 1)
                else if label(inblossom(w)) == 1 then
                  val base = scanBlossom(v, w)
                  if base >= 0 then addBlossom(base, k)
                  else { augmentMatching(k); augmented = true }
                else if label(w) == 0 then
                  label(w) = 2
                  labelend(w) = p ^ 1
              else if label(inblossom(w)) == 1 then
                val b = inblossom(v)
                if bestedge(b) == -1 || kslack < slack(bestedge(b)) then bestedge(b) = k
              else if label(w) == 0 then
                if bestedge(w) == -1 || kslack < slack(bestedge(w)) then bestedge(w) = k
        if augmented then stageDone = true
        else
          // Dualvariablen anpassen
          var deltatype = -1
          var delta = 0L
          var deltaedge = -1
          var deltablossom = -1
          if !maxCardinality then
            deltatype = 1
            delta = (0 until nvertex).map(dualvar(_)).min
          for v <- 0 until nvertex do
            if label(inblossom(v)) == 0 && bestedge(v) != -1 then
              val d = slack(bestedge(v))
              if deltatype == -1 || d < delta then { delta = d; deltatype = 2; deltaedge = bestedge(v) }
          for b <- 0 until 2 * nvertex do
            if blossomparent(b) == -1 && label(b) == 1 && bestedge(b) != -1 then
              val d = slack(bestedge(b)) / 2
              if deltatype == -1 || d < delta then { delta = d; deltatype = 3; deltaedge = bestedge(b) }
          for b <- nvertex until 2 * nvertex do
            if blossombase(b) >= 0 && blossomparent(b) == -1 && label(b) == 2 && (deltatype == -1 || dualvar(b) < delta) then
              delta = dualvar(b); deltatype = 4; deltablossom = b
          if deltatype == -1 then
            deltatype = 1
            delta = math.max(0L, (0 until nvertex).map(dualvar(_)).min)
          for v <- 0 until nvertex do
            if label(inblossom(v)) == 1 then dualvar(v) -= delta
            else if label(inblossom(v)) == 2 then dualvar(v) += delta
          for b <- nvertex until 2 * nvertex do
            if blossombase(b) >= 0 && blossomparent(b) == -1 then
              if label(b) == 1 then dualvar(b) += delta
              else if label(b) == 2 then dualvar(b) -= delta
          deltatype match
            case 1 => stageDone = true
            case 2 =>
              allowedge(deltaedge) = true
              var i = es(deltaedge).i; var j = es(deltaedge).j
              if label(inblossom(i)) == 0 then { val t = i; i = j; j = t }
              queue += i
            case 3 =>
              allowedge(deltaedge) = true
              queue += es(deltaedge).i
            case _ =>
              expandBlossom(deltablossom, false)
      if !augmented then finished = true
      else
        for b <- nvertex until 2 * nvertex do
          if blossomparent(b) == -1 && blossombase(b) >= 0 && label(b) == 1 && dualvar(b) == 0 then
            expandBlossom(b, true)

    mate.map(m => if m >= 0 then endpoint(m) else -1)
