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
package android.app.notification.legacy34.cts

import android.Manifest.permission.POST_NOTIFICATIONS
import android.app.Notification
import android.app.Notification.FLAG_NO_CLEAR
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.stubs.shared.NotificationHelper
import android.content.Context
import android.service.notification.StatusBarNotification
import androidx.test.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Home for tests that need to verify behavior for apps that target sdk version 34.
 */
@RunWith(AndroidJUnit4::class)
class NotificationManagerApi34Test {
    private lateinit var notificationManager: NotificationManager
    private lateinit var context: Context
    private lateinit var helper: NotificationHelper

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getContext()
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(POST_NOTIFICATIONS)
        helper = NotificationHelper(context)
        notificationManager = context.getSystemService(
                Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "name",
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
        assertEquals(context.applicationInfo.targetSdkVersion, 34)
    }

    @After
    fun tearDown() {
        notificationManager.cancelAll()
    }

    @Test
    fun testMediaStyle_noClearFlagNotSet() {
        val id = 99
        val notification: Notification = Notification.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.icon_black)
            .setStyle(Notification.MediaStyle())
            .build()
        notificationManager.notify(id, notification)
        val sbn: StatusBarNotification = helper.findPostedNotification(
            null,
            id,
            NotificationHelper.SEARCH_TYPE.APP
        )
        assertNotNull(sbn)
        assertNotEquals(FLAG_NO_CLEAR, sbn.getNotification().flags and FLAG_NO_CLEAR)
    }

    @Test
    fun testCustomMediaStyle_noClearFlagNotSet() {
        val id = 99
        val notification: Notification = Notification.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.icon_black)
            .setStyle(Notification.DecoratedMediaCustomViewStyle())
            .build()
        notificationManager.notify(id, notification)
        val sbn: StatusBarNotification = helper.findPostedNotification(
            null,
            id,
            NotificationHelper.SEARCH_TYPE.APP
        )
        assertNotNull(sbn)
        assertNotEquals(FLAG_NO_CLEAR, sbn.getNotification().flags and FLAG_NO_CLEAR)
    }

    companion object {
        val TAG = NotificationManagerApi34Test::class.java.simpleName
        val PKG = "android.app.notification.legacy34.cts"
        const val NOTIFICATION_CHANNEL_ID = "NotificationManagerApi34Test"
    }
}
