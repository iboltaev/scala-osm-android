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

case class Tile(x: Int, y: Int, z: Int)(textureStream: Observable[Bitmap]) {
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
  var closed: Boolean = false

  val subscription = textureStream(Subscriber { bmp: Bitmap =>
    MyView.runOnRenderThread { setTexture(bmp) }
  })

  def clear(): Unit = {
    closed = true
    subscription.unsubscribe()
    cleanTexture()
  }

  private def cleanTexture(): Unit = {
    if (haveTexture)
      GLES20.glDeleteTextures(1, texturenames, 0)

    haveTexture = false
  }

  def setTexture(bitmap: Bitmap): Unit = {
    cleanTexture()

    if (closed)
      return

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

class MyGLRenderer(cs: => MapCoordinateSystem) 
    extends GLSurfaceView.Renderer {

  var screenW: Int = 0
  var screenH: Int = 0

  def makeTile(tc: Types.TileCoord): Tile = {
    val tile = new Tile(tc._1, tc._2, tc._3)(BitmapLoader.bitmap(tc))
    tile.setTexture(
      EmptyTileRenderer.renderTile(tc._1, tc._2, tc._3, 1.0f))
    tile
  }

  override def onSurfaceCreated(u: GL10, config: EGLConfig): Unit = {
    GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
  }

  override def onSurfaceChanged(u: GL10, width: Int, height: Int): Unit = {
    GLES20.glViewport(0, 0, width, height)
    screenW = width; screenH = height
  }

  override def onDrawFrame(u: GL10): Unit = {
    Log.e("ScalaMap", "w: " + screenW + ", h: " + screenH)

    if (screenW != 0 && screenH != 0) {
      val coord = cs
      val tileIdxs = coord.visibleTiles(screenW, screenH)
      Log.e("ScalaMap", tileIdxs.toList.toString)
      val tiles = tileCache.get(
        tileIdxs.map { case (x, y) => (x, y, coord.scale)},
        makeTile)
      tiles.foreach(_.draw(coord, screenW, screenH))
    }
  }

  private def tileCache = new LRUCache[Types.TileCoord, Tile](64, _.clear())
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

object MyView {
  // quick & dirty singleton
  private var instanceOpt: Option[MyView] = None

  def instance: Option[MyView] = instanceOpt

  def instance(context: HelloScaloid): MyView = {
    if (instanceOpt.isEmpty)
      instanceOpt = Some(new MyView(context))

    instanceOpt.get
  }

  def runOnRenderThread(f: => Unit): Unit = {
    instanceOpt.foreach { view =>
      view.queueEvent(new Runnable {
        override def run(): Unit = { f }
      })
    }
  }
}

class HelloScaloid extends SActivity {
  onCreate {
    contentView = MyView.instance(this)
  }

  onPause {
    MyView.instance.foreach(_.onPause())
  }

  onResume {
    MyView.instance.foreach(_.onResume())
  }
}
