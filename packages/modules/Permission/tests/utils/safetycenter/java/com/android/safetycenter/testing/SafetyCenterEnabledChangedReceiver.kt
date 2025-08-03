/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.safetycenter.testing

import android.Manifest.permission.READ_SAFETY_CENTER_STATUS
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build.VERSION_CODES.TIRAMISU
import android.safetycenter.SafetyCenterManager.ACTION_SAFETY_CENTER_ENABLED_CHANGED
import androidx.annotation.RequiresApi
import com.android.safetycenter.testing.Coroutines.TIMEOUT_LONG
import com.android.safetycenter.testing.Coroutines.TIMEOUT_SHORT
import com.android.safetycenter.testing.Coroutines.runBlockingWithTimeout
import com.android.safetycenter.testing.ShellPermissions.callWithShellPermissionIdentity
import java.time.Duration

/** Broadcast receiver used for testing broadcasts sent when the SafetyCenter flag changes. */
@RequiresApi(TIRAMISU)
class SafetyCenterEnabledChangedReceiver(private val context: Context) : BroadcastReceiver() {

    private val mSafetySourceIntentHandler = SafetySourceIntentHandler()

    init {
        context.registerReceiver(this, IntentFilter(ACTION_SAFETY_CENTER_ENABLED_CHANGED))
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) {
            throw IllegalArgumentException("Received null intent")
        }

        if (intent.action != ACTION_SAFETY_CENTER_ENABLED_CHANGED) {
            throw IllegalArgumentException("Received intent with action: ${intent.action}")
        }

        runBlockingWithTimeout { mSafetySourceIntentHandler.handle(context, intent) }
    }

    fun setSafetyCenterEnabledWithReceiverPermissionAndWait(
        value: Boolean,
        timeout: Duration = TIMEOUT_LONG
    ): Boolean =
        callWithShellPermissionIdentity(READ_SAFETY_CENTER_STATUS) {
            SafetyCenterFlags.isEnabled = value
            receiveSafetyCenterEnabledChanged(timeout)
        }

    fun setSafetyCenterEnabledWithoutReceiverPermissionAndWait(
        value: Boolean,
    ) {
        SafetyCenterFlags.isEnabled = value
        WaitForBroadcasts.waitForBroadcasts()
        receiveSafetyCenterEnabledChanged(TIMEOUT_SHORT)
    }

    fun unregister() {
        context.unregisterReceiver(this)
        mSafetySourceIntentHandler.cancel()
    }

    /**
     * Waits for an [ACTION_SAFETY_CENTER_ENABLED_CHANGED] to be received by this receiver within
     * the given [timeout].
     */
    fun receiveSafetyCenterEnabledChanged(timeout: Duration = TIMEOUT_LONG): Boolean =
        runBlockingWithTimeout(timeout) {
            mSafetySourceIntentHandler.receiveSafetyCenterEnabledChanged()
        }
}
