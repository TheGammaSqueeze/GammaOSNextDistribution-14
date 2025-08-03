/*
 * Copyright 2023 The Android Open Source Project
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

package com.android.bluetooth.btservice;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.IBluetoothOobDataCallback;
import android.content.AttributionSource;
import android.os.ParcelUuid;

import com.android.bluetooth.x.com.android.modules.utils.SynchronousResultReceiver;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.FileDescriptor;

public class AdapterServiceBinderTest {
    @Mock private AdapterService mService;
    @Mock private AdapterProperties mAdapterProperties;

    private AdapterService.AdapterServiceBinder mBinder;
    private AttributionSource mAttributionSource;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mService.mAdapterProperties = mAdapterProperties;
        doReturn(true).when(mService).isAvailable();
        doNothing().when(mService).enforceCallingOrSelfPermission(any(), any());
        mBinder = new AdapterService.AdapterServiceBinder(mService);
        mAttributionSource = new AttributionSource.Builder(0).build();
    }

    @After
    public void cleaUp() {
        mBinder.cleanup();
    }

    @Test
    public void getAddress() {
        mBinder.getAddress(mAttributionSource, SynchronousResultReceiver.get());
        verify(mService.mAdapterProperties).getAddress();
    }

    @Test
    public void dump() {
        FileDescriptor fd = new FileDescriptor();
        String[] args = new String[] { };
        mBinder.dump(fd, args);
        verify(mService).dump(any(), any(), any());

        Mockito.clearInvocations(mService);
        mBinder.cleanup();
        mBinder.dump(fd, args);
        verify(mService, never()).dump(any(), any(), any());
    }

    @Test
    public void generateLocalOobData() {
        int transport = 0;
        IBluetoothOobDataCallback cb = Mockito.mock(IBluetoothOobDataCallback.class);

        mBinder.generateLocalOobData(transport, cb, mAttributionSource,
                SynchronousResultReceiver.get());
        verify(mService).generateLocalOobData(transport, cb);

        Mockito.clearInvocations(mService);
        mBinder.cleanup();
        mBinder.generateLocalOobData(transport, cb, mAttributionSource,
                SynchronousResultReceiver.get());
        verify(mService, never()).generateLocalOobData(transport, cb);
    }

    @Test
    public void getIoCapability() {
        mBinder.getIoCapability(mAttributionSource, SynchronousResultReceiver.get());
        verify(mService.mAdapterProperties).getIoCapability();
    }

    @Test
    public void getLeMaximumAdvertisingDataLength() {
        mBinder.getLeMaximumAdvertisingDataLength(SynchronousResultReceiver.get());
        verify(mService).getLeMaximumAdvertisingDataLength();
    }

    @Test
    public void getScanMode() {
        mBinder.getScanMode(mAttributionSource, SynchronousResultReceiver.get());
        verify(mService).getScanMode();
    }

    @Test
    public void isActivityAndEnergyReportingSupported() {
        mBinder.isActivityAndEnergyReportingSupported(SynchronousResultReceiver.get());
        verify(mService.mAdapterProperties).isActivityAndEnergyReportingSupported();
    }

    @Test
    public void isLe2MPhySupported() {
        mBinder.isLe2MPhySupported(SynchronousResultReceiver.get());
        verify(mService).isLe2MPhySupported();
    }

    @Test
    public void isLeCodedPhySupported() {
        mBinder.isLeCodedPhySupported(SynchronousResultReceiver.get());
        verify(mService).isLeCodedPhySupported();
    }

    @Test
    public void isLeExtendedAdvertisingSupported() {
        mBinder.isLeExtendedAdvertisingSupported(SynchronousResultReceiver.get());
        verify(mService).isLeExtendedAdvertisingSupported();
    }

    @Test
    public void removeActiveDevice() {
        int profiles = BluetoothAdapter.ACTIVE_DEVICE_ALL;
        mBinder.removeActiveDevice(profiles, mAttributionSource, SynchronousResultReceiver.get());
        verify(mService).setActiveDevice(null, profiles);
    }

    @Test
    public void reportActivityInfo() {
        mBinder.reportActivityInfo(mAttributionSource, SynchronousResultReceiver.get());
        verify(mService).reportActivityInfo();
    }

    @Test
    public void retrievePendingSocketForServiceRecord() {
        ParcelUuid uuid = ParcelUuid.fromString("0000110A-0000-1000-8000-00805F9B34FB");
        mBinder.retrievePendingSocketForServiceRecord(uuid, mAttributionSource,
                SynchronousResultReceiver.get());
        verify(mService).retrievePendingSocketForServiceRecord(uuid, mAttributionSource);
    }

    @Test
    public void setIoCapability() {
        int capability = BluetoothAdapter.IO_CAPABILITY_MAX - 1;
        mBinder.setIoCapability(capability, mAttributionSource, SynchronousResultReceiver.get());
        verify(mService.mAdapterProperties).setIoCapability(capability);
    }

    @Test
    public void stopRfcommListener() {
        ParcelUuid uuid = ParcelUuid.fromString("0000110A-0000-1000-8000-00805F9B34FB");
        mBinder.stopRfcommListener(uuid, mAttributionSource, SynchronousResultReceiver.get());
        verify(mService).stopRfcommListener(uuid, mAttributionSource);
    }
}
