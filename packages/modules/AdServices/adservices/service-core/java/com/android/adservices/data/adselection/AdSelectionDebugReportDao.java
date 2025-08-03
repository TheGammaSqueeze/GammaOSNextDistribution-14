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

package com.android.adservices.data.adselection;

import android.annotation.NonNull;

import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.time.Instant;
import java.util.List;

/** Data Access Object interface for access to the local AdSelectionDebugReport data storage. */
@Dao
public abstract class AdSelectionDebugReportDao {

    /**
     * Adds list of ad selection debug report entries into {@link
     * DBAdSelectionDebugReport.TABLE_NAME}
     *
     * @param adSelectionDebugReports is the List of DBAdSelectionDebugReport to add to the table
     *     ad_selection_debug_report.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract void persistAdSelectionDebugReporting(
            @NonNull List<DBAdSelectionDebugReport> adSelectionDebugReports);

    /**
     * Fetch all debug reports created before a specific timestamp.
     *
     * @param currentTime to compare against debug report creation time
     * @param limit to specify how many debug reports should be selected from DB.
     * @return All the debug reports
     */
    @Query(
            "SELECT * FROM ad_selection_debug_report WHERE creation_timestamp <= (:currentTime)"
                    + " LIMIT (:limit);")
    @Nullable
    public abstract List<DBAdSelectionDebugReport> getDebugReportsBeforeTime(
            @NonNull Instant currentTime, int limit);

    /**
     * deletes all debug reports before a specific timestamp.
     *
     * @param currentTime to compare against debug report creation time
     */
    @Query("DELETE FROM ad_selection_debug_report WHERE creation_timestamp <= (:currentTime)")
    public abstract void deleteDebugReportsBeforeTime(@NonNull Instant currentTime);
}
