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

import static com.android.cts.mockime.ImeEventStreamTestUtils.editorMatcher;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectCommand;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectEvent;
import static com.android.cts.mockime.ImeEventStreamTestUtils.notExpectEvent;
import static com.android.cts.mockime.ImeEventStreamTestUtils.withDescription;

import static com.google.common.truth.Truth.assertThat;

import android.app.Instrumentation;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.text.TextUtils;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;
import android.view.inputmethod.cts.util.EndToEndImeTestBase;
import android.view.inputmethod.cts.util.SecureSettingsUtils;
import android.view.inputmethod.cts.util.TestActivity;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.cts.mockime.ImeEvent;
import com.android.cts.mockime.ImeEventStream;
import com.android.cts.mockime.ImeSettings;
import com.android.cts.mockime.MockImeSession;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

@MediumTest
@RunWith(AndroidJUnit4.class)
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public class InputMethodSubtypeEndToEndTest extends EndToEndImeTestBase {
    static final long TIMEOUT = TimeUnit.SECONDS.toMillis(5);

    private static final InputMethodSubtype IMPLICITLY_ENABLED_TEST_SUBTYPE =
            new InputMethodSubtype.InputMethodSubtypeBuilder()
                    .setSubtypeId(0x01234567)
                    .setOverridesImplicitlyEnabledSubtype(true)
                    .build();

    private static final InputMethodSubtype IMPLICITLY_ENABLED_TEST_SUBTYPE2 =
            new InputMethodSubtype.InputMethodSubtypeBuilder()
                    .setSubtypeId(0x12345678)
                    .setOverridesImplicitlyEnabledSubtype(true)
                    .build();

    private static final InputMethodSubtype TEST_SUBTYPE1 =
            new InputMethodSubtype.InputMethodSubtypeBuilder()
                    .setSubtypeId(0x23456789)
                    .build();

    private static final InputMethodSubtype TEST_SUBTYPE2 =
            new InputMethodSubtype.InputMethodSubtypeBuilder()
                    .setSubtypeId(0x3456789a)
                    .build();

    private static final InputMethodSubtype TEST_SUBTYPE3 =
            new InputMethodSubtype.InputMethodSubtypeBuilder()
                    .setSubtypeId(0x456789ab)
                    .build();

    private static final String TEST_MARKER_PREFIX =
            "android.view.inputmethod.cts.InputMethodSubtypeTest";

    private static String getTestMarker() {
        return TEST_MARKER_PREFIX + "/"  + SystemClock.elapsedRealtimeNanos();
    }

    private void launchTestActivity(@NonNull String marker) {
        TestActivity.startSync(activity -> {
            final EditText editText = new EditText(activity);
            editText.setPrivateImeOptions(marker);

            final LinearLayout layout = new LinearLayout(activity);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.addView(editText);

            editText.requestFocus();
            return layout;
        });
    }

    @NonNull
    private final InputMethodManager mImm = Objects.requireNonNull(
            InstrumentationRegistry.getInstrumentation().getTargetContext().getSystemService(
                    InputMethodManager.class));

    /**
     * Verifies that {@link InputMethodManager#getCurrentInputMethodSubtype()} returns {@code null}
     * if the current IME does not have any {@link InputMethodSubtype}.
     */
    @Test
    public void testGetCurrentInputMethodSubtypeNull() throws Exception {
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        try (MockImeSession imeSession = MockImeSession.create(instrumentation.getContext(),
                instrumentation.getUiAutomation(), new ImeSettings.Builder())) {

            assertThat(mImm.getCurrentInputMethodSubtype()).isNull();
        }
    }

    /**
     * Verifies that {@link InputMethodManager#getCurrentInputMethodSubtype()} returns an expected
     * {@link InputMethodSubtype}.
     */
    @Test
    public void testGetCurrentInputMethodSubtype() throws Exception {
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        try (MockImeSession imeSession = MockImeSession.create(instrumentation.getContext(),
                instrumentation.getUiAutomation(),
                new ImeSettings.Builder().setAdditionalSubtypes(IMPLICITLY_ENABLED_TEST_SUBTYPE))) {

            assertThat(mImm.getCurrentInputMethodSubtype())
                    .isEqualTo(IMPLICITLY_ENABLED_TEST_SUBTYPE);
        }
    }

    /**
     * Verifies that
     * {@link android.inputmethodservice.InputMethodService#onCurrentInputMethodSubtypeChanged(
     * InputMethodSubtype)} will not happen for the cold startup.
     */
    @Test
    public void testNoOnCurrentInputMethodSubtypeChangedForColdStartup() throws Exception {
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        try (MockImeSession imeSession = MockImeSession.create(instrumentation.getContext(),
                instrumentation.getUiAutomation(),
                new ImeSettings.Builder().setAdditionalSubtypes(IMPLICITLY_ENABLED_TEST_SUBTYPE))) {
            final ImeEventStream stream = imeSession.openEventStream();

            final String marker = getTestMarker();
            launchTestActivity(marker);

            expectEvent(stream, event -> "onCreate".equals(event.getEventName()), TIMEOUT);
            final ImeEventStream eventsAfterOnCreate = stream.copy();

            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);

            // It's OK to pass 0 to timeout as we've already made sure that "onStartInput" happened.
            notExpectEvent(eventsAfterOnCreate, withDescription(
                    "onCurrentInputMethodSubtypeChanged(newSubtype=any)",
                    event -> "onCurrentInputMethodSubtypeChanged".equals(event.getEventName())), 0);
        }
    }

    /**
     * Verifies that {@link android.inputmethodservice.InputMethodService#switchInputMethod(String,
     * InputMethodSubtype)} works to switch {@link InputMethodSubtype} in the same IME.
     */
    @Test
    public void testSubtypeSwitchingInTheSameIme() throws Exception {
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        try (MockImeSession imeSession = MockImeSession.create(instrumentation.getContext(),
                instrumentation.getUiAutomation(),
                new ImeSettings.Builder().setAdditionalSubtypes(
                        IMPLICITLY_ENABLED_TEST_SUBTYPE, IMPLICITLY_ENABLED_TEST_SUBTYPE2))) {
            final ImeEventStream stream = imeSession.openEventStream();

            final String marker = getTestMarker();
            launchTestActivity(marker);
            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);

            final InputMethodSubtype initialSubtype = mImm.getCurrentInputMethodSubtype();
            assertThat(initialSubtype).isEqualTo(IMPLICITLY_ENABLED_TEST_SUBTYPE);

            expectCommand(stream, imeSession.callSwitchInputMethod(
                    imeSession.getImeId(), IMPLICITLY_ENABLED_TEST_SUBTYPE2), TIMEOUT);
            final ImeEvent result = expectEvent(stream, event ->
                    "onCurrentInputMethodSubtypeChanged".equals(event.getEventName()), TIMEOUT);
            final InputMethodSubtype actualNewSubtype =
                    result.getArguments().getParcelable("newSubtype", InputMethodSubtype.class);
            assertThat(actualNewSubtype).isEqualTo(IMPLICITLY_ENABLED_TEST_SUBTYPE2);

            assertThat(mImm.getLastInputMethodSubtype()).isEqualTo(IMPLICITLY_ENABLED_TEST_SUBTYPE);
            assertThat(mImm.getCurrentInputMethodSubtype())
                    .isEqualTo(IMPLICITLY_ENABLED_TEST_SUBTYPE2);
        }
    }

    /**
     * Verifies that
     * {@link InputMethodManager#setExplicitlyEnabledInputMethodSubtypes(String, int[])} works.
     */
    @Test
    public void testSetExplicitlyEnabledInputMethodSubtypes() throws Exception {
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        try (MockImeSession imeSession = MockImeSession.create(instrumentation.getContext(),
                instrumentation.getUiAutomation(),
                new ImeSettings.Builder().setAdditionalSubtypes(TEST_SUBTYPE1, TEST_SUBTYPE2,
                        IMPLICITLY_ENABLED_TEST_SUBTYPE))) {
            final ImeEventStream stream = imeSession.openEventStream();

            final String marker = getTestMarker();
            launchTestActivity(marker);

            expectEvent(stream, event -> "onCreate".equals(event.getEventName()), TIMEOUT);

            final InputMethodInfo mockImeInfo = mImm.getEnabledInputMethodList().stream()
                    .filter(imi -> TextUtils.equals(imi.getId(), imeSession.getImeId()))
                    .findFirst()
                    .get();

            // By default, implicitlyEnabled subtypes are enabled.
            assertThat(mImm.getEnabledInputMethodSubtypeList(mockImeInfo, false)).isEmpty();
            assertThat(mImm.getEnabledInputMethodSubtypeList(mockImeInfo, true))
                    .containsExactly(IMPLICITLY_ENABLED_TEST_SUBTYPE);

            // IMM#setEnabledInputMethodSubtypes() should be able to update the enabled subtypes.
            expectCommand(stream, imeSession.callSetExplicitlyEnabledInputMethodSubtypes(
                    imeSession.getImeId(), new int[]{TEST_SUBTYPE1.hashCode()}), TIMEOUT);
            assertThat(mImm.getEnabledInputMethodSubtypeList(mockImeInfo, false))
                    .containsExactly(TEST_SUBTYPE1);
            assertThat(mImm.getEnabledInputMethodSubtypeList(mockImeInfo, true))
                    .containsExactly(TEST_SUBTYPE1);

            // an empty array will reset the enabled subtypes.
            expectCommand(stream, imeSession.callSetExplicitlyEnabledInputMethodSubtypes(
                    imeSession.getImeId(),
                    new int[]{}), TIMEOUT);
            assertThat(mImm.getEnabledInputMethodSubtypeList(mockImeInfo, false)).isEmpty();
            assertThat(mImm.getEnabledInputMethodSubtypeList(mockImeInfo, true))
                    .containsExactly(IMPLICITLY_ENABLED_TEST_SUBTYPE);

            // duplicate entries should be just ignored.
            expectCommand(stream, imeSession.callSetExplicitlyEnabledInputMethodSubtypes(
                    imeSession.getImeId(),
                    new int[]{TEST_SUBTYPE1.hashCode(), TEST_SUBTYPE1.hashCode()}), TIMEOUT);
            assertThat(mImm.getEnabledInputMethodSubtypeList(mockImeInfo, false))
                    .containsExactly(TEST_SUBTYPE1);
            assertThat(mImm.getEnabledInputMethodSubtypeList(mockImeInfo, true))
                    .containsExactly(TEST_SUBTYPE1);

            // nonexistent entries should be just ignored.
            expectCommand(stream, imeSession.callSetExplicitlyEnabledInputMethodSubtypes(
                    imeSession.getImeId(),
                    new int[]{TEST_SUBTYPE2.hashCode(), TEST_SUBTYPE3.hashCode()}), TIMEOUT);
            assertThat(mImm.getEnabledInputMethodSubtypeList(mockImeInfo, false))
                    .containsExactly(TEST_SUBTYPE2);
            assertThat(mImm.getEnabledInputMethodSubtypeList(mockImeInfo, true))
                    .containsExactly(TEST_SUBTYPE2);
        }
    }

    /**
     * This is a regression test against Bug 291762796.
     *
     * <p>Suppose the scenario when the system is switching to an IME whose subtypes are already
     * enabled in {@link android.provider.Settings.Secure#ENABLED_INPUT_METHODS} but such subtypes
     * are not included in {@link InputMethodInfo#getSubtypeAt(int)}. This is rather common when
     * such subtypes are dynamically registered by
     * {@link InputMethodManager#setAdditionalInputMethodSubtypes(String, InputMethodSubtype[])},
     * because the system automatically removes additional subtypes in some cases such as updating
     * the IME APK. Then the IME calls
     * {@link InputMethodManager#setAdditionalInputMethodSubtypes(String, InputMethodSubtype[])} to
     * add enabled {@link InputMethodSubtype}s again.
     *
     * <p>There had been a bug that subsequent call of
     * {@link android.inputmethodservice.InputMethodService#switchInputMethod(String,
     * InputMethodSubtype)} fails if {@code imeId} is the caller's IME ID and {@code subtype} is one
     * of dynamically added {@link InputMethodSubtype} that was already enabled. This test makes
     * sure that the issue is not regressed again.</p>
     */
    @Test
    public void testSetAdditionalInputMethodSubtypesForAlreadyEnabledSubtype() throws Exception {
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        try (MockImeSession imeSession = MockImeSession.create(instrumentation.getContext(),
                instrumentation.getUiAutomation(),
                new ImeSettings.Builder())) {
            // Emulate the scenario when additional subtypes are already enabled.
            SecureSettingsUtils.updateEnabledInputMethods(instrumentation.getContext(),
                    imeSession.getImeId(), new InputMethodSubtype[]{
                            TEST_SUBTYPE1, TEST_SUBTYPE2,
                    });

            final ImeEventStream stream = imeSession.openEventStream();
            final String marker = getTestMarker();
            launchTestActivity(marker);

            expectEvent(stream, event -> "onCreate".equals(event.getEventName()), TIMEOUT);

            expectCommand(stream, imeSession.callSetAdditionalInputMethodSubtypes(
                    imeSession.getImeId(), new InputMethodSubtype[]{TEST_SUBTYPE2}), TIMEOUT);
            expectCommand(stream, imeSession.callSwitchInputMethod(
                    imeSession.getImeId(), TEST_SUBTYPE2), TIMEOUT);
            expectEvent(stream, event -> {
                if (!"onCurrentInputMethodSubtypeChanged".equals(event.getEventName())) {
                    return false;
                }
                var subtype = event.getArguments().getParcelable("newSubtype",
                        InputMethodSubtype.class);
                return Objects.equals(subtype, TEST_SUBTYPE2);
            }, TIMEOUT);
        }
    }
}
