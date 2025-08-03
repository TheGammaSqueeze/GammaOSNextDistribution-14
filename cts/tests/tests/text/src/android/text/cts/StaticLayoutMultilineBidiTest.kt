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

import android.graphics.Bitmap
import android.graphics.Canvas
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.text.flags.Flags
import com.google.common.truth.Truth.assertThat
import kotlin.math.ceil
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class StaticLayoutMultilineBidiTest {
    @Rule
    @JvmField
    val mCheckFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    private val paint = TextPaint().apply {
        textSize = 100f
    }

    @RequiresFlagsEnabled(Flags.FLAG_ICU_BIDI_MIGRATION)
    @Test
    fun testConsistentBidiDraw() {
        // This test case verifies that the lines drawn by single StaticLayout is consistent with
        // drawing each lines by multiple StaticLayout.
        // This verifies a bug (317144801) that draws lines inconsistently at the first line and
        // others.

        // A test string that tests the BiDi text in the 2nd line.
        val text = "a a a a a a a a a a a a \u202B@\u05E91\u202C"

        // Set 75% of desired width to make text two lines.
        val width = ceil(Layout.getDesiredWidth(text, paint) / 1.5).toInt()

        // Make actual (draw two lines with single StaticLayout) bitmap.
        val actualLayout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                .setIncludePad(false)
                .build()
        val actualBitmap = Bitmap.createBitmap(width, actualLayout.height, Bitmap.Config.ARGB_8888)
        val actualCanvas = Canvas(actualBitmap)
        actualLayout.draw(actualCanvas)

        // Make expected (draw two lines with two StaticLayouts) bitmap.
        val expectBitmap = Bitmap.createBitmap(width, actualLayout.height, Bitmap.Config.ARGB_8888)
        val expectCanvas = Canvas(expectBitmap)

        for (i in 0 until actualLayout.lineCount) {
            expectCanvas.save()
            try {
                val lineLayout = StaticLayout.Builder.obtain(
                        text,
                    actualLayout.getLineStart(i),
                    actualLayout.getLineEnd(i),
                    paint,
                    width
                ).setIncludePad(false).build()

                expectCanvas.translate(0f, actualLayout.getLineTop(i).toFloat())
                lineLayout.draw(expectCanvas)
            } finally {
                expectCanvas.restore()
            }
        }

        assertThat(actualBitmap.sameAs(expectBitmap)).isTrue()
    }
}
