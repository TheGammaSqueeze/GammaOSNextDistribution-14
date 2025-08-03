/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.gameframerate.cts;

import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.test.uiautomator.UiDevice;
import android.sysprop.SurfaceFlingerProperties;
import android.util.Log;
import android.view.Choreographer;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;

import java.io.IOException;
import java.util.ArrayList;

/**
 * An Activity to help with frame rate testing.
 */
public class GameFrameRateCtsActivity extends Activity {
    private static final String TAG = "GameFrameRateCtsActivity";
    private static final long FRAME_RATE_SWITCH_GRACE_PERIOD_NANOSECONDS = 2 * 1_000_000_000L;
    private static final long STABLE_FRAME_RATE_WAIT_NANOSECONDS = 1 * 1_000_000_000L;
    private static final long POST_BUFFER_INTERVAL_NANOSECONDS = 500_000_000L;
    private static final int PRECONDITION_WAIT_MAX_ATTEMPTS = 5;
    private static final long PRECONDITION_WAIT_TIMEOUT_NANOSECONDS = 20 * 1_000_000_000L;
    private static final long PRECONDITION_VIOLATION_WAIT_TIMEOUT_NANOSECONDS = 3 * 1_000_000_000L;
    private static final float FRAME_RATE_TOLERANCE = 0.01f;
    private static final float FPS_TOLERANCE_FOR_FRAME_RATE_OVERRIDE = 5;
    private static final long FRAME_RATE_MIN_WAIT_TIME_NANOSECONDS = 1 * 1_000_000_000L;
    private static final long FRAME_RATE_MAX_WAIT_TIME_NANOSECONDS = 10 * 1_000_000_000L;

    // Default game frame rate sets the frame rate to 60 by default, even if the system properties
    // "ro.surface_flinger.game_default_frame_rate_override" is not set. Ref: GameManagerService
    // {@link com.android.server.app.GameManagerService#onBootCompleted()}
    private static final Integer GAME_DEFAULT_FRAMERATE_INT =
            SurfaceFlingerProperties.game_default_frame_rate_override().orElse(60);

    private DisplayManager mDisplayManager;
    private SurfaceView mSurfaceView;
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private Object mLock = new Object();
    private Surface mSurface = null;
    private float mReportedDisplayRefreshRate;
    private float mReportedDisplayModeRefreshRate;
    private ArrayList<Float> mRefreshRateChangedEvents = new ArrayList<Float>();

    private long mLastBufferPostTime;

    private enum ActivityState { RUNNING, PAUSED, DESTROYED }
    private ActivityState mActivityState;

    SurfaceHolder.Callback mSurfaceHolderCallback = new SurfaceHolder.Callback() {
        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            synchronized (mLock) {
                mSurface = holder.getSurface();
                mLock.notify();
            }
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            synchronized (mLock) {
                mSurface = null;
                mLock.notify();
            }
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        }
    };

    DisplayManager.DisplayListener mDisplayListener = new DisplayManager.DisplayListener() {
        @Override
        public void onDisplayAdded(int displayId) {
        }

        @Override
        public void onDisplayChanged(int displayId) {
            synchronized (mLock) {
                float refreshRate = getDisplay().getRefreshRate();
                float displayModeRefreshRate = getDisplay().getMode().getRefreshRate();
                if (refreshRate != mReportedDisplayRefreshRate
                        || displayModeRefreshRate != mReportedDisplayModeRefreshRate) {
                    Log.i(TAG, String.format("Frame rate changed: (%.2f, %.2f) --> (%.2f, %.2f)",
                            mReportedDisplayModeRefreshRate,
                            mReportedDisplayRefreshRate,
                            displayModeRefreshRate,
                            refreshRate));
                    mReportedDisplayRefreshRate = refreshRate;
                    mReportedDisplayModeRefreshRate = displayModeRefreshRate;
                    mRefreshRateChangedEvents.add(refreshRate);
                    mLock.notify();
                }
            }
        }

        @Override
        public void onDisplayRemoved(int displayId) {
        }
    };

    private static class PreconditionViolatedException extends RuntimeException { }

    private static class FrameRateTimeoutException extends RuntimeException {
        FrameRateTimeoutException(FrameRateRange appRequestedFrameRate, float deviceRefreshRate) {
            this.appRequestedFrameRate = appRequestedFrameRate;
            this.deviceRefreshRate = deviceRefreshRate;
        }

        public FrameRateRange appRequestedFrameRate;
        public float deviceRefreshRate;
    }

    private static class FrameRateRange {
        FrameRateRange(float min, float max) {
            this.min = min;
            this.max = max;
        }
        public float min;
        public float max;
    }

    public void postBufferToSurface(int color) {
        mLastBufferPostTime = System.nanoTime();
        Canvas canvas = mSurface.lockCanvas(null);
        canvas.drawColor(color);
        mSurface.unlockCanvasAndPost(canvas);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        synchronized (mLock) {
            mDisplayManager = getSystemService(DisplayManager.class);
            mReportedDisplayRefreshRate = getDisplay().getRefreshRate();
            mReportedDisplayModeRefreshRate = getDisplay().getMode().getRefreshRate();
            mDisplayManager.registerDisplayListener(mDisplayListener, mHandler);
            mSurfaceView = new SurfaceView(this);
            mSurfaceView.setWillNotDraw(false);
            setContentView(mSurfaceView,
                    new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT));
            mSurfaceView.getHolder().addCallback(mSurfaceHolderCallback);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mDisplayManager.unregisterDisplayListener(mDisplayListener);
        synchronized (mLock) {
            mActivityState = ActivityState.DESTROYED;
            mLock.notify();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        synchronized (mLock) {
            mActivityState = ActivityState.PAUSED;
            mLock.notify();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        synchronized (mLock) {
            mActivityState = ActivityState.RUNNING;
            mLock.notify();
        }
    }

    private static boolean frameRatesEqual(float frameRate1, float frameRate2) {
        return Math.abs(frameRate1 - frameRate2) <= FRAME_RATE_TOLERANCE;
    }

    private static boolean frameRatesMatchesOverride(float frameRate1, float frameRate2) {
        return Math.abs(frameRate1 - frameRate2) <= FPS_TOLERANCE_FOR_FRAME_RATE_OVERRIDE;
    }

    private static boolean frameRatesMatchesOverride(
            float fps, FrameRateRange expectedFrameRateRange) {
        return (fps + FPS_TOLERANCE_FOR_FRAME_RATE_OVERRIDE >= expectedFrameRateRange.min)
                && (fps - FPS_TOLERANCE_FOR_FRAME_RATE_OVERRIDE <= expectedFrameRateRange.max);
    }

    // Waits until our SurfaceHolder has a surface and the activity is resumed.
    private void waitForPreconditions() throws InterruptedException {
        assertTrue(
                "Activity was unexpectedly destroyed", !isDestroyed());
        if (mSurface == null || mActivityState != ActivityState.RUNNING) {
            Log.i(TAG, String.format(
                    "Waiting for preconditions. Have surface? %b. Activity resumed? %b.",
                    mSurface != null, mActivityState == ActivityState.RUNNING));
        }
        long nowNanos = System.nanoTime();
        long endTimeNanos = nowNanos + PRECONDITION_WAIT_TIMEOUT_NANOSECONDS;
        while (mSurface == null || mActivityState != ActivityState.RUNNING) {
            long timeRemainingMillis = (endTimeNanos - nowNanos) / 1_000_000;
            assertTrue(String.format("Timed out waiting for preconditions. Have surface? %b."
                                    + " Activity resumed? %b.",
                            mSurface != null, mActivityState == ActivityState.RUNNING),
                    timeRemainingMillis > 0);
            mLock.wait(timeRemainingMillis);
            assertTrue(
                    "Activity was unexpectedly destroyed", !isDestroyed());
            nowNanos = System.nanoTime();
        }
    }

    // Returns true if we encounter a precondition violation, false otherwise.
    private boolean waitForPreconditionViolation() throws InterruptedException {
        assertTrue(
                "Activity was unexpectedly destroyed", !isDestroyed());
        long nowNanos = System.nanoTime();
        long endTimeNanos = nowNanos + PRECONDITION_VIOLATION_WAIT_TIMEOUT_NANOSECONDS;
        while (mSurface != null && mActivityState == ActivityState.RUNNING) {
            long timeRemainingMillis = (endTimeNanos - nowNanos) / 1_000_000;
            if (timeRemainingMillis <= 0) {
                break;
            }
            mLock.wait(timeRemainingMillis);
            assertTrue(
                    "Activity was unexpectedly destroyed", !isDestroyed());
            nowNanos = System.nanoTime();
        }
        return mSurface == null || mActivityState != ActivityState.RUNNING;
    }

    private void verifyPreconditions() {
        if (mSurface == null || mActivityState != ActivityState.RUNNING) {
            throw new PreconditionViolatedException();
        }
    }

    // Returns true if we reached waitUntilNanos, false if some other event occurred.
    private boolean waitForEvents(long waitUntilNanos)
            throws InterruptedException {
        mRefreshRateChangedEvents.clear();
        long nowNanos = System.nanoTime();
        while (nowNanos < waitUntilNanos) {
            long surfacePostTime = mLastBufferPostTime + POST_BUFFER_INTERVAL_NANOSECONDS;
            long timeoutNs = Math.min(waitUntilNanos, surfacePostTime) - nowNanos;
            long timeoutMs = timeoutNs / 1_000_000L;
            int remainderNs = (int) (timeoutNs % 1_000_000L);
            // Don't call wait(0, 0) - it blocks indefinitely.
            if (timeoutMs > 0 || remainderNs > 0) {
                mLock.wait(timeoutMs, remainderNs);
            }
            nowNanos = System.nanoTime();
            verifyPreconditions();
            if (!mRefreshRateChangedEvents.isEmpty()) {
                return false;
            }
            if (nowNanos >= surfacePostTime) {
                postBufferToSurface(Color.RED);
            }
        }
        return true;
    }

    private void waitForRefreshRateChange(FrameRateRange expectedRefreshRate)
            throws InterruptedException {
        Log.i(TAG, "Waiting for the refresh rate to change");
        long nowNanos = System.nanoTime();
        long gracePeriodEndTimeNanos =
                nowNanos + FRAME_RATE_SWITCH_GRACE_PERIOD_NANOSECONDS;
        while (true) {
            // Wait until we switch to the expected refresh rate
            while (!frameRatesMatchesOverride(mReportedDisplayRefreshRate, expectedRefreshRate)
                    && !waitForEvents(gracePeriodEndTimeNanos)) {
                // Empty
            }
            nowNanos = System.nanoTime();
            if (nowNanos >= gracePeriodEndTimeNanos) {
                throw new FrameRateTimeoutException(expectedRefreshRate,
                        mReportedDisplayRefreshRate);
            }

            // We've switched to a compatible frame rate. Now wait for a while to see if we stay at
            // that frame rate.
            long endTimeNanos = nowNanos + STABLE_FRAME_RATE_WAIT_NANOSECONDS;
            while (endTimeNanos > nowNanos) {
                if (waitForEvents(endTimeNanos)) {
                    Log.i(TAG, String.format("Stable frame rate %.2f verified",
                            mReportedDisplayRefreshRate));
                    return;
                }
                nowNanos = System.nanoTime();
                if (!mRefreshRateChangedEvents.isEmpty()) {
                    break;
                }
            }
        }
    }

    // Returns a range of frame rate that is accepted to
    // make this test more flexible for VRR devices.
    // For example, a frame rate of 80 is valid for a 120 Hz VRR display,
    // but only 60 is available when 80 is requested on non-VRR display.
    // Here we return the range of the requested override +- the gap between the closest divisor.
    private FrameRateRange getExpectedFrameRate(float refreshRate, int frameRateOverride) {
        float divisorOverrideGap = Float.MAX_VALUE;
        FrameRateRange expectedFrameRateRange = new FrameRateRange(0.f, 0.f);
        final float refreshRateRounded = Math.round(refreshRate);
        for (int divisor = 1; refreshRateRounded / divisor >= 30; ++divisor) {
            float frameRate = refreshRateRounded / divisor;
            if (frameRate > frameRateOverride) {
                continue;
            }
            if (Math.abs(frameRateOverride - frameRate) <= divisorOverrideGap) {
                divisorOverrideGap = Math.abs(frameRateOverride - frameRate);
            }
        }
        expectedFrameRateRange.min = frameRateOverride - divisorOverrideGap;
        expectedFrameRateRange.max = frameRateOverride + divisorOverrideGap;
        Log.i(TAG, String.format("getExpectedFrameRate expectedFrameRate %.2f %.2f",
                expectedFrameRateRange.min, expectedFrameRateRange.max));
        return expectedFrameRateRange;
    }

    interface FrameRateObserver {
        void observe(float initialRefreshRate, FrameRateRange expectedFrameRate, String condition)
                throws InterruptedException;
    }

    class BackpressureFrameRateObserver implements FrameRateObserver {
        @Override
        public void observe(float initialRefreshRate,
                FrameRateRange expectedFrameRate, String condition) {
            long startTime = System.nanoTime();
            int totalBuffers = 0;
            float fps = 0;
            while (System.nanoTime() - startTime <= FRAME_RATE_MAX_WAIT_TIME_NANOSECONDS) {
                postBufferToSurface(Color.BLACK + totalBuffers);
                totalBuffers++;
                if (System.nanoTime() - startTime >= FRAME_RATE_MIN_WAIT_TIME_NANOSECONDS) {
                    float testDuration = (System.nanoTime() - startTime) / 1e9f;
                    fps = totalBuffers / testDuration;
                    if (frameRatesMatchesOverride(fps, expectedFrameRate)) {
                        Log.i(TAG,
                                String.format("%s: backpressure observed refresh rate %.2f",
                                        condition,
                                        fps));
                        return;
                    }
                }
            }

            assertTrue(String.format(
                            "%s: backpressure observed refresh rate doesn't match the current"
                                    + "refresh rate. "
                                    + "expected: (%.2f, %.2f) observed: %.2f",
                            condition, expectedFrameRate.min, expectedFrameRate.max, fps),
                    frameRatesMatchesOverride(fps, expectedFrameRate));
        }
    }

    class ChoreographerFrameRateObserver implements FrameRateObserver {
        class ChoreographerThread extends Thread implements Choreographer.FrameCallback {
            Choreographer mChoreographer;
            long mStartTime;
            public Handler mHandler;
            Looper mLooper;
            int mTotalCallbacks = 0;
            long mEndTime;
            FrameRateRange mExpectedRefreshRate;
            String mCondition;

            ChoreographerThread(FrameRateRange expectedRefreshRate, String condition)
                    throws InterruptedException {
                mExpectedRefreshRate = expectedRefreshRate;
                mCondition = condition;
            }

            @Override
            public void run() {
                Looper.prepare();
                mChoreographer = Choreographer.getInstance();
                mHandler = new Handler();
                mLooper = Looper.myLooper();
                mStartTime = System.nanoTime();
                mChoreographer.postFrameCallback(this);
                Looper.loop();
            }

            @Override
            public void doFrame(long frameTimeNanos) {
                mTotalCallbacks++;
                mEndTime = System.nanoTime();
                if (mEndTime - mStartTime <= FRAME_RATE_MIN_WAIT_TIME_NANOSECONDS) {
                    mChoreographer.postFrameCallback(this);
                    return;
                } else if (frameRatesMatchesOverride(getFps(), mExpectedRefreshRate)
                        || mEndTime - mStartTime > FRAME_RATE_MAX_WAIT_TIME_NANOSECONDS) {
                    mLooper.quitSafely();
                    return;
                }
                mChoreographer.postFrameCallback(this);
            }

            public void verifyFrameRate() throws InterruptedException {
                float fps = getFps();
                Log.i(TAG,
                        String.format("%s: choreographer observed refresh rate %.2f",
                                mCondition,
                                fps));
                assertTrue(String.format(
                                "%s: choreographer observed refresh rate doesn't match the current "
                                        + "refresh rate. expected: (%.2f, %.2f) observed: %.2f",
                                mCondition,
                                mExpectedRefreshRate.min,
                                mExpectedRefreshRate.max,
                                fps),
                        frameRatesMatchesOverride(fps, mExpectedRefreshRate));
            }

            private float getFps() {
                return mTotalCallbacks / ((mEndTime - mStartTime) / 1e9f);
            }
        }

        @Override
        public void observe(float initialRefreshRate, FrameRateRange expectedFrameRate,
                String condition) throws InterruptedException {
            ChoreographerThread thread = new ChoreographerThread(expectedFrameRate, condition);
            thread.start();
            thread.join();
            thread.verifyFrameRate();
        }
    }

    class DisplayGetRefreshRateFrameRateObserver implements FrameRateObserver {
        @Override
        public void observe(float initialRefreshRate,
                FrameRateRange expectedFrameRate, String condition) {
            Log.i(TAG,
                    String.format("%s: Display.getRefreshRate() returned refresh rate %.2f",
                            condition, mReportedDisplayRefreshRate));
            assertTrue(String.format("%s: Display.getRefreshRate() doesn't match the "
                                    + "current refresh. expected: (%.2f, %.2f) observed: %.2f",
                            condition,  expectedFrameRate.min,
                            expectedFrameRate.max, mReportedDisplayRefreshRate),
                    frameRatesMatchesOverride(mReportedDisplayRefreshRate, expectedFrameRate));
        }
    }
    class DisplayModeGetRefreshRateFrameRateObserver implements FrameRateObserver {

        @Override
        public void observe(float initialRefreshRate,
                FrameRateRange expectedFrameRate, String condition) {
            Log.i(TAG,
                    String.format(
                            "%s: Display.getMode().getRefreshRate() returned refresh rate %.2f",
                            condition, mReportedDisplayModeRefreshRate));
            assertTrue(String.format("%s: Display.getMode().getRefreshRate() doesn't match the "
                                    + "current refresh. expected: %.2f observed: %.2f", condition,
                            initialRefreshRate, mReportedDisplayModeRefreshRate),
                    frameRatesMatchesOverride(mReportedDisplayModeRefreshRate, initialRefreshRate));
        }
    }

    interface TestScenario {
        void test(FrameRateObserver frameRateObserver, float initialRefreshRate,
                int[] frameRateOverrides) throws InterruptedException, IOException;
    }

    class GameModeTest implements TestScenario {
        private UiDevice mUiDevice;
        GameModeTest(UiDevice uiDevice) {
            mUiDevice = uiDevice;
        }
        @Override
        public void test(FrameRateObserver frameRateObserver, float initialRefreshRate,
                int[] frameRateOverrides) throws InterruptedException, IOException {
            Log.i(TAG, "Starting testGameModeFrameRateOverride");

            for (int frameRateOverride : frameRateOverrides) {

                Log.i(TAG, String.format("Setting Frame Rate to %d using Game Mode",
                        frameRateOverride));

                // Given that the frame rate we attempt to override is not always the divisor of
                // the current refresh rate, get the expected frame rate, which is the closest
                // divisor of the refresh rate here.
                FrameRateRange expectedFrameRate =
                        getExpectedFrameRate(initialRefreshRate, frameRateOverride);

                mUiDevice.executeShellCommand(String.format("cmd game set --mode 2 --fps %d %s",
                        frameRateOverride, getPackageName()));

                waitForRefreshRateChange(expectedFrameRate);
                frameRateObserver.observe(initialRefreshRate, expectedFrameRate,
                        String.format("Game Mode Override(%d), expectedFrameRate(%.2f %.2f)",
                                frameRateOverride, expectedFrameRate.min, expectedFrameRate.max));
            }


            Log.i(TAG, String.format("Resetting game mode."));

            FrameRateRange expectedFrameRate =
                    getExpectedFrameRate(initialRefreshRate, GAME_DEFAULT_FRAMERATE_INT);
            mUiDevice.executeShellCommand(String.format("cmd game reset %s", getPackageName()));
            waitForRefreshRateChange(expectedFrameRate);
            frameRateObserver.observe(initialRefreshRate, expectedFrameRate,
                    String.format("Game Default Frame Rate(%d), expectedFrameRate(%.2f %.2f)",
                            GAME_DEFAULT_FRAMERATE_INT,
                            expectedFrameRate.min,
                            expectedFrameRate.max));
        }
    }

    // The activity being intermittently paused/resumed has been observed to
    // cause test failures in practice, so we run the test with retry logic.
    public void testFrameRateOverride(TestScenario frameRateOverrideBehavior,
            FrameRateObserver frameRateObserver,
            float initialRefreshRate,
            int[] frameRateOverrides)
            throws InterruptedException, IOException {
        synchronized (mLock) {
            Log.i(TAG, "testFrameRateOverride started with initial refresh rate "
                    + initialRefreshRate);
            int attempts = 0;
            boolean testPassed = false;
            try {
                while (!testPassed) {
                    waitForPreconditions();
                    try {
                        frameRateOverrideBehavior.test(frameRateObserver,
                                initialRefreshRate, frameRateOverrides);
                        testPassed = true;
                    } catch (PreconditionViolatedException exc) {
                        // The logic below will retry if we're below max attempts.
                    } catch (FrameRateTimeoutException exc) {
                        // Sometimes we get a test timeout failure before we get the
                        // notification that the activity was paused, and it was the pause that
                        // caused the timeout failure. Wait for a bit to see if we get notified
                        // of a precondition violation, and if so, retry the test. Otherwise,
                        // fail.
                        assertTrue(
                                String.format(
                                        "Timed out waiting for a stable and compatible frame"
                                                + " rate. requested=(%.2f, %.2f) received=%.2f.",
                                        exc.appRequestedFrameRate.min,
                                        exc.appRequestedFrameRate.max,
                                        exc.deviceRefreshRate),
                                waitForPreconditionViolation());
                    }

                    if (!testPassed) {
                        Log.i(TAG,
                                String.format("Preconditions violated while running the test."
                                                + " Have surface? %b. Activity resumed? %b.",
                                        mSurface != null,
                                        mActivityState == ActivityState.RUNNING));
                        attempts++;
                        assertTrue(String.format(
                                        "Exceeded %d precondition wait attempts. Giving up.",
                                        PRECONDITION_WAIT_MAX_ATTEMPTS),
                                attempts < PRECONDITION_WAIT_MAX_ATTEMPTS);
                    }
                }
            } finally {
                if (testPassed) {
                    Log.i(TAG, "**** PASS ****");
                } else {
                    Log.i(TAG, "**** FAIL ****");
                }
            }

        }
    }
}
