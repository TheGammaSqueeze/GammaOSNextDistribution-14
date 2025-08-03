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

package com.android.car.settings.applications.performance;

import static android.car.settings.CarSettings.Secure.KEY_PACKAGES_DISABLED_ON_RESOURCE_OVERUSE;
import static android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.car.Car;
import android.car.drivingstate.CarUxRestrictions;
import android.car.watchdog.CarWatchdogManager;
import android.car.watchdog.PackageKillableState;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.UserHandle;
import android.provider.Settings;

import androidx.lifecycle.LifecycleOwner;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.common.ConfirmationDialogFragment;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.LogicalPreferenceGroup;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.TestLifecycleOwner;
import com.android.car.ui.preference.CarUiTwoActionTextPreference;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

@RunWith(AndroidJUnit4.class)
public final class PerfImpactingAppsPreferenceControllerTest {
    private static final int TEST_USER_ID = 100;
    private static final int TEST_PKG_UID = 10000001;
    private static final String TEST_PKG_NAME = "test.package.name";
    private static final int TEST_PRIVILEGE_PKG_UID = 10000002;
    private static final String TEST_PRIVILEGE_PKG_NAME = "test.privilege.package.name";
    private static final String TEST_DISABLED_PACKAGES_SETTING_STRING =
            TEST_PKG_NAME + ";" + TEST_PRIVILEGE_PKG_NAME;

    private final Context mContext = spy(ApplicationProvider.getApplicationContext());

    private MockitoSession mMockingSession;
    private LifecycleOwner mLifecycleOwner;
    private PerfImpactingAppsPreferenceController mController;
    private LogicalPreferenceGroup mPreferenceGroup;

    @Captor
    private ArgumentCaptor<Car.CarServiceLifecycleListener> mCarLifecycleCaptor;
    @Captor
    private ArgumentCaptor<ConfirmationDialogFragment> mDialogFragmentCaptor;
    @Mock
    private PackageManager mMockPackageManager;
    @Mock
    private FragmentController mMockFragmentController;
    @Mock
    private Car mMockCar;
    @Mock
    private CarWatchdogManager mMockCarWatchdogManager;

    @Before
    @UiThreadTest
    public void setUp() {
        mMockingSession = mockitoSession()
                .initMocks(this)
                .mockStatic(Car.class)
                .mockStatic(Settings.Secure.class)
                .strictness(Strictness.LENIENT)
                .startMocking();
        mLifecycleOwner = new TestLifecycleOwner();

        mDialogFragmentCaptor = ArgumentCaptor.forClass(ConfirmationDialogFragment.class);

        when(mContext.getPackageManager()).thenReturn(mMockPackageManager);
        when(Car.createCar(any(), any(), anyLong(), mCarLifecycleCaptor.capture())).then(
                invocation -> {
                    Car.CarServiceLifecycleListener listener = mCarLifecycleCaptor.getValue();
                    listener.onLifecycleChanged(mMockCar, true);
                    return mMockCar;
                });
        when(mMockCar.getCarManager(Car.CAR_WATCHDOG_SERVICE)).thenReturn(mMockCarWatchdogManager);
        when(mMockCarWatchdogManager.getPackageKillableStatesAsUser(
                UserHandle.getUserHandleForUid(TEST_PKG_UID)))
                .thenReturn(List.of(
                        new PackageKillableState(TEST_PKG_NAME, TEST_USER_ID,
                                PackageKillableState.KILLABLE_STATE_YES),
                        new PackageKillableState(TEST_PRIVILEGE_PKG_NAME, TEST_USER_ID,
                                PackageKillableState.KILLABLE_STATE_NEVER)));

        CarUxRestrictions restrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();

        PreferenceManager preferenceManager = new PreferenceManager(mContext);
        PreferenceScreen screen = preferenceManager.createPreferenceScreen(mContext);
        mPreferenceGroup = new LogicalPreferenceGroup(mContext);
        screen.addPreference(mPreferenceGroup);

        mController = new PerfImpactingAppsPreferenceController(mContext,
                /* preferenceKey= */ "key", mMockFragmentController, restrictions);

        PreferenceControllerTestUtil.assignPreference(mController, mPreferenceGroup);

        initController();
    }

    @After
    @UiThreadTest
    public void tearDown() {
        (new File(mContext.getDataDir(), TEST_PKG_NAME)).delete();
        (new File(mContext.getDataDir(), TEST_PRIVILEGE_PKG_NAME)).delete();

        mMockingSession.finishMocking();
    }

    @Test
    @UiThreadTest
    public void onPreferenceClick_primaryAppAction_sendAppDetailIntent() {
        doNothing().when(mContext).startActivity(any());

        Preference actualPreference = mPreferenceGroup.getPreference(0);

        Preference.OnPreferenceClickListener onPreferenceClickListener =
                actualPreference.getOnPreferenceClickListener();

        onPreferenceClickListener.onPreferenceClick(actualPreference);

        ArgumentCaptor<Intent> captor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext).startActivity(captor.capture());
        Intent intent = captor.getValue();
        assertThat(intent).isNotNull();
        assertWithMessage("Intent action").that(intent.getAction())
                .isEqualTo(ACTION_APPLICATION_DETAILS_SETTINGS);
        assertWithMessage("Intent data").that(intent.getData())
                .isEqualTo(Uri.parse("package:" + TEST_PKG_NAME));
    }

    @Test
    @UiThreadTest
    public void onPreferenceClick_showConfirmationDialog_prioritizeApp() {
        CarUiTwoActionTextPreference actualPreference =
                (CarUiTwoActionTextPreference) mPreferenceGroup.getPreference(0);

        actualPreference.performSecondaryActionClick();

        verify(mMockFragmentController).showDialog(mDialogFragmentCaptor.capture(), anyString());
        ConfirmationDialogFragment dialogFragment = mDialogFragmentCaptor.getValue();

        assertThat(dialogFragment).isNotNull();

        dialogFragment.onClick(dialogFragment.getDialog(), DialogInterface.BUTTON_POSITIVE);

        verify(mMockCarWatchdogManager).setKillablePackageAsUser(TEST_PKG_NAME,
                UserHandle.getUserHandleForUid(TEST_PKG_UID),
                /* isKillable= */ false);
    }

    @Test
    @UiThreadTest
    public void onPreferenceClick_showConfirmationDialog_prioritizePrivilegedApp() {
        CarUiTwoActionTextPreference actualPreference =
                (CarUiTwoActionTextPreference) mPreferenceGroup.getPreference(1);

        actualPreference.performSecondaryActionClick();

        verify(mMockFragmentController).showDialog(mDialogFragmentCaptor.capture(), anyString());
        ConfirmationDialogFragment dialogFragment = mDialogFragmentCaptor.getValue();

        assertThat(dialogFragment).isNotNull();

        dialogFragment.onClick(dialogFragment.getDialog(), DialogInterface.BUTTON_POSITIVE);

        verify(mMockPackageManager).setApplicationEnabledSetting(TEST_PRIVILEGE_PKG_NAME,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED, /* flags= */ 0);
    }

    @Test
    @UiThreadTest
    public void onPreferenceClick_showConfirmationDialog_cancel() {
        CarUiTwoActionTextPreference actualPreference =
                (CarUiTwoActionTextPreference) mPreferenceGroup.getPreference(1);

        actualPreference.performSecondaryActionClick();

        verify(mMockFragmentController).showDialog(mDialogFragmentCaptor.capture(), anyString());
        ConfirmationDialogFragment dialogFragment = mDialogFragmentCaptor.getValue();

        assertThat(dialogFragment).isNotNull();

        dialogFragment.onClick(dialogFragment.getDialog(), DialogInterface.BUTTON_NEGATIVE);

        verify(mMockPackageManager, never())
                .setApplicationEnabledSetting(anyString(), anyInt(), anyInt());
        verifyNoMoreInteractions(mMockCarWatchdogManager);
    }

    @Test
    @UiThreadTest
    public void onCreate_perfImpactingApps_withNoPackages() {
        when(Settings.Secure.getString(any(), eq(KEY_PACKAGES_DISABLED_ON_RESOURCE_OVERUSE)))
                .thenReturn("");

        mController.onDestroy(mLifecycleOwner);

        mController.onCreate(mLifecycleOwner);

        assertWithMessage("Preference group count")
                .that(mPreferenceGroup.getPreferenceCount()).isEqualTo(0);
    }

    private void initController() {
        List<ResolveInfo> expectedResultInfos = getResultInfos();

        when(Settings.Secure.getString(any(), eq(KEY_PACKAGES_DISABLED_ON_RESOURCE_OVERUSE)))
                .thenReturn(TEST_DISABLED_PACKAGES_SETTING_STRING);
        when(mMockPackageManager.queryIntentActivities(any(), any()))
                .thenReturn(expectedResultInfos);

        mController.onCreate(mLifecycleOwner);

        List<CarUiTwoActionTextPreference> expectedPreferences =
                getPreferences(expectedResultInfos);

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(expectedPreferences.size());

        for (int idx = 0; idx < expectedPreferences.size(); idx++) {
            assertThatPreferenceAreEqual(idx,
                    (CarUiTwoActionTextPreference) mPreferenceGroup.getPreference(idx),
                    expectedPreferences.get(idx));
        }
    }

    private List<CarUiTwoActionTextPreference> getPreferences(List<ResolveInfo> resolveInfos) {
        return resolveInfos.stream().map(
                resolveInfo ->
                        new PerfImpactingAppsPreferenceController.PerformanceImpactingAppPreference(
                                mContext, resolveInfo.activityInfo.applicationInfo))
                .collect(Collectors.toList());
    }

    private List<ResolveInfo> getResultInfos() {
        // Non-privileged Package
        ResolveInfo resolveInfo1 = new ResolveInfo();
        resolveInfo1.activityInfo = new ActivityInfo();
        resolveInfo1.activityInfo.applicationInfo = new ApplicationInfo();
        resolveInfo1.activityInfo.applicationInfo.uid = TEST_PKG_UID;
        resolveInfo1.activityInfo.applicationInfo.packageName = TEST_PKG_NAME;

        File appFile = new File(mContext.getDataDir(), TEST_PKG_NAME);
        assertWithMessage("%s dir", TEST_PKG_NAME).that(appFile.mkdir()).isTrue();

        resolveInfo1.activityInfo.applicationInfo.sourceDir = appFile.getAbsolutePath();

        // Privileged Package
        ResolveInfo resolveInfo2 = new ResolveInfo();
        resolveInfo2.activityInfo = new ActivityInfo();
        resolveInfo2.activityInfo.applicationInfo = new ApplicationInfo();
        resolveInfo2.activityInfo.applicationInfo.uid = TEST_PRIVILEGE_PKG_UID;
        resolveInfo2.activityInfo.applicationInfo.packageName = TEST_PRIVILEGE_PKG_NAME;

        appFile = new File(mContext.getDataDir(), TEST_PRIVILEGE_PKG_NAME);
        assertWithMessage("%s dir", TEST_PRIVILEGE_PKG_NAME).that(appFile.mkdir()).isTrue();

        resolveInfo2.activityInfo.applicationInfo.sourceDir = appFile.getAbsolutePath();

        return List.of(resolveInfo1, resolveInfo2);
    }

    private static void assertThatPreferenceAreEqual(int index, CarUiTwoActionTextPreference p1,
            CarUiTwoActionTextPreference p2) {
        assertWithMessage("Preference %s key", index).that(p1.getKey()).isEqualTo(p2.getKey());
        assertWithMessage("Preference %s title", index).that(p1.getTitle().toString())
                .isEqualTo(p2.getTitle().toString());
        assertWithMessage("Preference %s secondary action text", index)
                .that(p2.getSecondaryActionText().toString())
                .isEqualTo(p2.getSecondaryActionText().toString());
    }
}
