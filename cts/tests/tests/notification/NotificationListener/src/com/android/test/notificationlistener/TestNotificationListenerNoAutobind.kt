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

import android.content.ComponentName
import android.service.notification.NotificationListenerService

class TestNotificationListenerNoAutobind : NotificationListenerService() {
    var isConnected = false

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        isConnected = true
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isConnected = false
    }

    companion object {
        var instance: TestNotificationListenerNoAutobind? = null
            private set
        val componentName: ComponentName by lazy {
            val javaClass = TestNotificationListenerNoAutobind::class.java
            ComponentName(javaClass.getPackage().name, javaClass.name)
        }
    }
}
