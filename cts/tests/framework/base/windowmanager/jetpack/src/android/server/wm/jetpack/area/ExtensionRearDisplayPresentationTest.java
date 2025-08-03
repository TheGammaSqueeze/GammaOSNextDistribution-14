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

import static android.server.wm.UiDeviceUtils.pressHomeButton;
import static android.server.wm.UiDeviceUtils.pressSleepButton;
import static android.server.wm.UiDeviceUtils.pressUnlockButton;
import static android.server.wm.UiDeviceUtils.pressWakeupButton;
import static android.view.Display.DEFAULT_DISPLAY;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static androidx.window.extensions.area.WindowAreaComponent.SESSION_STATE_ACTIVE;
import static androidx.window.extensions.area.WindowAreaComponent.SESSION_STATE_CONTENT_VISIBLE;
import static androidx.window.extensions.area.WindowAreaComponent.SESSION_STATE_INACTIVE;

import static com.android.compatibility.common.util.PollingCheck.waitFor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.hardware.devicestate.DeviceStateManager;
import android.hardware.devicestate.DeviceStateRequest;
import android.hardware.display.DisplayManager;
import android.os.PowerManager;
import android.platform.test.annotations.Presubmit;
import android.server.wm.DeviceStateUtils;
import android.server.wm.jetpack.utils.TestActivity;
import android.server.wm.jetpack.utils.TestActivityLauncher;
import android.server.wm.jetpack.utils.TestRearDisplayActivity;
import android.server.wm.jetpack.utils.TestRearDisplayCrashingActivity;
import android.server.wm.jetpack.utils.WindowExtensionTestRule;
import android.server.wm.jetpack.utils.WindowManagerJetpackTestBase;
import android.view.Display;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.window.extensions.area.ExtensionWindowAreaPresentation;
import androidx.window.extensions.area.ExtensionWindowAreaStatus;
import androidx.window.extensions.area.WindowAreaComponent;
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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Tests for the {@link androidx.window.extensions.area.WindowAreaComponent} implementation
 * of the rear display functionality provided on the device (and only if one is available).
 *
 * Build/Install/Run:
 * atest CtsWindowManagerJetpackTestCases:ExtensionRearDisplayPresentationTest
 */
@LargeTest
@Presubmit
@RunWith(AndroidJUnit4.class)
public class ExtensionRearDisplayPresentationTest extends WindowManagerJetpackTestBase implements
        DeviceStateManager.DeviceStateCallback {

    private static final int TIMEOUT = 2000;
    private static final int INVALID_DEVICE_STATE = -1;

    private static final List<@WindowAreaComponent.WindowAreaSessionState Integer>
            SESSION_LIFECYCLE_VALUES = new ArrayList<>(
            Arrays.asList(SESSION_STATE_ACTIVE, SESSION_STATE_CONTENT_VISIBLE,
                    SESSION_STATE_ACTIVE, SESSION_STATE_INACTIVE));

    private TestRearDisplayActivity mActivity;
    private int[] mFoldedDeviceStates;
    private WindowAreaComponent mWindowAreaComponent;
    private int mCurrentDeviceState;
    private int mCurrentDeviceBaseState;
    private int[] mSupportedDeviceStates;
    private ExtensionWindowAreaStatus mWindowAreaPresentationStatus;

    @WindowAreaComponent.WindowAreaSessionState
    private int mWindowAreaSessionState;
    private int mRearDisplayPresentationState;

    private List<Integer> mSessionStateStatusValues;

    private final Context mInstrumentationContext = getInstrumentation().getTargetContext();
    private final DeviceStateManager mDeviceStateManager = mInstrumentationContext
            .getSystemService(DeviceStateManager.class);
    private final DisplayManager mDisplayManager = mInstrumentationContext
            .getSystemService(DisplayManager.class);
    private final ActivityManager mActivityManager = mInstrumentationContext
            .getSystemService(ActivityManager.class);

    private final Consumer<ExtensionWindowAreaStatus> mStatusListener =
            (status) -> mWindowAreaPresentationStatus = status;

    private final Consumer<@WindowAreaComponent.WindowAreaSessionState Integer>
            mSessionStateListener = (sessionStatus) -> {
                mSessionStateStatusValues.add(sessionStatus);
                mWindowAreaSessionState = sessionStatus;
            };

    @Rule
    public final WindowExtensionTestRule mWindowManagerJetpackTestRule =
            new WindowExtensionTestRule(WindowAreaComponent.class);

    @Before
    @Override
    public void setUp() {
        super.setUp();
        mSessionStateStatusValues = new ArrayList<>();
        mSupportedDeviceStates = mDeviceStateManager.getSupportedStates();
        assumeTrue(mSupportedDeviceStates.length > 1);
        mFoldedDeviceStates = getInstrumentation().getTargetContext().getResources().getIntArray(
                Resources.getSystem().getIdentifier("config_foldedDeviceStates", "array",
                        "android"));
        assumeTrue(mFoldedDeviceStates.length > 0);
        // TODO(b/236022708) Move rear display presentation state to device state config file
        mRearDisplayPresentationState = getInstrumentation().getTargetContext().getResources()
                .getInteger(Resources.getSystem().getIdentifier(
                        "config_deviceStateConcurrentRearDisplay", "integer", "android"));
        assumeTrue(mRearDisplayPresentationState != INVALID_DEVICE_STATE);
        assumeTrue(containsValue(mSupportedDeviceStates, mRearDisplayPresentationState));
        String rearDisplayAddress = getInstrumentation().getTargetContext().getResources()
                .getString(Resources.getSystem().getIdentifier(
                        "config_rearDisplayPhysicalAddress", "string", "android"));
        assumeTrue(rearDisplayAddress != null && !rearDisplayAddress.isEmpty());
        mDeviceStateManager.registerCallback(Runnable::run, this);
        mWindowAreaComponent =
                (WindowAreaComponent) mWindowManagerJetpackTestRule.getExtensionComponent();
        mWindowAreaComponent.addRearDisplayPresentationStatusListener(mStatusListener);
        unlockDeviceIfNeeded();
        mActivity = startActivityNewTask(TestRearDisplayActivity.class);
        waitAndAssert(() -> mWindowAreaPresentationStatus != null);
    }

    @After
    @Override
    public void tearDown() throws Throwable {
        super.tearDown();
        mDeviceStateManager.unregisterCallback(this);
        if (mWindowAreaComponent != null) {
            mWindowAreaComponent.removeRearDisplayPresentationStatusListener(mStatusListener);
                DeviceStateUtils.runWithControlDeviceStatePermission(
                        mDeviceStateManager::cancelStateRequest);
                DeviceStateUtils.runWithControlDeviceStatePermission(
                        mDeviceStateManager::cancelBaseStateOverride);
        }
    }

    /**
     * Tests that the RearDisplayPresentation status listeners receive the correct
     * {@link WindowAreaStatus} values.
     *
     * The test goes through all supported device states and verifies that the correct status is
     * returned. If the state does not place the device in the active RearDisplayPresentation state
     * and the state is not marked as `folded`, then it should receive the
     * {@link WindowAreaStatus#STATUS_AVAILABLE} value, otherwise it should receive the
     * {@link WindowAreaStatus#STATUS_UNAVAILABLE} value.
     */
    @ApiTest(apis = {
            "androidx.window.extensions.area."
                    + "WindowAreaComponent#addRearDisplayPresentationStatusListener",
            "androidx.window.extensions.area."
                    + "WindowAreaComponent#removeRearDisplayPresentationStatusListener"})
    @Test
    public void testRearDisplayPresentationStatusListeners() throws Throwable {
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

                // If the state does not put the device into the rear display presentation state,
                // and the state is not one where the device is folded, the status should be
                // available.
                if (mCurrentDeviceState == mRearDisplayPresentationState) {
                    waitAndAssert(() -> mWindowAreaPresentationStatus.getWindowAreaStatus()
                            == WindowAreaComponent.STATUS_ACTIVE);
                } else if (containsValue(mFoldedDeviceStates, mCurrentDeviceState)) {
                    waitAndAssert(() -> mWindowAreaPresentationStatus.getWindowAreaStatus()
                            == WindowAreaComponent.STATUS_UNAVAILABLE);
                } else {
                    waitAndAssert(() -> mWindowAreaPresentationStatus.getWindowAreaStatus()
                            == WindowAreaComponent.STATUS_AVAILABLE);
                }
            }
        }
    }

    /**
     * Tests that you can start and end rear display presentation mode with
     * {@link WindowAreaComponent#endRearDisplayPresentationSession()}. Verifies that the
     * {@link Consumer} that is provided when calling
     * {@link WindowAreaComponent#startRearDisplayPresentationSession} receives the
     * {@link WindowAreaComponent#SESSION_STATE_ACTIVE} when starting the session and
     * {@link WindowAreaComponent#SESSION_STATE_INACTIVE} when calling
     * {@link WindowAreaComponent#endRearDisplayPresentationSession()}.
     *
     * This test also verifies that the {@link TestPresentationView} was attached to the window
     * signifying that the presentation was created as expected, and then removed and detached as
     * expected.
     */
    @ApiTest(apis = {
            "androidx.window.extensions.area."
                    + "WindowAreaComponent#startRearDisplayPresentationSession",
            "androidx.window.extensions.area."
                    + "WindowAreaComponent#endRearDisplayPresentationSession"})
    @Test
    public void testStartAndEndRearDisplayPresentationSession() throws Throwable {
        assumeTrue(mWindowAreaPresentationStatus.getWindowAreaStatus()
                == WindowAreaComponent.STATUS_AVAILABLE);
        assumeTrue(mCurrentDeviceState != mRearDisplayPresentationState);

        // Rear displays should only exist after concurrent mode is started
        assertEquals(0, mDisplayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_REAR).length);

        mWindowAreaComponent.startRearDisplayPresentationSession(mActivity,
                mSessionStateListener);
        waitAndAssert(() -> mWindowAreaSessionState == SESSION_STATE_ACTIVE);
        assertEquals(mCurrentDeviceState, mRearDisplayPresentationState);
        assertTrue(mDisplayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_REAR).length > 0);

        ExtensionWindowAreaPresentation presentation =
                mWindowAreaComponent.getRearDisplayPresentation();
        assertNotNull(presentation);
        assertNotNull(presentation.getWindow());
        TestPresentationView presentationView = new TestPresentationView(
                presentation.getPresentationContext());
        mActivity.runOnUiThread(() -> presentation.setPresentationView(presentationView));
        waitAndAssert(() -> presentationView.mAttachedToWindow);
        assertNotEquals(presentationView.getDisplay().getDisplayId(), DEFAULT_DISPLAY);
        assertTrue(presentationView.getDisplay().getState() != Display.STATE_OFF);
        assertEquals(mWindowAreaSessionState, SESSION_STATE_CONTENT_VISIBLE);

        mWindowAreaComponent.endRearDisplayPresentationSession();
        waitAndAssert(() -> !presentationView.mAttachedToWindow);
        // Cancelling rear display presentation mode should cancel the override, so verifying that
        // the device state is the same as the physical state of the device.
        assertEquals(mCurrentDeviceState, mCurrentDeviceBaseState);
        assertEquals(WindowAreaComponent.STATUS_AVAILABLE,
                (int) mWindowAreaPresentationStatus.getWindowAreaStatus());
        // Since the non-visible and session ended callbacks happen so fast, we check if
        // the list of values received equal what we expected.
        assertEquals(mSessionStateStatusValues, SESSION_LIFECYCLE_VALUES);
    }

    /**
     * Tests that attempting to end a rear display presentation session that isn't active, operates
     * as a no-op instead of throwing an {@link Exception}.
     */
    @ApiTest(apis = {
            "androidx.window.extensions.area."
                    + "WindowAreaComponent#endRearDisplayPresentationSession"})
    @Test
    public void testEndRearDisplayPresentationSession_noActiveSession() {
        assumeTrue(mWindowAreaPresentationStatus.getWindowAreaStatus()
                != WindowAreaComponent.STATUS_ACTIVE);
        assumeTrue(mCurrentDeviceState != mRearDisplayPresentationState);

        mWindowAreaComponent.endRearDisplayPresentationSession();
    }

    /**
     * Tests that you can start, and then end rear display presentation mode by backgrounding the
     * calling application. Verifies that the {@link ExtensionWindowAreaPresentationSessionCallback}
     * that is provided when calling {@link WindowAreaComponent#startRearDisplayPresentationSession}
     * receives the {@link ExtensionWindowAreaPresentationSessionCallback#onSessionStarted} callback
     * when starting the session and
     * {@link ExtensionWindowAreaPresentationSessionCallback#onSessionEnded()} when backgrounding
     * the application.
     *
     * This test also verifies that the {@link TestPresentationView} was attached to the window
     * signifying that the presentation was created as expected, and then removed and detached as
     * expected.
     */
    @ApiTest(apis = {
            "androidx.window.extensions.area."
                    + "WindowAreaComponent#startRearDisplayPresentationSession",
            "androidx.window.extensions.area."
                    + "WindowAreaComponent#endRearDisplayPresentationSession"})
    @Test
    public void testStartAndEndRearDisplayPresentationSession_backgroundApp() throws Throwable {
        assumeTrue(mWindowAreaPresentationStatus.getWindowAreaStatus()
                == WindowAreaComponent.STATUS_AVAILABLE);
        assumeTrue(mCurrentDeviceState != mRearDisplayPresentationState);

        mWindowAreaComponent.startRearDisplayPresentationSession(mActivity,
                mSessionStateListener);
        waitAndAssert(() -> SESSION_STATE_ACTIVE == mWindowAreaSessionState);
        waitAndAssert(() -> mCurrentDeviceState == mRearDisplayPresentationState);

        ExtensionWindowAreaPresentation presentation =
                mWindowAreaComponent.getRearDisplayPresentation();
        assertNotNull(presentation);
        assertNotNull(presentation.getWindow());
        TestPresentationView presentationView = new TestPresentationView(
                presentation.getPresentationContext());
        mActivity.runOnUiThread(() -> presentation.setPresentationView(presentationView));
        waitAndAssert(() -> presentationView.mAttachedToWindow);
        waitAndAssert(() -> presentationView.getDisplay().getState() != Display.STATE_OFF);
        assertNotEquals(presentationView.getDisplay().getDisplayId(), DEFAULT_DISPLAY);
        assertEquals(mWindowAreaSessionState, SESSION_STATE_CONTENT_VISIBLE);

        pressHomeButton();
        waitAndAssert(() -> !presentationView.mAttachedToWindow);
        // Cancelling rear display presentation mode should cancel the override, so verifying that
        // the device state is the same as the physical state of the device.
        assertEquals(mCurrentDeviceState, mCurrentDeviceBaseState);
        assertEquals(WindowAreaComponent.STATUS_AVAILABLE,
                (int) mWindowAreaPresentationStatus.getWindowAreaStatus());
        // Since the non-visible and session ended callbacks happen so fast, we check if
        // the list of values received equal what we expected.
        assertEquals(mSessionStateStatusValues, SESSION_LIFECYCLE_VALUES);
    }

    /**
     * Tests that you can start, and then end rear display presentation mode by locking the device.
     * Verifies that the {@link ExtensionWindowAreaPresentationSessionCallback} that is
     * provided when calling {@link WindowAreaComponent#startRearDisplayPresentationSession}
     * receives the {@link ExtensionWindowAreaPresentationSessionCallback#onSessionStarted} callback
     * when starting the session and
     * {@link ExtensionWindowAreaPresentationSessionCallback#onSessionEnded()} when locking the
     * device.
     *
     * This test also verifies that the {@link TestPresentationView} was attached to the window
     * signifying that the presentation was created as expected, and then removed and detached as
     * expected.
     */
    @ApiTest(apis = {
            "androidx.window.extensions.area."
                    + "WindowAreaComponent#startRearDisplayPresentationSession",
            "androidx.window.extensions.area."
                    + "WindowAreaComponent#endRearDisplayPresentationSession"})
    @Test
    public void testStartAndEndRearDisplayPresentationSession_lockDevice() throws Throwable {
        assumeTrue(mWindowAreaPresentationStatus.getWindowAreaStatus()
                == WindowAreaComponent.STATUS_AVAILABLE);
        assumeTrue(mCurrentDeviceState != mRearDisplayPresentationState);

        mWindowAreaComponent.startRearDisplayPresentationSession(mActivity,
                mSessionStateListener);
        waitAndAssert(() -> mWindowAreaSessionState == SESSION_STATE_ACTIVE);
        assertEquals(mCurrentDeviceState, mRearDisplayPresentationState);

        ExtensionWindowAreaPresentation presentation =
                mWindowAreaComponent.getRearDisplayPresentation();
        assertNotNull(presentation);
        assertNotNull(presentation.getWindow());
        TestPresentationView presentationView = new TestPresentationView(
                presentation.getPresentationContext());
        mActivity.runOnUiThread(() -> presentation.setPresentationView(presentationView));
        waitAndAssert(() -> presentationView.mAttachedToWindow);
        assertNotEquals(presentationView.getDisplay().getDisplayId(), DEFAULT_DISPLAY);
        assertTrue(presentationView.getDisplay().getState() != Display.STATE_OFF);
        assertEquals(mWindowAreaSessionState, SESSION_STATE_CONTENT_VISIBLE);

        pressSleepButton();
        waitAndAssert(() -> !presentationView.mAttachedToWindow);
        // Cancelling rear display presentation mode should cancel the override, so verifying that
        // the device state is the same as the physical state of the device.
        assertEquals(mCurrentDeviceState, mCurrentDeviceBaseState);
        assertEquals(WindowAreaComponent.STATUS_AVAILABLE,
                (int) mWindowAreaPresentationStatus.getWindowAreaStatus());
        // Since the non-visible and session ended callbacks happen so fast, we check if
        // the list of values received equal what we expected.
        assertEquals(mSessionStateStatusValues, SESSION_LIFECYCLE_VALUES);
    }

    /**
     * Tests that you can start, and then end rear display presentation mode when the process dies.
     * Verifies that the device moves into the extension rear display presentation device state, and
     * then moves back to the base state of the device when the process that started the feature
     * dies.
     */
    @ApiTest(apis = {
            "androidx.window.extensions.area."
                    + "WindowAreaComponent#startRearDisplayPresentationSession"})
    @Test
    public void testStartAndEndRearDisplayPresentationSession_processDies() {
        assumeTrue(mWindowAreaPresentationStatus.getWindowAreaStatus()
                == WindowAreaComponent.STATUS_AVAILABLE);
        assumeTrue(mCurrentDeviceState != mRearDisplayPresentationState);

        Intent intent = new Intent(mActivity, TestRearDisplayCrashingActivity.class);
        mActivity.startActivity(intent);

        waitAndAssert(() ->  mCurrentDeviceState == mRearDisplayPresentationState);

        // The crashing activity that we launched will crash after the rear display presentation
        // feature has been enabled, so the device should transition back to the base state after
        // the process crashes.
        waitAndAssert(() -> mCurrentDeviceState == mCurrentDeviceBaseState);
    }

    @ApiTest(apis = {
            "androidx.window.extensions.area."
                    + "WindowAreaComponent#startRearDisplayPresentationSession",
            "androidx.window.extensions.area."
                    + "WindowAreaComponent#endRearDisplayPresentationSession"})
    @Test (expected = SecurityException.class)
    public void testStartActivityOnRearDisplay_whileRearPresentationSessionStarted()
            throws Throwable {
        assumeTrue(mWindowAreaPresentationStatus.getWindowAreaStatus()
                == WindowAreaComponent.STATUS_AVAILABLE);
        assumeTrue(mCurrentDeviceState != mRearDisplayPresentationState);

        mWindowAreaComponent.startRearDisplayPresentationSession(mActivity,
                mSessionStateListener);
        waitAndAssert(() -> mWindowAreaSessionState == SESSION_STATE_ACTIVE);
        assertEquals(mCurrentDeviceState, mRearDisplayPresentationState);

        Display[] rearDisplays = mDisplayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_REAR);
        assertTrue(rearDisplays.length > 0);

        final int rearDisplayId = rearDisplays[0].getDisplayId();

        final TestActivityLauncher<TestActivity> launcher =
                launcherForNewActivity(TestActivity.class, rearDisplayId);

        final boolean allowed = mActivityManager.isActivityStartAllowedOnDisplay(
                mInstrumentationContext, rearDisplayId, launcher.getIntent());
        assertFalse("Should not be allowed to launch", allowed);

        // Should throw SecurityException
        launcher.launch(mInstrumentation);
    }

    /**
     * Tests that the system properly cleans up the rear display presentation if an activity that
     * started it finished without cleaning itself up.
     */
    @ApiTest(apis = {
            "androidx.window.extensions.area."
                    + "WindowAreaComponent#addRearDisplayPresentationStatusListener"})
    @Test
    public void testStartRearDisplayPresentation_applicationFinishes() {
        assumeTrue(mWindowAreaPresentationStatus.getWindowAreaStatus()
                == WindowAreaComponent.STATUS_AVAILABLE);
        assumeTrue(mCurrentDeviceState != mRearDisplayPresentationState);

        mWindowAreaComponent.startRearDisplayPresentationSession(mActivity,
                mSessionStateListener);

        waitAndAssert(() -> mWindowAreaSessionState == SESSION_STATE_ACTIVE);
        assertEquals(mRearDisplayPresentationState, mCurrentDeviceState);

        ExtensionWindowAreaPresentation presentation =
                mWindowAreaComponent.getRearDisplayPresentation();
        TestPresentationView presentationView = new TestPresentationView(
                presentation.getPresentationContext());
        mActivity.runOnUiThread(() ->
                presentation.setPresentationView(presentationView));
        waitAndAssert(() -> presentationView.mAttachedToWindow);

        mActivity.finish();

        waitAndAssert(() -> mWindowAreaSessionState == SESSION_STATE_INACTIVE);

        // Currently when ending rear display presentation session, the display turns off. If this
        // expectation ever changes, we will probably also need to update KeyguardPresentation in
        // SystemUI to ensure that the secondary keyguard is shown.
        waitAndAssert(() -> Display.STATE_ON
                != presentation.getPresentationContext().getDisplay().getState());
    }

    /**
     * Tests that an app in the background cannot start a rear display presentation session.
     */
    @ApiTest(apis = {
            "androidx.window.extensions.area."
                    + "WindowAreaComponent#startRearDisplayPresentationSession"})
    @Test (expected = SecurityException.class)
    public void testStartRearDisplayPresentation_whenInBackground() {
        assumeTrue(mWindowAreaPresentationStatus.getWindowAreaStatus()
                == WindowAreaComponent.STATUS_AVAILABLE);
        assumeTrue(mCurrentDeviceState != mRearDisplayPresentationState);

        pressHomeButton();
        waitAndAssert(() -> mActivity.onStopInvoked);

        mWindowAreaComponent.startRearDisplayPresentationSession(mActivity,
                mSessionStateListener);
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

    private boolean containsValue(int[] values, int value) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == value) return true;
        }
        return false;
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
}
