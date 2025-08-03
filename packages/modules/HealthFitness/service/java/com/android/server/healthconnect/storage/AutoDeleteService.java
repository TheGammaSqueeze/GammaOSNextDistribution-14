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

package com.android.server.healthconnect.storage;

import android.annotation.NonNull;
import android.content.Context;
import android.util.Slog;

import com.android.server.healthconnect.storage.datatypehelpers.AccessLogsHelper;
import com.android.server.healthconnect.storage.datatypehelpers.ActivityDateHelper;
import com.android.server.healthconnect.storage.datatypehelpers.AppInfoHelper;
import com.android.server.healthconnect.storage.datatypehelpers.ChangeLogsHelper;
import com.android.server.healthconnect.storage.datatypehelpers.ChangeLogsRequestHelper;
import com.android.server.healthconnect.storage.datatypehelpers.HealthDataCategoryPriorityHelper;
import com.android.server.healthconnect.storage.datatypehelpers.PreferenceHelper;
import com.android.server.healthconnect.storage.request.DeleteTableRequest;
import com.android.server.healthconnect.storage.utils.RecordHelperProvider;

import java.util.ArrayList;
import java.util.List;

/**
 * A service that is run periodically to handle deletion of stale entries in HC DB.
 *
 * @hide
 */
public class AutoDeleteService {
    private static final String AUTO_DELETE_DURATION_RECORDS_KEY =
            "auto_delete_duration_records_key";
    private static final String TAG = "HealthConnectAutoDelete";

    /** Gets auto delete period for automatically deleting record entries */
    public static int getRecordRetentionPeriodInDays() {
        String result =
                PreferenceHelper.getInstance().getPreference(AUTO_DELETE_DURATION_RECORDS_KEY);

        if (result == null) return 0;
        return Integer.parseInt(result);
    }

    /** Sets auto delete period for automatically deleting record entries */
    public static void setRecordRetentionPeriodInDays(int days) {
        PreferenceHelper.getInstance()
                .insertOrReplacePreference(AUTO_DELETE_DURATION_RECORDS_KEY, String.valueOf(days));
    }

    /** Starts the Auto Deletion process. */
    public static void startAutoDelete(@NonNull Context context) {
        try {
            // Only do transactional operations here - as this job might get cancelled for several
            // reasons, such as: User switch, low battery etc.
            deleteStaleRecordEntries();
            deleteStaleChangeLogEntries();
            deleteStaleAccessLogEntries();
            // Update the recordTypesUsed by packages if required after the deletion of records.
            AppInfoHelper.getInstance().syncAppInfoRecordTypesUsed();
            // Re-sync activity dates table
            ActivityDateHelper.getInstance().reSyncForAllRecords();
            // Sync health data priority list table
            HealthDataCategoryPriorityHelper.getInstance().reSyncHealthDataPriorityTable(context);
        } catch (Exception e) {
            Slog.e(TAG, "Auto delete run failed", e);
            // Don't rethrow as that will crash system_server
        }
    }

    private static void deleteStaleRecordEntries() {
        String recordAutoDeletePeriodString =
                PreferenceHelper.getInstance().getPreference(AUTO_DELETE_DURATION_RECORDS_KEY);
        int recordAutoDeletePeriod =
                recordAutoDeletePeriodString == null
                        ? 0
                        : Integer.parseInt(recordAutoDeletePeriodString);
        if (recordAutoDeletePeriod != 0) {
            // 0 represents that no period is set,to delete only if not 0 else don't do anything
            List<DeleteTableRequest> deleteTableRequests = new ArrayList<>();
            RecordHelperProvider.getInstance()
                    .getRecordHelpers()
                    .values()
                    .forEach(
                            (recordHelper) -> {
                                DeleteTableRequest request =
                                        recordHelper.getDeleteRequestForAutoDelete(
                                                recordAutoDeletePeriod);
                                deleteTableRequests.add(request);
                            });
            try {
                TransactionManager.getInitialisedInstance()
                        .deleteWithoutChangeLogs(deleteTableRequests);
            } catch (Exception exception) {
                Slog.e(TAG, "Auto delete for records failed", exception);
                // Don't rethrow as that will crash system_server
            }
        }
    }

    private static void deleteStaleChangeLogEntries() {
        try {
            TransactionManager.getInitialisedInstance()
                    .deleteWithoutChangeLogs(
                            List.of(
                                    ChangeLogsHelper.getInstance().getDeleteRequestForAutoDelete(),
                                    ChangeLogsRequestHelper.getInstance()
                                            .getDeleteRequestForAutoDelete()));
        } catch (Exception exception) {
            Slog.e(TAG, "Auto delete for Change logs failed", exception);
            // Don't rethrow as that will crash system_server
        }
    }

    private static void deleteStaleAccessLogEntries() {
        try {
            TransactionManager.getInitialisedInstance()
                    .deleteWithoutChangeLogs(
                            List.of(
                                    AccessLogsHelper.getInstance()
                                            .getDeleteRequestForAutoDelete()));
        } catch (Exception exception) {
            Slog.e(TAG, "Auto delete for Access logs failed", exception);
            // Don't rethrow as that will crash system_server
        }
    }
}
