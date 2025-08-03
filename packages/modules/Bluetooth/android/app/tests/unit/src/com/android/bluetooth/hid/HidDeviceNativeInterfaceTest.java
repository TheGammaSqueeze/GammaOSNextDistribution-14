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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothHidDevice;

import com.android.bluetooth.TestUtils;
import com.android.bluetooth.btservice.AdapterService;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class HidDeviceNativeInterfaceTest {
    private static final byte[] TEST_DEVICE_ADDRESS =
            new byte[] { 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 };
    @Mock
    HidDeviceService mService;
    @Mock
    AdapterService mAdapterService;

    private HidDeviceNativeInterface mNativeInterface;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mService.isAvailable()).thenReturn(true);
        HidDeviceService.setHidDeviceService(mService);
        TestUtils.setAdapterService(mAdapterService);
        mNativeInterface = HidDeviceNativeInterface.getInstance();
    }

    @After
    public void tearDown() throws Exception {
        HidDeviceService.setHidDeviceService(null);
        TestUtils.clearAdapterService(mAdapterService);
    }

    @Test
    public void onApplicationStateChanged() {
        mNativeInterface.onApplicationStateChanged(TEST_DEVICE_ADDRESS, true);
        verify(mService).onApplicationStateChangedFromNative(any(), anyBoolean());
    }

    @Test
    public void onConnectStateChanged() {
        mNativeInterface.onConnectStateChanged(TEST_DEVICE_ADDRESS,
                BluetoothHidDevice.STATE_DISCONNECTED);
        verify(mService).onConnectStateChangedFromNative(any(), anyInt());
    }

    @Test
    public void onGetReport() {
        byte type = 1;
        byte id = 2;
        short bufferSize = 100;
        mNativeInterface.onGetReport(type, id, bufferSize);
        verify(mService).onGetReportFromNative(type, id, bufferSize);
    }

    @Test
    public void onSetReport() {
        byte reportType = 1;
        byte reportId = 2;
        byte[] data = new byte[] { 0x00, 0x00 };
        mNativeInterface.onSetReport(reportType, reportId, data);
        verify(mService).onSetReportFromNative(reportType, reportId, data);
    }

    @Test
    public void onSetProtocol() {
        byte protocol = 1;
        mNativeInterface.onSetProtocol(protocol);
        verify(mService).onSetProtocolFromNative(protocol);
    }

    @Test
    public void onInterruptData() {
        byte reportId = 3;
        byte[] data = new byte[] { 0x00, 0x00 };
        mNativeInterface.onInterruptData(reportId, data);
        verify(mService).onInterruptDataFromNative(reportId, data);
    }

    @Test
    public void onVirtualCableUnplug() {
        mNativeInterface.onVirtualCableUnplug();
        verify(mService).onVirtualCableUnplugFromNative();
    }
}
