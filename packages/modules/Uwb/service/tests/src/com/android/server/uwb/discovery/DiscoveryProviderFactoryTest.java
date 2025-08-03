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

package com.android.server.uwb.discovery;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertisingSetParameters;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanSettings;
import android.content.AttributionSource;
import android.content.Context;
import android.uwb.UwbTestUtils;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.server.uwb.discovery.DiscoveryAdvertiseProvider.DiscoveryAdvertiseCallback;
import com.android.server.uwb.discovery.DiscoveryScanProvider.DiscoveryScanCallback;
import com.android.server.uwb.discovery.ble.DiscoveryAdvertisement;
import com.android.server.uwb.discovery.info.AdvertiseInfo;
import com.android.server.uwb.discovery.info.DiscoveryInfo;
import com.android.server.uwb.discovery.info.DiscoveryInfo.TransportType;
import com.android.server.uwb.discovery.info.ScanInfo;
import com.android.server.uwb.discovery.info.SecureComponentInfo;
import com.android.server.uwb.discovery.info.UwbIndicationData;
import com.android.server.uwb.discovery.info.VendorSpecificData;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.Executor;

/** Unit test for {@link DiscoveryProviderFactory} */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class DiscoveryProviderFactoryTest {

    private static final Executor EXECUTOR = UwbTestUtils.getExecutor();
    private static final DiscoveryAdvertisement ADVERTISEMENT =
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

    private static final DiscoveryInfo DISCOVERY_INFO =
            new DiscoveryInfo(
                    TransportType.BLE,
                    Optional.of(
                            new ScanInfo(
                                    new ArrayList<ScanFilter>(),
                                    new ScanSettings.Builder().build())),
                    Optional.of(
                            new AdvertiseInfo(
                                    new AdvertisingSetParameters.Builder().build(), ADVERTISEMENT)),
                    Optional.empty());

    @Mock AttributionSource mMockAttributionSource;
    @Mock Context mMockContext;
    @Mock BluetoothManager mMockBluetoothManager;
    @Mock BluetoothAdapter mMockBluetoothAdapter;
    @Mock BluetoothLeAdvertiser mMockBluetoothLeAdvertiser;
    @Mock BluetoothLeScanner mMockBluetoothLeScanner;
    @Mock DiscoveryAdvertiseCallback mMockDiscoveryAdvertiseCallback;
    @Mock DiscoveryScanCallback mMockDiscoveryScanCallback;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mMockContext.getSystemService(BluetoothManager.class))
                .thenReturn(mMockBluetoothManager);
        when(mMockContext.createContext(any())).thenReturn(mMockContext);
        when(mMockBluetoothManager.getAdapter()).thenReturn(mMockBluetoothAdapter);
        when(mMockBluetoothAdapter.getBluetoothLeAdvertiser())
                .thenReturn(mMockBluetoothLeAdvertiser);
        when(mMockBluetoothAdapter.getBluetoothLeScanner()).thenReturn(mMockBluetoothLeScanner);
    }

    @Test
    public void testScanStart() {
        DiscoveryProvider provider =
                DiscoveryProviderFactory.createScanner(
                        mMockAttributionSource,
                        mMockContext,
                        EXECUTOR,
                        DISCOVERY_INFO,
                        mMockDiscoveryScanCallback);

        assertThat(provider).isNotNull();
        assertThat(provider.start()).isTrue();
    }

    @Test
    public void testAdvertiseStart() {
        DiscoveryProvider provider =
                DiscoveryProviderFactory.createAdvertiser(
                        mMockAttributionSource,
                        mMockContext,
                        EXECUTOR,
                        DISCOVERY_INFO,
                        mMockDiscoveryAdvertiseCallback);

        assertThat(provider).isNotNull();
        assertThat(provider.start()).isTrue();
    }
}
