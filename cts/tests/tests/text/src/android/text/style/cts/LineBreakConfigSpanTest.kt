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

package android.text.style.cts

import android.graphics.Paint
import android.graphics.text.LineBreakConfig
import android.graphics.text.LineBreakConfig.HYPHENATION_DISABLED
import android.graphics.text.LineBreakConfig.HYPHENATION_UNSPECIFIED
import android.graphics.text.LineBreakConfig.LINE_BREAK_STYLE_NO_BREAK
import android.graphics.text.LineBreakConfig.LINE_BREAK_STYLE_STRICT
import android.graphics.text.LineBreakConfig.LINE_BREAK_STYLE_UNSPECIFIED
import android.graphics.text.LineBreakConfig.LINE_BREAK_WORD_STYLE_UNSPECIFIED
import android.graphics.text.MeasuredText
import android.os.Parcel
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.text.MeasuredParagraph
import android.text.SpannableString
import android.text.Spanned
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.TextUtils
import android.text.cts.R
import android.text.style.LineBreakConfigSpan
import android.text.style.LocaleSpan
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.text.flags.Flags
import com.google.common.truth.Truth.assertThat
import java.util.Locale
import junit.framework.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_STRING = "hello, world."

@SmallTest
@RunWith(AndroidJUnit4::class)
class LineBreakConfigSpanTest {

    @JvmField
    @Rule
    val mCheckFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    private data class Run(val length: Int, val lineBreakConfig: LineBreakConfig?, val paint: Paint)

    private val paint = TextPaint().apply {
        textSize = 10f
        textLocale = Locale.US
    }

    private fun buildMeasuredText(
        paint: TextPaint,
        lineBreakConfig: LineBreakConfig?,
        text: CharSequence
    ): List<Run> {
        val res = mutableListOf<Run>()
        MeasuredParagraph.buildForStaticLayoutTest(
                paint, lineBreakConfig, text, 0, text.length, TextDirectionHeuristics.LTR,
                MeasuredText.Builder.HYPHENATION_MODE_NONE,
                false, // computeLayout
                object : MeasuredParagraph.StyleRunCallback {
            override fun onAppendStyleRun(
                    paint: Paint,
                    lineBreakConfig: LineBreakConfig?,
                    length: Int,
                    isRtl: Boolean
            ) {
                res.add(Run(length, lineBreakConfig, Paint(paint)))
            }

            override fun onAppendReplacementRun(paint: Paint, length: Int, width: Float) {
                // Ignore
            }
        })
        return res
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_NO_BREAK_NO_HYPHENATION_SPAN)
    fun testLineBreakConfigSpan() {
        val config = LineBreakConfig.Builder()
                .setLineBreakStyle(LineBreakConfig.LINE_BREAK_STYLE_LOOSE)
                .build()
        val text = SpannableString(TEST_STRING).apply {
            setSpan(
                LineBreakConfigSpan(config),
                0,
                TEST_STRING.length,
                Spanned.SPAN_INCLUSIVE_EXCLUSIVE
            )
        }

        val runs = buildMeasuredText(paint, null, text)
        assertEquals(runs.size, 1)
        assertEquals(TEST_STRING.length, runs[0].length)
        assertEquals(config, runs[0].lineBreakConfig)
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_NO_BREAK_NO_HYPHENATION_SPAN)
    fun testLineBreakConfigSpan_getSpanTypeId() {
        val config = LineBreakConfig.Builder()
                .setLineBreakStyle(LineBreakConfig.LINE_BREAK_STYLE_LOOSE)
                .build()
        val span = LineBreakConfigSpan(config)
        // The span type id is an opaque hidden ID, so only verifies that there should not crash by
        // calling function.
        span.getSpanTypeId()
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_NO_BREAK_NO_HYPHENATION_SPAN)
    fun testLineBreakConfigSpan_override() {
        // Span overview of this test case
        // |------------------------------------------------|: Test String
        //
        // Spans
        // |------------------------------------------------|: LineBreakSpan(config)
        // |------------------------------------------------|: LineBreakSpan(overrideConfig)
        //
        // Result
        // |------------------------------------------------|:
        //              runs[0] = overridden
        val config = LineBreakConfig.Builder()
                .setLineBreakStyle(LineBreakConfig.LINE_BREAK_STYLE_LOOSE)
                .setLineBreakWordStyle(LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE)
                .build()
        val overrideConfig = LineBreakConfig.Builder()
                .setLineBreakStyle(LineBreakConfig.LINE_BREAK_STYLE_NORMAL)
                .build()
        val text = SpannableString(TEST_STRING).apply {
            setSpan(
                LineBreakConfigSpan(config),
                0,
                TEST_STRING.length,
                    Spanned.SPAN_INCLUSIVE_EXCLUSIVE
            )
            setSpan(
                LineBreakConfigSpan(overrideConfig),
                0,
                TEST_STRING.length,
                    Spanned.SPAN_INCLUSIVE_EXCLUSIVE
            )
        }

        val overridden = LineBreakConfig.Builder()
                .setLineBreakStyle(LineBreakConfig.LINE_BREAK_STYLE_NORMAL)
                .setLineBreakWordStyle(LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE)
                .build()

        val runs = buildMeasuredText(paint, null, text)
        assertEquals(runs.size, 1)
        assertEquals(TEST_STRING.length, runs[0].length)
        assertEquals(overridden, runs[0].lineBreakConfig)
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_NO_BREAK_NO_HYPHENATION_SPAN)
    fun testLineBreakConfigSpan_partialOverride() {
        // |------------------------------------------------|: Test String
        //
        // Spans
        // |------------------------------------------------|: LineBreakSpan(config)
        // |-------------------------|                       : LineBreakSpan(overrideConfig)
        //
        // Result
        // |-------------------------|----------------------|:
        //  runs[0] = overridden      run[1] = config
        val middleTextPos = 5
        val config = LineBreakConfig.Builder()
                .setLineBreakStyle(LineBreakConfig.LINE_BREAK_STYLE_LOOSE)
                .setLineBreakWordStyle(LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE)
                .build()
        val overrideConfig = LineBreakConfig.Builder()
                .setLineBreakStyle(LineBreakConfig.LINE_BREAK_STYLE_NORMAL)
                .build()
        val text = SpannableString(TEST_STRING).apply {
            setSpan(
                LineBreakConfigSpan(config),
                0,
                TEST_STRING.length,
                    Spanned.SPAN_INCLUSIVE_EXCLUSIVE
            )
            setSpan(
                LineBreakConfigSpan(overrideConfig),
                0,
                middleTextPos,
                    Spanned.SPAN_INCLUSIVE_EXCLUSIVE
            )
        }

        val overridden = LineBreakConfig.Builder()
                .setLineBreakStyle(LineBreakConfig.LINE_BREAK_STYLE_NORMAL)
                .setLineBreakWordStyle(LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE)
                .build()

        val runs = buildMeasuredText(paint, null, text)
        assertEquals(runs.size, 2)
        assertEquals(middleTextPos, runs[0].length)
        assertEquals(overridden, runs[0].lineBreakConfig)
        assertEquals(TEST_STRING.length - middleTextPos, runs[1].length)
        assertEquals(config, runs[1].lineBreakConfig)
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_NO_BREAK_NO_HYPHENATION_SPAN)
    fun testLineBreakConfigSpan_partialOverride2() {
        // |--------------------------------------------------------|: Test String
        //
        // Spans
        // |--------------------------------------------------------|: LineBreakSpan(config)
        //                   |--------------------|                  : LineBreakSpan(overrideConfig)
        //
        // Result
        // |-----------------|--------------------|-----------------|:
        //  runs[0] = config  run[1] = overridden  run[2] = config
        val textPos1 = 4
        val textPos2 = 7
        val config = LineBreakConfig.Builder()
                .setLineBreakStyle(LineBreakConfig.LINE_BREAK_STYLE_LOOSE)
                .setLineBreakWordStyle(LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE)
                .build()
        val overrideConfig = LineBreakConfig.Builder()
                .setLineBreakStyle(LineBreakConfig.LINE_BREAK_STYLE_NORMAL)
                .build()
        val text = SpannableString(TEST_STRING).apply {
            setSpan(
                LineBreakConfigSpan(config),
                0,
                TEST_STRING.length,
                    Spanned.SPAN_INCLUSIVE_EXCLUSIVE
            )
            setSpan(
                LineBreakConfigSpan(overrideConfig),
                textPos1,
                textPos2,
                    Spanned.SPAN_INCLUSIVE_EXCLUSIVE
            )
        }

        val overridden = LineBreakConfig.Builder()
                .setLineBreakStyle(LineBreakConfig.LINE_BREAK_STYLE_NORMAL)
                .setLineBreakWordStyle(LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE)
                .build()

        val runs = buildMeasuredText(paint, null, text)
        assertEquals(runs.size, 3)
        assertEquals(textPos1, runs[0].length)
        assertEquals(config, runs[0].lineBreakConfig)
        assertEquals(textPos2 - textPos1, runs[1].length)
        assertEquals(overridden, runs[1].lineBreakConfig)
        assertEquals(TEST_STRING.length - textPos2, runs[2].length)
        assertEquals(config, runs[2].lineBreakConfig)
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_NO_BREAK_NO_HYPHENATION_SPAN)
    fun testLineBreakConfigSpan_partialOverride3() {
        // |--------------------------------------------------------|: Test String
        //
        // Spans
        // |--------------------------------------|                  : LineBreakSpan(config)
        //                   |--------------------------------------|: LineBreakSpan(overrideConfig)
        //
        // Result
        // |-----------------|--------------------|-----------------|:
        //  runs[0] = config  run[1] = overridden  run[2] = overrideConfig
        val textPos1 = 4
        val textPos2 = 7
        val config = LineBreakConfig.Builder()
                .setLineBreakStyle(LineBreakConfig.LINE_BREAK_STYLE_LOOSE)
                .setLineBreakWordStyle(LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE)
                .build()
        val overrideConfig = LineBreakConfig.Builder()
                .setLineBreakStyle(LineBreakConfig.LINE_BREAK_STYLE_NORMAL)
                .build()
        val text = SpannableString(TEST_STRING).apply {
            setSpan(
                LineBreakConfigSpan(config),
                0,
                textPos2,
                    Spanned.SPAN_INCLUSIVE_EXCLUSIVE
            )
            setSpan(
                LineBreakConfigSpan(overrideConfig),
                textPos1,
                TEST_STRING.length,
                    Spanned.SPAN_INCLUSIVE_EXCLUSIVE
            )
        }

        val overridden = LineBreakConfig.Builder()
                .setLineBreakStyle(LineBreakConfig.LINE_BREAK_STYLE_NORMAL)
                .setLineBreakWordStyle(LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE)
                .build()

        val runs = buildMeasuredText(paint, null, text)
        assertEquals(runs.size, 3)
        assertEquals(textPos1, runs[0].length)
        assertEquals(config, runs[0].lineBreakConfig)

        assertEquals(textPos2 - textPos1, runs[1].length)
        assertEquals(overridden, runs[1].lineBreakConfig)

        assertEquals(TEST_STRING.length - textPos2, runs[2].length)
        assertEquals(overrideConfig, runs[2].lineBreakConfig)
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_NO_BREAK_NO_HYPHENATION_SPAN)
    fun testLineBreakConfigSpan_MetricAffectingSpanMixture() {
        // |--------------------------------------------------------|: Test String
        //
        // Spans
        // |---------------------------------------------------------|: LineBreakSpan(config)
        //                   |--------------------|                   : LocaleSpan("ja-JP")
        //
        // Result
        // |-----------------|--------------------|------------------|:
        //  runs[0] = config  run[1] = config      run[2] = config
        //  runs[0] = en-US   run[1] = ja-JP       run[2] = en-US

        val textPos1 = 4
        val textPos2 = 7
        val config = LineBreakConfig.Builder()
                .setLineBreakStyle(LineBreakConfig.LINE_BREAK_STYLE_LOOSE)
                .setLineBreakWordStyle(LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE)
                .build()
        val text = SpannableString(TEST_STRING).apply {
            setSpan(
                LineBreakConfigSpan(config),
                0,
                TEST_STRING.length,
                    Spanned.SPAN_INCLUSIVE_EXCLUSIVE
            )
            setSpan(
                LocaleSpan(Locale.JAPAN),
                textPos1,
                textPos2,
                    Spanned.SPAN_INCLUSIVE_EXCLUSIVE
            )
        }

        val runs = buildMeasuredText(paint, null, text)
        assertEquals(runs.size, 3)
        assertEquals(textPos1, runs[0].length)
        assertEquals(config, runs[0].lineBreakConfig)
        assertEquals(Locale.US, runs[0].paint.textLocale)

        assertEquals(textPos2 - textPos1, runs[1].length)
        assertEquals(config, runs[1].lineBreakConfig)
        assertEquals(Locale.JAPAN, runs[1].paint.textLocale)

        assertEquals(TEST_STRING.length - textPos2, runs[2].length)
        assertEquals(config, runs[2].lineBreakConfig)
        assertEquals(Locale.US, runs[2].paint.textLocale)
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_NO_BREAK_NO_HYPHENATION_SPAN)
    fun testLineBreakConfigSpan_MetricAffectingSpanMixture2() {
        // |--------------------------------------------------------|: Test String
        //
        // Spans
        // |--------------------------------------|                   : LineBreakSpan(config)
        //                   |---------------------------------------|: LocaleSpan("ja-JP")
        //
        // Result
        // |-----------------|--------------------|------------------|:
        //  runs[0] = config  run[1] = config      run[2] = none
        //  runs[0] = en-US   run[1] = ja-JP       run[2] = ja-JP

        val textPos1 = 4
        val textPos2 = 7
        val config = LineBreakConfig.Builder()
                .setLineBreakStyle(LineBreakConfig.LINE_BREAK_STYLE_LOOSE)
                .setLineBreakWordStyle(LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE)
                .build()
        val text = SpannableString(TEST_STRING).apply {
            setSpan(
                LineBreakConfigSpan(config),
                0,
                textPos2,
                    Spanned.SPAN_INCLUSIVE_EXCLUSIVE
            )
            setSpan(
                LocaleSpan(Locale.JAPAN),
                textPos1,
                TEST_STRING.length,
                    Spanned.SPAN_INCLUSIVE_EXCLUSIVE
            )
        }

        val runs = buildMeasuredText(paint, null, text)
        assertEquals(runs.size, 3)
        assertEquals(textPos1, runs[0].length)
        assertEquals(config, runs[0].lineBreakConfig)
        assertEquals(Locale.US, runs[0].paint.textLocale)

        assertEquals(textPos2 - textPos1, runs[1].length)
        assertEquals(config, runs[1].lineBreakConfig)
        assertEquals(Locale.JAPAN, runs[1].paint.textLocale)

        assertEquals(TEST_STRING.length - textPos2, runs[2].length)
        assertEquals(LineBreakConfig.Builder().build(), runs[2].lineBreakConfig)
        assertEquals(Locale.JAPAN, runs[2].paint.textLocale)
    }

    private fun assertNoBreakSpan(span: LineBreakConfigSpan) {
        val config = span.lineBreakConfig
        assertThat(config).isNotNull()
        assertThat(config.lineBreakStyle).isEqualTo(LINE_BREAK_STYLE_NO_BREAK)
        assertThat(config.lineBreakWordStyle).isEqualTo(LINE_BREAK_WORD_STYLE_UNSPECIFIED)
        assertThat(config.hyphenation).isEqualTo(HYPHENATION_UNSPECIFIED)
    }

    private fun assertNoHyphenationSpan(span: LineBreakConfigSpan) {
        val config = span.lineBreakConfig
        assertThat(config).isNotNull()
        assertThat(config.lineBreakStyle).isEqualTo(LINE_BREAK_STYLE_UNSPECIFIED)
        assertThat(config.lineBreakWordStyle).isEqualTo(LINE_BREAK_WORD_STYLE_UNSPECIFIED)
        assertThat(config.hyphenation).isEqualTo(HYPHENATION_DISABLED)
    }

    private fun parcelUnparcelText(text: CharSequence): CharSequence {
        val inParcel = Parcel.obtain()
        val outParcel = Parcel.obtain()
        try {
            TextUtils.writeToParcel(text, inParcel, 0)
            val marshalled = inParcel.marshall()
            outParcel.unmarshall(marshalled, 0, marshalled.size)
            outParcel.setDataPosition(0)
            return TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(outParcel)
        } finally {
            outParcel.recycle()
            inParcel.recycle()
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_NO_BREAK_NO_HYPHENATION_SPAN)
    fun testLineBreakConfigSpan_parcelable() {
        val strictConfig = LineBreakConfig.Builder()
                .setLineBreakStyle(LINE_BREAK_STYLE_STRICT)
                .build()
        val text = SpannableString("abcde").apply {
            setSpan(LineBreakConfigSpan.createNoBreakSpan(), 1, 2, Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
            setSpan(
                LineBreakConfigSpan.createNoHyphenationSpan(),
                2,
                3,
                    Spanned.SPAN_INCLUSIVE_EXCLUSIVE
            )
            setSpan(LineBreakConfigSpan(strictConfig), 3, 4, Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
        }

        val parceledText = parcelUnparcelText(text)
        require(parceledText is Spanned)
        var spans = requireNotNull(parceledText.getSpans(1, 2, LineBreakConfigSpan::class.java))
        assertThat(spans.size).isEqualTo(1)
        var span = spans[0]
        assertNoBreakSpan(span)

        spans = requireNotNull(parceledText.getSpans(2, 3, LineBreakConfigSpan::class.java))
        assertThat(spans.size).isEqualTo(1)
        span = spans[0]
        assertNoHyphenationSpan(span)

        spans = requireNotNull(parceledText.getSpans(3, 4, LineBreakConfigSpan::class.java))
        assertThat(spans.size).isEqualTo(1)
        span = spans[0]
        val config = span.lineBreakConfig
        assertThat(config).isNotNull()
        assertThat(config.lineBreakStyle).isEqualTo(LINE_BREAK_STYLE_STRICT)
        assertThat(config.lineBreakWordStyle).isEqualTo(LINE_BREAK_WORD_STYLE_UNSPECIFIED)
        assertThat(config.hyphenation).isEqualTo(HYPHENATION_UNSPECIFIED)
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_NO_BREAK_NO_HYPHENATION_SPAN)
    fun testLineBreakConfigSpan_resource() {
        val context = InstrumentationRegistry.getInstrumentation().getTargetContext()

        val text = context.resources.getText(R.string.linebreakconfig_span_test)
        require(text is Spanned)
        var spans = requireNotNull(text.getSpans(1, 2, LineBreakConfigSpan::class.java))
        assertThat(spans.size).isEqualTo(1)
        var span = spans[0]
        assertNoBreakSpan(span)

        spans = requireNotNull(text.getSpans(2, 3, LineBreakConfigSpan::class.java))
        assertThat(spans.size).isEqualTo(1)
        span = spans[0]
        assertNoHyphenationSpan(span)

        spans = requireNotNull(text.getSpans(3, 4, LineBreakConfigSpan::class.java))
        assertThat(spans.size).isEqualTo(1)
        span = spans[0]
        val config = span.lineBreakConfig
        assertThat(config).isNotNull()
        assertThat(config.lineBreakStyle).isEqualTo(LINE_BREAK_STYLE_STRICT)
        assertThat(config.lineBreakWordStyle).isEqualTo(LINE_BREAK_WORD_STYLE_UNSPECIFIED)
        assertThat(config.hyphenation).isEqualTo(HYPHENATION_UNSPECIFIED)
    }
}
