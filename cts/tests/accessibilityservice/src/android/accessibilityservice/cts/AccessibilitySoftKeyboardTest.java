/**
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.accessibilityservice.cts;

import static android.accessibility.cts.common.InstrumentedAccessibilityService.enableService;
import static android.accessibilityservice.AccessibilityService.SHOW_MODE_AUTO;
import static android.accessibilityservice.AccessibilityService.SHOW_MODE_HIDDEN;
import static android.accessibilityservice.AccessibilityService.SHOW_MODE_IGNORE_HARD_KEYBOARD;
import static android.accessibilityservice.AccessibilityService.SoftKeyboardController.ENABLE_IME_SUCCESS;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.accessibility.cts.common.AccessibilityDumpOnFailureRule;
import android.accessibility.cts.common.InstrumentedAccessibilityService;
import android.accessibility.cts.common.InstrumentedAccessibilityServiceTestRule;
import android.accessibility.cts.common.ShellCommandBuilder;
import android.accessibilityservice.AccessibilityService.SoftKeyboardController;
import android.accessibilityservice.AccessibilityService.SoftKeyboardController.OnShowModeChangedListener;
import android.accessibilityservice.cts.activities.AccessibilityTestActivity;
import android.accessibilityservice.cts.utils.AsyncUtils;
import android.app.Instrumentation;
import android.inputmethodservice.cts.common.Ime1Constants;
import android.inputmethodservice.cts.common.test.ShellCommandUtils;
import android.os.Bundle;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.Presubmit;
import android.provider.Settings;
import android.view.inputmethod.InputMethodManager;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.CddTest;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Test cases for {@code SoftKeyboardController}. It tests the accessibility APIs for interacting
 * with the soft keyboard show mode.
 */
@RunWith(AndroidJUnit4.class)
@AppModeFull
@CddTest(requirements = {"3.10/C-1-1,C-1-2"})
@Presubmit
public class AccessibilitySoftKeyboardTest {
    private Instrumentation mInstrumentation;
    private int mLastCallbackValue;

    private InstrumentedAccessibilityService mService;
    private final Object mLock = new Object();
    private final OnShowModeChangedListener mListener = (c, showMode) -> {
        synchronized (mLock) {
            mLastCallbackValue = showMode;
            mLock.notifyAll();
        }
    };

    private InstrumentedAccessibilityServiceTestRule<InstrumentedAccessibilityService>
            mServiceRule = new InstrumentedAccessibilityServiceTestRule<>(
                    InstrumentedAccessibilityService.class);

    private AccessibilityDumpOnFailureRule mDumpOnFailureRule =
            new AccessibilityDumpOnFailureRule();

    @Rule
    public final RuleChain mRuleChain = RuleChain
            .outerRule(mServiceRule)
            .around(mDumpOnFailureRule);

    @Before
    public void setUp() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mService = mServiceRule.getService();
    }

    @Test
    public void testApiReturnValues_shouldChangeValueOnRequestAndSendCallback() throws Exception {
        final SoftKeyboardController controller = mService.getSoftKeyboardController();

        // Confirm that we start in the default state
        assertEquals(SHOW_MODE_AUTO, controller.getShowMode());

        controller.addOnShowModeChangedListener(mListener);
        assertCanSetAndGetShowModeAndCallbackHappens(SHOW_MODE_HIDDEN, mService);
        assertCanSetAndGetShowModeAndCallbackHappens(SHOW_MODE_IGNORE_HARD_KEYBOARD, mService);
        assertCanSetAndGetShowModeAndCallbackHappens(SHOW_MODE_AUTO, mService);

        // Make sure we can remove our listener.
        assertTrue(controller.removeOnShowModeChangedListener(mListener));
    }

    @Test
    public void secondServiceChangingTheShowMode_updatesModeAndNotifiesFirstService()
            throws Exception {

        final SoftKeyboardController controller = mService.getSoftKeyboardController();
        // Confirm that we start in the default state
        assertEquals(SHOW_MODE_AUTO, controller.getShowMode());

        final InstrumentedAccessibilityService secondService =
                enableService(StubAccessibilityButtonService.class);
        try {
            // Listen on the first service
            controller.addOnShowModeChangedListener(mListener);
            assertCanSetAndGetShowModeAndCallbackHappens(SHOW_MODE_HIDDEN, mService);

            // Change the mode on the second service
            assertCanSetAndGetShowModeAndCallbackHappens(SHOW_MODE_IGNORE_HARD_KEYBOARD,
                    secondService);
        } finally {
            secondService.runOnServiceSync(() -> secondService.disableSelf());
        }

        // Shutting down the second service, which was controlling the mode, should put us back
        // to the default
        waitForCallbackValueWithLock(SHOW_MODE_AUTO);
        final int showMode = mService.getOnService(() -> controller.getShowMode());
        assertEquals(SHOW_MODE_AUTO, showMode);
    }

    @Test
    public void testSwitchToInputMethod() throws Exception {
        final SoftKeyboardController controller = mService.getSoftKeyboardController();
        String currentIME = Settings.Secure.getString(
                mService.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        assertNotEquals(Ime1Constants.IME_ID, currentIME);
        // Enable a placeholder IME for this test.
        try (TestImeSession imeSession = new TestImeSession(Ime1Constants.IME_ID, true)) {
            // Switch to the placeholder IME.
            final boolean success = controller.switchToInputMethod(Ime1Constants.IME_ID);
            currentIME = Settings.Secure.getString(
                    mService.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);

            // The current IME should be set to the placeholder IME successfully.
            assertTrue(success);
            assertEquals(Ime1Constants.IME_ID, currentIME);
        }
    }

    @Test
    public void testSetInputMethodEnabled_differentPackage() throws Exception {
        // Disable a placeholder IME for this test.
        try (TestImeSession imeSession = new TestImeSession(Ime1Constants.IME_ID, false)) {
            final SoftKeyboardController controller = mService.getSoftKeyboardController();

            List<String> enabledIMEs = getEnabledInputMethods();
            assertFalse(enabledIMEs.contains(Ime1Constants.IME_ID));

            // Enable the placeholder IME.
            try {
                int result = controller.setInputMethodEnabled(Ime1Constants.IME_ID, true);
                fail("should have thrown SecurityException");
            } catch (SecurityException ignored) {
            }

            enabledIMEs = getEnabledInputMethods();
            // The placeholder IME should not be enabled;
            assertFalse(enabledIMEs.contains(Ime1Constants.IME_ID));
        }
    }

    @Test
    public void testSetInputMethodEnabled_success() throws Exception {
        String ImeId = "android.accessibilityservice.cts/.StubInputMethod";
        // Disable a placeholder IME for this test.
        try (TestImeSession imeSession = new TestImeSession(ImeId, false)) {
            final SoftKeyboardController controller = mService.getSoftKeyboardController();

            List<String> enabledIMEs = getEnabledInputMethods();
            assertFalse(enabledIMEs.contains(ImeId));

            // Enable the placeholder IME.
            int result = controller.setInputMethodEnabled(ImeId, true);
            enabledIMEs = getEnabledInputMethods();

            // The placeholder IME should be enabled;
            assertEquals(ENABLE_IME_SUCCESS, result);
            assertTrue(enabledIMEs.contains(ImeId));

            // Disable the placeholder IME.
            result = controller.setInputMethodEnabled(ImeId, false);
            enabledIMEs = getEnabledInputMethods();

            // The placeholder IME should be disabled;
            assertEquals(ENABLE_IME_SUCCESS, result);
            assertFalse(enabledIMEs.contains(ImeId));
        }
    }

    private void assertCanSetAndGetShowModeAndCallbackHappens(
            int mode, InstrumentedAccessibilityService service)
            throws Exception  {
        final SoftKeyboardController controller = service.getSoftKeyboardController();
        mLastCallbackValue = -1;
        final boolean setShowModeReturns =
                service.getOnService(() -> controller.setShowMode(mode));
        assertTrue(setShowModeReturns);
        waitForCallbackValueWithLock(mode);
        assertEquals(mode, controller.getShowMode());
    }

    private void waitForCallbackValueWithLock(int expectedValue) throws Exception {
        long timeoutTimeMillis = SystemClock.uptimeMillis() + AsyncUtils.DEFAULT_TIMEOUT_MS;

        while (SystemClock.uptimeMillis() < timeoutTimeMillis) {
            synchronized(mLock) {
                if (mLastCallbackValue == expectedValue) {
                    return;
                }
                try {
                    mLock.wait(timeoutTimeMillis - SystemClock.uptimeMillis());
                } catch (InterruptedException e) {
                    // Wait until timeout.
                }
            }
        }

        throw new IllegalStateException("last callback value <" + mLastCallbackValue
                + "> does not match expected value < " + expectedValue + ">");
    }

    private List<String> getEnabledInputMethods() {
        final InputMethodManager inputMethodManager = mInstrumentation.getTargetContext()
                .getSystemService(InputMethodManager.class);
        return inputMethodManager.getEnabledInputMethodList()
                .stream().map(inputMethodInfo -> inputMethodInfo.getId())
                .collect(Collectors.toList());
    }

    /**
     * Activity for testing the AccessibilityService API for hiding and showing the soft keyboard.
     */
    public static class SoftKeyboardModesActivity extends AccessibilityTestActivity {
        public SoftKeyboardModesActivity() {
            super();
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.accessibility_soft_keyboard_modes_test);
        }
    }

    private class TestImeSession implements AutoCloseable {
        TestImeSession(String imeId, boolean enabled) {
            // Enable/disable the placeholder IME by shell command.
            final String enableImeCommand;
            if (enabled) {
                enableImeCommand = ShellCommandUtils.enableIme(imeId);
            } else {
                enableImeCommand = ShellCommandUtils.disableIme(imeId);
            }
            ShellCommandBuilder.create(mInstrumentation)
                    .addCommand(enableImeCommand)
                    .run();
        }

        @Override
        public void close() throws Exception {
            // Reset IMEs by shell command.
            ShellCommandBuilder.create(mInstrumentation)
                    .addCommand(ShellCommandUtils.resetImes())
                    .run();
        }
    }
}
