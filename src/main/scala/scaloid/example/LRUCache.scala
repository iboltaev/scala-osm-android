package org.openstreetmap.sample

class LRUCache[K, V](size: Int, dispose: V => Unit) {

  def get(keys: Seq[K], mkVal: K => V): Seq[V] = {
    val inCache = for {
      k <- keys
      v <- cache.get(k)
    } yield (k, v)

    // fake varible to overrun compiler bug with lazy eval
    val icSize = inCache.size
    val outCache = keys.filter(!cache.contains(_)).toList

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
      cache.put(k, v)
    }

    shrink()

    result.map(_._2)
  }

  private def shrink(): Unit = {
    if (cache.size > size) {
      //Log.e("ScalaMap", "shrink " + cache.size.toString)
      val old = cache.take(cache.size - size)
      old.foreach { case (k, v) =>
        dispose(v)
        cache -= k
      }
    }
  }

  private var cache = scala.collection.mutable.LinkedHashMap[K, V]()
}
