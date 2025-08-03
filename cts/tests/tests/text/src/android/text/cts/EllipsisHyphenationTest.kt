/*
 * Copyright (C) 2022 The Android Open Source Project
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
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.any
import org.mockito.Mockito.anyFloat
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations

@MediumTest
@RunWith(AndroidJUnit4::class)
class EllipsisHyphenationTest {

    @Mock
    private lateinit var mockCanvas: Canvas

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
    }

    @Test
    fun simpleString() {
        val assets = InstrumentationRegistry.getInstrumentation().getContext().assets
        val paint = TextPaint().apply {
            textSize = 100f
            typeface = Typeface.createFromAsset(assets, "fonts/AllOneEmASCIIFont.ttf")
        }

        // Answering getClipBounds for providing rendering area.
        `when`(mockCanvas.getClipBounds(any()))
            .thenAnswer { input ->
                val rect = input.getArgument<Rect>(0)
                rect.left = 0
                rect.top = 0
                rect.right = 1024
                rect.bottom = 1024

                true
            }

        val startHyphenEdits = mutableListOf<Int>()
        val endHyphenEdits = mutableListOf<Int>()
        `when`(
            mockCanvas.drawText(
                any<CharSequence>(),
                anyInt(),
                anyInt(),
                anyFloat(),
                anyFloat(),
                any()
            )
        ).thenAnswer { input ->
                val paint = input.getArgument<Paint>(5)
                startHyphenEdits.add(paint.startHyphenEdit)
                endHyphenEdits.add(paint.endHyphenEdit)
            }

        val string = "Hyphenation "
        val w = 600 // Give 6-chars width for line.
        val layout = StaticLayout.Builder.obtain(string, 0, string.length, paint, w)
            .setMaxLines(2)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_FULL)
            .setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()

        layout.draw(mockCanvas)

        // The line break without ellipsis will be like as follows
        // |Hy-   |
        // |phen- |
        // |ation |
        //
        // Because of the 2nd line truncation, the end hyphen should be no edit.
        assertThat(startHyphenEdits).isEqualTo(
            listOf(Paint.START_HYPHEN_EDIT_NO_EDIT, Paint.START_HYPHEN_EDIT_NO_EDIT)
        )
        assertThat(endHyphenEdits).isEqualTo(
            listOf(Paint.END_HYPHEN_EDIT_INSERT_HYPHEN, Paint.END_HYPHEN_EDIT_NO_EDIT)
        )
    }
}
