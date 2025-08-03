/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static com.android.cts.mockime.ImeEventStreamTestUtils.editorMatcher;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectCommand;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectEvent;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.graphics.Color;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.SurroundingText;
import android.view.inputmethod.cts.util.EndToEndImeTestBase;
import android.view.inputmethod.cts.util.TestActivity;
import android.view.inputmethod.cts.util.UnlockScreenRule;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.cts.mockime.ImeEvent;
import com.android.cts.mockime.ImeEventStream;
import com.android.cts.mockime.ImeSettings;
import com.android.cts.mockime.MockImeSession;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Contains test cases for some special IME-related behaviors of {@link EditText}.
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public class EditTextImeSupportTest extends EndToEndImeTestBase {
    private static final long TIMEOUT = TimeUnit.SECONDS.toMillis(5);

    @Rule
    public final UnlockScreenRule mUnlockScreenRule = new UnlockScreenRule();

    private static final String TEST_MARKER_PREFIX =
            "android.view.inputmethod.cts.EditTextImeSupportTest";

    private static String getTestMarker() {
        return TEST_MARKER_PREFIX + "/"  + SystemClock.elapsedRealtimeNanos();
    }

    public EditText launchTestActivity(String marker, String initialText,
            int initialSelectionStart, int initialSelectionEnd) {
        final AtomicReference<EditText> editTextRef = new AtomicReference<>();
        TestActivity.startSync(activity-> {
            final LinearLayout layout = new LinearLayout(activity);
            layout.setOrientation(LinearLayout.VERTICAL);

            final EditText editText = new EditText(activity);
            editText.setPrivateImeOptions(marker);
            editText.setHint("editText");
            editText.setText(initialText);
            editText.setSelection(initialSelectionStart, initialSelectionEnd);
            editText.requestFocus();
            editTextRef.set(editText);

            layout.addView(editText);
            return layout;
        });
        return editTextRef.get();
    }

    /**
     * A regression test for Bug 161330778.
     */
    @Test
    public void testSetTextTriggersRestartInput() throws Exception {
        try (MockImeSession imeSession = MockImeSession.create(
                InstrumentationRegistry.getInstrumentation().getContext(),
                InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();

            final String initialText = "0123456789";
            final int initialSelectionStart = 3;
            final int initialSelectionEnd = 7;
            final String marker = getTestMarker();
            final EditText editText = launchTestActivity(marker, initialText, initialSelectionStart,
                    initialSelectionEnd);

            // 1nd onStartInput() should be with restarting == false, with the correct initial
            // surrounding text information.
            final ImeEvent firstOnStartInput = expectEvent(stream,
                    editorMatcher("onStartInput", marker), TIMEOUT);
            assertFalse(firstOnStartInput.getArguments().getBoolean("restarting"));

            // Verify the initial surrounding text info.
            final EditorInfo initialEditorInfo =
                    firstOnStartInput.getArguments().getParcelable("editorInfo");
            assertNotNull(initialEditorInfo);
            assertInitialSurroundingText(initialEditorInfo, initialText, initialSelectionStart,
                    initialSelectionEnd);

            final String updatedText = "NewText";

            // Create a copy of the stream to verify that there is no onUpdateSelection().
            stream.skipAll();
            final ImeEventStream copiedStream = stream.copy();

            // This should trigger InputMethodManager#restartInput(), which triggers the 2nd
            // onStartInput() with restarting == true.
            runOnMainSync(() -> editText.setText(updatedText));
            final ImeEvent secondOnStartInput = expectEvent(stream,
                    editorMatcher("onStartInput", marker), TIMEOUT);
            assertTrue(secondOnStartInput.getArguments().getBoolean("restarting"));

            // Verify the initial surrounding text after TextView#setText(). The cursor must be
            // placed at the beginning of the new text.
            final EditorInfo restartingEditorInfo =
                    secondOnStartInput.getArguments().getParcelable("editorInfo");
            assertNotNull(restartingEditorInfo);
            assertInitialSurroundingText(restartingEditorInfo, updatedText, 0, 0);

            assertFalse("TextView#setText() must not trigger onUpdateSelection",
                    copiedStream.findFirst(
                            event -> "onUpdateSelection".equals(event.getEventName())).isPresent());
        }
    }

    private static void assertInitialSurroundingText(@NonNull EditorInfo editorInfo,
            @NonNull String expectedText, int expectedSelectionStart, int expectedSelectionEnd) {

        assertNotEquals("expectedText must has a selection", -1, expectedSelectionStart);
        assertNotEquals("expectedText must has a selection", -1, expectedSelectionEnd);

        final CharSequence expectedTextBeforeCursor =
                expectedText.subSequence(0, expectedSelectionStart);
        final CharSequence expectedSelectedText =
                expectedText.subSequence(expectedSelectionStart, expectedSelectionEnd);
        final CharSequence expectedTextAfterCursor =
                expectedText.subSequence(expectedSelectionEnd, expectedText.length());
        final int expectedTextLength = expectedText.length();

        assertEqualsWithIgnoringSpans(expectedTextBeforeCursor,
                editorInfo.getInitialTextBeforeCursor(expectedTextLength, 0));
        assertEqualsWithIgnoringSpans(expectedSelectedText,
                editorInfo.getInitialSelectedText(0));
        assertEqualsWithIgnoringSpans(expectedTextAfterCursor,
                editorInfo.getInitialTextAfterCursor(expectedTextLength, 0));

        final SurroundingText initialSurroundingText =
                editorInfo.getInitialSurroundingText(expectedTextLength, expectedTextLength, 0);
        assertNotNull(initialSurroundingText);
        assertEqualsWithIgnoringSpans(expectedText, initialSurroundingText.getText());
        assertEquals(expectedSelectionStart, initialSurroundingText.getSelectionStart());
        assertEquals(expectedSelectionEnd, initialSurroundingText.getSelectionEnd());
    }

    private static void assertEqualsWithIgnoringSpans(@Nullable CharSequence expected,
            @Nullable CharSequence actual) {
        if (expected == actual) {
            return;
        }
        if (expected == null) {
            fail("must be null but was " + actual);
        }
        if (actual == null) {
            fail("must be " + expected + " but was null");
        }
        assertEquals(expected.toString(), actual.toString());
    }

    /**
     * Test when to see {@link EditorInfo#IME_FLAG_NAVIGATE_NEXT} and
     * {@link EditorInfo#IME_FLAG_NAVIGATE_PREVIOUS}.
     *
     * <p>This is also a regression test for Bug 31099943.</p>
     */
    @Test
    public void testNavigateFlags() throws Exception {
        try (MockImeSession imeSession = MockImeSession.create(
                InstrumentationRegistry.getInstrumentation().getContext(),
                InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();

            // For a single EditText, there should be no navigate flag
            verifyNavigateFlags(stream, new Control[]{
                    Control.FOCUSED_EDIT_TEXT,
            }, false /* navigateNext */, false /* navigatePrevious */);

            // For two EditText controls, there should be one navigate flag depending on the
            // geometry.
            verifyNavigateFlags(stream, new Control[]{
                    Control.FOCUSED_EDIT_TEXT,
                    Control.EDIT_TEXT,
            }, true /* navigateNext */, false /* navigatePrevious */);
            verifyNavigateFlags(stream, new Control[]{
                    Control.EDIT_TEXT,
                    Control.FOCUSED_EDIT_TEXT,
            }, false /* navigateNext */, true /* navigatePrevious */);

            // Non focusable View controls should be ignored when determining navigation flags.
            verifyNavigateFlags(stream, new Control[]{
                    Control.NON_FOCUSABLE_VIEW,
                    Control.FOCUSED_EDIT_TEXT,
                    Control.NON_FOCUSABLE_VIEW,
            }, false /* navigateNext */, false /* navigatePrevious */);

            // Even focusable View controls should be ignored when determining navigation flags if
            // View#onCheckIsTextEditor() returns false. (Regression test for Bug 31099943)
            verifyNavigateFlags(stream, new Control[]{
                    Control.FOCUSABLE_VIEW,
                    Control.FOCUSED_EDIT_TEXT,
                    Control.FOCUSABLE_VIEW,
            }, false /* navigateNext */, false /* navigatePrevious */);
        }
    }

    private enum Control {
        EDIT_TEXT,
        FOCUSED_EDIT_TEXT,
        FOCUSABLE_VIEW,
        NON_FOCUSABLE_VIEW,
    }

    private void verifyNavigateFlags(@NonNull ImeEventStream stream, @NonNull Control[] controls,
            boolean navigateNext, boolean navigatePrevious) throws Exception {
        final String marker = getTestMarker();
        TestActivity.startSync(activity-> {
            final LinearLayout layout = new LinearLayout(activity);
            layout.setOrientation(LinearLayout.VERTICAL);
            for (Control control : controls) {
                switch (control) {
                    case EDIT_TEXT:
                    case FOCUSED_EDIT_TEXT: {
                        final boolean focused = (Control.FOCUSED_EDIT_TEXT == control);
                        final EditText editText = new EditText(activity);
                        editText.setHint("editText");
                        layout.addView(editText);
                        if (focused) {
                            editText.setPrivateImeOptions(marker);
                            editText.requestFocus();
                        }
                        break;
                    }
                    case FOCUSABLE_VIEW:
                    case NON_FOCUSABLE_VIEW: {
                        final boolean focusable = (Control.FOCUSABLE_VIEW == control);
                        final View view = new View(activity);
                        view.setBackgroundColor(focusable ? Color.YELLOW : Color.RED);
                        view.setFocusable(focusable);
                        view.setFocusableInTouchMode(focusable);
                        view.setLayoutParams(new ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, 10 /* height */));
                        layout.addView(view);
                        break;
                    }
                    default:
                        throw new UnsupportedOperationException("Unknown control=" + control);
                }
            }
            return layout;
        });

        final ImeEvent startInput = expectEvent(stream,
                editorMatcher("onStartInput", marker), TIMEOUT);
        final EditorInfo editorInfo = startInput.getArguments().getParcelable("editorInfo");
        assertThat(editorInfo).isNotNull();
        assertThat(editorInfo.imeOptions & EditorInfo.IME_FLAG_NAVIGATE_NEXT)
                .isEqualTo(navigateNext ? EditorInfo.IME_FLAG_NAVIGATE_NEXT : 0);
        assertThat(editorInfo.imeOptions & EditorInfo.IME_FLAG_NAVIGATE_PREVIOUS)
                .isEqualTo(navigatePrevious ? EditorInfo.IME_FLAG_NAVIGATE_PREVIOUS : 0);
    }

    /**
     * Regression test for Bug 209958658.
     */
    @Test
    public void testEndBatchEditReturnValue() {
        EditText editText = new EditText(InstrumentationRegistry.getInstrumentation().getContext());
        EditorInfo editorInfo = new EditorInfo();
        InputConnection editableInputConnection = editText.onCreateInputConnection(editorInfo);
        assertThat(editableInputConnection.beginBatchEdit()).isTrue();
        assertThat(editableInputConnection.beginBatchEdit()).isTrue();
        assertThat(editableInputConnection.endBatchEdit()).isTrue();
        assertThat(editableInputConnection.endBatchEdit()).isFalse();

        // Extra invocations of endBatchEdit() continue to return false.
        assertThat(editableInputConnection.endBatchEdit()).isFalse();
    }

    /**
     * Verifies that IME receives a hide request when an active {@link EditText} becomes
     * disabled.
     */
    @Test
    public void testHideSoftInputWhenDisabled() throws Exception {
        try (MockImeSession imeSession = MockImeSession.create(
                InstrumentationRegistry.getInstrumentation().getContext(),
                InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();

            final String marker = getTestMarker();
            final AtomicReference<EditText> editTextRef = new AtomicReference<>();
            TestActivity.startSync(activity-> {
                final LinearLayout layout = new LinearLayout(activity);
                layout.setOrientation(LinearLayout.VERTICAL);

                final EditText editText = new EditText(activity);
                editText.setPrivateImeOptions(marker);
                editText.requestFocus();
                editTextRef.set(editText);

                layout.addView(editText);
                return layout;
            });
            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);

            final var editText = editTextRef.get();
            runOnMainSync(() -> editText.getContext().getSystemService(InputMethodManager.class)
                    .showSoftInput(editText, 0));
            expectEvent(stream, event -> "showSoftInput".equals(event.getEventName()), TIMEOUT);

            runOnMainSync(() -> editText.setEnabled(false));
            expectEvent(stream, event -> "hideSoftInput".equals(event.getEventName()), TIMEOUT);
        }
    }

    /**
     * Verifies that IME receives a hide request when an active {@link EditText} receives
     * {@link EditorInfo#IME_ACTION_DONE}.
     */
    @Test
    public void testHideSoftInputByActionDone() throws Exception {
        try (MockImeSession imeSession = MockImeSession.create(
                InstrumentationRegistry.getInstrumentation().getContext(),
                InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();

            final String marker = getTestMarker();
            final AtomicReference<EditText> editTextRef = new AtomicReference<>();
            TestActivity.startSync(activity-> {
                final LinearLayout layout = new LinearLayout(activity);
                layout.setOrientation(LinearLayout.VERTICAL);

                final EditText editText = new EditText(activity);
                editText.setPrivateImeOptions(marker);
                editText.requestFocus();
                editTextRef.set(editText);

                layout.addView(editText);
                return layout;
            });
            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);

            final var editText = editTextRef.get();
            runOnMainSync(() -> editText.getContext().getSystemService(InputMethodManager.class)
                    .showSoftInput(editText, 0));
            expectEvent(stream, event -> "showSoftInput".equals(event.getEventName()), TIMEOUT);

            expectCommand(stream, imeSession.callPerformEditorAction(EditorInfo.IME_ACTION_DONE),
                    TIMEOUT);
            expectEvent(stream, event -> "hideSoftInput".equals(event.getEventName()), TIMEOUT);
        }
    }
}
