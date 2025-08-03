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

package com.android.car.settings.qc;

import static android.os.UserManager.DISALLOW_BLUETOOTH;

import static com.android.car.qc.QCItem.QC_ACTION_TOGGLE_STATE;
import static com.android.car.settings.qc.PairedBluetoothDevices.BLUETOOTH_BUTTON;
import static com.android.car.settings.qc.PairedBluetoothDevices.EXTRA_BUTTON_TYPE;
import static com.android.car.settings.qc.PairedBluetoothDevices.EXTRA_DEVICE_KEY;
import static com.android.car.settings.qc.PairedBluetoothDevices.MEDIA_BUTTON;
import static com.android.car.settings.qc.PairedBluetoothDevices.PHONE_BUTTON;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.os.UserManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.qc.QCActionItem;
import com.android.car.qc.QCItem;
import com.android.car.qc.QCList;
import com.android.car.qc.QCRow;
import com.android.car.settings.R;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.settingslib.bluetooth.CachedBluetoothDevice;
import com.android.settingslib.bluetooth.CachedBluetoothDeviceManager;
import com.android.settingslib.bluetooth.LocalBluetoothAdapter;
import com.android.settingslib.bluetooth.LocalBluetoothManager;
import com.android.settingslib.bluetooth.LocalBluetoothProfile;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
public class PairedBluetoothDevicesTest extends BaseSettingsQCItemTestCase {
    private static final String DEFAULT_NAME = "DEFAULT_NAME";
    private static final String DEFAULT_SUMMARY = "DEFAULT_SUMMARY";
    private static final String DEFAULT_ADDRESS = "F6:8F:AC:E8:32:50";

    private PairedBluetoothDevices mPairedBluetoothDevices;
    private MockitoSession mSession;
    private ArrayList<CachedBluetoothDevice> mCachedDevices = new ArrayList<>();
    private Set<BluetoothDevice> mBondedDevices = new HashSet<>();

    @Mock
    private BluetoothAdapter mBluetoothAdapter;
    @Mock
    private LocalBluetoothAdapter mLocalBluetoothAdapter;
    @Mock
    private LocalBluetoothManager mBluetoothManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mSession = ExtendedMockito.mockitoSession()
                .strictness(Strictness.LENIENT)
                .mockStatic(BluetoothAdapter.class)
                .mockStatic(LocalBluetoothManager.class)
                .startMocking();
        when(BluetoothAdapter.getDefaultAdapter()).thenReturn(mBluetoothAdapter);
        when(mBluetoothAdapter.isEnabled()).thenReturn(true);
        when(LocalBluetoothManager.getInstance(any(), any())).thenReturn(mBluetoothManager);
        when(mBluetoothManager.getBluetoothAdapter()).thenReturn(mLocalBluetoothAdapter);
        when(mLocalBluetoothAdapter.getBondedDevices()).thenReturn(mBondedDevices);

        CachedBluetoothDeviceManager deviceManager = mock(CachedBluetoothDeviceManager.class);
        when(mBluetoothManager.getCachedDeviceManager()).thenReturn(deviceManager);
        when(deviceManager.getCachedDevicesCopy()).thenReturn(mCachedDevices);

        mPairedBluetoothDevices = new PairedBluetoothDevices(mContext);
    }

    @After
    public void tearDown() {
        if (mSession != null) {
            mSession.finishMocking();
        }
    }

    @Test
    public void getQCItem_bluetoothDisallowed_returnsNull() {
        setUserRestriction(DISALLOW_BLUETOOTH, true);
        QCItem item = mPairedBluetoothDevices.getQCItem();
        assertThat(item).isNull();
    }

    @Test
    public void getQCItem_bluetoothDisabled_returnsBluetoothDisabledMessage() {
        when(mBluetoothAdapter.isEnabled()).thenReturn(false);
        QCItem item = mPairedBluetoothDevices.getQCItem();
        assertThat(item).isNotNull();
        QCList list = (QCList) item;
        assertThat(list.getRows().size()).isEqualTo(1);
        QCRow row = list.getRows().get(0);
        assertThat(row.getTitle()).isEqualTo(
                mContext.getString(R.string.qc_bluetooth_off_devices_info));
        assertThat(row.getStartIcon()).isNotNull();
    }

    @Test
    public void getQCItem_noDevices_returnsPairMessage() {
        QCList list = (QCList) mPairedBluetoothDevices.getQCItem();
        assertThat(list.getRows().size()).isEqualTo(1);
        QCRow row = list.getRows().get(0);
        assertThat(row.getTitle()).isEqualTo(
                mContext.getString(R.string.qc_bluetooth_on_no_devices_info));
        assertThat(row.getStartIcon()).isNotNull();
    }

    @Test
    public void getQCItem_hasDevices_hasRows() {
        addBluetoothDevice("Device1", /* connected= */ true, /* busy= */ false,
                /* phoneEnabled= */ true, /* mediaEnabled= */ true,
                /* hasHumanReadableName= */ true);
        addBluetoothDevice("Device2",  /* connected= */ true, /* busy= */ false,
                /* phoneEnabled= */ true, /* mediaEnabled= */ true,
                /* hasHumanReadableName= */ true);
        QCList list = (QCList) mPairedBluetoothDevices.getQCItem();
        assertThat(list.getRows().size()).isEqualTo(2);
    }

    @Test
    public void getQCItem_hasDevices_onlyDisplaysHumanReadableNames() {
        addBluetoothDevice("Device1", /* connected= */ true, /* busy= */ false,
                /* phoneEnabled= */ true, /* mediaEnabled= */ true,
                /* hasHumanReadableName= */ true);
        addBluetoothDevice("Device2",  /* connected= */ true, /* busy= */ false,
                /* phoneEnabled= */ true, /* mediaEnabled= */ true,
                /* hasHumanReadableName= */ false);
        QCList list = (QCList) mPairedBluetoothDevices.getQCItem();
        assertThat(list.getRows().size()).isEqualTo(1);
    }

    @Test
    public void getQCItem_limitsDeviceCount() {
        addBluetoothDevice("Device1", /* connected= */ true, /* busy= */ false,
                /* phoneEnabled= */ true, /* mediaEnabled= */ true,
                /* hasHumanReadableName= */ true);
        addBluetoothDevice("Device2",  /* connected= */ false, /* busy= */ false,
                /* phoneEnabled= */ false, /* mediaEnabled= */ false,
                /* hasHumanReadableName= */ true);
        addBluetoothDevice("Device3",  /* connected= */ false, /* busy= */ false,
                /* phoneEnabled= */ false, /* mediaEnabled= */ false,
                /* hasHumanReadableName= */ true);
        addBluetoothDevice("Device4",  /* connected= */ false, /* busy= */ false,
                /* phoneEnabled= */ false, /* mediaEnabled= */ false,
                /* hasHumanReadableName= */ true);
        QCList list = (QCList) mPairedBluetoothDevices.getQCItem();
        assertThat(list.getRows().size()).isEqualTo(3);
    }

    @Test
    public void getQCItem_setsTitle() {
        addBluetoothDevice(DEFAULT_NAME, /* connected= */ true, /* busy= */ false,
                /* phoneEnabled= */ true, /* mediaEnabled= */ true,
                /* hasHumanReadableName= */ true);
        QCList list = (QCList) mPairedBluetoothDevices.getQCItem();
        QCRow row = list.getRows().get(0);
        assertThat(row.getTitle()).isEqualTo(DEFAULT_NAME);
    }

    @Test
    public void getQCItem_bluetoothDisabled_togglesUpdated() {
        addBluetoothDevice(DEFAULT_NAME, /* connected= */ false, /* busy= */ false,
                /* phoneEnabled= */ true, /* mediaEnabled= */ true,
                /* hasHumanReadableName= */ true);
        QCList list = (QCList) mPairedBluetoothDevices.getQCItem();
        QCRow row = list.getRows().get(0);
        QCActionItem btToggle = row.getEndItems().get(0);
        QCActionItem phoneToggle = row.getEndItems().get(1);
        QCActionItem mediaToggle = row.getEndItems().get(2);
        assertThat(btToggle.isChecked()).isFalse();
        assertThat(btToggle.isEnabled()).isTrue();
        assertThat(phoneToggle.isAvailable()).isFalse();
        assertThat(mediaToggle.isAvailable()).isFalse();
    }

    @Test
    public void getQCItem_bluetoothEnabled_togglesUpdated() {
        addBluetoothDevice(DEFAULT_NAME, /* connected= */ true, /* busy= */ false,
                /* phoneEnabled= */ true, /* mediaEnabled= */ true,
                /* hasHumanReadableName= */ true);
        QCList list = (QCList) mPairedBluetoothDevices.getQCItem();
        QCRow row = list.getRows().get(0);
        QCActionItem btToggle = row.getEndItems().get(0);
        QCActionItem phoneToggle = row.getEndItems().get(1);
        QCActionItem mediaToggle = row.getEndItems().get(2);
        assertThat(btToggle.isChecked()).isTrue();
        assertThat(btToggle.isEnabled()).isTrue();
        assertThat(phoneToggle.isChecked()).isTrue();
        assertThat(phoneToggle.isEnabled()).isTrue();
        assertThat(phoneToggle.isAvailable()).isTrue();
        assertThat(mediaToggle.isChecked()).isTrue();
        assertThat(mediaToggle.isEnabled()).isTrue();
        assertThat(mediaToggle.isAvailable()).isTrue();
    }

    @Test
    public void getQCItem_isBusy_togglesDisabled() {
        addBluetoothDevice(DEFAULT_NAME, /* connected= */ true, /* busy= */ true,
                /* phoneEnabled= */ true, /* mediaEnabled= */ true,
                /* hasHumanReadableName= */ true);
        QCList list = (QCList) mPairedBluetoothDevices.getQCItem();
        QCRow row = list.getRows().get(0);
        QCActionItem btToggle = row.getEndItems().get(0);
        QCActionItem phoneToggle = row.getEndItems().get(1);
        QCActionItem mediaToggle = row.getEndItems().get(2);
        assertThat(btToggle.isEnabled()).isFalse();
        assertThat(phoneToggle.isEnabled()).isFalse();
        assertThat(mediaToggle.isEnabled()).isFalse();
    }

    @Test
    public void getQCItem_hasBaseUmRestriction_togglesDisabled() {
        addBluetoothDevice(DEFAULT_NAME, /* connected= */ true, /* busy= */ false,
                /* phoneEnabled= */ true, /* mediaEnabled= */ true,
                /* hasHumanReadableName= */ true);
        setBaseUserRestriction(UserManager.DISALLOW_CONFIG_BLUETOOTH, /* restricted= */ true);
        QCList list = (QCList) mPairedBluetoothDevices.getQCItem();
        QCRow row = list.getRows().get(0);
        QCActionItem btToggle = row.getEndItems().get(0);
        QCActionItem phoneToggle = row.getEndItems().get(1);
        QCActionItem mediaToggle = row.getEndItems().get(2);
        assertThat(btToggle.isEnabled()).isTrue();
        assertThat(phoneToggle.isEnabled()).isFalse();
        assertThat(phoneToggle.isClickableWhileDisabled()).isFalse();
        assertThat(mediaToggle.isEnabled()).isFalse();
        assertThat(mediaToggle.isClickableWhileDisabled()).isFalse();
    }

    @Test
    public void getQCItem_hasUmRestriction_togglesClickableWhileDisabled() {
        addBluetoothDevice(DEFAULT_NAME, /* connected= */ true, /* busy= */ false,
                /* phoneEnabled= */ true, /* mediaEnabled= */ true,
                /* hasHumanReadableName= */ true);
        setUserRestriction(UserManager.DISALLOW_CONFIG_BLUETOOTH, /* restricted= */ true);
        QCList list = (QCList) mPairedBluetoothDevices.getQCItem();
        QCRow row = list.getRows().get(0);
        QCActionItem btToggle = row.getEndItems().get(0);
        QCActionItem phoneToggle = row.getEndItems().get(1);
        QCActionItem mediaToggle = row.getEndItems().get(2);
        assertThat(btToggle.isEnabled()).isTrue();
        assertThat(phoneToggle.isEnabled()).isFalse();
        assertThat(phoneToggle.isClickableWhileDisabled()).isTrue();
        assertThat(mediaToggle.isEnabled()).isFalse();
        assertThat(mediaToggle.isClickableWhileDisabled()).isTrue();
    }

    @Test
    public void onNotifyChange_toggleBluetooth() {
        addBluetoothDevice(DEFAULT_NAME, /* connected= */ false, /* busy= */ true,
                /* phoneEnabled= */ true, /* mediaEnabled= */ true,
                /* hasHumanReadableName= */ true);
        Intent intent = new Intent();
        intent.putExtra(EXTRA_DEVICE_KEY, DEFAULT_ADDRESS);
        intent.putExtra(EXTRA_BUTTON_TYPE, BLUETOOTH_BUTTON);
        intent.putExtra(QC_ACTION_TOGGLE_STATE, true);
        mPairedBluetoothDevices.onNotifyChange(intent);
        CachedBluetoothDevice device = mCachedDevices.get(0);
        verify(device).connect();
    }

    @Test
    public void onNotifyChange_togglePhone() {
        addBluetoothDevice(DEFAULT_NAME, /* connected= */ false, /* busy= */ true,
                /* phoneEnabled= */ false, /* mediaEnabled= */ true,
                /* hasHumanReadableName= */ true);
        Intent intent = new Intent();
        intent.putExtra(EXTRA_DEVICE_KEY, DEFAULT_ADDRESS);
        intent.putExtra(EXTRA_BUTTON_TYPE, PHONE_BUTTON);
        intent.putExtra(QC_ACTION_TOGGLE_STATE, true);
        mPairedBluetoothDevices.onNotifyChange(intent);
        CachedBluetoothDevice device = mCachedDevices.get(0);
        LocalBluetoothProfile profile = getProfile(device, BluetoothProfile.HEADSET_CLIENT);
        verify(profile).setEnabled(any(), eq(true));
    }

    @Test
    public void onNotifyChange_toggleMedia() {
        addBluetoothDevice(DEFAULT_NAME, /* connected= */ false, /* busy= */ true,
                /* phoneEnabled= */ true, /* mediaEnabled= */ false,
                /* hasHumanReadableName= */ true);
        Intent intent = new Intent();
        intent.putExtra(EXTRA_DEVICE_KEY, DEFAULT_ADDRESS);
        intent.putExtra(EXTRA_BUTTON_TYPE, MEDIA_BUTTON);
        intent.putExtra(QC_ACTION_TOGGLE_STATE, true);
        mPairedBluetoothDevices.onNotifyChange(intent);
        CachedBluetoothDevice device = mCachedDevices.get(0);
        LocalBluetoothProfile profile = getProfile(device, BluetoothProfile.A2DP_SINK);
        verify(profile).setEnabled(any(), eq(true));
    }

    @Test
    public void getQCItem_createsItem_bluetoothDisabled_zoneWrite() {
        when(mBluetoothAdapter.isEnabled()).thenReturn(false);
        mPairedBluetoothDevices.setAvailabilityStatusForZone("write");
        QCItem item = mPairedBluetoothDevices.getQCItem();
        assertThat(item).isNotNull();
    }

    @Test
    public void getQCItem_createsItem_bluetoothDisabled_zoneRead() {
        when(mBluetoothAdapter.isEnabled()).thenReturn(false);
        mPairedBluetoothDevices.setAvailabilityStatusForZone("read");
        QCItem item = mPairedBluetoothDevices.getQCItem();
        assertThat(item).isNotNull();
    }

    @Test
    public void getQCItem_createsItem_bluetoothDisabled_zoneHidden() {
        when(mBluetoothAdapter.isEnabled()).thenReturn(false);
        mPairedBluetoothDevices.setAvailabilityStatusForZone("hidden");
        QCItem item = mPairedBluetoothDevices.getQCItem();
        assertThat(item).isNull();
    }

    @Test
    public void getQCItem_createsItem_noDevices_zoneWrite() {
        mPairedBluetoothDevices.setAvailabilityStatusForZone("write");
        QCList list = (QCList) mPairedBluetoothDevices.getQCItem();
        assertThat(list.getRows().size()).isEqualTo(1);
        QCRow row = list.getRows().get(0);
        assertThat(row.getStartIcon()).isNotNull();
    }

    @Test
    public void getQCItem_createsItem_noDevices_zoneRead() {
        mPairedBluetoothDevices.setAvailabilityStatusForZone("read");
        QCList list = (QCList) mPairedBluetoothDevices.getQCItem();
        assertThat(list.getRows().size()).isEqualTo(1);
        QCRow row = list.getRows().get(0);
        assertThat(row.getStartIcon()).isNotNull();
    }

    @Test
    public void getQCItem_createsItem_noDevices_zoneHidden() {
        mPairedBluetoothDevices.setAvailabilityStatusForZone("hidden");
        QCList list = (QCList) mPairedBluetoothDevices.getQCItem();
        assertThat(list).isNull();
    }

    @Test
    public void getQCItem_createsToggles_bluetoothEnabled_zoneWrite() {
        mPairedBluetoothDevices.setAvailabilityStatusForZone("write");
        addBluetoothDevice(DEFAULT_NAME, /* connected= */ true, /* busy= */ false,
                /* phoneEnabled= */ true, /* mediaEnabled= */ true,
                /* hasHumanReadableName= */ true);
        QCList list = (QCList) mPairedBluetoothDevices.getQCItem();
        QCRow row = list.getRows().get(0);
        QCActionItem btToggle = row.getEndItems().get(0);
        QCActionItem phoneToggle = row.getEndItems().get(1);
        QCActionItem mediaToggle = row.getEndItems().get(2);
        assertThat(btToggle.isEnabled()).isTrue();
        assertThat(phoneToggle.isEnabled()).isTrue();
        assertThat(mediaToggle.isEnabled()).isTrue();
    }

    @Test
    public void getQCItem_createsToggles_bluetoothEnabled_zoneRead() {
        mPairedBluetoothDevices.setAvailabilityStatusForZone("read");
        addBluetoothDevice(DEFAULT_NAME, /* connected= */ true, /* busy= */ false,
                /* phoneEnabled= */ true, /* mediaEnabled= */ true,
                /* hasHumanReadableName= */ true);
        QCList list = (QCList) mPairedBluetoothDevices.getQCItem();
        QCRow row = list.getRows().get(0);
        QCActionItem btToggle = row.getEndItems().get(0);
        QCActionItem phoneToggle = row.getEndItems().get(1);
        QCActionItem mediaToggle = row.getEndItems().get(2);
        assertThat(btToggle.isEnabled()).isFalse();
        assertThat(phoneToggle.isEnabled()).isFalse();
        assertThat(mediaToggle.isEnabled()).isFalse();
        assertThat(btToggle.isClickableWhileDisabled()).isTrue();
        assertThat(phoneToggle.isClickableWhileDisabled()).isTrue();
        assertThat(mediaToggle.isClickableWhileDisabled()).isTrue();
    }

    @Test
    public void getQCItem_createsToggles_bluetoothEnabled_zoneHidden() {
        mPairedBluetoothDevices.setAvailabilityStatusForZone("hidden");
        addBluetoothDevice(DEFAULT_NAME, /* connected= */ true, /* busy= */ false,
                /* phoneEnabled= */ true, /* mediaEnabled= */ true,
                /* hasHumanReadableName= */ true);
        QCList list = (QCList) mPairedBluetoothDevices.getQCItem();
        assertThat(list).isNull();
    }

    private void addBluetoothDevice(String name, boolean connected, boolean busy,
            boolean phoneEnabled, boolean mediaEnabled, boolean hasHumanReadableName) {
        CachedBluetoothDevice cachedDevice = mock(CachedBluetoothDevice.class);
        BluetoothDevice device = mock(BluetoothDevice.class);
        when(cachedDevice.getDevice()).thenReturn(device);
        when(device.getBondState()).thenReturn(BluetoothDevice.BOND_BONDED);
        when(cachedDevice.getName()).thenReturn(name);
        when(cachedDevice.getAddress()).thenReturn(DEFAULT_ADDRESS);
        when(cachedDevice.isConnected()).thenReturn(connected);
        when(cachedDevice.isBusy()).thenReturn(busy);
        when(cachedDevice.getCarConnectionSummary(anyBoolean())).thenReturn(DEFAULT_SUMMARY);
        when(cachedDevice.hasHumanReadableName()).thenReturn(hasHumanReadableName);
        BluetoothClass btClass = mock(BluetoothClass.class);
        when(cachedDevice.getBtClass()).thenReturn(btClass);
        when(btClass.getMajorDeviceClass()).thenReturn(BluetoothClass.Device.Major.PHONE);
        LocalBluetoothProfile phoneProfile = mock(LocalBluetoothProfile.class);
        LocalBluetoothProfile mediaProfile = mock(LocalBluetoothProfile.class);
        when(phoneProfile.getProfileId()).thenReturn(BluetoothProfile.HEADSET_CLIENT);
        when(mediaProfile.getProfileId()).thenReturn(BluetoothProfile.A2DP_SINK);
        when(phoneProfile.isEnabled(any())).thenReturn(phoneEnabled);
        when(mediaProfile.isEnabled(any())).thenReturn(mediaEnabled);
        when(cachedDevice.getProfiles()).thenReturn(Arrays.asList(phoneProfile, mediaProfile));
        mCachedDevices.add(cachedDevice);
        mBondedDevices.add(device);
    }

    private LocalBluetoothProfile getProfile(CachedBluetoothDevice device, int profileId) {
        for (LocalBluetoothProfile profile : device.getProfiles()) {
            if (profile.getProfileId() == profileId) {
                return profile;
            }
        }
        return null;
    }
}
