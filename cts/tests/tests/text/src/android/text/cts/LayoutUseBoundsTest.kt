/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.text.LineBreaker
import android.platform.test.annotations.RequiresFlagsEnabled
import android.text.DynamicLayout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.cts.LayoutUseBoundsUtil.getDrawingHorizontalOffset
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.text.flags.Flags
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@SmallTest
@RunWith(Parameterized::class)
@RequiresFlagsEnabled(Flags.FLAG_USE_BOUNDS_FOR_WIDTH)
class LayoutUseBoundsTest(val p: Param) {

    // In this test case, the SIMPLE and HIGH_QUALITY line breaker produces the same line break
    // output.
    data class Param(val isOptimal: Boolean, val useDynamicLayout: Boolean) {
        override fun toString(): String = if (isOptimal) {
            if (useDynamicLayout) {
                "Dynamic/Optimal"
            } else {
                "Static/Optimal"
            }
        } else {
            if (useDynamicLayout) {
                "Dynamic/Greedy"
            } else {
                "Static/Greedy"
            }
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun getParams(): List<Param> = listOf(
                Param(true, true),
                Param(false, true),
                Param(true, false),
                Param(false, false)
        )
    }

    private val context = InstrumentationRegistry.getInstrumentation().getTargetContext()

    // The OvershootTest.ttf has the following coverage, extent, width and bbox.
    // U+0061(a), U+05D0(denoted as A in test comment): 1em, (   0, 0) - (1,   1)
    // U+0062(b), U+05D1(denoted as B in test comment): 1em, (   0, 0) - (1.5, 1)
    // U+0063(c), U+05D2(denoted as C in test comment): 1em, (   0, 0) - (2,   1)
    // U+0064(d), U+05D3(denoted as D in test comment): 1em, (   0, 0) - (2.5, 1)
    // U+0065(e), U+05D4(denoted as E in test comment): 1em, (-0.5, 0) - (1,   1)
    // U+0066(f), U+05D5(denoted as F in test comment): 1em, (-1.0, 0) - (1,   1)
    // U+0067(g), U+05D6(denoted as G in test comment): 1em, (-1.5, 0) - (1,   1)
    private val overshootFont = Typeface.createFromAsset(context.assets, "fonts/OvershootTest.ttf")
    private val overshootPaint = TextPaint().apply {
        typeface = overshootFont
        textSize = 10f // make 1em = 10px
    }

    private fun buildLayout(text: String, widthPx: Int, shiftDrawOffset: Boolean = false) =
            if (p.useDynamicLayout) {
                DynamicLayout.Builder.obtain(text, overshootPaint, widthPx)
                        .setUseBoundsForWidth(true)
                        .setBreakStrategy(if (p.isOptimal) {
                            LineBreaker.BREAK_STRATEGY_HIGH_QUALITY
                        } else {
                            LineBreaker.BREAK_STRATEGY_SIMPLE
                        })
                        .setShiftDrawingOffsetForStartOverhang(shiftDrawOffset)
                        .build()
                        .also {
                            assertThat(it.useBoundsForWidth).isTrue()
                            assertThat(it.shiftDrawingOffsetForStartOverhang)
                                    .isEqualTo(shiftDrawOffset)
                        }
            } else {
                StaticLayout.Builder.obtain(text, 0, text.length, overshootPaint, widthPx)
                        .setUseBoundsForWidth(true)
                        .setBreakStrategy(if (p.isOptimal) {
                            LineBreaker.BREAK_STRATEGY_HIGH_QUALITY
                        } else {
                            LineBreaker.BREAK_STRATEGY_SIMPLE
                        })
                        .setShiftDrawingOffsetForStartOverhang(shiftDrawOffset)
                        .build()
                        .also {
                            assertThat(it.useBoundsForWidth).isTrue()
                            assertThat(it.shiftDrawingOffsetForStartOverhang)
                                    .isEqualTo(shiftDrawOffset)
                        }
            }

    @Test
    fun testBreakOvershoot_trailing_LTR() {
        val text = "aaaa bbbb cccc dddd"

        // Width constraint: 1000px
        // |aaaa bbbb cccc dddd     : width: 205, max: 205
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 1000, false))).isEqualTo(0)
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 1000, true))).isEqualTo(0)
        var layout = buildLayout(text, 1000)
        assertThat(layout.computeDrawingBoundingBox()).isEqualTo(RectF(0f, 0f, 205f, 10f))
        assertThat(getDrawingHorizontalOffset(layout)).isEqualTo(0)
        assertThat(layout.lineCount).isEqualTo(1)
        assertThat(layout.getLineEnd(0)).isEqualTo(19)
        assertThat(layout.getLineWidth(0)).isEqualTo(205)
        assertThat(layout.getLineMax(0)).isEqualTo(205)

        // Width constraint: 150px
        // |aaaa bbbb cccc     |: width: 150, max 150
        // |dddd               |: width: 55, max 55
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 150, false))).isEqualTo(0)
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 150, true))).isEqualTo(0)
        layout = buildLayout(text, 150)
        assertThat(layout.computeDrawingBoundingBox()).isEqualTo(RectF(0f, 0f, 150f, 20f))
        assertThat(layout.lineCount).isEqualTo(2)
        // Line 0
        assertThat(layout.getLineEnd(0)).isEqualTo(15)
        assertThat(layout.getLineVisibleEnd(0)).isEqualTo(14)
        assertThat(layout.getLineWidth(0)).isEqualTo(150)
        assertThat(layout.getLineMax(0)).isEqualTo(150)
        // Line 1
        assertThat(layout.getLineEnd(1)).isEqualTo(19)
        assertThat(layout.getLineVisibleEnd(1)).isEqualTo(19)
        assertThat(layout.getLineWidth(1)).isEqualTo(55)
        assertThat(layout.getLineMax(1)).isEqualTo(55)

        // Width constraint: 105px
        // |aaaa bbbb    |: width: 100, max: 95
        // |cccc dddd    |: width: 105, max 105
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 105, false))).isEqualTo(0)
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 105, true))).isEqualTo(0)
        layout = buildLayout(text, 105)
        assertThat(layout.computeDrawingBoundingBox()).isEqualTo(RectF(0f, 0f, 105f, 20f))
        assertThat(layout.lineCount).isEqualTo(2)
        // Line 0
        assertThat(layout.getLineEnd(0)).isEqualTo(10)
        assertThat(layout.getLineVisibleEnd(0)).isEqualTo(9)
        assertThat(layout.getLineWidth(0)).isEqualTo(100)
        assertThat(layout.getLineMax(0)).isEqualTo(95)
        // Line 1
        assertThat(layout.getLineEnd(1)).isEqualTo(19)
        assertThat(layout.getLineVisibleEnd(1)).isEqualTo(19)
        assertThat(layout.getLineWidth(1)).isEqualTo(105)
        assertThat(layout.getLineMax(1)).isEqualTo(105)

        // Width constraint: 95px
        // |aaaa bbbb|: width: 100, max: 95
        // |cccc     |: width: 50, max: 50
        // |dddd     |: width: 55, max: 55
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 95, false))).isEqualTo(0)
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 95, true))).isEqualTo(0)
        layout = buildLayout(text, 95)
        assertThat(layout.computeDrawingBoundingBox()).isEqualTo(RectF(0f, 0f, 95f, 30f))
        assertThat(layout.lineCount).isEqualTo(3)
        // Line 0
        assertThat(layout.getLineEnd(0)).isEqualTo(10)
        assertThat(layout.getLineVisibleEnd(0)).isEqualTo(9)
        assertThat(layout.getLineWidth(0)).isEqualTo(100)
        assertThat(layout.getLineMax(0)).isEqualTo(95)
        // Line 1
        assertThat(layout.getLineEnd(1)).isEqualTo(15)
        assertThat(layout.getLineVisibleEnd(1)).isEqualTo(14)
        assertThat(layout.getLineWidth(1)).isEqualTo(50)
        assertThat(layout.getLineMax(1)).isEqualTo(50)
        // Line 2
        assertThat(layout.getLineEnd(2)).isEqualTo(19)
        assertThat(layout.getLineVisibleEnd(2)).isEqualTo(19)
        assertThat(layout.getLineWidth(2)).isEqualTo(55)
        assertThat(layout.getLineMax(2)).isEqualTo(55)

        // Width constraint: 55px
        // |aaaa|: width: 50, max: 40
        // |bbbb|: width: 50, max: 45
        // |cccc|: width: 50, max: 50
        // |dddd|: width: 55, max: 55
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 55, false))).isEqualTo(0)
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 55, true))).isEqualTo(0)
        layout = buildLayout(text, 55)
        assertThat(layout.computeDrawingBoundingBox()).isEqualTo(RectF(0f, 0f, 55f, 40f))
        assertThat(layout.lineCount).isEqualTo(4)
        // Line 0
        assertThat(layout.getLineEnd(0)).isEqualTo(5)
        assertThat(layout.getLineVisibleEnd(0)).isEqualTo(4)
        assertThat(layout.getLineWidth(0)).isEqualTo(50)
        assertThat(layout.getLineMax(0)).isEqualTo(40)
        // Line 1
        assertThat(layout.getLineEnd(1)).isEqualTo(10)
        assertThat(layout.getLineVisibleEnd(1)).isEqualTo(9)
        assertThat(layout.getLineWidth(1)).isEqualTo(50)
        assertThat(layout.getLineMax(1)).isEqualTo(45)
        // Line 2
        assertThat(layout.getLineEnd(2)).isEqualTo(15)
        assertThat(layout.getLineVisibleEnd(2)).isEqualTo(14)
        assertThat(layout.getLineWidth(2)).isEqualTo(50)
        assertThat(layout.getLineMax(2)).isEqualTo(50)
        // Line 3
        assertThat(layout.getLineEnd(3)).isEqualTo(19)
        assertThat(layout.getLineVisibleEnd(3)).isEqualTo(19)
        assertThat(layout.getLineWidth(3)).isEqualTo(55)
        assertThat(layout.getLineMax(3)).isEqualTo(55)
    }

    @Test
    fun testBreakOvershoot_trailing_RTL() {
        val text = "\u05D0\u05D0\u05D0\u05D0 \u05D1\u05D1\u05D1\u05D1 " +
                "\u05D2\u05D2\u05D2\u05D2 \u05D3\u05D3\u05D3\u05D3"

        // Width constraint: 1000px
        // DDDD CCCC BBBB AAAA|: width: 190, max: 190
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 1000, false))).isEqualTo(0)
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 1000, true))).isEqualTo(0)
        var layout = buildLayout(text, 1000)
        assertThat(layout.computeDrawingBoundingBox()).isEqualTo(RectF(810f, 0f, 1000f, 10f))
        assertThat(getDrawingHorizontalOffset(layout)).isEqualTo(0)
        assertThat(layout.lineCount).isEqualTo(1)
        assertThat(layout.getLineEnd(0)).isEqualTo(19)
        assertThat(layout.getLineWidth(0)).isEqualTo(190)
        assertThat(layout.getLineMax(0)).isEqualTo(190)

        // Width constraint: 150px
        // |CCCC BBBB AAAA|: width: 150, max: 140
        // |          DDDD|: width: 55, max: 55
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 150, false))).isEqualTo(0)
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 150, true))).isEqualTo(0)
        layout = buildLayout(text, 150)
        assertThat(layout.computeDrawingBoundingBox()).isEqualTo(RectF(10f, 0f, 165f, 20f))
        assertThat(layout.lineCount).isEqualTo(2)
        // Line 0
        assertThat(layout.getLineEnd(0)).isEqualTo(15)
        assertThat(layout.getLineVisibleEnd(0)).isEqualTo(14)
        assertThat(layout.getLineWidth(0)).isEqualTo(150)
        assertThat(layout.getLineMax(0)).isEqualTo(140)
        // Line 1
        assertThat(layout.getLineEnd(1)).isEqualTo(19)
        assertThat(layout.getLineVisibleEnd(1)).isEqualTo(19)
        assertThat(layout.getLineWidth(1)).isEqualTo(55)
        assertThat(layout.getLineMax(1)).isEqualTo(55)

        // Width constraint: 105px
        // |BBBB AAAA|: width: 100, max: 90
        // |DDDD CCCC|: width: 100, max 100
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 105, false))).isEqualTo(0)
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 105, true))).isEqualTo(0)
        layout = buildLayout(text, 105)
        assertThat(layout.computeDrawingBoundingBox()).isEqualTo(RectF(15f, 0f, 115f, 20f))
        assertThat(layout.lineCount).isEqualTo(2)
        // Line 0
        assertThat(layout.getLineEnd(0)).isEqualTo(10)
        assertThat(layout.getLineVisibleEnd(0)).isEqualTo(9)
        assertThat(layout.getLineWidth(0)).isEqualTo(100)
        assertThat(layout.getLineMax(0)).isEqualTo(90)
        // Line 1
        assertThat(layout.getLineEnd(1)).isEqualTo(19)
        assertThat(layout.getLineVisibleEnd(1)).isEqualTo(19)
        assertThat(layout.getLineWidth(1)).isEqualTo(100)
        assertThat(layout.getLineMax(1)).isEqualTo(100)

        // Width constraint: 95px
        // |BBBB AAAA|: width: 100, max: 90
        // |     CCCC|: width: 60, max: 50
        // |     DDDD|: width: 55, max: 55
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 95, false))).isEqualTo(0)
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 95, true))).isEqualTo(0)
        layout = buildLayout(text, 95)
        assertThat(layout.computeDrawingBoundingBox()).isEqualTo(RectF(5f, 0f, 110f, 30f))
        assertThat(layout.lineCount).isEqualTo(3)
        // Line 0
        assertThat(layout.getLineEnd(0)).isEqualTo(10)
        assertThat(layout.getLineVisibleEnd(0)).isEqualTo(9)
        assertThat(layout.getLineWidth(0)).isEqualTo(100)
        assertThat(layout.getLineMax(0)).isEqualTo(90)
        // Line 1
        assertThat(layout.getLineEnd(1)).isEqualTo(15)
        assertThat(layout.getLineVisibleEnd(1)).isEqualTo(14)
        assertThat(layout.getLineWidth(1)).isEqualTo(60)
        assertThat(layout.getLineMax(1)).isEqualTo(50)
        // Line 2
        assertThat(layout.getLineEnd(2)).isEqualTo(19)
        assertThat(layout.getLineVisibleEnd(2)).isEqualTo(19)
        assertThat(layout.getLineWidth(2)).isEqualTo(55)
        assertThat(layout.getLineMax(2)).isEqualTo(55)

        // Width constraint: 55px
        // |AAAA|: width: 50, max: 40
        // |BBBB|: width: 55, max: 45
        // |CCCC|: width: 60, max: 50
        // |DDDD|: width: 55, max: 55
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 55, false))).isEqualTo(0)
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 55, true))).isEqualTo(0)
        layout = buildLayout(text, 55)
        assertThat(layout.computeDrawingBoundingBox()).isEqualTo(RectF(15f, 0f, 70f, 40f))
        assertThat(layout.lineCount).isEqualTo(4)
        // Line 0
        assertThat(layout.getLineEnd(0)).isEqualTo(5)
        assertThat(layout.getLineVisibleEnd(0)).isEqualTo(4)
        assertThat(layout.getLineWidth(0)).isEqualTo(50)
        assertThat(layout.getLineMax(0)).isEqualTo(40)
        // Line 1
        assertThat(layout.getLineEnd(1)).isEqualTo(10)
        assertThat(layout.getLineVisibleEnd(1)).isEqualTo(9)
        assertThat(layout.getLineWidth(1)).isEqualTo(55)
        assertThat(layout.getLineMax(1)).isEqualTo(45)
        // Line 2
        assertThat(layout.getLineEnd(2)).isEqualTo(15)
        assertThat(layout.getLineVisibleEnd(2)).isEqualTo(14)
        assertThat(layout.getLineWidth(2)).isEqualTo(60)
        assertThat(layout.getLineMax(2)).isEqualTo(50)
        // Line 3
        assertThat(layout.getLineEnd(3)).isEqualTo(19)
        assertThat(layout.getLineVisibleEnd(3)).isEqualTo(19)
        assertThat(layout.getLineWidth(3)).isEqualTo(55)
        assertThat(layout.getLineMax(3)).isEqualTo(55)
    }

    @Test
    fun testBreakOvershoot_trailing_Bidi_LTRFirst() {
        val text = "a\u05D0\u05D0a b\u05D1\u05D1b c\u05D2\u05D2c d\u05D3\u05D3d"

        // Width constraint: 1000px
        // |aAAa bBBb cCCc dDDd     : width: 205, max: 205
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 1000, false))).isEqualTo(0)
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 1000, true))).isEqualTo(0)
        var layout = buildLayout(text, 1000)
        assertThat(layout.computeDrawingBoundingBox()).isEqualTo(RectF(0f, 0f, 205f, 10f))
        assertThat(getDrawingHorizontalOffset(layout)).isEqualTo(0)
        assertThat(layout.lineCount).isEqualTo(1)
        assertThat(layout.getLineEnd(0)).isEqualTo(19)
        assertThat(layout.getLineWidth(0)).isEqualTo(205)
        assertThat(layout.getLineMax(0)).isEqualTo(205)

        // Width constraint: 150px
        // |aAAa bBBb cCCc     |: width: 150, max 150
        // |dDDd               |: width: 55, max 150
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 150, false))).isEqualTo(0)
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 150, true))).isEqualTo(0)
        layout = buildLayout(text, 150)
        assertThat(layout.computeDrawingBoundingBox()).isEqualTo(RectF(0f, 0f, 150f, 20f))
        assertThat(layout.lineCount).isEqualTo(2)
        // Line 0
        assertThat(layout.getLineEnd(0)).isEqualTo(15)
        assertThat(layout.getLineVisibleEnd(0)).isEqualTo(14)
        assertThat(layout.getLineWidth(0)).isEqualTo(150)
        assertThat(layout.getLineMax(0)).isEqualTo(150)
        // Line 1
        assertThat(layout.getLineEnd(1)).isEqualTo(19)
        assertThat(layout.getLineVisibleEnd(1)).isEqualTo(19)
        assertThat(layout.getLineWidth(1)).isEqualTo(55)
        assertThat(layout.getLineMax(1)).isEqualTo(55)

        // Width constraint: 105px
        // |aAAa bBBb    |: width: 100, max: 95
        // |cCCc dDDd    |: width: 105, max 105
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 105, false))).isEqualTo(0)
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 105, true))).isEqualTo(0)
        layout = buildLayout(text, 105)
        assertThat(layout.computeDrawingBoundingBox()).isEqualTo(RectF(0f, 0f, 105f, 20f))
        assertThat(layout.lineCount).isEqualTo(2)
        // Line 0
        assertThat(layout.getLineEnd(0)).isEqualTo(10)
        assertThat(layout.getLineVisibleEnd(0)).isEqualTo(9)
        assertThat(layout.getLineWidth(0)).isEqualTo(100)
        assertThat(layout.getLineMax(0)).isEqualTo(95)
        // Line 1
        assertThat(layout.getLineEnd(1)).isEqualTo(19)
        assertThat(layout.getLineVisibleEnd(1)).isEqualTo(19)
        assertThat(layout.getLineWidth(1)).isEqualTo(105)
        assertThat(layout.getLineMax(1)).isEqualTo(105)

        // Width constraint: 95px
        // |aAAa bBBb|: width: 100, max: 95
        // |cCCc     |: width: 50, max: 50
        // |dDDd     |: width: 55, max: 55
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 95, false))).isEqualTo(0)
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 95, true))).isEqualTo(0)
        layout = buildLayout(text, 95)
        assertThat(layout.computeDrawingBoundingBox()).isEqualTo(RectF(0f, 0f, 95f, 30f))
        assertThat(layout.lineCount).isEqualTo(3)
        // Line 0
        assertThat(layout.getLineEnd(0)).isEqualTo(10)
        assertThat(layout.getLineVisibleEnd(0)).isEqualTo(9)
        assertThat(layout.getLineWidth(0)).isEqualTo(100)
        assertThat(layout.getLineMax(0)).isEqualTo(95)
        // Line 1
        assertThat(layout.getLineEnd(1)).isEqualTo(15)
        assertThat(layout.getLineVisibleEnd(1)).isEqualTo(14)
        assertThat(layout.getLineWidth(1)).isEqualTo(50)
        assertThat(layout.getLineMax(1)).isEqualTo(50)
        // Line 2
        assertThat(layout.getLineEnd(2)).isEqualTo(19)
        assertThat(layout.getLineVisibleEnd(2)).isEqualTo(19)
        assertThat(layout.getLineWidth(2)).isEqualTo(55)
        assertThat(layout.getLineMax(2)).isEqualTo(55)

        // Width constraint: 55px
        // |aAAa|: width: 50, max: 40
        // |bBBb|: width: 50, max: 45
        // |cCCc|: width: 50, max: 50
        // |dDDd|: width: 55, max: 55
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 55, false))).isEqualTo(0)
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 55, true))).isEqualTo(0)
        layout = buildLayout(text, 55)
        assertThat(layout.computeDrawingBoundingBox()).isEqualTo(RectF(0f, 0f, 55f, 40f))
        assertThat(layout.lineCount).isEqualTo(4)
        // Line 0
        assertThat(layout.getLineEnd(0)).isEqualTo(5)
        assertThat(layout.getLineVisibleEnd(0)).isEqualTo(4)
        assertThat(layout.getLineWidth(0)).isEqualTo(50)
        assertThat(layout.getLineMax(0)).isEqualTo(40)
        // Line 1
        assertThat(layout.getLineEnd(1)).isEqualTo(10)
        assertThat(layout.getLineVisibleEnd(1)).isEqualTo(9)
        assertThat(layout.getLineWidth(1)).isEqualTo(50)
        assertThat(layout.getLineMax(1)).isEqualTo(45)
        // Line 2
        assertThat(layout.getLineEnd(2)).isEqualTo(15)
        assertThat(layout.getLineVisibleEnd(2)).isEqualTo(14)
        assertThat(layout.getLineWidth(2)).isEqualTo(50)
        assertThat(layout.getLineMax(2)).isEqualTo(50)
        // Line 3
        assertThat(layout.getLineEnd(3)).isEqualTo(19)
        assertThat(layout.getLineVisibleEnd(3)).isEqualTo(19)
        assertThat(layout.getLineWidth(3)).isEqualTo(55)
        assertThat(layout.getLineMax(3)).isEqualTo(55)
    }

    @Test
    fun testBreakOvershoot_trailing_Bidi_RTLFirst() {
        val text = "\u05D0aa\u05D0 \u05D1bb\u05D1 \u05D2cc\u05D2 \u05D3dd\u05D3"

        // Width constraint: 1000px
        // DddD CccC BbbB AaaA|: width: 190, max: 190
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 1000, false))).isEqualTo(0)
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 1000, true))).isEqualTo(0)
        var layout = buildLayout(text, 1000)
        assertThat(layout.computeDrawingBoundingBox()).isEqualTo(RectF(810f, 0f, 1000f, 10f))
        assertThat(getDrawingHorizontalOffset(layout)).isEqualTo(0)
        assertThat(layout.lineCount).isEqualTo(1)
        assertThat(layout.getLineEnd(0)).isEqualTo(19)
        assertThat(layout.getLineWidth(0)).isEqualTo(190)
        assertThat(layout.getLineMax(0)).isEqualTo(190)

        // Width constraint: 150px
        // |CccC BbbB AaaA|: width: 150, max: 140
        // |          DddD|: width: 55, max: 55
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 150, false))).isEqualTo(0)
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 150, true))).isEqualTo(0)
        layout = buildLayout(text, 150)
        assertThat(layout.computeDrawingBoundingBox()).isEqualTo(RectF(10f, 0f, 165f, 20f))
        assertThat(layout.lineCount).isEqualTo(2)
        // Line 0
        assertThat(layout.getLineEnd(0)).isEqualTo(15)
        assertThat(layout.getLineVisibleEnd(0)).isEqualTo(14)
        assertThat(layout.getLineWidth(0)).isEqualTo(150)
        assertThat(layout.getLineMax(0)).isEqualTo(140)
        // Line 1
        assertThat(layout.getLineEnd(1)).isEqualTo(19)
        assertThat(layout.getLineVisibleEnd(1)).isEqualTo(19)
        assertThat(layout.getLineWidth(1)).isEqualTo(55)
        assertThat(layout.getLineMax(1)).isEqualTo(55)

        // Width constraint: 105px
        // |BbbB AaaA|: width: 100, max: 90
        // |DddD CccC|: width: 100, max 100
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 105, false))).isEqualTo(0)
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 105, true))).isEqualTo(0)
        layout = buildLayout(text, 105)
        assertThat(layout.computeDrawingBoundingBox()).isEqualTo(RectF(15f, 0f, 115f, 20f))
        assertThat(layout.lineCount).isEqualTo(2)
        // Line 0
        assertThat(layout.getLineEnd(0)).isEqualTo(10)
        assertThat(layout.getLineVisibleEnd(0)).isEqualTo(9)
        assertThat(layout.getLineWidth(0)).isEqualTo(100)
        assertThat(layout.getLineMax(0)).isEqualTo(90)
        // Line 1
        assertThat(layout.getLineEnd(1)).isEqualTo(19)
        assertThat(layout.getLineVisibleEnd(1)).isEqualTo(19)
        assertThat(layout.getLineWidth(1)).isEqualTo(100)
        assertThat(layout.getLineMax(1)).isEqualTo(100)

        // Width constraint: 95px
        // |BbbB AaaA|: width: 100, max: 90
        // |     CccC|: width: 60, max: 50
        // |     DddD|: width: 55, max: 55
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 95, false))).isEqualTo(0)
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 95, true))).isEqualTo(0)
        layout = buildLayout(text, 95)
        assertThat(layout.computeDrawingBoundingBox()).isEqualTo(RectF(5f, 0f, 110f, 30f))
        assertThat(layout.lineCount).isEqualTo(3)
        // Line 0
        assertThat(layout.getLineEnd(0)).isEqualTo(10)
        assertThat(layout.getLineVisibleEnd(0)).isEqualTo(9)
        assertThat(layout.getLineWidth(0)).isEqualTo(100)
        assertThat(layout.getLineMax(0)).isEqualTo(90)
        // Line 1
        assertThat(layout.getLineEnd(1)).isEqualTo(15)
        assertThat(layout.getLineVisibleEnd(1)).isEqualTo(14)
        assertThat(layout.getLineWidth(1)).isEqualTo(60)
        assertThat(layout.getLineMax(1)).isEqualTo(50)
        // Line 2
        assertThat(layout.getLineEnd(2)).isEqualTo(19)
        assertThat(layout.getLineVisibleEnd(2)).isEqualTo(19)
        assertThat(layout.getLineWidth(2)).isEqualTo(55)
        assertThat(layout.getLineMax(2)).isEqualTo(55)

        // Width constraint: 55px
        // |AaaA|: width: 50, max: 40
        // |BbbB|: width: 55, max: 45
        // |CccC|: width: 60, max: 50
        // |DddD|: width: 55, max: 55
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 55, false))).isEqualTo(0)
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 55, true))).isEqualTo(0)
        layout = buildLayout(text, 55)
        assertThat(layout.computeDrawingBoundingBox()).isEqualTo(RectF(15f, 0f, 70f, 40f))
        assertThat(layout.lineCount).isEqualTo(4)
        // Line 0
        assertThat(layout.getLineEnd(0)).isEqualTo(5)
        assertThat(layout.getLineVisibleEnd(0)).isEqualTo(4)
        assertThat(layout.getLineWidth(0)).isEqualTo(50)
        assertThat(layout.getLineMax(0)).isEqualTo(40)
        // Line 1
        assertThat(layout.getLineEnd(1)).isEqualTo(10)
        assertThat(layout.getLineVisibleEnd(1)).isEqualTo(9)
        assertThat(layout.getLineWidth(1)).isEqualTo(55)
        assertThat(layout.getLineMax(1)).isEqualTo(45)
        // Line 2
        assertThat(layout.getLineEnd(2)).isEqualTo(15)
        assertThat(layout.getLineVisibleEnd(2)).isEqualTo(14)
        assertThat(layout.getLineWidth(2)).isEqualTo(60)
        assertThat(layout.getLineMax(2)).isEqualTo(50)
        // Line 3
        assertThat(layout.getLineEnd(3)).isEqualTo(19)
        assertThat(layout.getLineVisibleEnd(3)).isEqualTo(19)
        assertThat(layout.getLineWidth(3)).isEqualTo(55)
        assertThat(layout.getLineMax(3)).isEqualTo(55)
    }

    @Test
    fun testBreakOvershoot_preceding_LTR() {
        val text = "aaaa eeee ffff gggg"

        // Width constraint: 1000px
        // |aaaa eeee ffff gggg     : width: 190, max: 190
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 1000, false))).isEqualTo(0)
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 1000, true))).isEqualTo(0)
        var layout = buildLayout(text, 1000)
        assertThat(layout.computeDrawingBoundingBox()).isEqualTo(RectF(0f, 0f, 190f, 10f))
        assertThat(layout.lineCount).isEqualTo(1)
        assertThat(layout.getLineEnd(0)).isEqualTo(19)
        assertThat(layout.getLineWidth(0)).isEqualTo(190)
        assertThat(layout.getLineMax(0)).isEqualTo(190)

        // Width constraint: 150px
        // |aaaa eeee ffff     |: width: 150, max 140
        // |gggg               |: width: 55, max 55
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 150, false))).isEqualTo(0)
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 150, true))).isEqualTo(15)
        layout = buildLayout(text, 150)
        assertThat(layout.computeDrawingBoundingBox()).isEqualTo(RectF(-15f, 0f, 140f, 20f))
        assertThat(layout.lineCount).isEqualTo(2)
        // Line 0
        assertThat(layout.getLineEnd(0)).isEqualTo(15)
        assertThat(layout.getLineVisibleEnd(0)).isEqualTo(14)
        assertThat(layout.getLineWidth(0)).isEqualTo(150)
        assertThat(layout.getLineMax(0)).isEqualTo(140)
        // Line 1
        assertThat(layout.getLineEnd(1)).isEqualTo(19)
        assertThat(layout.getLineVisibleEnd(1)).isEqualTo(19)
        assertThat(layout.getLineWidth(1)).isEqualTo(55)
        assertThat(layout.getLineMax(1)).isEqualTo(55)

        // Width constraint: 105px
        // |aaaa eeee    |: width: 100, max: 90
        // |ffff gggg    |: width: 100, max 100
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 105, false))).isEqualTo(0)
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 105, true))).isEqualTo(10)
        layout = buildLayout(text, 105)
        assertThat(layout.computeDrawingBoundingBox()).isEqualTo(RectF(-10f, 0f, 90f, 20f))
        assertThat(layout.lineCount).isEqualTo(2)
        // Line 0
        assertThat(layout.getLineEnd(0)).isEqualTo(10)
        assertThat(layout.getLineVisibleEnd(0)).isEqualTo(9)
        assertThat(layout.getLineWidth(0)).isEqualTo(100)
        assertThat(layout.getLineMax(0)).isEqualTo(90)
        // Line 1
        assertThat(layout.getLineEnd(1)).isEqualTo(19)
        assertThat(layout.getLineVisibleEnd(1)).isEqualTo(19)
        assertThat(layout.getLineWidth(1)).isEqualTo(100)
        assertThat(layout.getLineMax(1)).isEqualTo(100)

        // Width constraint: 95px
        // |aaaa eeee|: width: 100, max: 90
        // |ffff     |: width: 60, max: 50
        // |gggg     |: width: 55, max: 55
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 95, false))).isEqualTo(0)
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 95, true))).isEqualTo(15)
        layout = buildLayout(text, 95)
        assertThat(layout.computeDrawingBoundingBox()).isEqualTo(RectF(-15f, 0f, 90f, 30f))
        assertThat(layout.lineCount).isEqualTo(3)
        // Line 0
        assertThat(layout.getLineEnd(0)).isEqualTo(10)
        assertThat(layout.getLineVisibleEnd(0)).isEqualTo(9)
        assertThat(layout.getLineWidth(0)).isEqualTo(100)
        assertThat(layout.getLineMax(0)).isEqualTo(90)
        // Line 1
        assertThat(layout.getLineEnd(1)).isEqualTo(15)
        assertThat(layout.getLineVisibleEnd(1)).isEqualTo(14)
        assertThat(layout.getLineWidth(1)).isEqualTo(60)
        assertThat(layout.getLineMax(1)).isEqualTo(50)
        // Line 2
        assertThat(layout.getLineEnd(2)).isEqualTo(19)
        assertThat(layout.getLineVisibleEnd(2)).isEqualTo(19)
        assertThat(layout.getLineWidth(2)).isEqualTo(55)
        assertThat(layout.getLineMax(2)).isEqualTo(55)

        // Width constraint: 55px
        // |aaaa|: width: 50, max: 40
        // |bbbb|: width: 55, max: 45
        // |cccc|: width: 60, max: 50
        // |dddd|: width: 55, max: 55
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 55, false))).isEqualTo(0)
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 55, true))).isEqualTo(15)
        layout = buildLayout(text, 55)
        assertThat(layout.computeDrawingBoundingBox()).isEqualTo(RectF(-15f, 0f, 40f, 40f))
        assertThat(layout.lineCount).isEqualTo(4)
        // Line 0
        assertThat(layout.getLineEnd(0)).isEqualTo(5)
        assertThat(layout.getLineVisibleEnd(0)).isEqualTo(4)
        assertThat(layout.getLineWidth(0)).isEqualTo(50)
        assertThat(layout.getLineMax(0)).isEqualTo(40)
        // Line 1
        assertThat(layout.getLineEnd(1)).isEqualTo(10)
        assertThat(layout.getLineVisibleEnd(1)).isEqualTo(9)
        assertThat(layout.getLineWidth(1)).isEqualTo(55)
        assertThat(layout.getLineMax(1)).isEqualTo(45)
        // Line 2
        assertThat(layout.getLineEnd(2)).isEqualTo(15)
        assertThat(layout.getLineVisibleEnd(2)).isEqualTo(14)
        assertThat(layout.getLineWidth(2)).isEqualTo(60)
        assertThat(layout.getLineMax(2)).isEqualTo(50)
        // Line 3
        assertThat(layout.getLineEnd(3)).isEqualTo(19)
        assertThat(layout.getLineVisibleEnd(3)).isEqualTo(19)
        assertThat(layout.getLineWidth(3)).isEqualTo(55)
        assertThat(layout.getLineMax(3)).isEqualTo(55)
    }

    @Test
    fun testBreakOvershoot_preceding_RTL() {
        val text = "\u05D0\u05D0\u05D0\u05D0 \u05D4\u05D4\u05D4\u05D4 " +
                "\u05D5\u05D5\u05D5\u05D5 \u05D6\u05D6\u05D6\u05D6"

        // Width constraint: 1000px
        // GGGG FFFF EEEE AAAA|: width: 205, max: 205
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 1000, false))).isEqualTo(0)
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 1000, true))).isEqualTo(0)
        var layout = buildLayout(text, 1000)
        assertThat(layout.computeDrawingBoundingBox()).isEqualTo(RectF(795f, 0f, 1000f, 10f))
        assertThat(layout.lineCount).isEqualTo(1)
        assertThat(layout.getLineEnd(0)).isEqualTo(19)
        assertThat(layout.getLineWidth(0)).isEqualTo(205)
        assertThat(layout.getLineMax(0)).isEqualTo(205)

        // Width constraint: 150px
        // |FFFF EEEE AAAA|: width: 150, max: 150
        // |          GGGG|: width: 55, max: 55
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 150, false))).isEqualTo(0)
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 150, true))).isEqualTo(0)
        layout = buildLayout(text, 150)
        assertThat(layout.computeDrawingBoundingBox()).isEqualTo(RectF(0f, 0f, 150f, 20f))
        assertThat(layout.lineCount).isEqualTo(2)
        // Line 0
        assertThat(layout.getLineEnd(0)).isEqualTo(15)
        assertThat(layout.getLineVisibleEnd(0)).isEqualTo(14)
        assertThat(layout.getLineWidth(0)).isEqualTo(150)
        assertThat(layout.getLineMax(0)).isEqualTo(150)
        // Line 1
        assertThat(layout.getLineEnd(1)).isEqualTo(19)
        assertThat(layout.getLineVisibleEnd(1)).isEqualTo(19)
        assertThat(layout.getLineWidth(1)).isEqualTo(55)
        assertThat(layout.getLineMax(1)).isEqualTo(55)

        // Width constraint: 105px
        // |EEEE AAAA|: width: 100, max: 95
        // |GGGG FFFF|: width: 105, max 105
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 105, false))).isEqualTo(0)
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 105, true))).isEqualTo(0)
        layout = buildLayout(text, 105)
        assertThat(layout.computeDrawingBoundingBox()).isEqualTo(RectF(0f, 0f, 105f, 20f))
        assertThat(layout.lineCount).isEqualTo(2)
        // Line 0
        assertThat(layout.getLineEnd(0)).isEqualTo(10)
        assertThat(layout.getLineVisibleEnd(0)).isEqualTo(9)
        assertThat(layout.getLineWidth(0)).isEqualTo(100)
        assertThat(layout.getLineMax(0)).isEqualTo(95)
        // Line 1
        assertThat(layout.getLineEnd(1)).isEqualTo(19)
        assertThat(layout.getLineVisibleEnd(1)).isEqualTo(19)
        assertThat(layout.getLineWidth(1)).isEqualTo(105)
        assertThat(layout.getLineMax(1)).isEqualTo(105)

        // Width constraint: 95px
        // |EEEE AAAA|: width: 100, max: 95
        // |     FFFF|: width: 50, max: 50
        // |     GGGG|: width: 55, max: 55
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 95, false))).isEqualTo(0)
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 95, true))).isEqualTo(0)
        layout = buildLayout(text, 95)
        assertThat(layout.computeDrawingBoundingBox()).isEqualTo(RectF(0f, 0f, 95f, 30f))
        assertThat(layout.lineCount).isEqualTo(3)
        // Line 0
        assertThat(layout.getLineEnd(0)).isEqualTo(10)
        assertThat(layout.getLineVisibleEnd(0)).isEqualTo(9)
        assertThat(layout.getLineWidth(0)).isEqualTo(100)
        assertThat(layout.getLineMax(0)).isEqualTo(95)
        // Line 1
        assertThat(layout.getLineEnd(1)).isEqualTo(15)
        assertThat(layout.getLineVisibleEnd(1)).isEqualTo(14)
        assertThat(layout.getLineWidth(1)).isEqualTo(50)
        assertThat(layout.getLineMax(1)).isEqualTo(50)
        // Line 2
        assertThat(layout.getLineEnd(2)).isEqualTo(19)
        assertThat(layout.getLineVisibleEnd(2)).isEqualTo(19)
        assertThat(layout.getLineWidth(2)).isEqualTo(55)
        assertThat(layout.getLineMax(2)).isEqualTo(55)

        // Width constraint: 55px
        // |AAAA|: width: 50, max: 40
        // |EEEE|: width: 50, max: 45
        // |FFFF|: width: 50, max: 50
        // |GGGG|: width: 55, max: 55
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 55, false))).isEqualTo(0)
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 55, true))).isEqualTo(0)
        layout = buildLayout(text, 55)
        assertThat(layout.computeDrawingBoundingBox()).isEqualTo(RectF(0f, 0f, 55f, 40f))
        assertThat(layout.lineCount).isEqualTo(4)
        // Line 0
        assertThat(layout.getLineEnd(0)).isEqualTo(5)
        assertThat(layout.getLineVisibleEnd(0)).isEqualTo(4)
        assertThat(layout.getLineWidth(0)).isEqualTo(50)
        assertThat(layout.getLineMax(0)).isEqualTo(40)
        // Line 1
        assertThat(layout.getLineEnd(1)).isEqualTo(10)
        assertThat(layout.getLineVisibleEnd(1)).isEqualTo(9)
        assertThat(layout.getLineWidth(1)).isEqualTo(50)
        assertThat(layout.getLineMax(1)).isEqualTo(45)
        // Line 2
        assertThat(layout.getLineEnd(2)).isEqualTo(15)
        assertThat(layout.getLineVisibleEnd(2)).isEqualTo(14)
        assertThat(layout.getLineWidth(2)).isEqualTo(50)
        assertThat(layout.getLineMax(2)).isEqualTo(50)
        // Line 3
        assertThat(layout.getLineEnd(3)).isEqualTo(19)
        assertThat(layout.getLineVisibleEnd(3)).isEqualTo(19)
        assertThat(layout.getLineWidth(3)).isEqualTo(55)
        assertThat(layout.getLineMax(3)).isEqualTo(55)
    }

    @Test
    fun testBreakOvershoot_preceding_Bidi_LTRFirst() {
        val text = "a\u05D0\u05D0a e\u05D4\u05D4e f\u05D5\u05D5f g\u05D6\u05D6g"

        // Width constraint: 1000px
        // |aAAa eEEe fFFf gGGg     : width: 190, max: 190
        var layout = buildLayout(text, 1000)
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 1000, false))).isEqualTo(0)
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 1000, true))).isEqualTo(0)
        assertThat(layout.computeDrawingBoundingBox()).isEqualTo(RectF(0f, 0f, 190f, 10f))
        assertThat(layout.lineCount).isEqualTo(1)
        assertThat(layout.getLineEnd(0)).isEqualTo(19)
        assertThat(layout.getLineWidth(0)).isEqualTo(190)
        assertThat(layout.getLineMax(0)).isEqualTo(190)

        // Width constraint: 150px
        // |aAAa eEEe fFFf     |: width: 150, max 140
        // |gGGg               |: width: 55, max 55
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 150, false))).isEqualTo(0)
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 150, true))).isEqualTo(15)
        layout = buildLayout(text, 150)
        assertThat(layout.computeDrawingBoundingBox()).isEqualTo(RectF(-15f, 0f, 140f, 20f))
        assertThat(layout.lineCount).isEqualTo(2)
        // Line 0
        assertThat(layout.getLineEnd(0)).isEqualTo(15)
        assertThat(layout.getLineVisibleEnd(0)).isEqualTo(14)
        assertThat(layout.getLineWidth(0)).isEqualTo(150)
        assertThat(layout.getLineMax(0)).isEqualTo(140)
        // Line 1
        assertThat(layout.getLineEnd(1)).isEqualTo(19)
        assertThat(layout.getLineVisibleEnd(1)).isEqualTo(19)
        assertThat(layout.getLineWidth(1)).isEqualTo(55)
        assertThat(layout.getLineMax(1)).isEqualTo(55)

        // Width constraint: 105px
        // |aAAa eEEe    |: width: 100, max: 90
        // |fFFf gGGg    |: width: 100, max 100
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 105, false))).isEqualTo(0)
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 105, true))).isEqualTo(10)
        layout = buildLayout(text, 105)
        assertThat(layout.computeDrawingBoundingBox()).isEqualTo(RectF(-10f, 0f, 90f, 20f))
        assertThat(layout.lineCount).isEqualTo(2)
        // Line 0
        assertThat(layout.getLineEnd(0)).isEqualTo(10)
        assertThat(layout.getLineVisibleEnd(0)).isEqualTo(9)
        assertThat(layout.getLineWidth(0)).isEqualTo(100)
        assertThat(layout.getLineMax(0)).isEqualTo(90)
        // Line 1
        assertThat(layout.getLineEnd(1)).isEqualTo(19)
        assertThat(layout.getLineVisibleEnd(1)).isEqualTo(19)
        assertThat(layout.getLineWidth(1)).isEqualTo(100)
        assertThat(layout.getLineMax(1)).isEqualTo(100)

        // Width constraint: 95px
        // |aAAa eEEe|: width: 100, max: 90
        // |fFFf     |: width: 60, max: 50
        // |gGGg     |: width: 55, max: 55
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 95, false))).isEqualTo(0)
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 95, true))).isEqualTo(15)
        layout = buildLayout(text, 95)
        assertThat(layout.computeDrawingBoundingBox()).isEqualTo(RectF(-15f, 0f, 90f, 30f))
        assertThat(layout.lineCount).isEqualTo(3)
        // Line 0
        assertThat(layout.getLineEnd(0)).isEqualTo(10)
        assertThat(layout.getLineVisibleEnd(0)).isEqualTo(9)
        assertThat(layout.getLineWidth(0)).isEqualTo(100)
        assertThat(layout.getLineMax(0)).isEqualTo(90)
        // Line 1
        assertThat(layout.getLineEnd(1)).isEqualTo(15)
        assertThat(layout.getLineVisibleEnd(1)).isEqualTo(14)
        assertThat(layout.getLineWidth(1)).isEqualTo(60)
        assertThat(layout.getLineMax(1)).isEqualTo(50)
        // Line 2
        assertThat(layout.getLineEnd(2)).isEqualTo(19)
        assertThat(layout.getLineVisibleEnd(2)).isEqualTo(19)
        assertThat(layout.getLineWidth(2)).isEqualTo(55)
        assertThat(layout.getLineMax(2)).isEqualTo(55)

        // Width constraint: 55px
        // |aAAa|: width: 50, max: 40
        // |bBBb|: width: 55, max: 45
        // |cCCc|: width: 60, max: 50
        // |dDDd|: width: 55, max: 55
        layout = buildLayout(text, 55)
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 55, false))).isEqualTo(0)
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 55, true))).isEqualTo(15)
        assertThat(layout.computeDrawingBoundingBox()).isEqualTo(RectF(-15f, 0f, 40f, 40f))
        assertThat(layout.lineCount).isEqualTo(4)
        // Line 0
        assertThat(layout.getLineEnd(0)).isEqualTo(5)
        assertThat(layout.getLineVisibleEnd(0)).isEqualTo(4)
        assertThat(layout.getLineWidth(0)).isEqualTo(50)
        assertThat(layout.getLineMax(0)).isEqualTo(40)
        // Line 1
        assertThat(layout.getLineEnd(1)).isEqualTo(10)
        assertThat(layout.getLineVisibleEnd(1)).isEqualTo(9)
        assertThat(layout.getLineWidth(1)).isEqualTo(55)
        assertThat(layout.getLineMax(1)).isEqualTo(45)
        // Line 2
        assertThat(layout.getLineEnd(2)).isEqualTo(15)
        assertThat(layout.getLineVisibleEnd(2)).isEqualTo(14)
        assertThat(layout.getLineWidth(2)).isEqualTo(60)
        assertThat(layout.getLineMax(2)).isEqualTo(50)
        // Line 3
        assertThat(layout.getLineEnd(3)).isEqualTo(19)
        assertThat(layout.getLineVisibleEnd(3)).isEqualTo(19)
        assertThat(layout.getLineWidth(3)).isEqualTo(55)
        assertThat(layout.getLineMax(3)).isEqualTo(55)
    }

    @Test
    fun testBreakOvershoot_preceding_Bidi_RTLFirst() {
        val text = "\u05D0aa\u05D0 \u05D4ee\u05D4 \u05D5ff\u05D5 \u05D6gg\u05D6"

        // Width constraint: 1000px
        // DddD CccC BbbB AaaA|: width: 205, max: 205
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 1000, false))).isEqualTo(0)
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 1000, true))).isEqualTo(0)
        var layout = buildLayout(text, 1000)
        assertThat(layout.computeDrawingBoundingBox()).isEqualTo(RectF(795f, 0f, 1000f, 10f))
        assertThat(layout.lineCount).isEqualTo(1)
        assertThat(layout.getLineEnd(0)).isEqualTo(19)
        assertThat(layout.getLineWidth(0)).isEqualTo(205)
        assertThat(layout.getLineMax(0)).isEqualTo(205)

        // Width constraint: 150px
        // |CccC BbbB AaaA|: width: 150, max: 150
        // |          DddD|: width: 55, max: 55
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 150, false))).isEqualTo(0)
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 150, true))).isEqualTo(0)
        layout = buildLayout(text, 150)
        assertThat(layout.computeDrawingBoundingBox()).isEqualTo(RectF(0f, 0f, 150f, 20f))
        assertThat(layout.lineCount).isEqualTo(2)
        // Line 0
        assertThat(layout.getLineEnd(0)).isEqualTo(15)
        assertThat(layout.getLineVisibleEnd(0)).isEqualTo(14)
        assertThat(layout.getLineWidth(0)).isEqualTo(150)
        assertThat(layout.getLineMax(0)).isEqualTo(150)
        // Line 1
        assertThat(layout.getLineEnd(1)).isEqualTo(19)
        assertThat(layout.getLineVisibleEnd(1)).isEqualTo(19)
        assertThat(layout.getLineWidth(1)).isEqualTo(55)
        assertThat(layout.getLineMax(1)).isEqualTo(55)

        // Width constraint: 105px
        // |BbbB AaaA|: width: 100, max: 95
        // |DddD CccC|: width: 105, max 105
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 105, false))).isEqualTo(0)
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 105, true))).isEqualTo(0)
        layout = buildLayout(text, 105)
        assertThat(layout.computeDrawingBoundingBox()).isEqualTo(RectF(0f, 0f, 105f, 20f))
        assertThat(layout.lineCount).isEqualTo(2)
        // Line 0
        assertThat(layout.getLineEnd(0)).isEqualTo(10)
        assertThat(layout.getLineVisibleEnd(0)).isEqualTo(9)
        assertThat(layout.getLineWidth(0)).isEqualTo(100)
        assertThat(layout.getLineMax(0)).isEqualTo(95)
        // Line 1
        assertThat(layout.getLineEnd(1)).isEqualTo(19)
        assertThat(layout.getLineVisibleEnd(1)).isEqualTo(19)
        assertThat(layout.getLineWidth(1)).isEqualTo(105)
        assertThat(layout.getLineMax(1)).isEqualTo(105)

        // Width constraint: 95px
        // |BbbB AaaA|: width: 100, max: 95
        // |     CccC|: width: 50, max: 50
        // |     DddD|: width: 55, max: 55
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 95, false))).isEqualTo(0)
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 95, true))).isEqualTo(0)
        layout = buildLayout(text, 95)
        assertThat(layout.computeDrawingBoundingBox()).isEqualTo(RectF(0f, 0f, 95f, 30f))
        assertThat(layout.lineCount).isEqualTo(3)
        // Line 0
        assertThat(layout.getLineEnd(0)).isEqualTo(10)
        assertThat(layout.getLineVisibleEnd(0)).isEqualTo(9)
        assertThat(layout.getLineWidth(0)).isEqualTo(100)
        assertThat(layout.getLineMax(0)).isEqualTo(95)
        // Line 1
        assertThat(layout.getLineEnd(1)).isEqualTo(15)
        assertThat(layout.getLineVisibleEnd(1)).isEqualTo(14)
        assertThat(layout.getLineWidth(1)).isEqualTo(50)
        assertThat(layout.getLineMax(1)).isEqualTo(50)
        // Line 2
        assertThat(layout.getLineEnd(2)).isEqualTo(19)
        assertThat(layout.getLineVisibleEnd(2)).isEqualTo(19)
        assertThat(layout.getLineWidth(2)).isEqualTo(55)
        assertThat(layout.getLineMax(2)).isEqualTo(55)

        // Width constraint: 55px
        // |AaaA|: width: 50, max: 40
        // |BbbB|: width: 50, max: 45
        // |CccC|: width: 50, max: 50
        // |DddD|: width: 55, max: 55
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 55, false))).isEqualTo(0)
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 55, true))).isEqualTo(0)
        layout = buildLayout(text, 55, false)
        assertThat(layout.computeDrawingBoundingBox()).isEqualTo(RectF(0f, 0f, 55f, 40f))
        assertThat(layout.lineCount).isEqualTo(4)
        // Line 0
        assertThat(layout.getLineEnd(0)).isEqualTo(5)
        assertThat(layout.getLineVisibleEnd(0)).isEqualTo(4)
        assertThat(layout.getLineWidth(0)).isEqualTo(50)
        assertThat(layout.getLineMax(0)).isEqualTo(40)
        // Line 1
        assertThat(layout.getLineEnd(1)).isEqualTo(10)
        assertThat(layout.getLineVisibleEnd(1)).isEqualTo(9)
        assertThat(layout.getLineWidth(1)).isEqualTo(50)
        assertThat(layout.getLineMax(1)).isEqualTo(45)
        // Line 2
        assertThat(layout.getLineEnd(2)).isEqualTo(15)
        assertThat(layout.getLineVisibleEnd(2)).isEqualTo(14)
        assertThat(layout.getLineWidth(2)).isEqualTo(50)
        assertThat(layout.getLineMax(2)).isEqualTo(50)
        // Line 3
        assertThat(layout.getLineEnd(3)).isEqualTo(19)
        assertThat(layout.getLineVisibleEnd(3)).isEqualTo(19)
        assertThat(layout.getLineWidth(3)).isEqualTo(55)
        assertThat(layout.getLineMax(3)).isEqualTo(55)
    }
}
