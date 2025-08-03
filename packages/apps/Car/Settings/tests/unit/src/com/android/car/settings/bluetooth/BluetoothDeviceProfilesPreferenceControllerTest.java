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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothDevice;
import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.UserManager;
import android.widget.Toast;

import androidx.lifecycle.LifecycleOwner;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.LogicalPreferenceGroup;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.BluetoothTestUtils;
import com.android.car.settings.testutils.TestLifecycleOwner;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.settingslib.bluetooth.CachedBluetoothDevice;
import com.android.settingslib.bluetooth.LocalBluetoothManager;
import com.android.settingslib.bluetooth.LocalBluetoothProfile;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.Collections;

@RunWith(AndroidJUnit4.class)
public class BluetoothDeviceProfilesPreferenceControllerTest {

    private LifecycleOwner mLifecycleOwner;
    private Context mContext = ApplicationProvider.getApplicationContext();
    private PreferenceGroup mPreferenceGroup;
    private BluetoothDeviceProfilesPreferenceController mPreferenceController;
    private CarUxRestrictions mCarUxRestrictions;
    private LocalBluetoothManager mLocalBluetoothManager;
    private MockitoSession mSession;

    @Mock
    private FragmentController mFragmentController;
    @Mock
    private CachedBluetoothDevice mBondedCachedDevice;
    @Mock
    private BluetoothDevice mBondedDevice;
    @Mock
    private UserManager mUserManager;
    @Mock
    private Toast mMockToast;

    @Before
    @UiThreadTest
    public void setUp() {
        mSession = ExtendedMockito.mockitoSession()
                .initMocks(this)
                .mockStatic(Toast.class)
                .strictness(Strictness.LENIENT)
                .startMocking();
        ExtendedMockito.when(Toast.makeText(any(), anyString(), anyInt())).thenReturn(mMockToast);

        mLifecycleOwner = new TestLifecycleOwner();
        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();
        mLocalBluetoothManager = spy(BluetoothUtils.getLocalBtManager(mContext));

        // Ensure bluetooth is available and enabled.
        assumeTrue(mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH));
        BluetoothTestUtils.setBluetoothState(mContext, /* enable= */ true);

        PreferenceManager preferenceManager = new PreferenceManager(mContext);
        PreferenceScreen screen = preferenceManager.createPreferenceScreen(mContext);
        mPreferenceGroup = new LogicalPreferenceGroup(mContext);
        screen.addPreference(mPreferenceGroup);
        mPreferenceController = new BluetoothDeviceProfilesPreferenceController(mContext,
                /* preferenceKey= */ "key", mFragmentController, mCarUxRestrictions,
                mLocalBluetoothManager, mUserManager);

        when(mBondedDevice.getBondState()).thenReturn(BluetoothDevice.BOND_BONDED);
        when(mBondedCachedDevice.getDevice()).thenReturn(mBondedDevice);
        mPreferenceController.setCachedDevice(mBondedCachedDevice);

        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreferenceGroup);

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);
    }

    @After
    @UiThreadTest
    public void tearDown() {
        if (mSession != null) {
            mSession.finishMocking();
        }
    }

    @Test
    public void refreshUi_addsNewProfiles() {
        LocalBluetoothProfile profile1 = mock(LocalBluetoothProfile.class);
        when(profile1.getNameResource(mBondedDevice)).thenReturn(R.string.bt_profile_name);
        when(mBondedCachedDevice.getProfiles()).thenReturn(Collections.singletonList(profile1));

        mPreferenceController.refreshUi();

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(1);

        LocalBluetoothProfile profile2 = mock(LocalBluetoothProfile.class);
        when(profile2.getNameResource(mBondedDevice)).thenReturn(R.string.bt_profile_name);
        when(mBondedCachedDevice.getProfiles()).thenReturn(Arrays.asList(profile1, profile2));

        mPreferenceController.refreshUi();

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(2);
        BluetoothDeviceProfilePreference profilePreference =
                (BluetoothDeviceProfilePreference) mPreferenceGroup.getPreference(1);
        assertThat(profilePreference.getProfile()).isEqualTo(profile2);
    }

    @Test
    public void refreshUi_removesRemovedProfiles() {
        LocalBluetoothProfile profile1 = mock(LocalBluetoothProfile.class);
        when(profile1.getNameResource(mBondedDevice)).thenReturn(R.string.bt_profile_name);
        LocalBluetoothProfile profile2 = mock(LocalBluetoothProfile.class);
        when(profile2.getNameResource(mBondedDevice)).thenReturn(R.string.bt_profile_name);
        when(mBondedCachedDevice.getProfiles()).thenReturn(Arrays.asList(profile1, profile2));

        mPreferenceController.refreshUi();

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(2);

        when(mBondedCachedDevice.getProfiles()).thenReturn(Collections.singletonList(profile2));
        when(mBondedCachedDevice.getRemovedProfiles())
                .thenReturn(Collections.singletonList(profile1));

        mPreferenceController.refreshUi();

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(1);
        BluetoothDeviceProfilePreference profilePreference =
                (BluetoothDeviceProfilePreference) mPreferenceGroup.getPreference(0);
        assertThat(profilePreference.getProfile()).isEqualTo(profile2);
    }

    @Test
    public void refreshUi_profiles_showsPreference() {
        LocalBluetoothProfile profile = mock(LocalBluetoothProfile.class);
        when(profile.getNameResource(mBondedDevice)).thenReturn(R.string.bt_profile_name);
        when(mBondedCachedDevice.getProfiles()).thenReturn(Collections.singletonList(profile));

        mPreferenceController.refreshUi();

        assertThat(mPreferenceGroup.isVisible()).isTrue();
    }

    @Test
    public void refreshUi_noProfiles_hidesPreference() {
        when(mBondedCachedDevice.getProfiles()).thenReturn(Collections.emptyList());

        mPreferenceController.refreshUi();

        assertThat(mPreferenceGroup.isVisible()).isFalse();
    }

    @Test
    public void profileChecked_setsProfilePreferred() {
        LocalBluetoothProfile profile = mock(LocalBluetoothProfile.class);
        when(profile.getNameResource(mBondedDevice)).thenReturn(R.string.bt_profile_name);
        when(mBondedCachedDevice.getProfiles()).thenReturn(Collections.singletonList(profile));
        mPreferenceController.refreshUi();

        BluetoothDeviceProfilePreference profilePreference =
                (BluetoothDeviceProfilePreference) mPreferenceGroup.getPreference(0);
        profilePreference.refreshUi();

        assertThat(profilePreference.isChecked()).isFalse();
        profilePreference.performClick();

        verify(profile).setEnabled(mBondedDevice, true);
    }

    @Test
    public void profileChecked_connectsToProfile() {
        LocalBluetoothProfile profile = mock(LocalBluetoothProfile.class);
        when(profile.getNameResource(mBondedDevice)).thenReturn(R.string.bt_profile_name);
        when(mBondedCachedDevice.getProfiles()).thenReturn(Collections.singletonList(profile));
        mPreferenceController.refreshUi();

        BluetoothDeviceProfilePreference profilePreference =
                (BluetoothDeviceProfilePreference) mPreferenceGroup.getPreference(0);
        profilePreference.refreshUi();

        assertThat(profilePreference.isChecked()).isFalse();
        profilePreference.performClick();

        verify(profile).setEnabled(mBondedDevice, true);
    }

    @Test
    public void profileUnchecked_setsProfileNotPreferred() {
        LocalBluetoothProfile profile = mock(LocalBluetoothProfile.class);
        when(profile.getNameResource(mBondedDevice)).thenReturn(R.string.bt_profile_name);
        when(profile.isEnabled(mBondedDevice)).thenReturn(true);
        when(mBondedCachedDevice.getProfiles()).thenReturn(Collections.singletonList(profile));
        mPreferenceController.refreshUi();

        BluetoothDeviceProfilePreference profilePreference =
                (BluetoothDeviceProfilePreference) mPreferenceGroup.getPreference(0);
        profilePreference.refreshUi();

        assertThat(profilePreference.isChecked()).isTrue();
        profilePreference.performClick();

        verify(profile).setEnabled(mBondedDevice, false);
    }

    @Test
    public void profileUnchecked_disconnectsFromProfile() {
        LocalBluetoothProfile profile = mock(LocalBluetoothProfile.class);
        when(profile.getNameResource(mBondedDevice)).thenReturn(R.string.bt_profile_name);
        when(profile.isEnabled(mBondedDevice)).thenReturn(true);
        when(mBondedCachedDevice.getProfiles()).thenReturn(Collections.singletonList(profile));
        mPreferenceController.refreshUi();

        BluetoothDeviceProfilePreference profilePreference =
                (BluetoothDeviceProfilePreference) mPreferenceGroup.getPreference(0);
        profilePreference.refreshUi();

        assertThat(profilePreference.isChecked()).isTrue();
        profilePreference.performClick();

        verify(profile).setEnabled(mBondedDevice, false);
    }

    @Test
    public void profileDisabled_showsToast() {
        when(mUserManager.hasUserRestriction(UserManager.DISALLOW_CONFIG_BLUETOOTH))
                .thenReturn(true);
        when(mBondedCachedDevice.isBusy()).thenReturn(true);
        LocalBluetoothProfile profile = mock(LocalBluetoothProfile.class);
        when(profile.getNameResource(mBondedDevice)).thenReturn(R.string.bt_profile_name);
        when(profile.isEnabled(mBondedDevice)).thenReturn(true);
        when(mBondedCachedDevice.getProfiles()).thenReturn(Collections.singletonList(profile));
        mPreferenceController.refreshUi();

        CarUxRestrictions restrictions = new CarUxRestrictions.Builder(
                true, CarUxRestrictions.UX_RESTRICTIONS_NO_SETUP, 0).build();
        mPreferenceController.onUxRestrictionsChanged(restrictions);

        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        BluetoothDeviceProfilePreference profilePreference =
                (BluetoothDeviceProfilePreference) mPreferenceGroup.getPreference(0);
        profilePreference.refreshUi();
        profilePreference.performClick();

        String toastText = mContext.getResources().getString(R.string.action_unavailable);

        ExtendedMockito.verify(() -> Toast.makeText(any(), eq(toastText), anyInt()));
        verify(mMockToast).show();
    }
}
