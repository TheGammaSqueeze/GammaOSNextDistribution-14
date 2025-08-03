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

/** Contract for the queries table. Defines the table. */
public class QueriesContract {
    private QueriesContract() {
    }

    /**
     * Table containing queries. Each row in the table represents a single query.
     */
    public static class QueriesEntry implements BaseColumns {
        public static final String TABLE_NAME = "queries";

        /** The id of the query. */
        public static final String QUERY_ID = "queryId";

        /** Time of the query in milliseconds. */
        public static final String TIME_MILLIS = "timeMillis";

        /** Name of the package that handled the request */
        public static final String SERVICE_NAME = "serviceName";

        /** Blob representing the common query fields. */
        public static final String QUERY_DATA = "queryData";

        public static final String CREATE_TABLE_STATEMENT =
                "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " ("
                    + QUERY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + TIME_MILLIS + " INTEGER NOT NULL,"
                    + SERVICE_NAME + " TEXT NOT NULL,"
                    + QUERY_DATA + " BLOB NOT NULL)";

        private QueriesEntry() {}
    }
}
