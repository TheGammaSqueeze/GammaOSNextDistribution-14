/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.bluetooth;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.app.PendingIntent;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.ParcelUuid;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;

import com.google.protobuf.ByteString;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import pandora.HostProto;
import pandora.HostProto.AdvertiseRequest;
import pandora.HostProto.AdvertiseResponse;
import pandora.HostProto.OwnAddressType;

@RunWith(AndroidJUnit4.class)
public class LeScanningTest {
    private static final String TAG = "LeScanningTest";
    private static final int TIMEOUT_SCANNING_MS = 2000;
    private static final String TEST_UUID_STRING = "00001805-0000-1000-8000-00805f9b34fb";
    private static final String TEST_ADDRESS_RANDOM_STATIC = "F0:43:A8:23:10:11";
    private static final String ACTION_DYNAMIC_RECEIVER_SCAN_RESULT =
            "android.bluetooth.test.ACTION_DYNAMIC_RECEIVER_SCAN_RESULT";

    @Rule public final AdoptShellPermissionsRule mPermissionRule = new AdoptShellPermissionsRule();

    @Rule public final PandoraDevice mBumble = new PandoraDevice();

    private final Context mContext = ApplicationProvider.getApplicationContext();
    private final BluetoothManager mBluetoothManager =
            mContext.getSystemService(BluetoothManager.class);
    private final BluetoothAdapter mBluetoothAdapter = mBluetoothManager.getAdapter();
    private final BluetoothLeScanner mLeScanner = mBluetoothAdapter.getBluetoothLeScanner();

    @Test
    public void startBleScan_withCallbackTypeAllMatches() {
        advertiseWithBumble(TEST_UUID_STRING, OwnAddressType.PUBLIC);

        ScanFilter scanFilter =
                new ScanFilter.Builder()
                        .setServiceUuid(ParcelUuid.fromString(TEST_UUID_STRING))
                        .build();

        List<ScanResult> results =
                startScanning(scanFilter, ScanSettings.CALLBACK_TYPE_ALL_MATCHES);

        assertThat(results).isNotNull();
        assertThat(results.get(0).getScanRecord().getServiceUuids().get(0))
                .isEqualTo(ParcelUuid.fromString(TEST_UUID_STRING));
        assertThat(results.get(1).getScanRecord().getServiceUuids().get(0))
                .isEqualTo(ParcelUuid.fromString(TEST_UUID_STRING));
    }

    @Test
    public void scanForIrkIdentityAddress_withCallbackTypeAllMatches() {
        advertiseWithBumble(null, OwnAddressType.RANDOM);

        ScanFilter scanFilter =
                new ScanFilter.Builder()
                        .setDeviceAddress(
                                TEST_ADDRESS_RANDOM_STATIC,
                                BluetoothDevice.ADDRESS_TYPE_RANDOM,
                                Utils.BUMBLE_IRK)
                        .build();

        List<ScanResult> results =
                startScanning(scanFilter, ScanSettings.CALLBACK_TYPE_ALL_MATCHES);

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).getDevice().getAddress()).isEqualTo(TEST_ADDRESS_RANDOM_STATIC);
    }

    @Test
    public void startBleScan_withCallbackTypeFirstMatchSilentlyFails() {
        advertiseWithBumble(TEST_UUID_STRING, OwnAddressType.PUBLIC);

        ScanSettings scanSettings =
                new ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .setCallbackType(ScanSettings.CALLBACK_TYPE_FIRST_MATCH)
                        .build();

        ScanFilter scanFilter =
                new ScanFilter.Builder()
                        .setServiceUuid(ParcelUuid.fromString(TEST_UUID_STRING))
                        .build();

        ScanCallback mockScanCallback = mock(ScanCallback.class);

        mLeScanner.startScan(List.of(scanFilter), scanSettings, mockScanCallback);
        verify(mockScanCallback, after(TIMEOUT_SCANNING_MS).never()).onScanFailed(anyInt());
        mLeScanner.stopScan(mockScanCallback);
    }

    @Test
    public void startBleScan_withCallbackTypeMatchLostSilentlyFails() {
        advertiseWithBumble(TEST_UUID_STRING, OwnAddressType.PUBLIC);

        ScanSettings scanSettings =
                new ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .setCallbackType(ScanSettings.CALLBACK_TYPE_MATCH_LOST)
                        .build();

        ScanFilter scanFilter =
                new ScanFilter.Builder()
                        .setServiceUuid(ParcelUuid.fromString(TEST_UUID_STRING))
                        .build();

        ScanCallback mockScanCallback = mock(ScanCallback.class);

        mLeScanner.startScan(List.of(scanFilter), scanSettings, mockScanCallback);
        verify(mockScanCallback, after(TIMEOUT_SCANNING_MS).never()).onScanFailed(anyInt());
        mLeScanner.stopScan(mockScanCallback);
    }

    @Test
    public void startBleScan_withPendingIntentAndDynamicReceiverAndCallbackTypeAllMatches() {
        BroadcastReceiver mockReceiver = mock(BroadcastReceiver.class);
        IntentFilter intentFilter = new IntentFilter(ACTION_DYNAMIC_RECEIVER_SCAN_RESULT);
        mContext.registerReceiver(mockReceiver, intentFilter);

        advertiseWithBumble(TEST_UUID_STRING, OwnAddressType.PUBLIC);

        ScanSettings scanSettings =
                new ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                        .build();

        ScanFilter scanFilter =
                new ScanFilter.Builder()
                        .setServiceUuid(ParcelUuid.fromString(TEST_UUID_STRING))
                        .build();

        // NOTE: Intent.setClass() must not be called, or else scan results won't be received.
        Intent scanIntent = new Intent(ACTION_DYNAMIC_RECEIVER_SCAN_RESULT);
        PendingIntent pendingIntent =
                PendingIntent.getBroadcast(
                        mContext, 0, scanIntent, PendingIntent.FLAG_CANCEL_CURRENT);

        mLeScanner.startScan(List.of(scanFilter), scanSettings, pendingIntent);

        ArgumentCaptor<Intent> intent = ArgumentCaptor.forClass(Intent.class);
        verify(mockReceiver, timeout(TIMEOUT_SCANNING_MS)).onReceive(any(), intent.capture());

        mLeScanner.stopScan(pendingIntent);
        mContext.unregisterReceiver(mockReceiver);

        assertThat(intent.getValue().getAction()).isEqualTo(ACTION_DYNAMIC_RECEIVER_SCAN_RESULT);
        assertThat(intent.getValue().getIntExtra(BluetoothLeScanner.EXTRA_CALLBACK_TYPE, -1))
                .isEqualTo(ScanSettings.CALLBACK_TYPE_ALL_MATCHES);

        List<ScanResult> results =
                intent.getValue()
                        .getParcelableArrayListExtra(
                                BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT, ScanResult.class);
        assertThat(results).isNotEmpty();
        assertThat(results.get(0).getScanRecord().getServiceUuids()).isNotEmpty();
        assertThat(results.get(0).getScanRecord().getServiceUuids().get(0))
                .isEqualTo(ParcelUuid.fromString(TEST_UUID_STRING));
        assertThat(results.get(0).getScanRecord().getServiceUuids())
                .containsExactly(ParcelUuid.fromString(TEST_UUID_STRING));
    }

    @Test
    public void startBleScan_withPendingIntentAndStaticReceiverAndCallbackTypeAllMatches() {
        advertiseWithBumble(TEST_UUID_STRING, OwnAddressType.PUBLIC);

        ScanSettings scanSettings =
                new ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                        .build();

        ArrayList<ScanFilter> scanFilters = new ArrayList<>();
        ScanFilter scanFilter =
                new ScanFilter.Builder()
                        .setServiceUuid(ParcelUuid.fromString(TEST_UUID_STRING))
                        .build();
        scanFilters.add(scanFilter);

        PendingIntent pendingIntent =
                PendingIntentScanReceiver.newBroadcastPendingIntent(mContext, 0);

        mLeScanner.startScan(scanFilters, scanSettings, pendingIntent);
        List<ScanResult> results =
                PendingIntentScanReceiver.nextScanResult()
                        .completeOnTimeout(null, TIMEOUT_SCANNING_MS, TimeUnit.MILLISECONDS)
                        .join();
        mLeScanner.stopScan(pendingIntent);
        PendingIntentScanReceiver.resetNextScanResultFuture();

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).getScanRecord().getServiceUuids()).isNotEmpty();
        assertThat(results.get(0).getScanRecord().getServiceUuids())
                .containsExactly(ParcelUuid.fromString(TEST_UUID_STRING));
    }

    @Test
    public void startBleScan_oneTooManyScansFails() {
        final int maxNumScans = 32;
        advertiseWithBumble(TEST_UUID_STRING, OwnAddressType.PUBLIC);

        ScanSettings scanSettings =
                new ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                        .build();

        ScanFilter scanFilter =
                new ScanFilter.Builder()
                        .setServiceUuid(ParcelUuid.fromString(TEST_UUID_STRING))
                        .build();

        List<ScanCallback> scanCallbacks =
                Stream.generate(() -> mock(ScanCallback.class)).limit(maxNumScans).toList();
        for (ScanCallback mockScanCallback : scanCallbacks) {
            mLeScanner.startScan(List.of(scanFilter), scanSettings, mockScanCallback);
        }
        // This last scan should fail
        ScanCallback lastMockScanCallback = mock(ScanCallback.class);
        mLeScanner.startScan(List.of(scanFilter), scanSettings, lastMockScanCallback);

        // We expect an error only for the last scan, which was over the maximum active scans limit.
        for (ScanCallback mockScanCallback : scanCallbacks) {
            verify(mockScanCallback, timeout(TIMEOUT_SCANNING_MS).atLeast(1))
                    .onScanResult(eq(ScanSettings.CALLBACK_TYPE_ALL_MATCHES), any());
            verify(mockScanCallback, never()).onScanFailed(anyInt());
            mLeScanner.stopScan(mockScanCallback);
        }
        verify(lastMockScanCallback, timeout(TIMEOUT_SCANNING_MS))
                .onScanFailed(eq(ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED));
        mLeScanner.stopScan(lastMockScanCallback);
    }

    @Test
    public void startBleScan_withNonConnectablePublicAdvertisement() {
        AdvertiseRequest.Builder requestBuilder =
                AdvertiseRequest.newBuilder()
                        .setConnectable(false)
                        .setOwnAddressType(OwnAddressType.PUBLIC);
        advertiseWithBumble(requestBuilder);

        ScanFilter scanFilter =
                new ScanFilter.Builder()
                        .setDeviceAddress(mBumble.getRemoteDevice().getAddress())
                        .build();

        List<ScanResult> results =
                startScanning(scanFilter, ScanSettings.CALLBACK_TYPE_ALL_MATCHES);

        assertThat(results).isNotNull();
        assertThat(results.get(0).isConnectable()).isFalse();
        assertThat(results.get(1).isConnectable()).isFalse();
    }

    @Test
    public void startBleScan_withNonConnectableScannablePublicAdvertisement() {
        byte[] payload = {0x02, 0x03};
        // first 2 bytes are the manufacturer ID 0x00E0 (Google) in little endian
        byte[] manufacturerData = {(byte) 0xE0, 0x00, payload[0], payload[1]};
        HostProto.DataTypes.Builder scanResponse =
                HostProto.DataTypes.newBuilder()
                        .setManufacturerSpecificData(ByteString.copyFrom(manufacturerData));

        AdvertiseRequest.Builder requestBuilder =
                AdvertiseRequest.newBuilder()
                        .setConnectable(false)
                        .setOwnAddressType(OwnAddressType.PUBLIC)
                        .setScanResponseData(scanResponse);
        advertiseWithBumble(requestBuilder);

        ScanFilter scanFilter =
                new ScanFilter.Builder()
                        .setDeviceAddress(mBumble.getRemoteDevice().getAddress())
                        .build();

        List<ScanResult> results =
                startScanning(scanFilter, ScanSettings.CALLBACK_TYPE_ALL_MATCHES);

        assertThat(results).isNotNull();
        assertThat(results.get(0).isConnectable()).isFalse();
        assertThat(results.get(0).getScanRecord().getManufacturerSpecificData(0x00E0))
                .isEqualTo(payload);
    }

    private List<ScanResult> startScanning(ScanFilter scanFilter, int callbackType) {
        CompletableFuture<List<ScanResult>> future = new CompletableFuture<>();
        List<ScanResult> scanResults = new ArrayList<>();

        ScanSettings scanSettings =
                new ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .setCallbackType(callbackType)
                        .build();

        ScanCallback scanCallback =
                new ScanCallback() {
                    @Override
                    public void onScanResult(int callbackType, ScanResult result) {
                        Log.i(
                                TAG,
                                "onScanResult "
                                        + "address: "
                                        + result.getDevice().getAddress()
                                        + ", connectable: "
                                        + result.isConnectable()
                                        + ", callbackType: "
                                        + callbackType
                                        + ", service uuids: "
                                        + result.getScanRecord().getServiceUuids());

                        if (callbackType == ScanSettings.CALLBACK_TYPE_ALL_MATCHES) {
                            if (scanResults.size() < 2) {
                                scanResults.add(result);
                            } else {
                                future.complete(scanResults);
                            }
                        } else {
                            scanResults.add(result);
                            future.complete(scanResults);
                        }
                    }

                    @Override
                    public void onScanFailed(int errorCode) {
                        Log.i(TAG, "onScanFailed " + "errorCode: " + errorCode);
                        future.complete(null);
                    }
                };

        mLeScanner.startScan(List.of(scanFilter), scanSettings, scanCallback);

        List<ScanResult> result =
                future.completeOnTimeout(null, TIMEOUT_SCANNING_MS, TimeUnit.MILLISECONDS).join();

        mLeScanner.stopScan(scanCallback);

        return result;
    }

    private void advertiseWithBumble(String serviceUuid, OwnAddressType addressType) {
        AdvertiseRequest.Builder requestBuilder =
                AdvertiseRequest.newBuilder().setOwnAddressType(addressType);

        if (serviceUuid != null) {
            HostProto.DataTypes.Builder dataTypeBuilder = HostProto.DataTypes.newBuilder();
            dataTypeBuilder.addCompleteServiceClassUuids128(serviceUuid);
            requestBuilder.setData(dataTypeBuilder.build());
        }

        advertiseWithBumble(requestBuilder);
    }

    private void advertiseWithBumble(AdvertiseRequest.Builder requestBuilder) {
        // Bumble currently only supports legacy advertising.
        requestBuilder.setLegacy(true);
        // Collect and ignore responses.
        StreamObserverSpliterator<AdvertiseResponse> responseObserver =
                new StreamObserverSpliterator<>();
        mBumble.host().advertise(requestBuilder.build(), responseObserver);
    }
}
