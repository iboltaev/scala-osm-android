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

    val result = Observable.apply[Bitmap] { subscriber =>
      if (!subCounter.compareAndSet(null, subscriber))
        throw new RuntimeException("only 1 subscriber")

      subscriber.onStart()

      val call = promisesCache.get(Seq(Types.normalize(tc)), mkCall).head

      call.f.foreach { b =>
        subscriber.onNext(b)
      }
    }

    result.doOnUnsubscribe {
      subCounter.set(null)
      promisesCache.remove(Types.normalize(tc))
    }
  }

  private def mkCall(tc: Types.TileCoord): BitmapCall = {
    val url = s"https://a.tile.openstreetmap.org/${tc._3}/${tc._1}/${tc._2}.png"

    //Log.e("ScalaMap", s"url: ${url}")

    val req = new Request.Builder().get().url(url)
      .header("accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9")
      .header("user-agent", "Mozilla/5.0 (Windows NT 6.3; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.106 YaBrowser/20.7.1.70 Yowser/2.5 Safari/537.36")
      .header("cache-control", "max-age=0")
      .build

    val call = client.newCall(req)
    val promise = Promise[Bitmap]()

    call.enqueue(new Callback {
      override def onResponse(call: Call, r: Response): Unit = {
        if (r.isSuccessful) {
          val arr = r.body.bytes
          //Log.e("ScalaMap", s"success,length: ${arr.length}")
          val bmp = BitmapFactory.decodeByteArray(arr, 0, arr.length)
          //Log.e("ScalaMap", "success, bmp decoded")
          promise.success(bmp)
        } else {
          Log.e("ScalaMap", s"Http error, code: ${r.code}, headers: ${r.headers}")
          promise.success(null)
        }
      }

      override def onFailure(call: Call, exc: java.io.IOException): Unit = {
        Log.e("ScalaMap", s"failure: ${call.request.url.toString}, cause: ${exc.getMessage}")
        promise.failure(exc)
      }
    })
    BitmapCall(promise.future, call)
  }

  private val client = new OkHttpClient()
  private val promisesCache = new LRUCache[Types.TileCoord, BitmapCall](128, _.cancel())
}
