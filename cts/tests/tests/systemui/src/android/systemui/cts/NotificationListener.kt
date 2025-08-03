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
package android.systemui.cts

import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.android.compatibility.common.util.ShellUtils.runShellCommand

class NotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (DEBUG) Log.d(TAG, "onNotificationPosted: $sbn")
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (DEBUG) Log.d(TAG, "onNotificationRemoved: $sbn")
    }

    override fun onListenerConnected() {
        if (DEBUG) Log.d(TAG, "onListenerConnected")
        instance = this
    }

    override fun onListenerDisconnected() {
        if (DEBUG) Log.d(TAG, "onListenerDisconnected")
        instance = null
    }

    companion object {
        private const val DEBUG = false
        private const val TAG = "SystemUi_Cts_NotificationListener"

        private const val DEFAULT_TIMEOUT = 5_000L
        private const val DEFAULT_POLL_INTERVAL = 500L

        private const val CMD_NOTIFICATION_ALLOW_LISTENER = "cmd notification allow_listener %s"
        private const val CMD_NOTIFICATION_DISALLOW_LISTENER =
                "cmd notification disallow_listener %s"
        private const val COMPONENT_NAME = "android.systemui.cts/.NotificationListener"

        private var instance: NotificationListener? = null

        fun startNotificationListener(): Boolean {
            if (instance != null) {
                return true
            }

            runShellCommand(CMD_NOTIFICATION_ALLOW_LISTENER.format(COMPONENT_NAME))

            return wait { instance != null }
        }

        fun stopNotificationListener(): Boolean {
            if (instance == null) {
                return true
            }

            runShellCommand(CMD_NOTIFICATION_DISALLOW_LISTENER.format(COMPONENT_NAME))
            return wait { instance == null }
        }

        fun waitForNotificationToAppear(
            predicate: (StatusBarNotification) -> Boolean
        ): StatusBarNotification? {
            instance?.let {
                return waitForResult(extractor = {
                    it.activeNotifications.firstOrNull(predicate)
                }).second
            } ?: throw IllegalStateException("NotificationListenerService is not connected")
        }

        fun waitForNotificationToDisappear(
            predicate: (StatusBarNotification) -> Boolean
        ): Boolean {
            return instance?.let {
                wait { it.activeNotifications.none(predicate) }
            } ?: throw IllegalStateException("NotificationListenerService is not connected")
        }

        private fun wait(condition: () -> Boolean): Boolean {
            val (success, _) = waitForResult(extractor = condition, validator = { it })
            return success
        }

        private fun <R> waitForResult(
            timeout: Long = DEFAULT_TIMEOUT,
            interval: Long = DEFAULT_POLL_INTERVAL,
            extractor: () -> R,
            validator: (R) -> Boolean = { it != null }
        ): Pair<Boolean, R?> {
            val startTime = SystemClock.uptimeMillis()
            do {
                val result = extractor()
                if (validator(result)) {
                    return (true to result)
                }
                SystemClock.sleep(interval)
            } while (SystemClock.uptimeMillis() - startTime < timeout)

            return (false to null)
        }
    }
}