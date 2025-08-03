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

import android.Manifest.permission.SEND_SAFETY_CENTER_UPDATE
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.NotificationManager.IMPORTANCE_DEFAULT
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
import android.os.Build.VERSION_CODES.TIRAMISU
import android.os.IBinder
import android.safetycenter.SafetyCenterManager
import android.safetycenter.SafetyCenterManager.ACTION_SAFETY_CENTER_ENABLED_CHANGED
import androidx.annotation.RequiresApi
import androidx.test.core.app.ApplicationProvider
import com.android.safetycenter.testing.Coroutines.TIMEOUT_LONG
import com.android.safetycenter.testing.Coroutines.TIMEOUT_SHORT
import com.android.safetycenter.testing.Coroutines.runBlockingWithTimeout
import com.android.safetycenter.testing.SafetyCenterApisWithShellPermissions.dismissSafetyCenterIssueWithPermission
import com.android.safetycenter.testing.SafetyCenterApisWithShellPermissions.executeSafetyCenterIssueActionWithPermission
import com.android.safetycenter.testing.SafetyCenterApisWithShellPermissions.refreshSafetySourcesWithPermission
import com.android.safetycenter.testing.SafetySourceIntentHandler.Request
import com.android.safetycenter.testing.SafetySourceIntentHandler.Response
import com.android.safetycenter.testing.ShellPermissions.callWithShellPermissionIdentity
import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Broadcast receiver used for testing broadcasts sent to safety sources. */
@RequiresApi(TIRAMISU)
class SafetySourceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) {
            throw IllegalArgumentException("Received null intent")
        }

        if (runInForegroundService) {
            context.startForegroundService(intent.setClass(context, ForegroundService::class.java))
        } else {
            runBlockingWithTimeout { safetySourceIntentHandler.handle(context, intent) }
        }
    }

    /**
     * A foreground [Service] that handles incoming intents in the same way as
     * [SafetySourceReceiver].
     *
     * This is used to verify that [SafetySourceReceiver] can start foreground services. Broadcast
     * receivers are typically not allowed to do that, but Safety Center uses special broadcast
     * options that allow safety source receivers to process lengthy operations in foreground
     * services instead.
     */
    class ForegroundService : Service() {
        private val serviceJob = Job()
        private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)

        override fun onBind(intent: Intent?): IBinder? = null

        override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
            val notificationManager = getSystemService(NotificationManager::class.java)!!
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    NOTIFICATION_CHANNEL_ID,
                    IMPORTANCE_DEFAULT
                )
            )
            startForeground(
                NOTIFICATION_ID,
                Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                    .setContentTitle("SafetySourceReceiver")
                    .setContentText(
                        "SafetySourceReceiver is processing an incoming intent in its " +
                            "ForegroundService"
                    )
                    .setSmallIcon(android.R.drawable.ic_info)
                    .build()
            )
            serviceScope.launch {
                try {
                    safetySourceIntentHandler.handle(this@ForegroundService, intent!!)
                } finally {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                }
            }
            super.onStartCommand(intent, flags, startId)
            return START_NOT_STICKY
        }

        override fun onDestroy() {
            serviceJob.cancel()
            super.onDestroy()
        }

        companion object {
            private const val NOTIFICATION_ID: Int = 1204
            private const val NOTIFICATION_CHANNEL_ID: String = "NOTIFICATION_CHANNEL_ID"
        }
    }

    companion object {
        @Volatile private var safetySourceIntentHandler = SafetySourceIntentHandler()

        /** Whether this receiver should handle incoming intents in a foreground service instead. */
        @Volatile var runInForegroundService = false

        /** Enables the [SafetySourceReceiver] for the user running the test. */
        fun setup() {
            setComponentEnabledState(true)
        }

        /** Resets the state of the [SafetySourceReceiver] between tests. */
        fun reset() {
            setComponentEnabledState(false)
            safetySourceIntentHandler.cancel()
            safetySourceIntentHandler = SafetySourceIntentHandler()
            runInForegroundService = false
        }

        private fun setComponentEnabledState(enabled: Boolean) {
            val componentName =
                ComponentName(getApplicationContext(), SafetySourceReceiver::class.java)
            getApplicationContext()
                .packageManager
                .setComponentEnabledSetting(
                    componentName,
                    if (enabled) COMPONENT_ENABLED_STATE_ENABLED
                    else COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
        }

        /**
         * Sets the given [SafetySourceIntentHandler] [response] for the given [request] on this
         * receiver.
         */
        fun setResponse(request: Request, response: Response) {
            runBlockingWithTimeout { safetySourceIntentHandler.setResponse(request, response) }
        }

        fun SafetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            refreshReason: Int,
            safetySourceIds: List<String>? = null,
            timeout: Duration = TIMEOUT_LONG
        ): String =
            callWithShellPermissionIdentity(SEND_SAFETY_CENTER_UPDATE) {
                refreshSafetySourcesWithPermission(refreshReason, safetySourceIds)
                receiveRefreshSafetySources(timeout)
            }

        fun SafetyCenterManager.refreshSafetySourcesWithoutReceiverPermissionAndWait(
            refreshReason: Int,
            safetySourceIds: List<String>? = null
        ) {
            refreshSafetySourcesWithPermission(refreshReason, safetySourceIds)
            WaitForBroadcasts.waitForBroadcasts()
            receiveRefreshSafetySources(TIMEOUT_SHORT)
        }

        fun setSafetyCenterEnabledWithReceiverPermissionAndWait(
            value: Boolean,
            timeout: Duration = TIMEOUT_LONG
        ): Boolean =
            callWithShellPermissionIdentity(SEND_SAFETY_CENTER_UPDATE) {
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

        fun SafetyCenterManager.executeSafetyCenterIssueActionWithPermissionAndWait(
            issueId: String,
            issueActionId: String,
            timeout: Duration = TIMEOUT_LONG
        ) {
            callWithShellPermissionIdentity(SEND_SAFETY_CENTER_UPDATE) {
                executeSafetyCenterIssueActionWithPermission(issueId, issueActionId)
                receiveResolveAction(timeout)
            }
        }

        fun SafetyCenterManager.dismissSafetyCenterIssueWithPermissionAndWait(
            issueId: String,
            timeout: Duration = TIMEOUT_LONG
        ) {
            callWithShellPermissionIdentity(SEND_SAFETY_CENTER_UPDATE) {
                dismissSafetyCenterIssueWithPermission(issueId)
                receiveDismissIssue(timeout)
            }
        }

        private fun receiveRefreshSafetySources(timeout: Duration = TIMEOUT_LONG): String =
            runBlockingWithTimeout(timeout) {
                safetySourceIntentHandler.receiveRefreshSafetySources()
            }

        /**
         * Waits for an [ACTION_SAFETY_CENTER_ENABLED_CHANGED] to be received by this receiver
         * within the given [timeout].
         */
        fun receiveSafetyCenterEnabledChanged(timeout: Duration = TIMEOUT_LONG): Boolean =
            runBlockingWithTimeout(timeout) {
                safetySourceIntentHandler.receiveSafetyCenterEnabledChanged()
            }

        /** Waits for this receiver to resolve an action within the given [timeout]. */
        fun receiveResolveAction(timeout: Duration = TIMEOUT_LONG) {
            runBlockingWithTimeout(timeout) { safetySourceIntentHandler.receiveResolveAction() }
        }

        private fun receiveDismissIssue(timeout: Duration = TIMEOUT_LONG) {
            runBlockingWithTimeout(timeout) { safetySourceIntentHandler.receiveDimissIssue() }
        }

        private fun getApplicationContext(): Context = ApplicationProvider.getApplicationContext()
    }
}
