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
import static android.view.cts.util.ASurfaceControlTestUtils.getBufferId;
import static android.view.cts.util.ASurfaceControlTestUtils.getQuadrantBuffer;
import static android.view.cts.util.ASurfaceControlTestUtils.getSolidBuffer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorSpace;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Region;
import android.hardware.DataSpace;
import android.hardware.HardwareBuffer;
import android.hardware.SyncFence;
import android.media.Image;
import android.media.ImageReader;
import android.media.ImageWriter;
import android.os.SystemClock;
import android.platform.test.annotations.RequiresDevice;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.util.Log;
import android.view.Choreographer;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceControl;
import android.view.SurfaceHolder;
import android.view.cts.surfacevalidator.ASurfaceControlTestActivity;
import android.view.cts.surfacevalidator.ASurfaceControlTestActivity.PixelChecker;
import android.view.cts.surfacevalidator.ASurfaceControlTestActivity.RectChecker;
import android.view.cts.surfacevalidator.PixelColor;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.filters.LargeTest;

import com.android.compatibility.common.util.WidgetTestUtils;
import com.android.cts.hardware.SyncFenceUtil;
import com.android.window.flags.Flags;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@LargeTest
@RunWith(JUnitParamsRunner.class)
public class SurfaceControlTest {
    static {
        System.loadLibrary("ctssurfacecontrol_jni");
    }

    private static final String TAG = SurfaceControlTest.class.getSimpleName();
    private static final boolean DEBUG = false;
    private static final int DEFAULT_SURFACE_SIZE = 100;
    private static final int DEFAULT_LAYOUT_WIDTH = DEFAULT_SURFACE_SIZE;
    private static final int DEFAULT_LAYOUT_HEIGHT = DEFAULT_SURFACE_SIZE;

    /**
     * Use a rect that doesn't include 1 pixel in the border since some composers add blending at
     * the edges. It's easier to just ignore those pixels and ensure the rest are correct.
     */
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

    private long mDesiredPresentTimeNanos;

    @Before
    public void setup() {
        mActivityRule.getScenario().onActivity(activity -> mActivity = activity);
    }

    SurfaceControl getHostSurfaceControl() {
        return mActivity.getSurfaceControl();
    }

    ///////////////////////////////////////////////////////////////////////////
    // SurfaceHolder.Callbacks
    ///////////////////////////////////////////////////////////////////////////

    private abstract class BasicSurfaceHolderCallback implements SurfaceHolder.Callback {
        private final Set<SurfaceControl> mSurfaceControls = new HashSet<>();
        private final Set<HardwareBuffer> mBuffers = new HashSet<>();

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            Canvas canvas = holder.lockCanvas();
            canvas.drawColor(Color.YELLOW);
            holder.unlockCanvasAndPost(canvas);
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            SurfaceControl.Transaction transaction = new SurfaceControl.Transaction();
            for (SurfaceControl surfaceControl : mSurfaceControls) {
                transaction.reparent(surfaceControl, null);
            }
            transaction.apply();
            mSurfaceControls.clear();

            for (HardwareBuffer buffer : mBuffers) {
                buffer.close();
            }
            mBuffers.clear();
        }

        public SurfaceControl createFromWindow(SurfaceHolder surfaceHolder) {
            assertNotNull("No parent?", getHostSurfaceControl());
            SurfaceControl surfaceControl = new SurfaceControl.Builder()
                    .setParent(getHostSurfaceControl())
                    .setName("SurfaceControl_createFromWindowLayer")
                    .setHidden(false)
                    .build();
            mSurfaceControls.add(surfaceControl);
            return surfaceControl;
        }

        public SurfaceControl create(SurfaceControl parentSurfaceControl) {
            assertNotNull("No parent?", parentSurfaceControl);
            SurfaceControl surfaceControl = new SurfaceControl.Builder()
                    .setParent(parentSurfaceControl)
                    .setName("SurfaceControl_create")
                    .setHidden(false)
                    .build();
            mSurfaceControls.add(surfaceControl);
            return surfaceControl;
        }

        public void setSolidBuffer(SurfaceControl surfaceControl,
                int width, int height, int color) {
            SurfaceControl.Transaction tx = new SurfaceControl.Transaction();
            setSolidBuffer(surfaceControl, tx, width, height, color);
            tx.apply();
        }

        public void setSolidBuffer(SurfaceControl surfaceControl,
                                   SurfaceControl.Transaction tx,
                                   int width, int height, int color) {
            HardwareBuffer buffer = getSolidBuffer(width, height, color);
            assertNotNull("failed to make solid buffer", buffer);
            tx.setBuffer(surfaceControl, buffer);
            mBuffers.add(buffer);
        }

        public void setSolidBuffer(SurfaceControl surfaceControl,
                int width, int height, int color, int dataSpace) {
            HardwareBuffer buffer = getSolidBuffer(width, height, color);
            assertNotNull("failed to make solid buffer", buffer);
            new SurfaceControl.Transaction()
                    .setBuffer(surfaceControl, buffer)
                    .setDataSpace(surfaceControl, dataSpace)
                    .apply();
            mBuffers.add(buffer);
        }

        public void setSolidBuffer(SurfaceControl surfaceControl,
                int width, int height, int color, SyncFence fence) {
            HardwareBuffer buffer = getSolidBuffer(width, height, color);
            assertNotNull("failed to make solid buffer", buffer);
            new SurfaceControl.Transaction()
                    .setBuffer(surfaceControl, buffer, fence)
                    .apply();
            mBuffers.add(buffer);
        }

        public void setQuadrantBuffer(SurfaceControl surfaceControl,
                int width, int height, int colorTopLeft, int colorTopRight, int colorBottomRight,
                int colorBottomLeft) {
            HardwareBuffer buffer = getQuadrantBuffer(width, height, colorTopLeft, colorTopRight,
                    colorBottomRight, colorBottomLeft);
            assertNotNull("failed to make solid buffer", buffer);
            new SurfaceControl.Transaction()
                    .setBuffer(surfaceControl, buffer)
                    .apply();
            mBuffers.add(buffer);
        }

        public void setSourceToDefaultDest(SurfaceControl surfaceControl, Rect src) {
            float scaleY = DEFAULT_LAYOUT_HEIGHT / (float) src.height();
            float scaleX = DEFAULT_LAYOUT_WIDTH / (float) src.width();
            new SurfaceControl.Transaction()
                    .setCrop(surfaceControl, new Rect(0, 0,
                            DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT))
                    .setPosition(surfaceControl, -src.left * scaleX, -src.top * scaleY)
                    .setScale(surfaceControl, scaleX, scaleY)
                    .apply();
        }
    }

    private abstract class SurfaceHolderCallback implements
            SurfaceHolder.Callback {

        public abstract void addChildren(SurfaceControl parent);

        @Override
        public void surfaceCreated(@NonNull SurfaceHolder holder) {
            addChildren(mActivity.getSurfaceView().getSurfaceControl());
        }

        @Override
        public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width,
                int height) {}

        @Override
        public void surfaceDestroyed(@NonNull SurfaceHolder holder) {}
    }



    ///////////////////////////////////////////////////////////////////////////
    // Tests
    ///////////////////////////////////////////////////////////////////////////

    private void verifyTest(SurfaceHolder.Callback callback, PixelChecker pixelChecker) {
        mActivity.verifyTest(callback, pixelChecker, mName);
    }

    private void verifyTest(SurfaceHolder.Callback callback, PixelChecker pixelChecker,
            int numOfTransaction) {
        mActivity.verifyTest(callback, pixelChecker, mName, numOfTransaction);
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
                        createFromWindow(holder);
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
                        SurfaceControl parentSurfaceControl = createFromWindow(holder);
                        SurfaceControl childSurfaceControl = create(parentSurfaceControl);
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
    public void testSurfaceControl_acquire() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl surfaceControl = createFromWindow(holder);
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
                        SurfaceControl surfaceControl = createFromWindow(holder);
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
    public void testSurfaceTransaction_setBuffer_parentAndChild() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl parentSurfaceControl = createFromWindow(holder);
                        SurfaceControl childSurfaceControl = create(parentSurfaceControl);

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
                        SurfaceControl parentSurfaceControl = createFromWindow(holder);
                        SurfaceControl childSurfaceControl = create(parentSurfaceControl);

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
                        SurfaceControl surfaceControl = createFromWindow(holder);

                        setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                Color.RED);
                        new SurfaceControl.Transaction()
                                .setVisibility(surfaceControl, true)
                                .apply();
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
                        SurfaceControl surfaceControl = createFromWindow(holder);

                        setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                Color.RED);
                        new SurfaceControl.Transaction()
                                .setVisibility(surfaceControl, false)
                                .apply();
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
                        SurfaceControl surfaceControl = createFromWindow(holder);

                        setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                PixelColor.TRANSLUCENT_RED);
                        new SurfaceControl.Transaction()
                                .setOpaque(surfaceControl, true)
                                .apply();
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
                        SurfaceControl surfaceControl = createFromWindow(holder);

                        setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                PixelColor.TRANSLUCENT_RED);
                        new SurfaceControl.Transaction()
                                .setOpaque(surfaceControl, false)
                                .apply();
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
                new PixelChecker(Color.YELLOW) {
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
                        SurfaceControl surfaceControl = createFromWindow(holder);

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
                        SurfaceControl surfaceControl = createFromWindow(holder);

                        setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                Color.RED);
                        new SurfaceControl.Transaction()
                                .setScale(surfaceControl, .4f, .4f)
                                .setPosition(surfaceControl, 10, 10)
                                .apply();
                    }
                },
                new RectChecker(new Rect(0, 0, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT)) {
                    @Override
                    public PixelColor getExpectedColor(int x, int y) {
                        if (x >= 10 && x < 50 && y >= 10 && y < 50) {
                            return RED;
                        }
                        return YELLOW;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setDestinationRect_smallScaleFirst() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl surfaceControl = createFromWindow(holder);

                        setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                Color.RED);
                        new SurfaceControl.Transaction()
                                .setScale(surfaceControl, .4f, .4f)
                                .apply();
                        new SurfaceControl.Transaction()
                                .setPosition(surfaceControl, 10, 10)
                                .apply();
                    }
                },
                new RectChecker(new Rect(0, 0, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT)) {
                    @Override
                    public PixelColor getExpectedColor(int x, int y) {
                        if (x >= 10 && x < 50 && y >= 10 && y < 50) {
                            return RED;
                        }
                        return YELLOW;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setDestinationRect_smallPositionFirst() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl surfaceControl = createFromWindow(holder);

                        setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                Color.RED);
                        new SurfaceControl.Transaction()
                                .setPosition(surfaceControl, 10, 10)
                                .apply();
                        new SurfaceControl.Transaction()
                                .setScale(surfaceControl, .4f, .4f)
                                .apply();
                    }
                },
                new RectChecker(new Rect(0, 0, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT)) {
                    @Override
                    public PixelColor getExpectedColor(int x, int y) {
                        if (x >= 10 && x < 50 && y >= 10 && y < 50) {
                            return RED;
                        }
                        return YELLOW;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setDestinationRect_parentSmall() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl parentSurfaceControl = createFromWindow(holder);
                        SurfaceControl childSurfaceControl = create(parentSurfaceControl);

                        setSolidBuffer(childSurfaceControl, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, Color.RED);
                        new SurfaceControl.Transaction()
                                .setScale(parentSurfaceControl, .4f, .4f)
                                .setPosition(parentSurfaceControl, 10, 10)
                                .apply();
                    }
                },
                new PixelChecker(Color.RED) { //1600
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 1440 && pixelCount < 1760;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setDestinationRect_childSmall() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl parentSurfaceControl = createFromWindow(holder);
                        SurfaceControl childSurfaceControl = create(parentSurfaceControl);

                        setSolidBuffer(childSurfaceControl, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, Color.RED);
                        new SurfaceControl.Transaction()
                                .setScale(childSurfaceControl, .4f, .4f)
                                .setPosition(childSurfaceControl, 10, 10)
                                .apply();
                    }
                },
                new PixelChecker(Color.RED) { //1600
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 1440 && pixelCount < 1760;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setDestinationRect_extraLarge() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl surfaceControl = createFromWindow(holder);

                        setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                Color.RED);
                        new SurfaceControl.Transaction()
                                .setScale(surfaceControl, 3f, 3f)
                                .setPosition(surfaceControl, -100, -100)
                                .apply();
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
                        SurfaceControl parentSurfaceControl = createFromWindow(holder);
                        SurfaceControl childSurfaceControl = create(parentSurfaceControl);

                        setSolidBuffer(childSurfaceControl, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, Color.RED);
                        new SurfaceControl.Transaction()
                                .setScale(childSurfaceControl, 3f, 3f)
                                .setPosition(childSurfaceControl, -100, -100)
                                .apply();
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
                        SurfaceControl surfaceControl = createFromWindow(holder);

                        setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                Color.RED);
                        new SurfaceControl.Transaction()
                                .setPosition(surfaceControl, -20, -30)
                                .apply();
                    }
                },
                new PixelChecker(Color.RED) { //5600 (w = 80, h = 70)
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 5000 && pixelCount < 6000;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setDestinationRect_outOfParentBounds() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl surfaceControl = createFromWindow(holder);

                        setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                Color.RED);
                        new SurfaceControl.Transaction()
                                .setPosition(surfaceControl, 50, 50)
                                .apply();
                    }
                },
                new PixelChecker(Color.RED) { //2500
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 2250 && pixelCount < 2750;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setDestinationRect_twoLayers() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl surfaceControl1 = createFromWindow(holder);
                        SurfaceControl surfaceControl2 = createFromWindow(holder);

                        setSolidBuffer(surfaceControl1, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                Color.RED);
                        setSolidBuffer(surfaceControl2, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                Color.BLUE);
                        new SurfaceControl.Transaction()
                                .setPosition(surfaceControl1, 10, 10)
                                .setScale(surfaceControl1, .2f, .3f)
                                .apply();
                        new SurfaceControl.Transaction()
                                .setPosition(surfaceControl2, 70, 20)
                                .setScale(surfaceControl2, .2f, .3f)
                                .apply();
                    }
                },

                new RectChecker(new Rect(0, 0, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT)) {
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
                        SurfaceControl surfaceControl = createFromWindow(holder);

                        setQuadrantBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, Color.RED, Color.BLUE,
                                Color.MAGENTA, Color.GREEN);
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
                });
    }

    @Test
    public void testSurfaceTransaction_setSourceRect_small() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl surfaceControl = createFromWindow(holder);

                        setQuadrantBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, Color.RED, Color.BLUE,
                                Color.MAGENTA, Color.GREEN);
                        setSourceToDefaultDest(surfaceControl, new Rect(60, 10, 90, 90));
                    }
                },
                new PixelChecker(Color.MAGENTA) { //5000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 4500 && pixelCount < 5500;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setSourceRect_smallCentered() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl surfaceControl = createFromWindow(holder);

                        setQuadrantBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, Color.RED, Color.BLUE,
                                Color.MAGENTA, Color.GREEN);
                        setSourceToDefaultDest(surfaceControl, new Rect(40, 40, 60, 60));
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
    public void testSurfaceTransaction_setCropRect_extraLarge() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl surfaceControl = createFromWindow(holder);

                        setQuadrantBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, Color.RED, Color.BLUE,
                                Color.MAGENTA, Color.GREEN);
                        new SurfaceControl.Transaction()
                                .setCrop(surfaceControl, new Rect(-50, -50, 150, 150))
                                .apply();
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
                });
    }

    @Test
    public void testSurfaceTransaction_setCropRect_badOffset() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl surfaceControl = createFromWindow(holder);

                        setQuadrantBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, Color.RED, Color.BLUE,
                                Color.MAGENTA, Color.GREEN);
                        new SurfaceControl.Transaction()
                                .setCrop(surfaceControl, new Rect(-50, -50, 50, 50))
                                .apply();
                    }
                },
                new RectChecker(new Rect(0, 0, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT)) {
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
    public void testSurfaceTransaction_setCropRect_invalidCropWidth() {
        final AtomicBoolean caughtException = new AtomicBoolean(false);
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl surfaceControl = createFromWindow(holder);
                        setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                Color.YELLOW);
                        try {
                            new SurfaceControl.Transaction()
                                    .setCrop(surfaceControl, new Rect(0, 0, -1, 100))
                                    .apply();
                        } catch (IllegalArgumentException e) {
                            caughtException.set(true);
                        }
                    }
                },
                new PixelChecker(Color.YELLOW) { //10000
                    @Override
                    public boolean checkPixels(int matchingPixelCount, int width, int height) {
                        return matchingPixelCount > 9000 && matchingPixelCount < 11000;
                    }
                }
        );
        assertTrue(caughtException.get());
    }

    @Test
    public void testSurfaceTransaction_setCropRect_invalidCropHeight() {
        final AtomicBoolean caughtException = new AtomicBoolean(false);
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl surfaceControl = createFromWindow(holder);
                        setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                Color.YELLOW);
                        try {
                            new SurfaceControl.Transaction()
                                    .setCrop(surfaceControl, new Rect(0, 0, 100, -1))
                                    .apply();
                        } catch (IllegalArgumentException e) {
                            caughtException.set(true);
                        }
                    }
                },
                new PixelChecker(Color.YELLOW) { //10000
                    @Override
                    public boolean checkPixels(int matchingPixelCount, int width, int height) {
                        return matchingPixelCount > 9000 && matchingPixelCount < 11000;
                    }
                }
        );
        assertTrue(caughtException.get());
    }

    @Test
    public void testSurfaceTransaction_setTransform_flipH() {
        int halfWidth = DEFAULT_LAYOUT_WIDTH / 2;
        int halfHeight = DEFAULT_LAYOUT_HEIGHT / 2;
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl surfaceControl = createFromWindow(holder);

                        setQuadrantBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, Color.RED, Color.BLUE,
                                Color.MAGENTA, Color.GREEN);
                        new SurfaceControl.Transaction()
                                .setBufferTransform(surfaceControl, 1)
                                .apply();
                    }
                },
                new RectChecker(new Rect(0, 0, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT)) {
                    @Override
                    public PixelColor getExpectedColor(int x, int y) {
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
                        SurfaceControl surfaceControl = createFromWindow(holder);

                        setQuadrantBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, Color.RED, Color.BLUE,
                                Color.MAGENTA, Color.GREEN);
                        setSourceToDefaultDest(surfaceControl, new Rect(0, 50, 50, 100));
                        new SurfaceControl.Transaction()
                                .setBufferTransform(surfaceControl, 3)
                                .apply();
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
    public void testSurfaceTransaction_setDamageRegion_all() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl surfaceControl = createFromWindow(holder);
                        setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                Color.RED);

                        HardwareBuffer blueBuffer = getSolidBuffer(DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, Color.BLUE);
                        Region damageRegion = new Region();
                        damageRegion.op(0, 0, 25, 25, Region.Op.UNION);
                        damageRegion.op(25, 25, 100, 100, Region.Op.UNION);
                        new SurfaceControl.Transaction()
                                .setBuffer(surfaceControl, blueBuffer)
                                .setDamageRegion(surfaceControl, damageRegion)
                                .apply();
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
                        SurfaceControl surfaceControl1 = createFromWindow(holder);
                        SurfaceControl surfaceControl2 = createFromWindow(holder);
                        setSolidBuffer(surfaceControl1, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                Color.RED);
                        setSolidBuffer(surfaceControl2, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                Color.MAGENTA);

                        new SurfaceControl.Transaction()
                                .setLayer(surfaceControl1, 1)
                                .setLayer(surfaceControl2, 0)
                                .apply();
                    }
                },
                new PixelChecker(Color.YELLOW) {
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount == 0;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setZOrder_positive() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl surfaceControl1 = createFromWindow(holder);
                        SurfaceControl surfaceControl2 = createFromWindow(holder);
                        setSolidBuffer(surfaceControl1, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                Color.RED);
                        setSolidBuffer(surfaceControl2, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                Color.MAGENTA);

                        new SurfaceControl.Transaction()
                                .setLayer(surfaceControl1, 1)
                                .setLayer(surfaceControl2, 5)
                                .apply();
                    }
                },
                new PixelChecker(Color.RED) {
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount == 0;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setZOrder_negative() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl surfaceControl1 = createFromWindow(holder);
                        SurfaceControl surfaceControl2 = createFromWindow(holder);
                        setSolidBuffer(surfaceControl1, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                Color.RED);
                        setSolidBuffer(surfaceControl2, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                Color.MAGENTA);

                        new SurfaceControl.Transaction()
                                .setLayer(surfaceControl1, 1)
                                .setLayer(surfaceControl2, -15)
                                .apply();
                    }
                },
                new PixelChecker(Color.YELLOW) {
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount == 0;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setZOrder_max() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl surfaceControl1 = createFromWindow(holder);
                        SurfaceControl surfaceControl2 = createFromWindow(holder);
                        setSolidBuffer(surfaceControl1, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                Color.RED);
                        setSolidBuffer(surfaceControl2, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                Color.MAGENTA);

                        new SurfaceControl.Transaction()
                                .setLayer(surfaceControl1, 1)
                                .setLayer(surfaceControl2, Integer.MAX_VALUE)
                                .apply();
                    }
                },
                new PixelChecker(Color.RED) {
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount == 0;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setZOrder_min() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl surfaceControl1 = createFromWindow(holder);
                        SurfaceControl surfaceControl2 = createFromWindow(holder);
                        setSolidBuffer(surfaceControl1, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                Color.RED);
                        setSolidBuffer(surfaceControl2, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                Color.MAGENTA);

                        new SurfaceControl.Transaction()
                                .setLayer(surfaceControl1, 1)
                                .setLayer(surfaceControl2, Integer.MIN_VALUE)
                                .apply();
                    }
                },
                new PixelChecker(Color.YELLOW) {
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount == 0;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setDataSpace_srgb() {
        final int darkRed = 0xFF6F0000;
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl surfaceControl = createFromWindow(holder);
                        setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                darkRed, DataSpace.DATASPACE_SRGB);
                    }
                },
                new PixelChecker(darkRed) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setDataSpace_display_p3() {
        final int darkRed = 0xFF6F0000;
        long converted = Color.convert(0x6F / 255.f, 0f, 0f, 1f,
                ColorSpace.get(ColorSpace.Named.DISPLAY_P3), ColorSpace.get(ColorSpace.Named.SRGB));
        assertTrue(Color.isSrgb(converted));
        int argb = Color.toArgb(converted);
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl surfaceControl = createFromWindow(holder);
                        setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                darkRed, DataSpace.DATASPACE_DISPLAY_P3);
                    }
                },
                new PixelChecker(argb) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_nullSyncFence() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl surfaceControl = createFromWindow(holder);
                        setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                Color.BLUE, null);
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
    public void testSurfaceTransaction_invalidSyncFence() {
        SyncFence fence = SyncFenceUtil.createUselessFence();
        if (fence == null) {
            // Extension not supported
            Log.d(TAG, "Skipping test, EGL_ANDROID_native_fence_sync not available");
            return;
        }
        fence.close();
        assertFalse(fence.isValid());
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl surfaceControl = createFromWindow(holder);
                        setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                Color.BLUE, fence);
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
    public void testSurfaceTransaction_withSyncFence() {
        SyncFence fence = SyncFenceUtil.createUselessFence();
        if (fence == null) {
            // Extension not supported
            Log.d(TAG, "Skipping test, EGL_ANDROID_native_fence_sync not available");
            return;
        }
        assertTrue(fence.isValid());
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl surfaceControl = createFromWindow(holder);
                        setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                Color.BLUE, fence);
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
    public void testSurfaceControl_scaleToZero() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl parentSurfaceControl = createFromWindow(holder);
                        SurfaceControl childSurfaceControl = create(parentSurfaceControl);

                        setSolidBuffer(parentSurfaceControl,
                                DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT, Color.YELLOW);
                        setSolidBuffer(childSurfaceControl,
                                DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT, Color.RED);
                        new SurfaceControl.Transaction()
                                .setScale(childSurfaceControl, 0, 0)
                                .apply();
                    }
                },
                new PixelChecker(Color.YELLOW) { //10000
                    @Override
                    public boolean checkPixels(int matchingPixelCount, int width, int height) {
                        return matchingPixelCount > 9000 && matchingPixelCount < 11000;
                    }
                }
        );
    }

    @Test
    public void testSurfaceTransaction_negativeScaleX() {
        final AtomicBoolean caughtException = new AtomicBoolean(false);
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl surfaceControl = createFromWindow(holder);
                        setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                Color.YELLOW);
                        try {
                            new SurfaceControl.Transaction()
                                    .setScale(surfaceControl, -1, 1)
                                    .apply();
                        } catch (IllegalArgumentException e) {
                            caughtException.set(true);
                        }
                    }
                },
                new PixelChecker(Color.YELLOW) { //10000
                    @Override
                    public boolean checkPixels(int matchingPixelCount, int width, int height) {
                        return matchingPixelCount > 9000 && matchingPixelCount < 11000;
                    }
                }
        );
        assertTrue(caughtException.get());
    }

    @Test
    public void testSurfaceTransaction_negativeScaleY() {
        final AtomicBoolean caughtException = new AtomicBoolean(false);
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl surfaceControl = createFromWindow(holder);
                        setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                Color.YELLOW);
                        try {
                            new SurfaceControl.Transaction()
                                    .setScale(surfaceControl, 1, -1)
                                    .apply();
                        } catch (IllegalArgumentException e) {
                            caughtException.set(true);
                        }
                    }
                },
                new PixelChecker(Color.YELLOW) { //10000
                    @Override
                    public boolean checkPixels(int matchingPixelCount, int width, int height) {
                        return matchingPixelCount > 9000 && matchingPixelCount < 11000;
                    }
                }
        );
        assertTrue(caughtException.get());
    }

    class OnCompleteListenerHelper {
        private CountDownLatch mLatch = new CountDownLatch(1);
        private long mLatchTimeNanos;
        private SyncFence mPresentFence;

        SurfaceControl.Transaction getTransaction() {
            return makeTransactionWithListener().addTransactionCompletedListener(
                    Runnable::run,  stats -> {
                        mLatchTimeNanos = stats.getLatchTimeNanos();
                        mPresentFence = stats.getPresentFence();
                        mLatch.countDown();
                    });
        }

        void waitForStats() throws InterruptedException {
            assertTrue(mLatch.await(5, TimeUnit.SECONDS));
        }

        SyncFence getPresentFence() {
            return mPresentFence;
        }

        long getLatchTimeNanos() {
            return mLatchTimeNanos;
        }

        void close() {
            mPresentFence.close();
            mPresentFence = null;
        }
    }

    @Test
    @RequiresDevice // emulators can't support sync fences
    @RequiresFlagsEnabled(Flags.FLAG_SDK_DESIRED_PRESENT_TIME)
    public void testSurfaceTransaction_setDesiredPresentTime_now() throws InterruptedException {
        assumeTrue(Flags.sdkDesiredPresentTime());

        final OnCompleteListenerHelper helper = new OnCompleteListenerHelper();
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl surfaceControl = createFromWindow(holder);
                        SurfaceControl.Transaction tx = helper.getTransaction();
                        setSolidBuffer(surfaceControl, tx, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, Color.RED);

                        mDesiredPresentTimeNanos = System.nanoTime();
                        tx.setDesiredPresentTimeNanos(mDesiredPresentTimeNanos).apply();
                    }
                },
                new PixelChecker(Color.RED) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                }, 1);

        helper.waitForStats();
        assertTrue(helper.getLatchTimeNanos() > 0);

        assertTrue(helper.getPresentFence() != null);
        assertTrue(helper.getPresentFence().await(Duration.ofSeconds(5)));
        assertTrue("transaction was presented too early. presentTime="
                        + helper.getPresentFence().getSignalTime(),
                helper.getPresentFence().getSignalTime() >= mDesiredPresentTimeNanos);
        helper.close();
    }

    @Test
    @RequiresDevice // emulators can't support sync fences
    @RequiresFlagsEnabled(Flags.FLAG_SDK_DESIRED_PRESENT_TIME)
    public void testSurfaceTransaction_setDesiredPresentTime_30ms() throws InterruptedException {
        assumeTrue(Flags.sdkDesiredPresentTime());

        final OnCompleteListenerHelper helper = new OnCompleteListenerHelper();
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl surfaceControl = createFromWindow(holder);
                        SurfaceControl.Transaction tx = helper.getTransaction();
                        setSolidBuffer(surfaceControl, tx, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, Color.RED);

                        mDesiredPresentTimeNanos = System.nanoTime() + 30_000_000;
                        tx.setDesiredPresentTimeNanos(mDesiredPresentTimeNanos).apply();
                    }
                },
                new PixelChecker(Color.RED) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                }, 1);

        helper.waitForStats();
        assertTrue(helper.getLatchTimeNanos() > 0);
        assertTrue(helper.getPresentFence() != null);
        assertTrue(helper.getPresentFence().await(Duration.ofSeconds(5)));
        assertTrue("transaction was presented too early. presentTime="
                        + helper.getPresentFence().getSignalTime(),
                helper.getPresentFence().getSignalTime() >= mDesiredPresentTimeNanos);
        helper.close();
    }

    @Test
    @RequiresDevice // emulators can't support sync fences
    @RequiresFlagsEnabled(Flags.FLAG_SDK_DESIRED_PRESENT_TIME)
    public void testSurfaceTransaction_setDesiredPresentTime_100ms() throws InterruptedException {
        assumeTrue(Flags.sdkDesiredPresentTime());

        final OnCompleteListenerHelper helper = new OnCompleteListenerHelper();
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl surfaceControl = createFromWindow(holder);
                        SurfaceControl.Transaction tx = helper.getTransaction();
                        setSolidBuffer(surfaceControl, tx, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, Color.RED);

                        mDesiredPresentTimeNanos = System.nanoTime() + 100_000_000;
                        tx.setDesiredPresentTimeNanos(mDesiredPresentTimeNanos).apply();
                    }
                },
                new PixelChecker(Color.RED) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                }, 1);

        helper.waitForStats();
        assertTrue(helper.getLatchTimeNanos() > 0);
        assertTrue(helper.getPresentFence() != null);
        assertTrue(helper.getPresentFence().await(Duration.ofSeconds(5)));
        assertTrue("transaction was presented too early. presentTime="
                        + helper.getPresentFence().getSignalTime(),
                helper.getPresentFence().getSignalTime() >= mDesiredPresentTimeNanos);
        helper.close();
    }

    @Test
    @RequiresDevice // emulators can't support sync fences
    @RequiresFlagsEnabled(Flags.FLAG_SDK_DESIRED_PRESENT_TIME)
    public void testSurfaceTransaction_setFrameTimeline_preferredIndex()
            throws InterruptedException {
        assumeTrue(Flags.sdkDesiredPresentTime());

        final OnCompleteListenerHelper helper = new OnCompleteListenerHelper();
        final SurfaceControl.Transaction[] transaction = new SurfaceControl.Transaction[1];

        Choreographer.VsyncCallback vsyncCallback = new Choreographer.VsyncCallback() {
            @Override
            public void onVsync(@NonNull Choreographer.FrameData frameData) {
                long periodNanos = (long) (1e9 / mActivity.getDisplay().getRefreshRate());
                long threshold = periodNanos / 2;
                mDesiredPresentTimeNanos = frameData.getPreferredFrameTimeline()
                        .getExpectedPresentationTimeNanos() - threshold;
                transaction[0].setFrameTimeline(
                        frameData.getPreferredFrameTimeline().getVsyncId()).apply();
            }
        };

        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl surfaceControl = createFromWindow(holder);
                        transaction[0] = helper.getTransaction();
                        setSolidBuffer(surfaceControl, transaction[0], DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, Color.RED);
                        Choreographer.getInstance().postVsyncCallback(vsyncCallback);

                    }
                },
            new PixelChecker(Color.RED) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                }, 1);

        helper.waitForStats();
        assertTrue(helper.getLatchTimeNanos() > 0);
        assertTrue(helper.getPresentFence() != null);
        assertTrue(helper.getPresentFence().await(Duration.ofSeconds(5)));
        assertTrue("transaction was presented too early. presentTime="
                        + helper.getPresentFence().getSignalTime(),
                helper.getPresentFence().getSignalTime() >= mDesiredPresentTimeNanos);
        helper.close();
    }

    @Test
    @RequiresDevice // emulators can't support sync fences
    @RequiresFlagsEnabled(Flags.FLAG_SDK_DESIRED_PRESENT_TIME)
    public void testSurfaceTransaction_setFrameTimeline_nonPreferredIndex()
            throws InterruptedException {
        assumeTrue(Flags.sdkDesiredPresentTime());

        final OnCompleteListenerHelper helper = new OnCompleteListenerHelper();
        final SurfaceControl.Transaction[] transaction = new SurfaceControl.Transaction[1];

        Choreographer.VsyncCallback vsyncCallback = new Choreographer.VsyncCallback() {
            @Override
            public void onVsync(@NonNull Choreographer.FrameData frameData) {
                long periodNanos =
                        (long) (1e9 / mActivity.getDisplay().getRefreshRate());
                long threshold = periodNanos / 2;
                Choreographer.FrameTimeline[] timelines = frameData.getFrameTimelines();
                Choreographer.FrameTimeline lastTimeline = timelines[timelines.length - 1];
                mDesiredPresentTimeNanos =
                        lastTimeline.getExpectedPresentationTimeNanos() - threshold;
                transaction[0].setFrameTimeline(lastTimeline.getVsyncId()).apply();
            }
        };
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl surfaceControl = createFromWindow(holder);
                        transaction[0] = helper.getTransaction();
                        setSolidBuffer(surfaceControl, transaction[0], DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, Color.RED);
                        Choreographer.getInstance().postVsyncCallback(vsyncCallback);

                    }
                },
                new PixelChecker(Color.RED) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                }, 1);

        helper.waitForStats();
        assertTrue(helper.getLatchTimeNanos() > 0);
        assertTrue(helper.getPresentFence() != null);
        assertTrue(helper.getPresentFence().await(Duration.ofSeconds(5)));
        assertTrue("transaction was presented too early. presentTime="
                        + helper.getPresentFence().getSignalTime(),
                helper.getPresentFence().getSignalTime() >= mDesiredPresentTimeNanos);
        helper.close();
    }

    /**
     * @param delayMs delay between calling setBuffer
     */
    private void releaseBufferCallbackHelper(long delayMs) throws InterruptedException {
        final int setBufferCount = 3;
        CountDownLatch releaseCounter = new CountDownLatch(setBufferCount);
        long[] bufferIds = new long[setBufferCount];
        long[] receivedCallbacks = new long[setBufferCount];
        SyncFence[] receivedFences = new SyncFence[setBufferCount];
        long timeBeforeTest = System.nanoTime();
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl surfaceControl = createFromWindow(holder);
                        for (int i = 0; i < setBufferCount; i++) {
                            HardwareBuffer buffer = getSolidBuffer(DEFAULT_LAYOUT_WIDTH,
                                    DEFAULT_LAYOUT_HEIGHT, Color.YELLOW);
                            final int receiveIndex = i;
                            final long bufferId = getBufferId(buffer);
                            bufferIds[receiveIndex] = bufferId;
                            new SurfaceControl.Transaction()
                                    .setBuffer(surfaceControl, buffer, null,
                                            (SyncFence fence) -> {
                                                receivedCallbacks[receiveIndex] = bufferId;
                                                receivedFences[receiveIndex] = fence;
                                                releaseCounter.countDown();
                                            })
                                    .apply();
                            buffer.close();
                            try {
                                Thread.sleep(delayMs);
                            } catch (InterruptedException e) {
                            }
                        }
                        setSolidBuffer(surfaceControl,
                                DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT, Color.YELLOW);
                    }
                },
                new PixelChecker(Color.YELLOW) {
                    @Override
                    public boolean checkPixels(int matchingPixelCount, int width, int height) {
                        return matchingPixelCount > 9000 && matchingPixelCount < 11000;
                    }
                }
        );

        assertTrue(releaseCounter.await(5, TimeUnit.SECONDS));
        for (int i = 0; i < setBufferCount; i++) {
            assertEquals(bufferIds[i], receivedCallbacks[i]);
            if (i > 0) {
                assertNotEquals(bufferIds[i], bufferIds[i - 1]);
            }
            SyncFence fence = receivedFences[i];
            assertNotNull(fence);
            if (fence.isValid()) {
                fence.awaitForever();
                assertTrue(fence.getSignalTime() > timeBeforeTest);
                assertTrue(fence.getSignalTime() < System.nanoTime());
                fence.close();
            }
        }
    }

    @Test
    public void testReleaseBufferCallback() throws InterruptedException {
        releaseBufferCallbackHelper(0 /* delayMs */);
    }

    /**
     * This test is different than above {@link #testReleaseBufferCallback} since it's testing the
     * scenario where the buffers are sent a bit slower. This helps test a more real case where
     * we're more likely not to overwrite the buffers before latched. The delay isn't to fix
     * flakiness, but to actually try to test the case where buffers are latched and not just
     * directly overwritten. We could pace with commit callback, but that creates behavior that
     * users of setBuffer may not do, which could result in different output.
     *
     * Instead set 100ms delay between each buffer since that should help give time to SF to latch
     * a buffer before sending another one.
     */
    @Test
    public void testReleaseBufferCallback_Slow() throws InterruptedException {
        releaseBufferCallbackHelper(100 /* delayMs */);
    }

    @Test
    public void testReleaseBufferCallbackSameBuffer() throws InterruptedException {
        final int setBufferCount = 3;
        CountDownLatch releaseCounter = new CountDownLatch(setBufferCount);
        long[] bufferIds = new long[setBufferCount];
        long[] receivedCallbacks = new long[setBufferCount];
        SyncFence[] receivedFences = new SyncFence[setBufferCount];
        long timeBeforeTest = System.nanoTime();
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl surfaceControl = createFromWindow(holder);
                        HardwareBuffer buffer = getSolidBuffer(DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, Color.YELLOW);
                        for (int i = 0; i < setBufferCount; i++) {
                            final int receiveIndex = i;
                            final long bufferId = getBufferId(buffer);
                            bufferIds[receiveIndex] = bufferId;
                            new SurfaceControl.Transaction()
                                    .setBuffer(surfaceControl, buffer, null,
                                            (SyncFence fence) -> {
                                                receivedCallbacks[receiveIndex] = bufferId;
                                                receivedFences[receiveIndex] = fence;
                                                releaseCounter.countDown();
                                            })
                                    .apply();
                        }
                        buffer.close();
                        setSolidBuffer(surfaceControl,
                                DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT, Color.YELLOW);
                    }
                },
                new PixelChecker(Color.YELLOW) {
                    @Override
                    public boolean checkPixels(int matchingPixelCount, int width, int height) {
                        return matchingPixelCount > 9000 && matchingPixelCount < 11000;
                    }
                }
        );

        assertTrue(releaseCounter.await(5, TimeUnit.SECONDS));
        for (int i = 0; i < setBufferCount; i++) {
            assertEquals(bufferIds[i], receivedCallbacks[i]);
            if (i > 0) {
                assertEquals(bufferIds[i], bufferIds[i - 1]);
            }
            SyncFence fence = receivedFences[i];
            assertNotNull(fence);
            if (fence.isValid()) {
                fence.awaitForever();
                assertTrue(fence.getSignalTime() > timeBeforeTest);
                assertTrue(fence.getSignalTime() < System.nanoTime());
                fence.close();
            }
        }
    }

    @Test
    public void testReleaseBufferCallbackNullBuffer() throws InterruptedException {
        CountDownLatch releaseCounter = new CountDownLatch(1);
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl surfaceControl = createFromWindow(holder);
                        HardwareBuffer buffer = getSolidBuffer(DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, Color.YELLOW);
                        SurfaceControl.Transaction t = new SurfaceControl.Transaction()
                                .setBuffer(surfaceControl, buffer, null,
                                        (SyncFence fence) -> releaseCounter.countDown());
                        t.setBuffer(surfaceControl, (HardwareBuffer) null).apply();
                        buffer.close();
                    }
                },
                new PixelChecker(Color.YELLOW) {
                    @Override
                    public boolean checkPixels(int matchingPixelCount, int width, int height) {
                        return matchingPixelCount > 9000 && matchingPixelCount < 11000;
                    }
                }
        );

        assertTrue(releaseCounter.await(5, TimeUnit.SECONDS));
    }

    @Test
    public void testReleaseBufferCallbackNullBufferThenNonNull() throws InterruptedException {
        CountDownLatch releaseCounter = new CountDownLatch(2);
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl surfaceControl = createFromWindow(holder);
                        HardwareBuffer buffer = getSolidBuffer(DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, Color.YELLOW);
                        SurfaceControl.Transaction t = new SurfaceControl.Transaction()
                                .setBuffer(surfaceControl, buffer, null,
                                        (SyncFence fence) -> releaseCounter.countDown());
                        t.setBuffer(surfaceControl, (HardwareBuffer) null);
                        t.setBuffer(surfaceControl, buffer, null,
                                (SyncFence fence) -> releaseCounter.countDown()).apply();
                        setSolidBuffer(surfaceControl,
                                DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT, Color.YELLOW);
                        buffer.close();
                    }
                },
                new PixelChecker(Color.YELLOW) {
                    @Override
                    public boolean checkPixels(int matchingPixelCount, int width, int height) {
                        return matchingPixelCount > 9000 && matchingPixelCount < 11000;
                    }
                }
        );

        assertTrue(releaseCounter.await(5, TimeUnit.SECONDS));
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
        final HardwareBuffer buffer = getSolidBuffer(DEFAULT_LAYOUT_WIDTH,
                DEFAULT_LAYOUT_HEIGHT, Color.WHITE);

        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl surfaceControl = createFromWindow(holder);
                        new SurfaceControl.Transaction()
                                .setBuffer(surfaceControl, buffer)
                                .setDataSpace(surfaceControl, extendedDataspace)
                                .setExtendedRangeBrightness(surfaceControl, 3.f, 3.f)
                                .apply();
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
                            SurfaceControl surfaceControl = createFromWindow(holder);
                            new SurfaceControl.Transaction()
                                    .setBuffer(surfaceControl, buffer)
                                    .setDataSpace(surfaceControl, extendedDataspace)
                                    .setExtendedRangeBrightness(surfaceControl, 3.f, 3.f)
                                    .apply();
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
        assumeTrue(com.android.graphics.hwui.flags.Flags.limitedHdr());
        Display display = mActivity.getDisplay();
        if (!display.isHdrSdrRatioAvailable()) {
            assertEquals(1.0f, display.getHdrSdrRatio(), 0.0001f);
        }
        final int dataspace = DataSpace.DATASPACE_BT2020_HLG;
        final HardwareBuffer buffer = getSolidBuffer(DEFAULT_LAYOUT_WIDTH,
                DEFAULT_LAYOUT_HEIGHT, Color.WHITE);

        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl surfaceControl = createFromWindow(holder);
                        SurfaceControl.Transaction txn = new SurfaceControl.Transaction()
                                .setBuffer(surfaceControl, buffer)
                                .setDataSpace(surfaceControl, dataspace);
                        assertThrows(IllegalArgumentException.class,
                                () -> txn.setDesiredHdrHeadroom(surfaceControl, 0.5f));
                        assertThrows(IllegalArgumentException.class,
                                () -> txn.setDesiredHdrHeadroom(surfaceControl, -1f));
                        assertThrows(IllegalArgumentException.class,
                                () -> txn.setDesiredHdrHeadroom(surfaceControl, Float.NaN));
                        txn.apply();
                    }
                },
                // Don't attempt to check pixels
                new PixelChecker(Color.WHITE) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return true;
                    }
                });

        if (!display.isHdrSdrRatioAvailable()) {
            return;
        }

        float headroom = getStableHdrSdrRatio(display);
        if (headroom < 1.02f) {
            return;
        }
        float targetHeadroom = 1.f + (headroom - 1.f) / 2;

        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl surfaceControl = createFromWindow(holder);
                        new SurfaceControl.Transaction()
                                .setBuffer(surfaceControl, buffer)
                                .setDataSpace(surfaceControl, dataspace)
                                .setDesiredHdrHeadroom(surfaceControl, targetHeadroom)
                                .apply();
                    }
                },
                // Don't attempt to check pixels
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
                        SurfaceControl surfaceControl = createFromWindow(holder);
                        new SurfaceControl.Transaction()
                                .setBuffer(surfaceControl, buffer)
                                .setDataSpace(surfaceControl, dataspace)
                                .setDesiredHdrHeadroom(surfaceControl, 0.f)
                                .apply();
                    }
                },
                // Don't attempt to check pixels
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

    private static final class DefaultDataSpaceParameters {
        private final int mPixelFormat;
        private final byte[] mColor;
        private final int mExpectedColor;

        DefaultDataSpaceParameters(int pixelFormat, byte[] color, int expectedColor) {
            mPixelFormat = pixelFormat;
            mColor = color;
            mExpectedColor = expectedColor;
        }

        int getPixelFormat() {
            return mPixelFormat;
        }

        byte[] getColor() {
            return mColor;
        }

        int getExpectedColor() {
            return mExpectedColor;
        }
    }

    private static Object[] defaultDataSpaceForRGBParameters() {
        byte[] red = {(byte) 255, 0, 0};
        byte[] green = {0, (byte) 255, 0};
        byte[] blue = {0, 0, (byte) 255};

        byte[][] colors = {red, green, blue};
        int[] expectedColors = {Color.RED, Color.GREEN, Color.BLUE};

        return IntStream.range(0, colors.length)
                .mapToObj(index -> new DefaultDataSpaceParameters(
                        PixelFormat.RGBA_8888, colors[index], expectedColors[index]))
                .toArray();
    }

    @Test
    @Parameters(method = "defaultDataSpaceForRGBParameters")
    public void testDefaultDataSpaceForRGBBufferIssRGB(DefaultDataSpaceParameters parameters) {
        byte[] color = parameters.getColor();
        ImageReader reader = new ImageReader.Builder(DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT)
                .setImageFormat(parameters.getPixelFormat())
                .setUsage(
                        HardwareBuffer.USAGE_COMPOSER_OVERLAY
                                | HardwareBuffer.USAGE_CPU_WRITE_OFTEN
                                | HardwareBuffer.USAGE_CPU_READ_OFTEN
                                | HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE)
                .build();
        ImageWriter writer = new ImageWriter.Builder(reader.getSurface()).build();
        Image image = writer.dequeueInputImage();

        Image.Plane[] planes = image.getPlanes();
        assertEquals(1, planes.length);
        Image.Plane plane = planes[0];
        ByteBuffer buffer = plane.getBuffer();
        for (int row = 0; row < image.getHeight(); row++) {
            int rowOffset = row * plane.getRowStride();
            for (int col = 0; col < image.getWidth(); col++) {
                buffer.put(rowOffset + col * plane.getPixelStride(), color[0]);
                buffer.put(rowOffset + col * plane.getPixelStride() + 1, color[1]);
                buffer.put(rowOffset + col * plane.getPixelStride() + 2, color[2]);
                buffer.put(rowOffset + col * plane.getPixelStride() + 3, (byte) 255);
            }
        }
        image.setDataSpace(DataSpace.DATASPACE_UNKNOWN);
        writer.queueInputImage(image);

        try (Image acquiredImage = reader.acquireLatestImage()) {
            HardwareBuffer queuedBuffer = acquiredImage.getHardwareBuffer();
            verifyTest(
                    new BasicSurfaceHolderCallback() {
                        @Override
                        public void surfaceCreated(SurfaceHolder holder) {
                            SurfaceControl surfaceControl = createFromWindow(holder);
                            new SurfaceControl.Transaction()
                                    .setBuffer(surfaceControl, queuedBuffer)
                                    .setDataSpace(surfaceControl, DataSpace.DATASPACE_UNKNOWN)
                                    .apply();
                            queuedBuffer.close();
                        }
                    },
                    new PixelChecker(parameters.getExpectedColor()) {
                        @Override
                        public boolean checkPixels(int matchingPixelCount, int width, int height) {
                            return matchingPixelCount > 9000 && matchingPixelCount < 11000;
                        }
                    }
            );
        }
    }

    private static Object[] defaultDataSpaceForYUVParameters() {
        // Computed from BT. 709-6, but quantized to full range 8-bit rather than limited range
        // TODO: add more colors to disambiguate from BT. 601, but this is okay for now
        // since we should just make sure the colors aren't obviously wrong.
        byte[] blue = {18, (byte) 255, 116};
        byte[][] colors = {blue};
        int[] expectedColors = {Color.BLUE};

        // TODO(b/277348717): Check additional YUV formats. At the very least, YUV420, although
        // on some devices YUV420 is not supported in ImageReader even when
        // HardwareBuffer#isSupported returns true.
        return Stream.of(ImageFormat.YV12)
                .flatMap(format ->
                    IntStream.range(0, colors.length)
                            .mapToObj(index -> new DefaultDataSpaceParameters(
                                    format, colors[index], expectedColors[index]))
                )
                .toArray();
    }

    @Test
    @Parameters(method = "defaultDataSpaceForYUVParameters")
    public void testDefaultDataSpaceForYUVBufferIssRGB(DefaultDataSpaceParameters parameters) {
        // This is just blue
        int format = parameters.getPixelFormat();
        long usage = HardwareBuffer.USAGE_COMPOSER_OVERLAY
                | HardwareBuffer.USAGE_CPU_WRITE_OFTEN
                | HardwareBuffer.USAGE_CPU_READ_OFTEN
                | HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE;

        // YV12 should always be supported with this usage bit combination.
        // We also want to test with as many YUV formats as feasible.
        assumeTrue(format == ImageFormat.YV12
                || HardwareBuffer.isSupported(16, 16, format, 1, usage));
        byte[] yuv = parameters.getColor();
        ImageReader reader = new ImageReader.Builder(DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT)
                .setImageFormat(format)
                .setUsage(usage)
                .build();
        ImageWriter writer = new ImageWriter.Builder(reader.getSurface()).build();
        Image image = writer.dequeueInputImage();

        Image.Plane[] planes = image.getPlanes();
        assertEquals(3, planes.length);
        for (int i = 0; i < planes.length; i++) {
            Image.Plane plane = planes[i];
            ByteBuffer buffer = plane.getBuffer();
            int planeHeight = i == 0 ? image.getHeight() : image.getHeight() / 2;
            int planeWidth = i == 0 ? image.getWidth() : image.getWidth() / 2;
            for (int row = 0; row < planeHeight; row++) {
                int rowOffset = row * plane.getRowStride();
                for (int col = 0; col < planeWidth; col++) {
                    buffer.put(rowOffset + col * plane.getPixelStride(), yuv[i]);
                }
            }
        }

        image.setDataSpace(DataSpace.DATASPACE_UNKNOWN);
        writer.queueInputImage(image);

        try (Image acquiredImage = reader.acquireLatestImage()) {
            HardwareBuffer queuedBuffer = acquiredImage.getHardwareBuffer();
            verifyTest(
                    new BasicSurfaceHolderCallback() {
                        @Override
                        public void surfaceCreated(SurfaceHolder holder) {
                            SurfaceControl surfaceControl = createFromWindow(holder);
                            new SurfaceControl.Transaction()
                                    .setBuffer(surfaceControl, queuedBuffer)
                                    .setDataSpace(surfaceControl, DataSpace.DATASPACE_UNKNOWN)
                                    .apply();
                            queuedBuffer.close();
                        }
                    },
                    new PixelChecker(parameters.getExpectedColor()) {
                        @Override
                        public boolean checkPixels(int matchingPixelCount, int width, int height) {
                            return matchingPixelCount > 9000 && matchingPixelCount < 11000;
                        }
                    }
            );
        }
    }


    @Test
    public void testLifecycle() {
        final SurfaceControl.Builder b = new SurfaceControl.Builder();
        final SurfaceControl sc = b.setName("CTS").build();

        assertTrue("Failed to build SurfaceControl", sc != null);
        assertTrue(sc.isValid());
        sc.release();
        assertFalse(sc.isValid());
    }

    @Test
    public void testSameSurface() {
        final SurfaceControl.Builder b = new SurfaceControl.Builder();
        final SurfaceControl sc = b.setName("CTS").build();
        SurfaceControl copy = new SurfaceControl(sc, "SurfaceControlTest.testSameSurface");
        assertTrue(copy.isSameSurface(sc));
        sc.release();
        copy.release();
    }

    private SurfaceControl buildDefaultSurface(SurfaceControl parent) {
        return new SurfaceControl.Builder()
            .setBufferSize(DEFAULT_SURFACE_SIZE, DEFAULT_SURFACE_SIZE)
            .setName("CTS surface")
            .setParent(parent)
            .build();

    }

    void fillWithColor(SurfaceControl sc, int color) {
        Surface s = new Surface(sc);

        Canvas c = s.lockCanvas(null);
        c.drawColor(color);
        s.unlockCanvasAndPost(c);
    }

    private SurfaceControl buildDefaultSurface(SurfaceControl parent, int color) {
        final SurfaceControl sc = buildDefaultSurface(parent);
        fillWithColor(sc, color);
        return sc;
    }

    private SurfaceControl buildDefaultRedSurface(SurfaceControl parent) {
        return buildDefaultSurface(parent, Color.RED);
    }
    private SurfaceControl buildSmallRedSurface(SurfaceControl parent) {
        SurfaceControl surfaceControl = new SurfaceControl.Builder()
                .setBufferSize(DEFAULT_SURFACE_SIZE / 2, DEFAULT_SURFACE_SIZE / 2)
                .setName("CTS surface")
                .setParent(parent)
                .build();
        fillWithColor(surfaceControl, Color.RED);
        return surfaceControl;
    }

    /**
     * Verify that showing a 100x100 surface filled with RED produces roughly 10,000 red pixels.
     */
    @Test
    public void testShow() throws Throwable {
        verifyTest(
                new SurfaceHolderCallback() {
                    @Override
                    public void addChildren(SurfaceControl parent) {
                        final SurfaceControl sc = buildDefaultRedSurface(parent);

                        makeTransactionWithListener().setVisibility(sc, true).apply();

                        sc.release();
                    }
                },
                new RectChecker(DEFAULT_RECT, Color.RED), 1);
    }

    /**
     * The same setup as testShow, however we hide the surface and verify that we don't see Red.
     */
    @Test
    public void testHide() throws Throwable {
        verifyTest(
                new SurfaceHolderCallback () {
                    @Override
                    public void addChildren(SurfaceControl parent) {
                        final SurfaceControl sc = buildDefaultRedSurface(parent);

                        makeTransactionWithListener().setVisibility(sc, false).apply();

                        sc.release();
                    }
                },
                new RectChecker(DEFAULT_RECT, Color.BLACK), 1);
    }

    /**
     * Like testHide but we reparent the surface off-screen instead.
     */
    @Test
    public void testReparentOff() throws Throwable {
        final SurfaceControl sc = buildDefaultRedSurface(null);
        verifyTest(
                new SurfaceHolderCallback () {
                    @Override
                    public void addChildren(SurfaceControl parent) {
                        makeTransactionWithListener().reparent(sc, parent).apply();
                        makeTransactionWithListener().reparent(sc, null).apply();
                    }
                },
                new RectChecker(DEFAULT_RECT, Color.BLACK), 2);
      // Since the SurfaceControl is parented off-screen, if we release our reference
      // it may completely die. If this occurs while the render thread is still rendering
      // the RED background we could trigger a crash. For this test defer destroying the
      // Surface until we have collected our test results.
      if (sc != null) {
        sc.release();
      }
    }

    /**
     * Here we use the same red-surface set up but construct it off-screen and then re-parent it.
     */
    @Test
    public void testReparentOn() throws Throwable {
        verifyTest(
                new SurfaceHolderCallback () {
                    @Override
                    public void addChildren(SurfaceControl parent) {
                        final SurfaceControl sc = buildDefaultRedSurface(null);

                        makeTransactionWithListener().setVisibility(sc, true)
                            .reparent(sc, parent)
                            .apply();

                        sc.release();
                    }
                },
                new RectChecker(DEFAULT_RECT, Color.RED), 1);
    }

    /**
     * Test that a surface with Layer "2" appears over a surface with Layer "1".
     */
    @Test
    public void testSetLayer() throws Throwable {
        verifyTest(
                new SurfaceHolderCallback () {
                    @Override
                    public void addChildren(SurfaceControl parent) {
                        final SurfaceControl sc = buildDefaultRedSurface(parent);
                        final SurfaceControl sc2 = buildDefaultSurface(parent, Color.GREEN);

                        makeTransactionWithListener().setVisibility(sc, true)
                            .setVisibility(sc2, true)
                            .setLayer(sc, 1)
                            .setLayer(sc2, 2)
                            .apply();

                        sc.release();
                    }
                },
                new RectChecker(DEFAULT_RECT, Color.GREEN), 1);
    }

    /**
     * Try setting the position of a surface with the top-left corner off-screen.
     */
    @Test
    public void testSetGeometry_dstBoundsOffScreen() throws Throwable {
        verifyTest(
                new SurfaceHolderCallback () {
                    @Override
                    public void addChildren(SurfaceControl parent) {
                        final SurfaceControl sc = buildDefaultRedSurface(parent);
                        makeTransactionWithListener().setVisibility(sc, true)
                            .setGeometry(sc, null, new Rect(-50, -50, 50, 50), Surface.ROTATION_0)
                            .apply();
                        sc.release();
                    }
                },

                // The rect should be offset by -50 pixels
                new RectChecker(DEFAULT_RECT) {
                    final PixelColor mRed = new PixelColor(Color.RED);
                    final PixelColor mBlack = new PixelColor(Color.BLACK);
                    @Override
                    public PixelColor getExpectedColor(int x, int y) {
                        if (x < 50 && y < 50) {
                            return mRed;
                        } else {
                            return mBlack;
                        }
                    }
                }, 1);
    }

    /**
     * Try setting the position of a surface with the top-left corner on-screen.
     */
    @Test
    public void testSetGeometry_dstBoundsOnScreen() throws Throwable {
        verifyTest(
                new SurfaceHolderCallback () {
                    @Override
                    public void addChildren(SurfaceControl parent) {
                        final SurfaceControl sc = buildDefaultRedSurface(parent);
                        makeTransactionWithListener().setVisibility(sc, true)
                            .setGeometry(sc, null, new Rect(50, 50, 150, 150), Surface.ROTATION_0)
                            .apply();

                        sc.release();
                    }
                },

                // The rect should be offset by 50 pixels
                new RectChecker(DEFAULT_RECT) {
                    final PixelColor mRed = new PixelColor(Color.RED);
                    final PixelColor mBlack = new PixelColor(Color.BLACK);
                    @Override
                    public PixelColor getExpectedColor(int x, int y) {
                        if (x >= 50 && y >= 50) {
                            return mRed;
                        } else {
                            return mBlack;
                        }
                    }
                }, 1);
    }

    /**
     * Try scaling a surface
     */
    @Test
    public void testSetGeometry_dstBoundsScaled() throws Throwable {
        verifyTest(
                new SurfaceHolderCallback () {
                    @Override
                    public void addChildren(SurfaceControl parent) {
                        final SurfaceControl sc = buildSmallRedSurface(parent);
                        makeTransactionWithListener().setVisibility(sc, true)
                            .setGeometry(sc, new Rect(0, 0, DEFAULT_SURFACE_SIZE / 2, DEFAULT_SURFACE_SIZE / 2),
                                    new Rect(0, 0, DEFAULT_SURFACE_SIZE , DEFAULT_SURFACE_SIZE),
                                    Surface.ROTATION_0)
                            .apply();
                        sc.release();
                    }
                },

                new RectChecker(DEFAULT_RECT, Color.RED), 1);
    }

    private SurfaceControl.Transaction makeTransactionWithListener() {
        return new SurfaceControl.Transaction().addTransactionCommittedListener(
                new Executor() {
                    @Override
                    public void execute(Runnable command) {
                        command.run();
                    }
                }, new SurfaceControl.TransactionCommittedListener() {
                    @Override
                    public void onTransactionCommitted() {
                        mActivity.transactionCommitted();
                    }
                }
        );
    }
}
