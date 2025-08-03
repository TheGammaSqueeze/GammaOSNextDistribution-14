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

import static org.junit.Assert.assertTrue;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.accessibility.cts.common.AccessibilityDumpOnFailureRule;
import android.accessibilityservice.InputMethod;
import android.accessibilityservice.cts.activities.AccessibilityEndToEndActivity;
import android.accessibilityservice.cts.utils.AsyncUtils;
import android.accessibilityservice.cts.utils.InputConnectionSplitter;
import android.accessibilityservice.cts.utils.NoOpInputConnection;
import android.accessibilityservice.cts.utils.RunOnMainUtils;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.Presubmit;
import android.text.InputType;
import android.text.TextUtils;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.FlakyTest;
import androidx.test.filters.LargeTest;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.CddTest;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tests for {@link InputMethod.AccessibilityInputConnection}.
 */
@LargeTest
@AppModeFull
@RunWith(AndroidJUnit4.class)
@CddTest(requirements = {"3.10/C-1-1,C-1-2"})
@Presubmit
public final class AccessibilityInputConnectionTest {
    private static Instrumentation sInstrumentation;
    private static UiAutomation sUiAutomation;

    private static StubImeAccessibilityService sStubImeAccessibilityService;

    private ActivityTestRule<AccessibilityEndToEndActivity> mActivityRule =
            new ActivityTestRule<>(AccessibilityEndToEndActivity.class, false, false);

    private AccessibilityDumpOnFailureRule mDumpOnFailureRule =
            new AccessibilityDumpOnFailureRule();

    private AtomicReference<InputConnection> mLastInputConnectionSpy = new AtomicReference<>();

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
    }

    @AfterClass
    public static void postTestTearDown() {
        sStubImeAccessibilityService.disableSelfAndRemove();
        sUiAutomation.destroy();
    }

    @Before
    public void setUp() throws Exception {
        final String markerValue = "Test-" + SystemClock.elapsedRealtimeNanos();
        final CountDownLatch startInputLatch = new CountDownLatch(1);
        sStubImeAccessibilityService.setOnStartInputCallback(((editorInfo, restarting) -> {
            if (editorInfo != null && TextUtils.equals(markerValue, editorInfo.privateImeOptions)) {
                startInputLatch.countDown();
            }
        }));

        final AccessibilityEndToEndActivity activity = launchActivityAndWaitForItToBeOnscreen(
                sInstrumentation, sUiAutomation, mActivityRule);

        final LinearLayout layout = (LinearLayout) activity.findViewById(R.id.edittext).getParent();
        sInstrumentation.runOnMainSync(() -> {
            final EditText editText = new EditText(activity) {
                @Override
                public InputConnection onCreateInputConnection(EditorInfo editorInfo) {
                    final InputConnection ic = super.onCreateInputConnection(editorInfo);
                    // For some reasons, Mockito.spy() for real Framework classes did not work...
                    // Use NoOpInputConnection/InputConnectionSplitter instead.
                    final InputConnection spy = Mockito.spy(new NoOpInputConnection());
                    if (mLastInputConnectionSpy.get() == null) {
                        mLastInputConnectionSpy.set(spy);
                    }
                    return new InputConnectionSplitter(ic, spy);
                }
            };
            editText.setPrivateImeOptions(markerValue);
            layout.addView(editText);
            editText.requestFocus();
        });

        // Wait until EditorInfo#privateImeOptions becomes the expected marker value.
        assertTrue("time out waiting for input to start",
                startInputLatch.await(AsyncUtils.DEFAULT_TIMEOUT_MS, MILLISECONDS));
    }

    private InputMethod.AccessibilityInputConnection getInputConnection() {
        return RunOnMainUtils.getOnMain(
                sInstrumentation,
                () -> sStubImeAccessibilityService.getInputMethod().getCurrentInputConnection());
    }

    private InputConnection resetAndGetLastInputConnectionSpy() {
        final InputConnection spy = mLastInputConnectionSpy.get();
        Mockito.reset(spy);
        return spy;
    }

    @Test
    public void testCommitText() {
        final InputMethod.AccessibilityInputConnection ic = getInputConnection();
        final InputConnection spy = resetAndGetLastInputConnectionSpy();

        ic.commitText("test", 1, null);
        Mockito.verify(spy, Mockito.timeout(AsyncUtils.DEFAULT_TIMEOUT_MS))
                .commitText("test", 1, null);
    }

    @Test
    public void testSetSelection() {
        final InputMethod.AccessibilityInputConnection ic = getInputConnection();
        final InputConnection spy = resetAndGetLastInputConnectionSpy();

        ic.setSelection(1, 2);
        Mockito.verify(spy, Mockito.timeout(AsyncUtils.DEFAULT_TIMEOUT_MS)).setSelection(1, 2);
    }

    @Test
    public void testGetSurroundingText() {
        final InputMethod.AccessibilityInputConnection ic = getInputConnection();
        final InputConnection spy = resetAndGetLastInputConnectionSpy();

        ic.getSurroundingText(1, 2, InputConnection.GET_TEXT_WITH_STYLES);
        Mockito.verify(spy, Mockito.timeout(AsyncUtils.DEFAULT_TIMEOUT_MS))
                .getSurroundingText(1, 2, InputConnection.GET_TEXT_WITH_STYLES);
    }

    @Test
    public void testDeleteSurroundingText() {
        final InputMethod.AccessibilityInputConnection ic = getInputConnection();
        final InputConnection spy = resetAndGetLastInputConnectionSpy();

        ic.deleteSurroundingText(2, 1);
        Mockito.verify(spy, Mockito.timeout(AsyncUtils.DEFAULT_TIMEOUT_MS))
                .deleteSurroundingText(2, 1);
    }

    @Test
    public void testSendKeyEvent() {
        final InputMethod.AccessibilityInputConnection ic = getInputConnection();
        final InputConnection spy = resetAndGetLastInputConnectionSpy();

        final long eventTime = SystemClock.uptimeMillis();
        final KeyEvent keyEvent = new KeyEvent(eventTime, eventTime,
                KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A, 0, 0,
                KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE);

        ic.sendKeyEvent(keyEvent);
        Mockito.verify(spy, Mockito.timeout(AsyncUtils.DEFAULT_TIMEOUT_MS))
                .sendKeyEvent(keyEvent);
    }

    @Test
    @FlakyTest
    public void testPerformEditorAction() {
        final InputMethod.AccessibilityInputConnection ic = getInputConnection();
        final InputConnection spy = resetAndGetLastInputConnectionSpy();

        ic.performEditorAction(EditorInfo.IME_ACTION_PREVIOUS);
        Mockito.verify(spy, Mockito.timeout(AsyncUtils.DEFAULT_TIMEOUT_MS))
                .performEditorAction(EditorInfo.IME_ACTION_PREVIOUS);
    }

    @Test
    public void testPerformContextMenuAction() {
        final InputMethod.AccessibilityInputConnection ic = getInputConnection();
        final InputConnection spy = resetAndGetLastInputConnectionSpy();

        ic.performContextMenuAction(android.R.id.selectAll);
        Mockito.verify(spy, Mockito.timeout(AsyncUtils.DEFAULT_TIMEOUT_MS))
                .performContextMenuAction(android.R.id.selectAll);
    }

    @Test
    public void testGetCursorCapsMode() {
        final InputMethod.AccessibilityInputConnection ic = getInputConnection();
        final InputConnection spy = resetAndGetLastInputConnectionSpy();

        ic.getCursorCapsMode(InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        Mockito.verify(spy, Mockito.timeout(AsyncUtils.DEFAULT_TIMEOUT_MS))
                .getCursorCapsMode(InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
    }

    @Test
    public void testClearMetaKeyStates() {
        final InputMethod.AccessibilityInputConnection ic = getInputConnection();
        final InputConnection spy = resetAndGetLastInputConnectionSpy();

        ic.clearMetaKeyStates(KeyEvent.META_SHIFT_ON);
        Mockito.verify(spy, Mockito.timeout(AsyncUtils.DEFAULT_TIMEOUT_MS))
                .clearMetaKeyStates(KeyEvent.META_SHIFT_ON);
    }
}
