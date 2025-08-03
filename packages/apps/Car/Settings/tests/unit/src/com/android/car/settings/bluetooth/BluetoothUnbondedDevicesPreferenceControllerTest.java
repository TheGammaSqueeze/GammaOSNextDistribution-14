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

import static android.os.UserManager.DISALLOW_CONFIG_BLUETOOTH;

import static com.android.car.settings.common.PreferenceController.DISABLED_FOR_PROFILE;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.UserManager;

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
import com.android.car.settings.testutils.BluetoothTestUtils;
import com.android.car.settings.testutils.TestLifecycleOwner;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.settingslib.bluetooth.BluetoothEventManager;
import com.android.settingslib.bluetooth.CachedBluetoothDevice;
import com.android.settingslib.bluetooth.CachedBluetoothDeviceManager;
import com.android.settingslib.bluetooth.LocalBluetoothManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

import java.util.Arrays;
import java.util.Collections;

@RunWith(AndroidJUnit4.class)
public class BluetoothUnbondedDevicesPreferenceControllerTest {
    private Context mContext = spy(ApplicationProvider.getApplicationContext());
    private LifecycleOwner mLifecycleOwner;
    private CarUxRestrictions mCarUxRestrictions;
    private PreferenceGroup mPreferenceGroup;
    private BluetoothUnbondedDevicesPreferenceController mController;
    private int[] mUnbondedDeviceFilter;
    private MockitoSession mSession;

    @Mock
    private FragmentController mFragmentController;
    @Mock
    private CachedBluetoothDevice mUnbondedCachedDevice;
    @Mock
    private BluetoothDevice mUnbondedDevice;
    @Mock
    private BluetoothClass mBluetoothClass;
    @Mock
    private CachedBluetoothDeviceManager mCachedDeviceManager;
    @Mock
    private LocalBluetoothManager mLocalBluetoothManager;
    @Mock
    private BluetoothEventManager mBluetoothEventManager;
    @Mock
    private BluetoothAdapter mBluetoothAdapter;
    @Mock
    private UserManager mUserManager;

    @Before
    @UiThreadTest
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLifecycleOwner = new TestLifecycleOwner();
        mUnbondedDeviceFilter = new int[]{};
        Resources resources = spy(mContext.getResources());
        when(mContext.getResources()).thenReturn(resources);
        when(resources.getIntArray(R.array.config_unbonded_device_filter_allowlist))
                .thenReturn(mUnbondedDeviceFilter);

        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();

        // Ensure bluetooth is available and enabled.
        assumeTrue(mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH));
        BluetoothTestUtils.setBluetoothState(mContext, /* enable= */ true);

        when(mUnbondedDevice.getBondState()).thenReturn(BluetoothDevice.BOND_NONE);
        when(mUnbondedCachedDevice.getBondState()).thenReturn(BluetoothDevice.BOND_NONE);
        when(mUnbondedCachedDevice.getDevice()).thenReturn(mUnbondedDevice);
        when(mBluetoothClass.getMajorDeviceClass()).thenReturn(BluetoothClass.Device.Major.PHONE);
        when(mUnbondedDevice.getBluetoothClass()).thenReturn(mBluetoothClass);
        BluetoothDevice bondedDevice = mock(BluetoothDevice.class);
        when(bondedDevice.getBondState()).thenReturn(BluetoothDevice.BOND_BONDED);
        when(mBluetoothAdapter.getBondedDevices()).thenReturn(Collections.singleton(bondedDevice));
        BluetoothManager bluetoothManager = mock(BluetoothManager.class);
        when(bluetoothManager.getAdapter()).thenReturn(mBluetoothAdapter);
        when(mContext.getSystemService(BluetoothManager.class)).thenReturn(bluetoothManager);
        when(mContext.getSystemService(UserManager.class)).thenReturn(mUserManager);
        CachedBluetoothDevice bondedCachedDevice = mock(CachedBluetoothDevice.class);
        when(bondedCachedDevice.getDevice()).thenReturn(bondedDevice);
        when(mCachedDeviceManager.getCachedDevicesCopy()).thenReturn(
                Arrays.asList(mUnbondedCachedDevice, bondedCachedDevice));
        mSession = ExtendedMockito.mockitoSession().mockStatic(
                BluetoothUtils.class, withSettings().lenient()).startMocking();
        when(BluetoothUtils.getLocalBtManager(any())).thenReturn(
                mLocalBluetoothManager);
        when(mLocalBluetoothManager.getCachedDeviceManager()).thenReturn(mCachedDeviceManager);
        when(mLocalBluetoothManager.getEventManager()).thenReturn(mBluetoothEventManager);

        mController = new BluetoothUnbondedDevicesPreferenceController(mContext,
                /* preferenceKey= */ "key", mFragmentController,
                mCarUxRestrictions);
        PreferenceManager preferenceManager = new PreferenceManager(mContext);
        PreferenceScreen screen = preferenceManager.createPreferenceScreen(mContext);
        mPreferenceGroup = new LogicalPreferenceGroup(mContext);
        screen.addPreference(mPreferenceGroup);
        PreferenceControllerTestUtil.assignPreference(mController, mPreferenceGroup);
    }

    @After
    @UiThreadTest
    public void tearDown() {
        if (mSession != null) {
            mSession.finishMocking();
        }
    }

    @Test
    public void showsUnbondedDevices() {
        mController.onCreate(mLifecycleOwner);
        mController.onStart(mLifecycleOwner);
        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(1);
        BluetoothDevicePreference devicePreference =
                (BluetoothDevicePreference) mPreferenceGroup.getPreference(0);
        assertThat(devicePreference.getCachedDevice()).isEqualTo(mUnbondedCachedDevice);
    }

    @Test
    public void configUnbondedDeviceFilterIncludesPhones_showsUnbondedPhones() {
        mUnbondedDeviceFilter = new int[] {BluetoothClass.Device.Major.PHONE};
        mController.onCreate(mLifecycleOwner);
        mController.onStart(mLifecycleOwner);
        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(1);
        BluetoothDevicePreference devicePreference =
                (BluetoothDevicePreference) mPreferenceGroup.getPreference(0);
        assertThat(devicePreference.getCachedDevice()).isEqualTo(mUnbondedCachedDevice);
    }

    @Test
    public void onDeviceClicked_startsPairing() {
        mController.onCreate(mLifecycleOwner);
        mController.onStart(mLifecycleOwner);
        BluetoothDevicePreference devicePreference =
                (BluetoothDevicePreference) mPreferenceGroup.getPreference(0);

        devicePreference.performClick();

        verify(mUnbondedCachedDevice).startPairing();
    }

    @Test
    public void onDeviceClicked_pairingStartFails_resumesScanning() {
        mController.onCreate(mLifecycleOwner);
        mController.onStart(mLifecycleOwner);
        BluetoothDevicePreference devicePreference =
                (BluetoothDevicePreference) mPreferenceGroup.getPreference(0);
        when(mUnbondedCachedDevice.startPairing()).thenReturn(false);
        verify(mBluetoothAdapter).startDiscovery();

        Mockito.clearInvocations(mBluetoothAdapter);
        devicePreference.performClick();

        verify(mBluetoothAdapter).startDiscovery();
    }

    @Test
    public void onDeviceClicked_requestsPhonebookAccess() {
        mController.onCreate(mLifecycleOwner);
        mController.onStart(mLifecycleOwner);
        when(mUnbondedCachedDevice.startPairing()).thenReturn(true);
        BluetoothDevicePreference devicePreference =
                (BluetoothDevicePreference) mPreferenceGroup.getPreference(0);

        devicePreference.performClick();

        verify(mUnbondedDevice).setPhonebookAccessPermission(BluetoothDevice.ACCESS_ALLOWED);
    }

    @Test
    public void onDeviceClicked_requestsMessageAccess() {
        mController.onCreate(mLifecycleOwner);
        mController.onStart(mLifecycleOwner);
        when(mUnbondedCachedDevice.startPairing()).thenReturn(true);
        BluetoothDevicePreference devicePreference =
                (BluetoothDevicePreference) mPreferenceGroup.getPreference(0);

        devicePreference.performClick();

        verify(mUnbondedDevice).setMessageAccessPermission(BluetoothDevice.ACCESS_ALLOWED);
    }

    @Test
    public void getAvailabilityStatus_disallowConfigBluetooth_disabledForUser() {
        when(mUserManager.hasUserRestriction(DISALLOW_CONFIG_BLUETOOTH)).thenReturn(true);

        assertThat(mController.getAvailabilityStatus()).isEqualTo(DISABLED_FOR_PROFILE);
    }

    @Test
    public void getAvailabilityStatus_disallowConfigBluetooth_disabledForUser_zoneWrite() {
        when(mUserManager.hasUserRestriction(DISALLOW_CONFIG_BLUETOOTH)).thenReturn(true);
        mController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                DISABLED_FOR_PROFILE);
    }

    @Test
    public void getAvailabilityStatus_disallowConfigBluetooth_disabledForUser_zoneRead() {
        when(mUserManager.hasUserRestriction(DISALLOW_CONFIG_BLUETOOTH)).thenReturn(true);
        mController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                DISABLED_FOR_PROFILE);
    }

    @Test
    public void getAvailabilityStatus_disallowConfigBluetooth_disabledForUser_zoneHidden() {
        when(mUserManager.hasUserRestriction(DISALLOW_CONFIG_BLUETOOTH)).thenReturn(true);
        mController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                DISABLED_FOR_PROFILE);
    }
}
