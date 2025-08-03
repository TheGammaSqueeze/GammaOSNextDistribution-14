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

package com.android.car.settings.notifications;

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
import android.graphics.drawable.Drawable;
import android.os.Build;

import androidx.lifecycle.LifecycleOwner;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.R;
import com.android.car.settings.applications.ApplicationDetailsFragment;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.TestLifecycleOwner;
import com.android.car.ui.preference.CarUiTwoActionSwitchPreference;
import com.android.settingslib.applications.ApplicationsState;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;

@RunWith(AndroidJUnit4.class)
public class NotificationsAppListPreferenceControllerTest {
    private static final String PKG_NAME = "package.name";
    private static final int UID = 1001010;
    private static final int ID = 1;
    private static final String LABEL = "label";

    private Context mContext = spy(ApplicationProvider.getApplicationContext());
    private LifecycleOwner mLifecycleOwner;
    private PreferenceGroup mPreferenceCategory;
    private NotificationsAppListPreferenceController mPreferenceController;
    private CarUxRestrictions mCarUxRestrictions;
    private ArrayList<ApplicationsState.AppEntry> mAppEntryList;
    private ApplicationInfo mApplicationInfo;

    @Mock
    private FragmentController mFragmentController;
    @Mock
    private INotificationManager mMockNotificationManager;
    @Mock
    private PackageManager mMockPackageManager;
    @Mock
    private NotificationChannel mMockChannel;
    @Mock
    private CarUiTwoActionSwitchPreference mMockPreference;

    @Before
    @UiThreadTest
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLifecycleOwner = new TestLifecycleOwner();
        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();

        PreferenceManager preferenceManager = new PreferenceManager(mContext);
        PreferenceScreen screen = preferenceManager.createPreferenceScreen(mContext);
        mPreferenceCategory = new PreferenceCategory(mContext);
        screen.addPreference(mPreferenceCategory);

        mPreferenceController = new TestNotificationsAppListPreferenceController(mContext,
                /* preferenceKey= */ "key", mFragmentController, mCarUxRestrictions);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreferenceCategory);

        mPreferenceController.mNotificationManager = mMockNotificationManager;

        mApplicationInfo = new ApplicationInfo();
        mApplicationInfo.packageName = PKG_NAME;
        mApplicationInfo.uid = UID;
        mApplicationInfo.sourceDir = "";
        mApplicationInfo.targetSdkVersion = Build.VERSION_CODES.TIRAMISU;
        ApplicationsState.AppEntry appEntry =
                new ApplicationsState.AppEntry(mContext, mApplicationInfo, ID);
        appEntry.label = LABEL;
        appEntry.icon = mContext.getDrawable(R.drawable.test_icon);

        mAppEntryList = new ArrayList<>();
        mAppEntryList.add(appEntry);

        when(mContext.getPackageManager()).thenReturn(mMockPackageManager);
    }

    @Test
    public void onCreate_createsPreference() throws Exception {
        when(mMockNotificationManager.areNotificationsEnabledForPackage(PKG_NAME, UID))
                .thenReturn(true);
        setupNotificationsEnabledPermissions();

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onDataLoaded(mAppEntryList);

        assertThat(mPreferenceCategory.getPreferenceCount()).isEqualTo(1);

        verify(mMockPreference).setTitle(LABEL);
        verify(mMockPreference).setIcon(any(Drawable.class));
    }

    @Test
    public void onCreate_notificationEnabled_isChecked() throws Exception {
        when(mMockNotificationManager.areNotificationsEnabledForPackage(PKG_NAME, UID))
                .thenReturn(true);
        setupNotificationsEnabledPermissions();

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onDataLoaded(mAppEntryList);

        verify(mMockPreference).setSecondaryActionChecked(true);
    }

    @Test
    public void onCreate_notificationDisabled_isNotChecked() throws Exception {
        when(mMockNotificationManager.areNotificationsEnabledForPackage(PKG_NAME, UID))
                .thenReturn(false);
        setupNotificationsEnabledPermissions();

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onDataLoaded(mAppEntryList);

        verify(mMockPreference).setSecondaryActionChecked(false);
    }

    @Test
    public void onCreate_importanceLocked_isNotEnabled() throws Exception {
        when(mMockNotificationManager.isImportanceLocked(PKG_NAME, UID)).thenReturn(true);

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onDataLoaded(mAppEntryList);

        verify(mMockPreference).setSecondaryActionEnabled(false);
    }

    @Test
    public void onCreate_noNotificationPermission_isNotEnabled() throws Exception {
        when(mMockNotificationManager.isImportanceLocked(PKG_NAME, UID)).thenReturn(false);

        PackageInfo packageInfo = new PackageInfo();
        packageInfo.requestedPermissions = new String[] {};
        when(mMockPackageManager.getPackageInfoAsUser(eq(PKG_NAME),
                eq(PackageManager.GET_PERMISSIONS), anyInt())).thenReturn(packageInfo);

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onDataLoaded(mAppEntryList);

        verify(mMockPreference).setSecondaryActionEnabled(false);
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

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onDataLoaded(mAppEntryList);

        verify(mMockPreference).setSecondaryActionEnabled(false);
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

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onDataLoaded(mAppEntryList);

        verify(mMockPreference).setSecondaryActionEnabled(false);
    }

    @Test
    public void onCreate_hasPermissions_isEnabled() throws Exception {
        setupNotificationsEnabledPermissions();
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onDataLoaded(mAppEntryList);

        verify(mMockPreference).setSecondaryActionEnabled(true);
    }

    @Test
    public void onCreate_targetSdkBelow33_systemFixedFlag_isNotEnabled() throws Exception {
        mApplicationInfo.targetSdkVersion = Build.VERSION_CODES.S;

        when(mMockNotificationManager.isImportanceLocked(PKG_NAME, UID)).thenReturn(false);

        PackageInfo packageInfo = new PackageInfo();
        packageInfo.requestedPermissions = new String[] {Manifest.permission.POST_NOTIFICATIONS};
        when(mMockPackageManager.getPackageInfoAsUser(eq(PKG_NAME),
                eq(PackageManager.GET_PERMISSIONS), anyInt())).thenReturn(packageInfo);

        when(mMockPackageManager.getPermissionFlags(eq(Manifest.permission.POST_NOTIFICATIONS),
                eq(PKG_NAME), any())).thenReturn(PackageManager.FLAG_PERMISSION_SYSTEM_FIXED);

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onDataLoaded(mAppEntryList);

        verify(mMockPreference).setSecondaryActionEnabled(false);
    }

    @Test
    public void onCreate_targetSdkBelow33_policyFixedFlag_isNotEnabled() throws Exception {
        mApplicationInfo.targetSdkVersion = Build.VERSION_CODES.S;

        when(mMockNotificationManager.isImportanceLocked(PKG_NAME, UID)).thenReturn(false);

        PackageInfo packageInfo = new PackageInfo();
        packageInfo.requestedPermissions = new String[] {Manifest.permission.POST_NOTIFICATIONS};
        when(mMockPackageManager.getPackageInfoAsUser(eq(PKG_NAME),
                eq(PackageManager.GET_PERMISSIONS), anyInt())).thenReturn(packageInfo);

        when(mMockPackageManager.getPermissionFlags(eq(Manifest.permission.POST_NOTIFICATIONS),
                eq(PKG_NAME), any())).thenReturn(PackageManager.FLAG_PERMISSION_POLICY_FIXED);

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onDataLoaded(mAppEntryList);

        verify(mMockPreference).setSecondaryActionEnabled(false);
    }

    @Test
    public void onCreate_targetSdkBelow33_doesNotHavePermission_isEnabled() throws Exception {
        mApplicationInfo.targetSdkVersion = Build.VERSION_CODES.S;

        when(mMockNotificationManager.isImportanceLocked(PKG_NAME, UID)).thenReturn(false);

        PackageInfo packageInfo = new PackageInfo();
        packageInfo.requestedPermissions = new String[] {};
        when(mMockPackageManager.getPackageInfoAsUser(eq(PKG_NAME),
                eq(PackageManager.GET_PERMISSIONS), anyInt())).thenReturn(packageInfo);

        when(mMockPackageManager.getPermissionFlags(eq(Manifest.permission.POST_NOTIFICATIONS),
                eq(PKG_NAME), any())).thenReturn(0);

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onDataLoaded(mAppEntryList);

        verify(mMockPreference).setSecondaryActionEnabled(true);
    }

    @Test
    public void toggle_setEnable_enablingNotification() throws Exception {
        when(mMockNotificationManager.areNotificationsEnabledForPackage(PKG_NAME, UID))
                .thenReturn(false);
        when(mMockNotificationManager.onlyHasDefaultChannel(PKG_NAME, UID)).thenReturn(false);
        setupNotificationsEnabledPermissions();

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onDataLoaded(mAppEntryList);

        verify(mMockPreference).setOnSecondaryActionClickListener(any());

        mPreferenceController.onSecondaryActionClick(PKG_NAME, UID, /* newValue= */ true);

        verify(mMockNotificationManager).setNotificationsEnabledForPackage(PKG_NAME, UID, true);
    }

    @Test
    public void toggle_setDisable_disablingNotification() throws Exception {
        when(mMockNotificationManager.areNotificationsEnabledForPackage(PKG_NAME, UID))
                .thenReturn(true);
        when(mMockNotificationManager.onlyHasDefaultChannel(PKG_NAME, UID)).thenReturn(false);
        setupNotificationsEnabledPermissions();

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onDataLoaded(mAppEntryList);

        verify(mMockPreference).setOnSecondaryActionClickListener(any());

        mPreferenceController.onSecondaryActionClick(PKG_NAME, UID, /* newValue= */ false);

        verify(mMockNotificationManager).setNotificationsEnabledForPackage(PKG_NAME, UID, false);
    }

    @Test
    public void toggle_onlyHasDefaultChannel_updateChannel() throws Exception {
        when(mMockNotificationManager.areNotificationsEnabledForPackage(PKG_NAME, UID))
                .thenReturn(false);
        when(mMockNotificationManager.onlyHasDefaultChannel(PKG_NAME, UID)).thenReturn(true);
        when(mMockNotificationManager
                .getNotificationChannelForPackage(
                        PKG_NAME, UID, NotificationChannel.DEFAULT_CHANNEL_ID, null, true))
                .thenReturn(mMockChannel);
        setupNotificationsEnabledPermissions();

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onDataLoaded(mAppEntryList);

        verify(mMockPreference).setOnSecondaryActionClickListener(any());

        mPreferenceController.onSecondaryActionClick(PKG_NAME, UID, /* newValue= */ true);

        verify(mMockNotificationManager)
                .updateNotificationChannelForPackage(PKG_NAME, UID, mMockChannel);
    }

    @Test
    @UiThreadTest
    public void clickPreference_shouldOpenApplicationDetailsFragment() throws Exception {
        setupNotificationsEnabledPermissions();
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onDataLoaded(mAppEntryList);

        verify(mMockPreference).setOnPreferenceClickListener(any());

        mPreferenceController.onPrimaryActionClick(PKG_NAME);

        verify(mFragmentController).launchFragment(any(ApplicationDetailsFragment.class));
    }

    private void setupNotificationsEnabledPermissions() throws Exception {
        when(mMockNotificationManager.isImportanceLocked(PKG_NAME, UID)).thenReturn(false);

        PackageInfo packageInfo = new PackageInfo();
        packageInfo.requestedPermissions = new String[] {Manifest.permission.POST_NOTIFICATIONS};
        when(mMockPackageManager.getPackageInfoAsUser(eq(PKG_NAME),
                eq(PackageManager.GET_PERMISSIONS), anyInt())).thenReturn(packageInfo);

        when(mMockPackageManager.getPermissionFlags(eq(Manifest.permission.POST_NOTIFICATIONS),
                eq(PKG_NAME), any())).thenReturn(0);
    }

    private class TestNotificationsAppListPreferenceController extends
            NotificationsAppListPreferenceController {

        TestNotificationsAppListPreferenceController(Context context, String preferenceKey,
                FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
            super(context, preferenceKey, fragmentController, uxRestrictions);
        }

        @Override
        CarUiTwoActionSwitchPreference createPreference() {
            return mMockPreference;
        }
    }
}
