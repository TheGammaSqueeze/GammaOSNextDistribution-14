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
package com.android.bedstead.nene.notifications

import android.app.NotificationManager
import android.content.ComponentName
import android.os.Build
import android.service.notification.StatusBarNotification
import com.android.bedstead.nene.TestApis
import com.android.bedstead.nene.exceptions.AdbException
import com.android.bedstead.nene.exceptions.NeneException
import com.android.bedstead.nene.packages.ComponentReference
import com.android.bedstead.nene.permissions.CommonPermissions.MANAGE_NOTIFICATION_LISTENERS
import com.android.bedstead.nene.users.UserReference
import com.android.bedstead.nene.utils.ShellCommand
import com.android.bedstead.nene.utils.Tags
import com.android.bedstead.nene.utils.Versions
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/** Helper methods related to notifications.  */
object Notifications {

    private val mRegisteredListeners =
        Collections.newSetFromMap(ConcurrentHashMap<NotificationListener, Boolean>())
    private var mListenerAccessIsGranted = false

    /**
     * Creates a [NotificationListener].
     *
     *
     * This is required before interacting with notifications in any way. It is recommended that
     * you do this in a try() block so that the [NotificationListener] closes when you are
     * finished with it.
     */
    fun createListener(): NotificationListener {
        if (Tags.hasTag(Tags.USES_DEVICESTATE) && !Tags.hasTag(Tags.USES_NOTIFICATIONS)) {
            throw NeneException(
                "Tests which use Notifications must be annotated @NotificationsTest"
            )
        }
        val notificationListener = NotificationListener(this)
        mRegisteredListeners.add(notificationListener)
        initListenerIfRequired()
        return notificationListener
    }

    fun removeListener(listener: NotificationListener) {
        mRegisteredListeners.remove(listener)
        teardownListenerIfRequired()
    }

    private fun initListenerIfRequired() {
        if (mRegisteredListeners.isEmpty()) {
            return
        }
        if (mListenerAccessIsGranted) {
            return
        }
        mListenerAccessIsGranted = true
        setNotificationListenerAccessGranted(
            LISTENER_COMPONENT,  /* granted= */true, TestApis.users().instrumented()
        )
    }

    private fun teardownListenerIfRequired() {
        if (!mRegisteredListeners.isEmpty()) {
            return
        }
        if (!mListenerAccessIsGranted) {
            return
        }
        mListenerAccessIsGranted = false
        setNotificationListenerAccessGranted(
            LISTENER_COMPONENT,  /* granted= */false, TestApis.users().instrumented()
        )
    }

    internal fun onNotificationPosted(notification: Notification) {
        for (notificationListener in mRegisteredListeners) {
            notificationListener.onNotificationPosted(notification)
        }
    }

    /**
     * See [NotificationManager.setNotificationListenerAccessGranted].
     */
    fun setNotificationListenerAccessGranted(
        listener: ComponentReference, granted: Boolean, user: UserReference
    ) {
        if (!Versions.meetsMinimumSdkVersionRequirement(Build.VERSION_CODES.S)) {
            val command = if (granted) "allow_listener" else "disallow_listener"
            try {
                ShellCommand.builder("cmd notification")
                    .addOperand(command)
                    .addOperand(listener.componentName().flattenToShortString())
                    .allowEmptyOutput(true)
                    .validate { obj: String -> obj.isEmpty() }
                    .execute()
            } catch (e: AdbException) {
                throw NeneException(
                    "Error setting notification listener access $granted", e
                )
            }
            return
        }

        TestApis.permissions().withPermission(MANAGE_NOTIFICATION_LISTENERS).use {
            TestApis.context().androidContextAsUser(user)
                .getSystemService(NotificationManager::class.java)!!
                .setNotificationListenerAccessGranted(
                    listener.componentName(), granted,  /* userSet= */false
                )
        }
    }

    @JvmField
    val LISTENER_COMPONENT: ComponentReference = TestApis.packages().component(
        ComponentName(
            TestApis.context().instrumentedContext().packageName,
            NeneNotificationListenerService::class.java.canonicalName
        )
    )
}
