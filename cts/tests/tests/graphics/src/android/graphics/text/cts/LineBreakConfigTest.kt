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

package android.graphics.text.cts

import android.graphics.text.LineBreakConfig
import androidx.test.filters.SmallTest
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import junit.framework.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4ClassRunner::class)
class LineBreakConfigTest {

    @Test
    fun mergeLineConfig() {
        val base = LineBreakConfig.Builder()
                .setLineBreakStyle(LineBreakConfig.LINE_BREAK_STYLE_LOOSE)
                .setLineBreakWordStyle(LineBreakConfig.LINE_BREAK_WORD_STYLE_NONE)
                .build()

        val overrideConfig = LineBreakConfig.Builder()
                .setLineBreakStyle(LineBreakConfig.LINE_BREAK_STYLE_NONE)
                .setLineBreakWordStyle(LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE)
                .build()

        val merged = base.merge(overrideConfig)

        assertEquals(LineBreakConfig.LINE_BREAK_STYLE_NONE, merged.lineBreakStyle)
        assertEquals(LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE, merged.lineBreakWordStyle)
    }

    @Test
    fun mergeLineConfig_unspecified_style() {
        val base = LineBreakConfig.Builder()
                .setLineBreakStyle(LineBreakConfig.LINE_BREAK_STYLE_LOOSE)
                .setLineBreakWordStyle(LineBreakConfig.LINE_BREAK_WORD_STYLE_NONE)
                .build()

        val overrideConfig = LineBreakConfig.Builder()
                .setLineBreakStyle(LineBreakConfig.LINE_BREAK_STYLE_UNSPECIFIED)
                .setLineBreakWordStyle(LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE)
                .build()

        val merged = base.merge(overrideConfig)

        assertEquals(LineBreakConfig.LINE_BREAK_STYLE_LOOSE, merged.lineBreakStyle)
        assertEquals(LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE, merged.lineBreakWordStyle)
    }

    @Test
    fun mergeLineConfig_unspecified_word_style() {
        val base = LineBreakConfig.Builder()
                .setLineBreakStyle(LineBreakConfig.LINE_BREAK_STYLE_LOOSE)
                .setLineBreakWordStyle(LineBreakConfig.LINE_BREAK_WORD_STYLE_NONE)
                .build()

        val overrideConfig = LineBreakConfig.Builder()
                .setLineBreakStyle(LineBreakConfig.LINE_BREAK_STYLE_NONE)
                .setLineBreakWordStyle(LineBreakConfig.LINE_BREAK_WORD_STYLE_UNSPECIFIED)
                .build()

        val merged = base.merge(overrideConfig)

        assertEquals(LineBreakConfig.LINE_BREAK_STYLE_NONE, merged.lineBreakStyle)
        assertEquals(LineBreakConfig.LINE_BREAK_WORD_STYLE_NONE, merged.lineBreakWordStyle)
    }

    @Test
    fun mergeLineConfig_Builder() {
        val builder = LineBreakConfig.Builder()
                .setLineBreakStyle(LineBreakConfig.LINE_BREAK_STYLE_LOOSE)
                .setLineBreakWordStyle(LineBreakConfig.LINE_BREAK_WORD_STYLE_NONE)

        val overrideConfig = LineBreakConfig.Builder()
                .setLineBreakStyle(LineBreakConfig.LINE_BREAK_STYLE_NONE)
                .setLineBreakWordStyle(LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE)
                .build()

        val merged = builder.merge(overrideConfig).build()

        assertEquals(LineBreakConfig.LINE_BREAK_STYLE_NONE, merged.lineBreakStyle)
        assertEquals(LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE, merged.lineBreakWordStyle)
    }

    @Test
    fun mergeLineConfig_unspecified_style_Builder() {
        val builder = LineBreakConfig.Builder()
                .setLineBreakStyle(LineBreakConfig.LINE_BREAK_STYLE_LOOSE)
                .setLineBreakWordStyle(LineBreakConfig.LINE_BREAK_WORD_STYLE_NONE)

        val overrideConfig = LineBreakConfig.Builder()
                .setLineBreakStyle(LineBreakConfig.LINE_BREAK_STYLE_UNSPECIFIED)
                .setLineBreakWordStyle(LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE)
                .build()

        val merged = builder.merge(overrideConfig).build()

        assertEquals(LineBreakConfig.LINE_BREAK_STYLE_LOOSE, merged.lineBreakStyle)
        assertEquals(LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE, merged.lineBreakWordStyle)
    }

    @Test
    fun mergeLineConfig_unspecified_word_style_Builder() {
        val builder = LineBreakConfig.Builder()
                .setLineBreakStyle(LineBreakConfig.LINE_BREAK_STYLE_LOOSE)
                .setLineBreakWordStyle(LineBreakConfig.LINE_BREAK_WORD_STYLE_NONE)

        val overrideConfig = LineBreakConfig.Builder()
                .setLineBreakStyle(LineBreakConfig.LINE_BREAK_STYLE_NONE)
                .setLineBreakWordStyle(LineBreakConfig.LINE_BREAK_WORD_STYLE_UNSPECIFIED)
                .build()

        val merged = builder.merge(overrideConfig).build()

        assertEquals(LineBreakConfig.LINE_BREAK_STYLE_NONE, merged.lineBreakStyle)
        assertEquals(LineBreakConfig.LINE_BREAK_WORD_STYLE_NONE, merged.lineBreakWordStyle)
    }
}
