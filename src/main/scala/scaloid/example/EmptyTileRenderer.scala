package org.openstreetmap.sample

import android.graphics._
import android.util.Log

object EmptyTileRenderer {
  def renderTile(x: Int, y: Int, z: Int, scale: Float): Bitmap = {
    //val size = 1 << z
    //render(x % size, y % size, z % size, scale)
    emptyTile
  }
  
  private def render(x: Int, y: Int, z: Int, scale: Float): Bitmap = {
    // lazies is a compiler bug workaround
    //lazy val text = x + ":" + y + ":" + z
    lazy val text = "OSM"
    lazy val colors = Array.fill(256 * 256)(0xFFFFFFFF)
    lazy val bmp = Bitmap.createBitmap(colors, 256, 256, Bitmap.Config.ARGB_8888)
    lazy val mutableBmp = bmp.copy(Bitmap.Config.ARGB_8888, true)
    lazy val canvas = new Canvas(mutableBmp)
    lazy val paint = new Paint(Paint.ANTI_ALIAS_FLAG)
    paint.setColor(Color.rgb(64, 64, 64))
    paint.setTextSize(14 * scale)
    lazy val bounds = new Rect()
    paint.getTextBounds(text, 0, text.length, bounds)
    lazy val xd = (mutableBmp.getWidth() - bounds.width())/2
    lazy val yd = (mutableBmp.getHeight() + bounds.height())/2
    canvas.drawText(text, xd.toFloat, yd.toFloat, paint)

    Log.e("ScalaMap", "Render tile")

    mutableBmp
  }

  private val emptyTile = render(0, 0, 0, 1.0f)
}
