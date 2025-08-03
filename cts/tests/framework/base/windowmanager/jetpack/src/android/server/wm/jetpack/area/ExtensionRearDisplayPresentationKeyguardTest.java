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

import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static androidx.window.extensions.area.WindowAreaComponent.SESSION_STATE_ACTIVE;
import static androidx.window.extensions.area.WindowAreaComponent.SESSION_STATE_CONTENT_VISIBLE;
import static androidx.window.extensions.area.WindowAreaComponent.SESSION_STATE_INACTIVE;
import static com.android.compatibility.common.util.PollingCheck.waitFor;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeTrue;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.res.Resources;
import android.hardware.devicestate.DeviceStateManager;
import android.platform.test.annotations.Presubmit;
import android.server.wm.ActivityManagerTestBase;
import android.server.wm.DeviceStateUtils;
import android.server.wm.LockScreenSession;
import android.server.wm.jetpack.utils.TestRearDisplayActivity;
import android.server.wm.jetpack.utils.TestRearDisplayShowWhenLockedActivity;
import android.server.wm.jetpack.utils.WindowExtensionTestRule;
import android.view.Display;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.FlakyTest;
import androidx.test.filters.LargeTest;
import androidx.window.extensions.area.ExtensionWindowAreaPresentation;
import androidx.window.extensions.area.ExtensionWindowAreaStatus;
import androidx.window.extensions.area.WindowAreaComponent;
import androidx.window.extensions.core.util.function.Consumer;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.PollingCheck;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

/**
 * Rear display presentation tests for scenarios around Keyguard and KeyguardPresentation (keyguard
 * for the secondary displays).
 */
@LargeTest
@Presubmit
@RunWith(AndroidJUnit4.class)
public class ExtensionRearDisplayPresentationKeyguardTest
        extends ActivityManagerTestBase implements DeviceStateManager.DeviceStateCallback {

    private static final int TIMEOUT = 2000;

    private final Context mInstrumentationContext = getInstrumentation().getTargetContext();
    private final DeviceStateManager mDeviceStateManager = mInstrumentationContext
            .getSystemService(DeviceStateManager.class);
    private WindowAreaComponent mWindowAreaComponent;
    private ExtensionWindowAreaStatus mWindowAreaPresentationStatus;
    private int mCurrentDeviceState;
    private int mRearDisplayPresentationState;

    private final Consumer<ExtensionWindowAreaStatus> mStatusListener =
            (status) -> mWindowAreaPresentationStatus = status;

    @WindowAreaComponent.WindowAreaSessionState
    private int mWindowAreaSessionState;
    private List<Integer> mSessionStateStatusValues = new ArrayList<>();
    private final Consumer<@WindowAreaComponent.WindowAreaSessionState Integer>
            mSessionStateListener = (sessionStatus) -> {
        mSessionStateStatusValues.add(sessionStatus);
        mWindowAreaSessionState = sessionStatus;
    };

    @Rule
    public final WindowExtensionTestRule mWindowManagerJetpackTestRule =
            new WindowExtensionTestRule(WindowAreaComponent.class);

    @Before
    public void setUp() {
        // TODO(b/236022708) Move rear display presentation state to device state config file
        mRearDisplayPresentationState = getInstrumentation().getTargetContext().getResources()
                .getInteger(Resources.getSystem().getIdentifier(
                        "config_deviceStateConcurrentRearDisplay", "integer", "android"));
        assumeTrue(mRearDisplayPresentationState != -1);

        mWindowAreaComponent =
                (WindowAreaComponent) mWindowManagerJetpackTestRule.getExtensionComponent();
        mWindowAreaComponent.addRearDisplayPresentationStatusListener(mStatusListener);
        mDeviceStateManager.registerCallback(Runnable::run, this);
    }

    @After
    public void tearDown() {
        if (mWindowAreaComponent != null) {
            mWindowAreaComponent.removeRearDisplayPresentationStatusListener(mStatusListener);
            mDeviceStateManager.unregisterCallback(this);
            try {
                DeviceStateUtils.runWithControlDeviceStatePermission(
                        mDeviceStateManager::cancelStateRequest);
                DeviceStateUtils.runWithControlDeviceStatePermission(
                        mDeviceStateManager::cancelBaseStateOverride);
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        }
    }

    /**
     * Tests that an app without {@link FLAG_SHOW_WHEN_LOCKED} covered by Keyguard cannot start a
     * rear display presentation session.
     */
    @ApiTest(apis = {
            "androidx.window.extensions.area."
                    + "WindowAreaComponent#startRearDisplayPresentationSession"})
    @Test (expected = SecurityException.class)
    @FlakyTest(bugId = 295878833)
    public void testStartRearDisplayPresentation_whenKeyguardLocked() {
        assumeTrue(mWindowAreaPresentationStatus.getWindowAreaStatus()
                == WindowAreaComponent.STATUS_AVAILABLE);
        assumeTrue(mCurrentDeviceState != mRearDisplayPresentationState);

        final LockScreenSession lockScreenSession = createManagedLockScreenSession();
        final TestActivitySession<TestRearDisplayActivity> activitySession =
                createManagedTestActivitySession();

        lockScreenSession.setLockCredential();
        activitySession.launchTestActivityOnDisplaySync(TestRearDisplayActivity.class,
                DEFAULT_DISPLAY);

        lockScreenSession.gotoKeyguard();
        mWmState.waitForKeyguardShowingAndNotOccluded();
        mWmState.assertKeyguardShowingAndNotOccluded();

        // Try to start rear display presentation. Should throw SecurityException
        mWindowAreaComponent.startRearDisplayPresentationSession(activitySession.getActivity(),
                mSessionStateListener);
    }

    /**
     * Tests that an app with {@link FLAG_SHOW_WHEN_LOCKED} is able to start a rear display
     * presentation on top of keyguard, and that the presentation is shown on the secondary display.
     */
    @ApiTest(apis = {
            "androidx.window.extensions.area."
                    + "WindowAreaComponent#startRearDisplayPresentationSession"})
    @Test
    @FlakyTest(bugId = 295878833)
    public void testStartRearDisplayPresentation_afterKeyguardLocked() {
        assumeTrue(mWindowAreaPresentationStatus.getWindowAreaStatus()
                == WindowAreaComponent.STATUS_AVAILABLE);
        assumeTrue(mCurrentDeviceState != mRearDisplayPresentationState);

        final LockScreenSession lockScreenSession = createManagedLockScreenSession();
        lockScreenSession.setLockCredential();
        lockScreenSession.gotoKeyguard();

        mWmState.waitForKeyguardShowingAndNotOccluded();
        mWmState.assertKeyguardShowingAndNotOccluded();

        final TestActivitySession<TestRearDisplayShowWhenLockedActivity> activitySession =
                createManagedTestActivitySession();
        activitySession.launchTestActivityOnDisplaySync(TestRearDisplayShowWhenLockedActivity.class,
                DEFAULT_DISPLAY);

        mWindowAreaComponent.startRearDisplayPresentationSession(activitySession.getActivity(),
                mSessionStateListener);
        waitAndAssert(() -> SESSION_STATE_ACTIVE == mWindowAreaSessionState);
        assertEquals(mRearDisplayPresentationState, mCurrentDeviceState);

        ExtensionWindowAreaPresentation presentation =
                mWindowAreaComponent.getRearDisplayPresentation();
        assertNotNull(presentation);
        TestPresentationView presentationView = new TestPresentationView(
                presentation.getPresentationContext());
        activitySession.getActivity().runOnUiThread(() ->
                presentation.setPresentationView(presentationView));
        waitAndAssert(() -> presentationView.mAttachedToWindow);
        assertNotEquals(DEFAULT_DISPLAY, presentationView.getDisplay().getDisplayId());
        assertNotEquals(Display.STATE_OFF, presentationView.getDisplay().getState());
        assertEquals(mWindowAreaSessionState, SESSION_STATE_CONTENT_VISIBLE);
    }

    /**
     * Tests that if keyguard locks while an activity with {@link FLAG_SHOW_WHEN_LOCKED} was on top,
     * that the activity stays on top of Keyguard, and that it is able to start a rear presentation
     * session on the secondary display.
     */
    @ApiTest(apis = {
            "androidx.window.extensions.area."
                    + "WindowAreaComponent#startRearDisplayPresentationSession"})
    @Test
    @FlakyTest(bugId = 295878833)
    public void testStartRearDisplayPresentation_thenKeyguardLocked() {
        assumeTrue(mWindowAreaPresentationStatus.getWindowAreaStatus()
                == WindowAreaComponent.STATUS_AVAILABLE);
        assumeTrue(mCurrentDeviceState != mRearDisplayPresentationState);

        final LockScreenSession lockScreenSession = createManagedLockScreenSession();
        lockScreenSession.setLockCredential();

        final TestActivitySession<TestRearDisplayShowWhenLockedActivity> activitySession =
                createManagedTestActivitySession();
        activitySession.launchTestActivityOnDisplaySync(TestRearDisplayShowWhenLockedActivity.class,
                DEFAULT_DISPLAY);

        lockScreenSession.gotoKeyguard();
        mWmState.waitForKeyguardShowingAndOccluded();
        mWmState.assertKeyguardShowingAndOccluded();

        mWindowAreaComponent.startRearDisplayPresentationSession(activitySession.getActivity(),
                mSessionStateListener);
        waitAndAssert(() -> SESSION_STATE_ACTIVE == mWindowAreaSessionState);
        waitAndAssert(() -> mCurrentDeviceState == mRearDisplayPresentationState);

        ExtensionWindowAreaPresentation presentation =
                mWindowAreaComponent.getRearDisplayPresentation();
        assertNotNull(presentation);
        TestPresentationView presentationView = new TestPresentationView(
                presentation.getPresentationContext());
        activitySession.getActivity().runOnUiThread(() ->
                presentation.setPresentationView(presentationView));
        waitAndAssert(() -> presentationView.mAttachedToWindow);
        final int presentationDisplayId = presentationView.getDisplay().getDisplayId();
        assertNotEquals(DEFAULT_DISPLAY, presentationDisplayId);
        assertNotEquals(Display.STATE_OFF, presentationView.getDisplay().getState());
        assertEquals(mWindowAreaSessionState, SESSION_STATE_CONTENT_VISIBLE);

        mWmState.waitAndAssertKeyguardGoneOnSecondaryDisplay(presentationDisplayId);
    }

    /**
     * Tests that if an activity with {@link FLAG_SHOW_WHEN_LOCKED} which started a rear display
     * presentation on top of keyguard finishes without cleaning up, that the system will clean up
     * the rear display presentation session.
     */
    @ApiTest(apis = {
            "androidx.window.extensions.area."
                    + "WindowAreaComponent#startRearDisplayPresentationSession"})
    @Test
    @FlakyTest(bugId = 295878833)
    public void testStartRearDisplayPresentation_thenKeyguardLocked_activityFinishes() {
        assumeTrue(mWindowAreaPresentationStatus.getWindowAreaStatus()
                == WindowAreaComponent.STATUS_AVAILABLE);
        assumeTrue(mCurrentDeviceState != mRearDisplayPresentationState);

        final LockScreenSession lockScreenSession = createManagedLockScreenSession();
        lockScreenSession.setLockCredential();

        final TestActivitySession<TestRearDisplayShowWhenLockedActivity> activitySession =
                createManagedTestActivitySession();
        activitySession.launchTestActivityOnDisplaySync(TestRearDisplayShowWhenLockedActivity.class,
                DEFAULT_DISPLAY);

        lockScreenSession.gotoKeyguard();
        mWmState.waitForKeyguardShowingAndOccluded();
        mWmState.assertKeyguardShowingAndOccluded();

        mWindowAreaComponent.startRearDisplayPresentationSession(activitySession.getActivity(),
                mSessionStateListener);
        waitAndAssert(() -> SESSION_STATE_ACTIVE == mWindowAreaSessionState);
        waitAndAssert(() -> mCurrentDeviceState == mRearDisplayPresentationState);

        ExtensionWindowAreaPresentation presentation =
                mWindowAreaComponent.getRearDisplayPresentation();
        assertNotNull(presentation);
        TestPresentationView presentationView = new TestPresentationView(
                presentation.getPresentationContext());
        activitySession.getActivity().runOnUiThread(() ->
                presentation.setPresentationView(presentationView));
        waitAndAssert(() -> presentationView.mAttachedToWindow);
        final int presentationDisplayId = presentationView.getDisplay().getDisplayId();
        assertNotEquals(DEFAULT_DISPLAY, presentationDisplayId);
        assertNotEquals(Display.STATE_OFF, presentationView.getDisplay().getState());
        assertEquals(mWindowAreaSessionState, SESSION_STATE_CONTENT_VISIBLE);

        mWmState.waitAndAssertKeyguardGoneOnSecondaryDisplay(presentationDisplayId);

        activitySession.getActivity().finish();

        waitAndAssert(() -> mWindowAreaSessionState == SESSION_STATE_INACTIVE);
        assertNotEquals(Display.STATE_ON, presentationDisplayId);
    }

    /**
     * Tests that if an activity with {@link FLAG_SHOW_WHEN_LOCKED} starts a rear display
     * presentation session on top of keyguard dismisses keyguard, that the session continues after
     * keyguard is gone.
     */
    @ApiTest(apis = {
            "androidx.window.extensions.area."
                    + "WindowAreaComponent#startRearDisplayPresentationSession"})
    @Test
    @FlakyTest(bugId = 295878833)
    public void testStartRearDisplayPresentation_persistsAfterDismissingKeyguard() {
        assumeTrue(mWindowAreaPresentationStatus.getWindowAreaStatus()
                == WindowAreaComponent.STATUS_AVAILABLE);
        assumeTrue(mCurrentDeviceState != mRearDisplayPresentationState);

        final LockScreenSession lockScreenSession = createManagedLockScreenSession();
        lockScreenSession.setLockCredential();

        final TestActivitySession<TestRearDisplayShowWhenLockedActivity> activitySession =
                createManagedTestActivitySession();
        activitySession.launchTestActivityOnDisplaySync(TestRearDisplayShowWhenLockedActivity.class,
                DEFAULT_DISPLAY);

        lockScreenSession.gotoKeyguard();
        mWmState.waitForKeyguardShowingAndOccluded();
        mWmState.assertKeyguardShowingAndOccluded();

        mWindowAreaComponent.startRearDisplayPresentationSession(activitySession.getActivity(),
                mSessionStateListener);
        waitAndAssert(() -> SESSION_STATE_ACTIVE == mWindowAreaSessionState);
        waitAndAssert(() -> mCurrentDeviceState == mRearDisplayPresentationState);

        ExtensionWindowAreaPresentation presentation =
                mWindowAreaComponent.getRearDisplayPresentation();
        assertNotNull(presentation);
        TestPresentationView presentationView = new TestPresentationView(
                presentation.getPresentationContext());
        activitySession.getActivity().runOnUiThread(() ->
                presentation.setPresentationView(presentationView));
        waitAndAssert(() -> presentationView.mAttachedToWindow);
        final int presentationDisplayId = presentationView.getDisplay().getDisplayId();
        assertNotEquals(DEFAULT_DISPLAY, presentationDisplayId);
        assertNotEquals(Display.STATE_OFF, presentationView.getDisplay().getState());
        assertEquals(SESSION_STATE_CONTENT_VISIBLE, mWindowAreaSessionState);

        mWmState.waitAndAssertKeyguardGoneOnSecondaryDisplay(presentationDisplayId);

        KeyguardManager kgm = activitySession.getActivity().getSystemService(KeyguardManager.class);
        kgm.requestDismissKeyguard(activitySession.getActivity(), null /* callback */);
        lockScreenSession.enterAndConfirmLockCredential();
        mWmState.waitAndAssertKeyguardGone();
        assertEquals(SESSION_STATE_CONTENT_VISIBLE, mWindowAreaSessionState);
    }

    /**
     * Tests that if an activity with {@link FLAG_SHOW_WHEN_LOCKED} which started a rear display
     * presentation on top of keyguard and the device goes to sleep, that the session is cleaned
     * up.
     */
    @ApiTest(apis = {
            "androidx.window.extensions.area."
                    + "WindowAreaComponent#startRearDisplayPresentationSession"})
    @Test
    @FlakyTest(bugId = 295878833)
    public void testStartRearDisplayPresentation_afterKeyguardLocked_thenScreenOff() {
        assumeTrue(mWindowAreaPresentationStatus.getWindowAreaStatus()
                == WindowAreaComponent.STATUS_AVAILABLE);
        assumeTrue(mCurrentDeviceState != mRearDisplayPresentationState);

        final LockScreenSession lockScreenSession = createManagedLockScreenSession();
        lockScreenSession.setLockCredential();
        lockScreenSession.gotoKeyguard();

        mWmState.waitForKeyguardShowingAndNotOccluded();
        mWmState.assertKeyguardShowingAndNotOccluded();

        final TestActivitySession<TestRearDisplayShowWhenLockedActivity> activitySession =
                createManagedTestActivitySession();
        activitySession.launchTestActivityOnDisplaySync(TestRearDisplayShowWhenLockedActivity.class,
                DEFAULT_DISPLAY);

        mWindowAreaComponent.startRearDisplayPresentationSession(activitySession.getActivity(),
                mSessionStateListener);
        waitAndAssert(() -> SESSION_STATE_ACTIVE == mWindowAreaSessionState);
        waitAndAssert(() -> mCurrentDeviceState == mRearDisplayPresentationState);

        ExtensionWindowAreaPresentation presentation =
                mWindowAreaComponent.getRearDisplayPresentation();
        assertNotNull(presentation);
        TestPresentationView presentationView = new TestPresentationView(
                presentation.getPresentationContext());
        activitySession.getActivity().runOnUiThread(() ->
                presentation.setPresentationView(presentationView));
        waitAndAssert(() -> presentationView.mAttachedToWindow);
        final Display presentationDisplay = presentationView.getDisplay();
        assertNotEquals(DEFAULT_DISPLAY, presentationDisplay.getDisplayId());
        assertNotEquals(Display.STATE_OFF, presentationDisplay.getState());
        assertEquals(mWindowAreaSessionState, SESSION_STATE_CONTENT_VISIBLE);

        lockScreenSession.sleepDevice();
        waitAndAssert(() -> mWindowAreaSessionState == SESSION_STATE_INACTIVE);
        assertNotEquals(Display.STATE_ON, presentationDisplay.getDisplayId());
    }

    private void waitAndAssert(PollingCheck.PollingCheckCondition condition) {
        waitFor(TIMEOUT, condition);
    }

    @Override
    public void onStateChanged(int state) {
        mCurrentDeviceState = state;
    }
}
