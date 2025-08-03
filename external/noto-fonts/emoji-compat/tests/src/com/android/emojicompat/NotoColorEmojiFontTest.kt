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

package com.android.emojicompat

import androidx.test.runner.AndroidJUnit4
import androidx.test.filters.SmallTest

import com.google.common.truth.Truth.assertThat

import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class NotoColorEmojiTest {

    @Test
    fun flags_font_subset_font_has_the_same_version() {
        val subsetBuffer = NotoColorEmojiHelper.getSubsetFont()
        val flagsBuffer = NotoColorEmojiHelper.getFlagFont()

        val subsetVersion = FontFileHelper.getVersion(subsetBuffer)
        val flagsVersion = FontFileHelper.getVersion(flagsBuffer)

        assertThat(subsetVersion).isEqualTo(flagsVersion)
    }

    @Test
    fun subset_font_has_proper_PostScript_name() {
        assertThat(FontFileHelper.getPostScriptName(NotoColorEmojiHelper.getSubsetFont()))
                .isEqualTo("NotoColorEmoji")
    }

    @Test
    fun flags_font_has_proper_PostScript_name() {
        assertThat(FontFileHelper.getPostScriptName(NotoColorEmojiHelper.getFlagFont()))
                .isEqualTo("NotoColorEmojiFlags")
    }
}
