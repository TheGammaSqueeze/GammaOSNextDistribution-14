/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.intentresolver.v2.ui.model

import android.content.Intent
import android.content.Intent.ACTION_CHOOSER
import android.content.Intent.EXTRA_TEXT
import android.net.Uri
import com.android.intentresolver.v2.ext.toParcelAndBack
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

class ActivityModelTest {

    @Test
    fun testDefaultValues() {
        val input = ActivityModel(Intent(ACTION_CHOOSER), 0, "example.com", null)

        val output = input.toParcelAndBack()

        assertEquals(input, output)
    }

    @Test
    fun testCommonValues() {
        val intent = Intent(ACTION_CHOOSER).apply { putExtra(EXTRA_TEXT, "Test") }
        val input =
            ActivityModel(intent, 1234, "com.example", Uri.parse("android-app://example.com"))

        val output = input.toParcelAndBack()

        assertEquals(input, output)
    }

    @Test
    fun testReferrerPackage_withAppReferrer_usesReferrer() {
        val launch1 =
            ActivityModel(
                intent = Intent(),
                launchedFromUid = 1000,
                launchedFromPackage = "other.example.com",
                referrer = Uri.parse("android-app://app.example.com")
            )

        assertThat(launch1.referrerPackage).isEqualTo("app.example.com")
    }

    @Test
    fun testReferrerPackage_httpReferrer_isNull() {
        val launch =
            ActivityModel(
                intent = Intent(),
                launchedFromUid = 1000,
                launchedFromPackage = "example.com",
                referrer = Uri.parse("http://some.other.value")
            )

        assertThat(launch.referrerPackage).isNull()
    }

    @Test
    fun testReferrerPackage_nullReferrer_isNull() {
        val launch =
            ActivityModel(
                intent = Intent(),
                launchedFromUid = 1000,
                launchedFromPackage = "example.com",
                referrer = null
            )

        assertThat(launch.referrerPackage).isNull()
    }

    private fun assertEquals(expected: ActivityModel, actual: ActivityModel) {
        // Test fields separately: Intent does not override equals()
        assertWithMessage("%s.filterEquals(%s)", actual.intent, expected.intent)
            .that(actual.intent.filterEquals(expected.intent))
            .isTrue()

        assertWithMessage("actual fromUid is equal to expected")
            .that(actual.launchedFromUid)
            .isEqualTo(expected.launchedFromUid)

        assertWithMessage("actual fromPackage is equal to expected")
            .that(actual.launchedFromPackage)
            .isEqualTo(expected.launchedFromPackage)

        assertWithMessage("actual referrer is equal to expected")
            .that(actual.referrer)
            .isEqualTo(expected.referrer)
    }
}
