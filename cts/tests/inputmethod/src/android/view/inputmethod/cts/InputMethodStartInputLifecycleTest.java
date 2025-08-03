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

package android.view.inputmethod.cts;

import static android.inputmethodservice.InputMethodService.FINISH_INPUT_NO_FALLBACK_CONNECTION;
import static android.view.View.SCREEN_STATE_OFF;
import static android.view.View.SCREEN_STATE_ON;
import static android.view.View.VISIBLE;

import static com.android.cts.mockime.ImeEventStreamTestUtils.editorMatcher;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectBindInput;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectCommand;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectEvent;
import static com.android.cts.mockime.ImeEventStreamTestUtils.notExpectEvent;
import static com.android.cts.mockime.ImeEventStreamTestUtils.withDescription;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.inputmethodservice.InputMethodService;
import android.os.IBinder;
import android.os.Process;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.text.Editable;
import android.text.InputType;
import android.text.Selection;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.TextSnapshot;
import android.view.inputmethod.cts.util.DisableScreenDozeRule;
import android.view.inputmethod.cts.util.EndToEndImeTestBase;
import android.view.inputmethod.cts.util.MockTestActivityUtil;
import android.view.inputmethod.cts.util.RequireImeCompatFlagRule;
import android.view.inputmethod.cts.util.TestActivity;
import android.view.inputmethod.cts.util.TestUtils;
import android.view.inputmethod.cts.util.UnlockScreenRule;
import android.view.inputmethod.cts.util.WindowFocusStealer;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.cts.mockime.ImeCommand;
import com.android.cts.mockime.ImeEvent;
import com.android.cts.mockime.ImeEventStream;
import com.android.cts.mockime.ImeSettings;
import com.android.cts.mockime.MockImeSession;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Predicate;

@MediumTest
@RunWith(AndroidJUnit4.class)
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public class InputMethodStartInputLifecycleTest extends EndToEndImeTestBase {
    @Rule
    public final DisableScreenDozeRule mDisableScreenDozeRule = new DisableScreenDozeRule();
    @Rule
    public final UnlockScreenRule mUnlockScreenRule = new UnlockScreenRule();
    @Rule
    public final RequireImeCompatFlagRule mRequireImeCompatFlagRule = new RequireImeCompatFlagRule(
            FINISH_INPUT_NO_FALLBACK_CONNECTION, true);

    private static final long TIMEOUT = TimeUnit.SECONDS.toMillis(5);
    private static final long NOT_EXPECT_TIMEOUT = TimeUnit.SECONDS.toMillis(1);

    private static final String TEST_MARKER_PREFIX =
            "android.view.inputmethod.cts.FocusHandlingTest";

    private static String getTestMarker() {
        return TEST_MARKER_PREFIX + "/"  + SystemClock.elapsedRealtimeNanos();
    }

    @AppModeFull(reason = "KeyguardManager is not accessible from instant apps")
    @Test
    public void testInputConnectionStateWhenScreenStateChanges() throws Exception {
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        final Context context = instrumentation.getTargetContext();
        final InputMethodManager imm = context.getSystemService(InputMethodManager.class);
        assumeTrue(context.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_INPUT_METHODS));
        final AtomicReference<EditText> focusedEditTextRef = new AtomicReference<>();

        try (MockImeSession imeSession = MockImeSession.create(
                context, instrumentation.getUiAutomation(), new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();

            final String marker = getTestMarker();
            final AtomicInteger screenStateCallbackRef = new AtomicInteger(-1);
            TestActivity.startSync(activity -> {
                final LinearLayout layout = new LinearLayout(activity);
                layout.setOrientation(LinearLayout.VERTICAL);

                final EditText focusedEditText = new EditText(activity) {
                    @Override
                    public void onScreenStateChanged(int screenState) {
                        super.onScreenStateChanged(screenState);
                        screenStateCallbackRef.set(screenState);
                    }
                };
                focusedEditText.setPrivateImeOptions(marker);
                focusedEditText.setHint("editText");
                layout.addView(focusedEditText);
                focusedEditText.requestFocus();
                focusedEditTextRef.set(focusedEditText);

                final EditText nonFocusedEditText = new EditText(activity);
                layout.addView(nonFocusedEditText);

                return layout;
            });

            // Expected onStartInput when TestActivity launched.
            final EditText editText = focusedEditTextRef.get();
            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);

            // Expected text commit will not work when turnScreenOff.
            TestUtils.turnScreenOff();
            TestUtils.waitOnMainUntil(() -> screenStateCallbackRef.get() == SCREEN_STATE_OFF
                            && editText.getWindowVisibility() != VISIBLE, TIMEOUT);

            if (imeSession.isFinishInputNoFallbackConnectionEnabled()) {
                // Expected only onFinishInput and the EditText is inactive for input method.
                expectEvent(stream, onFinishInputMatcher(), TIMEOUT);
                notExpectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);
                assertFalse(TestUtils.getOnMainSync(() -> imm.hasActiveInputConnection(editText)));
            } else {
                expectEvent(stream, onFinishInputMatcher(), TIMEOUT);
            }

            final ImeCommand commit = imeSession.callCommitText("Hi!", 1);
            expectCommand(stream, commit, TIMEOUT);
            TestUtils.waitOnMainUntil(() -> !TextUtils.equals(editText.getText(), "Hi!"), TIMEOUT,
                    "InputMethodService#commitText should not work after screen off");

            // Expected text commit will work when turnScreenOn.
            TestUtils.turnScreenOn();
            TestUtils.unlockScreen();
            TestUtils.waitOnMainUntil(() -> screenStateCallbackRef.get() == SCREEN_STATE_ON
                            && editText.getWindowVisibility() == VISIBLE, TIMEOUT);
            mCtsTouchUtils.emulateTapOnViewCenter(instrumentation, null, editText);

            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);
            if (imeSession.isFinishInputNoFallbackConnectionEnabled()) {
                // Expected only onStartInput and the EditText is active for input method.
                notExpectEvent(stream, onFinishInputMatcher(), TIMEOUT);
            }
            assertTrue(TestUtils.getOnMainSync(() -> imm.hasActiveInputConnection(editText)));
            final ImeCommand commit1 = imeSession.callCommitText("Hello!", 1);
            expectCommand(stream, commit1, TIMEOUT);
            TestUtils.waitOnMainUntil(() -> TextUtils.equals(editText.getText(), "Hello!"), TIMEOUT,
                    "InputMethodService#commitText should work after screen on");
        }
    }

    /**
     * Test case for Bug 158624922 and Bug 152373385.
     *
     * Test {@link android.inputmethodservice.InputMethodService#onStartInput(EditorInfo, boolean)}
     * and {@link InputMethodService#onFinishInput()} won't be called and the input connection
     * remains active, even when a non-IME focusable window hosted by a different process
     * temporarily becomes the focused window.
     */
    @Test
    public void testNoStartNewInputWhileOtherProcessHasWindowFocus() throws Exception {
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        try (MockImeSession imeSession = MockImeSession.create(
                instrumentation.getContext(),
                instrumentation.getUiAutomation(),
                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();

            final String marker = getTestMarker();
            final EditText editText = launchTestActivity(marker);
            TestUtils.runOnMainSync(() -> editText.requestFocus());

            // Wait until the MockIme gets bound to the TestActivity.
            expectBindInput(stream, Process.myPid(), TIMEOUT);

            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);

            // Get app window token
            final IBinder appWindowToken = TestUtils.getOnMainSync(
                    () -> editText.getApplicationWindowToken());

            try (WindowFocusStealer focusStealer =
                         WindowFocusStealer.connect(instrumentation.getTargetContext(), TIMEOUT)) {

                focusStealer.stealWindowFocus(appWindowToken, TIMEOUT);

                // Wait until the edit text loses window focus.
                TestUtils.waitOnMainUntil(() -> !editText.hasWindowFocus(), TIMEOUT);
            }
            // Wait until the edit text gains window focus again.
            TestUtils.waitOnMainUntil(() -> editText.hasWindowFocus(), TIMEOUT);

            // Not expect the input connection will be started or finished even gaining non-IME
            // focusable window focus.
            notExpectEvent(stream, event -> "onFinishInput".equals(event.getEventName())
                    || "onStartInput".equals(event.getEventName()), TIMEOUT);

            // Verify the input connection of the EditText is still active and can accept text.
            final InputMethodManager imm = editText.getContext().getSystemService(
                    InputMethodManager.class);
            assertTrue(TestUtils.getOnMainSync(() -> imm.hasActiveInputConnection(editText)));
        }
    }

    private EditText launchTestActivity(String marker) {
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

    /**
     * A mostly-minimum implementation of {@link View} that can be used to test custom
     * implementations of {@link View#onCreateInputConnection(EditorInfo)}.
     */
    static class TestEditor extends View {
        TestEditor(@NonNull Context context) {
            super(context);
            setBackgroundColor(Color.YELLOW);
            setFocusableInTouchMode(true);
            setFocusable(true);
            setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 10 /* height */));
        }
    }

    private abstract static class TestInputConnection extends BaseInputConnection {
        @NonNull
        private final Editable mEditable;

        TestInputConnection(@NonNull View view, @NonNull Editable editable) {
            super(view, true /* fullEditor */);
            mEditable = editable;
        }

        @NonNull
        @Override
        public final Editable getEditable() {
            return mEditable;
        }
    }

    /**
     * Regression test for Bug 213350732.
     *
     * <p>Make sure that calling {@link InputMethodManager#invalidateInput(View)} before
     * {@link android.view.inputmethod.InputMethodSession} is delivered to the IME client does not
     * result in {@link NullPointerException}.</p>
     */
    @Test
    public void testInvalidateInputBeforeInputMethodSessionBecomesAvailable() throws Exception {
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        final boolean instant =
                instrumentation.getTargetContext().getPackageManager().isInstantApp();
        final String marker1 = getTestMarker();
        try (AutoCloseable closeable = MockTestActivityUtil.launchSync(instant,
                TIMEOUT, Map.of(MockTestActivityUtil.EXTRA_KEY_PRIVATE_IME_OPTIONS, marker1))) {

            try (MockImeSession imeSession = MockImeSession.create(
                    instrumentation.getContext(),
                    instrumentation.getUiAutomation(),
                    new ImeSettings.Builder())) {
                final ImeEventStream stream = imeSession.openEventStream();

                expectEvent(stream, editorMatcher("onStartInput", marker1), TIMEOUT);

                expectCommand(stream, imeSession.suspendCreateSession(), TIMEOUT);

                final String marker2 = getTestMarker();
                final EditText editText = launchTestActivity(marker2);
                TestUtils.runOnMainSync(() -> editText.getContext().getSystemService(
                        InputMethodManager.class).invalidateInput(editText));

                expectCommand(stream, imeSession.resumeCreateSession(), TIMEOUT);
            }
        }
    }

    @Test
    public void testInvalidateInput() throws Exception {
        // If IC#takeSnapshot() returns true, it should work, even if IC#{begin,end}BatchEdit()
        // always return false.
        expectNativeInvalidateInput((view, editable) -> new TestInputConnection(view, editable) {
            @Override
            public boolean beginBatchEdit() {
                return false;
            }
            @Override
            public boolean endBatchEdit() {
                return false;
            }
        });
    }

    @Test
    public void testInvalidateInputOnInputConnectionWithBatchEdit() throws Exception {
        // IMM#invalidateInput() should just work for ICs that support
        // {begin,end}BatchEdit().
        expectNativeInvalidateInput((view, editable) -> new TestInputConnection(view, editable) {
            private int mBatchEditCount = 0;
            @Override
            public boolean beginBatchEdit() {
                ++mBatchEditCount;
                return true;
            }
            @Override
            public boolean endBatchEdit() {
                if (mBatchEditCount <= 0) {
                    return false;
                }
                --mBatchEditCount;
                return mBatchEditCount > 0;
            }
        });
    }

    @Test
    public void testInvalidateInputFallback() throws Exception {
        // If IC#takeSnapshot() returns false, then fall back to IMM#restartInput()
        expectFallbackInvalidateInput((view, editable) -> new TestInputConnection(view, editable) {
            @Override
            public boolean beginBatchEdit() {
                return false;
            }
            @Override
            public boolean endBatchEdit() {
                return false;
            }
            @Override
            public TextSnapshot takeSnapshot() {
                return null;
            }
        });
    }

    @Test
    public void testInvalidateInputOnInputConnectionWithBrokenBatchEdit() throws Exception {
        // Bug 209958658 should not prevent the system from using the native invalidateInput().
        expectNativeInvalidateInput((view, editable) -> new TestInputConnection(view, editable) {
            private int mBatchEditCount = 0;

            @Override
            public boolean beginBatchEdit() {
                ++mBatchEditCount;
                return true;
            }
            @Override
            public boolean endBatchEdit() {
                if (mBatchEditCount <= 0) {
                    return false;
                }
                --mBatchEditCount;
                // This violates the spec. See Bug 209958658 for instance.
                return true;
            }
        });
    }

    @Test
    public void testInvalidateInputFallbackOnInputConnectionWithBrokenBatchEdit() throws Exception {
        // Even if IC#endBatchEdit() never returns false, the system should be able to fall back
        // to IMM#restartInput().  This is a regression test for Bug 208941904.
        expectFallbackInvalidateInput((view, editable) -> new TestInputConnection(view, editable) {
            @Override
            public boolean beginBatchEdit() {
                return true;
            }
            @Override
            public boolean endBatchEdit() {
                return true;
            }
        });
    }

    private void expectNativeInvalidateInput(
            BiFunction<View, Editable, InputConnection> inputConnectionProvider) throws Exception {
        testInvalidateInputMain(true, inputConnectionProvider);
    }

    private void expectFallbackInvalidateInput(
            BiFunction<View, Editable, InputConnection> inputConnectionProvider) throws Exception {
        testInvalidateInputMain(false, inputConnectionProvider);
    }

    private void testInvalidateInputMain(boolean expectNativeInvalidateInput,
            BiFunction<View, Editable, InputConnection> inputConnectionProvider) throws Exception {
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        try (MockImeSession imeSession = MockImeSession.create(
                instrumentation.getContext(),
                instrumentation.getUiAutomation(),
                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();

            final String marker = getTestMarker();
            final int initialSelStart = 3;
            final int initialSelEnd = 7;
            final int initialCapsMode = TextUtils.CAP_MODE_SENTENCES;

            final AtomicInteger onCreateConnectionCount = new AtomicInteger(0);
            class MyTestEditor extends TestEditor {
                final Editable mEditable;

                MyTestEditor(Context context, @NonNull Editable editable) {
                    super(context);
                    mEditable = editable;
                }

                @Override
                public boolean onCheckIsTextEditor() {
                    return true;
                }

                @Override
                public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
                    onCreateConnectionCount.incrementAndGet();
                    outAttrs.inputType =
                            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
                    outAttrs.initialSelStart = Selection.getSelectionStart(mEditable);
                    outAttrs.initialSelEnd = Selection.getSelectionEnd(mEditable);
                    outAttrs.initialCapsMode = initialCapsMode;
                    outAttrs.privateImeOptions = marker;
                    outAttrs.setInitialSurroundingText(mEditable);
                    return inputConnectionProvider.apply(this, mEditable);
                }
            }

            final AtomicReference<MyTestEditor> myEditorRef = new AtomicReference<>();
            TestActivity.startSync(activity -> {
                final LinearLayout layout = new LinearLayout(activity);
                layout.setOrientation(LinearLayout.VERTICAL);

                final Editable editable =
                        Editable.Factory.getInstance().newEditable("0123456789");
                Selection.setSelection(editable, initialSelStart, initialSelEnd);

                final MyTestEditor editor = new MyTestEditor(activity, editable);
                editor.requestFocus();
                myEditorRef.set(editor);

                layout.addView(editor);
                return layout;
            });
            final MyTestEditor myEditor = myEditorRef.get();

            // Wait until the MockIme gets bound to the TestActivity.
            expectBindInput(stream, Process.myPid(), TIMEOUT);

            {
                final ImeEvent startInputEvent =
                        expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);
                final EditorInfo editorInfo =
                        startInputEvent.getArguments().getParcelable("editorInfo");
                assertThat(editorInfo).isNotNull();
                assertThat(editorInfo.initialSelStart).isEqualTo(initialSelStart);
                assertThat(editorInfo.initialSelEnd).isEqualTo(initialSelEnd);
                assertThat(editorInfo.getInitialSelectedText(0).toString()).isEqualTo("3456");
            }

            stream.skipAll();
            final ImeEventStream forkedStream = stream.copy();

            final int prevOnCreateInputConnectionCount = onCreateConnectionCount.get();

            final int newSelStart = 1;
            final int newSelEnd = 3;
            TestUtils.runOnMainSync(() -> {
                Selection.setSelection(myEditor.mEditable, newSelStart, newSelEnd);
                final InputMethodManager imm = myEditor.getContext().getSystemService(
                        InputMethodManager.class);
                imm.invalidateInput(myEditor);
            });

            // Verify that InputMethodService#onStartInput() is triggered as if IMM#restartInput()
            // was called.
            {
                final ImeEvent startInputEvent =
                        expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);
                final boolean restarting = startInputEvent.getArguments().getBoolean("restarting");
                assertThat(restarting).isTrue();
                final EditorInfo editorInfo =
                        startInputEvent.getArguments().getParcelable("editorInfo");
                assertThat(editorInfo).isNotNull();
                assertThat(editorInfo.initialSelStart).isEqualTo(newSelStart);
                assertThat(editorInfo.initialSelEnd).isEqualTo(newSelEnd);
                assertThat(editorInfo.getInitialSelectedText(0).toString()).isEqualTo("12");
            }

            if (expectNativeInvalidateInput) {
                // If InputMethodManager#invalidateInput() is expected to be natively supported,
                // additional View#onCreateInputConnection() must not happen.
                assertThat(onCreateConnectionCount.get()).isEqualTo(
                        prevOnCreateInputConnectionCount);
            } else {
                // InputMethodManager#invalidateInput() is expected to be falling back into
                // InputMethodManager#restartInput(), which triggers View#onCreateInputConnection()
                // as a consequence.
                assertThat(onCreateConnectionCount.get()).isGreaterThan(
                        prevOnCreateInputConnectionCount);
            }

            // For historical reasons, InputMethodService#onFinishInput() will not be triggered when
            // restarting an input connection.
            assertThat(forkedStream.findFirst(onFinishInputMatcher()).isPresent()).isFalse();

            // Make sure that InputMethodManager#updateSelection() will be ignored when there is
            // no change from the last call of InputMethodManager#invalidateInput().
            TestUtils.runOnMainSync(() -> {
                Selection.setSelection(myEditor.mEditable, newSelStart, newSelEnd);
                final InputMethodManager imm = myEditor.getContext().getSystemService(
                        InputMethodManager.class);
                imm.updateSelection(myEditor, newSelStart, newSelEnd, -1, -1);
            });

            notExpectEvent(stream, event -> "onUpdateSelection".equals(event.getEventName()),
                    NOT_EXPECT_TIMEOUT);
        }
    }

    private static Predicate<ImeEvent> onFinishInputMatcher() {
        return withDescription("onFinishInput()",
                event -> TextUtils.equals("onFinishInput", event.getEventName()));
    }
}
