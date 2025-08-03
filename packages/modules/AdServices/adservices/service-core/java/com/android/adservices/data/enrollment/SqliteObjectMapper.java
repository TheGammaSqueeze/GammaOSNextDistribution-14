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

package com.android.adservices.data.enrollment;

import android.adservices.common.AdTechIdentifier;
import android.database.Cursor;
import android.net.Uri;

import com.android.adservices.LogUtil;
import com.android.adservices.service.enrollment.EnrollmentData;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Helper class for SQLite operations. */
public class SqliteObjectMapper {
    /** Create {@link EnrollmentData} object from SQLite datastore. */
    public static EnrollmentData constructEnrollmentDataFromCursor(Cursor cursor) {
        EnrollmentData.Builder builder = new EnrollmentData.Builder();
        setTextColumn(
                cursor,
                EnrollmentTables.EnrollmentDataContract.ENROLLMENT_ID,
                builder::setEnrollmentId);
        setTextColumn(
                cursor,
                EnrollmentTables.EnrollmentDataContract.COMPANY_ID,
                builder::setEnrolledAPIs);
        setTextColumn(
                cursor, EnrollmentTables.EnrollmentDataContract.SDK_NAMES, builder::setSdkNames);
        setTextColumn(
                cursor,
                EnrollmentTables.EnrollmentDataContract.ATTRIBUTION_SOURCE_REGISTRATION_URL,
                builder::setAttributionSourceRegistrationUrl);
        setTextColumn(
                cursor,
                EnrollmentTables.EnrollmentDataContract.ATTRIBUTION_TRIGGER_REGISTRATION_URL,
                builder::setAttributionTriggerRegistrationUrl);
        setTextColumn(
                cursor,
                EnrollmentTables.EnrollmentDataContract.ATTRIBUTION_REPORTING_URL,
                builder::setAttributionReportingUrl);
        setTextColumn(
                cursor,
                EnrollmentTables.EnrollmentDataContract.REMARKETING_RESPONSE_BASED_REGISTRATION_URL,
                builder::setRemarketingResponseBasedRegistrationUrl);
        setTextColumn(
                cursor,
                EnrollmentTables.EnrollmentDataContract.ENCRYPTION_KEY_URL,
                builder::setEncryptionKeyUrl);
        return builder.build();
    }

    /**
     * Transforms the FLEDGE RBR URLs found at the given {@code cursor} to a list of {@link
     * AdTechIdentifier} objects.
     */
    public static List<AdTechIdentifier> getAdTechIdentifiersFromFledgeCursor(Cursor cursor) {
        List<AdTechIdentifier> enrolledFledgeAdTechIdentifiers = new ArrayList<>();
        List<String> fledgeRbrUrls = new ArrayList<>();

        setTextColumn(
                cursor,
                EnrollmentTables.EnrollmentDataContract.REMARKETING_RESPONSE_BASED_REGISTRATION_URL,
                input -> {
                    fledgeRbrUrls.addAll(EnrollmentData.splitEnrollmentInputToList(input));
                    return null;
                });

        for (String fledgeRbrUrl : fledgeRbrUrls) {
            try {
                if (fledgeRbrUrl != null && !fledgeRbrUrl.trim().isEmpty()) {
                    enrolledFledgeAdTechIdentifiers.add(
                            AdTechIdentifier.fromString(Uri.parse(fledgeRbrUrl).getHost()));
                }
            } catch (Exception exception) {
                LogUtil.d(exception, "Failure parsing RBR URL \"%s\"; skipping", fledgeRbrUrl);
            }
        }

        return enrolledFledgeAdTechIdentifiers;
    }

    /**
     * Transforms the PAS urls found at the given {@code cursor} to a {@link AdTechIdentifier}
     * objects.
     */
    static AdTechIdentifier getAdTechIdentifierFromPASCursor(Cursor cursor) {

        List<String> pasUrls = new ArrayList<>();
        AdTechIdentifier enrolledPASAdTechIdentifier = null;

        setTextColumn(
                cursor,
                EnrollmentTables.EnrollmentDataContract.ENCRYPTION_KEY_URL,
                input -> {
                    pasUrls.add(input);
                    return null;
                });

        for (String pasUrl : pasUrls) {
            try {
                if (pasUrl != null && !pasUrl.trim().isEmpty()) {
                    enrolledPASAdTechIdentifier =
                            AdTechIdentifier.fromString(Uri.parse(pasUrl).getHost());
                }
            } catch (Exception exception) {
                LogUtil.d(exception, "Failure parsing PAS URL \"%s\"; skipping", pasUrl);
            }
        }

        return enrolledPASAdTechIdentifier;
    }

    private static <BuilderType> void setTextColumn(
            Cursor cursor, String column, Function<String, BuilderType> setter) {
        setColumnValue(cursor, column, cursor::getString, setter);
    }

    @SuppressWarnings("ReturnValueIgnored")
    private static <BuilderType, DataType> void setColumnValue(
            Cursor cursor,
            String column,
            Function<Integer, DataType> getColVal,
            Function<DataType, BuilderType> setter) {
        int index = cursor.getColumnIndex(column);
        if (index > -1 && !cursor.isNull(index)) {
            setter.apply(getColVal.apply(index));
        }
    }
}
