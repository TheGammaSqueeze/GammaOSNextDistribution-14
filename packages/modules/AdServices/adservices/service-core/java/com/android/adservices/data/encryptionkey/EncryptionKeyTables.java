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

package com.android.adservices.data.encryptionkey;

import java.util.List;

/** Container class for KeyCommitment table definitions and constants. */
public final class EncryptionKeyTables {
    public static final String[] ENCRYPTION_KEY_TABLES = {
        EncryptionKeyContract.TABLE,
    };

    /** Contract for Adtech encryption key data. */
    public interface EncryptionKeyContract {
        String TABLE = "encryption_key_data";
        String ID = "_id";
        String KEY_TYPE = "key_type";
        String ENROLLMENT_ID = "enrollment_id";
        String REPORTING_ORIGIN = "reporting_origin";
        String ENCRYPTION_KEY_URL = "encryption_key_url";
        String PROTOCOL_TYPE = "protocol_type";
        String KEY_COMMITMENT_ID = "key_commitment_id";
        String BODY = "body";
        String EXPIRATION = "expiration";
        String LAST_FETCH_TIME = "last_fetch_time";
    }

    public static final String CREATE_TABLE_ENCRYPTION_KEY_DATA_V2 =
            "CREATE TABLE "
                    + EncryptionKeyContract.TABLE
                    + " ("
                    + EncryptionKeyContract.ID
                    + " TEXT PRIMARY KEY NOT NULL, "
                    + EncryptionKeyContract.KEY_TYPE
                    + " TEXT, "
                    + EncryptionKeyContract.ENROLLMENT_ID
                    + " TEXT, "
                    + EncryptionKeyContract.REPORTING_ORIGIN
                    + " TEXT, "
                    + EncryptionKeyContract.ENCRYPTION_KEY_URL
                    + " TEXT, "
                    + EncryptionKeyContract.PROTOCOL_TYPE
                    + " TEXT, "
                    + EncryptionKeyContract.KEY_COMMITMENT_ID
                    + " TEXT, "
                    + EncryptionKeyContract.BODY
                    + " TEXT, "
                    + EncryptionKeyContract.EXPIRATION
                    + " TEXT "
                    + ")";
    public static final String CREATE_TABLE_ENCRYPTION_KEY_DATA_V3 =
            "CREATE TABLE "
                    + EncryptionKeyContract.TABLE
                    + " ("
                    + EncryptionKeyContract.ID
                    + " TEXT PRIMARY KEY NOT NULL, "
                    + EncryptionKeyContract.KEY_TYPE
                    + " TEXT, "
                    + EncryptionKeyContract.ENROLLMENT_ID
                    + " TEXT, "
                    + EncryptionKeyContract.REPORTING_ORIGIN
                    + " TEXT, "
                    + EncryptionKeyContract.ENCRYPTION_KEY_URL
                    + " TEXT, "
                    + EncryptionKeyContract.PROTOCOL_TYPE
                    + " TEXT, "
                    + EncryptionKeyContract.KEY_COMMITMENT_ID
                    + " TEXT, "
                    + EncryptionKeyContract.BODY
                    + " TEXT, "
                    + EncryptionKeyContract.EXPIRATION
                    + " TEXT, "
                    + EncryptionKeyContract.LAST_FETCH_TIME
                    + " INTEGER "
                    + ")";

    // Consolidated list of create statements for all tables in v3.
    public static final List<String> CREATE_STATEMENTS_V3 =
            List.of(CREATE_TABLE_ENCRYPTION_KEY_DATA_V3);

    // Consolidated list of create statements for all tables in v2.
    public static final List<String> CREATE_STATEMENTS_V2 =
            List.of(CREATE_TABLE_ENCRYPTION_KEY_DATA_V2);

    // Private constructor to prevent instantiation.
    private EncryptionKeyTables() {}
}
