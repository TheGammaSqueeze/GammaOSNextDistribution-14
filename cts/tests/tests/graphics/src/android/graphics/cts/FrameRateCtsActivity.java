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

package android.graphics.cts;

import static android.system.OsConstants.EINVAL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceControl;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;

import com.android.compatibility.common.util.DisplayUtil;

import com.google.common.primitives.Floats;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An Activity to help with frame rate testing.
 */
public class FrameRateCtsActivity extends Activity {
    static {
        System.loadLibrary("ctsgraphics_jni");
    }

    private static final String TAG = "FrameRateCtsActivity";
    private static final long FRAME_RATE_SWITCH_GRACE_PERIOD_SECONDS = 4;
    private static final long STABLE_FRAME_RATE_WAIT_SECONDS = 1;
    private static final long POST_BUFFER_INTERVAL_MILLIS = 500;
    private static final int PRECONDITION_WAIT_MAX_ATTEMPTS = 5;
    private static final long PRECONDITION_WAIT_TIMEOUT_SECONDS = 20;
    private static final long PRECONDITION_VIOLATION_WAIT_TIMEOUT_SECONDS = 3;
    private static final float FRAME_RATE_TOLERANCE_STRICT = 0.01f;

    // Tolerance which doesn't differentiate between the fractional refresh rate pairs, e.g.
    // 59.94 and 60 will be considered the same refresh rate.
    // Use this tolerance to verify the refresh rate after calling setFrameRate with
    // {@Surface.FRAME_RATE_COMPATIBILITY_DEFAULT}.
    private static final float FRAME_RATE_TOLERANCE_RELAXED = 0.1f;

    private DisplayManager mDisplayManager;
    private SurfaceView mSurfaceView;
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private final Object mLock = new Object();
    private Surface mSurface = null;
    private float mDeviceFrameRate;
    private ModeChangedEvents mModeChangedEvents = new ModeChangedEvents();

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
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}
    };

    DisplayManager.DisplayListener mDisplayListener = new DisplayManager.DisplayListener() {
        @Override
        public void onDisplayAdded(int displayId) {}

        @Override
        public void onDisplayChanged(int displayId) {
            if (displayId != Display.DEFAULT_DISPLAY) {
                return;
            }
            synchronized (mLock) {
                Display.Mode mode = mDisplayManager.getDisplay(displayId).getMode();
                mModeChangedEvents.add(mode);
                float frameRate =  mode.getRefreshRate();
                if (frameRate != mDeviceFrameRate) {
                    Log.i(TAG,
                            String.format("Frame rate changed: %.2f --> %.2f", mDeviceFrameRate,
                                    frameRate));
                    mDeviceFrameRate = frameRate;
                    mLock.notify();
                }
            }
        }

        @Override
        public void onDisplayRemoved(int displayId) {}
    };

    // Wrapper around ArrayList for which the only allowed mutable operation is add().
    // We use this to store all mode change events during a test. When we need to iterate over
    // all mode changes during a certain operation, we use the number of events in the beginning
    // and in the end. It's important to never clear or modify the elements in this list hence the
    // wrapper.
    private static class ModeChangedEvents {
        private List<Display.Mode> mEvents = new ArrayList<>();

        public void add(Display.Mode mode) {
            mEvents.add(mode);
        }

        public Display.Mode get(int i) {
            return mEvents.get(i);
        }

        public int size() {
            return mEvents.size();
        }
    }

    private static class PreconditionViolatedException extends RuntimeException {
        PreconditionViolatedException() {}
    }

    private static class FrameRateTimeoutException extends RuntimeException {
        FrameRateTimeoutException(float expectedFrameRate, float deviceFrameRate) {
            this.expectedFrameRate = expectedFrameRate;
            this.deviceFrameRate = deviceFrameRate;
        }

        public float expectedFrameRate;
        public float deviceFrameRate;
    }

    public enum Api {
        SURFACE("Surface"),
        ANATIVE_WINDOW("ANativeWindow"),
        SURFACE_CONTROL("SurfaceControl"),
        NATIVE_SURFACE_CONTROL("ASurfaceControl");

        private final String mName;
        Api(String name) {
            mName = name;
        }

        public String toString() {
            return mName;
        }
    }

    private static class TestSurface {
        private Api mApi;
        private String mName;
        private SurfaceControl mSurfaceControl;
        private Surface mSurface;
        private long mNativeSurfaceControl;
        private int mColor;
        private boolean mLastBufferPostTimeValid;
        private long mLastBufferPostTime;

        TestSurface(Api api, SurfaceControl parentSurfaceControl, Surface parentSurface,
                String name, Rect destFrame, boolean visible, int color) {
            mApi = api;
            mName = name;
            mColor = color;

            if (mApi == Api.SURFACE || mApi == Api.ANATIVE_WINDOW || mApi == Api.SURFACE_CONTROL) {
                assertNotNull("No parent surface", parentSurfaceControl);
                mSurfaceControl = new SurfaceControl.Builder()
                                          .setParent(parentSurfaceControl)
                                          .setName(mName)
                                          .setBufferSize(destFrame.right - destFrame.left,
                                                  destFrame.bottom - destFrame.top)
                                          .build();
                try (SurfaceControl.Transaction transaction = new SurfaceControl.Transaction()) {
                    transaction.setGeometry(mSurfaceControl, null, destFrame, Surface.ROTATION_0)
                            .apply();
                }
                mSurface = new Surface(mSurfaceControl);
            } else if (mApi == Api.NATIVE_SURFACE_CONTROL) {
                assertNotNull("No parent surface", parentSurface);
                mNativeSurfaceControl = nativeSurfaceControlCreate(parentSurface, mName,
                        destFrame.left, destFrame.top, destFrame.right, destFrame.bottom);
                assertTrue("Failed to create a native SurfaceControl", mNativeSurfaceControl != 0);
            }

            setVisibility(visible);
            postBuffer();
        }

        public int setFrameRate(float frameRate, int compatibility, int changeFrameRateStrategy) {
            Log.i(TAG,
                    String.format("Setting frame rate for %s: fps=%.2f compatibility=%s", mName,
                            frameRate, frameRateCompatibilityToString(compatibility)));

            int rc = 0;
            if (mApi == Api.SURFACE) {
                if (changeFrameRateStrategy == Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS) {
                    mSurface.setFrameRate(frameRate, compatibility);
                } else {
                    mSurface.setFrameRate(frameRate, compatibility, changeFrameRateStrategy);
                }
            } else if (mApi == Api.ANATIVE_WINDOW) {
                rc = nativeWindowSetFrameRate(mSurface, frameRate, compatibility,
                        changeFrameRateStrategy);
            } else if (mApi == Api.SURFACE_CONTROL) {
                try (SurfaceControl.Transaction transaction = new SurfaceControl.Transaction()) {
                    if (changeFrameRateStrategy == Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS) {
                        transaction
                                .setFrameRate(mSurfaceControl, frameRate, compatibility);
                    } else {
                        transaction
                                .setFrameRate(mSurfaceControl, frameRate, compatibility,
                                        changeFrameRateStrategy);
                    }
                    transaction.apply();
                }
            } else if (mApi == Api.NATIVE_SURFACE_CONTROL) {
                nativeSurfaceControlSetFrameRate(mNativeSurfaceControl, frameRate, compatibility,
                        changeFrameRateStrategy);
            }
            return rc;
        }

        public int clearFrameRate() {
            Log.i(TAG,
                    String.format("Clearing frame rate for %s", mName));
            int rc = 0;
            if (mApi == Api.SURFACE) {
                mSurface.clearFrameRate();
            } else if (mApi == Api.ANATIVE_WINDOW) {
                rc = nativeWindowClearFrameRate(mSurface);
            } else if (mApi == Api.SURFACE_CONTROL) {
                try (SurfaceControl.Transaction transaction = new SurfaceControl.Transaction()) {
                    transaction.clearFrameRate(mSurfaceControl);
                    transaction.apply();
                }
            } else if (mApi == Api.NATIVE_SURFACE_CONTROL) {
                nativeSurfaceControlClearFrameRate(mNativeSurfaceControl);
            }
            return rc;
        }

        public void setInvalidFrameRate(float frameRate, int compatibility,
                int changeFrameRateStrategy) {
            if (mApi == Api.SURFACE) {
                boolean caughtIllegalArgException = false;
                try {
                    setFrameRate(frameRate, compatibility, changeFrameRateStrategy);
                } catch (IllegalArgumentException exc) {
                    caughtIllegalArgException = true;
                }
                assertTrue("Expected an IllegalArgumentException from invalid call to"
                                + " Surface.setFrameRate()",
                        caughtIllegalArgException);
            } else {
                int rc = setFrameRate(frameRate, compatibility, changeFrameRateStrategy);
                if (mApi == Api.ANATIVE_WINDOW) {
                    assertEquals("Expected -EINVAL return value from invalid call to"
                            + " ANativeWindow_setFrameRate()", rc, -EINVAL);
                }
            }
        }

        public void setVisibility(boolean visible) {
            Log.i(TAG,
                    String.format("Setting visibility for %s: %s", mName,
                            visible ? "visible" : "hidden"));
            if (mApi == Api.SURFACE || mApi == Api.ANATIVE_WINDOW || mApi == Api.SURFACE_CONTROL) {
                try (SurfaceControl.Transaction transaction = new SurfaceControl.Transaction()) {
                    transaction.setVisibility(mSurfaceControl, visible).apply();
                }
            } else if (mApi == Api.NATIVE_SURFACE_CONTROL) {
                nativeSurfaceControlSetVisibility(mNativeSurfaceControl, visible);
            }
        }

        public void postBuffer() {
            mLastBufferPostTimeValid = true;
            mLastBufferPostTime = System.nanoTime();
            if (mApi == Api.SURFACE || mApi == Api.ANATIVE_WINDOW || mApi == Api.SURFACE_CONTROL) {
                Canvas canvas = mSurface.lockHardwareCanvas();
                canvas.drawColor(mColor);
                mSurface.unlockCanvasAndPost(canvas);
            } else if (mApi == Api.NATIVE_SURFACE_CONTROL) {
                assertTrue("Posting a buffer failed",
                        nativeSurfaceControlPostBuffer(mNativeSurfaceControl, mColor));
            }
        }

        public long getLastBufferPostTime() {
            assertTrue("No buffer posted yet", mLastBufferPostTimeValid);
            return mLastBufferPostTime;
        }

        public void release() {
            if (mSurface != null) {
                mSurface.release();
                mSurface = null;
            }
            if (mSurfaceControl != null) {
                try (SurfaceControl.Transaction transaction = new SurfaceControl.Transaction()) {
                    transaction.reparent(mSurfaceControl, null).apply();
                }
                mSurfaceControl.release();
                mSurfaceControl = null;
            }
            if (mNativeSurfaceControl != 0) {
                nativeSurfaceControlDestroy(mNativeSurfaceControl);
                mNativeSurfaceControl = 0;
            }
        }

        @Override
        protected void finalize() throws Throwable {
            try {
                release();
            } finally {
                super.finalize();
            }
        }
    }

    private static String frameRateCompatibilityToString(int compatibility) {
        switch (compatibility) {
            case Surface.FRAME_RATE_COMPATIBILITY_DEFAULT:
                return "default";
            case Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE:
                return "fixed_source";
            default:
                return "invalid(" + compatibility + ")";
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        synchronized (mLock) {
            mDisplayManager = (DisplayManager) getSystemService(DISPLAY_SERVICE);
            Display.Mode mode = getDisplay().getMode();
            mDeviceFrameRate = mode.getRefreshRate();
            // Insert the initial mode so we have the full display mode history.
            mModeChangedEvents.add(mode);
            mDisplayManager.registerDisplayListener(mDisplayListener, mHandler);
            mSurfaceView = new SurfaceView(this);
            mSurfaceView.setWillNotDraw(false);
            mSurfaceView.setZOrderOnTop(true);
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

    // Returns the refresh rates with the same resolution as "mode".
    private ArrayList<Float> getRefreshRates(Display.Mode mode, Display display) {
        Display.Mode[] modes = display.getSupportedModes();
        ArrayList<Float> frameRates = new ArrayList<>();
        for (Display.Mode supportedMode : modes) {
            if (hasSameResolution(supportedMode, mode)) {
                frameRates.add(supportedMode.getRefreshRate());
            }
        }
        Collections.sort(frameRates);
        ArrayList<Float> uniqueFrameRates = new ArrayList<>();
        for (float frameRate : frameRates) {
            if (uniqueFrameRates.isEmpty()
                    || frameRate - uniqueFrameRates.get(uniqueFrameRates.size() - 1)
                            >= FRAME_RATE_TOLERANCE_STRICT) {
                uniqueFrameRates.add(frameRate);
            }
        }
        return uniqueFrameRates;
    }

    private List<Float> getSeamedRefreshRates(Display.Mode mode, Display display) {
        List<Float> seamedRefreshRates = new ArrayList<>();
        Display.Mode[] modes = display.getSupportedModes();
        for (Display.Mode otherMode : modes) {
            if (hasSameResolution(mode, otherMode)
                    && !DisplayUtil.isModeSwitchSeamless(mode, otherMode)) {
                seamedRefreshRates.add(otherMode.getRefreshRate());
            }
        }
        return seamedRefreshRates;
    }

    private boolean hasSameResolution(Display.Mode mode1, Display.Mode mode2) {
        return mode1.getPhysicalHeight() == mode2.getPhysicalHeight()
                && mode1.getPhysicalWidth() == mode2.getPhysicalWidth();
    }

    private boolean isFrameRateMultiple(
            float higherFrameRate, float lowerFrameRate, float tolerance) {
        float multiple = higherFrameRate / lowerFrameRate;
        int roundedMultiple = Math.round(multiple);
        return roundedMultiple > 0
                && Math.abs(roundedMultiple * lowerFrameRate - higherFrameRate) <= tolerance;
    }

    // Returns two device-supported frame rates that aren't multiples of each other, or null if no
    // such incompatible frame rates are available. This is useful for testing behavior where we
    // have layers with conflicting frame rates.
    private float[] getIncompatibleFrameRates(Display display) {
        ArrayList<Float> frameRates = getRefreshRates(display.getMode(), display);
        for (int i = 0; i < frameRates.size(); i++) {
            for (int j = i + 1; j < frameRates.size(); j++) {
                if (!isFrameRateMultiple(Math.max(frameRates.get(i), frameRates.get(j)),
                            Math.min(frameRates.get(i), frameRates.get(j)),
                            FRAME_RATE_TOLERANCE_RELAXED)) {
                    return new float[] {frameRates.get(i), frameRates.get(j)};
                }
            }
        }
        return null;
    }

    // Waits until our SurfaceHolder has a surface and the activity is resumed.
    private void waitForPreconditions() throws InterruptedException {
        assertNotSame("Activity was unexpectedly destroyed", mActivityState,
                ActivityState.DESTROYED);
        if (mSurface == null || mActivityState != ActivityState.RUNNING) {
            Log.i(TAG,
                    String.format(
                            "Waiting for preconditions. Have surface? %b. Activity resumed? %b.",
                            mSurface != null, mActivityState == ActivityState.RUNNING));
        }
        long nowNanos = System.nanoTime();
        long endTimeNanos = nowNanos + PRECONDITION_WAIT_TIMEOUT_SECONDS * 1_000_000_000L;
        while (mSurface == null || mActivityState != ActivityState.RUNNING) {
            long timeRemainingMillis = (endTimeNanos - nowNanos) / 1_000_000;
            assertTrue(String.format("Timed out waiting for preconditions. Have surface? %b."
                                       + " Activity resumed? %b.",
                               mSurface != null, mActivityState == ActivityState.RUNNING),
                    timeRemainingMillis > 0);
            mLock.wait(timeRemainingMillis);
            assertNotSame("Activity was unexpectedly destroyed", mActivityState,
                    ActivityState.DESTROYED);
            nowNanos = System.nanoTime();
        }
        // Make sure any previous mode changes are completed.
        waitForStableFrameRate();
    }

    // Returns true if we encounter a precondition violation, false otherwise.
    private boolean waitForPreconditionViolation() throws InterruptedException {
        assertNotSame("Activity was unexpectedly destroyed", mActivityState,
                ActivityState.DESTROYED);
        long nowNanos = System.nanoTime();
        long endTimeNanos = nowNanos + PRECONDITION_VIOLATION_WAIT_TIMEOUT_SECONDS * 1_000_000_000L;
        while (mSurface != null && mActivityState == ActivityState.RUNNING) {
            long timeRemainingMillis = (endTimeNanos - nowNanos) / 1_000_000;
            if (timeRemainingMillis <= 0) {
                break;
            }
            mLock.wait(timeRemainingMillis);
            assertNotSame("Activity was unexpectedly destroyed", mActivityState,
                    ActivityState.DESTROYED);
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
    private boolean waitForEvents(long waitUntilNanos, TestSurface[] surfaces)
            throws InterruptedException {
        int numModeChangedEvents = mModeChangedEvents.size();
        long nowNanos = System.nanoTime();
        while (nowNanos < waitUntilNanos) {
            long surfacePostTime = Long.MAX_VALUE;
            for (TestSurface surface : surfaces) {
                surfacePostTime = Math.min(surfacePostTime,
                        surface.getLastBufferPostTime()
                                + (POST_BUFFER_INTERVAL_MILLIS * 1_000_000L));
            }
            long timeoutNs = Math.min(waitUntilNanos, surfacePostTime) - nowNanos;
            long timeoutMs = timeoutNs / 1_000_000L;
            int remainderNs = (int) (timeoutNs % 1_000_000L);
            // Don't call wait(0, 0) - it blocks indefinitely.
            if (timeoutMs > 0 || remainderNs > 0) {
                mLock.wait(timeoutMs, remainderNs);
            }
            nowNanos = System.nanoTime();
            verifyPreconditions();
            if (mModeChangedEvents.size() > numModeChangedEvents) {
                return false;
            }
            if (nowNanos >= surfacePostTime) {
                for (TestSurface surface : surfaces) {
                    surface.postBuffer();
                }
            }
        }
        return true;
    }

    private void waitForStableFrameRate(TestSurface... surfaces) throws InterruptedException {
        verifyCompatibleAndStableFrameRate(0, FRAME_RATE_TOLERANCE_STRICT, surfaces);
    }

    // Set expectedFrameRate to 0.0 to verify only stable frame rate.
    private void verifyCompatibleAndStableFrameRate(float expectedFrameRate, float tolerance,
            TestSurface... surfaces) throws InterruptedException {
        Log.i(TAG, "Verifying compatible and stable frame rate");
        long nowNanos = System.nanoTime();
        long gracePeriodEndTimeNanos =
                nowNanos + FRAME_RATE_SWITCH_GRACE_PERIOD_SECONDS * 1_000_000_000L;
        while (true) {
            if (expectedFrameRate > tolerance) { // expectedFrameRate > 0
                // Wait until we switch to a compatible frame rate.
                while (!isFrameRateMultiple(mDeviceFrameRate, expectedFrameRate, tolerance)
                        && !waitForEvents(gracePeriodEndTimeNanos, surfaces)) {
                    // Empty
                }
                nowNanos = System.nanoTime();
                if (nowNanos >= gracePeriodEndTimeNanos) {
                    throw new FrameRateTimeoutException(expectedFrameRate, mDeviceFrameRate);
                }
            }

            // We've switched to a compatible frame rate. Now wait for a while to see if we stay at
            // that frame rate.
            long endTimeNanos = nowNanos + STABLE_FRAME_RATE_WAIT_SECONDS * 1_000_000_000L;
            while (endTimeNanos > nowNanos) {
                int numModeChangedEvents = mModeChangedEvents.size();
                if (waitForEvents(endTimeNanos, surfaces)) {
                    Log.i(TAG, String.format("Stable frame rate %.2f verified", mDeviceFrameRate));
                    return;
                }
                nowNanos = System.nanoTime();
                if (mModeChangedEvents.size() > numModeChangedEvents) {
                    break;
                }
            }
        }
    }

    private void verifyModeSwitchesDontChangeResolution(int fromId, int toId) {
        assertTrue(fromId <= toId);
        for (int eventId = fromId; eventId < toId; eventId++) {
            Display.Mode fromMode = mModeChangedEvents.get(eventId - 1);
            Display.Mode toMode = mModeChangedEvents.get(eventId);
            assertTrue("Resolution change was not expected, but there was such from "
                    + fromMode + " to " + toMode + ".", hasSameResolution(fromMode, toMode));
        }
    }

    private void verifyModeSwitchesAreSeamless(int fromId, int toId) {
        assertTrue(fromId <= toId);
        for (int eventId = fromId; eventId < toId; eventId++) {
            Display.Mode fromMode = mModeChangedEvents.get(eventId - 1);
            Display.Mode toMode = mModeChangedEvents.get(eventId);
            assertTrue("Non-seamless mode switch was not expected, but there was a "
                            + "non-seamless switch from from " + fromMode + " to " + toMode + ".",
                    DisplayUtil.isModeSwitchSeamless(fromMode, toMode));
        }
    }

    // Unfortunately, we can't just use Consumer<Api> for this, because we need to declare that it
    // throws InterruptedException.
    private interface TestInterface {
        void run(Api api) throws InterruptedException;
    }

    private interface OneSurfaceTestInterface {
        void run(TestSurface surface) throws InterruptedException;
    }

    // Runs the given test for each api, waiting for the preconditions to be satisfied before
    // running the test. Includes retry logic when the test fails because the preconditions are
    // violated. E.g. if we lose the SurfaceHolder's surface, or the activity is paused/resumed,
    // we'll retry the test. The activity being intermittently paused/resumed has been observed to
    // cause test failures in practice.
    private void runTestsWithPreconditions(TestInterface test, String testName)
            throws InterruptedException {
        synchronized (mLock) {
            for (Api api : Api.values()) {
                Log.i(TAG, String.format("Testing %s %s", api, testName));
                int attempts = 0;
                boolean testPassed = false;
                try {
                    while (!testPassed) {
                        waitForPreconditions();
                        try {
                            test.run(api);
                            testPassed = true;
                        } catch (PreconditionViolatedException exc) {
                            // The logic below will retry if we're below max attempts.
                        } catch (FrameRateTimeoutException exc) {
                            StringWriter stringWriter = new StringWriter();
                            PrintWriter printWriter = new PrintWriter(stringWriter);
                            exc.printStackTrace(printWriter);
                            String stackTrace = stringWriter.toString();

                            // Sometimes we get a test timeout failure before we get the
                            // notification that the activity was paused, and it was the pause that
                            // caused the timeout failure. Wait for a bit to see if we get notified
                            // of a precondition violation, and if so, retry the test. Otherwise
                            // fail.
                            assertTrue(
                                    String.format(
                                            "Timed out waiting for a stable and compatible frame"
                                                    + " rate. expected=%.2f received=%.2f."
                                                    + " Stack trace: " + stackTrace,
                                            exc.expectedFrameRate, exc.deviceFrameRate),
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
                    String passFailMessage = String.format(
                            "%s %s %s", testPassed ? "Passed" : "Failed", api, testName);
                    if (testPassed) {
                        Log.i(TAG, passFailMessage);
                    } else {
                        Log.e(TAG, passFailMessage);
                    }
                }
            }
        }
    }

    public void testExactFrameRateMatch(int changeFrameRateStrategy) throws InterruptedException {
        String type = changeFrameRateStrategy == Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS
                ? "seamless" : "always";
        runTestsWithPreconditions(api -> testExactFrameRateMatch(api, changeFrameRateStrategy),
                type + " exact frame rate match");
    }

    public void testClearFrameRate() throws InterruptedException {
        runTestsWithPreconditions(this::testClearFrameRate, "clear frame rate");
    }

    private void testExactFrameRateMatch(Api api, int changeFrameRateStrategy)
            throws InterruptedException {
        runOneSurfaceTest(api, (TestSurface surface) -> {
            Display display = mDisplayManager.getDisplay(Display.DEFAULT_DISPLAY);
            Display.Mode currentMode = display.getMode();

            if (changeFrameRateStrategy == Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS) {
                // Seamless rates should be seamlessly achieved with no resolution changes.
                List<Float> seamlessRefreshRates =
                        Floats.asList(currentMode.getAlternativeRefreshRates());
                for (float frameRate : seamlessRefreshRates) {
                    int initialNumEvents = mModeChangedEvents.size();
                    surface.setFrameRate(frameRate, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT,
                            Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS);
                    verifyCompatibleAndStableFrameRate(frameRate, FRAME_RATE_TOLERANCE_RELAXED,
                            surface);
                    verifyModeSwitchesAreSeamless(initialNumEvents, mModeChangedEvents.size());
                    verifyModeSwitchesDontChangeResolution(initialNumEvents,
                            mModeChangedEvents.size());
                }
                // Reset to default
                surface.setFrameRate(0.f, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT,
                        Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS);
                // Wait for potential mode switches
                waitForStableFrameRate(surface);
                currentMode = display.getMode();

                // Seamed rates should never generate a seamed switch.
                List<Float> seamedRefreshRates = getSeamedRefreshRates(currentMode, display);
                for (float frameRate : seamedRefreshRates) {
                    int initialNumEvents = mModeChangedEvents.size();
                    surface.setFrameRate(frameRate, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT,
                            Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS);
                    // Mode switch can occur, since we could potentially switch to a multiple
                    // that happens to be seamless.
                    verifyModeSwitchesAreSeamless(initialNumEvents, mModeChangedEvents.size());
                }
            } else if (changeFrameRateStrategy == Surface.CHANGE_FRAME_RATE_ALWAYS) {
                // All rates should be seamfully achieved with no resolution changes.
                List<Float> allRefreshRates = getRefreshRates(currentMode, display);
                for (float frameRate : allRefreshRates) {
                    int initialNumEvents = mModeChangedEvents.size();
                    surface.setFrameRate(frameRate, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT,
                            Surface.CHANGE_FRAME_RATE_ALWAYS);
                    verifyCompatibleAndStableFrameRate(frameRate, FRAME_RATE_TOLERANCE_RELAXED,
                            surface);
                    verifyModeSwitchesDontChangeResolution(initialNumEvents,
                            mModeChangedEvents.size());
                }
            } else {
                Log.e(TAG, "Invalid changeFrameRateStrategy = " + changeFrameRateStrategy);
            }
        });
    }

    private String modeSwitchesToString(int fromId, int toId) {
        assertTrue(fromId <= toId);
        String string = "";
        for (int eventId = fromId; eventId < toId; eventId++) {
            Display.Mode fromMode = mModeChangedEvents.get(eventId - 1);
            Display.Mode toMode = mModeChangedEvents.get(eventId);
            string += fromMode + " -> " + toMode + "; ";
        }
        return string;
    }

    private void testFixedSource(Api api, int changeFrameRateStrategy) throws InterruptedException {
        Display display = getDisplay();
        float[] incompatibleFrameRates = getIncompatibleFrameRates(display);
        if (incompatibleFrameRates == null) {
            Log.i(TAG, "No incompatible frame rates to use for testing fixed_source behavior");
            return;
        }

        float frameRateA = incompatibleFrameRates[0];
        float frameRateB = incompatibleFrameRates[1];
        Log.i(TAG,
                String.format("Testing with incompatible frame rates: surfaceA=%.2f surfaceB=%.2f",
                        frameRateA, frameRateB));
        TestSurface surfaceA = null;
        TestSurface surfaceB = null;

        try {
            int width = mSurfaceView.getHolder().getSurfaceFrame().width();
            int height = mSurfaceView.getHolder().getSurfaceFrame().height() / 2;
            Rect destFrameA = new Rect(/*left=*/0, /*top=*/0, /*right=*/width, /*bottom=*/height);
            surfaceA = new TestSurface(api, mSurfaceView.getSurfaceControl(), mSurface, "surfaceA",
                    destFrameA, /*visible=*/true, Color.RED);
            Rect destFrameB = new Rect(
                    /*left=*/0, /*top=*/height, /*right=*/width, /*bottom=*/height * 2);
            surfaceB = new TestSurface(api, mSurfaceView.getSurfaceControl(), mSurface, "surfaceB",
                    destFrameB, /*visible=*/false, Color.GREEN);

            int initialNumEvents = mModeChangedEvents.size();
            surfaceA.setFrameRate(frameRateA, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT,
                    changeFrameRateStrategy);
            surfaceB.setFrameRate(frameRateB, Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,
                    changeFrameRateStrategy);

            if (changeFrameRateStrategy == Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS) {
                verifyModeSwitchesAreSeamless(initialNumEvents, mModeChangedEvents.size());
            } else {
                verifyCompatibleAndStableFrameRate(frameRateA, FRAME_RATE_TOLERANCE_STRICT,
                        surfaceA, surfaceB);
            }

            verifyModeSwitchesDontChangeResolution(initialNumEvents,
                    mModeChangedEvents.size());
            initialNumEvents = mModeChangedEvents.size();

            surfaceB.setVisibility(true);

            if (changeFrameRateStrategy == Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS) {
                verifyModeSwitchesAreSeamless(initialNumEvents, mModeChangedEvents.size());
            } else {
                verifyCompatibleAndStableFrameRate(frameRateB, FRAME_RATE_TOLERANCE_STRICT,
                        surfaceA, surfaceB);
            }
            verifyModeSwitchesDontChangeResolution(initialNumEvents,
                    mModeChangedEvents.size());
        } finally {
            if (surfaceA != null) {
                surfaceA.release();
            }
            if (surfaceB != null) {
                surfaceB.release();
            }
        }
    }

    public void testFixedSource(int changeFrameRateStrategy) throws InterruptedException {
        String type = changeFrameRateStrategy == Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS
                ? "seamless" : "always";
        runTestsWithPreconditions(api -> testFixedSource(api, changeFrameRateStrategy),
                type + " fixed source behavior");
    }

    private void testInvalidParams(Api api) {
        TestSurface surface = null;
        final int changeStrategy = Surface.CHANGE_FRAME_RATE_ALWAYS;
        try {
            surface = new TestSurface(api, mSurfaceView.getSurfaceControl(), mSurface,
                    "testSurface", mSurfaceView.getHolder().getSurfaceFrame(),
                    /*visible=*/true, Color.RED);
            int initialNumEvents = mModeChangedEvents.size();
            surface.setInvalidFrameRate(-100.f, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT,
                    changeStrategy);
            assertEquals(initialNumEvents, mModeChangedEvents.size());
            surface.setInvalidFrameRate(Float.POSITIVE_INFINITY,
                    Surface.FRAME_RATE_COMPATIBILITY_DEFAULT, changeStrategy);
            assertEquals(initialNumEvents, mModeChangedEvents.size());
            surface.setInvalidFrameRate(Float.NaN, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT,
                    changeStrategy);
            assertEquals(initialNumEvents, mModeChangedEvents.size());
            surface.setInvalidFrameRate(0.f, -10, changeStrategy);
            assertEquals(initialNumEvents, mModeChangedEvents.size());
            surface.setInvalidFrameRate(0.f, 50, changeStrategy);
            assertEquals(initialNumEvents, mModeChangedEvents.size());
        } finally {
            if (surface != null) {
                surface.release();
            }
        }
    }

    public void testInvalidParams() throws InterruptedException {
        runTestsWithPreconditions(this::testInvalidParams, "invalid params behavior");
    }

    private void runOneSurfaceTest(Api api, OneSurfaceTestInterface test)
            throws InterruptedException {
        TestSurface surface = null;
        try {
            surface = new TestSurface(api, mSurfaceView.getSurfaceControl(), mSurface,
                    "testSurface", mSurfaceView.getHolder().getSurfaceFrame(),
                    /*visible=*/true, Color.RED);

            test.run(surface);
        } finally {
            if (surface != null) {
                surface.release();
            }
        }
    }

    private void testMatchContentFramerate_None(Api api) throws InterruptedException {
        runOneSurfaceTest(api, (TestSurface surface) -> {
            Display display = getDisplay();
            Display.Mode currentMode = display.getMode();
            List<Float> frameRates = getRefreshRates(currentMode, display);

            for (float frameRate : frameRates) {
                int initialNumEvents = mModeChangedEvents.size();
                surface.setFrameRate(frameRate, Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,
                        Surface.CHANGE_FRAME_RATE_ALWAYS);

                assertEquals("Mode switches are not expected but these were detected "
                                + modeSwitchesToString(initialNumEvents, mModeChangedEvents.size()),
                        mModeChangedEvents.size(), initialNumEvents);
            }
        });
    }

    public void testMatchContentFramerate_None() throws InterruptedException {
        runTestsWithPreconditions(this::testMatchContentFramerate_None,
                "testMatchContentFramerate_None");
    }

    private void testMatchContentFramerate_Auto(Api api)
            throws InterruptedException {
        runOneSurfaceTest(api, (TestSurface surface) -> {
            Display display = getDisplay();
            Display.Mode currentMode = display.getMode();
            List<Float> frameRatesToTest = Floats.asList(currentMode.getAlternativeRefreshRates());

            for (float frameRate : frameRatesToTest) {
                int initialNumEvents = mModeChangedEvents.size();
                surface.setFrameRate(frameRate, Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,
                        Surface.CHANGE_FRAME_RATE_ALWAYS);

                verifyCompatibleAndStableFrameRate(frameRate, FRAME_RATE_TOLERANCE_STRICT, surface);
                verifyModeSwitchesDontChangeResolution(initialNumEvents,
                        mModeChangedEvents.size());
            }

            // Reset to default
            surface.setFrameRate(0.f, Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,
                    Surface.CHANGE_FRAME_RATE_ALWAYS);

            // Wait for potential mode switches.
            waitForStableFrameRate(surface);

            currentMode = display.getMode();
            List<Float> seamedRefreshRates = getSeamedRefreshRates(currentMode, display);

            for (float frameRate : seamedRefreshRates) {
                int initialNumEvents = mModeChangedEvents.size();
                surface.setFrameRate(frameRate, Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,
                        Surface.CHANGE_FRAME_RATE_ALWAYS);

                // Mode switches may have occurred, make sure they were all seamless.
                verifyModeSwitchesAreSeamless(initialNumEvents, mModeChangedEvents.size());
                verifyModeSwitchesDontChangeResolution(initialNumEvents,
                        mModeChangedEvents.size());
            }
        });
    }

    public void testMatchContentFramerate_Auto() throws InterruptedException {
        runTestsWithPreconditions(this::testMatchContentFramerate_Auto,
                "testMatchContentFramerate_Auto");
    }

    private void testMatchContentFramerate_Always(Api api) throws InterruptedException {
        runOneSurfaceTest(api, (TestSurface surface) -> {
            Display display = getDisplay();
            List<Float> frameRates = getRefreshRates(display.getMode(), display);
            for (float frameRate : frameRates) {
                int initialNumEvents = mModeChangedEvents.size();
                surface.setFrameRate(frameRate, Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,
                        Surface.CHANGE_FRAME_RATE_ALWAYS);

                verifyCompatibleAndStableFrameRate(frameRate, FRAME_RATE_TOLERANCE_STRICT, surface);
                verifyModeSwitchesDontChangeResolution(initialNumEvents,
                        mModeChangedEvents.size());
            }
        });
    }

    private void testClearFrameRate(Api api) throws InterruptedException {
        Display display = mDisplayManager.getDisplay(Display.DEFAULT_DISPLAY);
        Display.Mode initialMode = display.getMode();

        int width = mSurfaceView.getHolder().getSurfaceFrame().width();
        int height = mSurfaceView.getHolder().getSurfaceFrame().height() / 2;
        Rect destFrameA = new Rect(/*left=*/0, /*top=*/0, /*right=*/width, /*bottom=*/height);
        TestSurface surface = new TestSurface(api, mSurfaceView.getSurfaceControl(), mSurface,
                "surface", destFrameA, /*visible=*/true, Color.RED);

        // Verify clear frame rate if set frame rate is seamless
        List<Float> seamlessRefreshRates =
                Floats.asList(initialMode.getAlternativeRefreshRates());
        verifyClearFrameRate(surface, seamlessRefreshRates, initialMode.getRefreshRate(),
                Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS);

        // Verify clear frame rate if set frame rate is non-seamless
        List<Float> seamedRefreshRates = getSeamedRefreshRates(initialMode, display);
        verifyClearFrameRate(surface, seamedRefreshRates, initialMode.getRefreshRate(),
                Surface.CHANGE_FRAME_RATE_ALWAYS);
    }

    private void verifyClearFrameRate(TestSurface surface, List<Float> refreshRates,
            float initialRefreshRate, int changeFrameRateStrategy) throws InterruptedException {
        for (float frameRate : refreshRates) {
            if (initialRefreshRate != frameRate) {
                surface.setFrameRate(frameRate, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT,
                        changeFrameRateStrategy);
                verifyCompatibleAndStableFrameRate(frameRate, FRAME_RATE_TOLERANCE_RELAXED,
                        surface);

                // Clear the frame-rate
                surface.clearFrameRate();
                waitForStableFrameRate(surface);
                break;
            }
        }
    }

    public void testMatchContentFramerate_Always() throws InterruptedException {
        runTestsWithPreconditions(this::testMatchContentFramerate_Always,
                "testMatchContentFramerate_Always");
    }

    private static native int nativeWindowSetFrameRate(
            Surface surface, float frameRate, int compatibility, int changeFrameRateStrategy);
    private static native long nativeSurfaceControlCreate(
            Surface parentSurface, String name, int left, int top, int right, int bottom);
    private static native void nativeSurfaceControlDestroy(long surfaceControl);
    private static native void nativeSurfaceControlSetFrameRate(
            long surfaceControl, float frameRate, int compatibility, int changeFrameRateStrategy);
    private static native void nativeSurfaceControlSetVisibility(
            long surfaceControl, boolean visible);
    private static native boolean nativeSurfaceControlPostBuffer(long surfaceControl, int color);
    private static native int nativeWindowClearFrameRate(Surface surface);
    private static native void nativeSurfaceControlClearFrameRate(long surfaceControl);
}
