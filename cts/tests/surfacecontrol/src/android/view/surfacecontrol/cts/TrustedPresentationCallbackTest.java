/*
 * Copyright (C) 2023 The Android Open Source Project
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
import static android.server.wm.BuildUtils.HW_TIMEOUT_MULTIPLIER;
import static android.server.wm.CtsWindowInfoUtils.assertAndDumpWindowState;
import static android.view.cts.util.ASurfaceControlTestUtils.getSolidBuffer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Rect;
import android.hardware.HardwareBuffer;
import android.os.Bundle;
import android.platform.test.annotations.Presubmit;
import android.util.ArraySet;
import android.util.Size;
import android.view.Gravity;
import android.view.SurfaceControl;
import android.view.SurfaceControl.TrustedPresentationThresholds;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.ext.junit.rules.ActivityScenarioRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@Presubmit
public class TrustedPresentationCallbackTest {
    static {
        System.loadLibrary("ctssurfacecontrol_jni");
    }

    private static final String TAG = "TrustedPresentationCallbackTest";
    private static final int STABILITY_REQUIREMENT_MS = 500;
    private static final long WAIT_TIME_MS = HW_TIMEOUT_MULTIPLIER * 2000L;

    private static final float FRACTION_VISIBLE = 0.1f;

    private static final float FRACTION_SMALLER_THAN_VISIBLE = FRACTION_VISIBLE - .01f;

    @Rule
    public TestName mName = new TestName();

    @Rule
    public ActivityScenarioRule<TestActivity> mActivityRule =
            createFullscreenActivityScenarioRule(TestActivity.class);

    private TestActivity mActivity;
    private final Object mResultsLock = new Object();
    @GuardedBy("mResultsLock")
    private boolean mResult;
    @GuardedBy("mResultsLock")
    private boolean mReceivedResults;

    @Before
    public void setup() {
        mActivityRule.getScenario().onActivity(activity -> mActivity = activity);
        mResult = false;
        mReceivedResults = false;
    }

    private void registerTrustedPresentationCallback(SurfaceControl sc) {
        TrustedPresentationThresholds thresholds = new TrustedPresentationThresholds(
                1 /* minAlpha */, FRACTION_VISIBLE, STABILITY_REQUIREMENT_MS);
        SurfaceControl.Transaction t = new SurfaceControl.Transaction();
        t.setTrustedPresentationCallback(sc, thresholds, mActivity.mExecutor,
                inTrustedPresentationState -> {
                    synchronized (mResultsLock) {
                        mResult = inTrustedPresentationState;
                        mReceivedResults = true;
                        mResultsLock.notify();
                    }
                }).apply();
    }

    private SurfaceControl createChildSc(SurfaceControl parent) {
        SurfaceControl surfaceControl = new SurfaceControl.Builder()
                .setParent(parent)
                .setName("ChildSc")
                .setHidden(false)
                .build();

        return surfaceControl;
    }

    private void setBuffer(SurfaceControl surfaceControl, int width, int height) {
        HardwareBuffer buffer = getSolidBuffer(width, height, Color.RED);
        assertNotNull("failed to make solid buffer", buffer);
        new SurfaceControl.Transaction()
                .setBuffer(surfaceControl, buffer)
                .apply();

        mActivity.mSurfaceControls.add(surfaceControl);
        mActivity.mBuffers.add(buffer);
    }

    @Test
    public void testTrustedPresentationListener() throws InterruptedException {
        SurfaceControl rootSc = mActivity.getSurfaceControl();
        SurfaceControl sc = createChildSc(rootSc);
        registerTrustedPresentationCallback(sc);

        synchronized (mResultsLock) {
            setBuffer(sc, mActivity.mSvSize.getWidth(), mActivity.mSvSize.getHeight());
            assertResults(true);
        }

        synchronized (mResultsLock) {
            mReceivedResults = false;
            new SurfaceControl.Transaction().setVisibility(sc, false).apply();
            assertResults(false);
        }
    }


    @Test
    public void testTrustedPresentationListener_parentVisibilityChanges()
            throws InterruptedException {
        SurfaceControl rootSc = mActivity.getSurfaceControl();
        SurfaceControl sc = createChildSc(rootSc);
        registerTrustedPresentationCallback(sc);

        synchronized (mResultsLock) {
            setBuffer(sc, mActivity.mSvSize.getWidth(), mActivity.mSvSize.getHeight());
            assertResults(true);
        }

        synchronized (mResultsLock) {
            mReceivedResults = false;
            CountDownLatch countDownLatch = new CountDownLatch(1);
            mActivity.runOnUiThread(() -> {
                mActivity.mSurfaceView.setVisibility(View.INVISIBLE);
                countDownLatch.countDown();
            });
            countDownLatch.await(WAIT_TIME_MS, TimeUnit.MILLISECONDS);
            assertResults(false);
        }
    }

    @Test
    public void testTrustedPresentationListener_alphaBelow() throws InterruptedException {
        SurfaceControl rootSc = mActivity.getSurfaceControl();
        SurfaceControl sc = createChildSc(rootSc);
        registerTrustedPresentationCallback(sc);

        synchronized (mResultsLock) {
            setBuffer(sc, mActivity.mSvSize.getWidth(), mActivity.mSvSize.getHeight());
            assertResults(true);
        }

        synchronized (mResultsLock) {
            mReceivedResults = false;
            new SurfaceControl.Transaction().setAlpha(sc, .5f).apply();
            assertResults(false);
        }
    }

    @Test
    public void testTrustedPresentationListener_minFractionDueToCrop() throws InterruptedException {
        SurfaceControl rootSc = mActivity.getSurfaceControl();
        SurfaceControl sc = createChildSc(rootSc);
        registerTrustedPresentationCallback(sc);

        synchronized (mResultsLock) {
            setBuffer(sc, mActivity.mSvSize.getWidth(), mActivity.mSvSize.getHeight());
            assertResults(true);
        }

        synchronized (mResultsLock) {
            mReceivedResults = false;
            // threshold is 10% so make sure to crop even smaller than that
            new SurfaceControl.Transaction().setCrop(sc,
                    new Rect(0, 0,
                            (int) (mActivity.mSvSize.getWidth() * FRACTION_SMALLER_THAN_VISIBLE),
                            mActivity.mSvSize.getHeight())).apply();
            assertResults(false);
        }
    }

    @Test
    public void testTrustedPresentationListener_minFractionDueToCovered()
            throws InterruptedException {
        SurfaceControl rootSc = mActivity.getSurfaceControl();
        SurfaceControl sc1 = createChildSc(rootSc);
        SurfaceControl sc2 = createChildSc(rootSc);
        registerTrustedPresentationCallback(sc1);

        synchronized (mResultsLock) {
            setBuffer(sc1, mActivity.mSvSize.getWidth(), mActivity.mSvSize.getHeight());
            assertResults(true);
        }

        // Make Second SC visible
        synchronized (mResultsLock) {
            mReceivedResults = false;
            // Cover slightly more than what the threshold is expecting to ensure the listener
            // reports it's no longer at the threshold.
            setBuffer(sc2,
                    (int) (mActivity.mSvSize.getWidth() * (1 - FRACTION_SMALLER_THAN_VISIBLE)),
                    mActivity.mSvSize.getHeight());
            assertResults(false);
        }
    }

    @Test
    public void testTrustedPresentationListener_enteredExitEntered() throws InterruptedException {
        SurfaceControl rootSc = mActivity.getSurfaceControl();
        SurfaceControl sc = createChildSc(rootSc);
        registerTrustedPresentationCallback(sc);

        synchronized (mResultsLock) {
            setBuffer(sc, mActivity.mSvSize.getWidth(), mActivity.mSvSize.getHeight());
            assertResults(true);
        }

        synchronized (mResultsLock) {
            mReceivedResults = false;
            new SurfaceControl.Transaction().setCrop(sc,
                    new Rect(0, 0,
                            (int) (mActivity.mSvSize.getWidth() * FRACTION_SMALLER_THAN_VISIBLE),
                            mActivity.mSvSize.getHeight())).apply();
            assertResults(false);
        }

        synchronized (mResultsLock) {
            mReceivedResults = false;
            new SurfaceControl.Transaction().setCrop(sc, null).apply();
            assertResults(true);
        }
    }

    @Test
    public void testTrustedPresentationListener_invalidThreshold() {
        boolean threwException = false;
        try {
            new TrustedPresentationThresholds(-1 /* minAlpha */, 1 /* minFractionRendered */,
                    1000 /* stabilityRequirementMs */);
        } catch (IllegalArgumentException e) {
            threwException = true;
        }
        assertTrue(threwException);

        threwException = false;
        try {
            new TrustedPresentationThresholds(1 /* minAlpha */, -1 /* minFractionRendered */,
                    1000 /* stabilityRequirementMs */);
        } catch (IllegalArgumentException e) {
            threwException = true;
        }
        assertTrue(threwException);

        threwException = false;
        try {
            new TrustedPresentationThresholds(1 /* minAlpha */, 1 /* minFractionRendered */,
                    0 /* stabilityRequirementMs */);
        } catch (IllegalArgumentException e) {
            threwException = true;
        }

        assertTrue(threwException);
    }

    @Test
    public void testTrustedPresentationListener_clearCallback()
            throws InterruptedException {
        SurfaceControl rootSc = mActivity.getSurfaceControl();
        SurfaceControl sc = createChildSc(rootSc);
        registerTrustedPresentationCallback(sc);

        synchronized (mResultsLock) {
            setBuffer(sc, mActivity.mSvSize.getWidth(), mActivity.mSvSize.getHeight());
            assertResults(true);
        }

        synchronized (mResultsLock) {
            mReceivedResults = false;
            new SurfaceControl.Transaction().clearTrustedPresentationCallback(sc)
                    .setVisibility(sc, false).apply();
            mResultsLock.wait(WAIT_TIME_MS);
            // Ensure we waited the full time and never received a notify on the result from the
            // callback.
            assertFalse("Should never have received a callback", mReceivedResults);
            // results shouldn't have changed.
            assertTrue(mResult);
        }
    }

    @Test
    public void testTrustedPresentationListener_multipleSetCallbacks()
            throws InterruptedException {
        SurfaceControl rootSc = mActivity.getSurfaceControl();
        SurfaceControl sc = createChildSc(rootSc);

        TrustedPresentationThresholds thresholds = new TrustedPresentationThresholds(
                1 /* minAlpha */, FRACTION_VISIBLE, STABILITY_REQUIREMENT_MS);

        CountDownLatch latch1 = new CountDownLatch(1);
        SurfaceControl.Transaction t = new SurfaceControl.Transaction();
        t.setTrustedPresentationCallback(sc, thresholds, mActivity.mExecutor,
                inTrustedPresentationState -> latch1.countDown()).apply();

        CountDownLatch latch2 = new CountDownLatch(1);
        t.setTrustedPresentationCallback(sc, thresholds, mActivity.mExecutor,
                inTrustedPresentationState -> {
                    latch2.countDown();
                    mResult = inTrustedPresentationState;
                }).apply();

        setBuffer(sc, mActivity.mSvSize.getWidth(), mActivity.mSvSize.getHeight());

        // The first callback should never get callback for first presentation callback since we
        // overwrote it.
        assertFalse(latch1.await(WAIT_TIME_MS, TimeUnit.MILLISECONDS));
        assertTrue(latch2.await(WAIT_TIME_MS, TimeUnit.MILLISECONDS));

        assertTrue(mResult);
    }

    @Test
    public void testSetTrustedPresentationListenerAfterThreshold() throws InterruptedException {
        SurfaceControl rootSc = mActivity.getSurfaceControl();
        SurfaceControl sc = createChildSc(rootSc);
        registerTrustedPresentationCallback(sc);

        // Ensure received tpc callback so we know it's ready.
        synchronized (mResultsLock) {
            setBuffer(sc, mActivity.mSvSize.getWidth(), mActivity.mSvSize.getHeight());
            assertResults(true);
        }

        // Register a new trusted presentation listener to make sure we get a callback if we
        // registered after already in the trusted presented state. We'll need to wait the full
        // time again.
        synchronized (mResultsLock) {
            mReceivedResults = false;
            mResult = false;
            registerTrustedPresentationCallback(sc);
            long startTime = System.currentTimeMillis();
            assertResults(true);
            assertTrue(System.currentTimeMillis() - startTime >= STABILITY_REQUIREMENT_MS);
        }
    }

    @Test
    public void testSetTrustedPresentationListenerAfterClearing() throws InterruptedException {
        SurfaceControl rootSc = mActivity.getSurfaceControl();
        SurfaceControl sc = createChildSc(rootSc);
        registerTrustedPresentationCallback(sc);

        // Ensure received tpc callback so we know it's ready.
        synchronized (mResultsLock) {
            setBuffer(sc, mActivity.mSvSize.getWidth(), mActivity.mSvSize.getHeight());
            assertResults(true);
        }


        // Register a new trusted presentation listener to make sure we get a callback if we
        // registered after already in the trusted presented state. We'll need to wait the full
        // time again.
        synchronized (mResultsLock) {
            mReceivedResults = false;
            mResult = false;
            // Use a long stability requirement so we don't accidentally trigger it too early.
            TrustedPresentationThresholds thresholds = new TrustedPresentationThresholds(
                    1 /* minAlpha */, FRACTION_VISIBLE, HW_TIMEOUT_MULTIPLIER * 20000);
            SurfaceControl.Transaction t = new SurfaceControl.Transaction();
            t.setTrustedPresentationCallback(sc, thresholds, mActivity.mExecutor,
                    inTrustedPresentationState -> {
                        synchronized (mResultsLock) {
                            mResult = inTrustedPresentationState;
                            mReceivedResults = true;
                            mResultsLock.notify();
                        }
                    }).apply();
        }


        new SurfaceControl.Transaction().clearTrustedPresentationCallback(sc).apply();
        synchronized (mResultsLock) {
            assertFalse(mResult);
            assertFalse(mReceivedResults);
            registerTrustedPresentationCallback(sc);
            long startTime = System.currentTimeMillis();
            assertResults(true);
            assertTrue(System.currentTimeMillis() - startTime >= STABILITY_REQUIREMENT_MS);
        }
    }

    @GuardedBy("mResultsLock")
    private void assertResults(boolean result) throws InterruptedException {
        mResultsLock.wait(WAIT_TIME_MS);

        assertAndDumpWindowState(TAG, "Timed out waiting for results",
                mReceivedResults);
        assertEquals(result, mResult);
    }

    public static class TestActivity extends Activity implements SurfaceHolder.Callback {
        private final Executor mExecutor = Runnable::run;

        private final CountDownLatch mCountDownLatch = new CountDownLatch(3);

        private SurfaceView mSurfaceView;

        private final ArraySet<SurfaceControl> mSurfaceControls = new ArraySet<>();
        private final ArraySet<HardwareBuffer> mBuffers = new ArraySet<>();

        private Size mSvSize;

        @Override
        protected void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            WindowManager wm = getSystemService(WindowManager.class);
            Rect bounds = wm.getCurrentWindowMetrics().getBounds();
            // Make sure the content rendering is smaller than the display and not getting cut off
            // by the edges.
            mSvSize = new Size(bounds.width() / 2, bounds.height() / 2);

            FrameLayout content = new FrameLayout(this);
            mSurfaceView = new SurfaceView(this);
            content.addView(mSurfaceView,
                    new FrameLayout.LayoutParams(mSvSize.getWidth(), mSvSize.getHeight(),
                            Gravity.CENTER));
            setContentView(content);

            mSurfaceView.setZOrderOnTop(true);
            mSurfaceView.getHolder().addCallback(this);

        }

        @Override
        public void onEnterAnimationComplete() {
            mCountDownLatch.countDown();
        }

        @Override
        public void surfaceCreated(@NonNull SurfaceHolder holder) {
            mCountDownLatch.countDown();
        }


        @Override
        public void onAttachedToWindow() {
            SurfaceControl.Transaction t = new SurfaceControl.Transaction();
            t.addTransactionCommittedListener(mExecutor, mCountDownLatch::countDown);
            getWindow().getRootSurfaceControl().applyTransactionOnDraw(t);
        }

        @Override
        public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width,
                int height) {

        }

        @Override
        public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
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

        public SurfaceControl getSurfaceControl() throws InterruptedException {
            assertAndDumpWindowState(TAG, "No SurfaceView found",
                    mCountDownLatch.await(5, TimeUnit.SECONDS));
            return mSurfaceView.getSurfaceControl();
        }
    }
}
