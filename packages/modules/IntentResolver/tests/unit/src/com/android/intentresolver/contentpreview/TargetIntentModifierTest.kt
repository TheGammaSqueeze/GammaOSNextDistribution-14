/*
 * Copyright 2024 The Android Open Source Project
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

package com.android.intentresolver.contentpreview

import android.content.Intent
import android.content.Intent.ACTION_SEND
import android.content.Intent.ACTION_SEND_MULTIPLE
import android.content.Intent.EXTRA_STREAM
import android.net.Uri
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TargetIntentModifierTest {
    @Test
    fun testIntentActionChange() {
        val testSubject = TargetIntentModifier<Uri>(Intent(ACTION_SEND), { this }, { "image/png" })

        val u1 = createUri(1)
        val u2 = createUri(2)
        testSubject.onSelectionChanged(listOf(u1, u2)).let { intent ->
            assertThat(intent.action).isEqualTo(ACTION_SEND_MULTIPLE)
            assertThat(intent.getParcelableArrayListExtra(EXTRA_STREAM, Uri::class.java))
                .containsExactly(u1, u2)
                .inOrder()
        }

        testSubject.onSelectionChanged(listOf(u1)).let { intent ->
            assertThat(intent.action).isEqualTo(ACTION_SEND)
            assertThat(intent.getParcelableExtra(EXTRA_STREAM, Uri::class.java)).isEqualTo(u1)
        }
    }

    @Test
    fun testMimeTypeChange() {
        val testSubject =
            TargetIntentModifier<Pair<Uri, String?>>(Intent(ACTION_SEND), { first }, { second })

        val u1 = createUri(1)
        val u2 = createUri(2)
        testSubject.onSelectionChanged(listOf(u1 to "image/png", u2 to "image/png")).let { intent ->
            assertThat(intent.type).isEqualTo("image/png")
        }

        testSubject.onSelectionChanged(listOf(u1 to "image/png", u2 to "image/jpg")).let { intent ->
            assertThat(intent.type).isEqualTo("image/*")
        }

        testSubject.onSelectionChanged(listOf(u1 to "image/png", u2 to "video/mpeg")).let { intent
            ->
            assertThat(intent.type).isEqualTo("*/*")
        }

        testSubject.onSelectionChanged(listOf(u1 to "image/png", u2 to null)).let { intent ->
            assertThat(intent.type).isEqualTo("*/*")
        }
    }

    // TODO: test that the original intent's extras and flags remains the same
}

private fun createUri(id: Int) = Uri.parse("content://org.pkg/$id")

private data class Item(val uri: Uri, val mimeType: String?)
