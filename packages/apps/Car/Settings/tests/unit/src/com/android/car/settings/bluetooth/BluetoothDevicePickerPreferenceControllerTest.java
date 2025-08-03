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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertThrows;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothDevicePicker;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothUuid;
import android.car.drivingstate.CarUxRestrictions;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.ParcelUuid;

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
import com.android.settingslib.bluetooth.BluetoothEventManager;
import com.android.settingslib.bluetooth.CachedBluetoothDevice;
import com.android.settingslib.bluetooth.CachedBluetoothDeviceManager;
import com.android.settingslib.bluetooth.LocalBluetoothManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.util.Arrays;

@RunWith(AndroidJUnit4.class)
public class BluetoothDevicePickerPreferenceControllerTest {
    private static final String ALLOWED_LAUNCH_PACKAGE = "com.android.car.settings.tests.unit";
    private static final String DEFAULT_LAUNCH_PACKAGE = "test.package";
    private static final String DEFAULT_LAUNCH_CLASS = "TestClass";

    private Context mContext = spy(ApplicationProvider.getApplicationContext());
    private LifecycleOwner mLifecycleOwner;
    private CarUxRestrictions mCarUxRestrictions;
    private PreferenceGroup mPreferenceGroup;
    private BluetoothDevicePickerPreferenceController mPreferenceController;
    private MockitoSession mSession;

    @Mock
    private LocalBluetoothManager mLocalBluetoothManager;
    @Mock
    private BluetoothEventManager mBluetoothEventManager;
    @Mock
    private FragmentController mFragmentController;
    @Mock
    private CachedBluetoothDevice mUnbondedCachedDevice;
    @Mock
    private BluetoothDevice mUnbondedDevice;
    @Mock
    private CachedBluetoothDevice mBondedCachedDevice;
    @Mock
    private BluetoothDevice mBondedDevice;
    @Mock
    private CachedBluetoothDeviceManager mCachedDeviceManager;
    @Mock
    private BluetoothAdapter mMockBluetoothAdapter;

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

        when(mLocalBluetoothManager.getCachedDeviceManager()).thenReturn(mCachedDeviceManager);
        when(mLocalBluetoothManager.getEventManager()).thenReturn(mBluetoothEventManager);
        when(mUnbondedDevice.getBondState()).thenReturn(BluetoothDevice.BOND_NONE);
        when(mUnbondedCachedDevice.getBondState()).thenReturn(BluetoothDevice.BOND_NONE);
        when(mUnbondedCachedDevice.getDevice()).thenReturn(mUnbondedDevice);
        when(mBondedDevice.getBondState()).thenReturn(BluetoothDevice.BOND_BONDED);
        when(mBondedCachedDevice.getBondState()).thenReturn(BluetoothDevice.BOND_BONDED);
        when(mBondedCachedDevice.getDevice()).thenReturn(mBondedDevice);
        when(mCachedDeviceManager.getCachedDevicesCopy()).thenReturn(
                Arrays.asList(mUnbondedCachedDevice, mBondedCachedDevice));
        // Make bonded device appear first in the list.
        when(mBondedCachedDevice.compareTo(mUnbondedCachedDevice)).thenReturn(-1);
        when(mUnbondedCachedDevice.compareTo(mBondedCachedDevice)).thenReturn(1);

        mSession = ExtendedMockito.mockitoSession()
                .mockStatic(BluetoothUtils.class)
                .strictness(Strictness.LENIENT)
                .startMocking();
        when(BluetoothUtils.getLocalBtManager(mContext)).thenReturn(mLocalBluetoothManager);
        when(BluetoothUtils.shouldEnableBTScanning(eq(mContext), any())).thenReturn(true);
        BluetoothManager bluetoothManager = mock(BluetoothManager.class);
        when(bluetoothManager.getAdapter()).thenReturn(mMockBluetoothAdapter);
        when(mContext.getSystemService(BluetoothManager.class)).thenReturn(bluetoothManager);

        mPreferenceController = new TestBluetoothDevicePickerPreferenceController(mContext,
                /* preferenceKey= */ "key", mFragmentController,
                mCarUxRestrictions);
        PreferenceManager preferenceManager = new PreferenceManager(mContext);
        PreferenceScreen screen = preferenceManager.createPreferenceScreen(mContext);
        mPreferenceGroup = new LogicalPreferenceGroup(mContext);
        screen.addPreference(mPreferenceGroup);
    }

    @After
    @UiThreadTest
    public void tearDown() {
        if (mSession != null) {
            mSession.finishMocking();
        }
    }

    @Test
    public void checkInitialized_noLaunchIntentSet_throwsIllegalStateException() {
        assertThrows(IllegalStateException.class,
                () -> PreferenceControllerTestUtil.assignPreference(mPreferenceController,
                        mPreferenceGroup));
    }

    @Test
    public void onStart_appliesFilterType() {
        // Setup device to pass the filter.
        when(mBondedDevice.getUuids()).thenReturn(new ParcelUuid[]{BluetoothUuid.A2DP_SINK});
        Intent launchIntent = createLaunchIntent(/* needsAuth= */ false,
                BluetoothDevicePicker.FILTER_TYPE_AUDIO, DEFAULT_LAUNCH_PACKAGE,
                DEFAULT_LAUNCH_CLASS);
        mPreferenceController.setLaunchIntent(launchIntent);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreferenceGroup);
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(1);
        assertThat(((BluetoothDevicePreference) mPreferenceGroup.getPreference(
                0)).getCachedDevice()).isEqualTo(mBondedCachedDevice);
    }

    @Test
    public void onDeviceClicked_callingPackageEqualToLaunchPackage_setsClassName() {
        ComponentName component = new ComponentName(ALLOWED_LAUNCH_PACKAGE, DEFAULT_LAUNCH_CLASS);
        Intent launchIntent = createLaunchIntent(/* needsAuth= */ true,
                BluetoothDevicePicker.FILTER_TYPE_ALL, component.getPackageName(),
                component.getClassName());
        mPreferenceController.setLaunchIntent(launchIntent);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreferenceGroup);
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);
        BluetoothDevicePreference devicePreference =
                (BluetoothDevicePreference) mPreferenceGroup.getPreference(0);

        devicePreference.performClick();

        ArgumentCaptor<Intent> captor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext).sendBroadcast(captor.capture());
        Intent pickedIntent = captor.getValue();
        assertThat(pickedIntent.getAction()).isEqualTo(
                BluetoothDevicePicker.ACTION_DEVICE_SELECTED);
        assertThat(pickedIntent.getComponent()).isEqualTo(component);
        assertThat((BluetoothDevice) pickedIntent.getParcelableExtra(
                BluetoothDevice.EXTRA_DEVICE)).isEqualTo(mBondedDevice);
    }

    @Test
    public void onDeviceClicked_callingPackageNotEqualToLaunchPackage_doesNotSetClassName() {
        ComponentName component = new ComponentName(DEFAULT_LAUNCH_PACKAGE, DEFAULT_LAUNCH_CLASS);
        Intent launchIntent = createLaunchIntent(/* needsAuth= */ true,
                BluetoothDevicePicker.FILTER_TYPE_ALL, component.getPackageName(),
                component.getClassName());
        mPreferenceController.setLaunchIntent(launchIntent);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreferenceGroup);
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);
        BluetoothDevicePreference devicePreference =
                (BluetoothDevicePreference) mPreferenceGroup.getPreference(0);

        devicePreference.performClick();

        ArgumentCaptor<Intent> captor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext).sendBroadcast(captor.capture());
        Intent pickedIntent = captor.getValue();
        assertThat(pickedIntent.getAction()).isEqualTo(
                BluetoothDevicePicker.ACTION_DEVICE_SELECTED);
        assertThat(pickedIntent.getComponent()).isEqualTo(null);
        assertThat((BluetoothDevice) pickedIntent.getParcelableExtra(
                BluetoothDevice.EXTRA_DEVICE)).isEqualTo(mBondedDevice);
    }

    @Test
    public void onDeviceClicked_bondedDevice_goesBack() {
        Intent launchIntent = createLaunchIntent(/* needsAuth= */ true,
                BluetoothDevicePicker.FILTER_TYPE_ALL, DEFAULT_LAUNCH_PACKAGE,
                DEFAULT_LAUNCH_CLASS);
        mPreferenceController.setLaunchIntent(launchIntent);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreferenceGroup);
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);
        BluetoothDevicePreference devicePreference =
                (BluetoothDevicePreference) mPreferenceGroup.getPreference(0);

        devicePreference.performClick();

        verify(mFragmentController).goBack();
    }

    @Test
    public void onDeviceClicked_unbondedDevice_doesNotNeedAuth_sendsPickedIntent() {
        Intent launchIntent = createLaunchIntent(/* needsAuth= */ false,
                BluetoothDevicePicker.FILTER_TYPE_ALL, DEFAULT_LAUNCH_PACKAGE,
                DEFAULT_LAUNCH_CLASS);
        mPreferenceController.setLaunchIntent(launchIntent);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreferenceGroup);
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);
        BluetoothDevicePreference devicePreference =
                (BluetoothDevicePreference) mPreferenceGroup.getPreference(1);

        devicePreference.performClick();

        ArgumentCaptor<Intent> captor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext).sendBroadcast(captor.capture());
        Intent pickedIntent = captor.getValue();
        assertThat(pickedIntent.getAction()).isEqualTo(
                BluetoothDevicePicker.ACTION_DEVICE_SELECTED);
    }

    @Test
    public void onDeviceClicked_unbondedDevice_needsAuth_startsPairing() {
        Intent launchIntent = createLaunchIntent(/* needsAuth= */ true,
                BluetoothDevicePicker.FILTER_TYPE_ALL, DEFAULT_LAUNCH_PACKAGE,
                DEFAULT_LAUNCH_CLASS);
        mPreferenceController.setLaunchIntent(launchIntent);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreferenceGroup);
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);
        BluetoothDevicePreference devicePreference =
                (BluetoothDevicePreference) mPreferenceGroup.getPreference(1);

        devicePreference.performClick();

        verify(mUnbondedCachedDevice).startPairing();
    }

    @Test
    public void onDeviceClicked_unbondedDevice_needsAuth_pairingStartFails_resumesScanning() {
        Intent launchIntent = createLaunchIntent(/* needsAuth= */ true,
                BluetoothDevicePicker.FILTER_TYPE_ALL, DEFAULT_LAUNCH_PACKAGE,
                DEFAULT_LAUNCH_CLASS);
        mPreferenceController.setLaunchIntent(launchIntent);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreferenceGroup);
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);
        verify(mMockBluetoothAdapter).startDiscovery();
        BluetoothDevicePreference devicePreference =
                (BluetoothDevicePreference) mPreferenceGroup.getPreference(1);
        when(mUnbondedCachedDevice.startPairing()).thenReturn(false);

        Mockito.clearInvocations(mMockBluetoothAdapter);
        devicePreference.performClick();
        InOrder inOrder = inOrder(mMockBluetoothAdapter);
        inOrder.verify(mMockBluetoothAdapter).setScanMode(BluetoothAdapter.SCAN_MODE_CONNECTABLE);
        inOrder.verify(mMockBluetoothAdapter).startDiscovery();
    }

    @Test
    public void onDeviceBondStateChanged_selectedDeviceBonded_sendsPickedIntent() {
        Intent launchIntent = createLaunchIntent(/* needsAuth= */ true,
                BluetoothDevicePicker.FILTER_TYPE_ALL, DEFAULT_LAUNCH_PACKAGE,
                DEFAULT_LAUNCH_CLASS);
        mPreferenceController.setLaunchIntent(launchIntent);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreferenceGroup);
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);
        BluetoothDevicePreference devicePreference =
                (BluetoothDevicePreference) mPreferenceGroup.getPreference(1);

        // Select device.
        devicePreference.performClick();
        // Device bonds.
        mPreferenceController.onDeviceBondStateChanged(
                devicePreference.getCachedDevice(), BluetoothDevice.BOND_BONDED);

        ArgumentCaptor<Intent> captor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext).sendBroadcast(captor.capture());
        Intent pickedIntent = captor.getValue();
        assertThat(pickedIntent.getAction()).isEqualTo(
                BluetoothDevicePicker.ACTION_DEVICE_SELECTED);
    }

    @Test
    public void onDeviceBondStateChanged_selectedDeviceBonded_goesBack() {
        Intent launchIntent = createLaunchIntent(/* needsAuth= */ true,
                BluetoothDevicePicker.FILTER_TYPE_ALL, DEFAULT_LAUNCH_PACKAGE,
                DEFAULT_LAUNCH_CLASS);
        mPreferenceController.setLaunchIntent(launchIntent);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreferenceGroup);
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);
        BluetoothDevicePreference devicePreference =
                (BluetoothDevicePreference) mPreferenceGroup.getPreference(1);

        // Select device.
        devicePreference.performClick();
        // Device bonds.
        mPreferenceController.onDeviceBondStateChanged(
                devicePreference.getCachedDevice(), BluetoothDevice.BOND_BONDED);

        verify(mFragmentController).goBack();
    }

    @Test
    public void onDestroy_noDeviceSelected_sendsNullPickedIntent() {
        Intent launchIntent = createLaunchIntent(/* needsAuth= */ true,
                BluetoothDevicePicker.FILTER_TYPE_ALL, DEFAULT_LAUNCH_PACKAGE,
                DEFAULT_LAUNCH_CLASS);
        mPreferenceController.setLaunchIntent(launchIntent);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreferenceGroup);
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);

        mPreferenceController.onDestroy(mLifecycleOwner);

        ArgumentCaptor<Intent> captor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext).sendBroadcast(captor.capture());
        Intent pickedIntent = captor.getValue();
        assertThat(pickedIntent.getAction()).isEqualTo(
                BluetoothDevicePicker.ACTION_DEVICE_SELECTED);
        assertThat((BluetoothDevice) pickedIntent.getParcelableExtra(
                BluetoothDevice.EXTRA_DEVICE)).isNull();
    }

    private Intent createLaunchIntent(boolean needAuth, int filterType, String packageName,
            String className) {
        Intent intent = new Intent(BluetoothDevicePicker.ACTION_LAUNCH);
        intent.putExtra(BluetoothDevicePicker.EXTRA_NEED_AUTH, needAuth);
        intent.putExtra(BluetoothDevicePicker.EXTRA_FILTER_TYPE, filterType);
        intent.putExtra(BluetoothDevicePicker.EXTRA_LAUNCH_PACKAGE, packageName);
        intent.putExtra(BluetoothDevicePicker.EXTRA_LAUNCH_CLASS, className);
        return intent;
    }

    private static class TestBluetoothDevicePickerPreferenceController
            extends BluetoothDevicePickerPreferenceController {

        TestBluetoothDevicePickerPreferenceController(Context context, String preferenceKey,
                FragmentController fragmentController,
                CarUxRestrictions uxRestrictions) {
            super(context, preferenceKey, fragmentController, uxRestrictions);
        }

        @Override
        String getCallingAppPackageName(IBinder activityToken) {
            return ALLOWED_LAUNCH_PACKAGE;
        }
    }
}
