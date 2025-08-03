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
package android.widget.cts

import android.graphics.Paint
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.view.View.MeasureSpec
import android.widget.EditText
import android.widget.TextView
import androidx.test.InstrumentationRegistry
import androidx.test.filters.SmallTest
import androidx.test.runner.AndroidJUnit4
import com.android.text.flags.Flags.FLAG_FIX_LINE_HEIGHT_FOR_LOCALE
import com.google.common.truth.Truth.assertThat
import java.util.Locale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test [TextView].
 */
@SmallTest
@RunWith(AndroidJUnit4::class)
class TextViewMinimumFontMetrics {

    private val context = InstrumentationRegistry.getInstrumentation().getTargetContext()

    @JvmField
    @Rule
    val mCheckFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @Test
    @RequiresFlagsEnabled(FLAG_FIX_LINE_HEIGHT_FOR_LOCALE)
    fun testMinimumFontHeight_NullByDefault() {
        val textView = TextView(context)
        assertThat(textView.minimumFontMetrics).isNull() // Null by default
    }

    @Test
    @RequiresFlagsEnabled(FLAG_FIX_LINE_HEIGHT_FOR_LOCALE)
    fun testMinimumFontHeight_SetAndGet() {
        val textView = TextView(context)
        val fm = Paint.FontMetrics()
        fm.ascent = 1f
        fm.descent = 2f
        textView.minimumFontMetrics = fm
        assertThat(textView.minimumFontMetrics).isEqualTo(fm)
    }

    @Test
    @RequiresFlagsEnabled(FLAG_FIX_LINE_HEIGHT_FOR_LOCALE)
    fun testMinimumFontHeight_SetToLayout() {
        val textView = TextView(context)
        val fm = Paint.FontMetrics()
        fm.ascent = 1f
        fm.descent = 2f
        textView.minimumFontMetrics = fm

        textView.measure(
            MeasureSpec.makeMeasureSpec(1024, MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(1024, MeasureSpec.AT_MOST)
        )

        assertThat(textView.layout.minimumFontMetrics).isEqualTo(fm)
    }

    @Test
    @RequiresFlagsEnabled(FLAG_FIX_LINE_HEIGHT_FOR_LOCALE)
    fun testIsUseLocalePreferredLineHeightForMinimum_SetGet() {
        val textView = TextView(context)

        // False by default
        assertThat(textView.isLocalePreferredLineHeightForMinimumUsed).isFalse()
        textView.isLocalePreferredLineHeightForMinimumUsed = true
        assertThat(textView.isLocalePreferredLineHeightForMinimumUsed).isTrue()
        textView.isLocalePreferredLineHeightForMinimumUsed = false
        assertThat(textView.isLocalePreferredLineHeightForMinimumUsed).isFalse()
    }

    @Test
    @RequiresFlagsEnabled(FLAG_FIX_LINE_HEIGHT_FOR_LOCALE)
    fun testIsUseLocalePreferredLineHeightForMinimum_ForLocale_EmptyLine() {
        val textView = EditText(context).apply {
            textLocale = Locale.forLanguageTag("ja")
        }

        textView.setLocalePreferredLineHeightForMinimumUsed(true)
        textView.includeFontPadding = false // make line height = descent - ascent.

        textView.measure(
                MeasureSpec.makeMeasureSpec(1024, MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(1024, MeasureSpec.AT_MOST)
        )

        val fm = Paint.FontMetricsInt()
        textView.paint.getFontMetricsIntForLocale(fm)

        assertThat(textView.layout.height).isEqualTo(fm.descent - fm.ascent)
    }

    @Test
    @RequiresFlagsEnabled(FLAG_FIX_LINE_HEIGHT_FOR_LOCALE)
    fun testIsUseLocalePreferredLineHeightForMinimum_ForLocale() {
        val textView = EditText(context).apply {
            textLocale = Locale.forLanguageTag("ja")
            setText("abcde")
        }

        textView.setLocalePreferredLineHeightForMinimumUsed(true)
        textView.includeFontPadding = false // make line height = descent - ascent.

        textView.measure(
                MeasureSpec.makeMeasureSpec(1024, MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(1024, MeasureSpec.AT_MOST)
        )

        val fm = Paint.FontMetricsInt()
        textView.paint.getFontMetricsIntForLocale(fm)

        assertThat(textView.layout.height).isEqualTo(fm.descent - fm.ascent)
    }
}
