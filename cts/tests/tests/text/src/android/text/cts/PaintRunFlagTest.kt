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

package android.text.cts

import android.graphics.Paint
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.text.SpannedString
import android.text.TextUtils
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.text.flags.Flags.FLAG_LETTER_SPACING_JUSTIFICATION
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class PaintRunFlagTest {

    @Rule
    @JvmField
    val mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    val PAINT = Paint().apply {
        textSize = 10f
    }

    private fun assertSame_measureText_Result(text: String, start: Int, end: Int, paint: Paint) {
        val charArray = CharArray(text.length)
        TextUtils.getChars(text, 0, text.length, charArray, 0)

        // Measure the text without any flags with three variation APIs.
        paint.flags = paint.flags and Paint.TEXT_RUN_FLAG_LEFT_EDGE.inv()
        paint.flags = paint.flags and Paint.TEXT_RUN_FLAG_RIGHT_EDGE.inv()
        val measureTextStringNoFlag = paint.measureText(text, start, end)
        val measureTextCharSequenceNoFlag = paint.measureText(SpannedString(text), start, end)
        val measureTextCharArrayNoFlag = paint.measureText(charArray, start, end - start)

        // Measure the text with left edge flags with three variation APIs.
        paint.flags = paint.flags or Paint.TEXT_RUN_FLAG_LEFT_EDGE
        paint.flags = paint.flags and Paint.TEXT_RUN_FLAG_RIGHT_EDGE.inv()
        val measureTextStringLeft = paint.measureText(text, start, end)
        val measureTextCharSequenceLeft = paint.measureText(SpannedString(text), start, end)
        val measureTextCharArrayLeft = paint.measureText(charArray, start, end - start)

        // Measure the text with right edge flags with three variation APIs.
        paint.flags = paint.flags and Paint.TEXT_RUN_FLAG_LEFT_EDGE.inv()
        paint.flags = paint.flags or Paint.TEXT_RUN_FLAG_RIGHT_EDGE
        val measureTextStringRight = paint.measureText(text, start, end)
        val measureTextCharSequenceRight = paint.measureText(SpannedString(text), start, end)
        val measureTextCharArrayRight = paint.measureText(charArray, start, end - start)

        // Measure the text with both left and right edge flags with three variation APIs.
        paint.flags = paint.flags or Paint.TEXT_RUN_FLAG_LEFT_EDGE
        paint.flags = paint.flags or Paint.TEXT_RUN_FLAG_RIGHT_EDGE
        val measureTextStringWholeLine = paint.measureText(text, start, end)
        val measureTextCharSequenceWholeLine = paint.measureText(SpannedString(text), start, end)
        val measureTextCharArrayWholeLine = paint.measureText(charArray, start, end - start)

        assertThat(measureTextStringNoFlag).isEqualTo(measureTextCharSequenceNoFlag)
        assertThat(measureTextStringNoFlag).isEqualTo(measureTextCharArrayNoFlag)

        assertThat(measureTextStringNoFlag).isEqualTo(measureTextStringLeft)
        assertThat(measureTextStringNoFlag).isEqualTo(measureTextCharSequenceLeft)
        assertThat(measureTextStringNoFlag).isEqualTo(measureTextCharArrayLeft)

        assertThat(measureTextStringNoFlag).isEqualTo(measureTextStringRight)
        assertThat(measureTextStringNoFlag).isEqualTo(measureTextCharSequenceRight)
        assertThat(measureTextStringNoFlag).isEqualTo(measureTextCharArrayRight)

        assertThat(measureTextStringNoFlag).isEqualTo(measureTextStringWholeLine)
        assertThat(measureTextStringNoFlag).isEqualTo(measureTextCharSequenceWholeLine)
        assertThat(measureTextStringNoFlag).isEqualTo(measureTextCharArrayWholeLine)
    }

    private fun assertSame_getTextWidths_Result(text: String, start: Int, end: Int, paint: Paint) {
        val charArray = CharArray(text.length)
        val length = end - start
        TextUtils.getChars(text, 0, text.length, charArray, 0)

        // Measure the text without any flags with three variation APIs.
        paint.flags = paint.flags and Paint.TEXT_RUN_FLAG_LEFT_EDGE.inv()
        paint.flags = paint.flags and Paint.TEXT_RUN_FLAG_RIGHT_EDGE.inv()
        val widthsStringNoFlag = FloatArray(end - start)
        assertThat(paint.getTextWidths(text, start, end, widthsStringNoFlag))
                .isEqualTo(length)
        val widthsCharSequenceNoFlag = FloatArray(end - start)
        assertThat(paint.getTextWidths(SpannedString(text), start, end, widthsCharSequenceNoFlag))
                .isEqualTo(length)
        val widthCharArrayNoFlag = FloatArray(end - start)
        assertThat(paint.getTextWidths(charArray, start, end - start, widthCharArrayNoFlag))
                .isEqualTo(length)

        // Measure the text with left edge flags with three variation APIs.
        paint.flags = paint.flags or Paint.TEXT_RUN_FLAG_LEFT_EDGE
        paint.flags = paint.flags and Paint.TEXT_RUN_FLAG_RIGHT_EDGE.inv()
        val widthsStringLeft = FloatArray(end - start)
        assertThat(paint.getTextWidths(text, start, end, widthsStringLeft))
                .isEqualTo(length)
        val widthsCharSequenceLeft = FloatArray(end - start)
        assertThat(paint.getTextWidths(SpannedString(text), start, end, widthsCharSequenceLeft))
                .isEqualTo(length)
        val widthCharArrayLeft = FloatArray(end - start)
        assertThat(paint.getTextWidths(charArray, start, end - start, widthCharArrayLeft))
                .isEqualTo(length)

        // Measure the text with right edge flags with three variation APIs.
        paint.flags = paint.flags and Paint.TEXT_RUN_FLAG_LEFT_EDGE.inv()
        paint.flags = paint.flags or Paint.TEXT_RUN_FLAG_RIGHT_EDGE
        val widthsStringRight = FloatArray(end - start)
        assertThat(paint.getTextWidths(text, start, end, widthsStringRight))
                .isEqualTo(length)
        val widthsCharSequenceRight = FloatArray(end - start)
        assertThat(paint.getTextWidths(SpannedString(text), start, end, widthsCharSequenceRight))
                .isEqualTo(length)
        val widthCharArrayRight = FloatArray(end - start)
        assertThat(paint.getTextWidths(charArray, start, end - start, widthCharArrayRight))
                .isEqualTo(length)

        // Measure the text with both left and right edge flags with three variation APIs.
        paint.flags = paint.flags or Paint.TEXT_RUN_FLAG_LEFT_EDGE
        paint.flags = paint.flags or Paint.TEXT_RUN_FLAG_RIGHT_EDGE
        val widthsStringWholeLine = FloatArray(end - start)
        assertThat(paint.getTextWidths(text, start, end, widthsStringWholeLine))
                .isEqualTo(length)
        val widthsCharSequenceWholeLine = FloatArray(end - start)
        assertThat(paint.getTextWidths(
                SpannedString(text),
                start,
                end,
                widthsCharSequenceWholeLine
        )).isEqualTo(length)
        val widthCharArrayWholeLine = FloatArray(end - start)
        assertThat(paint.getTextWidths(charArray, start, end - start, widthCharArrayWholeLine))
                .isEqualTo(length)

        assertThat(widthsStringNoFlag).isEqualTo(widthsCharSequenceNoFlag)
        assertThat(widthsStringNoFlag).isEqualTo(widthCharArrayNoFlag)

        assertThat(widthsStringNoFlag).isEqualTo(widthsStringLeft)
        assertThat(widthsStringNoFlag).isEqualTo(widthsCharSequenceLeft)
        assertThat(widthsStringNoFlag).isEqualTo(widthCharArrayLeft)

        assertThat(widthsStringNoFlag).isEqualTo(widthsStringRight)
        assertThat(widthsStringNoFlag).isEqualTo(widthsCharSequenceRight)
        assertThat(widthsStringNoFlag).isEqualTo(widthCharArrayRight)

        assertThat(widthsStringNoFlag).isEqualTo(widthsStringWholeLine)
        assertThat(widthsStringNoFlag).isEqualTo(widthsCharSequenceWholeLine)
        assertThat(widthsStringNoFlag).isEqualTo(widthCharArrayWholeLine)
    }

    private fun getRunAdvances(text: String, rtl: Boolean, paint: Paint, out: FloatArray): Float {
        val charArray = CharArray(text.length).apply {
            TextUtils.getChars(text, 0, text.length, this, 0)
        }
        val len = text.length

        // Get result with getRunCharacterAdvance API.
        val width = paint.getRunCharacterAdvance(charArray, 0, len, 0, len, rtl, len, out, 0)

        // Check consistency with other APIs.
        var advances = FloatArray(text.length)
        assertThat(paint.getRunCharacterAdvance(text, 0, len, 0, len, rtl, len, advances, 0))
                .isEqualTo(width)
        assertThat(advances).isEqualTo(out)

        advances = FloatArray(text.length)
        assertThat(paint.getRunCharacterAdvance(SpannedString(text), 0, len, 0, len, rtl, len,
                advances, 0)).isEqualTo(width)
        assertThat(advances).isEqualTo(out)

        advances = FloatArray(text.length)
        paint.getTextRunAdvances(charArray, 0, len, 0, len, rtl, advances, 0)
        assertThat(advances).isEqualTo(out)

        assertThat(paint.getRunAdvance(charArray, 0, len, 0, len, rtl, len)).isEqualTo(width)
        assertThat(paint.getRunAdvance(text, 0, len, 0, len, rtl, len)).isEqualTo(width)
        assertThat(paint.getRunAdvance(SpannedString(text), 0, len, 0, len, rtl, len))
                .isEqualTo(width)
        return width
    }

    @RequiresFlagsEnabled(FLAG_LETTER_SPACING_JUSTIFICATION)
    @Test
    fun measureText_NoMeasureDifferenceWithFlag() {
        val paint = Paint(PAINT).apply {
            letterSpacing = 1f // Set letter spacing = 1em = 10px
        }

        // LTR
        assertSame_measureText_Result("Hello, World.", 0, 13, paint)
        assertSame_measureText_Result("Hello, World.", 0, 5, paint)
        assertSame_measureText_Result("Hello, World.", 5, 13, paint)

        // RTL
        assertSame_measureText_Result("\u05D0\u05D1\u05D2\u05D3", 0, 4, paint)
        assertSame_measureText_Result("\u05D0\u05D1\u05D2\u05D3", 0, 3, paint)
        assertSame_measureText_Result("\u05D0\u05D1\u05D2\u05D3", 1, 4, paint)

        // BiDi
        // The measureText is a legacy API: measurement is performed with paragraph direction LTR
        assertSame_measureText_Result("ab\u05D0\u05D1cd\u05D2\u05D3ef", 0, 10, paint)
        assertSame_measureText_Result("ab\u05D0\u05D1cd\u05D2\u05D3ef", 0, 5, paint)
        assertSame_measureText_Result("ab\u05D0\u05D1cd\u05D2\u05D3ef", 5, 10, paint)
    }

    @RequiresFlagsEnabled(FLAG_LETTER_SPACING_JUSTIFICATION)
    @Test
    fun getTextWidths_NoMeasureDifferenceWithFlag() {
        val paint = Paint(PAINT).apply {
            letterSpacing = 1f // Set letter spacing = 1em = 10px
        }

        // LTR
        assertSame_getTextWidths_Result("Hello, World.", 0, 13, paint)
        assertSame_getTextWidths_Result("Hello, World.", 0, 5, paint)
        assertSame_getTextWidths_Result("Hello, World.", 5, 13, paint)

        // RTL
        assertSame_getTextWidths_Result("\u05D0\u05D1\u05D2\u05D3", 0, 4, paint)
        assertSame_getTextWidths_Result("\u05D0\u05D1\u05D2\u05D3", 0, 3, paint)
        assertSame_getTextWidths_Result("\u05D0\u05D1\u05D2\u05D3", 1, 4, paint)

        // BiDi
        // The measureText is a legacy API: measurement is performed with paragraph direction LTR
        assertSame_getTextWidths_Result("ab\u05D0\u05D1cd\u05D2\u05D3ef", 0, 10, paint)
        assertSame_getTextWidths_Result("ab\u05D0\u05D1cd\u05D2\u05D3ef", 0, 5, paint)
        assertSame_getTextWidths_Result("ab\u05D0\u05D1cd\u05D2\u05D3ef", 5, 10, paint)
    }

    @RequiresFlagsEnabled(FLAG_LETTER_SPACING_JUSTIFICATION)
    @Test
    fun getTextRunAdvances_Latin() {
        val text = "Hello, World."
        val paint = Paint(PAINT).apply {
            letterSpacing = 1f // Set letter spacing = 1em = 10px
        }
        val len = text.length
        val advances = FloatArray(len)
        val letterSpace = paint.textSize
        val letterSpaceHalf = paint.textSize * 0.5f

        // First. prepare the result without letter spacing.
        val noLetterSpacingAdvances = FloatArray(text.length)
        val noLetterSpacingWidth = getRunAdvances(text, false, PAINT, noLetterSpacingAdvances)

        // In case of no flags, no letter space adjustment is applied, thus all advances should have
        // increased amount of letter space.
        paint.flags = paint.flags and Paint.TEXT_RUN_FLAG_LEFT_EDGE.inv()
        paint.flags = paint.flags and Paint.TEXT_RUN_FLAG_RIGHT_EDGE.inv()
        val widthNoFlag = getRunAdvances(text, false, paint, advances)
        for (i in 0 until text.length) {
            assertThat(advances[i]).isEqualTo(noLetterSpacingAdvances[i] + letterSpace)
        }
        assertThat(widthNoFlag).isEqualTo(noLetterSpacingWidth + letterSpace * len)

        // In case of left edge, the left most character should start from drawing origin 0, thus
        // the half of letter space is removed.
        paint.flags = paint.flags or Paint.TEXT_RUN_FLAG_LEFT_EDGE
        paint.flags = paint.flags and Paint.TEXT_RUN_FLAG_RIGHT_EDGE.inv()
        val widthLeftFlag = getRunAdvances(text, false, paint, advances)
        assertThat(advances[0]).isEqualTo(noLetterSpacingAdvances[0] + letterSpaceHalf)
        for (i in 1 until text.length) {
            assertThat(advances[i]).isEqualTo(noLetterSpacingAdvances[i] + letterSpace)
        }
        assertThat(widthLeftFlag)
                .isEqualTo(noLetterSpacingWidth + letterSpace * len - letterSpaceHalf)

        // In case of right edge, the right most character should not have right margin, thus the
        // half of letter space is removed.
        paint.flags = paint.flags and Paint.TEXT_RUN_FLAG_LEFT_EDGE.inv()
        paint.flags = paint.flags or Paint.TEXT_RUN_FLAG_RIGHT_EDGE
        val widthRightFlag = getRunAdvances(text, false, paint, advances)
        for (i in 0 until text.length - 1) {
            assertThat(advances[i]).isEqualTo(noLetterSpacingAdvances[i] + letterSpace)
        }
        assertThat(advances[len - 1]).isEqualTo(noLetterSpacingAdvances[len - 1] + letterSpaceHalf)
        assertThat(widthRightFlag)
                .isEqualTo(noLetterSpacingWidth + letterSpace * len - letterSpaceHalf)

        // In case of both edge (i.e. whole line), the left most character should start from drawing
        // origin 0 and the right most character should not have right margin, thus the half of
        // letter space is removed.
        paint.flags = paint.flags or Paint.TEXT_RUN_FLAG_LEFT_EDGE
        paint.flags = paint.flags or Paint.TEXT_RUN_FLAG_RIGHT_EDGE
        val widthLine = getRunAdvances(text, false, paint, advances)
        assertThat(advances[0]).isEqualTo(noLetterSpacingAdvances[0] + letterSpaceHalf)
        for (i in 1 until text.length - 1) {
            assertThat(advances[i]).isEqualTo(noLetterSpacingAdvances[i] + letterSpace)
        }
        assertThat(advances[len - 1]).isEqualTo(noLetterSpacingAdvances[len - 1] + letterSpaceHalf)
        assertThat(widthLine).isEqualTo(noLetterSpacingWidth + letterSpace * len - letterSpace)
    }

    @RequiresFlagsEnabled(FLAG_LETTER_SPACING_JUSTIFICATION)
    @Test
    fun getTextRunAdvances_Hebrew() {
        val text = "\u05D0\u05D1\u05D2\u05D3\u05D4\u05D5\u05D6\u05D7"
        val paint = Paint(PAINT).apply {
            letterSpacing = 1f // Set letter spacing = 1em = 10px
        }
        val len = text.length
        val advances = FloatArray(len)
        val letterSpace = paint.textSize
        val letterSpaceHalf = paint.textSize * 0.5f

        // First. prepare the result without letter spacing.
        val noLetterSpacingAdvances = FloatArray(text.length)
        val noLetterSpacingWidth = getRunAdvances(text, true, PAINT, noLetterSpacingAdvances)

        // In case of no flags, no letter space adjustment is applied, thus all advances should have
        // increased amount of letter space.
        paint.flags = paint.flags and Paint.TEXT_RUN_FLAG_LEFT_EDGE.inv()
        paint.flags = paint.flags and Paint.TEXT_RUN_FLAG_RIGHT_EDGE.inv()
        val widthNoFlag = getRunAdvances(text, true, paint, advances)
        for (i in 0 until text.length) {
            assertThat(advances[i]).isEqualTo(noLetterSpacingAdvances[i] + letterSpace)
        }
        assertThat(widthNoFlag).isEqualTo(noLetterSpacingWidth + letterSpace * len)

        // In case of left edge, the left most character should start from drawing origin 0, thus
        // the half of letter space is removed. Note that this test case is drawing RTL text, so
        // the last character is located at the most left position.
        paint.flags = paint.flags or Paint.TEXT_RUN_FLAG_LEFT_EDGE
        paint.flags = paint.flags and Paint.TEXT_RUN_FLAG_RIGHT_EDGE.inv()
        val widthLeftFlag = getRunAdvances(text, true, paint, advances)
        for (i in 0 until len - 1) {
            assertThat(advances[i]).isEqualTo(noLetterSpacingAdvances[i] + letterSpace)
        }
        assertThat(advances[len - 1]).isEqualTo(noLetterSpacingAdvances[len - 1] + letterSpaceHalf)
        assertThat(widthLeftFlag)
                .isEqualTo(noLetterSpacingWidth + letterSpace * len - letterSpaceHalf)

        // In case of right edge, the right most character should not have right margin, thus the
        // half of letter space is removed. Note that this test case is drawing RTL text, so
        // the first character is located at the most right position.
        paint.flags = paint.flags and Paint.TEXT_RUN_FLAG_LEFT_EDGE.inv()
        paint.flags = paint.flags or Paint.TEXT_RUN_FLAG_RIGHT_EDGE
        val widthRightFlag = getRunAdvances(text, true, paint, advances)
        assertThat(advances[0]).isEqualTo(noLetterSpacingAdvances[0] + letterSpaceHalf)
        for (i in 1 until len) {
            assertThat(advances[i]).isEqualTo(noLetterSpacingAdvances[i] + letterSpace)
        }
        assertThat(widthRightFlag)
                .isEqualTo(noLetterSpacingWidth + letterSpace * len - letterSpaceHalf)

        // In case of both edge (i.e. whole line), the left most character should start from drawing
        // origin 0 and the right most character should not have right margin, thus the half of
        // letter space is removed.
        paint.flags = paint.flags or Paint.TEXT_RUN_FLAG_LEFT_EDGE
        paint.flags = paint.flags or Paint.TEXT_RUN_FLAG_RIGHT_EDGE
        val widthLine = getRunAdvances(text, true, paint, advances)
        assertThat(advances[0]).isEqualTo(noLetterSpacingAdvances[0] + letterSpaceHalf)
        for (i in 1 until text.length - 1) {
            assertThat(advances[i]).isEqualTo(noLetterSpacingAdvances[i] + letterSpace)
        }
        assertThat(advances[len - 1]).isEqualTo(noLetterSpacingAdvances[len - 1] + letterSpaceHalf)
        assertThat(widthLine).isEqualTo(noLetterSpacingWidth + letterSpace * len - letterSpace)
    }
}
