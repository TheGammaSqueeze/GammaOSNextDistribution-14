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

package com.android.safetycenter.persistence

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.safetycenter.testing.EqualsHashCodeToStringTester
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import org.junit.Test
import org.junit.runner.RunWith

/** CTS tests for [PersistedSafetyCenterIssue]. */
@RunWith(AndroidJUnit4::class)
class PersistedSafetyCenterIssueTest {

    @Test
    fun getKey_returnsKey() {
        assertThat(ACTIVE_ISSUE.key).isEqualTo(ACTIVE_ISSUE_KEY)
        assertThat(DISMISSED_ISSUE.key).isEqualTo(DISMISSED_ISSUE_KEY)
    }

    @Test
    fun getFirstSeenAt_returnsFirstSeenAt() {
        assertThat(ACTIVE_ISSUE.firstSeenAt).isEqualTo(INSTANT)
        assertThat(DISMISSED_ISSUE.firstSeenAt).isEqualTo(INSTANT)
    }

    @Test
    fun getDismissedAt_returnsDismissedAt() {
        assertThat(ACTIVE_ISSUE.dismissedAt).isEqualTo(null)
        assertThat(DISMISSED_ISSUE.dismissedAt).isEqualTo(INSTANT)
    }

    @Test
    fun getDismissCount_returnsDismissCount() {
        assertThat(ACTIVE_ISSUE.dismissCount).isEqualTo(0)
        assertThat(DISMISSED_ISSUE.dismissCount).isEqualTo(1)
    }

    @Test
    fun getNotificationDismissedAt_returnsNotificationDismissedAt() {
        assertThat(ACTIVE_ISSUE.notificationDismissedAt).isEqualTo(null)
        assertThat(DISMISSED_ISSUE.notificationDismissedAt).isEqualTo(INSTANT)
    }

    @Test
    fun equalsHashCodeToString_usingEqualsHashCodeToStringTester() {
        EqualsHashCodeToStringTester.of<PersistedSafetyCenterIssue>()
            .addEqualityGroup(
                ACTIVE_ISSUE,
                PersistedSafetyCenterIssue.Builder()
                    .setKey(ACTIVE_ISSUE_KEY)
                    .setFirstSeenAt(INSTANT)
                    .build()
            )
            .addEqualityGroup(DISMISSED_ISSUE)
            .addEqualityGroup(
                PersistedSafetyCenterIssue.Builder()
                    .setKey("other")
                    .setFirstSeenAt(INSTANT)
                    .setDismissedAt(INSTANT)
                    .setDismissCount(1)
                    .build()
            )
            .addEqualityGroup(
                PersistedSafetyCenterIssue.Builder()
                    .setKey(DISMISSED_ISSUE_KEY)
                    .setFirstSeenAt(Instant.ofEpochMilli(0))
                    .setDismissedAt(INSTANT)
                    .setDismissCount(1)
                    .build()
            )
            .addEqualityGroup(
                PersistedSafetyCenterIssue.Builder()
                    .setKey(DISMISSED_ISSUE_KEY)
                    .setFirstSeenAt(INSTANT)
                    .setDismissedAt(Instant.ofEpochMilli(0))
                    .setDismissCount(1)
                    .build()
            )
            .addEqualityGroup(
                PersistedSafetyCenterIssue.Builder()
                    .setKey(DISMISSED_ISSUE_KEY)
                    .setFirstSeenAt(INSTANT)
                    .setDismissedAt(INSTANT)
                    .setDismissCount(99)
                    .build()
            )
            .test()
    }

    companion object {
        private const val ACTIVE_ISSUE_KEY = "active_key"
        private const val DISMISSED_ISSUE_KEY = "dismissed_key"
        private val INSTANT = Instant.ofEpochMilli(1654041600000)

        private val ACTIVE_ISSUE =
            PersistedSafetyCenterIssue.Builder()
                .setKey(ACTIVE_ISSUE_KEY)
                .setFirstSeenAt(INSTANT)
                .build()

        private val DISMISSED_ISSUE =
            PersistedSafetyCenterIssue.Builder()
                .setKey(DISMISSED_ISSUE_KEY)
                .setFirstSeenAt(INSTANT)
                .setDismissedAt(INSTANT)
                .setDismissCount(1)
                .setNotificationDismissedAt(INSTANT)
                .build()
    }
}
