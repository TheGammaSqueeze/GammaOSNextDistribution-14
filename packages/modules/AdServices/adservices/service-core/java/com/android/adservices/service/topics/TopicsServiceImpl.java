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
package com.android.adservices.service.topics;

import static android.adservices.common.AdServicesStatusUtils.FAILURE_REASON_CALLING_PACKAGE_DOES_NOT_BELONG_TO_CALLING_ID;
import static android.adservices.common.AdServicesStatusUtils.FAILURE_REASON_CALLING_PACKAGE_NOT_FOUND;
import static android.adservices.common.AdServicesStatusUtils.FAILURE_REASON_ENROLLMENT_BLOCKLISTED;
import static android.adservices.common.AdServicesStatusUtils.FAILURE_REASON_ENROLLMENT_INVALID_ID;
import static android.adservices.common.AdServicesStatusUtils.FAILURE_REASON_ENROLLMENT_MATCH_NOT_FOUND;
import static android.adservices.common.AdServicesStatusUtils.FAILURE_REASON_MANIFEST_ADSERVICES_CONFIG_NO_PERMISSION;
import static android.adservices.common.AdServicesStatusUtils.FAILURE_REASON_PACKAGE_NOT_IN_ALLOWLIST;
import static android.adservices.common.AdServicesStatusUtils.FAILURE_REASON_UNSET;
import static android.adservices.common.AdServicesStatusUtils.STATUS_BACKGROUND_CALLER;
import static android.adservices.common.AdServicesStatusUtils.STATUS_CALLER_NOT_ALLOWED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
import static android.adservices.common.AdServicesStatusUtils.STATUS_INVALID_ARGUMENT;
import static android.adservices.common.AdServicesStatusUtils.STATUS_PERMISSION_NOT_REQUESTED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_RATE_LIMIT_REACHED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;
import static android.adservices.common.AdServicesStatusUtils.STATUS_UNAUTHORIZED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_USER_CONSENT_REVOKED;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_CLASS__TARGETING;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__GET_TOPICS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__GET_TOPICS_PREVIEW_API;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_CALLBACK_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PACKAGE_NAME_NOT_FOUND_EXCEPTION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__RATE_LIMIT_CALLBACK_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_REQUEST_EMPTY_SDK_NAME;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS;

import android.adservices.common.AdServicesStatusUtils;
import android.adservices.common.CallerMetadata;
import android.adservices.topics.GetTopicsParam;
import android.adservices.topics.IGetTopicsCallback;
import android.adservices.topics.ITopicsService;
import android.annotation.NonNull;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Build;
import android.os.RemoteException;
import android.text.TextUtils;

import androidx.annotation.RequiresApi;

import com.android.adservices.LoggerFactory;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.AllowLists;
import com.android.adservices.service.common.AppImportanceFilter;
import com.android.adservices.service.common.AppImportanceFilter.WrongCallingApplicationStateException;
import com.android.adservices.service.common.AppManifestConfigHelper;
import com.android.adservices.service.common.PermissionHelper;
import com.android.adservices.service.common.SdkRuntimeUtil;
import com.android.adservices.service.common.Throttler;
import com.android.adservices.service.common.compat.ProcessCompatUtils;
import com.android.adservices.service.consent.AdServicesApiConsent;
import com.android.adservices.service.consent.AdServicesApiType;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.enrollment.EnrollmentData;
import com.android.adservices.service.enrollment.EnrollmentStatus;
import com.android.adservices.service.enrollment.EnrollmentUtil;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesStatsLog;
import com.android.adservices.service.stats.ApiCallStats;
import com.android.adservices.shared.util.Clock;

import java.util.concurrent.Executor;

/**
 * Implementation of {@link ITopicsService}.
 *
 * @hide
 */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
public class TopicsServiceImpl extends ITopicsService.Stub {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getTopicsLogger();
    private static final Executor sBackgroundExecutor = AdServicesExecutors.getBackgroundExecutor();
    private final Context mContext;
    private final TopicsWorker mTopicsWorker;
    private final AdServicesLogger mAdServicesLogger;
    private final ConsentManager mConsentManager;
    private final Clock mClock;
    private final Flags mFlags;
    private final Throttler mThrottler;
    private final EnrollmentDao mEnrollmentDao;
    private final AppImportanceFilter mAppImportanceFilter;

    public TopicsServiceImpl(
            Context context,
            TopicsWorker topicsWorker,
            ConsentManager consentManager,
            AdServicesLogger adServicesLogger,
            Clock clock,
            Flags flags,
            Throttler throttler,
            EnrollmentDao enrollmentDao,
            AppImportanceFilter appImportanceFilter) {
        mContext = context;
        mTopicsWorker = topicsWorker;
        mConsentManager = consentManager;
        mAdServicesLogger = adServicesLogger;
        mClock = clock;
        mFlags = flags;
        mThrottler = throttler;
        mEnrollmentDao = enrollmentDao;
        mAppImportanceFilter = appImportanceFilter;
    }

    @Override
    public void getTopics(
            @NonNull GetTopicsParam topicsParam,
            @NonNull CallerMetadata callerMetadata,
            @NonNull IGetTopicsCallback callback) {

        if (isThrottled(topicsParam, callback)) return;

        final long startServiceTime = mClock.elapsedRealtime();
        final String packageName = topicsParam.getAppPackageName();
        final String sdkName = topicsParam.getSdkName();

        // We need to save the Calling Uid before offloading to the background executor. Otherwise,
        // the Binder.getCallingUid will return the PPAPI process Uid. This also needs to be final
        // since it's used in the lambda.
        final int callingUid = Binder.getCallingUidOrThrow();

        // Check the permission in the same thread since we're looking for caller's permissions.
        // Note: The permission check uses sdk sandbox calling package name since PackageManager
        // checks if the permission is declared in the manifest of that package name.
        boolean hasTopicsPermission =
                PermissionHelper.hasTopicsPermission(mContext, packageName, callingUid);

        sBackgroundExecutor.execute(
                () -> {
                    ApiCallStats.Result result = null;
                    try {
                        if (mFlags.getTopicsDisableDirectAppCalls()) {
                            // Check if the request is valid.
                            if (!validateRequest(topicsParam, callback)) {
                                // Return early if the request is invalid.
                                sLogger.e("Invalid request %s", topicsParam);
                                result =
                                        ApiCallStats.failureResult(
                                                STATUS_INVALID_ARGUMENT, FAILURE_REASON_UNSET);
                                return;
                            }
                        }

                        result =
                                canCallerInvokeTopicsService(
                                        hasTopicsPermission, topicsParam, callingUid, callback);
                        if (!result.isSuccess()) {
                            return;
                        }

                        callback.onResult(mTopicsWorker.getTopics(packageName, sdkName));

                        if (topicsParam.shouldRecordObservation()) {
                            mTopicsWorker.recordUsage(
                                    topicsParam.getAppPackageName(), topicsParam.getSdkName());
                        }
                    } catch (RemoteException e) {
                        sLogger.e(e, "Unable to send result to the callback");
                        ErrorLogUtil.e(
                                e,
                                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_CALLBACK_ERROR,
                                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS);
                        result =
                                ApiCallStats.failureResult(
                                        STATUS_INTERNAL_ERROR, FAILURE_REASON_UNSET);
                    } finally {
                        long binderCallStartTimeMillis = callerMetadata.getBinderElapsedTimestamp();
                        long serviceLatency = mClock.elapsedRealtime() - startServiceTime;
                        // Double it to simulate the return binder time is same to call binder time
                        long binderLatency = (startServiceTime - binderCallStartTimeMillis) * 2;

                        final int apiLatency = (int) (serviceLatency + binderLatency);
                        final int apiName =
                                topicsParam.shouldRecordObservation()
                                        ? AD_SERVICES_API_CALLED__API_NAME__GET_TOPICS
                                        : AD_SERVICES_API_CALLED__API_NAME__GET_TOPICS_PREVIEW_API;
                        mAdServicesLogger.logApiCallStats(
                                new ApiCallStats.Builder()
                                        .setCode(AdServicesStatsLog.AD_SERVICES_API_CALLED)
                                        .setApiClass(AD_SERVICES_API_CALLED__API_CLASS__TARGETING)
                                        .setApiName(apiName)
                                        .setAppPackageName(packageName)
                                        .setSdkPackageName(sdkName)
                                        .setLatencyMillisecond(apiLatency)
                                        .setResult(result)
                                        .build());
                    }
                });
    }

    // Checks if GetTopicsParam is a valid request.
    private static boolean validateRequest(
            GetTopicsParam topicsParam, IGetTopicsCallback callback) {
        // Return false if sdkName is empty or null.
        if (TextUtils.isEmpty(topicsParam.getSdkName())) {
            ErrorLogUtil.e(
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_REQUEST_EMPTY_SDK_NAME,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS);
            invokeCallbackWithStatus(
                    callback,
                    STATUS_INVALID_ARGUMENT,
                    "Direct app calls are not supported for Topics API. Sdk name should not "
                            + "be null or empty");
            return false;
        }
        return true;
    }

    // Throttle the Topics API.
    // Return true if we should throttle (don't allow the API call).
    private boolean isThrottled(GetTopicsParam topicsParam, IGetTopicsCallback callback) {
        // There are 2 cases for throttling:
        // Case 1: the App calls Topics API directly, not via an SDK. In this case,
        // the SdkName == Empty
        // Case 2: the SDK calls Topics API.
        boolean throttled =
                TextUtils.isEmpty(topicsParam.getSdkName())
                        ? !mThrottler.tryAcquire(
                                Throttler.ApiKey.TOPICS_API_APP_PACKAGE_NAME,
                                topicsParam.getAppPackageName())
                        : !mThrottler.tryAcquire(
                                Throttler.ApiKey.TOPICS_API_SDK_NAME, topicsParam.getSdkName());

        if (throttled) {
            sLogger.e("Rate Limit Reached for TOPICS_API");
            try {
                callback.onFailure(STATUS_RATE_LIMIT_REACHED);
            } catch (RemoteException e) {
                sLogger.e(e, "Fail to call the callback on Rate Limit Reached.");
                ErrorLogUtil.e(
                        e,
                        AD_SERVICES_ERROR_REPORTED__ERROR_CODE__RATE_LIMIT_CALLBACK_FAILURE,
                        AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS);
            }
            return true;
        }
        return false;
    }

    // Enforce whether caller is from foreground.
    private void enforceForeground(int callingUid, @NonNull String sdkName) {
        // If caller calls Topics API from Sandbox, regard it as foreground.
        // Also enable a flag to force switch on/off this enforcing.
        if (ProcessCompatUtils.isSdkSandboxUid(callingUid)
                || !mFlags.getEnforceForegroundStatusForTopics()) {
            return;
        }

        // Call utility method in AppImportanceFilter to enforce foreground status
        //  Throw WrongCallingApplicationStateException  if the assertion fails.
        mAppImportanceFilter.assertCallerIsInForeground(
                callingUid, AD_SERVICES_API_CALLED__API_NAME__GET_TOPICS, sdkName);
    }

    /**
     * Check whether caller can invoke the Topics API. The caller is not allowed to do it when one
     * of the following occurs:
     *
     * <ul>
     *   <li>Permission was not requested.
     *   <li>Caller is not allowed - not present in the allowed list.
     *   <li>User consent was revoked.
     * </ul>
     *
     * @param sufficientPermission boolean which tells whether caller has sufficient permissions.
     * @param topicsParam {@link GetTopicsParam} to get information about the request.
     * @param callback {@link IGetTopicsCallback} to invoke when caller is not allowed.
     * @return ApiCallStats.Result containing API response status code and failure reason.
     */
    private ApiCallStats.Result canCallerInvokeTopicsService(
            boolean sufficientPermission,
            GetTopicsParam topicsParam,
            int callingUid,
            IGetTopicsCallback callback) {
        // Enforce caller calls Topics API from foreground
        try {
            enforceForeground(callingUid, topicsParam.getSdkName());
        } catch (WrongCallingApplicationStateException backgroundCaller) {
            sLogger.v("STATUS_BACKGROUND_CALLER: Failed foreground check");
            invokeCallbackWithStatus(
                    callback, STATUS_BACKGROUND_CALLER, backgroundCaller.getMessage());
            return ApiCallStats.failureResult(STATUS_BACKGROUND_CALLER, FAILURE_REASON_UNSET);
        }

        if (!sufficientPermission) {
            sLogger.v("STATUS_PERMISSION_NOT_REQUESTED: Caller did not declare permission");
            invokeCallbackWithStatus(
                    callback,
                    STATUS_PERMISSION_NOT_REQUESTED,
                    "Unauthorized caller. Permission not requested.");
            return ApiCallStats.failureResult(
                    STATUS_PERMISSION_NOT_REQUESTED, FAILURE_REASON_UNSET);
        }

        // This needs to access PhFlag which requires READ_DEVICE_CONFIG which
        // is not granted for binder thread. So we have to check it with one
        // of non-binder thread of the PPAPI.
        if (!AllowLists.isSignatureAllowListed(
                mContext,
                mFlags.getPpapiAppSignatureAllowList(),
                topicsParam.getAppPackageName())) {
            sLogger.v("STATUS_CALLER_NOT_ALLOWED: Caller signature not allowlisted");
            invokeCallbackWithStatus(
                    callback,
                    STATUS_CALLER_NOT_ALLOWED,
                    "Unauthorized caller. Signatures for calling package not allowed.");
            return ApiCallStats.failureResult(
                    STATUS_CALLER_NOT_ALLOWED, FAILURE_REASON_PACKAGE_NOT_IN_ALLOWLIST);
        }

        // Check whether calling package belongs to the callingUid
        ApiCallStats.Result result =
                enforceCallingPackageBelongsToUid(topicsParam.getAppPackageName(), callingUid);
        if (result.getResultCode() != STATUS_SUCCESS) {
            sLogger.v("STATUS_UNAUTHORIZED: Caller UID mismatch");
            invokeCallbackWithStatus(callback, result.getResultCode(), "Caller is not authorized.");
            return ApiCallStats.failureResult(result.getResultCode(), result.getFailureReason());
        }

        AdServicesApiConsent userConsent = mConsentManager.getConsent(AdServicesApiType.TOPICS);

        if (!userConsent.isGiven()) {
            sLogger.v("STATUS_USER_CONSENT_REVOKED: User consent revoked");
            invokeCallbackWithStatus(
                    callback, STATUS_USER_CONSENT_REVOKED, "User consent revoked.");
            return ApiCallStats.failureResult(STATUS_USER_CONSENT_REVOKED, FAILURE_REASON_UNSET);
        }

        // The app developer declares which SDKs they would like to allow Topics
        // access to use the enrollment ID. Get the enrollment ID for this SDK and
        // check that against the app's manifest.
        if (!mFlags.isDisableTopicsEnrollmentCheck() && !topicsParam.getSdkName().isEmpty()) {
            String errorString = "STATUS_SUCCESS";
            EnrollmentData enrollmentData =
                    mEnrollmentDao.getEnrollmentDataFromSdkName(topicsParam.getSdkName());
            boolean permitted = true;
            int failureReason = FAILURE_REASON_UNSET;

            if (enrollmentData == null) {
                errorString = "STATUS_CALLER_NOT_ALLOWED: Enrollment not found";
                permitted = false;
                failureReason = FAILURE_REASON_ENROLLMENT_MATCH_NOT_FOUND;
            } else if (enrollmentData.getEnrollmentId() == null) {
                errorString = "STATUS_CALLER_NOT_ALLOWED: Enrollment ID invalid";
                permitted = false;
                failureReason = FAILURE_REASON_ENROLLMENT_INVALID_ID;
            } else {
                if (!AppManifestConfigHelper.isAllowedTopicsAccess(
                        ProcessCompatUtils.isSdkSandboxUid(callingUid),
                        topicsParam.getAppPackageName(),
                        enrollmentData.getEnrollmentId())) {
                    errorString = "STATUS_CALLER_NOT_ALLOWED: App manifest config failed";
                    permitted = false;
                    failureReason = FAILURE_REASON_MANIFEST_ADSERVICES_CONFIG_NO_PERMISSION;
                } else if (mFlags.isEnrollmentBlocklisted(enrollmentData.getEnrollmentId())) {
                    errorString = "STATUS_CALLER_NOT_ALLOWED: Enrollment blocklisted";
                    permitted = false;
                    failureReason = FAILURE_REASON_ENROLLMENT_BLOCKLISTED;
                }
            }

            sLogger.v("Checked Topics enrollment: %s", errorString);

            if (!permitted) {
                invokeCallbackWithStatus(
                        callback, STATUS_CALLER_NOT_ALLOWED, "Caller is not authorized.");
                EnrollmentUtil enrollmentUtil = EnrollmentUtil.getInstance(mContext);
                Integer buildId = enrollmentUtil.getBuildId();
                Integer dataFileGroupStatus = enrollmentUtil.getFileGroupStatus();
                enrollmentUtil.logEnrollmentFailedStats(
                        mAdServicesLogger,
                        buildId,
                        dataFileGroupStatus,
                        mEnrollmentDao.getEnrollmentRecordCountForLogging(),
                        topicsParam.getSdkName(),
                        EnrollmentStatus.ErrorCause.UNKNOWN_ERROR_CAUSE.getValue());
                return ApiCallStats.failureResult(STATUS_CALLER_NOT_ALLOWED, failureReason);
            }
        }

        return ApiCallStats.successResult();
    }

    private static void invokeCallbackWithStatus(
            IGetTopicsCallback callback,
            @AdServicesStatusUtils.StatusCode int statusCode,
            String message) {
        sLogger.e(message);
        try {
            callback.onFailure(statusCode);
        } catch (RemoteException e) {
            sLogger.e(e, String.format("Fail to call the callback. %s", message));
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_CALLBACK_ERROR,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS);
        }
    }

    // Enforce that the callingPackage has the callingUid.
    private ApiCallStats.Result enforceCallingPackageBelongsToUid(
            String callingPackage, int callingUid) {
        int appCallingUid = SdkRuntimeUtil.getCallingAppUid(callingUid);
        int packageUid;
        try {
            packageUid = mContext.getPackageManager().getPackageUid(callingPackage, /* flags */ 0);
        } catch (PackageManager.NameNotFoundException e) {
            sLogger.e(e, callingPackage + " not found");
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PACKAGE_NAME_NOT_FOUND_EXCEPTION,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS);
            return ApiCallStats.failureResult(
                    STATUS_UNAUTHORIZED, FAILURE_REASON_CALLING_PACKAGE_NOT_FOUND);
        }
        if (packageUid != appCallingUid) {
            sLogger.e(callingPackage + " does not belong to uid " + callingUid);
            return ApiCallStats.failureResult(
                    STATUS_UNAUTHORIZED,
                    FAILURE_REASON_CALLING_PACKAGE_DOES_NOT_BELONG_TO_CALLING_ID);
        }
        return ApiCallStats.successResult();
    }

    /** Init the Topics Service. */
    public void init() {
        // This is to prevent cold-start latency on getTopics API.
        // Load cache when the service is created.
        // The recommended pattern is:
        // 1) In app startup, wake up the TopicsService.
        // 2) The TopicsService will load the Topics Cache from DB into memory.
        // 3) Later, when the app calls Topics API, the returned Topics will be served
        // from
        // Cache in memory.
        sBackgroundExecutor.execute(mTopicsWorker::loadCache);
    }
}
