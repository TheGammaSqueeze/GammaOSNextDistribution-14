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

package com.android.server.healthconnect.storage.datatypehelpers;

import android.content.Context;

import com.android.server.healthconnect.storage.TransactionManager;
import com.android.server.healthconnect.storage.utils.RecordHelperProvider;

/**
 * Helper class to collect Health Connect database stats for logging.
 *
 * @hide
 */
public class DatabaseStatsCollector {

    /** Get the size of Health Connect database. */
    public static long getDatabaseSize(Context context) {
        final TransactionManager transactionManager = TransactionManager.getInitialisedInstance();
        return transactionManager.getDatabaseSize(context);
    }

    /** Get the number of interval record entries in Health Connect database. */
    public static long getNumberOfIntervalRecordRows() {
        final TransactionManager transactionManager = TransactionManager.getInitialisedInstance();
        long count = 0L;
        for (RecordHelper<?> recordHelper :
                RecordHelperProvider.getInstance().getRecordHelpers().values()) {
            if (recordHelper instanceof IntervalRecordHelper
                    && !(recordHelper instanceof SeriesRecordHelper)) {
                count +=
                        transactionManager.getNumberOfEntriesInTheTable(
                                recordHelper.getMainTableName());
            }
        }
        return count;
    }

    /** Get the number of series record entries in Health Connect database. */
    public static long getNumberOfSeriesRecordRows() {
        final TransactionManager transactionManager = TransactionManager.getInitialisedInstance();
        long count = 0L;
        for (RecordHelper<?> recordHelper :
                RecordHelperProvider.getInstance().getRecordHelpers().values()) {
            if (recordHelper instanceof SeriesRecordHelper) {
                count +=
                        transactionManager.getNumberOfEntriesInTheTable(
                                recordHelper.getMainTableName());
            }
        }
        return count;
    }

    /** Get the number of instant record entries in Health Connect database. */
    public static long getNumberOfInstantRecordRows() {
        final TransactionManager transactionManager = TransactionManager.getInitialisedInstance();
        long count = 0L;
        for (RecordHelper<?> recordHelper :
                RecordHelperProvider.getInstance().getRecordHelpers().values()) {
            if (recordHelper instanceof InstantRecordHelper) {
                count +=
                        transactionManager.getNumberOfEntriesInTheTable(
                                recordHelper.getMainTableName());
            }
        }
        return count;
    }

    /** Get the number of change log entries in Health Connect database. */
    public static long getNumberOfChangeLogs() {
        return TransactionManager.getInitialisedInstance()
                .getNumberOfEntriesInTheTable(ChangeLogsHelper.TABLE_NAME);
    }
}
