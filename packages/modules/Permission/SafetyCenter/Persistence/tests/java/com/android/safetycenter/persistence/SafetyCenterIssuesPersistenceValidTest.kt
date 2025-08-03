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

import com.android.safetycenter.persistence.PersistenceConstants.PATH
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.time.Instant
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class SafetyCenterIssuesPersistenceValidTest {
    data class Params(
        private val testName: String,
        val fileName: String,
        val expected: List<PersistedSafetyCenterIssue>
    ) {
        override fun toString() = testName
    }

    @Parameterized.Parameter lateinit var params: Params

    @Test
    fun validFile_matchesExpected() {
        val file = File(PATH + params.fileName)

        val actual = SafetyCenterIssuesPersistence.read(file)

        assertThat(actual).isEqualTo(params.expected)
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun parameters() =
            arrayOf(
                Params("FileNotFound", "file_not_found.xml", emptyList()),
                Params("ValidV0", "valid_file_v0.xml", listOf(ISSUE_1, ISSUE_2)),
                Params("ValidV1", "valid_file_v1.xml", listOf(ISSUE_0, ISSUE_1, ISSUE_2, ISSUE_3)),
                Params(
                    "ValidV2",
                    "valid_file_v2.xml",
                    listOf(ISSUE_0, ISSUE_1, ISSUE_2, ISSUE_3, ISSUE_4, ISSUE_5)
                )
            )

        private val ISSUE_0 =
            PersistedSafetyCenterIssue.Builder()
                .setKey("key0")
                .setFirstSeenAt(Instant.ofEpochMilli(1654041600000))
                .build()

        private val ISSUE_1 =
            PersistedSafetyCenterIssue.Builder()
                .setKey("key1")
                .setFirstSeenAt(Instant.ofEpochMilli(1654041600000))
                .build()

        private val ISSUE_2 =
            PersistedSafetyCenterIssue.Builder()
                .setKey("key2")
                .setFirstSeenAt(Instant.ofEpochMilli(1654128000000))
                .setDismissedAt(Instant.ofEpochMilli(1654214400000))
                .setDismissCount(1)
                .build()

        private val ISSUE_3 =
            PersistedSafetyCenterIssue.Builder()
                .setKey("key3")
                .setFirstSeenAt(Instant.ofEpochMilli(1654128000000))
                .setDismissedAt(Instant.ofEpochMilli(1654214400000))
                .setDismissCount(10)
                .build()

        private val ISSUE_4 =
            PersistedSafetyCenterIssue.Builder()
                .setKey("key4")
                .setFirstSeenAt(Instant.ofEpochMilli(1654128000000))
                .setDismissedAt(Instant.ofEpochMilli(1654214400000))
                .setNotificationDismissedAt(Instant.ofEpochMilli(1654214400000))
                .setDismissCount(1)
                .build()

        private val ISSUE_5 =
            PersistedSafetyCenterIssue.Builder()
                .setKey("key5")
                .setFirstSeenAt(Instant.ofEpochMilli(1654128000000))
                .setNotificationDismissedAt(Instant.ofEpochMilli(1654214400000))
                .build()
    }
}
