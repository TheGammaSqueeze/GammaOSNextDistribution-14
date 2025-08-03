/*
 * Copyright 2023 The Android Open Source Project
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

package android.media.audio.cts.audiorecordpermissiontests.common

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log

/**
 * Activity which fires an intent when moving to top and from top, and registers a
 * receiver to trigger finishing the activity.
 */
open class SimpleActivity : Activity() {
    val TAG = getAppName() + "SimpleActivity"
    val PREFIX = "android.media.audio.cts."+ getAppName()

    lateinit var recv: BroadcastReceiver

    override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)
        recv = object: BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    PREFIX + ACTION_ACTIVITY_DO_FINISH -> {
                        Log.i(TAG, "Finishing activity, sending intent")
                        this@SimpleActivity.finish()
                        sendBroadcast(Intent(PREFIX + ACTION_ACTIVITY_FINISHED)
                                .setPackage(TARGET_PACKAGE))
                    }
                }
            }
        }
        getApplicationContext().registerReceiver(
            recv, IntentFilter(PREFIX + ACTION_ACTIVITY_DO_FINISH), Context.RECEIVER_EXPORTED)
    }

    override fun onResume() {
        super.onResume()
        Log.i(TAG, "On Top, sending intent")
        sendBroadcast(Intent(PREFIX + ACTION_ACTIVITY_STARTED).setPackage(TARGET_PACKAGE))
    }

    override fun onDestroy() {
        super.onDestroy()
        getApplicationContext().unregisterReceiver(recv)
    }

    open fun getAppName() : String {
        return "Base"
    }
}
