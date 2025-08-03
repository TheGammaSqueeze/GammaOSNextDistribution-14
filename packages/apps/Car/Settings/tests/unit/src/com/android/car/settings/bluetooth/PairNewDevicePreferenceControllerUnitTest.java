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

package com.android.car.settings.bluetooth;

import static com.android.car.settings.common.PreferenceController.AVAILABLE_FOR_VIEWING;
import static com.android.car.settings.common.PreferenceController.CONDITIONALLY_UNAVAILABLE;
import static com.android.car.settings.enterprise.ActionDisabledByAdminDialogFragment.DISABLED_BY_ADMIN_CONFIRM_DIALOG_TAG;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.os.UserManager;

import androidx.lifecycle.LifecycleOwner;
import androidx.preference.SwitchPreference;
import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;

import com.android.car.settings.R;
import com.android.car.settings.common.ColoredSwitchPreference;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.common.Settings;
import com.android.car.settings.testutils.EnterpriseTestUtils;
import com.android.car.settings.testutils.TestLifecycleOwner;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;

public final class PairNewDevicePreferenceControllerUnitTest {
    private static final String TEST_RESTRICTION =
            android.os.UserManager.DISALLOW_CONFIG_BLUETOOTH;

    private LifecycleOwner mLifecycleOwner;
    private Context mContext = spy(ApplicationProvider.getApplicationContext());
    private SwitchPreference mSwitchPreference;
    private PairNewDevicePreferenceController mPreferenceController;
    private CarUxRestrictions mCarUxRestrictions;

    @Mock
    private UserManager mUserManager;

    @Mock
    private FragmentController mFragmentController;

    @Mock
    private PackageManager mMockPm;

    @Mock
    private Resources mResources;

    @Before
    public void setUp() {
        mLifecycleOwner = new TestLifecycleOwner();
        MockitoAnnotations.initMocks(this);
        when(mContext.getSystemService(UserManager.class)).thenReturn(mUserManager);

        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();
        mPreferenceController = new PairNewDevicePreferenceController(mContext, /* preferenceKey= */
                "key", mFragmentController, mCarUxRestrictions);
        mSwitchPreference = new ColoredSwitchPreference(mContext);
        mSwitchPreference.setFragment(FragmentController.class.getCanonicalName());
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mSwitchPreference);
    }

    @Test
    public void restrictedByDpm_availabilityIsAvailableForViewing() {
        EnterpriseTestUtils.mockUserRestrictionSetByDpm(mUserManager, TEST_RESTRICTION, true);
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);

        mSwitchPreference.performClick();

        assertThat(mPreferenceController.getAvailabilityStatus()).isEqualTo(AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void restrictedByDpm_availabilityIsAvailableForViewing_zoneWrite() {
        EnterpriseTestUtils.mockUserRestrictionSetByDpm(mUserManager, TEST_RESTRICTION, true);
        mPreferenceController.setAvailabilityStatusForZone("write");
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);

        mSwitchPreference.performClick();

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE_FOR_VIEWING);
    }

    @Test
    @UiThreadTest
    public void restrictedByDpm_availabilityIsAvailableForViewing_zoneRead() {
        EnterpriseTestUtils.mockUserRestrictionSetByDpm(mUserManager, TEST_RESTRICTION, true);
        mPreferenceController.setAvailabilityStatusForZone("read");
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);

        mSwitchPreference.performClick();

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void restrictedByDpm_availabilityIsAvailableForViewing_zoneHidden() {
        EnterpriseTestUtils.mockUserRestrictionSetByDpm(mUserManager, TEST_RESTRICTION, true);
        mPreferenceController.setAvailabilityStatusForZone("hidden");
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);

        mSwitchPreference.performClick();

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void restrictedByDpm_showsDisabledByAdminDialog() {
        EnterpriseTestUtils.mockUserRestrictionSetByDpm(mUserManager, TEST_RESTRICTION, true);
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);

        mSwitchPreference.performClick();

        assertShowingDisabledByAdminDialog();
    }

    @Test
    public void preferenceClicked_triggersCustomPairingFlow_whenSystemApplicationIsFound() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);

        when(mContext.getPackageManager()).thenReturn(mMockPm);

        Intent intent = new Intent(Settings.ACTION_PAIR_DEVICE_SETTINGS);
        mSwitchPreference.setIntent(intent);

        String packageName = "some.test.package";

        when(mContext.getResources()).thenReturn(mResources);
        when(mResources.getBoolean(
                R.bool.config_use_custom_pair_device_flow)).thenReturn(true);
        when(mMockPm.queryIntentActivities(eq(intent), anyInt())).thenReturn(
                Collections.singletonList(createSystemResolveInfo(packageName)));

        doNothing().when(mContext).startActivity(any());

        assertThat(mSwitchPreference.getOnPreferenceClickListener().onPreferenceClick(
                mSwitchPreference)).isTrue();

        ArgumentCaptor<Intent> captor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext).startActivity(captor.capture());

        Intent capturedIntent = captor.getValue();
        assertThat(capturedIntent.getAction()).isEqualTo(Settings.ACTION_PAIR_DEVICE_SETTINGS);
        assertThat(capturedIntent.getPackage()).isEqualTo(packageName);
    }

    @Test
    public void preferenceClicked_triggersDefaultPairingFlow_whenNoMatchingApplicationsFound() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);

        Intent intent = new Intent();
        intent.setAction(Settings.ACTION_PAIR_DEVICE_SETTINGS);
        mSwitchPreference.setIntent(intent);

        when(mContext.getResources()).thenReturn(mResources);
        when(mResources.getBoolean(
                R.bool.config_use_custom_pair_device_flow)).thenReturn(true);
        when(mContext.getPackageManager()).thenReturn(mMockPm);
        when(mMockPm.queryIntentActivities(eq(intent), anyInt())).thenReturn(
                Collections.emptyList());

        assertThat(mSwitchPreference.getOnPreferenceClickListener().onPreferenceClick(
                mSwitchPreference)).isFalse();

        verify(mContext, never()).startActivity(any());
    }

    @Test
    public void preferenceClicked_triggersDefaultPairingFlow_whenNonSystemApplicationIsFound() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);

        when(mContext.getPackageManager()).thenReturn(mMockPm);

        Intent intent = new Intent(Settings.ACTION_PAIR_DEVICE_SETTINGS);
        mSwitchPreference.setIntent(intent);

        String packageName = "some.test.package";

        when(mContext.getResources()).thenReturn(mResources);
        when(mResources.getBoolean(
                R.bool.config_use_custom_pair_device_flow)).thenReturn(true);
        when(mMockPm.queryIntentActivities(eq(intent), anyInt())).thenReturn(
                Collections.singletonList(createNonSystemResolveInfo(packageName)));

        doNothing().when(mContext).startActivity(any());

        assertThat(mSwitchPreference.getOnPreferenceClickListener().onPreferenceClick(
                mSwitchPreference)).isFalse();

        verify(mContext, never()).startActivity(any());
    }

    @Test
    public void preferenceClicked_triggersDefaultPairingFlow_whenCustomPairingFlowIsDisabled() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);

        when(mContext.getResources()).thenReturn(mResources);
        when(mResources.getBoolean(
                R.bool.config_use_custom_pair_device_flow)).thenReturn(false);

        assertThat(mSwitchPreference.getOnPreferenceClickListener().onPreferenceClick(
                mSwitchPreference)).isFalse();
    }

    private void assertShowingDisabledByAdminDialog() {
        verify(mFragmentController).showDialog(any(), eq(DISABLED_BY_ADMIN_CONFIRM_DIALOG_TAG));
    }

    private ResolveInfo createSystemResolveInfo(String packageName) {
        ResolveInfo resolveInfo = createNonSystemResolveInfo(packageName);
        resolveInfo.activityInfo.applicationInfo.flags |= ApplicationInfo.FLAG_SYSTEM;
        return resolveInfo;
    }

    private ResolveInfo createNonSystemResolveInfo(String packageName) {
        ApplicationInfo applicationInfo = new ApplicationInfo();

        ActivityInfo activityInfo = new ActivityInfo();
        activityInfo.applicationInfo = applicationInfo;

        ResolveInfo resolveInfo = new ResolveInfo();
        resolveInfo.activityInfo = activityInfo;
        resolveInfo.activityInfo.packageName = packageName;

        return resolveInfo;
    }
}
