/*
 * Copyright 2017 The Android Open Source Project
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

package com.android.bluetooth.hfpclient;

import static android.content.pm.PackageManager.FEATURE_WATCH;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSinkAudioPolicy;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.BatteryManager;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.TestUtils;
import com.android.bluetooth.btservice.AdapterService;
import com.android.bluetooth.btservice.RemoteDevices;
import com.android.bluetooth.btservice.storage.DatabaseManager;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class HeadsetClientServiceTest {
    private HeadsetClientService mService = null;
    private BluetoothAdapter mAdapter = null;
    private Context mTargetContext;
    private boolean mIsAdapterServiceSet;
    private boolean mIsHeadsetClientServiceStarted;

    private static final int STANDARD_WAIT_MILLIS = 1000;

    @Mock private AdapterService mAdapterService;
    @Mock private HeadsetClientStateMachine mStateMachine;
    @Mock private NativeInterface mNativeInterface;
    @Mock private DatabaseManager mDatabaseManager;
    @Mock private RemoteDevices mRemoteDevices;

    @Before
    public void setUp() throws Exception {
        mTargetContext = InstrumentationRegistry.getTargetContext();
        MockitoAnnotations.initMocks(this);

        TestUtils.setAdapterService(mAdapterService);
        mIsAdapterServiceSet = true;
        doReturn(mDatabaseManager).when(mAdapterService).getDatabase();
        doReturn(mRemoteDevices).when(mAdapterService).getRemoteDevices();
        NativeInterface.setInstance(mNativeInterface);
    }

    @After
    public void tearDown() throws Exception {
        NativeInterface.setInstance(null);
        stopServiceIfStarted();
        if (mIsAdapterServiceSet) {
            TestUtils.clearAdapterService(mAdapterService);
        }
    }

    @Test
    public void testInitialize() throws Exception {
        startService();
        Assert.assertNotNull(HeadsetClientService.getHeadsetClientService());
    }

    @Ignore("b/260202548")
    @Test
    public void testSendBIEVtoStateMachineWhenBatteryChanged() throws Exception {
        startService();

        // Put mock state machine
        BluetoothDevice device =
                BluetoothAdapter.getDefaultAdapter().getRemoteDevice("00:01:02:03:04:05");
        mService.getStateMachineMap().put(device, mStateMachine);

        // Send battery changed intent
        Intent intent = new Intent(Intent.ACTION_BATTERY_CHANGED);
        intent.putExtra(BatteryManager.EXTRA_LEVEL, 50);
        mService.sendBroadcast(intent);

        // Expect send BIEV to state machine
        verify(mStateMachine, timeout(STANDARD_WAIT_MILLIS).times(1))
                .sendMessage(
                    eq(HeadsetClientStateMachine.SEND_BIEV),
                    eq(2),
                    anyInt());
    }

    @Test
    public void testUpdateBatteryLevel() throws Exception {
        startService();

        // Put mock state machine
        BluetoothDevice device =
                BluetoothAdapter.getDefaultAdapter().getRemoteDevice("00:01:02:03:04:05");
        mService.getStateMachineMap().put(device, mStateMachine);

        mService.updateBatteryLevel();

        // Expect send BIEV to state machine
        verify(mStateMachine, timeout(STANDARD_WAIT_MILLIS).times(1))
                .sendMessage(
                    eq(HeadsetClientStateMachine.SEND_BIEV),
                    eq(2),
                    anyInt());
    }

    @Test
    public void testSetCallAudioPolicy() throws Exception {
        startService();

        // Put mock state machine
        BluetoothDevice device =
                BluetoothAdapter.getDefaultAdapter().getRemoteDevice("00:01:02:03:04:05");
        mService.getStateMachineMap().put(device, mStateMachine);

        mService.setAudioPolicy(device, new BluetoothSinkAudioPolicy.Builder().build());

        verify(mStateMachine, timeout(STANDARD_WAIT_MILLIS).times(1))
                .setAudioPolicy(any(BluetoothSinkAudioPolicy.class));
    }

    @Test
    public void testDumpDoesNotCrash() throws Exception {
        startService();

        // Put mock state machine
        BluetoothDevice device =
                BluetoothAdapter.getDefaultAdapter().getRemoteDevice("00:01:02:03:04:05");
        mService.getStateMachineMap().put(device, mStateMachine);

        mService.dump(new StringBuilder());
    }

    @Test
    public void testHfpClientConnectionServiceStarted() throws Exception {
        Context context = Mockito.mock(Context.class);
        PackageManager packageManager = Mockito.mock(PackageManager.class);

        doReturn(false).when(packageManager).hasSystemFeature(FEATURE_WATCH);
        doReturn(packageManager).when(context).getPackageManager();

        HeadsetClientService service = new HeadsetClientService(context);
        service.start();

        verify(context).startService(any(Intent.class));

        service.stop();
    }

    @Test
    public void testHfpClientConnectionServiceNotStarted_wearable() throws Exception {
        Context context = Mockito.mock(Context.class);
        PackageManager packageManager = Mockito.mock(PackageManager.class);

        doReturn(true).when(packageManager).hasSystemFeature(FEATURE_WATCH);
        doReturn(packageManager).when(context).getPackageManager();

        HeadsetClientService service = new HeadsetClientService(context);
        service.start();

        verify(context, never()).startService(any(Intent.class));

        service.stop();
    }

    private void startService() throws Exception {
        // At this point the service should have started so check NOT null
        mService = new HeadsetClientService(mTargetContext);
        mService.start();
        mService.setAvailable(true);
        // Try getting the Bluetooth adapter
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        Assert.assertNotNull(mAdapter);
        mIsHeadsetClientServiceStarted = true;
    }

    private void stopServiceIfStarted() throws Exception {
        if (mIsHeadsetClientServiceStarted) {
            mService.stop();
            mService = HeadsetClientService.getHeadsetClientService();
            Assert.assertNull(mService);
        }
    }
}
