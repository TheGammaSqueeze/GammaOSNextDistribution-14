/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.cts.deviceowner;

import static com.google.common.truth.Truth.assertWithMessage;

import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.app.ActivityManager;
import android.app.Instrumentation;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.UserHandle;
import android.os.UserManager;
import android.test.AndroidTestCase;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import com.android.bedstead.dpmwrapper.TestAppSystemServiceFactory;
import com.android.compatibility.common.util.WifiConfigCreator;

import java.util.List;

/**
 * Base class for device-owner based tests.
 *
 * This class handles making sure that the test is the device owner
 * and that it has an active admin registered, so that all tests may
 * assume these are done. The admin component can be accessed through
 * {@link #getWho()}.
 */
public abstract class BaseDeviceOwnerTest extends AndroidTestCase {

    private static final String TAG = BaseDeviceOwnerTest.class.getSimpleName();

    protected DevicePolicyManager mDevicePolicyManager;
    @Nullable protected WifiManager mWifiManager;
    protected WifiManager mCurrentUserWifiManager;
    @Nullable protected WifiConfigCreator mWifiConfigCreator;
    protected Instrumentation mInstrumentation;
    protected UiDevice mDevice;
    protected boolean mHasSecureLockScreen;
    protected boolean mHasTelephonyFeature;
    protected boolean mIsAutomotive;
    /** User running the test (obtained from {@code mContext}). */
    protected @UserIdInt int mUserId;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mUserId = mContext.getUserId();
        Log.v(TAG, getClass().getSimpleName() + ".setUp(): test=" + getClass() + ", userId="
                + mUserId);

        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mDevice = UiDevice.getInstance(mInstrumentation);
        mDevicePolicyManager = TestAppSystemServiceFactory.getDevicePolicyManager(mContext,
                BasicAdminReceiver.class, /* forDeviceOwner= */ true);
        mWifiManager = TestAppSystemServiceFactory.getWifiManager(mContext,
                BasicAdminReceiver.class);
        mCurrentUserWifiManager = mContext.getSystemService(WifiManager.class);
        mWifiConfigCreator = mWifiManager == null ? null : new WifiConfigCreator(mContext,
                mWifiManager);
        mHasSecureLockScreen = mContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_SECURE_LOCK_SCREEN);
        mHasTelephonyFeature = mContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_TELEPHONY);
        mIsAutomotive = mContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_AUTOMOTIVE);

        Log.v(TAG, "dpm=" + mDevicePolicyManager + ", wifiManager=" + mWifiManager);

        assertDeviceOwner();
    }

    private void assertDeviceOwner() {
        int myUserId = UserHandle.myUserId();
        assertWithMessage("DPM for user %s", myUserId).that(mDevicePolicyManager).isNotNull();

        ComponentName admin = getWho();
        assertWithMessage("Component %s is admin for user %s", admin, myUserId)
                .that(mDevicePolicyManager.isAdminActive(admin)).isTrue();

        String pkgName = mContext.getPackageName();
        assertWithMessage("Component %s is device owner for user %s", admin, myUserId)
                .that(mDevicePolicyManager.isDeviceOwnerApp(pkgName)).isTrue();
        assertWithMessage("Component %s is profile owner for user %s", admin, myUserId)
                .that(mDevicePolicyManager.isManagedProfile(admin)).isFalse();
    }

    protected ComponentName getWho() {
        return BasicAdminReceiver.getComponentName(mContext);
    }

    protected String executeShellCommand(String... command) throws Exception {
        return mDevice.executeShellCommand(String.join(" ", command));
    }

    protected static boolean isHeadlessSystemUserMode() {
        return UserManager.isHeadlessSystemUserMode();
    }

    protected final UserHandle getCurrentUser() {
        UserHandle currentUser = UserHandle.of(ActivityManager.getCurrentUser());
        Log.v(TAG, "getCurrentUser(): returning " + currentUser);
        return currentUser;
    }

    protected final List<WifiConfiguration> getConfiguredNetworks() {
        // Must use a the WifiManager of the current user to list networks, as
        // getConfiguredNetworks() would return empty on systems using headless system
        // mode as that method "Return a list of all the networks configured for the current
        // foreground user", and the system user is running in the background in this case.
        return mCurrentUserWifiManager.getConfiguredNetworks();
    }
}
