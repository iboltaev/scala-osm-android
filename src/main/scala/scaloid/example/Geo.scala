package org.openstreetmap.sample

import java.lang.{ Math => JMath }

object Geo {
  def lonLatToXY(lon: Double, lat: Double, scale: Int): (Double, Double) = {
    // (0, 0) is Greenwich, center of the map; 
    // map is (-90..90 lat, -90..90 lon)
    val wh = (1 << scale).toDouble
    var xtile = (lon + 180) / 360 * wh
    var ytile = (1 - JMath.log(JMath.tan(JMath.toRadians(lat)) + 1 / JMath.cos(JMath.toRadians(lat))) / JMath.PI) / 2 * wh
    if (xtile < 0) xtile = 0
    if (xtile >= (1<<scale)) xtile = (wh - 1)
    if (ytile < 0) ytile = 0
    if (ytile >= (1<<scale)) ytile = (wh - 1);
  
    (xtile, ytile)
  }

  /*
  def xyToLonLat(x: Double, y: Double, scale: Int): (Double, Double) = {
    val wh = (1 << scale).toDouble
    (x * 180 / wh - 90, 90 - y * 180 / wh)
  }
   */

  def toXY(lonLat: Math.Vector2, scale: Int): Math.Vector2 = {
    val (x, y) = lonLatToXY(lonLat.x, lonLat.y, scale)
    Math.Vector2(x, y)
  }

  val availScales = 0 to 19
}
