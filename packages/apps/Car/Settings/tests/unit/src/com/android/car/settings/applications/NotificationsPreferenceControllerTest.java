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

package com.android.car.settings.applications;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.Manifest;
import android.app.INotificationManager;
import android.app.NotificationChannel;
import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.lifecycle.LifecycleOwner;
import androidx.preference.SwitchPreference;
import androidx.preference.TwoStatePreference;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.TestLifecycleOwner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class NotificationsPreferenceControllerTest {
    private static final String PKG_NAME = "package.name";
    private static final int UID = 1001010;

    private Context mContext = spy(ApplicationProvider.getApplicationContext());
    private LifecycleOwner mLifecycleOwner;
    private CarUxRestrictions mCarUxRestrictions;
    private NotificationsPreferenceController mController;
    private TwoStatePreference mTwoStatePreference;
    private PackageInfo mPackageInfo;

    @Mock
    private FragmentController mFragmentController;
    @Mock
    private INotificationManager mMockNotificationManager;
    @Mock
    private PackageManager mMockPackageManager;
    @Mock
    private NotificationChannel mMockChannel;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLifecycleOwner = new TestLifecycleOwner();
        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();

        mTwoStatePreference = new SwitchPreference(mContext);

        mController = new NotificationsPreferenceController(mContext,
                /* preferenceKey= */ "key", mFragmentController, mCarUxRestrictions);
        PreferenceControllerTestUtil.assignPreference(mController, mTwoStatePreference);

        mController.mNotificationManager = mMockNotificationManager;

        mPackageInfo = new PackageInfo();
        mPackageInfo.packageName = PKG_NAME;

        ApplicationInfo applicationInfo = new ApplicationInfo();
        applicationInfo.packageName = PKG_NAME;
        mPackageInfo.applicationInfo = applicationInfo;
        mPackageInfo.applicationInfo.uid = UID;
        mPackageInfo.applicationInfo.targetSdkVersion = Build.VERSION_CODES.TIRAMISU;
        mController.setPackageInfo(mPackageInfo);

        when(mContext.getPackageManager()).thenReturn(mMockPackageManager);
    }

    @Test
    public void onCreate_notificationEnabled_isChecked() throws Exception {
        when(mMockNotificationManager.areNotificationsEnabledForPackage(PKG_NAME, UID))
                .thenReturn(true);

        mController.onCreate(mLifecycleOwner);

        assertThat(mTwoStatePreference.isChecked()).isTrue();
    }

    @Test
    public void onCreate_notificationDisabled_isNotChecked() throws Exception {
        when(mMockNotificationManager.areNotificationsEnabledForPackage(PKG_NAME, UID))
                .thenReturn(false);

        mController.onCreate(mLifecycleOwner);

        assertThat(mTwoStatePreference.isChecked()).isFalse();
    }

    @Test
    public void onCreate_importanceLocked_isNotEnabled() throws Exception {
        when(mMockNotificationManager.isImportanceLocked(PKG_NAME, UID)).thenReturn(true);

        mController.onCreate(mLifecycleOwner);

        assertThat(mTwoStatePreference.isEnabled()).isFalse();
    }

    @Test
    public void onCreate_noNotificationPermission_isNotEnabled() throws Exception {
        when(mMockNotificationManager.isImportanceLocked(PKG_NAME, UID)).thenReturn(false);

        PackageInfo packageInfo = new PackageInfo();
        packageInfo.requestedPermissions = new String[] {};
        when(mMockPackageManager.getPackageInfoAsUser(eq(PKG_NAME),
                eq(PackageManager.GET_PERMISSIONS), anyInt())).thenReturn(packageInfo);

        mController.onCreate(mLifecycleOwner);

        assertThat(mTwoStatePreference.isEnabled()).isFalse();
    }

    @Test
    public void onCreate_systemFixedFlag_isNotEnabled() throws Exception {
        when(mMockNotificationManager.isImportanceLocked(PKG_NAME, UID)).thenReturn(false);

        PackageInfo packageInfo = new PackageInfo();
        packageInfo.requestedPermissions = new String[] {Manifest.permission.POST_NOTIFICATIONS};
        when(mMockPackageManager.getPackageInfoAsUser(eq(PKG_NAME),
                eq(PackageManager.GET_PERMISSIONS), anyInt())).thenReturn(packageInfo);

        when(mMockPackageManager.getPermissionFlags(eq(Manifest.permission.POST_NOTIFICATIONS),
                eq(PKG_NAME), any())).thenReturn(PackageManager.FLAG_PERMISSION_SYSTEM_FIXED);

        mController.onCreate(mLifecycleOwner);

        assertThat(mTwoStatePreference.isEnabled()).isFalse();
    }

    @Test
    public void onCreate_policyFixedFlag_isNotEnabled() throws Exception {
        when(mMockNotificationManager.isImportanceLocked(PKG_NAME, UID)).thenReturn(false);

        PackageInfo packageInfo = new PackageInfo();
        packageInfo.requestedPermissions = new String[] {Manifest.permission.POST_NOTIFICATIONS};
        when(mMockPackageManager.getPackageInfoAsUser(eq(PKG_NAME),
                eq(PackageManager.GET_PERMISSIONS), anyInt())).thenReturn(packageInfo);

        when(mMockPackageManager.getPermissionFlags(eq(Manifest.permission.POST_NOTIFICATIONS),
                eq(PKG_NAME), any())).thenReturn(PackageManager.FLAG_PERMISSION_POLICY_FIXED);

        mController.onCreate(mLifecycleOwner);

        assertThat(mTwoStatePreference.isEnabled()).isFalse();
    }

    @Test
    public void onCreate_hasPermissions_isEnabled() throws Exception {
        when(mMockNotificationManager.isImportanceLocked(PKG_NAME, UID)).thenReturn(false);

        PackageInfo packageInfo = new PackageInfo();
        packageInfo.requestedPermissions = new String[] {Manifest.permission.POST_NOTIFICATIONS};
        when(mMockPackageManager.getPackageInfoAsUser(eq(PKG_NAME),
                eq(PackageManager.GET_PERMISSIONS), anyInt())).thenReturn(packageInfo);

        when(mMockPackageManager.getPermissionFlags(eq(Manifest.permission.POST_NOTIFICATIONS),
                eq(PKG_NAME), any())).thenReturn(0);

        mController.onCreate(mLifecycleOwner);

        assertThat(mTwoStatePreference.isEnabled()).isTrue();
    }

    @Test
    public void onCreate_targetSdkBelow33_systemFixedFlag_isNotEnabled() throws Exception {
        mPackageInfo.applicationInfo.targetSdkVersion = Build.VERSION_CODES.S;

        when(mMockNotificationManager.isImportanceLocked(PKG_NAME, UID)).thenReturn(false);

        PackageInfo packageInfo = new PackageInfo();
        packageInfo.requestedPermissions = new String[] {Manifest.permission.POST_NOTIFICATIONS};
        when(mMockPackageManager.getPackageInfoAsUser(eq(PKG_NAME),
                eq(PackageManager.GET_PERMISSIONS), anyInt())).thenReturn(packageInfo);

        when(mMockPackageManager.getPermissionFlags(eq(Manifest.permission.POST_NOTIFICATIONS),
                eq(PKG_NAME), any())).thenReturn(PackageManager.FLAG_PERMISSION_SYSTEM_FIXED);

        mController.onCreate(mLifecycleOwner);

        assertThat(mTwoStatePreference.isEnabled()).isFalse();
    }

    @Test
    public void onCreate_targetSdkBelow33_policyFixedFlag_isNotEnabled() throws Exception {
        mPackageInfo.applicationInfo.targetSdkVersion = Build.VERSION_CODES.S;

        when(mMockNotificationManager.isImportanceLocked(PKG_NAME, UID)).thenReturn(false);

        PackageInfo packageInfo = new PackageInfo();
        packageInfo.requestedPermissions = new String[] {Manifest.permission.POST_NOTIFICATIONS};
        when(mMockPackageManager.getPackageInfoAsUser(eq(PKG_NAME),
                eq(PackageManager.GET_PERMISSIONS), anyInt())).thenReturn(packageInfo);

        when(mMockPackageManager.getPermissionFlags(eq(Manifest.permission.POST_NOTIFICATIONS),
                eq(PKG_NAME), any())).thenReturn(PackageManager.FLAG_PERMISSION_POLICY_FIXED);

        mController.onCreate(mLifecycleOwner);

        assertThat(mTwoStatePreference.isEnabled()).isFalse();
    }

    @Test
    public void onCreate_targetSdkBelow33_doesNotHavePermission_isEnabled() throws Exception {
        mPackageInfo.applicationInfo.targetSdkVersion = Build.VERSION_CODES.S;

        when(mMockNotificationManager.isImportanceLocked(PKG_NAME, UID)).thenReturn(false);

        PackageInfo packageInfo = new PackageInfo();
        packageInfo.requestedPermissions = new String[] {};
        when(mMockPackageManager.getPackageInfoAsUser(eq(PKG_NAME),
                eq(PackageManager.GET_PERMISSIONS), anyInt())).thenReturn(packageInfo);

        when(mMockPackageManager.getPermissionFlags(eq(Manifest.permission.POST_NOTIFICATIONS),
                eq(PKG_NAME), any())).thenReturn(0);

        mController.onCreate(mLifecycleOwner);

        assertThat(mTwoStatePreference.isEnabled()).isTrue();
    }

    @Test
    public void callChangeListener_setEnable_enablingNotification() throws Exception {
        when(mMockNotificationManager.onlyHasDefaultChannel(PKG_NAME, UID)).thenReturn(false);

        mTwoStatePreference.callChangeListener(true);

        verify(mMockNotificationManager).setNotificationsEnabledForPackage(PKG_NAME, UID, true);
    }

    @Test
    public void callChangeListener_setDisable_disablingNotification() throws Exception {
        when(mMockNotificationManager.onlyHasDefaultChannel(PKG_NAME, UID)).thenReturn(false);

        mTwoStatePreference.callChangeListener(false);

        verify(mMockNotificationManager).setNotificationsEnabledForPackage(PKG_NAME, UID, false);
    }

    @Test
    public void callChangeListener_onlyHasDefaultChannel_updateChannel() throws Exception {
        when(mMockNotificationManager.onlyHasDefaultChannel(PKG_NAME, UID)).thenReturn(true);
        when(mMockNotificationManager
                .getNotificationChannelForPackage(
                        PKG_NAME, UID, NotificationChannel.DEFAULT_CHANNEL_ID, null, true))
                .thenReturn(mMockChannel);

        mTwoStatePreference.callChangeListener(true);

        verify(mMockNotificationManager)
                .updateNotificationChannelForPackage(PKG_NAME, UID, mMockChannel);
    }
}
