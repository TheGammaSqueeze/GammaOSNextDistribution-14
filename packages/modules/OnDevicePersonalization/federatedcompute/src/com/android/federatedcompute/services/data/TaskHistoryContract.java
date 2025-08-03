/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.federatedcompute.services.data;

import android.provider.BaseColumns;

/** Contract for the task history table. Defines the table. */
public class TaskHistoryContract {
    private TaskHistoryContract() {}

    /**
     * Table containing federated compute task history. Each row in the table represents the
     * contribution information for a given task.
     */
    public static class TaskHistoryEntry implements BaseColumns {
        public static final String TABLE_NAME = "task_history";
        public static final String JOB_ID = "job_id";
        public static final String POPULATION_NAME = "population_name";
        public static final String TASK_ID = "task_id";
        // The timestamp when device uploads training result to GCS bucket done.
        public static final String CONTRIBUTION_TIME = "contribution_time";
        // The round number that device contribute training result successfully. The round number is
        // returned by federated compute server when assigning task to device.
        public static final String CONTRIBUTION_ROUND = "contribution_round";
        // The total number that device has participate in the training per task per population.
        public static final String TOTAL_PARTICIPATION = "total_participation";
        public static final String CREATE_TASK_HISTORY_TABLE_STATEMENT =
                "CREATE TABLE IF NOT EXISTS "
                        + TABLE_NAME
                        + " ("
                        + TaskHistoryEntry._ID
                        + " INTEGER PRIMARY KEY, "
                        + JOB_ID
                        + " INTEGER NOT NULL,"
                        + POPULATION_NAME
                        + " TEXT NOT NULL,"
                        + TASK_ID
                        + " TEXT NOT NULL,"
                        + CONTRIBUTION_TIME
                        + " INTEGER NOT NULL,"
                        + CONTRIBUTION_ROUND
                        + " INTEGER NOT NULL,"
                        + TOTAL_PARTICIPATION
                        + " INTEGER NOT NULL)";
    }
}
