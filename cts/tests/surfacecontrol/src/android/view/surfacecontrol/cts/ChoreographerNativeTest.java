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

package android.view.surfacecontrol.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.view.Display.Mode;
import android.view.Window;
import android.view.WindowManager;

import androidx.test.InstrumentationRegistry;
import androidx.test.annotation.UiThreadTest;
import androidx.test.filters.FlakyTest;
import androidx.test.filters.MediumTest;
import androidx.test.filters.SmallTest;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;
import com.android.compatibility.common.util.DisplayUtil;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

@FlakyTest
@RunWith(AndroidJUnit4.class)
public class ChoreographerNativeTest {
    private long mChoreographerPtr;

    @Rule
    public ActivityTestRule<CtsActivity> mTestActivityRule =
            new ActivityTestRule<>(
                CtsActivity.class);

    @Rule
    public final AdoptShellPermissionsRule mShellPermissionsRule =
            new AdoptShellPermissionsRule(
                    InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                    Manifest.permission.OVERRIDE_DISPLAY_MODE_REQUESTS,
                    Manifest.permission.MODIFY_REFRESH_RATE_SWITCHING_TYPE);

    private static native long nativeGetChoreographer();
    private static native boolean nativePrepareChoreographerTests(long ptr, long[] refreshPeriods);
    private static native void nativeTestPostCallbackWithoutDelayEventuallyRunsCallbacks(long ptr);
    private static native void nativeTestPostCallbackWithDelayEventuallyRunsCallbacks(long ptr);
    private static native void nativeTestPostCallback64WithoutDelayEventuallyRunsCallbacks(
            long ptr);
    private static native void nativeTestPostCallback64WithDelayEventuallyRunsCallbacks(long ptr);
    private static native void nativeTestPostVsyncCallbackWithoutDelayEventuallyRunsCallbacks(
            long ptr);
    private static native void nativeTestFrameCallbackDataVsyncIdValid(
            long ptr);
    private static native void nativeTestFrameCallbackDataDeadlineInFuture(
            long ptr);
    private static native void nativeTestFrameCallbackDataExpectedPresentTimeInFuture(
            long ptr);
    private static native void nativeTestPostCallbackMixedWithoutDelayEventuallyRunsCallbacks(
            long ptr);
    private static native void nativeTestPostCallbackMixedWithDelayEventuallyRunsCallbacks(
            long ptr);
    private static native void nativeTestRefreshRateCallback(
            long ptr);
    private static native void nativeTestUnregisteringRefreshRateCallback(long ptr);
    private static native void nativeTestMultipleRefreshRateCallbacks(long ptr);
    private static native void nativeTestAttemptToAddRefreshRateCallbackTwiceDoesNotAddTwice(
            long ptr);
    private static native void nativeTestRefreshRateCallbackMixedWithFrameCallbacks(long ptr);
    private native void nativeTestRefreshRateCallbacksAreSyncedWithDisplayManager();

    private Context mContext;
    private DisplayManager mDisplayManager;
    private Display mDefaultDisplay;
    private long[] mSupportedPeriods;

    static {
        System.loadLibrary("ctssurfacecontrol_jni");
    }

    @UiThreadTest
    @Before
    public void setup() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mDisplayManager = (DisplayManager) mContext.getSystemService(Context.DISPLAY_SERVICE);

        Optional<Display> defaultDisplayOpt = Arrays.stream(mDisplayManager.getDisplays())
                .filter(display -> display.getDisplayId() == Display.DEFAULT_DISPLAY)
                .findFirst();

        assertTrue(defaultDisplayOpt.isPresent());
        mDefaultDisplay = defaultDisplayOpt.get();

        mSupportedPeriods = Arrays.stream(mDefaultDisplay.getSupportedModes())
                .mapToLong(mode -> (long) (Duration.ofSeconds(1).toNanos() / mode.getRefreshRate()))
                .toArray();

        mChoreographerPtr = nativeGetChoreographer();
        if (!nativePrepareChoreographerTests(mChoreographerPtr, mSupportedPeriods)) {
            fail("Failed to setup choreographer tests");
        }
    }

    @MediumTest
    @Test
    public void testPostVsyncCallbackWithoutDelayEventuallyRunsCallbacks() {
        nativeTestPostVsyncCallbackWithoutDelayEventuallyRunsCallbacks(mChoreographerPtr);
    }

    @MediumTest
    @Test
    public void testFrameCallbackDataVsyncIdValid() {
        nativeTestFrameCallbackDataVsyncIdValid(mChoreographerPtr);
    }

    @MediumTest
    @Test
    public void testFrameCallbackDataDeadlineInFuture() {
        nativeTestFrameCallbackDataDeadlineInFuture(mChoreographerPtr);
    }

    @MediumTest
    @Test
    public void testFrameCallbackDataExpectedPresentTimeInFuture() {
        nativeTestFrameCallbackDataExpectedPresentTimeInFuture(mChoreographerPtr);
    }

    @MediumTest
    @Test
    public void testPostCallback64WithoutDelayEventuallyRunsCallbacks() {
        nativeTestPostCallback64WithoutDelayEventuallyRunsCallbacks(mChoreographerPtr);
    }

    @MediumTest
    @Test
    public void testPostCallback64WithDelayEventuallyRunsCallbacks() {
        nativeTestPostCallback64WithDelayEventuallyRunsCallbacks(mChoreographerPtr);
    }

    @MediumTest
    @Test
    public void testPostCallbackWithoutDelayEventuallyRunsCallbacks() {
        nativeTestPostCallbackWithoutDelayEventuallyRunsCallbacks(mChoreographerPtr);
    }

    @SmallTest
    @Test
    public void testPostCallbackWithDelayEventuallyRunsCallbacks() {
        nativeTestPostCallbackWithDelayEventuallyRunsCallbacks(mChoreographerPtr);
    }

    @SmallTest
    @Test
    public void testPostCallbackMixedWithoutDelayEventuallyRunsCallbacks() {
        nativeTestPostCallbackMixedWithoutDelayEventuallyRunsCallbacks(mChoreographerPtr);
    }

    @SmallTest
    @Test
    public void testPostCallbackMixedWithDelayEventuallyRunsCallbacks() {
        nativeTestPostCallbackMixedWithDelayEventuallyRunsCallbacks(mChoreographerPtr);
    }

    @SmallTest
    @Test
    public void testRefreshRateCallback() {
        nativeTestRefreshRateCallback(mChoreographerPtr);
    }

    @SmallTest
    @Test
    public void testUnregisteringRefreshRateCallback() {
        nativeTestUnregisteringRefreshRateCallback(mChoreographerPtr);
    }

    @SmallTest
    @Test
    public void testMultipleRefreshRateCallbacks() {
        nativeTestMultipleRefreshRateCallbacks(mChoreographerPtr);
    }

    @SmallTest
    @Test
    public void testAttemptToAddRefreshRateCallbackTwiceDoesNotAddTwice() {
        nativeTestAttemptToAddRefreshRateCallbackTwiceDoesNotAddTwice(mChoreographerPtr);
    }

    @UiThreadTest
    @SmallTest
    @Test
    public void testRefreshRateCallbackMixedWithFrameCallbacks() {
        nativeTestRefreshRateCallbackMixedWithFrameCallbacks(mChoreographerPtr);
    }

    @SmallTest
    @Test
    public void testRefreshRateCallbacksIsSyncedWithDisplayManager() {
        assumeTrue(mSupportedPeriods.length >= 2);
        assumeTrue(findModeForSeamlessSwitch().isPresent());

        int initialMatchContentFrameRate = 0;
        try {

            // Set-up just for this particular test:
            // We must force the screen to be on for this window, and DisplayManager must be
            // configured to always respect the app-requested refresh rate.
            Handler handler = new Handler(Looper.getMainLooper());
            handler.post(() -> {
                mTestActivityRule.getActivity().getWindow().addFlags(
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            });
            mDisplayManager.setShouldAlwaysRespectAppRequestedMode(true);
            initialMatchContentFrameRate = toSwitchingType(
                    mDisplayManager.getMatchContentFrameRateUserPreference());
            mDisplayManager.setRefreshRateSwitchingType(DisplayManager.SWITCHING_TYPE_NONE);
            nativeTestRefreshRateCallbacksAreSyncedWithDisplayManager();
        } finally {
            mDisplayManager.setRefreshRateSwitchingType(initialMatchContentFrameRate);
            mDisplayManager.setShouldAlwaysRespectAppRequestedMode(false);
        }
    }

    // Called by jni in a refresh rate callback
    private void checkRefreshRateIsCurrentAndSwitch(int refreshRate) {
        assertEquals(Math.round(mDefaultDisplay.getRefreshRate()), refreshRate);

        Optional<Mode> maybeNextMode = findModeForSeamlessSwitch();
        assertTrue(maybeNextMode.isPresent());
        Mode mode = maybeNextMode.get();

        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(() -> {
            Window window = mTestActivityRule.getActivity().getWindow();
            WindowManager.LayoutParams params = window.getAttributes();
            params.preferredDisplayModeId = mode.getModeId();
            window.setAttributes(params);
        });
    }

    private Optional<Mode> findModeForSeamlessSwitch() {
        Mode activeMode = mDefaultDisplay.getMode();
        int refreshRate = Math.round(mDefaultDisplay.getRefreshRate());
        return Arrays.stream(mDefaultDisplay.getSupportedModes())
                .filter(mode -> DisplayUtil.isModeSwitchSeamless(activeMode, mode))
                .filter(mode ->  Math.round(mode.getRefreshRate()) != refreshRate)
                .findFirst();
    }

    private int toSwitchingType(int matchContentFrameRateUserPreference) {
        switch (matchContentFrameRateUserPreference) {
            case DisplayManager.MATCH_CONTENT_FRAMERATE_NEVER:
                return DisplayManager.SWITCHING_TYPE_NONE;
            case DisplayManager.MATCH_CONTENT_FRAMERATE_SEAMLESSS_ONLY:
                return DisplayManager.SWITCHING_TYPE_WITHIN_GROUPS;
            case DisplayManager.MATCH_CONTENT_FRAMERATE_ALWAYS:
                return DisplayManager.SWITCHING_TYPE_ACROSS_AND_WITHIN_GROUPS;
            default:
                return -1;
        }
    }
}
