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

package com.android.federatedcompute.services.data;

import android.provider.BaseColumns;

/** The contract class for training tasks. */
public final class FederatedTraningTaskContract {
    public static final String FEDERATED_TRAINING_TASKS_TABLE = "federated_training_tasks";

    private FederatedTraningTaskContract() {}

    /** Column name for the federated training task table. */
    public static final class FederatedTrainingTaskColumns implements BaseColumns {

        private FederatedTrainingTaskColumns() {}

        // The package name of the application this task was created from.
        // ODP most likely for time being. Must be non-empty.
        public static final String APP_PACKAGE_NAME = "app_package_name";

        // A unique, app-specified JobScheduler job ID for this task. Must be
        // non-zero.
        public static final String JOB_SCHEDULER_JOB_ID = "jobscheduler_job_id";

        // The package name and class name  of the application this task belongs to. Must be
        // non-empty.
        public static final String OWNER_ID = "owner_id";

        // The package cert digest of the application this task belongs to. Must be
        // non-empty.
        public static final String OWNER_ID_CERT_DIGEST = "owner_cert_digest";

        // An app-specified population name, to be provided to the federated learning
        // server during check in. Must be non-empty.
        public static final String POPULATION_NAME = "population_name";

        /** The remote federated compute server address that client provided. */
        public static final String SERVER_ADDRESS = "server_address";

        public static final String INTERVAL_OPTIONS = "interval_options";

        public static final String CONTEXT_DATA = "context_data";

        // The time the task was originally created.
        public static final String CREATION_TIME = "creation_time";

        // The time the task was last scheduled. Must always be set to a valid value.
        public static final String LAST_SCHEDULED_TIME = "last_scheduled_time";

        // The start time of the task's last run. This is population scoped and must
        // be reset if population name changes. Must always be either unset, or set to
        // a valid value.
        public static final String LAST_RUN_START_TIME = "last_run_start_time";

        // The end time of the task's last run. This is population scoped and must
        // be reset if population name changes. Must always be either unset, or set to
        // a valid value.
        public static final String LAST_RUN_END_TIME = "last_run_end_time";

        // The earliest time to run the task by. This is population scoped and must
        // be reset if population name changes. Must always be set to a valid value.
        public static final String EARLIEST_NEXT_RUN_TIME = "earliest_next_run_time";

        // The constraints that should apply to this task.
        public static final String CONSTRAINTS = "constraints";

        public static final String SCHEDULING_REASON = "scheduling_reason";

        public static final String RESCHEDULE_COUNT = "reschedule_count";
    }
}
