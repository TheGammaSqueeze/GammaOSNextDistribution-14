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
import android.bluetooth.le.AdvertisingSet;
import android.bluetooth.le.AdvertisingSetCallback;
import android.bluetooth.le.AdvertisingSetParameters;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.AttributionSource;
import android.content.Context;
import android.uwb.UwbTestUtils;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.server.uwb.discovery.DiscoveryAdvertiseProvider.DiscoveryAdvertiseCallback;
import com.android.server.uwb.discovery.info.AdvertiseInfo;
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

import java.util.concurrent.Executor;

/** Unit test for {@link BleDiscoveryAdvertiseProvider} */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class BleDiscoveryAdvertiseProviderTest {

    private static final Executor EXECUTOR = UwbTestUtils.getExecutor();
    private static final AdvertisingSetParameters PARAMETERS =
            new AdvertisingSetParameters.Builder()
                    .setInterval(AdvertisingSetParameters.INTERVAL_HIGH)
                    .setIncludeTxPower(true)
                    .setTxPowerLevel(AdvertisingSetParameters.TX_POWER_HIGH)
                    .build();
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

    private static final AdvertiseInfo ADVERTISE_INFO =
            new AdvertiseInfo(PARAMETERS, ADVERTISEMENT);

    @Mock AttributionSource mMockAttributionSource;
    @Mock Context mMockContext;
    @Mock BluetoothManager mMockBluetoothManager;
    @Mock BluetoothAdapter mMockBluetoothAdapter;
    @Mock BluetoothLeAdvertiser mMockBluetoothLeAdvertiser;
    @Mock DiscoveryAdvertiseCallback mMockDiscoveryAdvertiseCallback;
    @Mock DiscoveryAdvertisement mMockDiscoveryAdvertisement;
    @Mock AdvertisingSet mMockAdvertisingSet;

    private BleDiscoveryAdvertiseProvider mBleDiscoveryAdvertiseProvider;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mMockContext.getSystemService(BluetoothManager.class))
                .thenReturn(mMockBluetoothManager);
        when(mMockContext.createContext(any())).thenReturn(mMockContext);

        mBleDiscoveryAdvertiseProvider =
                new BleDiscoveryAdvertiseProvider(
                        mMockAttributionSource,
                        mMockContext,
                        EXECUTOR,
                        ADVERTISE_INFO,
                        mMockDiscoveryAdvertiseCallback);
    }

    @Test
    public void testStartAdvertise_checkAdvertisingSetParameters() {
        when(mMockBluetoothManager.getAdapter()).thenReturn(mMockBluetoothAdapter);
        when(mMockBluetoothAdapter.getBluetoothLeAdvertiser())
                .thenReturn(mMockBluetoothLeAdvertiser);

        AdvertisingSetParameters expectedParameters =
                new AdvertisingSetParameters.Builder()
                        .setInterval(AdvertisingSetParameters.INTERVAL_HIGH)
                        .setIncludeTxPower(true)
                        .setConnectable(true)
                        .setTxPowerLevel(AdvertisingSetParameters.TX_POWER_HIGH)
                        .build();
        assertThat(mBleDiscoveryAdvertiseProvider.start()).isTrue();
        ArgumentCaptor<AdvertisingSetParameters> captor =
                ArgumentCaptor.forClass(AdvertisingSetParameters.class);
        verify(mMockBluetoothLeAdvertiser, times(1))
                .startAdvertisingSet(
                        captor.capture(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(AdvertisingSetCallback.class));
        assertThat(captor.getValue().toString()).isEqualTo(expectedParameters.toString());
    }

    @Test
    public void testStartAdvertise_failedBTUnavailable() {
        when(mMockBluetoothManager.getAdapter()).thenReturn(null);

        assertThat(mBleDiscoveryAdvertiseProvider.start()).isFalse();
        verify(mMockBluetoothLeAdvertiser, never())
                .startAdvertisingSet(
                        any(), any(), any(), any(), any(), any(AdvertisingSetCallback.class));
    }

    @Test
    public void testStartAdvertise_failedBTDisabled() {
        when(mMockBluetoothManager.getAdapter()).thenReturn(mMockBluetoothAdapter);
        when(mMockBluetoothAdapter.getBluetoothLeAdvertiser()).thenReturn(null);

        assertThat(mBleDiscoveryAdvertiseProvider.start()).isFalse();
        verify(mMockBluetoothLeAdvertiser, never())
                .startAdvertisingSet(
                        any(), any(), any(), any(), any(), any(AdvertisingSetCallback.class));
    }

    @Test
    public void testStartAdvertise_failedBleAdvertise() {
        when(mMockBluetoothManager.getAdapter()).thenReturn(mMockBluetoothAdapter);
        when(mMockBluetoothAdapter.getBluetoothLeAdvertiser())
                .thenReturn(mMockBluetoothLeAdvertiser);
        doThrow(new IllegalArgumentException())
                .when(mMockBluetoothLeAdvertiser)
                .startAdvertisingSet(
                        any(), any(), any(), any(), any(), any(AdvertisingSetCallback.class));

        assertThat(mBleDiscoveryAdvertiseProvider.start()).isFalse();
        verify(mMockBluetoothLeAdvertiser, times(1))
                .startAdvertisingSet(
                        any(), any(), any(), any(), any(), any(AdvertisingSetCallback.class));
    }

    private void teststartAdvertisingSet(int status) {
        when(mMockBluetoothManager.getAdapter()).thenReturn(mMockBluetoothAdapter);
        when(mMockBluetoothAdapter.getBluetoothLeAdvertiser())
                .thenReturn(mMockBluetoothLeAdvertiser);

        Answer bleScanOpenResponse =
                new Answer() {
                    public Object answer(InvocationOnMock invocation) {
                        Object[] args = invocation.getArguments();
                        AdvertisingSetCallback cb = (AdvertisingSetCallback) args[5];
                        EXECUTOR.execute(
                                () ->
                                        cb.onAdvertisingSetStarted(
                                                mMockAdvertisingSet, /*txPower=*/ 1, status));
                        return null;
                    }
                };

        doAnswer(bleScanOpenResponse)
                .when(mMockBluetoothLeAdvertiser)
                .startAdvertisingSet(
                        any(), any(), any(), any(), any(), any(AdvertisingSetCallback.class));

        assertThat(mBleDiscoveryAdvertiseProvider.start()).isTrue();
        verify(mMockBluetoothLeAdvertiser, times(1))
                .startAdvertisingSet(
                        any(), any(), any(), any(), any(), any(AdvertisingSetCallback.class));
    }

    @Test
    public void teststartAdvertisingSet_success() {
        teststartAdvertisingSet(AdvertisingSetCallback.ADVERTISE_SUCCESS);
        verifyZeroInteractions(mMockDiscoveryAdvertiseCallback);
    }

    @Test
    public void teststartAdvertisingSet_failedAlreadyStarted() {
        teststartAdvertisingSet(AdvertisingSetCallback.ADVERTISE_FAILED_ALREADY_STARTED);
        verifyZeroInteractions(mMockDiscoveryAdvertiseCallback);
    }

    @Test
    public void teststartAdvertisingSet_failedDataTooLarge() {
        teststartAdvertisingSet(AdvertisingSetCallback.ADVERTISE_FAILED_DATA_TOO_LARGE);
        verify(mMockDiscoveryAdvertiseCallback, times(1))
                .onDiscoveryFailed(AdvertisingSetCallback.ADVERTISE_FAILED_DATA_TOO_LARGE);
    }

    @Test
    public void teststartAdvertisingSet_failedFeatureUnsupported() {
        teststartAdvertisingSet(AdvertisingSetCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED);
        verify(mMockDiscoveryAdvertiseCallback, times(1))
                .onDiscoveryFailed(AdvertisingSetCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED);
    }

    @Test
    public void teststartAdvertisingSet_failedInternalError() {
        teststartAdvertisingSet(AdvertisingSetCallback.ADVERTISE_FAILED_INTERNAL_ERROR);
        verify(mMockDiscoveryAdvertiseCallback, times(1))
                .onDiscoveryFailed(AdvertisingSetCallback.ADVERTISE_FAILED_INTERNAL_ERROR);
    }

    @Test
    public void teststartAdvertisingSet_failedTooManyAdvertiser() {
        teststartAdvertisingSet(AdvertisingSetCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS);
        verify(mMockDiscoveryAdvertiseCallback, times(1))
                .onDiscoveryFailed(AdvertisingSetCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS);
    }

    @Test
    public void testStopAdvertise_failedNotStarted() {
        assertThat(mBleDiscoveryAdvertiseProvider.stop()).isFalse();
        verify(mMockBluetoothManager, never()).getAdapter();
        verify(mMockBluetoothLeAdvertiser, never())
                .stopAdvertisingSet(any(AdvertisingSetCallback.class));
    }

    @Test
    public void testStopAdvertise_failedBTUnavailable() {
        teststartAdvertisingSet(AdvertisingSetCallback.ADVERTISE_SUCCESS);
        verifyZeroInteractions(mMockDiscoveryAdvertiseCallback);

        when(mMockBluetoothManager.getAdapter()).thenReturn(null);

        assertThat(mBleDiscoveryAdvertiseProvider.stop()).isFalse();
        verify(mMockBluetoothLeAdvertiser, never())
                .stopAdvertisingSet(any(AdvertisingSetCallback.class));
    }

    @Test
    public void testStopAdvertise_failedBTDisabled() {
        teststartAdvertisingSet(AdvertisingSetCallback.ADVERTISE_SUCCESS);
        verifyZeroInteractions(mMockDiscoveryAdvertiseCallback);

        when(mMockBluetoothManager.getAdapter()).thenReturn(mMockBluetoothAdapter);
        when(mMockBluetoothAdapter.getBluetoothLeAdvertiser()).thenReturn(null);

        assertThat(mBleDiscoveryAdvertiseProvider.stop()).isFalse();
        verify(mMockBluetoothLeAdvertiser, never())
                .stopAdvertisingSet(any(AdvertisingSetCallback.class));
    }

    @Test
    public void testStopAdvertise_success() {
        teststartAdvertisingSet(AdvertisingSetCallback.ADVERTISE_SUCCESS);
        verifyZeroInteractions(mMockDiscoveryAdvertiseCallback);

        assertThat(mBleDiscoveryAdvertiseProvider.stop()).isTrue();
        verify(mMockBluetoothLeAdvertiser, times(1))
                .stopAdvertisingSet(any(AdvertisingSetCallback.class));
    }
}
