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

import android.service.notification.StatusBarNotification
import android.util.Log
import com.android.bedstead.nene.exceptions.NeneException
import com.android.queryable.Queryable
import com.android.queryable.queries.NotificationQuery
import com.android.queryable.queries.NotificationQueryHelper
import com.android.queryable.queries.StringQuery
import com.android.queryable.queries.StringQueryHelper
import java.time.Duration
import java.time.Instant
import java.util.Deque

/** Query for notifications.  */
class NotificationListenerQuery internal constructor(private val receivedNotifications: Deque<Notification>) :
    Queryable {
    private val packageName = StringQueryHelper(this)
    private val notification = NotificationQueryHelper(this)
    private var hasStartedFetchingResults = false
    private var skippedPollResults = 0
    private val nonMatchingNotifications: MutableSet<Notification> = HashSet()

    /** Query by package name.  */
    fun wherePackageName(): StringQuery<NotificationListenerQuery> {
        check(!hasStartedFetchingResults) { "Cannot modify query after fetching results" }
        return packageName
    }

    /** Query by notification content.  */
    fun whereNotification(): NotificationQuery<NotificationListenerQuery> {
        check(!hasStartedFetchingResults) { "Cannot modify query after fetching results" }
        return notification
    }

    /** Poll for matching notifications.  */
    @JvmOverloads
    fun poll(timeout: Duration = Duration.ofSeconds(30)): Notification? {
        hasStartedFetchingResults = true
        val endTime = Instant.now().plus(timeout)
        while (Instant.now().isBefore(endTime)) {
            val nextResult = get(skippedPollResults)
            if (nextResult != null) {
                skippedPollResults++
                return nextResult
            }
            try {
                Thread.sleep(500)
            } catch (e: InterruptedException) {
                throw NeneException("Interrupted while polling", e)
            }
        }
        return null
    }

    private operator fun get(skipResults: Int): Notification? {
        var skipResults = skipResults
        hasStartedFetchingResults = true
        for (m in receivedNotifications) {
            if (matches(m)) {
                skipResults -= 1
                if (skipResults < 0) {
                    return m
                }
            } else {
                Log.d(LOG_TAG, "Found non-matching notification $m")
                nonMatchingNotifications.add(m)
            }
        }
        return null
    }

    /**
     * Get notifications which were received but didn't match the query.
     */
    fun nonMatchingNotifications(): Set<Notification> {
        return nonMatchingNotifications
    }

    private fun matches(n: Notification): Boolean {
        return (StringQueryHelper.matches(packageName, n.packageName)
                && NotificationQueryHelper.matches(notification, n.statusBarNotification))
    }

    override fun isEmptyQuery(): Boolean {
        return (Queryable.isEmptyQuery(packageName)
                && Queryable.isEmptyQuery(notification))
    }

    override fun describeQuery(fieldName: String): String {
        return "{" + Queryable.joinQueryStrings(
            packageName.describeQuery("packageName"),
            notification.describeQuery("notification")
        ) + "}"
    }

    override fun toString(): String {
        return describeQuery("")
    }

    companion object {
        private const val LOG_TAG = "NotificationListener"
    }
}
