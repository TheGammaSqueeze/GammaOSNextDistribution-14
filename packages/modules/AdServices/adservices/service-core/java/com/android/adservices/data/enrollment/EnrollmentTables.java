/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.adservices.data.enrollment;

import java.util.List;

/** Container class for Enrollment table definitions and constants. */
public final class EnrollmentTables {
    public static final String[] ENROLLMENT_TABLES = {
        EnrollmentTables.EnrollmentDataContract.TABLE,
    };

    /** Contract for Adtech enrollment data. */
    public interface EnrollmentDataContract {
        String TABLE = "enrollment_data";
        String ENROLLMENT_ID = "enrollment_id";
        String COMPANY_ID = "company_id";
        // Following six string columns each consist of a space separated list.
        String SDK_NAMES = "sdk_names";
        String ATTRIBUTION_SOURCE_REGISTRATION_URL = "attribution_source_registration_url";
        String ATTRIBUTION_TRIGGER_REGISTRATION_URL = "attribution_trigger_registration_url";
        String ATTRIBUTION_REPORTING_URL = "attribution_reporting_url";
        String REMARKETING_RESPONSE_BASED_REGISTRATION_URL =
                "remarketing_response_based_registration_url";
        String ENCRYPTION_KEY_URL = "encryption_key_url";
    }

    public static final String CREATE_TABLE_ENROLLMENT_DATA_V1 =
            "CREATE TABLE "
                    + EnrollmentDataContract.TABLE
                    + " ("
                    + EnrollmentDataContract.ENROLLMENT_ID
                    + " TEXT PRIMARY KEY NOT NULL, "
                    + EnrollmentDataContract.COMPANY_ID
                    + " TEXT, "
                    + EnrollmentDataContract.SDK_NAMES
                    + " TEXT, "
                    + EnrollmentDataContract.ATTRIBUTION_SOURCE_REGISTRATION_URL
                    + " TEXT, "
                    + EnrollmentDataContract.ATTRIBUTION_TRIGGER_REGISTRATION_URL
                    + " TEXT, "
                    + EnrollmentDataContract.ATTRIBUTION_REPORTING_URL
                    + " TEXT, "
                    + EnrollmentDataContract.REMARKETING_RESPONSE_BASED_REGISTRATION_URL
                    + " TEXT, "
                    + EnrollmentDataContract.ENCRYPTION_KEY_URL
                    + " TEXT "
                    + ")";

    // Consolidated list of create statements for all tables.
    public static final List<String> CREATE_STATEMENTS = List.of(CREATE_TABLE_ENROLLMENT_DATA_V1);

    // Consolidated list of create statements for all tables.
    public static final List<String> CREATE_STATEMENTS_V1 =
            List.of(CREATE_TABLE_ENROLLMENT_DATA_V1);

    // Private constructor to prevent instantiation.
    private EnrollmentTables() {}
}
