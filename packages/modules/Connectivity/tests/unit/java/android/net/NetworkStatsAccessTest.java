/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.net;

import static com.android.testutils.DevSdkIgnoreRuleKt.SC_V2;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.when;

import android.Manifest;
import android.Manifest.permission;
import android.app.AppOpsManager;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.telephony.TelephonyManager;

import androidx.test.filters.SmallTest;

import com.android.testutils.DevSdkIgnoreRule;
import com.android.testutils.DevSdkIgnoreRunner;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(DevSdkIgnoreRunner.class)
@SmallTest
@DevSdkIgnoreRule.IgnoreUpTo(SC_V2) // TODO: Use to Build.VERSION_CODES.SC_V2 when available
public class NetworkStatsAccessTest {
    private static final String TEST_PKG = "com.example.test";
    private static final int TEST_PID = 1234;
    private static final int TEST_UID = 12345;

    @Mock private Context mContext;
    @Mock private DevicePolicyManager mDpm;
    @Mock private TelephonyManager mTm;
    @Mock private AppOpsManager mAppOps;

    // Hold the real service so we can restore it when tearing down the test.
    private DevicePolicyManager mSystemDpm;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(mContext.getSystemService(Context.TELEPHONY_SERVICE)).thenReturn(mTm);
        when(mContext.getSystemService(Context.APP_OPS_SERVICE)).thenReturn(mAppOps);
        when(mContext.getSystemServiceName(DevicePolicyManager.class))
                .thenReturn(Context.DEVICE_POLICY_SERVICE);
        when(mContext.getSystemService(Context.DEVICE_POLICY_SERVICE)).thenReturn(mDpm);
        if (mContext.getSystemService(DevicePolicyManager.class) == null) {
            // Test is using mockito-extended
            doCallRealMethod().when(mContext).getSystemService(DevicePolicyManager.class);
        }

        setHasCarrierPrivileges(false);
        setIsDeviceOwner(false);
        setIsProfileOwner(false);
        setHasAppOpsPermission(AppOpsManager.MODE_DEFAULT, false);
        setHasReadHistoryPermission(false);
        setHasNetworkStackPermission(false);
        setHasMainlineNetworkStackPermission(false);
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testCheckAccessLevel_hasCarrierPrivileges() throws Exception {
        setHasCarrierPrivileges(true);
        assertEquals(NetworkStatsAccess.Level.DEVICE,
                NetworkStatsAccess.checkAccessLevel(mContext, TEST_PID, TEST_UID, TEST_PKG));
    }

    @Test
    public void testCheckAccessLevel_isDeviceOwner() throws Exception {
        setIsDeviceOwner(true);
        assertEquals(NetworkStatsAccess.Level.DEVICE,
                NetworkStatsAccess.checkAccessLevel(mContext, TEST_PID, TEST_UID, TEST_PKG));
    }

    @Test
    public void testCheckAccessLevel_isProfileOwner() throws Exception {
        setIsProfileOwner(true);
        assertEquals(NetworkStatsAccess.Level.USER,
                NetworkStatsAccess.checkAccessLevel(mContext, TEST_PID, TEST_UID, TEST_PKG));
    }

    @Test
    public void testCheckAccessLevel_hasAppOpsBitAllowed() throws Exception {
        setIsProfileOwner(true);
        setHasAppOpsPermission(AppOpsManager.MODE_ALLOWED, false);
        assertEquals(NetworkStatsAccess.Level.DEVICESUMMARY,
                NetworkStatsAccess.checkAccessLevel(mContext, TEST_PID, TEST_UID, TEST_PKG));
    }

    @Test
    public void testCheckAccessLevel_hasAppOpsBitDefault_grantedPermission() throws Exception {
        setIsProfileOwner(true);
        setHasAppOpsPermission(AppOpsManager.MODE_DEFAULT, true);
        assertEquals(NetworkStatsAccess.Level.DEVICESUMMARY,
                NetworkStatsAccess.checkAccessLevel(mContext, TEST_PID, TEST_UID, TEST_PKG));
    }

    @Test
    public void testCheckAccessLevel_hasReadHistoryPermission() throws Exception {
        setIsProfileOwner(true);
        setHasReadHistoryPermission(true);
        assertEquals(NetworkStatsAccess.Level.DEVICESUMMARY,
                NetworkStatsAccess.checkAccessLevel(mContext, TEST_PID, TEST_UID, TEST_PKG));
    }

    @Test
    public void testCheckAccessLevel_deniedAppOpsBit() throws Exception {
        setHasAppOpsPermission(AppOpsManager.MODE_ERRORED, true);
        assertEquals(NetworkStatsAccess.Level.DEFAULT,
                NetworkStatsAccess.checkAccessLevel(mContext, TEST_PID, TEST_UID, TEST_PKG));
    }

    @Test
    public void testCheckAccessLevel_deniedAppOpsBit_deniedPermission() throws Exception {
        assertEquals(NetworkStatsAccess.Level.DEFAULT,
                NetworkStatsAccess.checkAccessLevel(mContext, TEST_PID, TEST_UID, TEST_PKG));
    }

    @Test
    public void testCheckAccessLevel_hasNetworkStackPermission() throws Exception {
        assertEquals(NetworkStatsAccess.Level.DEFAULT,
                NetworkStatsAccess.checkAccessLevel(mContext, TEST_PID, TEST_UID, TEST_PKG));

        setHasNetworkStackPermission(true);
        assertEquals(NetworkStatsAccess.Level.DEVICE,
                NetworkStatsAccess.checkAccessLevel(mContext, TEST_PID, TEST_UID, TEST_PKG));

        setHasNetworkStackPermission(false);
        assertEquals(NetworkStatsAccess.Level.DEFAULT,
                NetworkStatsAccess.checkAccessLevel(mContext, TEST_PID, TEST_UID, TEST_PKG));

        setHasMainlineNetworkStackPermission(true);
        assertEquals(NetworkStatsAccess.Level.DEVICE,
                NetworkStatsAccess.checkAccessLevel(mContext, TEST_PID, TEST_UID, TEST_PKG));
    }

    private void setHasCarrierPrivileges(boolean hasPrivileges) {
        when(mTm.checkCarrierPrivilegesForPackageAnyPhone(TEST_PKG)).thenReturn(
                hasPrivileges ? TelephonyManager.CARRIER_PRIVILEGE_STATUS_HAS_ACCESS
                        : TelephonyManager.CARRIER_PRIVILEGE_STATUS_NO_ACCESS);
    }

    private void setIsDeviceOwner(boolean isOwner) {
        when(mDpm.isDeviceOwnerApp(TEST_PKG)).thenReturn(isOwner);
    }

    private void setIsProfileOwner(boolean isOwner) {
        when(mDpm.isProfileOwnerApp(TEST_PKG)).thenReturn(isOwner);
    }

    private void setHasAppOpsPermission(int appOpsMode, boolean hasPermission) {
        when(mAppOps.noteOp(AppOpsManager.OPSTR_GET_USAGE_STATS, TEST_UID, TEST_PKG,
                null /* attributionTag */, null /* message */)).thenReturn(appOpsMode);
        when(mContext.checkCallingPermission(Manifest.permission.PACKAGE_USAGE_STATS)).thenReturn(
                hasPermission ? PackageManager.PERMISSION_GRANTED
                        : PackageManager.PERMISSION_DENIED);
    }

    private void setHasReadHistoryPermission(boolean hasPermission) {
        when(mContext.checkCallingOrSelfPermission(permission.READ_NETWORK_USAGE_HISTORY))
                .thenReturn(hasPermission ? PackageManager.PERMISSION_GRANTED
                        : PackageManager.PERMISSION_DENIED);
    }

    private void setHasNetworkStackPermission(boolean hasPermission) {
        when(mContext.checkPermission(android.Manifest.permission.NETWORK_STACK,
                TEST_PID, TEST_UID)).thenReturn(hasPermission ? PackageManager.PERMISSION_GRANTED
                : PackageManager.PERMISSION_DENIED);
    }

    private void setHasMainlineNetworkStackPermission(boolean hasPermission) {
        when(mContext.checkPermission(NetworkStack.PERMISSION_MAINLINE_NETWORK_STACK,
                TEST_PID, TEST_UID)).thenReturn(hasPermission ? PackageManager.PERMISSION_GRANTED
                : PackageManager.PERMISSION_DENIED);
    }
}
