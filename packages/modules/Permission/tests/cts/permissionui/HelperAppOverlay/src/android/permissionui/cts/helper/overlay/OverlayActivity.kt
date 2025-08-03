package android.permissionui.cts.helper.overlay

import android.app.Activity
import android.os.Bundle
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

class OverlayActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mainLayout = LinearLayout(this)
        mainLayout.layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        val textView = TextView(this)

        textView.text = "Find me!"
        mainLayout.addView(textView)

        val windowParams = WindowManager.LayoutParams()
        windowParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        windowManager.addView(mainLayout, windowParams)
    }
}
