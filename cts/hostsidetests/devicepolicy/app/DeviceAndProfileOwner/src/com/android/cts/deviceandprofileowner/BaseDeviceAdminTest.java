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
package com.android.cts.deviceandprofileowner;

import static com.google.common.truth.Truth.assertWithMessage;

import android.annotation.NonNull;
import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Process;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.UserManager;
import android.test.InstrumentationTestCase;
import android.text.TextUtils;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bedstead.dpmwrapper.DeviceOwnerHelper;
import com.android.bedstead.dpmwrapper.TestAppSystemServiceFactory;
import com.android.compatibility.common.util.SystemUtil;
import com.android.cts.deviceandprofileowner.BaseDeviceAdminTest.BasicAdminReceiver;

import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Base class for profile and device based tests.
 *
 * <p>This class handles making sure that the test is the profile or device owner and that it has an
 * active admin registered, so that all tests may assume these are done.
 */
public abstract class BaseDeviceAdminTest extends InstrumentationTestCase {

    public static final class BasicAdminReceiver extends DeviceAdminReceiver {

        static final String ACTION_NETWORK_LOGS_AVAILABLE =
                "com.android.cts.deviceandprofileowner.action.ACTION_NETWORK_LOGS_AVAILABLE";

        static final String EXTRA_NETWORK_LOGS_BATCH_TOKEN =
                "com.android.cts.deviceandprofileowner.extra.NETWORK_LOGS_BATCH_TOKEN";

        // Shared preference used to coordinate compliance acknowledgement test.
        static final String COMPLIANCE_ACK_PREF_NAME = "compliance-pref";
        // Shared preference key controlling whether to use default callback implementation.
        static final String COMPLIANCE_ACK_PREF_KEY_OVERRIDE = "compliance-pref-override";
        // Shared preference key to save broadcast receipt.
        static final String COMPLIANCE_ACK_PREF_KEY_BCAST_RECEIVED = "compliance-pref-bcast";

        @Override
        public void onReceive(Context context, Intent intent) {
            if (DeviceOwnerHelper.runManagerMethod(this, context, intent)) return;

            super.onReceive(context, intent);
        }

        @Override
        public String onChoosePrivateKeyAlias(Context context, Intent intent, int uid, Uri uri,
                String suggestedAlias) {
            super.onChoosePrivateKeyAlias(context, intent, uid, uri, suggestedAlias);
            if (uid != Process.myUid() || uri == null) {
                return null;
            }
            return uri.getQueryParameter("alias");
        }

        @Override
        public void onPasswordExpiring(Context context, Intent intent, UserHandle user) {
            super.onPasswordExpiring(context, intent, user);
            if (mOnPasswordExpiryTimeoutCalled != null) {
                mOnPasswordExpiryTimeoutCalled.countDown();
            }
        }

        @Override
        public void onNetworkLogsAvailable(Context context, Intent intent, long batchToken,
                int networkLogsCount) {
            super.onNetworkLogsAvailable(context, intent, batchToken, networkLogsCount);
            // send the broadcast, the rest of the test happens in NetworkLoggingTest
            Intent batchIntent = new Intent(ACTION_NETWORK_LOGS_AVAILABLE);
            batchIntent.putExtra(EXTRA_NETWORK_LOGS_BATCH_TOKEN, batchToken);
            context.sendBroadcast(batchIntent);
        }

        @Override
        public void onComplianceAcknowledgementRequired(
                @NonNull Context context, @NonNull Intent intent) {
            final SharedPreferences pref =
                    context.getSharedPreferences(COMPLIANCE_ACK_PREF_NAME, Context.MODE_PRIVATE);
            // Record the broadcast receipt.
            pref.edit().putBoolean(COMPLIANCE_ACK_PREF_KEY_BCAST_RECEIVED, true).commit();
            // Call the default implementation unless instructed otherwise.
            if (!pref.getBoolean(COMPLIANCE_ACK_PREF_KEY_OVERRIDE, false)) {
                super.onComplianceAcknowledgementRequired(context, intent);
            }
        }

        @Override
        public void onPasswordChanged(@NonNull Context context, @NonNull Intent intent,
                @NonNull UserHandle user) {
            super.onPasswordChanged(context, intent, user);
            if (mOnPasswordChangedCalled != null) {
                mOnPasswordChangedCalled.countDown();
            }
        }
    }

    private static final String TAG = BaseDeviceAdminTest.class.getSimpleName();

    public static final String PACKAGE_NAME = BasicAdminReceiver.class.getPackage().getName();
    public static final ComponentName ADMIN_RECEIVER_COMPONENT = new ComponentName(
            PACKAGE_NAME, BasicAdminReceiver.class.getName());

    protected DevicePolicyManager mDevicePolicyManager;
    protected DevicePolicyManager mLocalDevicePolicyManager;
    protected UserManager mUserManager;
    protected Context mContext;
    protected boolean mHasSecureLockScreen;
    protected boolean mIsAutomotive;
    protected boolean mIsDeviceOwnerTest;
    static CountDownLatch mOnPasswordExpiryTimeoutCalled;
    static CountDownLatch mOnPasswordChangedCalled;
    protected final String mTag = getClass().getSimpleName();

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mContext = getInstrumentation().getContext();

        mUserManager = mContext.getSystemService(UserManager.class);
        assertWithMessage("userManager").that(mUserManager).isNotNull();

        mHasSecureLockScreen = mContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_SECURE_LOCK_SCREEN);
        mIsAutomotive = mContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_AUTOMOTIVE);

        mIsDeviceOwnerTest = "DeviceOwner"
                .equals(InstrumentationRegistry.getArguments().getString("admin_type"));

        mDevicePolicyManager = TestAppSystemServiceFactory.getDevicePolicyManager(mContext,
                BasicAdminReceiver.class, mIsDeviceOwnerTest);
        mLocalDevicePolicyManager = mContext.getSystemService(DevicePolicyManager.class);

        Log.v(TAG, "setup(): dpm for " + getClass() + " and user " + mContext.getUserId() + ": "
                + mDevicePolicyManager);
        assertWithMessage("dpm").that(mDevicePolicyManager).isNotNull();

        boolean isActiveAdmin = mDevicePolicyManager.isAdminActive(ADMIN_RECEIVER_COMPONENT);
        boolean isProfileOwner = mDevicePolicyManager.isProfileOwnerApp(PACKAGE_NAME);
        boolean isDeviceOwner = mDevicePolicyManager.isDeviceOwnerApp(PACKAGE_NAME);

        Log.d(mTag, "setup() on user " + mContext.getUserId() + ": package=" + PACKAGE_NAME
                + ", adminReceiverComponent=" + ADMIN_RECEIVER_COMPONENT
                + ", isActiveAdmin=" + isActiveAdmin + ", isProfileOwner=" + isProfileOwner
                + ", isDeviceOwner=" + isDeviceOwner + ", isDeviceOwnerTest=" + mIsDeviceOwnerTest);

        assertWithMessage("active admin for %s", ADMIN_RECEIVER_COMPONENT).that(isActiveAdmin)
                .isTrue();

        assertWithMessage("profile owner or device owner for %s", PACKAGE_NAME)
                .that(isProfileOwner || isDeviceOwner).isTrue();
    }

    protected int getTargetApiLevel() throws Exception {
        final PackageManager pm = mContext.getPackageManager();
        final PackageInfo pi = pm.getPackageInfo(mContext.getPackageName(), /* flags= */ 0);
        return pi.applicationInfo.targetSdkVersion;
    }

    /**
     * Runs a Shell command, returning a trimmed response.
     */
    protected String runShellCommand(String template, Object...args) {
        final String command = String.format(template, args);
        Log.d(mTag, "runShellCommand(): " + command);
        try {
            final String result = SystemUtil.runShellCommand(getInstrumentation(), command);
            return TextUtils.isEmpty(result) ? "" : result.trim();
        } catch (Exception e) {
            throw new RuntimeException("Command '" + command + "' failed: ", e);
        }
    }

    protected void waitUntilUserUnlocked() {
        boolean isUserUnlocked = mUserManager.isUserUnlocked();
        int retries = 30;
        while (retries >= 0 && !isUserUnlocked) {
            retries--;
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                break;
            }
        }
        assertWithMessage("user unlocked").that(mUserManager.isUserUnlocked()).isTrue();
    }

    protected void assertPasswordSufficiency(boolean expectPasswordSufficient) {
        waitUntilUserUnlocked();
        assertWithMessage("isActivePasswordSufficient()")
                .that(mDevicePolicyManager.isActivePasswordSufficient())
                .isEqualTo(expectPasswordSufficient);
    }

    protected boolean isDeviceOwner() {
        return mDevicePolicyManager.isDeviceOwnerApp(PACKAGE_NAME);
    }

    protected void setDelegatedScopes(String delegatePackage, List<String> scopes) {
        Log.v(TAG, "Calling setDelegatedScopes(" + ADMIN_RECEIVER_COMPONENT.flattenToShortString()
                + ", " + delegatePackage + ", " + scopes + ") using " + mDevicePolicyManager);
        mDevicePolicyManager.setDelegatedScopes(ADMIN_RECEIVER_COMPONENT, delegatePackage, scopes);
    }

    void sleep(int timeMs) {
        Log.d(TAG, "Sleeping " + timeMs + " ms");
        SystemClock.sleep(timeMs);
    }
}
