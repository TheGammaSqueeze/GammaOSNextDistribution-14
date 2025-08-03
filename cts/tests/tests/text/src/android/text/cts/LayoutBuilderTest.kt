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
package android.text.cts

import android.graphics.text.LineBreakConfig
import android.platform.test.annotations.RequiresFlagsEnabled
import android.text.BoringLayout
import android.text.Layout
import android.text.Layout.Alignment
import android.text.Layout.BREAK_STRATEGY_BALANCED
import android.text.Layout.HYPHENATION_FREQUENCY_FULL
import android.text.Layout.JUSTIFICATION_MODE_INTER_WORD
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.TextUtils
import android.text.style.LeadingMarginSpan
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.text.flags.Flags.FLAG_USE_BOUNDS_FOR_WIDTH
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
@RequiresFlagsEnabled(FLAG_USE_BOUNDS_FOR_WIDTH)
class LayoutBuilderTest {
    private val BORING_TEXT = "hello, world."
    private val STATIC_LAYOUT_TEXT = SpannableString("hello, world").apply {
        setSpan(LeadingMarginSpan.Standard(10), 0, this.length, Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
    }

    private val PAINT = TextPaint().apply {
        textSize = 10f
    }
    private val WIDTH = 1000

    private fun makeBuilder(text: CharSequence) = Layout.Builder(text, 0, text.length, PAINT, WIDTH)

    @Test
    fun buildBoringLayout() {
        val layout = makeBuilder(BORING_TEXT).build()
        assertThat(layout).isInstanceOf(BoringLayout::class.java)
        assertThat(layout.text).isEqualTo(BORING_TEXT)
        assertThat(layout.paint).isSameInstanceAs(PAINT)
        assertThat(layout.width).isEqualTo(WIDTH)
    }

    @Test
    fun buildStaticLayout() {
        val layout = makeBuilder(STATIC_LAYOUT_TEXT).build()
        assertThat(layout).isInstanceOf(StaticLayout::class.java)
        assertThat(layout.text).isEqualTo(STATIC_LAYOUT_TEXT)
        assertThat(layout.paint).isSameInstanceAs(PAINT)
        assertThat(layout.width).isEqualTo(WIDTH)
    }

    @Test
    fun buildBoringDefaults() {
        val layout = makeBuilder(BORING_TEXT).build()
        assertThat(layout.alignment).isEqualTo(Alignment.ALIGN_NORMAL)
        assertThat(layout.textDirectionHeuristic)
                .isEqualTo(TextDirectionHeuristics.FIRSTSTRONG_LTR)
        assertThat(layout.spacingMultiplier).isEqualTo(1.0f)
        assertThat(layout.lineSpacingMultiplier).isEqualTo(1.0f)
        assertThat(layout.spacingAdd).isEqualTo(0.0f)
        assertThat(layout.lineSpacingAmount).isEqualTo(0.0f)
        assertThat(layout.isFontPaddingIncluded).isTrue()
        assertThat(layout.isFallbackLineSpacingEnabled).isFalse()
        assertThat(layout.ellipsizedWidth).isEqualTo(WIDTH)
        assertThat(layout.ellipsize).isNull()
        assertThat(layout.maxLines).isEqualTo(Int.MAX_VALUE)
        assertThat(layout.breakStrategy).isEqualTo(Layout.BREAK_STRATEGY_SIMPLE)
        assertThat(layout.hyphenationFrequency).isEqualTo(Layout.HYPHENATION_FREQUENCY_NONE)
        assertThat(layout.leftIndents).isNull()
        assertThat(layout.rightIndents).isNull()
        assertThat(layout.justificationMode).isEqualTo(Layout.JUSTIFICATION_MODE_NONE)

        val defaultConfig = LineBreakConfig.Builder()
                .setLineBreakStyle(LineBreakConfig.LINE_BREAK_STYLE_NONE)
                .setLineBreakWordStyle(LineBreakConfig.LINE_BREAK_WORD_STYLE_NONE)
                .build()
        assertThat(layout.lineBreakConfig).isEqualTo(defaultConfig)
    }

    @Test
    fun buildStaticDefaults() {
        val layout = makeBuilder(STATIC_LAYOUT_TEXT).build()
        assertThat(layout.alignment).isEqualTo(Alignment.ALIGN_NORMAL)
        assertThat(layout.textDirectionHeuristic)
                .isEqualTo(TextDirectionHeuristics.FIRSTSTRONG_LTR)
        assertThat(layout.spacingMultiplier).isEqualTo(1.0f)
        assertThat(layout.lineSpacingMultiplier).isEqualTo(1.0f)
        assertThat(layout.spacingAdd).isEqualTo(0.0f)
        assertThat(layout.lineSpacingAmount).isEqualTo(0.0f)
        assertThat(layout.isFontPaddingIncluded).isTrue()
        assertThat(layout.isFallbackLineSpacingEnabled).isFalse()
        assertThat(layout.ellipsizedWidth).isEqualTo(WIDTH)
        assertThat(layout.ellipsize).isNull()
        assertThat(layout.maxLines).isEqualTo(Int.MAX_VALUE)
        assertThat(layout.breakStrategy).isEqualTo(Layout.BREAK_STRATEGY_SIMPLE)
        assertThat(layout.hyphenationFrequency).isEqualTo(Layout.HYPHENATION_FREQUENCY_NONE)
        assertThat(layout.leftIndents).isNull()
        assertThat(layout.rightIndents).isNull()
        assertThat(layout.justificationMode).isEqualTo(Layout.JUSTIFICATION_MODE_NONE)

        val defaultConfig = LineBreakConfig.Builder()
                .setLineBreakStyle(LineBreakConfig.LINE_BREAK_STYLE_NONE)
                .setLineBreakWordStyle(LineBreakConfig.LINE_BREAK_WORD_STYLE_NONE)
                .build()
        assertThat(layout.lineBreakConfig).isEqualTo(defaultConfig)
    }

    @Test
    fun buildLayout_SetGetAlignment() {
        val boringLayout = makeBuilder(BORING_TEXT)
                .setAlignment(Alignment.ALIGN_OPPOSITE)
                .build()
        assertThat(boringLayout.alignment).isEqualTo(Alignment.ALIGN_OPPOSITE)

        val staticLayout = makeBuilder(STATIC_LAYOUT_TEXT)
                .setAlignment(Alignment.ALIGN_OPPOSITE)
                .build()
        assertThat(staticLayout.alignment).isEqualTo(Alignment.ALIGN_OPPOSITE)
    }

    @Test
    fun buildLayout_SetGetTextDirectionHeuristics() {
        val boringLayout = makeBuilder(BORING_TEXT)
                .setTextDirectionHeuristic(TextDirectionHeuristics.FIRSTSTRONG_RTL)
                .build()
        assertThat(boringLayout.textDirectionHeuristic)
                .isEqualTo(TextDirectionHeuristics.FIRSTSTRONG_RTL)

        val staticLayout = makeBuilder(STATIC_LAYOUT_TEXT)
                .setTextDirectionHeuristic(TextDirectionHeuristics.FIRSTSTRONG_RTL)
                .build()
        assertThat(staticLayout.textDirectionHeuristic)
                .isEqualTo(TextDirectionHeuristics.FIRSTSTRONG_RTL)
    }

    @Test
    fun buildLayout_SetGetLineSpacingMultiplier() {
        val boringLayout = makeBuilder(BORING_TEXT)
                .setLineSpacingMultiplier(2.0f)
                .build()
        assertThat(boringLayout.lineSpacingMultiplier).isEqualTo(2.0f)
        assertThat(boringLayout.spacingMultiplier).isEqualTo(2.0f)

        val staticLayout = makeBuilder(STATIC_LAYOUT_TEXT)
                .setLineSpacingMultiplier(2.0f)
                .build()
        assertThat(staticLayout.lineSpacingMultiplier).isEqualTo(2.0f)
        assertThat(staticLayout.spacingMultiplier).isEqualTo(2.0f)
    }

    @Test
    fun buildLayout_SetGetLineSpacingAmount() {
        val boringLayout = makeBuilder(BORING_TEXT)
                .setLineSpacingAmount(3.0f)
                .build()
        assertThat(boringLayout.lineSpacingAmount).isEqualTo(3.0f)
        assertThat(boringLayout.spacingAdd).isEqualTo(3.0f)

        val staticLayout = makeBuilder(STATIC_LAYOUT_TEXT)
                .setLineSpacingAmount(3.0f)
                .build()
        assertThat(staticLayout.lineSpacingAmount).isEqualTo(3.0f)
        assertThat(staticLayout.spacingAdd).isEqualTo(3.0f)
    }

    @Test
    fun buildLayout_SetGetIncludePadding() {
        val boringLayout = makeBuilder(BORING_TEXT)
                .setFontPaddingIncluded(true)
                .build()
        assertThat(boringLayout.isFontPaddingIncluded).isTrue()

        val staticLayout = makeBuilder(STATIC_LAYOUT_TEXT)
                .setFontPaddingIncluded(true)
                .build()
        assertThat(staticLayout.isFontPaddingIncluded).isTrue()
    }

    @Test
    fun buildLayout_SetGetFallbackLineSpacing() {
        val boringLayout = makeBuilder(BORING_TEXT)
                .setFallbackLineSpacingEnabled(true)
                .build()
        assertThat(boringLayout.isFallbackLineSpacingEnabled).isTrue()

        val staticLayout = makeBuilder(STATIC_LAYOUT_TEXT)
                .setFallbackLineSpacingEnabled(true)
                .build()
        assertThat(staticLayout.isFallbackLineSpacingEnabled).isTrue()
    }

    @Test
    fun buildLayout_SetGetEllipsizedWidth() {
        val boringLayout = makeBuilder(BORING_TEXT)
                .setEllipsize(TextUtils.TruncateAt.END)
                .setEllipsizedWidth(80)
                .build()
        assertThat(boringLayout.ellipsizedWidth).isEqualTo(80)

        val staticLayout = makeBuilder(STATIC_LAYOUT_TEXT)
                .setEllipsize(TextUtils.TruncateAt.END)
                .setEllipsizedWidth(80)
                .build()
        assertThat(staticLayout.ellipsizedWidth).isEqualTo(80)
    }

    @Test
    fun buildLayout_SetGetEllipsize() {
        val boringLayout = makeBuilder(BORING_TEXT)
                .setEllipsize(TextUtils.TruncateAt.MARQUEE)
                .build()
        assertThat(boringLayout.ellipsize).isSameInstanceAs(TextUtils.TruncateAt.MARQUEE)

        val staticLayout = makeBuilder(STATIC_LAYOUT_TEXT)
                .setEllipsize(TextUtils.TruncateAt.MARQUEE)
                .build()
        assertThat(staticLayout.ellipsize).isSameInstanceAs(TextUtils.TruncateAt.MARQUEE)
    }

    @Test
    fun buildLayout_SetGetMaxLines() {
        val boringLayout = makeBuilder(BORING_TEXT)
                .setMaxLines(3)
                .build()
        assertThat(boringLayout.maxLines).isEqualTo(3)

        val staticLayout = makeBuilder(STATIC_LAYOUT_TEXT)
                .setMaxLines(3)
                .build()
        assertThat(staticLayout.maxLines).isEqualTo(3)
    }

    @Test
    fun buildLayout_SetGetBreakStrategy() {
        val boringLayout = makeBuilder(BORING_TEXT)
                .setBreakStrategy(BREAK_STRATEGY_BALANCED)
                .build()
        assertThat(boringLayout.breakStrategy).isEqualTo(BREAK_STRATEGY_BALANCED)

        val staticLayout = makeBuilder(STATIC_LAYOUT_TEXT)
                .setBreakStrategy(BREAK_STRATEGY_BALANCED)
                .build()
        assertThat(staticLayout.breakStrategy).isEqualTo(BREAK_STRATEGY_BALANCED)
    }

    @Test
    fun buildLayout_SetGetHyphenationFrequency() {
        val boringLayout = makeBuilder(BORING_TEXT)
                .setHyphenationFrequency(HYPHENATION_FREQUENCY_FULL)
                .build()
        assertThat(boringLayout.hyphenationFrequency).isEqualTo(HYPHENATION_FREQUENCY_FULL)

        val staticLayout = makeBuilder(STATIC_LAYOUT_TEXT)
                .setHyphenationFrequency(HYPHENATION_FREQUENCY_FULL)
                .build()
        assertThat(staticLayout.hyphenationFrequency).isEqualTo(HYPHENATION_FREQUENCY_FULL)
    }

    @Test
    fun buildLayout_SetGetLeftIndents() {
        val boringLayout = makeBuilder(BORING_TEXT)
                .setLeftIndents(intArrayOf(1, 2, 3))
                .build()
        assertThat(boringLayout.leftIndents).isEqualTo(intArrayOf(1, 2, 3))

        val staticLayout = makeBuilder(STATIC_LAYOUT_TEXT)
                .setLeftIndents(intArrayOf(1, 2, 3))
                .build()
        assertThat(staticLayout.leftIndents).isEqualTo(intArrayOf(1, 2, 3))
    }

    @Test
    fun buildLayout_SetGetRightIndents() {
        val boringLayout = makeBuilder(BORING_TEXT)
                .setRightIndents(intArrayOf(1, 2, 3))
                .build()
        assertThat(boringLayout.rightIndents).isEqualTo(intArrayOf(1, 2, 3))

        val staticLayout = makeBuilder(STATIC_LAYOUT_TEXT)
                .setRightIndents(intArrayOf(1, 2, 3))
                .build()
        assertThat(staticLayout.rightIndents).isEqualTo(intArrayOf(1, 2, 3))
    }

    @Test
    fun buildLayout_SetGetJustification() {
        val boringLayout = makeBuilder(BORING_TEXT)
                .setJustificationMode(JUSTIFICATION_MODE_INTER_WORD)
                .build()
        assertThat(boringLayout.justificationMode).isEqualTo(JUSTIFICATION_MODE_INTER_WORD)

        val staticLayout = makeBuilder(STATIC_LAYOUT_TEXT)
                .setJustificationMode(JUSTIFICATION_MODE_INTER_WORD)
                .build()
        assertThat(staticLayout.justificationMode).isEqualTo(JUSTIFICATION_MODE_INTER_WORD)
    }

    @Test
    fun buildLayout_SetGetLineBreakConfig() {
        val config = LineBreakConfig.Builder()
                .setLineBreakStyle(LineBreakConfig.LINE_BREAK_STYLE_LOOSE)
                .build()
        val boringLayout = makeBuilder(BORING_TEXT)
                .setLineBreakConfig(config)
                .build()
        assertThat(boringLayout.lineBreakConfig).isSameInstanceAs(config)

        val staticLayout = makeBuilder(STATIC_LAYOUT_TEXT)
                .setLineBreakConfig(config)
                .build()
        assertThat(staticLayout.lineBreakConfig).isSameInstanceAs(config)
    }
}
