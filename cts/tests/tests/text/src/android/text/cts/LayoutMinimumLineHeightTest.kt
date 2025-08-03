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

import android.graphics.Paint
import android.os.LocaleList
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.text.DynamicLayout
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.text.flags.Flags.FLAG_FIX_LINE_HEIGHT_FOR_LOCALE
import com.google.common.truth.Truth.assertThat
import kotlin.math.ceil
import kotlin.math.floor
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class LayoutMinimumLineHeightTest {

    @Rule
    @JvmField
    val mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    private val paint = TextPaint().apply {
        textSize = 100f // Make 1em = 100px
        textLocales = LocaleList.forLanguageTags("en-US") // fix the locale
    }

    private fun getMetrics(text: String, paint: TextPaint): Pair<Int, Int> {
        val l = StaticLayout.Builder.obtain(text, 0, text.length, paint, Int.MAX_VALUE)
                .setIncludePad(false)
                .build()
        return Pair(l.getLineAscent(0), l.getLineDescent(0))
    }

    @RequiresFlagsEnabled(FLAG_FIX_LINE_HEIGHT_FOR_LOCALE)
    @Test
    fun layout_SetGetMinimumFontMetrics() {
        val text = "Hello, World."
        val metrics = Paint.FontMetrics()
        metrics.ascent = 1f
        metrics.descent = 2f
        val layout = Layout.Builder(text, 0, text.length, paint, 1024)
                .setMinimumFontMetrics(metrics)
                .setFontPaddingIncluded(false) // For not making ascent to top
                .build()

        assertThat(layout.minimumFontMetrics).isEqualTo(metrics)
    }

    @RequiresFlagsEnabled(FLAG_FIX_LINE_HEIGHT_FOR_LOCALE)
    @Test
    fun StaticLayout_SetGetMinimumFontMetrics() {
        val text = "Hello, World."
        val metrics = Paint.FontMetrics()
        metrics.ascent = 1f
        metrics.descent = 2f
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, 1024)
                .setMinimumFontMetrics(metrics)
                .setIncludePad(false) // For not making ascent to top
                .build()

        assertThat(layout.minimumFontMetrics).isEqualTo(metrics)
    }

    @RequiresFlagsEnabled(FLAG_FIX_LINE_HEIGHT_FOR_LOCALE)
    @Test
    fun DynamicLayout_SetGetMinimumFontMetrics() {
        val text = "Hello, World."
        val metrics = Paint.FontMetrics()
        metrics.ascent = 1f
        metrics.descent = 2f
        val layout = DynamicLayout.Builder.obtain(text, paint, 1024)
                .setMinimumFontMetrics(metrics)
                .setIncludePad(false) // For not making ascent to top
                .build()

        assertThat(layout.minimumFontMetrics).isEqualTo(metrics)
    }

    @RequiresFlagsEnabled(FLAG_FIX_LINE_HEIGHT_FOR_LOCALE)
    @Test
    fun Layout_expandToReserveMinimumFontMetrics() {
        val text = "Hello, World."
        val originalMetrics = getMetrics(text, paint)
        // Set the minimum font metrics as two times larger to the original one.
        val requestMetrics = Paint.FontMetrics().apply {
            // Intentionally not modifying the top/bottom value for testing adjustment.
            ascent = originalMetrics.first * 2f
            descent = originalMetrics.second * 2f
        }
        val layout = Layout.Builder(text, 0, text.length, paint, 1024)
                .setMinimumFontMetrics(requestMetrics)
                .setFontPaddingIncluded(false) // For not making ascent to top
                .build()

        assertThat(layout.getLineAscent(0)).isEqualTo(floor(requestMetrics.ascent).toInt())
        assertThat(layout.getLineDescent(0)).isEqualTo(ceil(requestMetrics.descent).toInt())
    }

    @RequiresFlagsEnabled(FLAG_FIX_LINE_HEIGHT_FOR_LOCALE)
    @Test
    fun StaticLayout_expandToReserveMinimumFontMetrics() {
        val text = "Hello, World."
        val originalMetrics = getMetrics(text, paint)
        // Set the minimum font metrics as two times larger to the original one.
        val requestMetrics = Paint.FontMetrics().apply {
            // Intentionally not modifying the top/bottom value for testing adjustment.
            ascent = originalMetrics.first * 2f
            descent = originalMetrics.second * 2f
        }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, 1024)
                .setMinimumFontMetrics(requestMetrics)
                .setIncludePad(false) // For not making ascent to top
                .build()

        assertThat(layout.getLineAscent(0)).isEqualTo(floor(requestMetrics.ascent).toInt())
        assertThat(layout.getLineDescent(0)).isEqualTo(ceil(requestMetrics.descent).toInt())
    }

    @RequiresFlagsEnabled(FLAG_FIX_LINE_HEIGHT_FOR_LOCALE)
    @Test
    fun DynamicLayout_expandToReserveMinimumFontMetrics() {
        val text = "Hello, World."
        val originalMetrics = getMetrics(text, paint)
        // Set the minimum font metrics as two times larger to the original one.
        val requestMetrics = Paint.FontMetrics().apply {
            // Intentionally not modifying the top/bottom value for testing adjustment.
            ascent = originalMetrics.first * 2f
            descent = originalMetrics.second * 2f
        }
        val layout = DynamicLayout.Builder.obtain(text, paint, 1024)
                .setMinimumFontMetrics(requestMetrics)
                .setIncludePad(false) // For not making ascent to top
                .build()

        assertThat(layout.getLineAscent(0)).isEqualTo(floor(requestMetrics.ascent).toInt())
        assertThat(layout.getLineDescent(0)).isEqualTo(ceil(requestMetrics.descent).toInt())
    }

    @RequiresFlagsEnabled(FLAG_FIX_LINE_HEIGHT_FOR_LOCALE)
    @Test
    fun Layout_useActualFont() {
        val text = "Hello, World."
        val originalMetrics = getMetrics(text, paint)
        // Set the minimum font metrics as 0.5 times larger to the original one.
        val requestMetrics = Paint.FontMetrics().apply {
            // Intentionally not modifying the top/bottom value for testing adjustment.
            ascent = originalMetrics.first / 2f
            descent = originalMetrics.second / 2f
        }
        val layout = Layout.Builder(text, 0, text.length, paint, 1024)
                .setMinimumFontMetrics(requestMetrics)
                .setFontPaddingIncluded(false) // For not making ascent to top
                .build()

        assertThat(layout.getLineAscent(0)).isEqualTo(originalMetrics.first)
        assertThat(layout.getLineDescent(0)).isEqualTo(originalMetrics.second)
    }

    @RequiresFlagsEnabled(FLAG_FIX_LINE_HEIGHT_FOR_LOCALE)
    @Test
    fun StaticLayout_useActualFont() {
        val text = "Hello, World."
        val originalMetrics = getMetrics(text, paint)
        // Set the minimum font metrics as 0.5 times larger to the original one.
        val requestMetrics = Paint.FontMetrics().apply {
            // Intentionally not modifying the top/bottom value for testing adjustment.
            ascent = originalMetrics.first / 2f
            descent = originalMetrics.second / 2f
        }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, 1024)
                .setMinimumFontMetrics(requestMetrics)
                .setIncludePad(false) // For not making ascent to top
                .build()

        assertThat(layout.getLineAscent(0)).isEqualTo(originalMetrics.first)
        assertThat(layout.getLineDescent(0)).isEqualTo(originalMetrics.second)
    }

    @RequiresFlagsEnabled(FLAG_FIX_LINE_HEIGHT_FOR_LOCALE)
    @Test
    fun DynamicLayout_useActualFont() {
        val text = "Hello, World."
        val originalMetrics = getMetrics(text, paint)
        // Set the minimum font metrics as 0.5 times larger to the original one.
        val requestMetrics = Paint.FontMetrics().apply {
            // Intentionally not modifying the top/bottom value for testing adjustment.
            ascent = originalMetrics.first / 2f
            descent = originalMetrics.second / 2f
        }
        val layout = DynamicLayout.Builder.obtain(text, paint, 1024)
                .setMinimumFontMetrics(requestMetrics)
                .setIncludePad(false) // For not making ascent to top
                .build()

        assertThat(layout.getLineAscent(0)).isEqualTo(originalMetrics.first)
        assertThat(layout.getLineDescent(0)).isEqualTo(originalMetrics.second)
    }
}
