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

package android.server.wm.activity;

import static android.view.Display.DEFAULT_DISPLAY;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.ComponentCallbacks;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.hardware.display.DisplayManager.DisplayListener;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.os.SystemClock;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.server.wm.RotationSession;
import android.server.wm.WindowManagerTestBase;
import android.util.Log;
import android.util.Size;
import android.view.Display;

import androidx.annotation.NonNull;

import com.android.compatibility.common.util.ApiTest;
import com.android.window.flags.Flags;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Tests that verify the behavior of client side window configuration related state changed
 * callbacks, such as {@link DisplayListener}, to ensure that they are synchronized with the client
 * side {@link Configuration} change.
 *
 * Build/Install/Run:
 *     atest CtsWindowManagerDeviceWindow:ConfigurationCallbacksTest
 */
@Presubmit
public class ConfigurationCallbacksTest extends WindowManagerTestBase {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final String TAG = ConfigurationCallbacksTest.class.getSimpleName();

    private ReportedDisplayMetrics mReportedDisplayMetrics;

    private WindowConfigTracker mDisplayListenerTracker;
    private WindowConfigTracker mActivityOnConfigurationChangedTracker;
    private WindowConfigTracker mApplicationOnConfigurationChangedTracker;

    private TestComponentCallbacks mApplicationCallbacks;
    private TestDisplayListener mDisplayListener;
    private TestActivity mActivity;

    @Before
    public void setUp() throws Exception {
        super.setUp();

        mReportedDisplayMetrics = ReportedDisplayMetrics.getDisplayMetrics(Display.DEFAULT_DISPLAY);

        mDisplayListenerTracker = new WindowConfigTracker("DisplayListener");
        mActivityOnConfigurationChangedTracker = new WindowConfigTracker(
                "Activity#onConfigurationChanged");
        // Application callback is expected to be triggered before Activity Config update.
        mApplicationOnConfigurationChangedTracker = new WindowConfigTracker(
                "Application#onConfigurationChanged", true /* excludeActivity */);

        mActivity = startActivityInWindowingModeFullScreen(TestActivity.class);
        waitAndAssertResumedActivity(mActivity.getComponentName(), "The activity must be resumed.");
        assertFalse(mActivity.isInMultiWindowMode());

        mActivity.setWindowConfigTracker(mActivityOnConfigurationChangedTracker);

        mApplicationCallbacks = new TestComponentCallbacks(
                mApplicationOnConfigurationChangedTracker);
        mActivity.getApplication().registerComponentCallbacks(mApplicationCallbacks);

        mDisplayListener = new TestDisplayListener(mDisplayListenerTracker);
        mDm.registerDisplayListener(mDisplayListener, new Handler(Looper.getMainLooper()));
    }

    @After
    public void tearDown() throws RemoteException {
        if (mDisplayListener != null) {
            mDm.unregisterDisplayListener(mDisplayListener);
        }
        if (mActivity != null) {
            mActivity.getApplication().unregisterComponentCallbacks(mApplicationCallbacks);
            mActivity.finish();
        }
        if (mReportedDisplayMetrics != null) {
            mReportedDisplayMetrics.restoreDisplayMetrics();
        }
    }

    /**
     * Verifies that when the display rotates, the last triggered
     * {@link DisplayListener#onDisplayChanged} have updated {@link android.app.WindowConfiguration}
     * that is synchronized with the display window.
     */
    @RequiresFlagsEnabled(Flags.FLAG_BUNDLE_CLIENT_TRANSACTION_FLAG)
    @Test
    @ApiTest(apis = {
            "android.hardware.display.DisplayManager.DisplayListener#onDisplayChanged",
            "android.app.Activity#onConfigurationChanged",
            "android.content.ComponentCallbacks#onConfigurationChanged",
    })
    public void testDisplayRotate() {
        assumeTrue(supportsRotation());

        final RotationSession rotationSession = createManagedRotationSession();
        int rotation = rotationSession.get();
        for (int i = 0; i < 4; i++) {
            rotation = (rotation + 1) % 4;
            initTrackers();
            rotationSession.set(rotation);
            waitAndAssertRotationInCallbacks(rotation);
        }
    }

    /**
     * Verifies that when the display resizes, the last triggered
     * {@link DisplayListener#onDisplayChanged} have updated {@link android.app.WindowConfiguration}
     * that is synchronized with the display window.
     */
    @RequiresFlagsEnabled(Flags.FLAG_BUNDLE_CLIENT_TRANSACTION_FLAG)
    @Test
    @ApiTest(apis = {
            "android.hardware.display.DisplayManager.DisplayListener#onDisplayChanged",
            "android.app.Activity#onConfigurationChanged",
            "android.content.ComponentCallbacks#onConfigurationChanged",
    })
    public void testDisplayResize() {
        final Size originalSize = mReportedDisplayMetrics.getSize();
        // Use a negative offset in case the device set config_maxUiWidth.
        final int offset = -Math.min(originalSize.getWidth() / 10, originalSize.getHeight() / 10);
        final int newWidth = originalSize.getWidth() + offset;
        final int newHeight = originalSize.getHeight() + offset;
        assumeTrue("Can't resize the display smaller than min size",
                newWidth >= 200 && newHeight >= 200);

        initTrackers();
        mReportedDisplayMetrics.setSize(new Size(newWidth, newHeight));
        waitAndAssertDimensionsOffsetInCallbacks(offset);
    }

    /**
     * Initializes {@link WindowConfigTracker}s.
     * Should be called before triggering the test system event.
     */
    private void initTrackers() {
        Log.d(TAG, "initTrackers");
        mDisplayListenerTracker.init();
        mActivityOnConfigurationChangedTracker.init();
        mApplicationOnConfigurationChangedTracker.init();
    }

    /**
     * Waits and asserts that the last system callbacks must come with the given display rotation.
     */
    private void waitAndAssertRotationInCallbacks(int expectedRotation) {
        final long curTime = SystemClock.elapsedRealtime();
        mDisplayListenerTracker.waitAndAssertRotation(expectedRotation, curTime);
        mActivityOnConfigurationChangedTracker.waitAndAssertRotation(expectedRotation, curTime);
        mApplicationOnConfigurationChangedTracker.waitAndAssertRotation(expectedRotation, curTime);
    }

    /**
     * Waits and asserts that the last system callbacks must come with display dimensions with the
     * given offset from the current dimensions.
     *
     * Note: the same offset will be used for both width and height because the display size set
     * through {@link ReportedDisplayMetrics} is independent to the display rotation.
     */
    private void waitAndAssertDimensionsOffsetInCallbacks(int expectedOffset) {
        final long curTime = SystemClock.elapsedRealtime();
        mDisplayListenerTracker.waitAndAssertDimensionsOffset(expectedOffset, curTime);
        mActivityOnConfigurationChangedTracker.waitAndAssertDimensionsOffset(expectedOffset,
                curTime);
        mApplicationOnConfigurationChangedTracker.waitAndAssertDimensionsOffset(expectedOffset,
                curTime);
    }

    /**
     * Helper class to keep track and assert the window configuration in system callbacks.
     *
     * Use flow:
     * 1. Calls {@link #init()} to reset tracked value, and record the initial config.
     * 2. Applies any action to trigger system callbacks.
     * 3. Calls {@link #waitAndAssertRotation} or {@link #waitAndAssertDimensionsOffset}
     *    to test that the last system callback has the current config.
     */
    private class WindowConfigTracker {

        private static final int INVALID_ROTATION = -1;

        private static final long CALLBACK_TIMEOUT_MS = 2000L;
        private static final long CALLBACK_TIMEOUT_MAX_RETRY = 4;
        private static final long CALLBACK_TIMEOUT_MAX_WAITING_TIME_MS = 8000L; // TIMEOUT * RETRY

        @NonNull
        private final String mCallbackName;
        private final boolean mExcludeActivity;

        /** How many times the callback has been triggered since the last {@link #init()} */
        private int mCallbackCount;

        /** The system time when the last system callback was triggered. */
        private long mLastCallbackSystemTime;

        private int mInitDisplayRotation = INVALID_ROTATION;
        private int mInitActivityRotation = INVALID_ROTATION;
        private int mInitApplicationRotation = INVALID_ROTATION;
        private final Rect mInitWindowMetricsBounds = new Rect();
        private final Rect mInitActivityBounds = new Rect();
        private final Rect mInitApplicationBounds = new Rect();

        private int mLastDisplayRotation = INVALID_ROTATION;
        private int mLastActivityRotation = INVALID_ROTATION;
        private int mLastApplicationRotation = INVALID_ROTATION;
        private final Rect mLastWindowMetricsBounds = new Rect();
        private final Rect mLastActivityBounds = new Rect();
        private final Rect mLastApplicationBounds = new Rect();

        WindowConfigTracker(@NonNull String callbackName) {
            this(callbackName, false /* excludeActivity */);
        }

        WindowConfigTracker(@NonNull String callbackName, boolean excludeActivity) {
            mCallbackName = callbackName;
            mExcludeActivity = excludeActivity;
        }

        /** Should be called before triggering the test system event. */
        void init() {
            // Reset counter and timer
            mCallbackCount = 0;
            mLastCallbackSystemTime = 0;

            // Record the current config
            mInitDisplayRotation = getDisplayRotation();
            mInitActivityRotation = getActivityRotation();
            mInitApplicationRotation = getApplicationRotation();
            mInitWindowMetricsBounds.set(getWindowMetricsBounds());
            mInitActivityBounds.set(getActivityBounds());
            mInitApplicationBounds.set(getApplicationBounds());

            // Reset the config from last system callback.
            mLastDisplayRotation = INVALID_ROTATION;
            mLastActivityRotation = INVALID_ROTATION;
            mLastApplicationRotation = INVALID_ROTATION;
            mLastWindowMetricsBounds.setEmpty();
            mLastActivityBounds.setEmpty();
            mLastApplicationBounds.setEmpty();
        }

        /**
         * Called when there is a system callback regarding the window config changed.
         */
        void onWindowConfigChanged() {
            mCallbackCount++;
            mLastCallbackSystemTime = SystemClock.elapsedRealtime();

            mLastDisplayRotation = getDisplayRotation();
            mLastActivityRotation = getActivityRotation();
            mLastApplicationRotation = getApplicationRotation();
            mLastWindowMetricsBounds.set(getWindowMetricsBounds());
            mLastActivityBounds.set(getActivityBounds());
            mLastApplicationBounds.set(getApplicationBounds());
        }

        /**
         * Waits and asserts that the last system callback must come with the given display
         * rotation.
         */
        void waitAndAssertRotation(int expectedRotation, long startTime) {
            waitForLastCallbackTimeout(startTime);
            assertCallbackTriggered();

            final String errorMessage = mCallbackName
                    + ": expect the last rotation to be "
                    + expectedRotation + ", but have:"
                    + "\ninitDisplayRotation=" + mInitDisplayRotation
                    + "\ninitActivityRotation=" + mInitActivityRotation
                    + "\ninitApplicationRotation=" + mInitApplicationRotation
                    + "\nlastDisplayRotation=" + mLastDisplayRotation
                    + "\nlastActivityRotation=" + mLastActivityRotation
                    + "\nlastApplicationRotation=" + mLastApplicationRotation
                    + "\nThe callback has been triggered for " + mCallbackCount + " times.";
            assertTrue(errorMessage, mLastDisplayRotation == expectedRotation
                    && (mExcludeActivity || mLastActivityRotation == expectedRotation)
                    && mLastApplicationRotation == expectedRotation);
        }

        /**
         * Waits and asserts that the last system callback must come with display dimensions with
         * the given offset from the initial dimensions.
         *
         * Note: the same offset will be used for both width and height because the display size set
         * through {@link ReportedDisplayMetrics} is independent to the display rotation.
         */
        void waitAndAssertDimensionsOffset(int expectedOffset, long startTime) {
            waitForLastCallbackTimeout(startTime);
            assertCallbackTriggered();

            final String errorMessage = mCallbackName
                    + ": expect the offset from last bounds right/bottom to be "
                    + expectedOffset + ", but have:"
                    + "\ninitDisplayBounds=" + mInitWindowMetricsBounds
                    + "\ninitActivityBounds=" + mInitActivityBounds
                    + "\ninitApplicationBounds=" + mInitApplicationBounds
                    + "\nlastDisplayBounds=" + mLastWindowMetricsBounds
                    + "\nlastActivityBounds=" + mLastActivityBounds
                    + "\nlastApplicationBounds=" + mLastApplicationBounds
                    + "\nThe callback has been triggered for " + mCallbackCount + " times.";
            mInitWindowMetricsBounds.right += expectedOffset;
            mInitWindowMetricsBounds.bottom += expectedOffset;
            mInitActivityBounds.right += expectedOffset;
            mInitActivityBounds.bottom += expectedOffset;
            mInitApplicationBounds.right += expectedOffset;
            mInitApplicationBounds.bottom += expectedOffset;
            assertTrue(errorMessage, mInitWindowMetricsBounds.equals(mLastWindowMetricsBounds)
                    && (mExcludeActivity || mInitActivityBounds.equals(mLastActivityBounds))
                    && mInitApplicationBounds.equals(mLastApplicationBounds));
        }

        /**
         * Waits until there is enough time from the last callback. This is to ensure that there is
         * no unexpected following callbacks with different config.
         */
        private void waitForLastCallbackTimeout(long startTime) {
            long curTime = SystemClock.elapsedRealtime();
            if (curTime - CALLBACK_TIMEOUT_MAX_WAITING_TIME_MS >= startTime) {
                // No need to wait in case we have waited long enough in other Trackers.
                return;
            }

            for (int i = 0; i < CALLBACK_TIMEOUT_MAX_RETRY; i++) {
                curTime = SystemClock.elapsedRealtime();
                if (mCallbackCount > 0
                        && curTime - CALLBACK_TIMEOUT_MS >= mLastCallbackSystemTime) {
                    return;
                }
                Log.i(TAG, "*** Waiting for last callback " + mCallbackName + " IDLE retry=" + i);
                SystemClock.sleep(CALLBACK_TIMEOUT_MS);
            }
        }

        private void assertCallbackTriggered() {
            assertNotEquals(mCallbackName + ": callback has never been triggered",
                    0, mCallbackCount);
            assertTrue(mCallbackName + ": the last callback didn't wait enough time before timeout",
                    SystemClock.elapsedRealtime() - CALLBACK_TIMEOUT_MS >= mLastCallbackSystemTime);
        }

        private int getDisplayRotation() {
            return mDm.getDisplay(DEFAULT_DISPLAY).getRotation();
        }

        private int getActivityRotation() {
            return mActivity.getResources()
                    .getConfiguration().windowConfiguration.getDisplayRotation();
        }

        private int getApplicationRotation() {
            return mActivity.getApplicationContext().getResources()
                    .getConfiguration().windowConfiguration.getDisplayRotation();
        }

        @NonNull
        private Rect getWindowMetricsBounds() {
            return mWm.getMaximumWindowMetrics().getBounds();
        }

        @NonNull
        private Rect getActivityBounds() {
            return mActivity.getResources()
                    .getConfiguration().windowConfiguration.getBounds();
        }

        @NonNull
        private Rect getApplicationBounds() {
            return mActivity.getApplicationContext().getResources()
                    .getConfiguration().windowConfiguration.getBounds();
        }
    }

    private static class TestComponentCallbacks implements ComponentCallbacks {

        @NonNull
        private final WindowConfigTracker mTracker;

        TestComponentCallbacks(@NonNull WindowConfigTracker tracker) {
            mTracker = tracker;
        }

        @Override
        public void onConfigurationChanged(@NonNull Configuration newConfig) {
            Log.d(TAG, "Application#onConfigurationChanged");
            mTracker.onWindowConfigChanged();
        }

        @Override
        public void onLowMemory() {}
    }

    private static class TestDisplayListener implements DisplayManager.DisplayListener {

        @NonNull
        private final WindowConfigTracker mTracker;

        TestDisplayListener(@NonNull WindowConfigTracker tracker) {
            mTracker = tracker;
        }

        @Override
        public void onDisplayAdded(int displayId) {}

        @Override
        public void onDisplayRemoved(int displayId) {}

        @Override
        public void onDisplayChanged(int displayId) {
            if (displayId == DEFAULT_DISPLAY) {
                // Only test against the default display.
                Log.d(TAG, "DisplayListener#onDisplayChanged");
                mTracker.onWindowConfigChanged();
            }
        }
    }

    /** Activity to be used for verifying window state {@link #onConfigurationChanged}. */
    public static class TestActivity extends FocusableActivity {

        private WindowConfigTracker mTracker;

        /** Initializes to track the window state. */
        void setWindowConfigTracker(@NonNull WindowConfigTracker tracker) {
            mTracker = tracker;
        }

        @Override
        public void onConfigurationChanged(@NonNull Configuration newConfig) {
            super.onConfigurationChanged(newConfig);
            if (mTracker != null) {
                Log.d(TAG, "Activity#onConfigurationChanged");
                mTracker.onWindowConfigChanged();
            }
        }
    }
}
