package org.openstreetmap.sample

object Types {
  type TileCoord = (Int, Int, Int)

  def normalize(tc: TileCoord): TileCoord = {
    val size = 1 << tc._3
    (tc._1 % size, tc._2 % size, tc._3)
  }
}
