/*
 * Copyright 2018 The Android Open Source Project
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
package com.android.bluetooth.map;

import static com.android.bluetooth.map.BluetoothMapService.MSG_MAS_CONNECT_CANCEL;
import static com.android.bluetooth.map.BluetoothMapService.UPDATE_MAS_INSTANCES;
import static com.android.bluetooth.map.BluetoothMapService.USER_TIMEOUT;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.TestUtils;
import com.android.bluetooth.btservice.AdapterService;
import com.android.bluetooth.btservice.storage.DatabaseManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class BluetoothMapServiceTest {
    private static final String REMOTE_DEVICE_ADDRESS = "00:00:00:00:00:00";

    private BluetoothMapService mService = null;
    private BluetoothAdapter mAdapter = null;
    private BluetoothDevice mRemoteDevice;


    @Mock private AdapterService mAdapterService;
    @Mock private DatabaseManager mDatabaseManager;

    @Before
    public void setUp() throws Exception {
        Context targetContext = InstrumentationRegistry.getTargetContext();
        MockitoAnnotations.initMocks(this);
        TestUtils.setAdapterService(mAdapterService);
        doReturn(mDatabaseManager).when(mAdapterService).getDatabase();
        mService = new BluetoothMapService(targetContext);
        mService.start();
        mService.setAvailable(true);
        // Try getting the Bluetooth adapter
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        assertThat(mAdapter).isNotNull();
        mRemoteDevice = mAdapter.getRemoteDevice(REMOTE_DEVICE_ADDRESS);
    }

    @After
    public void tearDown() throws Exception {
        mService.stop();
        mService = BluetoothMapService.getBluetoothMapService();
        assertThat(mService).isNull();
        TestUtils.clearAdapterService(mAdapterService);
    }

    @Test
    public void initialize() {
        assertThat(BluetoothMapService.getBluetoothMapService()).isNotNull();
    }

    @Test
    public void getDevicesMatchingConnectionStates_whenNoDeviceIsConnected_returnsEmptyList() {
        when(mAdapterService.getBondedDevices()).thenReturn(new BluetoothDevice[] {mRemoteDevice});

        assertThat(mService.getDevicesMatchingConnectionStates(
                new int[] {BluetoothProfile.STATE_CONNECTED})).isEmpty();
    }

    @Test
    public void getNextMasId_isInRange() {
        int masId = mService.getNextMasId();
        assertThat(masId).isAtMost(0xff);
        assertThat(masId).isAtLeast(1);
    }

    @Test
    public void sendConnectCancelMessage() {
        TestableHandler handler = spy(new TestableHandler(Looper.getMainLooper()));
        mService.mSessionStatusHandler = handler;

        mService.sendConnectCancelMessage();

        verify(handler, timeout(1_000)).messageArrived(
                eq(MSG_MAS_CONNECT_CANCEL), anyInt(), anyInt(), any());
    }

    @Test
    public void sendConnectTimeoutMessage() {
        TestableHandler handler = spy(new TestableHandler(Looper.getMainLooper()));
        mService.mSessionStatusHandler = handler;

        mService.sendConnectTimeoutMessage();

        verify(handler, timeout(1_000)).messageArrived(
                eq(USER_TIMEOUT), anyInt(), anyInt(), any());
    }

    @Test
    public void updateMasInstances() {
        int action = 5;
        TestableHandler handler = spy(new TestableHandler(Looper.getMainLooper()));
        mService.mSessionStatusHandler = handler;

        mService.updateMasInstances(action);

        verify(handler, timeout(1_000)).messageArrived(
                eq(UPDATE_MAS_INSTANCES), eq(action), anyInt(), any());
    }

    public static class TestableHandler extends Handler {
        public TestableHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            messageArrived(msg.what, msg.arg1, msg.arg2, msg.obj);
        }

        public void messageArrived(int what, int arg1, int arg2, Object obj) {}
    }

    @Test
    public void testDumpDoesNotCrash() {
        mService.dump(new StringBuilder());
    }
}
