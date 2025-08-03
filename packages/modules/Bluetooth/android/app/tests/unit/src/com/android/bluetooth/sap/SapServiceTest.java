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

package com.android.bluetooth.sap;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;

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
public class SapServiceTest {
    private static final int TIMEOUT_MS = 5_000;

    private SapService mService = null;
    private BluetoothAdapter mAdapter = null;
    private Context mTargetContext;

    @Mock private AdapterService mAdapterService;
    @Mock private DatabaseManager mDatabaseManager;
    private BluetoothDevice mDevice;

    @Before
    public void setUp() throws Exception {
        mTargetContext = InstrumentationRegistry.getTargetContext();
        MockitoAnnotations.initMocks(this);
        TestUtils.setAdapterService(mAdapterService);
        mService = new SapService(mTargetContext);
        mService.start();
        mService.setAvailable(true);
        // Try getting the Bluetooth adapter
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        assertThat(mAdapter).isNotNull();
        mDevice = TestUtils.getTestDevice(mAdapter, 0);
    }

    @After
    public void tearDown() throws Exception {
        mService.stop();
        mService = SapService.getSapService();
        assertThat(mService).isNull();
        TestUtils.clearAdapterService(mAdapterService);
    }

    @Test
    public void testGetSapService() {
        assertThat(mService).isEqualTo(SapService.getSapService());
        assertThat(mService.getConnectedDevices()).isEmpty();
    }

    /**
     * Test stop SAP Service
     */
    @Test
    public void testStopSapService() throws Exception {
        // SAP Service is already running: test stop(). Note: must be done on the main thread
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(
                        () -> {
                            mService.stop();
                            mService.start();
                        });
    }

    /** Test get connection policy for BluetoothDevice */
    @Test
    public void testGetConnectionPolicy() {
        when(mAdapterService.getDatabase()).thenReturn(mDatabaseManager);
        when(mDatabaseManager.getProfileConnectionPolicy(mDevice, BluetoothProfile.SAP))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_UNKNOWN);
        assertThat(mService.getConnectionPolicy(mDevice))
                .isEqualTo(BluetoothProfile.CONNECTION_POLICY_UNKNOWN);

        when(mDatabaseManager
                .getProfileConnectionPolicy(mDevice, BluetoothProfile.SAP))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_FORBIDDEN);
        assertThat(mService.getConnectionPolicy(mDevice))
                .isEqualTo(BluetoothProfile.CONNECTION_POLICY_FORBIDDEN);

        when(mDatabaseManager
                .getProfileConnectionPolicy(mDevice, BluetoothProfile.SAP))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_ALLOWED);

        assertThat(mService.getConnectionPolicy(mDevice))
                .isEqualTo(BluetoothProfile.CONNECTION_POLICY_ALLOWED);
    }

    @Test
    public void testGetRemoteDevice() {
        assertThat(mService.getRemoteDevice()).isNull();
    }

    @Test
    public void testGetRemoteDeviceName() {
        assertThat(SapService.getRemoteDeviceName()).isNull();
    }

    @Test
    public void testReceiver_ConnectionAccessReplyIntent_shouldNotCrash() {
        Intent intent = new Intent(BluetoothDevice.ACTION_CONNECTION_ACCESS_REPLY);
        intent.putExtra(
                BluetoothDevice.EXTRA_ACCESS_REQUEST_TYPE, BluetoothDevice.REQUEST_TYPE_SIM_ACCESS);
        mService.mSapReceiver.onReceive(mTargetContext, intent);
    }
}
