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

import static org.junit.Assert.assertArrayEquals;

import android.content.res.AssetManager;
import android.graphics.Paint;
import android.graphics.Typeface;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class TextRunAdvancesTest {
    private static Paint getPaint() {
        Paint paint = new Paint();
        AssetManager am = InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getAssets();
        Typeface typeface = Typeface.createFromAsset(am, "fonts/textrunadvances.ttf");
        paint.setTypeface(typeface);
        paint.setTextSize(10f);  // Make 1em = 10px
        return paint;
    }

    @Test
    public void testGetRunCharacterAdvance() {
        final Paint paint = getPaint();
        final char[] text = "abcde".toCharArray();
        final float[] advances = new float[5];

        final float advance = paint.getRunCharacterAdvance(text, 1, 3, 0, text.length,
                true, 2, advances, 0);
        // Since start and end equal to [1, 3), only "bc" is measured.
        assertArrayEquals(advances, new float[] {10.0f, 10.0f, 0.0f, 0.0f, 0.0f}, 0.0f);
        // Start is 1 and offset is 2, and the advance should be 10.0f.
        assertThat(advance).isEqualTo(10.0f);
    }

    @Test
    public void testGetRunCharacterAdvance_charSequence() {
        final Paint paint = getPaint();
        final CharSequence text = "abcde";
        final float[] advances = new float[5];

        final float advance = paint.getRunCharacterAdvance(text, 0, text.length(), 0,
                text.length(), true, text.length(), advances, 0);
        assertArrayEquals(advances, new float[] {30.0f, 10.0f, 10.0f, 10.0f, 10.0f}, 0.0f);
        assertThat(advance).isEqualTo(70.0f);
    }

    @Test
    public void testGetRunCharacterAdvance_LTR() {
        final Paint paint = getPaint();
        final char[] text = "abcde".toCharArray();
        final float[] advances = new float[5];

        final float advance = paint.getRunCharacterAdvance(text, 0, text.length, 0, text.length,
                false, text.length, advances, 0);
        assertArrayEquals(advances, new float[] {30.0f, 10.0f, 10.0f, 10.0f, 10.0f}, 0.0f);
        assertThat(advance).isEqualTo(70.0f);
    }

    @Test
    public void testGetRunCharacterAdvance_RTL() {
        final Paint paint = getPaint();
        final char[] text = "\u05D1\u05D0\u05D0\u05D0\u05D0".toCharArray();
        final float[] advances = new float[5];

        final float advance = paint.getRunCharacterAdvance(text, 0, text.length, 0, text.length,
                true, text.length, advances, 0);
        assertArrayEquals(advances, new float[] {30.0f, 10.0f, 10.0f, 10.0f, 10.0f}, 0.0f);
        assertThat(advance).isEqualTo(70.0f);
    }

    @Test
    public void testGetRunCharacterAdvance_surrogate() {
        final Paint paint = getPaint();
        final char[] text = "\ud83d\ude00abc".toCharArray();
        final float[] advances = new float[5];

        final float advance = paint.getRunCharacterAdvance(text, 0, text.length, 0, text.length,
                true, text.length, advances, 0);
        // For surrogates, the first character is assigned with all advance.
        assertArrayEquals(advances, new float[] {30.0f, 0.0f, 30.0f, 10.0f, 10.0f}, 0.0f);
        assertThat(advance).isEqualTo(80.0f);
    }

    @Test
    public void testGetRunCharacterAdvance_advanceIndex() {
        final Paint paint = getPaint();
        final char[] text = "abc".toCharArray();
        final float[] advances = new float[5];

        paint.getRunCharacterAdvance(text, 0, text.length, 0, text.length,
                true, text.length, advances, 2);
        assertArrayEquals(advances, new float[] {0.0f, 0.0f, 30.0f, 10.0f, 10.0f}, 0.0f);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void testGetRunCharacterAdvance_contextStartOutOfBounds() {
        final Paint paint = getPaint();
        final char[] text = "abcde".toCharArray();

        paint.getRunCharacterAdvance(text, 0, text.length, -1, text.length,
                true, text.length, null, 0);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void testGetRunCharacterAdvance_contextEndOutOfBounds() {
        final Paint paint = getPaint();
        final char[] text = "abcde".toCharArray();

        paint.getRunCharacterAdvance(text, 0, text.length, 0, text.length + 1,
                true, text.length, null, 0);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void testGetRunCharacterAdvance_contextStartLargerThanContextEnd() {
        final Paint paint = getPaint();
        final char[] text = "abcde".toCharArray();

        paint.getRunCharacterAdvance(text, 3, 3, 3, 1,
                true, text.length, null, 0);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void testGetRunCharacterAdvance_startOutOfBounds() {
        final Paint paint = getPaint();
        final char[] text = "abcde".toCharArray();
        final int offset = 1;
        final int start = 0;
        final int end = 2;
        final int contextStart = 1;
        final int contextEnd = 3;

        paint.getRunCharacterAdvance(text, start, end, contextStart, contextEnd, true,
                offset, null, 0);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void testGetRunCharacterAdvance_endOutOfBounds() {
        final Paint paint = getPaint();
        final char[] text = "abcde".toCharArray();
        final int offset = 2;
        final int start = 1;
        final int end = 4;
        final int contextStart = 1;
        final int contextEnd = 3;

        paint.getRunCharacterAdvance(text, start, end, contextStart, contextEnd, true,
                offset, null, 0);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void testGetRunCharacterAdvance_startLargerThanEnd() {
        final Paint paint = getPaint();
        final char[] text = "abcde".toCharArray();
        final int offset = 2;
        final int start = 3;
        final int end = 2;
        final int contextStart = 1;
        final int contextEnd = 3;

        paint.getRunCharacterAdvance(text, start, end, contextStart, contextEnd, true,
                offset, null, 0);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void testGetRunCharacterAdvance_offsetOutOfBounds() {
        final Paint paint = getPaint();
        final char[] text = "abcde".toCharArray();
        final int offset = 1;
        final int start = 2;
        final int end = 3;
        final int contextStart = 0;
        final int contextEnd = 4;

        paint.getRunCharacterAdvance(text, start, end, contextStart, contextEnd, true,
                offset, null, 0);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void testGetRunCharacterAdvance_advancesNoEnoughSpace() {
        final Paint paint = getPaint();
        final char[] text = "abcde".toCharArray();
        float[] advances = new float[2];
        final int offset = 2;
        final int start = 1;
        final int end = 4;
        final int contextStart = 0;
        final int contextEnd = 5;

        paint.getRunCharacterAdvance(text, start, end, contextStart, contextEnd, true,
                offset, advances, 0);
    }
}
