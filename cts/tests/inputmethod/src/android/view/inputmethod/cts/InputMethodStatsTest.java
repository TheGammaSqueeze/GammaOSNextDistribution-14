/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.view.inputmethod.cts;

import static android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE;
import static android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED;
import static android.view.inputmethod.cts.util.InputMethodVisibilityVerifier.expectImeInvisible;
import static android.view.inputmethod.cts.util.InputMethodVisibilityVerifier.expectImeVisible;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.fail;

import android.app.Activity;
import android.app.Instrumentation;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.support.test.uiautomator.UiDevice;
import android.view.MotionEvent;
import android.view.WindowInsets;
import android.view.WindowInsetsController.OnControllableInsetsChangedListener;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.cts.util.EndToEndImeTestBase;
import android.view.inputmethod.cts.util.MetricsRecorder;
import android.view.inputmethod.cts.util.TestActivity;
import android.view.inputmethod.cts.util.TestUtils;
import android.view.inputmethod.nano.ImeProtoEnums;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.PollingCheck;
import com.android.cts.mockime.ImeSettings;
import com.android.cts.mockime.MockImeSession;
import com.android.os.nano.AtomsProto;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Test suite to ensure IME stats get tracked and logged correctly.
 */
@RunWith(AndroidJUnit4.class)
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public class InputMethodStatsTest extends EndToEndImeTestBase {

    private static final String TAG = "InputMethodStatsTest";

    private static final int EDIT_TEXT_ID = 1;
    private static final int TEXT_VIEW_ID = 2;

    private static final long TIMEOUT = TimeUnit.SECONDS.toMillis(20);

    private Instrumentation mInstrumentation;

    /** The test app package name from which atoms will be logged. */
    private String mPkgName;

    @Before
    public void setUp() throws Exception {
        MetricsRecorder.removeConfig();
        MetricsRecorder.clearReports();

        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mPkgName = mInstrumentation.getContext().getPackageName();
    }

    @After
    public void tearDown() throws Exception {
        MetricsRecorder.removeConfig();
        MetricsRecorder.clearReports();

        mInstrumentation = null;
        mPkgName = "";
    }

    /**
     * Creates and launches a test activity.
     *
     * @param mode the {@link WindowManager.LayoutParams#softInputMode softInputMode} for the
     *             activity.
     *
     * @return the created activity.
     */
    private TestActivity createTestActivity(final int mode) {
        return TestActivity.startSync(activity -> createLayout(mode, activity));
    }

    /**
     * Creates a linear layout with one EditText.
     *
     * @param mode     the {@link WindowManager.LayoutParams#softInputMode softInputMode} for the
     *                 activity.
     * @param activity the activity to create the layout for.
     *
     * @return the created layout.
     */
    private LinearLayout createLayout(final int mode, final Activity activity) {
        final var layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);

        final var editText = new EditText(activity);
        editText.setId(EDIT_TEXT_ID);
        editText.setText("Editable");

        final var textView = new TextView(activity);
        textView.setId(TEXT_VIEW_ID);
        textView.setText("Not Editable");

        layout.addView(editText);
        layout.addView(textView);
        editText.requestFocus();
        activity.getWindow().setSoftInputMode(mode);
        return layout;
    }

    /**
     * Waits for the given inset type to be controllable on the given activity's
     * {@link android.view.WindowInsetsController}.
     *
     * @param type     the inset type waiting to be controllable.
     * @param activity the activity whose Window Insets Controller to wait on.
     *
     * @implNote This is used to avoid the case where
     * {@link android.view.InsetsController#show(int)}
     * is called before IME insets control is available, starting a more complex flow which is
     * currently harder to track with the {@link com.android.server.inputmethod.ImeTrackerService}
     * system.
     *
     * TODO(b/263069667): Remove this method when the ImeInsetsSourceConsumer show flow is fixed.
     */
    private void awaitControl(final int type, final Activity activity) {
        final var latch = new CountDownLatch(1);
        final OnControllableInsetsChangedListener listener = (controller, typeMask) -> {
            if ((typeMask & type) != 0) {
                latch.countDown();
            }
        };
        TestUtils.runOnMainSync(() -> activity.getWindow()
                .getDecorView()
                .getWindowInsetsController()
                .addOnControllableInsetsChangedListener(listener));

        try {
            if (!latch.await(TIMEOUT, TimeUnit.SECONDS)) {
                fail("IME insets controls not available");
            }
        } catch (InterruptedException e) {
            fail("Waiting for IMe insets controls to be available failed");
        } finally {
            TestUtils.runOnMainSync(() -> activity.getWindow()
                    .getDecorView()
                    .getWindowInsetsController()
                    .removeOnControllableInsetsChangedListener(listener));
        }
    }

    /**
     * Test the logging for an IME show request from the client.
     */
    @Test
    public void testClientShowImeRequestFinished() throws Throwable {
        verifyLogging(true /* show */,
                List.of(ImeProtoEnums.ORIGIN_CLIENT, ImeProtoEnums.ORIGIN_CLIENT_SHOW_SOFT_INPUT),
                false /* fromImeProcess */, false /* fromUser */,
                (imeSession, activity) -> {
                    awaitControl(WindowInsets.Type.ime(), activity);
                    expectImeInvisible(TIMEOUT);

                    TestUtils.runOnMainSync(() -> activity.getWindow()
                            .getDecorView()
                            .getWindowInsetsController()
                            .show(WindowInsets.Type.ime()));

                    expectImeVisible(TIMEOUT);
                });
    }

    /**
     * Test the logging for an IME hide request from the client.
     */
    @Test
    public void testClientHideImeRequestFinished() throws Exception {
        verifyLogging(false /* show */,
                List.of(ImeProtoEnums.ORIGIN_CLIENT, ImeProtoEnums.ORIGIN_CLIENT_HIDE_SOFT_INPUT),
                false /* fromImeProcess */, false /* fromUser */,
                (imeSession, activity) -> {
                    TestUtils.runOnMainSync(() -> activity.getWindow()
                            .getDecorView()
                            .getWindowInsetsController()
                            .hide(WindowInsets.Type.ime()));

                    expectImeInvisible(TIMEOUT);
                });
    }

    /**
     * Test the logging for an IME show request from the server.
     */
    @Test
    public void testServerShowImeRequestFinished() throws Exception {
        verifyLogging(true /* show */,
                List.of(ImeProtoEnums.ORIGIN_SERVER, ImeProtoEnums.ORIGIN_SERVER_START_INPUT),
                false /* fromImeProcess */, false /* fromUser */,
                (imeSession, activity) -> {
                    createTestActivity(SOFT_INPUT_STATE_ALWAYS_VISIBLE);

                    expectImeVisible(TIMEOUT);
                });
    }

    /**
     * Test the logging for an IME hide request from the server.
     */
    @Test
    public void testServerHideImeRequestFinished() throws Exception {
        verifyLogging(false /* show */,
                List.of(ImeProtoEnums.ORIGIN_SERVER, ImeProtoEnums.ORIGIN_SERVER_HIDE_INPUT),
                false /* fromImeProcess */, false /* fromUser */,
                (imeSession, activity) -> {
                    imeSession.hideSoftInputFromServerForTest();

                    expectImeInvisible(TIMEOUT);
                });
    }

    /**
     * Test the logging for an IME show request from the IME.
     */
    @Test
    public void testImeShowImeRequestFinished() throws Exception {
        // In the past, the origin of this request was considered in the server.
        verifyLogging(true /* show */,
                List.of(ImeProtoEnums.ORIGIN_IME, ImeProtoEnums.ORIGIN_SERVER_START_INPUT),
                true /* fromImeProcess */, false /* fromUser */,
                (imeSession, activity) -> {
                    imeSession.callRequestShowSelf(0 /* flags */);

                    expectImeVisible(TIMEOUT);
                });

    }

    /**
     * Test the logging for an IME hide request from the IME.
     */
    @Test
    public void testImeHideImeRequestFinished() throws Exception {
        verifyLogging(false /* show */,
                List.of(ImeProtoEnums.ORIGIN_IME, ImeProtoEnums.ORIGIN_SERVER_HIDE_INPUT),
                true /* fromImeProcess */, false /* fromUser */,
                (imeSession, activity) -> {
                    imeSession.callRequestHideSelf(0 /* flags */);

                    expectImeInvisible(TIMEOUT);
                });
    }

    /**
     * Test the logging for an IME show request from a user interaction using InputMethodManager.
     */
    @Test
    public void testFromUser_withImm_showImeRequestFinished() throws Exception {
        verifyLogging(true /* show */,
                List.of(ImeProtoEnums.ORIGIN_CLIENT, ImeProtoEnums.ORIGIN_CLIENT_SHOW_SOFT_INPUT),
                false /* fromImeProcess */, true /* fromUser */,
                (imeSession, activity) -> {
                    final EditText editText = activity.requireViewById(EDIT_TEXT_ID);
                    editText.setShowSoftInputOnFocus(false);
                    // onClickListener is run later, so ViewRootImpl#isHandlingPointeEvent will
                    // be false. onTouchListener runs immediately, so the value will be true.
                    editText.setOnTouchListener((v, ev) -> {
                        // Three motion events are sent, only react to one of them.
                        if (ev.getAction() != MotionEvent.ACTION_DOWN) {
                            return false;
                        }
                        editText.getContext().getSystemService(InputMethodManager.class)
                                .showSoftInput(editText, 0 /* flags */);
                        return true;
                    });
                    mCtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, null, editText);

                    expectImeVisible(TIMEOUT);
                });
    }

    /**
     * Test the logging for an IME hide request from a user interaction using InputMethodManager.
     */
    @Test
    public void testFromUser_withImm_hideImeRequestFinished() throws Exception {
        verifyLogging(false /* show */,
                List.of(ImeProtoEnums.ORIGIN_CLIENT, ImeProtoEnums.ORIGIN_CLIENT_HIDE_SOFT_INPUT),
                false /* fromImeProcess */, true /* formUser */,
                (imeSession, activity) -> {
                    final TextView textView = activity.requireViewById(TEXT_VIEW_ID);
                    // onClickListener is run later, so ViewRootImpl#isHandlingPointeEvent will
                    // be false. onTouchListener runs immediately, so the value will be true.
                    textView.setOnTouchListener((v, ev) -> {
                        // Three motion events are sent, only react to one of them.
                        if (ev.getActionMasked() != MotionEvent.ACTION_DOWN) {
                            return false;
                        }
                        textView.getContext().getSystemService(InputMethodManager.class)
                                .hideSoftInputFromWindow(textView.getWindowToken(), 0 /* flags */);
                        return true;
                    });
                    mCtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, null, textView);

                    expectImeInvisible(TIMEOUT);
                });
    }

    /**
     * Test the logging for an IME show request from a user interaction using
     * WindowInsetsController.
     */
    @Test
    public void testFromUser_withWic_showImeRequestFinished() throws Exception {
        verifyLogging(true /* show */,
                List.of(ImeProtoEnums.ORIGIN_CLIENT, ImeProtoEnums.ORIGIN_CLIENT_SHOW_SOFT_INPUT),
                false /* fromImeProcess */, true /* fromUser */,
                (imeSession, activity) -> {
                    final EditText editText = activity.requireViewById(EDIT_TEXT_ID);
                    editText.setShowSoftInputOnFocus(false);
                    // onClickListener is run later, so ViewRootImpl#isHandlingPointeEvent will
                    // be false. onTouchListener runs immediately, so the value will be true.
                    editText.setOnTouchListener((v, ev) -> {
                        // Three motion events are sent, only react to one of them.
                        if (ev.getActionMasked() != MotionEvent.ACTION_DOWN) {
                            return false;
                        }
                        activity.getWindow().getInsetsController().show(WindowInsets.Type.ime());
                        return true;
                    });
                    mCtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, null, editText);

                    expectImeVisible(TIMEOUT);
                });
    }

    /**
     * Test the logging for an IME hide request from a user interaction using
     * WindowInsetsController.
     */
    @Test
    public void testFromUser_withWic_hideImeRequestFinished() throws Exception {
        verifyLogging(false /* show */,
                List.of(ImeProtoEnums.ORIGIN_CLIENT, ImeProtoEnums.ORIGIN_CLIENT_HIDE_SOFT_INPUT),
                false /* fromImeProcess */, true /* fromUser */,
                (imeSession, activity) -> {
                    final TextView textView = activity.requireViewById(TEXT_VIEW_ID);
                    // onClickListener is run later, so ViewRootImpl#isHandlingPointeEvent will
                    // be false. onTouchListener runs immediately, so the value will be true.
                    textView.setOnTouchListener((v, ev) -> {
                        // Three motion events are sent, only react to one of them.
                        if (ev.getActionMasked() != MotionEvent.ACTION_DOWN) {
                            return false;
                        }
                        activity.getWindow().getInsetsController().hide(WindowInsets.Type.ime());
                        return true;
                    });
                    mCtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, null, textView);

                    expectImeInvisible(TIMEOUT);
                });
    }

    /**
     * Test the logging for an IME hide request from a user interaction using back button press.
     */
    @Test
    public void testFromUser_withBackPress_hideImeRequestFinished() throws Exception {
        verifyLogging(false /* show */,
                List.of(ImeProtoEnums.ORIGIN_IME, ImeProtoEnums.ORIGIN_SERVER_HIDE_INPUT),
                true /* fromImeProcess */, true /* fromUser */, (imeSession, activity) -> {
                    UiDevice.getInstance(mInstrumentation)
                            .pressBack();

                    expectImeInvisible(TIMEOUT);
                });
    }

    /**
     * Verifies the logged atom events for the given test runnable and expected values.
     *
     * @param show           whether this is testing a show request (starts with IME hidden),
     *                       or hide request (starts with IME shown).
     * @param origins        the expected IME request origins. This is a list of possible origins,
     *                       to also allow previously deprecated ones.
     * @param fromImeProcess whether this request is expected to be created in the IME process,
     *                       or the test app process.
     * @param fromUser       whether this request is expected to be created from user interaction.
     * @param runnable       the runnable with the test code to execute.
     */
    private void verifyLogging(boolean show, @NonNull List<Integer> origins, boolean fromImeProcess,
            boolean fromUser, @NonNull TestRunnable runnable) throws Exception {
        // Create mockImeSession to decouple from real IMEs,
        // and enable calling expectImeVisible and expectImeInvisible.
        try (var imeSession = MockImeSession.create(
                mInstrumentation.getContext(),
                mInstrumentation.getUiAutomation(),
                new ImeSettings.Builder())) {
            // Wait for any outstanding IME requests to finish, to not interfere with test.
            PollingCheck.waitFor(() -> !imeSession.hasPendingImeVisibilityRequests(),
                    "Test Setup Failed: There should be no pending IME requests present when the "
                            + "test starts.");

            // Expect atoms pushed from either from the IME process, or from the test app process.
            MetricsRecorder.uploadConfigForPushedAtomWithUid(
                    fromImeProcess ? imeSession.getMockImePackageName() : mPkgName,
                    AtomsProto.Atom.IME_REQUEST_FINISHED_FIELD_NUMBER,
                    false /* useUidAttributionChain */);

            final TestActivity activity;
            if (show) {
                // Use STATE_UNCHANGED to not trigger any other IME requests.
                activity = createTestActivity(SOFT_INPUT_STATE_UNCHANGED);
                expectImeInvisible(TIMEOUT);
            } else {
                // If running a hide test, start with the IME showing already.
                activity = createTestActivity(SOFT_INPUT_STATE_ALWAYS_VISIBLE);
                expectImeVisible(TIMEOUT);
                // Wait for any outstanding IME requests to finish, to capture all atoms.
                PollingCheck.waitFor(() -> !imeSession.hasPendingImeVisibilityRequests(),
                        "Test Error: Pending IME requests took too long, likely timing out.");

                // Remove logs for the show requests.
                MetricsRecorder.clearReports();
            }

            // Run the given test.
            runnable.run(imeSession, activity);

            // Wait for any outstanding IME requests to finish, to capture all atoms.
            PollingCheck.waitFor(() -> !imeSession.hasPendingImeVisibilityRequests(),
                    "Test Error: Pending IME requests took too long, likely timing out.");

            // Must have at least one atom received.
            final var data = MetricsRecorder.getEventMetricDataList();
            assertWithMessage("Number of atoms logged")
                    .that(data.size())
                    .isAtLeast(1);

            // Check received atom data.
            try {
                int successfulAtoms = 0;
                for (int i = 0; i < data.size(); i++) {
                    final var atom = data.get(i).atom;
                    assertThat(atom).isNotNull();

                    final var imeRequestFinished = atom.getImeRequestFinished();
                    assertThat(imeRequestFinished).isNotNull();

                    // Skip cancelled requests.
                    if (imeRequestFinished.status == ImeProtoEnums.STATUS_CANCEL) continue;

                    successfulAtoms++;

                    assertWithMessage("Ime Request type")
                            .that(imeRequestFinished.type)
                            .isEqualTo(show ? ImeProtoEnums.TYPE_SHOW : ImeProtoEnums.TYPE_HIDE);
                    assertWithMessage("Ime Request status")
                            .that(imeRequestFinished.status)
                            .isEqualTo(ImeProtoEnums.STATUS_SUCCESS);
                    assertWithMessage("Ime Request origin")
                            .that(imeRequestFinished.origin)
                            .isIn(origins);
                    if (fromUser) {
                        // Assert only when fromUser was expected to be true.
                        assertWithMessage("Ime Request fromUser")
                                .that(imeRequestFinished.fromUser)
                                .isEqualTo(true);
                    }
                }

                // Must have at least one successful request received.
                assertWithMessage("Number of successful atoms logged")
                        .that(successfulAtoms)
                        .isAtLeast(1);
            } catch (AssertionError e) {
                throw new AssertionError(e.getMessage() + "\natoms data:\n" + data, e);
            }
        }
    }

    /** Interface for the test code to be ran. */
    private interface TestRunnable {

        /**
         * Execute the given test code given the ime session and activity.
         *
         * @param imeSession the initialized mock ime session.
         * @param activity   the initialized test activity.
         */
        void run(@NonNull MockImeSession imeSession, @NonNull TestActivity activity);
    }
}
