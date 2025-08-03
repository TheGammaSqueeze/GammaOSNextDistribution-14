/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.test.notificationlistener

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.service.notification.NotificationListenerService

class NLSControlService : Service() {
    private inner class MyNLSControlService : INLSControlService.Stub() {

        override fun requestUnbindComponent() {
            NotificationListenerService.requestUnbind(
                TestNotificationListenerNoAutobind.componentName
            )
        }

        override fun requestRebindComponent() {
            NotificationListenerService.requestRebind(
                TestNotificationListenerNoAutobind.componentName
            )
        }

        override fun isNotificationListenerConnected(): Boolean {
            val listener = TestNotificationListenerNoAutobind.instance
            return listener?.isConnected ?: false
        }
    }

    private val mBinder = MyNLSControlService()
    override fun onBind(intent: Intent): IBinder? {
        return mBinder
    }
}
