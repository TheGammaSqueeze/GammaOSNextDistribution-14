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
package com.android.intentresolver.v2.ui.viewmodel

import android.content.Intent
import android.content.Intent.ACTION_VIEW
import android.net.Uri
import android.os.UserHandle
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import com.android.intentresolver.v2.ResolverActivity.PROFILE_WORK
import com.android.intentresolver.v2.shared.model.Profile.Type.WORK
import com.android.intentresolver.v2.ui.model.ActivityModel
import com.android.intentresolver.v2.ui.model.ChooserRequest
import com.android.intentresolver.v2.ui.model.ResolverRequest
import com.android.intentresolver.v2.validation.Invalid
import com.android.intentresolver.v2.validation.UncaughtException
import com.android.intentresolver.v2.validation.Valid
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

private val targetUri = Uri.parse("content://example.com/123")

private fun createActivityModel(
    targetIntent: Intent,
    referrer: Uri? = null,
) =
    ActivityModel(
        intent = targetIntent,
        launchedFromUid = 10000,
        launchedFromPackage = "com.android.example",
        referrer = referrer ?: "android-app://com.android.example".toUri()
    )

class ResolverRequestTest {
    @Test
    fun testDefaults() {
        val intent = Intent(ACTION_VIEW).apply { data = targetUri }
        val activity = createActivityModel(intent)

        val result = readResolverRequest(activity)

        assertThat(result).isInstanceOf(Valid::class.java)
        result as Valid<ResolverRequest>

        assertThat(result.warnings).isEmpty()

        assertThat(result.value.intent.filterEquals(activity.intent)).isTrue()
        assertThat(result.value.callingUser).isNull()
        assertThat(result.value.selectedProfile).isNull()
    }

    @Test
    fun testInvalidSelectedProfile() {
        val intent =
            Intent(ACTION_VIEW).apply {
                data = targetUri
                putExtra(EXTRA_SELECTED_PROFILE, -1000)
            }

        val activity = createActivityModel(intent)

        val result = readResolverRequest(activity)

        assertThat(result).isInstanceOf(Invalid::class.java)
        result as Invalid<ResolverRequest>

        assertWithMessage("the first finding")
            .that(result.errors.firstOrNull())
            .isInstanceOf(UncaughtException::class.java)
    }

    @Test
    fun payloadIntents_includesOnlyTarget() {
        val intent2 = Intent(Intent.ACTION_SEND_MULTIPLE)
        val intent1 =
            Intent(Intent.ACTION_SEND).apply {
                putParcelableArrayListExtra(Intent.EXTRA_ALTERNATE_INTENTS, arrayListOf(intent2))
            }
        val activity = createActivityModel(targetIntent = intent1)

        val result = readResolverRequest(activity)

        assertThat(result).isInstanceOf(Valid::class.java)
        result as Valid<ResolverRequest>

        // Assert that payloadIntents does NOT include EXTRA_ALTERNATE_INTENTS
        // that is only supported for Chooser and should be not be added here.
        assertThat(result.value.payloadIntents).containsExactly(intent1)
    }

    @Test
    fun testAllValues() {
        val intent = Intent(ACTION_VIEW).apply { data = Uri.parse("content://example.com/123") }
        val activity = createActivityModel(targetIntent = intent)

        activity.intent.putExtras(
            bundleOf(
                EXTRA_CALLING_USER to UserHandle.of(123),
                EXTRA_SELECTED_PROFILE to PROFILE_WORK,
                EXTRA_IS_AUDIO_CAPTURE_DEVICE to true,
            )
        )

        val result = readResolverRequest(activity)

        assertThat(result).isInstanceOf(Valid::class.java)
        result as Valid<ResolverRequest>

        assertThat(result.value.intent.filterEquals(activity.intent)).isTrue()
        assertThat(result.value.isAudioCaptureDevice).isTrue()
        assertThat(result.value.callingUser).isEqualTo(UserHandle.of(123))
        assertThat(result.value.selectedProfile).isEqualTo(WORK)
    }
}
