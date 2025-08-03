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

package android.server.wm.display;

import static android.server.wm.InputMethodVisibilityVerifier.expectImeInvisible;
import static android.server.wm.InputMethodVisibilityVerifier.expectImeVisible;
import static android.server.wm.MockImeHelper.createManagedMockImeSession;
import static android.server.wm.UiDeviceUtils.pressBackButton;
import static android.server.wm.WindowManagerState.STATE_RESUMED;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.WindowManager.DISPLAY_IME_POLICY_FALLBACK_DISPLAY;
import static android.view.WindowManager.DISPLAY_IME_POLICY_HIDE;
import static android.view.WindowManager.DISPLAY_IME_POLICY_LOCAL;
import static android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED;

import static com.android.cts.mockime.ImeEventStreamTestUtils.editorMatcher;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectCommand;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectEvent;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectEventWithKeyValue;
import static com.android.cts.mockime.ImeEventStreamTestUtils.hideSoftInputMatcher;
import static com.android.cts.mockime.ImeEventStreamTestUtils.notExpectEvent;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.platform.test.annotations.Presubmit;
import android.server.wm.MultiDisplayTestBase;
import android.server.wm.WindowManagerState;
import android.server.wm.WindowManagerState.DisplayContent;
import android.server.wm.WindowManagerState.WindowState;
import android.server.wm.intent.Activities;
import android.text.TextUtils;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.SystemUtil;
import com.android.cts.mockime.ImeCommand;
import com.android.cts.mockime.ImeEventStream;
import com.android.cts.mockime.MockImeSession;

import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Build/Install/Run:
 *     atest CtsWindowManagerDeviceDisplay:MultiDisplayImeTests
 */
@Presubmit
@android.server.wm.annotation.Group3
public class MultiDisplayImeTests extends MultiDisplayTestBase {
    static final long NOT_EXPECT_TIMEOUT = TimeUnit.SECONDS.toMillis(2);
    static final long TIMEOUT = TimeUnit.SECONDS.toMillis(5);

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();

        assumeTrue(supportsMultiDisplay());
    }

    @Test
    public void testImeWindowCanSwitchToDifferentDisplays() throws Exception {
        assumeTrue(MSG_NO_MOCK_IME, supportsInstallableIme());

        final MockImeSession mockImeSession = createManagedMockImeSession(this);
        final TestActivitySession<ImeTestActivity> imeTestActivitySession =
                createManagedTestActivitySession();
        final TestActivitySession<ImeTestActivity2> imeTestActivitySession2 =
                createManagedTestActivitySession();

        // Create a virtual display and launch an activity on it.
        final DisplayContent newDisplay = createManagedVirtualDisplaySession()
                .setDisplayImePolicy(DISPLAY_IME_POLICY_LOCAL)
                .setSimulateDisplay(true)
                .createDisplay();

        final ImeEventStream stream = mockImeSession.openEventStream();

        imeTestActivitySession.launchTestActivityOnDisplaySync(ImeTestActivity.class,
                newDisplay.mId);

        expectEvent(stream, editorMatcher("onStartInput",
                imeTestActivitySession.getActivity().mEditText.getPrivateImeOptions()), TIMEOUT);

        // Make the activity to show soft input.
        showSoftInputAndAssertImeShownOnDisplay(newDisplay.mId, imeTestActivitySession, stream);

        // Assert the configuration of the IME window is the same as the configuration of the
        // virtual display.
        assertImeWindowAndDisplayConfiguration(mWmState.getImeWindowState(), newDisplay);

        // Launch another activity on the default display.
        imeTestActivitySession2.launchTestActivityOnDisplaySync(
                ImeTestActivity2.class, DEFAULT_DISPLAY);
        expectEvent(stream, editorMatcher("onStartInput",
                imeTestActivitySession2.getActivity().mEditText.getPrivateImeOptions()), TIMEOUT);

        // Make the activity to show soft input.
        showSoftInputAndAssertImeShownOnDisplay(DEFAULT_DISPLAY, imeTestActivitySession2, stream);

        // Assert the configuration of the IME window is the same as the configuration of the
        // default display.
        assertImeWindowAndDisplayConfiguration(mWmState.getImeWindowState(),
                mWmState.getDisplay(DEFAULT_DISPLAY));
    }

    /**
     * This checks that calling showSoftInput on the incorrect display, requiring the fallback IMM,
     * will not drop the statsToken tracking the show request.
     */
    @Test
    public void testFallbackImmMaintainsParameters() throws Exception {
        try (var mockImeSession = createManagedMockImeSession(this);
                TestActivitySession<ImeTestActivity> imeTestActivitySession =
                        createManagedTestActivitySession();
                var displaySession = createManagedVirtualDisplaySession()) {
            final var newDisplay = displaySession.setSimulateDisplay(true).createDisplay();

            imeTestActivitySession.launchTestActivityOnDisplaySync(
                    ImeTestActivity.class, newDisplay.mId);
            final var activity = imeTestActivitySession.getActivity();
            final var stream = mockImeSession.openEventStream();

            expectEvent(stream, editorMatcher("onStartInput",
                    activity.mEditText.getPrivateImeOptions()), TIMEOUT);

            imeTestActivitySession.runOnMainSyncAndWait(() -> {
                final var imm = activity.getApplicationContext()
                        .getSystemService(InputMethodManager.class);
                imm.showSoftInput(activity.mEditText, 0 /* flags */);
            });

            expectImeVisible(TIMEOUT);
            PollingCheck.waitFor(() -> !mockImeSession.hasPendingImeVisibilityRequests(),
                    "No pending requests should remain after the IME is visible");
        }
    }

    @Test
    public void testImeApiForBug118341760() throws Exception {
        assumeTrue(MSG_NO_MOCK_IME, supportsInstallableIme());

        final MockImeSession mockImeSession = createManagedMockImeSession(this);
        final TestActivitySession<ImeTestActivityWithBrokenContextWrapper> imeTestActivitySession =
                createManagedTestActivitySession();
        // Create a virtual display and launch an activity on it.
        final DisplayContent newDisplay = createManagedVirtualDisplaySession()
                .setSimulateDisplay(true)
                .createDisplay();
        imeTestActivitySession.launchTestActivityOnDisplaySync(
                ImeTestActivityWithBrokenContextWrapper.class, newDisplay.mId);

        final ImeTestActivityWithBrokenContextWrapper activity =
                imeTestActivitySession.getActivity();
        final ImeEventStream stream = mockImeSession.openEventStream();
        final String privateImeOption = activity.getEditText().getPrivateImeOptions();
        expectEvent(stream, event -> {
            if (!TextUtils.equals("onStartInput", event.getEventName())) {
                return false;
            }
            final EditorInfo editorInfo = event.getArguments().getParcelable("editorInfo");
            return TextUtils.equals(editorInfo.packageName, mContext.getPackageName())
                    && TextUtils.equals(editorInfo.privateImeOptions, privateImeOption);
        }, TIMEOUT);

        imeTestActivitySession.runOnMainSyncAndWait(() -> {
            final InputMethodManager imm = activity.getSystemService(InputMethodManager.class);
            assertTrue("InputMethodManager.isActive() should work",
                    imm.isActive(activity.getEditText()));
        });
    }

    @Test
    public void testImeWindowCanSwitchWhenTopFocusedDisplayChange() throws Exception {
        // If config_perDisplayFocusEnabled, the focus will not move even if touching on
        // the Activity in the different display.
        assumeFalse(perDisplayFocusEnabled());
        assumeTrue(MSG_NO_MOCK_IME, supportsInstallableIme());

        final MockImeSession mockImeSession = createManagedMockImeSession(this);
        final TestActivitySession<ImeTestActivity> imeTestActivitySession =
                createManagedTestActivitySession();
        final TestActivitySession<ImeTestActivity2> imeTestActivitySession2 =
                createManagedTestActivitySession();

        // Create a virtual display and launch an activity on virtual & default display.
        final DisplayContent newDisplay = createManagedVirtualDisplaySession()
                .setSimulateDisplay(true)
                .setDisplayImePolicy(DISPLAY_IME_POLICY_LOCAL)
                .createDisplay();
        imeTestActivitySession.launchTestActivityOnDisplaySync(ImeTestActivity.class,
                DEFAULT_DISPLAY);
        imeTestActivitySession2.launchTestActivityOnDisplaySync(ImeTestActivity2.class,
                newDisplay.mId);

        final DisplayContent defDisplay = mWmState.getDisplay(DEFAULT_DISPLAY);
        final ImeEventStream stream = mockImeSession.openEventStream();

        // Tap on the imeTestActivity task center instead of the display center because
        // the activity might not be spanning the entire display
        WindowManagerState.Task imeTestActivityTask = mWmState
                .getTaskByActivity(imeTestActivitySession.getActivity().getComponentName());
        tapOnTaskCenter(imeTestActivityTask);
        expectEvent(stream, editorMatcher("onStartInput",
                imeTestActivitySession.getActivity().mEditText.getPrivateImeOptions()), TIMEOUT);
        showSoftInputAndAssertImeShownOnDisplay(defDisplay.mId, imeTestActivitySession, stream);

        // Tap virtual display as top focused display & request focus on EditText to show
        // soft input.
        tapOnDisplayCenter(newDisplay.mId);
        expectEvent(stream, editorMatcher("onStartInput",
                imeTestActivitySession2.getActivity().mEditText.getPrivateImeOptions()), TIMEOUT);
        showSoftInputAndAssertImeShownOnDisplay(newDisplay.mId, imeTestActivitySession2, stream);

        // Tap on the imeTestActivity task center instead of the display center because
        // the activity might not be spanning the entire display
        imeTestActivityTask = mWmState
                .getTaskByActivity(imeTestActivitySession.getActivity().getComponentName());
        tapOnTaskCenter(imeTestActivityTask);
        expectEvent(stream, editorMatcher("onStartInput",
                imeTestActivitySession.getActivity().mEditText.getPrivateImeOptions()), TIMEOUT);
        showSoftInputAndAssertImeShownOnDisplay(defDisplay.mId, imeTestActivitySession, stream);
    }

    /**
     * Test that the IME can be shown in a different display (actually the default display) than
     * the display on which the target IME application is shown.  Then test several basic operations
     * in {@link InputConnection}.
     */
    @Test
    public void testCrossDisplayBasicImeOperations() throws Exception {
        assumeTrue(MSG_NO_MOCK_IME, supportsInstallableIme());

        final MockImeSession mockImeSession = createManagedMockImeSession(this);
        final TestActivitySession<ImeTestActivity> imeTestActivitySession =
                createManagedTestActivitySession();

        // Create a virtual display by app and assume the display should not show IME window.
        final DisplayContent newDisplay = createManagedVirtualDisplaySession()
                .setPublicDisplay(true)
                .createDisplay();
        SystemUtil.runWithShellPermissionIdentity(
                () -> assertTrue("Display should not support showing IME window",
                        mTargetContext.getSystemService(WindowManager.class)
                                .getDisplayImePolicy(newDisplay.mId)
                                == DISPLAY_IME_POLICY_FALLBACK_DISPLAY));

        // Launch Ime test activity in virtual display.
        imeTestActivitySession.launchTestActivityOnDisplay(ImeTestActivity.class,
                newDisplay.mId);
        final ImeEventStream stream = mockImeSession.openEventStream();

        // Expect onStartInput would be executed when user tapping on the
        // non-system created display intentionally.
        tapAndAssertEditorFocusedOnImeActivity(imeTestActivitySession, newDisplay.mId);
        expectEvent(stream, editorMatcher("onStartInput",
                imeTestActivitySession.getActivity().mEditText.getPrivateImeOptions()), TIMEOUT);

        // Verify the activity to show soft input on the default display.
        showSoftInputAndAssertImeShownOnDisplay(DEFAULT_DISPLAY, imeTestActivitySession, stream);

        // Commit text & make sure the input texts should be delivered to focused EditText on
        // virtual display.
        final EditText editText = imeTestActivitySession.getActivity().mEditText;
        final String commitText = "test commit";
        expectCommand(stream, mockImeSession.callCommitText(commitText, 1), TIMEOUT);
        imeTestActivitySession.runOnMainAndAssertWithTimeout(
                () -> TextUtils.equals(commitText, editText.getText()), TIMEOUT,
                "The input text should be delivered");

        // Since the IME and the IME target app are running in different displays,
        // InputConnection#requestCursorUpdates() is not supported and it should return false.
        // See InputMethodServiceTest#testOnUpdateCursorAnchorInfo() for the normal scenario.
        final ImeCommand callCursorUpdates = mockImeSession.callRequestCursorUpdates(
                InputConnection.CURSOR_UPDATE_IMMEDIATE);
        assertFalse(expectCommand(stream, callCursorUpdates, TIMEOUT).getReturnBooleanValue());
    }

    /**
     * Test that the IME can be hidden with the {@link WindowManager#DISPLAY_IME_POLICY_HIDE} flag.
     */
    @Test
    public void testDisplayPolicyImeHideImeOperation() throws Exception {
        assumeTrue(MSG_NO_MOCK_IME, supportsInstallableIme());

        final MockImeSession mockImeSession = createManagedMockImeSession(this);
        final TestActivitySession<ImeTestActivity> imeTestActivitySession =
                createManagedTestActivitySession();

        // Create a virtual display and launch an activity on virtual display.
        final DisplayContent newDisplay = createManagedVirtualDisplaySession()
                .setDisplayImePolicy(DISPLAY_IME_POLICY_HIDE)
                .setSimulateDisplay(true)
                .createDisplay();

        // Launch Ime test activity and initial the editor focus on virtual display.
        imeTestActivitySession.launchTestActivityOnDisplaySync(ImeTestActivity.class,
                newDisplay.mId);

        // Verify the activity is launched to the secondary display.
        final ComponentName imeTestActivityName =
                imeTestActivitySession.getActivity().getComponentName();
        assertThat(mWmState.hasActivityInDisplay(newDisplay.mId, imeTestActivityName)).isTrue();

        // Verify invoking showSoftInput will be ignored when the display with the HIDE policy.
        final ImeEventStream stream = mockImeSession.openEventStream();
        imeTestActivitySession.runOnMainSyncAndWait(
                imeTestActivitySession.getActivity()::showSoftInput);
        notExpectEvent(stream, editorMatcher("showSoftInput",
                imeTestActivitySession.getActivity().mEditText.getPrivateImeOptions()),
                NOT_EXPECT_TIMEOUT);
    }

    /**
     * A regression test for Bug 273630528.
     *
     * Test that the IME on the editor activity with embedded in virtual display will be hidden
     * after pressing the back key.
     */
    @Test
    public void testHideImeWhenImeTargetOnEmbeddedVirtualDisplay() throws Exception {
        assumeTrue(MSG_NO_MOCK_IME, supportsInstallableIme());

        final VirtualDisplaySession session = createManagedVirtualDisplaySession();
        final MockImeSession imeSession = createManagedMockImeSession(this);
        final TestActivitySession<ImeTestActivity> imeActivitySession =
                createManagedTestActivitySession();

        // Setup a virtual display embedded on an activity.
        final DisplayContent dc = session
                .setPublicDisplay(true)
                .setSupportsTouch(true)
                .createDisplay();

        // Launch a test activity on that virtual display and show IME by tapping the editor.
        imeActivitySession.launchTestActivityOnDisplay(ImeTestActivity.class, dc.mId);
        tapAndAssertEditorFocusedOnImeActivity(imeActivitySession, dc.mId);
        final ImeEventStream stream = imeSession.openEventStream();
        final String marker = imeActivitySession.getActivity().mEditText.getPrivateImeOptions();
        expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);

        // Expect soft-keyboard becomes visible after requesting show IME.
        showSoftInputAndAssertImeShownOnDisplay(DEFAULT_DISPLAY, imeActivitySession, stream);
        expectEventWithKeyValue(stream, "onWindowVisibilityChanged", "visible",
                View.VISIBLE, TIMEOUT);
        expectImeVisible(TIMEOUT);

        // Pressing back key, expect soft-keyboard will become invisible.
        pressBackButton();
        expectEvent(stream, hideSoftInputMatcher(), TIMEOUT);
        expectEventWithKeyValue(stream, "onWindowVisibilityChanged", "visible",
                View.GONE, TIMEOUT);
        expectImeInvisible(TIMEOUT);
    }

    @Test
    public void testImeWindowCanShownWhenActivityMovedToDisplay() throws Exception {
        // If config_perDisplayFocusEnabled, the focus will not move even if touching on
        // the Activity in the different display.
        assumeFalse(perDisplayFocusEnabled());
        assumeTrue(MSG_NO_MOCK_IME, supportsInstallableIme());

        // Launch a regular activity on default display at the test beginning to prevent the test
        // may mis-touch the launcher icon that breaks the test expectation.
        final TestActivitySession<Activities.RegularActivity> testActivitySession =
                createManagedTestActivitySession();
        testActivitySession.launchTestActivityOnDisplaySync(Activities.RegularActivity.class,
                DEFAULT_DISPLAY);

        // Create a virtual display and launch an activity on virtual display.
        final DisplayContent newDisplay = createManagedVirtualDisplaySession()
                .setDisplayImePolicy(DISPLAY_IME_POLICY_LOCAL)
                .setSimulateDisplay(true)
                .createDisplay();

        // Leverage MockImeSession to ensure at least an IME exists as default.
        final MockImeSession mockImeSession = createManagedMockImeSession(this);
        final TestActivitySession<ImeTestActivity> imeTestActivitySession =
                createManagedTestActivitySession();
        // Launch Ime test activity and initial the editor focus on virtual display.
        imeTestActivitySession.launchTestActivityOnDisplaySync(ImeTestActivity.class,
                newDisplay.mId);

        // Verify the activity is launched to the secondary display.
        final ComponentName imeTestActivityName =
                imeTestActivitySession.getActivity().getComponentName();
        assertThat(mWmState.hasActivityInDisplay(newDisplay.mId, imeTestActivityName)).isTrue();

        // Tap default display, assume a pointer-out-side event will happened to change the top
        // display.
        final DisplayContent defDisplay = mWmState.getDisplay(DEFAULT_DISPLAY);
        tapOnDisplayCenter(defDisplay.mId);
        mWmState.waitForAppTransitionIdleOnDisplay(DEFAULT_DISPLAY);
        mWmState.assertValidity();

        // Reparent ImeTestActivity from virtual display to default display.
        getLaunchActivityBuilder()
                .setUseInstrumentation()
                .setTargetActivity(imeTestActivitySession.getActivity().getComponentName())
                .setIntentFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .allowMultipleInstances(false)
                .setDisplayId(DEFAULT_DISPLAY).execute();
        waitAndAssertTopResumedActivity(imeTestActivitySession.getActivity().getComponentName(),
                DEFAULT_DISPLAY, "Activity launched on default display and on top");

        // Activity is no longer on the secondary display
        assertThat(mWmState.hasActivityInDisplay(newDisplay.mId, imeTestActivityName)).isFalse();

        // Tap on the imeTestActivity task center instead of the display center because
        // the activity might not be spanning the entire display
        final ImeEventStream stream = mockImeSession.openEventStream();
        final WindowManagerState.Task testActivityTask = mWmState
                .getTaskByActivity(imeTestActivitySession.getActivity().getComponentName());
        tapOnTaskCenter(testActivityTask);
        expectEvent(stream, editorMatcher("onStartInput",
                imeTestActivitySession.getActivity().mEditText.getPrivateImeOptions()), TIMEOUT);

        // Verify the activity shows soft input on the default display.
        showSoftInputAndAssertImeShownOnDisplay(DEFAULT_DISPLAY, imeTestActivitySession, stream);
    }

    @Test
    public void testNoConfigurationChangedWhenSwitchBetweenTwoIdenticalDisplays() throws Exception {
        // If config_perDisplayFocusEnabled, the focus will not move even if touching on
        // the Activity in the different display.
        assumeFalse(perDisplayFocusEnabled());
        assumeTrue(MSG_NO_MOCK_IME, supportsInstallableIme());

        // Create two displays with the same display metrics
        final List<DisplayContent> newDisplays = createManagedVirtualDisplaySession()
                .setDisplayImePolicy(DISPLAY_IME_POLICY_LOCAL)
                .setOwnContentOnly(true)
                .setSimulateDisplay(true)
                .setResizeDisplay(false)
                .createDisplays(2);
        final DisplayContent firstDisplay = newDisplays.get(0);
        final DisplayContent secondDisplay = newDisplays.get(1);

        // Skip if the test environment somehow didn't create 2 displays with identical size.
        assumeTrue("Skip the test if the size of the created displays aren't identical",
                firstDisplay.getDisplayRect().equals(secondDisplay.getDisplayRect()));

        final TestActivitySession<ImeTestActivity2> imeTestActivitySession2 =
                createManagedTestActivitySession();
        imeTestActivitySession2.launchTestActivityOnDisplaySync(
                ImeTestActivity2.class, secondDisplay.mId);

        // Make firstDisplay the top focus display.
        tapOnDisplayCenter(firstDisplay.mId);

        mWmState.waitForWithAmState(state -> state.getFocusedDisplayId() == firstDisplay.mId,
                "First display must be top focused.");

        // Initialize IME test environment
        final MockImeSession mockImeSession = createManagedMockImeSession(this);
        final TestActivitySession<ImeTestActivity> imeTestActivitySession =
                createManagedTestActivitySession();
        ImeEventStream stream = mockImeSession.openEventStream();
        // Filter out onConfigurationChanged events in case that IME is moved from the default
        // display to the firstDisplay.
        ImeEventStream configChangeVerifyStream = clearOnConfigurationChangedFromStream(stream);

        imeTestActivitySession.launchTestActivityOnDisplaySync(ImeTestActivity.class,
                firstDisplay.mId);
        // Wait until IME is ready for the IME client to call showSoftInput().
        expectEvent(stream, editorMatcher("onStartInput",
                imeTestActivitySession.getActivity().mEditText.getPrivateImeOptions()), TIMEOUT);

        int imeDisplayId = expectCommand(stream, mockImeSession.callGetDisplayId(),
                TIMEOUT).getReturnIntegerValue();
        assertThat(imeDisplayId).isEqualTo(firstDisplay.mId);

        imeTestActivitySession.runOnMainSyncAndWait(
                imeTestActivitySession.getActivity()::showSoftInput);
        waitOrderedImeEventsThenAssertImeShown(stream, firstDisplay.mId,
                event -> "showSoftInput".equals(event.getEventName()));
        try {
            // Launch Ime must not lead to screen size changes.
            waitAndAssertImeNoScreenSizeChanged(configChangeVerifyStream);

            final Rect currentBoundsOnFirstDisplay = expectCommand(stream,
                    mockImeSession.callGetCurrentWindowMetricsBounds(), TIMEOUT)
                    .getReturnParcelableValue();

            // Clear onConfigurationChanged events before IME moves to the secondary display to
            // prevent flaky because IME may receive configuration updates which we don't care
            // about. An example is CONFIG_KEYBOARD_HIDDEN.
            configChangeVerifyStream = clearOnConfigurationChangedFromStream(stream);

            // Tap secondDisplay to change it to the top focused display.
            tapOnDisplayCenter(secondDisplay.mId);

            // Move ImeTestActivity from firstDisplay to secondDisplay.
            getLaunchActivityBuilder()
                    .setUseInstrumentation()
                    .setTargetActivity(imeTestActivitySession.getActivity().getComponentName())
                    .setIntentFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .allowMultipleInstances(false)
                    .setDisplayId(secondDisplay.mId).execute();

            // Make sure ImeTestActivity is move from the firstDisplay to the secondDisplay
            waitAndAssertTopResumedActivity(imeTestActivitySession.getActivity().getComponentName(),
                    secondDisplay.mId, "ImeTestActivity must be top-resumed on display#"
                            + secondDisplay.mId);
            assertThat(mWmState.hasActivityInDisplay(firstDisplay.mId,
                    imeTestActivitySession.getActivity().getComponentName())).isFalse();
            // Wait until IME is ready for the IME client to call showSoftInput().
            expectEvent(stream, editorMatcher("onStartInput",
                    imeTestActivitySession.getActivity().mEditText.getPrivateImeOptions()),
                    TIMEOUT);
            imeDisplayId = expectCommand(stream, mockImeSession.callGetDisplayId(),
                    TIMEOUT).getReturnIntegerValue();
            assertThat(imeDisplayId).isEqualTo(secondDisplay.mId);

            // Show soft input again to trigger IME movement.
            imeTestActivitySession.runOnMainSyncAndWait(
                    imeTestActivitySession.getActivity()::showSoftInput);
            waitOrderedImeEventsThenAssertImeShown(stream, secondDisplay.mId,
                    event -> "showSoftInput".equals(event.getEventName()));

            // Moving IME to the display with the same display metrics must not lead to
            // screen size changes.
            waitAndAssertImeNoScreenSizeChanged(configChangeVerifyStream);

            final Rect currentBoundsOnSecondDisplay = expectCommand(stream,
                    mockImeSession.callGetCurrentWindowMetricsBounds(), TIMEOUT)
                    .getReturnParcelableValue();

            assertWithMessage("The current WindowMetrics bounds of IME must not be changed.")
                    .that(currentBoundsOnFirstDisplay).isEqualTo(currentBoundsOnSecondDisplay);
        } catch (AssertionError e) {
            mWmState.computeState();
            final Rect displayRect1 = mWmState.getDisplay(firstDisplay.mId).getDisplayRect();
            final Rect displayRect2 = mWmState.getDisplay(secondDisplay.mId).getDisplayRect();
            assumeTrue("Skip test since the size of one or both displays happens unexpected change",
                    displayRect1.equals(displayRect2));
            throw e;
        }
    }

    public static class ImeTestActivity extends Activity {
        EditText mEditText;

        @Override
        protected void onCreate(Bundle icicle) {
            super.onCreate(icicle);
            mEditText = new EditText(this);
            // Set private IME option for editorMatcher to identify which TextView received
            // onStartInput event.
            resetPrivateImeOptionsIdentifier();
            final LinearLayout layout = new LinearLayout(this);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.addView(mEditText);
            mEditText.requestFocus();
            // SOFT_INPUT_STATE_UNSPECIFIED may produced unexpected behavior for CTS. To make tests
            // deterministic, using SOFT_INPUT_STATE_UNCHANGED instead.
            setUnchangedSoftInputState();
            setContentView(layout);
        }

        void showSoftInput() {
            final InputMethodManager imm = getSystemService(InputMethodManager.class);
            imm.showSoftInput(mEditText, 0);
        }

        void resetPrivateImeOptionsIdentifier() {
            mEditText.setPrivateImeOptions(
                    getClass().getName() + "/" + Long.toString(SystemClock.elapsedRealtimeNanos()));
        }

        private void setUnchangedSoftInputState() {
            final Window window = getWindow();
            final int currentSoftInputMode = window.getAttributes().softInputMode;
            final int newSoftInputMode =
                    (currentSoftInputMode & ~WindowManager.LayoutParams.SOFT_INPUT_MASK_STATE)
                            | SOFT_INPUT_STATE_UNCHANGED;
            window.setSoftInputMode(newSoftInputMode);
        }
    }

    public static class ImeTestActivity2 extends ImeTestActivity { }

    public static final class ImeTestActivityWithBrokenContextWrapper extends Activity {
        private EditText mEditText;

        /**
         * Emulates the behavior of certain {@link ContextWrapper} subclasses we found in the wild.
         *
         * <p> Certain {@link ContextWrapper} subclass in the wild delegate method calls to
         * ApplicationContext except for {@link #getSystemService(String)}.</p>
         *
         **/
        private static final class Bug118341760ContextWrapper extends ContextWrapper {
            private final Context mOriginalContext;

            Bug118341760ContextWrapper(Context base) {
                super(base.getApplicationContext());
                mOriginalContext = base;
            }

            /**
             * Emulates the behavior of {@link ContextWrapper#getSystemService(String)} of certain
             * {@link ContextWrapper} subclasses we found in the wild.
             *
             * @param name The name of the desired service.
             * @return The service or {@code null} if the name does not exist.
             */
            @Override
            public Object getSystemService(String name) {
                return mOriginalContext.getSystemService(name);
            }
        }

        @Override
        protected void onCreate(Bundle icicle) {
            super.onCreate(icicle);
            mEditText = new EditText(new Bug118341760ContextWrapper(this));
            // Use SystemClock.elapsedRealtimeNanos()) as a unique ID of this edit text.
            mEditText.setPrivateImeOptions(Long.toString(SystemClock.elapsedRealtimeNanos()));
            final LinearLayout layout = new LinearLayout(this);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.addView(mEditText);
            mEditText.requestFocus();
            setContentView(layout);
        }

        EditText getEditText() {
            return mEditText;
        }
    }

    private void assertImeWindowAndDisplayConfiguration(
            WindowState imeWinState, DisplayContent display) {
        // The IME window should inherit the configuration from the IME DisplayArea.
        final WindowManagerState.DisplayArea imeContainerDisplayArea = display.getImeContainer();
        final Configuration configurationForIme = imeWinState.getMergedOverrideConfiguration();
        final Configuration configurationForImeContainer =
                imeContainerDisplayArea.getMergedOverrideConfiguration();
        final int displayDensityDpiForIme = configurationForIme.densityDpi;
        final int displayDensityDpiForImeContainer = configurationForImeContainer.densityDpi;
        final Rect displayBoundsForIme = configurationForIme.windowConfiguration.getBounds();
        final Rect displayBoundsForImeContainer =
                configurationForImeContainer.windowConfiguration.getBounds();

        assertEquals("Display density not the same",
                displayDensityDpiForImeContainer, displayDensityDpiForIme);
        assertEquals("Display bounds not the same",
                displayBoundsForImeContainer, displayBoundsForIme);
    }

    private void tapAndAssertEditorFocusedOnImeActivity(
            TestActivitySession<? extends ImeTestActivity> activitySession, int expectDisplayId) {
        final int[] location = new int[2];
        waitAndAssertActivityStateOnDisplay(activitySession.getActivity().getComponentName(),
                STATE_RESUMED, expectDisplayId,
                "ImeActivity failed to appear on display#" + expectDisplayId);
        activitySession.runOnMainSyncAndWait(() -> {
            final EditText editText = activitySession.getActivity().mEditText;
            editText.getLocationOnScreen(location);
        });
        final ComponentName expectComponent = activitySession.getActivity().getComponentName();
        tapOnDisplaySync(location[0], location[1], expectDisplayId);
        mWmState.computeState(activitySession.getActivity().getComponentName());
        mWmState.assertFocusedAppOnDisplay("Activity not focus on the display", expectComponent,
                expectDisplayId);
    }

    private void showSoftInputAndAssertImeShownOnDisplay(int displayId,
            TestActivitySession<? extends ImeTestActivity> activitySession, ImeEventStream stream)
            throws Exception {
        activitySession.runOnMainSyncAndWait(
                activitySession.getActivity()::showSoftInput);
        expectEvent(stream, editorMatcher("onStartInputView",
                activitySession.getActivity().mEditText.getPrivateImeOptions()), TIMEOUT);
        // Assert the IME is shown on the expected display.
        mWmState.waitAndAssertImeWindowShownOnDisplay(displayId);
    }
}
