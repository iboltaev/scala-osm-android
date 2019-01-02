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

