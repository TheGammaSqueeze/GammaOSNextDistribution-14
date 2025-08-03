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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.UserManager;

import androidx.lifecycle.LifecycleOwner;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.LogicalPreferenceGroup;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.BluetoothTestUtils;
import com.android.car.settings.testutils.TestLifecycleOwner;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.settingslib.bluetooth.BluetoothDeviceFilter;
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

import java.util.Collections;

@RunWith(AndroidJUnit4.class)
public class BluetoothScanningDevicesGroupPreferenceControllerTest {
    private Context mContext = spy(ApplicationProvider.getApplicationContext());
    private LifecycleOwner mLifecycleOwner;
    private CarUxRestrictions mCarUxRestrictions;
    private PreferenceGroup mPreferenceGroup;
    private TestBluetoothScanningDevicesGroupPreferenceController mController;
    private MockitoSession mSession;

    @Mock
    private UserManager mUserManager;
    @Mock
    private LocalBluetoothManager mMockManager;
    @Mock
    private FragmentController mFragmentController;
    @Mock
    private BluetoothAdapter mBluetoothAdapter;
    @Mock
    private CachedBluetoothDevice mCachedDevice;
    @Mock
    private BluetoothEventManager mBluetoothEventManager;
    @Mock
    private BluetoothDevice mDevice;
    @Mock
    private CachedBluetoothDeviceManager mCachedDeviceManager;

    @Before
    @UiThreadTest
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mLifecycleOwner = new TestLifecycleOwner();

        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();

        // Ensure bluetooth is available and enabled.
        assumeTrue(mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH));
        BluetoothTestUtils.setBluetoothState(mContext, /* enable= */ true);

        when(mBluetoothAdapter.startDiscovery()).then(invocation -> {
            when(mBluetoothAdapter.isDiscovering()).thenReturn(true);
            return true;
        });
        when(mBluetoothAdapter.cancelDiscovery()).then(invocation -> {
            when(mBluetoothAdapter.isDiscovering()).thenReturn(false);
            return true;
        });
        BluetoothManager bluetoothManager = mock(BluetoothManager.class);
        when(bluetoothManager.getAdapter()).thenReturn(mBluetoothAdapter);
        when(mContext.getSystemService(BluetoothManager.class)).thenReturn(bluetoothManager);
        when(mContext.getSystemService(UserManager.class)).thenReturn(mUserManager);
        mSession = ExtendedMockito.mockitoSession().mockStatic(
                BluetoothUtils.class, withSettings().lenient()).startMocking();
        when(BluetoothUtils.getLocalBtManager(any())).thenReturn(
                mMockManager);
        when(BluetoothUtils.shouldEnableBTScanning(eq(mContext), any())).thenReturn(true);
        when(mDevice.getBondState()).thenReturn(BluetoothDevice.BOND_NONE);
        when(mCachedDevice.getBondState()).thenReturn(BluetoothDevice.BOND_NONE);
        when(mCachedDevice.getDevice()).thenReturn(mDevice);
        when(mCachedDeviceManager.getCachedDevicesCopy()).thenReturn(
                Collections.singletonList(mCachedDevice));
        when(mMockManager.getCachedDeviceManager()).thenReturn(mCachedDeviceManager);
        when(mMockManager.getEventManager()).thenReturn(mBluetoothEventManager);

        mController = new TestBluetoothScanningDevicesGroupPreferenceController(mContext,
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
    public void disallowConfigBluetooth_doesNotStartScanning() {
        when(mUserManager.hasUserRestriction(DISALLOW_CONFIG_BLUETOOTH)).thenReturn(true);

        mController.onCreate(mLifecycleOwner);
        mController.onStart(mLifecycleOwner);

        verify(mBluetoothAdapter, never()).startDiscovery();
        // User can't scan, but they can still see known devices.
        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(1);
    }

    @Test
    public void onScanningStateChanged_scanningEnabled_receiveStopped_restartsScanning() {
        mController.onCreate(mLifecycleOwner);
        mController.onStart(mLifecycleOwner);
        verify(mBluetoothAdapter).startDiscovery();

        Mockito.clearInvocations(mBluetoothAdapter);
        mBluetoothAdapter.cancelDiscovery();
        mController.onScanningStateChanged(/* started= */ false);
        // start discovery should be called a second time after the state change
        verify(mBluetoothAdapter).startDiscovery();
    }

    @Test
    public void onScanningStateChanged_scanningDisabled_receiveStopped_doesNothing() {
        mController.onCreate(mLifecycleOwner);
        mController.onStart(mLifecycleOwner);
        verify(mBluetoothAdapter).startDiscovery();
        // Set a device bonding to disable scanning.
        when(mCachedDevice.getBondState()).thenReturn(BluetoothDevice.BOND_BONDING);
        mController.refreshUi();
        verify(mBluetoothAdapter).cancelDiscovery();

        Mockito.clearInvocations(mBluetoothAdapter);
        mController.onScanningStateChanged(/* started= */ false);
        verify(mBluetoothAdapter, never()).startDiscovery();
    }

    @Test
    public void onDeviceBondStateChanged_refreshesUi() {
        mController.onCreate(mLifecycleOwner);
        mController.onStart(mLifecycleOwner);
        verify(mBluetoothAdapter).startDiscovery();

        // Change state to bonding to cancel scanning on refresh.
        when(mCachedDevice.getBondState()).thenReturn(BluetoothDevice.BOND_BONDING);
        when(mDevice.getBondState()).thenReturn(BluetoothDevice.BOND_BONDING);
        mController.onDeviceBondStateChanged(mCachedDevice, BluetoothDevice.BOND_BONDING);

        verify(mBluetoothAdapter).cancelDiscovery();
    }

    @Test
    public void onDeviceClicked_callsInternal() {
        mController.onCreate(mLifecycleOwner);
        mController.onStart(mLifecycleOwner);
        BluetoothDevicePreference devicePreference =
                (BluetoothDevicePreference) mPreferenceGroup.getPreference(0);

        devicePreference.performClick();

        assertThat(mController.getLastClickedDevice()).isEquivalentAccordingToCompareTo(
                devicePreference.getCachedDevice());
    }

    @Test
    public void onDeviceClicked_cancelsScanning() {
        mController.onCreate(mLifecycleOwner);
        mController.onStart(mLifecycleOwner);
        BluetoothDevicePreference devicePreference =
                (BluetoothDevicePreference) mPreferenceGroup.getPreference(0);
        verify(mBluetoothAdapter).startDiscovery();

        devicePreference.performClick();

        verify(mBluetoothAdapter).cancelDiscovery();
    }

    @Test
    public void refreshUi_noDeviceBonding_startsScanning() {
        mController.onCreate(mLifecycleOwner);
        mController.onStart(mLifecycleOwner);

        mController.refreshUi();

        verify(mBluetoothAdapter).startDiscovery();
    }

    @Test
    public void refreshUi_noDeviceBonding_enablesGroup() {
        mController.onCreate(mLifecycleOwner);
        mController.onStart(mLifecycleOwner);

        mController.refreshUi();

        assertThat(mPreferenceGroup.isEnabled()).isTrue();
    }

    @Test
    public void refreshUi_noDeviceBonding_setsScanModeConnectableDiscoverable() {
        mController.onCreate(mLifecycleOwner);
        mController.onStart(mLifecycleOwner);

        mController.refreshUi();

        verify(mBluetoothAdapter).setScanMode(BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE);
    }

    @Test
    public void refreshUi_notValidCallingPackage_doesNotSetScanMode() {
        when(BluetoothUtils.shouldEnableBTScanning(eq(mContext), any())).thenReturn(false);
        mController.onCreate(mLifecycleOwner);
        mController.onStart(mLifecycleOwner);

        mController.refreshUi();

        verify(mBluetoothAdapter, never())
                .setScanMode(BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE);
    }

    @Test
    public void refreshUi_deviceBonding_stopsScanning() {
        mController.onCreate(mLifecycleOwner);
        mController.onStart(mLifecycleOwner);
        when(mCachedDevice.getBondState()).thenReturn(BluetoothDevice.BOND_BONDING);

        mController.refreshUi();

        verify(mBluetoothAdapter).cancelDiscovery();
    }

    @Test
    public void refreshUi_deviceBonding_disablesGroup() {
        mController.onCreate(mLifecycleOwner);
        mController.onStart(mLifecycleOwner);
        when(mCachedDevice.getBondState()).thenReturn(BluetoothDevice.BOND_BONDING);

        mController.refreshUi();

        assertThat(mPreferenceGroup.isEnabled()).isFalse();
    }

    @Test
    public void refreshUi_deviceBonding_setsScanModeConnectable() {
        mController.onCreate(mLifecycleOwner);
        mController.onStart(mLifecycleOwner);
        when(mCachedDevice.getBondState()).thenReturn(BluetoothDevice.BOND_BONDING);

        mController.refreshUi();

        verify(mBluetoothAdapter).setScanMode(BluetoothAdapter.SCAN_MODE_CONNECTABLE);
    }

    @Test
    public void onStop_stopsScanning() {
        mController.onCreate(mLifecycleOwner);
        mController.onStart(mLifecycleOwner);
        verify(mBluetoothAdapter).startDiscovery();

        mController.onStop(mLifecycleOwner);

        verify(mBluetoothAdapter).cancelDiscovery();
    }

    @Test
    public void onStop_clearsNonBondedDevices() {
        mController.onCreate(mLifecycleOwner);
        mController.onStart(mLifecycleOwner);
        mController.onStop(mLifecycleOwner);

        verify(mCachedDeviceManager).clearNonBondedDevices();
    }

    @Test
    public void onStop_clearsGroup() {
        mController.onCreate(mLifecycleOwner);
        mController.onStart(mLifecycleOwner);
        assertThat(mPreferenceGroup.getPreferenceCount()).isGreaterThan(0);

        mController.onStop(mLifecycleOwner);

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(0);
    }

    @Test
    public void onStop_setsScanModeConnectable() {
        mController.onCreate(mLifecycleOwner);
        mController.onStart(mLifecycleOwner);
        Mockito.clearInvocations(mBluetoothAdapter);
        mController.onStop(mLifecycleOwner);

        verify(mBluetoothAdapter).setScanMode(BluetoothAdapter.SCAN_MODE_CONNECTABLE);
    }

    @Test
    public void discoverableScanModeTimeout_controllerStarted_resetsDiscoverableScanMode() {
        mController.onCreate(mLifecycleOwner);
        mController.onStart(mLifecycleOwner);

        when(mBluetoothAdapter.getScanMode()).thenReturn(BluetoothAdapter.SCAN_MODE_CONNECTABLE);
        mContext.sendBroadcast(new Intent(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED));

        verify(mBluetoothAdapter).setScanMode(BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE);
    }

    @Test
    public void discoverableScanModeTimeout_controllerStopped_doesNotResetDiscoverableScanMode() {
        mController.onCreate(mLifecycleOwner);
        mController.onStart(mLifecycleOwner);
        mController.onStop(mLifecycleOwner);

        Mockito.clearInvocations(mBluetoothAdapter);
        when(mBluetoothAdapter.getScanMode()).thenReturn(BluetoothAdapter.SCAN_MODE_CONNECTABLE);
        mContext.sendBroadcast(new Intent(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED));

        verify(mBluetoothAdapter, never())
                .setScanMode(BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE);
    }

    private static final class TestBluetoothScanningDevicesGroupPreferenceController extends
            BluetoothScanningDevicesGroupPreferenceController {

        private CachedBluetoothDevice mLastClickedDevice;

        TestBluetoothScanningDevicesGroupPreferenceController(Context context,
                String preferenceKey,
                FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
            super(context, preferenceKey, fragmentController, uxRestrictions);
        }

        @Override
        protected void onDeviceClickedInternal(CachedBluetoothDevice cachedDevice) {
            mLastClickedDevice = cachedDevice;
        }

        CachedBluetoothDevice getLastClickedDevice() {
            return mLastClickedDevice;
        }

        @Override
        protected BluetoothDeviceFilter.Filter getDeviceFilter() {
            return BluetoothDeviceFilter.ALL_FILTER;
        }
    }
}
