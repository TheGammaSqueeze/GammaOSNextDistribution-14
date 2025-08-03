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

package com.android.server.nearby.metrics;

import static android.nearby.ScanRequest.SCAN_MODE_BALANCED;
import static android.nearby.ScanRequest.SCAN_TYPE_FAST_PAIR;

import android.nearby.NearbyDeviceParcelable;
import android.nearby.PublicCredential;
import android.nearby.ScanRequest;
import android.os.WorkSource;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.server.nearby.proto.NearbyStatsLog;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

public class NearbyMetricsTest {
    private static final int SESSION_ID = 11111;
    private static final int WORK_SOURCE_UID = 2222;

    private static final String DEVICE_NAME = "testDevice";
    private static final int SCAN_MEDIUM = 1;
    private static final int RSSI = -60;
    private static final String FAST_PAIR_MODEL_ID = "1234";
    private static final String BLUETOOTH_ADDRESS = "00:11:22:33:FF:EE";
    private static final byte[] SCAN_DATA = new byte[] {1, 2, 3, 4};
    private static final PublicCredential PUBLIC_CREDENTIAL =
            new PublicCredential.Builder(
                            new byte[] {1},
                            new byte[] {2},
                            new byte[] {3},
                            new byte[] {4},
                            new byte[] {5})
                    .build();

    private final WorkSource mWorkSource = new WorkSource(WORK_SOURCE_UID);
    private final WorkSource mEmptyWorkSource = new WorkSource();

    private final ScanRequest.Builder mScanRequestBuilder =
            new ScanRequest.Builder()
                    .setScanMode(SCAN_MODE_BALANCED)
                    .setScanType(SCAN_TYPE_FAST_PAIR);
    private final ScanRequest mScanRequest = mScanRequestBuilder.setWorkSource(mWorkSource).build();
    private final ScanRequest mScanRequestWithEmptyWorkSource =
            mScanRequestBuilder.setWorkSource(mEmptyWorkSource).build();

    private final NearbyDeviceParcelable mNearbyDevice =
            new NearbyDeviceParcelable.Builder()
                    .setName(DEVICE_NAME)
                    .setMedium(SCAN_MEDIUM)
                    .setTxPower(1)
                    .setRssi(RSSI)
                    .setAction(1)
                    .setPublicCredential(PUBLIC_CREDENTIAL)
                    .setFastPairModelId(FAST_PAIR_MODEL_ID)
                    .setBluetoothAddress(BLUETOOTH_ADDRESS)
                    .setData(SCAN_DATA)
                    .build();

    private MockitoSession mSession;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mSession =
                ExtendedMockito.mockitoSession()
                        .strictness(Strictness.LENIENT)
                        .mockStatic(NearbyStatsLog.class)
                        .startMocking();
    }

    @After
    public void tearDown() {
        mSession.finishMocking();
    }

    @Test
    public void testLogScanStart() {
        NearbyMetrics.logScanStarted(SESSION_ID, mScanRequest);
        ExtendedMockito.verify(() -> NearbyStatsLog.write(
                NearbyStatsLog.NEARBY_DEVICE_SCAN_STATE_CHANGED,
                WORK_SOURCE_UID,
                SESSION_ID,
                NearbyStatsLog
                        .NEARBY_DEVICE_SCAN_STATE_CHANGED__SCAN_STATE__NEARBY_SCAN_STATE_STARTED,
                SCAN_TYPE_FAST_PAIR,
                0,
                0,
                "",
                ""));
    }

    @Test
    public void testLogScanStart_emptyWorkSource() {
        NearbyMetrics.logScanStarted(SESSION_ID, mScanRequestWithEmptyWorkSource);
        ExtendedMockito.verify(() -> NearbyStatsLog.write(
                NearbyStatsLog.NEARBY_DEVICE_SCAN_STATE_CHANGED,
                -1,
                SESSION_ID,
                NearbyStatsLog
                        .NEARBY_DEVICE_SCAN_STATE_CHANGED__SCAN_STATE__NEARBY_SCAN_STATE_STARTED,
                SCAN_TYPE_FAST_PAIR,
                0,
                0,
                "",
                ""));
    }

    @Test
    public void testLogScanStopped() {
        NearbyMetrics.logScanStopped(SESSION_ID, mScanRequest);
        ExtendedMockito.verify(() -> NearbyStatsLog.write(
                NearbyStatsLog.NEARBY_DEVICE_SCAN_STATE_CHANGED,
                WORK_SOURCE_UID,
                SESSION_ID,
                NearbyStatsLog
                        .NEARBY_DEVICE_SCAN_STATE_CHANGED__SCAN_STATE__NEARBY_SCAN_STATE_STOPPED,
                SCAN_TYPE_FAST_PAIR,
                0,
                0,
                "",
                ""));
    }

    @Test
    public void testLogScanStopped_emptyWorkSource() {
        NearbyMetrics.logScanStopped(SESSION_ID, mScanRequestWithEmptyWorkSource);
        ExtendedMockito.verify(() -> NearbyStatsLog.write(
                NearbyStatsLog.NEARBY_DEVICE_SCAN_STATE_CHANGED,
                -1,
                SESSION_ID,
                NearbyStatsLog
                        .NEARBY_DEVICE_SCAN_STATE_CHANGED__SCAN_STATE__NEARBY_SCAN_STATE_STOPPED,
                SCAN_TYPE_FAST_PAIR,
                0,
                0,
                "",
                ""));
    }

    @Test
    public void testLogScanDeviceDiscovered() {
        NearbyMetrics.logScanDeviceDiscovered(SESSION_ID, mScanRequest, mNearbyDevice);
        ExtendedMockito.verify(() -> NearbyStatsLog.write(
                NearbyStatsLog.NEARBY_DEVICE_SCAN_STATE_CHANGED,
                WORK_SOURCE_UID,
                SESSION_ID,
                NearbyStatsLog
                        .NEARBY_DEVICE_SCAN_STATE_CHANGED__SCAN_STATE__NEARBY_SCAN_STATE_DISCOVERED,
                SCAN_TYPE_FAST_PAIR,
                SCAN_MEDIUM,
                RSSI,
                FAST_PAIR_MODEL_ID,
                ""));
    }

    @Test
    public void testLogScanDeviceDiscovered_emptyWorkSource() {
        NearbyMetrics.logScanDeviceDiscovered(
                SESSION_ID, mScanRequestWithEmptyWorkSource, mNearbyDevice);
        ExtendedMockito.verify(() -> NearbyStatsLog.write(
                NearbyStatsLog.NEARBY_DEVICE_SCAN_STATE_CHANGED,
                -1,
                SESSION_ID,
                NearbyStatsLog
                        .NEARBY_DEVICE_SCAN_STATE_CHANGED__SCAN_STATE__NEARBY_SCAN_STATE_DISCOVERED,
                SCAN_TYPE_FAST_PAIR,
                SCAN_MEDIUM,
                RSSI,
                FAST_PAIR_MODEL_ID,
                ""));
    }
}
