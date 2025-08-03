/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.text.cts;

import static com.google.common.truth.Truth.assertThat;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.text.PositionedGlyphs;
import android.graphics.text.TextRunShaper;
import android.text.Layout;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextDirectionHeuristic;
import android.text.TextDirectionHeuristics;
import android.text.TextPaint;
import android.text.TextShaper;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.TypefaceSpan;
import android.util.Pair;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class TextShaperTest {

    private List<Pair<PositionedGlyphs, TextPaint>> shapeText(CharSequence text, TextPaint paint) {
        ArrayList<Pair<PositionedGlyphs, TextPaint>> result = new ArrayList<>();
        TextShaper.shapeText(text, 0, text.length(), TextDirectionHeuristics.LTR, paint,
                (start, end, glyphs, p) -> {
                result.add(new Pair(glyphs, new TextPaint(p)));
            });
        return result;
    }

    @Test
    public void shapeText_noStyle() {
        // Setup
        TextPaint paint = new TextPaint();
        paint.setTextSize(100f);
        String text = "Hello, World.";

        // Act
        // If the text is not styled, the result should be equal to TextShaper.shapeTextRun.
        List<Pair<PositionedGlyphs, TextPaint>> glyphs = shapeText(text, paint);
        PositionedGlyphs singleStyleResult =
                TextRunShaper.shapeTextRun(text, 0, text.length(), 0, text.length(), 0f, 0f, false,
                        paint);

        // Asserts
        assertThat(glyphs.size()).isEqualTo(1);
        assertThat(glyphs.get(0).first).isEqualTo(singleStyleResult);
        assertThat(glyphs.get(0).second.getTextSize()).isEqualTo(100f);
    }

    @Test
    public void shapeText_multiStyle() {
        // Setup
        TextPaint paint = new TextPaint();
        paint.setTextSize(100f);

        SpannableString text = new SpannableString("Hello, World.");

        // Act
        // If the text is not styled, the result should be equal to TextShaper.shapeTextRun.
        text.setSpan(new AbsoluteSizeSpan(240), 0, 7, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        text.setSpan(new TypefaceSpan("serif"), 7, 13, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        List<Pair<PositionedGlyphs, TextPaint>> result = shapeText(text, paint);

        // Asserts
        assertThat(result.size()).isEqualTo(2);
        assertThat(result.get(0).first.getOffsetX()).isEqualTo(0f);
        assertThat(result.get(1).first.getOffsetX()).isGreaterThan(0f);
        // Styled text shaper doesn't support vertical layout, so Y origin is always 0
        assertThat(result.get(0).first.getOffsetY()).isEqualTo(0f);
        assertThat(result.get(1).first.getOffsetY()).isEqualTo(0f);


        // OEM may remove serif font, so expect only when there is a serif font.
        if (!Typeface.SERIF.equals(Typeface.DEFAULT)) {
            // The first character should be rendered by default font, Roboto. The last character
            // should be rendered by serif font.
            assertThat(result.get(0).first.getFont(0)).isNotEqualTo(result.get(1).first.getFont(0));
        }

        assertThat(result.get(0).second.getTextSize()).isEqualTo(240f);
        assertThat(result.get(1).second.getTextSize()).isEqualTo(100f);
    }

    public void assertSameDrawResult(CharSequence text, TextPaint paint,
            TextDirectionHeuristic textDir) {
        // For some reasons, StaticLayout breaks text even if we give the exact the same amount
        // of width. To avoid unexpected line breaking, adding 10px as a workaround.
        int width = (int) Math.ceil(Layout.getDesiredWidth(text, paint)) + 10;
        StaticLayout layout = StaticLayout.Builder.obtain(
                text, 0, text.length(), paint, width)
                .setTextDirection(textDir)
                .setIncludePad(false).build();
        int height = layout.getHeight();

        // Expected bitmap output
        Bitmap layoutResult = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas layoutCanvas = new Canvas(layoutResult);
        layout.draw(layoutCanvas);

        // actual bitmap output
        Bitmap glyphsResult = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas glyphsCanvas = new Canvas(glyphsResult);

        // StaticLayout uses the default font's ascent for baseline
        Paint.FontMetricsInt fmi = paint.getFontMetricsInt();
        // In the RTL paragraph, the shape result goes from right to left. StaticLayout
        // automatically moves the drawing offset to the right most position. We do it by manual.
        if (textDir.isRtl(text, 0, text.length())) {
            glyphsCanvas.translate(width, -fmi.ascent);
        } else {
            glyphsCanvas.translate(0, -fmi.ascent);
        }

        // Draws text.
        TextShaper.shapeText(text, 0, text.length(), textDir, paint,
                (start, end, glyphs, drawPaint) -> {
                    for (int i = 0; i < glyphs.glyphCount(); ++i) {
                        glyphsCanvas.drawGlyphs(
                                new int[] { glyphs.getGlyphId(i) },
                                0,
                                new float[] { glyphs.getGlyphX(i), glyphs.getGlyphY(i) },
                                0,
                                1,
                                glyphs.getFont(i),
                                drawPaint
                        );
                    }
                });
        assertThat(glyphsResult.sameAs(layoutResult)).isTrue();
    }

    @Test
    public void testDrawConsistencyNoStyle() {
        TextPaint paint = new TextPaint();
        paint.setTextSize(32f);
        paint.setColor(Color.BLUE);
        assertSameDrawResult("Hello, Android.", paint, TextDirectionHeuristics.LTR);
    }

    @Test
    public void testDrawConsistencyNoStyleMultiFont() {
        TextPaint paint = new TextPaint();
        paint.setTextSize(32f);
        paint.setColor(Color.BLUE);
        assertSameDrawResult("こんにちは、Android.", paint, TextDirectionHeuristics.LTR);
    }

    @Test
    public void testDrawConsistencyMultiStyle() {
        TextPaint paint = new TextPaint();
        paint.setTextSize(32f);
        paint.setColor(Color.BLUE);
        SpannableString text = new SpannableString("こんにちは Android.");
        text.setSpan(new AbsoluteSizeSpan(32), 3, 8, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        text.setSpan(new ForegroundColorSpan(Color.RED), 5, 10, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        text.setSpan(new TypefaceSpan("serif"), 6, 14, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        assertSameDrawResult(text, paint, TextDirectionHeuristics.LTR);
    }

    @Test
    public void testDrawConsistencyBidi() {
        TextPaint paint = new TextPaint();
        paint.setTextSize(32f);
        paint.setColor(Color.BLUE);
        assertSameDrawResult("مرحبا, Android.", paint, TextDirectionHeuristics.FIRSTSTRONG_LTR);
        assertSameDrawResult("مرحبا, Android.", paint, TextDirectionHeuristics.LTR);
        assertSameDrawResult("مرحبا, Android.", paint, TextDirectionHeuristics.RTL);
    }

    @Test
    public void testDrawConsistencyBidi2() {
        TextPaint paint = new TextPaint();
        paint.setTextSize(32f);
        paint.setColor(Color.BLUE);
        assertSameDrawResult("Hello, العالمية", paint, TextDirectionHeuristics.FIRSTSTRONG_LTR);
        assertSameDrawResult("Hello, العالمية", paint, TextDirectionHeuristics.LTR);
        assertSameDrawResult("Hello, العالمية", paint, TextDirectionHeuristics.RTL);
    }

    @Test
    public void testDrawConsistencyBidiMultiStyle() {
        TextPaint paint = new TextPaint();
        paint.setTextSize(32f);
        paint.setColor(Color.BLUE);
        SpannableString text = new SpannableString("مرحبا, Android.");
        text.setSpan(new AbsoluteSizeSpan(32), 3, 8, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        text.setSpan(new ForegroundColorSpan(Color.RED), 5, 10, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        text.setSpan(new TypefaceSpan("serif"), 6, 14, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        assertSameDrawResult(text, paint, TextDirectionHeuristics.FIRSTSTRONG_LTR);
        assertSameDrawResult(text, paint, TextDirectionHeuristics.LTR);
        assertSameDrawResult(text, paint, TextDirectionHeuristics.RTL);
    }

    @Test
    public void testDrawConsistencyBidi2MultiStyle() {
        TextPaint paint = new TextPaint();
        paint.setTextSize(32f);
        paint.setColor(Color.BLUE);
        SpannableString text = new SpannableString("Hello, العالمية");
        text.setSpan(new AbsoluteSizeSpan(32), 3, 8, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        text.setSpan(new ForegroundColorSpan(Color.RED), 5, 10, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        text.setSpan(new TypefaceSpan("serif"), 6, 14, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        assertSameDrawResult(text, paint, TextDirectionHeuristics.FIRSTSTRONG_LTR);
        assertSameDrawResult(text, paint, TextDirectionHeuristics.LTR);
        assertSameDrawResult(text, paint, TextDirectionHeuristics.RTL);
    }
}
