package android.sensorprivacy.cts.usemiccamera.overlay

import android.app.Activity
import android.hardware.input.InputManager
import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowManager.LayoutParams
import android.widget.FrameLayout
import android.widget.TextView

class OverlayActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val params = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        params.type = LayoutParams.TYPE_APPLICATION_OVERLAY
        params.flags = LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                LayoutParams.FLAG_NOT_TOUCH_MODAL or
                LayoutParams.FLAG_NOT_TOUCHABLE or
                LayoutParams.FLAG_KEEP_SCREEN_ON
        params.alpha = getSystemService(InputManager::class.java)!!.maximumObscuringOpacityForTouch

        val frameLayout = FrameLayout(this)
        val textView = TextView(this)
        textView.text = "This Should Be Hidden"
        frameLayout.addView(textView)
        windowManager.addView(frameLayout, params)
    }
}