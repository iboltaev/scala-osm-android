package org.openstreetmap.sample

object Types {
  type TileCoord = (Int, Int, Int)

  def normalize(tc: TileCoord): TileCoord = {
    val size = 1 << tc._3
    val x = mod(tc._1, size)
    val y = mod(tc._2, size)
    (x, y, tc._3)
  }

  private def mod(a: Int, b: Int): Int = {
    if (a < 0) a + (1 - a/b)*b else a % b
  }
}
