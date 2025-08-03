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

package com.android.bluetooth.bass_client;

import android.bluetooth.le.ScanFilter;
import android.os.ParcelUuid;
import android.util.Log;

import java.util.Arrays;
import java.util.List;

/**
 * Bass Utility functions
 */
class BassUtils {
    private static final String TAG = "BassUtils";

    static boolean containUuid(List<ScanFilter> filters, ParcelUuid uuid) {
        for (ScanFilter filter: filters) {
            if (filter.getServiceUuid().equals(uuid)) {
                return true;
            }
        }
        return false;
    }

    static int parseBroadcastId(byte[] broadcastIdBytes) {
        int broadcastId;
        broadcastId = (0x00FF0000 & (broadcastIdBytes[2] << 16));
        broadcastId |= (0x0000FF00 & (broadcastIdBytes[1] << 8));
        broadcastId |= (0x000000FF & broadcastIdBytes[0]);
        return broadcastId;
    }

    static void log(String msg) {
        if (BassConstants.BASS_DBG) {
            Log.d(TAG, msg);
        }
    }

    static void printByteArray(byte[] array) {
        log("Entire byte Array as string: " + Arrays.toString(array));
    }

    static void reverse(byte[] address) {
        int len = address.length;
        for (int i = 0; i < len / 2; ++i) {
            byte b = address[i];
            address[i] = address[len - 1 - i];
            address[len - 1 - i] = b;
        }
    }
}
