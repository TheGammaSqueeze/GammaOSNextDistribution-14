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

import android.Manifest;
import android.app.compat.CompatChanges;
import android.graphics.cts.FrameRateOverrideCtsActivity.FrameRateObserver;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemProperties;
import android.support.test.uiautomator.UiDevice;
import android.sysprop.SurfaceFlingerProperties;
import android.util.Log;
import android.view.Display;
import android.view.Window;
import android.view.WindowManager;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.MediumTest;
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
@MediumTest
@RunWith(AndroidJUnit4.class)
public final class FrameRateOverrideTest {
    private static final String TAG = "FrameRateOverrideTest";
    // See b/170503758 for more details
    private static final long DISPLAY_MODE_RETURNS_PHYSICAL_REFRESH_RATE_CHANGEID = 170503758;

    // The tolerance within which we consider refresh rates are equal
    private static final float REFRESH_RATE_TOLERANCE = 0.01f;

    // Needs to be in sync with RefreshRateSelector::kMinSupportedFrameRate
    private static final float MIN_SUPPORTED_FRAME_RATE_HZ = 20.0f;

    private int mInitialMatchContentFrameRate;
    private DisplayManager mDisplayManager;
    private UiDevice mUiDevice;
    private final Handler mHandler = new Handler(Looper.getMainLooper());


    @Rule
    public ActivityTestRule<FrameRateOverrideCtsActivity> mActivityRule =
            new ActivityTestRule<>(FrameRateOverrideCtsActivity.class);

    @Before
    public void setUp() throws Exception {
        mUiDevice = UiDevice.getInstance(
                        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation());
        mUiDevice.wakeUp();
        mUiDevice.executeShellCommand("wm dismiss-keyguard");

        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(
                        Manifest.permission.MODIFY_REFRESH_RATE_SWITCHING_TYPE,
                        Manifest.permission.OVERRIDE_DISPLAY_MODE_REQUESTS);

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
    public void tearDown() {
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

                // Do not add refresh rates that are too low as those will be discarded by SF
                if (mode.getRefreshRate() / 2
                        < MIN_SUPPORTED_FRAME_RATE_HZ + REFRESH_RATE_TOLERANCE) {
                    continue;
                }
                modesWithSameResolution.add(mode);
            }
        }

        return modesWithSameResolution;
    }

    // Use WindowManager.LayoutParams#preferredMinDisplayRefreshRate and
    // WindowManager.LayoutParams#preferredMaxDisplayRefreshRateto set the frame rate override.
    // This would be communicated to SF by DM setting the render frame rate policy to a single
    // value, which is the preferred refresh rate.
    private void testGlobalFrameRateOverride(FrameRateObserver frameRateObserver)
            throws InterruptedException, IOException {
        FrameRateOverrideCtsActivity activity = mActivityRule.getActivity();
        for (Display.Mode mode : getModesToTest()) {
            setMode(mode);
            activity.testFrameRateOverride(
                    activity.new PreferredRefreshRateFrameTest(),
                    frameRateObserver, mode.getRefreshRate());
            Log.i(TAG, "\n");
        }
        Log.i(TAG, "\n");
    }

    // Use WindowManager.LayoutParams#preferredDisplayModeId to lock the physical display refresh
    // rate. This would be communicated to SF by DM setting the physical refresh rate frame rate
    // a single value, which is the refresh rate of the preferredDisplayModeId, while still
    // allowing render rate switching. The test will use Surface#setFrameRate to tell SF the
    // preferred render rate and to enable the frame rate override for the test app.
    private void testAppFrameRateOverride(FrameRateObserver frameRateObserver)
            throws InterruptedException, IOException {
        FrameRateOverrideCtsActivity activity = mActivityRule.getActivity();
        for (Display.Mode mode : getModesToTest()) {
            setMode(mode);
            activity.testFrameRateOverride(activity.new SurfaceSetFrameRateTest(),
                    frameRateObserver, mode.getRefreshRate());
            Log.i(TAG, "\n");
        }
        Log.i(TAG, "\n");
    }

    @Test
    public void testAppBackpressure()
            throws InterruptedException, IOException {
        if (isTvEmulator()) {
            Log.i(TAG, "**** Skipping Backpressure ****");
            return;
        }

        Log.i(TAG, "**** Starting Backpressure Test ****");
        FrameRateOverrideCtsActivity activity = mActivityRule.getActivity();
        testAppFrameRateOverride(activity.new BackpressureFrameRateObserver());
    }

    @Test
    public void testAppChoreographer()
            throws InterruptedException, IOException {
        Log.i(TAG, "**** Starting App Override Choreographer Test ****");
        FrameRateOverrideCtsActivity activity = mActivityRule.getActivity();
        testAppFrameRateOverride(activity.new ChoreographerFrameRateObserver());
    }

    @Test
    public void testAppDisplayGetRefreshRate()
            throws InterruptedException, IOException {
        Log.i(TAG, "**** Starting App Override Display#getRefreshRate Test ****");
        FrameRateOverrideCtsActivity activity = mActivityRule.getActivity();
        testAppFrameRateOverride(activity.new DisplayGetRefreshRateFrameRateObserver());
    }

    @Test
    public void testAppDisplayModeGetRefreshRateDisplayModeReturnsPhysicalRefreshRateEnabled()
            throws InterruptedException, IOException {
        Log.i(TAG, "**** Starting App Override Display.Mode#getRefreshRate Test ****");
        FrameRateOverrideCtsActivity activity = mActivityRule.getActivity();
        testAppFrameRateOverride(
                activity.new DisplayModeGetRefreshRateFrameRateObserver());
    }

    @Test
    public void testGlobalBackpressure()
            throws InterruptedException, IOException {
        if (isTvEmulator()) {
            Log.i(TAG, "**** Skipping Backpressure ****");
            return;
        }

        Log.i(TAG, "**** Starting Global Override Backpressure Test ****");
        FrameRateOverrideCtsActivity activity = mActivityRule.getActivity();
        testGlobalFrameRateOverride(activity.new BackpressureFrameRateObserver());
    }

    @Test
    public void testGlobalChoreographer()
            throws InterruptedException, IOException {
        Log.i(TAG, "**** Starting Global Override Choreographer Test ****");
        FrameRateOverrideCtsActivity activity = mActivityRule.getActivity();
        testGlobalFrameRateOverride(activity.new ChoreographerFrameRateObserver());
    }

    @Test
    public void testGlobalDisplayGetRefreshRate()
            throws InterruptedException, IOException {
        Log.i(TAG, "**** Starting Global Override Display#getRefreshRate Test ****");
        FrameRateOverrideCtsActivity activity = mActivityRule.getActivity();
        testGlobalFrameRateOverride(activity.new DisplayGetRefreshRateFrameRateObserver());
    }

    @Test
    public void testGlobalDisplayModeGetRefreshRateDisplayModeReturnsPhysicalRefreshRateEnabled()
            throws InterruptedException, IOException {
        Log.i(TAG, "**** Starting Global Override Display.Mode#getRefreshRate Test ****");
        FrameRateOverrideCtsActivity activity = mActivityRule.getActivity();
        testGlobalFrameRateOverride(
                activity.new DisplayModeGetRefreshRateFrameRateObserver());
    }
}
