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

import static android.bluetooth.BluetoothDevice.BATTERY_LEVEL_UNKNOWN;
import static android.bluetooth.BluetoothGatt.GATT_SUCCESS;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.HandlerThread;
import android.os.Looper;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.LargeTest;

import com.android.bluetooth.TestUtils;
import com.android.bluetooth.btservice.AdapterService;

import org.hamcrest.core.IsInstanceOf;
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

@LargeTest
@RunWith(JUnit4.class)
public class BatteryStateMachineTest {
    @Rule
    public final MockitoRule mockito = MockitoJUnit.rule();

    private BluetoothAdapter mAdapter;
    private Context mTargetContext;
    private HandlerThread mHandlerThread;
    private StubBatteryStateMachine mBatteryStateMachine;
    private static final int CONNECTION_TIMEOUT_MS = 1_000;
    private static final int TIMEOUT_MS = 2_000;
    private static final int WAIT_MS = 1_000;

    private BluetoothDevice mTestDevice;
    @Mock private AdapterService mAdapterService;
    @Mock private BatteryService mBatteryService;

    @Before
    public void setUp() throws Exception {
        mTargetContext = InstrumentationRegistry.getTargetContext();
        TestUtils.setAdapterService(mAdapterService);

        mAdapter = BluetoothAdapter.getDefaultAdapter();

        // Get a device for testing
        mTestDevice = mAdapter.getRemoteDevice("00:01:02:03:04:05");

        // Set up thread and looper
        mHandlerThread = new HandlerThread("BatteryStateMachineTestHandlerThread");
        mHandlerThread.start();
        mBatteryStateMachine = new StubBatteryStateMachine(mTestDevice,
                mBatteryService, mHandlerThread.getLooper());
        // Override the timeout value to speed up the test
        mBatteryStateMachine.sConnectTimeoutMs = CONNECTION_TIMEOUT_MS;
        mBatteryStateMachine.start();
    }

    @After
    public void tearDown() throws Exception {
        mBatteryStateMachine.doQuit();
        mHandlerThread.quit();
        TestUtils.clearAdapterService(mAdapterService);
        reset(mBatteryService);
    }

    /**
     * Test that default state is disconnected
     */
    @Test
    public void testDefaultDisconnectedState() {
        Assert.assertEquals(BluetoothProfile.STATE_DISCONNECTED,
                mBatteryStateMachine.getConnectionState());
    }

    /**
     * Allow/disallow connection to any device.
     *
     * @param allow if true, connection is allowed
     */
    private void allowConnection(boolean allow) {
        when(mBatteryService.canConnect(any(BluetoothDevice.class))).thenReturn(allow);
    }

    private void allowConnectGatt(boolean allow) {
        mBatteryStateMachine.mShouldAllowGatt = allow;
    }

    /**
     * Test that an incoming connection with policy forbidding connection is rejected
     */
    @Test
    public void testOkToConnectFails() {
        allowConnection(false);
        allowConnectGatt(true);

        // Inject an event for when incoming connection is requested
        mBatteryStateMachine.sendMessage(BatteryStateMachine.CONNECT);

        verify(mBatteryService, after(WAIT_MS).never())
                .handleConnectionStateChanged(any(BatteryStateMachine.class), anyInt(), anyInt());

        // Check that we are in Disconnected state
        Assert.assertThat(mBatteryStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(BatteryStateMachine.Disconnected.class));
    }

    @Test
    public void testFailToConnectGatt() {
        allowConnection(true);
        allowConnectGatt(false);

        // Inject an event for when incoming connection is requested
        mBatteryStateMachine.sendMessage(BatteryStateMachine.CONNECT);

        verify(mBatteryService, after(WAIT_MS).never())
                .handleConnectionStateChanged(any(BatteryStateMachine.class), anyInt(), anyInt());

        // Check that we are in Disconnected state
        Assert.assertThat(mBatteryStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(BatteryStateMachine.Disconnected.class));
        assertNull(mBatteryStateMachine.mBluetoothGatt);
    }

    @Test
    public void testSuccessfullyConnected() {
        allowConnection(true);
        allowConnectGatt(true);

        // Inject an event for when incoming connection is requested
        mBatteryStateMachine.sendMessage(BatteryStateMachine.CONNECT);

        verify(mBatteryService, timeout(TIMEOUT_MS))
                .handleConnectionStateChanged(any(BatteryStateMachine.class),
                        eq(BluetoothProfile.STATE_DISCONNECTED),
                        eq(BluetoothProfile.STATE_CONNECTING));

        Assert.assertThat(mBatteryStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(BatteryStateMachine.Connecting.class));

        assertNotNull(mBatteryStateMachine.mGattCallback);
        mBatteryStateMachine.notifyConnectionStateChanged(
                GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED);

        verify(mBatteryService, timeout(TIMEOUT_MS))
                .handleConnectionStateChanged(any(BatteryStateMachine.class),
                        eq(BluetoothProfile.STATE_CONNECTING),
                        eq(BluetoothProfile.STATE_CONNECTED));

        Assert.assertThat(mBatteryStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(BatteryStateMachine.Connected.class));
    }

    @Test
    public void testDisconnectBeforeConnected() {
        allowConnection(true);
        allowConnectGatt(true);

        mBatteryStateMachine.sendMessage(BatteryStateMachine.CONNECT);

        verify(mBatteryService, timeout(TIMEOUT_MS))
                .handleConnectionStateChanged(any(BatteryStateMachine.class),
                        eq(BluetoothProfile.STATE_DISCONNECTED),
                        eq(BluetoothProfile.STATE_CONNECTING));

        mBatteryStateMachine.sendMessage(BatteryStateMachine.DISCONNECT);

        verify(mBatteryService, timeout(TIMEOUT_MS))
                .handleConnectionStateChanged(any(BatteryStateMachine.class),
                        eq(BluetoothProfile.STATE_CONNECTING),
                        eq(BluetoothProfile.STATE_DISCONNECTED));
    }

    @Test
    public void testConnectedStateChanges() {
        allowConnection(true);
        allowConnectGatt(true);

        // Connected -> CONNECT
        reconnect();

        mBatteryStateMachine.sendMessage(BatteryStateMachine.CONNECT);

        assertThat(mBatteryStateMachine.getCurrentState())
                .isInstanceOf(BatteryStateMachine.Connected.class);

        // Connected -> DISCONNECT
        reconnect();

        mBatteryStateMachine.sendMessage(BatteryStateMachine.DISCONNECT);

        TestUtils.waitForLooperToFinishScheduledTask(mBatteryStateMachine.getHandler().getLooper());

        mBatteryStateMachine.notifyConnectionStateChanged(
                GATT_SUCCESS, BluetoothProfile.STATE_DISCONNECTED);

        assertThat(mBatteryStateMachine.getCurrentState())
                .isInstanceOf(BatteryStateMachine.Disconnected.class);

        // Connected -> STATE_DISCONNECTED
        reconnect();

        mBatteryStateMachine.sendMessage(
                BatteryStateMachine.CONNECTION_STATE_CHANGED, BluetoothGatt.STATE_DISCONNECTED);

        TestUtils.waitForLooperToFinishScheduledTask(mBatteryStateMachine.getHandler().getLooper());

        mBatteryStateMachine.notifyConnectionStateChanged(
                GATT_SUCCESS, BluetoothProfile.STATE_DISCONNECTED);

        // Connected -> STATE_CONNECTED
        reconnect();

        mBatteryStateMachine.sendMessage(
                BatteryStateMachine.CONNECTION_STATE_CHANGED, BluetoothGatt.STATE_CONNECTED);

        assertThat(mBatteryStateMachine.getCurrentState())
                .isInstanceOf(BatteryStateMachine.Connected.class);

        // Connected -> ILLEGAL_STATE
        reconnect();

        int badState = -1;
        mBatteryStateMachine.sendMessage(
                BatteryStateMachine.CONNECTION_STATE_CHANGED, badState);

        assertThat(mBatteryStateMachine.getCurrentState())
                .isInstanceOf(BatteryStateMachine.Connected.class);

        // Connected -> NOT_HANDLED
        reconnect();

        int notHandled = -1;
        mBatteryStateMachine.sendMessage(notHandled);

        assertThat(mBatteryStateMachine.getCurrentState())
                .isInstanceOf(BatteryStateMachine.Connected.class);
    }

    @Test
    public void testBatteryLevelChanged() {
        allowConnection(true);
        allowConnectGatt(true);

        mBatteryStateMachine.updateBatteryLevel(new byte[] {(byte) 0x30});

        verify(mBatteryService, timeout(TIMEOUT_MS))
                .handleBatteryChanged(any(BluetoothDevice.class), eq(0x30));
    }

    @Test
    public void testEmptyBatteryLevelIgnored() {
        allowConnection(true);
        allowConnectGatt(true);

        try {
            mBatteryStateMachine.updateBatteryLevel(new byte[0]);
        } catch (Exception ex) {
            fail();
        }

        verify(mBatteryService, after(WAIT_MS).never())
                .handleBatteryChanged(any(BluetoothDevice.class), anyInt());
    }

    @Test
    public void testDisconnectResetBatteryLevel() {
        allowConnection(true);
        allowConnectGatt(true);

        // Inject an event for when incoming connection is requested
        mBatteryStateMachine.sendMessage(BatteryStateMachine.CONNECT);

        verify(mBatteryService, timeout(TIMEOUT_MS))
                .handleConnectionStateChanged(
                        any(BatteryStateMachine.class),
                        eq(BluetoothProfile.STATE_DISCONNECTED),
                        eq(BluetoothProfile.STATE_CONNECTING));

        Assert.assertThat(
                mBatteryStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(BatteryStateMachine.Connecting.class));

        mBatteryStateMachine.notifyConnectionStateChanged(
                GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED);

        verify(mBatteryService, timeout(TIMEOUT_MS))
                .handleConnectionStateChanged(
                        any(BatteryStateMachine.class),
                        eq(BluetoothProfile.STATE_CONNECTING),
                        eq(BluetoothProfile.STATE_CONNECTED));

        Assert.assertThat(
                mBatteryStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(BatteryStateMachine.Connected.class));
        // update the battery level
        mBatteryStateMachine.updateBatteryLevel(new byte[] {(byte) 0x30});
        verify(mBatteryService, timeout(TIMEOUT_MS))
                .handleBatteryChanged(any(BluetoothDevice.class), eq(0x30));

        // Disconnect should reset the battery level
        mBatteryStateMachine.sendMessage(BatteryStateMachine.DISCONNECT);
        verify(mBatteryService, timeout(TIMEOUT_MS))
                .handleBatteryChanged(any(BluetoothDevice.class), eq(BATTERY_LEVEL_UNKNOWN));
    }

    private void reconnect() {
        // Inject an event for when incoming connection is requested
        mBatteryStateMachine.sendMessage(BatteryStateMachine.CONNECT);

        TestUtils.waitForLooperToFinishScheduledTask(mBatteryStateMachine.getHandler().getLooper());

        mBatteryStateMachine.notifyConnectionStateChanged(
                GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED);

        TestUtils.waitForLooperToFinishScheduledTask(mBatteryStateMachine.getHandler().getLooper());
    }

    // It simulates GATT connection for testing.
    public class StubBatteryStateMachine extends BatteryStateMachine {
        boolean mShouldAllowGatt = true;

        StubBatteryStateMachine(BluetoothDevice device, BatteryService service, Looper looper) {
            super(device, service, looper);
        }

        @Override
        public boolean connectGatt() {
            mGattCallback = new GattCallback();
            return mShouldAllowGatt;
        }

        public void notifyConnectionStateChanged(int status, int newState) {
            if (mGattCallback != null) {
                mGattCallback.onConnectionStateChange(mBluetoothGatt, status, newState);
            }
        }
    }
}

