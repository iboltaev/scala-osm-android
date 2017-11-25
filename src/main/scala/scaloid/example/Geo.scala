package org.openstreetmap.sample

object Geo {
  def lonLatToXY(lon: Double, lat: Double, scale: Int): (Double, Double) = {
    // (0, 0) is Greenwich, center of the map; 
    // map is (-90..90 lat, -90..90 lon)
    val wh = (1 << scale).toDouble
    (wh * (lon + 90) / 180, wh * (90 - lat) / 180)
  }

  def xyToLonLat(x: Double, y: Double, scale: Int): (Double, Double) = {
    val wh = (1 << scale).toDouble
    (x * 180 / wh - 90, 90 - y * 180 / wh)
  }

  def toXY(lonLat: Math.Vector2, scale: Int): Math.Vector2 = {
    val (x, y) = lonLatToXY(lonLat.x, lonLat.y, scale)
    Math.Vector2(x, y)
  }

  val availScales = 0 to 19
}
