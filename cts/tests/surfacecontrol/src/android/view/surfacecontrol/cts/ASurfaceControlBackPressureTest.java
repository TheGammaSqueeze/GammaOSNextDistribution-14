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

package android.view.surfacecontrol.cts;

import static android.view.cts.util.ASurfaceControlTestUtils.TransactionCompleteListener;
import static android.view.cts.util.ASurfaceControlTestUtils.applyAndDeleteSurfaceTransaction;
import static android.view.cts.util.ASurfaceControlTestUtils.nSurfaceControl_createFromWindow;
import static android.view.cts.util.ASurfaceControlTestUtils.nSurfaceControl_release;
import static android.view.cts.util.ASurfaceControlTestUtils.nSurfaceTransaction_create;
import static android.view.cts.util.ASurfaceControlTestUtils.nSurfaceTransaction_releaseBuffer;
import static android.view.cts.util.ASurfaceControlTestUtils.nSurfaceTransaction_setBuffer;
import static android.view.cts.util.ASurfaceControlTestUtils.nSurfaceTransaction_setEnableBackPressure;
import static android.view.cts.util.ASurfaceControlTestUtils.nSurfaceTransaction_setOnCompleteCallback;
import static android.view.cts.util.ASurfaceControlTestUtils.nSurfaceTransaction_setSolidBuffer;
import static android.view.cts.util.ASurfaceControlTestUtils.reparent;

import static org.junit.Assert.assertTrue;

import android.graphics.Canvas;
import android.graphics.Color;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.cts.surfacevalidator.CapturedActivity;
import android.view.cts.surfacevalidator.MultiFramePixelChecker;
import android.view.cts.surfacevalidator.SurfaceControlTestCase;

import androidx.test.filters.LargeTest;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.UiObjectNotFoundException;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class ASurfaceControlBackPressureTest {

    private static class SyncTransactionCompleteListener implements TransactionCompleteListener {
        private final CountDownLatch mCountDownLatch = new CountDownLatch(1);

        @Override
        public void onTransactionComplete(long latchTime, long presentTime) {
            mCountDownLatch.countDown();
        }

        public void waitForTransactionComplete() {
            try {
                mCountDownLatch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private static final int DEFAULT_LAYOUT_WIDTH = 50;
    private static final int DEFAULT_LAYOUT_HEIGHT = 50;

    @Rule
    public ActivityTestRule<CapturedActivity> mActivityRule =
            new ActivityTestRule<>(CapturedActivity.class);

    private CapturedActivity mActivity;

    @Rule
    public TestName mName = new TestName();

    @Before
    public void setup() {
        mActivity = mActivityRule.getActivity();
    }

    @After
    public void tearDown() throws UiObjectNotFoundException {
        mActivity.restoreSettings();
    }

    public abstract static class BasicSurfaceHolderCallback implements SurfaceHolder.Callback {
        private final Set<Long> mSurfaceControls = new HashSet<>();
        private final Set<Long> mBuffers = new HashSet<>();
        private final Set<BufferCycler> mBufferCyclers = new HashSet<>();

        // Helper class to submit buffers as fast as possible. The thread submits a buffer,
        // waits for the transaction complete callback, and then submits the next buffer.
        class BufferCycler extends Thread {
            private final long mSurfaceControl;
            private final long[] mBuffers;
            private volatile boolean mStop = false;
            private int mFrameNumber = 0;

            BufferCycler(long surfaceControl, long[] buffers) {
                mSurfaceControl = surfaceControl;
                mBuffers = buffers;
            }

            private long getNextBuffer() {
                return mBuffers[mFrameNumber++ % mBuffers.length];
            }

            @Override
            public void run() {
                while (!mStop) {
                    SyncTransactionCompleteListener listener =
                            new SyncTransactionCompleteListener();
                    // Send all buffers in batches so we can stuff the SurfaceFlinger transaction
                    // queue.
                    for (int i = 0; i < mBuffers.length; i++) {
                        long surfaceTransaction = createSurfaceTransaction();
                        nSurfaceTransaction_setBuffer(mSurfaceControl, surfaceTransaction,
                                getNextBuffer());
                        if (i == 0) {
                            nSurfaceTransaction_setOnCompleteCallback(surfaceTransaction,
                                    false /* waitForFence */, listener);
                        }
                        applyAndDeleteSurfaceTransaction(surfaceTransaction);
                    }

                    // Wait for one of transactions to be applied before sending more transactions.
                    listener.waitForTransactionComplete();
                }
            }

            void end() {
                mStop = true;
            }
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            Canvas canvas = holder.lockCanvas();
            canvas.drawColor(Color.YELLOW);
            holder.unlockCanvasAndPost(canvas);
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            for (BasicSurfaceHolderCallback.BufferCycler cycler : mBufferCyclers) {
                cycler.end();
                try {
                    cycler.join();
                } catch (InterruptedException e) {
                }
            }
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

        public long createSurfaceTransaction() {
            long surfaceTransaction = nSurfaceTransaction_create();
            assertTrue("failed to create surface transaction", surfaceTransaction != 0);
            return surfaceTransaction;
        }

        public long createFromWindow(Surface surface) {
            long surfaceControl = nSurfaceControl_createFromWindow(surface);
            assertTrue("failed to create surface control", surfaceControl != 0);

            mSurfaceControls.add(surfaceControl);
            return surfaceControl;
        }

        public void setEnableBackPressure(long surfaceControl, boolean enableBackPressure) {
            long surfaceTransaction = createSurfaceTransaction();
            nSurfaceTransaction_setEnableBackPressure(surfaceControl, surfaceTransaction,
                    enableBackPressure);
            applyAndDeleteSurfaceTransaction(surfaceTransaction);
        }

        public long setSolidBuffer(long surfaceControl, int width, int height, int color) {
            long surfaceTransaction = createSurfaceTransaction();
            long buffer = nSurfaceTransaction_setSolidBuffer(
                    surfaceControl, surfaceTransaction, width, height, color);
            assertTrue("failed to set buffer", buffer != 0);
            mBuffers.add(buffer);
            applyAndDeleteSurfaceTransaction(surfaceTransaction);
            return buffer;
        }

        public void addBufferCycler(long surfaceControl, long[] buffers) {
            BasicSurfaceHolderCallback.BufferCycler cycler =
                    new BasicSurfaceHolderCallback.BufferCycler(surfaceControl, buffers);
            cycler.start();
            mBufferCyclers.add(cycler);
        }
    }

    @Test
    public void testSurfaceTransaction_setEnableBackPressure() throws Throwable {
        int[] colors = new int[]{Color.RED, Color.GREEN, Color.BLUE};
        BasicSurfaceHolderCallback callback = new BasicSurfaceHolderCallback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                long surfaceControl = createFromWindow(holder.getSurface());
                setEnableBackPressure(surfaceControl, true);
                long[] buffers = new long[6];
                for (int i = 0; i < buffers.length; i++) {
                    buffers[i] = setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH,
                            DEFAULT_LAYOUT_HEIGHT, colors[i % colors.length]);
                }
                addBufferCycler(surfaceControl, buffers);
            }
        };

        MultiFramePixelChecker pixelChecker = new MultiFramePixelChecker(colors) {
            @Override
            public boolean checkPixels(int pixelCount, int width, int height) {
                return pixelCount > 2000 && pixelCount < 3000;
            }
        };

        mActivity.verifyTest(new SurfaceControlTestCase(callback, pixelChecker,
                        DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                        DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT),
                mName);
    }

    @Test
    public void testSurfaceTransaction_defaultBackPressureDisabled() throws Throwable {
        int[] colors = new int[]{Color.RED, Color.GREEN, Color.BLUE};
        BasicSurfaceHolderCallback callback = new BasicSurfaceHolderCallback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                long surfaceControl = createFromWindow(holder.getSurface());
                // back pressure is disabled by default
                long[] buffers = new long[6];
                for (int i = 0; i < buffers.length; i++) {
                    buffers[i] = setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH,
                            DEFAULT_LAYOUT_HEIGHT, colors[i % colors.length]);
                }
                addBufferCycler(surfaceControl, buffers);
            }
        };

        MultiFramePixelChecker pixelChecker = new MultiFramePixelChecker(colors) {
            @Override
            public boolean checkPixels(int pixelCount, int width, int height) {
                return pixelCount > 2000 && pixelCount < 3000;
            }
        };

        CapturedActivity.TestResult result = mActivity.runTest(new SurfaceControlTestCase(callback,
                pixelChecker,
                DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT));

        assertTrue(result.passFrames > 0);

        // With back pressure disabled, the default config, we expect at least one or more frames to
        // fail since we expect at least one buffer to be dropped.
        assertTrue(result.failFrames > 0);
    }
}
