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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.ScanResult;
import android.content.AttributionSource;
import android.content.Context;
import android.uwb.UwbTestUtils;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.server.uwb.discovery.TransportClientProvider.TransportClientCallback;
import com.android.server.uwb.discovery.TransportServerProvider.TransportServerCallback;
import com.android.server.uwb.discovery.info.DiscoveryInfo;
import com.android.server.uwb.discovery.info.DiscoveryInfo.TransportType;
import com.android.server.uwb.discovery.info.TransportClientInfo;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;
import java.util.concurrent.Executor;

/** Unit test for {@link TransportProviderFactory} */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class TransportProviderFactoryTest {

    private static final Executor EXECUTOR = UwbTestUtils.getExecutor();
    private static final int SECID = 2;

    @Mock AttributionSource mMockAttributionSource;
    @Mock Context mMockContext;
    @Mock BluetoothManager mMockBluetoothManager;
    @Mock BluetoothAdapter mMockBluetoothAdapter;
    @Mock BluetoothGattServer mMockBluetoothGattServer;
    @Mock TransportServerCallback mMockTransportServerCallback;
    @Mock TransportClientCallback mMockTransportClientCallback;
    @Mock ScanResult mMockScanResult;
    @Mock BluetoothDevice mMockBluetoothDevice;
    @Mock BluetoothGatt mMockBluetoothGatt;

    private DiscoveryInfo mDiscoveryInfo;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mMockContext.createContext(any())).thenReturn(mMockContext);
        when(mMockContext.getSystemService(BluetoothManager.class))
                .thenReturn(mMockBluetoothManager);
        when(mMockBluetoothManager.getAdapter()).thenReturn(mMockBluetoothAdapter);
        when(mMockBluetoothManager.openGattServer(
                        eq(mMockContext), any(BluetoothGattServerCallback.class)))
                .thenReturn(mMockBluetoothGattServer);
        when(mMockBluetoothGattServer.addService(any())).thenReturn(true);
        when(mMockScanResult.getDevice()).thenReturn(mMockBluetoothDevice);
        when(mMockBluetoothDevice.connectGatt(eq(mMockContext), anyBoolean(), any(), anyInt()))
                .thenReturn(mMockBluetoothGatt);

        mDiscoveryInfo =
                new DiscoveryInfo(
                        TransportType.BLE,
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of(new TransportClientInfo(mMockScanResult)));
    }

    @Test
    public void testServerStart() {
        TransportServerProvider provider =
                TransportProviderFactory.createServer(
                        mMockAttributionSource,
                        mMockContext,
                        SECID,
                        mDiscoveryInfo,
                        mMockTransportServerCallback);

        assertThat(provider).isNotNull();
        assertThat(provider.start()).isTrue();
    }

    @Test
    public void testClientStart() {
        TransportClientProvider provider =
                TransportProviderFactory.createClient(
                        mMockAttributionSource,
                        mMockContext,
                        EXECUTOR,
                        SECID,
                        mDiscoveryInfo,
                        mMockTransportClientCallback);

        assertThat(provider).isNotNull();
        assertThat(provider.start()).isTrue();
    }
}
