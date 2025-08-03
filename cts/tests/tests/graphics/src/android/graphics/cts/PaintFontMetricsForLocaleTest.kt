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
package android.graphics.cts

import android.graphics.Paint
import android.graphics.Paint.FontMetrics
import android.graphics.Paint.FontMetricsInt
import android.graphics.text.TextRunShaper
import android.os.LocaleList
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.filters.SmallTest
import androidx.test.runner.AndroidJUnit4
import com.android.text.flags.Flags
import com.google.common.truth.Truth.assertThat
import kotlin.math.roundToInt
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class PaintFontMetricsForLocaleTest {

    @Rule
    @JvmField
    val mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    companion object {
        val PAINT = Paint().apply {
            textSize = 100f // make 1em = 100px
        }

        val JP_ASCENT = TextRunShaper.shapeTextRun("あ", 0, 1, 0, 1, 0f, 0f, false, PAINT).ascent
        val JP_DESCENT = TextRunShaper.shapeTextRun("あ", 0, 1, 0, 1, 0f, 0f, false, PAINT).descent
    }

    @RequiresFlagsEnabled(Flags.FLAG_FIX_LINE_HEIGHT_FOR_LOCALE)
    @Test
    fun testExtentForLocale() {
        val paint = Paint(PAINT).apply {
            textLocales = LocaleList.forLanguageTags("ja-JP")
        }
        val metrics = FontMetrics()
        paint.getFontMetricsForLocale(metrics)
        assertThat(metrics.ascent).isEqualTo(JP_ASCENT)
        assertThat(metrics.descent).isEqualTo(JP_DESCENT)
    }

    @RequiresFlagsEnabled(Flags.FLAG_FIX_LINE_HEIGHT_FOR_LOCALE)
    @Test
    fun testExtentIntForLocale() {
        val paint = Paint(PAINT).apply {
            textLocales = LocaleList.forLanguageTags("ja-JP")
        }
        val metrics = FontMetricsInt()
        paint.getFontMetricsIntForLocale(metrics)
        assertThat(metrics.ascent).isEqualTo(JP_ASCENT.roundToInt())
        assertThat(metrics.descent).isEqualTo(JP_DESCENT.roundToInt())
    }

    @RequiresFlagsEnabled(Flags.FLAG_FIX_LINE_HEIGHT_FOR_LOCALE)
    @Test
    fun testExtentForRoboto() {
        val paint = Paint(PAINT).apply {
            textLocales = LocaleList.forLanguageTags("en-US")
        }

        val expectedMetrics = FontMetrics()
        paint.getFontMetrics(expectedMetrics)

        val metrics = FontMetrics()
        paint.getFontMetricsForLocale(metrics)

        assertThat(metrics.ascent).isEqualTo(expectedMetrics.ascent)
        assertThat(metrics.descent).isEqualTo(expectedMetrics.descent)
    }

    @RequiresFlagsEnabled(Flags.FLAG_FIX_LINE_HEIGHT_FOR_LOCALE)
    @Test
    fun testExtentIntForRoboto() {
        val paint = Paint(PAINT).apply {
            textLocales = LocaleList.forLanguageTags("en-US")
        }

        val expectedMetrics = FontMetricsInt()
        paint.getFontMetricsInt(expectedMetrics)

        val metrics = FontMetricsInt()
        paint.getFontMetricsIntForLocale(metrics)

        assertThat(metrics.ascent).isEqualTo(expectedMetrics.ascent)
        assertThat(metrics.descent).isEqualTo(expectedMetrics.descent)
    }
}
