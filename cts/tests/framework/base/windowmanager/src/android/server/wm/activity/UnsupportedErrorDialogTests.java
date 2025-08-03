/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.server.wm.activity;

import static android.server.wm.ShellCommandHelper.executeShellCommand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import android.app.ActivityTaskManager;
import android.content.ComponentName;
import android.os.SystemClock;
import android.platform.test.annotations.Postsubmit;
import android.provider.Settings;
import android.server.wm.ActivityManagerTestBase;
import android.server.wm.annotation.Group3;
import android.server.wm.app.Components;
import android.server.wm.settings.SettingsSession;
import android.view.KeyEvent;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

/**
 * Test scenarios where crash dialogs should not be shown if they are not supported.
 *
 * <p>Build/Install/Run:
 * atest CtsWindowManagerDeviceOther:UnsupportedErrorDialogTests
 */
@Group3
@Postsubmit
public class UnsupportedErrorDialogTests extends ActivityManagerTestBase {
    private final UiDevice mUiDevice = UiDevice.getInstance(mInstrumentation);

    @Override
    @Before
    public void setUp() throws Exception {
        // These tests are for cases when error dialogs are not supported
        Assume.assumeFalse(ActivityTaskManager.currentUiModeSupportsErrorDialogs(mContext));
        super.setUp();
        resetAppErrors();
    }

    /** Make sure the developer option applies correctly leading to the dialog being shown. */
    @Test
    public void testDevSettingOverride() {
        try (SettingsSession<Integer> devDialogShow =
                     secureIntSession(Settings.Secure.ANR_SHOW_BACKGROUND);
             SettingsSession<Integer> showOnFirstCrash =
                     globalIntSession(Settings.Global.SHOW_FIRST_CRASH_DIALOG);
             SettingsSession<Integer> showOnFirstCrashDev =
                     secureIntSession(Settings.Secure.SHOW_FIRST_CRASH_DIALOG_DEV_OPTION)) {
            // set developer setting to show dialogs anyway
            devDialogShow.set(1);
            // enable only the dev option for showing the crash dialog after the first crash
            showOnFirstCrash.set(0);
            showOnFirstCrashDev.set(1);
            launchActivityNoWait(Components.CRASHING_ACTIVITY);
            findCrashDialogAndCloseApp();
            ensureActivityNotFocused(Components.CRASHING_ACTIVITY);
        }
    }

    /**
     * Make sure the dialog appears if the dev option is set even if the user specifically
     * set to suppress it.
     */
    @Test
    public void testDevSettingPrecedence() {
        try (SettingsSession<Integer> devDialogShow =
                     secureIntSession(Settings.Secure.ANR_SHOW_BACKGROUND);
             SettingsSession<Integer> userDialogHide =
                     globalIntSession(Settings.Global.HIDE_ERROR_DIALOGS);
             SettingsSession<Integer> showOnFirstCrash =
                     globalIntSession(Settings.Global.SHOW_FIRST_CRASH_DIALOG)
        ) {
            devDialogShow.set(1);
            showOnFirstCrash.set(1);
            userDialogHide.set(1);

            launchActivityNoWait(Components.CRASHING_ACTIVITY);
            findCrashDialogAndCloseApp();
            ensureActivityNotFocused(Components.CRASHING_ACTIVITY);
        }
    }

    /** Make sure the AppError dialog is not shown even if would have after the initial crash. */
    @Test
    public void testFirstCrashDialogNotShown() {
        try (SettingsSession<Integer> devDialogShow =
                     secureIntSession(Settings.Secure.ANR_SHOW_BACKGROUND);
             SettingsSession<Integer> showOnFirstCrash =
                     globalIntSession(Settings.Global.SHOW_FIRST_CRASH_DIALOG)) {
            devDialogShow.set(0);
            // enable showing crash dialog after first crash
            showOnFirstCrash.set(1);

            launchActivityNoWait(Components.CRASHING_ACTIVITY);

            ensureNoCrashDialog(Components.CRASHING_ACTIVITY);
            ensureActivityNotFocused(Components.CRASHING_ACTIVITY);
        }
    }

    /** Ensure the AppError dialog is not shown even after multiple crashes. */
    @Test
    public void testRepeatedCrashDialogNotShown() {
        try (SettingsSession<Integer> devDialogShow =
                     secureIntSession(Settings.Secure.ANR_SHOW_BACKGROUND);
             SettingsSession<Integer> showOnFirstCrash =
                     globalIntSession("show_first_crash_dialog");
             SettingsSession<Integer> showOnFirstCrashDev =
                     secureIntSession("show_first_crash_dialog_dev_option")) {
            // disable all overrides
            devDialogShow.set(0);
            showOnFirstCrash.set(0);
            showOnFirstCrashDev.set(0);

            // repeatedly crash the app without resetting AppErrors
            for (int i = 0; i < 5; i++) {
                launchActivityNoWait(Components.CRASHING_ACTIVITY);
                ensureNoCrashDialog(Components.CRASHING_ACTIVITY);
            }
            ensureActivityNotFocused(Components.CRASHING_ACTIVITY);
        }
    }

    /** Ensure the ANR dialog is also not shown. */
    @Test
    public void testAnrIsNotShown() {
        // leave the settings at their defaults
        // launch non responsive app
        executeShellCommand(getAmStartCmd(Components.UNRESPONSIVE_ACTIVITY) + " --ei "
                + Components.UnresponsiveActivity.EXTRA_ON_CREATE_DELAY_MS + " 30000");
        // wait for app to be focused
        mWmState.waitAndAssertAppFocus(Components.UNRESPONSIVE_ACTIVITY.getPackageName(),
                2_000 /* waitTime */);
        // queue up enough key events to trigger an ANR
        for (int i = 0; i < 20; i++) {
            injectKey(KeyEvent.KEYCODE_TAB, false /* longPress */, false /* sync */);
            SystemClock.sleep(500);
            mWmState.computeState();
            if (!mWmState.isActivityVisible(Components.UNRESPONSIVE_ACTIVITY)) {
                break;
            }
        }
        ensureNoCrashDialog(Components.UNRESPONSIVE_ACTIVITY);
        ensureActivityNotFocused(Components.UNRESPONSIVE_ACTIVITY);
    }

    private void findCrashDialogAndCloseApp() {
        UiObject2 closeAppButton = findCloseButton();
        assertNotNull("Could not find crash dialog!", closeAppButton);
        closeAppButton.click();
    }

    private void ensureNoCrashDialog(ComponentName activity) {
        UiObject2 closeButton = findCloseButton();
        if (closeButton != null) {
            closeButton.click();
            fail("An unexpected crash dialog appeared!");
        }
        final int numWindows = mWmState.getWindowsByPackageName(activity.getPackageName()).size();
        assertEquals(0, numWindows);
    }

    private void ensureActivityNotFocused(ComponentName activity) {
        mWmState.computeState();
        mWmState.assertNotFocusedActivity("The activity should not be focused", activity);
    }

    /** Attempt to find the close button of a crash or ANR dialog in at most 2 seconds. */
    private UiObject2 findCloseButton() {
        return mUiDevice.wait(
                Until.findObject(By.res("android:id/aerr_close")),
                2_000);
    }

    private void resetAppErrors() {
        SystemUtil.runWithShellPermissionIdentity(mAm::resetAppErrors,
                android.Manifest.permission.RESET_APP_ERRORS);
    }

    private SettingsSession<Integer> globalIntSession(String settingName) {
        return new SettingsSession<>(
                Settings.Global.getUriFor(settingName),
                Settings.Global::getInt, Settings.Global::putInt);
    }

    private SettingsSession<Integer> secureIntSession(String settingName) {
        return new SettingsSession<>(
                Settings.Secure.getUriFor(settingName),
                Settings.Secure::getInt, Settings.Secure::putInt);
    }
}
