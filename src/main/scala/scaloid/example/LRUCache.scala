package org.openstreetmap.sample

import android.util.Log


class LRUCache[K, V](size: Int, dispose: V => Unit) {
  def contains(k: K): Boolean = {
    cache.contains(k)
  }

  def toStream: Stream[(K, V)] = {
    cache.iterator.toStream
  }

  def get(keys: Seq[K], mkVal: K => V): Seq[V] = {
    //Log.e("ScalaMap", s"keys: ${keys}, cache size: ${cache.size}")
    //Log.e("ScalaMap", s"cache 1: ${cache.toList}")

    val inCache = for {
      k <- keys
      v <- cache.get(k)
    } yield (k, v)

    //Log.e("ScalaMap", s"in cache: ${inCache}")

    // fake varible to overrun compiler bug with lazy eval
    val icSize = inCache.size
    val outCache = keys.filter(!cache.contains(_)).toList

    //Log.e("ScalaMap", s"out cache: ${outCache}")

    val newVals = for {
      k <- outCache
      v = mkVal(k)
    } yield (k, v)
    
    for (k <- keys) {
      cache.remove(k)
    }

    val result = inCache ++ newVals
    // Fake variable to overrun compiler bug with lazy eval
    val resSize = result.size

    for ((k, v) <- result) {
      //Log.e("ScalaMap", s"put ${(k, v)}")
      cache.put(k, v)
    }

    shrink()

    //Log.e("ScalaMap", s"cache 2: ${cache.toList}")

    result.map(_._2)
  }

  def remove(k: K): Unit = {
    cache.get(k).foreach(dispose)
    cache.remove(k)
  }

  private def shrink(): Unit = {
    if (cache.size > size) {
      //Log.e("ScalaMap", "shrink " + cache.size.toString)
      val old = cache.take(cache.size - size)
      old.foreach { case (k, v) =>
        dispose(v)
        cache.remove(k)
      }
    }
  }

  private val cache = scala.collection.mutable.LinkedHashMap[K, V]()
}
