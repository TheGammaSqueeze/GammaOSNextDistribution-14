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

package android.security.cts.CVE_2022_20420;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeNoException;
import static org.junit.Assume.assumeTrue;

import android.app.ActivityManager;
import android.app.UiAutomation;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IDeviceIdleController;
import android.os.PowerExemptionManager;
import android.os.Process;
import android.os.ServiceManager;
import android.platform.test.annotations.AsbSecurityTest;

import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class CVE_2022_20420 extends StsExtraBusinessLogicTestCase {
    private static final int TIMEOUT_MS = 10000;
    private static final int USER_ID = 0;
    private Context mContext;
    private DevicePolicyManager mPolicyManager;
    private ComponentName mComponentName;
    private UiAutomation mAutomation;

    @After
    public void tearDown() {
        try {
            mAutomation.dropShellPermissionIdentity();
            mPolicyManager.removeActiveAdmin(mComponentName);
        } catch (Exception ignored) {
            // ignore all exceptions as the test has been completed.
        }
    }

    @AsbSecurityTest(cveBugId = 238377411)
    @Test
    public void testDeviceAdminAppRestricted() {
        try {
            // Add test app to Power Save Whitelist.
            mContext = getInstrumentation().getTargetContext();
            mAutomation = getInstrumentation().getUiAutomation();
            mAutomation.adoptShellPermissionIdentity(android.Manifest.permission.DEVICE_POWER,
                    android.Manifest.permission.MANAGE_DEVICE_ADMINS,
                    android.Manifest.permission.INTERACT_ACROSS_USERS_FULL);
            IDeviceIdleController mDeviceIdleService =
                    IDeviceIdleController.Stub.asInterface(ServiceManager.getService("deviceidle"));
            mDeviceIdleService.addPowerSaveWhitelistApp(mContext.getPackageName());

            // Set test app as "Active Admin".
            mPolicyManager = mContext.getSystemService(DevicePolicyManager.class);
            mComponentName = new ComponentName(mContext, PocDeviceAdminReceiver.class);
            mPolicyManager.setActiveAdmin(mComponentName, true, USER_ID);
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    future.complete(true);
                }
            };
            mContext.registerReceiver(broadcastReceiver,
                    new IntentFilter("broadcastCVE_2022_20420"));
            future.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);

            // Call vulnerable function getBackgroundRestrictionExemptionReason()
            ActivityManager activityManager = mContext.getSystemService(ActivityManager.class);
            int reason = activityManager.getBackgroundRestrictionExemptionReason(Process.myUid());
            assumeTrue(
                    "Reason code other than REASON_ACTIVE_DEVICE_ADMIN/REASON_ALLOWLISTED_PACKAGE"
                            + " returned by getBackgroundRestrictionExemptionReason() = " + reason,
                    reason == PowerExemptionManager.REASON_ACTIVE_DEVICE_ADMIN
                            || reason == PowerExemptionManager.REASON_ALLOWLISTED_PACKAGE);
            assertFalse("Vulnerable to b/238377411 !!",
                    reason == PowerExemptionManager.REASON_ALLOWLISTED_PACKAGE);
        } catch (Exception e) {
            assumeNoException(e);
        }
    }
}
