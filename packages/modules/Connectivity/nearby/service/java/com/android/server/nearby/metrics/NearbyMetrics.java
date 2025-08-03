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

import android.nearby.NearbyDeviceParcelable;
import android.nearby.ScanRequest;
import android.os.WorkSource;

import com.android.server.nearby.proto.NearbyStatsLog;

/**
 * A class to collect and report Nearby metrics.
 */
public class NearbyMetrics {
    /**
     * Logs a scan started event.
     */
    public static void logScanStarted(int scanSessionId, ScanRequest scanRequest) {
        NearbyStatsLog.write(
                NearbyStatsLog.NEARBY_DEVICE_SCAN_STATE_CHANGED,
                getUid(scanRequest),
                scanSessionId,
                NearbyStatsLog
                        .NEARBY_DEVICE_SCAN_STATE_CHANGED__SCAN_STATE__NEARBY_SCAN_STATE_STARTED,
                scanRequest.getScanType(),
                0,
                0,
                "",
                "");
    }

    /**
     * Logs a scan stopped event.
     */
    public static void logScanStopped(int scanSessionId, ScanRequest scanRequest) {
        NearbyStatsLog.write(
                NearbyStatsLog.NEARBY_DEVICE_SCAN_STATE_CHANGED,
                getUid(scanRequest),
                scanSessionId,
                NearbyStatsLog
                        .NEARBY_DEVICE_SCAN_STATE_CHANGED__SCAN_STATE__NEARBY_SCAN_STATE_STOPPED,
                scanRequest.getScanType(),
                0,
                0,
                "",
                "");
    }

    /**
     * Logs a scan device discovered event.
     */
    public static void logScanDeviceDiscovered(int scanSessionId, ScanRequest scanRequest,
            NearbyDeviceParcelable nearbyDevice) {
        NearbyStatsLog.write(
                NearbyStatsLog.NEARBY_DEVICE_SCAN_STATE_CHANGED,
                getUid(scanRequest),
                scanSessionId,
                NearbyStatsLog
                        .NEARBY_DEVICE_SCAN_STATE_CHANGED__SCAN_STATE__NEARBY_SCAN_STATE_DISCOVERED,
                scanRequest.getScanType(),
                nearbyDevice.getMedium(),
                nearbyDevice.getRssi(),
                nearbyDevice.getFastPairModelId(),
                "");
    }

    private static int getUid(ScanRequest scanRequest) {
        WorkSource workSource = scanRequest.getWorkSource();
        return workSource.isEmpty() ? -1 : workSource.getUid(0);
    }
}
