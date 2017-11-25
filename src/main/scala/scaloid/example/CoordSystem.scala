package org.openstreetmap.sample

import scala.collection.immutable._
import scala.language.implicitConversions


case class MapCoordinateSystem(start: Math.Vector2, basis: Math.Matrix2, scale: Int) {
  lazy val inverse = basis.inv

  def toScreen(r: Math.Vector2): Math.Vector2 = basis * (r - start)
  def toWorldXY(r: Math.Vector2): Math.Vector2 = (inverse * r) + start

  def moveInScreen(from: Math.Vector2, to: Math.Vector2) = {
    val delta = toWorldXY(to) - toWorldXY(from)
    MapCoordinateSystem(start - delta, basis, scale)
  }

  def transformInScreen(
    prevFingers: (Math.Vector2, Math.Vector2), 
    nextFingers: (Math.Vector2, Math.Vector2)) = {
    // 'prevFingers' is equal to 'nextFingers' in a
    // world coordinate system
    val pf1 = toWorldXY(prevFingers._1)
    val pf2 = toWorldXY(prevFingers._2)

    // NBI * nextFingers._1 + NS = pf1
    // NBI * nextFingers._2 + NS = pf2
    // find NBI, NS  -> new start, new inverse basis
    /*
     X0  * nf1.0 + X1 * nf1.1 = pf1.0 - y0
     -X1 * nf1.0 + X0 * nf1.1 = pf1.1 - y1; X = (X0, X1), - inverse new basis, NBI
     X0  * nf2.0 + X1 * nf2.1 = pf2.0 - y0; y = (y0, y1) - new start, NS
     -X1 * nf2.0 + X0 * nf2.1 = pf2.1 - y1

     orthoBasis2(nf1.0, nf1.1) * X = pf1 - y
     orthoBasis2(nf2.0, nf2.1) * X = pf2 - y

     X = orthoBasis2(nf1).inv * (pf1 - y)
     X = orthoBasis2(nf2).inv * (pf2 - y)

     (orthoBasis2(nf2).inv - orthoBasis2(nf1).inv) * y = orthoBasis2(nf2).inv * pf2 - orthoBasis2(nf1).inv * pf1
     y = (orthoBasis2(nf2).inv - orthoBasis2(nf1).inv).inv * (orthoBasis2(nf2).inv * pf2 - orthoBasis2(nf1).inv * pf1)
     */

    val obnf1 = Math.orthoMatrix2(nextFingers._1.x, nextFingers._1.y)
    val obnf2 = Math.orthoMatrix2(nextFingers._2.x, nextFingers._2.y)

    val y = (obnf2.inv - obnf1.inv).inv * (obnf2.inv * pf2 - obnf1.inv * pf1)
    val x = obnf1.inv * (pf1 - y)

    MapCoordinateSystem(y, Math.orthoMatrix1(x.x, x.y).inv, scale)
  }

  def toMatrix: android.graphics.Matrix = {
    val result = new android.graphics.Matrix()
    val (x00, x01, x10, x11) = (basis.x00.toFloat, basis.x01.toFloat, basis.x10.toFloat, basis.x11.toFloat)
    val (x, y) = (start.x.toFloat, start.y.toFloat)
    val array = Array[Float](
      x00, x01, -x00 * x - x01 * y,
      x10, x11, -x10 * x - x11 * y,
      0.0f, 0.0f, 1.0f)

    result.setValues(array)
    result
  }

  def bfs(
    q: Queue[(Int, Int)],
    neighbours: => (Int, Int) => Seq[(Int, Int)] ,
    visited: Set[(Int, Int)] = Set.empty): Stream[(Int, Int)] = {

    if (q.isEmpty) Stream.empty
    else if (visited.contains(q.head)) bfs(q.tail, neighbours, visited)
    else q.head #:: bfs(
      q.tail ++ neighbours(q.head._1, q.head._2), 
      neighbours, 
      visited + q.head)
  }

  def visibleTiles(screenW: Int, screenH: Int): Seq[(Int, Int)] = {
    def inB(w: Math.Vector2): Boolean = {
      w.x >= 0 && w.x < screenW && w.y >= 0 && w.y < screenH
    }

    def inBound(v: (Int, Int)): Boolean = {
      val lt = toScreen((v._1, v._2))
      val rt = toScreen((v._1 + 1, v._2))
      val lb = toScreen((v._1, v._2 + 1))
      val rb = toScreen((v._1 + 1, v._2 + 1))
      inB(lt) || inB(rt) || inB(lb) || inB(rb)
    }

    def tileNeighbours(x: Int, y: Int): Seq[(Int, Int)] =
      Seq((x - 1, y - 1), (x - 1, y), (x - 1, y + 1),
        (x + 1, y - 1), (x + 1, y), (x + 1, y + 1),
        (x, y - 1), (x, y + 1)).filter(inBound)

    val vertexes = Array[(Double, Double)](
      (0.0, 0.0), (screenW, 0.0),
      (0.0, screenH), (screenW, screenH))

    val transformed = vertexes.map { case (x, y) =>
      val c = toWorldXY(Math.Vector2(x, y))
      (c.x.toInt, c.y.toInt)
    }

    bfs(Queue() ++ transformed, tileNeighbours)
  }
}
