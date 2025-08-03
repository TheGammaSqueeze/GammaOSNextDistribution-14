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

package com.android.adservices.service.signals;

import static android.adservices.common.AdServicesStatusUtils.FAILURE_REASON_UNSET;

import static com.android.adservices.service.common.Throttler.ApiKey.PROTECTED_SIGNAL_API_UPDATE_SIGNALS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_CLASS__FLEDGE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__UPDATE_SIGNALS;

import android.adservices.common.AdServicesPermissions;
import android.adservices.common.AdServicesStatusUtils;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CallerMetadata;
import android.adservices.common.FledgeErrorResponse;
import android.adservices.signals.IProtectedSignalsService;
import android.adservices.signals.UpdateSignalsCallback;
import android.adservices.signals.UpdateSignalsInput;
import android.annotation.NonNull;
import android.content.Context;
import android.os.Build;
import android.os.RemoteException;
import android.os.SystemClock;

import androidx.annotation.RequiresApi;

import com.android.adservices.LoggerFactory;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.signals.ProtectedSignalsDatabase;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.AdTechUriValidator;
import com.android.adservices.service.common.AppImportanceFilter;
import com.android.adservices.service.common.CallingAppUidSupplier;
import com.android.adservices.service.common.CallingAppUidSupplierBinderImpl;
import com.android.adservices.service.common.FledgeAllowListsFilter;
import com.android.adservices.service.common.FledgeAuthorizationFilter;
import com.android.adservices.service.common.ProtectedSignalsServiceFilter;
import com.android.adservices.service.common.Throttler;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.devapi.DevContextFilter;
import com.android.adservices.service.exception.FilterException;
import com.android.adservices.service.signals.evict.SignalEvictionController;
import com.android.adservices.service.signals.updateprocessors.UpdateEncoderEventHandler;
import com.android.adservices.service.signals.updateprocessors.UpdateProcessorSelector;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.AdServicesStatsLog;
import com.android.adservices.service.stats.AdsRelevanceExecutionLogger;
import com.android.adservices.service.stats.AdsRelevanceExecutionLoggerFactory;
import com.android.adservices.service.stats.ApiCallStats;
import com.android.internal.annotations.VisibleForTesting;

import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

/** Implementation of the Protected Signals service. */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
public class ProtectedSignalsServiceImpl extends IProtectedSignalsService.Stub {

    public static final int TIMEOUT_MS = 5000;
    public static final long MAX_SIZE_BYTES = 10000;
    public static final String ADTECH_CALLER_NAME = "caller";
    public static final String CLASS_NAME = "ProtectedSignalsServiceImpl";
    public static final String FIELD_NAME = "updateUri";
    private static final String EMPTY_PACKAGE_NAME = "";
    private static final String EMPTY_SDK_NAME = "";

    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    @NonNull private final Context mContext;
    @NonNull private final UpdateSignalsOrchestrator mUpdateSignalsOrchestrator;
    @NonNull private final FledgeAuthorizationFilter mFledgeAuthorizationFilter;
    @NonNull private final ConsentManager mConsentManager;
    @NonNull private final ExecutorService mExecutorService;
    @NonNull private final DevContextFilter mDevContextFilter;
    @NonNull private final AdServicesLogger mAdServicesLogger;
    @NonNull private final Flags mFlags;
    @NonNull private final CallingAppUidSupplier mCallingAppUidSupplier;

    @NonNull private final ProtectedSignalsServiceFilter mProtectedSignalsServiceFilter;

    private ProtectedSignalsServiceImpl(@NonNull Context context) {
        this(
                context,
                new UpdateSignalsOrchestrator(
                        AdServicesExecutors.getBackgroundExecutor(),
                        new UpdatesDownloader(
                                AdServicesExecutors.getLightWeightExecutor(),
                                new AdServicesHttpsClient(
                                        AdServicesExecutors.getBlockingExecutor(),
                                        TIMEOUT_MS,
                                        TIMEOUT_MS,
                                        FlagsFactory.getFlags()
                                                .getProtectedSignalsFetchSignalUpdatesMaxSizeBytes())),
                        new UpdateProcessingOrchestrator(
                                ProtectedSignalsDatabase.getInstance(context).protectedSignalsDao(),
                                new UpdateProcessorSelector(),
                                new UpdateEncoderEventHandler(context),
                                new SignalEvictionController()),
                        new AdTechUriValidator(ADTECH_CALLER_NAME, "", CLASS_NAME, FIELD_NAME),
                        Clock.systemUTC()),
                FledgeAuthorizationFilter.create(context, AdServicesLoggerImpl.getInstance()),
                ConsentManager.getInstance(),
                DevContextFilter.create(context),
                AdServicesExecutors.getBackgroundExecutor(),
                AdServicesLoggerImpl.getInstance(),
                FlagsFactory.getFlags(),
                CallingAppUidSupplierBinderImpl.create(),
                new ProtectedSignalsServiceFilter(
                        context,
                        ConsentManager.getInstance(),
                        FlagsFactory.getFlags(),
                        AppImportanceFilter.create(
                                context,
                                AD_SERVICES_API_CALLED__API_CLASS__FLEDGE,
                                () ->
                                        FlagsFactory.getFlags()
                                                .getForegroundStatuslLevelForValidation()),
                        FledgeAuthorizationFilter.create(
                                context, AdServicesLoggerImpl.getInstance()),
                        new FledgeAllowListsFilter(
                                FlagsFactory.getFlags(), AdServicesLoggerImpl.getInstance()),
                        Throttler.getInstance(FlagsFactory.getFlags())));
    }

    @VisibleForTesting
    public ProtectedSignalsServiceImpl(
            @NonNull Context context,
            @NonNull UpdateSignalsOrchestrator updateSignalsOrchestrator,
            @NonNull FledgeAuthorizationFilter fledgeAuthorizationFilter,
            @NonNull ConsentManager consentManager,
            @NonNull DevContextFilter devContextFilter,
            @NonNull ExecutorService executorService,
            @NonNull AdServicesLogger adServicesLogger,
            @NonNull Flags flags,
            @NonNull CallingAppUidSupplier callingAppUidSupplier,
            @NonNull ProtectedSignalsServiceFilter protectedSignalsServiceFilter) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(updateSignalsOrchestrator);
        Objects.requireNonNull(fledgeAuthorizationFilter);
        Objects.requireNonNull(consentManager);
        Objects.requireNonNull(executorService);
        Objects.requireNonNull(adServicesLogger);
        Objects.requireNonNull(protectedSignalsServiceFilter);

        mContext = context;
        mUpdateSignalsOrchestrator = updateSignalsOrchestrator;
        mFledgeAuthorizationFilter = fledgeAuthorizationFilter;
        mConsentManager = consentManager;
        mDevContextFilter = devContextFilter;
        mExecutorService = executorService;
        mAdServicesLogger = adServicesLogger;
        mFlags = flags;
        mCallingAppUidSupplier = callingAppUidSupplier;
        mProtectedSignalsServiceFilter = protectedSignalsServiceFilter;
    }

    /** Creates a new instance of {@link ProtectedSignalsServiceImpl}. */
    public static ProtectedSignalsServiceImpl create(@NonNull Context context) {
        return new ProtectedSignalsServiceImpl(context);
    }

    @Override
    public void updateSignals(
            @NonNull UpdateSignalsInput updateSignalsInput,
            @NonNull UpdateSignalsCallback updateSignalsCallback)
            throws RemoteException {
        sLogger.v("Entering updateSignals");

        final int apiName = AD_SERVICES_API_CALLED__API_NAME__UPDATE_SIGNALS;
        String callerPackageName = updateSignalsInput == null ?
                EMPTY_PACKAGE_NAME : updateSignalsInput.getCallerPackageName();

        try {
            Objects.requireNonNull(updateSignalsInput);
            Objects.requireNonNull(updateSignalsCallback);
        } catch (NullPointerException exception) {
            mAdServicesLogger.logApiCallStats(
                    new ApiCallStats.Builder()
                            .setCode(AdServicesStatsLog.AD_SERVICES_API_CALLED)
                            .setApiClass(AD_SERVICES_API_CALLED__API_CLASS__FLEDGE)
                            .setApiName(apiName)
                            .setLatencyMillisecond(0)
                            .setResult(
                                    AdServicesStatusUtils.STATUS_INVALID_ARGUMENT,
                                    FAILURE_REASON_UNSET)
                            .setAppPackageName(callerPackageName)
                            .setSdkPackageName(EMPTY_SDK_NAME)
                            .build());
            // Rethrow because we want to fail fast
            throw exception;
        }

        AdsRelevanceExecutionLoggerFactory adsRelevanceExecutionLoggerFactory =
                new AdsRelevanceExecutionLoggerFactory(
                        callerPackageName,
                        new CallerMetadata.Builder()
                                .setBinderElapsedTimestamp(SystemClock.elapsedRealtime())
                                .build(),
                        com.android.adservices.shared.util.Clock.getInstance(),
                        mAdServicesLogger,
                        mFlags,
                        apiName);
        AdsRelevanceExecutionLogger adsRelevanceExecutionLogger =
                adsRelevanceExecutionLoggerFactory.getAdsRelevanceExecutionLogger();

        // Caller permissions must be checked in the binder thread, before anything else
        mFledgeAuthorizationFilter.assertAppDeclaredPermission(
                mContext,
                updateSignalsInput.getCallerPackageName(),
                apiName,
                AdServicesPermissions.ACCESS_ADSERVICES_PROTECTED_SIGNALS);

        final int callerUid = getCallingUid(adsRelevanceExecutionLogger);
        final DevContext devContext = mDevContextFilter.createDevContext();
        sLogger.v("Running updateSignals");
        mExecutorService.execute(
                () ->
                        doUpdateSignals(
                                updateSignalsInput,
                                updateSignalsCallback,
                                callerUid,
                                devContext,
                                adsRelevanceExecutionLogger));
    }

    private void doUpdateSignals(
            UpdateSignalsInput input,
            UpdateSignalsCallback callback,
            int callerUid,
            DevContext devContext,
            AdsRelevanceExecutionLogger adsRelevanceExecutionLogger) {
        sLogger.v("Entering doUpdateSignals");

        final int apiName = AD_SERVICES_API_CALLED__API_NAME__UPDATE_SIGNALS;

        int resultCode = AdServicesStatusUtils.STATUS_UNSET;
        // The filters log internally, so don't accidentally log again
        boolean shouldLog = false;
        try {
            try {
                AdTechIdentifier buyer;
                try {
                    buyer =
                            mProtectedSignalsServiceFilter.filterRequestAndExtractIdentifier(
                                    input.getUpdateUri(),
                                    input.getCallerPackageName(),
                                    mFlags.getDisableFledgeEnrollmentCheck(),
                                    mFlags.getEnforceForegroundStatusForSignals(),
                                    // Consent is enforced in a separate call below.
                                    false,
                                    callerUid,
                                    apiName,
                                    PROTECTED_SIGNAL_API_UPDATE_SIGNALS,
                                    devContext);
                    shouldLog = true;
                } catch (Throwable t) {
                    throw new FilterException(t);
                }

                // Fail silently for revoked user consent
                if (mConsentManager.isPasFledgeConsentGiven()
                        && !mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(
                                input.getCallerPackageName())) {
                    sLogger.v("Orchestrating signal update");
                    mUpdateSignalsOrchestrator
                            .orchestrateUpdate(
                                    input.getUpdateUri(),
                                    buyer,
                                    input.getCallerPackageName(),
                                    devContext)
                            .get();
                    PeriodicEncodingJobService.scheduleIfNeeded(mContext, mFlags, false);
                    resultCode = AdServicesStatusUtils.STATUS_SUCCESS;
                } else {
                    sLogger.v("Consent revoked");
                    resultCode = AdServicesStatusUtils.STATUS_USER_CONSENT_REVOKED;
                }
            } catch (ExecutionException exception) {
                sLogger.d(
                        exception,
                        "Error encountered in updateSignals, unpacking from ExecutionException"
                                + " and notifying caller");
                resultCode = notifyFailure(callback, exception.getCause());
                return;
            } catch (Exception exception) {
                sLogger.d(exception, "Error encountered in updateSignals, notifying caller");
                resultCode = notifyFailure(callback, exception);
                return;
            }
            callback.onSuccess();
        } catch (Exception exception) {
            sLogger.e(exception, "Unable to send result to the callback");
            resultCode = AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
        } finally {
            if (shouldLog) {
                adsRelevanceExecutionLogger.endAdsRelevanceApi(resultCode);
            }
        }
    }

    // TODO(b/297055198) Refactor this method into a utility class
    private int getCallingUid(AdsRelevanceExecutionLogger adsRelevanceExecutionLogger)
            throws IllegalStateException {
        try {
            return mCallingAppUidSupplier.getCallingAppUid();
        } catch (IllegalStateException illegalStateException) {
            adsRelevanceExecutionLogger.endAdsRelevanceApi(
                    AdServicesStatusUtils.STATUS_INTERNAL_ERROR);
            throw illegalStateException;
        }
    }

    private int notifyFailure(UpdateSignalsCallback callback, Throwable t) throws RemoteException {
        sLogger.d(t, "Notifying caller about exception");
        int resultCode;

        boolean isFilterException = t instanceof FilterException;

        if (isFilterException) {
            resultCode = FilterException.getResultCode(t);
        } else if (t instanceof IllegalArgumentException) {
            resultCode = AdServicesStatusUtils.STATUS_INVALID_ARGUMENT;
        } else {
            sLogger.d(t, "Unexpected error during operation");
            resultCode = AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
        }

        callback.onFailure(
                new FledgeErrorResponse.Builder()
                        .setStatusCode(resultCode)
                        .setErrorMessage(t.getMessage())
                        .build());
        return resultCode;
    }
}
