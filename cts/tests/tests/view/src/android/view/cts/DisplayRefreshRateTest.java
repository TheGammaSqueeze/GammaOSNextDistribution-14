/*
 * Copyright (C) 2011 The Android Open Source Project
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

package android.view.cts;

import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Test that the screen refresh rate claimed by
 * android.view.Display.getRefreshRate() matches the steady-state framerate
 * achieved by vsync-limited eglSwapBuffers(). The primary goal is to test
 * Display.getRefreshRate() -- using GL is just an easy and hopefully reliable
 * way of measuring the actual refresh rate.
 */
@LargeTest
@RunWith(AndroidJUnit4.class)
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public class DisplayRefreshRateTest {
    // The test passes if
    //   abs(measured_fps - Display.getRefreshRate()) <= FPS_TOLERANCE.
    // A smaller tolerance requires a more accurate measured_fps in order
    // to avoid false negatives.
    private static final float FPS_TOLERANCE = 2.0f;

    private static final String TAG = "DisplayRefreshRateTest";

    private DisplayManager mDisplayManager;

    private Display mDisplay;

    private int mInitialMatchContentFrameRate;

    private final DisplayListener mDisplayListener = new DisplayListener();

    private DisplayRefreshRateCtsActivity mActivity;
    private DisplayRefreshRateCtsActivity.FpsResult mFpsResult;

    @Rule(order = 1)
    public ActivityTestRule<DisplayRefreshRateCtsActivity> mActivityRule =
            new ActivityTestRule<>(DisplayRefreshRateCtsActivity.class);

    @Rule(order = 0)
    public AdoptShellPermissionsRule mAdoptShellPermissionsRule = new AdoptShellPermissionsRule(
            InstrumentationRegistry.getInstrumentation().getUiAutomation(),
            Manifest.permission.OVERRIDE_DISPLAY_MODE_REQUESTS,
            Manifest.permission.MODIFY_REFRESH_RATE_SWITCHING_TYPE,
            Manifest.permission.START_ACTIVITIES_FROM_SDK_SANDBOX);

    class DisplayListener implements DisplayManager.DisplayListener {
        private CountDownLatch mCountDownLatch = new CountDownLatch(1);

        void waitForModeToChange(int modeId) throws InterruptedException {
            while (modeId != mDisplay.getMode().getModeId()
                && mDisplay.getMode().getRefreshRate() != mDisplay.getRefreshRate()) {
                mCountDownLatch.await(5, TimeUnit.SECONDS);
            }
        }

        @Override
        public void onDisplayAdded(int displayId) {

        }

        @Override
        public void onDisplayRemoved(int displayId) {

        }

        @Override
        public void onDisplayChanged(int displayId) {
            if (displayId != mDisplay.getDisplayId()) {
                return;
            }

            mCountDownLatch.countDown();
        }
    }


    @Before
    public void setup() throws InterruptedException {
        mActivity = mActivityRule.getActivity();
        mFpsResult = mActivity.getFpsResult();

        Context context = mActivity.getApplicationContext();
        mDisplayManager = context.getSystemService(DisplayManager.class);

        mInitialMatchContentFrameRate =
                toSwitchingType(mDisplayManager.getMatchContentFrameRateUserPreference());
        mDisplayManager.setRefreshRateSwitchingType(DisplayManager.SWITCHING_TYPE_NONE);
        mDisplayManager.setShouldAlwaysRespectAppRequestedMode(true);

        // This tests the fps of the default display.
        // In consideration of multi-display devices we use getApplicationContext()
        // to get the default display.
        WindowManager wm = context.getSystemService(WindowManager.class);
        mDisplay = wm.getDefaultDisplay();

        mDisplayManager.registerDisplayListener(mDisplayListener,
                new Handler(Looper.getMainLooper()));

        int highestRefreshRateModeId = getHighestRefreshRateModeId();
        mActivity.setModeId(highestRefreshRateModeId);
        mDisplayListener.waitForModeToChange(highestRefreshRateModeId);
    }

    private int getHighestRefreshRateModeId() {
        int highestRefreshRateModeId = mDisplay.getMode().getModeId();
        for (Display.Mode mode : mDisplay.getSupportedModes()) {
            if (mode.getPhysicalHeight() != mDisplay.getMode().getPhysicalHeight()) {
                continue;
            }

            if (mode.getPhysicalWidth() != mDisplay.getMode().getPhysicalWidth()) {
                continue;
            }

            if (mode.getRefreshRate() > mDisplay.getMode().getRefreshRate()) {
                highestRefreshRateModeId = mode.getModeId();
            }
        }
        return highestRefreshRateModeId;
    }

    @After
    public void tearDown() {
        mDisplayManager.setRefreshRateSwitchingType(mInitialMatchContentFrameRate);
        mDisplayManager.setShouldAlwaysRespectAppRequestedMode(false);
    }

    @Test
    public void testRefreshRate() {
        boolean fpsOk = false;

        for (int i = 0; i < 3; i++) {
            float claimedFps = mDisplay.getRefreshRate();
            float achievedFps = mFpsResult.waitResult();
            Log.d(TAG, "claimed " + claimedFps + " fps, " +
                       "achieved " + achievedFps + " fps");
            fpsOk = Math.abs(claimedFps - achievedFps) <= FPS_TOLERANCE;
            if (fpsOk) {
                break;
            } else {
                // it could be other activity like bug report capturing for other failures
                // sleep for a while and re-try
                SystemClock.sleep(10000);
                mFpsResult.restart();
            }
        }
        mActivity.finish();
        assertTrue(fpsOk);
    }

    private static int toSwitchingType(int matchContentFrameRateUserPreference) {
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
