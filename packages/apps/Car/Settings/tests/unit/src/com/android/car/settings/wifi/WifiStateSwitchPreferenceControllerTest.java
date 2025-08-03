/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.car.settings.wifi;

import static com.android.car.settings.common.PreferenceController.AVAILABLE;
import static com.android.car.settings.common.PreferenceController.AVAILABLE_FOR_VIEWING;
import static com.android.car.settings.common.PreferenceController.CONDITIONALLY_UNAVAILABLE;
import static com.android.car.settings.common.PreferenceController.UNSUPPORTED_ON_DEVICE;
import static com.android.car.settings.enterprise.ActionDisabledByAdminDialogFragment.DISABLED_BY_ADMIN_CONFIRM_DIALOG_TAG;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.os.UserManager;

import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.common.ColoredSwitchPreference;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.enterprise.ActionDisabledByAdminDialogFragment;
import com.android.car.settings.testutils.EnterpriseTestUtils;
import com.android.car.settings.testutils.TestLifecycleOwner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class WifiStateSwitchPreferenceControllerTest {
    private static final String TEST_RESTRICTION =
            android.os.UserManager.DISALLOW_CONFIG_WIFI;
    private Context mContext = spy(ApplicationProvider.getApplicationContext());
    private ColoredSwitchPreference mSwitchPreference;
    private LifecycleOwner mLifecycleOwner;
    private WifiStateSwitchPreferenceController mPreferenceController;
    private CarUxRestrictions mCarUxRestrictions;

    @Mock
    private FragmentController mFragmentController;
    @Mock
    private Lifecycle mMockLifecycle;
    @Mock
    private PackageManager mMockPackageManager;
    @Mock
    private UserManager mMockUserManager;
    @Mock
    private WifiManager mMockWifiManager;

    @Before
    @UiThreadTest
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mLifecycleOwner = new TestLifecycleOwner();
        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();
        mSwitchPreference = new ColoredSwitchPreference(mContext);
        mPreferenceController = new WifiStateSwitchPreferenceController(mContext,
                /* preferenceKey= */ "key", mFragmentController, mCarUxRestrictions);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mSwitchPreference);

        when(mContext.getPackageManager()).thenReturn(mMockPackageManager);
        when(mContext.getSystemService(WifiManager.class)).thenReturn(mMockWifiManager);
        when(mContext.getSystemService(UserManager.class)).thenReturn(mMockUserManager);
        when(mFragmentController.getSettingsLifecycle()).thenReturn(mMockLifecycle);
    }

    @Test
    public void onWifiStateChanged_disabled_setsSwitchUnchecked() {
        initializePreference(/* checked= */ true, /* enabled= */ true);
        mPreferenceController.onWifiStateChanged(WifiManager.WIFI_STATE_DISABLED);

        assertThat(mSwitchPreference.isChecked()).isFalse();
    }

    @Test
    public void onWifiStateChanged_enabled_setsSwitchChecked() {
        initializePreference(/* checked= */ false, /* enabled= */ true);
        mPreferenceController.onWifiStateChanged(WifiManager.WIFI_STATE_ENABLED);

        assertThat(mSwitchPreference.isChecked()).isTrue();
    }

    @Test
    public void onWifiStateChanged_enabling_setsSwitchChecked() {
        initializePreference(/* checked= */ false, /* enabled= */ true);
        mPreferenceController.onWifiStateChanged(WifiManager.WIFI_STATE_ENABLING);

        assertThat(mSwitchPreference.isChecked()).isTrue();
    }

    @Test
    public void onPolicyChanged_enabled_setsSwitchEnabled() {
        initializePreference(/* checked= */ false, /* enabled= */ false);

        mPreferenceController.mPowerPolicyListener.getPolicyChangeHandler()
                .handlePolicyChange(/* isOn= */ true);

        assertThat(mSwitchPreference.isEnabled()).isTrue();
    }

    @Test
    public void onPolicyChanged_disabled_setsSwitchDisabled() {
        initializePreference(/* checked= */ false, /* enabled= */ true);

        mPreferenceController.mPowerPolicyListener.getPolicyChangeHandler()
                .handlePolicyChange(/* isOn= */ false);

        assertThat(mSwitchPreference.isEnabled()).isFalse();
    }

    @Test
    public void getAvailabilityStatus_wifiAvailable_notRestricted_available() {
        when(mMockPackageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)).thenReturn(true);

        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mPreferenceController.getAvailabilityStatus()).isEqualTo(AVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_wifiAvailable_notRestricted_available_zoneWrite() {
        when(mMockPackageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)).thenReturn(true);

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_wifiAvailable_notRestricted_available_zoneRead() {
        when(mMockPackageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)).thenReturn(true);

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void getAvailabilityStatus_wifiAvailable_notRestricted_available_zoneHidden() {
        when(mMockPackageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)).thenReturn(true);

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_wifiAvailable_restrictedByDpm_viewing() {
        when(mMockPackageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)).thenReturn(true);
        EnterpriseTestUtils
                .mockUserRestrictionSetByDpm(mMockUserManager, TEST_RESTRICTION, true);

        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mPreferenceController.getAvailabilityStatus()).isEqualTo(AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void getAvailabilityStatus_wifiAvailable_restrictedByDpm_viewing_zoneWrite() {
        when(mMockPackageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)).thenReturn(true);
        EnterpriseTestUtils
                .mockUserRestrictionSetByDpm(mMockUserManager, TEST_RESTRICTION, true);

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void getAvailabilityStatus_wifiAvailable_restrictedByDpm_viewing_zoneRead() {
        when(mMockPackageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)).thenReturn(true);
        EnterpriseTestUtils
                .mockUserRestrictionSetByDpm(mMockUserManager, TEST_RESTRICTION, true);

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void getAvailabilityStatus_wifiAvailable_restrictedByDpm_viewing_zoneHidden() {
        when(mMockPackageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)).thenReturn(true);
        EnterpriseTestUtils
                .mockUserRestrictionSetByDpm(mMockUserManager, TEST_RESTRICTION, true);

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_wifiNotAvailable_unsupportedOnDevice() {
        when(mMockPackageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)).thenReturn(false);

        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mPreferenceController.getAvailabilityStatus()).isEqualTo(UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_wifiNotAvailable_unsupportedOnDevice_zoneWrite() {
        when(mMockPackageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)).thenReturn(false);

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_wifiNotAvailable_unsupportedOnDevice_zoneRead() {
        when(mMockPackageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)).thenReturn(false);

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_wifiNotAvailable_unsupportedOnDevice_zoneHidden() {
        when(mMockPackageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)).thenReturn(false);

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), UNSUPPORTED_ON_DEVICE);
    }

    @Test
    @UiThreadTest
    public void onPreferenceClicked_wifiAvailable_restrictedByDpm_showAdminDialog() {
        when(mMockPackageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)).thenReturn(true);
        EnterpriseTestUtils
                .mockUserRestrictionSetByDpm(mMockUserManager, TEST_RESTRICTION, true);

        mPreferenceController.onCreate(mLifecycleOwner);
        mSwitchPreference.performClick();

        assertShowingDisabledByAdminDialog();
    }

    private void assertShowingDisabledByAdminDialog() {
        verify(mFragmentController).showDialog(any(ActionDisabledByAdminDialogFragment.class),
                eq(DISABLED_BY_ADMIN_CONFIRM_DIALOG_TAG));
    }

    private void initializePreference(boolean checked, boolean enabled) {
        when(mMockPackageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)).thenReturn(true);
        when(mMockWifiManager.isWifiEnabled()).thenReturn(enabled);
        mSwitchPreference.setChecked(checked);
        mSwitchPreference.setEnabled(enabled);

        mPreferenceController.onCreate(mLifecycleOwner);
    }
}
