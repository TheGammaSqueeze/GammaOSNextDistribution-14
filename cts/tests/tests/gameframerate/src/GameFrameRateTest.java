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

import android.Manifest;
import android.app.compat.CompatChanges;
import android.gameframerate.cts.GameFrameRateCtsActivity.FrameRateObserver;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemProperties;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.support.test.uiautomator.UiDevice;
import android.sysprop.SurfaceFlingerProperties;
import android.util.Log;
import android.view.Display;
import android.view.Window;
import android.view.WindowManager;

import androidx.test.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Tests for frame rate override and the behaviour of {@link Display#getRefreshRate()} and
 * {@link Display.Mode#getRefreshRate()} Api.
 */
@RunWith(AndroidJUnit4.class)
public final class GameFrameRateTest {
    private static final String TAG = "GameFrameRateTest";
    // See b/170503758 for more details
    private static final long DISPLAY_MODE_RETURNS_PHYSICAL_REFRESH_RATE_CHANGEID = 170503758;

    private static final String TEST_PKG = "android.gameframerate.cts";

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    // The tolerance within which we consider refresh rates are equal
    private static final float REFRESH_RATE_TOLERANCE = 0.01f;

    private int mInitialMatchContentFrameRate;
    private DisplayManager mDisplayManager;
    private UiDevice mUiDevice;
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private static final int[] refreshRateDivisorsToTest =
            {120, 110, 100, 90, 80, 70, 60, 50, 40, 30};


    @Rule
    public ActivityTestRule<GameFrameRateCtsActivity> mActivityRule =
            new ActivityTestRule<>(GameFrameRateCtsActivity.class);

    @Before
    public void setUp() throws Exception {
        mUiDevice = UiDevice.getInstance(
                androidx.test.platform.app.InstrumentationRegistry.getInstrumentation());
        mUiDevice.wakeUp();
        mUiDevice.executeShellCommand("wm dismiss-keyguard");
        mUiDevice.executeShellCommand("device_config put game_overlay"
                                      + TEST_PKG + " mode=2:mode=3");

        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(
                        Manifest.permission.MODIFY_REFRESH_RATE_SWITCHING_TYPE,
                        Manifest.permission.OVERRIDE_DISPLAY_MODE_REQUESTS,
                        Manifest.permission.MANAGE_GAME_MODE);

        mDisplayManager = mActivityRule.getActivity().getSystemService(DisplayManager.class);
        mInitialMatchContentFrameRate = toSwitchingType(
                mDisplayManager.getMatchContentFrameRateUserPreference());
        mDisplayManager.setRefreshRateSwitchingType(
                DisplayManager.SWITCHING_TYPE_RENDER_FRAME_RATE_ONLY);
        mDisplayManager.setShouldAlwaysRespectAppRequestedMode(true);
        boolean changeIsEnabled =
                CompatChanges.isChangeEnabled(DISPLAY_MODE_RETURNS_PHYSICAL_REFRESH_RATE_CHANGEID);
        Log.i(TAG, "DISPLAY_MODE_RETURNS_PHYSICAL_REFRESH_RATE_CHANGEID is "
                + (changeIsEnabled ? "enabled" : "disabled"));
    }

    @After
    public void tearDown() throws Exception {
        mUiDevice.executeShellCommand("device_config delete game_overlay " + TEST_PKG);
        mDisplayManager.setRefreshRateSwitchingType(mInitialMatchContentFrameRate);
        mDisplayManager.setShouldAlwaysRespectAppRequestedMode(false);
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .dropShellPermissionIdentity();
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

    private void setMode(Display.Mode mode) {
        Log.i(TAG, "Setting display refresh rate to " + mode.getRefreshRate());
        mHandler.post(() -> {
            Window window = mActivityRule.getActivity().getWindow();
            WindowManager.LayoutParams params = window.getAttributes();
            params.preferredDisplayModeId = mode.getModeId();
            params.preferredRefreshRate = 0;
            params.preferredMinDisplayRefreshRate = 0;
            params.preferredMaxDisplayRefreshRate = 0;
            window.setAttributes(params);
        });
    }

    // The TV emulator is not expected to be a performant device,
    // so backpressure tests will always fail.
    private boolean isTvEmulator() {
        return SystemProperties.get("ro.build.characteristics").equals("emulator")
                && SystemProperties.get("ro.product.system.name").equals("atv_generic");
    }

    // Find refresh rates with the same resolution.
    private List<Display.Mode> getModesToTest() {
        List<Display.Mode> modesWithSameResolution = new ArrayList<>();
        if (!SurfaceFlingerProperties.enable_frame_rate_override().orElse(true)) {
            Log.i(TAG, "Frame rate override is not enabled, skipping");
            return modesWithSameResolution;
        }

        Display.Mode[] modes = mActivityRule.getActivity().getDisplay().getSupportedModes();
        Display.Mode currentMode = mActivityRule.getActivity().getDisplay().getMode();
        final long currentDisplayHeight = currentMode.getPhysicalHeight();
        final long currentDisplayWidth = currentMode.getPhysicalWidth();

        for (Display.Mode mode : modes) {
            if (mode.getPhysicalHeight() == currentDisplayHeight
                    && mode.getPhysicalWidth() == currentDisplayWidth) {
                modesWithSameResolution.add(mode);
            }
        }

        return modesWithSameResolution;
    }

    private void testGameModeFrameRateOverride(FrameRateObserver frameRateObserver)
            throws InterruptedException, IOException {
        GameFrameRateCtsActivity activity = mActivityRule.getActivity();
        List<Display.Mode> modesToTest = getModesToTest();

        for (Display.Mode mode : modesToTest) {
            setMode(mode);
            activity.testFrameRateOverride(
                    activity.new GameModeTest(mUiDevice),
                    frameRateObserver, mode.getRefreshRate(), refreshRateDivisorsToTest);
            Log.i(TAG, "\n");
        }

        Log.i(TAG, "\n");
    }

    @Test
    @RequiresFlagsEnabled({android.server.app.Flags.FLAG_GAME_DEFAULT_FRAME_RATE,
            com.android.graphics.surfaceflinger.flags.Flags.FLAG_GAME_DEFAULT_FRAME_RATE})
    public void testGameModeBackpressure() throws InterruptedException, IOException {
        if (isTvEmulator()) {
            Log.i(TAG, "**** Skipping Backpressure ****");
            return;
        }

        Log.i(TAG, "**** Starting Game Mode Backpressure Test ****");
        GameFrameRateCtsActivity activity = mActivityRule.getActivity();
        testGameModeFrameRateOverride(activity.new BackpressureFrameRateObserver());
    }

    @Test
    @RequiresFlagsEnabled({android.server.app.Flags.FLAG_GAME_DEFAULT_FRAME_RATE,
            com.android.graphics.surfaceflinger.flags.Flags.FLAG_GAME_DEFAULT_FRAME_RATE})
    public void testGameModeChoreographer() throws InterruptedException, IOException {
        Log.i(TAG, "**** Starting Game Mode Choreographer Test ****");
        GameFrameRateCtsActivity activity = mActivityRule.getActivity();
        testGameModeFrameRateOverride(activity.new ChoreographerFrameRateObserver());
    }

    @Test
    @RequiresFlagsEnabled({android.server.app.Flags.FLAG_GAME_DEFAULT_FRAME_RATE,
            com.android.graphics.surfaceflinger.flags.Flags.FLAG_GAME_DEFAULT_FRAME_RATE})
    public void testGameModeDisplayGetRefreshRate() throws InterruptedException, IOException {
        Log.i(TAG, "**** Starting Game Mode Display#getRefreshRate Test ****");
        GameFrameRateCtsActivity activity = mActivityRule.getActivity();
        testGameModeFrameRateOverride(activity.new DisplayGetRefreshRateFrameRateObserver());
    }

    @Test
    @RequiresFlagsEnabled({android.server.app.Flags.FLAG_GAME_DEFAULT_FRAME_RATE,
            com.android.graphics.surfaceflinger.flags.Flags.FLAG_GAME_DEFAULT_FRAME_RATE})
    public void testGameModeDisplayModeGetRefreshRateDisplayModeReturnsPhysicalRefreshRate()
            throws InterruptedException, IOException {
        Log.i(TAG, "**** Starting Game Mode Display.Mode#getRefreshRate Test ****");
        GameFrameRateCtsActivity activity = mActivityRule.getActivity();
        testGameModeFrameRateOverride(
                activity.new DisplayModeGetRefreshRateFrameRateObserver());
    }
}
