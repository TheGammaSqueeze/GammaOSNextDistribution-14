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
import android.platform.test.annotations.Presubmit;
import android.text.BoringLayout;
import android.text.Layout;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextDirectionHeuristics;
import android.text.TextPaint;
import android.text.style.TypefaceSpan;

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
public class BoringLayoutFallbackLineSpacingTest {

    @Test
    public void testFallbackSpacing_Metrics() throws IOException {
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
        BoringLayout.Metrics boring = BoringLayout.isBoring(
                text, p, TextDirectionHeuristics.LTR, false /* useFallbackLineSpacing */, null);

        BoringLayout.Metrics fallbackLineSpacingBoring = BoringLayout.isBoring(
                text, p, TextDirectionHeuristics.LTR, true /* useFallbackLineSpacing */, null);


        // LowGlyphFont has -1 em ascent and 1 em descent. TallGlyphFont has -3em ascent and 3em
        // descent.
        assertThat(boring.ascent).isEqualTo(-100);
        assertThat(boring.descent).isEqualTo(100);
        assertThat(fallbackLineSpacingBoring.ascent).isEqualTo(-300);
        assertThat(fallbackLineSpacingBoring.descent).isEqualTo(300);
    }

    @Test
    public void testFallbackSpacing_Layout() throws IOException {
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
        BoringLayout.Metrics metrics = BoringLayout.isBoring(text, p, TextDirectionHeuristics.LTR,
                false /* useFallbackLineSpacing */, null);
        BoringLayout layout = BoringLayout.make(
                text, p,
                Integer.MAX_VALUE /* outer width */,
                Layout.Alignment.ALIGN_NORMAL,
                1.0f /* spacing mult */,
                0.0f /* spacing add */,
                metrics,
                false /* includePad */);
        BoringLayout includePadLayout = BoringLayout.make(
                text, p,
                Integer.MAX_VALUE /* outer width */,
                Layout.Alignment.ALIGN_NORMAL,
                1.0f /* spacing mult */,
                0.0f /* spacing add */,
                metrics,
                true /* includePad */);

        BoringLayout.Metrics fallbackMetrics = BoringLayout.isBoring(text, p,
                TextDirectionHeuristics.LTR, true /* useFallbackLineSpacing */, null);
        BoringLayout fallbackLayout = BoringLayout.make(
                text, p,
                Integer.MAX_VALUE /* outer width */,
                Layout.Alignment.ALIGN_NORMAL,
                1.0f /* spacing mult */,
                0.0f /* spacing add */,
                fallbackMetrics,
                false /* includePad */);
        BoringLayout includePadFallbackLayout = BoringLayout.make(
                text, p,
                Integer.MAX_VALUE /* outer width */,
                Layout.Alignment.ALIGN_NORMAL,
                1.0f /* spacing mult */,
                0.0f /* spacing add */,
                fallbackMetrics,
                true /* includePad */);


        // LowGlyphFont has -1 em ascent and 1 em descent. TallGlyphFont has -3em ascent and 3em
        // descent.
        assertThat(layout.getLineBottom(0)).isEqualTo(200);
        assertThat(includePadLayout.getLineBottom(0)).isEqualTo(200);
        assertThat(fallbackLayout.getLineBottom(0)).isEqualTo(600);
        assertThat(includePadFallbackLayout.getLineBottom(0)).isEqualTo(600);
    }

    @Test
    public void testReplacementSpans() {
        SpannableString ss = new SpannableString("Hello, World.");
        ss.setSpan(new TypefaceSpan(Typeface.SERIF), 5, 10, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        TextPaint paint = new TextPaint();

        BoringLayout.isBoring(ss, paint, TextDirectionHeuristics.FIRSTSTRONG_LTR, true, null);
    }

}
