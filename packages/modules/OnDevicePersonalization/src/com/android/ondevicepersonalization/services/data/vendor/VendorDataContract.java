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

package com.android.ondevicepersonalization.services.data.vendor;

import android.provider.BaseColumns;

/** Contract for the vendor_data tables. Defines the table. */
public class VendorDataContract {
    private VendorDataContract() {
    }

    /** Type value denoting data stored in row is raw binary data */
    public static final int DATA_TYPE_RAW = 0;

    /** Type value denoting data stored in row is a reference to a file */
    public static final int DATA_TYPE_FILE = 1;

    /**
     * Table containing immutable data belonging to vendors. Each table is owned by a single vendor
     * and contains data which will be used during ad requests.
     */
    public static class VendorDataEntry implements BaseColumns {
        /** Lookup key for the row */
        public static final String KEY = "key";

        /** Type of data stored in the row */
        public static final String TYPE = "type";

        /** Row data - ads or other vendor settings */
        public static final String DATA = "data";

        private VendorDataEntry() {
        }

        /**
         * Returns the create table statement for the given table name.
         */
        public static String getCreateTableIfNotExistsStatement(final String tableName) {
            return "CREATE TABLE IF NOT EXISTS " + tableName + " ("
                    + KEY + " TEXT NOT NULL,"
                    + TYPE + " INTEGER DEFAULT 0,"
                    + DATA + " BLOB NOT NULL,"
                    + "PRIMARY KEY(" + KEY + "))";
        }
    }
}
