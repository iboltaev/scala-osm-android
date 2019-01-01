package org.openstreetmap.sample

import rx.lang.scala._
import rx.lang.scala.observables.{AsyncOnSubscribe, SyncOnSubscribe}
import rx.lang.scala.schedulers._

import android.graphics._

import org.http4s.blaze.http.http2.client.Http2Client

import scala.concurrent.{Promise, Future}
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicReference

object BitmapLoader {
  def bitmap(tc: Types.TileCoord): Observable[Bitmap] = {
    val subCounter = new AtomicReference[Subscriber[Bitmap]](null)

    val result = Observable.apply[Bitmap] { subscriber =>
      if (!subCounter.compareAndSet(null, subscriber))
        throw new RuntimeException("only 1 subscriber")

      subscriber.onStart()

      val f = promisesCache.get(Seq(Types.normalize(tc)), mkFuture).head

      f.foreach { b =>
        subscriber.onNext(b)
      }
    }

    result.doOnUnsubscribe {

    }
  }

  private def mkFuture(tc: Types.TileCoord): Future[Bitmap] = {
    val url = s"https://a.tile.openstreetmap.org/${tc._3}/${tc._1}/${tc._2}.png"
    Http2Client.defaultH2Client.GET(url) { resp =>
      resp.body.accumulate().map { byteBuf =>
        val arr = Array.ofDim[Byte](byteBuf.remaining())
        byteBuf.get(arr)
        BitmapFactory.decodeByteArray(arr, 0, arr.length)
      }
    }
  }

  val promisesCache = new LRUCache[Types.TileCoord, Future[Bitmap]](128, v => {})
}
