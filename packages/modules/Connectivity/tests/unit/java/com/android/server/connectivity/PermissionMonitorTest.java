/*
 * Copyright (C) 2018 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.server.connectivity;

import static android.Manifest.permission.CHANGE_NETWORK_STATE;
import static android.Manifest.permission.CHANGE_WIFI_STATE;
import static android.Manifest.permission.CONNECTIVITY_INTERNAL;
import static android.Manifest.permission.CONNECTIVITY_USE_RESTRICTED_NETWORKS;
import static android.Manifest.permission.INTERNET;
import static android.Manifest.permission.NETWORK_STACK;
import static android.Manifest.permission.UPDATE_DEVICE_STATS;
import static android.content.pm.ApplicationInfo.PRIVATE_FLAG_OEM;
import static android.content.pm.ApplicationInfo.PRIVATE_FLAG_PRODUCT;
import static android.content.pm.ApplicationInfo.PRIVATE_FLAG_VENDOR;
import static android.content.pm.PackageInfo.REQUESTED_PERMISSION_GRANTED;
import static android.content.pm.PackageInfo.REQUESTED_PERMISSION_REQUIRED;
import static android.content.pm.PackageManager.GET_PERMISSIONS;
import static android.content.pm.PackageManager.MATCH_ANY_USER;
import static android.net.ConnectivitySettingsManager.UIDS_ALLOWED_ON_RESTRICTED_NETWORKS;
import static android.net.INetd.PERMISSION_INTERNET;
import static android.net.INetd.PERMISSION_NETWORK;
import static android.net.INetd.PERMISSION_NONE;
import static android.net.INetd.PERMISSION_SYSTEM;
import static android.net.INetd.PERMISSION_UNINSTALLED;
import static android.net.INetd.PERMISSION_UPDATE_DEVICE_STATS;
import static android.net.NetworkStack.PERMISSION_MAINLINE_NETWORK_STACK;
import static android.os.Process.SYSTEM_UID;

import static com.android.server.connectivity.PermissionMonitor.isHigherNetworkPermission;

import static junit.framework.Assert.fail;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.intThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.net.INetd;
import android.net.UidRange;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.os.SystemConfigManager;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.util.SparseIntArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;

import com.android.modules.utils.build.SdkLevel;
import com.android.net.module.util.CollectionUtils;
import com.android.networkstack.apishim.ProcessShimImpl;
import com.android.networkstack.apishim.common.ProcessShim;
import com.android.server.BpfNetMaps;
import com.android.testutils.DevSdkIgnoreRule;
import com.android.testutils.DevSdkIgnoreRunner;
import com.android.testutils.HandlerUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.AdditionalAnswers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

@RunWith(DevSdkIgnoreRunner.class)
@SmallTest
@DevSdkIgnoreRule.IgnoreUpTo(Build.VERSION_CODES.R)
public class PermissionMonitorTest {
    private static final int MOCK_USER_ID1 = 0;
    private static final int MOCK_USER_ID2 = 1;
    private static final int MOCK_USER_ID3 = 2;
    private static final UserHandle MOCK_USER1 = UserHandle.of(MOCK_USER_ID1);
    private static final UserHandle MOCK_USER2 = UserHandle.of(MOCK_USER_ID2);
    private static final UserHandle MOCK_USER3 = UserHandle.of(MOCK_USER_ID3);
    private static final int MOCK_APPID1 = 10001;
    private static final int MOCK_APPID2 = 10086;
    private static final int MOCK_APPID3 = 10110;
    private static final int MOCK_APPID4 = 10111;
    private static final int SYSTEM_APPID1 = 1100;
    private static final int SYSTEM_APPID2 = 1108;
    private static final int VPN_APPID = 10002;
    private static final int MOCK_UID11 = MOCK_USER1.getUid(MOCK_APPID1);
    private static final int MOCK_UID12 = MOCK_USER1.getUid(MOCK_APPID2);
    private static final int MOCK_UID13 = MOCK_USER1.getUid(MOCK_APPID3);
    private static final int MOCK_UID14 = MOCK_USER1.getUid(MOCK_APPID4);
    private static final int SYSTEM_APP_UID11 = MOCK_USER1.getUid(SYSTEM_APPID1);
    private static final int VPN_UID = MOCK_USER1.getUid(VPN_APPID);
    private static final int MOCK_UID21 = MOCK_USER2.getUid(MOCK_APPID1);
    private static final int MOCK_UID22 = MOCK_USER2.getUid(MOCK_APPID2);
    private static final int MOCK_UID23 = MOCK_USER2.getUid(MOCK_APPID3);
    private static final int SYSTEM_APP_UID21 = MOCK_USER2.getUid(SYSTEM_APPID1);
    private static final int MOCK_UID31 = MOCK_USER3.getUid(MOCK_APPID1);
    private static final int MOCK_UID32 = MOCK_USER3.getUid(MOCK_APPID2);
    private static final int MOCK_UID33 = MOCK_USER3.getUid(MOCK_APPID3);
    private static final String REAL_SYSTEM_PACKAGE_NAME = "android";
    private static final String MOCK_PACKAGE1 = "appName1";
    private static final String MOCK_PACKAGE2 = "appName2";
    private static final String MOCK_PACKAGE3 = "appName3";
    private static final String SYSTEM_PACKAGE1 = "sysName1";
    private static final String SYSTEM_PACKAGE2 = "sysName2";
    private static final String PARTITION_SYSTEM = "system";
    private static final String PARTITION_OEM = "oem";
    private static final String PARTITION_PRODUCT = "product";
    private static final String PARTITION_VENDOR = "vendor";
    private static final int VERSION_P = Build.VERSION_CODES.P;
    private static final int VERSION_Q = Build.VERSION_CODES.Q;
    private static final int PERMISSION_TRAFFIC_ALL =
            PERMISSION_INTERNET | PERMISSION_UPDATE_DEVICE_STATS;
    private static final int TIMEOUT_MS = 2_000;

    @Mock private Context mContext;
    @Mock private PackageManager mPackageManager;
    @Mock private INetd mNetdService;
    @Mock private UserManager mUserManager;
    @Mock private PermissionMonitor.Dependencies mDeps;
    @Mock private SystemConfigManager mSystemConfigManager;
    @Mock private BpfNetMaps mBpfNetMaps;

    private PermissionMonitor mPermissionMonitor;
    private NetdMonitor mNetdMonitor;
    private BpfMapMonitor mBpfMapMonitor;
    private HandlerThread mHandlerThread;
    private ProcessShim mProcessShim = ProcessShimImpl.newInstance();

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        when(mContext.getSystemService(eq(Context.USER_SERVICE))).thenReturn(mUserManager);
        doReturn(List.of(MOCK_USER1)).when(mUserManager).getUserHandles(eq(true));
        when(mContext.getSystemServiceName(SystemConfigManager.class))
                .thenReturn(Context.SYSTEM_CONFIG_SERVICE);
        when(mContext.getSystemService(Context.SYSTEM_CONFIG_SERVICE))
                .thenReturn(mSystemConfigManager);
        if (mContext.getSystemService(SystemConfigManager.class) == null) {
            // Test is using mockito-extended
            doCallRealMethod().when(mContext).getSystemService(SystemConfigManager.class);
        }
        when(mSystemConfigManager.getSystemPermissionUids(anyString())).thenReturn(new int[0]);
        doAnswer(invocation -> {
            final Object[] args = invocation.getArguments();
            final Context asUserCtx = mock(Context.class, AdditionalAnswers.delegatesTo(mContext));
            final UserHandle user = (UserHandle) args[0];
            doReturn(user).when(asUserCtx).getUser();
            return asUserCtx;
        }).when(mContext).createContextAsUser(any(), anyInt());
        when(mDeps.getUidsAllowedOnRestrictedNetworks(any())).thenReturn(Set.of());
        // Set DEVICE_INITIAL_SDK_INT to Q that SYSTEM_UID won't have restricted network permission
        // by default.
        doReturn(VERSION_Q).when(mDeps).getDeviceFirstSdkInt();

        mHandlerThread = new HandlerThread("PermissionMonitorTest");
        mPermissionMonitor = new PermissionMonitor(
                mContext, mNetdService, mBpfNetMaps, mDeps, mHandlerThread);
        mNetdMonitor = new NetdMonitor(mNetdService);
        mBpfMapMonitor = new BpfMapMonitor(mBpfNetMaps);

        // Start the HandlerThread after PermissionMonitor created as CS current behavior.
        mHandlerThread.start();

        doReturn(List.of()).when(mPackageManager).getInstalledPackagesAsUser(anyInt(), anyInt());
        onUserAdded(MOCK_USER1);
    }

    @After
    public void tearDown() throws Exception {
        if (mHandlerThread != null) {
            mHandlerThread.quitSafely();
            mHandlerThread.join();
        }
    }

    private boolean hasRestrictedNetworkPermission(String partition, int targetSdkVersion,
            String packageName, int uid, String... permissions) {
        final PackageInfo packageInfo =
                packageInfoWithPermissions(REQUESTED_PERMISSION_GRANTED, permissions, partition);
        packageInfo.packageName = packageName;
        packageInfo.applicationInfo.targetSdkVersion = targetSdkVersion;
        packageInfo.applicationInfo.uid = uid;
        return mPermissionMonitor.hasRestrictedNetworkPermission(packageInfo);
    }

    private boolean hasSdkSandbox(final int uid) {
        return SdkLevel.isAtLeastT() && Process.isApplicationUid(uid);
    }

    private static PackageInfo systemPackageInfoWithPermissions(String... permissions) {
        return packageInfoWithPermissions(
                REQUESTED_PERMISSION_GRANTED, permissions, PARTITION_SYSTEM);
    }

    private static PackageInfo vendorPackageInfoWithPermissions(String... permissions) {
        return packageInfoWithPermissions(
                REQUESTED_PERMISSION_GRANTED, permissions, PARTITION_VENDOR);
    }

    private static PackageInfo packageInfoWithPermissions(int permissionsFlags,
            String[] permissions, String partition) {
        int[] requestedPermissionsFlags = new int[permissions.length];
        for (int i = 0; i < permissions.length; i++) {
            requestedPermissionsFlags[i] = permissionsFlags;
        }
        final PackageInfo packageInfo = new PackageInfo();
        packageInfo.requestedPermissions = permissions;
        packageInfo.applicationInfo = new ApplicationInfo();
        packageInfo.requestedPermissionsFlags = requestedPermissionsFlags;
        int privateFlags = 0;
        switch (partition) {
            case PARTITION_OEM:
                privateFlags = PRIVATE_FLAG_OEM;
                break;
            case PARTITION_PRODUCT:
                privateFlags = PRIVATE_FLAG_PRODUCT;
                break;
            case PARTITION_VENDOR:
                privateFlags = PRIVATE_FLAG_VENDOR;
                break;
        }
        packageInfo.applicationInfo.privateFlags = privateFlags;
        return packageInfo;
    }

    private static PackageInfo buildPackageInfo(String packageName, int uid,
            String... permissions) {
        final PackageInfo pkgInfo = systemPackageInfoWithPermissions(permissions);
        pkgInfo.packageName = packageName;
        pkgInfo.applicationInfo.uid = uid;
        return pkgInfo;
    }

    // TODO: Move this method to static lib.
    private static @NonNull <T> T[] appendElement(Class<T> kind, @Nullable T[] array, T element) {
        final T[] result;
        if (array != null) {
            result = Arrays.copyOf(array, array.length + 1);
        } else {
            result = (T[]) Array.newInstance(kind, 1);
        }
        result[result.length - 1] = element;
        return result;
    }

    private void buildAndMockPackageInfoWithPermissions(String packageName, int uid,
            String... permissions) throws Exception {
        final PackageInfo packageInfo = buildPackageInfo(packageName, uid, permissions);
        // This will return the wrong UID for the package when queried with other users.
        doReturn(packageInfo).when(mPackageManager)
                .getPackageInfo(eq(packageName), anyInt() /* flag */);
        final String[] oldPackages = mPackageManager.getPackagesForUid(uid);
        // If it's duplicated package, no need to set it again.
        if (CollectionUtils.contains(oldPackages, packageName)) return;

        // Combine the package if this uid is shared with other packages.
        final String[] newPackages = appendElement(String.class, oldPackages, packageName);
        doReturn(newPackages).when(mPackageManager).getPackagesForUid(eq(uid));
    }

    private void startMonitoring() {
        processOnHandlerThread(() -> mPermissionMonitor.startMonitoring());
    }

    private void onUserAdded(UserHandle user) {
        processOnHandlerThread(() -> mPermissionMonitor.onUserAdded(user));
    }

    private void onUserRemoved(UserHandle user) {
        processOnHandlerThread(() -> mPermissionMonitor.onUserRemoved(user));
    }

    private void onPackageAdded(String packageName, int uid) {
        processOnHandlerThread(() -> mPermissionMonitor.onPackageAdded(packageName, uid));
    }

    private void onPackageRemoved(String packageName, int uid) {
        processOnHandlerThread(() -> mPermissionMonitor.onPackageRemoved(packageName, uid));
    }

    private void sendAppIdsTrafficPermission(SparseIntArray netdPermissionsAppIds) {
        processOnHandlerThread(() ->
                mPermissionMonitor.sendAppIdsTrafficPermission(netdPermissionsAppIds));
    }

    private void sendPackagePermissionsForAppId(int appId, int permissions) {
        processOnHandlerThread(() ->
                mPermissionMonitor.sendPackagePermissionsForAppId(appId, permissions));
    }

    private void addPackage(String packageName, int uid, String... permissions) throws Exception {
        buildAndMockPackageInfoWithPermissions(packageName, uid, permissions);
        processOnHandlerThread(() -> mPermissionMonitor.onPackageAdded(packageName, uid));
    }

    private void removePackage(String packageName, int uid) {
        final String[] oldPackages = mPackageManager.getPackagesForUid(uid);
        // If the package isn't existed, no need to remove it.
        if (!CollectionUtils.contains(oldPackages, packageName)) return;

        // Remove the package if this uid is shared with other packages.
        final String[] newPackages = Arrays.stream(oldPackages).filter(e -> !e.equals(packageName))
                .toArray(String[]::new);
        doReturn(newPackages).when(mPackageManager).getPackagesForUid(eq(uid));
        processOnHandlerThread(() -> mPermissionMonitor.onPackageRemoved(packageName, uid));
    }

    @Test
    public void testHasPermission() {
        PackageInfo app = systemPackageInfoWithPermissions();
        assertFalse(mPermissionMonitor.hasPermission(app, CHANGE_NETWORK_STATE));
        assertFalse(mPermissionMonitor.hasPermission(app, NETWORK_STACK));
        assertFalse(mPermissionMonitor.hasPermission(app, CONNECTIVITY_USE_RESTRICTED_NETWORKS));
        assertFalse(mPermissionMonitor.hasPermission(app, CONNECTIVITY_INTERNAL));

        app = systemPackageInfoWithPermissions(CHANGE_NETWORK_STATE, NETWORK_STACK);
        assertTrue(mPermissionMonitor.hasPermission(app, CHANGE_NETWORK_STATE));
        assertTrue(mPermissionMonitor.hasPermission(app, NETWORK_STACK));
        assertFalse(mPermissionMonitor.hasPermission(app, CONNECTIVITY_USE_RESTRICTED_NETWORKS));
        assertFalse(mPermissionMonitor.hasPermission(app, CONNECTIVITY_INTERNAL));

        app = systemPackageInfoWithPermissions(
                CONNECTIVITY_USE_RESTRICTED_NETWORKS, CONNECTIVITY_INTERNAL);
        assertFalse(mPermissionMonitor.hasPermission(app, CHANGE_NETWORK_STATE));
        assertFalse(mPermissionMonitor.hasPermission(app, NETWORK_STACK));
        assertTrue(mPermissionMonitor.hasPermission(app, CONNECTIVITY_USE_RESTRICTED_NETWORKS));
        assertTrue(mPermissionMonitor.hasPermission(app, CONNECTIVITY_INTERNAL));

        app = packageInfoWithPermissions(REQUESTED_PERMISSION_REQUIRED, new String[] {
                CONNECTIVITY_USE_RESTRICTED_NETWORKS, CONNECTIVITY_INTERNAL, NETWORK_STACK },
                PARTITION_SYSTEM);
        assertFalse(mPermissionMonitor.hasPermission(app, CHANGE_NETWORK_STATE));
        assertFalse(mPermissionMonitor.hasPermission(app, NETWORK_STACK));
        assertFalse(mPermissionMonitor.hasPermission(app, CONNECTIVITY_USE_RESTRICTED_NETWORKS));
        assertFalse(mPermissionMonitor.hasPermission(app, CONNECTIVITY_INTERNAL));

        app = systemPackageInfoWithPermissions(CHANGE_NETWORK_STATE);
        app.requestedPermissions = null;
        assertFalse(mPermissionMonitor.hasPermission(app, CHANGE_NETWORK_STATE));

        app = systemPackageInfoWithPermissions(CHANGE_NETWORK_STATE);
        app.requestedPermissionsFlags = null;
        assertFalse(mPermissionMonitor.hasPermission(app, CHANGE_NETWORK_STATE));
    }

    @Test
    public void testIsVendorApp() {
        PackageInfo app = systemPackageInfoWithPermissions();
        assertFalse(mPermissionMonitor.isVendorApp(app.applicationInfo));
        app = packageInfoWithPermissions(REQUESTED_PERMISSION_GRANTED,
                new String[] {}, PARTITION_OEM);
        assertTrue(mPermissionMonitor.isVendorApp(app.applicationInfo));
        app = packageInfoWithPermissions(REQUESTED_PERMISSION_GRANTED,
                new String[] {}, PARTITION_PRODUCT);
        assertTrue(mPermissionMonitor.isVendorApp(app.applicationInfo));
        app = vendorPackageInfoWithPermissions();
        assertTrue(mPermissionMonitor.isVendorApp(app.applicationInfo));
    }

    @Test
    public void testHasNetworkPermission() {
        PackageInfo app = systemPackageInfoWithPermissions();
        assertFalse(mPermissionMonitor.hasNetworkPermission(app));
        app = systemPackageInfoWithPermissions(CHANGE_NETWORK_STATE);
        assertTrue(mPermissionMonitor.hasNetworkPermission(app));
        app = systemPackageInfoWithPermissions(NETWORK_STACK);
        assertFalse(mPermissionMonitor.hasNetworkPermission(app));
        app = systemPackageInfoWithPermissions(CONNECTIVITY_USE_RESTRICTED_NETWORKS);
        assertFalse(mPermissionMonitor.hasNetworkPermission(app));
        app = systemPackageInfoWithPermissions(CONNECTIVITY_INTERNAL);
        assertFalse(mPermissionMonitor.hasNetworkPermission(app));
    }

    @Test
    public void testHasRestrictedNetworkPermission() {
        assertFalse(hasRestrictedNetworkPermission(
                PARTITION_SYSTEM, VERSION_P, MOCK_PACKAGE1, MOCK_UID11));
        assertFalse(hasRestrictedNetworkPermission(
                PARTITION_SYSTEM, VERSION_P, MOCK_PACKAGE1, MOCK_UID11, CHANGE_NETWORK_STATE));
        assertTrue(hasRestrictedNetworkPermission(
                PARTITION_SYSTEM, VERSION_P, MOCK_PACKAGE1, MOCK_UID11, NETWORK_STACK));
        assertFalse(hasRestrictedNetworkPermission(
                PARTITION_SYSTEM, VERSION_P, MOCK_PACKAGE1, MOCK_UID11, CONNECTIVITY_INTERNAL));
        assertTrue(hasRestrictedNetworkPermission(
                PARTITION_SYSTEM, VERSION_P, MOCK_PACKAGE1, MOCK_UID11,
                CONNECTIVITY_USE_RESTRICTED_NETWORKS));
        assertFalse(hasRestrictedNetworkPermission(
                PARTITION_SYSTEM, VERSION_P, MOCK_PACKAGE1, MOCK_UID11, CHANGE_WIFI_STATE));
        assertTrue(hasRestrictedNetworkPermission(
                PARTITION_SYSTEM, VERSION_P, MOCK_PACKAGE1, MOCK_UID11,
                PERMISSION_MAINLINE_NETWORK_STACK));

        assertFalse(hasRestrictedNetworkPermission(
                PARTITION_SYSTEM, VERSION_Q, MOCK_PACKAGE1, MOCK_UID11));
        assertFalse(hasRestrictedNetworkPermission(
                PARTITION_SYSTEM, VERSION_Q, MOCK_PACKAGE1, MOCK_UID11, CONNECTIVITY_INTERNAL));
    }

    @Test
    public void testHasRestrictedNetworkPermissionSystemUid() {
        doReturn(VERSION_P).when(mDeps).getDeviceFirstSdkInt();
        assertTrue(hasRestrictedNetworkPermission(
                PARTITION_SYSTEM, VERSION_P, SYSTEM_PACKAGE1, SYSTEM_UID));
        assertTrue(hasRestrictedNetworkPermission(
                PARTITION_SYSTEM, VERSION_P, SYSTEM_PACKAGE1, SYSTEM_UID, CONNECTIVITY_INTERNAL));
        assertTrue(hasRestrictedNetworkPermission(
                PARTITION_SYSTEM, VERSION_P, SYSTEM_PACKAGE1, SYSTEM_UID,
                CONNECTIVITY_USE_RESTRICTED_NETWORKS));

        doReturn(VERSION_Q).when(mDeps).getDeviceFirstSdkInt();
        assertFalse(hasRestrictedNetworkPermission(
                PARTITION_SYSTEM, VERSION_Q, SYSTEM_PACKAGE1, SYSTEM_UID));
        assertFalse(hasRestrictedNetworkPermission(
                PARTITION_SYSTEM, VERSION_Q, SYSTEM_PACKAGE1, SYSTEM_UID, CONNECTIVITY_INTERNAL));
        assertTrue(hasRestrictedNetworkPermission(
                PARTITION_SYSTEM, VERSION_Q, SYSTEM_PACKAGE1, SYSTEM_UID,
                CONNECTIVITY_USE_RESTRICTED_NETWORKS));
    }

    @Test
    public void testHasRestrictedNetworkPermissionVendorApp() {
        assertTrue(hasRestrictedNetworkPermission(
                PARTITION_VENDOR, VERSION_P, MOCK_PACKAGE1, MOCK_UID11));
        assertTrue(hasRestrictedNetworkPermission(
                PARTITION_VENDOR, VERSION_P, MOCK_PACKAGE1, MOCK_UID11, CHANGE_NETWORK_STATE));
        assertTrue(hasRestrictedNetworkPermission(
                PARTITION_VENDOR, VERSION_P, MOCK_PACKAGE1, MOCK_UID11, NETWORK_STACK));
        assertTrue(hasRestrictedNetworkPermission(
                PARTITION_VENDOR, VERSION_P, MOCK_PACKAGE1, MOCK_UID11, CONNECTIVITY_INTERNAL));
        assertTrue(hasRestrictedNetworkPermission(
                PARTITION_VENDOR, VERSION_P, MOCK_PACKAGE1, MOCK_UID11,
                CONNECTIVITY_USE_RESTRICTED_NETWORKS));
        assertTrue(hasRestrictedNetworkPermission(
                PARTITION_VENDOR, VERSION_P, MOCK_PACKAGE1, MOCK_UID11, CHANGE_WIFI_STATE));

        assertFalse(hasRestrictedNetworkPermission(
                PARTITION_VENDOR, VERSION_Q, MOCK_PACKAGE1, MOCK_UID11));
        assertFalse(hasRestrictedNetworkPermission(
                PARTITION_VENDOR, VERSION_Q, MOCK_PACKAGE1, MOCK_UID11, CONNECTIVITY_INTERNAL));
        assertFalse(hasRestrictedNetworkPermission(
                PARTITION_VENDOR, VERSION_Q, MOCK_PACKAGE1, MOCK_UID11, CHANGE_NETWORK_STATE));
    }

    @Test
    public void testHasRestrictedNetworkPermissionUidAllowedOnRestrictedNetworks() {
        mPermissionMonitor.updateUidsAllowedOnRestrictedNetworks(Set.of(MOCK_UID11));
        assertTrue(hasRestrictedNetworkPermission(
                PARTITION_VENDOR, VERSION_Q, MOCK_PACKAGE1, MOCK_UID11));
        assertTrue(hasRestrictedNetworkPermission(
                PARTITION_VENDOR, VERSION_Q, MOCK_PACKAGE1, MOCK_UID11, CHANGE_NETWORK_STATE));
        assertTrue(hasRestrictedNetworkPermission(
                PARTITION_VENDOR, VERSION_Q, MOCK_PACKAGE1, MOCK_UID11, CONNECTIVITY_INTERNAL));

        assertFalse(hasRestrictedNetworkPermission(
                PARTITION_VENDOR, VERSION_Q, MOCK_PACKAGE2, MOCK_UID12));
        assertFalse(hasRestrictedNetworkPermission(
                PARTITION_VENDOR, VERSION_Q, MOCK_PACKAGE2, MOCK_UID12, CHANGE_NETWORK_STATE));
        assertFalse(hasRestrictedNetworkPermission(
                PARTITION_VENDOR, VERSION_Q, MOCK_PACKAGE2, MOCK_UID12, CONNECTIVITY_INTERNAL));

    }

    private boolean wouldBeCarryoverPackage(String partition, int targetSdkVersion, int uid) {
        final PackageInfo packageInfo = packageInfoWithPermissions(
                REQUESTED_PERMISSION_GRANTED, new String[] {}, partition);
        packageInfo.applicationInfo.targetSdkVersion = targetSdkVersion;
        packageInfo.applicationInfo.uid = uid;
        return mPermissionMonitor.isCarryoverPackage(packageInfo.applicationInfo);
    }

    @Test
    public void testIsCarryoverPackage() {
        doReturn(VERSION_P).when(mDeps).getDeviceFirstSdkInt();
        assertTrue(wouldBeCarryoverPackage(PARTITION_SYSTEM, VERSION_P, SYSTEM_UID));
        assertTrue(wouldBeCarryoverPackage(PARTITION_VENDOR, VERSION_P, SYSTEM_UID));
        assertFalse(wouldBeCarryoverPackage(PARTITION_SYSTEM, VERSION_P, MOCK_UID11));
        assertTrue(wouldBeCarryoverPackage(PARTITION_VENDOR, VERSION_P, MOCK_UID11));
        assertTrue(wouldBeCarryoverPackage(PARTITION_SYSTEM, VERSION_Q, SYSTEM_UID));
        assertTrue(wouldBeCarryoverPackage(PARTITION_VENDOR, VERSION_Q, SYSTEM_UID));
        assertFalse(wouldBeCarryoverPackage(PARTITION_SYSTEM, VERSION_Q, MOCK_UID11));
        assertFalse(wouldBeCarryoverPackage(PARTITION_VENDOR, VERSION_Q, MOCK_UID11));

        doReturn(VERSION_Q).when(mDeps).getDeviceFirstSdkInt();
        assertFalse(wouldBeCarryoverPackage(PARTITION_SYSTEM, VERSION_P, SYSTEM_UID));
        assertTrue(wouldBeCarryoverPackage(PARTITION_VENDOR, VERSION_P, SYSTEM_UID));
        assertFalse(wouldBeCarryoverPackage(PARTITION_SYSTEM, VERSION_P, MOCK_UID11));
        assertTrue(wouldBeCarryoverPackage(PARTITION_VENDOR, VERSION_P, MOCK_UID11));
        assertFalse(wouldBeCarryoverPackage(PARTITION_SYSTEM, VERSION_Q, SYSTEM_UID));
        assertFalse(wouldBeCarryoverPackage(PARTITION_VENDOR, VERSION_Q, SYSTEM_UID));
        assertFalse(wouldBeCarryoverPackage(PARTITION_SYSTEM, VERSION_Q, MOCK_UID11));
        assertFalse(wouldBeCarryoverPackage(PARTITION_VENDOR, VERSION_Q, MOCK_UID11));

        assertFalse(wouldBeCarryoverPackage(PARTITION_OEM, VERSION_Q, SYSTEM_UID));
        assertFalse(wouldBeCarryoverPackage(PARTITION_PRODUCT, VERSION_Q, SYSTEM_UID));
        assertFalse(wouldBeCarryoverPackage(PARTITION_OEM, VERSION_Q, MOCK_UID11));
        assertFalse(wouldBeCarryoverPackage(PARTITION_PRODUCT, VERSION_Q, MOCK_UID11));
    }

    private boolean wouldBeUidAllowedOnRestrictedNetworks(int uid) {
        final ApplicationInfo applicationInfo = new ApplicationInfo();
        applicationInfo.uid = uid;
        return mPermissionMonitor.isUidAllowedOnRestrictedNetworks(applicationInfo);
    }

    @Test
    public void testIsAppAllowedOnRestrictedNetworks() {
        mPermissionMonitor.updateUidsAllowedOnRestrictedNetworks(Set.of());
        assertFalse(wouldBeUidAllowedOnRestrictedNetworks(MOCK_UID11));
        assertFalse(wouldBeUidAllowedOnRestrictedNetworks(MOCK_UID12));

        mPermissionMonitor.updateUidsAllowedOnRestrictedNetworks(Set.of(MOCK_UID11));
        assertTrue(wouldBeUidAllowedOnRestrictedNetworks(MOCK_UID11));
        assertFalse(wouldBeUidAllowedOnRestrictedNetworks(MOCK_UID12));

        mPermissionMonitor.updateUidsAllowedOnRestrictedNetworks(Set.of(MOCK_UID12));
        assertFalse(wouldBeUidAllowedOnRestrictedNetworks(MOCK_UID11));
        assertTrue(wouldBeUidAllowedOnRestrictedNetworks(MOCK_UID12));

        mPermissionMonitor.updateUidsAllowedOnRestrictedNetworks(Set.of(123));
        assertFalse(wouldBeUidAllowedOnRestrictedNetworks(MOCK_UID11));
        assertFalse(wouldBeUidAllowedOnRestrictedNetworks(MOCK_UID12));
    }

    private void assertBackgroundPermission(boolean hasPermission, String name, int uid,
            String... permissions) throws Exception {
        addPackage(name, uid, permissions);
        assertEquals(hasPermission, mPermissionMonitor.hasUseBackgroundNetworksPermission(uid));
        if (hasSdkSandbox(uid)) {
            final int sdkSandboxUid = mProcessShim.toSdkSandboxUid(uid);
            assertEquals(hasPermission,
                    mPermissionMonitor.hasUseBackgroundNetworksPermission(sdkSandboxUid));
        }
    }

    @Test
    public void testHasUseBackgroundNetworksPermission() throws Exception {
        assertFalse(mPermissionMonitor.hasUseBackgroundNetworksPermission(SYSTEM_UID));
        assertBackgroundPermission(false, SYSTEM_PACKAGE1, SYSTEM_UID);
        assertBackgroundPermission(false, SYSTEM_PACKAGE1, SYSTEM_UID, CONNECTIVITY_INTERNAL);
        assertBackgroundPermission(true, SYSTEM_PACKAGE1, SYSTEM_UID, CHANGE_NETWORK_STATE);
        assertBackgroundPermission(true, SYSTEM_PACKAGE1, SYSTEM_UID, NETWORK_STACK);

        assertFalse(mPermissionMonitor.hasUseBackgroundNetworksPermission(MOCK_UID11));
        assertBackgroundPermission(false, MOCK_PACKAGE1, MOCK_UID11);
        assertBackgroundPermission(true, MOCK_PACKAGE1, MOCK_UID11,
                CONNECTIVITY_USE_RESTRICTED_NETWORKS);

        assertFalse(mPermissionMonitor.hasUseBackgroundNetworksPermission(MOCK_UID12));
        assertBackgroundPermission(false, MOCK_PACKAGE2, MOCK_UID12);
        assertBackgroundPermission(false, MOCK_PACKAGE2, MOCK_UID12,
                CONNECTIVITY_INTERNAL);
        assertBackgroundPermission(true, MOCK_PACKAGE2, MOCK_UID12, NETWORK_STACK);
    }

    private class BpfMapMonitor {
        private final SparseIntArray mAppIdsTrafficPermission = new SparseIntArray();
        private static final int DOES_NOT_EXIST = -2;

        BpfMapMonitor(BpfNetMaps mockBpfmap) throws Exception {
            // Add hook to verify and track result of trafficSetNetPerm.
            doAnswer((InvocationOnMock invocation) -> {
                final Object[] args = invocation.getArguments();
                final int permission = (int) args[0];
                for (final int appId : (int[]) args[1]) {
                    mAppIdsTrafficPermission.put(appId, permission);
                }
                return null;
            }).when(mockBpfmap).setNetPermForUids(anyInt(), any(int[].class));
        }

        public void expectTrafficPerm(int permission, Integer... appIds) {
            for (final int appId : appIds) {
                if (mAppIdsTrafficPermission.get(appId, DOES_NOT_EXIST) == DOES_NOT_EXIST) {
                    fail("appId " + appId + " does not exist.");
                }
                if (mAppIdsTrafficPermission.get(appId) != permission) {
                    fail("appId " + appId + " has wrong permission: "
                            + mAppIdsTrafficPermission.get(appId));
                }
                if (hasSdkSandbox(appId)) {
                    int sdkSandboxAppId = mProcessShim.toSdkSandboxUid(appId);
                    if (mAppIdsTrafficPermission.get(sdkSandboxAppId, DOES_NOT_EXIST)
                            == DOES_NOT_EXIST) {
                        fail("SDK sandbox appId " + sdkSandboxAppId + " does not exist.");
                    }
                    if (mAppIdsTrafficPermission.get(sdkSandboxAppId) != permission) {
                        fail("SDK sandbox appId " + sdkSandboxAppId + " has wrong permission: "
                                + mAppIdsTrafficPermission.get(sdkSandboxAppId));
                    }
                }
            }
        }
    }

    private class NetdMonitor {
        private final SparseIntArray mUidsNetworkPermission = new SparseIntArray();
        private static final int DOES_NOT_EXIST = -2;

        NetdMonitor(INetd mockNetd) throws Exception {
            // Add hook to verify and track result of networkSetPermission.
            doAnswer((InvocationOnMock invocation) -> {
                final Object[] args = invocation.getArguments();
                final int permission = (int) args[0];
                for (final int uid : (int[]) args[1]) {
                    // TODO: Currently, permission monitor will send duplicate commands for each uid
                    // corresponding to each user. Need to fix that and uncomment below test.
                    // if (mApps.containsKey(uid) && mApps.get(uid) == isSystem) {
                    //     fail("uid " + uid + " is already set to " + isSystem);
                    // }
                    mUidsNetworkPermission.put(uid, permission);
                }
                return null;
            }).when(mockNetd).networkSetPermissionForUser(anyInt(), any(int[].class));

            // Add hook to verify and track result of networkClearPermission.
            doAnswer((InvocationOnMock invocation) -> {
                final Object[] args = invocation.getArguments();
                for (final int uid : (int[]) args[0]) {
                    // TODO: Currently, permission monitor will send duplicate commands for each uid
                    // corresponding to each user. Need to fix that and uncomment below test.
                    // if (!mApps.containsKey(uid)) {
                    //     fail("uid " + uid + " does not exist.");
                    // }
                    mUidsNetworkPermission.delete(uid);
                }
                return null;
            }).when(mockNetd).networkClearPermissionForUser(any(int[].class));
        }

        public void expectNetworkPerm(int permission, UserHandle[] users, int... appIds) {
            for (final UserHandle user : users) {
                for (final int appId : appIds) {
                    final int uid = user.getUid(appId);
                    if (mUidsNetworkPermission.get(uid, DOES_NOT_EXIST) == DOES_NOT_EXIST) {
                        fail("uid " + uid + " does not exist.");
                    }
                    if (mUidsNetworkPermission.get(uid) != permission) {
                        fail("uid " + uid + " has wrong permission: " +  permission);
                    }
                    if (hasSdkSandbox(uid)) {
                        int sdkSandboxUid = mProcessShim.toSdkSandboxUid(uid);
                        if (mUidsNetworkPermission.get(sdkSandboxUid, DOES_NOT_EXIST)
                                == DOES_NOT_EXIST) {
                            fail("SDK sandbox uid " + uid + " does not exist.");
                        }
                        if (mUidsNetworkPermission.get(sdkSandboxUid) != permission) {
                            fail("SDK sandbox uid " + uid + " has wrong permission: "
                                    + permission);
                        }
                    }
                }
            }
        }

        public void expectNoNetworkPerm(UserHandle[] users, int... appIds) {
            for (final UserHandle user : users) {
                for (final int appId : appIds) {
                    final int uid = user.getUid(appId);
                    if (mUidsNetworkPermission.get(uid, DOES_NOT_EXIST) != DOES_NOT_EXIST) {
                        fail("uid " + uid + " has listed permissions, expected none.");
                    }
                    if (hasSdkSandbox(uid)) {
                        int sdkSandboxUid = mProcessShim.toSdkSandboxUid(uid);
                        if (mUidsNetworkPermission.get(sdkSandboxUid, DOES_NOT_EXIST)
                                != DOES_NOT_EXIST) {
                            fail("SDK sandbox uid " + sdkSandboxUid
                                    + " has listed permissions, expected none.");
                        }
                    }
                }
            }
        }
    }

    @Test
    public void testUserAndPackageAddRemove() throws Exception {
        // MOCK_UID11: MOCK_PACKAGE1 only has network permission.
        // SYSTEM_APP_UID11: SYSTEM_PACKAGE1 has system permission.
        // SYSTEM_APP_UID11: SYSTEM_PACKAGE2 only has network permission.
        buildAndMockPackageInfoWithPermissions(MOCK_PACKAGE1, MOCK_UID11, CHANGE_NETWORK_STATE);
        buildAndMockPackageInfoWithPermissions(SYSTEM_PACKAGE1, SYSTEM_APP_UID11,
                CONNECTIVITY_USE_RESTRICTED_NETWORKS);
        buildAndMockPackageInfoWithPermissions(SYSTEM_PACKAGE2, SYSTEM_APP_UID11,
                CHANGE_NETWORK_STATE);

        // Add user MOCK_USER1.
        onUserAdded(MOCK_USER1);
        // Add SYSTEM_PACKAGE2, expect only have network permission.
        addPackageForUsers(new UserHandle[]{MOCK_USER1}, SYSTEM_PACKAGE2, SYSTEM_APPID1);
        mNetdMonitor.expectNetworkPerm(PERMISSION_NETWORK, new UserHandle[]{MOCK_USER1},
                SYSTEM_APPID1);

        // Add SYSTEM_PACKAGE1, expect permission upgrade.
        addPackageForUsers(new UserHandle[]{MOCK_USER1}, SYSTEM_PACKAGE1, SYSTEM_APPID1);
        mNetdMonitor.expectNetworkPerm(PERMISSION_SYSTEM, new UserHandle[]{MOCK_USER1},
                SYSTEM_APPID1);

        final List<PackageInfo> pkgs = List.of(
                buildPackageInfo(SYSTEM_PACKAGE1, SYSTEM_APP_UID21,
                        CONNECTIVITY_USE_RESTRICTED_NETWORKS),
                buildPackageInfo(SYSTEM_PACKAGE2, SYSTEM_APP_UID21, CHANGE_NETWORK_STATE));
        doReturn(pkgs).when(mPackageManager).getInstalledPackagesAsUser(eq(GET_PERMISSIONS),
                eq(MOCK_USER_ID2));
        // Add user MOCK_USER2.
        onUserAdded(MOCK_USER2);
        mNetdMonitor.expectNetworkPerm(PERMISSION_SYSTEM, new UserHandle[]{MOCK_USER1, MOCK_USER2},
                SYSTEM_APPID1);

        // Remove SYSTEM_PACKAGE2, expect keep system permission.
        doReturn(new String[]{SYSTEM_PACKAGE1}).when(mPackageManager)
                .getPackagesForUid(intThat(uid -> UserHandle.getAppId(uid) == SYSTEM_APPID1));
        removePackageForUsers(new UserHandle[]{MOCK_USER1, MOCK_USER2},
                SYSTEM_PACKAGE2, SYSTEM_APPID1);
        mNetdMonitor.expectNetworkPerm(PERMISSION_SYSTEM, new UserHandle[]{MOCK_USER1, MOCK_USER2},
                SYSTEM_APPID1);

        // Add SYSTEM_PACKAGE2, expect keep system permission.
        addPackageForUsers(new UserHandle[]{MOCK_USER1, MOCK_USER2}, SYSTEM_PACKAGE2,
                SYSTEM_APPID1);
        mNetdMonitor.expectNetworkPerm(PERMISSION_SYSTEM, new UserHandle[]{MOCK_USER1, MOCK_USER2},
                SYSTEM_APPID1);

        // Add MOCK_PACKAGE1
        buildAndMockPackageInfoWithPermissions(MOCK_PACKAGE1, MOCK_UID21, CHANGE_NETWORK_STATE);
        addPackageForUsers(new UserHandle[]{MOCK_USER1, MOCK_USER2}, MOCK_PACKAGE1, MOCK_APPID1);
        mNetdMonitor.expectNetworkPerm(PERMISSION_SYSTEM, new UserHandle[]{MOCK_USER1, MOCK_USER2},
                SYSTEM_APPID1);
        mNetdMonitor.expectNetworkPerm(PERMISSION_NETWORK, new UserHandle[]{MOCK_USER1, MOCK_USER2},
                MOCK_APPID1);

        // Remove MOCK_PACKAGE1, expect no permission left for all user.
        doReturn(new String[]{}).when(mPackageManager)
                .getPackagesForUid(intThat(uid -> UserHandle.getAppId(uid) == MOCK_APPID1));
        removePackageForUsers(new UserHandle[]{MOCK_USER1, MOCK_USER2}, MOCK_PACKAGE1, MOCK_APPID1);
        mNetdMonitor.expectNoNetworkPerm(new UserHandle[]{MOCK_USER1, MOCK_USER2}, MOCK_APPID1);

        // Remove SYSTEM_PACKAGE1, expect permission downgrade.
        when(mPackageManager.getPackagesForUid(
                intThat(uid -> UserHandle.getAppId(uid) == SYSTEM_APPID1)))
                .thenReturn(new String[]{SYSTEM_PACKAGE2});
        removePackageForUsers(new UserHandle[]{MOCK_USER1, MOCK_USER2},
                SYSTEM_PACKAGE1, SYSTEM_APPID1);
        mNetdMonitor.expectNetworkPerm(PERMISSION_NETWORK, new UserHandle[]{MOCK_USER1, MOCK_USER2},
                SYSTEM_APPID1);

        onUserRemoved(MOCK_USER1);
        mNetdMonitor.expectNetworkPerm(PERMISSION_NETWORK, new UserHandle[]{MOCK_USER2},
                SYSTEM_APPID1);
        mNetdMonitor.expectNoNetworkPerm(new UserHandle[]{MOCK_USER1}, SYSTEM_APPID1);

        // Remove all packages, expect no permission left.
        when(mPackageManager.getPackagesForUid(
                intThat(uid -> UserHandle.getAppId(uid) == SYSTEM_APPID1)))
                .thenReturn(new String[]{});
        removePackageForUsers(new UserHandle[]{MOCK_USER2}, SYSTEM_PACKAGE2, SYSTEM_APPID1);
        mNetdMonitor.expectNoNetworkPerm(new UserHandle[]{MOCK_USER1, MOCK_USER2}, SYSTEM_APPID1,
                MOCK_APPID1);

        // Remove last user, expect no permission change.
        onUserRemoved(MOCK_USER2);
        mNetdMonitor.expectNoNetworkPerm(new UserHandle[]{MOCK_USER1, MOCK_USER2}, SYSTEM_APPID1,
                MOCK_APPID1);
    }

    private void doTestUidFilteringDuringVpnConnectDisconnectAndUidUpdates(@Nullable String ifName)
            throws Exception {
        doReturn(List.of(
                buildPackageInfo(SYSTEM_PACKAGE1, SYSTEM_APP_UID11, CHANGE_NETWORK_STATE,
                        CONNECTIVITY_USE_RESTRICTED_NETWORKS),
                buildPackageInfo(MOCK_PACKAGE1, MOCK_UID11),
                buildPackageInfo(MOCK_PACKAGE2, MOCK_UID12),
                buildPackageInfo(SYSTEM_PACKAGE2, VPN_UID)))
                .when(mPackageManager).getInstalledPackagesAsUser(eq(GET_PERMISSIONS), anyInt());
        buildAndMockPackageInfoWithPermissions(MOCK_PACKAGE1, MOCK_UID11);
        startMonitoring();
        // Every app on user 0 except MOCK_UID12 is subject to the VPN.
        final Set<UidRange> vpnRange1 = Set.of(
                new UidRange(0, MOCK_UID12 - 1),
                new UidRange(MOCK_UID12 + 1, UserHandle.PER_USER_RANGE - 1));
        final Set<UidRange> vpnRange2 = Set.of(new UidRange(MOCK_UID12, MOCK_UID12));

        // When VPN is connected, expect a rule to be set up for user app MOCK_UID11
        mPermissionMonitor.onVpnUidRangesAdded(ifName, vpnRange1, VPN_UID);
        verify(mBpfNetMaps).addUidInterfaceRules(eq(ifName), aryEq(new int[]{MOCK_UID11}));

        reset(mBpfNetMaps);

        // When MOCK_UID11 package is uninstalled and reinstalled, expect Netd to be updated
        onPackageRemoved(MOCK_PACKAGE1, MOCK_UID11);
        verify(mBpfNetMaps).removeUidInterfaceRules(aryEq(new int[]{MOCK_UID11}));
        onPackageAdded(MOCK_PACKAGE1, MOCK_UID11);
        verify(mBpfNetMaps).addUidInterfaceRules(eq(ifName), aryEq(new int[]{MOCK_UID11}));

        reset(mBpfNetMaps);

        // During VPN uid update (vpnRange1 -> vpnRange2), ConnectivityService first deletes the
        // old UID rules then adds the new ones. Expect netd to be updated
        mPermissionMonitor.onVpnUidRangesRemoved(ifName, vpnRange1, VPN_UID);
        verify(mBpfNetMaps).removeUidInterfaceRules(aryEq(new int[] {MOCK_UID11}));
        mPermissionMonitor.onVpnUidRangesAdded(ifName, vpnRange2, VPN_UID);
        verify(mBpfNetMaps).addUidInterfaceRules(eq(ifName), aryEq(new int[]{MOCK_UID12}));

        reset(mBpfNetMaps);

        // When VPN is disconnected, expect rules to be torn down
        mPermissionMonitor.onVpnUidRangesRemoved(ifName, vpnRange2, VPN_UID);
        verify(mBpfNetMaps).removeUidInterfaceRules(aryEq(new int[] {MOCK_UID12}));
    }

    @Test
    public void testUidFilteringDuringVpnConnectDisconnectAndUidUpdates() throws Exception {
        doTestUidFilteringDuringVpnConnectDisconnectAndUidUpdates("tun0");
    }

    @Test
    public void testUidFilteringDuringVpnConnectDisconnectAndUidUpdatesWithWildcard()
            throws Exception {
        doTestUidFilteringDuringVpnConnectDisconnectAndUidUpdates(null /* ifName */);
    }

    private void doTestUidFilteringDuringPackageInstallAndUninstall(@Nullable String ifName) throws
            Exception {
        doReturn(List.of(
                buildPackageInfo(SYSTEM_PACKAGE1, SYSTEM_APP_UID11, CHANGE_NETWORK_STATE,
                        NETWORK_STACK, CONNECTIVITY_USE_RESTRICTED_NETWORKS),
                buildPackageInfo(SYSTEM_PACKAGE2, VPN_UID)))
                .when(mPackageManager).getInstalledPackagesAsUser(eq(GET_PERMISSIONS), anyInt());
        buildAndMockPackageInfoWithPermissions(MOCK_PACKAGE1, MOCK_UID11);
        doReturn(List.of(MOCK_USER1, MOCK_USER2)).when(mUserManager).getUserHandles(eq(true));

        startMonitoring();
        final Set<UidRange> vpnRange = Set.of(UidRange.createForUser(MOCK_USER1),
                UidRange.createForUser(MOCK_USER2));
        mPermissionMonitor.onVpnUidRangesAdded(ifName, vpnRange, VPN_UID);

        // Newly-installed package should have uid rules added
        addPackageForUsers(new UserHandle[]{MOCK_USER1, MOCK_USER2}, MOCK_PACKAGE1, MOCK_APPID1);
        verify(mBpfNetMaps).addUidInterfaceRules(eq(ifName), aryEq(new int[]{MOCK_UID11}));
        verify(mBpfNetMaps).addUidInterfaceRules(eq(ifName), aryEq(new int[]{MOCK_UID21}));

        // Removed package should have its uid rules removed
        onPackageRemoved(MOCK_PACKAGE1, MOCK_UID11);
        verify(mBpfNetMaps).removeUidInterfaceRules(aryEq(new int[]{MOCK_UID11}));
        verify(mBpfNetMaps, never()).removeUidInterfaceRules(aryEq(new int[]{MOCK_UID21}));
    }

    @Test
    public void testUidFilteringDuringPackageInstallAndUninstall() throws Exception {
        doTestUidFilteringDuringPackageInstallAndUninstall("tun0");
    }

    @Test
    public void testUidFilteringDuringPackageInstallAndUninstallWithWildcard() throws Exception {
        doTestUidFilteringDuringPackageInstallAndUninstall(null /* ifName */);
    }

    @Test
    public void testLockdownUidFilteringWithLockdownEnableDisable() {
        doReturn(List.of(
                buildPackageInfo(SYSTEM_PACKAGE1, SYSTEM_APP_UID11, CHANGE_NETWORK_STATE,
                        CONNECTIVITY_USE_RESTRICTED_NETWORKS),
                buildPackageInfo(MOCK_PACKAGE1, MOCK_UID11),
                buildPackageInfo(MOCK_PACKAGE2, MOCK_UID12),
                buildPackageInfo(SYSTEM_PACKAGE2, VPN_UID)))
                .when(mPackageManager).getInstalledPackagesAsUser(eq(GET_PERMISSIONS), anyInt());
        startMonitoring();
        // Every app on user 0 except MOCK_UID12 is subject to the VPN.
        final UidRange[] lockdownRange = {
                new UidRange(0, MOCK_UID12 - 1),
                new UidRange(MOCK_UID12 + 1, UserHandle.PER_USER_RANGE - 1)
        };

        // Add Lockdown uid range, expect a rule to be set up for MOCK_UID11 and VPN_UID
        mPermissionMonitor.updateVpnLockdownUidRanges(true /* add */, lockdownRange);
        verify(mBpfNetMaps, times(2)).updateUidLockdownRule(anyInt(), eq(true) /* add */);
        verify(mBpfNetMaps).updateUidLockdownRule(MOCK_UID11, true /* add */);
        verify(mBpfNetMaps).updateUidLockdownRule(VPN_UID, true /* add */);

        reset(mBpfNetMaps);

        // Remove Lockdown uid range, expect rules to be torn down
        mPermissionMonitor.updateVpnLockdownUidRanges(false /* add */, lockdownRange);
        verify(mBpfNetMaps, times(2)).updateUidLockdownRule(anyInt(), eq(false) /* add */);
        verify(mBpfNetMaps).updateUidLockdownRule(MOCK_UID11, false /* add */);
        verify(mBpfNetMaps).updateUidLockdownRule(VPN_UID, false /* add */);
    }

    @Test
    public void testLockdownUidFilteringWithLockdownEnableDisableWithMultiAdd() {
        doReturn(List.of(
                buildPackageInfo(SYSTEM_PACKAGE1, SYSTEM_APP_UID11, CHANGE_NETWORK_STATE,
                        CONNECTIVITY_USE_RESTRICTED_NETWORKS),
                buildPackageInfo(MOCK_PACKAGE1, MOCK_UID11),
                buildPackageInfo(SYSTEM_PACKAGE2, VPN_UID)))
                .when(mPackageManager).getInstalledPackagesAsUser(eq(GET_PERMISSIONS), anyInt());
        startMonitoring();
        // MOCK_UID11 is subject to the VPN.
        final UidRange range = new UidRange(MOCK_UID11, MOCK_UID11);
        final UidRange[] lockdownRange = {range};

        // Add Lockdown uid range at 1st time, expect a rule to be set up
        mPermissionMonitor.updateVpnLockdownUidRanges(true /* add */, lockdownRange);
        verify(mBpfNetMaps).updateUidLockdownRule(anyInt(), eq(true) /* add */);
        verify(mBpfNetMaps).updateUidLockdownRule(MOCK_UID11, true /* add */);

        reset(mBpfNetMaps);

        // Add Lockdown uid range at 2nd time, expect a rule not to be set up because the uid
        // already has the rule
        mPermissionMonitor.updateVpnLockdownUidRanges(true /* add */, lockdownRange);
        verify(mBpfNetMaps, never()).updateUidLockdownRule(anyInt(),  anyBoolean());

        reset(mBpfNetMaps);

        // Remove Lockdown uid range at 1st time, expect a rule not to be torn down because we added
        // the range 2 times.
        mPermissionMonitor.updateVpnLockdownUidRanges(false /* add */, lockdownRange);
        verify(mBpfNetMaps, never()).updateUidLockdownRule(anyInt(),  anyBoolean());

        reset(mBpfNetMaps);

        // Remove Lockdown uid range at 2nd time, expect a rule to be torn down because we added
        // twice and we removed twice.
        mPermissionMonitor.updateVpnLockdownUidRanges(false /* add */, lockdownRange);
        verify(mBpfNetMaps).updateUidLockdownRule(anyInt(), eq(false) /* add */);
        verify(mBpfNetMaps).updateUidLockdownRule(MOCK_UID11, false /* add */);
    }

    @Test
    public void testLockdownUidFilteringWithLockdownEnableDisableWithMultiAddAndOverlap() {
        doReturn(List.of(buildPackageInfo(SYSTEM_PACKAGE1, SYSTEM_APP_UID11, CHANGE_NETWORK_STATE,
                        CONNECTIVITY_USE_RESTRICTED_NETWORKS),
                buildPackageInfo(MOCK_PACKAGE1, MOCK_UID13),
                buildPackageInfo(MOCK_PACKAGE2, MOCK_UID14),
                buildPackageInfo(SYSTEM_PACKAGE2, VPN_UID)))
                .when(mPackageManager).getInstalledPackagesAsUser(eq(GET_PERMISSIONS), anyInt());
        startMonitoring();
        // MOCK_UID13 is subject to the VPN.
        final UidRange range1 = new UidRange(MOCK_UID13, MOCK_UID13);
        final UidRange[] lockdownRange1 = {range1};

        // Add Lockdown uid range at 1st time, expect a rule to be set up
        mPermissionMonitor.updateVpnLockdownUidRanges(true /* add */, lockdownRange1);
        verify(mBpfNetMaps).updateUidLockdownRule(anyInt(), eq(true) /* add */);
        verify(mBpfNetMaps).updateUidLockdownRule(MOCK_UID13, true /* add */);

        reset(mBpfNetMaps);

        // MOCK_UID13 and MOCK_UID14 are sequential and subject to the VPN in a separate range.
        final UidRange range2 = new UidRange(MOCK_UID13, MOCK_UID14);
        final UidRange[] lockdownRange2 = {range2};

        // Add overlapping multiple-UID range. Rule may be set again, which is functionally
        // a no-op, so it is fine.
        mPermissionMonitor.updateVpnLockdownUidRanges(true /* add */, lockdownRange2);
        verify(mBpfNetMaps, atLeast(1)).updateUidLockdownRule(anyInt(), eq(true) /* add */);
        verify(mBpfNetMaps).updateUidLockdownRule(MOCK_UID14, true /* add */);

        reset(mBpfNetMaps);

        // Remove the multiple-UID range. UID from first rule should not be removed.
        mPermissionMonitor.updateVpnLockdownUidRanges(false /* false */, lockdownRange2);
        verify(mBpfNetMaps, times(1)).updateUidLockdownRule(anyInt(), eq(false) /* add */);
        verify(mBpfNetMaps).updateUidLockdownRule(MOCK_UID14, false /* add */);

        reset(mBpfNetMaps);

        // Add the multiple-UID range back again to be able to test removing the first range, too.
        mPermissionMonitor.updateVpnLockdownUidRanges(true /* add */, lockdownRange2);
        verify(mBpfNetMaps, atLeast(1)).updateUidLockdownRule(anyInt(), eq(true) /* add */);
        verify(mBpfNetMaps).updateUidLockdownRule(MOCK_UID14, true /* add */);

        reset(mBpfNetMaps);

        // Remove the single-UID range. The rule for MOCK_UID11 should not change because it is
        // still covered by the second, multiple-UID range rule.
        mPermissionMonitor.updateVpnLockdownUidRanges(false /* false */, lockdownRange1);
        verify(mBpfNetMaps, never()).updateUidLockdownRule(anyInt(),  anyBoolean());

        reset(mBpfNetMaps);

        // Remove the multiple-UID range. Expect both UID rules to be torn down.
        mPermissionMonitor.updateVpnLockdownUidRanges(false /* false */, lockdownRange2);
        verify(mBpfNetMaps, times(2)).updateUidLockdownRule(anyInt(), eq(false) /* add */);
        verify(mBpfNetMaps).updateUidLockdownRule(MOCK_UID13, false /* add */);
        verify(mBpfNetMaps).updateUidLockdownRule(MOCK_UID14, false /* add */);
    }

    @Test
    public void testLockdownUidFilteringWithLockdownEnableDisableWithDuplicates() {
        doReturn(List.of(
                buildPackageInfo(SYSTEM_PACKAGE1, SYSTEM_APP_UID11, CHANGE_NETWORK_STATE,
                        CONNECTIVITY_USE_RESTRICTED_NETWORKS),
                buildPackageInfo(MOCK_PACKAGE1, MOCK_UID11),
                buildPackageInfo(SYSTEM_PACKAGE2, VPN_UID)))
                .when(mPackageManager).getInstalledPackagesAsUser(eq(GET_PERMISSIONS), anyInt());
        startMonitoring();
        // MOCK_UID11 is subject to the VPN.
        final UidRange range = new UidRange(MOCK_UID11, MOCK_UID11);
        final UidRange[] lockdownRangeDuplicates = {range, range};
        final UidRange[] lockdownRange = {range};

        // Add Lockdown uid ranges which contains duplicated uid ranges
        mPermissionMonitor.updateVpnLockdownUidRanges(true /* add */, lockdownRangeDuplicates);
        verify(mBpfNetMaps).updateUidLockdownRule(anyInt(), eq(true) /* add */);
        verify(mBpfNetMaps).updateUidLockdownRule(MOCK_UID11, true /* add */);

        reset(mBpfNetMaps);

        // Remove Lockdown uid range at 1st time, expect a rule not to be torn down because uid
        // ranges we added contains duplicated uid ranges.
        mPermissionMonitor.updateVpnLockdownUidRanges(false /* add */, lockdownRange);
        verify(mBpfNetMaps, never()).updateUidLockdownRule(anyInt(), anyBoolean());

        reset(mBpfNetMaps);

        // Remove Lockdown uid range at 2nd time, expect a rule to be torn down.
        mPermissionMonitor.updateVpnLockdownUidRanges(false /* add */, lockdownRange);
        verify(mBpfNetMaps).updateUidLockdownRule(anyInt(), eq(false) /* add */);
        verify(mBpfNetMaps).updateUidLockdownRule(MOCK_UID11, false /* add */);
    }

    @Test
    public void testLockdownUidFilteringWithInstallAndUnInstall() {
        doReturn(List.of(
                buildPackageInfo(SYSTEM_PACKAGE1, SYSTEM_APP_UID11, CHANGE_NETWORK_STATE,
                        NETWORK_STACK, CONNECTIVITY_USE_RESTRICTED_NETWORKS),
                buildPackageInfo(SYSTEM_PACKAGE2, VPN_UID)))
                .when(mPackageManager).getInstalledPackagesAsUser(eq(GET_PERMISSIONS), anyInt());
        doReturn(List.of(MOCK_USER1, MOCK_USER2)).when(mUserManager).getUserHandles(eq(true));

        startMonitoring();
        final UidRange[] lockdownRange = {
                UidRange.createForUser(MOCK_USER1),
                UidRange.createForUser(MOCK_USER2)
        };
        mPermissionMonitor.updateVpnLockdownUidRanges(true /* add */, lockdownRange);

        reset(mBpfNetMaps);

        // Installing package should add Lockdown rules
        addPackageForUsers(new UserHandle[]{MOCK_USER1, MOCK_USER2}, MOCK_PACKAGE1, MOCK_APPID1);
        verify(mBpfNetMaps, times(2)).updateUidLockdownRule(anyInt(), eq(true) /* add */);
        verify(mBpfNetMaps).updateUidLockdownRule(MOCK_UID11, true /* add */);
        verify(mBpfNetMaps).updateUidLockdownRule(MOCK_UID21, true /* add */);

        reset(mBpfNetMaps);

        // Uninstalling package should remove Lockdown rules
        onPackageRemoved(MOCK_PACKAGE1, MOCK_UID11);
        verify(mBpfNetMaps).updateUidLockdownRule(anyInt(), eq(false) /* add */);
        verify(mBpfNetMaps).updateUidLockdownRule(MOCK_UID11, false /* add */);
    }

    // Normal package add/remove operations will trigger multiple intent for uids corresponding to
    // each user. To simulate generic package operations, the onPackageAdded/Removed will need to be
    // called multiple times with the uid corresponding to each user.
    private void addPackageForUsers(UserHandle[] users, String packageName, int appId) {
        for (final UserHandle user : users) {
            processOnHandlerThread(() ->
                    mPermissionMonitor.onPackageAdded(packageName, user.getUid(appId)));
        }
    }

    private void removePackageForUsers(UserHandle[] users, String packageName, int appId) {
        for (final UserHandle user : users) {
            processOnHandlerThread(() ->
                    mPermissionMonitor.onPackageRemoved(packageName, user.getUid(appId)));
        }
    }

    @Test
    public void testPackagePermissionUpdate() throws Exception {
        // MOCK_APPID1: MOCK_PACKAGE1 only has internet permission.
        // MOCK_APPID2: MOCK_PACKAGE2 does not have any permission.
        // SYSTEM_APPID1: SYSTEM_PACKAGE1 has internet permission and update device stats permission
        // SYSTEM_APPID2: SYSTEM_PACKAGE2 has only update device stats permission.
        // The SDK sandbox APPIDs must have permissions mirroring the app
        SparseIntArray netdPermissionsAppIds = new SparseIntArray();
        netdPermissionsAppIds.put(MOCK_APPID1, PERMISSION_INTERNET);
        if (hasSdkSandbox(MOCK_APPID1)) {
            netdPermissionsAppIds.put(mProcessShim.toSdkSandboxUid(MOCK_APPID1),
                    PERMISSION_INTERNET);
        }
        netdPermissionsAppIds.put(MOCK_APPID2, PERMISSION_NONE);
        if (hasSdkSandbox(MOCK_APPID2)) {
            netdPermissionsAppIds.put(mProcessShim.toSdkSandboxUid(MOCK_APPID2),
                    PERMISSION_NONE);
        }
        netdPermissionsAppIds.put(SYSTEM_APPID1, PERMISSION_TRAFFIC_ALL);
        netdPermissionsAppIds.put(SYSTEM_APPID2, PERMISSION_UPDATE_DEVICE_STATS);

        // Send the permission information to netd, expect permission updated.
        sendAppIdsTrafficPermission(netdPermissionsAppIds);

        mBpfMapMonitor.expectTrafficPerm(PERMISSION_INTERNET, MOCK_APPID1);
        mBpfMapMonitor.expectTrafficPerm(PERMISSION_NONE, MOCK_APPID2);
        mBpfMapMonitor.expectTrafficPerm(PERMISSION_TRAFFIC_ALL, SYSTEM_APPID1);
        mBpfMapMonitor.expectTrafficPerm(PERMISSION_UPDATE_DEVICE_STATS, SYSTEM_APPID2);

        // Update permission of MOCK_APPID1, expect new permission show up.
        sendPackagePermissionsForAppId(MOCK_APPID1, PERMISSION_TRAFFIC_ALL);
        mBpfMapMonitor.expectTrafficPerm(PERMISSION_TRAFFIC_ALL, MOCK_APPID1);

        // Change permissions of SYSTEM_APPID2, expect new permission show up and old permission
        // revoked.
        sendPackagePermissionsForAppId(SYSTEM_APPID2, PERMISSION_INTERNET);
        mBpfMapMonitor.expectTrafficPerm(PERMISSION_INTERNET, SYSTEM_APPID2);

        // Revoke permission from SYSTEM_APPID1, expect no permission stored.
        sendPackagePermissionsForAppId(SYSTEM_APPID1, PERMISSION_NONE);
        mBpfMapMonitor.expectTrafficPerm(PERMISSION_NONE, SYSTEM_APPID1);
    }

    @Test
    public void testPackageInstall() throws Exception {
        addPackage(MOCK_PACKAGE1, MOCK_UID11, INTERNET, UPDATE_DEVICE_STATS);
        mBpfMapMonitor.expectTrafficPerm(PERMISSION_TRAFFIC_ALL, MOCK_APPID1);

        addPackage(MOCK_PACKAGE2, MOCK_UID12, INTERNET);
        mBpfMapMonitor.expectTrafficPerm(PERMISSION_INTERNET, MOCK_APPID2);
    }

    @Test
    public void testPackageInstallSharedUid() throws Exception {
        addPackage(MOCK_PACKAGE1, MOCK_UID11, INTERNET, UPDATE_DEVICE_STATS);
        mBpfMapMonitor.expectTrafficPerm(PERMISSION_TRAFFIC_ALL, MOCK_APPID1);

        // Install another package with the same uid and no permissions should not cause the appId
        // to lose permissions.
        addPackage(MOCK_PACKAGE2, MOCK_UID11);
        mBpfMapMonitor.expectTrafficPerm(PERMISSION_TRAFFIC_ALL, MOCK_APPID1);
    }

    @Test
    public void testPackageUninstallBasic() throws Exception {
        addPackage(MOCK_PACKAGE1, MOCK_UID11, INTERNET, UPDATE_DEVICE_STATS);
        mBpfMapMonitor.expectTrafficPerm(PERMISSION_TRAFFIC_ALL, MOCK_APPID1);

        when(mPackageManager.getPackagesForUid(MOCK_UID11)).thenReturn(new String[]{});
        onPackageRemoved(MOCK_PACKAGE1, MOCK_UID11);
        mBpfMapMonitor.expectTrafficPerm(PERMISSION_UNINSTALLED, MOCK_APPID1);
    }

    @Test
    public void testPackageRemoveThenAdd() throws Exception {
        addPackage(MOCK_PACKAGE1, MOCK_UID11, INTERNET, UPDATE_DEVICE_STATS);
        mBpfMapMonitor.expectTrafficPerm(PERMISSION_TRAFFIC_ALL, MOCK_APPID1);

        when(mPackageManager.getPackagesForUid(MOCK_UID11)).thenReturn(new String[]{});
        onPackageRemoved(MOCK_PACKAGE1, MOCK_UID11);
        mBpfMapMonitor.expectTrafficPerm(PERMISSION_UNINSTALLED, MOCK_APPID1);

        addPackage(MOCK_PACKAGE1, MOCK_UID11, INTERNET);
        mBpfMapMonitor.expectTrafficPerm(PERMISSION_INTERNET, MOCK_APPID1);
    }

    @Test
    public void testPackageUpdate() throws Exception {
        addPackage(MOCK_PACKAGE1, MOCK_UID11);
        mBpfMapMonitor.expectTrafficPerm(PERMISSION_NONE, MOCK_APPID1);

        addPackage(MOCK_PACKAGE1, MOCK_UID11, INTERNET);
        mBpfMapMonitor.expectTrafficPerm(PERMISSION_INTERNET, MOCK_APPID1);
    }

    @Test
    public void testPackageUninstallWithMultiplePackages() throws Exception {
        addPackage(MOCK_PACKAGE1, MOCK_UID11, INTERNET, UPDATE_DEVICE_STATS);
        mBpfMapMonitor.expectTrafficPerm(PERMISSION_TRAFFIC_ALL, MOCK_APPID1);

        // Install another package with the same uid but different permissions.
        addPackage(MOCK_PACKAGE2, MOCK_UID11, INTERNET);
        mBpfMapMonitor.expectTrafficPerm(PERMISSION_TRAFFIC_ALL, MOCK_UID11);

        // Uninstall MOCK_PACKAGE1 and expect only INTERNET permission left.
        when(mPackageManager.getPackagesForUid(eq(MOCK_UID11)))
                .thenReturn(new String[]{MOCK_PACKAGE2});
        onPackageRemoved(MOCK_PACKAGE1, MOCK_UID11);
        mBpfMapMonitor.expectTrafficPerm(PERMISSION_INTERNET, MOCK_APPID1);
    }

    @Test
    public void testRealSystemPermission() throws Exception {
        // Use the real context as this test must ensure the *real* system package holds the
        // necessary permission.
        final Context realContext = InstrumentationRegistry.getContext();
        final PermissionMonitor monitor = new PermissionMonitor(
                realContext, mNetdService, mBpfNetMaps, mHandlerThread);
        final PackageManager manager = realContext.getPackageManager();
        final PackageInfo systemInfo = manager.getPackageInfo(REAL_SYSTEM_PACKAGE_NAME,
                GET_PERMISSIONS | MATCH_ANY_USER);
        assertTrue(monitor.hasPermission(systemInfo, CONNECTIVITY_USE_RESTRICTED_NETWORKS));
    }

    @Test
    public void testUpdateUidPermissionsFromSystemConfig() throws Exception {
        when(mSystemConfigManager.getSystemPermissionUids(eq(INTERNET)))
                .thenReturn(new int[]{ MOCK_UID11, MOCK_UID12 });
        when(mSystemConfigManager.getSystemPermissionUids(eq(UPDATE_DEVICE_STATS)))
                .thenReturn(new int[]{ MOCK_UID12 });

        startMonitoring();
        mBpfMapMonitor.expectTrafficPerm(PERMISSION_INTERNET, MOCK_APPID1);
        mBpfMapMonitor.expectTrafficPerm(PERMISSION_TRAFFIC_ALL, MOCK_APPID2);
    }

    private BroadcastReceiver expectBroadcastReceiver(String... actions) {
        final ArgumentCaptor<BroadcastReceiver> receiverCaptor =
                ArgumentCaptor.forClass(BroadcastReceiver.class);
        verify(mContext, times(1)).registerReceiver(receiverCaptor.capture(),
                argThat(filter -> {
                    for (String action : actions) {
                        if (!filter.hasAction(action)) {
                            return false;
                        }
                    }
                    return true;
                }), any(), any());
        final BroadcastReceiver originalReceiver = receiverCaptor.getValue();
        return new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                processOnHandlerThread(() -> originalReceiver.onReceive(context, intent));
            }
        };
    }

    private void processOnHandlerThread(Runnable function) {
        final Handler handler = mHandlerThread.getThreadHandler();
        handler.post(() -> function.run());
        HandlerUtils.waitForIdle(mHandlerThread, TIMEOUT_MS);
    }

    @Test
    public void testIntentReceiver() throws Exception {
        startMonitoring();
        final BroadcastReceiver receiver = expectBroadcastReceiver(
                Intent.ACTION_PACKAGE_ADDED, Intent.ACTION_PACKAGE_REMOVED);

        // Verify receiving PACKAGE_ADDED intent.
        final Intent addedIntent = new Intent(Intent.ACTION_PACKAGE_ADDED,
                Uri.fromParts("package", MOCK_PACKAGE1, null /* fragment */));
        addedIntent.putExtra(Intent.EXTRA_UID, MOCK_UID11);
        buildAndMockPackageInfoWithPermissions(MOCK_PACKAGE1, MOCK_UID11, INTERNET,
                UPDATE_DEVICE_STATS);
        receiver.onReceive(mContext, addedIntent);
        mBpfMapMonitor.expectTrafficPerm(PERMISSION_TRAFFIC_ALL, MOCK_APPID1);

        // Verify receiving PACKAGE_REMOVED intent.
        when(mPackageManager.getPackagesForUid(MOCK_UID11)).thenReturn(new String[]{});
        final Intent removedIntent = new Intent(Intent.ACTION_PACKAGE_REMOVED,
                Uri.fromParts("package", MOCK_PACKAGE1, null /* fragment */));
        removedIntent.putExtra(Intent.EXTRA_UID, MOCK_UID11);
        receiver.onReceive(mContext, removedIntent);
        mBpfMapMonitor.expectTrafficPerm(PERMISSION_UNINSTALLED, MOCK_APPID1);
    }

    private ContentObserver expectRegisterContentObserver(Uri expectedUri) {
        final ArgumentCaptor<ContentObserver> captor =
                ArgumentCaptor.forClass(ContentObserver.class);
        verify(mDeps).registerContentObserver(any(),
                argThat(uri -> uri.equals(expectedUri)), anyBoolean(), captor.capture());
        final ContentObserver originalObserver = captor.getValue();
        return new ContentObserver(null) {
            @Override
            public void onChange(final boolean selfChange) {
                processOnHandlerThread(() -> originalObserver.onChange(selfChange));
            }
        };
    }

    @Test
    public void testUidsAllowedOnRestrictedNetworksChanged() throws Exception {
        startMonitoring();
        final ContentObserver contentObserver = expectRegisterContentObserver(
                Settings.Global.getUriFor(UIDS_ALLOWED_ON_RESTRICTED_NETWORKS));

        // Prepare PackageInfo for MOCK_PACKAGE1 and MOCK_PACKAGE2
        buildAndMockPackageInfoWithPermissions(MOCK_PACKAGE1, MOCK_UID11);
        buildAndMockPackageInfoWithPermissions(MOCK_PACKAGE2, MOCK_UID12);

        // MOCK_UID11 is listed in setting that allow to use restricted networks, MOCK_UID11
        // should have SYSTEM permission.
        when(mDeps.getUidsAllowedOnRestrictedNetworks(any())).thenReturn(Set.of(MOCK_UID11));
        contentObserver.onChange(true /* selfChange */);
        mNetdMonitor.expectNetworkPerm(PERMISSION_SYSTEM, new UserHandle[]{MOCK_USER1},
                MOCK_APPID1);
        mNetdMonitor.expectNoNetworkPerm(new UserHandle[]{MOCK_USER1}, MOCK_APPID2);

        // MOCK_UID12 is listed in setting that allow to use restricted networks, MOCK_UID12
        // should have SYSTEM permission but MOCK_UID11 should revoke permission.
        when(mDeps.getUidsAllowedOnRestrictedNetworks(any())).thenReturn(Set.of(MOCK_UID12));
        contentObserver.onChange(true /* selfChange */);
        mNetdMonitor.expectNetworkPerm(PERMISSION_SYSTEM, new UserHandle[]{MOCK_USER1},
                MOCK_APPID2);
        mNetdMonitor.expectNoNetworkPerm(new UserHandle[]{MOCK_USER1}, MOCK_APPID1);

        // No uid lists in setting, should revoke permission from all uids.
        when(mDeps.getUidsAllowedOnRestrictedNetworks(any())).thenReturn(Set.of());
        contentObserver.onChange(true /* selfChange */);
        mNetdMonitor.expectNoNetworkPerm(new UserHandle[]{MOCK_USER1}, MOCK_APPID1, MOCK_APPID2);
    }

    @Test
    public void testUidsAllowedOnRestrictedNetworksChangedWithSharedUid() throws Exception {
        startMonitoring();
        final ContentObserver contentObserver = expectRegisterContentObserver(
                Settings.Global.getUriFor(UIDS_ALLOWED_ON_RESTRICTED_NETWORKS));

        buildAndMockPackageInfoWithPermissions(MOCK_PACKAGE1, MOCK_UID11, CHANGE_NETWORK_STATE);
        buildAndMockPackageInfoWithPermissions(MOCK_PACKAGE2, MOCK_UID11);

        // MOCK_PACKAGE1 have CHANGE_NETWORK_STATE, MOCK_UID11 should have NETWORK permission.
        addPackageForUsers(new UserHandle[]{MOCK_USER1}, MOCK_PACKAGE1, MOCK_APPID1);
        mNetdMonitor.expectNetworkPerm(PERMISSION_NETWORK, new UserHandle[]{MOCK_USER1},
                MOCK_APPID1);

        // MOCK_UID11 is listed in setting that allow to use restricted networks, MOCK_UID11
        // should upgrade to SYSTEM permission.
        when(mDeps.getUidsAllowedOnRestrictedNetworks(any())).thenReturn(Set.of(MOCK_UID11));
        contentObserver.onChange(true /* selfChange */);
        mNetdMonitor.expectNetworkPerm(PERMISSION_SYSTEM, new UserHandle[]{MOCK_USER1},
                MOCK_APPID1);

        // No app lists in setting, MOCK_UID11 should downgrade to NETWORK permission.
        when(mDeps.getUidsAllowedOnRestrictedNetworks(any())).thenReturn(Set.of());
        contentObserver.onChange(true /* selfChange */);
        mNetdMonitor.expectNetworkPerm(PERMISSION_NETWORK, new UserHandle[]{MOCK_USER1},
                MOCK_APPID1);

        // MOCK_PACKAGE1 removed, should revoke permission from MOCK_UID11.
        when(mPackageManager.getPackagesForUid(MOCK_UID11)).thenReturn(new String[]{MOCK_PACKAGE2});
        removePackageForUsers(new UserHandle[]{MOCK_USER1}, MOCK_PACKAGE1, MOCK_APPID1);
        mNetdMonitor.expectNoNetworkPerm(new UserHandle[]{MOCK_USER1}, MOCK_APPID1);
    }

    @Test
    public void testUidsAllowedOnRestrictedNetworksChangedWithMultipleUsers() throws Exception {
        startMonitoring();
        final ContentObserver contentObserver = expectRegisterContentObserver(
                Settings.Global.getUriFor(UIDS_ALLOWED_ON_RESTRICTED_NETWORKS));

        // Prepare PackageInfo for MOCK_APPID1 and MOCK_APPID2 in MOCK_USER1.
        buildAndMockPackageInfoWithPermissions(MOCK_PACKAGE1, MOCK_UID11);
        buildAndMockPackageInfoWithPermissions(MOCK_PACKAGE2, MOCK_UID12);

        // MOCK_UID11 is listed in setting that allow to use restricted networks, MOCK_UID11 should
        // have SYSTEM permission and MOCK_UID12 has no permissions.
        when(mDeps.getUidsAllowedOnRestrictedNetworks(any())).thenReturn(Set.of(MOCK_UID11));
        contentObserver.onChange(true /* selfChange */);
        mNetdMonitor.expectNetworkPerm(PERMISSION_SYSTEM, new UserHandle[]{MOCK_USER1},
                MOCK_APPID1);
        mNetdMonitor.expectNoNetworkPerm(new UserHandle[]{MOCK_USER1}, MOCK_APPID2);

        // Add user MOCK_USER2.
        final List<PackageInfo> pkgs = List.of(buildPackageInfo(MOCK_PACKAGE1, MOCK_UID21));
        buildAndMockPackageInfoWithPermissions(MOCK_PACKAGE2, MOCK_UID22);
        doReturn(pkgs).when(mPackageManager)
                .getInstalledPackagesAsUser(eq(GET_PERMISSIONS), eq(MOCK_USER_ID2));
        onUserAdded(MOCK_USER2);
        // MOCK_APPID1 in MOCK_USER1 should have SYSTEM permission but in MOCK_USER2 should have no
        // permissions. And MOCK_APPID2 has no permissions in either users.
        mNetdMonitor.expectNetworkPerm(PERMISSION_SYSTEM, new UserHandle[]{MOCK_USER1},
                MOCK_APPID1);
        mNetdMonitor.expectNoNetworkPerm(new UserHandle[]{MOCK_USER2}, MOCK_APPID1);
        mNetdMonitor.expectNoNetworkPerm(new UserHandle[]{MOCK_USER1, MOCK_USER2}, MOCK_APPID2);

        // MOCK_UID22 is listed in setting that allow to use restricted networks,
        // MOCK_APPID2 in MOCK_USER2 should have SYSTEM permission but in MOCK_USER1 should have no
        // permissions. And MOCK_APPID1 has no permissions in either users.
        doReturn(Set.of(MOCK_UID22)).when(mDeps).getUidsAllowedOnRestrictedNetworks(any());
        contentObserver.onChange(true /* selfChange */);
        mNetdMonitor.expectNetworkPerm(PERMISSION_SYSTEM, new UserHandle[]{MOCK_USER2},
                MOCK_APPID2);
        mNetdMonitor.expectNoNetworkPerm(new UserHandle[]{MOCK_USER1}, MOCK_APPID2);
        mNetdMonitor.expectNoNetworkPerm(new UserHandle[]{MOCK_USER1, MOCK_USER2}, MOCK_APPID1);

        // Remove user MOCK_USER1
        onUserRemoved(MOCK_USER1);
        mNetdMonitor.expectNetworkPerm(PERMISSION_SYSTEM, new UserHandle[]{MOCK_USER2},
                MOCK_APPID2);
        mNetdMonitor.expectNoNetworkPerm(new UserHandle[]{MOCK_USER2}, MOCK_APPID1);
        mNetdMonitor.expectNoNetworkPerm(new UserHandle[]{MOCK_USER1}, MOCK_APPID2);

        // No uid lists in setting, should revoke permission from all uids.
        when(mDeps.getUidsAllowedOnRestrictedNetworks(any())).thenReturn(Set.of());
        contentObserver.onChange(true /* selfChange */);
        mNetdMonitor.expectNoNetworkPerm(new UserHandle[]{MOCK_USER2}, MOCK_APPID1, MOCK_APPID2);
    }

    @Test
    public void testOnExternalApplicationsAvailable() throws Exception {
        // Initial the permission state. MOCK_PACKAGE1 and MOCK_PACKAGE2 are installed on external
        // and have different uids. There has no permission for both uids.
        doReturn(List.of(
                buildPackageInfo(MOCK_PACKAGE1, MOCK_UID11),
                buildPackageInfo(MOCK_PACKAGE2, MOCK_UID12)))
                .when(mPackageManager).getInstalledPackagesAsUser(eq(GET_PERMISSIONS), anyInt());
        startMonitoring();
        mNetdMonitor.expectNoNetworkPerm(new UserHandle[]{MOCK_USER1}, MOCK_APPID1, MOCK_APPID2);
        mBpfMapMonitor.expectTrafficPerm(PERMISSION_NONE, MOCK_APPID1, MOCK_APPID2);

        final BroadcastReceiver receiver = expectBroadcastReceiver(
                Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE);
        // Verify receiving EXTERNAL_APPLICATIONS_AVAILABLE intent and update permission to netd.
        final Intent externalIntent = new Intent(Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE);
        externalIntent.putExtra(Intent.EXTRA_CHANGED_PACKAGE_LIST,
                new String[] { MOCK_PACKAGE1 , MOCK_PACKAGE2});
        buildAndMockPackageInfoWithPermissions(MOCK_PACKAGE1, MOCK_UID11,
                CONNECTIVITY_USE_RESTRICTED_NETWORKS, INTERNET);
        buildAndMockPackageInfoWithPermissions(MOCK_PACKAGE2, MOCK_UID12, CHANGE_NETWORK_STATE,
                UPDATE_DEVICE_STATS);
        receiver.onReceive(mContext, externalIntent);
        mNetdMonitor.expectNetworkPerm(PERMISSION_SYSTEM, new UserHandle[]{MOCK_USER1},
                MOCK_APPID1);
        mNetdMonitor.expectNetworkPerm(PERMISSION_NETWORK, new UserHandle[]{MOCK_USER1},
                MOCK_APPID2);
        mBpfMapMonitor.expectTrafficPerm(PERMISSION_INTERNET, MOCK_APPID1);
        mBpfMapMonitor.expectTrafficPerm(PERMISSION_UPDATE_DEVICE_STATS, MOCK_APPID2);
    }

    @Test
    public void testOnExternalApplicationsAvailable_AppsNotRegisteredOnStartMonitoring()
            throws Exception {
        startMonitoring();
        final BroadcastReceiver receiver = expectBroadcastReceiver(
                Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE);

        // Initial the permission state. MOCK_PACKAGE1 and MOCK_PACKAGE2 are installed on external
        // and have different uids. There has no permission for both uids.
        buildAndMockPackageInfoWithPermissions(MOCK_PACKAGE1, MOCK_UID11,
                CONNECTIVITY_USE_RESTRICTED_NETWORKS, INTERNET);
        buildAndMockPackageInfoWithPermissions(MOCK_PACKAGE2, MOCK_UID12, CHANGE_NETWORK_STATE,
                UPDATE_DEVICE_STATS);

        // Verify receiving EXTERNAL_APPLICATIONS_AVAILABLE intent and update permission to netd.
        final Intent externalIntent = new Intent(Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE);
        externalIntent.putExtra(Intent.EXTRA_CHANGED_PACKAGE_LIST,
                new String[] { MOCK_PACKAGE1 , MOCK_PACKAGE2});
        receiver.onReceive(mContext, externalIntent);
        mNetdMonitor.expectNetworkPerm(PERMISSION_SYSTEM, new UserHandle[]{MOCK_USER1},
                MOCK_APPID1);
        mNetdMonitor.expectNetworkPerm(PERMISSION_NETWORK, new UserHandle[]{MOCK_USER1},
                MOCK_APPID2);
        mBpfMapMonitor.expectTrafficPerm(PERMISSION_INTERNET, MOCK_APPID1);
        mBpfMapMonitor.expectTrafficPerm(PERMISSION_UPDATE_DEVICE_STATS, MOCK_APPID2);
    }

    @Test
    public void testOnExternalApplicationsAvailableWithSharedUid()
            throws Exception {
        // Initial the permission state. MOCK_PACKAGE1 and MOCK_PACKAGE2 are installed on external
        // storage and shared on MOCK_UID11. There has no permission for MOCK_UID11.
        doReturn(List.of(
                buildPackageInfo(MOCK_PACKAGE1, MOCK_UID11),
                buildPackageInfo(MOCK_PACKAGE2, MOCK_UID11)))
                .when(mPackageManager).getInstalledPackagesAsUser(eq(GET_PERMISSIONS), anyInt());
        startMonitoring();
        mNetdMonitor.expectNoNetworkPerm(new UserHandle[]{MOCK_USER1}, MOCK_APPID1);
        mBpfMapMonitor.expectTrafficPerm(PERMISSION_NONE, MOCK_APPID1);

        final BroadcastReceiver receiver = expectBroadcastReceiver(
                Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE);
        // Verify receiving EXTERNAL_APPLICATIONS_AVAILABLE intent and update permission to netd.
        final Intent externalIntent = new Intent(Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE);
        externalIntent.putExtra(Intent.EXTRA_CHANGED_PACKAGE_LIST, new String[] {MOCK_PACKAGE1});
        buildAndMockPackageInfoWithPermissions(MOCK_PACKAGE1, MOCK_UID11, CHANGE_NETWORK_STATE);
        buildAndMockPackageInfoWithPermissions(MOCK_PACKAGE2, MOCK_UID11, UPDATE_DEVICE_STATS);
        receiver.onReceive(mContext, externalIntent);
        mNetdMonitor.expectNetworkPerm(PERMISSION_NETWORK, new UserHandle[]{MOCK_USER1},
                MOCK_APPID1);
        mBpfMapMonitor.expectTrafficPerm(PERMISSION_UPDATE_DEVICE_STATS, MOCK_APPID1);
    }

    @Test
    public void testOnExternalApplicationsAvailableWithSharedUid_DifferentStorage()
            throws Exception {
        // Initial the permission state. MOCK_PACKAGE1 is installed on external storage and
        // MOCK_PACKAGE2 is installed on device. These two packages are shared on MOCK_UID11.
        // MOCK_UID11 has NETWORK and INTERNET permissions.
        doReturn(List.of(
                buildPackageInfo(MOCK_PACKAGE1, MOCK_UID11),
                buildPackageInfo(MOCK_PACKAGE2, MOCK_UID11, CHANGE_NETWORK_STATE, INTERNET)))
                .when(mPackageManager).getInstalledPackagesAsUser(eq(GET_PERMISSIONS), anyInt());
        startMonitoring();
        mNetdMonitor.expectNetworkPerm(PERMISSION_NETWORK, new UserHandle[]{MOCK_USER1},
                MOCK_APPID1);
        mBpfMapMonitor.expectTrafficPerm(PERMISSION_INTERNET, MOCK_APPID1);

        final BroadcastReceiver receiver = expectBroadcastReceiver(
                Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE);
        // Verify receiving EXTERNAL_APPLICATIONS_AVAILABLE intent and update permission to netd.
        final Intent externalIntent = new Intent(Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE);
        externalIntent.putExtra(Intent.EXTRA_CHANGED_PACKAGE_LIST, new String[] {MOCK_PACKAGE1});
        buildAndMockPackageInfoWithPermissions(MOCK_PACKAGE1, MOCK_UID11,
                CONNECTIVITY_USE_RESTRICTED_NETWORKS, UPDATE_DEVICE_STATS);
        buildAndMockPackageInfoWithPermissions(MOCK_PACKAGE2, MOCK_UID11, CHANGE_NETWORK_STATE,
                INTERNET);
        receiver.onReceive(mContext, externalIntent);
        mNetdMonitor.expectNetworkPerm(PERMISSION_SYSTEM, new UserHandle[]{MOCK_USER1},
                MOCK_APPID1);
        mBpfMapMonitor.expectTrafficPerm(PERMISSION_TRAFFIC_ALL, MOCK_APPID1);
    }

    @Test
    public void testIsHigherNetworkPermission() {
        assertFalse(isHigherNetworkPermission(PERMISSION_NONE, PERMISSION_NONE));
        assertFalse(isHigherNetworkPermission(PERMISSION_NONE, PERMISSION_NETWORK));
        assertFalse(isHigherNetworkPermission(PERMISSION_NONE, PERMISSION_SYSTEM));
        assertTrue(isHigherNetworkPermission(PERMISSION_NETWORK, PERMISSION_NONE));
        assertFalse(isHigherNetworkPermission(PERMISSION_NETWORK, PERMISSION_NETWORK));
        assertFalse(isHigherNetworkPermission(PERMISSION_NETWORK, PERMISSION_SYSTEM));
        assertTrue(isHigherNetworkPermission(PERMISSION_SYSTEM, PERMISSION_NONE));
        assertTrue(isHigherNetworkPermission(PERMISSION_SYSTEM, PERMISSION_NETWORK));
        assertFalse(isHigherNetworkPermission(PERMISSION_SYSTEM, PERMISSION_SYSTEM));
    }

    private void prepareMultiUserPackages() {
        // MOCK_USER1 has installed 3 packages
        // mockApp1 has no permission and share MOCK_APPID1.
        // mockApp2 has INTERNET permission and share MOCK_APPID2.
        // mockApp3 has UPDATE_DEVICE_STATS permission and share MOCK_APPID3.
        final List<PackageInfo> pkgs1 = List.of(
                buildPackageInfo("mockApp1", MOCK_UID11),
                buildPackageInfo("mockApp2", MOCK_UID12, INTERNET),
                buildPackageInfo("mockApp3", MOCK_UID13, UPDATE_DEVICE_STATS));

        // MOCK_USER2 has installed 2 packages
        // mockApp4 has UPDATE_DEVICE_STATS permission and share MOCK_APPID1.
        // mockApp5 has INTERNET permission and share MOCK_APPID2.
        final List<PackageInfo> pkgs2 = List.of(
                buildPackageInfo("mockApp4", MOCK_UID21, UPDATE_DEVICE_STATS),
                buildPackageInfo("mockApp5", MOCK_UID23, INTERNET));

        // MOCK_USER3 has installed 1 packages
        // mockApp6 has UPDATE_DEVICE_STATS permission and share MOCK_APPID2.
        final List<PackageInfo> pkgs3 = List.of(
                buildPackageInfo("mockApp6", MOCK_UID32, UPDATE_DEVICE_STATS));

        doReturn(pkgs1).when(mPackageManager).getInstalledPackagesAsUser(eq(GET_PERMISSIONS),
                eq(MOCK_USER_ID1));
        doReturn(pkgs2).when(mPackageManager).getInstalledPackagesAsUser(eq(GET_PERMISSIONS),
                eq(MOCK_USER_ID2));
        doReturn(pkgs3).when(mPackageManager).getInstalledPackagesAsUser(eq(GET_PERMISSIONS),
                eq(MOCK_USER_ID3));
    }

    private void addUserAndVerifyAppIdsPermissions(UserHandle user, int appId1Perm,
            int appId2Perm, int appId3Perm) {
        processOnHandlerThread(() -> mPermissionMonitor.onUserAdded(user));
        mBpfMapMonitor.expectTrafficPerm(appId1Perm, MOCK_APPID1);
        mBpfMapMonitor.expectTrafficPerm(appId2Perm, MOCK_APPID2);
        mBpfMapMonitor.expectTrafficPerm(appId3Perm, MOCK_APPID3);
    }

    private void removeUserAndVerifyAppIdsPermissions(UserHandle user, int appId1Perm,
            int appId2Perm, int appId3Perm) {
        processOnHandlerThread(() -> mPermissionMonitor.onUserRemoved(user));
        mBpfMapMonitor.expectTrafficPerm(appId1Perm, MOCK_APPID1);
        mBpfMapMonitor.expectTrafficPerm(appId2Perm, MOCK_APPID2);
        mBpfMapMonitor.expectTrafficPerm(appId3Perm, MOCK_APPID3);
    }

    @Test
    public void testAppIdsTrafficPermission_UserAddedRemoved() {
        prepareMultiUserPackages();

        // Add MOCK_USER1 and verify the permissions with each appIds.
        addUserAndVerifyAppIdsPermissions(MOCK_USER1, PERMISSION_NONE, PERMISSION_INTERNET,
                PERMISSION_UPDATE_DEVICE_STATS);

        // Add MOCK_USER2 and verify the permissions upgrade on MOCK_APPID1 & MOCK_APPID3.
        addUserAndVerifyAppIdsPermissions(MOCK_USER2, PERMISSION_UPDATE_DEVICE_STATS,
                PERMISSION_INTERNET, PERMISSION_TRAFFIC_ALL);

        // Add MOCK_USER3 and verify the permissions upgrade on MOCK_APPID2.
        addUserAndVerifyAppIdsPermissions(MOCK_USER3, PERMISSION_UPDATE_DEVICE_STATS,
                PERMISSION_TRAFFIC_ALL, PERMISSION_TRAFFIC_ALL);

        // Remove MOCK_USER2 and verify the permissions downgrade on MOCK_APPID1 & MOCK_APPID3.
        removeUserAndVerifyAppIdsPermissions(MOCK_USER2, PERMISSION_NONE, PERMISSION_TRAFFIC_ALL,
                PERMISSION_UPDATE_DEVICE_STATS);

        // Remove MOCK_USER1 and verify the permissions downgrade on all appIds.
        removeUserAndVerifyAppIdsPermissions(MOCK_USER1, PERMISSION_UNINSTALLED,
                PERMISSION_UPDATE_DEVICE_STATS, PERMISSION_UNINSTALLED);

        // Add MOCK_USER2 back and verify the permissions upgrade on MOCK_APPID1 & MOCK_APPID3.
        addUserAndVerifyAppIdsPermissions(MOCK_USER2, PERMISSION_UPDATE_DEVICE_STATS,
                PERMISSION_UPDATE_DEVICE_STATS, PERMISSION_INTERNET);

        // Remove MOCK_USER3 and verify the permissions downgrade on MOCK_APPID2.
        removeUserAndVerifyAppIdsPermissions(MOCK_USER3, PERMISSION_UPDATE_DEVICE_STATS,
                PERMISSION_UNINSTALLED, PERMISSION_INTERNET);
    }

    @Test
    public void testAppIdsTrafficPermission_Multiuser_PackageAdded() throws Exception {
        // Add two users with empty package list.
        onUserAdded(MOCK_USER1);
        onUserAdded(MOCK_USER2);

        final int[] netdPermissions = {PERMISSION_NONE, PERMISSION_INTERNET,
                PERMISSION_UPDATE_DEVICE_STATS, PERMISSION_TRAFFIC_ALL};
        final String[][] grantPermissions = {new String[]{}, new String[]{INTERNET},
                new String[]{UPDATE_DEVICE_STATS}, new String[]{INTERNET, UPDATE_DEVICE_STATS}};

        // Verify that the permission combination is expected when same appId package is installed
        // on another user. List the expected permissions below.
        // NONE                + NONE                = NONE
        // NONE                + INTERNET            = INTERNET
        // NONE                + UPDATE_DEVICE_STATS = UPDATE_DEVICE_STATS
        // NONE                + ALL                 = ALL
        // INTERNET            + NONE                = INTERNET
        // INTERNET            + INTERNET            = INTERNET
        // INTERNET            + UPDATE_DEVICE_STATS = ALL
        // INTERNET            + ALL                 = ALL
        // UPDATE_DEVICE_STATS + NONE                = UPDATE_DEVICE_STATS
        // UPDATE_DEVICE_STATS + INTERNET            = ALL
        // UPDATE_DEVICE_STATS + UPDATE_DEVICE_STATS = UPDATE_DEVICE_STATS
        // UPDATE_DEVICE_STATS + ALL                 = ALL
        // ALL                 + NONE                = ALL
        // ALL                 + INTERNET            = ALL
        // ALL                 + UPDATE_DEVICE_STATS = ALL
        // ALL                 + ALL                 = ALL
        for (int i = 0, num = 0; i < netdPermissions.length; i++) {
            final int current = netdPermissions[i];
            final String[] user1Perm = grantPermissions[i];
            for (int j = 0; j < netdPermissions.length; j++) {
                final int appId = MOCK_APPID1 + num;
                final int added = netdPermissions[j];
                final String[] user2Perm = grantPermissions[j];
                // Add package on MOCK_USER1 and verify the permission is same as package granted.
                addPackage(MOCK_PACKAGE1, MOCK_USER1.getUid(appId), user1Perm);
                mBpfMapMonitor.expectTrafficPerm(current, appId);

                // Add package which share the same appId on MOCK_USER2, and verify the permission
                // has combined.
                addPackage(MOCK_PACKAGE2, MOCK_USER2.getUid(appId), user2Perm);
                mBpfMapMonitor.expectTrafficPerm((current | added), appId);
                num++;
            }
        }
    }

    private void verifyAppIdPermissionsAfterPackageRemoved(int appId, int expectedPerm,
            String[] user1Perm, String[] user2Perm) throws Exception {
        // Add package on MOCK_USER1 and verify the permission is same as package granted.
        addPackage(MOCK_PACKAGE1, MOCK_USER1.getUid(appId), user1Perm);
        mBpfMapMonitor.expectTrafficPerm(expectedPerm, appId);

        // Add two packages which share the same appId and don't declare permission on
        // MOCK_USER2. Verify the permission has no change.
        addPackage(MOCK_PACKAGE2, MOCK_USER2.getUid(appId));
        addPackage(MOCK_PACKAGE3, MOCK_USER2.getUid(appId), user2Perm);
        mBpfMapMonitor.expectTrafficPerm(expectedPerm, appId);

        // Remove one packages from MOCK_USER2. Verify the permission has no change too.
        removePackage(MOCK_PACKAGE2, MOCK_USER2.getUid(appId));
        mBpfMapMonitor.expectTrafficPerm(expectedPerm, appId);

        // Remove last packages from MOCK_USER2. Verify the permission has still no change.
        removePackage(MOCK_PACKAGE3, MOCK_USER2.getUid(appId));
        mBpfMapMonitor.expectTrafficPerm(expectedPerm, appId);
    }

    @Test
    public void testAppIdsTrafficPermission_Multiuser_PackageRemoved() throws Exception {
        // Add two users with empty package list.
        onUserAdded(MOCK_USER1);
        onUserAdded(MOCK_USER2);

        int appId = MOCK_APPID1;
        // Verify that the permission combination is expected when same appId package is removed on
        // another user. List the expected permissions below.
        /***** NONE *****/
        // NONE + NONE = NONE
        verifyAppIdPermissionsAfterPackageRemoved(
                appId++, PERMISSION_NONE, new String[]{}, new String[]{});

        /***** INTERNET *****/
        // INTERNET + NONE = INTERNET
        verifyAppIdPermissionsAfterPackageRemoved(
                appId++, PERMISSION_INTERNET, new String[]{INTERNET}, new String[]{});

        // INTERNET + INTERNET = INTERNET
        verifyAppIdPermissionsAfterPackageRemoved(
                appId++, PERMISSION_INTERNET, new String[]{INTERNET}, new String[]{INTERNET});

        /***** UPDATE_DEVICE_STATS *****/
        // UPDATE_DEVICE_STATS + NONE = UPDATE_DEVICE_STATS
        verifyAppIdPermissionsAfterPackageRemoved(appId++, PERMISSION_UPDATE_DEVICE_STATS,
                new String[]{UPDATE_DEVICE_STATS}, new String[]{});

        // UPDATE_DEVICE_STATS + UPDATE_DEVICE_STATS = UPDATE_DEVICE_STATS
        verifyAppIdPermissionsAfterPackageRemoved(appId++, PERMISSION_UPDATE_DEVICE_STATS,
                new String[]{UPDATE_DEVICE_STATS}, new String[]{UPDATE_DEVICE_STATS});

        /***** ALL *****/
        // ALL + NONE = ALL
        verifyAppIdPermissionsAfterPackageRemoved(appId++, PERMISSION_TRAFFIC_ALL,
                new String[]{INTERNET, UPDATE_DEVICE_STATS}, new String[]{});

        // ALL + INTERNET = ALL
        verifyAppIdPermissionsAfterPackageRemoved(appId++, PERMISSION_TRAFFIC_ALL,
                new String[]{INTERNET, UPDATE_DEVICE_STATS}, new String[]{INTERNET});

        // ALL + UPDATE_DEVICE_STATS = ALL
        verifyAppIdPermissionsAfterPackageRemoved(appId++, PERMISSION_TRAFFIC_ALL,
                new String[]{INTERNET, UPDATE_DEVICE_STATS}, new String[]{UPDATE_DEVICE_STATS});

        // ALL + ALL = ALL
        verifyAppIdPermissionsAfterPackageRemoved(appId++, PERMISSION_TRAFFIC_ALL,
                new String[]{INTERNET, UPDATE_DEVICE_STATS},
                new String[]{INTERNET, UPDATE_DEVICE_STATS});

        /***** UNINSTALL *****/
        // UNINSTALL + UNINSTALL = UNINSTALL
        verifyAppIdPermissionsAfterPackageRemoved(
                appId, PERMISSION_NONE, new String[]{}, new String[]{});
        removePackage(MOCK_PACKAGE1, MOCK_USER1.getUid(appId));
        mBpfMapMonitor.expectTrafficPerm(PERMISSION_UNINSTALLED, appId);
    }
}
