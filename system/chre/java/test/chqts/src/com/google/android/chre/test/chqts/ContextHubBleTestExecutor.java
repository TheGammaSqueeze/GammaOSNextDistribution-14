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

package com.google.android.chre.test.chqts;

import static com.google.common.truth.Truth.assertThat;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertisingSet;
import android.bluetooth.le.AdvertisingSetCallback;
import android.bluetooth.le.AdvertisingSetParameters;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.hardware.location.NanoAppBinary;
import android.os.ParcelUuid;

import com.google.android.utils.chre.ChreApiTestUtil;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;

import org.junit.Assert;

import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.chre.rpc.proto.ChreApiTest;

/**
 * A class that contains common BLE functionality using the CHRE API Test nanoapp.
 */
public class ContextHubBleTestExecutor extends ContextHubChreApiTestExecutor {
    private static final String TAG = "ContextHubBleTestExecutor";

    /**
     * The Base UUID is used for calculating 128-bit UUIDs from "short UUIDs" (16- and 32-bit).
     *
     * @see {https://www.bluetooth.com/specifications/assigned-numbers/service-discovery}
     */
    public static final UUID BASE_UUID = UUID.fromString("00000000-0000-1000-8000-00805F9B34FB");

    /**
     * Used for UUID conversion. This is the bit index where the 16-bit UUID is inserted.
     */
    private static final int BIT_INDEX_OF_16_BIT_UUID = 32;

    /**
     * The ID for the Google Eddystone beacon.
     */
    public static final UUID EDDYSTONE_UUID = to128BitUuid((short) 0xFEAA);

    /**
     * The delay to report results in milliseconds.
     */
    private static final int REPORT_DELAY_MS = 0;

    /**
     * The RSSI threshold for the BLE scan filter.
     */
    private static final int RSSI_THRESHOLD = -128;

    /**
     * The advertisement type for service data.
     */
    public static final int CHRE_BLE_AD_TYPE_SERVICE_DATA_WITH_UUID_16 = 0x16;

    /**
     * The BLE advertisement event ID.
     */
    public static final int CHRE_EVENT_BLE_ADVERTISEMENT = 0x0350 + 1;

    /**
     * CHRE BLE capabilities and filter capabilities.
     */
    public static final int CHRE_BLE_CAPABILITIES_SCAN = 1 << 0;
    public static final int CHRE_BLE_FILTER_CAPABILITIES_SERVICE_DATA = 1 << 7;

    private BluetoothAdapter mBluetoothAdapter = null;
    private BluetoothLeAdvertiser mBluetoothLeAdvertiser = null;
    private BluetoothLeScanner mBluetoothLeScanner = null;

    /**
     * The signal for advertising start.
     */
    private CountDownLatch mAdvertisingStartLatch = new CountDownLatch(1);

    /**
     * The signal for advertising stop.
     */
    private CountDownLatch mAdvertisingStopLatch = new CountDownLatch(1);

    /**
     * Indicates whether the device is advertising or not.
     */
    private AtomicBoolean mIsAdvertising = new AtomicBoolean();

    /**
     * Callback for BLE scans.
     */
    private final ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            // do nothing
        }

        @Override
        public void onScanFailed(int errorCode) {
            Assert.fail("Failed to start a BLE scan on the host");
        }

        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            // do nothing
        }
    };

    /**
     * The BLE advertising callback. It updates the mIsAdvertising state
     * and notifies any waiting threads that the state of advertising
     * has changed.
     */
    private AdvertisingSetCallback mAdvertisingSetCallback = new AdvertisingSetCallback() {
        @Override
        public void onAdvertisingSetStarted(AdvertisingSet advertisingSet,
                int txPower, int status) {
            mIsAdvertising.set(true);
            mAdvertisingStartLatch.countDown();
        }

        @Override
        public void onAdvertisingDataSet(AdvertisingSet advertisingSet, int status) {
            // do nothing
        }

        @Override
        public void onScanResponseDataSet(AdvertisingSet advertisingSet, int status) {
            // do nothing
        }

        @Override
        public void onAdvertisingSetStopped(AdvertisingSet advertisingSet) {
            mIsAdvertising.set(false);
            mAdvertisingStopLatch.countDown();
        }
    };

    public ContextHubBleTestExecutor(NanoAppBinary nanoapp) {
        super(nanoapp);

        BluetoothManager bluetoothManager = mContext.getSystemService(BluetoothManager.class);
        assertThat(bluetoothManager).isNotNull();
        mBluetoothAdapter = bluetoothManager.getAdapter();
        if (mBluetoothAdapter != null) {
            mBluetoothLeAdvertiser = mBluetoothAdapter.getBluetoothLeAdvertiser();
            mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
        }
    }

    /**
     * Generates a BLE scan filter that filters only for the known Google beacons:
     * Google Eddystone and Nearby Fastpair.
     *
     * @param useEddystone          if true, filter for Google Eddystone.
     * @param useNearbyFastpair     if true, filter for Nearby Fastpair.
     */
    public static ChreApiTest.ChreBleScanFilter getDefaultScanFilter(boolean useEddystone,
            boolean useNearbyFastpair) {
        ChreApiTest.ChreBleScanFilter.Builder builder =
                ChreApiTest.ChreBleScanFilter.newBuilder()
                        .setRssiThreshold(RSSI_THRESHOLD);

        if (useEddystone) {
            ChreApiTest.ChreBleGenericFilter eddystoneFilter =
                    ChreApiTest.ChreBleGenericFilter.newBuilder()
                            .setType(CHRE_BLE_AD_TYPE_SERVICE_DATA_WITH_UUID_16)
                            .setLength(2)
                            .setData(ByteString.copyFrom(HexFormat.of().parseHex("AAFE")))
                            .setMask(ByteString.copyFrom(HexFormat.of().parseHex("FFFF")))
                            .build();
            builder = builder.addScanFilters(eddystoneFilter);
        }

        if (useNearbyFastpair) {
            ChreApiTest.ChreBleGenericFilter nearbyFastpairFilter =
                    ChreApiTest.ChreBleGenericFilter.newBuilder()
                            .setType(CHRE_BLE_AD_TYPE_SERVICE_DATA_WITH_UUID_16)
                            .setLength(2)
                            .setData(ByteString.copyFrom(HexFormat.of().parseHex("2CFE")))
                            .setMask(ByteString.copyFrom(HexFormat.of().parseHex("FFFF")))
                            .build();
            builder = builder.addScanFilters(nearbyFastpairFilter);
        }

        return builder.build();
    }

    /**
     * Generates a BLE scan filter that filters only for the known Google beacons:
     * Google Eddystone and Nearby Fastpair.
     */
    public static ChreApiTest.ChreBleScanFilter getDefaultScanFilter() {
        return getDefaultScanFilter(true /* useEddystone */, true /* useNearbyFastpair */);
    }

    /**
     * Generates a BLE scan filter that filters only for Google Eddystone.
     */
    public static ChreApiTest.ChreBleScanFilter getGoogleEddystoneScanFilter() {
        return getDefaultScanFilter(true /* useEddystone */, false /* useNearbyFastpair */);
    }

    /**
     * Generates a BLE scan filter that filters only for the known Google beacons:
     * Google Eddystone and Nearby Fastpair. We specify the filter data in (little-endian) LE
     * here as the CHRE code will take BE input and transform it to LE.
     */
    public static List<ScanFilter> getDefaultScanFilterHost() {
        assertThat(CHRE_BLE_AD_TYPE_SERVICE_DATA_WITH_UUID_16)
                .isEqualTo(ScanRecord.DATA_TYPE_SERVICE_DATA_16_BIT);

        ScanFilter scanFilter = new ScanFilter.Builder()
                .setAdvertisingDataTypeWithData(
                        ScanRecord.DATA_TYPE_SERVICE_DATA_16_BIT,
                        ByteString.copyFrom(HexFormat.of().parseHex("AAFE")).toByteArray(),
                        ByteString.copyFrom(HexFormat.of().parseHex("FFFF")).toByteArray())
                .build();
        ScanFilter scanFilter2 = new ScanFilter.Builder()
                .setAdvertisingDataTypeWithData(
                        ScanRecord.DATA_TYPE_SERVICE_DATA_16_BIT,
                        ByteString.copyFrom(HexFormat.of().parseHex("2CFE")).toByteArray(),
                        ByteString.copyFrom(HexFormat.of().parseHex("FFFF")).toByteArray())
                .build();

        return ImmutableList.of(scanFilter, scanFilter2);
    }

    /**
     * Starts a BLE scan and asserts it was started successfully in a synchronous manner.
     * This waits for the event to be received and returns the status in the event.
     *
     * @param scanFilter                The scan filter.
     */
    public void chreBleStartScanSync(ChreApiTest.ChreBleScanFilter scanFilter) throws Exception {
        ChreApiTest.ChreBleStartScanAsyncInput.Builder inputBuilder =
                ChreApiTest.ChreBleStartScanAsyncInput.newBuilder()
                        .setMode(ChreApiTest.ChreBleScanMode.CHRE_BLE_SCAN_MODE_FOREGROUND)
                        .setReportDelayMs(REPORT_DELAY_MS)
                        .setHasFilter(scanFilter != null);
        if (scanFilter != null) {
            inputBuilder.setFilter(scanFilter);
        }

        ChreApiTestUtil util = new ChreApiTestUtil();
        List<ChreApiTest.GeneralSyncMessage> response =
                util.callServerStreamingRpcMethodSync(getRpcClient(),
                        "chre.rpc.ChreApiTestService.ChreBleStartScanSync",
                        inputBuilder.build());
        assertThat(response).isNotEmpty();
        for (ChreApiTest.GeneralSyncMessage status: response) {
            assertThat(status.getStatus()).isTrue();
        }
    }

    /**
     * Stops a BLE scan and asserts it was started successfully in a synchronous manner.
     * This waits for the event to be received and returns the status in the event.
     */
    public void chreBleStopScanSync() throws Exception {
        ChreApiTestUtil util = new ChreApiTestUtil();
        List<ChreApiTest.GeneralSyncMessage> response =
                util.callServerStreamingRpcMethodSync(getRpcClient(),
                        "chre.rpc.ChreApiTestService.ChreBleStopScanSync");
        assertThat(response).isNotEmpty();
        for (ChreApiTest.GeneralSyncMessage status: response) {
            assertThat(status.getStatus()).isTrue();
        }
    }

    /**
     * Returns true if the required BLE capabilities and filter capabilities exist,
     * otherwise returns false.
     */
    public boolean doesNecessaryBleCapabilitiesExist() throws Exception {
        if (mBluetoothLeScanner == null) {
            return false;
        }

        ChreApiTest.Capabilities capabilitiesResponse =
                ChreApiTestUtil.callUnaryRpcMethodSync(getRpcClient(),
                        "chre.rpc.ChreApiTestService.ChreBleGetCapabilities");
        int capabilities = capabilitiesResponse.getCapabilities();
        if ((capabilities & CHRE_BLE_CAPABILITIES_SCAN) != 0) {
            ChreApiTest.Capabilities filterCapabilitiesResponse =
                    ChreApiTestUtil.callUnaryRpcMethodSync(getRpcClient(),
                            "chre.rpc.ChreApiTestService.ChreBleGetFilterCapabilities");
            int filterCapabilities = filterCapabilitiesResponse.getCapabilities();
            return (filterCapabilities & CHRE_BLE_FILTER_CAPABILITIES_SERVICE_DATA) != 0;
        }
        return false;
    }

    /**
     * Returns true if the required BLE capabilities and filter capabilities exist
     * on the AP, otherwise returns false.
     */
    public boolean doesNecessaryBleCapabilitiesExistOnTheAP() throws Exception {
        return mBluetoothLeAdvertiser != null;
    }

    /**
     * Starts a BLE scan on the host side with known Google beacon filters.
     */
    public void startBleScanOnHost() {
        ScanSettings scanSettings = new ScanSettings.Builder()
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();
        mBluetoothLeScanner.startScan(getDefaultScanFilterHost(),
                scanSettings, mScanCallback);
    }

    /**
     * Stops a BLE scan on the host side.
     */
    public void stopBleScanOnHost() {
        mBluetoothLeScanner.stopScan(mScanCallback);
    }

    /**
     * Starts broadcasting the Google Eddystone beacon from the AP.
     */
    public void startBleAdvertisingGoogleEddystone() throws InterruptedException {
        if (mIsAdvertising.get()) {
            return;
        }

        AdvertisingSetParameters parameters = (new AdvertisingSetParameters.Builder())
                .setLegacyMode(true)
                .setConnectable(false)
                .setInterval(AdvertisingSetParameters.INTERVAL_HIGH)
                .setTxPowerLevel(AdvertisingSetParameters.TX_POWER_HIGH)
                .build();

        AdvertiseData data = new AdvertiseData.Builder()
                .addServiceData(new ParcelUuid(EDDYSTONE_UUID), new byte[] {0})
                .setIncludeDeviceName(false)
                .setIncludeTxPowerLevel(true)
                .build();

        mBluetoothLeAdvertiser.startAdvertisingSet(parameters, data,
                null, null, null, mAdvertisingSetCallback);
        mAdvertisingStartLatch.await();
        assertThat(mIsAdvertising.get()).isTrue();
    }

    /**
     * Stops advertising Google Eddystone from the AP.
     */
    public void stopBleAdvertisingGoogleEddystone() throws InterruptedException {
        if (!mIsAdvertising.get()) {
            return;
        }

        mBluetoothLeAdvertiser.stopAdvertisingSet(mAdvertisingSetCallback);
        mAdvertisingStopLatch.await();
        assertThat(mIsAdvertising.get()).isFalse();
    }

    /**
     * Converts short UUID to 128 bit UUID.
     */
    private static UUID to128BitUuid(short shortUuid) {
        return new UUID(
                ((shortUuid & 0xFFFFL) << BIT_INDEX_OF_16_BIT_UUID)
                        | BASE_UUID.getMostSignificantBits(),
                        BASE_UUID.getLeastSignificantBits());
    }
}
