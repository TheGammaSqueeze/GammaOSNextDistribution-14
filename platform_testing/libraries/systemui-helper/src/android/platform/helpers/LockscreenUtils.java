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

package android.platform.helpers;

import static android.content.Context.KEYGUARD_SERVICE;
import static android.os.SystemClock.sleep;
import static android.platform.helpers.CommonUtils.executeShellCommand;
import static android.platform.helpers.Constants.SHORT_WAIT_TIME_IN_SECONDS;
import static android.platform.helpers.ui.UiAutomatorUtils.getUiDevice;
import static android.platform.uiautomator_helpers.DeviceHelpers.getContext;
import static android.platform.uiautomator_helpers.WaitUtils.ensureThat;
import static android.view.KeyEvent.KEYCODE_ENTER;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;

import static java.lang.String.format;
import static java.lang.System.currentTimeMillis;
import static java.util.concurrent.TimeUnit.SECONDS;

import android.app.KeyguardManager;
import android.content.ContentResolver;
import android.os.RemoteException;
import android.platform.helpers.features.common.HomeLockscreenPage;
import android.platform.test.util.HealthTestingUtils;
import android.provider.Settings;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

/**
 * All required util for Lockscreen.
 * @deprecated use classes from the "systemui-tapl" library instead
 */
@Deprecated
public class LockscreenUtils {
    private static final String TAG = "LockscreenUtils";
    private static final String RESET_LOCKSCREEN_SHELL_COMMAND = "locksettings clear --old";
    private static final String INPUT_KEYEVENT_COMMAND = "input keyevent";
    private static final String INPUT_TEXT_COMMAND = "input keyboard text";
    private static final String SET_PASSWORD_COMMAND = "locksettings set-password";
    private static final String SET_PIN_COMMAND = "locksettings set-pin";
    private static final String SET_PATTERN_COMMAND = "locksettings set-pattern";
    private static final String SET_SWIPE_COMMAND = "locksettings set-disabled false";
    private static final String SET_LOCK_AS_NONE_COMMAND = "locksettings set-disabled true";
    private static final int MAX_LOCKSCREEN_TIMEOUT_IN_SEC = 10;

    public static int sPreviousAodSetting;

    private LockscreenUtils() {
    }

    /**
     * To get an instance of class that can be used to lock and unlock the keygaurd.
     *
     * @return an instance of class that can be used to lock and unlock the screen.
     */
    public static final KeyguardManager getKeyguardManager() {
        return (KeyguardManager) getContext().getSystemService(KEYGUARD_SERVICE);
    }

    /**
     * Different way to set the Lockscreen for Android device. Currently we only support PIN,
     * PATTERN and PASSWORD
     *
     * @param lockscreenType it enum with list of supported lockscreen type
     * @param lockscreenCode code[PIN or PATTERN or PASSWORD] which needs to be set.
     * @param expectedResult expected result after setting the lockscreen because for lock type
     *                       Swipe and None Keygaurd#isKeyguardSecure remain unlocked i.e. false.
     */
    public static void setLockscreen(LockscreenType lockscreenType, String lockscreenCode,
            boolean expectedResult) {
        Log.d(TAG, format("Setting Lockscreen [%s(%s)]", lockscreenType, lockscreenCode));
        switch (lockscreenType) {
            case PIN:
                executeShellCommand(format("%s %s", SET_PIN_COMMAND, lockscreenCode));
                break;
            case PASSWORD:
                executeShellCommand(format("%s %s", SET_PASSWORD_COMMAND, lockscreenCode));
                break;
            case PATTERN:
                executeShellCommand(format("%s %s", SET_PATTERN_COMMAND, lockscreenCode));
                break;
            case SWIPE:
                executeShellCommand(SET_SWIPE_COMMAND);
                break;
            case NONE:
                executeShellCommand(SET_LOCK_AS_NONE_COMMAND);
                break;
            default:
                throw new AssertionError("Non-supported Lockscreen Type: " + lockscreenType);
        }
        assertKeyguardSecure(expectedResult);
    }

    private static void assertKeyguardSecure(boolean expectedSecure) {
        HealthTestingUtils.waitForCondition(
                () -> String.format("Assert that keyguard %s secure, but failed.",
                        expectedSecure ? "is" : "isn't"),
                () -> getKeyguardManager().isKeyguardSecure() == expectedSecure);
    }

    /**
     * Resets the give lockscreen.
     *
     * @param lockscreenCode old code which is currently set.
     */
    public static void resetLockscreen(String lockscreenCode) {
        Log.d(TAG, String.format("Re-Setting Lockscreen %s", lockscreenCode));
        executeShellCommand(
                format("%s %s", RESET_LOCKSCREEN_SHELL_COMMAND, lockscreenCode));
        assertKeyguardSecure(/* expectedSecure= */ false);
    }

    /**
     * Entering the given code on the lockscreen
     *
     * @param lockscreenType type of lockscreen set.
     * @param lockscreenCode valid lockscreen code.
     */
    public static void enterCodeOnLockscreen(LockscreenType lockscreenType,
            String lockscreenCode) {
        Log.d(TAG,
                format("Entering Lockscreen code: %s(%s)", lockscreenType, lockscreenCode));
        assertEquals("Lockscreen was not set", true,
                getKeyguardManager().isKeyguardSecure());
        switch (lockscreenType) {
            case PIN:
            case PASSWORD:
                // Entering the lockscreen code in text box.
                executeShellCommand(format("%s %s", INPUT_TEXT_COMMAND, lockscreenCode));
                // Pressing the ENTER button after entering the code.
                executeShellCommand(format("%s %s", INPUT_KEYEVENT_COMMAND, KEYCODE_ENTER));
                break;
            default:
                throw new AssertionError("Non-supported Lockscreen Type: " + lockscreenType);
        }
    }

    /**
     * Check if the device is locked as per the user expectation.
     *
     * @param expectedLockStatus expected device lock status.
     */
    public static void checkDeviceLock(boolean expectedLockStatus) {
        Log.d(TAG, format("Checking device lock status: %s", expectedLockStatus));
        long endTime = currentTimeMillis() + SECONDS.toMillis(MAX_LOCKSCREEN_TIMEOUT_IN_SEC);
        while (currentTimeMillis() <= endTime) {
            if (getKeyguardManager().isDeviceLocked() == expectedLockStatus) {
                break;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        assertThat(getKeyguardManager().isDeviceLocked()).isEqualTo(expectedLockStatus);
    }

    /**
     * Goes to the Locked screen page
     *
     * @deprecated use Root.goToLockscreen() to improve validation b/322870306
     */
    public static void goToLockScreen() {
        try {
            getUiDevice().sleep();
            sleep(SHORT_WAIT_TIME_IN_SECONDS * 1000);
            getUiDevice().wakeUp();
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /** Ensures that the lockscreen is visible. */
    public static void ensureLockscreen() {
        HomeLockscreenPage page = new HomeLockscreenPage();
        HealthTestingUtils.waitForCondition(() -> "Lock screen is not visible", page::isVisible);
    }

    /** Ensures that the lockscreen is not visible. */
    public static void ensureNoLockscreen() {
        HomeLockscreenPage page = new HomeLockscreenPage();
        ensureThat("Lock screen is invisible", () -> !page.isVisible());
    }

    /**
     * Dismisses the lock screen, by swiping up, if it's visible.
     * The device shouldn't have a password set.
     */
    public static void dismissLockScreen() {
        checkDeviceLock(false /* expectedLockStatus */);

        HomeLockscreenPage page = new HomeLockscreenPage();
        if (page.isVisible()) {
            page.swipeUp();
        }
    }

    public static void ensureAoD(boolean enabled) {
        final ContentResolver contentResolver =
                InstrumentationRegistry.getInstrumentation().getContext().getContentResolver();
        sPreviousAodSetting = Settings.Secure.getInt(
                contentResolver, Settings.Secure.DOZE_ALWAYS_ON, 0);
        final boolean isAodEnabled = sPreviousAodSetting != 0;
        if (isAodEnabled != enabled) {
            Settings.Secure.putInt(
                    contentResolver, Settings.Secure.DOZE_ALWAYS_ON, enabled ? 1 : 0);
        }
    }

    public static void recoverAoD() {
        final ContentResolver contentResolver =
                InstrumentationRegistry.getInstrumentation().getContext().getContentResolver();
        Settings.Secure.putInt(
                contentResolver, Settings.Secure.DOZE_ALWAYS_ON, sPreviousAodSetting);
    }

    /**
     * Enum for different types of Lockscreen, PIN, PATTERN and PASSWORD.
     */
    public enum LockscreenType {
        PIN,
        PASSWORD,
        PATTERN,
        SWIPE,
        NONE
    }
}
