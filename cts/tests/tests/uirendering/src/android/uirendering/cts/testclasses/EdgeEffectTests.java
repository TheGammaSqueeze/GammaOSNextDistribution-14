/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static android.uirendering.cts.util.MockVsyncHelper.nextFrame;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RecordingCanvas;
import android.graphics.Rect;
import android.graphics.RenderNode;
import android.graphics.drawable.Drawable;
import android.uirendering.cts.bitmapverifiers.ColorVerifier;
import android.uirendering.cts.bitmapverifiers.PerPixelBitmapVerifier;
import android.uirendering.cts.bitmapverifiers.RegionVerifier;
import android.uirendering.cts.testinfrastructure.ActivityTestBase;
import android.uirendering.cts.util.MockVsyncHelper;
import android.view.ContextThemeWrapper;
import android.view.animation.AnimationUtils;
import android.widget.EdgeEffect;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.LargeTest;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class EdgeEffectTests extends ActivityTestBase {
    private static final int WIDTH = 90;
    private static final int HEIGHT = 90;

    private Context mThemeContext;
    private float mPreviousDurationScale;

    interface EdgeEffectInitializer {
        void initialize(EdgeEffect edgeEffect);
    }

    private Context getContext() {
        return mThemeContext;
    }

    @Before
    public void setUp() {
        final Context targetContext = InstrumentationRegistry.getTargetContext();
        mThemeContext = new ContextThemeWrapper(targetContext,
                android.R.style.Theme_Material_Light);
        mPreviousDurationScale = ValueAnimator.getDurationScale();
        ValueAnimator.setDurationScale(1.0f);
    }

    @After
    public void tearDown() {
        ValueAnimator.setDurationScale(mPreviousDurationScale);
    }

    private static class EdgeEffectValidator extends PerPixelBitmapVerifier {
        public float stretch; // in pixels, vertically

        EdgeEffectValidator() {
        }

        @Override
        protected boolean verifyPixel(int x, int y, int observedColor) {
            if (y < HEIGHT / 2) {
                // Top half should always be the top color
                return observedColor == Color.WHITE;
            }

            // This may be either bottom or top color, depending on the stretch
            stretch += Color.red(observedColor) / 255f / WIDTH;
            return true;
        }
    }

    private void assertEdgeEffect(EdgeEffectInitializer initializer) {
        EdgeEffect edgeEffect = new EdgeEffect(getContext());
        edgeEffect.setSize(WIDTH, HEIGHT);
        initializer.initialize(edgeEffect);

        RenderNode renderNode = drawEdgeEffect(edgeEffect, 0, 0f);

        float stretchPixelCount = getStretchDownPixelCount(renderNode);

        // at least 1 pixel stretch
        assertTrue(stretchPixelCount > 1);
    }

    private float getStretchDownPixelCount(
            RenderNode renderNode
    ) {
        EdgeEffectValidator verifier = new EdgeEffectValidator();
        createTest()
                .addCanvasClientWithoutUsingPicture((canvas, width, height) -> {
                    canvas.drawRenderNode(renderNode);
                }, true)
                .runWithVerifier(verifier);

        return verifier.stretch;
    }

    @Test
    public void testOnPull() {
        assertEdgeEffect(edgeEffect -> {
            edgeEffect.onPull(1);
        });
    }

    @Test
    public void testSetSize() {
        EdgeEffect edgeEffect = new EdgeEffect(getContext());
        edgeEffect.setSize(WIDTH, HEIGHT / 2);

        RenderNode renderNode = drawEdgeEffect(edgeEffect, HEIGHT / 2f, 0f);

        float stretchPixelCount = getStretchDownPixelCount(renderNode);

        // The top half is not in the stretched area, so the only thing being stretched is the
        // bottom half
        assertEquals(0f, stretchPixelCount, 0.01f);
    }

    @Test
    public void testIsFinished() {
        EdgeEffect effect = new EdgeEffect(getContext());
        assertTrue(effect.isFinished());
        effect.onPull(0.5f);
        assertFalse(effect.isFinished());
    }

    @Test
    public void testFinish() {
        EdgeEffect effect = new EdgeEffect(getContext());
        effect.onPull(1);
        effect.finish();
        assertTrue(effect.isFinished());

        effect.onAbsorb(1000);
        effect.finish();
        assertFalse(effect.draw(new Canvas()));
    }

    @Test
    public void testGetMaxHeight() {
        EdgeEffect edgeEffect = new EdgeEffect(getContext());
        edgeEffect.setSize(200, 200);
        assertEquals(200, edgeEffect.getMaxHeight());
        edgeEffect.setSize(200, 0);
        assertEquals(0, edgeEffect.getMaxHeight());
    }

    @Test
    public void testDistance() {
        EdgeEffect effect = new EdgeEffect(getContext());

        assertEquals(0f, effect.getDistance(), 0.001f);

        assertEquals(0.1f, effect.onPullDistance(0.1f, 0.5f), 0.001f);

        assertEquals(0.1f, effect.getDistance(), 0.001f);

        assertEquals(-0.05f, effect.onPullDistance(-0.05f, 0.5f), 0.001f);

        assertEquals(0.05f, effect.getDistance(), 0.001f);

        assertEquals(-0.05f, effect.onPullDistance(-0.2f, 0.5f), 0.001f);

        assertEquals(0f, effect.getDistance(), 0.001f);
    }

    @Test
    public void testPullToZeroReleases() {
        EdgeEffect edgeEffect = new EdgeEffect(getContext());
        edgeEffect.setSize(200, 200);
        assertEquals(0.5f, edgeEffect.onPullDistance(0.5f, 0.5f), 0f);
        assertFalse(edgeEffect.isFinished());
        assertEquals(-0.5f, edgeEffect.onPullDistance(-0.5f, 0.5f), 0f);
        assertTrue(edgeEffect.isFinished());
    }

    @Test
    public void testFlingNegativeReleases() throws Throwable {
        MockVsyncHelper.runOnVsyncThread(() -> {
            EdgeEffect edgeEffect = new EdgeEffect(getContext());
            edgeEffect.setSize(WIDTH, HEIGHT);
            assertEquals(0.01f, edgeEffect.onPullDistance(0.01f, 0.5f), 0f);
            assertFalse(edgeEffect.isFinished());
            edgeEffect.onAbsorb(-10000);
            nextFrame();
            drawEdgeEffect(edgeEffect, 0f, 0f);
            // It should have flung past 0 in one frame
            assertTrue(edgeEffect.isFinished());
        });
    }

    /**
     * Can't stretch past 1.0
     */
    @Test
    public void testMaxStretch() throws Throwable {
        EdgeEffect edgeEffect = new EdgeEffect(getContext());
        edgeEffect.setSize(WIDTH, HEIGHT);
        float pulled = edgeEffect.onPullDistance(1f, 0.5f);
        assertEquals(1f, pulled, 0.001f);
        assertEquals(1f, edgeEffect.getDistance(), 0.001f);
        pulled = edgeEffect.onPullDistance(0.1f, 0.5f);
        assertEquals(0.1f, pulled, 0.001f);
        assertEquals(1f, edgeEffect.getDistance(), 0.001f);
    }

    /**
     * A fling past 1.0 results in stopping at 1 and then retracting.
     */
    @Test
    public void testMaxFling() throws Throwable {
        MockVsyncHelper.runOnVsyncThread(() -> {
            EdgeEffect edgeEffect = new EdgeEffect(getContext());
            edgeEffect.setSize(WIDTH, HEIGHT);
            assertEquals(0.99f, edgeEffect.onPullDistance(0.99f, 0.5f), 0.001f);
            edgeEffect.onAbsorb(10000);
            nextFrame();
            drawEdgeEffect(edgeEffect, 0f, 0f);
            assertEquals(1f, edgeEffect.getDistance(), 0.001f);
            nextFrame();
            drawEdgeEffect(edgeEffect, 0f, 0f);
            assertTrue(edgeEffect.getDistance() < 1f);
        });
    }

    private RenderNode drawStretchEffect(float rotation) {
        EdgeEffect edgeEffect = new EdgeEffect(getContext());
        edgeEffect.setSize(WIDTH, HEIGHT);
        edgeEffect.onPullDistance(1f, 0.5f);

        return drawEdgeEffect(edgeEffect, 0, rotation);
    }

    private RenderNode drawEdgeEffect(EdgeEffect edgeEffect, float verticalOffset, float rotation) {
        RenderNode renderNode = new RenderNode("");
        renderNode.setPosition(0, 0, WIDTH, HEIGHT);
        RecordingCanvas recordingCanvas = renderNode.beginRecording();
        Paint paint = new Paint();
        paint.setColor(Color.WHITE);
        recordingCanvas.drawRect(0f, 0f, WIDTH, HEIGHT / 2f, paint);
        paint.setColor(Color.BLACK);
        recordingCanvas.drawRect(0, HEIGHT / 2f, WIDTH, HEIGHT, paint);
        renderNode.endRecording();

        RenderNode outer = new RenderNode("outer");
        outer.setPosition(0, 0, WIDTH, HEIGHT);
        RecordingCanvas outerRecordingCanvas = outer.beginRecording();
        outerRecordingCanvas.drawRenderNode(renderNode);
        recordingCanvas.translate(0f, verticalOffset);
        recordingCanvas.rotate(rotation, WIDTH / 2f, HEIGHT / 2f);
        edgeEffect.draw(outerRecordingCanvas);
        outer.endRecording();
        return outer;
    }

    @Test
    public void testStretchTop() {
        RenderNode renderNode = drawStretchEffect(0f);
        Rect innerRect = new Rect(0, 0, WIDTH, HEIGHT / 2 + 1);
        Rect outerRect = new Rect(0, HEIGHT / 2 + 10, WIDTH, HEIGHT);
        createTest()
                .addCanvasClientWithoutUsingPicture((canvas, width, height) -> {
                    canvas.drawRenderNode(renderNode);
                }, true)
                .runWithVerifier(
                        new RegionVerifier().addVerifier(
                                innerRect,
                                new ColorVerifier(Color.WHITE)
                        ).addVerifier(
                                outerRect,
                                new ColorVerifier(Color.BLACK)
                        ));
    }

    @Test
    public void testStretchBottom() {
        RenderNode renderNode = drawStretchEffect(180f);
        Rect innerRect = new Rect(0, 0, WIDTH, 1);
        Rect outerRect = new Rect(0, (HEIGHT / 2) - 1, WIDTH, HEIGHT / 2);
        createTest()
                .addCanvasClientWithoutUsingPicture((canvas, width, height) -> {
                    canvas.drawRenderNode(renderNode);
                }, true)
                .runWithVerifier(
                        new RegionVerifier().addVerifier(
                                innerRect,
                                new ColorVerifier(Color.WHITE)
                        ).addVerifier(
                                outerRect,
                                new ColorVerifier(Color.BLACK)
                        ));
    }

    @Test
    public void testNoSetSizeCallDoesNotCrash() {
        EdgeEffect edgeEffect = new EdgeEffect(getContext());
        edgeEffect.onPullDistance(1f, 1f);
        edgeEffect.onAbsorb(100);
        edgeEffect.onRelease();

        RenderNode node = new RenderNode("");
        RecordingCanvas canvas = node.beginRecording();
        edgeEffect.draw(canvas);
        node.endRecording();
    }

    @Test
    public void testInvalidPullDistanceDoesNotCrash() {
        EdgeEffect edgeEffect = new EdgeEffect(getContext());
        // Verify that bad inputs to onPull do not crash
        edgeEffect.onPull(Float.NaN, Float.NaN);

        edgeEffect.setSize(TEST_WIDTH, TEST_HEIGHT);
        RenderNode node = new RenderNode("");
        node.setPosition(0, 0, TEST_WIDTH, TEST_HEIGHT);
        RecordingCanvas canvas = node.beginRecording();

        edgeEffect.draw(canvas);
        node.endRecording();
    }

    @Test
    public void testAbsorbThenDrawDoesNotCrash() {
        MockVsyncHelper.runOnVsyncThread(() -> {
            EdgeEffect edgeEffect = new EdgeEffect(getContext());
            edgeEffect.onPullDistance(1f, 1f);
            edgeEffect.onAbsorb(100);
            edgeEffect.onRelease();

            nextFrame();

            edgeEffect.setSize(10, 10);
            RenderNode node = new RenderNode("");
            node.setPosition(0, 0, TEST_WIDTH, TEST_HEIGHT);
            RecordingCanvas canvas = node.beginRecording();
            Paint paint = new Paint();
            paint.setColor(Color.RED);
            canvas.drawRect(0f, 0f, TEST_WIDTH, TEST_HEIGHT, paint);

            canvas.rotate(90, TEST_WIDTH / 2f, TEST_HEIGHT / 2f);
            edgeEffect.draw(canvas);
            node.endRecording();
        });
    }

    @Test
    public void testStretchEffectUsesLayer() {
        // Verify that any overscroll effect leverages a layer for rendering to ensure
        // consistent graphics behavior.
        EdgeEffect effect = new EdgeEffect(getContext());
        OverscrollDrawable drawable = new OverscrollDrawable(effect, Color.CYAN);
        drawable.setBounds(0, 0, TEST_WIDTH, TEST_HEIGHT);

        // Verify that the black background is visible when rendering without overscroll
        createTest()
                .addCanvasClientWithoutUsingPicture((canvas, width, height) -> {
                    canvas.drawColor(Color.BLACK);
                    drawable.setOverscroll(false);
                    drawable.draw(canvas);
                }, true)
                .runWithVerifier(
                        new RegionVerifier().addVerifier(
                                new Rect(0, 0, TEST_WIDTH, TEST_HEIGHT),
                                new ColorVerifier(Color.BLACK)
                        ));

        // Verify cyan color is visible when rendering with overscroll enabled
        createTest()
                .addCanvasClientWithoutUsingPicture((canvas, width, height) -> {
                    canvas.drawColor(Color.BLACK);
                    drawable.setOverscroll(true);
                    drawable.draw(canvas);
                }, true)
                .runWithVerifier(
                        new RegionVerifier().addVerifier(
                                new Rect(0, 0, TEST_WIDTH, TEST_HEIGHT),
                                new ColorVerifier(Color.CYAN)
                        ));
    }

    /**
     * Test drawable class that renders content using {@link BlendMode#DST_OVER} which
     * requires a compositing layer in order to draw pixels, otherwise nothing is drawn.
     */
    private static class OverscrollDrawable extends Drawable {

        private final int mChildColor;

        private final EdgeEffect mEdgeEffect;

        private final RenderNode mParentNode = new RenderNode("");
        private final RenderNode mChildNode = new RenderNode("");

        private boolean mOverscroll = false;

        public OverscrollDrawable(EdgeEffect edgeEffect, int color) {
            mChildColor = color;
            mEdgeEffect = edgeEffect;
        }

        public void setOverscroll(boolean overscroll) {
            mOverscroll = overscroll;
            invalidateSelf();
        }

        protected void onBoundsChange(Rect bounds) {
            super.onBoundsChange(bounds);
            mEdgeEffect.setSize(bounds.width(), bounds.height());
            mParentNode.setPosition(0, 0, bounds.width(), bounds.height());
            mChildNode.setPosition(0, 0, bounds.width(), bounds.height());
        }

        @Override
        public void draw(Canvas canvas) {
            if (mOverscroll) {
                mEdgeEffect.onPullDistance(1.0f, 0.5f);
            } else {
                mEdgeEffect.finish();
            }

            RecordingCanvas parentCanvas = mParentNode.beginRecording();
            {
                RecordingCanvas childCanvas = mChildNode.beginRecording();
                {
                    childCanvas.drawColor(mChildColor, BlendMode.DST_OVER);
                }
                mChildNode.endRecording();

                parentCanvas.drawRenderNode(mChildNode);
                mEdgeEffect.draw(parentCanvas);
            }
            mParentNode.endRecording();

            canvas.drawRenderNode(mParentNode);
        }

        @Override
        public void setAlpha(int alpha) {
            // NO-OP
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            // NO-OP
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    /**
     * A held pull should not retract.
     */
    @Test
    @LargeTest
    public void testStretchPullAndHold() throws Exception {
        MockVsyncHelper.runOnVsyncThread(() -> {
            EdgeEffect edgeEffect = createEdgeEffectWithPull();
            assertEquals(0.25f, edgeEffect.getDistance(), 0.001f);

            // We must wait until the EdgeEffect would normally start receding (167 ms)
            sleepAnimationTime(200);

            // Drawing will cause updates of the distance if it is animating
            RenderNode renderNode = new RenderNode(null);
            Canvas canvas = renderNode.beginRecording();
            edgeEffect.draw(canvas);

            // A glow effect would start receding now, so let's be sure it doesn't:
            sleepAnimationTime(200);
            edgeEffect.draw(canvas);

            // It should not be updating now
            assertEquals(0.25f, edgeEffect.getDistance(), 0.001f);

            // Now let's release it and it should start animating
            edgeEffect.onRelease();

            sleepAnimationTime(20);

            // Now that it should be animating, the draw should update the distance
            edgeEffect.draw(canvas);

            assertTrue(edgeEffect.getDistance() < 0.25f);
        });
    }

    /**
     * It should be possible to catch the stretch effect during an animation.
     */
    @Test
    @LargeTest
    public void testCatchStretchDuringAnimation() throws Exception {
        MockVsyncHelper.runOnVsyncThread(() -> {
            EdgeEffect edgeEffect = createEdgeEffectWithPull();
            assertEquals(0.25f, edgeEffect.getDistance(), 0.001f);
            edgeEffect.onRelease();

            // Wait some time to be sure it is animating away.
            sleepAnimationTime(20);

            // Drawing will cause updates of the distance if it is animating
            RenderNode renderNode = new RenderNode(null);
            Canvas canvas = renderNode.beginRecording();
            edgeEffect.draw(canvas);

            // It should have started retracting. Now catch it.
            float consumed = edgeEffect.onPullDistance(0f, 0.5f);
            assertEquals(0f, consumed, 0f);

            float distanceAfterAnimation = edgeEffect.getDistance();
            assertTrue(distanceAfterAnimation < 0.25f);

            sleepAnimationTime(50);

            // There should be no change once it has been caught.
            edgeEffect.draw(canvas);
            assertEquals(distanceAfterAnimation, edgeEffect.getDistance(), 0f);
        });
    }

    /**
     * When an EdgeEffect is drawn on a non-RecordingCanvas, the animation
     * should immediately end.
     */
    @Test
    public void testStretchOnBitmapCanvas() throws Throwable {
        EdgeEffect edgeEffect = createEdgeEffectWithPull();
        Bitmap bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        edgeEffect.draw(canvas);
        assertTrue(edgeEffect.isFinished());
        assertEquals(0f, edgeEffect.getDistance(), 0f);
    }

    @Test
    public void testEdgeEffectWithAnimationsDisabled() {
        float previousDuration = ValueAnimator.getDurationScale();
        ValueAnimator.setDurationScale(0);
        try {
            EdgeEffect edgeEffect = createEdgeEffectWithPull();
            RenderNode renderNode = new RenderNode("");
            renderNode.setPosition(0, 0, WIDTH, HEIGHT);

            RecordingCanvas recordingCanvas = renderNode.beginRecording();
            Paint paint = new Paint();
            paint.setColor(Color.WHITE);
            recordingCanvas.drawRect(0f, 0f, WIDTH, HEIGHT / 2f, paint);
            paint.setColor(Color.BLACK);
            recordingCanvas.drawRect(0, HEIGHT / 2f, WIDTH, HEIGHT, paint);
            edgeEffect.draw(recordingCanvas);

            renderNode.endRecording();

            assertTrue(edgeEffect.isFinished());
            assertEquals(0f, edgeEffect.getDistance(), 0f);

            Rect innerRect = new Rect(0, 0, WIDTH, HEIGHT / 2);
            Rect outerRect = new Rect(0, HEIGHT / 2, WIDTH, HEIGHT);
            createTest()
                    .addCanvasClientWithoutUsingPicture((canvas, width, height) -> {
                        canvas.drawRenderNode(renderNode);
                    }, true)
                    .runWithVerifier(
                            new RegionVerifier().addVerifier(
                                    innerRect,
                                    new ColorVerifier(Color.WHITE)
                            ).addVerifier(
                                    outerRect,
                                    new ColorVerifier(Color.BLACK)
                            ));
        } finally {
            ValueAnimator.setDurationScale(previousDuration);
        }
    }

    @Test
    public void testEdgeEffectAnimationDisabledMidAnimation() {
        float previousDuration = ValueAnimator.getDurationScale();
        try {
            EdgeEffect edgeEffect = new EdgeEffect(getContext());
            edgeEffect.setSize(WIDTH, HEIGHT);
            edgeEffect.onPullDistance(0.5f, 0.5f);
            edgeEffect.onAbsorb(100);

            // Explicitly disable animations post stretch
            ValueAnimator.setDurationScale(0);

            RenderNode renderNode = new RenderNode("");
            renderNode.setPosition(0, 0, WIDTH, HEIGHT);

            RecordingCanvas recordingCanvas = renderNode.beginRecording();
            Paint paint = new Paint();
            paint.setColor(Color.WHITE);
            recordingCanvas.drawRect(0f, 0f, WIDTH, HEIGHT / 2f, paint);
            paint.setColor(Color.BLACK);
            recordingCanvas.drawRect(0, HEIGHT / 2f, WIDTH, HEIGHT, paint);
            edgeEffect.draw(recordingCanvas);

            renderNode.endRecording();

            assertTrue(edgeEffect.isFinished());
            assertEquals(0f, edgeEffect.getDistance(), 0f);

            Rect innerRect = new Rect(0, 0, WIDTH, HEIGHT / 2);
            Rect outerRect = new Rect(0, HEIGHT / 2, WIDTH, HEIGHT);
            createTest()
                    .addCanvasClientWithoutUsingPicture((canvas, width, height) -> {
                        canvas.drawRenderNode(renderNode);
                    }, true)
                    .runWithVerifier(
                            new RegionVerifier().addVerifier(
                                    innerRect,
                                    new ColorVerifier(Color.WHITE)
                            ).addVerifier(
                                    outerRect,
                                    new ColorVerifier(Color.BLACK)
                            ));
        } finally {
            ValueAnimator.setDurationScale(previousDuration);
        }
    }

    @Test
    public void testOnAborbAfterStretch() throws Throwable {
        EdgeEffect edgeEffect = new EdgeEffect(getContext());
        edgeEffect.setSize(100, 100);
        float distance = edgeEffect.onPullDistance(0.5f, 0.5f);
        edgeEffect.onAbsorb(100);
        assertEquals(distance, edgeEffect.getDistance(), 0.01f);
    }

    private EdgeEffect createEdgeEffectWithPull() {
        EdgeEffect edgeEffect = new EdgeEffect(getContext());
        edgeEffect.setSize(100, 100);
        edgeEffect.onPullDistance(0.25f, 0.5f);
        return edgeEffect;
    }

    /**
     * This sleeps until the {@link AnimationUtils#currentAnimationTimeMillis()} and
     * {@link AnimationUtils#getExpectedPresentationTimeNanos()} changes
     * by at least <code>durationMillis</code> milliseconds. This is useful for EdgeEffect because
     * it uses that mechanism to determine the animation duration.
     *
     * Must be in a {@link MockVsyncHelper#runOnVsyncThread(MockVsyncHelper.CallableVoid)}
     *
     * @param durationMillis The time to sleep in milliseconds.
     */
    private void sleepAnimationTime(long durationMillis) {
        AnimationUtils.lockAnimationClock(
                AnimationUtils.currentAnimationTimeMillis() + durationMillis,
                AnimationUtils.getExpectedPresentationTimeNanos()
                    + (long) ((int) durationMillis * 1e6));
    }

    private interface StretchVerifier {
        void verify(float oldStretch, float newStretch);
    }

    // validates changes to the stretch over the course of an animation
    private void verifyStretch(
            long timeBetweenFrames,
            EdgeEffectInitializer initializer,
            StretchVerifier stretchVerifier
    ) {
        MockVsyncHelper.runOnVsyncThread(() -> {
            EdgeEffect edgeEffect = new EdgeEffect(getContext());
            edgeEffect.setSize(WIDTH, HEIGHT);
            initializer.initialize(edgeEffect);
            RenderNode renderNode1 = drawEdgeEffect(edgeEffect, 0, 0);
            float oldStretch = getStretchDownPixelCount(renderNode1);
            for (int i = 0; i < 3; i++) {
                sleepAnimationTime(timeBetweenFrames);
                RenderNode renderNode2 = drawEdgeEffect(edgeEffect, 0, 0);
                float newStretch = getStretchDownPixelCount(renderNode2);
                stretchVerifier.verify(oldStretch, newStretch);
                oldStretch = newStretch;
            }
        });
    }

    @Test
    public void testOnAbsorb() {
        verifyStretch(
                8, // The spring will bounce back very quickly
                edgeEffect -> edgeEffect.onAbsorb(300),
                (oldStretch, newStretch) -> {
                    assertTrue("Stretch should grow",
                            oldStretch < newStretch);
                }
        );
    }

    @Test
    public void testOnRelease() {
        verifyStretch(
                16,
                edgeEffect -> {
                    edgeEffect.onPull(1);
                    edgeEffect.onRelease();
                }, (oldStretch, newStretch) ->
                        assertTrue("Stretch should decrease", oldStretch > newStretch)
        );
    }
}
