package org.openstreetmap.sample

import org.scaloid.common._

import rx.lang.scala._
import rx.lang.scala.observables.{AsyncOnSubscribe, SyncOnSubscribe}
import rx.lang.scala.schedulers._

import android.graphics._
import android.view._
import android.content._
import android.util.Log
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.opengl.GLUtils

import scala.annotation.tailrec
import scala.collection.immutable._
import scala.language.implicitConversions
import scala.concurrent._
import scala.concurrent.duration._

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer

import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

import java.nio.file.Paths

import java.lang.Runnable

import android.widget.Toast

private class MyGLRenderer(view: TiledView, cs: => MapCoordinateSystem) 
    extends GLSurfaceView.Renderer {

  var screenW: Int = 0
  var screenH: Int = 0

  def tileOrdering(zoom: Int): Ordering[Tile] = Ordering.by { tile =>
    (tile.nonEmpty, -(tile.z - zoom).abs, tile.z)
  }

  def makeTile(tc: Types.TileCoord): Tile = {
    Log.e("ScalaMap", s"makeTile ${tc}")
    val tile = new Tile(tc._1, tc._2, tc._3, view)(BitmapLoader.bitmap(Types.normalize(tc)))
    tile.setTexture(
      EmptyTileRenderer.renderTile(tc._1, tc._2, tc._3, 1.0f))
    tile
  }

  override def onSurfaceCreated(u: GL10, config: EGLConfig): Unit = {
    GLES20.glClearColor(1.0f, 1.0f, 1.0f, 1.0f)
    view.shaders.clear()
    tileCache.clear()
  }

  override def onSurfaceChanged(u: GL10, width: Int, height: Int): Unit = {
    GLES20.glViewport(0, 0, width, height)
    screenW = width; screenH = height
  }

  override def onDrawFrame(u: GL10): Unit = {
    drawFrame()
  }

  def drawFrame(): Unit = {
    Log.e("ScalaMap", "w: " + screenW + ", h: " + screenH)

    if (screenW != 0 && screenH != 0) {
      val coord = cs
      val tileIdxs = coord.visibleTiles(screenW, screenH)
      Log.e("ScalaMap", tileIdxs.toList.toString)
      val actualTiles = tileCache.get(
        tileIdxs
          .map { case (x, y) => (x, y, coord.scale) }
          .toList,
        makeTile)
      actualTiles.foreach(_.activate())

      val actualKeys = actualTiles.map { t => (t.x, t.y, t.z) }.toSet
      val nonActual = tileCache.toStream.filterNot { case (k, _) => actualKeys.contains(k) }

      nonActual.foreach { case (k, t) =>
        t.unactivate()
      }

      implicit val ordering: Ordering[Tile] = tileOrdering(coord.scale)

      tileCache.toStream
        .map(_._2)
        .sorted
        .foreach(_.draw(coord, screenW, screenH))
    }
  }

  private val tileCache = new LRUCache[Types.TileCoord, Tile](64, _.clear())
}

class TiledView(context: ScalaOSM) extends GLSurfaceView(context) {
  import java.util.concurrent.atomic.AtomicReference
  
  setEGLContextClientVersion(2);

  val shaders = new Shaders

  private val renderer = new MyGLRenderer(this, coordSystem.get)

  setRenderer(renderer)
  setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY)

  val mapScale: Int = 11
    
  // !! lonLat !!
  val offset: Math.Vector2 = Geo.toXY(
    Math.Vector2(30.32, 59.95), mapScale)

  var gestureRecognizer = GestureRecognizer.empty

  Log.e("ScalaMap", s"width: ${getWidth}, height: ${getHeight}")

  lazy val coordSystem = new AtomicReference(
    MapCoordinateSystem(offset, Math.Matrix2.identity * 256, mapScale)
      .moveInScreen(Math.Vector2(0, 0), Math.Vector2(getWidth/2, getHeight/2))
    )

  override def onTouchEvent(e: MotionEvent): Boolean = {
    gestureRecognizer = gestureRecognizer.nextEvent(e)
    val cs = coordSystem.get()
    gestureRecognizer.gesture.foreach {
      case Move(from, to) =>
        coordSystem.set(cs.moveInScreen(from, to))
      case Transform(from, to) =>
        coordSystem.set(cs.transformInScreen(from, to))
      case Tap(pos) =>
        val xy = cs.toWorldXY(pos)
        onTap(xy.x, xy.y, cs.scale)
      case _ =>
    }

    Log.e("ScalaMap", coordSystem.toString)

    true
  }

  protected def onTap(x: Double, y: Double, scale: Int): Unit = {
    val toast = Toast.makeText(context, s"""Tap @ ${"%.2f".format(x)} : ${"%.2f".format(y)}, scale: ${scale}""", Toast.LENGTH_LONG)
    toast.show
  }

  def runOnRenderThread(f: => Unit): Unit = {
    queueEvent(new Runnable {
      override def run(): Unit = { f }
    })
  }
}
