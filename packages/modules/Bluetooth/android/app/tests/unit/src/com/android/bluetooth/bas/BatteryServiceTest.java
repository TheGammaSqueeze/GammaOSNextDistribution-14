/*
 * Copyright 2022 The Android Open Source Project
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

package com.android.bluetooth.bas;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothUuid;
import android.content.Context;
import android.os.Looper;
import android.os.ParcelUuid;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.LargeTest;

import com.android.bluetooth.TestUtils;
import com.android.bluetooth.btservice.AdapterService;
import com.android.bluetooth.btservice.storage.DatabaseManager;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.concurrent.TimeoutException;

@LargeTest
@RunWith(JUnit4.class)
public class BatteryServiceTest {
    private BluetoothAdapter mAdapter;
    private Context mTargetContext;
    private BatteryService mService;
    private BluetoothDevice mDevice;
    private static final int CONNECTION_TIMEOUT_MS = 1000;

    @Mock private AdapterService mAdapterService;
    @Mock private DatabaseManager mDatabaseManager;

    @Rule
    public final MockitoRule mockito = MockitoJUnit.rule();

    @Before
    public void setUp() throws Exception {
        mTargetContext = InstrumentationRegistry.getTargetContext();
        if (Looper.myLooper() == null) {
            Looper.prepare();
        }

        TestUtils.setAdapterService(mAdapterService);
        doReturn(mDatabaseManager).when(mAdapterService).getDatabase();

        mAdapter = BluetoothAdapter.getDefaultAdapter();

        startService();

        // Override the timeout value to speed up the test
        BatteryStateMachine.sConnectTimeoutMs = CONNECTION_TIMEOUT_MS;    // 1s

        // Get a device for testing
        mDevice = TestUtils.getTestDevice(mAdapter, 0);
        doReturn(BluetoothDevice.BOND_BONDED).when(mAdapterService)
                .getBondState(any(BluetoothDevice.class));
    }

    @After
    public void tearDown() throws Exception {
        stopService();
        TestUtils.clearAdapterService(mAdapterService);
    }

    private void startService() throws TimeoutException {
        mService = new BatteryService(mTargetContext);
        mService.start();
        mService.setAvailable(true);
    }

    private void stopService() throws TimeoutException {
        mService.stop();
        mService = BatteryService.getBatteryService();
        Assert.assertNull(mService);
    }

    /**
     * Test get Battery Service
     */
    @Test
    public void testGetBatteryService() {
        Assert.assertEquals(mService, BatteryService.getBatteryService());
    }

    /**
     * Test get/set policy for BluetoothDevice
     */
    @Test
    public void testGetSetPolicy() {
        when(mDatabaseManager
                .getProfileConnectionPolicy(mDevice, BluetoothProfile.BATTERY))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_UNKNOWN);
        Assert.assertEquals("Initial device policy",
                BluetoothProfile.CONNECTION_POLICY_UNKNOWN,
                mService.getConnectionPolicy(mDevice));

        when(mDatabaseManager
                .getProfileConnectionPolicy(mDevice, BluetoothProfile.BATTERY))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_FORBIDDEN);
        Assert.assertEquals("Setting device policy to POLICY_FORBIDDEN",
                BluetoothProfile.CONNECTION_POLICY_FORBIDDEN,
                mService.getConnectionPolicy(mDevice));

        when(mDatabaseManager
                .getProfileConnectionPolicy(mDevice, BluetoothProfile.BATTERY))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_ALLOWED);
        Assert.assertEquals("Setting device policy to POLICY_ALLOWED",
                BluetoothProfile.CONNECTION_POLICY_ALLOWED,
                mService.getConnectionPolicy(mDevice));
    }

    /**
     * Test if getProfileConnectionPolicy works after the service is stopped.
     */
    @Test
    public void testGetPolicyAfterStopped() {
        mService.stop();
        when(mDatabaseManager
                .getProfileConnectionPolicy(mDevice, BluetoothProfile.BATTERY))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_UNKNOWN);
        Assert.assertEquals("Initial device policy",
                BluetoothProfile.CONNECTION_POLICY_UNKNOWN,
                mService.getConnectionPolicy(mDevice));
    }

    /**
     *  Test okToConnect method using various test cases
     */
    @Test
    public void testCanConnect() {
        int badPolicyValue = 1024;
        int badBondState = 42;
        testCanConnectCase(mDevice,
                BluetoothDevice.BOND_NONE, BluetoothProfile.CONNECTION_POLICY_UNKNOWN, false);
        testCanConnectCase(mDevice,
                BluetoothDevice.BOND_NONE, BluetoothProfile.CONNECTION_POLICY_FORBIDDEN, false);
        testCanConnectCase(mDevice,
                BluetoothDevice.BOND_NONE, BluetoothProfile.CONNECTION_POLICY_ALLOWED, false);
        testCanConnectCase(mDevice,
                BluetoothDevice.BOND_NONE, badPolicyValue, false);
        testCanConnectCase(mDevice,
                BluetoothDevice.BOND_BONDING, BluetoothProfile.CONNECTION_POLICY_UNKNOWN, false);
        testCanConnectCase(mDevice,
                BluetoothDevice.BOND_BONDING, BluetoothProfile.CONNECTION_POLICY_FORBIDDEN, false);
        testCanConnectCase(mDevice,
                BluetoothDevice.BOND_BONDING, BluetoothProfile.CONNECTION_POLICY_ALLOWED, false);
        testCanConnectCase(mDevice,
                BluetoothDevice.BOND_BONDING, badPolicyValue, false);
        testCanConnectCase(mDevice,
                BluetoothDevice.BOND_BONDED, BluetoothProfile.CONNECTION_POLICY_UNKNOWN, true);
        testCanConnectCase(mDevice,
                BluetoothDevice.BOND_BONDED, BluetoothProfile.CONNECTION_POLICY_FORBIDDEN, false);
        testCanConnectCase(mDevice,
                BluetoothDevice.BOND_BONDED, BluetoothProfile.CONNECTION_POLICY_ALLOWED, true);
        testCanConnectCase(mDevice,
                BluetoothDevice.BOND_BONDED, badPolicyValue, false);
        testCanConnectCase(mDevice,
                badBondState, BluetoothProfile.CONNECTION_POLICY_UNKNOWN, false);
        testCanConnectCase(mDevice,
                badBondState, BluetoothProfile.CONNECTION_POLICY_FORBIDDEN, false);
        testCanConnectCase(mDevice,
                badBondState, BluetoothProfile.CONNECTION_POLICY_ALLOWED, false);
        testCanConnectCase(mDevice,
                badBondState, badPolicyValue, false);
    }

    /**
     * Test that an outgoing connection to device
     */
    @Test
    public void testConnectAndDump() {
        // Update the device policy so okToConnect() returns true
        when(mAdapterService.getDatabase()).thenReturn(mDatabaseManager);
        when(mDatabaseManager
                .getProfileConnectionPolicy(mDevice, BluetoothProfile.BATTERY))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_ALLOWED);
        // Return Battery UUID
        doReturn(new ParcelUuid[]{BluetoothUuid.BATTERY}).when(mAdapterService)
                .getRemoteUuids(any(BluetoothDevice.class));
        // Send a connect request
        Assert.assertTrue("Connect expected to succeed", mService.connect(mDevice));

        // Test dump() is not crashed.
        mService.dump(new StringBuilder());
    }

    /**
     * Test that an outgoing connection to device with POLICY_FORBIDDEN is rejected
     */
    @Test
    public void testForbiddenPolicy_FailsToConnect() {
        // Set the device policy to POLICY_FORBIDDEN so connect() should fail
        when(mAdapterService.getDatabase()).thenReturn(mDatabaseManager);
        when(mDatabaseManager
                .getProfileConnectionPolicy(mDevice, BluetoothProfile.BATTERY))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_FORBIDDEN);

        // Send a connect request
        Assert.assertFalse("Connect expected to fail", mService.connect(mDevice));
    }

    @Test
    public void getConnectionState_whenNoDevicesAreConnected_returnsDisconnectedState() {
        Assert.assertEquals(mService.getConnectionState(mDevice),
                BluetoothProfile.STATE_DISCONNECTED);
    }

    @Test
    public void getDevices_whenNoDevicesAreConnected_returnsEmptyList() {
        Assert.assertTrue(mService.getDevices().isEmpty());
    }

    @Test
    public void getDevicesMatchingConnectionStates() {
        when(mAdapterService.getBondedDevices()).thenReturn(new BluetoothDevice[] {mDevice});
        int states[] = new int[] {BluetoothProfile.STATE_DISCONNECTED};

        Assert.assertTrue(mService.getDevicesMatchingConnectionStates(states).contains(mDevice));
    }

    @Test
    public void setConnectionPolicy() {
        Assert.assertTrue(mService.setConnectionPolicy(
                mDevice, BluetoothProfile.CONNECTION_POLICY_FORBIDDEN));
    }

    /**
     *  Helper function to test okToConnect() method
     *
     *  @param device test device
     *  @param bondState bond state value, could be invalid
     *  @param policy value, could be invalid
     *  @param expected expected result from okToConnect()
     */
    private void testCanConnectCase(BluetoothDevice device, int bondState, int policy,
            boolean expected) {
        doReturn(bondState).when(mAdapterService).getBondState(device);
        when(mAdapterService.getDatabase()).thenReturn(mDatabaseManager);
        when(mDatabaseManager.getProfileConnectionPolicy(device, BluetoothProfile.BATTERY))
                .thenReturn(policy);
        Assert.assertEquals(expected, mService.canConnect(device));
    }
}
