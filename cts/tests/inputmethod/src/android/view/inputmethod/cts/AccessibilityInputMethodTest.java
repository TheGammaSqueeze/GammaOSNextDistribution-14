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

import static android.view.inputmethod.cts.util.TestUtils.runOnMainSync;

import static com.android.cts.mocka11yime.MockA11yImeEventStreamUtils.editorMatcherForA11yIme;
import static com.android.cts.mocka11yime.MockA11yImeEventStreamUtils.expectA11yImeCommand;
import static com.android.cts.mocka11yime.MockA11yImeEventStreamUtils.expectA11yImeEvent;
import static com.android.cts.mocka11yime.MockA11yImeEventStreamUtils.notExpectA11yImeEvent;
import static com.android.cts.mockime.ImeEventStreamTestUtils.editorMatcher;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectBindInput;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectEvent;
import static com.android.cts.mockime.ImeEventStreamTestUtils.notExpectEvent;

import static com.google.common.truth.Truth.assertThat;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Context;
import android.graphics.Color;
import android.os.Process;
import android.os.SystemClock;
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
import android.view.inputmethod.cts.util.EndToEndImeTestBase;
import android.view.inputmethod.cts.util.TestActivity;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.filters.FlakyTest;
import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.cts.mocka11yime.MockA11yImeEventStream;
import com.android.cts.mocka11yime.MockA11yImeSession;
import com.android.cts.mocka11yime.MockA11yImeSettings;
import com.android.cts.mockime.ImeEvent;
import com.android.cts.mockime.ImeEventStream;
import com.android.cts.mockime.ImeSettings;
import com.android.cts.mockime.MockImeSession;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

@MediumTest
@RunWith(AndroidJUnit4.class)
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public final class AccessibilityInputMethodTest extends EndToEndImeTestBase {
    private static final long TIMEOUT = TimeUnit.SECONDS.toMillis(10);
    private static final long NOT_EXPECT_TIMEOUT = TimeUnit.SECONDS.toMillis(1);

    private static final String TEST_MARKER_PREFIX =
            "android.view.inputmethod.cts.AccessibilityInputMethodTest";

    private static String getTestMarker() {
        return TEST_MARKER_PREFIX + "/"  + SystemClock.elapsedRealtimeNanos();
    }

    @FunctionalInterface
    private interface A11yImeTest {
        void run(@NonNull UiAutomation uiAutomation, @NonNull MockImeSession imeSession,
                @NonNull MockA11yImeSession a11yImeSession) throws Exception;
    }

    private void testA11yIme(@NonNull A11yImeTest test) throws Exception {
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        // For MockA11yIme to work, FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES needs to be specified
        // when obtaining UiAutomation object.
        final UiAutomation uiAutomation = instrumentation.getUiAutomation(
                UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        try (var imeSession = MockImeSession.create(instrumentation.getContext(), uiAutomation,
                new ImeSettings.Builder());
             var a11yImeSession = MockA11yImeSession.create(instrumentation.getContext(),
                     uiAutomation, MockA11yImeSettings.DEFAULT, TIMEOUT)) {
            test.run(uiAutomation, imeSession, a11yImeSession);
        }
    }

    @Test
    @FlakyTest
    public void testLifecycle() throws Exception {
        testA11yIme((uiAutomation, imeSession, a11yImeSession) -> {
            final var stream = a11yImeSession.openEventStream();

            final String marker = getTestMarker();
            final String markerForRestartInput = marker + "++";
            final AtomicReference<EditText> anotherEditTextRef = new AtomicReference<>();
            TestActivity.startSync(testActivity -> {
                final LinearLayout layout = new LinearLayout(testActivity);
                layout.setOrientation(LinearLayout.VERTICAL);
                final EditText editText = new EditText(testActivity);
                editText.setPrivateImeOptions(marker);
                editText.requestFocus();
                layout.addView(editText);

                final EditText anotherEditText = new EditText(testActivity);
                anotherEditText.setPrivateImeOptions(markerForRestartInput);
                layout.addView(anotherEditText);
                anotherEditTextRef.set(anotherEditText);

                return layout;
            });

            expectA11yImeEvent(stream, event -> "onCreate".equals(event.getEventName()), TIMEOUT);

            expectA11yImeEvent(stream, event -> "onCreateInputMethod".equals(event.getEventName()),
                    TIMEOUT);

            expectA11yImeEvent(stream, event -> "onServiceCreated".equals(event.getEventName()),
                    TIMEOUT);

            expectA11yImeEvent(stream, editorMatcherForA11yIme("onStartInput", marker), TIMEOUT);

            runOnMainSync(() -> anotherEditTextRef.get().requestFocus());

            expectA11yImeEvent(stream, event -> "onFinishInput".equals(event.getEventName()),
                    TIMEOUT);

            expectA11yImeEvent(stream,
                    editorMatcherForA11yIme("onStartInput", markerForRestartInput), TIMEOUT);
        });
    }

    @Test
    @FlakyTest
    public void testRestartInput() throws Exception {
        testA11yIme((uiAutomation, imeSession, a11yImeSession) -> {
            final var stream = a11yImeSession.openEventStream();

            final String marker = getTestMarker();
            final AtomicReference<EditText> editTextRef = new AtomicReference<>();
            TestActivity.startSync(testActivity -> {
                final EditText editText = new EditText(testActivity);
                editTextRef.set(editText);
                editText.setPrivateImeOptions(marker);
                editText.requestFocus();

                final LinearLayout layout = new LinearLayout(testActivity);
                layout.setOrientation(LinearLayout.VERTICAL);
                layout.addView(editText);
                return layout;
            });

            expectA11yImeEvent(stream, event -> "onCreate".equals(event.getEventName()), TIMEOUT);

            expectA11yImeEvent(stream, event -> "onCreateInputMethod".equals(event.getEventName()),
                    TIMEOUT);

            expectA11yImeEvent(stream, event -> "onServiceCreated".equals(event.getEventName()),
                    TIMEOUT);

            expectA11yImeEvent(stream, event -> {
                if (!TextUtils.equals(event.getEventName(), "onStartInput")) {
                    return false;
                }
                final var editorInfo =
                        event.getArguments().getParcelable("editorInfo", EditorInfo.class);
                final boolean restarting = event.getArguments().getBoolean("restarting");
                if (!TextUtils.equals(editorInfo.privateImeOptions, marker)) {
                    return false;
                }
                // For the initial "onStartInput", "restarting" must be false.
                return !restarting;
            }, TIMEOUT);

            final String markerForRestartInput = marker + "++";
            runOnMainSync(() -> {
                final EditText editText = editTextRef.get();
                editText.setPrivateImeOptions(markerForRestartInput);
                editText.getContext().getSystemService(InputMethodManager.class)
                        .restartInput(editText);
            });

            expectA11yImeEvent(stream, event -> {
                if (!TextUtils.equals(event.getEventName(), "onStartInput")) {
                    return false;
                }
                final var editorInfo =
                        event.getArguments().getParcelable("editorInfo", EditorInfo.class);
                final boolean restarting = event.getArguments().getBoolean("restarting");
                if (!TextUtils.equals(editorInfo.privateImeOptions, markerForRestartInput)) {
                    return false;
                }
                // For "onStartInput" because of IMM#restartInput(), "restarting" must be true.
                return restarting;
            }, TIMEOUT);
        });
    }

    private void verifyOnStartInputEventForFallbackInputConnection(
            @NonNull ImeEvent startInputEvent, boolean restarting) {
        assertThat(startInputEvent.getEnterState().hasFallbackInputConnection()).isTrue();
        final boolean actualRestarting = startInputEvent.getArguments().getBoolean("restarting");
        if (restarting) {
            assertThat(actualRestarting).isTrue();
        } else {
            assertThat(actualRestarting).isFalse();
        }
        final var editorInfo = startInputEvent.getArguments().getParcelable("editorInfo",
                EditorInfo.class);
        assertThat(editorInfo).isNotNull();
        assertThat(editorInfo.inputType).isEqualTo(EditorInfo.TYPE_NULL);
    }

    private void verifyStateAfterFinishInput(
            @NonNull MockA11yImeSession a11yImeSession,
            @NonNull MockA11yImeEventStream a11yImeEventStream) throws Exception {
        final var currentInputStartedEvent = expectA11yImeCommand(a11yImeEventStream,
                a11yImeSession.callGetCurrentInputStarted(), TIMEOUT);
        assertThat(currentInputStartedEvent.getReturnBooleanValue()).isFalse();
        final var getCurrentEditorInfoEvent = expectA11yImeCommand(a11yImeEventStream,
                a11yImeSession.callGetCurrentInputEditorInfo(), TIMEOUT);
        assertThat(getCurrentEditorInfoEvent.isNullReturnValue()).isTrue();
        final var getCurrentInputConnectionEvent = expectA11yImeCommand(a11yImeEventStream,
                a11yImeSession.callGetCurrentInputConnection(), TIMEOUT);
        assertThat(getCurrentInputConnectionEvent.isNullReturnValue()).isTrue();
    }

    @Test
    @FlakyTest
    public void testNoFallbackInputConnection() throws Exception {
        final String marker = getTestMarker();
        testA11yIme((uiAutomation, imeSession, a11yImeSession) -> {
            final var imeEventStream = imeSession.openEventStream();
            final var a11yImeEventStream = a11yImeSession.openEventStream();

            final AtomicReference<EditText> editTextForFallbackInputConnectionRef =
                    new AtomicReference<>();
            TestActivity.startSync(testActivity -> {
                final LinearLayout layout = new LinearLayout(testActivity);
                layout.setOrientation(LinearLayout.VERTICAL);
                final EditText editText = new EditText(testActivity);
                editText.setPrivateImeOptions(marker);
                editText.requestFocus();
                layout.addView(editText);

                final EditText editTextForFallbackInputConnection = new EditText(testActivity) {
                    @Override
                    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
                        return null;
                    }
                };
                editTextForFallbackInputConnectionRef.set(editTextForFallbackInputConnection);
                layout.addView(editTextForFallbackInputConnection);
                return layout;
            });

            expectEvent(imeEventStream, editorMatcher("onStartInput", marker), TIMEOUT);
            expectA11yImeEvent(a11yImeEventStream, editorMatcherForA11yIme("onStartInput", marker),
                    TIMEOUT);

            // Switch to an EditText that returns null InputConnection.
            runOnMainSync(() -> editTextForFallbackInputConnectionRef.get().requestFocus());

            // Both IME and A11y IME should receive "onFinishInput".
            expectEvent(imeEventStream,
                    event -> "onFinishInput".equals(event.getEventName()), TIMEOUT);
            expectA11yImeEvent(a11yImeEventStream,
                    event -> "onFinishInput".equals(event.getEventName()), TIMEOUT);

            // Only IME will receive "onStartInput" with a fallback InputConnection.
            {
                final var startInputEvent = expectEvent(imeEventStream,
                        event -> "onStartInput".equals(event.getEventName()), TIMEOUT);
                verifyOnStartInputEventForFallbackInputConnection(startInputEvent,
                        false /* restarting */);
            }

            // A11y IME should never receive "onStartInput" with a fallback InputConnection.
            {
                notExpectA11yImeEvent(a11yImeEventStream,
                        event -> "onStartInput".equals(event.getEventName()), NOT_EXPECT_TIMEOUT);
                verifyStateAfterFinishInput(a11yImeSession, a11yImeEventStream);
            }
        });
    }

    @Test
    @FlakyTest
    public void testNoFallbackInputConnectionAfterRestartInput() throws Exception {
        final String marker = getTestMarker();
        testA11yIme((uiAutomation, imeSession, a11yImeSession) -> {
            final var imeEventStream = imeSession.openEventStream();
            final var a11yImeEventStream = a11yImeSession.openEventStream();

            final AtomicReference<EditText> editTextRef = new AtomicReference<>();
            final AtomicBoolean testFallbackInputConnectionRef = new AtomicBoolean();
            TestActivity.startSync(testActivity -> {
                final LinearLayout layout = new LinearLayout(testActivity);
                layout.setOrientation(LinearLayout.VERTICAL);
                final EditText editText = new EditText(testActivity) {
                    @Override
                    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
                        return testFallbackInputConnectionRef.get()
                                ? null : super.onCreateInputConnection(outAttrs);
                    }
                };
                editTextRef.set(editText);
                editText.setPrivateImeOptions(marker);
                editText.requestFocus();
                layout.addView(editText);
                return layout;
            });

            expectEvent(imeEventStream, editorMatcher("onStartInput", marker), TIMEOUT);
            expectA11yImeEvent(a11yImeEventStream, editorMatcherForA11yIme("onStartInput", marker),
                    TIMEOUT);

            // Trigger restartInput.
            testFallbackInputConnectionRef.set(true);
            runOnMainSync(() ->
                    editTextRef.get().getContext().getSystemService(InputMethodManager.class)
                            .restartInput(editTextRef.get()));

            // Only IME will receive "onStartInput" with a fallback InputConnection.
            {
                final var startInputEvent = expectEvent(imeEventStream,
                        event -> "onStartInput".equals(event.getEventName()), TIMEOUT);
                verifyOnStartInputEventForFallbackInputConnection(startInputEvent,
                        true /* restarting */);
            }

            // A11y IME should never receive "onStartInput" with a fallback InputConnection.
            {
                expectA11yImeEvent(a11yImeEventStream,
                        event -> "onFinishInput".equals(event.getEventName()), TIMEOUT);
                notExpectA11yImeEvent(a11yImeEventStream,
                        event -> "onStartInput".equals(event.getEventName()), NOT_EXPECT_TIMEOUT);
                verifyStateAfterFinishInput(a11yImeSession, a11yImeEventStream);
            }
        });
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

    private void assertEditorInfo(@NonNull EditorInfo editorInfo,
            int initialSelStart, int initialSelEnd, @Nullable String initialSurroundingText) {
        assertThat(editorInfo).isNotNull();
        assertThat(editorInfo.initialSelStart).isEqualTo(initialSelStart);
        assertThat(editorInfo.initialSelEnd).isEqualTo(initialSelEnd);
        assertThat(editorInfo.getInitialSelectedText(0).toString())
                .isEqualTo(initialSurroundingText);
    }

    private void testInvalidateInputMain(
            BiFunction<View, Editable, InputConnection> inputConnectionProvider) throws Exception {
        final String marker = getTestMarker();
        final int initialSelStart = 3;
        final int initialSelEnd = 7;
        final int initialCapsMode = TextUtils.CAP_MODE_SENTENCES;

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

        testA11yIme((uiAutomation, imeSession, a11yImeSession) -> {
            final var imeEventStream = imeSession.openEventStream();
            final var a11yImeEventStream = a11yImeSession.openEventStream();

            final AtomicReference<MyTestEditor> myEditorRef = new AtomicReference<>();
            TestActivity.startSync(activity -> {
                final var layout = new LinearLayout(activity);
                layout.setOrientation(LinearLayout.VERTICAL);

                final var editable = Editable.Factory.getInstance().newEditable("0123456789");
                Selection.setSelection(editable, initialSelStart, initialSelEnd);

                final var editor = new MyTestEditor(activity, editable);
                editor.requestFocus();
                myEditorRef.set(editor);

                layout.addView(editor);
                return layout;
            });
            final MyTestEditor myEditor = myEditorRef.get();

            // Wait until the MockIme gets bound to the TestActivity.
            expectBindInput(imeEventStream, Process.myPid(), TIMEOUT);

            // Also make sure that MockA11yIme is up.
            expectA11yImeEvent(a11yImeEventStream, event -> "onCreate".equals(event.getEventName()),
                    TIMEOUT);

            // Confirm both MockIme and MockA11yIme receive "onStartInput"
            {
                final var startInputEvent =
                        expectEvent(imeEventStream, editorMatcher("onStartInput", marker), TIMEOUT);
                final var editorInfo = startInputEvent.getArguments().getParcelable("editorInfo",
                        EditorInfo.class);
                assertEditorInfo(editorInfo, initialSelStart, initialSelEnd, "3456");
            }
            {
                final var startInputEvent = expectA11yImeEvent(a11yImeEventStream,
                        editorMatcherForA11yIme("onStartInput", marker), TIMEOUT);
                final var editorInfo = startInputEvent.getArguments().getParcelable("editorInfo",
                        EditorInfo.class);
                assertEditorInfo(editorInfo, initialSelStart, initialSelEnd, "3456");
            }

            imeEventStream.skipAll();
            a11yImeEventStream.skipAll();
            final ImeEventStream forkedImeEventStream = imeEventStream.copy();
            final MockA11yImeEventStream forkedA11yImeEventStream = a11yImeEventStream.copy();

            // Trigger invalidate input.
            final int newSelStart = 1;
            final int newSelEnd = 3;
            runOnMainSync(() -> {
                Selection.setSelection(myEditor.mEditable, newSelStart, newSelEnd);
                myEditor.getContext().getSystemService(InputMethodManager.class)
                        .invalidateInput(myEditor);
            });

            // Verify that InputMethodService#onStartInput() is triggered as if IMM#restartInput()
            // was called.
            {
                final var startInputEvent =
                        expectEvent(imeEventStream, editorMatcher("onStartInput", marker), TIMEOUT);
                final boolean restarting = startInputEvent.getArguments().getBoolean("restarting");
                assertThat(restarting).isTrue();
                final var editorInfo = startInputEvent.getArguments().getParcelable("editorInfo",
                        EditorInfo.class);
                assertEditorInfo(editorInfo, newSelStart, newSelEnd, "12");
            }
            // Also verify that android.accessibilityservice.InputMethod#onStartInput() is triggered
            // as if IMM#restartInput() was called.
            {
                final var startInputEvent = expectA11yImeEvent(a11yImeEventStream,
                        editorMatcherForA11yIme("onStartInput", marker), TIMEOUT);
                final boolean restarting = startInputEvent.getArguments().getBoolean("restarting");
                assertThat(restarting).isTrue();
                final var editorInfo = startInputEvent.getArguments().getParcelable("editorInfo",
                        EditorInfo.class);
                assertEditorInfo(editorInfo, newSelStart, newSelEnd, "12");
            }

            // For historical reasons, InputMethodService#onFinishInput() will not be triggered when
            // restarting an input connection.
            assertThat(forkedImeEventStream.findFirst(
                    event -> "onFinishInput".equals(event.getEventName())).isPresent()).isFalse();

            // A11yIME also inherited the above IME behavior.
            assertThat(forkedA11yImeEventStream.findFirst(
                    event -> "onFinishInput".equals(event.getEventName())).isPresent()).isFalse();

            // Make sure that InputMethodManager#updateSelection() will be ignored when there is
            // no change from the last call of InputMethodManager#invalidateInput().
            runOnMainSync(() -> {
                Selection.setSelection(myEditor.mEditable, newSelStart, newSelEnd);
                myEditor.getContext().getSystemService(InputMethodManager.class).updateSelection(
                        myEditor, newSelStart, newSelEnd, -1, -1);
            });

            notExpectEvent(imeEventStream,
                    event -> "onUpdateSelection".equals(event.getEventName()), NOT_EXPECT_TIMEOUT);
            notExpectA11yImeEvent(a11yImeEventStream,
                    event -> "onUpdateSelection".equals(event.getEventName()), NOT_EXPECT_TIMEOUT);
        });
    }

    @Test
    @FlakyTest
    public void testInvalidateInput() throws Exception {
        // If IC#takeSnapshot() returns true, it should work, even if IC#{begin,end}BatchEdit()
        // always return false.
        testInvalidateInputMain((view, editable) -> new TestInputConnection(view, editable) {
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
    @FlakyTest
    public void testInvalidateInputFallback() throws Exception {
        // If IC#takeSnapshot() returns false, then fall back to IMM#restartInput()
        testInvalidateInputMain((view, editable) -> new TestInputConnection(view, editable) {
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
}
