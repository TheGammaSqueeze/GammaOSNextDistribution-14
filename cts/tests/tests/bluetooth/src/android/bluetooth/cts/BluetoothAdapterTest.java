/*
 * Copyright (C) 2009 The Android Open Source Project
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

package android.bluetooth.cts;

import static android.Manifest.permission.BLUETOOTH_CONNECT;
import static android.Manifest.permission.BLUETOOTH_PRIVILEGED;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.annotation.NonNull;
import android.app.UiAutomation;
import android.bluetooth.BluetoothActivityEnergyInfo;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothQualityReport;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothStatusCodes;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemProperties;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bluetooth.flags.Flags;
import com.android.compatibility.common.util.ApiLevelUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Very basic test, just of the static methods of {@link BluetoothAdapter}.
 */
@RunWith(AndroidJUnit4.class)
@MediumTest
public class BluetoothAdapterTest {
    private static final String TAG = "BluetoothAdapterTest";
    private static final int SET_NAME_TIMEOUT = 5000; // ms timeout for setting adapter name
    private static final String ENABLE_DUAL_MODE_AUDIO =
            "persist.bluetooth.enable_dual_mode_audio";

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    private Context mContext;
    private boolean mHasBluetooth;
    private ReentrantLock mAdapterNameChangedlock;
    private Condition mConditionAdapterNameChanged;
    private boolean mIsAdapterNameChanged;

    private BluetoothAdapter mAdapter;
    private UiAutomation mUiAutomation;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mHasBluetooth = mContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_BLUETOOTH);
        if (mHasBluetooth) {
            mAdapter = mContext.getSystemService(BluetoothManager.class).getAdapter();
            assertNotNull(mAdapter);
            mUiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
            mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);
        }
        mAdapterNameChangedlock = new ReentrantLock();
        mConditionAdapterNameChanged = mAdapterNameChangedlock.newCondition();
        mIsAdapterNameChanged = false;
    }

    @After
    public void tearDown() {
        if (mHasBluetooth) {
            mUiAutomation.dropShellPermissionIdentity();
        }
    }

    @Test
    public void getDefaultAdapter() {
        /*
         * Note: If the target doesn't support Bluetooth at all, then
         * this method should return null.
         */
        if (mHasBluetooth) {
            assertNotNull(BluetoothAdapter.getDefaultAdapter());
        } else {
            assertNull(BluetoothAdapter.getDefaultAdapter());
        }
    }

    @Test
    public void checkBluetoothAddress() {
        // Can't be null.
        assertFalse(BluetoothAdapter.checkBluetoothAddress(null));

        // Must be 17 characters long.
        assertFalse(BluetoothAdapter.checkBluetoothAddress(""));
        assertFalse(BluetoothAdapter.checkBluetoothAddress("0"));
        assertFalse(BluetoothAdapter.checkBluetoothAddress("00"));
        assertFalse(BluetoothAdapter.checkBluetoothAddress("00:"));
        assertFalse(BluetoothAdapter.checkBluetoothAddress("00:0"));
        assertFalse(BluetoothAdapter.checkBluetoothAddress("00:00"));
        assertFalse(BluetoothAdapter.checkBluetoothAddress("00:00:"));
        assertFalse(BluetoothAdapter.checkBluetoothAddress("00:00:0"));
        assertFalse(BluetoothAdapter.checkBluetoothAddress("00:00:00"));
        assertFalse(BluetoothAdapter.checkBluetoothAddress("00:00:00:"));
        assertFalse(BluetoothAdapter.checkBluetoothAddress("00:00:00:0"));
        assertFalse(BluetoothAdapter.checkBluetoothAddress("00:00:00:00"));
        assertFalse(BluetoothAdapter.checkBluetoothAddress("00:00:00:00:"));
        assertFalse(BluetoothAdapter.checkBluetoothAddress("00:00:00:00:0"));
        assertFalse(BluetoothAdapter.checkBluetoothAddress("00:00:00:00:00"));
        assertFalse(BluetoothAdapter.checkBluetoothAddress("00:00:00:00:00:"));
        assertFalse(BluetoothAdapter.checkBluetoothAddress("00:00:00:00:00:0"));

        // Must have colons between octets.
        assertFalse(BluetoothAdapter.checkBluetoothAddress("00x00:00:00:00:00"));
        assertFalse(BluetoothAdapter.checkBluetoothAddress("00:00.00:00:00:00"));
        assertFalse(BluetoothAdapter.checkBluetoothAddress("00:00:00-00:00:00"));
        assertFalse(BluetoothAdapter.checkBluetoothAddress("00:00:00:00900:00"));
        assertFalse(BluetoothAdapter.checkBluetoothAddress("00:00:00:00:00?00"));

        // Hex letters must be uppercase.
        assertFalse(BluetoothAdapter.checkBluetoothAddress("a0:00:00:00:00:00"));
        assertFalse(BluetoothAdapter.checkBluetoothAddress("0b:00:00:00:00:00"));
        assertFalse(BluetoothAdapter.checkBluetoothAddress("00:c0:00:00:00:00"));
        assertFalse(BluetoothAdapter.checkBluetoothAddress("00:0d:00:00:00:00"));
        assertFalse(BluetoothAdapter.checkBluetoothAddress("00:00:e0:00:00:00"));
        assertFalse(BluetoothAdapter.checkBluetoothAddress("00:00:0f:00:00:00"));

        assertTrue(BluetoothAdapter.checkBluetoothAddress("00:00:00:00:00:00"));
        assertTrue(BluetoothAdapter.checkBluetoothAddress("12:34:56:78:9A:BC"));
        assertTrue(BluetoothAdapter.checkBluetoothAddress("DE:F0:FE:DC:B8:76"));
    }

    /** Checks enable(), disable(), getState(), isEnabled() */
    @Test
    public void enableDisable() {
        assumeTrue(mHasBluetooth);

        for (int i = 0; i < 5; i++) {
            assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));
            assertTrue(BTAdapterUtils.enableAdapter(mAdapter, mContext));
        }
    }

    @Test
    public void getAddress() {
        assumeTrue(mHasBluetooth);

        assertTrue(BTAdapterUtils.enableAdapter(mAdapter, mContext));
        assertTrue(BluetoothAdapter.checkBluetoothAddress(mAdapter.getAddress()));

        mUiAutomation.dropShellPermissionIdentity();
        assertThrows(SecurityException.class, () -> mAdapter.getAddress());

    }

    @Test
    public void setName_getName() {
        assumeTrue(mHasBluetooth);

        assertTrue(BTAdapterUtils.enableAdapter(mAdapter, mContext));

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_LOCAL_NAME_CHANGED);
        filter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        mContext.registerReceiver(mAdapterNameChangeReceiver, filter);

        String name = mAdapter.getName();
        assertNotNull(name);

        // Check renaming the adapter
        String genericName = "Generic Device 1";
        mIsAdapterNameChanged = false;
        assertTrue(mAdapter.setName(genericName));
        assertTrue(waitForAdapterNameChange());
        mIsAdapterNameChanged = false;
        assertEquals(genericName, mAdapter.getName());

        // Check setting adapter back to original name
        assertTrue(mAdapter.setName(name));
        assertTrue(waitForAdapterNameChange());
        mIsAdapterNameChanged = false;
        assertEquals(name, mAdapter.getName());

        mUiAutomation.dropShellPermissionIdentity();
        assertThrows(SecurityException.class, () -> mAdapter.setName("The name"));
        assertThrows(SecurityException.class, () -> mAdapter.getName());
    }

    @Test
    public void getBondedDevices() {
        assumeTrue(mHasBluetooth);

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // empty value is returned when Bluetooth is disabled
        Set<BluetoothDevice> devices = mAdapter.getBondedDevices();
        assertNotNull(devices);
        assertTrue(devices.isEmpty());

        assertTrue(BTAdapterUtils.enableAdapter(mAdapter, mContext));
        devices = mAdapter.getBondedDevices();
        assertNotNull(devices);
        for (BluetoothDevice device : devices) {
            assertTrue(BluetoothAdapter.checkBluetoothAddress(device.getAddress()));
        }

        mUiAutomation.dropShellPermissionIdentity();
        assertThrows(SecurityException.class, () -> mAdapter.getBondedDevices());

    }

    @Test
    public void getProfileConnectionState() {
        assumeTrue(mHasBluetooth);

        mUiAutomation.dropShellPermissionIdentity();
        // getProfileConnectionState is caching it's return value and cts test doesn't know how to
        // deal with it
        // assertThrows(SecurityException.class,
        //         () -> mAdapter.getProfileConnectionState(BluetoothProfile.A2DP));
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);
        assertEquals(mAdapter.getProfileConnectionState(BluetoothProfile.A2DP),
                BluetoothAdapter.STATE_DISCONNECTED);
        assertTrue(BTAdapterUtils.enableAdapter(mAdapter, mContext));
        assertEquals(mAdapter.getProfileConnectionState(BluetoothProfile.A2DP),
                BluetoothAdapter.STATE_DISCONNECTED);
    }

    @Test
    public void getRemoteDevice() {
        assumeTrue(mHasBluetooth);

        // getRemoteDevice() should work even with Bluetooth disabled
        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));
        mUiAutomation.dropShellPermissionIdentity();

        // test bad addresses
        assertThrows(IllegalArgumentException.class, () -> mAdapter.getRemoteDevice((String) null));
        assertThrows(IllegalArgumentException.class, () ->
                mAdapter.getRemoteDevice("00:00:00:00:00:00:00:00"));
        assertThrows(IllegalArgumentException.class, () -> mAdapter.getRemoteDevice((byte[]) null));
        assertThrows(IllegalArgumentException.class, () ->
                mAdapter.getRemoteDevice(new byte[] {0x00, 0x00, 0x00, 0x00, 0x00}));

        // test success
        BluetoothDevice device = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");
        assertNotNull(device);
        assertEquals("00:11:22:AA:BB:CC", device.getAddress());
        device = mAdapter.getRemoteDevice(
                new byte[] {0x01, 0x02, 0x03, 0x04, 0x05, 0x06});
        assertNotNull(device);
        assertEquals("01:02:03:04:05:06", device.getAddress());
    }

    @Test
    public void getRemoteLeDevice() {
        assumeTrue(mHasBluetooth);

        // getRemoteLeDevice() should work even with Bluetooth disabled
        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));
        mUiAutomation.dropShellPermissionIdentity();

        // test bad addresses
        assertThrows(IllegalArgumentException.class,
                () -> mAdapter.getRemoteLeDevice((String) null,
                                                 BluetoothDevice.ADDRESS_TYPE_PUBLIC));
        assertThrows(IllegalArgumentException.class,
                () -> mAdapter.getRemoteLeDevice("01:02:03:04:05:06:07:08",
                                                 BluetoothDevice.ADDRESS_TYPE_PUBLIC));
        assertThrows(IllegalArgumentException.class,
                () -> mAdapter.getRemoteLeDevice("01:02:03:04:05",
                                                 BluetoothDevice.ADDRESS_TYPE_PUBLIC));
        assertThrows(IllegalArgumentException.class,
                () -> mAdapter.getRemoteLeDevice("00:01:02:03:04:05",
                                                 BluetoothDevice.ADDRESS_TYPE_RANDOM + 1));
        assertThrows(IllegalArgumentException.class,
                () -> mAdapter.getRemoteLeDevice("00:01:02:03:04:05",
                                                 BluetoothDevice.ADDRESS_TYPE_PUBLIC - 1));

        // test success
        BluetoothDevice device = mAdapter.getRemoteLeDevice("00:11:22:AA:BB:CC",
                BluetoothDevice.ADDRESS_TYPE_PUBLIC);
        assertNotNull(device);
        assertEquals("00:11:22:AA:BB:CC", device.getAddress());
        device = mAdapter.getRemoteLeDevice("01:02:03:04:05:06",
                BluetoothDevice.ADDRESS_TYPE_RANDOM);
        assertNotNull(device);
        assertEquals("01:02:03:04:05:06", device.getAddress());
    }

    @Test
    public void isLeAudioSupported() throws IOException {
        assumeTrue(mHasBluetooth);

        assertNotSame(BluetoothStatusCodes.ERROR_UNKNOWN, mAdapter.isLeAudioSupported());
    }

    @Test
    public void isLeAudioBroadcastSourceSupported() throws IOException {
        assumeTrue(mHasBluetooth);

        assertNotSame(BluetoothStatusCodes.ERROR_UNKNOWN,
                mAdapter.isLeAudioBroadcastSourceSupported());
    }

    @Test
    public void isLeAudioBroadcastAssistantSupported() throws IOException {
        assumeTrue(mHasBluetooth);

        assertNotSame(BluetoothStatusCodes.ERROR_UNKNOWN,
                mAdapter.isLeAudioBroadcastAssistantSupported());
    }

    @Test
    public void isDistanceMeasurementSupported() throws IOException {
        assumeTrue(mHasBluetooth);

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
        assertNotSame(BluetoothStatusCodes.ERROR_UNKNOWN,
                mAdapter.isDistanceMeasurementSupported());
        TestUtils.dropPermissionAsShellUid();
    }

    @Test
    public void getMaxConnectedAudioDevices() {
        assumeTrue(mHasBluetooth);

        // Defined in com.android.bluetooth.btservice.AdapterProperties
        int maxConnectedAudioDevicesLowerBound = 1;
        // Defined in com.android.bluetooth.btservice.AdapterProperties
        int maxConnectedAudioDevicesUpperBound = 5;

        assertTrue(BTAdapterUtils.enableAdapter(mAdapter, mContext));
        assertTrue(mAdapter.getMaxConnectedAudioDevices() >= maxConnectedAudioDevicesLowerBound);
        assertTrue(mAdapter.getMaxConnectedAudioDevices() <= maxConnectedAudioDevicesUpperBound);

        mUiAutomation.dropShellPermissionIdentity();
        assertThrows(SecurityException.class, () -> mAdapter.getMaxConnectedAudioDevices());
    }

    @Test
    public void listenUsingRfcommWithServiceRecord() throws IOException {
        assumeTrue(mHasBluetooth);

        assertTrue(BTAdapterUtils.enableAdapter(mAdapter, mContext));
        BluetoothServerSocket socket = mAdapter.listenUsingRfcommWithServiceRecord(
                "test", UUID.randomUUID());
        assertNotNull(socket);
        socket.close();

        mUiAutomation.dropShellPermissionIdentity();
        assertThrows(SecurityException.class, () -> mAdapter.listenUsingRfcommWithServiceRecord(
                    "test", UUID.randomUUID()));
    }

    @Test
    public void discoverableTimeout() {
        assumeTrue(mHasBluetooth);

        Duration minutes = Duration.ofMinutes(2);

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));
        assertEquals(null, mAdapter.getDiscoverableTimeout());
        assertEquals(BluetoothStatusCodes.ERROR_BLUETOOTH_NOT_ENABLED,
                mAdapter.setDiscoverableTimeout(minutes));

        assertTrue(BTAdapterUtils.enableAdapter(mAdapter, mContext));
        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
        assertThrows(IllegalArgumentException.class, () -> mAdapter.setDiscoverableTimeout(
                Duration.ofDays(25000)));
        assertEquals(BluetoothStatusCodes.SUCCESS,
                mAdapter.setDiscoverableTimeout(minutes));
        assertEquals(minutes, mAdapter.getDiscoverableTimeout());
    }

    @Test
    public void getConnectionState() {
        assumeTrue(mHasBluetooth);

        // Verify return value if Bluetooth is not enabled
        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));
        assertEquals(BluetoothProfile.STATE_DISCONNECTED, mAdapter.getConnectionState());
    }

    @Test
    public void getMostRecentlyConnectedDevices() {
        assumeTrue(mHasBluetooth);

        assertTrue(BTAdapterUtils.enableAdapter(mAdapter, mContext));

        // Verify throws SecurityException without permission.BLUETOOTH_PRIVILEGED
        assertThrows(SecurityException.class, () -> mAdapter.getMostRecentlyConnectedDevices());

        // Verify return value if Bluetooth is not enabled
        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));
        List<BluetoothDevice> devices = mAdapter.getMostRecentlyConnectedDevices();
        assertTrue(devices.isEmpty());
    }

    @Test
    public void getUuids() {
        assumeTrue(mHasBluetooth);

        assertTrue(BTAdapterUtils.enableAdapter(mAdapter, mContext));

        // Verify return value without permission.BLUETOOTH_CONNECT
        mUiAutomation.dropShellPermissionIdentity();
        assertThrows(SecurityException.class, () -> mAdapter.getUuidsList());
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);

        assertNotNull(mAdapter.getUuidsList());
        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify return value if Bluetooth is not enabled
        assertEquals(0, mAdapter.getUuidsList().size());

    }

    @Test
    public void nameForState() {
        assertEquals("ON", BluetoothAdapter.nameForState(BluetoothAdapter.STATE_ON));
        assertEquals("OFF", BluetoothAdapter.nameForState(BluetoothAdapter.STATE_OFF));
        assertEquals("TURNING_ON",
                BluetoothAdapter.nameForState(BluetoothAdapter.STATE_TURNING_ON));
        assertEquals("TURNING_OFF",
                BluetoothAdapter.nameForState(BluetoothAdapter.STATE_TURNING_OFF));

        assertEquals("BLE_ON", BluetoothAdapter.nameForState(BluetoothAdapter.STATE_BLE_ON));

        // Check value before state range
        for (int state = 0; state < BluetoothAdapter.STATE_OFF; state++) {
            assertEquals("?!?!? (" + state + ")", BluetoothAdapter.nameForState(state));
        }
        // Check value after state range (skip TURNING_OFF)
        for (int state = BluetoothAdapter.STATE_BLE_ON + 2; state < 100; state++) {
            assertEquals("?!?!? (" + state + ")", BluetoothAdapter.nameForState(state));
        }
    }

    @Test
    public void BluetoothConnectionCallback_disconnectReasonText() {
        assertEquals("Reason unknown", BluetoothAdapter.BluetoothConnectionCallback
                .disconnectReasonToString(BluetoothStatusCodes.ERROR_UNKNOWN));
    }

    @Test
    public void registerBluetoothConnectionCallback() {
        assumeTrue(mHasBluetooth);

        Executor executor = mContext.getMainExecutor();
        BluetoothAdapter.BluetoothConnectionCallback callback =
                new BluetoothAdapter.BluetoothConnectionCallback() {
                    @Override
                    public void onDeviceConnected(@NonNull BluetoothDevice device) {}
                    @Override
                    public void onDeviceDisconnected(BluetoothDevice device, int reason) {}

                };

        // placeholder call for coverage
        callback.onDeviceConnected(null);
        callback.onDeviceDisconnected(null, BluetoothStatusCodes.ERROR_UNKNOWN);

        // Verify parameter
        assertFalse(mAdapter.registerBluetoothConnectionCallback(null, callback));
        assertFalse(mAdapter.registerBluetoothConnectionCallback(executor, null));
        assertFalse(mAdapter.unregisterBluetoothConnectionCallback(null));

        assertTrue(BTAdapterUtils.enableAdapter(mAdapter, mContext));

        // Verify throws SecurityException without permission.BLUETOOTH_PRIVILEGED
        assertThrows(SecurityException.class,
                () -> mAdapter.registerBluetoothConnectionCallback(executor, callback));

        mUiAutomation.dropShellPermissionIdentity();
        // Verify throws SecurityException without permission.BLUETOOTH_CONNECT
        assertThrows(SecurityException.class, () ->
                mAdapter.registerBluetoothConnectionCallback(executor, callback));
        assertThrows(SecurityException.class, () ->
                mAdapter.unregisterBluetoothConnectionCallback(callback));
    }

    @Test
    public void requestControllerActivityEnergyInfo() {
        assumeTrue(mHasBluetooth);

        BluetoothAdapter.OnBluetoothActivityEnergyInfoCallback callback =
                new BluetoothAdapter.OnBluetoothActivityEnergyInfoCallback() {
                    @Override
                    public void onBluetoothActivityEnergyInfoAvailable(
                            BluetoothActivityEnergyInfo info) {
                        assertNotNull(info);
                    }

                    @Override
                    public void onBluetoothActivityEnergyInfoError(int errorCode) {}
                };

        // Verify parameter
        assertThrows(NullPointerException.class,
                () -> mAdapter.requestControllerActivityEnergyInfo(null, callback));
    }

    @Test
    public void clearBluetooth() {
        assumeTrue(mHasBluetooth);

        assertTrue(BTAdapterUtils.enableAdapter(mAdapter, mContext));

        // Verify throws SecurityException without permission.BLUETOOTH_PRIVILEGED
        assertThrows(SecurityException.class, () -> mAdapter.clearBluetooth());
        mUiAutomation.dropShellPermissionIdentity();
        // Verify throws SecurityException without permission.BLUETOOTH_CONNECT
        assertThrows(SecurityException.class, () -> mAdapter.clearBluetooth());

        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);
        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));
        // Verify throws RuntimeException when trying to save sysprop for later (permission denied)
        assertThrows(RuntimeException.class, () -> mAdapter.clearBluetooth());
    }

    @Test
    public void BluetoothProfile_getConnectionStateName() {
        assumeTrue(mHasBluetooth);

        assertEquals("STATE_DISCONNECTED",
                BluetoothProfile.getConnectionStateName(BluetoothProfile.STATE_DISCONNECTED));
        assertEquals("STATE_CONNECTED",
                BluetoothProfile.getConnectionStateName(BluetoothProfile.STATE_CONNECTED));
        assertEquals("STATE_CONNECTING",
                BluetoothProfile.getConnectionStateName(BluetoothProfile.STATE_CONNECTING));
        assertEquals("STATE_CONNECTED",
                BluetoothProfile.getConnectionStateName(BluetoothProfile.STATE_CONNECTED));
        assertEquals("STATE_DISCONNECTING",
                BluetoothProfile.getConnectionStateName(BluetoothProfile.STATE_DISCONNECTING));
        assertEquals("STATE_UNKNOWN",
                BluetoothProfile.getConnectionStateName(BluetoothProfile.STATE_DISCONNECTING + 1));
    }

    @Test
    public void BluetoothProfile_getProfileName() {
        assertEquals("HEADSET",
                BluetoothProfile.getProfileName(BluetoothProfile.HEADSET));
        assertEquals("A2DP",
                BluetoothProfile.getProfileName(BluetoothProfile.A2DP));
        assertEquals("HID_HOST",
                BluetoothProfile.getProfileName(BluetoothProfile.HID_HOST));
        assertEquals("PAN",
                BluetoothProfile.getProfileName(BluetoothProfile.PAN));
        assertEquals("PBAP",
                BluetoothProfile.getProfileName(BluetoothProfile.PBAP));
        assertEquals("GATT",
                BluetoothProfile.getProfileName(BluetoothProfile.GATT));
        assertEquals("GATT_SERVER",
                BluetoothProfile.getProfileName(BluetoothProfile.GATT_SERVER));
        assertEquals("MAP",
                BluetoothProfile.getProfileName(BluetoothProfile.MAP));
        assertEquals("SAP",
                BluetoothProfile.getProfileName(BluetoothProfile.SAP));
        assertEquals("A2DP_SINK",
                BluetoothProfile.getProfileName(BluetoothProfile.A2DP_SINK));
        assertEquals("AVRCP_CONTROLLER",
                BluetoothProfile.getProfileName(BluetoothProfile.AVRCP_CONTROLLER));
        // assertEquals("AVRCP",
        //         BluetoothProfile.getProfileName(BluetoothProfile.AVRCP));
        assertEquals("HEADSET_CLIENT",
                BluetoothProfile.getProfileName(BluetoothProfile.HEADSET_CLIENT));
        assertEquals("PBAP_CLIENT",
                BluetoothProfile.getProfileName(BluetoothProfile.PBAP_CLIENT));
        assertEquals("MAP_CLIENT",
                BluetoothProfile.getProfileName(BluetoothProfile.MAP_CLIENT));
        assertEquals("HID_DEVICE",
                BluetoothProfile.getProfileName(BluetoothProfile.HID_DEVICE));
        assertEquals("OPP",
                BluetoothProfile.getProfileName(BluetoothProfile.OPP));
        assertEquals("HEARING_AID",
                BluetoothProfile.getProfileName(BluetoothProfile.HEARING_AID));
        assertEquals("LE_AUDIO",
                BluetoothProfile.getProfileName(BluetoothProfile.LE_AUDIO));
        assertEquals("HAP_CLIENT",
                BluetoothProfile.getProfileName(BluetoothProfile.HAP_CLIENT));

        if (!ApiLevelUtil.isAtLeast(Build.VERSION_CODES.TIRAMISU)) {
            return;
        }

        assertEquals("VOLUME_CONTROL",
                BluetoothProfile.getProfileName(BluetoothProfile.VOLUME_CONTROL));
        assertEquals("CSIP_SET_COORDINATOR",
                BluetoothProfile.getProfileName(BluetoothProfile.CSIP_SET_COORDINATOR));
        assertEquals("LE_AUDIO_BROADCAST",
                BluetoothProfile.getProfileName(BluetoothProfile.LE_AUDIO_BROADCAST));
        assertEquals("LE_AUDIO_BROADCAST_ASSISTANT",
                BluetoothProfile.getProfileName(BluetoothProfile.LE_AUDIO_BROADCAST_ASSISTANT));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_AUTO_ON_FEATURE)
    public void autoOnApi() {
        assumeTrue(mHasBluetooth);

        assertThrows(SecurityException.class, () -> mAdapter.isAutoOnSupported());
        assertThrows(SecurityException.class, () -> mAdapter.isAutoOnEnabled());
        assertThrows(SecurityException.class, () -> mAdapter.setAutoOnEnabled(false));

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_PRIVILEGED);

        // Not all devices support the auto on feature
        assumeTrue(mAdapter.isAutoOnSupported());

        mAdapter.setAutoOnEnabled(false);
        assertEquals(false, mAdapter.isAutoOnEnabled());

        mAdapter.setAutoOnEnabled(true);
        assertEquals(true, mAdapter.isAutoOnEnabled());
    }

    @Test
    public void getSetBluetoothHciSnoopLoggingMode() {
        assumeTrue(mHasBluetooth);

        assertThrows(SecurityException.class, () -> mAdapter
                .setBluetoothHciSnoopLoggingMode(BluetoothAdapter.BT_SNOOP_LOG_MODE_FULL));
        assertThrows(SecurityException.class, () -> mAdapter
                .getBluetoothHciSnoopLoggingMode());

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_PRIVILEGED);

        assertThrows(IllegalArgumentException.class, () -> mAdapter
                .setBluetoothHciSnoopLoggingMode(-1));

        assertEquals(BluetoothStatusCodes.SUCCESS, mAdapter
                .setBluetoothHciSnoopLoggingMode(BluetoothAdapter.BT_SNOOP_LOG_MODE_FULL));
        assertEquals(mAdapter.getBluetoothHciSnoopLoggingMode(),
                BluetoothAdapter.BT_SNOOP_LOG_MODE_FULL);

        assertEquals(BluetoothStatusCodes.SUCCESS, mAdapter
                .setBluetoothHciSnoopLoggingMode(BluetoothAdapter.BT_SNOOP_LOG_MODE_FILTERED));
        assertEquals(mAdapter.getBluetoothHciSnoopLoggingMode(),
                BluetoothAdapter.BT_SNOOP_LOG_MODE_FILTERED);

        assertEquals(BluetoothStatusCodes.SUCCESS, mAdapter
                .setBluetoothHciSnoopLoggingMode(BluetoothAdapter.BT_SNOOP_LOG_MODE_DISABLED));
        assertEquals(mAdapter.getBluetoothHciSnoopLoggingMode(),
                BluetoothAdapter.BT_SNOOP_LOG_MODE_DISABLED);

    }

    @Test
    public void setPreferredAudioProfiles_getPreferredAudioProfiles() {
        assumeTrue(mHasBluetooth);

        assertTrue(BTAdapterUtils.enableAdapter(mAdapter, mContext));
        String deviceAddress = "00:11:22:AA:BB:CC";
        BluetoothDevice device = mAdapter.getRemoteDevice(deviceAddress);

        Bundle preferences = new Bundle();
        preferences.putInt(BluetoothAdapter.AUDIO_MODE_OUTPUT_ONLY, BluetoothProfile.HEADSET);

        // Test invalid input
        assertThrows(NullPointerException.class, () ->
                mAdapter.setPreferredAudioProfiles(device, null));
        assertThrows(IllegalArgumentException.class,
                () -> mAdapter.setPreferredAudioProfiles(device, preferences));
        assertThrows(NullPointerException.class, () -> mAdapter.getPreferredAudioProfiles(null));

        preferences.putInt(BluetoothAdapter.AUDIO_MODE_OUTPUT_ONLY, BluetoothProfile.HID_HOST);
        assertThrows(IllegalArgumentException.class,
                () -> mAdapter.setPreferredAudioProfiles(device, preferences));

        preferences.putInt(BluetoothAdapter.AUDIO_MODE_OUTPUT_ONLY, BluetoothProfile.LE_AUDIO);
        preferences.putInt(BluetoothAdapter.AUDIO_MODE_DUPLEX, BluetoothProfile.A2DP);
        assertThrows(IllegalArgumentException.class,
                () -> mAdapter.setPreferredAudioProfiles(device, preferences));

        preferences.putInt(BluetoothAdapter.AUDIO_MODE_DUPLEX, BluetoothProfile.GATT);
        assertThrows(IllegalArgumentException.class,
                () -> mAdapter.setPreferredAudioProfiles(device, preferences));

        preferences.putInt(BluetoothAdapter.AUDIO_MODE_DUPLEX, BluetoothProfile.HEADSET);

        assertThrows(NullPointerException.class, () ->
                mAdapter.setPreferredAudioProfiles(null, preferences));

        // Check what happens when the device is not bonded
        assertTrue(mAdapter.getPreferredAudioProfiles(device).isEmpty());
        assertEquals(BluetoothStatusCodes.ERROR_DEVICE_NOT_BONDED,
                mAdapter.setPreferredAudioProfiles(device, preferences));
    }

    @Test
    public void preferredAudioProfileCallbacks() {
        assumeTrue(mHasBluetooth);

        assertTrue(BTAdapterUtils.enableAdapter(mAdapter, mContext));
        String deviceAddress = "00:11:22:AA:BB:CC";
        BluetoothDevice device = mAdapter.getRemoteDevice(deviceAddress);

        Executor executor = mContext.getMainExecutor();
        BluetoothAdapter.PreferredAudioProfilesChangedCallback callback =
                new BluetoothAdapter.PreferredAudioProfilesChangedCallback() {
            @Override
            public void onPreferredAudioProfilesChanged(
                    @androidx.annotation.NonNull BluetoothDevice device,
                    @androidx.annotation.NonNull Bundle preferredAudioProfiles, int status) {}
        };

        callback.onPreferredAudioProfilesChanged(device, Bundle.EMPTY,
                BluetoothStatusCodes.SUCCESS);

        assertThrows(NullPointerException.class, () ->
                mAdapter.registerPreferredAudioProfilesChangedCallback(null, callback));
        assertThrows(NullPointerException.class, () ->
                mAdapter.registerPreferredAudioProfilesChangedCallback(executor, null));
        assertThrows(NullPointerException.class, () ->
                mAdapter.unregisterPreferredAudioProfilesChangedCallback(null));

        // This should throw a SecurityException because no BLUETOOTH_PRIVILEGED permission
        assertThrows(SecurityException.class, () ->
                mAdapter.registerPreferredAudioProfilesChangedCallback(executor, callback));
        assertThrows(IllegalArgumentException.class, () ->
                mAdapter.unregisterPreferredAudioProfilesChangedCallback(callback));

        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);

        if (isDualModeAudioEnabled()) {
            assertEquals(BluetoothStatusCodes.SUCCESS,
                    mAdapter.registerPreferredAudioProfilesChangedCallback(executor, callback));
            assertEquals(BluetoothStatusCodes.SUCCESS,
                    mAdapter.unregisterPreferredAudioProfilesChangedCallback(callback));
        } else {
            assertEquals(BluetoothStatusCodes.FEATURE_NOT_SUPPORTED,
                    mAdapter.registerPreferredAudioProfilesChangedCallback(executor, callback));
            assertThrows(IllegalArgumentException.class, () ->
                    mAdapter.unregisterPreferredAudioProfilesChangedCallback(callback));
        }
    }

    @Test
    public void bluetoothQualityReportReadyCallbacks() {
        assumeTrue(mHasBluetooth);

        assertTrue(BTAdapterUtils.enableAdapter(mAdapter, mContext));
        String deviceAddress = "00:11:22:AA:BB:CC";
        BluetoothDevice device = mAdapter.getRemoteDevice(deviceAddress);

        Executor executor = mContext.getMainExecutor();
        BluetoothAdapter.BluetoothQualityReportReadyCallback callback =
                new BluetoothAdapter.BluetoothQualityReportReadyCallback() {
            @Override
            public void onBluetoothQualityReportReady(
                    @androidx.annotation.NonNull BluetoothDevice device,
                    @androidx.annotation.NonNull BluetoothQualityReport bluetoothQualityReport,
                    int status) {}
        };

        BluetoothQualityReport bqr =
                BluetoothQualityReportTest.getBqr(BluetoothQualityReport.QUALITY_REPORT_ID_MONITOR);

        callback.onBluetoothQualityReportReady(device, bqr,
                BluetoothStatusCodes.SUCCESS);

        assertThrows(NullPointerException.class, () ->
                mAdapter.registerBluetoothQualityReportReadyCallback(null, callback));
        assertThrows(NullPointerException.class, () ->
                mAdapter.registerBluetoothQualityReportReadyCallback(executor, null));
        assertThrows(NullPointerException.class, () ->
                mAdapter.unregisterBluetoothQualityReportReadyCallback(null));

        // This should throw a SecurityException because no BLUETOOTH_PRIVILEGED permission
        assertThrows(SecurityException.class, () ->
                mAdapter.registerBluetoothQualityReportReadyCallback(executor, callback));
        assertThrows(IllegalArgumentException.class, () ->
                mAdapter.unregisterBluetoothQualityReportReadyCallback(callback));

        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);

        // Try the happy path
        assertEquals(BluetoothStatusCodes.SUCCESS,
                mAdapter.registerBluetoothQualityReportReadyCallback(executor, callback));
        assertEquals(BluetoothStatusCodes.SUCCESS,
                mAdapter.unregisterBluetoothQualityReportReadyCallback(callback));
    }

    @Test
    public void notifyActiveDeviceChangeApplied() {
        assumeTrue(mHasBluetooth);

        assertTrue(BTAdapterUtils.enableAdapter(mAdapter, mContext));
        String deviceAddress = "00:11:22:AA:BB:CC";
        BluetoothDevice device = mAdapter.getRemoteDevice(deviceAddress);

        assertThrows(NullPointerException.class, () ->
                mAdapter.notifyActiveDeviceChangeApplied(null));

        assertEquals(BluetoothStatusCodes.ERROR_BLUETOOTH_NOT_ALLOWED,
                mAdapter.notifyActiveDeviceChangeApplied(device));
    }

    private boolean isDualModeAudioEnabled() {
        return SystemProperties.getBoolean(ENABLE_DUAL_MODE_AUDIO, false);
    }

    private boolean waitForAdapterNameChange() {
        mAdapterNameChangedlock.lock();
        try {
            // Wait for the Adapter name to be changed
            while (!mIsAdapterNameChanged) {
                if (!mConditionAdapterNameChanged.await(
                        SET_NAME_TIMEOUT, TimeUnit.MILLISECONDS)) {
                    Log.e(TAG, "Timeout while waiting for adapter name change");
                    break;
                }
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "waitForAdapterNameChange: interrupted");
        } finally {
            mAdapterNameChangedlock.unlock();
        }
        return mIsAdapterNameChanged;
    }

    private final BroadcastReceiver mAdapterNameChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(BluetoothAdapter.ACTION_LOCAL_NAME_CHANGED)) {
                mAdapterNameChangedlock.lock();
                mIsAdapterNameChanged = true;
                try {
                    mConditionAdapterNameChanged.signal();
                } catch (IllegalMonitorStateException ex) {
                } finally {
                    mAdapterNameChangedlock.unlock();
                }
            }
        }
    };
}
