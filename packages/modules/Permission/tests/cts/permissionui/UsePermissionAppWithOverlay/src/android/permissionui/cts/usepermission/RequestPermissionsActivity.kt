/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.permissionui.cts.usepermission

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.util.Log

class RequestPermissionsActivity : Activity() {

    var paused = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState != null) {
            Log.w(TAG, "Activity was recreated. (Perhaps due to a configuration change?)")
            return
        }

        registerReceiver(
            object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action != ACTION_SHOW_OVERLAY) {
                        return
                    }

                    startActivity(
                        intent
                            .setAction(null)
                            .setComponent(ComponentName(context!!, OverlayActivity::class.java))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            },
            IntentFilter(ACTION_SHOW_OVERLAY),
            RECEIVER_EXPORTED
        )
        Handler(mainLooper).post(this::eventuallyRequestPermission)
    }

    /**
     * Keep trying to requestPermissions until the dialog shows. It may fail the first few times due
     * to rapid install/uninstall tests do
     */
    private fun eventuallyRequestPermission() {
        if (!paused) {
            val permissions = intent.getStringArrayExtra("$packageName.PERMISSIONS")!!
            requestPermissions(permissions, 1)
            Handler(mainLooper).postDelayed(this::eventuallyRequestPermission, 200)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        setResult(
            RESULT_OK,
            Intent().apply {
                putExtra("$packageName.PERMISSIONS", permissions)
                putExtra("$packageName.GRANT_RESULTS", grantResults)
            }
        )
        finish()
    }

    override fun onPause() {
        paused = true
        super.onPause()
    }

    override fun onResume() {
        paused = false
        super.onResume()
    }

    companion object {
        const val ACTION_SHOW_OVERLAY = "android.permissionui.cts.usepermission.ACTION_SHOW_OVERLAY"
        const val ACTION_HIDE_OVERLAY = "android.permissionui.cts.usepermission.ACTION_HIDE_OVERLAY"
        private val TAG = RequestPermissionsActivity::class.simpleName
    }
}
