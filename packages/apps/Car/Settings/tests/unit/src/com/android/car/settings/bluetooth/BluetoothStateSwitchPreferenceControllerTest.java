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

package com.android.car.settings.bluetooth;

import static android.os.UserManager.DISALLOW_BLUETOOTH;

import static com.android.car.settings.common.PreferenceController.AVAILABLE;
import static com.android.car.settings.common.PreferenceController.AVAILABLE_FOR_VIEWING;
import static com.android.car.settings.common.PreferenceController.CONDITIONALLY_UNAVAILABLE;
import static com.android.car.settings.common.PreferenceController.DISABLED_FOR_PROFILE;
import static com.android.car.settings.common.PreferenceController.UNSUPPORTED_ON_DEVICE;
import static com.android.car.settings.enterprise.ActionDisabledByAdminDialogFragment.DISABLED_BY_ADMIN_CONFIRM_DIALOG_TAG;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothAdapter;
import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.UserManager;

import androidx.lifecycle.LifecycleOwner;
import androidx.preference.SwitchPreference;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.common.ColoredSwitchPreference;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.BluetoothTestUtils;
import com.android.car.settings.testutils.EnterpriseTestUtils;
import com.android.car.settings.testutils.TestLifecycleOwner;
import com.android.settingslib.bluetooth.LocalBluetoothManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class BluetoothStateSwitchPreferenceControllerTest {
    private static final String TEST_RESTRICTION =
            android.os.UserManager.DISALLOW_CONFIG_BLUETOOTH;

    private LifecycleOwner mLifecycleOwner;
    private Context mContext = spy(ApplicationProvider.getApplicationContext());
    private SwitchPreference mSwitchPreference;
    private BluetoothStateSwitchPreferenceController mPreferenceController;
    private LocalBluetoothManager mLocalBluetoothManager;
    private CarUxRestrictions mCarUxRestrictions;

    @Mock
    private PackageManager mPackageManager;

    @Mock
    private UserManager mUserManager;

    @Mock
    private FragmentController mFragmentController;

    @Before
    public void setUp() {
        mLifecycleOwner = new TestLifecycleOwner();
        MockitoAnnotations.initMocks(this);

        mLocalBluetoothManager = LocalBluetoothManager.getInstance(mContext, /* onInitCallback= */
                null);

        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();

        mSwitchPreference = new ColoredSwitchPreference(mContext);
        mPreferenceController = new BluetoothStateSwitchPreferenceController(mContext,
                /* preferenceKey= */ "key", mFragmentController, mCarUxRestrictions);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mSwitchPreference);

        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)).thenReturn(true);
    }

    @Test
    public void onStart_setsBluetoothManagerForegroundActivity() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);

        assertThat(mLocalBluetoothManager.getForegroundActivity()).isEqualTo(mContext);
    }

    @Test
    public void onStop_clearsBluetoothManagerForegroundActivity() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);
        mPreferenceController.onStop(mLifecycleOwner);

        assertThat(mLocalBluetoothManager.getForegroundActivity()).isNull();
    }

    @Test
    public void onStart_initializesSwitchState() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);

        assertThat(mSwitchPreference.isChecked()).isEqualTo(
                BluetoothAdapter.getDefaultAdapter().isEnabled());
    }

    @Test
    public void switchClicked_disabled_checksAndDisablesSwitch() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);
        mSwitchPreference.setChecked(false);
        BluetoothTestUtils.setBluetoothState(mContext, /* enable= */ false);

        mSwitchPreference.performClick();

        assertThat(mSwitchPreference.isChecked()).isTrue();
        assertThat(mSwitchPreference.isEnabled()).isFalse();
    }

    @Test
    public void testGetAvailabilityStatus_bluetoothFeatureDisabled() {
        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)).thenReturn(false);

        assertThat(mPreferenceController.getAvailabilityStatus()).isEqualTo(UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void testGetAvailabilityStatus_noRestrictions() {
        assertThat(mPreferenceController.getAvailabilityStatus()).isEqualTo(AVAILABLE);
    }

    @Test
    public void testGetAvailabilityStatus_restrictedByUm() {
        when(mContext.getSystemService(UserManager.class)).thenReturn(mUserManager);
        EnterpriseTestUtils.mockUserRestrictionSetByUm(mUserManager, TEST_RESTRICTION, true);

        assertThat(mPreferenceController.getAvailabilityStatus()).isEqualTo(DISABLED_FOR_PROFILE);
    }

    @Test
    public void restrictedByDpm_availabilityIsAvailableForViewing() {
        when(mContext.getSystemService(UserManager.class)).thenReturn(mUserManager);
        EnterpriseTestUtils.mockUserRestrictionSetByDpm(mUserManager, TEST_RESTRICTION, true);
        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mPreferenceController.getAvailabilityStatus()).isEqualTo(AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void restrictedByDpm_availabilityIsAvailableForViewing_zoneWrite() {
        when(mContext.getSystemService(UserManager.class)).thenReturn(mUserManager);
        EnterpriseTestUtils.mockUserRestrictionSetByDpm(mUserManager, TEST_RESTRICTION, true);
        mPreferenceController.setAvailabilityStatusForZone("write");
        mPreferenceController.onCreate(mLifecycleOwner);

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void restrictedByDpm_availabilityIsAvailableForViewing_zoneRead() {
        when(mContext.getSystemService(UserManager.class)).thenReturn(mUserManager);
        EnterpriseTestUtils.mockUserRestrictionSetByDpm(mUserManager, TEST_RESTRICTION, true);
        mPreferenceController.setAvailabilityStatusForZone("read");

        mPreferenceController.onCreate(mLifecycleOwner);

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void restrictedByDpm_availabilityIsAvailableForViewing_zoneHidden() {
        when(mContext.getSystemService(UserManager.class)).thenReturn(mUserManager);
        EnterpriseTestUtils.mockUserRestrictionSetByDpm(mUserManager, TEST_RESTRICTION, true);
        mPreferenceController.setAvailabilityStatusForZone("hidden");
        mPreferenceController.onCreate(mLifecycleOwner);

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void restrictedByDpm_disableSwitchPreference() {
        when(mContext.getSystemService(UserManager.class)).thenReturn(mUserManager);
        EnterpriseTestUtils.mockUserRestrictionSetByDpm(mUserManager, TEST_RESTRICTION, true);
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);
        mSwitchPreference.setEnabled(true);
        BluetoothTestUtils.setBluetoothState(mContext, /* enable= */ true);

        mSwitchPreference.performClick();

        assertThat(mSwitchPreference.isEnabled()).isFalse();
    }

    @Test
    public void restrictedByDpm_showsDisabledByAdminDialog() {
        when(mContext.getSystemService(UserManager.class)).thenReturn(mUserManager);
        EnterpriseTestUtils.mockUserRestrictionSetByDpm(mUserManager, TEST_RESTRICTION, true);
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);
        BluetoothTestUtils.setBluetoothState(mContext, /* enable= */ true);

        mSwitchPreference.performClick();

        assertShowingDisabledByAdminDialog();
    }

    private void assertShowingDisabledByAdminDialog() {
        verify(mFragmentController).showDialog(any(), eq(DISABLED_BY_ADMIN_CONFIRM_DIALOG_TAG));
    }

    @Test
    public void switchClicked_enabled_unchecksAndDisablesSwitch() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);
        mSwitchPreference.setChecked(true);
        BluetoothTestUtils.setBluetoothState(mContext, /* enable= */ true);

        mSwitchPreference.performClick();

        assertThat(mSwitchPreference.isChecked()).isFalse();
        assertThat(mSwitchPreference.isEnabled()).isFalse();
    }

    @Test
    public void stateChanged_turningOn_setsSwitchChecked() {
        mPreferenceController.onCreate(mLifecycleOwner);

        mPreferenceController.handleStateChanged(BluetoothAdapter.STATE_TURNING_ON);

        assertThat(mSwitchPreference.isChecked()).isTrue();
    }

    @Test
    public void stateChanged_turningOn_setsSwitchDisabled() {
        mPreferenceController.onCreate(mLifecycleOwner);

        mPreferenceController.handleStateChanged(BluetoothAdapter.STATE_TURNING_ON);

        assertThat(mSwitchPreference.isEnabled()).isFalse();
    }

    @Test
    public void stateChanged_on_setsSwitchChecked() {
        mPreferenceController.onCreate(mLifecycleOwner);

        mPreferenceController.handleStateChanged(BluetoothAdapter.STATE_ON);

        assertThat(mSwitchPreference.isChecked()).isTrue();
    }

    @Test
    public void stateChanged_on_setsSwitchEnabled() {
        mPreferenceController.onCreate(mLifecycleOwner);

        mPreferenceController.handleStateChanged(BluetoothAdapter.STATE_ON);

        assertThat(mSwitchPreference.isEnabled()).isTrue();
    }

    @Test
    public void stateChanged_turningOff_setsSwitchUnchecked() {
        mPreferenceController.onCreate(mLifecycleOwner);

        mPreferenceController.handleStateChanged(BluetoothAdapter.STATE_TURNING_OFF);

        assertThat(mSwitchPreference.isChecked()).isFalse();
    }

    @Test
    public void stateChanged_turningOff_setsSwitchDisabled() {
        mPreferenceController.onCreate(mLifecycleOwner);

        mPreferenceController.handleStateChanged(BluetoothAdapter.STATE_TURNING_OFF);

        assertThat(mSwitchPreference.isEnabled()).isFalse();
    }

    @Test
    public void stateChanged_off_setsSwitchUnchecked() {
        mPreferenceController.onCreate(mLifecycleOwner);

        mPreferenceController.handleStateChanged(BluetoothAdapter.STATE_OFF);

        assertThat(mSwitchPreference.isChecked()).isFalse();
    }

    @Test
    public void stateChanged_off_setsSwitchEnabled() {
        mPreferenceController.onCreate(mLifecycleOwner);

        mPreferenceController.handleStateChanged(BluetoothAdapter.STATE_OFF);

        assertThat(mSwitchPreference.isEnabled()).isTrue();
    }

    @Test
    public void stateChanged_on_userRestricted_setsSwitchDisabled() {
        when(mContext.getSystemService(UserManager.class)).thenReturn(mUserManager);
        mPreferenceController.onCreate(mLifecycleOwner);
        when(mUserManager.hasUserRestriction(DISALLOW_BLUETOOTH)).thenReturn(true);
        mPreferenceController.handleStateChanged(BluetoothAdapter.STATE_ON);

        assertThat(mSwitchPreference.isEnabled()).isFalse();
    }

    @Test
    public void stateChanged_off_userRestricted_setsSwitchDisabled() {
        when(mContext.getSystemService(UserManager.class)).thenReturn(mUserManager);
        mPreferenceController.onCreate(mLifecycleOwner);
        when(mUserManager.hasUserRestriction(DISALLOW_BLUETOOTH)).thenReturn(true);

        mPreferenceController.handleStateChanged(BluetoothAdapter.STATE_OFF);

        assertThat(mSwitchPreference.isEnabled()).isFalse();
    }

    @Test
    public void onPolicyChanged_enabled_setsSwitchEnabled() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mSwitchPreference.setEnabled(false);

        mPreferenceController.mPowerPolicyListener.getPolicyChangeHandler()
                .handlePolicyChange(/* isOn= */ true);

        assertThat(mSwitchPreference.isEnabled()).isTrue();
    }

    @Test
    public void onPolicyChanged_disabled_setsSwitchDisabled() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mSwitchPreference.setEnabled(true);

        mPreferenceController.mPowerPolicyListener.getPolicyChangeHandler()
                .handlePolicyChange(/* isOn= */ false);

        assertThat(mSwitchPreference.isEnabled()).isFalse();
    }
}
