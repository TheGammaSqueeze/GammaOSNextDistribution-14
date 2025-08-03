/*
 * Copyright 2018 The Android Open Source Project
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

package android.view.surfacecontrol.cts;

import static android.server.wm.ActivityManagerTestBase.createFullscreenActivityScenarioRule;
import static android.view.cts.surfacevalidator.ASurfaceControlTestActivity.RectChecker;
import static android.view.cts.surfacevalidator.ASurfaceControlTestActivity.WAIT_TIMEOUT_S;
import static android.view.cts.util.ASurfaceControlTestUtils.applyAndDeleteSurfaceTransaction;
import static android.view.cts.util.ASurfaceControlTestUtils.createSurfaceTransaction;
import static android.view.cts.util.ASurfaceControlTestUtils.nSurfaceControl_acquire;
import static android.view.cts.util.ASurfaceControlTestUtils.nSurfaceControl_create;
import static android.view.cts.util.ASurfaceControlTestUtils.nSurfaceControl_createFromWindow;
import static android.view.cts.util.ASurfaceControlTestUtils.nSurfaceControl_fromJava;
import static android.view.cts.util.ASurfaceControlTestUtils.nSurfaceControl_release;
import static android.view.cts.util.ASurfaceControlTestUtils.nSurfaceTransaction_apply;
import static android.view.cts.util.ASurfaceControlTestUtils.nSurfaceTransaction_create;
import static android.view.cts.util.ASurfaceControlTestUtils.nSurfaceTransaction_delete;
import static android.view.cts.util.ASurfaceControlTestUtils.nSurfaceTransaction_fromJava;
import static android.view.cts.util.ASurfaceControlTestUtils.nSurfaceTransaction_releaseBuffer;
import static android.view.cts.util.ASurfaceControlTestUtils.nSurfaceTransaction_setBuffer;
import static android.view.cts.util.ASurfaceControlTestUtils.nSurfaceTransaction_setDamageRegion;
import static android.view.cts.util.ASurfaceControlTestUtils.nSurfaceTransaction_setDataSpace;
import static android.view.cts.util.ASurfaceControlTestUtils.nSurfaceTransaction_setDesiredHdrHeadroom;
import static android.view.cts.util.ASurfaceControlTestUtils.nSurfaceTransaction_setDesiredPresentTime;
import static android.view.cts.util.ASurfaceControlTestUtils.nSurfaceTransaction_setExtendedRangeBrightness;
import static android.view.cts.util.ASurfaceControlTestUtils.nSurfaceTransaction_setFrameTimeline;
import static android.view.cts.util.ASurfaceControlTestUtils.nSurfaceTransaction_setOnCommitCallback;
import static android.view.cts.util.ASurfaceControlTestUtils.nSurfaceTransaction_setOnCommitCallbackWithoutContext;
import static android.view.cts.util.ASurfaceControlTestUtils.nSurfaceTransaction_setOnCompleteCallback;
import static android.view.cts.util.ASurfaceControlTestUtils.nSurfaceTransaction_setOnCompleteCallbackWithoutContext;
import static android.view.cts.util.ASurfaceControlTestUtils.nSurfaceTransaction_setPosition;
import static android.view.cts.util.ASurfaceControlTestUtils.nSurfaceTransaction_setQuadrantBuffer;
import static android.view.cts.util.ASurfaceControlTestUtils.nSurfaceTransaction_setSolidBuffer;
import static android.view.cts.util.ASurfaceControlTestUtils.reparent;
import static android.view.cts.util.ASurfaceControlTestUtils.setBufferAlpha;
import static android.view.cts.util.ASurfaceControlTestUtils.setBufferOpaque;
import static android.view.cts.util.ASurfaceControlTestUtils.setBufferTransform;
import static android.view.cts.util.ASurfaceControlTestUtils.setColor;
import static android.view.cts.util.ASurfaceControlTestUtils.setCrop;
import static android.view.cts.util.ASurfaceControlTestUtils.setGeometry;
import static android.view.cts.util.ASurfaceControlTestUtils.setPosition;
import static android.view.cts.util.ASurfaceControlTestUtils.setScale;
import static android.view.cts.util.ASurfaceControlTestUtils.setVisibility;
import static android.view.cts.util.ASurfaceControlTestUtils.setZOrder;
import static android.view.cts.util.FrameCallbackData.nGetFrameTimelines;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.hardware.DataSpace;
import android.os.SystemClock;
import android.os.Trace;
import android.platform.test.annotations.RequiresDevice;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceControl;
import android.view.SurfaceHolder;
import android.view.cts.surfacevalidator.ASurfaceControlTestActivity;
import android.view.cts.surfacevalidator.ASurfaceControlTestActivity.PixelChecker;
import android.view.cts.surfacevalidator.PixelColor;
import android.view.cts.util.ASurfaceControlTestUtils;
import android.view.cts.util.FrameCallbackData;
import android.view.cts.util.FrameCallbackData.FrameTimeline;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.WidgetTestUtils;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

import java.lang.ref.Reference;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class ASurfaceControlTest {
    private static final String TAG = ASurfaceControlTest.class.getSimpleName();
    private static final boolean DEBUG = false;

    private static final int DEFAULT_LAYOUT_WIDTH = 100;
    private static final int DEFAULT_LAYOUT_HEIGHT = 100;
    private static final Rect DEFAULT_RECT = new Rect(1, 1, DEFAULT_LAYOUT_WIDTH - 1,
            DEFAULT_LAYOUT_HEIGHT - 1);

    private static final PixelColor RED = new PixelColor(Color.RED);
    private static final PixelColor BLUE = new PixelColor(Color.BLUE);
    private static final PixelColor MAGENTA = new PixelColor(Color.MAGENTA);
    private static final PixelColor GREEN = new PixelColor(Color.GREEN);
    private static final PixelColor YELLOW = new PixelColor(Color.YELLOW);

    @Rule
    public ActivityScenarioRule<ASurfaceControlTestActivity> mActivityRule =
            createFullscreenActivityScenarioRule(ASurfaceControlTestActivity.class);

    @Rule
    public TestName mName = new TestName();

    private ASurfaceControlTestActivity mActivity;

    private long mDesiredPresentTime;

    @Before
    public void setup() {
        mActivityRule.getScenario().onActivity(activity -> mActivity = activity);
    }

    ///////////////////////////////////////////////////////////////////////////
    // SurfaceHolder.Callbacks
    ///////////////////////////////////////////////////////////////////////////

    private static class SurfaceHolderCallback implements SurfaceHolder.Callback {
        BasicSurfaceHolderCallback mBasicSurfaceHolderCallback;

        SurfaceHolderCallback(BasicSurfaceHolderCallback basicSurfaceHolderCallback) {
            mBasicSurfaceHolderCallback = basicSurfaceHolderCallback;
        }

        @Override
        public void surfaceCreated(@NonNull SurfaceHolder holder) {
            Canvas canvas = holder.lockCanvas();
            canvas.drawColor(Color.YELLOW);
            holder.unlockCanvasAndPost(canvas);

            mBasicSurfaceHolderCallback.surfaceCreated(holder);
        }

        @Override
        public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width,
                int height) {
        }

        @Override
        public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
            mBasicSurfaceHolderCallback.surfaceDestroyed();
        }
    }

    private abstract static class BasicSurfaceHolderCallback {
        private final Set<Long> mSurfaceControls = new HashSet<>();
        private final Set<Long> mBuffers = new HashSet<>();

        public abstract void surfaceCreated(SurfaceHolder surfaceHolder);

        public void surfaceDestroyed() {
            for (Long surfaceControl : mSurfaceControls) {
                reparent(surfaceControl, 0);
                nSurfaceControl_release(surfaceControl);
            }
            mSurfaceControls.clear();

            for (Long buffer : mBuffers) {
                nSurfaceTransaction_releaseBuffer(buffer);
            }
            mBuffers.clear();
        }

        public long createFromWindow(Surface surface) {
            long surfaceControl = nSurfaceControl_createFromWindow(surface);
            assertTrue("failed to create surface control", surfaceControl != 0);

            mSurfaceControls.add(surfaceControl);
            return surfaceControl;
        }

        public long create(long parentSurfaceControl) {
            long childSurfaceControl = nSurfaceControl_create(parentSurfaceControl);
            assertTrue("failed to create child surface control", childSurfaceControl != 0);

            mSurfaceControls.add(childSurfaceControl);
            return childSurfaceControl;
        }

        public long setSolidBuffer(
                long surfaceControl, long surfaceTransaction, int width, int height, int color) {
            long buffer = nSurfaceTransaction_setSolidBuffer(surfaceControl, surfaceTransaction,
                    width, height, color);
            assertTrue("failed to set buffer", buffer != 0);
            mBuffers.add(buffer);
            return buffer;
        }

        public long setSolidBuffer(long surfaceControl, int width, int height, int color) {
            long surfaceTransaction = createSurfaceTransaction();
            long buffer = setSolidBuffer(surfaceControl, surfaceTransaction, width, height, color);
            TimedTransactionListener onCommitCallback = new TimedTransactionListener();
            nSurfaceTransaction_setOnCommitCallback(surfaceTransaction, onCommitCallback);
            applyAndDeleteSurfaceTransaction(surfaceTransaction);
            try {
                onCommitCallback.mLatch.await(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
            }
            if (onCommitCallback.mLatch.getCount() > 0) {
                Log.e(TAG, "Failed to wait for commit callback");
            }
            return buffer;
        }

        public void setNullBuffer(long surfaceControl) {
            long surfaceTransaction = createSurfaceTransaction();
            nSurfaceTransaction_setBuffer(surfaceControl, surfaceTransaction, 0 /* buffer */);
            TimedTransactionListener onCommitCallback = new TimedTransactionListener();
            nSurfaceTransaction_setOnCommitCallback(surfaceTransaction, onCommitCallback);
            applyAndDeleteSurfaceTransaction(surfaceTransaction);
            try {
                onCommitCallback.mLatch.await(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
            }
            if (onCommitCallback.mLatch.getCount() > 0) {
                Log.e(TAG, "Failed to wait for commit callback");
            }
        }

        public void setQuadrantBuffer(long surfaceControl, long surfaceTransaction, int width,
                int height, int colorTopLeft, int colorTopRight, int colorBottomRight,
                int colorBottomLeft) {
            long buffer = nSurfaceTransaction_setQuadrantBuffer(surfaceControl, surfaceTransaction,
                    width, height, colorTopLeft, colorTopRight, colorBottomRight, colorBottomLeft);
            assertTrue("failed to set buffer", buffer != 0);
            mBuffers.add(buffer);
        }

        public void setQuadrantBuffer(long surfaceControl, int width, int height, int colorTopLeft,
                int colorTopRight, int colorBottomRight, int colorBottomLeft) {
            long surfaceTransaction = createSurfaceTransaction();
            setQuadrantBuffer(surfaceControl, surfaceTransaction, width, height, colorTopLeft,
                    colorTopRight, colorBottomRight, colorBottomLeft);
            TimedTransactionListener onCommitCallback = new TimedTransactionListener();
            nSurfaceTransaction_setOnCommitCallback(surfaceTransaction, onCommitCallback);
            applyAndDeleteSurfaceTransaction(surfaceTransaction);
            try {
                onCommitCallback.mLatch.await(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
            }
            if (onCommitCallback.mLatch.getCount() > 0) {
                Log.e(TAG, "Failed to wait for commit callback");
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Tests
    ///////////////////////////////////////////////////////////////////////////

    private void verifyTest(BasicSurfaceHolderCallback callback, PixelChecker pixelChecker) {
        SurfaceHolderCallback surfaceHolderCallback = new SurfaceHolderCallback(callback);
        mActivity.verifyTest(surfaceHolderCallback, pixelChecker, mName);
    }

    @Test
    public void testSurfaceTransaction_create() {
        long surfaceTransaction = nSurfaceTransaction_create();
        assertTrue("failed to create surface transaction", surfaceTransaction != 0);

        nSurfaceTransaction_delete(surfaceTransaction);
    }

    @Test
    public void testSurfaceTransaction_apply() {
        long surfaceTransaction = nSurfaceTransaction_create();
        assertTrue("failed to create surface transaction", surfaceTransaction != 0);

        Log.e("Transaction", "created: " + surfaceTransaction);

        nSurfaceTransaction_apply(surfaceTransaction);
        nSurfaceTransaction_delete(surfaceTransaction);
    }

    // INTRO: The following tests run a series of commands and verify the
    // output based on the number of pixels with a certain color on the display.
    //
    // The interface being tested is a NDK api but the only way to record the display
    // through public apis is in through the SDK. So the test logic and test verification
    // is in Java but the hooks that call into the NDK api are jni code.
    //
    // The set up is done during the surfaceCreated callback. In most cases, the
    // test uses the opportunity to create a child layer through createFromWindow and
    // performs operations on the child layer.
    //
    // When there is no visible buffer for the layer(s) the color defaults to black.
    // The test cases allow a +/- 10% error rate. This is based on the error
    // rate allowed in the SurfaceViewSyncTests

    @Test
    public void testSurfaceControl_createFromWindow() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl = createFromWindow(holder.getSurface());
                    }
                },
                new PixelChecker(Color.YELLOW) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });
    }

    @Test
    public void testSurfaceControl_create() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long parentSurfaceControl = createFromWindow(holder.getSurface());
                        long childSurfaceControl = create(parentSurfaceControl);
                    }
                },
                new PixelChecker(Color.YELLOW) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });
    }

    @Test
    public void testSurfaceControl_fromJava() {
        SurfaceControl.Builder builder = new SurfaceControl.Builder();
        builder.setName("testSurfaceControl_fromJava");
        SurfaceControl control = builder.build();
        final long childSurfaceControl = nSurfaceControl_fromJava(control);
        assertTrue(childSurfaceControl != 0);
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long parentSurfaceControl = createFromWindow(holder.getSurface());
                        setVisibility(childSurfaceControl, true);
                        setSolidBuffer(childSurfaceControl, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, Color.RED);
                        reparent(childSurfaceControl, parentSurfaceControl);
                    }
                },
                new PixelChecker(Color.RED) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });
        nSurfaceControl_release(childSurfaceControl);
    }

    @Test
    public void testSurfaceTransaction_fromJava() {
        SurfaceControl.Transaction jTransaction = new SurfaceControl.Transaction();
        final long transaction = nSurfaceTransaction_fromJava(jTransaction);
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl = createFromWindow(holder.getSurface());
                        setSolidBuffer(surfaceControl, transaction, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, Color.RED);
                        nSurfaceTransaction_apply(transaction);
                    }
                },
                new PixelChecker(Color.RED) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });
        Reference.reachabilityFence(jTransaction);
    }

    @Test
    public void testSurfaceControl_acquire() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl = createFromWindow(holder.getSurface());
                        // increment one refcount
                        nSurfaceControl_acquire(surfaceControl);
                        // decrement one refcount incremented from create call
                        nSurfaceControl_release(surfaceControl);
                        setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                Color.RED);
                    }
                },
                new PixelChecker(Color.RED) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setBuffer() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl = createFromWindow(holder.getSurface());
                        setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                Color.RED);
                    }
                },
                new PixelChecker(Color.RED) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setNullBuffer() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl = createFromWindow(holder.getSurface());
                        setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                Color.RED);
                        setNullBuffer(surfaceControl);
                    }
                },
                new PixelChecker(Color.YELLOW) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setBuffer_parentAndChild() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long parentSurfaceControl = createFromWindow(holder.getSurface());
                        long childSurfaceControl = create(parentSurfaceControl);

                        setSolidBuffer(parentSurfaceControl, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, Color.BLUE);
                        setSolidBuffer(childSurfaceControl, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, Color.RED);
                    }
                },
                new PixelChecker(Color.RED) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setBuffer_childOnly() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long parentSurfaceControl = createFromWindow(holder.getSurface());
                        long childSurfaceControl = create(parentSurfaceControl);

                        setSolidBuffer(childSurfaceControl, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, Color.RED);
                    }
                },
                new PixelChecker(Color.RED) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setVisibility_show() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl = createFromWindow(holder.getSurface());

                        setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                Color.RED);
                        setVisibility(surfaceControl, true);
                    }
                },
                new PixelChecker(Color.RED) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setVisibility_hide() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl = createFromWindow(holder.getSurface());

                        setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                Color.RED);
                        setVisibility(surfaceControl, false);
                    }
                },
                new PixelChecker(Color.YELLOW) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setBufferOpaque_opaque() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl = createFromWindow(holder.getSurface());

                        setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                PixelColor.TRANSLUCENT_RED);
                        setBufferOpaque(surfaceControl, true);
                    }
                },
                new PixelChecker(Color.RED) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setBufferOpaque_translucent() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl = createFromWindow(holder.getSurface());

                        setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                PixelColor.TRANSLUCENT_RED);
                        setBufferOpaque(surfaceControl, false);
                    }
                },
                // setBufferOpaque is an optimization that can be used by SurfaceFlinger.
                // It isn't required to affect SurfaceFlinger's behavior.
                //
                // Ideally we would check for a specific blending of red with a layer below
                // it. Unfortunately we don't know what blending the layer will use and
                // we don't know what variation the GPU/DPU/blitter might have. Although
                // we don't know what shade of red might be present, we can at least check
                // that the optimization doesn't cause the framework to drop the buffer entirely.
                new PixelChecker(Color.YELLOW, false /* logWhenNoMatch */) {
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount == 0;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setDestinationRect() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl = createFromWindow(holder.getSurface());

                        setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                Color.RED);
                    }
                },
                new PixelChecker(Color.RED) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setDestinationRect_small() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl = createFromWindow(holder.getSurface());

                        setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                Color.RED);
                        setGeometry(surfaceControl, 0, 0, 100, 100, 10, 10, 50, 50, 0);
                    }
                },
                new RectChecker(DEFAULT_RECT) {
                    @Override
                    public PixelColor getExpectedColor(int x, int y) {
                        if (x >= 10 && x < 50 && y >= 10 && y < 50) {
                            return RED;
                        } else {
                            return YELLOW;
                        }
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setDestinationRect_childSmall() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long parentSurfaceControl = createFromWindow(holder.getSurface());
                        long childSurfaceControl = create(parentSurfaceControl);

                        setSolidBuffer(childSurfaceControl, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, Color.RED);
                        setGeometry(childSurfaceControl, 0, 0, 100, 100, 10, 10, 50, 50, 0);
                    }
                },
                new RectChecker(DEFAULT_RECT) {
                    @Override
                    public PixelColor getExpectedColor(int x, int y) {
                        if (x >= 10 && x < 50 && y >= 10 && y < 50) {
                            return RED;
                        } else {
                            return YELLOW;
                        }
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setDestinationRect_extraLarge() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl = createFromWindow(holder.getSurface());

                        setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                Color.RED);
                        setGeometry(surfaceControl, 0, 0, 100, 100, -100, -100, 200, 200, 0);
                    }
                },
                new PixelChecker(Color.RED) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setDestinationRect_childExtraLarge() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long parentSurfaceControl = createFromWindow(holder.getSurface());
                        long childSurfaceControl = create(parentSurfaceControl);

                        setSolidBuffer(childSurfaceControl, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, Color.RED);
                        setGeometry(childSurfaceControl, 0, 0, 100, 100, -100, -100, 200, 200, 0);
                    }
                },
                new PixelChecker(Color.RED) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setDestinationRect_negativeOffset() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl = createFromWindow(holder.getSurface());

                        setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                Color.RED);
                        setGeometry(surfaceControl, 0, 0, 100, 100, -30, -20, 50, 50, 0);
                    }
                },
                new RectChecker(DEFAULT_RECT) {
                    @Override
                    public PixelColor getExpectedColor(int x, int y) {
                        if (x < 80 && y < 70) {
                            return RED;
                        } else {
                            return YELLOW;
                        }
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setDestinationRect_outOfParentBounds() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl = createFromWindow(holder.getSurface());

                        setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                Color.RED);
                        setGeometry(surfaceControl, 0, 0, 100, 100, 50, 50, 110, 105, 0);
                    }
                },
                new RectChecker(DEFAULT_RECT) {
                    @Override
                    public PixelColor getExpectedColor(int x, int y) {
                        if (x >= 50 && y >= 50) {
                            return RED;
                        } else {
                            return YELLOW;
                        }
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setDestinationRect_twoLayers() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl1 = createFromWindow(holder.getSurface());
                        long surfaceControl2 = createFromWindow(holder.getSurface());

                        setSolidBuffer(surfaceControl1, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                Color.RED);
                        setSolidBuffer(surfaceControl2, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                Color.BLUE);
                        setGeometry(surfaceControl1, 0, 0, 100, 100, 10, 10, 30, 40, 0);
                        setGeometry(surfaceControl2, 0, 0, 100, 100, 70, 20, 90, 50, 0);
                    }
                },
                new RectChecker(DEFAULT_RECT) {
                    @Override
                    public PixelColor getExpectedColor(int x, int y) {
                        if (x >= 10 && x < 30 && y >= 10 && y < 40) {
                            return RED;
                        } else if (x >= 70 && x < 90 && y >= 20 && y < 50) {
                            return BLUE;
                        } else {
                            return YELLOW;
                        }
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setSourceRect() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl = createFromWindow(holder.getSurface());

                        setQuadrantBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, Color.RED, Color.BLUE,
                                Color.MAGENTA, Color.GREEN);
                    }
                },
                new RectChecker(DEFAULT_RECT) {
                    @Override
                    public PixelColor getExpectedColor(int x, int y) {
                        int halfWidth = DEFAULT_LAYOUT_WIDTH / 2;
                        int halfHeight = DEFAULT_LAYOUT_HEIGHT / 2;
                        if (x < halfWidth && y < halfHeight) {
                            return RED;
                        } else if (x >= halfWidth && y < halfHeight) {
                            return BLUE;
                        } else if (x < halfWidth && y >= halfHeight) {
                            return GREEN;
                        } else {
                            return MAGENTA;
                        }
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setSourceRect_smallCentered() {
        // These rectangles leave two 10px strips unchecked to allow blended pixels due to GL
        // texture filtering.
        Rect topLeft = new Rect(0, 0, 45, 45);
        Rect topRight = new Rect(55, 0, 100, 45);
        Rect bottomLeft = new Rect(0, 55, 45, 100);
        Rect bottomRight = new Rect(55, 55, 100, 100);
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl = createFromWindow(holder.getSurface());

                        setQuadrantBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, Color.RED, Color.BLUE,
                                Color.MAGENTA, Color.GREEN);
                        setGeometry(surfaceControl, 10, 10, 90, 90, 0, 0, 100, 100, 0);
                    }
                },

                new RectChecker(List.of(topLeft, topRight, bottomLeft, bottomRight)) {
                    @Override
                    public PixelColor getExpectedColor(int x, int y) {
                        if (topLeft.contains(x, y)) {
                            return RED;
                        } else if (topRight.contains(x, y)) {
                            return BLUE;
                        } else if (bottomLeft.contains(x, y)) {
                            return GREEN;
                        } else if (bottomRight.contains(x, y)) {
                            return MAGENTA;
                        }
                        throw new AssertionError(String.format("Unexpected pixel (%d, %d)", x, y));
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setSourceRect_small() {
        // These rectangles leave a 10px strip unchecked to allow blended pixels due to GL
        // texture filtering.
        Rect topHalf = new Rect(0, 0, 100, 45);
        Rect bottomHalf = new Rect(0, 55, 100, 100);
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl = createFromWindow(holder.getSurface());

                        setQuadrantBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, Color.RED, Color.BLUE,
                                Color.MAGENTA, Color.GREEN);
                        setGeometry(surfaceControl, 60, 10, 90, 90, 0, 0, 100, 100, 0);
                    }
                },
                new RectChecker(List.of(topHalf, bottomHalf)) {
                    @Override
                    public PixelColor getExpectedColor(int x, int y) {
                        if (topHalf.contains(x, y)) {
                            return BLUE;
                        } else if (bottomHalf.contains(x, y)) {
                            return MAGENTA;
                        }
                        throw new AssertionError(String.format("Unexpected pixel (%d, %d)", x, y));
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setSourceRect_extraLarge() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl = createFromWindow(holder.getSurface());

                        setQuadrantBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, Color.RED, Color.BLUE,
                                Color.MAGENTA, Color.GREEN);
                        setGeometry(surfaceControl, -50, -50, 150, 150, 0, 0, 100, 100, 0);
                    }
                },

                new RectChecker(DEFAULT_RECT) {
                    @Override
                    public PixelColor getExpectedColor(int x, int y) {
                        int halfWidth = DEFAULT_LAYOUT_WIDTH / 2;
                        int halfHeight = DEFAULT_LAYOUT_HEIGHT / 2;
                        if (x < halfWidth && y < halfHeight) {
                            return RED;
                        } else if (x >= halfWidth && y < halfHeight) {
                            return BLUE;
                        } else if (x < halfWidth && y >= halfHeight) {
                            return GREEN;
                        } else {
                            return MAGENTA;
                        }
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setSourceRect_badOffset() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl = createFromWindow(holder.getSurface());

                        setQuadrantBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, Color.RED, Color.BLUE,
                                Color.MAGENTA, Color.GREEN);
                        setGeometry(surfaceControl, -50, -50, 50, 50, 0, 0, 100, 100, 0);
                    }
                },
                new PixelChecker(Color.RED) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setTransform_flipH() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl = createFromWindow(holder.getSurface());

                        setQuadrantBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, Color.RED, Color.BLUE,
                                Color.MAGENTA, Color.GREEN);
                        setGeometry(surfaceControl, 0, 0, 100, 100, 0, 0, 100, 100,
                                /*NATIVE_WINDOW_TRANSFORM_FLIP_H*/ 1);
                    }
                },
                new RectChecker(DEFAULT_RECT) {
                    @Override
                    public PixelColor getExpectedColor(int x, int y) {
                        int halfWidth = DEFAULT_LAYOUT_WIDTH / 2;
                        int halfHeight = DEFAULT_LAYOUT_HEIGHT / 2;
                        if (x < halfWidth && y < halfHeight) {
                            return BLUE;
                        } else if (x >= halfWidth && y < halfHeight) {
                            return RED;
                        } else if (x < halfWidth && y >= halfHeight) {
                            return MAGENTA;
                        } else {
                            return GREEN;
                        }
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setTransform_rotate180() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl = createFromWindow(holder.getSurface());

                        setQuadrantBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, Color.RED, Color.BLUE,
                                Color.MAGENTA, Color.GREEN);
                        setGeometry(surfaceControl, 0, 0, 100, 100, 0, 0, 100, 100,
                                /*NATIVE_WINDOW_TRANSFORM_ROT_180*/ 3);
                    }
                },
                new RectChecker(DEFAULT_RECT) {
                    @Override
                    public PixelColor getExpectedColor(int x, int y) {
                        int halfWidth = DEFAULT_LAYOUT_WIDTH / 2;
                        int halfHeight = DEFAULT_LAYOUT_HEIGHT / 2;
                        if (x < halfWidth && y < halfHeight) {
                            return MAGENTA;
                        } else if (x >= halfWidth && y < halfHeight) {
                            return GREEN;
                        } else if (x < halfWidth && y >= halfHeight) {
                            return BLUE;
                        } else {
                            return RED;
                        }
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setDamageRegion_all() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl = createFromWindow(holder.getSurface());
                        setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                Color.RED);

                        long surfaceTransaction = createSurfaceTransaction();
                        setSolidBuffer(surfaceControl, surfaceTransaction, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, Color.BLUE);
                        nSurfaceTransaction_setDamageRegion(surfaceControl, surfaceTransaction, 0,
                                0, 100, 100);
                        applyAndDeleteSurfaceTransaction(surfaceTransaction);
                    }
                },
                new PixelChecker(Color.BLUE) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setZOrder_zero() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl1 = createFromWindow(holder.getSurface());
                        long surfaceControl2 = createFromWindow(holder.getSurface());
                        setSolidBuffer(surfaceControl1, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                Color.RED);
                        setSolidBuffer(surfaceControl2, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                Color.MAGENTA);

                        setZOrder(surfaceControl1, 1);
                        setZOrder(surfaceControl2, 0);
                    }
                },
                new RectChecker(DEFAULT_RECT) {
                    @Override
                    public PixelColor getExpectedColor(int x, int y) {
                        return RED;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setZOrder_positive() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl1 = createFromWindow(holder.getSurface());
                        long surfaceControl2 = createFromWindow(holder.getSurface());
                        setSolidBuffer(surfaceControl1, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                Color.RED);
                        setSolidBuffer(surfaceControl2, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                Color.MAGENTA);

                        setZOrder(surfaceControl1, 1);
                        setZOrder(surfaceControl2, 5);
                    }
                },
                new RectChecker(DEFAULT_RECT) {
                    @Override
                    public PixelColor getExpectedColor(int x, int y) {
                        return MAGENTA;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setZOrder_negative() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl1 = createFromWindow(holder.getSurface());
                        long surfaceControl2 = createFromWindow(holder.getSurface());
                        setSolidBuffer(surfaceControl1, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                Color.RED);
                        setSolidBuffer(surfaceControl2, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                Color.MAGENTA);

                        setZOrder(surfaceControl1, 1);
                        setZOrder(surfaceControl2, -15);
                    }
                },
                new RectChecker(DEFAULT_RECT) {
                    @Override
                    public PixelColor getExpectedColor(int x, int y) {
                        return RED;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setZOrder_max() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl1 = createFromWindow(holder.getSurface());
                        long surfaceControl2 = createFromWindow(holder.getSurface());
                        setSolidBuffer(surfaceControl1, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                Color.RED);
                        setSolidBuffer(surfaceControl2, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                Color.MAGENTA);

                        setZOrder(surfaceControl1, 1);
                        setZOrder(surfaceControl2, Integer.MAX_VALUE);
                    }
                },
                new RectChecker(DEFAULT_RECT) {
                    @Override
                    public PixelColor getExpectedColor(int x, int y) {
                        return MAGENTA;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setZOrder_min() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl1 = createFromWindow(holder.getSurface());
                        long surfaceControl2 = createFromWindow(holder.getSurface());
                        setSolidBuffer(surfaceControl1, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                Color.RED);
                        setSolidBuffer(surfaceControl2, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                Color.MAGENTA);

                        setZOrder(surfaceControl1, 1);
                        setZOrder(surfaceControl2, Integer.MIN_VALUE);
                    }
                },
                new RectChecker(DEFAULT_RECT) {
                    @Override
                    public PixelColor getExpectedColor(int x, int y) {
                        return RED;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setOnComplete() {
        TimedTransactionListener onCompleteCallback = new TimedTransactionListener();
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl = createFromWindow(holder.getSurface());

                        long surfaceTransaction = createSurfaceTransaction();
                        setSolidBuffer(surfaceControl, surfaceTransaction, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, Color.RED);
                        nSurfaceTransaction_setOnCompleteCallback(surfaceTransaction,
                                false /* waitForFence */, onCompleteCallback);
                        applyAndDeleteSurfaceTransaction(surfaceTransaction);

                        // Wait for callbacks to fire.
                        try {
                            onCompleteCallback.mLatch.await(1, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                        }
                        if (onCompleteCallback.mLatch.getCount() > 0) {
                            Log.e(TAG, "Failed to wait for callback");
                        }
                    }
                },
                new PixelChecker(Color.RED) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });

        // Validate we got callbacks.
        assertEquals(0, onCompleteCallback.mLatch.getCount());
        assertTrue(onCompleteCallback.mCallbackTime > 0);
    }

    @Test
    @RequiresDevice // emulators can't support sync fences
    public void testSurfaceTransaction_setDesiredPresentTime_now() {
        TimedTransactionListener onCompleteCallback = new TimedTransactionListener();
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl = createFromWindow(holder.getSurface());

                        long surfaceTransaction = createSurfaceTransaction();
                        setSolidBuffer(surfaceControl, surfaceTransaction, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, Color.RED);
                        mDesiredPresentTime = nSurfaceTransaction_setDesiredPresentTime(
                                surfaceTransaction, 0);
                        nSurfaceTransaction_setOnCompleteCallback(surfaceTransaction,
                                true /* waitForFence */, onCompleteCallback);
                        applyAndDeleteSurfaceTransaction(surfaceTransaction);
                        // Wait for callbacks to fire.
                        try {
                            onCompleteCallback.mLatch.await(1, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                        }
                        if (onCompleteCallback.mLatch.getCount() > 0) {
                            Log.e(TAG, "Failed to wait for callback");
                        }
                    }
                },
                new PixelChecker(Color.RED) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });

        assertEquals(0, onCompleteCallback.mLatch.getCount());
        assertTrue(onCompleteCallback.mCallbackTime > 0);
        assertTrue(onCompleteCallback.mLatchTime > 0);

        assertTrue("transaction was presented too early. presentTime="
                        + onCompleteCallback.mPresentTime,
                onCompleteCallback.mPresentTime >= mDesiredPresentTime);
    }

    @Test
    @RequiresDevice // emulators can't support sync fences
    public void testSurfaceTransaction_setDesiredPresentTime_30ms() {
        TimedTransactionListener onCompleteCallback = new TimedTransactionListener();
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl = createFromWindow(holder.getSurface());

                        long surfaceTransaction = createSurfaceTransaction();
                        setSolidBuffer(surfaceControl, surfaceTransaction, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, Color.RED);
                        mDesiredPresentTime = nSurfaceTransaction_setDesiredPresentTime(
                                surfaceTransaction, 30000000);
                        nSurfaceTransaction_setOnCompleteCallback(surfaceTransaction,
                                true /* waitForFence */, onCompleteCallback);
                        applyAndDeleteSurfaceTransaction(surfaceTransaction);
                        // Wait for callbacks to fire.
                        try {
                            onCompleteCallback.mLatch.await(1, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                        }
                        if (onCompleteCallback.mLatch.getCount() > 0) {
                            Log.e(TAG, "Failed to wait for callback");
                        }
                    }
                },
                new PixelChecker(Color.RED) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });

        assertEquals(0, onCompleteCallback.mLatch.getCount());
        assertTrue(onCompleteCallback.mCallbackTime > 0);
        assertTrue(onCompleteCallback.mLatchTime > 0);

        assertTrue("transaction was presented too early. presentTime="
                        + onCompleteCallback.mPresentTime,
                onCompleteCallback.mPresentTime >= mDesiredPresentTime);
    }

    @Test
    @RequiresDevice // emulators can't support sync fences
    public void testSurfaceTransaction_setDesiredPresentTime_100ms() {
        TimedTransactionListener onCompleteCallback = new TimedTransactionListener();
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl = createFromWindow(holder.getSurface());

                        long surfaceTransaction = createSurfaceTransaction();
                        setSolidBuffer(surfaceControl, surfaceTransaction, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, Color.RED);
                        mDesiredPresentTime = nSurfaceTransaction_setDesiredPresentTime(
                                surfaceTransaction, 100000000);
                        nSurfaceTransaction_setOnCompleteCallback(surfaceTransaction,
                                true /* waitForFence */, onCompleteCallback);
                        applyAndDeleteSurfaceTransaction(surfaceTransaction);
                        // Wait for callbacks to fire.
                        try {
                            onCompleteCallback.mLatch.await(1, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                        }
                        if (onCompleteCallback.mLatch.getCount() > 0) {
                            Log.e(TAG, "Failed to wait for callback");
                        }
                    }
                },
                new PixelChecker(Color.RED) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });

        assertEquals(0, onCompleteCallback.mLatch.getCount());

        assertTrue(onCompleteCallback.mCallbackTime > 0);
        assertTrue(onCompleteCallback.mLatchTime > 0);

        assertTrue("transaction was presented too early. presentTime="
                        + onCompleteCallback.mPresentTime,
                onCompleteCallback.mPresentTime >= mDesiredPresentTime);
    }

    @Test
    public void testSurfaceTransaction_setBufferAlpha_1_0() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl = createFromWindow(holder.getSurface());

                        setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                Color.RED);
                        setBufferAlpha(surfaceControl, 1.0);
                    }
                },
                new PixelChecker(Color.RED) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setBufferAlpha_0_5() {
        BasicSurfaceHolderCallback callback = new BasicSurfaceHolderCallback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                long surfaceControl = createFromWindow(holder.getSurface());

                setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                        Color.RED);
                setBufferAlpha(surfaceControl, 0.5);
            }
        };
        verifyTest(callback,
                new PixelChecker(Color.YELLOW, false /* logWhenNoMatch */) {
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount == 0;
                    }
                });
        verifyTest(callback,
                new PixelChecker(Color.RED, false /* logWhenNoMatch */) {
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount == 0;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setBufferAlpha_0_0() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl = createFromWindow(holder.getSurface());

                        setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                Color.RED);
                        setBufferAlpha(surfaceControl, 0.0);
                    }
                },
                new PixelChecker(Color.YELLOW) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_reparent() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long parentSurfaceControl1 = createFromWindow(holder.getSurface());
                        long parentSurfaceControl2 = createFromWindow(holder.getSurface());
                        long childSurfaceControl = create(parentSurfaceControl1);

                        setGeometry(parentSurfaceControl1, 0, 0, 100, 100, 0, 0, 25, 100, 0);
                        setGeometry(parentSurfaceControl2, 0, 0, 100, 100, 25, 0, 100, 100, 0);

                        setSolidBuffer(childSurfaceControl, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, Color.RED);

                        reparent(childSurfaceControl, parentSurfaceControl2);
                    }
                },
                new RectChecker(DEFAULT_RECT) {
                    @Override
                    public PixelColor getExpectedColor(int x, int y) {
                        if (x >= 25) {
                            return RED;
                        } else {
                            return YELLOW;
                        }
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_reparent_null() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long parentSurfaceControl = createFromWindow(holder.getSurface());
                        long childSurfaceControl = create(parentSurfaceControl);

                        setSolidBuffer(childSurfaceControl, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, Color.RED);

                        reparent(childSurfaceControl, 0);
                    }
                },
                new PixelChecker(Color.YELLOW) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setColor() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl = createFromWindow(holder.getSurface());

                        setColor(surfaceControl, 0, 1.0f, 0, 1.0f);
                    }
                },
                new PixelChecker(Color.GREEN) { // 10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_noColorNoBuffer() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long parentSurfaceControl = createFromWindow(holder.getSurface());
                        long childSurfaceControl = create(parentSurfaceControl);

                        setColor(parentSurfaceControl, 0, 1.0f, 0, 1.0f);
                    }
                },
                new PixelChecker(Color.GREEN) { // 10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setColorAlpha() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long parentSurfaceControl = createFromWindow(holder.getSurface());
                        setColor(parentSurfaceControl, 0, 0, 1.0f, 0);
                    }
                },
                new PixelChecker(Color.YELLOW) { // 10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setColorAndBuffer() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl = createFromWindow(holder.getSurface());

                        setSolidBuffer(
                                surfaceControl, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, Color.RED);
                        setColor(surfaceControl, 0, 1.0f, 0, 1.0f);
                    }
                },
                new PixelChecker(Color.RED) { // 10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setColorAndBuffer_bufferAlpha_0_5() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl = createFromWindow(holder.getSurface());

                        setSolidBuffer(
                                surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                Color.RED);
                        setBufferAlpha(surfaceControl, 0.5);
                        setColor(surfaceControl, 0, 0, 1.0f, 1.0f);
                    }
                },
                new PixelChecker(Color.RED, false /* logWhenNoMatch */) {
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount == 0;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setBufferNoColor_bufferAlpha_0() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControlA = createFromWindow(holder.getSurface());
                        long surfaceControlB = createFromWindow(holder.getSurface());

                        setColor(surfaceControlA, 1.0f, 0, 0, 1.0f);
                        setSolidBuffer(surfaceControlB, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                Color.TRANSPARENT);

                        setZOrder(surfaceControlA, 1);
                        setZOrder(surfaceControlB, 2);
                    }
                },
                new PixelChecker(Color.RED) { // 10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setColorAndBuffer_hide() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long parentSurfaceControl = createFromWindow(holder.getSurface());
                        long childSurfaceControl = create(parentSurfaceControl);

                        setColor(parentSurfaceControl, 0, 1.0f, 0, 1.0f);

                        setSolidBuffer(
                                childSurfaceControl, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, Color.RED);
                        setColor(childSurfaceControl, 0, 0, 1.0f, 1.0f);
                        setVisibility(childSurfaceControl, false);
                    }
                },
                new PixelChecker(Color.GREEN) { // 10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_zOrderMultipleSurfaces() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControlA = createFromWindow(holder.getSurface());
                        long surfaceControlB = createFromWindow(holder.getSurface());

                        // blue color layer of A is above the green buffer and red color layer
                        // of B
                        setColor(surfaceControlA, 0, 0, 1.0f, 1.0f);
                        setSolidBuffer(
                                surfaceControlB, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, Color.GREEN);
                        setColor(surfaceControlB, 1.0f, 0, 0, 1.0f);
                        setZOrder(surfaceControlA, 5);
                        setZOrder(surfaceControlB, 4);
                    }
                },
                new PixelChecker(Color.BLUE) { // 10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_zOrderMultipleSurfacesWithParent() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long parentSurfaceControl = createFromWindow(holder.getSurface());
                        long surfaceControlA = create(parentSurfaceControl);
                        long surfaceControlB = create(parentSurfaceControl);

                        setColor(surfaceControlA, 0, 1.0f, 0, 1.0f);
                        setSolidBuffer(
                                surfaceControlA, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, Color.GREEN);
                        setColor(surfaceControlB, 1.0f, 0, 0, 1.0f);
                        setZOrder(surfaceControlA, 3);
                        setZOrder(surfaceControlB, 4);
                    }
                },
                new PixelChecker(Color.RED) { // 10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setPosition() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl = createFromWindow(holder.getSurface());

                        setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                Color.RED);
                        setPosition(surfaceControl, 20, 10);
                    }
                },
                new RectChecker(DEFAULT_RECT) {
                    @Override
                    public PixelColor getExpectedColor(int x, int y) {
                        if (x >= 20 && y >= 10) {
                            return RED;
                        } else {
                            return YELLOW;
                        }
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setPositionNegative() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl = createFromWindow(holder.getSurface());

                        setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                Color.RED);
                        // Offset -20, -10
                        setPosition(surfaceControl, -20, -10);
                    }
                },
                new RectChecker(DEFAULT_RECT) {
                    @Override
                    public PixelColor getExpectedColor(int x, int y) {
                        if (x < DEFAULT_LAYOUT_WIDTH - 20 && y < DEFAULT_LAYOUT_HEIGHT - 10) {
                            return RED;
                        } else {
                            return YELLOW;
                        }
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setScale() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl = createFromWindow(holder.getSurface());

                        setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                Color.RED);
                        setScale(surfaceControl, .5f, .5f);
                    }
                },
                new RectChecker(DEFAULT_RECT) {
                    @Override
                    public PixelColor getExpectedColor(int x, int y) {
                        int halfWidth = DEFAULT_LAYOUT_WIDTH / 2;
                        int halfHeight = DEFAULT_LAYOUT_HEIGHT / 2;
                        if (x < halfWidth && y < halfHeight) {
                            return RED;
                        } else {
                            return YELLOW;
                        }
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_scaleToZero() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long parentSurfaceControl = createFromWindow(holder.getSurface());
                        long childSurfaceControl = create(parentSurfaceControl);

                        setSolidBuffer(parentSurfaceControl,
                                DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT, Color.YELLOW);
                        setSolidBuffer(childSurfaceControl,
                                DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT, Color.RED);
                        setScale(childSurfaceControl, 0f, 0f);
                    }
                },
                new PixelChecker(Color.YELLOW) {
                    @Override
                    public boolean checkPixels(int matchingPixelCount, int width, int height) {
                        return matchingPixelCount > 9000 & matchingPixelCount < 11000;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setPositionAndScale() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl = createFromWindow(holder.getSurface());

                        setQuadrantBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, Color.RED, Color.BLUE,
                                Color.MAGENTA, Color.GREEN);

                        // Set the position to -50, -50 in parent space then scale 2x in each
                        // direction relative to 0,0. The end result should be a -50,-50,150,150
                        // buffer coverage or essentially a 2x center-scale

                        setPosition(surfaceControl, -50, -50);
                        setScale(surfaceControl, 2, 2);
                    }
                },
                new RectChecker(new Rect(0, 0, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT)) {
                    @Override
                    public PixelColor getExpectedColor(int x, int y) {
                        int halfWidth = DEFAULT_LAYOUT_WIDTH / 2;
                        int halfHeight = DEFAULT_LAYOUT_HEIGHT / 2;
                        if (x < halfWidth && y < halfHeight) {
                            return RED;
                        } else if (x >= halfWidth && y < halfHeight) {
                            return BLUE;
                        } else if (x < halfWidth && y >= halfHeight) {
                            return GREEN;
                        } else {
                            return MAGENTA;
                        }
                    }

                    @Override
                    public boolean checkPixels(int matchingPixelCount, int width, int height) {
                        // There will be sampling artifacts along the center line, ignore those
                        return matchingPixelCount > 9000 && matchingPixelCount < 11000;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setBufferTransform90() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl = createFromWindow(holder.getSurface());

                        setQuadrantBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, Color.RED, Color.BLUE,
                                Color.MAGENTA, Color.GREEN);
                        setPosition(surfaceControl, -50, -50);
                        setBufferTransform(surfaceControl, /* NATIVE_WINDOW_TRANSFORM_ROT_90 */ 4);
                    }
                },
                new RectChecker(DEFAULT_RECT) {
                    @Override
                    public PixelColor getExpectedColor(int x, int y) {
                        int halfWidth = DEFAULT_LAYOUT_WIDTH / 2;
                        int halfHeight = DEFAULT_LAYOUT_HEIGHT / 2;
                        if (x < halfWidth && y < halfHeight) {
                            return BLUE;
                        } else {
                            return YELLOW;
                        }
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setCropSmall() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl = createFromWindow(holder.getSurface());

                        setQuadrantBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, Color.RED, Color.BLUE,
                                Color.MAGENTA, Color.GREEN);
                        setCrop(surfaceControl, new Rect(0, 0, 50, 50));
                    }
                },

                new RectChecker(DEFAULT_RECT) {
                    @Override
                    public PixelColor getExpectedColor(int x, int y) {
                        int halfWidth = DEFAULT_LAYOUT_WIDTH / 2;
                        int halfHeight = DEFAULT_LAYOUT_HEIGHT / 2;
                        if (x < halfWidth && y < halfHeight) {
                            return RED;
                        } else {
                            return YELLOW;
                        }
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setCropLarge() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl = createFromWindow(holder.getSurface());

                        setQuadrantBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, Color.RED, Color.BLUE,
                                Color.MAGENTA, Color.GREEN);
                        setCrop(surfaceControl, new Rect(0, 0, 150, 150));
                    }
                },

                new RectChecker(DEFAULT_RECT) {
                    @Override
                    public PixelColor getExpectedColor(int x, int y) {
                        int halfWidth = DEFAULT_LAYOUT_WIDTH / 2;
                        int halfHeight = DEFAULT_LAYOUT_HEIGHT / 2;
                        if (x < halfWidth && y < halfHeight) {
                            return RED;
                        } else if (x >= halfWidth && y < halfHeight) {
                            return BLUE;
                        } else if (x < halfWidth && y >= halfHeight) {
                            return GREEN;
                        } else {
                            return MAGENTA;
                        }
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setCropOffset() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl = createFromWindow(holder.getSurface());

                        setQuadrantBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, Color.RED, Color.BLUE,
                                Color.MAGENTA, Color.GREEN);
                        setCrop(surfaceControl, new Rect(50, 50, 100, 100));
                    }
                }, new RectChecker(DEFAULT_RECT) {
                    @Override
                    public PixelColor getExpectedColor(int x, int y) {
                        int halfWidth = DEFAULT_LAYOUT_WIDTH / 2;
                        int halfHeight = DEFAULT_LAYOUT_HEIGHT / 2;
                        // Only Magenta is visible in the lower right quadrant
                        if (x >= halfWidth && y >= halfHeight) {
                            return MAGENTA;
                        } else {
                            return YELLOW;
                        }
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setCropNegative() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl = createFromWindow(holder.getSurface());

                        setQuadrantBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, Color.RED, Color.BLUE,
                                Color.MAGENTA, Color.GREEN);
                        setCrop(surfaceControl, new Rect(-50, -50, 50, 50));
                    }
                }, new RectChecker(DEFAULT_RECT) {
                    @Override
                    public PixelColor getExpectedColor(int x, int y) {
                        int halfWidth = DEFAULT_LAYOUT_WIDTH / 2;
                        int halfHeight = DEFAULT_LAYOUT_HEIGHT / 2;
                        if (x < halfWidth && y < halfHeight) {
                            return RED;
                        } else {
                            return YELLOW;
                        }
                    }
                });
    }

    // Returns success of the surface transaction to decide whether to continue the test, such as
    // additional assertions.
    private boolean verifySetFrameTimeline(boolean usePreferredIndex, SurfaceHolder holder) {
        TimedTransactionListener onCompleteCallback = new TimedTransactionListener();
        long surfaceControl = nSurfaceControl_createFromWindow(holder.getSurface());
        assertTrue("failed to create surface control", surfaceControl != 0);
        long surfaceTransaction = createSurfaceTransaction();
        long buffer = nSurfaceTransaction_setSolidBuffer(surfaceControl, surfaceTransaction,
                DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT, Color.RED);
        assertTrue("failed to set buffer", buffer != 0);

        // Get choreographer frame timelines.
        FrameCallbackData frameCallbackData = nGetFrameTimelines();
        FrameTimeline[] frameTimelines = frameCallbackData.getFrameTimelines();

        int timelineIndex = frameCallbackData.getPreferredFrameTimelineIndex();
        if (!usePreferredIndex) {
            if (frameTimelines.length == 1) {
                // If there is only one frame timeline then it is already the preferred timeline.
                // Thus testing a non-preferred index is impossible.
                Log.i(TAG, "Non-preferred frame timeline does not exist");
                return false;
            }
            if (timelineIndex == frameTimelines.length - 1) {
                timelineIndex--;
            } else {
                timelineIndex++;
            }
        }
        FrameTimeline frameTimeline = frameTimelines[timelineIndex];
        long vsyncId = frameTimeline.getVsyncId();
        assertTrue("Vsync ID not valid", vsyncId > 0);

        Trace.beginSection("Surface transaction created " + vsyncId);
        nSurfaceTransaction_setFrameTimeline(surfaceTransaction, vsyncId);
        nSurfaceTransaction_setOnCompleteCallback(surfaceTransaction,
                true /* waitForFence */, onCompleteCallback);
        applyAndDeleteSurfaceTransaction(surfaceTransaction);
        Trace.endSection();

        Trace.beginSection("Wait for complete callback " + vsyncId);
        // Wait for callbacks to fire.
        try {
            onCompleteCallback.mLatch.await(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
        }
        if (onCompleteCallback.mLatch.getCount() > 0) {
            Log.e(TAG, "Failed to wait for callback");
        }
        Trace.endSection();

        assertEquals(0, onCompleteCallback.mLatch.getCount());
        assertTrue(onCompleteCallback.mCallbackTime > 0);
        assertTrue(onCompleteCallback.mLatchTime > 0);

        long periodNanos = (long) (1e9 / mActivity.getDisplay().getRefreshRate());
        long threshold = periodNanos / 2;
        // Check that the frame did not present earlier than the frame timeline chosen from setting
        // a vsyncId in the surface transaction; this should be guaranteed as part of the API
        // specification. Don't check whether the frame presents on-time since it can be flaky from
        // other delays.
        assertTrue("Frame presented too early using frame timeline index=" + timelineIndex
                        + " (preferred index=" + frameCallbackData.getPreferredFrameTimelineIndex()
                        + ", preferred vsyncId="
                        + frameTimelines[frameCallbackData.getPreferredFrameTimelineIndex()]
                                  .getVsyncId()
                        + "), vsyncId=" + frameTimeline.getVsyncId() + ", actual presentation time="
                        + onCompleteCallback.mPresentTime + ", expected presentation time="
                        + frameTimeline.getExpectedPresentTime() + ", actual - expected diff (ns)="
                        + (onCompleteCallback.mPresentTime - frameTimeline.getExpectedPresentTime())
                        + ", acceptable diff threshold (ns)= " + threshold,
                onCompleteCallback.mPresentTime
                        > frameTimeline.getExpectedPresentTime() - threshold);
        return true;
    }

    @Test
    @RequiresDevice // emulators can't support sync fences
    public void testSurfaceTransaction_setFrameTimeline_preferredIndex() {
        Trace.beginSection(
                "testSurfaceTransaction_setFrameTimeline_preferredIndex");
        Trace.endSection();

        BasicSurfaceHolderCallback basicSurfaceHolderCallback = new BasicSurfaceHolderCallback() {
            @Override
            public void surfaceCreated(SurfaceHolder surfaceHolder) {
                // Noop.
            }
        };
        final CountDownLatch readyFence = new CountDownLatch(1);
        ASurfaceControlTestActivity.SurfaceHolderCallback surfaceHolderCallback =
                new ASurfaceControlTestActivity.SurfaceHolderCallback(
                        new SurfaceHolderCallback(basicSurfaceHolderCallback), readyFence,
                        mActivity.getParentFrameLayout().getRootSurfaceControl());
        mActivity.createSurface(surfaceHolderCallback);
        try {
            assertTrue("timeout", readyFence.await(WAIT_TIMEOUT_S, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            Assert.fail("interrupted");
        }
        if (!verifySetFrameTimeline(true, mActivity.getSurfaceView().getHolder())) return;
        mActivity.verifyScreenshot(
                new PixelChecker(Color.RED) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                }, mName);

    }

    @Test
    @RequiresDevice // emulators can't support sync fences
    public void testSurfaceTransaction_setFrameTimeline_notPreferredIndex() {
        Trace.beginSection(
                "testSurfaceTransaction_setFrameTimeline_notPreferredIndex");
        Trace.endSection();

        BasicSurfaceHolderCallback basicSurfaceHolderCallback = new BasicSurfaceHolderCallback() {
            @Override
            public void surfaceCreated(SurfaceHolder surfaceHolder) {
                // Noop.
            }
        };
        final CountDownLatch readyFence = new CountDownLatch(1);
        ASurfaceControlTestActivity.SurfaceHolderCallback surfaceHolderCallback =
                new ASurfaceControlTestActivity.SurfaceHolderCallback(
                        new SurfaceHolderCallback(basicSurfaceHolderCallback), readyFence,
                        mActivity.getParentFrameLayout().getRootSurfaceControl());
        mActivity.createSurface(surfaceHolderCallback);
        try {
            assertTrue("timeout", readyFence.await(WAIT_TIMEOUT_S, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            Assert.fail("interrupted");
        }
        if (!verifySetFrameTimeline(true, mActivity.getSurfaceView().getHolder())) return;
        mActivity.verifyScreenshot(
                new PixelChecker(Color.RED) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                }, mName);

    }

    static class TimedTransactionListener implements
            ASurfaceControlTestUtils.TransactionCompleteListener {
        long mCallbackTime = -1;
        long mLatchTime = -1;
        long mPresentTime = -1;
        CountDownLatch mLatch = new CountDownLatch(1);

        @Override
        public void onTransactionComplete(long inLatchTime, long presentTime) {
            mCallbackTime = SystemClock.elapsedRealtime();
            mLatchTime = inLatchTime;
            mPresentTime = presentTime;
            mLatch.countDown();
        }
    }

    @Test
    public void testSurfaceTransactionOnCommitCallback_emptyTransaction()
            throws InterruptedException {
        // Create and send an empty transaction with onCommit and onComplete callbacks.
        long surfaceTransaction = nSurfaceTransaction_create();
        TimedTransactionListener onCompleteCallback = new TimedTransactionListener();
        nSurfaceTransaction_setOnCompleteCallback(surfaceTransaction, false /* waitForFence */,
                onCompleteCallback);
        TimedTransactionListener onCommitCallback = new TimedTransactionListener();
        nSurfaceTransaction_setOnCommitCallback(surfaceTransaction, onCommitCallback);
        nSurfaceTransaction_apply(surfaceTransaction);
        nSurfaceTransaction_delete(surfaceTransaction);

        // Wait for callbacks to fire.
        onCommitCallback.mLatch.await(1, TimeUnit.SECONDS);
        onCompleteCallback.mLatch.await(1, TimeUnit.SECONDS);

        // Validate we got callbacks.
        assertEquals(0, onCommitCallback.mLatch.getCount());
        assertTrue(onCommitCallback.mCallbackTime > 0);
        assertEquals(0, onCompleteCallback.mLatch.getCount());
        assertTrue(onCompleteCallback.mCallbackTime > 0);

        // Validate we received the callbacks in expected order.
        assertTrue(onCommitCallback.mCallbackTime <= onCompleteCallback.mCallbackTime);
    }

    @Test
    public void testSurfaceTransactionOnCommitCallback_bufferTransaction()
            throws Throwable {
        // Create and send a transaction with a buffer update and with onCommit and onComplete
        // callbacks.
        TimedTransactionListener onCompleteCallback = new TimedTransactionListener();
        TimedTransactionListener onCommitCallback = new TimedTransactionListener();
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceTransaction = nSurfaceTransaction_create();
                        long surfaceControl = createFromWindow(holder.getSurface());
                        setSolidBuffer(surfaceControl, surfaceTransaction, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, Color.RED);
                        nSurfaceTransaction_setOnCompleteCallback(
                                surfaceTransaction /* waitForFence */, false,
                                onCompleteCallback);
                        nSurfaceTransaction_setOnCommitCallback(surfaceTransaction,
                                onCommitCallback);
                        nSurfaceTransaction_apply(surfaceTransaction);
                        nSurfaceTransaction_delete(surfaceTransaction);

                        // Wait for callbacks to fire.
                        try {
                            onCommitCallback.mLatch.await(1, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                        }
                        if (onCommitCallback.mLatch.getCount() > 0) {
                            Log.e(TAG, "Failed to wait for commit callback");
                        }
                    }
                },
                new PixelChecker(Color.RED) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });

        onCompleteCallback.mLatch.await(1, TimeUnit.SECONDS);

        // Validate we got callbacks with a valid latch time.
        assertEquals(0, onCommitCallback.mLatch.getCount());
        assertTrue(onCommitCallback.mCallbackTime > 0);
        assertTrue(onCommitCallback.mLatchTime > 0);
        assertEquals(0, onCompleteCallback.mLatch.getCount());
        assertTrue(onCompleteCallback.mCallbackTime > 0);
        assertTrue(onCompleteCallback.mLatchTime > 0);

        // Validate we received the callbacks in expected order and the latch times reported
        // matches.
        assertTrue(onCommitCallback.mCallbackTime <= onCompleteCallback.mCallbackTime);
        assertEquals(onCommitCallback.mLatchTime, onCompleteCallback.mLatchTime);
    }

    @Test
    public void testSurfaceTransactionOnCommitCallback_geometryTransaction()
            throws Throwable {
        // Create and send a transaction with a buffer update and with onCommit and onComplete
        // callbacks.
        TimedTransactionListener onCompleteCallback = new TimedTransactionListener();
        TimedTransactionListener onCommitCallback = new TimedTransactionListener();
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceTransaction = nSurfaceTransaction_create();
                        long surfaceControl = createFromWindow(holder.getSurface());
                        setSolidBuffer(surfaceControl, surfaceTransaction, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, Color.RED);
                        nSurfaceTransaction_apply(surfaceTransaction);
                        nSurfaceTransaction_delete(surfaceTransaction);
                        surfaceTransaction = nSurfaceTransaction_create();
                        nSurfaceTransaction_setPosition(surfaceControl, surfaceTransaction, 1, 0);
                        nSurfaceTransaction_setOnCompleteCallback(surfaceTransaction,
                                false /* waitForFence */, onCompleteCallback);
                        nSurfaceTransaction_setOnCommitCallback(surfaceTransaction,
                                onCommitCallback);
                        nSurfaceTransaction_apply(surfaceTransaction);
                        nSurfaceTransaction_delete(surfaceTransaction);
                    }
                },
                new RectChecker(DEFAULT_RECT) {
                    @Override
                    public PixelColor getExpectedColor(int x, int y) {
                        if (x >= 1) {
                            return RED;
                        } else {
                            return YELLOW;
                        }
                    }
                });

        // Wait for callbacks to fire.
        onCommitCallback.mLatch.await(1, TimeUnit.SECONDS);
        onCompleteCallback.mLatch.await(1, TimeUnit.SECONDS);

        // Validate we got callbacks with a valid latch time.
        assertTrue(onCommitCallback.mLatch.getCount() == 0);
        assertTrue(onCommitCallback.mCallbackTime > 0);
        assertTrue(onCommitCallback.mLatchTime > 0);
        assertTrue(onCompleteCallback.mLatch.getCount() == 0);
        assertTrue(onCompleteCallback.mCallbackTime > 0);
        assertTrue(onCompleteCallback.mLatchTime > 0);

        // Validate we received the callbacks in expected order and the latch times reported
        // matches.
        assertTrue(onCommitCallback.mCallbackTime <= onCompleteCallback.mCallbackTime);
        assertTrue(onCommitCallback.mLatchTime == onCompleteCallback.mLatchTime);
    }

    @Test
    public void testSurfaceTransactionOnCommitCallback_withoutContext()
            throws InterruptedException {
        // Create and send an empty transaction with onCommit callbacks without context.
        long surfaceTransaction = nSurfaceTransaction_create();
        TimedTransactionListener onCommitCallback = new TimedTransactionListener();
        nSurfaceTransaction_setOnCommitCallbackWithoutContext(surfaceTransaction, onCommitCallback);
        nSurfaceTransaction_apply(surfaceTransaction);
        nSurfaceTransaction_delete(surfaceTransaction);

        // Wait for callbacks to fire.
        onCommitCallback.mLatch.await(1, TimeUnit.SECONDS);

        // Validate we got callbacks.
        assertEquals(0, onCommitCallback.mLatch.getCount());
        assertTrue(onCommitCallback.mCallbackTime > 0);
    }

    @Test
    public void testSurfaceTransactionOnCompleteCallback_withoutContext()
            throws InterruptedException {
        // Create and send an empty transaction with onComplete callbacks without context.
        long surfaceTransaction = nSurfaceTransaction_create();
        TimedTransactionListener onCompleteCallback = new TimedTransactionListener();
        nSurfaceTransaction_setOnCompleteCallbackWithoutContext(surfaceTransaction,
                false /* waitForFence */, onCompleteCallback);
        nSurfaceTransaction_apply(surfaceTransaction);
        nSurfaceTransaction_delete(surfaceTransaction);

        // Wait for callbacks to fire.
        onCompleteCallback.mLatch.await(1, TimeUnit.SECONDS);

        // Validate we got callbacks.
        assertEquals(0, onCompleteCallback.mLatch.getCount());
        assertTrue(onCompleteCallback.mCallbackTime > 0);
    }

    @Test
    public void testSetExtendedRangeBrightness() throws Exception {
        mActivity.awaitReadyState();
        Display display = mActivity.getDisplay();
        if (!display.isHdrSdrRatioAvailable()) {
            assertEquals(1.0f, display.getHdrSdrRatio(), 0.0001f);
        }
        // Set something super low so that if hdr/sdr ratio is available, we'll get some level
        // of HDR probably
        mActivity.getWindow().getAttributes().screenBrightness = 0.01f;
        // Wait for the screenBrightness to be picked up by VRI
        WidgetTestUtils.runOnMainAndDrawSync(mActivity.getParentFrameLayout(), () -> {});
        CountDownLatch hdrReady = new CountDownLatch(1);
        Exception[] listenerErrors = new Exception[1];
        if (display.isHdrSdrRatioAvailable()) {
            display.registerHdrSdrRatioChangedListener(Runnable::run, new Consumer<Display>() {
                boolean mIsRegistered = true;

                @Override
                public void accept(Display updatedDisplay) {
                    try {
                        assertEquals(display.getDisplayId(), updatedDisplay.getDisplayId());
                        assertTrue(mIsRegistered);
                        if (display.getHdrSdrRatio() > 2.f) {
                            hdrReady.countDown();
                            display.unregisterHdrSdrRatioChangedListener(this);
                            mIsRegistered = false;
                        }
                    } catch (Exception e) {
                        synchronized (mActivity) {
                            listenerErrors[0] = e;
                            hdrReady.countDown();
                        }
                    }
                }
            });
        } else {
            assertThrows(IllegalStateException.class, () ->
                    display.registerHdrSdrRatioChangedListener(Runnable::run, ignored -> {}));
        }

        final int extendedDataspace = DataSpace.pack(DataSpace.STANDARD_BT709,
                DataSpace.TRANSFER_SRGB, DataSpace.RANGE_EXTENDED);

        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceTransaction = nSurfaceTransaction_create();
                        long surfaceControl = createFromWindow(holder.getSurface());
                        setSolidBuffer(surfaceControl, surfaceTransaction, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, Color.WHITE);
                        nSurfaceTransaction_setDataSpace(surfaceControl, surfaceTransaction,
                                extendedDataspace);
                        nSurfaceTransaction_setExtendedRangeBrightness(surfaceControl,
                                surfaceTransaction, 3.f, 3.f);
                        nSurfaceTransaction_apply(surfaceTransaction);
                        nSurfaceTransaction_delete(surfaceTransaction);
                    }
                },
                new PixelChecker(Color.WHITE) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });

        // This isn't actually an error if it never happens, it's not _required_ that there's HDR
        // headroom available...
        if (display.isHdrSdrRatioAvailable()) {
            hdrReady.await(1, TimeUnit.SECONDS);
        }

        if (display.getHdrSdrRatio() > 2.f) {
            verifyTest(
                    new BasicSurfaceHolderCallback() {
                        @Override
                        public void surfaceCreated(SurfaceHolder holder) {
                            long surfaceTransaction = nSurfaceTransaction_create();
                            long surfaceControl = createFromWindow(holder.getSurface());
                            setSolidBuffer(surfaceControl, surfaceTransaction, DEFAULT_LAYOUT_WIDTH,
                                    DEFAULT_LAYOUT_HEIGHT, Color.WHITE);
                            nSurfaceTransaction_setDataSpace(surfaceControl, surfaceTransaction,
                                    extendedDataspace);
                            nSurfaceTransaction_setExtendedRangeBrightness(surfaceControl,
                                    surfaceTransaction, 3.f, 3.f);
                            nSurfaceTransaction_apply(surfaceTransaction);
                            nSurfaceTransaction_delete(surfaceTransaction);
                        }
                    },
                    new PixelChecker(Color.WHITE) { //10000
                        @Override
                        public boolean checkPixels(int pixelCount, int width, int height) {
                            return pixelCount > 9000 && pixelCount < 11000;
                        }
                    });
        }

        synchronized (mActivity) {
            if (listenerErrors[0] != null) {
                throw listenerErrors[0];
            }
        }
    }

    private float getStableHdrSdrRatio(Display display) {
        float ratio = -1f;
        float incomingRatio = display.getHdrSdrRatio();
        long startMillis = SystemClock.uptimeMillis();
        try {
            do {
                ratio = incomingRatio;
                TimeUnit.MILLISECONDS.sleep(100);
                incomingRatio = display.getHdrSdrRatio();
                // Bail if the ratio settled or if it's been way too long.
            } while (Math.abs(ratio - incomingRatio) > 0.01
                    && SystemClock.uptimeMillis() - startMillis < 10000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return ratio;
    }

    @Test
    public void testSetDesiredHdrHeadroom() throws Exception {
        mActivity.awaitReadyState();
        Display display = mActivity.getDisplay();
        assumeTrue(display.isHdrSdrRatioAvailable());

        final int dataspace = DataSpace.DATASPACE_BT2020_HLG;

        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceTransaction = nSurfaceTransaction_create();
                        long surfaceControl = createFromWindow(holder.getSurface());
                        setSolidBuffer(surfaceControl, surfaceTransaction, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, Color.WHITE);
                        nSurfaceTransaction_setDataSpace(surfaceControl, surfaceTransaction,
                                dataspace);
                        nSurfaceTransaction_apply(surfaceTransaction);
                        nSurfaceTransaction_delete(surfaceTransaction);
                    }
                },
                // Don't check pixels
                new PixelChecker(Color.WHITE) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return true;
                    }
                });

        float headroom = getStableHdrSdrRatio(display);
        // Require some small threshold for allowable headroom
        assumeTrue(headroom > 1.02f);
        float targetHeadroom = 1.f + (headroom - 1.f) / 2;

        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceTransaction = nSurfaceTransaction_create();
                        long surfaceControl = createFromWindow(holder.getSurface());
                        setSolidBuffer(surfaceControl, surfaceTransaction, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, Color.WHITE);
                        nSurfaceTransaction_setDataSpace(surfaceControl, surfaceTransaction,
                                dataspace);
                        nSurfaceTransaction_setDesiredHdrHeadroom(surfaceControl,
                                surfaceTransaction, targetHeadroom);
                        nSurfaceTransaction_apply(surfaceTransaction);
                        nSurfaceTransaction_delete(surfaceTransaction);
                    }
                },
                new PixelChecker(Color.WHITE) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        boolean achievedHeadroom =
                                getStableHdrSdrRatio(display) <= (targetHeadroom + 1.01);
                        assertTrue("Headroom restriction is not respected", achievedHeadroom);
                        return achievedHeadroom;
                    }
                });
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceTransaction = nSurfaceTransaction_create();
                        long surfaceControl = createFromWindow(holder.getSurface());
                        setSolidBuffer(surfaceControl, surfaceTransaction, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, Color.WHITE);
                        nSurfaceTransaction_setDataSpace(surfaceControl, surfaceTransaction,
                                dataspace);
                        nSurfaceTransaction_setDesiredHdrHeadroom(surfaceControl,
                                surfaceTransaction, 0.f);
                        nSurfaceTransaction_apply(surfaceTransaction);
                        nSurfaceTransaction_delete(surfaceTransaction);
                    }
                },
                new PixelChecker(Color.WHITE) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        boolean achievedHeadroom = getStableHdrSdrRatio(display) > targetHeadroom;
                        assertTrue("Removed headroom restriction is not respected",
                                achievedHeadroom);
                        return achievedHeadroom;
                    }
                });
    }
}
