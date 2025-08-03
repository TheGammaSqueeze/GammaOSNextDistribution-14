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

/** Contract for the vendor_settings tables. Defines the table. */
public class VendorSettingsContract {
    private VendorSettingsContract() {
    }

    /**
     * Table containing the settings for vendors
     */
    public static class VendorSettingsEntry implements BaseColumns {
        public static final String TABLE_NAME = "vendor_settings";
        /** Name of the vendor package that owns the setting */
        public static final String OWNER = "owner";
        /** Certificate digest of the vendor package that owns the setting */
        public static final String CERT_DIGEST = "certDigest";
        /** The syncToken represented as a timestamp */
        public static final String SYNC_TOKEN = "syncToken";
        public static final String CREATE_TABLE_STATEMENT = "CREATE TABLE IF NOT EXISTS "
                + TABLE_NAME + " ("
                + OWNER + " TEXT NOT NULL,"
                + CERT_DIGEST + " TEXT NOT NULL,"
                + SYNC_TOKEN + " INTEGER NOT NULL,"
                + "PRIMARY KEY(" + OWNER + "," + CERT_DIGEST + "))";

        private VendorSettingsEntry() {
        }
    }
}
