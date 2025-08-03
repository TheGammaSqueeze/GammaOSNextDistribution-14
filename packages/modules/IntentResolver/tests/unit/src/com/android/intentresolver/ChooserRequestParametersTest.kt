/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.intentresolver

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.service.chooser.ChooserAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChooserRequestParametersTest {
    @Test
    fun testChooserActions() {
        val actionCount = 3
        val intent = Intent(Intent.ACTION_SEND)
        val actions = createChooserActions(actionCount)
        val chooserIntent =
            Intent(Intent.ACTION_CHOOSER).apply {
                putExtra(Intent.EXTRA_INTENT, intent)
                putExtra(Intent.EXTRA_CHOOSER_CUSTOM_ACTIONS, actions)
            }
        val request = ChooserRequestParameters(chooserIntent, "", Uri.EMPTY)
        assertThat(request.chooserActions).containsExactlyElementsIn(actions).inOrder()
    }

    @Test
    fun testChooserActions_empty() {
        val intent = Intent(Intent.ACTION_SEND)
        val chooserIntent =
            Intent(Intent.ACTION_CHOOSER).apply { putExtra(Intent.EXTRA_INTENT, intent) }
        val request = ChooserRequestParameters(chooserIntent, "", Uri.EMPTY)
        assertThat(request.chooserActions).isEmpty()
    }

    @Test
    fun testChooserActions_tooMany() {
        val intent = Intent(Intent.ACTION_SEND)
        val chooserActions = createChooserActions(10)
        val chooserIntent =
            Intent(Intent.ACTION_CHOOSER).apply {
                putExtra(Intent.EXTRA_INTENT, intent)
                putExtra(Intent.EXTRA_CHOOSER_CUSTOM_ACTIONS, chooserActions)
            }

        val request = ChooserRequestParameters(chooserIntent, "", Uri.EMPTY)

        val expectedActions = chooserActions.sliceArray(0 until 5)
        assertThat(request.chooserActions).containsExactlyElementsIn(expectedActions).inOrder()
    }

    private fun createChooserActions(count: Int): Array<ChooserAction> {
        return Array(count) { i -> createChooserAction("$i") }
    }

    private fun createChooserAction(label: CharSequence): ChooserAction {
        val icon = Icon.createWithContentUri("content://org.package.app/image")
        val pendingIntent =
            PendingIntent.getBroadcast(
                InstrumentationRegistry.getInstrumentation().getTargetContext(),
                0,
                Intent("TESTACTION"),
                PendingIntent.FLAG_IMMUTABLE
            )
        return ChooserAction.Builder(icon, label, pendingIntent).build()
    }
}
