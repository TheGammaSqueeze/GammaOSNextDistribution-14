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

package com.android.adservices.service.common;

import static android.adservices.common.AdServicesStatusUtils.FAILURE_REASON_ENROLLMENT_BLOCKLISTED;
import static android.adservices.common.AdServicesStatusUtils.FAILURE_REASON_MANIFEST_ADSERVICES_CONFIG_NO_PERMISSION;
import static android.adservices.common.AdServicesStatusUtils.STATUS_CALLER_NOT_ALLOWED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_PERMISSION_NOT_REQUESTED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_UNAUTHORIZED;

import static com.android.adservices.service.common.AppManifestConfigCall.API_AD_SELECTION;
import static com.android.adservices.service.common.AppManifestConfigCall.API_CUSTOM_AUDIENCES;
import static com.android.adservices.service.common.AppManifestConfigCall.API_PROTECTED_SIGNALS;

import android.adservices.common.AdServicesStatusUtils;
import android.adservices.common.AdTechIdentifier;
import android.annotation.NonNull;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.util.Pair;

import androidx.annotation.RequiresApi;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.enrollment.EnrollmentData;
import com.android.adservices.service.enrollment.EnrollmentStatus;
import com.android.adservices.service.enrollment.EnrollmentUtil;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.internal.annotations.VisibleForTesting;

import java.util.Collection;
import java.util.Locale;
import java.util.Objects;

/** Verify caller of FLEDGE API has the permission of performing certain behaviour. */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
public class FledgeAuthorizationFilter {

    @VisibleForTesting public static final String INVALID_API_TYPE = "Invalid apiType: %d";
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    @NonNull private final PackageManager mPackageManager;
    @NonNull private final EnrollmentDao mEnrollmentDao;
    @NonNull private final AdServicesLogger mAdServicesLogger;
    private EnrollmentUtil mEnrollmentUtil;

    @VisibleForTesting
    public FledgeAuthorizationFilter(
            @NonNull PackageManager packageManager,
            @NonNull EnrollmentDao enrollmentDao,
            @NonNull AdServicesLogger adServicesLogger) {
        this(packageManager, enrollmentDao, adServicesLogger, null);
    }

    @VisibleForTesting
    public FledgeAuthorizationFilter(
            @NonNull PackageManager packageManager,
            @NonNull EnrollmentDao enrollmentDao,
            @NonNull AdServicesLogger adServicesLogger,
            EnrollmentUtil enrollmentUtil) {
        Objects.requireNonNull(packageManager);
        Objects.requireNonNull(enrollmentDao);
        Objects.requireNonNull(adServicesLogger);

        mPackageManager = packageManager;
        mEnrollmentDao = enrollmentDao;
        mAdServicesLogger = adServicesLogger;
        mEnrollmentUtil = enrollmentUtil;
    }

    /** Creates an instance of {@link FledgeAuthorizationFilter}. */
    public static FledgeAuthorizationFilter create(
            @NonNull Context context, @NonNull AdServicesLogger adServicesLogger) {
        Objects.requireNonNull(context);

        return new FledgeAuthorizationFilter(
                context.getPackageManager(),
                EnrollmentDao.getInstance(context),
                adServicesLogger,
                EnrollmentUtil.getInstance(context));
    }

    /**
     * Check if the package name provided by the caller is one of the package of the calling uid.
     *
     * @param callingPackageName the caller-supplied package name
     * @param callingUid the uid get from the Binder
     * @param apiNameLoggingId the id of the api being called
     * @throws CallerMismatchException if the package name provided does not associate with the uid.
     */
    public void assertCallingPackageName(
            @NonNull String callingPackageName, int callingUid, int apiNameLoggingId)
            throws CallerMismatchException {
        Objects.requireNonNull(callingPackageName);

        sLogger.v(
                "Asserting package name \"%s\" is valid for uid %d",
                callingPackageName, callingUid);

        String[] packageNamesForUid = mPackageManager.getPackagesForUid(callingUid);
        for (String packageNameForUid : packageNamesForUid) {
            sLogger.v("Candidate package name \"%s\"", packageNameForUid);
            if (callingPackageName.equals(packageNameForUid)) return;
        }

        sLogger.v("No match found, failing calling package name match in API %d", apiNameLoggingId);
        mAdServicesLogger.logFledgeApiCallStats(
                apiNameLoggingId, callingPackageName, STATUS_UNAUTHORIZED, /*latencyMs=*/ 0);
        throw new CallerMismatchException();
    }

    /**
     * Check if the app had declared a permission, and throw an error if it has not
     *
     * @param context api service context
     * @param apiNameLoggingId the id of the api being called
     * @throws SecurityException if the package did not declare custom audience permission
     */
    public void assertAppDeclaredPermission(
            @NonNull Context context,
            @NonNull String appPackageName,
            int apiNameLoggingId,
            @NonNull String permission)
            throws SecurityException {
        Objects.requireNonNull(context);
        Objects.requireNonNull(appPackageName);
        Objects.requireNonNull(permission);

        if (!PermissionHelper.hasPermission(context, appPackageName, permission)) {
            logAndThrowPermissionFailure(appPackageName, apiNameLoggingId, permission);
        }
    }

    /**
     * Check if the app had declared any of the listed permissions, and throw an error if it has not
     *
     * @param context api service context
     * @param appPackageName the package name of the calling app
     * @param apiNameLoggingId the id of the api being called
     * @param permissions lists of permissions that allow calling the API
     * @throws SecurityException if the package did not declare custom audience permission
     */
    public void assertAppDeclaredAnyPermission(
            @NonNull Context context,
            @NonNull String appPackageName,
            int apiNameLoggingId,
            @NonNull Collection<String> permissions)
            throws SecurityException {
        Objects.requireNonNull(context);
        Objects.requireNonNull(appPackageName);
        Objects.requireNonNull(permissions);

        for (String permission : permissions) {
            if (PermissionHelper.hasPermission(context, appPackageName, permission)) {
                return; // Found a valid permission
            }
        }
        logAndThrowMultiplePermissionFailure(appPackageName, apiNameLoggingId, permissions);
    }

    private void logAndThrowPermissionFailure(
            String callerAppPackageName, int apiNameLoggingId, String permission) {
        sLogger.v("Permission %s not declared by caller in API %d", permission, apiNameLoggingId);
        mAdServicesLogger.logFledgeApiCallStats(
                apiNameLoggingId,
                callerAppPackageName,
                STATUS_PERMISSION_NOT_REQUESTED,
                /*latencyMs=*/ 0);
        throw new SecurityException(
                AdServicesStatusUtils.SECURITY_EXCEPTION_PERMISSION_NOT_REQUESTED_ERROR_MESSAGE);
    }

    private void logAndThrowMultiplePermissionFailure(
            String callerAppPackageName, int apiNameLoggingId, Collection<String> permissions) {
        sLogger.v("Permissions %s not declared by caller in API %d", permissions, apiNameLoggingId);
        mAdServicesLogger.logFledgeApiCallStats(
                apiNameLoggingId,
                callerAppPackageName,
                STATUS_PERMISSION_NOT_REQUESTED,
                /*latencyMs=*/ 0);
        throw new SecurityException(
                AdServicesStatusUtils.SECURITY_EXCEPTION_PERMISSION_NOT_REQUESTED_ERROR_MESSAGE);
    }

    /**
     * Check if a certain ad tech is enrolled and authorized to perform the operation for the
     * package.
     *
     * @param context api service context
     * @param appPackageName the package name to check against
     * @param adTechIdentifier the ad tech to check against
     * @param apiNameLoggingId the id of the api being called
     * @throws AdTechNotAllowedException if the ad tech is not authorized to perform the operation
     */
    public void assertAdTechAllowed(
            @NonNull Context context,
            @NonNull String appPackageName,
            @NonNull AdTechIdentifier adTechIdentifier,
            int apiNameLoggingId,
            @AppManifestConfigCall.ApiType int apiType)
            throws AdTechNotAllowedException {
        Objects.requireNonNull(context);
        Objects.requireNonNull(appPackageName);
        Objects.requireNonNull(adTechIdentifier);

        int buildId = -1;
        int dataFileGroupStatus = 0;
        if (mEnrollmentUtil != null) {
            buildId = mEnrollmentUtil.getBuildId();
            dataFileGroupStatus = mEnrollmentUtil.getFileGroupStatus();
        }
        int enrollmentRecordsCount = mEnrollmentDao.getEnrollmentRecordCountForLogging();
        EnrollmentData enrollmentData = getEnrollmentData(adTechIdentifier, apiType);

        if (enrollmentData == null) {
            sLogger.v(
                    "Enrollment data match not found for ad tech \"%s\" while calling API %d",
                    adTechIdentifier.toString(), apiNameLoggingId);
            mAdServicesLogger.logFledgeApiCallStats(
                    apiNameLoggingId, appPackageName, STATUS_CALLER_NOT_ALLOWED, /*latencyMs=*/ 0);
            if (mEnrollmentUtil != null) {
                mEnrollmentUtil.logEnrollmentFailedStats(
                        mAdServicesLogger,
                        buildId,
                        dataFileGroupStatus,
                        enrollmentRecordsCount,
                        adTechIdentifier.toString(),
                        EnrollmentStatus.ErrorCause.ENROLLMENT_NOT_FOUND_ERROR_CAUSE.getValue());
            }
            throw new AdTechNotAllowedException();
        }

        if (!isAllowedAccess(appPackageName, apiType, enrollmentData)) {
            sLogger.v(
                    "App package name \"%s\" with ad tech identifier \"%s\" not authorized to call"
                            + " API %d",
                    appPackageName, adTechIdentifier.toString(), apiNameLoggingId);
            mAdServicesLogger.logFledgeApiCallStats(
                    apiNameLoggingId, appPackageName, STATUS_CALLER_NOT_ALLOWED, /*latencyMs=*/ 0);
            mEnrollmentUtil.logEnrollmentFailedStats(
                    mAdServicesLogger,
                    buildId,
                    dataFileGroupStatus,
                    enrollmentRecordsCount,
                    adTechIdentifier.toString(),
                    EnrollmentStatus.ErrorCause.UNKNOWN_ERROR_CAUSE.getValue());
            throw new AdTechNotAllowedException();
        }

        // Check if enrollment is in blocklist.
        if (FlagsFactory.getFlags().isEnrollmentBlocklisted(enrollmentData.getEnrollmentId())) {
            sLogger.v(
                    "App package name \"%s\" with ad tech identifier \"%s\" not authorized to call"
                            + " API %d",
                    appPackageName, adTechIdentifier.toString(), apiNameLoggingId);
            mAdServicesLogger.logFledgeApiCallStats(
                    apiNameLoggingId, appPackageName, STATUS_CALLER_NOT_ALLOWED, /*latencyMs=*/ 0);
            mEnrollmentUtil.logEnrollmentFailedStats(
                    mAdServicesLogger,
                    buildId,
                    dataFileGroupStatus,
                    enrollmentRecordsCount,
                    adTechIdentifier.toString(),
                    EnrollmentStatus.ErrorCause.ENROLLMENT_BLOCKLISTED_ERROR_CAUSE.getValue());
            throw new AdTechNotAllowedException();
        }
    }

    private EnrollmentData getEnrollmentData(AdTechIdentifier adTechIdentifier, int apiType) {
        if (apiType == API_CUSTOM_AUDIENCES) {
            return mEnrollmentDao.getEnrollmentDataForFledgeByAdTechIdentifier(adTechIdentifier);
        } else if (apiType == API_PROTECTED_SIGNALS) {
            return mEnrollmentDao.getEnrollmentDataForPASByAdTechIdentifier(adTechIdentifier);
        } else if (apiType == API_AD_SELECTION) {
            EnrollmentData caEnrollment =
                    mEnrollmentDao.getEnrollmentDataForFledgeByAdTechIdentifier(adTechIdentifier);
            if (caEnrollment == null) {
                return mEnrollmentDao.getEnrollmentDataForPASByAdTechIdentifier(adTechIdentifier);
            }
            return caEnrollment;
        } else {
            throw new IllegalStateException(String.format(INVALID_API_TYPE, apiType));
        }
    }

    private Pair<AdTechIdentifier, EnrollmentData> getAdTechIdentifierEnrollmentDataPair(
            Uri uriForAdTech, int apiType) {
        if (apiType == API_CUSTOM_AUDIENCES) {
            return mEnrollmentDao.getEnrollmentDataForFledgeByMatchingAdTechIdentifier(
                    uriForAdTech);
        } else if (apiType == API_PROTECTED_SIGNALS) {
            return mEnrollmentDao.getEnrollmentDataForPASByMatchingAdTechIdentifier(uriForAdTech);
        } else if (apiType == API_AD_SELECTION) {
            Pair<AdTechIdentifier, EnrollmentData> caEnrollment =
                    mEnrollmentDao.getEnrollmentDataForFledgeByMatchingAdTechIdentifier(
                            uriForAdTech);
            if (caEnrollment == null) {
                return mEnrollmentDao.getEnrollmentDataForPASByMatchingAdTechIdentifier(
                        uriForAdTech);
            }
            return caEnrollment;
        } else {
            throw new IllegalStateException(
                    String.format(Locale.ENGLISH, INVALID_API_TYPE, apiType));
        }
    }

    private boolean isAllowedAccess(
            String appPackageName,
            @AppManifestConfigCall.ApiType int apiType,
            EnrollmentData enrollmentData) {
        if (apiType == API_CUSTOM_AUDIENCES) {
            return AppManifestConfigHelper.isAllowedCustomAudiencesAccess(
                    appPackageName, enrollmentData.getEnrollmentId());
        } else if (apiType == API_PROTECTED_SIGNALS) {
            return AppManifestConfigHelper.isAllowedProtectedSignalsAccess(
                    appPackageName, enrollmentData.getEnrollmentId());
        } else if (apiType == API_AD_SELECTION) {
            return AppManifestConfigHelper.isAllowedAdSelectionAccess(
                    appPackageName, enrollmentData.getEnrollmentId());
        } else {
            throw new IllegalStateException(
                    String.format(Locale.ENGLISH, INVALID_API_TYPE, apiType));
        }
    }

    /**
     * Extract and return an {@link AdTechIdentifier} from the given {@link Uri} after checking if
     * the ad tech is enrolled and authorized to perform the operation for the package.
     *
     * @param context API service context
     * @param appPackageName the package name to check against
     * @param uriForAdTech a {@link Uri} matching the ad tech to check against
     * @param apiNameLoggingId the logging ID of the API being called
     * @return an {@link AdTechIdentifier} which is allowed to perform the operation
     * @throws AdTechNotAllowedException if the ad tech is not authorized to perform the operation
     */
    @NonNull
    public AdTechIdentifier getAndAssertAdTechFromUriAllowed(
            @NonNull Context context,
            @NonNull String appPackageName,
            @NonNull Uri uriForAdTech,
            int apiNameLoggingId,
            @AppManifestConfigCall.ApiType int apiType)
            throws AdTechNotAllowedException {
        Objects.requireNonNull(context);
        Objects.requireNonNull(appPackageName);
        Objects.requireNonNull(uriForAdTech);

        int buildId = -1;
        int dataFileGroupStatus = 0;
        if (mEnrollmentUtil != null) {
            buildId = mEnrollmentUtil.getBuildId();
            dataFileGroupStatus = mEnrollmentUtil.getFileGroupStatus();
        }
        int enrollmentRecordsCount = mEnrollmentDao.getEnrollmentRecordCountForLogging();
        Pair<AdTechIdentifier, EnrollmentData> enrollmentResult =
                getAdTechIdentifierEnrollmentDataPair(uriForAdTech, apiType);

        if (enrollmentResult == null) {
            sLogger.v(
                    "Enrollment data match not found for URI \"%s\" while calling API %d",
                    uriForAdTech, apiNameLoggingId);
            mAdServicesLogger.logFledgeApiCallStats(
                    apiNameLoggingId, appPackageName, STATUS_CALLER_NOT_ALLOWED, /*latencyMs=*/ 0);
            mEnrollmentUtil.logEnrollmentFailedStats(
                    mAdServicesLogger,
                    buildId,
                    dataFileGroupStatus,
                    enrollmentRecordsCount,
                    uriForAdTech.toString(),
                    EnrollmentStatus.ErrorCause.ENROLLMENT_NOT_FOUND_ERROR_CAUSE.getValue());
            throw new AdTechNotAllowedException();
        }

        AdTechIdentifier adTechIdentifier = enrollmentResult.first;
        EnrollmentData enrollmentData = enrollmentResult.second;

        int failureReason;

        boolean isAllowedAccess = isAllowedAccess(appPackageName, apiType, enrollmentData);

        boolean isEnrollmentBlocklisted =
                FlagsFactory.getFlags().isEnrollmentBlocklisted(enrollmentData.getEnrollmentId());
        int errorCause = EnrollmentStatus.ErrorCause.UNKNOWN_ERROR_CAUSE.getValue();
        if (!isAllowedAccess || isEnrollmentBlocklisted) {
            sLogger.v(
                    "App package name \"%s\" with ad tech identifier \"%s\" from URI \"%s\" not"
                            + " authorized to call API %d",
                    appPackageName, adTechIdentifier.toString(), uriForAdTech, apiNameLoggingId);
            if (!isAllowedAccess) {
                failureReason = FAILURE_REASON_MANIFEST_ADSERVICES_CONFIG_NO_PERMISSION;
            } else {
                failureReason = FAILURE_REASON_ENROLLMENT_BLOCKLISTED;
                errorCause =
                        EnrollmentStatus.ErrorCause.ENROLLMENT_BLOCKLISTED_ERROR_CAUSE.getValue();
            }
            mAdServicesLogger.logFledgeApiCallStats(
                    apiNameLoggingId, appPackageName, STATUS_CALLER_NOT_ALLOWED, /*latencyMs=*/ 0);
            mEnrollmentUtil.logEnrollmentFailedStats(
                    mAdServicesLogger,
                    buildId,
                    dataFileGroupStatus,
                    enrollmentRecordsCount,
                    uriForAdTech.toString(),
                    errorCause);
            throw new AdTechNotAllowedException();
        }

        return adTechIdentifier;
    }

    /**
     * Check if a certain ad tech is enrolled for FLEDGE.
     *
     * @param adTechIdentifier the ad tech to check against
     * @param apiNameLoggingId the id of the api being called
     * @throws AdTechNotAllowedException if the ad tech is not enrolled in FLEDGE
     */
    public void assertAdTechEnrolled(
            @NonNull AdTechIdentifier adTechIdentifier, int apiNameLoggingId)
            throws AdTechNotAllowedException {
        Objects.requireNonNull(adTechIdentifier);

        EnrollmentData enrollmentData =
                mEnrollmentDao.getEnrollmentDataForFledgeByAdTechIdentifier(adTechIdentifier);

        if (enrollmentData == null) {
            sLogger.v(
                    "Enrollment data match not found for ad tech \"%s\" while calling API %d",
                    adTechIdentifier.toString(), apiNameLoggingId);
            mAdServicesLogger.logFledgeApiCallStats(
                    apiNameLoggingId, STATUS_CALLER_NOT_ALLOWED, /*latencyMs=*/ 0);

            int buildId = -1;
            int dataFileGroupStatus = 0;
            if (mEnrollmentUtil != null) {
                buildId = mEnrollmentUtil.getBuildId();
                dataFileGroupStatus = mEnrollmentUtil.getFileGroupStatus();
            }
            mEnrollmentUtil.logEnrollmentFailedStats(
                    mAdServicesLogger,
                    buildId,
                    dataFileGroupStatus,
                    mEnrollmentDao.getEnrollmentRecordCountForLogging(),
                    adTechIdentifier.toString(),
                    EnrollmentStatus.ErrorCause.ENROLLMENT_NOT_FOUND_ERROR_CAUSE.getValue());
            throw new AdTechNotAllowedException();
        }
    }

    /**
     * Internal exception for easy assertion catches specific to checking that a caller matches the
     * given package name.
     *
     * <p>This exception is not meant to be exposed externally and should not be passed outside of
     * the service.
     */
    public static class CallerMismatchException extends SecurityException {
        /**
         * Creates a {@link CallerMismatchException}, used in cases where a caller should match the
         * package name provided to the API.
         */
        public CallerMismatchException() {
            super(
                    AdServicesStatusUtils
                            .SECURITY_EXCEPTION_CALLER_NOT_ALLOWED_ON_BEHALF_ERROR_MESSAGE);
        }
    }

    /**
     * Internal exception for easy assertion catches specific to checking that an ad tech is allowed
     * to use the PP APIs.
     *
     * <p>This exception is not meant to be exposed externally and should not be passed outside of
     * the service.
     */
    public static class AdTechNotAllowedException extends SecurityException {
        /**
         * Creates a {@link AdTechNotAllowedException}, used in cases where an ad tech should have
         * been allowed to use the PP APIs.
         */
        public AdTechNotAllowedException() {
            super(AdServicesStatusUtils.SECURITY_EXCEPTION_CALLER_NOT_ALLOWED_ERROR_MESSAGE);
        }
    }
}
