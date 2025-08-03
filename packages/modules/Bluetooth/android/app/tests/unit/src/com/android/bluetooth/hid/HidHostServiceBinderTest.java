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

package com.android.bluetooth.hid;

import static org.mockito.Mockito.verify;
import android.platform.test.flag.junit.SetFlagsRule;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;
import com.android.bluetooth.flags.Flags;
import com.android.bluetooth.x.com.android.modules.utils.SynchronousResultReceiver;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class HidHostServiceBinderTest {
    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();
    private static final String REMOTE_DEVICE_ADDRESS = "00:00:00:00:00:00";

    @Mock
    private HidHostService mService;

    BluetoothDevice mRemoteDevice;

    HidHostService.BluetoothHidHostBinder mBinder;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mRemoteDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(REMOTE_DEVICE_ADDRESS);
        mBinder = new HidHostService.BluetoothHidHostBinder(mService);
    }

    @Test
    public void connect_callsServiceMethod() {
        mBinder.connect(mRemoteDevice, null, SynchronousResultReceiver.get());

        verify(mService).connect(mRemoteDevice);
    }

    @Test
    public void disconnect_callsServiceMethod() {
        mBinder.disconnect(mRemoteDevice, null, SynchronousResultReceiver.get());

        verify(mService).disconnect(mRemoteDevice);
    }

    @Test
    public void getConnectedDevices_callsServiceMethod() {
        mBinder.getConnectedDevices(null, SynchronousResultReceiver.get());

        verify(mService).getDevicesMatchingConnectionStates(
                new int[] { BluetoothProfile.STATE_CONNECTED });
    }

    @Test
    public void getDevicesMatchingConnectionStates_callsServiceMethod() {
        int[] states = new int[] {BluetoothProfile.STATE_CONNECTED};
        mBinder.getDevicesMatchingConnectionStates(states, null, SynchronousResultReceiver.get());

        verify(mService).getDevicesMatchingConnectionStates(states);
    }

    @Test
    public void getConnectionState_callsServiceMethod() {
        mBinder.getConnectionState(mRemoteDevice, null, SynchronousResultReceiver.get());

        verify(mService).getConnectionState(mRemoteDevice);
    }

    @Test
    public void setConnectionPolicy_callsServiceMethod() {
        int connectionPolicy = BluetoothProfile.CONNECTION_POLICY_ALLOWED;
        mBinder.setConnectionPolicy(mRemoteDevice, connectionPolicy,
                null, SynchronousResultReceiver.get());

        verify(mService).setConnectionPolicy(mRemoteDevice, connectionPolicy);
    }

    @Test
    public void getConnectionPolicy_callsServiceMethod() {
        mBinder.getConnectionPolicy(mRemoteDevice, null, SynchronousResultReceiver.get());

        verify(mService).getConnectionPolicy(mRemoteDevice);
    }

    @Test
    public void setPreferredTransport_callsServiceMethod() {
        mSetFlagsRule.enableFlags(Flags.FLAG_ALLOW_SWITCHING_HID_AND_HOGP);
        int preferredTransport = BluetoothDevice.TRANSPORT_AUTO;
        mBinder.setPreferredTransport(
                mRemoteDevice, preferredTransport, null, SynchronousResultReceiver.get());

        verify(mService).setPreferredTransport(mRemoteDevice, preferredTransport);
    }

    @Test
    public void getPreferredTransport_callsServiceMethod() {
        mSetFlagsRule.enableFlags(Flags.FLAG_ALLOW_SWITCHING_HID_AND_HOGP);
        mBinder.getPreferredTransport(mRemoteDevice, null, SynchronousResultReceiver.get());

        verify(mService).getPreferredTransport(mRemoteDevice);
    }

    @Test
    public void getProtocolMode_callsServiceMethod() {
        mBinder.getProtocolMode(mRemoteDevice, null,
                SynchronousResultReceiver.get());

        verify(mService).getProtocolMode(mRemoteDevice);
    }

    @Test
    public void virtualUnplug_callsServiceMethod() {
        mBinder.virtualUnplug(mRemoteDevice, null, SynchronousResultReceiver.get());

        verify(mService).virtualUnplug(mRemoteDevice);
    }

    @Test
    public void setProtocolMode_callsServiceMethod() {
        int protocolMode = 1;
        mBinder.setProtocolMode(mRemoteDevice, protocolMode, null, SynchronousResultReceiver.get());

        verify(mService).setProtocolMode(mRemoteDevice, protocolMode);
    }

    @Test
    public void getReport_callsServiceMethod() {
        byte reportType = 1;
        byte reportId = 2;
        int bufferSize = 16;
        mBinder.getReport(mRemoteDevice, reportType, reportId, bufferSize, null,
                SynchronousResultReceiver.get());

        verify(mService).getReport(mRemoteDevice, reportType, reportId, bufferSize);
    }

    @Test
    public void setReport_callsServiceMethod() {
        byte reportType = 1;
        String report = "test_report";
        mBinder.setReport(mRemoteDevice, reportType, report, null,
                SynchronousResultReceiver.get());

        verify(mService).setReport(mRemoteDevice, reportType, report);
    }

    @Test
    public void sendData_callsServiceMethod() {
        String report = "test_report";
        mBinder.sendData(mRemoteDevice, report, null,
                SynchronousResultReceiver.get());

        verify(mService).sendData(mRemoteDevice, report);
    }

    @Test
    public void setIdleTime_callsServiceMethod() {
        byte idleTime = 1;
        mBinder.setIdleTime(mRemoteDevice, idleTime, null,
                SynchronousResultReceiver.get());

        verify(mService).setIdleTime(mRemoteDevice, idleTime);
    }

    @Test
    public void getIdleTime_callsServiceMethod() {
        mBinder.getIdleTime(mRemoteDevice, null, SynchronousResultReceiver.get());

        verify(mService).getIdleTime(mRemoteDevice);
    }

    @Test
    public void cleanUp_doesNotCrash() {
        mBinder.cleanup();
    }
}
