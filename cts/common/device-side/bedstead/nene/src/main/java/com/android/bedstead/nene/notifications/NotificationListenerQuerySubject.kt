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

import com.google.common.truth.Fact
import com.google.common.truth.FailureMetadata
import com.google.common.truth.Subject
import com.google.common.truth.Subject.Factory
import com.google.common.truth.Truth
import java.time.Duration

/** [Subject] for [NotificationListenerQuery].  */
class NotificationListenerQuerySubject private constructor(
    metadata: FailureMetadata,
    private val mActual: NotificationListenerQuery
) : Subject(metadata, mActual) {
    /**
     * Asserts that a notification was posted (that [NotificationListenerQuery.poll] returns
     * non-null).
     */
    fun wasPosted() {
        if (mActual.poll() == null) {
            failWithoutActual(
                Fact.simpleFact(
                    "Expected notification to have been posted matching: "
                            + mActual + " but it was not posted. Did see: "
                            + mActual.nonMatchingNotifications()
                )
            )
        }
    }

    /**
     * Asserts that a notification was posted (that [NotificationListenerQuery.poll]
     * returns non-null).
     */
    fun wasPostedWithin(timeout: Duration?) {
        if (mActual.poll(timeout!!) == null) {
            failWithoutActual(
                Fact.simpleFact(
                    "Expected notification to have been posted matching: "
                            + mActual + " but it was not posted. Did see: "
                            + mActual.nonMatchingNotifications()
                )
            )
        }
    }

    companion object {
        /**
         * Assertions about [NotificationListenerQuery].
         */
        fun notificationListenerQuery(): Factory<NotificationListenerQuerySubject, NotificationListenerQuery> {
            return Factory { metadata: FailureMetadata, actual: NotificationListenerQuery ->
                NotificationListenerQuerySubject(
                    metadata,
                    actual
                )
            }
        }

        /**
         * Assertions about [NotificationListenerQuery].
         */
        @JvmStatic
        fun assertThat(
            actual: NotificationListenerQuery
        ): NotificationListenerQuerySubject {
            return Truth.assertAbout(notificationListenerQuery()).that(actual)
        }
    }
}
