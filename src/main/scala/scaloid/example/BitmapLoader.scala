package org.openstreetmap.sample

import rx.lang.scala._
import rx.lang.scala.observables.{AsyncOnSubscribe, SyncOnSubscribe}
import rx.lang.scala.schedulers._

import android.graphics._

import java.util.concurrent.atomic.AtomicInteger

object BitmapLoader {
  def bitmap(tc: TileCache.TileCoord): Observable[Bitmap] = {
    

    val result = Observable.apply[Bitmap] { subscriber =>

    }


    result.doOnUnsubscribe {

    }
  }
}
