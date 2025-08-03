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

package android.server.wm.jetpack.area;

import static android.server.wm.UiDeviceUtils.pressUnlockButton;
import static android.server.wm.UiDeviceUtils.pressWakeupButton;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.compatibility.common.util.PollingCheck.waitFor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.hardware.devicestate.DeviceStateManager;
import android.hardware.devicestate.DeviceStateRequest;
import android.os.PowerManager;
import android.platform.test.annotations.Presubmit;
import android.server.wm.DeviceStateUtils;
import android.server.wm.jetpack.extensions.util.ExtensionsUtil;
import android.server.wm.jetpack.utils.TestRearDisplayActivity;
import android.server.wm.jetpack.utils.WindowExtensionTestRule;
import android.server.wm.jetpack.utils.WindowManagerJetpackTestBase;
import android.util.DisplayMetrics;
import android.util.Pair;
import android.view.WindowMetrics;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.FlakyTest;
import androidx.test.filters.LargeTest;
import androidx.window.extensions.area.WindowAreaComponent;
import androidx.window.extensions.area.WindowAreaComponent.WindowAreaSessionState;
import androidx.window.extensions.area.WindowAreaComponent.WindowAreaStatus;
import androidx.window.extensions.core.util.function.Consumer;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.PollingCheck;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Tests for the {@link androidx.window.extensions.area.WindowAreaComponent} implementation
 * of the rear display functionality provided on the device (and only if one is available).
 *
 * Build/Install/Run:
 * atest CtsWindowManagerJetpackTestCases:ExtensionRearDisplayTest
 */
@LargeTest
@Presubmit
@RunWith(AndroidJUnit4.class)
public class ExtensionRearDisplayTest extends WindowManagerJetpackTestBase implements
        DeviceStateManager.DeviceStateCallback {

    private static final int TIMEOUT = 2000;
    private static final int INVALID_DEVICE_STATE = -1;
    private static final int INVALID_DISPLAY_ADDRESS = -1;

    private static final int CALLBACK_TYPE_WINDOW_AREA_STATUS = 1;
    private static final int CALLBACK_TYPE_WINDOW_AREA_SESSION_STATE = 2;

    private TestRearDisplayActivity mActivity;
    private WindowAreaComponent mWindowAreaComponent;
    private int mCurrentDeviceState;
    private int mCurrentDeviceBaseState;
    private int[] mSupportedDeviceStates;
    @WindowAreaStatus
    private Integer mWindowAreaStatus;
    @WindowAreaSessionState
    private Integer mWindowAreaSessionState;
    private int mRearDisplayState;
    private long mRearDisplayAddress;

    private final Context mInstrumentationContext = getInstrumentation().getTargetContext();
    private final KeyguardManager mKeyguardManager = mInstrumentationContext.getSystemService(
            KeyguardManager.class);
    private final DeviceStateManager mDeviceStateManager = mInstrumentationContext
            .getSystemService(DeviceStateManager.class);

    private final List<Pair<Integer, Integer>> mCallbackLogs = new ArrayList<>();

    private final Consumer<Integer> mStatusListener = (status) -> {
        mWindowAreaStatus = status;
        mCallbackLogs.add(new Pair<>(CALLBACK_TYPE_WINDOW_AREA_STATUS, status));
    };

    private final Consumer<Integer> mSessionStateListener = (sessionState) -> {
        mWindowAreaSessionState = sessionState;
        mCallbackLogs.add(new Pair<>(CALLBACK_TYPE_WINDOW_AREA_SESSION_STATE, sessionState));
    };

    @Rule
    public final WindowExtensionTestRule mWindowManagerJetpackTestRule =
            new WindowExtensionTestRule(WindowAreaComponent.class);

    @Before
    @Override
    public void setUp() {
        super.setUp();
        mWindowAreaComponent =
                (WindowAreaComponent) mWindowManagerJetpackTestRule.getExtensionComponent();
        mSupportedDeviceStates = mDeviceStateManager.getSupportedStates();
        assumeTrue(mSupportedDeviceStates.length > 1);
        // TODO(b/236022708) Move rear display state to device state config file
        mRearDisplayState = getInstrumentation().getTargetContext().getResources()
                .getInteger(Resources.getSystem()
                        .getIdentifier("config_deviceStateRearDisplay", "integer", "android"));
        mRearDisplayAddress = getRearDisplayAddress();
        assumeTrue(mRearDisplayState != INVALID_DEVICE_STATE);
        mDeviceStateManager.registerCallback(Runnable::run, this);
        mWindowAreaComponent.addRearDisplayStatusListener(mStatusListener);
        unlockDeviceIfNeeded();
        mActivity = startActivityNewTask(TestRearDisplayActivity.class);
        waitAndAssert(() -> mWindowAreaStatus != null);
        mCallbackLogs.clear();
    }

    @After
    @Override
    public void tearDown() throws Throwable {
        super.tearDown();
        if (mWindowAreaComponent != null) {
            mWindowAreaComponent.removeRearDisplayStatusListener(mStatusListener);
            DeviceStateUtils.runWithControlDeviceStatePermission(
                    () -> mDeviceStateManager.cancelStateRequest());
        }
    }

    /**
     * Tests that the RearDisplay status listeners receive the correct {@link WindowAreaStatus}
     * values.
     *
     * The test goes through all supported device states and verifies that the correct status is
     * returned. If the state does not place the device in the active RearDisplay configuration
     * (i.e. the base state of the device is different than the current state, and that current
     * state is the RearDisplay state), then it should receive the
     * {@link WindowAreaStatus#STATUS_AVAILABLE} value, otherwise it should receive the
     * {@link WindowAreaStatus#STATUS_ACTIVE} value.
     */
    @ApiTest(apis = {
            "androidx.window.extensions.area.WindowAreaComponent#addRearDisplayStatusListener",
            "androidx.window.extensions.area.WindowAreaComponent#removeRearDisplayStatusListener"})
    @Test
    public void testRearDisplayStatusListeners() throws Throwable {
        Set<Integer> requestedStates = new HashSet<>();
        while (requestedStates.size() != mSupportedDeviceStates.length) {
            int newState = determineNewState(mCurrentDeviceState, mSupportedDeviceStates,
                    requestedStates);
            if (newState != INVALID_DEVICE_STATE) {
                requestedStates.add(newState);
                DeviceStateRequest request = DeviceStateRequest.newBuilder(newState).build();
                DeviceStateUtils.runWithControlDeviceStatePermission(() ->
                            mDeviceStateManager.requestState(request, null, null));

                waitAndAssert(() -> mCurrentDeviceState == newState);
                // If the state does not put the device into the rear display configuration,
                // then the listener should receive the STATUS_AVAILABLE value.
                if (!isRearDisplayActive(mCurrentDeviceState, mCurrentDeviceBaseState)) {
                    waitAndAssert(
                            () -> mWindowAreaStatus == WindowAreaComponent.STATUS_AVAILABLE);
                } else {
                    waitAndAssert(
                            () -> mWindowAreaStatus == WindowAreaComponent.STATUS_ACTIVE);
                }
            }
        }
    }

    /**
     * Tests that you can start and end rear display mode. Verifies that the {@link Consumer} that
     * is provided when calling {@link WindowAreaComponent#startRearDisplaySession} receives
     * the {@link WindowAreaSessionState#SESSION_STATE_ACTIVE} value when starting the session
     * and {@link WindowAreaSessionState#SESSION_STATE_INACTIVE} when ending the session.
     *
     * This test also verifies that the {@link android.app.Activity} is still visible when rear
     * display mode is started, and that the activity received a configuration change when enabling
     * and disabling rear display mode. This is verifiable due to the current generation of
     * hardware and the fact that there are different screen sizes from the different displays.
     */
    @ApiTest(apis = {
            "androidx.window.extensions.area.WindowAreaComponent#startRearDisplaySession",
            "androidx.window.extensions.area.WindowAreaComponent#endRearDisplaySession"})
    @Test
    public void testStartAndEndRearDisplaySession() throws Throwable {
        assumeTrue(mWindowAreaStatus == WindowAreaComponent.STATUS_AVAILABLE);
        assumeTrue(mCurrentDeviceState != mRearDisplayState);

        // Get initial window metrics to determine if the activity is moved, it's returned
        // back to the initial configuration when feature is ended.
        WindowMetrics initialWindowMetrics = mActivity.getWindowManager().getCurrentWindowMetrics();

        // Running with CONTROL_DEVICE_STATE permission to bypass educational overlay
        DeviceStateUtils.runWithControlDeviceStatePermission(() ->
                mWindowAreaComponent.startRearDisplaySession(mActivity, mSessionStateListener));

        waitAndAssert(() -> isActivityVisible(mActivity));
        waitAndAssert(() -> mWindowAreaSessionState != null
                && mWindowAreaSessionState == WindowAreaComponent.SESSION_STATE_ACTIVE);
        assertEquals(mCurrentDeviceState, mRearDisplayState);
        assertEquals(WindowAreaComponent.STATUS_ACTIVE, (int) mWindowAreaStatus);

        WindowMetrics rearDisplayWindowMetrics =
                mActivity.getWindowManager().getCurrentWindowMetrics();

        if (!rearDisplayWindowMetrics.getBounds().equals(initialWindowMetrics.getBounds())) {
            assertTrue(mActivity.mConfigurationChanged);
        }
        resetActivityConfigurationChangeValues(mActivity);

        DeviceStateUtils.runWithControlDeviceStatePermission(() ->
                mWindowAreaComponent.endRearDisplaySession());


        waitAndAssert(() -> mWindowAreaStatus == WindowAreaComponent.STATUS_AVAILABLE);
        waitAndAssert(() -> initialWindowMetrics.getBounds().equals(
                mActivity.getWindowManager().getCurrentWindowMetrics().getBounds()));
        // Cancelling rear display mode should cancel the override, so verifying that the
        // device state is the same as the physical state of the device.
        assertEquals(mCurrentDeviceState, mCurrentDeviceBaseState);
        assertEquals(WindowAreaComponent.SESSION_STATE_INACTIVE, (int) mWindowAreaSessionState);

        // If the rear display window metrics did not match the initial window metrics, verifying
        // that the Activity has gone through a configuration change when the feature was disabled.
        if (!rearDisplayWindowMetrics.getBounds().equals(initialWindowMetrics.getBounds())) {
            waitAndAssert(() -> mActivity.mConfigurationChanged);
        }
        assertTrue(isActivityVisible(mActivity));

        verifyCallbacks();
    }

    /**
     * Tests that attempting to end a rear display session that isn't active, operates as a no-op
     * instead of throwing an {@link Exception}.
     */
    @ApiTest(apis = {
            "androidx.window.extensions.area.WindowAreaComponent#endRearDisplaySession"})
    @Test
    public void testEndRearDisplaySession_noActiveSession() {
        assumeTrue(mWindowAreaStatus != WindowAreaComponent.STATUS_ACTIVE);
        assumeTrue(mCurrentDeviceState != mRearDisplayState);

        mWindowAreaComponent.endRearDisplaySession();
    }

    /**
     * Tests that the {@link DisplayMetrics} returned by
     * {@link WindowAreaComponent#getRearDisplayMetrics} is non-null and matches the expected
     * metrics pertaining to the rear display address.
     */
    @ApiTest(apis = {
            "androidx.window.extensions.area.WindowAreaComponent#getRearDisplayMetrics"})
    @Test
    @FlakyTest(bugId = 295869141)
    public void testGetRearDisplayMetrics() throws Throwable {
        ExtensionsUtil.assumeVendorApiLevelAtLeast(3 /* vendorApiLevel */);
        assumeTrue(mRearDisplayAddress != INVALID_DISPLAY_ADDRESS);

        DisplayMetrics originalMetrics = mWindowAreaComponent.getRearDisplayMetrics();

        // Enable rear display mode to get the expected display metrics for the rear display
        // Running with CONTROL_DEVICE_STATE permission to bypass educational overlay
        DeviceStateUtils.runWithControlDeviceStatePermission(() ->
                mWindowAreaComponent.startRearDisplaySession(mActivity, mSessionStateListener));

        // Verify that the new display metrics of the activity match the expected rear display.
        // If the activity needed to change displays or go through a configuration change, there is
        // some time before the new display metrics match.
        DisplayMetrics expectedMetrics = new DisplayMetrics();
        waitAndAssert(() -> {
            mActivity.getDisplay().getRealMetrics(expectedMetrics);
            return expectedMetrics.equals(originalMetrics);
        });

        waitAndAssert(() -> mCurrentDeviceState == mRearDisplayState);

        mActivity.getDisplay().getRealMetrics(expectedMetrics);
        DeviceStateUtils.runWithControlDeviceStatePermission(() ->
                mWindowAreaComponent.endRearDisplaySession());
        waitAndAssert(() -> mCurrentDeviceState == mCurrentDeviceBaseState);

        DisplayMetrics actualMetrics = mWindowAreaComponent.getRearDisplayMetrics();

        assertNotNull(actualMetrics);
        assertEquals(expectedMetrics, actualMetrics);

        // Verifying that the metrics are the same before and after entering the feature.
        assertEquals(originalMetrics, actualMetrics);
    }

    /**
     * Tests that the {@link DisplayMetrics} returned by
     * {@link WindowAreaComponent#getRearDisplayMetrics} is non-null
     */
    @ApiTest(apis = {
            "androidx.window.extensions.area.WindowAreaComponent#getRearDisplayMetrics"})
    @Test
    @FlakyTest(bugId = 295869141)
    public void testGetRearDisplayMetrics_afterRotation() throws Throwable {
        ExtensionsUtil.assumeVendorApiLevelAtLeast(3 /* vendorApiLevel */);
        assumeTrue(mRearDisplayAddress != INVALID_DISPLAY_ADDRESS);
        DisplayMetrics originalMetricsApi = mWindowAreaComponent.getRearDisplayMetrics();
        assertNotNull(originalMetricsApi);

        DisplayMetrics currentMetrics = new DisplayMetrics();
        // Enable rear display mode to get the expected display metrics for the rear display
        // Running with CONTROL_DEVICE_STATE permission to bypass educational overlay
        DeviceStateUtils.runWithControlDeviceStatePermission(() ->
                mWindowAreaComponent.startRearDisplaySession(mActivity, mSessionStateListener));

        // Verify that the activity is on the rear display by matching the display metrics with what
        // was returned in the API. This isn't an immediate operation as the activity may have had
        // to switch displays.
        waitAndAssert(() -> {
            mActivity.getDisplay().getRealMetrics(currentMetrics);
            return originalMetricsApi.equals(currentMetrics);
        });
        waitAndAssert(() -> mCurrentDeviceState == mRearDisplayState);
        assertTrue(isActivityVisible(mActivity));

        WindowMetrics windowMetrics = mActivity.getWindowManager().getCurrentWindowMetrics();

        int newOrientation;
        if (mActivity.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_PORTRAIT) {
            newOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
            mActivity.setRequestedOrientation(newOrientation);
        } else {
            newOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
            mActivity.setRequestedOrientation(newOrientation);
        }
        waitAndAssert(() -> mActivity.getRequestedOrientation() == newOrientation);

        WindowMetrics postRotationWindowMetrics =
                mActivity.getWindowManager().getCurrentWindowMetrics();

        DisplayMetrics postRotationMetricsApi = mWindowAreaComponent.getRearDisplayMetrics();
        assertNotNull(postRotationMetricsApi);

        // Verify that the metrics returned from the activity do not equal after rotation
        assertNotEquals(windowMetrics, postRotationWindowMetrics);
        assertNotEquals(originalMetricsApi, postRotationMetricsApi);
    }

    /**
     * Tests that the {@link DisplayMetrics} returned by
     * {@link WindowAreaComponent#getRearDisplayMetrics} is non-null and empty for an empty or
     * incorrectly formatted rear display address.
     */
    @ApiTest(apis = {
            "androidx.window.extensions.area.WindowAreaComponent#getRearDisplayMetrics"})
    @Test
    public void testGetRearDisplayMetrics_invalidRearDisplayAddress() {
        ExtensionsUtil.assumeVendorApiLevelAtLeast(3 /* vendorApiLevel */);
        assumeTrue(mRearDisplayAddress == INVALID_DISPLAY_ADDRESS);
        DisplayMetrics expectedDisplayMetrics = new DisplayMetrics();
        DisplayMetrics actualDisplayMetrics = mWindowAreaComponent.getRearDisplayMetrics();
        assertEquals(expectedDisplayMetrics, actualDisplayMetrics);
    }

    @Override
    public void onBaseStateChanged(int state) {
        mCurrentDeviceBaseState = state;
    }

    @Override
    public void onStateChanged(int state) {
        mCurrentDeviceState = state;
    }

    /**
     * Returns the next state that we should request that isn't the current state and
     * has not already been requested.
     */
    private int determineNewState(int currentDeviceState, int[] statesToRequest,
            Set<Integer> requestedStates) {
        for (int state : statesToRequest) {
            if (state != currentDeviceState && !requestedStates.contains(state)) {
                return state;
            }
        }
        return INVALID_DEVICE_STATE;
    }

    /**
     * Helper method to determine if a rear display session is currently active by checking
     * if the current device configuration matches that of rear display. This would be true
     * if there is a device override currently active (base state != current state) and the current
     * state is that which corresponds to {@code mRearDisplayState}
     * @return {@code true} if the device is in rear display mode and {@code false} if not
     */
    private boolean isRearDisplayActive(int currentDeviceState, int currentDeviceBaseState) {
        return (currentDeviceState != currentDeviceBaseState)
                && (currentDeviceState == mRearDisplayState);
    }

    private void resetActivityConfigurationChangeValues(@NonNull TestRearDisplayActivity activity) {
        activity.mConfigurationChanged = false;
    }

    private long getRearDisplayAddress() {
        String address = getInstrumentation().getTargetContext().getResources().getString(
                Resources.getSystem().getIdentifier("config_rearDisplayPhysicalAddress", "string",
                        "android"));
        return address.isEmpty() ? INVALID_DISPLAY_ADDRESS : Long.parseLong(address);
    }

    private void unlockDeviceIfNeeded() {
        if (isKeyguardLocked() || !Objects.requireNonNull(
                mInstrumentationContext.getSystemService(PowerManager.class)).isInteractive()) {
            pressWakeupButton();
            pressUnlockButton();
        }
    }

    private void waitAndAssert(PollingCheck.PollingCheckCondition condition) {
        waitFor(TIMEOUT, condition);
    }

    /**
     * Verifies the order of window area status callbacks and window area session state callbacks.
     *
     * Currently, only checks that the STATUS_ACTIVE window area status callback should never happen
     * after SESSION_STATE_INACTIVE window area session callback.
     */
    private void verifyCallbacks() {
        boolean sessionEnded = false;
        for (Pair<Integer, Integer> callback : mCallbackLogs) {
            if (callback.first == CALLBACK_TYPE_WINDOW_AREA_SESSION_STATE) {
                if (callback.second == WindowAreaComponent.SESSION_STATE_INACTIVE) {
                    sessionEnded = true;
                }
            }
            if (callback.first == CALLBACK_TYPE_WINDOW_AREA_STATUS) {
                if (sessionEnded && callback.second == WindowAreaComponent.STATUS_ACTIVE) {
                    throw new IllegalStateException(
                            "STATUS_ACTIVE should not happen after SESSION_STATE_INACTIVE");
                }
            }
        }
    }
}
