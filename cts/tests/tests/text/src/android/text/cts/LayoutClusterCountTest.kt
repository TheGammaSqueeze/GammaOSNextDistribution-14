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

import android.graphics.Color
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.style.BackgroundColorSpan
import android.text.style.LocaleSpan
import android.text.style.RelativeSizeSpan
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.text.flags.Flags.FLAG_LETTER_SPACING_JUSTIFICATION
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
@RequiresFlagsEnabled(FLAG_LETTER_SPACING_JUSTIFICATION)
class LayoutClusterCountTest {

    @Rule
    @JvmField
    val mCheckFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    private lateinit var mPaint: TextPaint
    @Before
    fun setUp() {
        mPaint = TextPaint()
        mPaint.textSize = 100f
    }

    fun getLayout(
            text: CharSequence,
            paint: TextPaint = mPaint,
            width: Int = Layout.getDesiredWidth(text, paint).toInt() + 1,
            rtl: Boolean = false
    ): Layout {
        val dir = if (rtl) {
            TextDirectionHeuristics.RTL
        } else {
            TextDirectionHeuristics.LTR
        }
        return Layout.Builder(text, 0, text.length, paint, width)
                .setTextDirectionHeuristic(dir)
                .build()
    }

    private fun assertLetterSpacingUnit(
            text: String,
            expectedUnitCount: Int,
            expectedUnitCountWithTrailingSpacing: Int,
            paint: TextPaint = mPaint,
            rtl: Boolean = false,
            ) {
        val width = Layout.getDesiredWidth(text, paint).toInt() + 1
        assertEquals(
                expectedUnitCount,
                getLayout(text, paint, width, rtl).getLineLetterSpacingUnitCount(0, false)
        )
        assertEquals(
                expectedUnitCountWithTrailingSpacing,
                getLayout(text, paint, width, rtl).getLineLetterSpacingUnitCount(0, true)
        )
    }

    @RequiresFlagsEnabled(FLAG_LETTER_SPACING_JUSTIFICATION)
    @Test
    fun testClusterCount() {
        assertLetterSpacingUnit("a", 1, 1)
        assertLetterSpacingUnit("ab", 2, 2)
        assertLetterSpacingUnit("ab cd", 5, 5)
        assertLetterSpacingUnit("ab cd ", 5, 6)
        assertLetterSpacingUnit("ab cd    ", 5, 9)
    }

    @RequiresFlagsEnabled(FLAG_LETTER_SPACING_JUSTIFICATION)
    @Test
    fun testClusterCount_Ligature() {
        assertLetterSpacingUnit("fi", 1, 1)

        // By disabling ligature, the cluster gets 2.
        val noLigaturePaint = TextPaint(mPaint).apply {
            fontFeatureSettings = "'liga' off"
        }
        assertLetterSpacingUnit("fi", 2, 2, noLigaturePaint)
    }

    @RequiresFlagsEnabled(FLAG_LETTER_SPACING_JUSTIFICATION)
    @Test
    fun testClusterCount_ComposingCharacter() {
        // U+0300 is a composing accent.
        assertLetterSpacingUnit("a\u0300", 1, 1)
    }

    @RequiresFlagsEnabled(FLAG_LETTER_SPACING_JUSTIFICATION)
    @Test
    fun testClusterCount_Emoji() {
        // \u261D\uD83C\uDFFB is U+261D U+1F3FB which is supported sequence. Should have 1 cluster.
        assertLetterSpacingUnit("\u261D\uD83C\uDFFB", 1, 1)

        // Regular letters can not be combined with skin tone selectors. Should have two clusters.
        assertLetterSpacingUnit("a\uD83C\uDFFB", 2, 2)

        // Variation selectors should be enclosed to the previous character.
        assertLetterSpacingUnit("\u0023\uFE0E", 1, 1)

        // Flag sequence.
        assertLetterSpacingUnit("\uD83C\uDDE6\uD83C\uDDE8", 1, 1)
    }

    @RequiresFlagsEnabled(FLAG_LETTER_SPACING_JUSTIFICATION)
    @Test
    fun testClusterCount_Multiline() {
        val sampleText = "Hello, World. This is Android."
        val desired = Layout.getDesiredWidth(sampleText, mPaint)
        val layout = getLayout(sampleText, mPaint, (desired / 3).toInt())

        // There is no ligatures, so the  char count is the cluster count.
        for (i in 0 until layout.lineCount) {
            assertEquals(
                (layout.getLineVisibleEnd(i) - layout.getLineStart(i)),
                    layout.getLineLetterSpacingUnitCount(i, false)
            )
            assertEquals(
                (layout.getLineEnd(i) - layout.getLineStart(i)),
                    layout.getLineLetterSpacingUnitCount(i, true)
            )
        }
    }

    @RequiresFlagsEnabled(FLAG_LETTER_SPACING_JUSTIFICATION)
    @Test
    fun testClusterCount_Bidi() {
        val rtl = "\u05D0\u05D1\u05D2"
        val ltr = "abc"

        assertLetterSpacingUnit(rtl, 3, 3, rtl = false)
        assertLetterSpacingUnit(rtl, 3, 3, rtl = true)

        assertLetterSpacingUnit(rtl + ltr, 6, 6, rtl = false)
        assertLetterSpacingUnit(rtl + ltr, 6, 6, rtl = true)

        assertLetterSpacingUnit(ltr + rtl, 6, 6, rtl = false)
        assertLetterSpacingUnit(ltr + rtl, 6, 6, rtl = true)

        assertLetterSpacingUnit(ltr + rtl + ltr, 9, 9, rtl = false)
        assertLetterSpacingUnit(ltr + rtl + ltr, 9, 9, rtl = true)

        assertLetterSpacingUnit(rtl + ltr + rtl, 9, 9, rtl = false)
        assertLetterSpacingUnit(rtl + ltr + rtl, 9, 9, rtl = true)
    }

    @RequiresFlagsEnabled(FLAG_LETTER_SPACING_JUSTIFICATION)
    @Test
    fun testClusterCount_MultiStyle() {
        val ss = SpannableString("Hello, World.")
        val expectedClusters = getLayout(ss).getLineLetterSpacingUnitCount(0, false)

        ss.setSpan(BackgroundColorSpan(Color.BLUE), 1, 3, Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
        assertEquals(expectedClusters, getLayout(ss).getLineLetterSpacingUnitCount(0, false))

        ss.setSpan(LocaleSpan(Locale.JAPANESE), 4, 7, Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
        assertEquals(expectedClusters, getLayout(ss).getLineLetterSpacingUnitCount(0, false))

        ss.setSpan(RelativeSizeSpan(1.5f), 2, 10, Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
        assertEquals(expectedClusters, getLayout(ss).getLineLetterSpacingUnitCount(0, false))
    }
}
