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
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class SafetyCenterIssuesPersistenceInvalidTest {

    data class Params(
        private val testName: String,
        val fileName: String,
        val errorMessage: String,
        val causeErrorMessage: String?
    ) {
        override fun toString() = testName
    }

    @Parameterized.Parameter lateinit var params: Params

    @Test
    fun invalidFile_throws() {
        val file = File(PATH + params.fileName)

        val thrown =
            assertThrows(PersistenceException::class.java) {
                SafetyCenterIssuesPersistence.read(file)
            }

        assertThat(thrown).hasMessageThat().isEqualTo(params.errorMessage)
        if (params.causeErrorMessage != null) {
            assertThat(thrown.cause).hasMessageThat().isEqualTo(params.causeErrorMessage)
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun parameters() =
            arrayOf(
                Params(
                    "Corrupted",
                    "invalid_file_corrupted.txt",
                    "Failed to read file: ${PATH}invalid_file_corrupted.txt",
                    null
                ),
                Params(
                    "ExtraAttribute",
                    "invalid_file_extra_attribute.xml",
                    "Unexpected attribute extra",
                    null
                ),
                Params(
                    "ExtraElement",
                    "invalid_file_extra_element.xml",
                    "Element issue not closed",
                    null
                ),
                Params(
                    "ExtraRoot",
                    "invalid_file_extra_root.txt",
                    "Unexpected extra root element",
                    null
                ),
                Params(
                    "InconsistentDismissCount",
                    "invalid_file_inconsistent_dismiss_count.xml",
                    "Element issue invalid",
                    "dismissCount cannot be 0 if dismissedAt is present"
                ),
                Params(
                    "InconsistentDismissedAt",
                    "invalid_file_inconsistent_dismissed_at.xml",
                    "Element issue invalid",
                    "dismissedAt must be present if dismissCount is greater than 0"
                ),
                Params(
                    "InvalidDismissCount",
                    "invalid_file_invalid_dismiss_count.xml",
                    "Attribute value \"NaN\" for dismiss_count invalid",
                    null
                ),
                Params(
                    "InvalidDismissedAt",
                    "invalid_file_invalid_dismissed_at.xml",
                    "Attribute value \"NaN\" for dismissed_at_epoch_millis invalid",
                    null
                ),
                Params(
                    "InvalidFirstSeenAt",
                    "invalid_file_invalid_first_seen_at.xml",
                    "Attribute value \"NaN\" for first_seen_at_epoch_millis invalid",
                    null
                ),
                Params(
                    "InvalidNotificationDismissedAt",
                    "invalid_file_invalid_notification_dismissed_at.xml",
                    "Attribute value \"NaN\" for notification_dismissed_at_epoch_millis invalid",
                    null
                ),
                Params(
                    "InvalidVersion",
                    "invalid_file_invalid_version.xml",
                    "Attribute value \"NaN\" for version invalid",
                    null
                ),
                Params(
                    "MissingFirstSeenAt",
                    "invalid_file_missing_first_seen_at.xml",
                    "Element issue invalid",
                    "Required attribute firstSeenAt missing"
                ),
                Params(
                    "MissingKey",
                    "invalid_file_missing_key.xml",
                    "Element issue invalid",
                    "Required attribute key missing"
                ),
                Params(
                    "MissingVersion",
                    "invalid_file_missing_version.xml",
                    "Missing version",
                    null
                ),
                Params(
                    "NegativeDismissCount",
                    "invalid_file_negative_dismiss_count.xml",
                    "Attribute value \"-1\" for dismiss_count invalid",
                    null
                ),
                Params("WrongRoot", "invalid_file_wrong_root.xml", "Element issues missing", null),
                Params(
                    "WrongVersion",
                    "invalid_file_wrong_version.xml",
                    "Unsupported version: 99",
                    null
                )
            )
    }
}
