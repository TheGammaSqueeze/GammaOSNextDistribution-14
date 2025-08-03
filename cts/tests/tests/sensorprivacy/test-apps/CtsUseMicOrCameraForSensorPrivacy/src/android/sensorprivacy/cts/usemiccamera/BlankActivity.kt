package android.sensorprivacy.cts.usemiccamera

import android.app.Activity
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout

class BlankActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this)
        layout.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        setContentView(layout)
    }
}