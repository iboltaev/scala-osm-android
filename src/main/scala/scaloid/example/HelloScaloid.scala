package org.openstreetmap.sample

import org.scaloid.common._

class ScalaOSM extends SActivity {
  onCreate {
    contentView = TiledView.instance(this)
  }

  onPause {
    TiledView.instance.foreach(_.onPause())
  }

  onResume {
    TiledView.instance.foreach(_.onResume())
  }
}
