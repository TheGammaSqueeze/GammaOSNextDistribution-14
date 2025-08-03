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

package com.android.car.bluetooth;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.TruthJUnit.assume;

import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothStatusCodes;
import android.bluetooth.BluetoothUuid;
import android.content.Intent;
import android.os.ParcelUuid;
import android.os.RemoteException;
import android.os.SystemClock;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.RequiresDevice;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.Arrays;
import java.util.List;

/**
 * Unit tests for {@link BluetoothConnectionRetryManager}
 *
 * Run:
 * atest BluetoothConnectionRetryManagerTest
 */
@RequiresDevice
public class BluetoothConnectionRetryManagerTest
        extends AbstractExtendedMockitoBluetoothTestCase {
    private static final String TAG = BluetoothConnectionRetryManagerTest.class.getSimpleName();
    private static final boolean VERBOSE = false;

    private static final List<String> DEVICE_LIST = Arrays.asList(
            "DE:AD:BE:EF:00:00",
            "DE:AD:BE:EF:00:01",
            "DE:AD:BE:EF:00:02",
            "DE:AD:BE:EF:00:03",
            "DE:AD:BE:EF:00:04",
            "DE:AD:BE:EF:00:05",
            "DE:AD:BE:EF:00:06",
            "DE:AD:BE:EF:00:07");

    private static final int TIMING_BUFFER_MS = 100;

    BluetoothConnectionRetryManager mConnectionRetryManager;

    // Tests assume HFP is the only supported profile
    private final int mProfileId = BluetoothProfile.HEADSET_CLIENT;
    private final String mConnectionAction = BluetoothUtils.HFP_CLIENT_CONNECTION_STATE_CHANGED;

    private MockContext mMockContext;
    @Mock private BluetoothManager mMockBluetoothManager;
    @Mock private BluetoothAdapter mMockBluetoothAdapter;

    //--------------------------------------------------------------------------------------------//
    // Setup/TearDown                                                                             //
    //--------------------------------------------------------------------------------------------//

    @Before
    public void setUp() {
        mMockContext = new MockContext(InstrumentationRegistry.getTargetContext());
        mMockContext.addMockedSystemService(BluetoothManager.class, mMockBluetoothManager);
        when(mMockBluetoothManager.getAdapter()).thenReturn(mMockBluetoothAdapter);
        when(mMockBluetoothAdapter.getUuidsList()).thenReturn(
                Arrays.asList(new ParcelUuid[]{BluetoothUuid.HFP}));

        mMockContext.addMockedSystemService(BluetoothManager.class, mMockBluetoothManager);
        when(mMockBluetoothManager.getAdapter()).thenReturn(mMockBluetoothAdapter);
        when(mMockBluetoothAdapter.getUuidsList()).thenReturn(
                Arrays.asList(new ParcelUuid[]{BluetoothUuid.HFP}));

        mConnectionRetryManager = BluetoothConnectionRetryManager.create(mMockContext);
        assertThat(mConnectionRetryManager).isNotNull();
        // Override the timeout value to speed up tests
        mConnectionRetryManager.sRetryFirstConnectTimeoutMs = 1000;
    }

    @After
    public void tearDown() {
        if (mConnectionRetryManager != null) {
            mConnectionRetryManager.release();
            mConnectionRetryManager = null;
        }
        if (mMockContext != null) {
            mMockContext.release();
            mMockContext = null;
        }
    }

    //--------------------------------------------------------------------------------------------//
    // Utilities                                                                                  //
    //--------------------------------------------------------------------------------------------//

    private void sendBondStateChanged(BluetoothDevice device, int newState) {
        assertThat(mMockContext).isNotNull();
        Intent intent = new Intent(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
        intent.putExtra(BluetoothDevice.EXTRA_BOND_STATE, newState);
        mMockContext.sendBroadcast(intent);
    }

    private void sendConnectionStateChanged(BluetoothDevice device, int newState) {
        assertThat(mMockContext).isNotNull();
        Intent intent = new Intent(mConnectionAction);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
        intent.putExtra(BluetoothProfile.EXTRA_STATE, newState);
        mMockContext.sendBroadcast(intent);
    }

    private void sendSuccessfulConnection(BluetoothDevice device) {
        assertThat(mMockContext).isNotNull();
        Intent intent = new Intent(mConnectionAction);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
        intent.putExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, BluetoothProfile.STATE_CONNECTING);
        intent.putExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_CONNECTED);
        mMockContext.sendBroadcast(intent);
    }

    private void sendFailedConnectionMethod1(BluetoothDevice device) {
        assertThat(mMockContext).isNotNull();
        Intent intent = new Intent(mConnectionAction);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
        intent.putExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, BluetoothProfile.STATE_CONNECTING);
        intent.putExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTING);
        mMockContext.sendBroadcast(intent);
    }

    private void sendFailedConnectionMethod2(BluetoothDevice device) {
        assertThat(mMockContext).isNotNull();
        Intent intent = new Intent(mConnectionAction);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
        intent.putExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, BluetoothProfile.STATE_CONNECTING);
        intent.putExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED);
        mMockContext.sendBroadcast(intent);
    }

    private void sendFailedConnectionsPeriodic(BluetoothDevice device, int count, long interval) {
        for (int i = 0; i < count; i++) {
            sendFailedConnectionMethod1(device);
            SystemClock.sleep(interval);
        }
    }

    private BluetoothDevice createMockDevice(String bdAddr) {
        // Tests assume HFP is the only supported profile
        return createMockDevice(bdAddr,
                new ParcelUuid[]{BluetoothUuid.HFP_AG, BluetoothUuid.HSP_AG});
    }

    private BluetoothDevice createMockDevice(String bdAddr, ParcelUuid[] uuids) {
        BluetoothDevice device = mock(BluetoothDevice.class);
        when(device.getAddress()).thenReturn(bdAddr);
        when(device.getName()).thenReturn(bdAddr);
        when(device.getUuids()).thenReturn(uuids);
        when(device.connect()).thenReturn(BluetoothStatusCodes.SUCCESS);
        when(device.disconnect()).thenReturn(BluetoothStatusCodes.SUCCESS);
        return device;
    }

    //--------------------------------------------------------------------------------------------//
    // First-connection after bonding tests                                                       //
    //--------------------------------------------------------------------------------------------//

    /**
     * Preconditions:
     * - The connection retry manager is initialized.
     *
     * Actions:
     * - A device bonds.
     * - The device successfully connects.
     *
     * Outcome:
     * - Nothing, no retry attempts should be made.
     */
    @Test
    public void testSuccessfulFirstConnect_doNothing() {
        mConnectionRetryManager.init();
        BluetoothDevice device = createMockDevice(DEVICE_LIST.get(0));

        sendBondStateChanged(device, BluetoothDevice.BOND_BONDED);
        sendSuccessfulConnection(device);

        assertThat(mConnectionRetryManager.isRetryPosted(device, mProfileId)).isFalse();
    }

    /**
     * Preconditions:
     * - The connection retry manager is initialized.
     *
     * Actions:
     * - A device bonds.
     * - The device fails to connect (CONNECTING --> DISCONNECTING)
     *
     * Outcome:
     * - A retry attempt should be posted.
     */
    @Test
    public void testFailedFirstConnectMethod1_retryPosted() {
        mConnectionRetryManager.init();
        BluetoothDevice device = createMockDevice(DEVICE_LIST.get(0));

        sendBondStateChanged(device, BluetoothDevice.BOND_BONDED);
        sendFailedConnectionMethod1(device);

        assertThat(mConnectionRetryManager.isRetryPosted(device, mProfileId)).isTrue();
    }

    /**
     * Preconditions:
     * - The connection retry manager is initialized.
     *
     * Actions:
     * - A device bonds.
     * - The device fails to connect (CONNECTING --> DISCONNECTED)
     *
     * Outcome:
     * - A retry attempt should be posted.
     */
    @Test
    public void testFailedFirstConnectMethod2_retryPosted() {
        mConnectionRetryManager.init();
        BluetoothDevice device = createMockDevice(DEVICE_LIST.get(0));

        sendBondStateChanged(device, BluetoothDevice.BOND_BONDED);
        sendFailedConnectionMethod2(device);

        assertThat(mConnectionRetryManager.isRetryPosted(device, mProfileId)).isTrue();
    }

    /**
     * Preconditions:
     * - The connection retry manager is initialized.
     *
     * Actions:
     * - A device bonds.
     * - Device fails to connect for the first time.
     *
     * Outcome:
     * - Verify the actual connect command is invoked for the retry, not just retry posted.
     */
    @Test
    public void testFailedFirstConnect_retryConnectInvoked() throws RemoteException {
        mConnectionRetryManager.init();
        BluetoothDevice device = createMockDevice(DEVICE_LIST.get(0));

        sendBondStateChanged(device, BluetoothDevice.BOND_BONDED);
        sendFailedConnectionMethod1(device);

        // should not be invoked immediately
        verify(device, times(0)).connect();
        verify(device, after(mConnectionRetryManager.sRetryFirstConnectTimeoutMs
                + TIMING_BUFFER_MS).times(1)).connect();
    }

    /**
     * Preconditions:
     * - The connection retry manager is initialized.
     *
     * Actions:
     * - Two devices bond.
     * - One device fails to connect.
     * - The other device does nothing.
     *
     * Outcome:
     * - Only the device that failed to connect should have a retry attempt.
     */
    @Test
    public void testFirstConnectTwoDevices_onlyOneDeviceRetries() {
        mConnectionRetryManager.init();
        BluetoothDevice deviceDoesNothing = createMockDevice(DEVICE_LIST.get(0));
        BluetoothDevice deviceFailsConnection = createMockDevice(DEVICE_LIST.get(1));

        sendBondStateChanged(deviceDoesNothing, BluetoothDevice.BOND_BONDED);
        sendBondStateChanged(deviceFailsConnection, BluetoothDevice.BOND_BONDED);
        sendFailedConnectionMethod1(deviceFailsConnection);

        assertThat(mConnectionRetryManager.isRetryPosted(deviceDoesNothing, mProfileId)).isFalse();
        assertThat(mConnectionRetryManager.isRetryPosted(deviceFailsConnection, mProfileId))
                .isTrue();
    }

    /**
     * Preconditions:
     * - The connection retry manager is initialized.
     *
     * Actions:
     * - A device never bonds.
     * - Send an unsuccessful connection attempt.
     *
     * Outcome:
     * - Nothing, no retry attempts should be made, no exceptions thrown.
     */
    @Test
    public void testFirstConnectDeviceNeverBonded_doNothing() {
        mConnectionRetryManager.init();
        BluetoothDevice device = createMockDevice(DEVICE_LIST.get(0));

        sendFailedConnectionMethod1(device);

        assertThat(mConnectionRetryManager.isRetryPosted(device, mProfileId)).isFalse();
    }

    /**
     * Preconditions:
     * - The connection retry manager is initialized.
     *
     * Actions:
     * - A device bonds.
     * - Then, device unbonds.
     * - Then, send an unsuccessful connection attempt.
     *
     * Outcome:
     * - Nothing, no retry attempts should be made, no exceptions thrown.
     */
    @Test
    public void testDeviceUnbondsBeforeFirstConnect_doNothing() {
        mConnectionRetryManager.init();
        BluetoothDevice device = createMockDevice(DEVICE_LIST.get(0));

        sendBondStateChanged(device, BluetoothDevice.BOND_BONDED);
        sendBondStateChanged(device, BluetoothDevice.BOND_NONE);
        sendFailedConnectionMethod1(device);

        assertThat(mConnectionRetryManager.isRetryPosted(device, mProfileId)).isFalse();
    }

    /**
     * Preconditions:
     * - The connection retry manager is initialized.
     *
     * Actions:
     * - A device bonds.
     * - Then, an unsuccessful connection attempt.
     * - Then, device unbonds (before a successful connection).
     *
     * Outcome:
     * - Retry attempts should be removed when the device unbonds.
     */
    @Test
    public void testDeviceUnbondsAfterRetryPosted_retriesRemoved() {
        mConnectionRetryManager.init();
        BluetoothDevice device = createMockDevice(DEVICE_LIST.get(0));

        sendBondStateChanged(device, BluetoothDevice.BOND_BONDED);
        sendFailedConnectionMethod1(device);
        assertThat(mConnectionRetryManager.isRetryPosted(device, mProfileId)).isTrue();
        sendBondStateChanged(device, BluetoothDevice.BOND_NONE);
        assertThat(mConnectionRetryManager.isRetryPosted(device, mProfileId)).isFalse();
    }

    /**
     * Preconditions:
     * - The connection retry manager is initialized.
     *
     * Actions:
     * - A device bonds.
     * - Device successfully connects for the first time.
     * - Device disconnects (but remains bonded).
     * - Device fails to connect a second time.
     *
     * Outcome:
     * - Nothing, no retry attempts should be made, no exceptions thrown.
     * - Only retry failed _first_ attempts, not subsequent ones.
     */
    @Test
    public void testSuccessfulFirstConnectFailedSecond_doNothing() {
        mConnectionRetryManager.init();
        BluetoothDevice device = createMockDevice(DEVICE_LIST.get(0));

        sendBondStateChanged(device, BluetoothDevice.BOND_BONDED);
        sendSuccessfulConnection(device);
        sendConnectionStateChanged(device, BluetoothProfile.STATE_DISCONNECTED);
        sendFailedConnectionMethod1(device);

        assertThat(mConnectionRetryManager.isRetryPosted(device, mProfileId)).isFalse();
    }

    /**
     * Preconditions:
     * - The connection retry manager is initialized.
     *
     * Actions:
     * - A device bonds.
     * - Device fails first-connect twice.
     *
     * Outcome:
     * - Two retry attempts should be made.
     */
    @Test
    public void testFailedFirstConnectTwice_retryTwice() throws RemoteException {
        mConnectionRetryManager.init();
        assume().that(mConnectionRetryManager.getMaxRetriesFirstConnection()).isGreaterThan(1);
        BluetoothDevice device = createMockDevice(DEVICE_LIST.get(0));

        sendBondStateChanged(device, BluetoothDevice.BOND_BONDED);
        sendFailedConnectionsPeriodic(device, 2, mConnectionRetryManager
                .sRetryFirstConnectTimeoutMs + TIMING_BUFFER_MS);

        verify(device, times(2)).connect();
    }

    /**
     * Preconditions:
     * - The connection retry manager is initialized.
     *
     * Actions:
     * - A device bonds.
     * - Device fails first-connect greater than {@code N} times, where {@code N} is the maximum
     *   retry attempts.
     *
     * Outcome:
     * - Only {@code N} retry attempts should be made.
     */
    @Test
    public void testFailedFirstConnectMoreThanMaxTimes_retryMaxTimes() throws RemoteException {
        mConnectionRetryManager.init();
        BluetoothDevice device = createMockDevice(DEVICE_LIST.get(0));

        int expectedRetryAttempts = mConnectionRetryManager.getMaxRetriesFirstConnection();
        int failedAttempts = expectedRetryAttempts + 2;

        sendBondStateChanged(device, BluetoothDevice.BOND_BONDED);
        sendFailedConnectionsPeriodic(device, failedAttempts, mConnectionRetryManager
                .sRetryFirstConnectTimeoutMs + TIMING_BUFFER_MS);

        verify(device, times(expectedRetryAttempts)).connect();
    }

    /**
     * Preconditions:
     * - The connection retry manager is initialized.
     *
     * Actions:
     * - A device bonds.
     * - Device fails first-connect in quick succession: 3 failed attempts spaced {@code T/2} ms
     *   apart, where {@code T} is
     *   {@link BluetoothConnectionRetryManager.sRetryFirstConnectTimeoutMs}.
     *
     * Outcome:
     * - Only the first and third failures should trigger retry-connect attempts.
     */
    @Test
    public void testFailedFirstConnectThreeInQuickSuccession_retryTwice() throws RemoteException {
        mConnectionRetryManager.init();
        assume().that(mConnectionRetryManager.getMaxRetriesFirstConnection()).isGreaterThan(1);
        BluetoothDevice device = createMockDevice(DEVICE_LIST.get(0));

        long halfInterval = mConnectionRetryManager.sRetryFirstConnectTimeoutMs / 2;

        sendBondStateChanged(device, BluetoothDevice.BOND_BONDED);
        sendFailedConnectionMethod1(device); // failure 1
        SystemClock.sleep(halfInterval);
        sendFailedConnectionMethod1(device); // failure 2
        SystemClock.sleep(halfInterval + TIMING_BUFFER_MS); // full interval since failure 1
        verify(device, times(1)).connect(); // for #1
        sendFailedConnectionMethod1(device); // failure 3
        SystemClock.sleep(halfInterval); // full interval since failure 2
        verify(device, times(1)).connect(); // for #2
        SystemClock.sleep(halfInterval + TIMING_BUFFER_MS); // full interval since failure 3
        verify(device, times(2)).connect(); // for #3
    }
}
