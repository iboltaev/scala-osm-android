package org.openstreetmap.sample

import scala.collection.immutable._
import scala.language.implicitConversions


case class MapCoordinateSystem(
  start: Math.Vector2, basis: Math.Matrix2, scale: Int) {

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

    val obnf1 = Math.orthoMatrix2(nextFingers._1.x, nextFingers._1.y)
    val obnf2 = Math.orthoMatrix2(nextFingers._2.x, nextFingers._2.y)

    val y = (obnf2.inv - obnf1.inv).inv * (obnf2.inv * pf2 - obnf1.inv * pf1)
    val x = obnf1.inv * (pf1 - y)
    val m = Math.orthoMatrix1(x.x, x.y).inv
    val v = Math.Vector2(m.x00, m.x01)

    if (v.mod >= 340 * 340 && scale < 19) {
      MapCoordinateSystem(
        y * 2, m * 0.5, scale + 1)
    } else if (v.mod <= 170 * 170 && scale > 0) {
      MapCoordinateSystem(
        y * 0.5, m * 2, scale - 1)
    } else {
      MapCoordinateSystem(y, m, scale)
    }
  }

  def bfs(
    q: Queue[(Int, Int)],
    neighbours: => (Int, Int) => Seq[(Int, Int)] ,
    visited: Set[(Int, Int)] = Set.empty): Stream[(Int, Int)] = {

    if (q.isEmpty) Stream.empty
    else if (visited.contains(q.head)) bfs(q.tail, neighbours, visited)
    else q.head #:: bfs(
      q.tail ++ neighbours(q.head._1, q.head._2).filterNot(visited.contains), 
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

    val tiles = bfs(Queue() ++ transformed, tileNeighbours)

    // center tiles - first
    tiles.sortWith { (p1, p2) =>
      val (c1, c2) = ((p1._1 + 0.5, p1._2 + 0.5), (p2._1 + 0.5, p2._2 + 0.5))
      val (s1, s2) = (toScreen(Math.Vector2(c1)), toScreen(Math.Vector2(c2)))
      val (d1, d2) = (
        (s1.x - screenW/2, s1.y - screenH/2),
        (s2.x - screenW/2, s2.y - screenH/2))

      (d1._1 * d1._1 + d1._2 * d1._2) < (d2._1 * d2._1 + d2._2 * d2._2)
    }
  }
}
