/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.uirendering.cts.testclasses;

import static android.graphics.Bitmap.Config.ARGB_8888;
import static android.graphics.Bitmap.Config.HARDWARE;
import static android.graphics.Bitmap.Config.RGB_565;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorSpace;
import android.graphics.HardwareBufferRenderer;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.RecordingCanvas;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.graphics.fonts.Font;
import android.graphics.fonts.SystemFonts;
import android.hardware.HardwareBuffer;
import android.uirendering.cts.bitmapverifiers.SamplePointVerifier;
import android.uirendering.cts.testinfrastructure.ActivityTestBase;
import android.uirendering.cts.util.BitmapDumper;

import androidx.annotation.ColorLong;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class ColorSpaceTests extends ActivityTestBase {
    private Bitmap mMask;

    @Before
    public void loadMask() {
        Bitmap res = BitmapFactory.decodeResource(getActivity().getResources(),
                android.uirendering.cts.R.drawable.alpha_mask);
        mMask = Bitmap.createBitmap(res.getWidth(), res.getHeight(), Bitmap.Config.ALPHA_8);
        Canvas c = new Canvas(mMask);
        c.drawBitmap(res, 0, 0, null);
    }

    @Test
    public void testDrawDisplayP3() {
        // Uses hardware transfer function
        Bitmap bitmap8888 = loadAsset("green-p3.png", ARGB_8888);
        Bitmap bitmapHardware = loadAsset("green-p3.png", HARDWARE);
        createTest()
                .addCanvasClient("Draw_DisplayP3_8888",
                        (c, w, h) -> drawAsset(c, bitmap8888), true)
                .addCanvasClient(
                        (c, w, h) -> drawAsset(c, bitmapHardware), true)
                .runWithVerifier(new SamplePointVerifier(
                        new Point[] {
                                point(0, 0), point(48, 0), point(32, 40), point(0, 40), point(0, 56)
                        },
                        new int[] { 0xff00ff00, 0xff00ff00, 0xff00ff00, 0xffffffff, 0xff7f7f00 }
                ));
    }

    @Test
    public void testDrawDisplayP3Config565() {
        // Uses hardware transfer function
        Bitmap bitmap = loadAsset("green-p3.png", RGB_565);
        createTest()
                .addCanvasClient("Draw_DisplayP3_565", (c, w, h) -> drawAsset(c, bitmap), true)
                .runWithVerifier(new SamplePointVerifier(
                        new Point[] {
                                point(0, 0), point(48, 0), point(32, 40), point(0, 40), point(0, 56)
                        },
                        new int[] { 0xff00ff00, 0xff00ff00, 0xff00ff00, 0xffffffff, 0xff7f7f00 }
                ));
    }

    @Test
    public void testDrawProPhotoRGB() {
        // Uses hardware limited shader transfer function
        Bitmap bitmap8888 = loadAsset("orange-prophotorgb.png", ARGB_8888);
        Bitmap bitmapHardware = loadAsset("orange-prophotorgb.png", HARDWARE);
        createTest()
                .addCanvasClient("Draw_ProPhotoRGB_8888",
                        (c, w, h) -> drawAsset(c, bitmap8888), true)
                .addCanvasClient(
                        (c, w, h) -> drawAsset(c, bitmapHardware), true)
                .runWithVerifier(new SamplePointVerifier(
                        new Point[] {
                                point(0, 0), point(48, 0), point(32, 40), point(0, 40), point(0, 56)
                        },
                        new int[] { 0xffff7f00, 0xffff7f00, 0xffff7f00, 0xffffffff, 0xffff3f00 }
                ));
    }

    @Test
    public void testDrawProPhotoRGBConfig565() {
        // Uses hardware limited shader transfer function
        Bitmap bitmap = loadAsset("orange-prophotorgb.png", RGB_565);
        createTest()
                .addCanvasClient("Draw_ProPhotoRGB_565",
                        (c, w, h) -> drawAsset(c, bitmap), true)
                .runWithVerifier(new SamplePointVerifier(
                        new Point[] {
                                point(0, 0), point(48, 0), point(32, 40), point(0, 40), point(0, 56)
                        },
                        new int[] { 0xffff7f00, 0xffff7f00, 0xffff7f00, 0xffffffff, 0xffff3f00 }
                ));
    }

    @Test
    public void testDrawTranslucentAdobeRGB() {
        // Uses hardware simplified gamma transfer function
        Bitmap bitmap8888 = loadAsset("red-adobergb.png", ARGB_8888);
        Bitmap bitmapHardware = loadAsset("red-adobergb.png", HARDWARE);
        createTest()
                .addCanvasClient("Draw_AdobeRGB_Translucent_8888",
                        (c, w, h) -> drawTranslucentAsset(c, bitmap8888), true)
                .addCanvasClient(
                        (c, w, h) -> drawTranslucentAsset(c, bitmapHardware), true)
                .runWithVerifier(new SamplePointVerifier(
                        new Point[] { point(0, 0) },
                        new int[] { 0xffed8080 }
                ));
    }

    @Test
    public void testHlgWhitePoint() {
        final ColorSpace bt2020_hlg = ColorSpace.get(ColorSpace.Named.BT2020_HLG);
        ColorSpace.Connector connector = ColorSpace.connect(ColorSpace.get(ColorSpace.Named.SRGB),
                bt2020_hlg);
        float[] connectorResult = connector.transform(1f, 1f, 1f);
        Assert.assertEquals(.75f, connectorResult[0], 0.001f);
        Assert.assertEquals(.75f, connectorResult[1], 0.001f);
        Assert.assertEquals(.75f, connectorResult[2], 0.001f);

        Color bitmapResult = transformViaBitmap(Color.pack(Color.WHITE), bt2020_hlg);
        Assert.assertEquals(.75f, bitmapResult.red(), 0.001f);
        Assert.assertEquals(.75f, bitmapResult.green(), 0.001f);
        Assert.assertEquals(.75f, bitmapResult.blue(), 0.001f);
    }

    @Test
    public void testPqWhitePoint() {
        final ColorSpace bt2020_pq = ColorSpace.get(ColorSpace.Named.BT2020_PQ);
        ColorSpace.Connector connector = ColorSpace.connect(ColorSpace.get(ColorSpace.Named.SRGB),
                bt2020_pq);
        float[] connectorResult = connector.transform(1f, 1f, 1f);
        Assert.assertEquals(.58f, connectorResult[0], 0.001f);
        Assert.assertEquals(.58f, connectorResult[1], 0.001f);
        Assert.assertEquals(.58f, connectorResult[2], 0.001f);

        Color bitmapResult = transformViaBitmap(Color.pack(Color.WHITE), bt2020_pq);
        Assert.assertEquals(.58f, bitmapResult.red(), 0.001f);
        Assert.assertEquals(.58f, bitmapResult.green(), 0.001f);
        Assert.assertEquals(.58f, bitmapResult.blue(), 0.001f);
    }

    @Test
    public void testEmojiRespectsColorSpace() {
        HardwareBuffer buffer = HardwareBuffer.create(32, 32, HardwareBuffer.RGBA_8888,
                1, HardwareBuffer.USAGE_GPU_COLOR_OUTPUT | HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE);
        final ColorSpace dest = ColorSpace.get(ColorSpace.Named.BT2020_PQ);
        HardwareBufferRenderer renderer = new HardwareBufferRenderer(buffer);
        RenderNode content = new RenderNode("emoji");
        content.setPosition(0, 0, 32, 32);
        RecordingCanvas canvas = content.beginRecording();
        Paint p = new Paint();
        p.setTextSize(32);
        canvas.drawColor(Color.pack(1.0f, 1.0f, 1.0f, 1.0f, dest));
        canvas.drawText(Character.toString('\u2B1C'), 0.0f, 32.0f, p);
        content.endRecording();
        renderer.setContentRoot(content);
        CountDownLatch latch = new CountDownLatch(1);
        renderer.obtainRenderRequest().setColorSpace(dest).draw(Runnable::run, result -> {
            result.getFence().awaitForever();
            latch.countDown();
        });
        try {
            Assert.assertTrue(latch.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException ex) {
            Assert.fail(ex.getMessage());
        }
        Bitmap result = Bitmap.wrapHardwareBuffer(buffer, dest)
                .copy(Bitmap.Config.ARGB_8888, false);
        Color color = result.getColor(16, 16).convert(
                ColorSpace.get(ColorSpace.Named.EXTENDED_SRGB));
        if (color.red() > 1 || color.blue() > 1 || color.green() > 1) {
            BitmapDumper.dumpBitmap(result);
            Assert.fail("Emoji failed colorspace conversion; got " + color.red() + ", "
                    + color.blue() + ", " + color.green());
        }
    }

    // Renders many glyphs from a color font to overflow into Skia's multi-atlas codepath.
    //
    // Originally created to ensure SkSL helper functions (for e.g. colorspace conversion) aren't
    // duplicated when needing to pull from multiple atlases, which could cause a shader compilation
    // error resulting in no glyphs being drawn.
    @Test
    public void testMultiAtlasGlyphsWithColorSpace() throws IOException {
        final int canvasSize = 64;
        final int[] textSizes = { 80, 60, 40, 30, 25, 20, 18, 13, 12, 11, 10, 9, 8 };
        final int numGlyphs = 1000;
        final int[] glyphIds = new int[numGlyphs];
        final float[] positions = new float[2 * numGlyphs];
        for (int i = 0; i < numGlyphs; i++) {
            glyphIds[i] = i;
            // Position in bottom left to better fill space
            positions[2 * i] = 0;
            positions[2 * i + 1] = canvasSize;
        }

        Font font = null;
        for (Font sysFont : SystemFonts.getAvailableFonts()) {
            if (sysFont.getFile().getName().equals("NotoColorEmoji.ttf")) {
                font = sysFont;
                break;
            }
        }
        // Per SystemEmojiTest#uniquePostScript (CtsGraphicsTestCases), NotoColorEmoji.ttf should
        // always be available as a fallback font, even if other emoji font files are installed on
        // the system.
        Assert.assertNotNull(font);

        HardwareBuffer buffer = HardwareBuffer.create(canvasSize, canvasSize,
                HardwareBuffer.RGBA_8888, 1,
                HardwareBuffer.USAGE_GPU_COLOR_OUTPUT | HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE);
        final ColorSpace dest = ColorSpace.get(ColorSpace.Named.BT2020_PQ); // Colorspace conversion
        HardwareBufferRenderer renderer = new HardwareBufferRenderer(buffer);
        RenderNode content = new RenderNode("colored_glyphs");
        content.setPosition(0, 0, canvasSize, canvasSize);
        Paint p = new Paint();

        // Render twice to ensure the final image was all rendered after the switch to multi-atlas
        for (int renderAttempt = 0; renderAttempt < 2; renderAttempt++) {
            RecordingCanvas canvas = content.beginRecording();
            // Start with a white background
            canvas.drawColor(Color.pack(1.0f, 1.0f, 1.0f, 1.0f, dest));

            for (int i = 0; i < textSizes.length; i++) {
                p.setTextSize(textSizes[i]);
                canvas.drawGlyphs(glyphIds, 0, positions, 0, glyphIds.length, font, p);
            }

            content.endRecording();
            renderer.setContentRoot(content);
            CountDownLatch latch = new CountDownLatch(1);
            renderer.obtainRenderRequest().setColorSpace(dest).draw(Runnable::run, result -> {
                result.getFence().awaitForever();
                latch.countDown();
            });
            try {
                Assert.assertTrue(latch.await(30, TimeUnit.SECONDS));
            } catch (InterruptedException ex) {
                Assert.fail(ex.getMessage());
            }
        }
        Bitmap result = Bitmap.wrapHardwareBuffer(buffer, dest)
                .copy(Bitmap.Config.ARGB_8888, false);

        // Ensure that some pixels are neither white nor black. The emoji include other colors, and
        // if we only see white and black (or gray), then they are not being rendered correctly.
        // (Some glyph shapes may render black even on failure.)
        final float saturationThreshold = 0.01f;
        for (int y = 0; y < canvasSize; y++) {
            for (int x = 0; x < canvasSize; x++) {
                Color color = result.getColor(x, y);
                float[] hsv = new float[3];
                Color.colorToHSV(color.toArgb(), hsv);
                if (hsv[1] > saturationThreshold) {
                    // Success!
                    return;
                }
            }
        }
        // All pixels failed
        BitmapDumper.dumpBitmap(result);
        Assert.fail("Failed to render render glyphs from multiple atlases while a colorspace"
                        + " conversion was set. All pixels were either white or black.");
    }

    private void drawAsset(@NonNull Canvas canvas, Bitmap bitmap) {
        // Render bitmap directly
        canvas.save();
        canvas.clipRect(0, 0, 32, 32);
        canvas.drawBitmap(bitmap, 0, 0, null);
        canvas.restore();

        // Render bitmap via shader
        Paint p = new Paint();
        p.setShader(new BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
        canvas.drawRect(32.0f, 0.0f, 64.0f, 32.0f, p);

        // Render bitmap via shader using another bitmap as a mask
        canvas.save();
        canvas.clipRect(0, 32, 64, 48);
        canvas.drawBitmap(mMask, 0, 0, p);
        canvas.restore();

        // Render bitmap with alpha to test modulation
        p.setShader(null);
        p.setAlpha(127);
        canvas.save();
        canvas.clipRect(0, 48, 64, 64);
        canvas.drawColor(0xffff0000);
        canvas.drawBitmap(bitmap, 0, 0, p);
        canvas.restore();
    }

    @Nullable
    private Bitmap loadAsset(@NonNull String assetName, @NonNull Bitmap.Config config) {
        Bitmap bitmap;
        AssetManager assets = getActivity().getResources().getAssets();
        try (InputStream in = assets.open(assetName)) {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inPreferredConfig = config;

            bitmap = BitmapFactory.decodeStream(in, null, opts);
        } catch (IOException e) {
            throw new RuntimeException("Test failed: ", e);
        }
        return bitmap;
    }

    private void drawTranslucentAsset(@NonNull Canvas canvas, Bitmap bitmap) {
        canvas.drawBitmap(bitmap, 0, 0, null);
    }

    @NonNull
    private static Point point(int x, int y) {
        return new Point(x, y);
    }

    private static Color transformViaBitmap(@ColorLong long source, ColorSpace dest) {
        Bitmap bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.RGBA_F16, false, dest);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(source);
        return bitmap.getColor(0, 0);
    }
}
