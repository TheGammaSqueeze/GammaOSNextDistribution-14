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
package com.android.adservices.service.appsetid;

import static android.adservices.common.AdServicesStatusUtils.FAILURE_REASON_CALLING_PACKAGE_DOES_NOT_BELONG_TO_CALLING_ID;
import static android.adservices.common.AdServicesStatusUtils.FAILURE_REASON_CALLING_PACKAGE_NOT_FOUND;
import static android.adservices.common.AdServicesStatusUtils.FAILURE_REASON_PACKAGE_NOT_IN_ALLOWLIST;
import static android.adservices.common.AdServicesStatusUtils.FAILURE_REASON_UNSET;
import static android.adservices.common.AdServicesStatusUtils.STATUS_BACKGROUND_CALLER;
import static android.adservices.common.AdServicesStatusUtils.STATUS_CALLER_NOT_ALLOWED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
import static android.adservices.common.AdServicesStatusUtils.STATUS_RATE_LIMIT_REACHED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;
import static android.adservices.common.AdServicesStatusUtils.STATUS_UNAUTHORIZED;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_CLASS__APPSETID;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__GET_APPSETID;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_CALLBACK_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PACKAGE_NAME_NOT_FOUND_EXCEPTION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__RATE_LIMIT_CALLBACK_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__APP_SET_ID;

import android.adservices.appsetid.GetAppSetIdParam;
import android.adservices.appsetid.IAppSetIdService;
import android.adservices.appsetid.IGetAppSetIdCallback;
import android.adservices.common.AdServicesStatusUtils;
import android.adservices.common.CallerMetadata;
import android.annotation.NonNull;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.RemoteException;

import com.android.adservices.LogUtil;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.AllowLists;
import com.android.adservices.service.common.AppImportanceFilter;
import com.android.adservices.service.common.AppImportanceFilter.WrongCallingApplicationStateException;
import com.android.adservices.service.common.SdkRuntimeUtil;
import com.android.adservices.service.common.Throttler;
import com.android.adservices.service.common.compat.ProcessCompatUtils;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesStatsLog;
import com.android.adservices.service.stats.ApiCallStats;
import com.android.adservices.shared.util.Clock;

import java.util.concurrent.Executor;

/**
 * Implementation of {@link IAppSetIdService}.
 *
 * @hide
 */
public class AppSetIdServiceImpl extends IAppSetIdService.Stub {
    private final Context mContext;
    private final AppSetIdWorker mAppSetIdWorker;
    private static final Executor sBackgroundExecutor = AdServicesExecutors.getBackgroundExecutor();
    private final AdServicesLogger mAdServicesLogger;
    private final Clock mClock;
    private final Flags mFlags;
    private final Throttler mThrottler;
    private final AppImportanceFilter mAppImportanceFilter;

    public AppSetIdServiceImpl(
            Context context,
            AppSetIdWorker appsetidWorker,
            AdServicesLogger adServicesLogger,
            Clock clock,
            Flags flags,
            Throttler throttler,
            AppImportanceFilter appImportanceFilter) {
        mContext = context;
        mAppSetIdWorker = appsetidWorker;
        mAdServicesLogger = adServicesLogger;
        mClock = clock;
        mFlags = flags;
        mThrottler = throttler;
        mAppImportanceFilter = appImportanceFilter;
    }

    @Override
    public void getAppSetId(
            @NonNull GetAppSetIdParam appSetIdParam,
            @NonNull CallerMetadata callerMetadata,
            @NonNull IGetAppSetIdCallback callback) {

        if (isThrottled(appSetIdParam, callback)) return;

        final long startServiceTime = mClock.elapsedRealtime();
        final String packageName = appSetIdParam.getAppPackageName();
        final String sdkPackageName = appSetIdParam.getSdkPackageName();

        // We need to save the Calling Uid before offloading to the background executor. Otherwise
        // the Binder.getCallingUid will return the PPAPI process Uid. This also needs to be final
        // since it's used in the lambda.
        final int callingUid = Binder.getCallingUidOrThrow();

        sBackgroundExecutor.execute(
                () -> {
                    ApiCallStats.Result result = null;
                    try {
                        result =
                                canCallerInvokeAppSetIdService(appSetIdParam, callingUid, callback);
                        if (!result.isSuccess()) {
                            return;
                        }
                        mAppSetIdWorker.getAppSetId(packageName, callingUid, callback);

                    } catch (Exception e) {
                        LogUtil.e(e, "Unable to send result to the callback");
                        ErrorLogUtil.e(
                                e,
                                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_CALLBACK_ERROR,
                                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__APP_SET_ID);
                        result =
                                ApiCallStats.failureResult(
                                        STATUS_INTERNAL_ERROR, FAILURE_REASON_UNSET);
                    } finally {
                        long binderCallStartTimeMillis = callerMetadata.getBinderElapsedTimestamp();
                        long serviceLatency = mClock.elapsedRealtime() - startServiceTime;
                        // Double it to simulate the return binder time is same to call binder time
                        long binderLatency = (startServiceTime - binderCallStartTimeMillis) * 2;

                        final int apiLatency = (int) (serviceLatency + binderLatency);
                        mAdServicesLogger.logApiCallStats(
                                new ApiCallStats.Builder()
                                        .setCode(AdServicesStatsLog.AD_SERVICES_API_CALLED)
                                        .setApiClass(AD_SERVICES_API_CALLED__API_CLASS__APPSETID)
                                        .setApiName(AD_SERVICES_API_CALLED__API_NAME__GET_APPSETID)
                                        .setAppPackageName(packageName)
                                        .setSdkPackageName(sdkPackageName)
                                        .setLatencyMillisecond(apiLatency)
                                        .setResult(result)
                                        .build());
                    }
                });
    }

    // Throttle the AppSetId API.
    // Return true if we should throttle (don't allow the API call).
    private boolean isThrottled(GetAppSetIdParam appSetIdParam, IGetAppSetIdCallback callback) {
        boolean throttled =
                !mThrottler.tryAcquire(
                        Throttler.ApiKey.APPSETID_API_APP_PACKAGE_NAME,
                        appSetIdParam.getAppPackageName());

        if (throttled) {
            LogUtil.e("Rate Limit Reached for APPSETID_API");
            try {
                callback.onError(STATUS_RATE_LIMIT_REACHED);
            } catch (RemoteException e) {
                LogUtil.e(e, "Fail to call the callback on Rate Limit Reached.");
                ErrorLogUtil.e(
                        e,
                        AD_SERVICES_ERROR_REPORTED__ERROR_CODE__RATE_LIMIT_CALLBACK_FAILURE,
                        AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__APP_SET_ID);
            }
            return true;
        }
        return false;
    }

    // Enforce whether caller is from foreground.
    private void enforceForeground(int callingUid) {
        // If caller calls AppSetId API from Sandbox, regard it as foreground.
        // Also enable a flag to force switch on/off this enforcing.
        if (ProcessCompatUtils.isSdkSandboxUid(callingUid)
                || !mFlags.getEnforceForegroundStatusForAppSetId()) {
            return;
        }

        // Call utility method in AppImportanceFilter to enforce foreground status
        //  Throw WrongCallingApplicationStateException  if the assertion fails.
        mAppImportanceFilter.assertCallerIsInForeground(
                callingUid, AD_SERVICES_API_CALLED__API_NAME__GET_APPSETID, null);
    }

    /**
     * Check whether caller can invoke the AppSetId API. The caller is not allowed to do it when one
     * of the following occurs:
     *
     * <ul>
     *   <li>Caller is not allowed - not present in the allowed list.
     * </ul>
     *
     * @param appSetIdParam {@link GetAppSetIdParam} to get information about the request.
     * @param callback {@link IGetAppSetIdCallback} to invoke when caller is not allowed.
     * @return ApiCallStats.Result containing API response status code and failure reason.
     */
    private ApiCallStats.Result canCallerInvokeAppSetIdService(
            GetAppSetIdParam appSetIdParam, int callingUid, IGetAppSetIdCallback callback) {
        // Enforce caller calls AppSetId API from foreground.
        try {
            enforceForeground(callingUid);
        } catch (WrongCallingApplicationStateException backgroundCaller) {
            invokeCallbackWithStatus(
                    callback, STATUS_BACKGROUND_CALLER, backgroundCaller.getMessage());
            return ApiCallStats.failureResult(STATUS_BACKGROUND_CALLER, FAILURE_REASON_UNSET);
        }

        // This needs to access PhFlag which requires READ_DEVICE_CONFIG which
        // is not granted for binder thread. So we have to check it with one
        // of non-binder thread of the PPAPI.
        boolean appCanUsePpapi =
                AllowLists.isPackageAllowListed(
                        mFlags.getPpapiAppAllowList(), appSetIdParam.getAppPackageName());
        if (!appCanUsePpapi) {
            invokeCallbackWithStatus(
                    callback,
                    STATUS_CALLER_NOT_ALLOWED,
                    "Unauthorized caller. Caller is not allowed.");
            return ApiCallStats.failureResult(
                    STATUS_CALLER_NOT_ALLOWED, FAILURE_REASON_PACKAGE_NOT_IN_ALLOWLIST);
        }

        // Check whether calling package belongs to the callingUid
        ApiCallStats.Result result =
                enforceCallingPackageBelongsToUid(appSetIdParam.getAppPackageName(), callingUid);
        if (result.getResultCode() != STATUS_SUCCESS) {
            invokeCallbackWithStatus(callback, result.getResultCode(), "Caller is not authorized.");
            return result;
        }
        return ApiCallStats.successResult();
    }

    private void invokeCallbackWithStatus(
            IGetAppSetIdCallback callback,
            @AdServicesStatusUtils.StatusCode int statusCode,
            String message) {
        LogUtil.e(message);
        try {
            callback.onError(statusCode);
        } catch (RemoteException e) {
            LogUtil.e(e, String.format("Fail to call the callback. %s", message));
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_CALLBACK_ERROR,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__APP_SET_ID);
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
            LogUtil.e(e, callingPackage + " not found");
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PACKAGE_NAME_NOT_FOUND_EXCEPTION,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__APP_SET_ID);
            return ApiCallStats.failureResult(
                    STATUS_UNAUTHORIZED, FAILURE_REASON_CALLING_PACKAGE_NOT_FOUND);
        }
        if (packageUid != appCallingUid) {
            LogUtil.e(callingPackage + " does not belong to uid " + callingUid);
            return ApiCallStats.failureResult(
                    STATUS_UNAUTHORIZED,
                    FAILURE_REASON_CALLING_PACKAGE_DOES_NOT_BELONG_TO_CALLING_ID);
        }
        return ApiCallStats.successResult();
    }
}
