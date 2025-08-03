package android.permissionui.cts.usepermission

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager

class OverlayActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.overlay_activity)
        val params = window.attributes
        params.flags =
            (WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)

        if (!intent.getBooleanExtra(EXTRA_FULL_OVERLAY, true)) {
            params.gravity = Gravity.LEFT or Gravity.TOP
            val left = intent.getIntExtra(OVERLAY_LEFT, params.x)
            val top = intent.getIntExtra(OVERLAY_TOP, params.y)
            val right = intent.getIntExtra(OVERLAY_RIGHT, params.x + params.width)
            val bottom = intent.getIntExtra(OVERLAY_BOTTOM, top + 1)
            params.x = left
            params.y = top
            params.width = right - left
            params.height = bottom - top
        }

        registerReceiver(
            object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action != RequestPermissionsActivity.ACTION_HIDE_OVERLAY) {
                        return
                    }

                    finish()
                }
            },
            IntentFilter(RequestPermissionsActivity.ACTION_HIDE_OVERLAY),
            RECEIVER_EXPORTED
        )
    }

    companion object {
        const val EXTRA_FULL_OVERLAY = "android.permissionui.cts.usepermission.extra.FULL_OVERLAY"

        const val OVERLAY_LEFT = "android.permissionui.cts.usepermission.extra.OVERLAY_LEFT"
        const val OVERLAY_TOP = "android.permissionui.cts.usepermission.extra.OVERLAY_TOP"
        const val OVERLAY_RIGHT = "android.permissionui.cts.usepermission.extra.OVERLAY_RIGHT"
        const val OVERLAY_BOTTOM = "android.permissionui.cts.usepermission.extra.OVERLAY_BOTTOM"
    }
}
