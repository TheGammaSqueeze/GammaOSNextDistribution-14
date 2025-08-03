/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.BlendMode;
import android.graphics.BlendModeColorFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.ColorSpace;
import android.graphics.HardwareBufferRenderer;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.RecordingCanvas;
import android.graphics.Rect;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.hardware.HardwareBuffer;
import android.uirendering.cts.R;
import android.uirendering.cts.bitmapverifiers.BitmapVerifier;
import android.uirendering.cts.bitmapverifiers.BlurPixelVerifier;
import android.uirendering.cts.bitmapverifiers.ColorVerifier;
import android.uirendering.cts.bitmapverifiers.RectVerifier;
import android.uirendering.cts.bitmapverifiers.RegionVerifier;
import android.uirendering.cts.bitmapverifiers.SamplePointVerifier;
import android.uirendering.cts.testinfrastructure.ActivityTestBase;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class RenderNodeTests extends ActivityTestBase {

    @Test
    public void testDefaults() {
        final RenderNode renderNode = new RenderNode(null);
        assertEquals(0, renderNode.getLeft());
        assertEquals(0, renderNode.getRight());
        assertEquals(0, renderNode.getTop());
        assertEquals(0, renderNode.getBottom());
        assertEquals(0, renderNode.getWidth());
        assertEquals(0, renderNode.getHeight());

        assertEquals(0, renderNode.getTranslationX(), 0.01f);
        assertEquals(0, renderNode.getTranslationY(), 0.01f);
        assertEquals(0, renderNode.getTranslationZ(), 0.01f);
        assertEquals(0, renderNode.getElevation(), 0.01f);

        assertEquals(0, renderNode.getRotationX(), 0.01f);
        assertEquals(0, renderNode.getRotationY(), 0.01f);
        assertEquals(0, renderNode.getRotationZ(), 0.01f);

        assertEquals(1, renderNode.getScaleX(), 0.01f);
        assertEquals(1, renderNode.getScaleY(), 0.01f);

        assertEquals(1, renderNode.getAlpha(), 0.01f);

        assertEquals(0, renderNode.getPivotX(), 0.01f);
        assertEquals(0, renderNode.getPivotY(), 0.01f);

        assertEquals(Color.BLACK, renderNode.getAmbientShadowColor());
        assertEquals(Color.BLACK, renderNode.getSpotShadowColor());

        assertEquals(8, renderNode.getCameraDistance(), 0.01f);

        assertTrue(renderNode.isForceDarkAllowed());
        assertTrue(renderNode.hasIdentityMatrix());
        assertTrue(renderNode.getClipToBounds());
        assertFalse(renderNode.getClipToOutline());
        assertFalse(renderNode.isPivotExplicitlySet());
        assertFalse(renderNode.hasDisplayList());
        assertFalse(renderNode.hasOverlappingRendering());
        assertFalse(renderNode.hasShadow());
        assertFalse(renderNode.getUseCompositingLayer());
    }

    @Test
    public void testBasicDraw() {
        final Rect rect = new Rect(10, 10, 80, 80);

        final RenderNode renderNode = new RenderNode("Blue rect");
        renderNode.setPosition(rect.left, rect.top, rect.right, rect.bottom);
        assertEquals(rect.left, renderNode.getLeft());
        assertEquals(rect.top, renderNode.getTop());
        assertEquals(rect.right, renderNode.getRight());
        assertEquals(rect.bottom, renderNode.getBottom());
        renderNode.setClipToBounds(true);

        {
            Canvas canvas = renderNode.beginRecording();
            assertEquals(rect.width(), canvas.getWidth());
            assertEquals(rect.height(), canvas.getHeight());
            assertTrue(canvas.isHardwareAccelerated());
            canvas.drawColor(Color.BLUE);
            renderNode.endRecording();
        }

        assertTrue(renderNode.hasDisplayList());
        assertTrue(renderNode.hasIdentityMatrix());

        createTest()
                .addCanvasClientWithoutUsingPicture((canvas, width, height) -> {
                    canvas.drawRenderNode(renderNode);
                }, true)
                .runWithVerifier(new RectVerifier(Color.WHITE, Color.BLUE, rect));
    }

    @Test
    public void testAlphaOverlappingRendering() {
        final Rect rect = new Rect(10, 10, 80, 80);
        final RenderNode renderNode = new RenderNode(null);
        renderNode.setPosition(rect.left, rect.top, rect.right, rect.bottom);
        renderNode.setHasOverlappingRendering(true);
        assertTrue(renderNode.hasOverlappingRendering());
        {
            Canvas canvas = renderNode.beginRecording();
            canvas.drawColor(Color.RED);
            canvas.drawColor(Color.BLUE);
            renderNode.endRecording();
        }
        renderNode.setAlpha(.5f);
        createTest()
              .addCanvasClientWithoutUsingPicture((canvas, width, height) -> {
                  canvas.drawRenderNode(renderNode);
              }, true)
              .runWithVerifier(new RectVerifier(Color.WHITE, 0xFF8080FF, rect));
    }

    @Test
    public void testAlphaNonOverlappingRendering() {
        final Rect rect = new Rect(10, 10, 80, 80);
        final RenderNode renderNode = new RenderNode(null);
        renderNode.setPosition(rect.left, rect.top, rect.right, rect.bottom);
        renderNode.setHasOverlappingRendering(false);
        assertFalse(renderNode.hasOverlappingRendering());
        {
            Canvas canvas = renderNode.beginRecording();
            canvas.drawColor(Color.RED);
            canvas.drawColor(Color.BLUE);
            renderNode.endRecording();
        }
        renderNode.setAlpha(.5f);
        createTest()
                .addCanvasClientWithoutUsingPicture((canvas, width, height) -> {
                    canvas.drawRenderNode(renderNode);
                }, true)
                .runWithVerifier(new RectVerifier(Color.WHITE, 0xFF8040BF, rect));
    }

    @Test
    public void testUseCompositingLayer() {
        final Rect rect = new Rect(10, 10, 80, 80);
        final RenderNode renderNode = new RenderNode(null);
        renderNode.setPosition(rect.left, rect.top, rect.right, rect.bottom);
        {
            Canvas canvas = renderNode.beginRecording();
            canvas.drawColor(0xFF0000AF);
            renderNode.endRecording();
        }
        // Construct & apply a Y'UV lightness invert color matrix to the layer paint
        Paint paint = new Paint();
        ColorMatrix matrix = new ColorMatrix();
        ColorMatrix tmp = new ColorMatrix();
        matrix.setRGB2YUV();
        tmp.set(new float[] {
                -1f, 0f, 0f, 0f, 255f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
        });
        matrix.postConcat(tmp);
        tmp.setYUV2RGB();
        matrix.postConcat(tmp);
        paint.setColorFilter(new ColorMatrixColorFilter(matrix));
        renderNode.setUseCompositingLayer(true, paint);
        createTest()
                .addCanvasClientWithoutUsingPicture((canvas, width, height) -> {
                    canvas.drawRenderNode(renderNode);
                }, true)
                .runWithVerifier(new RectVerifier(Color.WHITE, 0xFFD7D7FF, rect));
    }

    @Test
    public void testComputeApproximateMemoryUsage() {
        final RenderNode renderNode = new RenderNode("sizeTest");
        assertTrue(renderNode.computeApproximateMemoryUsage() > 500);
        assertTrue(renderNode.computeApproximateMemoryUsage() < 1500);
        long sizeBefore = renderNode.computeApproximateMemoryUsage();
        {
            Canvas canvas = renderNode.beginRecording();
            assertTrue(canvas.isHardwareAccelerated());
            canvas.drawColor(Color.BLUE);
            renderNode.endRecording();
        }
        long sizeAfter = renderNode.computeApproximateMemoryUsage();
        assertTrue(sizeAfter > sizeBefore);
        renderNode.discardDisplayList();
        assertEquals(sizeBefore, renderNode.computeApproximateMemoryUsage());
    }

    @Test
    public void testTranslationGetSet() {
        final RenderNode renderNode = new RenderNode("translation");

        assertTrue(renderNode.hasIdentityMatrix());

        assertFalse(renderNode.setTranslationX(0.0f));
        assertFalse(renderNode.setTranslationY(0.0f));
        assertFalse(renderNode.setTranslationZ(0.0f));

        assertTrue(renderNode.hasIdentityMatrix());

        assertTrue(renderNode.setTranslationX(1.0f));
        assertEquals(1.0f, renderNode.getTranslationX(), 0.0f);
        assertTrue(renderNode.setTranslationY(1.0f));
        assertEquals(1.0f, renderNode.getTranslationY(), 0.0f);
        assertTrue(renderNode.setTranslationZ(1.0f));
        assertEquals(1.0f, renderNode.getTranslationZ(), 0.0f);

        assertFalse(renderNode.hasIdentityMatrix());

        assertTrue(renderNode.setTranslationX(0.0f));
        assertTrue(renderNode.setTranslationY(0.0f));
        assertTrue(renderNode.setTranslationZ(0.0f));

        assertTrue(renderNode.hasIdentityMatrix());
    }

    @Test
    public void testAlphaGetSet() {
        final RenderNode renderNode = new RenderNode("alpha");

        assertFalse(renderNode.setAlpha(1.0f));
        assertTrue(renderNode.setAlpha(.5f));
        assertEquals(.5f, renderNode.getAlpha(), 0.0001f);
        assertTrue(renderNode.setAlpha(1.0f));
    }

    @Test
    public void testRotationGetSet() {
        final RenderNode renderNode = new RenderNode("rotation");

        assertFalse(renderNode.setRotationX(0.0f));
        assertFalse(renderNode.setRotationY(0.0f));
        assertFalse(renderNode.setRotationZ(0.0f));
        assertTrue(renderNode.hasIdentityMatrix());

        assertTrue(renderNode.setRotationX(1.0f));
        assertEquals(1.0f, renderNode.getRotationX(), 0.0f);
        assertTrue(renderNode.setRotationY(1.0f));
        assertEquals(1.0f, renderNode.getRotationY(), 0.0f);
        assertTrue(renderNode.setRotationZ(1.0f));
        assertEquals(1.0f, renderNode.getRotationZ(), 0.0f);
        assertFalse(renderNode.hasIdentityMatrix());

        assertTrue(renderNode.setRotationX(0.0f));
        assertTrue(renderNode.setRotationY(0.0f));
        assertTrue(renderNode.setRotationZ(0.0f));
        assertTrue(renderNode.hasIdentityMatrix());
    }

    @Test
    public void testScaleGetSet() {
        final RenderNode renderNode = new RenderNode("scale");

        assertFalse(renderNode.setScaleX(1.0f));
        assertFalse(renderNode.setScaleY(1.0f));

        assertTrue(renderNode.setScaleX(2.0f));
        assertEquals(2.0f, renderNode.getScaleX(), 0.0f);
        assertTrue(renderNode.setScaleY(2.0f));
        assertEquals(2.0f, renderNode.getScaleY(), 0.0f);

        assertTrue(renderNode.setScaleX(1.0f));
        assertTrue(renderNode.setScaleY(1.0f));
    }

    @Test
    public void testStartEndRecordingEmpty() {
        final RenderNode renderNode = new RenderNode(null);
        assertEquals(0, renderNode.getWidth());
        assertEquals(0, renderNode.getHeight());
        RecordingCanvas canvas = renderNode.beginRecording();
        assertTrue(canvas.isHardwareAccelerated());
        assertEquals(0, canvas.getWidth());
        assertEquals(0, canvas.getHeight());
        renderNode.endRecording();
    }

    @Test
    public void testStartEndRecordingWithBounds() {
        final RenderNode renderNode = new RenderNode(null);
        renderNode.setPosition(10, 20, 30, 50);
        assertEquals(20, renderNode.getWidth());
        assertEquals(30, renderNode.getHeight());
        RecordingCanvas canvas = renderNode.beginRecording();
        assertTrue(canvas.isHardwareAccelerated());
        assertEquals(20, canvas.getWidth());
        assertEquals(30, canvas.getHeight());
        renderNode.endRecording();
    }

    @Test
    public void testStartEndRecordingEmptyWithSize() {
        final RenderNode renderNode = new RenderNode(null);
        assertEquals(0, renderNode.getWidth());
        assertEquals(0, renderNode.getHeight());
        RecordingCanvas canvas = renderNode.beginRecording(5, 10);
        assertTrue(canvas.isHardwareAccelerated());
        assertEquals(5, canvas.getWidth());
        assertEquals(10, canvas.getHeight());
        renderNode.endRecording();
    }

    @Test
    public void testStartEndRecordingWithBoundsWithSize() {
        final RenderNode renderNode = new RenderNode(null);
        renderNode.setPosition(10, 20, 30, 50);
        assertEquals(20, renderNode.getWidth());
        assertEquals(30, renderNode.getHeight());
        RecordingCanvas canvas = renderNode.beginRecording(5, 10);
        assertTrue(canvas.isHardwareAccelerated());
        assertEquals(5, canvas.getWidth());
        assertEquals(10, canvas.getHeight());
        renderNode.endRecording();
    }

    @Test
    public void testGetUniqueId() {
        final RenderNode r1 = new RenderNode(null);
        final RenderNode r2 = new RenderNode(null);
        assertNotEquals(r1.getUniqueId(), r2.getUniqueId());
        final Set<Long> usedIds = new HashSet<>();
        assertTrue(usedIds.add(r1.getUniqueId()));
        assertTrue(usedIds.add(r2.getUniqueId()));
        for (int i = 0; i < 100; i++) {
            assertTrue(usedIds.add(new RenderNode(null).getUniqueId()));
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidCameraDistance() {
        final RenderNode renderNode = new RenderNode(null);
        renderNode.setCameraDistance(-1f);
    }

    @Test
    public void testCameraDistanceSetGet() {
        final RenderNode renderNode = new RenderNode(null);
        renderNode.setCameraDistance(100f);
        assertEquals(100f, renderNode.getCameraDistance(), 0.0f);
    }

    @Test
    public void testBitmapRenderEffect() {
        Bitmap bitmap = Bitmap.createBitmap(TEST_WIDTH, TEST_HEIGHT, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.BLUE);

        final RenderNode renderNode = new RenderNode(null);
        renderNode.setRenderEffect(RenderEffect.createBitmapEffect(bitmap));
        renderNode.setPosition(0, 0, TEST_WIDTH, TEST_HEIGHT);
        {
            Canvas recordingCanvas = renderNode.beginRecording();
            // must have at least 1 drawing instruction
            recordingCanvas.drawColor(Color.TRANSPARENT);
            renderNode.endRecording();
        }
        createTest()
                .addCanvasClientWithoutUsingPicture((canvas, width, height) -> {
                    canvas.drawRenderNode(renderNode);
                }, true)
                .runWithVerifier(new ColorVerifier(Color.BLUE));
    }

    @Test
    public void testOffsetImplicitInputRenderEffect() {
        final int offsetX = 20;
        final int offsetY = 20;
        RenderEffect offsetEffect = RenderEffect.createOffsetEffect(offsetX, offsetY);
        final RenderNode renderNode = new RenderNode(null);
        renderNode.setRenderEffect(offsetEffect);
        renderNode.setPosition(0, 0, TEST_WIDTH, TEST_HEIGHT);
        {
            Canvas recordingCanvas = renderNode.beginRecording();
            Paint paint = new Paint();
            paint.setColor(Color.BLUE);
            recordingCanvas.drawRect(0, 0, TEST_WIDTH, TEST_HEIGHT, paint);
            renderNode.endRecording();
        }

        createTest()
                .addCanvasClientWithoutUsingPicture((canvas, width, height) -> {
                    Paint canvasClientPaint = new Paint();
                    canvasClientPaint.setColor(Color.RED);
                    canvas.drawRect(0, 0, width, height, canvasClientPaint);
                    canvas.drawRenderNode(renderNode);
                }, true)
                .runWithVerifier(
                        new RegionVerifier()
                                .addVerifier(
                                        new Rect(
                                                0,
                                                0,
                                                TEST_WIDTH - 1,
                                                offsetY - 1
                                        ),
                                        new ColorVerifier(Color.RED)
                                )
                                .addVerifier(
                                        new Rect(
                                                offsetX + 1,
                                                offsetY + 1,
                                                TEST_WIDTH - 1,
                                                TEST_HEIGHT - 1),
                                        new ColorVerifier(Color.BLUE)
                                )
                                .addVerifier(
                                        new Rect(
                                                0,
                                                0,
                                                offsetX - 1,
                                                TEST_HEIGHT - 1
                                        ),
                                        new ColorVerifier(Color.RED)
                        )
            );
    }

    @Test
    public void testColorFilterRenderEffectImplicitInput() {
        RenderEffect colorFilterEffect = RenderEffect.createColorFilterEffect(
                new BlendModeColorFilter(Color.RED, BlendMode.SRC_OVER));
        final RenderNode renderNode = new RenderNode(null);
        renderNode.setRenderEffect(colorFilterEffect);
        renderNode.setPosition(0, 0, TEST_WIDTH, TEST_HEIGHT);
        {
            Canvas recordingCanvas = renderNode.beginRecording();
            Paint paint = new Paint();
            paint.setColor(Color.BLUE);
            recordingCanvas.drawRect(0, 0, TEST_WIDTH, TEST_HEIGHT, paint);
            renderNode.endRecording();
        }

        createTest()
                .addCanvasClientWithoutUsingPicture((canvas, width, height) -> {
                    canvas.drawRenderNode(renderNode);
                }, true)
                .runWithVerifier(new ColorVerifier(Color.RED));
    }

    @Test
    public void testBlendModeRenderEffectImplicitInput() {
        Bitmap srcBitmap = Bitmap.createBitmap(TEST_WIDTH, TEST_HEIGHT, Bitmap.Config.ARGB_8888);
        srcBitmap.eraseColor(Color.BLUE);

        Bitmap dstBitmap = Bitmap.createBitmap(TEST_WIDTH, TEST_HEIGHT, Bitmap.Config.ARGB_8888);
        dstBitmap.eraseColor(Color.RED);

        RenderEffect colorFilterEffect = RenderEffect.createBlendModeEffect(
                RenderEffect.createBitmapEffect(dstBitmap),
                RenderEffect.createBitmapEffect(srcBitmap),
                BlendMode.SRC
        );

        final RenderNode renderNode = new RenderNode(null);
        renderNode.setRenderEffect(colorFilterEffect);
        renderNode.setPosition(0, 0, TEST_WIDTH, TEST_HEIGHT);
        {
            Canvas recordingCanvas = renderNode.beginRecording();
            recordingCanvas.drawColor(Color.TRANSPARENT);
            renderNode.endRecording();
        }

        createTest()
                .addCanvasClientWithoutUsingPicture((canvas, width, height) -> {
                    canvas.drawRenderNode(renderNode);
                }, true)
                .runWithVerifier(new ColorVerifier(Color.BLUE));
    }

    @Test
    public void testColorFilterRenderEffect() {
        Bitmap bitmap = Bitmap.createBitmap(TEST_WIDTH, TEST_HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas bitmapCanvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setColor(Color.BLUE);
        bitmapCanvas.drawRect(0, 0, TEST_WIDTH, TEST_HEIGHT, paint);

        RenderEffect bitmapEffect = RenderEffect.createBitmapEffect(
                bitmap, null, new Rect(0, 0, TEST_WIDTH, TEST_HEIGHT));

        RenderEffect colorFilterEffect = RenderEffect.createColorFilterEffect(
                new BlendModeColorFilter(Color.RED, BlendMode.SRC_OVER), bitmapEffect);
        final RenderNode renderNode = new RenderNode(null);
        renderNode.setRenderEffect(colorFilterEffect);
        renderNode.setPosition(0, 0, TEST_WIDTH, TEST_HEIGHT);
        {
            Canvas recordingCanvas = renderNode.beginRecording();
            Paint renderNodePaint = new Paint();
            recordingCanvas.drawRect(0, 0, TEST_WIDTH, TEST_HEIGHT, renderNodePaint);
            renderNode.endRecording();
        }

        createTest()
                .addCanvasClientWithoutUsingPicture((canvas, width, height) -> {
                    canvas.drawRenderNode(renderNode);
                }, true)
                .runWithVerifier(new ColorVerifier(Color.RED));
    }

    @Test
    public void testOffsetRenderEffect() {
        Bitmap bitmap = Bitmap.createBitmap(TEST_WIDTH, TEST_HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas bitmapCanvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setColor(Color.BLUE);
        bitmapCanvas.drawRect(0, 0, bitmap.getWidth(), bitmap.getHeight(), paint);

        final int offsetX = 20;
        final int offsetY = 20;
        RenderEffect bitmapEffect = RenderEffect.createBitmapEffect(bitmap);
        RenderEffect offsetEffect = RenderEffect.createOffsetEffect(offsetX, offsetY, bitmapEffect);
        final RenderNode renderNode = new RenderNode(null);
        renderNode.setRenderEffect(offsetEffect);
        renderNode.setPosition(0, 0, TEST_WIDTH, TEST_HEIGHT);
        {
            Canvas recordingCanvas = renderNode.beginRecording();
            recordingCanvas.drawRect(0, 0, TEST_WIDTH, TEST_HEIGHT, new Paint());
            renderNode.endRecording();
        }

        createTest()
                .addCanvasClientWithoutUsingPicture((canvas, width, height) -> {
                    Paint canvasClientPaint = new Paint();
                    canvasClientPaint.setColor(Color.RED);
                    canvas.drawRect(0, 0, width, height, canvasClientPaint);
                    canvas.drawRenderNode(renderNode);
                }, true)
                .runWithVerifier(
                        new RegionVerifier()
                                .addVerifier(
                                        new Rect(0, 0, TEST_WIDTH - 1, offsetY - 1),
                                        new ColorVerifier(Color.RED)
                                )
                                .addVerifier(
                                        new Rect(
                                                offsetX + 1,
                                                offsetY + 1,
                                                TEST_WIDTH - 1,
                                                TEST_HEIGHT - 1
                                        ),
                                        new ColorVerifier(Color.BLUE)
                                )
                                .addVerifier(
                                        new Rect(0, 0, offsetX - 1, TEST_HEIGHT - 1),
                                        new ColorVerifier(Color.RED)
                                )
            );
    }

    @Test
    public void testViewRenderNodeBlurEffect() {
        final int blurRadius = 10;
        final Rect fullBounds = new Rect(0, 0, TEST_WIDTH, TEST_HEIGHT);
        final Rect insetBounds = new Rect(blurRadius, blurRadius, TEST_WIDTH - blurRadius,
                TEST_HEIGHT - blurRadius);

        final Rect unblurredBounds = new Rect(insetBounds);
        unblurredBounds.inset(blurRadius, blurRadius);
        createTest()
                .addLayout(R.layout.frame_layout, (view) -> {
                    FrameLayout root = view.findViewById(R.id.frame_layout);
                    View innerView = new View(view.getContext());
                    innerView.setLayoutParams(
                            new FrameLayout.LayoutParams(TEST_WIDTH, TEST_HEIGHT));
                    innerView.setBackground(new TestDrawable());
                    root.addView(innerView);
                }, true)
                .runWithVerifier(
                        new RegionVerifier()
                                .addVerifier(
                                        unblurredBounds,
                                        new ColorVerifier(Color.BLUE))
                                .addVerifier(
                                        fullBounds,
                                        new BlurPixelVerifier(Color.BLUE, Color.WHITE)
                                )
            );
    }

    @Test
    public void testRenderEffectOnParentInvalidatesWhenChildChanges() {
        final CountDownLatch latch = new CountDownLatch(1);
        createTest()
                .addLayout(R.layout.frame_layout, (view) -> {
                    FrameLayout root = view.findViewById(R.id.frame_layout);
                    root.setRenderEffect(
                            RenderEffect.createBlurEffect(
                                    30f, 30f, null, Shader.TileMode.CLAMP)
                    );
                    View innerView = new View(view.getContext());
                    innerView.setLayoutParams(
                            new FrameLayout.LayoutParams(TEST_WIDTH, TEST_HEIGHT));
                    innerView.setBackgroundColor(Color.BLUE);
                    root.addView(innerView);
                    root.getViewTreeObserver().registerFrameCommitCallback(
                            new Runnable() {
                                @Override
                                public void run() {
                                    innerView.setBackgroundColor(Color.RED);
                                    latch.countDown();
                                    root.getViewTreeObserver().unregisterFrameCommitCallback(this);
                                }
                            }
                    );
                }, true, latch)
                .runWithVerifier(new ColorVerifier(Color.RED)
            );
    }

    private static class TestDrawable extends Drawable {

        private final Paint mPaint = new Paint();

        @Override
        public void draw(@NonNull Canvas canvas) {
            mPaint.setColor(Color.WHITE);

            Rect rect = getBounds();
            canvas.drawRect(rect, mPaint);
            mPaint.setColor(Color.BLUE);

            canvas.drawRect(
                    10,
                    10,
                    rect.right - 10,
                    rect.bottom - 10,
                    mPaint
            );
        }

        @Override
        public void setAlpha(int alpha) {
            // No-op
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            // No-op
        }

        @Override
        public int getOpacity() {
            return 0;
        }
    }

    @Test
    public void testBlurRenderEffectImplicitInput() {
        final int blurRadius = 10;
        final Rect fullBounds = new Rect(0, 0, TEST_WIDTH, TEST_HEIGHT);
        final Rect insetBounds = new Rect(blurRadius, blurRadius, TEST_WIDTH - blurRadius,
                TEST_HEIGHT - blurRadius);

        final RenderNode renderNode = new RenderNode(null);
        renderNode.setRenderEffect(
                RenderEffect.createBlurEffect(
                        blurRadius,
                        blurRadius,
                        Shader.TileMode.DECAL
                )
        );
        renderNode.setPosition(0, 0, TEST_WIDTH, TEST_HEIGHT);
        {
            Canvas canvas = renderNode.beginRecording();
            Paint paint = new Paint();
            paint.setColor(Color.WHITE);
            canvas.drawRect(fullBounds, paint);

            paint.setColor(Color.BLUE);

            canvas.drawRect(insetBounds, paint);
            renderNode.endRecording();
        }

        final Rect unblurredBounds = new Rect(insetBounds);
        unblurredBounds.inset(blurRadius, blurRadius);
        createTest()
                .addCanvasClientWithoutUsingPicture((canvas, width, height) -> {
                    canvas.drawRenderNode(renderNode);
                }, true)
                .runWithVerifier(
                        new RegionVerifier()
                                .addVerifier(
                                        unblurredBounds,
                                        new ColorVerifier(Color.BLUE))
                                .addVerifier(
                                        fullBounds,
                                        new BlurPixelVerifier(Color.BLUE, Color.WHITE)
                                )
            );
    }

    @Test
    public void testBlurRenderEffect() {
        final int blurRadius = 10;
        final Rect fullBounds = new Rect(0, 0, TEST_WIDTH, TEST_HEIGHT);
        final Rect insetBounds = new Rect(blurRadius, blurRadius, TEST_WIDTH - blurRadius,
                TEST_HEIGHT - blurRadius);

        final RenderNode renderNode = new RenderNode(null);
        renderNode.setRenderEffect(
                RenderEffect.createBlurEffect(
                        blurRadius,
                        blurRadius,
                        null,
                        Shader.TileMode.DECAL
                )
        );
        renderNode.setPosition(0, 0, TEST_WIDTH, TEST_HEIGHT);
        {
            Canvas canvas = renderNode.beginRecording();
            Paint paint = new Paint();
            paint.setColor(Color.WHITE);
            canvas.drawRect(fullBounds, paint);

            paint.setColor(Color.BLUE);

            canvas.drawRect(insetBounds, paint);
            renderNode.endRecording();
        }

        final Rect unblurredBounds = new Rect(insetBounds);
        unblurredBounds.inset(blurRadius, blurRadius);
        createTest()
                .addCanvasClientWithoutUsingPicture((canvas, width, height) -> {
                    canvas.drawRenderNode(renderNode);
                }, true)
                .runWithVerifier(
                        new RegionVerifier()
                                .addVerifier(
                                        unblurredBounds,
                                        new ColorVerifier(Color.BLUE))
                                .addVerifier(
                                        fullBounds,
                                        new BlurPixelVerifier(Color.BLUE, Color.WHITE)
                                )
            );
    }

    @Test
    public void testChainRenderEffect() {
        Bitmap bitmap = Bitmap.createBitmap(TEST_WIDTH, TEST_HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas bitmapCanvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.BLUE);
        bitmapCanvas.drawRect(0, 0, bitmap.getWidth(), bitmap.getHeight(), paint);

        final int offsetX = 20;
        final int offsetY = 20;
        RenderEffect bitmapEffect = RenderEffect.createBitmapEffect(bitmap);
        RenderEffect offsetEffect = RenderEffect.createOffsetEffect(offsetX, offsetY);
        RenderEffect chainEffect = RenderEffect.createChainEffect(offsetEffect, bitmapEffect);
        final RenderNode renderNode = new RenderNode(null);
        renderNode.setRenderEffect(chainEffect);
        renderNode.setPosition(0, 0, TEST_WIDTH, TEST_HEIGHT);
        {
            Canvas recordingCanvas = renderNode.beginRecording();
            recordingCanvas.drawRect(0, 0, TEST_WIDTH, TEST_HEIGHT, new Paint());
            renderNode.endRecording();
        }

        createTest()
                .addCanvasClientWithoutUsingPicture((canvas, width, height) -> {
                    Paint canvasClientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    canvasClientPaint.setColor(Color.RED);
                    canvas.drawRect(0, 0, width, height, canvasClientPaint);
                    canvas.drawRenderNode(renderNode);
                }, true)
                .runWithVerifier(
                        new RegionVerifier()
                                .addVerifier(
                                    new Rect(
                                            0,
                                            0,
                                            TEST_WIDTH - 1,
                                            offsetY - 1
                                    ),
                                    new ColorVerifier(Color.RED)
                                )
                                .addVerifier(
                                        new Rect(
                                                offsetX + 1,
                                                offsetY + 1,
                                                TEST_WIDTH - 1,
                                                TEST_HEIGHT - 1
                                        ),
                                        new ColorVerifier(Color.BLUE)
                                )
                                .addVerifier(
                                        new Rect(0, 0, offsetX - 1, TEST_HEIGHT - 1),
                                        new ColorVerifier(Color.RED)
                                )
            );
    }

    @Test
    public void testShaderRenderEffect() {
        LinearGradient gradient = new LinearGradient(
                0f, 0f,
                0f, TEST_HEIGHT,
                new int[] { Color.RED, Color.BLUE },
                null,
                Shader.TileMode.CLAMP
        );

        RenderEffect shaderEffect = RenderEffect.createShaderEffect(gradient);
        final RenderNode renderNode = new RenderNode(null);
        renderNode.setRenderEffect(shaderEffect);
        renderNode.setPosition(0, 0, TEST_WIDTH, TEST_HEIGHT);
        {
            Canvas recordingCanvas = renderNode.beginRecording();
            recordingCanvas.drawRect(0, 0, TEST_WIDTH, TEST_HEIGHT, new Paint());
            renderNode.endRecording();
        }

        createTest()
                .addCanvasClientWithoutUsingPicture((canvas, width, height) -> {
                    canvas.drawRenderNode(renderNode);
                }, true)
                .runWithVerifier(
                    new SamplePointVerifier(
                            new Point[] {
                                    new Point(0, 0),
                                    new Point(0, TEST_HEIGHT - 1)
                            },
                            new int[] { Color.RED, Color.BLUE }
                    )
            );
    }

    private static String sRedBlueInversionShader = ""
            + "uniform shader inputShader;"
            +  "vec4 main(vec2 coord) { "
            + "  vec4 color = inputShader.eval(coord);"
            + "  return vec4(color.b, color.g, color.r, color.a);"
            + "}";

    @Test(expected = IllegalArgumentException.class)
    public void testRuntimeShaderRenderEffectInvalidUinformName() {
        RuntimeShader shader = new RuntimeShader(sRedBlueInversionShader);
        RenderEffect runtimeEffect = RenderEffect.createRuntimeShaderEffect(
                shader, "invalidUniformName");
    }

    @Test
    public void testRuntimeShaderRenderEffect() {
        RuntimeShader shader = new RuntimeShader(sRedBlueInversionShader);
        RenderEffect runtimeEffect = RenderEffect.createRuntimeShaderEffect(
                shader, "inputShader");
        final RenderNode renderNode = new RenderNode(null);
        renderNode.setRenderEffect(runtimeEffect);
        renderNode.setPosition(0, 0, TEST_WIDTH, TEST_HEIGHT);
        {
            Canvas recordingCanvas = renderNode.beginRecording();
            Paint paint = new Paint();
            paint.setColor(Color.BLUE);
            recordingCanvas.drawRect(0, 0, TEST_WIDTH, TEST_HEIGHT, paint);
            renderNode.endRecording();
        }

        createTest()
                .addCanvasClientWithoutUsingPicture((canvas, width, height) -> {
                    canvas.drawRenderNode(renderNode);
                }, true)
                .runWithVerifier(new ColorVerifier(Color.RED));
    }

    @Test
    public void testBlurShaderLargeRadiiEdgeReplication() {
        final int blurRadius = 200;
        final int left = 0;
        final int top = 0;
        final int right = TEST_WIDTH;
        final int bottom = TEST_HEIGHT;
        final RenderNode renderNode = new RenderNode(null);
        renderNode.setRenderEffect(
                RenderEffect.createBlurEffect(
                        blurRadius,
                        blurRadius,
                        null,
                        Shader.TileMode.CLAMP
                )
        );
        renderNode.setPosition(left, top, right, bottom);
        {
            Canvas canvas = renderNode.beginRecording();
            Paint blurPaint = new Paint();
            blurPaint.setColor(Color.BLUE);
            canvas.save();
            canvas.clipRect(left, top, right, bottom);
            canvas.drawRect(left, top, right, bottom, blurPaint);
            canvas.restore();
            renderNode.endRecording();
        }
        // Ensure that blurring with large blur radii with clipped content shows a solid
        // blur square.
        // Previously blur radii that were very large would end up blurring pixels outside
        // of the source with transparent leading to larger blur radii actually being less
        // blurred than smaller radii.
        // Because the internal SkTileMode is set to kClamp, the edges of the source are used in
        // blur kernels that extend beyond the bounds of the source
        createTest()
                .addCanvasClientWithoutUsingPicture((canvas, width, height) -> {
                    canvas.drawRenderNode(renderNode);
                }, true)
                .runWithVerifier(new ColorVerifier(Color.BLUE));
    }

    static void assertRender(RenderNode root, BitmapVerifier verifier) throws Exception {
        HardwareBuffer buffer = HardwareBuffer.create(TEST_WIDTH, TEST_HEIGHT,
                HardwareBuffer.RGBA_8888, 1,
                HardwareBuffer.USAGE_GPU_COLOR_OUTPUT | HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE);
        ColorSpace colorSpace = ColorSpace.get(ColorSpace.Named.SRGB);
        HardwareBufferRenderer renderer = new HardwareBufferRenderer(buffer);
        renderer.setContentRoot(root);
        CountDownLatch fence = new CountDownLatch(1);
        HardwareBufferRenderer.RenderResult[] renderResult =
                new HardwareBufferRenderer.RenderResult[1];
        renderer.obtainRenderRequest()
                .setColorSpace(colorSpace)
                .draw(Runnable::run, result -> {
                    renderResult[0] = result;
                    fence.countDown();
                });
        assertTrue(fence.await(5, TimeUnit.SECONDS));
        assertEquals(HardwareBufferRenderer.RenderResult.SUCCESS, renderResult[0].getStatus());
        assertTrue(renderResult[0].getFence().await(Duration.ofSeconds(5)));
        Bitmap hwResult = Bitmap.wrapHardwareBuffer(buffer, colorSpace);
        Bitmap result = hwResult.copy(Bitmap.Config.ARGB_8888, false);
        assertTrue(verifier.verify(result));
    }

    @Test
    public void testUpdateModel() throws Exception {
        // Create a simple tree in the shape of
        //           root
        //          /    \
        //       rootA   rootB
        //              /     \
        //           rootB1  rootB2
        RenderNode root = new RenderNode("root");
        root.setPosition(0, 0, TEST_WIDTH, TEST_HEIGHT);
        RenderNode rootA = new RenderNode("root-A");
        rootA.setPosition(0, 0, TEST_WIDTH / 2, TEST_HEIGHT);
        RenderNode rootB = new RenderNode("root-B");
        rootB.setPosition(0, 0, TEST_WIDTH, TEST_HEIGHT);
        RenderNode rootB1 = new RenderNode("root-B-1");
        rootB1.setPosition(TEST_WIDTH / 2, 0, TEST_WIDTH, TEST_HEIGHT / 2);
        RenderNode rootB2 = new RenderNode("root-B-2");
        rootB2.setPosition(TEST_WIDTH / 2, TEST_HEIGHT / 2, TEST_WIDTH, TEST_HEIGHT);

        RecordingCanvas canvas = null;
        canvas = root.beginRecording();
        canvas.drawRenderNode(rootA);
        canvas.drawRenderNode(rootB);
        root.endRecording();

        canvas = rootB.beginRecording();
        canvas.drawRenderNode(rootB1);
        canvas.drawRenderNode(rootB2);
        rootB.endRecording();

        canvas = rootA.beginRecording();
        canvas.drawColor(Color.RED);
        rootA.endRecording();

        canvas = rootB1.beginRecording();
        canvas.drawColor(Color.RED);
        rootB1.endRecording();

        canvas = rootB2.beginRecording();
        canvas.drawColor(Color.RED);
        rootB2.endRecording();

        assertRender(root, new ColorVerifier(Color.RED));

        canvas = rootB2.beginRecording();
        canvas.drawColor(Color.BLUE);
        rootB2.endRecording();

        assertRender(root, new RectVerifier(Color.RED, Color.BLUE,
                new Rect(TEST_WIDTH / 2, TEST_HEIGHT / 2, TEST_WIDTH, TEST_HEIGHT)));

        canvas = rootB1.beginRecording();
        canvas.drawColor(Color.BLUE);
        rootB1.endRecording();

        assertRender(root, new RectVerifier(Color.RED, Color.BLUE,
                new Rect(TEST_WIDTH / 2, 0, TEST_WIDTH, TEST_HEIGHT)));

        canvas = rootB.beginRecording();
        canvas.drawColor(Color.GREEN);
        rootB.endRecording();
        canvas = rootA.beginRecording();
        canvas.drawColor(Color.GREEN);
        rootA.endRecording();

        assertRender(root, new ColorVerifier(Color.GREEN));
    }
}
