package org.openstreetmap.sample

import scala.language.implicitConversions
import scala.collection.immutable._

import android.graphics._
import android.view._
import android.content._

sealed trait Gesture
case class Move(from: Math.Vector2, to: Math.Vector2) extends Gesture
case class Transform(
  from: (Math.Vector2, Math.Vector2),
  to: (Math.Vector2, Math.Vector2)) extends Gesture

case class GestureRecognizer(prev: GestureRecognizer.Position, next: GestureRecognizer.Position) {
  val gesture: Option[Gesture] = {
    val commonKeys = next.keySet.intersect(prev.keySet)
    if (commonKeys.isEmpty) None
    else if (commonKeys.size == 1) {
      Some(Move(prev(commonKeys.head)._2, next(commonKeys.head)._2))
    } else if (commonKeys.size == 2) {
      val prevArr = prev.toVector.sortBy(_._1).map(_._2._2)
      val nextArr = next.toVector.sortBy(_._1).map(_._2._2)
      Some(
        Transform(
          (prevArr(0), prevArr(1)),
          (nextArr(0), nextArr(1))))
    } else None
  }

  def nextEvent(ev: MotionEvent): GestureRecognizer = {
    val action = ev.getActionMasked

    if (action != MotionEvent.ACTION_MOVE && action != MotionEvent.ACTION_DOWN) {
      GestureRecognizer.empty
    } else {
      val m = for {
        idx <- 0 until ev.getPointerCount
        id = ev.getPointerId(idx)
      } yield id -> (idx, Math.Vector2(ev.getX(idx).toDouble, ev.getY(idx).toDouble))

      GestureRecognizer(next, m.toMap)
    }
  }
}

object GestureRecognizer {
  type Position = Map[Int, (Int, Math.Vector2)] // ID -> (Idx, position)

  def empty = GestureRecognizer(Map.empty, Map.empty)
}
