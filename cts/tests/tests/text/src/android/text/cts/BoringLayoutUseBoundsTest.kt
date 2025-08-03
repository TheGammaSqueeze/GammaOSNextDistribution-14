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
import android.platform.test.annotations.RequiresFlagsEnabled
import android.text.BoringLayout
import android.text.Layout
import android.text.TextPaint
import android.text.cts.LayoutUseBoundsUtil.getDrawingHorizontalOffset
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.text.flags.Flags.FLAG_USE_BOUNDS_FOR_WIDTH
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
@RequiresFlagsEnabled(FLAG_USE_BOUNDS_FOR_WIDTH)
class BoringLayoutUseBoundsTest {

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

    private fun buildLayout(text: String, widthPx: Int, shiftDrawingOffset: Boolean = false) =
            Layout.Builder(text, 0, text.length, overshootPaint, widthPx)
                    .setUseBoundsForWidth(true)
                    .setShiftDrawingOffsetForStartOverhang(shiftDrawingOffset)
                    .build().also {
                        assertThat(it).isInstanceOf(BoringLayout::class.java)
                        assertThat(it.useBoundsForWidth).isTrue()
                        assertThat(it.shiftDrawingOffsetForStartOverhang)
                                .isEqualTo(shiftDrawingOffset)
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
        assertThat(layout.lineCount).isEqualTo(1)
        assertThat(layout.getLineEnd(0)).isEqualTo(19)
        assertThat(layout.getLineWidth(0)).isEqualTo(205)
        assertThat(layout.getLineMax(0)).isEqualTo(205)
    }

    @Test
    fun testBreakOvershoot_preceding_LTR() {
        val text = "gggg ffff eeee aaaa"

        // Width constraint: 1000px
        // |gggg ffff eeee aaaa     : width: 205, max: 205
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 1000, false))).isEqualTo(0)
        assertThat(getDrawingHorizontalOffset(buildLayout(text, 1000, true))).isEqualTo(15)
        var layout = buildLayout(text, 1000)
        assertThat(layout.computeDrawingBoundingBox()).isEqualTo(RectF(-15f, 0f, 190f, 10f))
        assertThat(layout.lineCount).isEqualTo(1)
        assertThat(layout.getLineEnd(0)).isEqualTo(19)
        assertThat(layout.getLineWidth(0)).isEqualTo(205)
        assertThat(layout.getLineMax(0)).isEqualTo(205)
    }
}
