/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.cts.packageinstaller;

import android.app.UiAutomation;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import java.util.regex.Pattern;

/**
 * This class tests manual package install and uninstall by a device owner.
 */
public final class ManualPackageInstallTest extends BasePackageInstallTest {

    private static final String TAG = ManualPackageInstallTest.class.getSimpleName();

    private static final int AUTOMATOR_WAIT_TIMEOUT = 5000;
    private static final int INSTALL_WAIT_TIME = 5000;

    private static final BySelector INSTALL_BUTTON_SELECTOR = By.text(Pattern.compile("Install",
            Pattern.CASE_INSENSITIVE));

    private UiAutomation mUiAutomation;
    private boolean mIsAutomotive;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mUiAutomation = getInstrumentation().getUiAutomation();
        mIsAutomotive = mPackageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE);
    }

    public void testManualInstallSucceeded() throws Exception {
        assertInstallPackage();
    }

    public void testManualInstallBlocked() throws Exception {
        synchronized (mPackageInstallerTimeoutLock) {
            mCallbackReceived = false;
            mCallbackStatus = PACKAGE_INSTALLER_STATUS_UNDEFINED;
        }
        // Calls the original installPackage which does not click through the install button.
        Log.d(TAG, "Installing " + TEST_APP_LOCATION);
        super.installPackage(TEST_APP_LOCATION);
        synchronized (mPackageInstallerTimeoutLock) {
            try {
                mPackageInstallerTimeoutLock.wait(PACKAGE_INSTALLER_TIMEOUT_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Log.e(TAG, "Interrupted", e);
            }
            assertTrue(mCallbackReceived);
            assertEquals(PackageInstaller.STATUS_PENDING_USER_ACTION, mCallbackStatus);
        }

        mCallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Log.d(TAG, "Starting " + mCallbackIntent + " on user " + UserHandle.myUserId());
        mContext.startActivity(mCallbackIntent);

        automateDismissInstallBlockedDialog();

        // Assuming installation is not synchronous, we should wait a while before checking.
        Thread.sleep(INSTALL_WAIT_TIME);
        assertFalse(isPackageInstalled(TEST_APP_PKG));
    }

    @Override
    protected void installPackage(String packageLocation) throws Exception {
        super.installPackage(packageLocation);

        synchronized (mPackageInstallerTimeoutLock) {
            try {
                mPackageInstallerTimeoutLock.wait(PACKAGE_INSTALLER_TIMEOUT_MS);
            } catch (InterruptedException e) {
            }
            assertTrue(mCallbackReceived);
            assertEquals(PackageInstaller.STATUS_PENDING_USER_ACTION, mCallbackStatus);
        }

        // Use a receiver to listen for package install.
        synchronized (mPackageInstallerTimeoutLock) {
            mCallbackReceived = false;
            mCallbackStatus = PACKAGE_INSTALLER_STATUS_UNDEFINED;
        }

        mCallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(mCallbackIntent);

        automateInstallClick();
    }

    private void automateInstallClick() {
        mDevice.wait(Until.hasObject(INSTALL_BUTTON_SELECTOR), AUTOMATOR_WAIT_TIMEOUT);
        UiObject2 button = mDevice.findObject(INSTALL_BUTTON_SELECTOR);
        assertNotNull("Install button not found", button);
        button.click();
    }

    private void automateDismissInstallBlockedDialog() {
        BySelector selector = getPopUpImageSelector();
        mDevice.wait(Until.hasObject(selector), AUTOMATOR_WAIT_TIMEOUT);
        UiObject2 icon = mDevice.findObject(selector);
        assertNotNull("Policy transparency dialog icon not found: " + selector, icon);
        // "OK" button only present in the dialog if it is blocked by policy.
        UiObject2 button = mDevice.findObject(getPopUpButtonSelector());
        assertNotNull("OK button not found", button);
        button.click();
    }

    private String getSettingsPackageName() {
        String settingsPackageName = mIsAutomotive
                ? "com.android.car.settings"
                : "com.android.settings";
        mUiAutomation.adoptShellPermissionIdentity("android.permission.INTERACT_ACROSS_USERS");
        try {
            ResolveInfo resolveInfo = mPackageManager.resolveActivityAsUser(
                    new Intent(Settings.ACTION_SETTINGS), PackageManager.MATCH_SYSTEM_ONLY,
                    UserHandle.USER_SYSTEM);
            if (resolveInfo != null && resolveInfo.activityInfo != null) {
                settingsPackageName = resolveInfo.activityInfo.packageName;
            }
        } finally {
            mUiAutomation.dropShellPermissionIdentity();
        }
        Log.d(TAG, "getSettingsPackageName(): returning " + settingsPackageName);
        return settingsPackageName;
    }

    private BySelector getPopUpButtonSelector() {
        return By.clazz(android.widget.Button.class.getName())
                .res("android:id/button1")
                .pkg(getSettingsPackageName());
    }

    private BySelector getPopUpImageSelector() {
        final String settingsPackageName = getSettingsPackageName();
        final String resId = mIsAutomotive ? "car_ui_alert_icon" : "admin_support_icon";
        return By.clazz(android.widget.ImageView.class.getName())
                .res(settingsPackageName + ":id/" + resId)
                .pkg(settingsPackageName);
    }
}
