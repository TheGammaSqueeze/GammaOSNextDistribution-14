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

import android.graphics.Typeface
import android.graphics.text.LineBreaker
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.LineBreakConfigSpan
import androidx.test.InstrumentationRegistry
import androidx.test.filters.SmallTest
import androidx.test.runner.AndroidJUnit4
import com.android.text.flags.Flags
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class StaticLayoutLineBreakingNoBreakSpanTest {

    @Rule
    @JvmField
    val mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    private val PAINT = TextPaint().apply {
        val context = InstrumentationRegistry.getTargetContext()
        typeface = Typeface.createFromAsset(context.assets, "fonts/AllOneEmASCIIFont.ttf")
        textSize = 10f // make 1em = 10px
    }

    fun buildLayout(text: CharSequence, width: Int, strategy: Int): Layout {
        return StaticLayout.Builder.obtain(text, 0, text.length, PAINT, width)
                .setBreakStrategy(strategy)
                .setHyphenationFrequency(LineBreaker.HYPHENATION_FREQUENCY_NONE)
                .build()
    }

    fun assertLineBreak(layout: Layout, vararg lines: String) {
        assertThat(layout.lineCount).isEqualTo(lines.size)
        var pos = 0
        for (i in 0 until layout.lineCount) {
            val expectLineRange = Pair(pos, (pos + lines[i].length))
            val actualLineRange = Pair(layout.getLineStart(i), layout.getLineEnd(i))
            assertThat(actualLineRange).isEqualTo(expectLineRange)
            pos += lines[i].length
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_NO_BREAK_NO_HYPHENATION_SPAN)
    @Test
    fun testLineBreakPreventLineBreak() {
        // Checking precondition of the line break.
        var layout = buildLayout("This is an example.", 80, LineBreaker.BREAK_STRATEGY_HIGH_QUALITY)
        assertLineBreak(layout, "This ", "is an ", "example.")

        // Wrapping "This is" with NoBreakSpan, so that the line breaker prevents line break inside
        // the given span.
        layout = buildLayout(SpannableString("This is an example.").apply {
            setSpan(LineBreakConfigSpan.createNoBreakSpan(), 0, 7, Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
        }, 80, LineBreaker.BREAK_STRATEGY_HIGH_QUALITY)
        assertLineBreak(layout, "This is ", "an ", "example.")
    }
}
