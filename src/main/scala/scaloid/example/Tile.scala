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

case class Tile(x: Int, y: Int, z: Int, view: TiledView)(textureStream: Observable[Bitmap]) {
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

  private var haveTexture: Boolean = false
  private var closed: Boolean = false

  private var subscription: Subscription = null
  private var isEmpty: Boolean = true

  def nonEmpty: Boolean = !isEmpty

  def activate(): Unit = {
    if (subscription == null) {
      subscription = textureStream(Subscriber { bmp: Bitmap =>
        TiledView.runOnRenderThread {
          isEmpty = false
          setTexture(bmp)
        }
      })
    }
  }

  def unactivate(): Unit = {
    if (subscription != null)
      subscription.unsubscribe()

    subscription = null
  }

  def clear(): Unit = {
    closed = true
    unactivate()
    cleanTexture()
  }

  private def cleanTexture(): Unit = {
    if (haveTexture)
      GLES20.glDeleteTextures(1, texturenames, 0)

    haveTexture = false
  }

  def setTexture(bitmap: Bitmap): Unit = {
    Log.e("ScalaMap", "setTexture")

    cleanTexture()

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

    val v1 = toOgl(cs.toScreen((x, y, z)))
    val v2 = toOgl(cs.toScreen((x, y + 1, z)))
    val v3 = toOgl(cs.toScreen((x + 1, y + 1, z)))
    val v4 = toOgl(cs.toScreen((x + 1, y, z)))

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
