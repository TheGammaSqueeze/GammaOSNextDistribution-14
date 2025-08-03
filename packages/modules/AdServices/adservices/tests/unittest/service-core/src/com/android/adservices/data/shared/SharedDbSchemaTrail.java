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

package com.android.adservices.data.shared;

import static com.android.adservices.data.encryptionkey.EncryptionKeyTables.EncryptionKeyContract;
import static com.android.adservices.data.enrollment.EnrollmentTables.EnrollmentDataContract;

import com.android.adservices.data.encryptionkey.EncryptionKeyTables;
import com.android.adservices.data.enrollment.EnrollmentTables;

import com.google.common.collect.ImmutableMap;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Has scripts to create database at any version. To introduce migration to a new version x, this
 * class should have one entry of {@link #CREATE_TABLES_STATEMENTS_BY_VERSION} for version x. These
 * entries will cause creation of method {@code getCreateStatementByTableVx}, where the previous
 * version's (x-1) scripts will be revised to create scripts for version x.
 */
public class SharedDbSchemaTrail {
    private static final String CREATE_TABLE_ENROLLMENT_V1 =
            "CREATE TABLE "
                    + EnrollmentTables.EnrollmentDataContract.TABLE
                    + " ("
                    + EnrollmentTables.EnrollmentDataContract.ENROLLMENT_ID
                    + " TEXT PRIMARY KEY NOT NULL, "
                    + EnrollmentTables.EnrollmentDataContract.COMPANY_ID
                    + " TEXT, "
                    + EnrollmentTables.EnrollmentDataContract.SDK_NAMES
                    + " TEXT, "
                    + EnrollmentTables.EnrollmentDataContract.ATTRIBUTION_SOURCE_REGISTRATION_URL
                    + " TEXT, "
                    + EnrollmentTables.EnrollmentDataContract.ATTRIBUTION_TRIGGER_REGISTRATION_URL
                    + " TEXT, "
                    + EnrollmentTables.EnrollmentDataContract.ATTRIBUTION_REPORTING_URL
                    + " TEXT, "
                    + EnrollmentTables.EnrollmentDataContract
                            .REMARKETING_RESPONSE_BASED_REGISTRATION_URL
                    + " TEXT, "
                    + EnrollmentTables.EnrollmentDataContract.ENCRYPTION_KEY_URL
                    + " TEXT "
                    + ")";

    private static final String CREATE_TABLE_ENCRYPTION_KEY_V2 =
            "CREATE TABLE "
                    + EncryptionKeyTables.EncryptionKeyContract.TABLE
                    + " ("
                    + EncryptionKeyTables.EncryptionKeyContract.ID
                    + " TEXT PRIMARY KEY NOT NULL, "
                    + EncryptionKeyTables.EncryptionKeyContract.KEY_TYPE
                    + " TEXT, "
                    + EncryptionKeyTables.EncryptionKeyContract.ENROLLMENT_ID
                    + " TEXT, "
                    + EncryptionKeyTables.EncryptionKeyContract.REPORTING_ORIGIN
                    + " TEXT, "
                    + EncryptionKeyTables.EncryptionKeyContract.ENCRYPTION_KEY_URL
                    + " TEXT, "
                    + EncryptionKeyTables.EncryptionKeyContract.PROTOCOL_TYPE
                    + " TEXT, "
                    + EncryptionKeyTables.EncryptionKeyContract.KEY_COMMITMENT_ID
                    + " TEXT, "
                    + EncryptionKeyTables.EncryptionKeyContract.BODY
                    + " TEXT, "
                    + EncryptionKeyTables.EncryptionKeyContract.EXPIRATION
                    + " TEXT "
                    + ")";

    private static final String CREATE_TABLE_ENCRYPTION_KEY_V3 =
            "CREATE TABLE "
                    + EncryptionKeyTables.EncryptionKeyContract.TABLE
                    + " ("
                    + EncryptionKeyTables.EncryptionKeyContract.ID
                    + " TEXT PRIMARY KEY NOT NULL, "
                    + EncryptionKeyTables.EncryptionKeyContract.KEY_TYPE
                    + " TEXT, "
                    + EncryptionKeyTables.EncryptionKeyContract.ENROLLMENT_ID
                    + " TEXT, "
                    + EncryptionKeyTables.EncryptionKeyContract.REPORTING_ORIGIN
                    + " TEXT, "
                    + EncryptionKeyTables.EncryptionKeyContract.ENCRYPTION_KEY_URL
                    + " TEXT, "
                    + EncryptionKeyTables.EncryptionKeyContract.PROTOCOL_TYPE
                    + " TEXT, "
                    + EncryptionKeyTables.EncryptionKeyContract.KEY_COMMITMENT_ID
                    + " TEXT, "
                    + EncryptionKeyTables.EncryptionKeyContract.BODY
                    + " TEXT, "
                    + EncryptionKeyTables.EncryptionKeyContract.EXPIRATION
                    + " TEXT, "
                    + EncryptionKeyTables.EncryptionKeyContract.LAST_FETCH_TIME
                    + " INTEGER "
                    + ")";

    private static Map<String, String> getCreateStatementByTableV1() {
        return ImmutableMap.of(EnrollmentDataContract.TABLE, CREATE_TABLE_ENROLLMENT_V1);
    }

    private static Map<String, String> getCreateStatementByTableV2() {
        Map<String, String> createStatements = new HashMap<>(getCreateStatementByTableV1());
        createStatements.put(EncryptionKeyContract.TABLE, CREATE_TABLE_ENCRYPTION_KEY_V2);
        return createStatements;
    }

    private static Map<String, String> getCreateStatementByTableV3() {
        Map<String, String> createStatements = new HashMap<>(getCreateStatementByTableV2());
        createStatements.put(EncryptionKeyContract.TABLE, CREATE_TABLE_ENCRYPTION_KEY_V3);
        return createStatements;
    }

    private static final Map<Integer, Collection<String>> CREATE_TABLES_STATEMENTS_BY_VERSION =
            new ImmutableMap.Builder<Integer, Collection<String>>()
                    .put(1, getCreateStatementByTableV1().values())
                    .put(2, getCreateStatementByTableV2().values())
                    .put(3, getCreateStatementByTableV3().values())
                    .build();

    /**
     * Returns a map of table to the respective create statement at the provided version.
     *
     * @param version version for which create statements are requested
     * @return map of table to their create statement
     */
    public static Collection<String> getCreateTableStatementsByVersion(int version) {
        if (version < 1) {
            throw new IllegalArgumentException("Unsupported version " + version);
        }

        return CREATE_TABLES_STATEMENTS_BY_VERSION.get(version);
    }
}
