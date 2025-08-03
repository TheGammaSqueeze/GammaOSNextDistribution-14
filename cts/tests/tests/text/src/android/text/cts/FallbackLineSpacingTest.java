/*
 * Copyright (C) 2021 The Android Open Source Project
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

import android.content.res.AssetManager;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.fonts.Font;
import android.graphics.fonts.FontFamily;
import android.platform.test.annotations.Presubmit;
import android.text.TextPaint;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

/**
 * Tests StaticLayout vertical metrics behavior.
 */
@Presubmit
@SmallTest
@RunWith(AndroidJUnit4.class)
public class FallbackLineSpacingTest {

    @Test
    public void testFallbackSpacing() throws IOException {
        AssetManager am =
                InstrumentationRegistry.getInstrumentation().getTargetContext().getAssets();
        Typeface typeface = new Typeface.CustomFallbackBuilder(
                new FontFamily.Builder(
                        new Font.Builder(am, "fonts/LowGlyphFont.ttf").build()
                ).build()
        ).addCustomFallback(
                new FontFamily.Builder(
                        new Font.Builder(am, "fonts/TallGlyphFont.ttf").build()
                ).build()
        ).build();

        TextPaint p = new TextPaint();
        p.setTextSize(100);  // make 1 em = 100 pixels
        p.setTypeface(typeface);

        String text = "a\u1000";

        Paint.FontMetricsInt fmi = new Paint.FontMetricsInt();
        p.getFontMetricsInt(text, 0, text.length(), 0, text.length(), false, fmi);
        assertThat(fmi.top).isEqualTo(-300);
        assertThat(fmi.ascent).isEqualTo(-300);
        assertThat(fmi.descent).isEqualTo(300);
        assertThat(fmi.bottom).isEqualTo(300);

        p.getFontMetricsInt(text, 0, 1, 0, text.length(), false, fmi);
        assertThat(fmi.top).isEqualTo(-100);
        assertThat(fmi.ascent).isEqualTo(-100);
        assertThat(fmi.descent).isEqualTo(100);
        assertThat(fmi.bottom).isEqualTo(100);

        p.getFontMetricsInt(text, 1, 1, 0, text.length(), false, fmi);
        assertThat(fmi.top).isEqualTo(-300);
        assertThat(fmi.ascent).isEqualTo(-300);
        assertThat(fmi.descent).isEqualTo(300);
        assertThat(fmi.bottom).isEqualTo(300);
    }

    @Test
    public void testEmptyString() {
        Paint.FontMetricsInt fmi = new Paint.FontMetricsInt();
        Paint paint = new Paint();
        paint.setTextSize(100);  // make 1em = 100 pixels
        paint.getFontMetricsInt("a", 0, 0, 0, 0, false, fmi);

        Paint.FontMetricsInt noTextFmi = paint.getFontMetricsInt();

        assertThat(fmi).isEqualTo(noTextFmi);
    }

    @Test
    public void testEmptyCharArray() {
        Paint.FontMetricsInt fmi = new Paint.FontMetricsInt();
        Paint paint = new Paint();
        paint.setTextSize(100);  // make 1em = 100 pixels
        paint.getFontMetricsInt("a".toCharArray(), 0, 0, 0, 0, false, fmi);

        Paint.FontMetricsInt noTextFmi = paint.getFontMetricsInt();

        assertThat(fmi).isEqualTo(noTextFmi);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullTextArgument_String() {
        new Paint().getFontMetricsInt((String) null, 0, 0, 0, 0, false,
                new Paint.FontMetricsInt());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullTextArgument_CharArgument() {
        new Paint().getFontMetricsInt((char[]) null, 0, 0, 0, 0, false,
                new Paint.FontMetricsInt());
    }

    // start argument test cases
    @Test(expected = IllegalArgumentException.class)
    public void testSmallStartArgument_String() {
        new Paint().getFontMetricsInt("a", -1, 0, 0, 0, false,
                new Paint.FontMetricsInt());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSmallStartArgument_CharArray() {
        new Paint().getFontMetricsInt("a".toCharArray(), -1, 0, 0, 0, false,
                new Paint.FontMetricsInt());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testLargeStartArgument_String() {
        new Paint().getFontMetricsInt("a", 2, 0, 0, 0, false,
                new Paint.FontMetricsInt());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testLargeStartArgument_CharArray() {
        new Paint().getFontMetricsInt("a".toCharArray(), 2, 0, 0, 0, false,
                new Paint.FontMetricsInt());
    }

    // count argument test cases
    @Test(expected = IllegalArgumentException.class)
    public void testSmallCountArgument_String() {
        new Paint().getFontMetricsInt("a", 0, -1, 0, 0, false,
                new Paint.FontMetricsInt());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSmalCountArgument_CharArray() {
        new Paint().getFontMetricsInt("a".toCharArray(), 0, -1, 0, 0, false,
                new Paint.FontMetricsInt());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testLargeCountArgument_String() {
        new Paint().getFontMetricsInt("a", 0, 2, 0, 0, false,
                new Paint.FontMetricsInt());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testLargeCountArgument_CharArray() {
        new Paint().getFontMetricsInt("a".toCharArray(), 0, 2, 0, 0, false,
                new Paint.FontMetricsInt());
    }

    // ctxStart argument test cases
    @Test(expected = IllegalArgumentException.class)
    public void testSmallCtxStartArgument_String() {
        new Paint().getFontMetricsInt("a", 0, 1, -1, 0, false,
                new Paint.FontMetricsInt());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSmallCtxStartArgument_CharArray() {
        new Paint().getFontMetricsInt("a".toCharArray(), 0, 1, -1, 0, false,
                new Paint.FontMetricsInt());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testLargeCtxStartArgument_String() {
        new Paint().getFontMetricsInt("a", 0, 1, 2, 0, false,
                new Paint.FontMetricsInt());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testLargeCtxStartArgument_CharArray() {
        new Paint().getFontMetricsInt("a".toCharArray(), 0, 1, 2, 0, false,
                new Paint.FontMetricsInt());
    }

    // count argument test cases
    @Test(expected = IllegalArgumentException.class)
    public void testSmallCtxCountArgument_String() {
        new Paint().getFontMetricsInt("a", 0, 1, 0, -1, false,
                new Paint.FontMetricsInt());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSmalCtxCountArgument_CharArray() {
        new Paint().getFontMetricsInt("a".toCharArray(), 0, 1, 0, -1, false,
                new Paint.FontMetricsInt());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testLargeCtxCountArgument_String() {
        new Paint().getFontMetricsInt("a", 0, 1, 0, 2, false,
                new Paint.FontMetricsInt());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testLargeCtxCountArgument_CharArray() {
        new Paint().getFontMetricsInt("a".toCharArray(), 0, 1, 0, 2, false,
                new Paint.FontMetricsInt());
    }

    // count argument test cases
    @Test(expected = IllegalArgumentException.class)
    public void testSmallNullOutArgument_String() {
        new Paint().getFontMetricsInt("a", 0, 1, 0, 1, false, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSmalNullOutArgument_CharArray() {
        new Paint().getFontMetricsInt("a".toCharArray(), 0, 1, 0, 1, false, null);
    }

}
