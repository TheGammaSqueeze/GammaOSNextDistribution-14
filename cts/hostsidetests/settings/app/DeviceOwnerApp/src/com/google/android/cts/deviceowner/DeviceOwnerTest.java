/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.google.android.cts.deviceowner;

import static android.server.wm.WindowManagerState.STATE_RESUMED;

import static com.google.common.truth.Truth.assertWithMessage;

import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.RemoteException;
import android.os.UserHandle;
import android.provider.Settings;
import android.server.wm.WindowManagerStateHelper;
import android.test.InstrumentationTestCase;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiScrollable;
import androidx.test.uiautomator.UiSelector;
import androidx.test.uiautomator.Until;

import com.android.bedstead.dpmwrapper.DeviceOwnerHelper;
import com.android.bedstead.dpmwrapper.TestAppSystemServiceFactory;
import com.android.compatibility.common.util.enterprise.DeviceAdminReceiverUtils;

/**
 * Class for device-owner based tests.
 *
 * <p>This class handles making sure that the test is the device owner and that it has an active
 * admin registered if necessary. The admin component can be accessed through {@link #getWho()}.
 */
public final class DeviceOwnerTest extends InstrumentationTestCase {

    private static final String TAG = DeviceOwnerTest.class.getSimpleName();

    public static final int TIMEOUT_MS = 2_000;

    public static final double DEADZONE_PCT = 0.2;

    protected Context mContext;
    protected UiDevice mDevice;

    /** Device Admin receiver for DO. */
    public static final class BasicAdminReceiver extends DeviceAdminReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            // Ignore intents used by DpmWrapper IPC between current and system users
            if (DeviceOwnerHelper.runManagerMethod(this, context, intent)) return;

            // Hack used to manually disable the admin during development
            if (DeviceAdminReceiverUtils.disableSelf(context, intent)) return;

            super.onReceive(context, intent);
        }
    }

    static final String CAR_SETTING_FRAG_RESOURCE_ID_REGEX = ".*:id/fragment_container_wrapper";
    static final String PACKAGE_NAME = DeviceOwnerTest.class.getPackage().getName();
    static final ComponentName RECEIVER_COMPONENT =
            new ComponentName(PACKAGE_NAME, BasicAdminReceiver.class.getName());

    protected DevicePolicyManager mDevicePolicyManager;
    protected PackageManager mPackageManager;
    protected boolean mIsDeviceOwner;
    private String mWorkPolicyInfoText;
    private boolean mIsAutomotive;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mContext = getInstrumentation().getContext();
        mDevice = UiDevice.getInstance(getInstrumentation());
        mPackageManager = mContext.getPackageManager();
        mDevicePolicyManager = TestAppSystemServiceFactory.getDevicePolicyManager(mContext,
                BasicAdminReceiver.class, /* forDeviceOwner= */ true);

        mIsAutomotive = mPackageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE);

        mWorkPolicyInfoText = mIsAutomotive
                ? "Privacy Settings for Device Owner CTS host side app vehicle policy"
                : "Your work policy info";

        mIsDeviceOwner = mDevicePolicyManager.isDeviceOwnerApp(PACKAGE_NAME);
        Log.d(TAG, "setup(): dpm=" + mDevicePolicyManager + ", isDO: " + mIsDeviceOwner);

        if (mIsDeviceOwner) {
            assertWithMessage("isAdminActive(%s)", RECEIVER_COMPONENT)
                    .that(mDevicePolicyManager.isAdminActive(RECEIVER_COMPONENT)).isTrue();

            // Note DPM.getDeviceOwner() now always returns null on non-DO users as of NYC.
            assertWithMessage("%s.getDeviceOwner()", mDevicePolicyManager)
                    .that(mDevicePolicyManager.getDeviceOwner()).isEqualTo(PACKAGE_NAME);
        }

        try {
            mDevice.setOrientationNatural();
        } catch (RemoteException e) {
            throw new RuntimeException("failed to freeze device orientation", e);
        }
        wakeupDeviceAndPressHome();
    }

    private void wakeupDeviceAndPressHome() throws Exception {
        mDevice.wakeUp();
        mDevice.pressMenu();
        mDevice.pressHome();
    }

    @Override
    protected void tearDown() throws Exception {
        mDevice.pressBack();
        mDevice.pressHome();
        mDevice.waitForIdle(TIMEOUT_MS); // give UI time to finish animating
    }

    private boolean launchPrivacyAndCheckWorkPolicyInfo() throws Exception {
        // Launch Settings
        launchSettingsPage(InstrumentationRegistry.getContext(), Settings.ACTION_PRIVACY_SETTINGS);

        // Wait for loading permission usage data.
        mDevice.waitForIdle(TIMEOUT_MS);

        Log.d(TAG, "Waiting " + TIMEOUT_MS + "ms for the '" + mWorkPolicyInfoText + "' message");

        boolean found = null != mDevice.wait(Until.findObject(By.text(mWorkPolicyInfoText)),
                TIMEOUT_MS);

        // For automotive and Safety Center UI, try to scroll the privacy list to find the item
        if (!found) {
            UiScrollable scroller;
            if (mIsAutomotive) {
                scroller = new UiScrollable(new UiSelector()
                        .resourceIdMatches(CAR_SETTING_FRAG_RESOURCE_ID_REGEX));
            } else {
                scroller = new UiScrollable(new UiSelector().scrollable(true));
            }
            try {
                // Swipe far away from the edges to avoid triggering navigation gestures
                scroller.setSwipeDeadZonePercentage(DEADZONE_PCT);
                found = scroller.scrollTextIntoView(mWorkPolicyInfoText);
            } catch (UiObjectNotFoundException e) { }
        }

        Log.d(TAG, "Message found: " + found);
        return found;
    }

    private void launchSettingsPage(Context ctx, String pageName) throws Exception {
        Intent intent = new Intent(pageName);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        Log.d(TAG, "Launching settings page on user " + UserHandle.myUserId() + " using " + intent);
        ctx.startActivity(intent);

        ComponentName componentName =
                ctx.getPackageManager()
                        .resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                        .getComponentInfo()
                        .getComponentName();

        Log.d(TAG, "Waiting for STATE_RESUMED on " + componentName);

        new WindowManagerStateHelper().waitForActivityState(componentName, STATE_RESUMED);
    }

    private void disableWorkPolicyInfoActivity() {
        mContext.getPackageManager()
                .setComponentEnabledSetting(
                        new ComponentName(mContext, WorkPolicyInfoActivity.class),
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP);
    }

    private void launchPrivacySettingsAndAssertWorkPolicyInfoIsShowing() throws Exception {
        assertWithMessage("Work policy info (%s) on settings entry", mWorkPolicyInfoText)
                .that(launchPrivacyAndCheckWorkPolicyInfo()).isTrue();
    }

    private void launchPrivacySettingsAndAssertWorkPolicyInfoIsNotShowing() throws Exception {
        assertWithMessage("Work policy info (%s) on settings entry", mWorkPolicyInfoText)
                .that(launchPrivacyAndCheckWorkPolicyInfo()).isFalse();
    }

    /**
     * If the app is the active device owner and has work policy info, then we should have a Privacy
     * entry for it.
     */
    public void testDeviceOwnerWithInfo() throws Exception {
        assertWithMessage("is device owner").that(mIsDeviceOwner).isTrue();

        launchPrivacySettingsAndAssertWorkPolicyInfoIsShowing();
    }

    /**
     * If the app is the active device owner, but doesn't have work policy info, then we shouldn't
     * have a Privacy entry for it.
     */
    public void testDeviceOwnerWithoutInfo() throws Exception {
        assertWithMessage("is device owner").that(mIsDeviceOwner).isTrue();

        disableWorkPolicyInfoActivity();

        launchPrivacySettingsAndAssertWorkPolicyInfoIsNotShowing();
    }

    /**
     * If the app is NOT the active device owner, then we should not have a Privacy entry for work
     * policy info.
     */
    public void testNonDeviceOwnerWithInfo() throws Exception {
        assertWithMessage("is device owner").that(mIsDeviceOwner).isFalse();

        launchPrivacySettingsAndAssertWorkPolicyInfoIsNotShowing();
    }

    /**
     * If the app is NOT the active device owner, and doesn't have work policy info, then we should
     * not have a Privacy entry for work policy info.
     */
    public void testNonDeviceOwnerWithoutInfo() throws Exception {
        assertWithMessage("is device owner").that(mIsDeviceOwner).isFalse();

        disableWorkPolicyInfoActivity();

        launchPrivacySettingsAndAssertWorkPolicyInfoIsNotShowing();
    }
}
