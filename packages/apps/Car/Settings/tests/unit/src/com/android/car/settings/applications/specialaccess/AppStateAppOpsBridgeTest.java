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

package com.android.car.settings.applications.specialaccess;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import android.Manifest;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ParceledListSlice;
import android.os.RemoteException;
import android.os.UserHandle;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.settingslib.applications.ApplicationsState;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.AdditionalMatchers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class AppStateAppOpsBridgeTest {

    private static final int APP_OP_CODE = AppOpsManager.OP_WRITE_SETTINGS;
    private static final String PERMISSION = Manifest.permission.WRITE_SETTINGS;
    private static final int USER_ID1 = 1;
    private static final int USER_ID2 = 2;

    private Context mContext = ApplicationProvider.getApplicationContext();
    private AppOpsManager mAppOpsManager;
    private AppStateAppOpsBridge mBridge;
    private ParceledListSlice<PackageInfo> mParceledListSlice1;
    private ParceledListSlice<PackageInfo> mParceledListSlice2;
    private List<PackageInfo> mPackages;
    private MockitoSession mSession;

    @Mock
    private IPackageManager mMockIPackageManager;
    @Mock
    private AppOpsManager mMockAppOpsManager;

    @Before
    public void setUp() throws RemoteException {
        MockitoAnnotations.initMocks(this);
        mPackages = new ArrayList<>();
        mParceledListSlice1 = new ParceledListSlice<>(mPackages);
        when(mMockIPackageManager.getPackagesHoldingPermissions(
                AdditionalMatchers.aryEq(new String[]{PERMISSION}),
                eq((long) PackageManager.GET_PERMISSIONS),
                eq(USER_ID1)))
                .thenReturn(mParceledListSlice1);

        mAppOpsManager = (AppOpsManager) mContext.getSystemService(Context.APP_OPS_SERVICE);
        UserHandle userHandle = UserHandle.of(USER_ID1);
        mBridge = new AppStateAppOpsBridge(mContext, APP_OP_CODE, PERMISSION, mMockIPackageManager,
                Collections.singletonList(userHandle), mMockAppOpsManager);

        mSession = ExtendedMockito.mockitoSession().mockStatic(
                UserHandle.class, withSettings().lenient()).startMocking();

        ExtendedMockito.when(UserHandle.getUserId(USER_ID1)).thenReturn(USER_ID1);
    }

    @After
    public void tearDown() {
        if (mSession != null) {
            mSession.finishMocking();
        }
    }

    @Test
    public void androidPackagesIgnored() throws RemoteException {
        String packageName = "android";
        PackageInfo packageInfo = createPackageInfo(packageName, USER_ID1);
        addPackageWithPermission(packageInfo, AppOpsManager.MODE_ALLOWED);
        ApplicationsState.AppEntry entry = createAppEntry(packageInfo);

        mBridge.loadExtraInfo(Collections.singletonList(entry));

        assertThat(entry.extraInfo).isNull();
    }

    @Test
    public void selfPackageIgnored() throws RemoteException {
        String packageName = mContext.getPackageName();
        PackageInfo packageInfo = createPackageInfo(packageName, USER_ID1);
        addPackageWithPermission(packageInfo, AppOpsManager.MODE_ALLOWED);
        ApplicationsState.AppEntry entry = createAppEntry(packageInfo);

        mBridge.loadExtraInfo(Collections.singletonList(entry));

        assertThat(entry.extraInfo).isNull();
    }

    @Test
    public void packagesNotRequestingPermissionIgnored() throws RemoteException {
        String packageName = "test.package";
        PackageInfo packageInfo = createPackageInfo(packageName, USER_ID1);
        packageInfo.requestedPermissions = null;
        mPackages.add(packageInfo);
        when(mMockIPackageManager.isPackageAvailable(packageInfo.packageName, USER_ID1))
                .thenReturn(true);
        ApplicationsState.AppEntry entry = createAppEntry(packageInfo);

        mBridge.loadExtraInfo(Collections.singletonList(entry));

        assertThat(entry.extraInfo).isNull();
    }

    @Test
    public void unavailablePackageIgnored() throws RemoteException {
        String packageName = "test.package";
        PackageInfo packageInfo = createPackageInfo(packageName, USER_ID1);
        addPackageWithPermission(packageInfo, AppOpsManager.MODE_ALLOWED);
        when(mMockIPackageManager.isPackageAvailable(packageInfo.packageName, USER_ID1))
                .thenReturn(false);
        ApplicationsState.AppEntry entry = createAppEntry(packageInfo);

        mBridge.loadExtraInfo(Collections.singletonList(entry));

        assertThat(entry.extraInfo).isNull();
    }

    @Test
    public void loadsAppOpsExtraInfo_modeAllowed_isPermissible() throws RemoteException {
        String packageName = "test.package";
        PackageInfo packageInfo = createPackageInfo(packageName, USER_ID1);
        addPackageWithPermission(packageInfo, AppOpsManager.MODE_ALLOWED);
        ApplicationsState.AppEntry entry = createAppEntry(packageInfo);
        assertThat(entry.extraInfo).isNull();

        mBridge.loadExtraInfo(Collections.singletonList(entry));

        assertThat(entry.extraInfo).isNotNull();
        assertThat(((AppStateAppOpsBridge.PermissionState) entry.extraInfo).isPermissible())
                .isTrue();
    }

    @Test
    public void loadsAppOpsExtraInfo_modeDefault_isPermissible() throws RemoteException {
        String packageName = "test.package";
        PackageInfo packageInfo = createPackageInfo(packageName, USER_ID1);
        addPackageWithPermission(packageInfo, AppOpsManager.MODE_DEFAULT);
        ApplicationsState.AppEntry entry = createAppEntry(packageInfo);
        assertThat(entry.extraInfo).isNull();

        mBridge.loadExtraInfo(Collections.singletonList(entry));

        assertThat(entry.extraInfo).isNotNull();
        assertThat(((AppStateAppOpsBridge.PermissionState) entry.extraInfo).isPermissible())
                .isTrue();
    }

    @Test
    public void loadsAppOpsExtraInfo_modeIgnored_isNotPermissible() throws RemoteException {
        ExtendedMockito.when(UserHandle.getUserId(anyInt())).thenReturn(USER_ID1);

        String packageName = "test.package";
        PackageInfo packageInfo = createPackageInfo(packageName, USER_ID1);
        addPackageWithPermission(packageInfo, AppOpsManager.MODE_IGNORED);
        ApplicationsState.AppEntry entry = createAppEntry(packageInfo);
        assertThat(entry.extraInfo).isNull();

        mockPackageOps(packageName, USER_ID1, AppOpsManager.MODE_IGNORED);

        mBridge.loadExtraInfo(Collections.singletonList(entry));

        assertThat(entry.extraInfo).isNotNull();
        assertThat(((AppStateAppOpsBridge.PermissionState) entry.extraInfo).isPermissible())
                .isFalse();
    }

    @Test
    public void loadsAppOpsExtraInfo_multipleApps() throws RemoteException {
        String packageName1 = "test.package1";
        PackageInfo packageInfo1 = createPackageInfo(packageName1, USER_ID1);
        addPackageWithPermission(packageInfo1, AppOpsManager.MODE_ALLOWED);
        ApplicationsState.AppEntry entry1 = createAppEntry(packageInfo1);

        String packageName2 = "test.package2";
        PackageInfo packageInfo2 = createPackageInfo(packageName2, USER_ID2);
        addPackageWithPermission(packageInfo2, AppOpsManager.MODE_ALLOWED);
        ApplicationsState.AppEntry entry2 = createAppEntry(packageInfo2);
        ExtendedMockito.when(UserHandle.getUserId(USER_ID2)).thenReturn(USER_ID1);

        mBridge.loadExtraInfo(Arrays.asList(entry1, entry2));

        assertThat(entry1.extraInfo).isNotNull();
        assertThat(entry2.extraInfo).isNotNull();
    }

    @Test
    public void loadsAppOpExtraInfo_multipleProfiles() throws RemoteException {
        String packageName1 = "test.package1";
        PackageInfo packageInfo1 = createPackageInfo(packageName1, USER_ID1);
        addPackageWithPermission(packageInfo1, AppOpsManager.MODE_ALLOWED);
        ApplicationsState.AppEntry entry1 = createAppEntry(packageInfo1);

        // Add a package for another profile.
        String packageName2 = "test.package2";
        PackageInfo packageInfo2 = createPackageInfo(packageName2, USER_ID2);
        mParceledListSlice2 = new ParceledListSlice<>(Collections.singletonList(packageInfo2));
        when(mMockIPackageManager.getPackagesHoldingPermissions(
                AdditionalMatchers.aryEq(new String[]{PERMISSION}),
                eq((long) PackageManager.GET_PERMISSIONS),
                eq(USER_ID2)))
                .thenReturn(mParceledListSlice2);
        when(mMockIPackageManager.isPackageAvailable(packageInfo2.packageName,
                USER_ID2)).thenReturn(true);
        mAppOpsManager.setMode(APP_OP_CODE, packageInfo2.applicationInfo.uid,
                packageInfo2.packageName, AppOpsManager.MODE_ALLOWED);
        ApplicationsState.AppEntry entry2 = createAppEntry(packageInfo2);
        ExtendedMockito.when(UserHandle.getUserId(USER_ID2)).thenReturn(USER_ID2);

        // Recreate the bridge so it has all user profiles.
        UserHandle userHandle1 = new UserHandle(USER_ID1);
        UserHandle userHandle2 = new UserHandle(USER_ID2);
        mBridge = new AppStateAppOpsBridge(mContext, APP_OP_CODE, PERMISSION, mMockIPackageManager,
                Arrays.asList(userHandle1, userHandle2), mMockAppOpsManager);

        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        mBridge.loadExtraInfo(Arrays.asList(entry1, entry2));

        assertThat(entry1.extraInfo).isNotNull();
        assertThat(entry2.extraInfo).isNotNull();
    }

    @Test
    public void appEntryNotIncluded_extraInfoCleared() {
        String packageName = "test.package";
        PackageInfo packageInfo = createPackageInfo(packageName, USER_ID1);
        ApplicationsState.AppEntry entry = createAppEntry(packageInfo);
        entry.extraInfo = new Object();

        mBridge.loadExtraInfo(Collections.singletonList(entry));

        assertThat(entry.extraInfo).isNull();
    }

    private PackageInfo createPackageInfo(String packageName, int uid) {
        ApplicationInfo applicationInfo = new ApplicationInfo();
        applicationInfo.packageName = packageName;
        applicationInfo.uid = uid;

        PackageInfo packageInfo = new PackageInfo();
        packageInfo.packageName = packageName;
        packageInfo.applicationInfo = applicationInfo;
        packageInfo.requestedPermissions = new String[]{PERMISSION};

        return packageInfo;
    }

    private void addPackageWithPermission(PackageInfo packageInfo, int mode)
            throws RemoteException {
        mPackages.add(packageInfo);
        when(mMockIPackageManager.isPackageAvailable(packageInfo.packageName,
                USER_ID1)).thenReturn(true);
        mAppOpsManager.setMode(APP_OP_CODE, packageInfo.applicationInfo.uid,
                packageInfo.packageName, mode);
    }

    private ApplicationsState.AppEntry createAppEntry(PackageInfo packageInfo) {
        ApplicationsState.AppEntry appEntry = mock(ApplicationsState.AppEntry.class);
        appEntry.info = packageInfo.applicationInfo;
        return appEntry;
    }

    private void mockPackageOps(String packageName, int uid, int mode) {
        AppOpsManager.OpEntry opEntry = mock(AppOpsManager.OpEntry.class);
        when(opEntry.getMode()).thenReturn(mode);
        AppOpsManager.PackageOps packageOps = new AppOpsManager.PackageOps(packageName, uid,
                Collections.singletonList(opEntry));
        List<AppOpsManager.PackageOps> packageOpsList = Collections.singletonList(packageOps);
        when(mMockAppOpsManager.getPackagesForOps((int[]) any())).thenReturn(packageOpsList);
    }
}
