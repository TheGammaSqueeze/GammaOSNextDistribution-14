/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static android.provider.InputMethodManagerDeviceConfig.KEY_HIDE_IME_WHEN_NO_EDITOR_FOCUS;
import static android.view.WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
import static android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN;
import static android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE;
import static android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN;
import static android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED;
import static android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED;
import static android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE;
import static android.view.inputmethod.cts.util.InputMethodVisibilityVerifier.expectImeInvisible;
import static android.view.inputmethod.cts.util.InputMethodVisibilityVerifier.expectImeVisible;
import static android.view.inputmethod.cts.util.TestUtils.getOnMainSync;
import static android.view.inputmethod.cts.util.TestUtils.runOnMainSync;
import static android.view.inputmethod.cts.util.TestUtils.waitOnMainUntil;
import static android.widget.PopupWindow.INPUT_METHOD_NEEDED;
import static android.widget.PopupWindow.INPUT_METHOD_NOT_NEEDED;

import static com.android.cts.mockime.ImeEventStreamTestUtils.editorMatcher;
import static com.android.cts.mockime.ImeEventStreamTestUtils.editorMatcherRestartingFalse;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectBindInput;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectCommand;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectEvent;
import static com.android.cts.mockime.ImeEventStreamTestUtils.notExpectEvent;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.Manifest;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.provider.DeviceConfig;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.cts.util.AutoCloseableWrapper;
import android.view.inputmethod.cts.util.EndToEndImeTestBase;
import android.view.inputmethod.cts.util.TestActivity;
import android.view.inputmethod.cts.util.TestActivity2;
import android.view.inputmethod.cts.util.TestUtils;
import android.view.inputmethod.cts.util.UnlockScreenRule;
import android.view.inputmethod.cts.util.WindowFocusHandleService;
import android.view.inputmethod.cts.util.WindowFocusStealer;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.test.filters.FlakyTest;
import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.SystemUtil;
import com.android.cts.mockime.ImeCommand;
import com.android.cts.mockime.ImeEvent;
import com.android.cts.mockime.ImeEventStream;
import com.android.cts.mockime.ImeSettings;
import com.android.cts.mockime.MockImeSession;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.testng.Assert;

import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@MediumTest
@RunWith(AndroidJUnit4.class)
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public class FocusHandlingTest extends EndToEndImeTestBase {
    static final long TIMEOUT = TimeUnit.SECONDS.toMillis(5);
    static final long EXPECT_TIMEOUT = TimeUnit.SECONDS.toMillis(2);
    static final long NOT_EXPECT_TIMEOUT = TimeUnit.SECONDS.toMillis(1);

    @Rule
    public final UnlockScreenRule mUnlockScreenRule = new UnlockScreenRule();

    private static final String TEST_MARKER_PREFIX =
            "android.view.inputmethod.cts.FocusHandlingTest";

    public EditText launchTestActivity(String marker) {
        final AtomicReference<EditText> editTextRef = new AtomicReference<>();
        TestActivity.startSync(activity-> {
            final LinearLayout layout = new LinearLayout(activity);
            layout.setOrientation(LinearLayout.VERTICAL);

            final EditText editText = new EditText(activity);
            editText.setPrivateImeOptions(marker);
            editText.setHint("editText");
            editText.requestFocus();
            editTextRef.set(editText);

            layout.addView(editText);
            return layout;
        });
        return editTextRef.get();
    }

    public EditText launchTestActivity(String marker,
            @NonNull AtomicBoolean outEditHasWindowFocusRef) {
        final EditText editText = launchTestActivity(marker);
        editText.post(() -> {
            final ViewTreeObserver observerForEditText = editText.getViewTreeObserver();
            observerForEditText.addOnWindowFocusChangeListener((hasFocus) ->
                    outEditHasWindowFocusRef.set(editText.hasWindowFocus()));
            outEditHasWindowFocusRef.set(editText.hasWindowFocus());
        });
        return editText;
    }

    private static String getTestMarker() {
        return TEST_MARKER_PREFIX + "/"  + SystemClock.elapsedRealtimeNanos();
    }

    @FlakyTest(bugId = 149246840)
    @Test
    public void testOnStartInputCalledOnceIme() throws Exception {
        try (MockImeSession imeSession = createTestImeSession()) {
            final ImeEventStream stream = imeSession.openEventStream();

            final String marker = getTestMarker();
            final EditText editText = launchTestActivity(marker);

            // Wait until the MockIme gets bound to the TestActivity.
            expectBindInput(stream, Process.myPid(), TIMEOUT);

            // Emulate tap event
            mCtsTouchUtils.emulateTapOnViewCenter(
                    InstrumentationRegistry.getInstrumentation(), null, editText);

            // Wait until "onStartInput" gets called for the EditText.
            final ImeEvent onStart =
                    expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);

            assertFalse(stream.dump(), onStart.getEnterState().hasFallbackInputConnection());
            assertFalse(stream.dump(), onStart.getArguments().getBoolean("restarting"));

            // There shouldn't be onStartInput any more.
            notExpectEvent(stream, editorMatcherRestartingFalse("onStartInput", marker),
                           NOT_EXPECT_TIMEOUT);
        }
    }

    @Test
    public void testSwitchingBetweenEquivalentNonEditableViews() throws Exception {
        // When avoidable IME prevention is enabled, the onStartInput calls do not happen
        // TODO(b/240260832): Refine the assumption when testing a non-preemptible IME.
        Assume.assumeFalse(isPreventImeStartup());
        try (MockImeSession imeSession = createTestImeSession()) {
            final ImeEventStream stream = imeSession.openEventStream();

            // "onStartInput" with a fallback InputConnection for StateInitializeActivity.
            // "debug.imm.optimize_noneditable_views" doesn't prevent startInput when it has
            // WINDOW_GAINED_FOCUS flag.
            expectEvent(stream, event -> "onStartInput".equals(event.getEventName())
                    && event.getEnterState().hasFallbackInputConnection(), EXPECT_TIMEOUT);

            final AtomicReference<TextView> viewRef1 = new AtomicReference<>();
            final AtomicReference<TextView> viewRef2 = new AtomicReference<>();
            final BlockingQueue<KeyEvent> keyEvents = new LinkedBlockingQueue<>();

            final TestActivity testActivity = TestActivity.startSync(activity -> {
                final LinearLayout layout = new LinearLayout(activity);
                layout.setOrientation(LinearLayout.VERTICAL);

                final TextView view1 = new Button(activity);
                view1.setText("View 1");
                layout.addView(view1);
                view1.setFocusableInTouchMode(true);
                viewRef1.set(view1);

                final TextView view2 = new Button(activity) {
                    @Override
                    public boolean dispatchKeyEvent(KeyEvent event) {
                        keyEvents.add(event);
                        return super.dispatchKeyEvent(event);
                    }
                };
                view2.setText("View 2");
                layout.addView(view2);
                view2.setFocusableInTouchMode(true);
                viewRef2.set(view2);

                return layout;
            });

            // "onStartInput" with a fallback InputConnection for TestActivity.
            // "debug.imm.optimize_noneditable_views" doesn't prevent startInput when it has
            // WINDOW_GAINED_FOCUS flag.
            expectEvent(stream, event -> "onStartInput".equals(event.getEventName())
                    && event.getEnterState().hasFallbackInputConnection(), EXPECT_TIMEOUT);

            // The focus change below still triggers "onStartInput".
            // "debug.imm.optimize_noneditable_views" doesn't prevent startInput when
            // StartInputReason is different.
            testActivity.runOnUiThread(() -> viewRef1.get().requestFocus());
            expectEvent(stream, event -> "onStartInput".equals(event.getEventName())
                    && event.getEnterState().hasFallbackInputConnection(), EXPECT_TIMEOUT);

            // If optimization is enabled, we do not expect another call to "onStartInput" after
            // a view focus change.
            testActivity.runOnUiThread(() -> viewRef2.get().requestFocus());
            if (SystemProperties.getBoolean("debug.imm.optimize_noneditable_views", true)) {
                notExpectEvent(stream, event -> "onStartInput".equals(event.getEventName())
                        && event.getEnterState().hasFallbackInputConnection(), NOT_EXPECT_TIMEOUT);
            } else {
                expectEvent(stream, event -> "onStartInput".equals(event.getEventName())
                        && event.getEnterState().hasFallbackInputConnection(), EXPECT_TIMEOUT);
            }

            // Force show the IME and expect it to come up
            testActivity.runOnUiThread(() ->
                    viewRef1.get().getWindowInsetsController().show(WindowInsets.Type.ime()));
            expectEvent(stream, event -> "showSoftInput".equals(event.getEventName()),
                    EXPECT_TIMEOUT);

            final String testInput = "Test";
            final ImeCommand commitText = imeSession.callCommitText(testInput, 0);
            expectCommand(stream, commitText, EXPECT_TIMEOUT);

            waitOnMainUntil(() -> !keyEvents.isEmpty(), EXPECT_TIMEOUT);
            Assert.assertEquals(keyEvents.size(), 1, "Expecting exactly one key event!");
            final KeyEvent keyEvent = keyEvents.remove();
            Assert.assertEquals(keyEvent.getAction(), KeyEvent.ACTION_MULTIPLE);
            Assert.assertEquals(keyEvent.getKeyCode(), KeyEvent.KEYCODE_UNKNOWN);
            Assert.assertEquals(keyEvent.getCharacters(), testInput);
        }
    }

    @Test
    public void testSoftInputStateAlwaysVisibleWithoutFocusedEditorView() throws Exception {
        try (MockImeSession imeSession = createTestImeSession()) {
            final ImeEventStream stream = imeSession.openEventStream();

            final String marker = getTestMarker();
            final TestActivity testActivity = TestActivity.startSync(activity -> {
                final LinearLayout layout = new LinearLayout(activity);
                layout.setOrientation(LinearLayout.VERTICAL);

                final TextView textView = new TextView(activity) {
                    @Override
                    public boolean onCheckIsTextEditor() {
                        return false;
                    }
                };
                textView.setText("textView");
                textView.setPrivateImeOptions(marker);
                textView.requestFocus();

                activity.getWindow().setSoftInputMode(SOFT_INPUT_STATE_ALWAYS_VISIBLE);
                layout.addView(textView);
                return layout;
            });

            if (testActivity.getApplicationInfo().targetSdkVersion >= Build.VERSION_CODES.P) {
                // Input shouldn't start
                notExpectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);
                // There shouldn't be onStartInput because the focused view is not an editor.
                notExpectEvent(stream, event -> "showSoftInput".equals(event.getEventName()),
                        TIMEOUT);
            } else {
                // Wait until the MockIme gets bound to the TestActivity.
                expectBindInput(stream, Process.myPid(), TIMEOUT);
                // For apps that target pre-P devices, onStartInput() should be called.
                expectEvent(stream, event -> "showSoftInput".equals(event.getEventName()), TIMEOUT);
            }
        }
    }

    @Test
    public void testNoEditorNoStartInput() throws Exception {
        Assume.assumeTrue(isPreventImeStartup());
        try (MockImeSession imeSession = createTestImeSession()) {
            final ImeEventStream stream = imeSession.openEventStream();

            final String marker = getTestMarker();
            TestActivity.startSync(activity -> {
                final LinearLayout layout = new LinearLayout(activity);
                layout.setOrientation(LinearLayout.VERTICAL);

                final TextView textView = new TextView(activity) {
                    @Override
                    public boolean onCheckIsTextEditor() {
                        return false;
                    }
                };
                textView.setText("textView");
                textView.requestFocus();
                textView.setPrivateImeOptions(marker);
                layout.addView(textView);
                return layout;
            });

            // Input shouldn't start
            notExpectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);
        }
    }

    @Test
    public void testDelayedAddEditorStartsInput() throws Exception {
        Assume.assumeTrue(isPreventImeStartup());
        try (MockImeSession imeSession = createTestImeSession()) {
            final ImeEventStream stream = imeSession.openEventStream();

            final AtomicReference<LinearLayout> layoutRef = new AtomicReference<>();
            final TestActivity testActivity = TestActivity.startSync(activity -> {
                final LinearLayout layout = new LinearLayout(activity);
                layout.setOrientation(LinearLayout.VERTICAL);
                layoutRef.set(layout);

                return layout;
            });

            // Activity adds EditText at a later point.
            TestUtils.waitOnMainUntil(() -> layoutRef.get().hasWindowFocus(), TIMEOUT);
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            final String marker = getTestMarker();
            testActivity.runOnUiThread(() -> {
                final EditText editText = new EditText(testActivity);
                editText.setText("Editable");
                editText.setPrivateImeOptions(marker);
                layoutRef.get().addView(editText);
                editText.requestFocus();
            });

            // Input should start
            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);
        }
    }

    @Test
    public void testEditorStartsInput() throws Exception {
        try (MockImeSession imeSession = createTestImeSession()) {
            final ImeEventStream stream = imeSession.openEventStream();

            final String marker = getTestMarker();
            TestActivity.startSync(activity -> {
                final LinearLayout layout = new LinearLayout(activity);
                layout.setOrientation(LinearLayout.VERTICAL);

                final EditText editText = new EditText(activity);
                editText.setPrivateImeOptions(marker);
                editText.setText("Editable");
                editText.requestFocus();
                layout.addView(editText);
                return layout;
            });

            // Input should start
            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);
        }
    }

    @Test
    public void testSoftInputStateAlwaysVisibleFocusedEditorView() throws Exception {
        try (MockImeSession imeSession = createTestImeSession()) {
            final ImeEventStream stream = imeSession.openEventStream();

            TestActivity.startSync(activity -> {
                final LinearLayout layout = new LinearLayout(activity);
                layout.setOrientation(LinearLayout.VERTICAL);

                final EditText editText = new EditText(activity);
                editText.setText("editText");
                editText.requestFocus();

                activity.getWindow().setSoftInputMode(SOFT_INPUT_STATE_ALWAYS_VISIBLE);
                layout.addView(editText);
                return layout;
            });

            // Wait until the MockIme gets bound to the TestActivity.
            expectBindInput(stream, Process.myPid(), TIMEOUT);

            expectEvent(stream, event -> "showSoftInput".equals(event.getEventName()), TIMEOUT);
        }
    }

    @ApiTest(apis = {"android.inputmethodservice.InputMethodService#showSoftInput"})
    @FlakyTest
    @Test
    public void testSoftInputStateAlwaysVisibleFocusEditorAfterLaunch() throws Exception {
        Assume.assumeFalse(isPreventImeStartup());
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        try (MockImeSession imeSession = createTestImeSession()) {
            final ImeEventStream stream = imeSession.openEventStream();

            // Launch a test activity with STATE_ALWAYS_VISIBLE without requesting editor focus.
            AtomicReference<EditText> editTextRef = new AtomicReference<>();
            TestActivity.startSync(activity -> {
                final LinearLayout layout = new LinearLayout(activity);
                layout.setOrientation(LinearLayout.VERTICAL);

                final EditText editText = new EditText(activity);
                editTextRef.set(editText);
                editText.setText("editText");
                activity.getWindow().setSoftInputMode(SOFT_INPUT_STATE_ALWAYS_VISIBLE);
                layout.addView(editText);
                return layout;
            });

            // Wait until the MockIme gets bound to the TestActivity.
            expectBindInput(stream, Process.myPid(), TIMEOUT);

            // Not expect showSoftInput called when the editor not yet focused.
            notExpectEvent(stream, event -> "showSoftInput".equals(event.getEventName()),
                    NOT_EXPECT_TIMEOUT);

            // Expect showSoftInput called when the editor is focused.
            instrumentation.runOnMainSync(editTextRef.get()::requestFocus);
            mCtsTouchUtils.emulateTapOnViewCenter(instrumentation, null, editTextRef.get());
            assertTrue(TestUtils.getOnMainSync(() -> editTextRef.get().hasFocus()
                    && editTextRef.get().hasWindowFocus()));
            expectEvent(stream, event -> "showSoftInput".equals(event.getEventName()), TIMEOUT);
        }
    }

    /**
     * Makes sure that an existing {@link android.view.inputmethod.InputConnection} will not be
     * invalidated by showing a focusable {@link PopupWindow} with
     * {@link PopupWindow#INPUT_METHOD_NOT_NEEDED}.
     *
     * <p>If {@link android.view.WindowManager.LayoutParams#FLAG_ALT_FOCUSABLE_IM} is set and
     * {@link android.view.WindowManager.LayoutParams#FLAG_NOT_FOCUSABLE} is not set to a
     * {@link android.view.Window}, showing that window must not invalidate an existing valid
     * {@link android.view.inputmethod.InputConnection}.</p>
     *
     * @see android.view.WindowManager.LayoutParams#mayUseInputMethod(int)
     */
    @Test
    public void testFocusableWindowDoesNotInvalidateExistingInputConnection() throws Exception {
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        try (MockImeSession imeSession = createTestImeSession()) {
            final ImeEventStream stream = imeSession.openEventStream();

            final String marker = getTestMarker();
            final EditText editText = launchTestActivity(marker);
            instrumentation.runOnMainSync(editText::requestFocus);

            // Wait until the MockIme gets bound to the TestActivity.
            expectBindInput(stream, Process.myPid(), TIMEOUT);

            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);

            // Make sure that InputConnection#commitText() works.
            final ImeCommand commit1 = imeSession.callCommitText("test commit", 1);
            expectCommand(stream, commit1, TIMEOUT);
            TestUtils.waitOnMainUntil(
                    () -> TextUtils.equals(editText.getText(), "test commit"), TIMEOUT);
            instrumentation.runOnMainSync(() -> editText.setText(""));

            // Create then show a popup window that cannot be the IME target.
            try (AutoCloseableWrapper<PopupWindow> popupWindowWrapper = AutoCloseableWrapper.create(
                TestUtils.getOnMainSync(() -> {
                    final Context context = instrumentation.getTargetContext();
                    final PopupWindow popup = new PopupWindow(context);
                    popup.setFocusable(true);
                    popup.setInputMethodMode(INPUT_METHOD_NOT_NEEDED);
                    final TextView textView = new TextView(context);
                    textView.setText("Test Text");
                    popup.setContentView(textView);
                    popup.showAsDropDown(editText);
                    return popup;
                }), popupWindow -> runOnMainSync(popupWindow::dismiss))
            ) {
                instrumentation.waitForIdleSync();

                // Make sure that the EditText no longer has window-focus
                TestUtils.waitOnMainUntil(() -> !editText.hasWindowFocus(), TIMEOUT);

                // Make sure that InputConnection#commitText() works.
                final ImeCommand commit2 = imeSession.callCommitText("Hello!", 1);
                expectCommand(stream, commit2, TIMEOUT);
                TestUtils.waitOnMainUntil(
                        () -> TextUtils.equals(editText.getText(), "Hello!"), TIMEOUT);
                instrumentation.runOnMainSync(() -> editText.setText(""));

                stream.skipAll();

                final String marker2 = getTestMarker();
                // Call InputMethodManager#restartInput()
                instrumentation.runOnMainSync(() -> {
                    editText.setPrivateImeOptions(marker2);
                    editText.getContext()
                            .getSystemService(InputMethodManager.class)
                            .restartInput(editText);
                });

                // Make sure that onStartInput() is called with restarting == true.
                expectEvent(stream, event -> {
                    if (!TextUtils.equals("onStartInput", event.getEventName())) {
                        return false;
                    }
                    if (!event.getArguments().getBoolean("restarting")) {
                        return false;
                    }
                    final EditorInfo editorInfo = event.getArguments().getParcelable("editorInfo");
                    return TextUtils.equals(marker2, editorInfo.privateImeOptions);
                }, TIMEOUT);

                // Make sure that InputConnection#commitText() works.
                final ImeCommand commit3 = imeSession.callCommitText("World!", 1);
                expectCommand(stream, commit3, TIMEOUT);
                TestUtils.waitOnMainUntil(
                        () -> TextUtils.equals(editText.getText(), "World!"), TIMEOUT);
                instrumentation.runOnMainSync(() -> editText.setText(""));
            }

            instrumentation.waitForIdleSync();

            // Make sure that the EditText now has window-focus again.
            TestUtils.waitOnMainUntil(editText::hasWindowFocus, TIMEOUT);

            // Make sure that InputConnection#commitText() works.
            final ImeCommand commit4 = imeSession.callCommitText("Done!", 1);
            expectCommand(stream, commit4, TIMEOUT);
            TestUtils.waitOnMainUntil(
                    () -> TextUtils.equals(editText.getText(), "Done!"), TIMEOUT);
            instrumentation.runOnMainSync(() -> editText.setText(""));
        }
    }

    /**
     * Test case for Bug 152698568.
     *
     * <p>This test ensures that showing a non-focusable {@link PopupWindow} with
     * {@link PopupWindow#INPUT_METHOD_NEEDED} does not affect IME visibility.</p>
     */
    @Test
    public void testNonFocusablePopupWindowDoesNotAffectImeVisibility() throws Exception {
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        try (MockImeSession imeSession = createTestImeSession()) {
            final ImeEventStream stream = imeSession.openEventStream();

            final String marker = getTestMarker();
            final EditText editText = launchTestActivity(marker);

            // Wait until the MockIme is connected to the edit text.
            runOnMainSync(editText::requestFocus);
            expectBindInput(stream, Process.myPid(), TIMEOUT);
            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);

            expectImeInvisible(TIMEOUT);

            // Show IME.
            runOnMainSync(() -> editText.getContext().getSystemService(InputMethodManager.class)
                    .showSoftInput(editText, 0));

            expectEvent(stream, editorMatcher("onStartInputView", marker), TIMEOUT);
            expectImeVisible(TIMEOUT);

            // Create then show a non-focusable PopupWindow with INPUT_METHOD_NEEDED.
            try (AutoCloseableWrapper<PopupWindow> popupWindowWrapper = AutoCloseableWrapper.create(
                TestUtils.getOnMainSync(() -> {
                    final Context context = instrumentation.getTargetContext();
                    final PopupWindow popup = new PopupWindow(context);
                    popup.setFocusable(false);
                    popup.setInputMethodMode(INPUT_METHOD_NEEDED);
                    final TextView textView = new TextView(context);
                    textView.setText("Popup");
                    popup.setContentView(textView);
                    // Show the popup window.
                    popup.showAsDropDown(editText);
                    return popup;
                }), popup -> TestUtils.runOnMainSync(popup::dismiss))
            ) {
                instrumentation.waitForIdleSync();

                // Make sure that the IME remains to be visible.
                expectImeVisible(TIMEOUT);

                SystemClock.sleep(NOT_EXPECT_TIMEOUT);

                // Make sure that the IME remains to be visible.
                expectImeVisible(TIMEOUT);
            }
        }
    }

    /**
     * Test case for Bug 70629102.
     *
     * {@link InputMethodManager#restartInput(View)} can be called even when another process
     * temporarily owns focused window. {@link InputMethodManager} should continue to work after
     * the IME target application gains window focus again.
     */
    @Test
    public void testRestartInputWhileOtherProcessHasWindowFocus() throws Exception {
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        try (MockImeSession imeSession = createTestImeSession()) {
            final ImeEventStream stream = imeSession.openEventStream();

            final String marker = getTestMarker();
            final EditText editText = launchTestActivity(marker);
            instrumentation.runOnMainSync(editText::requestFocus);

            // Wait until the MockIme gets bound to the TestActivity.
            expectBindInput(stream, Process.myPid(), TIMEOUT);

            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);

            // Get app window token
            final IBinder appWindowToken = TestUtils.getOnMainSync(
                    editText::getApplicationWindowToken);

            try (WindowFocusStealer focusStealer =
                         WindowFocusStealer.connect(instrumentation.getTargetContext(), TIMEOUT)) {

                focusStealer.stealWindowFocus(appWindowToken, TIMEOUT);

                // Wait until the edit text loses window focus.
                TestUtils.waitOnMainUntil(() -> !editText.hasWindowFocus(), TIMEOUT);

                // Call InputMethodManager#restartInput()
                instrumentation.runOnMainSync(() -> {
                    editText.getContext()
                            .getSystemService(InputMethodManager.class)
                            .restartInput(editText);
                });
            }

            // Wait until the edit text gains window focus again.
            TestUtils.waitOnMainUntil(editText::hasWindowFocus, TIMEOUT);

            // Make sure that InputConnection#commitText() still works.
            final ImeCommand command = imeSession.callCommitText("test commit", 1);
            expectCommand(stream, command, TIMEOUT);

            TestUtils.waitOnMainUntil(
                    () -> TextUtils.equals(editText.getText(), "test commit"), TIMEOUT);
        }
    }

    /**
     * Test {@link EditText#setShowSoftInputOnFocus(boolean)}.
     */
    @Test
    public void testSetShowInputOnFocus() throws Exception {
        try (MockImeSession imeSession = createTestImeSession()) {
            final ImeEventStream stream = imeSession.openEventStream();

            final String marker = getTestMarker();
            final EditText editText = launchTestActivity(marker);
            runOnMainSync(() -> editText.setShowSoftInputOnFocus(false));

            // Wait until "onStartInput" gets called for the EditText.
            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);

            // Emulate tap event
            mCtsTouchUtils.emulateTapOnViewCenter(
                    InstrumentationRegistry.getInstrumentation(), null, editText);

            // "showSoftInput" must not happen when setShowSoftInputOnFocus(false) is called.
            notExpectEvent(stream, event -> "showSoftInput".equals(event.getEventName()),
                    NOT_EXPECT_TIMEOUT);
        }
    }

    @AppModeFull(reason = "Instant apps cannot hold android.permission.SYSTEM_ALERT_WINDOW")
    @Test
    public void testMultiWindowFocusHandleOnDifferentUiThread() throws Exception {
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        try (CloseOnce session = CloseOnce.of(new ServiceSession(instrumentation));
             MockImeSession imeSession = createTestImeSession()) {
            final ImeEventStream stream = imeSession.openEventStream();
            final AtomicBoolean popupTextHasWindowFocus = new AtomicBoolean(false);
            final AtomicBoolean popupTextHasViewFocus = new AtomicBoolean(false);
            final AtomicBoolean editTextHasWindowFocus = new AtomicBoolean(false);

            // Start a TestActivity and verify the edit text will receive focus and keyboard shown.
            final String marker = getTestMarker();
            final EditText editText = launchTestActivity(marker, editTextHasWindowFocus);

            // Wait until the MockIme gets bound to the TestActivity.
            expectBindInput(stream, Process.myPid(), TIMEOUT);

            // Emulate tap event
            mCtsTouchUtils.emulateTapOnViewCenter(instrumentation, null, editText);
            TestUtils.waitOnMainUntil(editTextHasWindowFocus::get, TIMEOUT);

            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);
            expectEvent(stream, event -> "showSoftInput".equals(event.getEventName()), TIMEOUT);

            // Create a popupTextView which from Service with different UI thread.
            final ServiceSession serviceSession = (ServiceSession) session.mAutoCloseable;
            final EditText popupTextView = serviceSession.getService().getPopupTextView(
                    popupTextHasWindowFocus);
            assertNotSame(popupTextView.getHandler().getLooper(),
                    serviceSession.getService().getMainLooper());

            // Verify popupTextView will also receive window focus change and soft keyboard shown
            // after tapping the view.
            final String marker1 = getTestMarker();
            popupTextView.post(() -> {
                popupTextView.setPrivateImeOptions(marker1);
                popupTextHasViewFocus.set(popupTextView.requestFocus());
            });
            TestUtils.waitOnMainUntil(popupTextHasViewFocus::get, TIMEOUT);

            mCtsTouchUtils.emulateTapOnViewCenter(instrumentation, null, popupTextView);
            TestUtils.waitOnMainUntil(() -> popupTextHasWindowFocus.get()
                            && !editTextHasWindowFocus.get(), TIMEOUT);
            expectEvent(stream, editorMatcher("onStartInput", marker1), TIMEOUT);
            expectEvent(stream, event -> "showSoftInput".equals(event.getEventName()), TIMEOUT);

            // Emulate tap event for editText again, verify soft keyboard and window focus will
            // come back.
            mCtsTouchUtils.emulateTapOnViewCenter(instrumentation, null, editText);
            TestUtils.waitOnMainUntil(() -> editTextHasWindowFocus.get()
                    && !popupTextHasWindowFocus.get(), TIMEOUT);
            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);
            expectEvent(stream, event -> "showSoftInput".equals(event.getEventName()), TIMEOUT);

            // Remove the popTextView window and back to test activity, and then verify if
            // commitText is still workable.
            session.close();
            TestUtils.waitOnMainUntil(editText::hasWindowFocus, TIMEOUT);
            final ImeCommand commit = imeSession.callCommitText("test commit", 1);
            expectCommand(stream, commit, TIMEOUT);
            TestUtils.waitOnMainUntil(
                    () -> TextUtils.equals(editText.getText(), "test commit"), TIMEOUT);
        }
    }

    @Test
    public void testKeyboardStateAfterImeFocusableFlagChanged() throws Exception {
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        try (MockImeSession imeSession = createTestImeSession()) {
            final ImeEventStream stream = imeSession.openEventStream();
            final AtomicReference<EditText> editTextRef = new AtomicReference<>();
            final String marker = getTestMarker();
            final TestActivity testActivity = TestActivity.startSync(activity-> {
                // Initially set activity window to not IME focusable.
                activity.getWindow().addFlags(FLAG_ALT_FOCUSABLE_IM);

                final LinearLayout layout = new LinearLayout(activity);
                layout.setOrientation(LinearLayout.VERTICAL);

                final EditText editText = new EditText(activity);
                editText.setPrivateImeOptions(marker);
                editText.setHint("editText");
                editTextRef.set(editText);
                editText.requestFocus();

                layout.addView(editText);
                return layout;
            });

            // Emulate tap event, expect there is no "onStartInput", and "showSoftInput" happened.
            final EditText editText = editTextRef.get();
            mCtsTouchUtils.emulateTapOnViewCenter(instrumentation, null, editText);
            notExpectEvent(stream, editorMatcher("onStartInput", marker), NOT_EXPECT_TIMEOUT);
            notExpectEvent(stream, event -> "showSoftInput".equals(event.getEventName()),
                    NOT_EXPECT_TIMEOUT);

            // Set testActivity window to be IME focusable.
            testActivity.getWindow().getDecorView().post(() -> {
                final WindowManager.LayoutParams params = testActivity.getWindow().getAttributes();
                testActivity.getWindow().clearFlags(FLAG_ALT_FOCUSABLE_IM);
                editTextRef.get().requestFocus();
            });

            // Make sure test activity's window has changed to be IME focusable.
            TestUtils.waitOnMainUntil(() -> WindowManager.LayoutParams.mayUseInputMethod(
                    testActivity.getWindow().getAttributes().flags), TIMEOUT);

            // Emulate tap event again.
            mCtsTouchUtils.emulateTapOnViewCenter(instrumentation, null, editText);
            assertTrue(TestUtils.getOnMainSync(() -> editText.hasFocus()
                    && editText.hasWindowFocus()));

            // "onStartInput", and "showSoftInput" must happen when editText became IME focusable.
            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);
            expectEvent(stream, event -> "showSoftInput".equals(event.getEventName()), TIMEOUT);
        }
    }

    @AppModeFull(reason = "Instant apps cannot hold android.permission.SYSTEM_ALERT_WINDOW")
    @Test
    public void testOnCheckIsTextEditorRunOnUIThread() throws Exception {
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        final CountDownLatch uiThreadSignal = new CountDownLatch(1);
        try (CloseOnce session = CloseOnce.of(new ServiceSession(instrumentation))) {
            final AtomicBoolean popupTextHasWindowFocus = new AtomicBoolean(false);

            // Create a popupTextView which from Service with different UI thread and set a
            // countDownLatch to verify onCheckIsTextEditor run on UI thread.
            final ServiceSession serviceSession = (ServiceSession) session.mAutoCloseable;
            serviceSession.getService().setUiThreadSignal(uiThreadSignal);
            final EditText popupTextView = serviceSession.getService().getPopupTextView(
                    popupTextHasWindowFocus);
            assertTrue(popupTextView.getHandler().getLooper()
                    != serviceSession.getService().getMainLooper());

            // Emulate tap event
            mCtsTouchUtils.emulateTapOnViewCenter(instrumentation, null, popupTextView);

            // Wait until the UI thread countDownLatch reach to 0 or timeout
            assertTrue(uiThreadSignal.await(EXPECT_TIMEOUT, TimeUnit.MILLISECONDS));
        }
    }

    /**
     * Make sure that {@link View#isInEditMode()} will never get called on a non-UI thread even if
     * {@link InputMethodManager#isActive()} is called on a background thread.
     *
     * <p>This is basically a regression test for b/286016109.</p>
     */
    @Test
    public void testOnCheckIsTextEditorRunOnUIThreadWithInputMethodManagerIsActive()
            throws Exception {
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        try (MockImeSession imeSession = MockImeSession.create(
                instrumentation.getContext(),
                instrumentation.getUiAutomation(),
                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();
            final String marker = getTestMarker();
            final AtomicReference<LinearLayout> layoutRef = new AtomicReference<>();

            // Launch test activity
            TestActivity.startSync(activity -> {
                final LinearLayout layout = new LinearLayout(activity);
                layout.setOrientation(LinearLayout.VERTICAL);
                final EditText editText = new EditText(activity);
                editText.setPrivateImeOptions(marker);
                editText.setHint("editText");
                layoutRef.set(layout);
                layout.addView(editText);

                editText.requestFocus();
                return layout;
            });

            // "onStartInput" gets called for the EditText.
            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);

            final HandlerThread backgroundThread = new HandlerThread("testthread");
            backgroundThread.start();

            final AtomicBoolean nonUiThreadCallMade = new AtomicBoolean(false);
            final CountDownLatch latch = new CountDownLatch(1);
            final String marker2 = getTestMarker();
            runOnMainSync(() -> {
                final LinearLayout layout = layoutRef.get();
                final EditText editText2 = new EditText(layout.getContext()) {
                    @Override
                    public boolean onCheckIsTextEditor() {
                        if (!Looper.getMainLooper().isCurrentThread()) {
                            nonUiThreadCallMade.set(true);
                        }
                        return super.onCheckIsTextEditor();
                    }
                };
                editText2.setPrivateImeOptions(marker2);
                layout.addView(editText2);
                editText2.requestFocus();

                final InputMethodManager imm =
                        Objects.requireNonNull(
                                layout.getContext().getSystemService(InputMethodManager.class));
                Handler.createAsync(backgroundThread.getLooper()).post(() -> {
                    // IMM#isActive() is known to have side effect to trigger startInput().
                    // Do this on a background thread to emulate b/286016109
                    imm.isActive();
                    latch.countDown();
                });
            });
            backgroundThread.quitSafely();
            assertTrue(latch.await(TIMEOUT, TimeUnit.MILLISECONDS));

            expectEvent(stream, editorMatcher("onStartInput", marker2), TIMEOUT);
            assertFalse(nonUiThreadCallMade.get());
        }
    }

    @Test
    public void testRequestFocusOnWindowFocusChanged() throws Exception {
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        try (MockImeSession imeSession = createTestImeSession()) {
            final ImeEventStream stream = imeSession.openEventStream();
            final String marker = getTestMarker();
            final AtomicReference<EditText> editTextRef = new AtomicReference<>();

            // Launch test activity
            TestActivity.startSync(activity -> {
                final LinearLayout layout = new LinearLayout(activity);
                layout.setOrientation(LinearLayout.VERTICAL);

                final EditText editText = new EditText(activity);
                editText.setPrivateImeOptions(marker);
                editText.setHint("editText");

                // Request focus when onWindowFocusChanged
                final ViewTreeObserver observer = editText.getViewTreeObserver();
                observer.addOnWindowFocusChangeListener(
                        new ViewTreeObserver.OnWindowFocusChangeListener() {
                            @Override
                            public void onWindowFocusChanged(boolean hasFocus) {
                                editText.requestFocus();
                            }
                        });
                editTextRef.set(editText);
                layout.addView(editText);
                return layout;
            });

            // Emulate tap event
            final EditText editText = editTextRef.get();
            mCtsTouchUtils.emulateTapOnViewCenter(instrumentation, null, editText);

            // "onStartInput" and "showSoftInput" gets called for the EditText.
            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);
            expectEvent(stream, event -> "showSoftInput".equals(event.getEventName()), TIMEOUT);

            // No "hideSoftInput" happened
            notExpectEvent(stream, event -> "hideSoftInput".equals(event.getEventName()),
                    NOT_EXPECT_TIMEOUT);
        }
    }

    /**
     * Start an activity with a focused test editor and wait for the IME to become visible,
     * then start another activity with the given {@code softInputMode} and an <b>unfocused</b>
     * test editor.
     *
     * @return the event stream positioned before the second app is launched
     */
    private ImeEventStream startFocusedEditorActivity_thenAnotherUnfocusedEditorActivity(
            int softInputMode)
            throws Exception {
        try (MockImeSession imeSession = createTestImeSession()) {
            final String marker = getTestMarker();

            // Launch an activity with a text edit and request focus
            TestActivity.startSync(activity -> {
                final LinearLayout layout = new LinearLayout(activity);
                layout.setOrientation(LinearLayout.VERTICAL);

                final EditText editText = new EditText(activity);
                editText.setText("editText");
                editText.setPrivateImeOptions(marker);
                editText.requestFocus();

                activity.getWindow().setSoftInputMode(SOFT_INPUT_STATE_ALWAYS_VISIBLE);
                layout.addView(editText);
                return layout;
            });

            ImeEventStream stream = imeSession.openEventStream();

            // Wait until the MockIme gets bound and started for the TestActivity.
            expectBindInput(stream, Process.myPid(), TIMEOUT);
            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);
            expectImeVisible(TIMEOUT);

            // Skip events relating to showStateInitializeActivity() and TestActivity1
            stream.skipAll();

            // Launch another activity without a text edit but with the requested softInputMode set
            TestActivity2.startSync(activity -> {
                activity.getWindow().setSoftInputMode(softInputMode);

                final LinearLayout layout = new LinearLayout(activity);
                layout.setOrientation(LinearLayout.VERTICAL);

                final EditText editText = new EditText(activity);
                // Do not request focus for the editText
                editText.setText("Unfocused editText");
                layout.addView(editText);
                return layout;
            });

            return stream;
        }
    }

    @Test
    public void testUnfocusedEditor_stateUnspecified_hidesIme() throws Exception {
        ImeEventStream stream = startFocusedEditorActivity_thenAnotherUnfocusedEditorActivity(
                SOFT_INPUT_STATE_UNSPECIFIED);
        expectImeHidden(stream);
        expectOnFinishInput(stream);
    }

    @Test
    public void testUnfocusedEditor_stateHidden_hidesIme() throws Exception {
        Assume.assumeFalse(isPreventImeStartup());
        ImeEventStream stream = startFocusedEditorActivity_thenAnotherUnfocusedEditorActivity(
                SOFT_INPUT_STATE_HIDDEN);
        expectImeHidden(stream);
        expectOnFinishInput(stream);
    }

    @Test
    public void testUnfocusedEditor_stateAlwaysHidden_hidesIme() throws Exception {
        Assume.assumeFalse(isPreventImeStartup());
        ImeEventStream stream = startFocusedEditorActivity_thenAnotherUnfocusedEditorActivity(
                SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        expectImeHidden(stream);
        expectOnFinishInput(stream);
    }

    @Test
    @ApiTest(apis = {"android.inputmethodservice.InputMethodService#onStartInput",
            "android.inputmethodservice.InputMethodService#showSoftInput"})
    public void testUnfocusedEditor_stateVisible() throws Exception {
        Assume.assumeFalse(isPreventImeStartup());
        ImeEventStream stream = startFocusedEditorActivity_thenAnotherUnfocusedEditorActivity(
                SOFT_INPUT_STATE_VISIBLE);
        // The previous IME should be finished
        expectOnFinishInput(stream);

        // Input should be started
        expectEvent(stream, event -> "onStartInput".equals(event.getEventName()),
                EXPECT_TIMEOUT);

        final boolean willHideIme = willHideImeWhenNoEditorFocus();
        if (willHideIme) {
            // The keyboard will not expected to show when focusing the app set STATE_VISIBLE
            // without an editor from the IME shown activity
            notExpectEvent(stream, event -> "showSoftInput".equals(event.getEventName()),
                    NOT_EXPECT_TIMEOUT);
        } else {
            expectEvent(stream, event -> "showSoftInput".equals(event.getEventName()),
                    EXPECT_TIMEOUT);
        }
    }

    @Test
    @ApiTest(apis = {"android.inputmethodservice.InputMethodService#onStartInput",
            "android.inputmethodservice.InputMethodService#showSoftInput"})
    public void testUnfocusedEditor_stateAlwaysVisible() throws Exception {
        Assume.assumeFalse(isPreventImeStartup());
        ImeEventStream stream = startFocusedEditorActivity_thenAnotherUnfocusedEditorActivity(
                SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        // The previous IME should be finished
        expectOnFinishInput(stream);

        // Input should be started
        expectEvent(stream, event -> "onStartInput".equals(event.getEventName()),
                EXPECT_TIMEOUT);

        final boolean willHideIme = willHideImeWhenNoEditorFocus();
        if (willHideIme) {
            // The keyboard will not expected to show when focusing the app set STATE_ALWAYS_VISIBLE
            // without an editor from the IME shown activity
            notExpectEvent(stream, event -> "showSoftInput".equals(event.getEventName()),
                    NOT_EXPECT_TIMEOUT);
        } else {
            expectEvent(stream, event -> "showSoftInput".equals(event.getEventName()),
                    EXPECT_TIMEOUT);
        }
    }

    @Test
    @ApiTest(apis = {"android.inputmethodservice.InputMethodService#onStartInput",
            "android.inputmethodservice.InputMethodService#showSoftInput"})
    public void testUnfocusedEditor_stateUnchanged() throws Exception {
        Assume.assumeFalse(isPreventImeStartup());
        ImeEventStream stream = startFocusedEditorActivity_thenAnotherUnfocusedEditorActivity(
                SOFT_INPUT_STATE_UNCHANGED);
        // The previous IME should be finished
        expectOnFinishInput(stream);

        // Input should be started
        expectEvent(stream, event -> "onStartInput".equals(event.getEventName()),
                EXPECT_TIMEOUT);

        final boolean willHideIme = willHideImeWhenNoEditorFocus();
        if (willHideIme) {
            // The keyboard will not expected to show when focusing the app set STATE_UNCHANGED
            // without an editor from the IME shown activity
            notExpectEvent(stream, event -> "showSoftInput".equals(event.getEventName()),
                    NOT_EXPECT_TIMEOUT);
        } else {
            expectEvent(stream, event -> "showSoftInput".equals(event.getEventName()),
                    EXPECT_TIMEOUT);
        }
    }

    @Test
    public void detachServed_withDifferentNextServed_b211105987() throws Exception {
        final AtomicReference<ViewGroup> layoutRef = new AtomicReference<>();
        final AtomicReference<EditText> firstEditorRef = new AtomicReference<>();
        final AtomicReference<EditText> secondEditorRef = new AtomicReference<>();
        final AtomicReference<InputMethodManager> imm = new AtomicReference<>();

        TestActivity.startSync(activity -> {
            final LinearLayout layout = new LinearLayout(activity);
            layout.setOrientation(LinearLayout.VERTICAL);
            layoutRef.set(layout);

            final EditText editText = new EditText(activity);
            editText.requestFocus();
            firstEditorRef.set(editText);
            layout.addView(editText);
            imm.set(activity.getSystemService(InputMethodManager.class));
            return layout;
        });

        waitOnMainUntil(() -> imm.get().hasActiveInputConnection(firstEditorRef.get()), TIMEOUT);

        runOnMainSync(() -> {
            final ViewGroup layout = layoutRef.get();

            final EditText editText = new EditText(layout.getContext());
            secondEditorRef.set(editText);
            layout.addView(editText);
        });

        waitOnMainUntil(() -> secondEditorRef.get().isLaidOut(), TIMEOUT);

        runOnMainSync(() -> {
            secondEditorRef.get().requestFocus();
            layoutRef.get().removeView(firstEditorRef.get());
        });

        assertTrue(getOnMainSync(() -> imm.get().hasActiveInputConnection(secondEditorRef.get())));
    }

    @AppModeFull(reason = "Instant apps cannot start TranslucentActivity from existing activity.")
    @Test
    public void testClearCurRootViewWhenDifferentProcessBecomesActive() throws Exception {
        final var editorRef = new AtomicReference<EditText>();
        final var imm = new AtomicReference<InputMethodManager>();

        final var testActivity = TestActivity.startSync(activity -> {
            final var layout = new LinearLayout(activity);
            layout.setOrientation(LinearLayout.VERTICAL);

            final var editText = new EditText(activity);
            editText.requestFocus();
            editorRef.set(editText);
            layout.addView(editText);
            imm.set(activity.getSystemService(InputMethodManager.class));
            return layout;
        });

        waitOnMainUntil(() -> imm.get().hasActiveInputConnection(editorRef.get()), TIMEOUT);

        // launch activity in a different package.
        final var intent = new Intent(Intent.ACTION_MAIN);
        intent.setComponent(new ComponentName(
                "android.view.inputmethod.ctstestapp",
                "android.view.inputmethod.ctstestapp.TranslucentActivity"));
        runOnMainSync(() -> testActivity.startActivity(intent));

        waitOnMainUntil(() -> !imm.get().isCurrentRootView(editorRef.get()), TIMEOUT,
                "Initial activity did not lose IME connection after second activity started.");
    }

    /**
     * A regression test for Bug 260682160.
     *
     * Ensure the input connection will be started eventually when temporary add & remove
     * ALT_FOCUSABLE_IM flag during the editor focus-out and focus-in stage.
     */
    @Test
    public void testInputConnectionWhenAddAndRemoveAltFocusableImFlagInFocus() throws Exception {
        try (MockImeSession imeSession = createTestImeSession()) {
            final ImeEventStream stream = imeSession.openEventStream();
            final String marker1 = getTestMarker();
            final String marker2 = getTestMarker();

            final AtomicReference<EditText> firstEditorRef = new AtomicReference<>();
            final AtomicReference<EditText> secondEditorRef = new AtomicReference<>();
            final int iterations = 10;

            for (int i = 0; i < iterations; i++) {
                final TestActivity testActivity = TestActivity.startSync(activity -> {
                    final LinearLayout layout = new LinearLayout(activity);
                    layout.setOrientation(LinearLayout.VERTICAL);
                    final EditText firstEditor = new EditText(activity);
                    firstEditor.setPrivateImeOptions(marker1);
                    firstEditor.setOnFocusChangeListener((v, hasFocus) -> {
                        if (!hasFocus) {
                            // Test Scenario 1: add ALT_FOCUSABLE_IM flag when the first editor
                            // lost the focus to disable the input and focusing the second editor.
                            activity.getWindow().addFlags(FLAG_ALT_FOCUSABLE_IM);
                            secondEditorRef.get().requestFocus();
                        }
                    });
                    firstEditor.requestFocus();

                    final EditText secondEditor = new EditText(activity);
                    secondEditor.setPrivateImeOptions(marker2);
                    firstEditorRef.set(firstEditor);
                    secondEditorRef.set(secondEditor);
                    layout.addView(firstEditor);
                    layout.addView(secondEditor);
                    activity.getWindow().setSoftInputMode(SOFT_INPUT_STATE_VISIBLE);
                    return layout;
                });
                expectEvent(stream, editorMatcher("onStartInput", marker1), TIMEOUT);

                testActivity.runOnUiThread(() -> firstEditorRef.get().clearFocus());
                TestUtils.waitOnMainUntil(() -> secondEditorRef.get().hasFocus(), TIMEOUT);

                testActivity.runOnUiThread(() -> {
                    // Test Scenario 2: remove ALT_FOCUSABLE_IM flag & call showSoftInput after
                    // the second editor focused.
                    final InputMethodManager im = testActivity.getSystemService(
                            InputMethodManager.class);
                    testActivity.getWindow().clearFlags(FLAG_ALT_FOCUSABLE_IM);
                    im.showSoftInput(secondEditorRef.get(), 0);
                });

                // Expect the input connection can started and commit the text to the second editor.
                expectEvent(stream, editorMatcher("onStartInput", marker2), TIMEOUT);
                expectImeVisible(TIMEOUT);

                final String testInput = "Test";
                final ImeCommand commitText = imeSession.callCommitText(testInput, 0);
                expectCommand(stream, commitText, EXPECT_TIMEOUT);
                assertThat(secondEditorRef.get().getText().toString()).isEqualTo(testInput);
            }
        }
    }

    private static void expectImeHidden(@NonNull ImeEventStream stream) throws TimeoutException {
        expectEvent(stream, event -> "hideSoftInput".equals(event.getEventName()), EXPECT_TIMEOUT);
    }

    private static void expectOnFinishInput(@NonNull ImeEventStream stream)
            throws TimeoutException {
        expectEvent(stream, event -> "onFinishInput".equals(event.getEventName()), EXPECT_TIMEOUT);
    }

    @NonNull
    private static MockImeSession createTestImeSession() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        return MockImeSession.create(
                instrumentation.getContext(),
                instrumentation.getUiAutomation(),
                new ImeSettings.Builder());
    }

    private static class ServiceSession implements ServiceConnection, AutoCloseable {
        private final Context mContext;
        private final Instrumentation mInstrumentation;

        ServiceSession(Instrumentation instrumentation) {
            mContext = instrumentation.getContext();
            mInstrumentation = instrumentation;
            mInstrumentation.getUiAutomation().adoptShellPermissionIdentity(
                    Manifest.permission.SYSTEM_ALERT_WINDOW);
            if (mContext.checkSelfPermission(
                    Manifest.permission.SYSTEM_ALERT_WINDOW) != PackageManager.PERMISSION_GRANTED) {
                fail("Require SYSTEM_ALERT_WINDOW permission");
            }
            Intent service = new Intent(mContext, WindowFocusHandleService.class);
            mContext.bindService(service, this, Context.BIND_AUTO_CREATE);

            // Wait for service bound.
            try {
                TestUtils.waitOnMainUntil(() -> WindowFocusHandleService.getInstance() != null,
                        TIMEOUT, "WindowFocusHandleService should be bound");
            } catch (TimeoutException e) {
                fail("WindowFocusHandleService should be bound");
            }
        }

        @Override
        public void close() throws Exception {
            mContext.unbindService(this);
            mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
        }

        WindowFocusHandleService getService() {
            return WindowFocusHandleService.getInstance();
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
        }
    }

    private static final class CloseOnce implements AutoCloseable {
        final AtomicBoolean mClosed = new AtomicBoolean(false);
        final AutoCloseable mAutoCloseable;
        private CloseOnce(@NonNull AutoCloseable autoCloseable) {
            mAutoCloseable = autoCloseable;
        }
        @Override
        public void close() throws Exception {
            if (!mClosed.getAndSet(true)) {
                mAutoCloseable.close();
            }
        }
        @NonNull
        static CloseOnce of(@NonNull AutoCloseable autoCloseable) {
            return new CloseOnce(autoCloseable);
        }
    }

    private static boolean willHideImeWhenNoEditorFocus() throws Exception {
        return SystemUtil.callWithShellPermissionIdentity(
                () -> DeviceConfig.getBoolean(DeviceConfig.NAMESPACE_INPUT_METHOD_MANAGER,
                        KEY_HIDE_IME_WHEN_NO_EDITOR_FOCUS, true));
    }
}
