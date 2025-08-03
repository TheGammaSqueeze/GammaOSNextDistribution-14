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
import android.graphics.Typeface;
import android.graphics.fonts.Font;
import android.graphics.fonts.FontFamily;
import android.graphics.text.PositionedGlyphs;
import android.graphics.text.TextRunShaper;
import android.platform.test.annotations.Presubmit;
import android.text.StaticLayout;
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
public class StaticLayoutFallbackLineSpacingTest {

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

        PositionedGlyphs glyphs = TextRunShaper.shapeTextRun("a", 0, 1, 0, 1, 0, 0, false, p);
        // LowGlyphFont has -1 em ascent and 1 em descent.
        assertThat(glyphs.getAscent()).isEqualTo(-100);
        assertThat(glyphs.getDescent()).isEqualTo(100);

        glyphs = TextRunShaper.shapeTextRun("\u1000", 0, 1, 0, 1, 0, 0, false, p);
        // TallGlyphFont has -3 em ascent and 3 em descent.
        assertThat(glyphs.getAscent()).isEqualTo(-300);
        assertThat(glyphs.getDescent()).isEqualTo(300);

        String text = "a\u1000";
        glyphs = TextRunShaper.shapeTextRun(
                text, 0, text.length(), 0, text.length(), 0, 0, false, p);

        // TallGlyphFont has -3em ascent and 3em descent.
        assertThat(glyphs.getAscent()).isEqualTo(-300);
        assertThat(glyphs.getDescent()).isEqualTo(300);

        StaticLayout layout = StaticLayout.Builder.obtain(
                text, 0, text.length(), p, Integer.MAX_VALUE)
                .setUseLineSpacingFromFallbacks(true)
                .setIncludePad(true)
                .build();
        assertThat(layout.getLineBottom(0)).isEqualTo(600);
    }

}
