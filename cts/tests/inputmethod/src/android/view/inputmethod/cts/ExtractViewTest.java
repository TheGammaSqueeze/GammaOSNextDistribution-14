/*
 * Copyright (C) 2024 The Android Open Source Project
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

import static android.view.inputmethod.cts.util.InputMethodVisibilityVerifier.expectImeVisible;

import static com.android.cts.mockime.ImeEventStreamTestUtils.editorMatcher;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectEvent;

import static com.google.common.truth.Truth.assertThat;

import android.app.Instrumentation;
import android.os.SystemClock;
import android.view.WindowInsets;
import android.view.inputmethod.cts.util.TestActivity;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.SystemUtil;
import com.android.cts.mockime.ImeEventStream;
import com.android.cts.mockime.ImeSettings;
import com.android.cts.mockime.MockIme;
import com.android.cts.mockime.MockImeSession;

import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class ExtractViewTest {
    private static final long TIMEOUT = TimeUnit.SECONDS.toMillis(5);
    private static final long START_INPUT_TIMEOUT = TimeUnit.SECONDS.toMillis(10);

    private static final String TEST_MARKER_PREFIX =
            "android.view.inputmethod.cts.ExtractViewTest";

    // https://developer.android.com/reference/android/R.id#inputExtractEditText
    private static final BySelector EXTRACT_EDIT_TEXT_SELECTOR =
            By.res("android", "inputExtractEditText");

    // https://developer.android.com/reference/android/R.id#inputExtractAccessories
    private static final BySelector EXTRACT_ACCESSORIES_SELECTOR =
            By.res("android", "inputExtractAccessories");

    // https://developer.android.com/reference/android/R.id#inputExtractAction
    private static final BySelector EXTRACT_ACTION_SELECTOR =
            By.res("android", "inputExtractAction");

    private Instrumentation mInstrumentation;
    private UiDevice mUiDevice;

    private static String getTestMarker() {
        return TEST_MARKER_PREFIX + "/" + SystemClock.elapsedRealtimeNanos();
    }

    @Before
    public void setUp() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mUiDevice = UiDevice.getInstance(mInstrumentation);
    }

    @ApiTest(apis = {"android.inputmethodservice.InputMethodService#onCreateExtractTextView"})
    @Test
    public void testOnCreateExtractTextView() throws Exception {
        String marker = getTestMarker();
        try (MockImeSession imeSession = MockImeSession.create(
                InstrumentationRegistry.getInstrumentation().getContext(),
                InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                new ImeSettings.Builder().setFullscreenModePolicy(
                                ImeSettings.FullscreenModePolicy.FORCE_FULLSCREEN)
                        .setCustomExtractTextViewEnabled(false))) {
            final ImeEventStream stream = imeSession.openEventStream();
            final EditText editText = startTestActivityAndReturnEditText(marker);
            expectEvent(stream, editorMatcher("onStartInput", marker), START_INPUT_TIMEOUT);

            // Show IME in fullscreen mode with extract view, and verify the view.
            mInstrumentation.runOnMainSync(
                    () -> editText.getWindowInsetsController().show(WindowInsets.Type.ime()));
            expectImeVisible(TIMEOUT);
            assertThat(mUiDevice.wait(Until.findObject(EXTRACT_EDIT_TEXT_SELECTOR), TIMEOUT))
                    .isNotNull();

            // Call commitText() and verify the committed text.
            final String text = "commitText-" + marker;
            imeSession.callCommitText(text, 1 /* newCursorPosition */);
            assertThat(mUiDevice.wait(Until.findObject(By.text(text)), TIMEOUT)).isNotNull();
            SystemUtil.eventually(
                    () -> assertThat(editText.getText().toString()).isEqualTo(text), TIMEOUT);
        }
    }

    @ApiTest(apis = {"android.inputmethodservice.InputMethodService#onCreateExtractTextView"})
    @Test
    public void testOnCreateExtractTextViewWithCustomView() throws Exception {
        String marker = getTestMarker();
        try (MockImeSession imeSession = MockImeSession.create(
                InstrumentationRegistry.getInstrumentation().getContext(),
                InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                new ImeSettings.Builder().setFullscreenModePolicy(
                                ImeSettings.FullscreenModePolicy.FORCE_FULLSCREEN)
                        .setCustomExtractTextViewEnabled(true))) {
            final ImeEventStream stream = imeSession.openEventStream();
            final EditText editText = startTestActivityAndReturnEditText(marker);
            expectEvent(stream, editorMatcher("onStartInput", marker), START_INPUT_TIMEOUT);

            // Show IME in fullscreen mode with extract view, and verify the views.
            mInstrumentation.runOnMainSync(
                    () -> editText.getWindowInsetsController().show(WindowInsets.Type.ime()));
            expectImeVisible(TIMEOUT);
            assertThat(mUiDevice.wait(
                    Until.findObject(By.text(MockIme.CUSTOM_EXTRACT_EDIT_TEXT_LABEL)), TIMEOUT))
                    .isNotNull();
            assertThat(mUiDevice.wait(Until.findObject(EXTRACT_EDIT_TEXT_SELECTOR), TIMEOUT))
                    .isNotNull();
            assertThat(mUiDevice.wait(Until.findObject(EXTRACT_ACCESSORIES_SELECTOR), TIMEOUT))
                    .isNotNull();
            assertThat(mUiDevice.wait(Until.findObject(EXTRACT_ACTION_SELECTOR), TIMEOUT))
                    .isNotNull();

            // Call commitText() and verify the committed text.
            final String text = "commitText-" + marker;
            imeSession.callCommitText(text, 1 /* newCursorPosition */);
            assertThat(mUiDevice.wait(Until.findObject(By.text(text)), TIMEOUT)).isNotNull();
            SystemUtil.eventually(
                    () -> assertThat(editText.getText().toString()).isEqualTo(text), TIMEOUT);
        }
    }

    @ApiTest(apis = {"android.inputmethodservice.InputMethodService#setExtractView"})
    @Test
    public void testSetExtractView() throws Exception {
        String marker = getTestMarker();
        try (MockImeSession imeSession = MockImeSession.create(
                InstrumentationRegistry.getInstrumentation().getContext(),
                InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                new ImeSettings.Builder().setFullscreenModePolicy(
                                ImeSettings.FullscreenModePolicy.FORCE_FULLSCREEN)
                        .setCustomExtractTextViewEnabled(false))) {
            final ImeEventStream stream = imeSession.openEventStream();
            final EditText editText = startTestActivityAndReturnEditText(marker);
            expectEvent(stream, editorMatcher("onStartInput", marker), START_INPUT_TIMEOUT);

            // Show IME in fullscreen mode with extract view.
            mInstrumentation.runOnMainSync(
                    () -> editText.getWindowInsetsController().show(WindowInsets.Type.ime()));
            expectImeVisible(TIMEOUT);

            // Call setExtractView() and verify the views.
            final String label = "label-" + marker;
            imeSession.callSetExtractView(label);
            assertThat(mUiDevice.wait(Until.findObject(By.text(label)), TIMEOUT)).isNotNull();
            assertThat(mUiDevice.wait(Until.findObject(EXTRACT_EDIT_TEXT_SELECTOR), TIMEOUT))
                    .isNotNull();
            assertThat(mUiDevice.wait(Until.findObject(EXTRACT_ACCESSORIES_SELECTOR), TIMEOUT))
                    .isNotNull();
            assertThat(mUiDevice.wait(Until.findObject(EXTRACT_ACTION_SELECTOR), TIMEOUT))
                    .isNotNull();

            // Call commitText() and verify the committed text.
            final String text = "commitText-" + marker;
            imeSession.callCommitText(text, 1 /* newCursorPosition */);
            assertThat(mUiDevice.wait(Until.findObject(By.text(text)), TIMEOUT)).isNotNull();
            SystemUtil.eventually(
                    () -> assertThat(editText.getText().toString()).isEqualTo(text), TIMEOUT);
        }
    }

    @NotNull
    private EditText startTestActivityAndReturnEditText(String marker) {
        AtomicReference<EditText> editTextRef = new AtomicReference<>();
        TestActivity.startSync(activity -> {
            final LinearLayout layout = new LinearLayout(activity);
            layout.setOrientation(LinearLayout.VERTICAL);

            final EditText editText = new EditText(activity);
            editText.setPrivateImeOptions(marker);
            editText.requestFocus();
            editTextRef.set(editText);
            layout.addView(editText);

            return layout;
        });
        return editTextRef.get();
    }

}
