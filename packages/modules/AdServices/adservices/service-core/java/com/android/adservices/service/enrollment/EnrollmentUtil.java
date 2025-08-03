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

package com.android.adservices.service.enrollment;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.android.adservices.service.stats.AdServicesLogger;
import com.android.internal.annotations.VisibleForTesting;

/** Util class for all enrollment-related classes */
public class EnrollmentUtil {
    private static EnrollmentUtil sSingleton;
    private final Context mContext;

    @VisibleForTesting public static final String ENROLLMENT_SHARED_PREF = "adservices_enrollment";
    @VisibleForTesting public static final String BUILD_ID = "build_id";
    @VisibleForTesting public static final String FILE_GROUP_STATUS = "file_group_status";

    private EnrollmentUtil(Context context) {
        mContext = context;
    }

    /** Returns an instance of the EnrollmentDao given a context. */
    @NonNull
    public static EnrollmentUtil getInstance(@NonNull Context context) {
        synchronized (EnrollmentUtil.class) {
            if (sSingleton == null) {
                sSingleton = new EnrollmentUtil(context);
            }
            return sSingleton;
        }
    }

    /** Get build ID from shared preference */
    public int getBuildId() {
        SharedPreferences prefs =
                mContext.getSharedPreferences(ENROLLMENT_SHARED_PREF, Context.MODE_PRIVATE);
        return prefs.getInt(BUILD_ID, /* defaultValue */ -1);
    }

    /** Get file group status from shared preference */
    public int getFileGroupStatus() {
        SharedPreferences prefs =
                mContext.getSharedPreferences(ENROLLMENT_SHARED_PREF, Context.MODE_PRIVATE);
        return prefs.getInt(FILE_GROUP_STATUS, /* defaultValue */ 0);
    }

    private int convertBuildIdStringToInt(String buildIdString) {
        if (buildIdString == null) {
            return -1;
        }
        try {
            int buildId = Integer.parseInt(buildIdString);
            return buildId;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private String getQueryParameterValue(String queryParameter) {
        if (queryParameter == null) {
            return "";
        }
        return queryParameter;
    }

    /** Log EnrollmentData atom metrics for enrollment database transactions */
    public void logEnrollmentDataStats(
            AdServicesLogger logger, int type, boolean isSuccessful, Integer buildId) {
        logger.logEnrollmentDataStats(type, isSuccessful, buildId);
    }

    /** Log EnrollmentFileDownload atom metrics for enrollment data downloading */
    public void logEnrollmentFileDownloadStats(
            AdServicesLogger logger, boolean isSuccessful, String buildIdString) {
        logger.logEnrollmentFileDownloadStats(
                isSuccessful, convertBuildIdStringToInt(buildIdString));
    }

    /** Log EnrollmentData atom metrics for enrollment database queries */
    public void logEnrollmentMatchStats(
            AdServicesLogger logger, boolean isSuccessful, Integer buildId) {
        logger.logEnrollmentMatchStats(isSuccessful, buildId);
    }

    /** Log EnrollmentFailed atom metrics for enrollment-related status_caller_not_found errors */
    public void logEnrollmentFailedStats(
            AdServicesLogger logger,
            Integer buildId,
            Integer dataFileGroupStatus,
            int enrollmentRecordCount,
            String queryParameter,
            int errorCause) {
        logger.logEnrollmentFailedStats(
                buildId,
                dataFileGroupStatus,
                enrollmentRecordCount,
                getQueryParameterValue(queryParameter),
                errorCause);
    }
}
