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
package com.android.bluetooth.content_profiles;

import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothProtoEnums;
import android.os.SystemClock;
import android.util.Log;

import com.android.bluetooth.BluetoothStatsLog;
import com.android.internal.annotations.VisibleForTesting;

/**
 * Utility method to report exceptions and error/warn logs in content profiles.
 */
public class ContentProfileErrorReportUtils {
    private static final String TAG = ContentProfileErrorReportUtils.class.getSimpleName();

    /* Minimum period between two error reports */
    @VisibleForTesting static final long MIN_PERIOD_BETWEEN_TWO_ERROR_REPORTS_MILLIS = 1_000;

    @VisibleForTesting static long sLastReportTime = 0;

    /**
     * Report error by writing BluetoothStatsLog.BLUETOOTH_CONTENT_PROFILE_ERROR_REPORTED atom. A
     * report will be skipped if not enough time has passed from the last report.
     *
     * @param profile One of: {@link BluetoothProfile#PBAP}, {@link BluetoothProfile#MAP}, {@link
     *     BluetoothProfile#OPP}
     * @param fileNameEnum File name enum which is declared in {@link BluetoothProtoEnums}
     * @param type One of the following: {@link
     *     BluetoothStatsLog#BLUETOOTH_CONTENT_PROFILE_ERROR_REPORTED__TYPE__EXCEPTION}, {@link
     *     BluetoothStatsLog#BLUETOOTH_CONTENT_PROFILE_ERROR_REPORTED__TYPE__LOG_ERROR}, {@link
     *     BluetoothStatsLog#BLUETOOTH_CONTENT_PROFILE_ERROR_REPORTED__TYPE__LOG_WARN}
     * @param tag A tag which represents the code location of this error. The values are managed per
     *     each java file.
     * @return true if successfully wrote the error, false otherwise
     */
    public static synchronized boolean report(int profile, int fileNameEnum, int type, int tag) {
        if (isTooFrequentReport()) {
            Log.w(
                    TAG,
                    "Skipping reporting this error to prevent flooding."
                            + " fileNameEnum="
                            + fileNameEnum
                            + ", tag="
                            + tag);
            return false;
        }

        BluetoothStatsLog.write(
                BluetoothStatsLog.BLUETOOTH_CONTENT_PROFILE_ERROR_REPORTED,
                profile,
                fileNameEnum,
                type,
                tag);
        sLastReportTime = SystemClock.uptimeMillis();
        return true;
    }

    private static boolean isTooFrequentReport() {
        return SystemClock.uptimeMillis() - sLastReportTime
                < MIN_PERIOD_BETWEEN_TWO_ERROR_REPORTS_MILLIS;
    }
}
