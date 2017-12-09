package org.openstreetmap.sample

import org.scaloid.common._

import scala.annotation.tailrec
import scala.collection.immutable._
import scala.language.implicitConversions

import android.graphics._
import android.view._
import android.content._
import android.util.Log
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.opengl.GLUtils

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer

import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

import akka.stream._
import akka.stream.scaladsl._

import akka.actor.ActorSystem
import akka.util.ByteString
import scala.concurrent._
import scala.concurrent.duration._
import java.nio.file.Paths

object Shaders {
  lazy val program = {
    val mp = GLES20.glCreateProgram()

    GLES20.glAttachShader(
      mp, loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode))
    GLES20.glAttachShader(
      mp, loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode))

    GLES20.glLinkProgram(mp)

    mp
  }

  def loadShader(tp: Int, shaderCode: String): Int = {
    val shader = GLES20.glCreateShader(tp)
    GLES20.glShaderSource(shader, shaderCode)
    GLES20.glCompileShader(shader)

    shader
  }

  val vertexShaderCode =
    "attribute vec4 vPosition;" +
    "attribute vec2 a_texCoord;" +
    "varying vec2 v_texCoord;" +
    "void main() {" +
    "  v_texCoord = a_texCoord;" +
    "  gl_Position = vPosition;" +
    "}";

  val fragmentShaderCode =
    "precision mediump float;" +
    "varying vec2 v_texCoord;" +
    "uniform sampler2D s_texture;" +  
    "void main() {" +
    "  gl_FragColor = texture2D(s_texture, v_texCoord);" +
    "}";
}

case class Tile(x: Int, y: Int, z: Int) {
  val program = Shaders.program

  var vertexBuffer: FloatBuffer = null
  var drawListBuffer: ShortBuffer = null
  var mPositionHandle: Int = -1
  var mColorHandle: Int = -1

  val uvs = Array(
    0.0f, 0.0f, 
    0.0f, 1.0f, 
    1.0f, 1.0f, 
    1.0f, 0.0f)

  val bb1 = ByteBuffer.allocateDirect(uvs.length * 4);
  bb1.order(ByteOrder.nativeOrder());
  val uvBuffer = bb1.asFloatBuffer();
  uvBuffer.put(uvs);
  uvBuffer.position(0);

  val texturenames = Array.ofDim[Int](1)

  val COORDS_PER_VERTEX = 3
  val squareCoords = Array(
    0.0f, 0.0f, 0.0f, // top left
    0.0f, 0.0f, 0.0f, // bottom left
    0.0f, 0.0f, 0.0f, // bottom right
    0.0f, 0.0f, 0.0f  // top right
  )

  val drawOrder = Array[Short](0, 1, 2, 0, 2, 3)
  val vertexStride = COORDS_PER_VERTEX * 4
  val color = Array(1.0f, 1.0f, 1.0f, 0.5f)

  val bb = ByteBuffer.allocateDirect(squareCoords.length * 4)
  bb.order(ByteOrder.nativeOrder())
  vertexBuffer = bb.asFloatBuffer()

  val dlb = ByteBuffer.allocateDirect(drawOrder.length * 2)
  dlb.order(ByteOrder.nativeOrder())
  drawListBuffer = dlb.asShortBuffer()
  drawListBuffer.put(drawOrder)
  drawListBuffer.position(0)

  var haveTexture: Boolean = false

  def clear(): Unit = {
    if (haveTexture)
      GLES20.glDeleteTextures(1, texturenames, 0)

    haveTexture = false
  }

  def setTexture(bitmap: Bitmap): Unit = {
    clear()

    haveTexture = true
    GLES20.glGenTextures(1, texturenames, 0);

    GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texturenames(0));

    GLES20.glTexParameteri(
      GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER,
      GLES20.GL_LINEAR);
    GLES20.glTexParameteri(
      GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER,
      GLES20.GL_LINEAR);
    
    // Set wrapping mode
    GLES20.glTexParameteri(
      GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S,
      GLES20.GL_CLAMP_TO_EDGE);
    GLES20.glTexParameteri(
      GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T,
      GLES20.GL_CLAMP_TO_EDGE);
    
    // Load the bitmap into the bound texture.
    GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);

    // We are done using the bitmap so we should recycle it.
    bitmap.recycle();
  }

  def draw(cs: MapCoordinateSystem, width: Int, height: Int): Unit = {
    def toOgl(v: Math.Vector2): Math.Vector2 = {
      val newx = (v.x - width.toDouble/2)
      val newy = (height.toDouble/2 - v.y)
      Math.Vector2(newx * 2/width, newy * 2/height)
    }

    GLES20.glUseProgram(program)

    val v1 = toOgl(cs.toScreen(Math.Vector2(x, y)))
    val v2 = toOgl(cs.toScreen(Math.Vector2(x, y + 1)))
    val v3 = toOgl(cs.toScreen(Math.Vector2(x + 1, y + 1)))
    val v4 = toOgl(cs.toScreen(Math.Vector2(x + 1, y)))

    squareCoords(0) = v1.x.toFloat; squareCoords(1) = v1.y.toFloat
    squareCoords(3) = v2.x.toFloat; squareCoords(4) = v2.y.toFloat
    squareCoords(6) = v3.x.toFloat; squareCoords(7) = v3.y.toFloat
    squareCoords(9) = v4.x.toFloat; squareCoords(10) = v4.y.toFloat

    vertexBuffer.position(0)
    vertexBuffer.put(squareCoords)
    vertexBuffer.position(0)

    mPositionHandle = GLES20.glGetAttribLocation(program, "vPosition")
    GLES20.glEnableVertexAttribArray(mPositionHandle)
    GLES20.glVertexAttribPointer(
      mPositionHandle, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, 
      vertexStride, vertexBuffer)

    val mTexCoordLoc = GLES20.glGetAttribLocation(program, "a_texCoord")

    // Enable generic vertex attribute array
    GLES20.glEnableVertexAttribArray ( mTexCoordLoc );
 
    // Prepare the texturecoordinates
    GLES20.glVertexAttribPointer ( mTexCoordLoc, 2, GLES20.GL_FLOAT,
                false,
                0, uvBuffer);
    
    val mSamplerLoc = GLES20.glGetUniformLocation (program, "s_texture")
 
    // Set the sampler texture unit to 0, where we have saved the texture.
    GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texturenames(0));

    GLES20.glUniform1i ( mSamplerLoc, 0);

    GLES20.glDrawElements(
      GLES20.GL_TRIANGLES, drawOrder.length,
      GLES20.GL_UNSIGNED_SHORT, drawListBuffer);

    GLES20.glDisableVertexAttribArray(mPositionHandle);
    GLES20.glDisableVertexAttribArray(mTexCoordLoc);
  }
}

object TileCache {
  type TileCoord = (Int, Int, Int)

  def loadTiles(tiles: Seq[TileCoord]): Seq[Tile] = {
    val inCache = tiles.flatMap { tc => cache.get(tc).toSeq }
    // fake varible to overrun compiler bug with lazy eval
    val icSize = inCache.size
    val outCache = tiles.filter(!cache.contains(_)).toList
    val newTiles = outCache.map { tc =>
      val tile = new Tile(tc._1, tc._2, tc._3)
      tile.setTexture(
        TileRenderer.renderTile(tc._1, tc._2, tc._3, 1.0f))
      tile
    }
    
    for (tc <- tiles) {
      cache.remove(tc)
    }

    val result = inCache ++ newTiles
    // Fake variable to overrun compiler bug with lazy eval
    val resSize = result.size

    for (t <- result) {
      cache.put((t.x, t.y, t.z), t)
    }

    shrink()

    result
  }

  private def shrink(): Unit = {
    if (cache.size > maxCacheSize) {
      Log.e("ScalaMap", "shrink " + cache.size.toString)
      val old = cache.take(cache.size - maxCacheSize)
      old.foreach(_._2.clear())
      cache = cache.drop(cache.size - maxCacheSize)
    }
  }

  private val maxCacheSize = 64
  private var cache = scala.collection.mutable.LinkedHashMap[TileCoord, Tile]()
}

object TileRenderer {
  def renderTile(x: Int, y: Int, z: Int, scale: Float): Bitmap = {
    val size = 1 << z
    render(x % size, y % size, z % size, scale)
  }
  
  private def render(x: Int, y: Int, z: Int, scale: Float): Bitmap = {
    // lazies is a compiler bug workaround
    lazy val text = x + ":" + y + ":" + z
    lazy val colors = Array.fill(256 * 256)(0xFFFFFFFF)
    lazy val bmp = Bitmap.createBitmap(colors, 256, 256, Bitmap.Config.ARGB_8888)
    lazy val mutableBmp = bmp.copy(Bitmap.Config.ARGB_8888, true)
    lazy val canvas = new Canvas(mutableBmp)
    lazy val paint = new Paint(Paint.ANTI_ALIAS_FLAG)
    paint.setColor(Color.rgb(0, 0, 0))
    paint.setTextSize(14 * scale)
    lazy val bounds = new Rect()
    paint.getTextBounds(text, 0, text.length, bounds)
    lazy val xd = (mutableBmp.getWidth() - bounds.width())/2
    lazy val yd = (mutableBmp.getHeight() + bounds.height())/2
    canvas.drawText(text, xd.toFloat, yd.toFloat, paint)

    Log.e("ScalaMap", "Render tile")

    mutableBmp
  }
}


class MyGLRenderer(cs: => MapCoordinateSystem) 
    extends GLSurfaceView.Renderer {
  
  var screenW: Int = 0
  var screenH: Int = 0

  override def onSurfaceCreated(u: GL10, config: EGLConfig): Unit = {
    GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
  }

  override def onSurfaceChanged(u: GL10, width: Int, height: Int): Unit = {
    GLES20.glViewport(0, 0, width, height)
    screenW = width; screenH = height
  }

  override def onDrawFrame(u: GL10): Unit = {
    //GLES20.glClear(
      //GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

    Log.e("ScalaMap", "w: " + screenW + ", h: " + screenH)

    if (screenW != 0 && screenH != 0) {
      val coord = cs
      val tileIdxs = coord.visibleTiles(screenW, screenH)
      Log.e("ScalaMap", tileIdxs.toList.toString)
      val tiles = TileCache.loadTiles(tileIdxs.map { case (x, y) => (x, y, coord.scale)})
      tiles.foreach(_.draw(coord, screenW, screenH))
    }
  }
}

class MyView(context: HelloScaloid) extends GLSurfaceView(context) {
  import java.util.concurrent.atomic.AtomicReference
  
  setEGLContextClientVersion(2);

  val renderer = new MyGLRenderer(coordSystem.get)

  setRenderer(renderer)
  setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY)

  val mapScale: Int = 11
    
  // !! latLon !!
  val offset: Math.Vector2 = Geo.toXY(
    Math.Vector2(59.69, 30.03), mapScale)

  var gestureRecognizer = GestureRecognizer.empty
    
  var coordSystem = new AtomicReference(MapCoordinateSystem(
      offset, Math.Matrix2.identity * 256, mapScale))

  implicit lazy val system = ActorSystem("QuickStart")
  implicit lazy val materializer = ActorMaterializer()

  override def onTouchEvent(e: MotionEvent): Boolean = {
    gestureRecognizer = gestureRecognizer.nextEvent(e)
    gestureRecognizer.gesture.foreach {
      case Move(from, to) =>
        val cs = coordSystem.get()
        coordSystem.set(cs.moveInScreen(from, to))
      case Transform(from, to) =>
        val cs = coordSystem.get()
        coordSystem.set(cs.transformInScreen(from, to))
      case _ =>
    }

    Log.e("ScalaMap", coordSystem.toString)

    true
  }
}

class HelloScaloid extends SActivity {
  var view: Option[MyView] = None
  onCreate {
    view = Some(new MyView(this))
    contentView = view.get
  }

  onPause {
    view.foreach(_.onPause())
  }

  onResume {
    view.foreach(_.onResume())
  }
}
