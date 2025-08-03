/*
 * Copyright (C) 2021 The Android Open Source Project
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

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper

const val EXTRA_CREATE_CHANNELS = "extra_create"
const val EXTRA_REQUEST_NOTIF_PERMISSION = "extra_request_notif_permission"
const val EXTRA_REQUEST_OTHER_PERMISSIONS = "extra_request_permissions"
const val EXTRA_START_SECOND_ACTIVITY = "extra_start_second_activity"
const val EXTRA_START_SECOND_APP = "extra_start_second_app"
const val SECONDARY_APP_INTENT = "emptyactivity.main"
const val SECONDARY_APP_PKG = "android.permissionui.cts.usepermissionother"
const val TEST_PKG = "android.permissionui.cts"
const val CHANNEL_ID_31 = "test_channel_id"
const val BROADCAST_ACTION = "usepermission.createchannels.BROADCAST"
const val DELAY_MS = 1000L
const val LONG_DELAY_MS = 2000L

class CreateNotificationChannelsActivity : Activity() {
    private lateinit var notificationManager: NotificationManager
    private var launchActivityOnSecondResume = false
    private var isFirstResume = true
    private var windowHasFocus = false
    private var pendingCreateChannel = false
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState != null) {
            throw RuntimeException(
                "Activity was recreated (perhaps due to a configuration change?) " +
                    "and this activity doesn't currently know how to gracefully handle " +
                    "configuration changes."
            )
        }

        registerReceiver(receiver, IntentFilter(BROADCAST_ACTION), RECEIVER_EXPORTED)
        handleIntent(intent)
    }

    private fun handleIntent(providedIntent: Intent?, broacastAfterComplete: Boolean = false) {
        if (providedIntent == null) {
            return
        }
        val launchSecondActivity =
            providedIntent.getBooleanExtra(EXTRA_START_SECOND_ACTIVITY, false)
        notificationManager = baseContext.getSystemService(NotificationManager::class.java)!!
        if (providedIntent.getBooleanExtra(EXTRA_START_SECOND_APP, false)) {
            handler.postDelayed(
                {
                    val intent2 = Intent(SECONDARY_APP_INTENT)
                    intent2.`package` = SECONDARY_APP_PKG
                    intent2.addCategory(Intent.CATEGORY_DEFAULT)
                    handler.postDelayed({ createChannel() }, DELAY_MS)
                    startActivity(intent2)
                },
                LONG_DELAY_MS
            )
        } else if (providedIntent.getBooleanExtra(EXTRA_CREATE_CHANNELS, false)) {
            createChannel()
            if (launchSecondActivity) {
                launchActivityOnSecondResume = true
            }
        } else if (launchSecondActivity) {
            launchSecondActivity()
        }

        if (providedIntent.getBooleanExtra(EXTRA_REQUEST_OTHER_PERMISSIONS, false)) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 0)
        }

        if (providedIntent.getBooleanExtra(EXTRA_REQUEST_NOTIF_PERMISSION, false)) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        }

        if (broacastAfterComplete) {
            sendBroadcast(Intent(BROADCAST_ACTION).setPackage(TEST_PKG))
        }
    }

    private val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                handleIntent(intent, true)
            }
        }

    private fun launchSecondActivity() {
        handler.postDelayed(
            {
                val intent2 = Intent(Intent.ACTION_MAIN)
                intent2.`package` = packageName
                intent2.addCategory(Intent.CATEGORY_DEFAULT)
                intent2.putExtra(EXTRA_CREATE_CHANNELS, true)
                startActivity(intent2)
            },
            LONG_DELAY_MS
        )
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        windowHasFocus = hasFocus
        if (windowHasFocus && pendingCreateChannel) {
            pendingCreateChannel = false
            createChannel()
        }
    }

    private fun createChannel() {
        // Wait until window has focus so the permission prompt can be displayed
        if (!windowHasFocus) {
            pendingCreateChannel = true
            return
        }

        if (notificationManager.getNotificationChannel(CHANNEL_ID_31) == null) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID_31,
                    "Foreground Services",
                    NotificationManager.IMPORTANCE_HIGH
                )
            )
        }
    }

    override fun onResume() {
        super.onResume()
        if (!isFirstResume && launchActivityOnSecondResume) {
            launchSecondActivity()
        }
        isFirstResume = false
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val grantedPerms = arrayListOf<String>()
        for ((i, permName) in permissions.withIndex()) {
            if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                grantedPerms.add(permName)
            }
        }
        sendBroadcast(
            Intent(BROADCAST_ACTION)
                .putStringArrayListExtra(
                    PackageManager.EXTRA_REQUEST_PERMISSIONS_RESULTS,
                    grantedPerms
                )
                .setPackage(TEST_PKG)
        )
    }

    companion object {
        private val TAG = CreateNotificationChannelsActivity::class.simpleName
    }
}
