/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.adservices.service.customaudience;

import static com.android.adservices.service.common.Throttler.ApiKey.FLEDGE_API_SCHEDULE_CUSTOM_AUDIENCE_UPDATE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__SCHEDULE_CUSTOM_AUDIENCE_UPDATE;

import android.adservices.common.AdServicesStatusUtils;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.FledgeErrorResponse;
import android.adservices.customaudience.PartialCustomAudience;
import android.adservices.customaudience.ScheduleCustomAudienceUpdateCallback;
import android.adservices.customaudience.ScheduleCustomAudienceUpdateInput;
import android.annotation.NonNull;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.LimitExceededException;
import android.os.RemoteException;

import androidx.annotation.RequiresApi;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.DBScheduledCustomAudienceUpdate;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.AppImportanceFilter;
import com.android.adservices.service.common.CustomAudienceServiceFilter;
import com.android.adservices.service.common.FledgeAllowListsFilter;
import com.android.adservices.service.common.FledgeAuthorizationFilter;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.exception.FilterException;
import com.android.adservices.service.stats.AdServicesLogger;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;

import java.io.InvalidObjectException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Schedules a delayed update for Custom Audience. Calling apps provide and update uri on behalf of
 * buyers, which are requested to provide Custom Audience Updates. This class also allows calling
 * app to pass in list of {@link PartialCustomAudience} objects which are sent along with the update
 * request to the server and used in the overrides of incoming Custom Audience Objects.
 */
@RequiresApi(Build.VERSION_CODES.S)
public class ScheduleCustomAudienceUpdateImpl {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    private static final int API_NAME =
            AD_SERVICES_API_CALLED__API_NAME__SCHEDULE_CUSTOM_AUDIENCE_UPDATE;
    public static final int MIN_DELAY_TIME_MINUTES = 30;
    public static final int MAX_DELAY_TIME_MINUTES = 300;
    private static final Boolean ENFORCE_CONSENT = true;
    @NonNull private final Context mContext;
    @NonNull private final ConsentManager mConsentManager;
    @NonNull private final Flags mFlags;
    @NonNull private final AdServicesLogger mAdServicesLogger;
    @NonNull private final ListeningExecutorService mBackgroundExecutorService;
    @NonNull private final CustomAudienceServiceFilter mCustomAudienceServiceFilter;
    @NonNull private final CustomAudienceDao mCustomAudienceDao;
    @NonNull private final boolean mDisableFledgeEnrollmentCheck;
    @NonNull private final boolean mEnforceForegroundStatus;
    @NonNull private final boolean mScheduleCustomAudienceUpdateEnabled;
    int mCallingAppUid;
    @NonNull private String mCallerAppPackageName;

    public ScheduleCustomAudienceUpdateImpl(
            @NonNull Context context,
            @NonNull ConsentManager consentManager,
            int callerId,
            @NonNull Flags flags,
            @NonNull AdServicesLogger adServicesLogger,
            @NonNull ListeningExecutorService backgroundExecutorService,
            @NonNull CustomAudienceServiceFilter customAudienceServiceFilter,
            @NonNull CustomAudienceDao customAudienceDao) {
        mContext = context;
        mConsentManager = consentManager;
        mCallingAppUid = callerId;
        mAdServicesLogger = adServicesLogger;
        mBackgroundExecutorService = backgroundExecutorService;
        mCustomAudienceServiceFilter = customAudienceServiceFilter;
        mCustomAudienceDao = customAudienceDao;
        mDisableFledgeEnrollmentCheck = flags.getDisableFledgeEnrollmentCheck();
        mEnforceForegroundStatus = flags.getEnforceForegroundStatusForFledgeCustomAudience();
        mScheduleCustomAudienceUpdateEnabled = flags.getFledgeScheduleCustomAudienceUpdateEnabled();
        mFlags = flags;
    }

    /** Schedules a delayed Custom Audience Update */
    public void doScheduleCustomAudienceUpdate(
            @NonNull ScheduleCustomAudienceUpdateInput input,
            @NonNull ScheduleCustomAudienceUpdateCallback callback,
            @NonNull DevContext devContext) {
        try {
            mCallerAppPackageName = input.getCallerPackageName();

            if (!mScheduleCustomAudienceUpdateEnabled) {
                sLogger.v("scheduleCustomAudienceUpdate is disabled.");
                throw new IllegalStateException("scheduleCustomAudienceUpdate is disabled.");
            }

            FluentFuture<AdTechIdentifier> buyerFuture =
                    FluentFuture.from(filterAndValidateRequest(input, devContext));
            buyerFuture
                    .transformAsync(
                            buyer -> scheduleUpdate(buyer, input, devContext),
                            mBackgroundExecutorService)
                    .addCallback(
                            new FutureCallback<Void>() {
                                @Override
                                public void onSuccess(Void result) {
                                    sLogger.v("Completed scheduleCustomAudienceUpdate execution");
                                    // Schedule job that triggers updates
                                    ScheduleCustomAudienceUpdateJobService.scheduleIfNeeded(
                                            mContext, mFlags, false);
                                    notifySuccess(callback);
                                }

                                @Override
                                public void onFailure(Throwable t) {
                                    sLogger.d(
                                            t,
                                            "Error encountered in scheduleCustomAudienceUpdate"
                                                    + " execution");
                                    if (t instanceof FilterException
                                            && t.getCause()
                                                    instanceof
                                                    ConsentManager.RevokedConsentException) {
                                        // Skip logging if a FilterException occurs.
                                        // AdSelectionServiceFilter ensures the failing
                                        // assertion is logged
                                        // internally.

                                        // Fail Silently by notifying success to caller
                                        notifySuccess(callback);
                                    } else {
                                        notifyFailure(callback, t);
                                    }
                                }
                            },
                            mBackgroundExecutorService);
        } catch (Throwable t) {
            notifyFailure(callback, t);
        }
    }

    private ListenableFuture<AdTechIdentifier> filterAndValidateRequest(
            @NonNull ScheduleCustomAudienceUpdateInput input, @NonNull DevContext devContext) {

        return mBackgroundExecutorService.submit(
                () -> {
                    sLogger.v("In scheduleCustomAudienceUpdate filterAndValidateRequest");
                    AdTechIdentifier buyer = null;
                    try {
                        if (mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(
                                input.getCallerPackageName())) {
                            sLogger.v("Consent revoked");
                            throw new ConsentManager.RevokedConsentException();
                        }
                        // Extract buyer ad tech identifier and filter request
                        buyer =
                                mCustomAudienceServiceFilter.filterRequestAndExtractIdentifier(
                                        input.getUpdateUri(),
                                        input.getCallerPackageName(),
                                        mDisableFledgeEnrollmentCheck,
                                        mEnforceForegroundStatus,
                                        ENFORCE_CONSENT,
                                        mCallingAppUid,
                                        API_NAME,
                                        FLEDGE_API_SCHEDULE_CUSTOM_AUDIENCE_UPDATE,
                                        devContext);

                        validateDelayTime(input.getMinDelay());

                    } catch (FledgeAuthorizationFilter.CallerMismatchException t) {
                        throw new FilterException(t);
                    } catch (AppImportanceFilter.WrongCallingApplicationStateException t) {
                        throw new FilterException(t);
                    } catch (FledgeAuthorizationFilter.AdTechNotAllowedException t) {
                        throw new FilterException(t);
                    } catch (FledgeAllowListsFilter.AppNotAllowedException t) {
                        throw new FilterException(t);
                    } catch (LimitExceededException t) {
                        throw new FilterException(t);
                    } catch (ConsentManager.RevokedConsentException t) {
                        throw new FilterException(t);
                    }
                    sLogger.v("Completed scheduleCustomAudienceUpdate filterAndValidateRequest");
                    return buyer;
                });
    }

    private ListenableFuture<Void> scheduleUpdate(
            AdTechIdentifier buyer,
            ScheduleCustomAudienceUpdateInput input,
            DevContext devContext) {
        String owner = input.getCallerPackageName();
        Uri updateUri = input.getUpdateUri();
        Instant now = Instant.now();
        Instant scheduledTime = now.plus(input.getMinDelay().toMinutes(), ChronoUnit.MINUTES);

        DBScheduledCustomAudienceUpdate scheduledUpdate =
                DBScheduledCustomAudienceUpdate.builder()
                        .setUpdateUri(updateUri)
                        .setOwner(owner)
                        .setBuyer(buyer)
                        .setCreationTime(Instant.now())
                        .setScheduledTime(scheduledTime)
                        .setIsDebuggable(devContext.getDevOptionsEnabled())
                        .build();

        sLogger.d(
                String.format(
                        "Persisting update for uri:<%s> scheduled at time:%s in storage",
                        updateUri, scheduledUpdate));
        return (ListenableFuture<Void>)
                mBackgroundExecutorService.submit(
                        () ->
                                mCustomAudienceDao
                                        .insertScheduledUpdateAndPartialCustomAudienceList(
                                                scheduledUpdate,
                                                input.getPartialCustomAudienceList()));
    }

    private void notifyFailure(ScheduleCustomAudienceUpdateCallback callback, Throwable t) {
        try {
            int resultCode;

            boolean isFilterException = t instanceof FilterException;

            if (isFilterException) {
                resultCode = FilterException.getResultCode(t);
            } else if (t instanceof IllegalArgumentException) {
                resultCode = AdServicesStatusUtils.STATUS_INVALID_ARGUMENT;
            } else if (t instanceof InvalidObjectException) {
                resultCode = AdServicesStatusUtils.STATUS_INVALID_OBJECT;
            } else if (t instanceof LimitExceededException) {
                resultCode = AdServicesStatusUtils.STATUS_SERVER_RATE_LIMIT_REACHED;
            } else {
                sLogger.d(t, "Unexpected error during operation");
                resultCode = AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
            }

            // Skip logging if a FilterException occurs.
            // AdSelectionServiceFilter ensures the failing assertion is logged internally.
            // Note: Failure is logged before the callback to ensure deterministic testing.
            if (!isFilterException) {
                mAdServicesLogger.logFledgeApiCallStats(
                        API_NAME, mCallerAppPackageName, resultCode, /*latencyMs=*/ 0);
            }

            callback.onFailure(
                    new FledgeErrorResponse.Builder()
                            .setStatusCode(resultCode)
                            .setErrorMessage(t.getMessage())
                            .build());
        } catch (RemoteException e) {
            sLogger.e(e, "Unable to send failed result to the callback");
            mAdServicesLogger.logFledgeApiCallStats(
                    API_NAME,
                    mCallerAppPackageName,
                    AdServicesStatusUtils.STATUS_INTERNAL_ERROR,
                    /*latencyMs=*/ 0);
        }
    }

    /** Invokes the onSuccess function from the callback and handles the exception. */
    private void notifySuccess(@NonNull ScheduleCustomAudienceUpdateCallback callback) {
        try {
            mAdServicesLogger.logFledgeApiCallStats(
                    API_NAME,
                    mCallerAppPackageName,
                    AdServicesStatusUtils.STATUS_SUCCESS,
                    /*latencyMs=*/ 0);
            callback.onSuccess();
        } catch (RemoteException e) {
            sLogger.e(e, "Unable to send successful result to the callback");
            mAdServicesLogger.logFledgeApiCallStats(
                    API_NAME,
                    mCallerAppPackageName,
                    AdServicesStatusUtils.STATUS_INTERNAL_ERROR,
                    /*latencyMs=*/ 0);
        }
    }

    private void validateDelayTime(Duration delayTime) {
        int minTimeDelayMinutes =
                Math.min(
                        MIN_DELAY_TIME_MINUTES,
                        mFlags.getFledgeScheduleCustomAudienceMinDelayMinsOverride());
        if (delayTime.toMinutes() < minTimeDelayMinutes
                || delayTime.toMinutes() > MAX_DELAY_TIME_MINUTES) {
            sLogger.e("Delay Time not within permissible limits");
            throw new IllegalArgumentException("Delay Time not within permissible limits");
        }
    }
}
