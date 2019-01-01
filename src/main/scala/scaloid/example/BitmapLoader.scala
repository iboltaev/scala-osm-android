package org.openstreetmap.sample

import rx.lang.scala._
import rx.lang.scala.observables.{AsyncOnSubscribe, SyncOnSubscribe}
import rx.lang.scala.schedulers._

import android.graphics._
import android.util.Log

import okhttp3._

import scala.concurrent.{Promise, Future}
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicReference

object BitmapLoader {
  private case class BitmapCall(f: Future[Bitmap], call: Call) {
    def cancel(): Unit = {
      Log.e("ScalaMap", s"cancelling ${call.request.url.toString}")
      call.cancel()
    }
  }

  def bitmap(tc: Types.TileCoord): Observable[Bitmap] = {
    val subCounter = new AtomicReference[Subscriber[Bitmap]](null)
    val callRef = new AtomicReference[BitmapCall](null)

    val result = Observable.apply[Bitmap] { subscriber =>
      if (!subCounter.compareAndSet(null, subscriber))
        throw new RuntimeException("only 1 subscriber")

      subscriber.onStart()

      val call = promisesCache.get(Seq(Types.normalize(tc)), mkCall).head
      callRef.set(call)

      call.f.foreach { b =>
        subscriber.onNext(b)
      }
    }

    result.doOnUnsubscribe {
      Option(callRef.get()).foreach(_.cancel())
    }
  }

  private def mkCall(tc: Types.TileCoord): BitmapCall = {
    val url = s"https://a.tile.openstreetmap.org/${tc._3}/${tc._1}/${tc._2}.png"
    Log.e("ScalaMap", s"url: ${url}")
    val req = new Request.Builder().get().url(url).build
    val call = client.newCall(req)
    val promise = Promise[Bitmap]()
    call.enqueue(new Callback {
      override def onResponse(call: Call, r: Response): Unit = {
        val arr = r.body.bytes
        Log.e("ScalaMap", s"success,length: ${arr.length}")
        val bmp = BitmapFactory.decodeByteArray(arr, 0, arr.length)
        Log.e("ScalaMap", "success, bmp decoded")
        promise.success(bmp)
      }

      override def onFailure(call: Call, exc: java.io.IOException): Unit = {
        Log.e("ScalaMap", s"failure: ${call.request.url.toString}, cause: ${exc.getMessage}")
        promise.failure(exc)
      }
    })
    BitmapCall(promise.future, call)
  }

  private val client = new OkHttpClient()
  private val promisesCache = new LRUCache[Types.TileCoord, BitmapCall](128, v => v.cancel())
}
