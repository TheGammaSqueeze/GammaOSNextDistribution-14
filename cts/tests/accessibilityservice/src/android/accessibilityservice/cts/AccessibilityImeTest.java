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

package android.accessibilityservice.cts;

import static android.accessibility.cts.common.InstrumentedAccessibilityService.enableService;
import static android.accessibilityservice.cts.utils.ActivityLaunchUtils.launchActivityAndWaitForItToBeOnscreen;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.accessibility.cts.common.AccessibilityDumpOnFailureRule;
import android.accessibility.cts.common.InstrumentedAccessibilityService;
import android.accessibilityservice.InputMethod;
import android.accessibilityservice.cts.activities.AccessibilityEndToEndActivity;
import android.accessibilityservice.cts.utils.AsyncUtils;
import android.accessibilityservice.cts.utils.RunOnMainUtils;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.Presubmit;
import android.text.TextUtils;
import android.util.Log;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.FlakyTest;
import androidx.test.filters.LargeTest;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.PollingCheck;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Test one a11y service requiring ime capabilities and one doesn't.
 */
@LargeTest
@AppModeFull
@RunWith(AndroidJUnit4.class)
@CddTest(requirements = {"3.10/C-1-1,C-1-2"})
@Presubmit
public class AccessibilityImeTest {
    private static final String LOG_TAG = "AccessibilityImeTest";
    private static Instrumentation sInstrumentation;
    private static UiAutomation sUiAutomation;

    private static StubImeAccessibilityService sStubImeAccessibilityService;
    private static StubNonImeAccessibilityService sStubNonImeAccessibilityService;

    private AccessibilityEndToEndActivity mActivity;

    private ActivityTestRule<AccessibilityEndToEndActivity> mActivityRule =
            new ActivityTestRule<>(AccessibilityEndToEndActivity.class, false, false);

    private AccessibilityDumpOnFailureRule mDumpOnFailureRule =
            new AccessibilityDumpOnFailureRule();

    private EditText mEditText;
    private String mInitialText;

    @Rule
    public final RuleChain mRuleChain = RuleChain
            .outerRule(mActivityRule)
            .around(mDumpOnFailureRule);

    @BeforeClass
    public static void oneTimeSetup() throws Exception {
        sInstrumentation = InstrumentationRegistry.getInstrumentation();
        sUiAutomation = sInstrumentation.getUiAutomation();
        sInstrumentation
                .getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        sStubImeAccessibilityService = enableService(StubImeAccessibilityService.class);
        sStubNonImeAccessibilityService = enableService(StubNonImeAccessibilityService.class);
    }

    @AfterClass
    public static void postTestTearDown() {
        sStubImeAccessibilityService.disableSelfAndRemove();
        sStubNonImeAccessibilityService.disableSelfAndRemove();
        sUiAutomation.destroy();
    }

    @Before
    public void setUp() throws Exception {
        mActivity = launchActivityAndWaitForItToBeOnscreen(
                sInstrumentation, sUiAutomation, mActivityRule);
        // focus the edit text
        mEditText = mActivity.findViewById(R.id.edittext);
        // initial text
        mInitialText = mActivity.getString(R.string.text_input_blah);
    }

    /**
     * Verifies that
     * 1) {@link android.accessibilityservice.AccessibilityService#onCreateInputMethod()} will be
     * called and 2) it will return the default implementation of
     * {@link android.accessibilityservice.InputMethod}, which is still functional.
     */
    @Test
    public void testDefaultImplementation() throws Exception {
        InstrumentedAccessibilityService serviceToBeCleanedUp = null;
        try {
            final StubSimpleImeAccessibilityService service =
                    enableService(StubSimpleImeAccessibilityService.class);
            serviceToBeCleanedUp = service;
            assertTrue("time out waiting for onCreateInputMethod() to get called.",
                    service.awaitOnCreateInputMethod(AsyncUtils.DEFAULT_TIMEOUT_MS, MILLISECONDS));

            final InputMethod inputMethod = service.getInputMethod();
            assertNotNull(inputMethod);

            // Set a unique value to "privateImeOptions".
            final String markerValue = "Test-" + SystemClock.elapsedRealtimeNanos();
            sInstrumentation.runOnMainSync(() -> mEditText.setPrivateImeOptions(markerValue));

            requestFocusAndSetCursorToEnd();

            // Wait until EditorInfo#privateImeOptions becomes the expected marker value.
            PollingCheck.waitFor(AsyncUtils.DEFAULT_TIMEOUT_MS,
                    () -> TextUtils.equals(
                            markerValue,
                            RunOnMainUtils.getOnMain(sInstrumentation, () -> {
                                final EditorInfo editorInfo =
                                        inputMethod.getCurrentInputEditorInfo();
                                return editorInfo != null ? editorInfo.privateImeOptions : null;
                            })));

            assertTrue(RunOnMainUtils.getOnMain(sInstrumentation,
                    inputMethod::getCurrentInputStarted));

            final InputMethod.AccessibilityInputConnection connection =
                    inputMethod.getCurrentInputConnection();
            assertNotNull(connection);

            connection.commitText("abc", 1, null);

            final String expectedText = mInitialText + "abc";
            PollingCheck.waitFor(AsyncUtils.DEFAULT_TIMEOUT_MS,
                    () -> RunOnMainUtils.getOnMain(sInstrumentation,
                            () -> TextUtils.equals(expectedText, mEditText.getText())));
        } finally {
            if (serviceToBeCleanedUp != null) {
                serviceToBeCleanedUp.disableSelfAndRemove();
            }
        }
    }

    /**
     * Verifies that calling
     * {@link InputMethodManager#updateSelection(android.view.View, int, int, int, int)} does not
     * directly invoke {@link android.accessibilityservice.InputMethod#onUpdateSelection(int, int,
     * int, int, int, int)}, which can cause an infinite-loop.
     */
    @AppModeFull(reason = "No need to consider instant apps that host A11yIme")
    @Test
    public void testOnUpdateSelectionForInProcessA11yIme() throws Exception {
        final CountDownLatch startInputLatch = new CountDownLatch(1);

        final String marker = this.getClass() + "/"  + SystemClock.elapsedRealtimeNanos();
        sInstrumentation.runOnMainSync(() -> {
            sStubImeAccessibilityService.setOnStartInputCallback((editorInfo, restarting) -> {
                if (TextUtils.equals(editorInfo.privateImeOptions, marker)) {
                    startInputLatch.countDown();
                    sStubImeAccessibilityService.setOnStartInputCallback(null);
                }
            });

            mEditText.setPrivateImeOptions(marker);
            mEditText.requestFocus();
        });

        assertTrue("Waiting for input to start",
                startInputLatch.await(AsyncUtils.DEFAULT_TIMEOUT_MS, MILLISECONDS));

        final ThreadLocal<Boolean> invocationOnGoing = new ThreadLocal<>();
        final AtomicBoolean directInvocationDetected = new AtomicBoolean(false);
        final CountDownLatch onUpdateSelectionLatch = new CountDownLatch(1);
        final int expectedNewSelStart = 123;
        final int expectedNewSelEnd = 321;

        sInstrumentation.runOnMainSync(() -> {
            sStubImeAccessibilityService.setOnUpdateSelectionCallback((oldSelStart, oldSelEnd,
                    newSelStart, newSelEnd, candidatesStart, candidatesEnd) -> {
                if (newSelStart == expectedNewSelStart && newSelEnd == expectedNewSelEnd) {
                    directInvocationDetected.set(invocationOnGoing.get());
                    onUpdateSelectionLatch.countDown();
                    sStubImeAccessibilityService.setOnUpdateSelectionCallback(null);
                }
            });

            final InputMethodManager imm =
                    mEditText.getContext().getSystemService(InputMethodManager.class);
            invocationOnGoing.set(true);
            try {
                imm.updateSelection(mEditText, expectedNewSelStart, expectedNewSelEnd, -1, -1);
            } finally {
                invocationOnGoing.set(false);
            }
        });

        assertTrue("Waiting for InputMethod#onUpdateSelection() to get called",
                onUpdateSelectionLatch.await(AsyncUtils.DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS));

        assertFalse("InputMethodManager#updateSelection must not directly invoke"
                + " InputMethod#onUpdateSelection", directInvocationDetected.get());
    }

    @Test
    @FlakyTest
    public void testInputConnection_requestIme() throws InterruptedException {
        CountDownLatch startInputLatch = new CountDownLatch(1);
        sStubImeAccessibilityService.setStartInputCountDownLatch(startInputLatch);

        requestFocusAndSetCursorToEnd();

        assertTrue("time out waiting for input to start",
                startInputLatch.await(AsyncUtils.DEFAULT_TIMEOUT_MS, MILLISECONDS));
        assertNotNull(sStubImeAccessibilityService.getInputMethod());
        InputMethod.AccessibilityInputConnection connection =
                sStubImeAccessibilityService.getInputMethod().getCurrentInputConnection();
        assertNotNull(connection);

        sStubImeAccessibilityService.setSelectionTarget(mInitialText.length() * 2);
        CountDownLatch selectionChangeLatch = new CountDownLatch(1);
        sStubImeAccessibilityService.setSelectionChangeLatch(selectionChangeLatch);

        connection.commitText(mInitialText, 1, null);

        assertTrue("time out waiting for selection change",
                selectionChangeLatch.await(AsyncUtils.DEFAULT_TIMEOUT_MS, MILLISECONDS));
        assertEquals(mInitialText + mInitialText, mEditText.getText().toString());
    }

    @Test
    public void testInputConnection_notRequestIme() throws InterruptedException {
        CountDownLatch startInputLatch = new CountDownLatch(1);
        sStubNonImeAccessibilityService.setStartInputCountDownLatch(startInputLatch);

        requestFocusAndSetCursorToEnd();

        assertFalse("should time out waiting for input to start",
                startInputLatch.await(AsyncUtils.DEFAULT_TIMEOUT_MS, MILLISECONDS));
        assertNull(sStubNonImeAccessibilityService.getInputMethod());
    }

    @Test
    @FlakyTest
    public void testSelectionChange_requestIme() throws InterruptedException {
        CountDownLatch startInputLatch = new CountDownLatch(1);
        sStubImeAccessibilityService.setStartInputCountDownLatch(startInputLatch);

        requestFocusAndSetCursorToEnd();

        assertTrue("time out waiting for input to start",
                startInputLatch.await(AsyncUtils.DEFAULT_TIMEOUT_MS, MILLISECONDS));
        assertNotNull(sStubImeAccessibilityService.getInputMethod());
        InputMethod.AccessibilityInputConnection connection =
                sStubImeAccessibilityService.getInputMethod().getCurrentInputConnection();
        assertNotNull(connection);

        final int targetPos = mInitialText.length() - 1;
        sStubImeAccessibilityService.setSelectionTarget(targetPos);
        CountDownLatch selectionChangeLatch = new CountDownLatch(1);
        sStubImeAccessibilityService.setSelectionChangeLatch(selectionChangeLatch);

        connection.setSelection(targetPos, targetPos);
        boolean changed = selectionChangeLatch.await(AsyncUtils.DEFAULT_TIMEOUT_MS, MILLISECONDS);
        // Add some logs to help debug flakiness.
        if (!changed) {
            Log.v(LOG_TAG, "selection start after set selection is "
                    + mEditText.getSelectionStart());
            Log.v(LOG_TAG, "selection end after set selection is "
                    + mEditText.getSelectionEnd());

        }
        assertTrue("time out waiting for selection change", changed);

        assertEquals(targetPos, mEditText.getSelectionStart());
        assertEquals(targetPos, mEditText.getSelectionEnd());

        assertEquals(targetPos, sStubImeAccessibilityService.selStart);
        assertEquals(targetPos, sStubImeAccessibilityService.selEnd);
    }

    @Test
    public void testSelectionChange_notRequestIme() throws InterruptedException {
        requestFocusAndSetCursorToEnd();

        final int targetPos = mInitialText.length() - 1;
        sStubNonImeAccessibilityService.setSelectionTarget(targetPos);
        CountDownLatch selectionChangeLatch = new CountDownLatch(1);
        sStubNonImeAccessibilityService.setSelectionChangeLatch(selectionChangeLatch);

        sInstrumentation.runOnMainSync(() -> {
            mEditText.setSelection(targetPos, targetPos);
        });
        assertFalse("should time out waiting for selection change",
                selectionChangeLatch.await(AsyncUtils.DEFAULT_TIMEOUT_MS, MILLISECONDS));

        assertEquals(targetPos, mEditText.getSelectionStart());
        assertEquals(targetPos, mEditText.getSelectionEnd());

        assertEquals(-1, sStubNonImeAccessibilityService.oldSelStart);
        assertEquals(-1, sStubNonImeAccessibilityService.oldSelEnd);
        assertEquals(-1, sStubNonImeAccessibilityService.selStart);
        assertEquals(-1, sStubNonImeAccessibilityService.selEnd);
    }

    private void requestFocusAndSetCursorToEnd() {
        sInstrumentation.runOnMainSync(() -> {
            mEditText.requestFocus();
            mEditText.setSelection(mInitialText.length(), mInitialText.length());
        });
        assertTrue("edit text is not focused", mEditText.isFocused());
        assertEquals("selection start not set to text end", mInitialText.length(),
                mEditText.getSelectionStart());
        assertEquals("selection end not set to text end", mInitialText.length(),
                mEditText.getSelectionEnd());
    }
}
