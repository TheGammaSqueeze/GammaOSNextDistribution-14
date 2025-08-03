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

package android.text.cts;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.graphics.Typeface;
import android.text.BoringLayout;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class LayoutFillCharacterBoundsTest {

    private static final TextPaint sTextPaint = new TextPaint();

    static {
        // The textrunadvances.ttf font supports following characters
        // - U+0061 (a): The glyph has 3em width.
        // - U+0062..U+0065 (b..e): The glyph has 1em width.
        // - U+1F600 (GRINNING FACE): The glyph has 3em width.
        // - U+05D0 (HEBREW LETTER ALEF): The glyph has 1 em width.
        // - U+05D1 (HEBREW LETTER BET): The glyph has 3 em width.
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        sTextPaint.setTypeface(Typeface.createFromAsset(context.getAssets(),
                "fonts/textrunadvances.ttf"));
        sTextPaint.setTextSize(10.0f);  // Make 1em == 10px.
    }

    private static StaticLayout getStaticLayout(CharSequence text, int width) {
        return StaticLayout.Builder.obtain(text, 0, text.length(), sTextPaint, width)
                .setIncludePad(false)
                .build();
    }

    @Test
    public void staticLayout_fillCharacterBounds_LTR() {
        final String text = "aabb";
        final StaticLayout staticLayout = getStaticLayout(text, 200);
        final float[] characterBounds = new float[4 * text.length()];
        staticLayout.fillCharacterBounds(0, text.length(), characterBounds, 0);
        final int top = staticLayout.getLineTop(0);
        final int bottom = staticLayout.getLineBottom(0);

        assertThat(characterBounds).isEqualTo(new float[] {
                0.0f, top, 30.0f, bottom,
                30.0f, top, 60.0f, bottom,
                60.0f, top, 70.0f, bottom,
                70.0f, top, 80.0f, bottom});
    }

    @Test
    public void staticLayout_fillCharacterBounds_LTR_multiline() {
        final String text = "abab";
        final StaticLayout staticLayout = getStaticLayout(text, 50);
        final float[]characterBounds = new float[4 * text.length()];
        staticLayout.fillCharacterBounds(0, text.length(), characterBounds, 0);
        final int firstLineTop = staticLayout.getLineTop(0);
        final int firstLineBottom = staticLayout.getLineBottom(0);
        final int secondLineTop = staticLayout.getLineTop(1);
        final int secondLineBottom = staticLayout.getLineBottom(1);

        assertThat(characterBounds).isEqualTo(new float[] {
                0.0f, firstLineTop, 30.0f, firstLineBottom,
                30.0f, firstLineTop, 40.0f, firstLineBottom,
                0.0f, secondLineTop, 30.0f, secondLineBottom,
                30.0f, secondLineTop, 40.0f, secondLineBottom});
    }

    @Test
    public void staticLayout_fillCharacterBounds_RTL() {
        final String text = "\u05D0\u05D0\u05D1\u05D1";
        final StaticLayout staticLayout = getStaticLayout(text, 200);
        final float[]characterBounds = new float[4 * text.length()];
        staticLayout.fillCharacterBounds(0, text.length(), characterBounds, 0);
        final int top = staticLayout.getLineTop(0);
        final int bottom = staticLayout.getLineBottom(0);

        assertThat(characterBounds).isEqualTo(new float[] {
                190.0f, top, 200.0f, bottom,
                180.0f, top, 190.0f, bottom,
                150.0f, top, 180.0f, bottom,
                120.0f, top, 150.0f, bottom});
    }

    @Test
    public void staticLayout_fillCharacterBounds_RTL_multiline() {
        final String text = "\u05D1\u05D0\u05D1\u05D0";
        final StaticLayout staticLayout = getStaticLayout(text, 50);
        final float[]characterBounds = new float[4 * text.length()];
        staticLayout.fillCharacterBounds(0, text.length(), characterBounds, 0);
        final int firstLineTop = staticLayout.getLineTop(0);
        final int firstLineBottom = staticLayout.getLineBottom(0);
        final int secondLineTop = staticLayout.getLineTop(1);
        final int secondLineBottom = staticLayout.getLineBottom(1);

        assertThat(characterBounds).isEqualTo(new float[] {
                20.0f, firstLineTop, 50.0f, firstLineBottom,
                10.0f, firstLineTop, 20.0f, firstLineBottom,
                20.0f, secondLineTop, 50.0f, secondLineBottom,
                10.0f, secondLineTop, 20.0f, secondLineBottom});
    }

    @Test
    public void boringLayout_fillCharacterBounds() {
        final String text = "abab";
        final BoringLayout.Metrics metrics = new BoringLayout.Metrics();
        BoringLayout.isBoring(text, sTextPaint, metrics);
        final BoringLayout boringLayout = BoringLayout.make(text, sTextPaint, 100,
                Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, metrics, false);
        final float[] characterBounds = new float[4 * text.length()];
        boringLayout.fillCharacterBounds(0, text.length(), characterBounds, 0);
        final int top = boringLayout.getLineTop(0);
        final int bottom = boringLayout.getLineBottom(0);

        assertThat(characterBounds).isEqualTo(new float[] {
                0.0f, top, 30.0f, bottom,
                30.0f, top, 40.0f, bottom,
                40.0f, top, 70.0f, bottom,
                70.0f, top, 80.0f, bottom});
    }
}
