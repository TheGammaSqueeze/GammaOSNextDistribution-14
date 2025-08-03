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
package com.android.intentresolver.v2.ext

import android.content.ComponentName
import android.content.Intent
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.util.function.Predicate
import org.junit.Test

class IntentExtTest {

    private val hasSendAction =
        Predicate<Intent> {
            it?.action == Intent.ACTION_SEND || it?.action == Intent.ACTION_SEND_MULTIPLE
        }

    @Test
    fun hasAction() {
        val sendIntent = Intent(Intent.ACTION_SEND)
        assertThat(sendIntent.hasAction(Intent.ACTION_SEND)).isTrue()
        assertThat(sendIntent.hasAction(Intent.ACTION_VIEW)).isFalse()
    }

    @Test
    fun hasComponent() {
        assertThat(Intent().hasComponent()).isFalse()
        assertThat(Intent().setComponent(ComponentName("A", "B")).hasComponent()).isTrue()
    }

    @Test
    fun hasSendAction() {
        assertThat(Intent(Intent.ACTION_SEND).hasSendAction()).isTrue()
        assertThat(Intent(Intent.ACTION_SEND_MULTIPLE).hasSendAction()).isTrue()
        assertThat(Intent(Intent.ACTION_SENDTO).hasSendAction()).isFalse()
        assertThat(Intent(Intent.ACTION_VIEW).hasSendAction()).isFalse()
    }

    @Test
    fun hasSingleCategory() {
        val intent = Intent().addCategory(Intent.CATEGORY_HOME)
        assertThat(intent.hasSingleCategory(Intent.CATEGORY_HOME)).isTrue()
        assertThat(intent.hasSingleCategory(Intent.CATEGORY_DEFAULT)).isFalse()

        intent.addCategory(Intent.CATEGORY_TEST)
        assertThat(intent.hasSingleCategory(Intent.CATEGORY_TEST)).isFalse()
    }

    @Test
    fun ifMatch_matched() {
        val sendIntent = Intent(Intent.ACTION_SEND)
        val sendMultipleIntent = Intent(Intent.ACTION_SEND_MULTIPLE)

        sendIntent.ifMatch(hasSendAction) { addFlags(Intent.FLAG_ACTIVITY_MATCH_EXTERNAL) }
        sendMultipleIntent.ifMatch(hasSendAction) { addFlags(Intent.FLAG_ACTIVITY_MATCH_EXTERNAL) }
        assertWithMessage("sendIntent flags")
            .that(sendIntent.flags)
            .isEqualTo(Intent.FLAG_ACTIVITY_MATCH_EXTERNAL)
        assertWithMessage("sendMultipleIntent flags")
            .that(sendMultipleIntent.flags)
            .isEqualTo(Intent.FLAG_ACTIVITY_MATCH_EXTERNAL)
    }

    @Test
    fun ifMatch_notMatched() {
        val viewIntent = Intent(Intent.ACTION_VIEW)

        viewIntent.ifMatch(hasSendAction) { addFlags(Intent.FLAG_ACTIVITY_MATCH_EXTERNAL) }
        assertWithMessage("viewIntent flags").that(viewIntent.flags).isEqualTo(0)
    }
}
