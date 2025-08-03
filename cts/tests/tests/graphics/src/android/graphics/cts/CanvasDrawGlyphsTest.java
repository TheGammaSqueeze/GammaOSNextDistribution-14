/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.graphics.cts;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.fonts.Font;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class CanvasDrawGlyphsTest {
    private static final String FONT_PATH = "fonts/draw/draw_glyph_font.ttf";
    private static final int BMP_WIDTH = 300;
    private static final int BMP_HEIGHT = 100;
    private static final float DRAW_ORIGIN_X = 20f;
    private static final float DRAW_ORIGIN_Y = 70f;
    private static final float TEXT_SIZE = 50f;  // make 1em = 50px
    // All glyph in the test font has 1em advance, i.e. TEXT_SIZE.
    private static final float GLYPH_ADVANCE = TEXT_SIZE;
    private Font mFont;
    private Typeface mTypeface;

    @Before
    public void setup() throws IOException {
        Context context = InstrumentationRegistry.getTargetContext();
        mFont = new Font.Builder(context.getAssets(), FONT_PATH).build();
        mTypeface = Typeface.createFromAsset(context.getAssets(), FONT_PATH);
    }

    private Bitmap drawGlyphsToBitmap(int[] glyphIds, int glyphIdOffset, float[] positions,
            int positionStart, int glyphCount, Font font, Paint paint) {
        Bitmap bmp = Bitmap.createBitmap(BMP_WIDTH, BMP_HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        canvas.drawGlyphs(glyphIds, glyphIdOffset, positions, positionStart, glyphCount,
                font, paint);
        return bmp;
    }

    @Test
    public void drawGlyphs_SameToDrawText() {
        Paint paint = new Paint();
        paint.setTextSize(TEXT_SIZE);

        Bitmap glyphBmp = drawGlyphsToBitmap(
                new int[] { 1, 2, 3, 4, 5 },  // Corresponding to "abcde" in test font
                0,  // glyph offset
                new float[] {
                        DRAW_ORIGIN_X, DRAW_ORIGIN_Y,
                        DRAW_ORIGIN_X + GLYPH_ADVANCE, DRAW_ORIGIN_Y,
                        DRAW_ORIGIN_X + GLYPH_ADVANCE * 2, DRAW_ORIGIN_Y,
                        DRAW_ORIGIN_X + GLYPH_ADVANCE * 3, DRAW_ORIGIN_Y,
                        DRAW_ORIGIN_X + GLYPH_ADVANCE * 4, DRAW_ORIGIN_Y,
                },
                0,  // position offset
                5,  // glyph count
                mFont,
                paint
        );

        Bitmap textBmp = Bitmap.createBitmap(300, 100, Bitmap.Config.ARGB_8888);
        Canvas textCanvas = new Canvas(textBmp);
        paint.setTypeface(mTypeface);
        textCanvas.drawText("abcde", 0, 5, DRAW_ORIGIN_X, DRAW_ORIGIN_Y, paint);

        assertThat(glyphBmp.sameAs(textBmp)).isTrue();
    }

    @Test
    public void drawGlyphs_glyphOffset() {
        Paint paint = new Paint();
        paint.setTextSize(TEXT_SIZE);

        Bitmap glyphBmp = drawGlyphsToBitmap(
                new int[] { -1, -1, 1, 2, 3, 4, 5 },  // Skip first two
                2,  // glyph offset
                new float[] {
                        DRAW_ORIGIN_X, DRAW_ORIGIN_Y,
                        DRAW_ORIGIN_X + GLYPH_ADVANCE, DRAW_ORIGIN_Y,
                        DRAW_ORIGIN_X + GLYPH_ADVANCE * 2, DRAW_ORIGIN_Y,
                        DRAW_ORIGIN_X + GLYPH_ADVANCE * 3, DRAW_ORIGIN_Y,
                        DRAW_ORIGIN_X + GLYPH_ADVANCE * 4, DRAW_ORIGIN_Y,
                },
                0,  // position offset
                5,  // glyph count
                mFont,
                paint
        );

        Bitmap glyphBmp2 = drawGlyphsToBitmap(
                new int[] { -1, -1, -1, 1, 2, 3, 4, 5 },  // Skip first three
                3,  // glyph offset
                new float[] {
                        DRAW_ORIGIN_X, DRAW_ORIGIN_Y,
                        DRAW_ORIGIN_X + GLYPH_ADVANCE, DRAW_ORIGIN_Y,
                        DRAW_ORIGIN_X + GLYPH_ADVANCE * 2, DRAW_ORIGIN_Y,
                        DRAW_ORIGIN_X + GLYPH_ADVANCE * 3, DRAW_ORIGIN_Y,
                        DRAW_ORIGIN_X + GLYPH_ADVANCE * 4, DRAW_ORIGIN_Y,
                },
                0,  // position offset
                5,  // glyph count
                mFont,
                paint
        );

        Bitmap expectedBmp = drawGlyphsToBitmap(
                new int[] { 1, 2, 3, 4, 5 },
                0,  // glyph offset
                new float[] {
                        DRAW_ORIGIN_X, DRAW_ORIGIN_Y,
                        DRAW_ORIGIN_X + GLYPH_ADVANCE, DRAW_ORIGIN_Y,
                        DRAW_ORIGIN_X + GLYPH_ADVANCE * 2, DRAW_ORIGIN_Y,
                        DRAW_ORIGIN_X + GLYPH_ADVANCE * 3, DRAW_ORIGIN_Y,
                        DRAW_ORIGIN_X + GLYPH_ADVANCE * 4, DRAW_ORIGIN_Y,
                },
                0,  // position offset
                5,  // glyph count
                mFont,
                paint
        );

        assertThat(glyphBmp.sameAs(glyphBmp2)).isTrue();
        assertThat(glyphBmp.sameAs(expectedBmp)).isTrue();
    }

    @Test
    public void drawGlyphs_positionOffset() {
        Paint paint = new Paint();
        paint.setTextSize(TEXT_SIZE);

        Bitmap glyphBmp = drawGlyphsToBitmap(
                new int[] { 1, 2, 3, 4, 5 },
                0,  // glyph offset
                new float[] {
                        -1f, -1f,  // Skip first two
                        DRAW_ORIGIN_X, DRAW_ORIGIN_Y,
                        DRAW_ORIGIN_X + GLYPH_ADVANCE, DRAW_ORIGIN_Y,
                        DRAW_ORIGIN_X + GLYPH_ADVANCE * 2, DRAW_ORIGIN_Y,
                        DRAW_ORIGIN_X + GLYPH_ADVANCE * 3, DRAW_ORIGIN_Y,
                        DRAW_ORIGIN_X + GLYPH_ADVANCE * 4, DRAW_ORIGIN_Y,
                },
                2,  // position offset
                5,  // glyph count
                mFont,
                paint
        );

        Bitmap glyphBmp2 = drawGlyphsToBitmap(
                new int[] { 1, 2, 3, 4, 5 },
                0,  // glyph offset
                new float[] {
                        -1f, -1f, -1f,  // Skip first three
                        DRAW_ORIGIN_X, DRAW_ORIGIN_Y,
                        DRAW_ORIGIN_X + GLYPH_ADVANCE, DRAW_ORIGIN_Y,
                        DRAW_ORIGIN_X + GLYPH_ADVANCE * 2, DRAW_ORIGIN_Y,
                        DRAW_ORIGIN_X + GLYPH_ADVANCE * 3, DRAW_ORIGIN_Y,
                        DRAW_ORIGIN_X + GLYPH_ADVANCE * 4, DRAW_ORIGIN_Y,
                },
                3,  // position offset
                5,  // glyph count
                mFont,
                paint
        );

        Bitmap expectedBmp = drawGlyphsToBitmap(
                new int[] { 1, 2, 3, 4, 5 },
                0,  // glyph offset
                new float[] {
                        DRAW_ORIGIN_X, DRAW_ORIGIN_Y,
                        DRAW_ORIGIN_X + GLYPH_ADVANCE, DRAW_ORIGIN_Y,
                        DRAW_ORIGIN_X + GLYPH_ADVANCE * 2, DRAW_ORIGIN_Y,
                        DRAW_ORIGIN_X + GLYPH_ADVANCE * 3, DRAW_ORIGIN_Y,
                        DRAW_ORIGIN_X + GLYPH_ADVANCE * 4, DRAW_ORIGIN_Y,
                },
                0,  // position offset
                5,  // glyph count
                mFont,
                paint
        );

        assertThat(glyphBmp.sameAs(glyphBmp2)).isTrue();
        assertThat(glyphBmp.sameAs(expectedBmp)).isTrue();
    }

    @Test
    public void drawGlyphs_glyphCount() {
        Paint paint = new Paint();
        paint.setTextSize(TEXT_SIZE);

        Bitmap firstThreeBmp = drawGlyphsToBitmap(
                new int[] { 1, 2, 3, 4, 5 },
                0,  // glyph offset
                new float[] {
                        DRAW_ORIGIN_X, DRAW_ORIGIN_Y,
                        DRAW_ORIGIN_X + GLYPH_ADVANCE, DRAW_ORIGIN_Y,
                        DRAW_ORIGIN_X + GLYPH_ADVANCE * 2, DRAW_ORIGIN_Y,
                        DRAW_ORIGIN_X + GLYPH_ADVANCE * 3, DRAW_ORIGIN_Y,
                        DRAW_ORIGIN_X + GLYPH_ADVANCE * 4, DRAW_ORIGIN_Y,
                },
                0,  // position offset
                3,  // glyph count
                mFont,
                paint
        );

        Bitmap firstTwoBmp = drawGlyphsToBitmap(
                new int[] { 1, 2, 3, 4, 5 },
                0,  // glyph offset
                new float[] {
                        DRAW_ORIGIN_X, DRAW_ORIGIN_Y,
                        DRAW_ORIGIN_X + GLYPH_ADVANCE, DRAW_ORIGIN_Y,
                        DRAW_ORIGIN_X + GLYPH_ADVANCE * 2, DRAW_ORIGIN_Y,
                        DRAW_ORIGIN_X + GLYPH_ADVANCE * 3, DRAW_ORIGIN_Y,
                        DRAW_ORIGIN_X + GLYPH_ADVANCE * 4, DRAW_ORIGIN_Y,
                },
                0,  // position offset
                2,  // glyph count
                mFont,
                paint
        );

        assertThat(firstThreeBmp.sameAs(firstTwoBmp)).isFalse();
    }

    @Test
    public void drawGlyphs_positionDifference() {
        Paint paint = new Paint();
        paint.setTextSize(TEXT_SIZE);

        float dx = 10f;
        float dy = 1f;
        Bitmap bmp = drawGlyphsToBitmap(
                new int[] { 1, 2, 3, 4, 5 },
                0,  // glyph offset
                new float[] {
                        DRAW_ORIGIN_X, DRAW_ORIGIN_Y,
                        DRAW_ORIGIN_X + dx, DRAW_ORIGIN_Y + dy,
                        DRAW_ORIGIN_X + dx * 2, DRAW_ORIGIN_Y + dy * 2,
                        DRAW_ORIGIN_X + dx * 3, DRAW_ORIGIN_Y + dy * 3,
                        DRAW_ORIGIN_X + dx * 4, DRAW_ORIGIN_Y + dy * 4,
                },
                0,  // position offset
                5,  // glyph count
                mFont,
                paint
        );

        dx = 5f;
        dy = 2f;
        Bitmap differentGlyphPositionBmp = drawGlyphsToBitmap(
                new int[] { 1, 2, 3, 4, 5 },
                0,  // glyph offset
                new float[] {
                        DRAW_ORIGIN_X, DRAW_ORIGIN_Y,
                        DRAW_ORIGIN_X + dx, DRAW_ORIGIN_Y + dy,
                        DRAW_ORIGIN_X + dx * 2, DRAW_ORIGIN_Y + dy * 2,
                        DRAW_ORIGIN_X + dx * 3, DRAW_ORIGIN_Y + dy * 3,
                        DRAW_ORIGIN_X + dx * 4, DRAW_ORIGIN_Y + dy * 4,
                },
                0,  // position offset
                5,  // glyph count
                mFont,
                paint
        );

        assertThat(bmp.sameAs(differentGlyphPositionBmp)).isFalse();
    }

    @Test
    public void drawGlyphs_paintEffect() {
        Paint paint = new Paint();
        paint.setTextSize(TEXT_SIZE);

        Bitmap bmp = drawGlyphsToBitmap(
                new int[] { 1, 2, 3, 4, 5 },
                0,  // glyph offset
                new float[] {
                        DRAW_ORIGIN_X, DRAW_ORIGIN_Y,
                        DRAW_ORIGIN_X + GLYPH_ADVANCE, DRAW_ORIGIN_Y,
                        DRAW_ORIGIN_X + GLYPH_ADVANCE * 2, DRAW_ORIGIN_Y,
                        DRAW_ORIGIN_X + GLYPH_ADVANCE * 3, DRAW_ORIGIN_Y,
                        DRAW_ORIGIN_X + GLYPH_ADVANCE * 4, DRAW_ORIGIN_Y,
                },
                0,  // position offset
                5,  // glyph count
                mFont,
                paint
        );

        paint.setTextSize(TEXT_SIZE * 2f);
        Bitmap twiceTextSizeBmp = drawGlyphsToBitmap(
                new int[] { 1, 2, 3, 4, 5 },
                0,  // glyph offset
                new float[] {
                        DRAW_ORIGIN_X, DRAW_ORIGIN_Y,
                        DRAW_ORIGIN_X + GLYPH_ADVANCE, DRAW_ORIGIN_Y,
                        DRAW_ORIGIN_X + GLYPH_ADVANCE * 2, DRAW_ORIGIN_Y,
                        DRAW_ORIGIN_X + GLYPH_ADVANCE * 3, DRAW_ORIGIN_Y,
                        DRAW_ORIGIN_X + GLYPH_ADVANCE * 4, DRAW_ORIGIN_Y,
                },
                0,  // position offset
                5,  // glyph count
                mFont,
                paint
        );

        assertThat(bmp.sameAs(twiceTextSizeBmp)).isFalse();
    }

    @Test(expected = NullPointerException.class)
    public void nullGlyphIds() {
        drawGlyphsToBitmap(null, 0, new float[] {}, 0, 0, mFont, new Paint());
    }

    @Test(expected = NullPointerException.class)
    public void nullPositions() {
        drawGlyphsToBitmap(new int[] {}, 0, null, 0, 0, mFont, new Paint());
    }

    @Test(expected = NullPointerException.class)
    public void nullFont() {
        drawGlyphsToBitmap(new int[] {}, 0, new float[] {}, 0, 0, null, new Paint());
    }

    @Test(expected = NullPointerException.class)
    public void nullPaint() {
        drawGlyphsToBitmap(new int[] {}, 0, new float[] {}, 0, 0, mFont, null);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void negativeGlyphOffset() {
        drawGlyphsToBitmap(new int[] {}, -1, new float[] {}, 0, 0, mFont, new Paint());
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void negativePositionOffset() {
        drawGlyphsToBitmap(new int[] {}, 0, new float[] {}, -1, 0, mFont, new Paint());
    }

    @Test(expected = IllegalArgumentException.class)
    public void negativeGlyphCount() {
        drawGlyphsToBitmap(new int[] {}, 0, new float[] {}, 0, -1, mFont, new Paint());
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void tooShortGlyphIds() {
        drawGlyphsToBitmap(new int[] {1, 2}, 1, new float[] {}, 0, 2, mFont, new Paint());
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void tooShortPositions() {
        drawGlyphsToBitmap(new int[] { 1 }, 0, new float[] { 0f, 0f }, 1, 1, mFont, new Paint());
    }
}
