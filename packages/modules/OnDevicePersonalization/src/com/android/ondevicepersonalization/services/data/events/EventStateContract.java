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

package com.android.ondevicepersonalization.services.data.events;

import android.provider.BaseColumns;

/** Contract for the events state table. Defines the table. */
public class EventStateContract {
    private EventStateContract() {
    }

    /**
     * Table containing event state. Each row in the table
     * represents the state of events and queries for a given task.
     */
    public static class EventStateEntry implements BaseColumns {
        public static final String TABLE_NAME = "event_state";

        /** Unique identifier of the task for processing this event */
        public static final String TASK_IDENTIFIER = "taskIdentifier";

        /** Name of the service package for this event */
        public static final String SERVICE_NAME = "serviceName";

        /** Token representing the event state. */
        public static final String TOKEN = "token";


        public static final String CREATE_TABLE_STATEMENT =
                "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " ("
                    + TASK_IDENTIFIER + " TEXT NOT NULL,"
                    + SERVICE_NAME + " TEXT NOT NULL,"
                    + TOKEN + " BLOB NOT NULL,"
                    + "UNIQUE(" + TASK_IDENTIFIER + ","
                        + SERVICE_NAME + "))";

        private EventStateEntry() {}
    }
}
