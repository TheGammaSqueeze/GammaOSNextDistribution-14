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

package com.android.ondevicepersonalization.services.data.events;

import android.provider.BaseColumns;

/** Contract for the events table. Defines the table. */
public class EventsContract {
    private EventsContract() {
    }

    /**
     * Table containing events. Each row in the table
     * represents a single event.
     */
    public static class EventsEntry implements BaseColumns {
        public static final String TABLE_NAME = "events";

        /** The id of the event. */
        public static final String EVENT_ID = "eventId";

        /** The id of the query. */
        public static final String QUERY_ID = "queryId";

        /** Index of the request log entry for this event */
        public static final String ROW_INDEX = "rowIndex";

        /** Name of the service that owns this event */
        public static final String SERVICE_NAME = "serviceName";

        /** Integer enum defining the type of event */
        public static final String TYPE = "type";

        /** Time of the event in milliseconds. */
        public static final String TIME_MILLIS = "timeMillis";

        /** Blob representing the event. */
        public static final String EVENT_DATA = "eventData";

        public static final String CREATE_TABLE_STATEMENT =
                "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " ("
                    + EVENT_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + QUERY_ID + " INTEGER NOT NULL,"
                    + ROW_INDEX + " INTEGER NOT NULL,"
                    + SERVICE_NAME + " TEXT NOT NULL,"
                    + TYPE + " INTEGER NOT NULL,"
                    + TIME_MILLIS + " INTEGER NOT NULL,"
                    + EVENT_DATA + " BLOB NOT NULL,"
                    + "FOREIGN KEY(" + QUERY_ID + ") REFERENCES "
                        + QueriesContract.QueriesEntry.TABLE_NAME + "("
                        + QueriesContract.QueriesEntry.QUERY_ID + "),"
                    + "UNIQUE(" + QUERY_ID + ","
                        + ROW_INDEX + ","
                        + SERVICE_NAME + ","
                        + TYPE + "))";

        private EventsEntry() {}
    }
}
