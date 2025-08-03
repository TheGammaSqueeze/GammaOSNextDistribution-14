/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.server.uwb.discovery.ble;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.AttributionSource;
import android.content.Context;
import android.uwb.UwbTestUtils;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.server.uwb.discovery.DiscoveryScanProvider.DiscoveryResult;
import com.android.server.uwb.discovery.DiscoveryScanProvider.DiscoveryScanCallback;
import com.android.server.uwb.discovery.info.ScanInfo;
import com.android.server.uwb.discovery.info.SecureComponentInfo;
import com.android.server.uwb.discovery.info.UwbIndicationData;
import com.android.server.uwb.discovery.info.VendorSpecificData;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/** Unit test for {@link BleDiscoveryScanProvider} */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class BleDiscoveryScanProviderTest {

    private static final Executor EXECUTOR = UwbTestUtils.getExecutor();

    @Mock AttributionSource mMockAttributionSource;
    @Mock Context mMockContext;
    @Mock BluetoothManager mMockBluetoothManager;
    @Mock BluetoothAdapter mMockBluetoothAdapter;
    @Mock BluetoothLeScanner mMockBluetoothLeScanner;
    @Mock DiscoveryScanCallback mMockDiscoveryScanCallback;
    @Mock ScanInfo mScanInfo;

    private BleDiscoveryScanProvider mBleDiscoveryScanProvider;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mMockContext.getSystemService(BluetoothManager.class))
                .thenReturn(mMockBluetoothManager);
        when(mMockContext.createContext(any())).thenReturn(mMockContext);

        mBleDiscoveryScanProvider =
                new BleDiscoveryScanProvider(
                        mMockAttributionSource,
                        mMockContext,
                        EXECUTOR,
                        mScanInfo,
                        mMockDiscoveryScanCallback);
    }

    @Test
    public void testStartScan_successWithScanInfo() {
        when(mMockBluetoothManager.getAdapter()).thenReturn(mMockBluetoothAdapter);
        when(mMockBluetoothAdapter.getBluetoothLeScanner()).thenReturn(mMockBluetoothLeScanner);

        List<ScanFilter> scanFilterList = new ArrayList<ScanFilter>();
        scanFilterList.add(new ScanFilter.Builder().setDeviceName("TEST_NAME").build());
        mBleDiscoveryScanProvider =
                new BleDiscoveryScanProvider(
                        mMockAttributionSource,
                        mMockContext,
                        EXECUTOR,
                        new ScanInfo(
                                scanFilterList,
                                new ScanSettings.Builder().setReportDelay(10).build()),
                        mMockDiscoveryScanCallback);

        assertThat(mBleDiscoveryScanProvider.start()).isTrue();
        verify(mMockBluetoothLeScanner, times(1)).startScan(any(), any(), any(ScanCallback.class));
    }

    @Test
    public void testStartScan_failedBTUnavailable() {
        when(mMockBluetoothManager.getAdapter()).thenReturn(null);

        assertThat(mBleDiscoveryScanProvider.start()).isFalse();
        verify(mMockBluetoothLeScanner, never()).startScan(any(), any(), any(ScanCallback.class));
    }

    @Test
    public void testStartScan_failedBTDisabled() {
        when(mMockBluetoothManager.getAdapter()).thenReturn(mMockBluetoothAdapter);
        when(mMockBluetoothAdapter.getBluetoothLeScanner()).thenReturn(null);

        assertThat(mBleDiscoveryScanProvider.start()).isFalse();
        verify(mMockBluetoothLeScanner, never()).startScan(any(), any(), any(ScanCallback.class));
    }

    @Test
    public void testStartScan_failedBleScan() {
        when(mMockBluetoothManager.getAdapter()).thenReturn(mMockBluetoothAdapter);
        when(mMockBluetoothAdapter.getBluetoothLeScanner()).thenReturn(mMockBluetoothLeScanner);
        doThrow(new IllegalArgumentException())
                .when(mMockBluetoothLeScanner)
                .startScan(any(), any(), any(ScanCallback.class));

        assertThat(mBleDiscoveryScanProvider.start()).isFalse();
        verify(mMockBluetoothLeScanner, times(1)).startScan(any(), any(), any(ScanCallback.class));
    }

    @Test
    public void testStopScan_failedBTUnavailable() {
        when(mMockBluetoothManager.getAdapter()).thenReturn(null);

        assertThat(mBleDiscoveryScanProvider.stop()).isFalse();
        verify(mMockBluetoothLeScanner, never()).stopScan(any(ScanCallback.class));
    }

    @Test
    public void testStopScan_failedBTDisabled() {
        when(mMockBluetoothManager.getAdapter()).thenReturn(mMockBluetoothAdapter);
        when(mMockBluetoothAdapter.getBluetoothLeScanner()).thenReturn(null);

        assertThat(mBleDiscoveryScanProvider.stop()).isFalse();
        verify(mMockBluetoothLeScanner, never()).stopScan(any(ScanCallback.class));
    }

    @Test
    public void testBleScanFailed() {
        when(mMockBluetoothManager.getAdapter()).thenReturn(mMockBluetoothAdapter);
        when(mMockBluetoothAdapter.getBluetoothLeScanner()).thenReturn(mMockBluetoothLeScanner);

        Answer bleScanOpenResponse =
                new Answer() {
                    public Object answer(InvocationOnMock invocation) {
                        Object[] args = invocation.getArguments();
                        ScanCallback cb = (ScanCallback) args[2];
                        cb.onScanFailed(ScanCallback.SCAN_FAILED_ALREADY_STARTED);
                        return null;
                    }
                };

        doAnswer(bleScanOpenResponse)
                .when(mMockBluetoothLeScanner)
                .startScan(any(), any(), any(ScanCallback.class));

        assertThat(mBleDiscoveryScanProvider.start()).isTrue();
        verify(mMockBluetoothLeScanner, times(1)).startScan(any(), any(), any(ScanCallback.class));
        verify(mMockDiscoveryScanCallback, times(1))
                .onDiscoveryFailed(ScanCallback.SCAN_FAILED_ALREADY_STARTED);
    }

    @Test
    public void testBleScanResult_noScanRecord() {
        when(mMockBluetoothManager.getAdapter()).thenReturn(mMockBluetoothAdapter);
        when(mMockBluetoothAdapter.getBluetoothLeScanner()).thenReturn(mMockBluetoothLeScanner);

        ScanResult scanResult =
                new ScanResult(
                        /*device=*/ null,
                        /*eventType=*/ 0,
                        /*primaryPhy=*/ 0,
                        /*secondaryPhy=*/ 0,
                        /*advertisingSid=*/ 0,
                        /*txPower=*/ 0,
                        /*rssi=*/ 0,
                        /*periodicAdvertisingInterval=*/ 0,
                        /*scanRecord=*/ null,
                        /*timestampNanos=*/ 0);

        Answer bleScanOpenResponse =
                new Answer() {
                    public Object answer(InvocationOnMock invocation) {
                        Object[] args = invocation.getArguments();
                        ScanCallback cb = (ScanCallback) args[2];
                        cb.onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, scanResult);
                        return null;
                    }
                };

        doAnswer(bleScanOpenResponse)
                .when(mMockBluetoothLeScanner)
                .startScan(any(), any(), any(ScanCallback.class));

        assertThat(mBleDiscoveryScanProvider.start()).isTrue();
        verify(mMockBluetoothLeScanner, times(1)).startScan(any(), any(), any(ScanCallback.class));
        verifyZeroInteractions(mMockDiscoveryScanCallback);
    }

    private static ScanRecord parseScanRecord(byte[] bytes) {
        Class<?> scanRecordClass = ScanRecord.class;
        try {
            Method method = scanRecordClass.getDeclaredMethod("parseFromBytes", byte[].class);
            return (ScanRecord) method.invoke(null, bytes);
        } catch (NoSuchMethodException
                | IllegalAccessException
                | IllegalArgumentException
                | InvocationTargetException e) {
            return null;
        }
    }

    private void testBleScanResult(ScanResult scanResult) {
        when(mMockBluetoothManager.getAdapter()).thenReturn(mMockBluetoothAdapter);
        when(mMockBluetoothAdapter.getBluetoothLeScanner()).thenReturn(mMockBluetoothLeScanner);

        Answer bleScanOpenResponse =
                new Answer() {
                    public Object answer(InvocationOnMock invocation) {
                        Object[] args = invocation.getArguments();
                        ScanCallback cb = (ScanCallback) args[2];
                        EXECUTOR.execute(
                                () ->
                                        cb.onScanResult(
                                                ScanSettings.CALLBACK_TYPE_ALL_MATCHES,
                                                scanResult));
                        return null;
                    }
                };

        doAnswer(bleScanOpenResponse)
                .when(mMockBluetoothLeScanner)
                .startScan(any(), any(), any(ScanCallback.class));

        assertThat(mBleDiscoveryScanProvider.start()).isTrue();
    }

    @Test
    public void testBleScanResult_noServiceData() {
        ScanRecord scanRecord =
                parseScanRecord(
                        new byte[] {
                            // advertising flags
                            0x02, 0x01, 0x1a,
                        });
        ScanResult scanResult =
                new ScanResult(
                        /*device=*/ null,
                        /*eventType=*/ 0,
                        /*primaryPhy=*/ 0,
                        /*secondaryPhy=*/ 0,
                        /*advertisingSid=*/ 0,
                        /*txPower=*/ 0,
                        /*rssi=*/ 0,
                        /*periodicAdvertisingInterval=*/ 0,
                        scanRecord,
                        /*timestampNanos=*/ 0);

        testBleScanResult(scanResult);
        verify(mMockBluetoothLeScanner, times(1)).startScan(any(), any(), any(ScanCallback.class));
        verifyZeroInteractions(mMockDiscoveryScanCallback);
    }

    @Test
    public void testBleScanResult_serviceDataParsingFailed() {
        ScanRecord scanRecord =
                parseScanRecord(
                        new byte[] {
                            // advertising flags
                            0x02,
                            0x01,
                            0x1a,
                            // 16 bit service uuids
                            0x03,
                            0x03,
                            (byte) 0xF3,
                            (byte) 0xFF,
                            // service data, invalid indication data length
                            0x05,
                            0x16,
                            (byte) 0xF3,
                            (byte) 0xFF,
                            0x12,
                            (byte) 0b11101001,
                        });
        ScanResult scanResult =
                new ScanResult(
                        /*device=*/ null,
                        /*eventType=*/ 0,
                        /*primaryPhy=*/ 0,
                        /*secondaryPhy=*/ 0,
                        /*advertisingSid=*/ 0,
                        /*txPower=*/ 0,
                        /*rssi=*/ 0,
                        /*periodicAdvertisingInterval=*/ 0,
                        scanRecord,
                        /*timestampNanos=*/ 0);

        testBleScanResult(scanResult);
        verify(mMockBluetoothLeScanner, times(1)).startScan(any(), any(), any(ScanCallback.class));
        verifyZeroInteractions(mMockDiscoveryScanCallback);
    }

    @Test
    public void testBleScanResult_rssiBelowThreshold() {
        ScanRecord scanRecord =
                parseScanRecord(
                        new byte[] {
                            // advertising flags
                            0x02,
                            0x01,
                            0x1a,
                            // 16 bit service uuids
                            0x03,
                            0x03,
                            (byte) 0xF3,
                            (byte) 0xFF,
                            // service data, rssi threhold=-100
                            0x06,
                            0x16,
                            (byte) 0xF3,
                            (byte) 0xFF,
                            0x12,
                            (byte) 0b11101001,
                            (byte) 0x9C,
                        });
        ScanResult scanResult =
                new ScanResult(
                        /*device=*/ null,
                        /*eventType=*/ 0,
                        /*primaryPhy=*/ 0,
                        /*secondaryPhy=*/ 0,
                        /*advertisingSid=*/ 0,
                        /*txPower=*/ 0,
                        /*rssi=*/ -101,
                        /*periodicAdvertisingInterval=*/ 0,
                        scanRecord,
                        /*timestampNanos=*/ 0);
        testBleScanResult(scanResult);
        verify(mMockBluetoothLeScanner, times(1)).startScan(any(), any(), any(ScanCallback.class));
        verifyZeroInteractions(mMockDiscoveryScanCallback);
    }

    @Test
    public void testBleScanResult_success() {
        ScanRecord scanRecord =
                parseScanRecord(
                        new byte[] {
                            // advertising flags
                            0x02,
                            0x01,
                            0x1a,
                            // 16 bit service uuids
                            0x03,
                            0x03,
                            (byte) 0xF3,
                            (byte) 0xFF,
                            // service data, rssi threhold=-100
                            0x06,
                            0x16,
                            (byte) 0xF3,
                            (byte) 0xFF,
                            0x12,
                            (byte) 0b11101001,
                            (byte) 0x9C,
                            // manufacturer specific data
                            0x05,
                            (byte) 0xff,
                            0x75,
                            0x00,
                            0x02,
                            0x15,
                        });
        ScanResult scanResult =
                new ScanResult(
                        /*device=*/ null,
                        /*eventType=*/ 0,
                        /*primaryPhy=*/ 0,
                        /*secondaryPhy=*/ 0,
                        /*advertisingSid=*/ 0,
                        /*txPower=*/ 0,
                        /*rssi=*/ 10,
                        /*periodicAdvertisingInterval=*/ 0,
                        scanRecord,
                        /*timestampNanos=*/ 0);

        DiscoveryAdvertisement expectedAdv =
                new DiscoveryAdvertisement(
                        new UwbIndicationData(
                                /*firaUwbSupport=*/ true,
                                /*iso14443Support=*/ true,
                                /*uwbRegulartoryInfoAvailableInAd=*/ true,
                                /*uwbRegulartoryInfoAvailableInOob=*/ false,
                                /*firaProfileInfoAvailableInAd=*/ true,
                                /*firaProfileInfoAvailableInOob=*/ false,
                                /*dualGapRoleSupport=*/ true,
                                /*bluetoothRssiThresholdDbm=*/ -100,
                                new SecureComponentInfo[] {}),
                        /*regulatoryInfo=*/ null,
                        /*firaProfileSupportInfo=*/ null,
                        new VendorSpecificData[] {
                            new VendorSpecificData(/*vendorId=*/ 117, new byte[] {0x02, 0x15}),
                        });

        testBleScanResult(scanResult);
        verify(mMockBluetoothLeScanner, times(1)).startScan(any(), any(), any(ScanCallback.class));

        ArgumentCaptor<DiscoveryResult> captor = ArgumentCaptor.forClass(DiscoveryResult.class);
        verify(mMockDiscoveryScanCallback, times(1)).onDiscovered(captor.capture());
        assertThat(captor.getValue().discoveryAdvertisement.toString())
                .isEqualTo(expectedAdv.toString());
        assertThat(captor.getValue().scanResult).isEqualTo(scanResult);
    }
}
