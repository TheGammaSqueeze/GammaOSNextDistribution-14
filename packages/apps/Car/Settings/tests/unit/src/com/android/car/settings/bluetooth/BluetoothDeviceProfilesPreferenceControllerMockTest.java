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

package com.android.car.settings.bluetooth;

import static com.android.car.settings.common.PreferenceController.AVAILABLE;
import static com.android.car.settings.common.PreferenceController.AVAILABLE_FOR_VIEWING;
import static com.android.car.settings.common.PreferenceController.CONDITIONALLY_UNAVAILABLE;
import static com.android.car.settings.common.PreferenceController.DISABLED_FOR_PROFILE;
import static com.android.car.settings.enterprise.ActionDisabledByAdminDialogFragment.DISABLED_BY_ADMIN_CONFIRM_DIALOG_TAG;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.os.UserManager;
import android.widget.Toast;

import androidx.lifecycle.LifecycleOwner;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.LogicalPreferenceGroup;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.enterprise.ActionDisabledByAdminDialogFragment;
import com.android.car.settings.testutils.BluetoothTestUtils;
import com.android.car.settings.testutils.EnterpriseTestUtils;
import com.android.car.settings.testutils.TestLifecycleOwner;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.settingslib.bluetooth.CachedBluetoothDevice;
import com.android.settingslib.bluetooth.LocalBluetoothProfile;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public final class BluetoothDeviceProfilesPreferenceControllerMockTest {
    private static final String TEST_RESTRICTION =
            android.os.UserManager.DISALLOW_CONFIG_BLUETOOTH;

    private final Context mContext = spy(ApplicationProvider.getApplicationContext());
    private final LifecycleOwner mLifecycleOwner = new TestLifecycleOwner();

    private BluetoothDeviceProfilesPreferenceController mPreferenceController;
    private CarUxRestrictions mCarUxRestrictions;
    private PreferenceGroup mPreferenceGroup;
    private MockitoSession mSession;

    @Mock
    private BluetoothDevice mDevice;
    @Mock
    private FragmentController mFragmentController;
    @Mock
    private UserManager mMockUserManager;
    @Mock
    private Toast mMockToast;
    @Mock
    private CachedBluetoothDevice mCachedDevice;

    @Before
    @UiThreadTest
    public void setUp() {
        mSession = ExtendedMockito.mockitoSession()
                .initMocks(this)
                .mockStatic(Toast.class)
                .strictness(Strictness.LENIENT)
                .startMocking();
        when(mContext.getSystemService(UserManager.class)).thenReturn(mMockUserManager);

        when(Toast.makeText(any(), anyString(), anyInt())).thenReturn(mMockToast);
        BluetoothTestUtils.setBluetoothState(mContext, /* enable= */ true);

        when(mCachedDevice.getDevice()).thenReturn(mDevice);

        LocalBluetoothProfile profile =
                new BluetoothTestUtils.TestLocalBluetoothProfile(BluetoothProfile.HEADSET_CLIENT);
        when(mCachedDevice.getProfiles()).thenReturn(List.of(profile));
        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();
        mPreferenceController = new BluetoothDeviceProfilesPreferenceController(mContext,
                "key", mFragmentController, mCarUxRestrictions);

        mPreferenceController.setCachedDevice(mCachedDevice);
        PreferenceManager preferenceManager = new PreferenceManager(mContext);
        PreferenceScreen screen = preferenceManager.createPreferenceScreen(mContext);
        mPreferenceGroup = new LogicalPreferenceGroup(mContext);
        screen.addPreference(mPreferenceGroup);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreferenceGroup);
    }

    @After
    @UiThreadTest
    public void tearDown() {
        if (mSession != null) {
            mSession.finishMocking();
        }
    }

    @Test
    public void testGetAvailabilityStatus_restrictedByUm_disabledForUser() {
        EnterpriseTestUtils.mockUserRestrictionSetByUm(mMockUserManager, TEST_RESTRICTION, true);

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);

        assertThat(mPreferenceController.getAvailabilityStatus()).isEqualTo(DISABLED_FOR_PROFILE);
    }

    @Test
    public void testGetAvailabilityStatus_restrictedByUm_disabledForUser_zoneWrite() {
        EnterpriseTestUtils.mockUserRestrictionSetByUm(mMockUserManager, TEST_RESTRICTION, true);

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);
        mPreferenceController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), DISABLED_FOR_PROFILE);
    }

    @Test
    public void testGetAvailabilityStatus_restrictedByUm_disabledForUser_zoneRead() {
        EnterpriseTestUtils.mockUserRestrictionSetByUm(mMockUserManager, TEST_RESTRICTION, true);

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);
        mPreferenceController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), DISABLED_FOR_PROFILE);
    }

    @Test
    public void testGetAvailabilityStatus_restrictedByUm_disabledForUser_zoneHidden() {
        EnterpriseTestUtils.mockUserRestrictionSetByUm(mMockUserManager, TEST_RESTRICTION, true);

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);
        mPreferenceController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), DISABLED_FOR_PROFILE);
    }

    @Test
    public void testGetAvailabilityStatus_restrictedByDpm_disabledForUser() {
        EnterpriseTestUtils.mockUserRestrictionSetByDpm(mMockUserManager, TEST_RESTRICTION, true);

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);

        assertThat(mPreferenceController.getAvailabilityStatus()).isEqualTo(AVAILABLE_FOR_VIEWING);
        BluetoothDeviceProfilePreference pref =
                (BluetoothDeviceProfilePreference) mPreferenceGroup.getPreference(0);
        assertThat(pref.isEnabled()).isFalse();
    }

    @Test
    public void testGetAvailabilityStatus_restrictedByDpm_disabledForUser_zoneWrite() {
        EnterpriseTestUtils.mockUserRestrictionSetByDpm(mMockUserManager, TEST_RESTRICTION, true);

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);
        mPreferenceController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE_FOR_VIEWING);
        BluetoothDeviceProfilePreference pref =
                (BluetoothDeviceProfilePreference) mPreferenceGroup.getPreference(0);
        assertThat(pref.isEnabled()).isFalse();
    }

    @Test
    public void testGetAvailabilityStatus_restrictedByDpm_disabledForUser_zoneRead() {
        EnterpriseTestUtils.mockUserRestrictionSetByDpm(mMockUserManager, TEST_RESTRICTION, true);

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);
        mPreferenceController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE_FOR_VIEWING);
        BluetoothDeviceProfilePreference pref =
                (BluetoothDeviceProfilePreference) mPreferenceGroup.getPreference(0);
        assertThat(pref.isEnabled()).isFalse();
    }

    @Test
    public void testGetAvailabilityStatus_restrictedByDpm_disabledForUser_zoneHidden() {
        EnterpriseTestUtils.mockUserRestrictionSetByDpm(mMockUserManager, TEST_RESTRICTION, true);

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);
        mPreferenceController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
        BluetoothDeviceProfilePreference pref =
                (BluetoothDeviceProfilePreference) mPreferenceGroup.getPreference(0);
        assertThat(pref.isEnabled()).isFalse();
    }

    @Test
    public void testGetAvailabilityStatus_notRestricted_available() {
        EnterpriseTestUtils.mockUserRestrictionSetByDpm(mMockUserManager, TEST_RESTRICTION, false);

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);

        assertThat(mPreferenceController.getAvailabilityStatus()).isEqualTo(AVAILABLE);
        BluetoothDeviceProfilePreference pref =
                (BluetoothDeviceProfilePreference) mPreferenceGroup.getPreference(0);
        assertThat(pref.isEnabled()).isTrue();
    }

    @Test
    public void testGetAvailabilityStatus_notRestricted_available_zoneWrite() {
        EnterpriseTestUtils.mockUserRestrictionSetByDpm(mMockUserManager, TEST_RESTRICTION, false);

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);
        mPreferenceController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE);
        BluetoothDeviceProfilePreference pref =
                (BluetoothDeviceProfilePreference) mPreferenceGroup.getPreference(0);
        assertThat(pref.isEnabled()).isTrue();
    }

    @Test
    public void testGetAvailabilityStatus_notRestricted_available_zoneRead() {
        EnterpriseTestUtils.mockUserRestrictionSetByDpm(mMockUserManager, TEST_RESTRICTION, false);

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);
        mPreferenceController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE_FOR_VIEWING);
        BluetoothDeviceProfilePreference pref =
                (BluetoothDeviceProfilePreference) mPreferenceGroup.getPreference(0);
        assertThat(pref.isEnabled()).isTrue();
    }

    @Test
    public void testGetAvailabilityStatus_notRestricted_available_zoneHidden() {
        EnterpriseTestUtils.mockUserRestrictionSetByDpm(mMockUserManager, TEST_RESTRICTION, false);

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);
        mPreferenceController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
        BluetoothDeviceProfilePreference pref =
                (BluetoothDeviceProfilePreference) mPreferenceGroup.getPreference(0);
        assertThat(pref.isEnabled()).isTrue();
    }

    @Test
    @UiThreadTest
    public void testDisabledClick_restrictedByDpm_dialog() {
        EnterpriseTestUtils.mockUserRestrictionSetByDpm(mMockUserManager, TEST_RESTRICTION, true);
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);

        BluetoothDeviceProfilePreference pref =
                (BluetoothDeviceProfilePreference) mPreferenceGroup.getPreference(0);
        pref.performClick();

        assertShowingDisabledByAdminDialog();
    }

    private void assertShowingBlockedToast() {
        String toastText = mContext.getResources().getString(R.string.action_unavailable);
        ExtendedMockito.verify(
                () -> Toast.makeText(any(), eq(toastText), anyInt()));
        verify(mMockToast).show();
    }

    private void assertShowingDisabledByAdminDialog() {
        verify(mFragmentController).showDialog(any(ActionDisabledByAdminDialogFragment.class),
                eq(DISABLED_BY_ADMIN_CONFIRM_DIALOG_TAG));
    }
}
