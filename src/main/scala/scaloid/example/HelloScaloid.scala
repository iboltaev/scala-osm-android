package org.openstreetmap.sample

import org.scaloid.common._

class ScalaOSM extends SActivity {
  var cv: Option[TiledView] = None
  onCreate {
    val v = new TiledView(this)
    cv = Some(v)
    contentView = v
  }

  onPause {
    cv.foreach(_.onPause())
  }

  onResume {
    cv.foreach(_.onResume())
  }
}
