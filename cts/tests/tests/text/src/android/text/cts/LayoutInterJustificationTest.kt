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

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.text.Layout
import android.text.TextPaint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.text.flags.Flags.FLAG_LETTER_SPACING_JUSTIFICATION
import com.google.common.truth.Truth.assertThat
import kotlin.math.ceil
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.any
import org.mockito.Mockito.anyBoolean
import org.mockito.Mockito.anyFloat
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations

@SmallTest
@RunWith(AndroidJUnit4::class)
@RequiresFlagsEnabled(FLAG_LETTER_SPACING_JUSTIFICATION)
class LayoutInterJustificationTest {

    @Rule
    @JvmField
    val mCheckFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    private lateinit var mPaint: TextPaint

    @Mock
    private lateinit var mockCanvas: Canvas

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)

        mPaint = TextPaint()
        mPaint.textSize = 10f // Make 1em = 10px
    }

    @RequiresFlagsEnabled(FLAG_LETTER_SPACING_JUSTIFICATION)
    @Test
    fun defaultJustificationMode() {
        val text = "Hello, World."
        val paint = mPaint
        val width = 100
        val layout = Layout.Builder(text, 0, text.length, paint, width)
                .build()

        assertThat(layout.justificationMode).isEqualTo(Layout.JUSTIFICATION_MODE_NONE)
    }

    @RequiresFlagsEnabled(FLAG_LETTER_SPACING_JUSTIFICATION)
    @Test
    fun setAndGetJustificationMode() {
        val text = "Hello, World."
        val paint = mPaint
        val width = 100
        var layout = Layout.Builder(text, 0, text.length, paint, width)
                .setJustificationMode(Layout.JUSTIFICATION_MODE_INTER_CHARACTER)
                .build()

        assertThat(layout.justificationMode).isEqualTo(Layout.JUSTIFICATION_MODE_INTER_CHARACTER)

        layout = Layout.Builder(text, 0, text.length, paint, width)
                .setJustificationMode(Layout.JUSTIFICATION_MODE_INTER_WORD)
                .build()

        assertThat(layout.justificationMode).isEqualTo(Layout.JUSTIFICATION_MODE_INTER_WORD)
    }

    @RequiresFlagsEnabled(FLAG_LETTER_SPACING_JUSTIFICATION)
    @Test
    fun letterSpacingAmountTest() {
        val text = "Hello, World."
        val paint = mPaint
        val threeLineText = "$text $text $text"
        val width = Layout.getDesiredWidth(text, paint)
        val layoutWidth = ceil(width * 1.25).toInt()

        // Preparation
        var layout = Layout.Builder(
            threeLineText,
            0,
            threeLineText.length,
            paint,
                layoutWidth
        )
                .setJustificationMode(Layout.JUSTIFICATION_MODE_INTER_CHARACTER)
                .build()
        assertThat(layout.getLineStart(1)).isEqualTo(14)
        assertThat(layout.getLineEnd(1)).isEqualTo(28)

        `when`(mockCanvas.getClipBounds(any()))
                .thenAnswer { input ->
                    val rect = input.getArgument<Rect>(0)
                    rect.left = 0
                    rect.top = 0
                    rect.right = layoutWidth
                    rect.bottom = layoutWidth
                    true
                }

        val actualLetterSpacings = mutableListOf<Float>()

        `when`(mockCanvas.drawTextRun(
                any<CharSequence>(),
                anyInt(),
                anyInt(),
                anyInt(),
                anyInt(),
                anyFloat(),
                anyFloat(),
                anyBoolean(),
                any())).thenAnswer{ input ->
            val paint = input.getArgument<Paint>(8)
            actualLetterSpacings.add(paint.letterSpacing * paint.textSize) // Convert to px
            true
        }

        // Do: draw a text
        layout.draw(mockCanvas)

        // Assertions
       assertThat(layout.lineCount).isEqualTo(3)

        val clusterCountLine1 = layout.getLineLetterSpacingUnitCount(0, false)
        val expectedLetterSpacingLine1 = (layoutWidth - width) / (clusterCountLine1 - 1)
        assertThat(actualLetterSpacings[0]).isEqualTo(expectedLetterSpacingLine1)

        val clusterCountLine2 = layout.getLineLetterSpacingUnitCount(1, false)
        val expectedLetterSpacingLine2 = (layoutWidth - width) / (clusterCountLine2 - 1)
        assertThat(actualLetterSpacings[1]).isEqualTo(expectedLetterSpacingLine2)

        // No justification at the end of the line.
    }
}
