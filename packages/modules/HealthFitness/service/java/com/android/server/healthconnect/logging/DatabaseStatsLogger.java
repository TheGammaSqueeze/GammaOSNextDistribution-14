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

package com.android.server.healthconnect.logging;

import android.annotation.NonNull;
import android.content.Context;
import android.health.HealthFitnessStatsLog;

import com.android.server.healthconnect.storage.datatypehelpers.DatabaseStatsCollector;

import java.util.Objects;

/**
 * Class to log Health Connect database stats.
 *
 * @hide
 */
class DatabaseStatsLogger {

    /** Write Health Connect database stats to statsd. */
    static void log(@NonNull Context context) {

        long numberOfInstantRecords = DatabaseStatsCollector.getNumberOfInstantRecordRows();
        long numberOfIntervalRecords = DatabaseStatsCollector.getNumberOfIntervalRecordRows();
        long numberOfSeriesRecords = DatabaseStatsCollector.getNumberOfSeriesRecordRows();
        long numberOfChangeLogs = DatabaseStatsCollector.getNumberOfChangeLogs();

        // If this condition is true then the user does not uses HC and we should not collect data.
        // This will reduce the load on logging service otherwise we will get daily data from
        // billions of Android devices.
        if (numberOfInstantRecords == 0
                && numberOfIntervalRecords == 0
                && numberOfSeriesRecords == 0
                && numberOfChangeLogs == 0) {
            return;
        }

        Objects.requireNonNull(context);
        HealthFitnessStatsLog.write(
                HealthFitnessStatsLog.HEALTH_CONNECT_STORAGE_STATS,
                DatabaseStatsCollector.getDatabaseSize(context),
                numberOfInstantRecords,
                numberOfIntervalRecords,
                numberOfSeriesRecords,
                numberOfChangeLogs);
    }
}
