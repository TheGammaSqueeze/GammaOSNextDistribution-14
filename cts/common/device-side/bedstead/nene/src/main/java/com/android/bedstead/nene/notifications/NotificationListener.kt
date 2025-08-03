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

import java.lang.IllegalStateException
import java.time.Duration
import java.util.ArrayDeque
import java.util.Deque

/**
 * Registered notification listener for receiving notifications on device.
 */
class NotificationListener internal constructor(private val notifications: Notifications) :
    AutoCloseable {

    private val receivedNotifications: Deque<Notification> by lazy {
        NeneNotificationListenerService.connectedLatch.await()
        val d = ArrayDeque<Notification>()

        d.addAll(NeneNotificationListenerService.instance.get()!!
            .activeNotifications.map { Notification(it) })

        d
    }

    private val query by lazy { query() }

    override fun close() {
        notifications.removeListener(this)
    }

    /**
     * Query received notifications.
     */
    fun query(): NotificationListenerQuery {
        return NotificationListenerQuery(receivedNotifications)
    }

    /**
     * Poll for received notifications.
     */
    fun poll(): Notification? {
        return query.poll()
    }

    /**
     * Poll for received notifications within [Duration].
     */
    fun poll(duration: Duration): Notification? {
        return query.poll(duration)
    }

    internal fun onNotificationPosted(notification: Notification) {
        receivedNotifications.addLast(notification)
    }
}
