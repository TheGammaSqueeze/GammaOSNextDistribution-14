/*
 * Copyright (C) 2009 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorSpace;
import android.graphics.Gainmap;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;

import androidx.test.filters.SmallTest;

import com.android.compatibility.common.util.ColorUtils;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

@SmallTest
@RunWith(JUnitParamsRunner.class)
public class BitmapShaderTest {
    private static final int TILE_WIDTH = 20;
    private static final int TILE_HEIGHT = 20;
    private static final int BORDER_WIDTH = 5;
    private static final int BORDER_COLOR = Color.BLUE;
    private static final int CENTER_COLOR = Color.RED;
    private static final int NUM_TILES = 4;

    private Object[] testConfigs() {
        return new Object[] {
            Bitmap.Config.ARGB_8888,
            Bitmap.Config.RGBA_1010102
        };
    }

    @Test
    @Parameters(method = "testConfigs")
    public void testBitmapShader(Bitmap.Config config) {
        Bitmap tile = Bitmap.createBitmap(TILE_WIDTH, TILE_HEIGHT, config);
        tile.eraseColor(BORDER_COLOR);
        Canvas c = new Canvas(tile);
        Paint p = new Paint();
        p.setColor(CENTER_COLOR);
        c.drawRect(BORDER_WIDTH, BORDER_WIDTH,
                TILE_WIDTH - BORDER_WIDTH, TILE_HEIGHT - BORDER_WIDTH, p);
        BitmapShader shader = new BitmapShader(tile, Shader.TileMode.REPEAT,
                Shader.TileMode.REPEAT);
        Paint paint = new Paint();
        paint.setShader(shader);
        // create a bitmap that fits (NUM_TILES - 0.5) tiles in both directions
        Bitmap b = Bitmap.createBitmap(NUM_TILES * TILE_WIDTH - TILE_WIDTH / 2,
                NUM_TILES * TILE_HEIGHT - TILE_HEIGHT / 2, config);
        b.eraseColor(Color.BLACK);
        Canvas canvas = new Canvas(b);
        canvas.drawPaint(paint);

        for (int y = 0; y < NUM_TILES; y++) {
            for (int x = 0; x < NUM_TILES; x++) {
                verifyTile(b, x * TILE_WIDTH, y * TILE_HEIGHT);
            }
        }
    }

    /**
     * Check the colors of the tile at the given coordinates in the given
     * bitmap.
     */
    private void verifyTile(Bitmap bitmap, int tileX, int tileY) {
        for (int y = 0; y < TILE_HEIGHT; y++) {
            for (int x = 0; x < TILE_WIDTH; x++) {
                if (x < BORDER_WIDTH || x >= TILE_WIDTH - BORDER_WIDTH ||
                    y < BORDER_WIDTH || y >= TILE_HEIGHT - BORDER_WIDTH) {
                    verifyColor(BORDER_COLOR, bitmap, x + tileX, y + tileY);
                } else {
                    verifyColor(CENTER_COLOR, bitmap, x + tileX, y + tileY);
                }
            }
        }
    }

    /**
     * Asserts that the pixel at the given coordinates in the given bitmap
     * matches the given color. Simply returns if the coordinates are outside
     * the bitmap area.
     */
    private void verifyColor(int color, Bitmap bitmap, int x, int y) {
        if (x < bitmap.getWidth() && y < bitmap.getHeight()) {
            assertEquals(color, bitmap.getPixel(x, y));
        }
    }

    @Test
    @Parameters(method = "testConfigs")
    public void testClamp(Bitmap.Config config) {
        Bitmap bitmap = Bitmap.createBitmap(2, 1, config);
        bitmap.setPixel(0, 0, Color.RED);
        bitmap.setPixel(1, 0, Color.BLUE);

        BitmapShader shader = new BitmapShader(bitmap,
                Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);

        Bitmap dstBitmap = Bitmap.createBitmap(4, 1, config);
        Canvas canvas = new Canvas(dstBitmap);
        Paint paint = new Paint();
        paint.setShader(shader);
        canvas.drawRect(0, 0, 4, 1, paint);
        canvas.setBitmap(null);

        int[] pixels = new int[4];
        dstBitmap.getPixels(pixels, 0, 4, 0, 0, 4, 1);
        Assert.assertArrayEquals(new int[] { Color.RED, Color.BLUE, Color.BLUE, Color.BLUE },
                pixels);
    }

    @Test
    @Parameters(method = "testConfigs")
    public void testRepeat(Bitmap.Config config) {
        Bitmap bitmap = Bitmap.createBitmap(2, 1, config);
        bitmap.setPixel(0, 0, Color.RED);
        bitmap.setPixel(1, 0, Color.BLUE);

        BitmapShader shader = new BitmapShader(bitmap,
                Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);

        Bitmap dstBitmap = Bitmap.createBitmap(4, 1, config);
        Canvas canvas = new Canvas(dstBitmap);
        Paint paint = new Paint();
        paint.setShader(shader);
        canvas.drawRect(0, 0, 4, 1, paint);
        canvas.setBitmap(null);

        int[] pixels = new int[4];
        dstBitmap.getPixels(pixels, 0, 4, 0, 0, 4, 1);
        Assert.assertArrayEquals(new int[] { Color.RED, Color.BLUE, Color.RED, Color.BLUE },
                pixels);
    }

    @Test
    @Parameters(method = "testConfigs")
    public void testMirror(Bitmap.Config config) {
        Bitmap bitmap = Bitmap.createBitmap(2, 1, config);
        bitmap.setPixel(0, 0, Color.RED);
        bitmap.setPixel(1, 0, Color.BLUE);

        BitmapShader shader = new BitmapShader(bitmap,
                Shader.TileMode.MIRROR, Shader.TileMode.MIRROR);

        Bitmap dstBitmap = Bitmap.createBitmap(4, 1, config);
        Canvas canvas = new Canvas(dstBitmap);
        Paint paint = new Paint();
        paint.setShader(shader);
        canvas.drawRect(0, 0, 4, 1, paint);
        canvas.setBitmap(null);

        int[] pixels = new int[4];
        dstBitmap.getPixels(pixels, 0, 4, 0, 0, 4, 1);
        Assert.assertArrayEquals(new int[] { Color.RED, Color.BLUE, Color.BLUE, Color.RED },
                pixels);
    }

    @Test
    @Parameters(method = "testConfigs")
    public void testFiltering(Bitmap.Config config) {
        Bitmap bitmap = Bitmap.createBitmap(2, 2, config);
        bitmap.setPixel(0, 0, Color.RED);
        bitmap.setPixel(1, 0, Color.BLUE);
        bitmap.setPixel(0, 1, Color.BLUE);
        bitmap.setPixel(1, 1, Color.RED);

        BitmapShader shader = new BitmapShader(bitmap,
                Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        Assert.assertEquals(BitmapShader.FILTER_MODE_DEFAULT, shader.getFilterMode());

        // use slightly left than half to avoid any confusion on which pixel
        // is sampled with FILTER_MODE_NEAREST
        Matrix matrix = new Matrix();
        matrix.postScale(0.49f, 0.49f);
        shader.setLocalMatrix(matrix);

        Bitmap dstBitmap = Bitmap.createBitmap(1, 1, config);
        Canvas canvas = new Canvas(dstBitmap);
        Paint paint = new Paint();
        paint.setShader(shader);

        paint.setFilterBitmap(false);
        canvas.drawPaint(paint);
        ColorUtils.verifyColor("expected solid red color", Color.RED, dstBitmap.getPixel(0, 0), 0);

        paint.setFilterBitmap(true);
        canvas.drawPaint(paint);
        ColorUtils.verifyColor("color should be a blue/red mix", Color.valueOf(0.5f, 0.0f, 0.5f),
                dstBitmap.getColor(0, 0), 0.05f);

        shader.setFilterMode(BitmapShader.FILTER_MODE_NEAREST);
        canvas.drawPaint(paint);
        ColorUtils.verifyColor("expected solid red color", Color.RED, dstBitmap.getPixel(0, 0), 0);

        shader.setFilterMode(BitmapShader.FILTER_MODE_LINEAR);
        paint.setFilterBitmap(false);
        canvas.drawPaint(paint);
        ColorUtils.verifyColor("color should be a blue/red mix", Color.valueOf(0.5f, 0.0f, 0.5f),
                dstBitmap.getColor(0, 0), 0.05f);
    }

    @Test
    public void testAnisotropicFiltering() {
        Bitmap bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
        bitmap.setPixel(0, 0, Color.RED);
        bitmap.setPixel(1, 0, Color.BLUE);
        bitmap.setPixel(0, 1, Color.BLUE);
        bitmap.setPixel(1, 1, Color.RED);

        BitmapShader shader = new BitmapShader(bitmap,
                Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        shader.setMaxAnisotropy(16);
        assertEquals(16, shader.getMaxAnisotropy());

        // Configuring the filter mode should override the max anisotropic value
        shader.setFilterMode(BitmapShader.FILTER_MODE_LINEAR);
        assertEquals(0, shader.getMaxAnisotropy());

        // Configuring the anisotropic value should override the default filter mode
        shader.setMaxAnisotropy(4);
        assertEquals(BitmapShader.FILTER_MODE_DEFAULT, shader.getFilterMode());
    }

    @Test
    public void testRecycledBitmapThrowsISE() {
        Bitmap bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        BitmapShader shader = new BitmapShader(bitmap,
                Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        Paint paint = new Paint();
        Canvas canvas = new Canvas(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888));
        bitmap.recycle();
        assertThrows(IllegalStateException.class, () -> {
            paint.setShader(shader);
            canvas.drawPaint(paint);
        });
        assertThrows(IllegalStateException.class, () -> {
            new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        });
    }

    @Test
    public void testBitmapShaderAppliesGainmapHLG() {
        Bitmap bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.WHITE);
        Bitmap gainmapBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        gainmapBitmap.eraseColor(0);
        bitmap.setGainmap(new Gainmap(gainmapBitmap));

        Paint paint = new Paint();
        paint.setShader(new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        Bitmap result = Bitmap.createBitmap(1, 1, Bitmap.Config.RGBA_F16, false,
                ColorSpace.get(ColorSpace.Named.BT2020_HLG));
        Canvas canvas = new Canvas(result);
        canvas.drawPaint(paint);

        // Gainmap is all-black, no boost should happen
        Color color = result.getColor(0, 0);
        assertEquals(0.75f, color.red(), 0.01f);
        assertEquals(0.75f, color.green(), 0.01f);
        assertEquals(0.75f, color.blue(), 0.01f);

        gainmapBitmap.eraseColor(Color.WHITE);
        canvas.drawPaint(paint);
        color = result.getColor(0, 0);

        // Gainmap is all-white, maximum boost of 2x should happen
        assertEquals(0.88, color.red(), 0.01f);
        assertEquals(0.88, color.green(), 0.01f);
        assertEquals(0.88, color.blue(), 0.01f);
    }
}
