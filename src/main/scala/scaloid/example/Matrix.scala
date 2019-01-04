package org.openstreetmap.sample

import scala.language.implicitConversions

object Math {
  case class Matrix2(x00: Double, x01: Double, x10: Double, x11: Double) {
    lazy val det = x00 * x11 - x01 * x10

    lazy val inv = {
      val indet = (1.toDouble)/det
      Math.*(Matrix2(x11, -x01, -x10, x00), indet)
    }

    def *(v: Vector2) = Math.*(this, v)
    def *(v: Double) = Matrix2(x00 * v, x01 * v, x10 * v, x11 * v)
    def -(r: Matrix2) = Matrix2(x00 - r.x00, x01 - r.x01, x10 - r.x10, x11 - r.x11)
  }

  object Matrix2 {
    def identity = Matrix2(1, 0, 0, 1)
  }

  case class Vector2(x: Double, y: Double) {
    def orto = Vector2(-y, x)

    def +(r: Vector2) = Vector2(x + r.x, y + r.y)
    def -(r: Vector2) = Vector2(x - r.x, y - r.y)
    def *(d: Double) = Vector2(x * d, y * d)

    lazy val mod = x * x + y * y
  }

  object Vector2 {
    def apply(p: (Double, Double)): Vector2 = apply(p._1, p._2)
  }

  implicit def *(m: Matrix2, v: Vector2): Vector2 = 
    Vector2(m.x00 * v.x + m.x01 * v.y, m.x10 * v.x + m.x11 * v.y)

  implicit def *(m: Matrix2, v: Double): Matrix2 = Matrix2(
    m.x00 * v, m.x01 * v, m.x10 * v, m.x11 * v)

  implicit def +(v1: Vector2, v2: Vector2): Vector2 = Vector2(
    v1.x + v2.x, v1.y + v2.y)

  implicit def -(v1: Vector2, v2: Vector2): Vector2 = Vector2(
    v1.x - v2.x, v1.y - v2.y)

  implicit def *(v1: Vector2, v2: Vector2): Double =
    v1.x * v2.x + v1.y * v2.y

  implicit def toVector2(p: (Int, Int)): Vector2 = Vector2(p._1, p._2)
  implicit def toVectord2(p: (Double, Double)): Vector2 = Vector2(p._1, p._2)

  def orthoMatrix1(x0: Double, x1: Double) = Matrix2(x0, x1, -x1, x0)
  def orthoMatrix2(x0: Double, x1: Double) = Matrix2(x0, x1, x1, -x0)
}
