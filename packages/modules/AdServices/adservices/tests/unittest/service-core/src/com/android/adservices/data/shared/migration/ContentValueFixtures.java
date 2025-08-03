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

package com.android.adservices.data.shared.migration;

import static com.android.adservices.data.shared.migration.ContentValueFixtures.EnrollmentValues.ATTRIBUTION_REPORTING_URL;
import static com.android.adservices.data.shared.migration.ContentValueFixtures.EnrollmentValues.ATTRIBUTION_REPORTING_URL_CS;
import static com.android.adservices.data.shared.migration.ContentValueFixtures.EnrollmentValues.ATTRIBUTION_REPORTING_URL_U;
import static com.android.adservices.data.shared.migration.ContentValueFixtures.EnrollmentValues.ATTRIBUTION_SOURCE_REGISTRATION_URL;
import static com.android.adservices.data.shared.migration.ContentValueFixtures.EnrollmentValues.ATTRIBUTION_SOURCE_REGISTRATION_URL_CS;
import static com.android.adservices.data.shared.migration.ContentValueFixtures.EnrollmentValues.ATTRIBUTION_SOURCE_REGISTRATION_URL_U;
import static com.android.adservices.data.shared.migration.ContentValueFixtures.EnrollmentValues.ATTRIBUTION_TRIGGER_REGISTRATION_URL;
import static com.android.adservices.data.shared.migration.ContentValueFixtures.EnrollmentValues.ATTRIBUTION_TRIGGER_REGISTRATION_URL_CS;
import static com.android.adservices.data.shared.migration.ContentValueFixtures.EnrollmentValues.ATTRIBUTION_TRIGGER_REGISTRATION_URL_U;
import static com.android.adservices.data.shared.migration.ContentValueFixtures.EnrollmentValues.ENCRYPTION_KEY_URL;
import static com.android.adservices.data.shared.migration.ContentValueFixtures.EnrollmentValues.ENCRYPTION_KEY_URL_CS;
import static com.android.adservices.data.shared.migration.ContentValueFixtures.EnrollmentValues.ENCRYPTION_KEY_URL_U;
import static com.android.adservices.data.shared.migration.ContentValueFixtures.EnrollmentValues.ENROLLED_APIS;
import static com.android.adservices.data.shared.migration.ContentValueFixtures.EnrollmentValues.ENROLLED_APIS_CS;
import static com.android.adservices.data.shared.migration.ContentValueFixtures.EnrollmentValues.ENROLLED_APIS_U;
import static com.android.adservices.data.shared.migration.ContentValueFixtures.EnrollmentValues.ENROLLMENT_ID;
import static com.android.adservices.data.shared.migration.ContentValueFixtures.EnrollmentValues.ENROLLMENT_ID_CS;
import static com.android.adservices.data.shared.migration.ContentValueFixtures.EnrollmentValues.ENROLLMENT_ID_U;
import static com.android.adservices.data.shared.migration.ContentValueFixtures.EnrollmentValues.REMARKETING_RESPONSE_BASED_REGISTRATION_URL;
import static com.android.adservices.data.shared.migration.ContentValueFixtures.EnrollmentValues.REMARKETING_RESPONSE_BASED_REGISTRATION_URL_CS;
import static com.android.adservices.data.shared.migration.ContentValueFixtures.EnrollmentValues.REMARKETING_RESPONSE_BASED_REGISTRATION_URL_U;
import static com.android.adservices.data.shared.migration.ContentValueFixtures.EnrollmentValues.SDK_NAMES;
import static com.android.adservices.data.shared.migration.ContentValueFixtures.EnrollmentValues.SDK_NAMES_CS;
import static com.android.adservices.data.shared.migration.ContentValueFixtures.EnrollmentValues.SDK_NAMES_U;

import android.content.ContentValues;

import com.android.adservices.common.WebUtil;
import com.android.adservices.data.enrollment.EnrollmentTables;

import java.util.Arrays;
import java.util.List;

public class ContentValueFixtures {

    public static class EnrollmentValues {

        // Default Enrollment Example Data
        public static final String ENROLLMENT_ID = "enrollment_id";
        public static final String ENROLLED_APIS = "PRIVACY_SANDBOX_API_ATTRIBUTION_REPORTING";
        public static final String SDK_NAMES = "SDK_NAMES";
        public static final String ATTRIBUTION_SOURCE_REGISTRATION_URL =
                WebUtil.validUrl("https://subdomain.example.test/source");
        public static final String ATTRIBUTION_TRIGGER_REGISTRATION_URL =
                WebUtil.validUrl("https://subdomain.example.test/trigger");
        public static final String ATTRIBUTION_REPORTING_URL =
                WebUtil.validUrl("https://subdomain.example.test/report");
        public static final String REMARKETING_RESPONSE_BASED_REGISTRATION_URL =
                WebUtil.validUrl("https://subdomain.example.test/remarket");
        public static final String ENCRYPTION_KEY_URL =
                WebUtil.validUrl("https://subdomain2.example.test/encryption");

        // Cross Site to Default Enrollment Data
        public static final String ENROLLMENT_ID_CS = "enrollment_id_cs";
        public static final String ENROLLED_APIS_CS = "PRIVACY_SANDBOX_API_ATTRIBUTION_REPORTING";
        public static final String SDK_NAMES_CS = "SDK_NAMES";
        public static final String ATTRIBUTION_SOURCE_REGISTRATION_URL_CS =
                WebUtil.validUrl("https://subdomain2.example.test/source");
        public static final String ATTRIBUTION_TRIGGER_REGISTRATION_URL_CS =
                WebUtil.validUrl("https://subdomain2.example.test/trigger");
        public static final String ATTRIBUTION_REPORTING_URL_CS =
                WebUtil.validUrl("https://subdomain2.example.test/report");
        public static final String REMARKETING_RESPONSE_BASED_REGISTRATION_URL_CS =
                WebUtil.validUrl("https://subdomain2.example.test/remarket");
        public static final String ENCRYPTION_KEY_URL_CS =
                WebUtil.validUrl("https://subdomain2.example.test/encryption");

        // Cross Site to Default Enrollment Data
        public static final String ENROLLMENT_ID_U = "enrollment_id_u";
        public static final String ENROLLED_APIS_U = "PRIVACY_SANDBOX_API_ATTRIBUTION_REPORTING";
        public static final String SDK_NAMES_U = "SDK_NAMES";
        public static final String ATTRIBUTION_SOURCE_REGISTRATION_URL_U =
                WebUtil.validUrl("https://subdomain.unique-example.test/source");
        public static final String ATTRIBUTION_TRIGGER_REGISTRATION_URL_U =
                WebUtil.validUrl("https://subdomain.unique-example.test/trigger");
        public static final String ATTRIBUTION_REPORTING_URL_U =
                WebUtil.validUrl("https://subdomain.unique-example.test/report");
        public static final String REMARKETING_RESPONSE_BASED_REGISTRATION_URL_U =
                WebUtil.validUrl("https://subdomain.unique-example.test/remarket");
        public static final String ENCRYPTION_KEY_URL_U =
                WebUtil.validUrl("https://subdomain.unique-example.test/encryption");
    }

    public static ContentValues generateEnrollmentDefaultExampleContentValuesV1() {
        ContentValues values = new ContentValues();
        values.put(EnrollmentTables.EnrollmentDataContract.ENROLLMENT_ID, ENROLLMENT_ID);
        values.put(EnrollmentTables.EnrollmentDataContract.COMPANY_ID, ENROLLED_APIS);
        values.put(EnrollmentTables.EnrollmentDataContract.SDK_NAMES, SDK_NAMES);
        values.put(
                EnrollmentTables.EnrollmentDataContract.ATTRIBUTION_SOURCE_REGISTRATION_URL,
                ATTRIBUTION_SOURCE_REGISTRATION_URL);
        values.put(
                EnrollmentTables.EnrollmentDataContract.ATTRIBUTION_TRIGGER_REGISTRATION_URL,
                ATTRIBUTION_TRIGGER_REGISTRATION_URL);
        values.put(
                EnrollmentTables.EnrollmentDataContract.ATTRIBUTION_REPORTING_URL,
                ATTRIBUTION_REPORTING_URL);
        values.put(
                EnrollmentTables.EnrollmentDataContract.REMARKETING_RESPONSE_BASED_REGISTRATION_URL,
                REMARKETING_RESPONSE_BASED_REGISTRATION_URL);
        values.put(EnrollmentTables.EnrollmentDataContract.ENCRYPTION_KEY_URL, ENCRYPTION_KEY_URL);

        return values;
    }

    public static ContentValues generateEnrollmentUniqueExampleContentValuesV1() {
        ContentValues values = new ContentValues();
        values.put(EnrollmentTables.EnrollmentDataContract.ENROLLMENT_ID, ENROLLMENT_ID_U);
        values.put(EnrollmentTables.EnrollmentDataContract.COMPANY_ID, ENROLLED_APIS_U);
        values.put(EnrollmentTables.EnrollmentDataContract.SDK_NAMES, SDK_NAMES_U);
        values.put(
                EnrollmentTables.EnrollmentDataContract.ATTRIBUTION_SOURCE_REGISTRATION_URL,
                ATTRIBUTION_SOURCE_REGISTRATION_URL_U);
        values.put(
                EnrollmentTables.EnrollmentDataContract.ATTRIBUTION_TRIGGER_REGISTRATION_URL,
                ATTRIBUTION_TRIGGER_REGISTRATION_URL_U);
        values.put(
                EnrollmentTables.EnrollmentDataContract.ATTRIBUTION_REPORTING_URL,
                ATTRIBUTION_REPORTING_URL_U);
        values.put(
                EnrollmentTables.EnrollmentDataContract.REMARKETING_RESPONSE_BASED_REGISTRATION_URL,
                REMARKETING_RESPONSE_BASED_REGISTRATION_URL_U);
        values.put(
                EnrollmentTables.EnrollmentDataContract.ENCRYPTION_KEY_URL, ENCRYPTION_KEY_URL_U);

        return values;
    }

    public static ContentValues generateEnrollmentCrossSiteExampleContentValuesV1() {
        ContentValues values = new ContentValues();
        values.put(EnrollmentTables.EnrollmentDataContract.ENROLLMENT_ID, ENROLLMENT_ID_CS);
        values.put(EnrollmentTables.EnrollmentDataContract.COMPANY_ID, ENROLLED_APIS_CS);
        values.put(EnrollmentTables.EnrollmentDataContract.SDK_NAMES, SDK_NAMES_CS);
        values.put(
                EnrollmentTables.EnrollmentDataContract.ATTRIBUTION_SOURCE_REGISTRATION_URL,
                ATTRIBUTION_SOURCE_REGISTRATION_URL_CS);
        values.put(
                EnrollmentTables.EnrollmentDataContract.ATTRIBUTION_TRIGGER_REGISTRATION_URL,
                ATTRIBUTION_TRIGGER_REGISTRATION_URL_CS);
        values.put(
                EnrollmentTables.EnrollmentDataContract.ATTRIBUTION_REPORTING_URL,
                ATTRIBUTION_REPORTING_URL_CS);
        values.put(
                EnrollmentTables.EnrollmentDataContract.REMARKETING_RESPONSE_BASED_REGISTRATION_URL,
                REMARKETING_RESPONSE_BASED_REGISTRATION_URL_CS);
        values.put(
                EnrollmentTables.EnrollmentDataContract.ENCRYPTION_KEY_URL, ENCRYPTION_KEY_URL_CS);

        return values;
    }

    /**
     * @return List of Example EnrollmentV1 contentValues. Contain multiple distinct values that
     *     differ in URI Origin but share Site.
     */
    public static List<ContentValues> generateCrossSiteEnrollmentListV1() {
        List<ContentValues> list =
                Arrays.asList(
                        generateEnrollmentDefaultExampleContentValuesV1(),
                        generateEnrollmentCrossSiteExampleContentValuesV1());
        return list;
    }

    /**
     * @return List of Example EnrollmentV1 contentValues. Contain multiple distinct values that
     *     differ in URI Origin and Site.
     */
    public static List<ContentValues> generateDistinctSiteEnrollmentListV1() {
        List<ContentValues> list =
                Arrays.asList(
                        generateEnrollmentDefaultExampleContentValuesV1(),
                        generateEnrollmentUniqueExampleContentValuesV1());
        return list;
    }

    /**
     * @return List of Example EnrollmentV1 contentValues. Contain multiple distinct records with
     *     cases that (share/are distinct) in URI Origin/Site.
     */
    public static List<ContentValues> generateFullSiteEnrollmentListV1() {
        List<ContentValues> list =
                Arrays.asList(
                        generateEnrollmentDefaultExampleContentValuesV1(),
                        generateEnrollmentCrossSiteExampleContentValuesV1(),
                        generateEnrollmentUniqueExampleContentValuesV1());
        return list;
    }
}
