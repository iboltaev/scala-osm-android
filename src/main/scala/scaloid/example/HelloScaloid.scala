package org.openstreetmap.sample

import org.scaloid.common._

import android.view.ViewGroup._
import android.widget.LinearLayout

class ScalaOSM extends SActivity {
  var cv: Option[LinearLayout] = None

  def mkTiledView: TiledView = {
    val view = new TiledView(this)
    view.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT, 0.8f))
    view
  }

  onCreate {
    val layout = new LinearLayout(this)
    layout.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

    val view = mkTiledView
    layout.addView(view)
 
    cv = Some(layout)
    contentView = layout
  }

  onPause {
    
    cv.foreach { ll =>
      (0 until ll.getChildCount).foreach { i => ll.getChildAt(i).asInstanceOf[TiledView].onPause }
    }
  }

  onResume {
    
    cv.foreach { ll =>
      (0 until ll.getChildCount).foreach { i => ll.getChildAt(i).asInstanceOf[TiledView].onResume }
    }
  }
}
