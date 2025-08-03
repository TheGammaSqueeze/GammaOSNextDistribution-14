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

package com.android.adservices.service.customaudience;

import static android.adservices.common.AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
import static android.adservices.common.AdServicesStatusUtils.STATUS_INVALID_ARGUMENT;

import static com.android.adservices.service.common.Throttler.ApiKey.FLEDGE_API_JOIN_CUSTOM_AUDIENCE;
import static com.android.adservices.service.common.Throttler.ApiKey.FLEDGE_API_LEAVE_CUSTOM_AUDIENCE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_CLASS__FLEDGE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__FETCH_AND_JOIN_CUSTOM_AUDIENCE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__OVERRIDE_CUSTOM_AUDIENCE_REMOTE_INFO;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REMOVE_CUSTOM_AUDIENCE_REMOTE_INFO_OVERRIDE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__RESET_ALL_CUSTOM_AUDIENCE_OVERRIDES;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__SCHEDULE_CUSTOM_AUDIENCE_UPDATE;

import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdServicesPermissions;
import android.adservices.common.AdServicesStatusUtils;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.FledgeErrorResponse;
import android.adservices.customaudience.CustomAudience;
import android.adservices.customaudience.CustomAudienceOverrideCallback;
import android.adservices.customaudience.FetchAndJoinCustomAudienceCallback;
import android.adservices.customaudience.FetchAndJoinCustomAudienceInput;
import android.adservices.customaudience.ICustomAudienceCallback;
import android.adservices.customaudience.ICustomAudienceService;
import android.adservices.customaudience.ScheduleCustomAudienceUpdateCallback;
import android.adservices.customaudience.ScheduleCustomAudienceUpdateInput;
import android.annotation.NonNull;
import android.content.Context;
import android.os.Build;
import android.os.LimitExceededException;
import android.os.RemoteException;

import androidx.annotation.RequiresApi;

import com.android.adservices.LoggerFactory;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.adselection.SharedStorageDatabase;
import com.android.adservices.data.customaudience.AdDataConversionStrategyFactory;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.adselection.AdFilteringFeatureFactory;
import com.android.adservices.service.common.AdRenderIdValidator;
import com.android.adservices.service.common.AppImportanceFilter;
import com.android.adservices.service.common.AppImportanceFilter.WrongCallingApplicationStateException;
import com.android.adservices.service.common.CallingAppUidSupplier;
import com.android.adservices.service.common.CallingAppUidSupplierBinderImpl;
import com.android.adservices.service.common.CustomAudienceServiceFilter;
import com.android.adservices.service.common.FledgeAllowListsFilter;
import com.android.adservices.service.common.FledgeAuthorizationFilter;
import com.android.adservices.service.common.Throttler;
import com.android.adservices.service.common.cache.CacheProviderFactory;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.devapi.CustomAudienceOverrider;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.devapi.DevContextFilter;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.internal.annotations.VisibleForTesting;

import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

/** Implementation of the Custom Audience service. */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
public class CustomAudienceServiceImpl extends ICustomAudienceService.Stub {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    @NonNull private final Context mContext;
    @NonNull private final CustomAudienceImpl mCustomAudienceImpl;
    @NonNull private final FledgeAuthorizationFilter mFledgeAuthorizationFilter;
    @NonNull private final ConsentManager mConsentManager;
    @NonNull private final ExecutorService mExecutorService;
    @NonNull private final DevContextFilter mDevContextFilter;
    @NonNull private final AdServicesLogger mAdServicesLogger;
    @NonNull private final AppImportanceFilter mAppImportanceFilter;
    @NonNull private final Flags mFlags;
    @NonNull private final CallingAppUidSupplier mCallingAppUidSupplier;

    @NonNull private final CustomAudienceServiceFilter mCustomAudienceServiceFilter;
    @NonNull private final AdFilteringFeatureFactory mAdFilteringFeatureFactory;

    private static final String API_NOT_AUTHORIZED_MSG =
            "This API is not enabled for the given app because either dev options are disabled or"
                    + " the app is not debuggable.";

    private CustomAudienceServiceImpl(@NonNull Context context) {
        this(
                context,
                CustomAudienceImpl.getInstance(context),
                FledgeAuthorizationFilter.create(context, AdServicesLoggerImpl.getInstance()),
                ConsentManager.getInstance(),
                DevContextFilter.create(context),
                AdServicesExecutors.getBackgroundExecutor(),
                AdServicesLoggerImpl.getInstance(),
                AppImportanceFilter.create(
                        context,
                        AD_SERVICES_API_CALLED__API_CLASS__FLEDGE,
                        () -> FlagsFactory.getFlags().getForegroundStatuslLevelForValidation()),
                FlagsFactory.getFlags(),
                CallingAppUidSupplierBinderImpl.create(),
                new CustomAudienceServiceFilter(
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
                        Throttler.getInstance(FlagsFactory.getFlags())),
                new AdFilteringFeatureFactory(
                        SharedStorageDatabase.getInstance(context).appInstallDao(),
                        SharedStorageDatabase.getInstance(context).frequencyCapDao(),
                        FlagsFactory.getFlags()));
    }

    /** Creates a new instance of {@link CustomAudienceServiceImpl}. */
    public static CustomAudienceServiceImpl create(@NonNull Context context) {
        return new CustomAudienceServiceImpl(context);
    }

    @VisibleForTesting
    public CustomAudienceServiceImpl(
            @NonNull Context context,
            @NonNull CustomAudienceImpl customAudienceImpl,
            @NonNull FledgeAuthorizationFilter fledgeAuthorizationFilter,
            @NonNull ConsentManager consentManager,
            @NonNull DevContextFilter devContextFilter,
            @NonNull ExecutorService executorService,
            @NonNull AdServicesLogger adServicesLogger,
            @NonNull AppImportanceFilter appImportanceFilter,
            @NonNull Flags flags,
            @NonNull CallingAppUidSupplier callingAppUidSupplier,
            @NonNull CustomAudienceServiceFilter customAudienceServiceFilter,
            @NonNull AdFilteringFeatureFactory adFilteringFeatureFactory) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(customAudienceImpl);
        Objects.requireNonNull(fledgeAuthorizationFilter);
        Objects.requireNonNull(consentManager);
        Objects.requireNonNull(executorService);
        Objects.requireNonNull(adServicesLogger);
        Objects.requireNonNull(appImportanceFilter);
        Objects.requireNonNull(customAudienceServiceFilter);

        mContext = context;
        mCustomAudienceImpl = customAudienceImpl;
        mFledgeAuthorizationFilter = fledgeAuthorizationFilter;
        mConsentManager = consentManager;
        mDevContextFilter = devContextFilter;
        mExecutorService = executorService;
        mAdServicesLogger = adServicesLogger;
        mAppImportanceFilter = appImportanceFilter;
        mFlags = flags;
        mCallingAppUidSupplier = callingAppUidSupplier;
        mCustomAudienceServiceFilter = customAudienceServiceFilter;
        mAdFilteringFeatureFactory = adFilteringFeatureFactory;
    }

    /**
     * Adds a user to a custom audience.
     *
     * @hide
     */
    @Override
    public void joinCustomAudience(
            @NonNull CustomAudience customAudience,
            @NonNull String ownerPackageName,
            @NonNull ICustomAudienceCallback callback) {
        sLogger.v("Entering joinCustomAudience");

        final int apiName = AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE;

        try {
            Objects.requireNonNull(customAudience);
            Objects.requireNonNull(ownerPackageName);
            Objects.requireNonNull(callback);
        } catch (NullPointerException exception) {
            mAdServicesLogger.logFledgeApiCallStats(
                    apiName, ownerPackageName, STATUS_INVALID_ARGUMENT, /*latencyMs=*/ 0);
            // Rethrow because we want to fail fast
            throw exception;
        }

        // Caller permissions must be checked in the binder thread, before anything else
        mFledgeAuthorizationFilter.assertAppDeclaredPermission(
                mContext,
                ownerPackageName,
                apiName,
                AdServicesPermissions.ACCESS_ADSERVICES_CUSTOM_AUDIENCE);

        final int callerUid = getCallingUid(apiName, ownerPackageName);
        final DevContext devContext = mDevContextFilter.createDevContext();
        sLogger.v("Running service");
        mExecutorService.execute(
                () ->
                        doJoinCustomAudience(
                                customAudience, ownerPackageName, callback, callerUid, devContext));
    }

    /** Try to join the custom audience and signal back to the caller using the callback. */
    private void doJoinCustomAudience(
            @NonNull CustomAudience customAudience,
            @NonNull String ownerPackageName,
            @NonNull ICustomAudienceCallback callback,
            final int callerUid,
            @NonNull final DevContext devContext) {
        Objects.requireNonNull(customAudience);
        Objects.requireNonNull(ownerPackageName);
        Objects.requireNonNull(callback);
        Objects.requireNonNull(devContext);

        sLogger.v("Entering doJoinCustomAudience");

        final int apiName = AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE;
        int resultCode = AdServicesStatusUtils.STATUS_UNSET;
        int failureReason = AdServicesStatusUtils.FAILURE_REASON_UNSET;
        // The filters log internally, so don't accidentally log again
        boolean shouldLog = false;
        try {
            try {
                // Filter and validate request
                mCustomAudienceServiceFilter.filterRequest(
                        customAudience.getBuyer(),
                        ownerPackageName,
                        mFlags.getEnforceForegroundStatusForFledgeCustomAudience(),
                        false,
                        callerUid,
                        apiName,
                        FLEDGE_API_JOIN_CUSTOM_AUDIENCE,
                        devContext);

                shouldLog = true;

                // Fail silently for revoked user consent
                if (!mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(
                        ownerPackageName)) {
                    sLogger.v("Joining custom audience");
                    mCustomAudienceImpl.joinCustomAudience(
                            customAudience, ownerPackageName, devContext);
                    BackgroundFetchJobService.scheduleIfNeeded(mContext, mFlags, false);
                    resultCode = AdServicesStatusUtils.STATUS_SUCCESS;
                } else {
                    sLogger.v("Consent revoked");
                    resultCode = AdServicesStatusUtils.STATUS_USER_CONSENT_REVOKED;
                }
            } catch (Exception exception) {
                sLogger.d(exception, "Error encountered in joinCustomAudience, notifying caller");
                resultCode = notifyFailure(callback, exception);
                return;
            }

            callback.onSuccess();
        } catch (Exception exception) {
            sLogger.e(exception, "Unable to send result to the callback");
            resultCode = STATUS_INTERNAL_ERROR;
        } finally {
            if (shouldLog) {
                mAdServicesLogger.logFledgeApiCallStats(
                        apiName, ownerPackageName, resultCode, /*latencyMs=*/ 0);
            }
        }
    }

    /**
     * Adds the user to the {@link CustomAudience} fetched from a {@code fetchUri}
     *
     * @hide
     */
    @Override
    public void fetchAndJoinCustomAudience(
            @NonNull FetchAndJoinCustomAudienceInput input,
            @NonNull FetchAndJoinCustomAudienceCallback callback) {
        sLogger.v("Executing fetchAndJoinCustomAudience.");
        final int apiName = AD_SERVICES_API_CALLED__API_NAME__FETCH_AND_JOIN_CUSTOM_AUDIENCE;

        // Failing fast if parameters are null.
        try {
            Objects.requireNonNull(input);
            Objects.requireNonNull(callback);
        } catch (NullPointerException exception) {
            mAdServicesLogger.logFledgeApiCallStats(
                    apiName, STATUS_INVALID_ARGUMENT, /*latencyMs=*/ 0);

            // Rethrow because we want to fail fast
            throw exception;
        }

        // Caller permissions must be checked in the binder thread, before anything else
        mFledgeAuthorizationFilter.assertAppDeclaredPermission(
                mContext,
                input.getCallerPackageName(),
                apiName,
                AdServicesPermissions.ACCESS_ADSERVICES_CUSTOM_AUDIENCE);

        final int callerUid = getCallingUid(apiName, input.getCallerPackageName());
        final DevContext devContext = mDevContextFilter.createDevContext();
        mExecutorService.execute(
                () -> {
                    FetchCustomAudienceImpl impl =
                            new FetchCustomAudienceImpl(
                                    mFlags,
                                    // TODO(b/235841960): Align on internal Clock usage.
                                    Clock.systemUTC(),
                                    mAdServicesLogger,
                                    mExecutorService,
                                    mCustomAudienceImpl.getCustomAudienceDao(),
                                    callerUid,
                                    mCustomAudienceServiceFilter,
                                    new AdServicesHttpsClient(
                                            AdServicesExecutors.getBlockingExecutor(),
                                            CacheProviderFactory.createNoOpCache()),
                                    mAdFilteringFeatureFactory.getFrequencyCapAdDataValidator(),
                                    AdRenderIdValidator.createInstance(mFlags),
                                    AdDataConversionStrategyFactory.getAdDataConversionStrategy(
                                            mFlags.getFledgeAdSelectionFilteringEnabled(),
                                            mFlags.getFledgeAuctionServerAdRenderIdEnabled()));

                    impl.doFetchCustomAudience(input, callback, devContext);
                });
    }

    private int notifyFailure(ICustomAudienceCallback callback, Exception exception)
            throws RemoteException {
        int resultCode;
        if (exception instanceof NullPointerException
                || exception instanceof IllegalArgumentException) {
            resultCode = STATUS_INVALID_ARGUMENT;
        } else if (exception instanceof WrongCallingApplicationStateException) {
            resultCode = AdServicesStatusUtils.STATUS_BACKGROUND_CALLER;
        } else if (exception instanceof FledgeAuthorizationFilter.CallerMismatchException) {
            resultCode = AdServicesStatusUtils.STATUS_UNAUTHORIZED;
        } else if (exception instanceof FledgeAuthorizationFilter.AdTechNotAllowedException
                || exception instanceof FledgeAllowListsFilter.AppNotAllowedException) {
            resultCode = AdServicesStatusUtils.STATUS_CALLER_NOT_ALLOWED;
        } else if (exception instanceof LimitExceededException) {
            resultCode = AdServicesStatusUtils.STATUS_RATE_LIMIT_REACHED;
        } else if (exception instanceof IllegalStateException) {
            resultCode = STATUS_INTERNAL_ERROR;
        } else {
            sLogger.e(exception, "Unexpected error during operation");
            resultCode = STATUS_INTERNAL_ERROR;
        }
        callback.onFailure(
                new FledgeErrorResponse.Builder()
                        .setStatusCode(resultCode)
                        .setErrorMessage(exception.getMessage())
                        .build());
        return resultCode;
    }

    @Override
    public void scheduleCustomAudienceUpdate(
            ScheduleCustomAudienceUpdateInput input,
            ScheduleCustomAudienceUpdateCallback callback) {
        final int apiName = AD_SERVICES_API_CALLED__API_NAME__SCHEDULE_CUSTOM_AUDIENCE_UPDATE;
        try {
            Objects.requireNonNull(input);
            Objects.requireNonNull(callback);
        } catch (NullPointerException exception) {
            mAdServicesLogger.logFledgeApiCallStats(
                    apiName,
                    input.getCallerPackageName(),
                    STATUS_INVALID_ARGUMENT,
                    /*latencyMs=*/ 0);

            // Rethrow because we want to fail fast
            throw exception;
        }

        // Caller permissions must be checked in the binder thread, before anything else
        mFledgeAuthorizationFilter.assertAppDeclaredPermission(
                mContext,
                input.getCallerPackageName(),
                apiName,
                AdServicesPermissions.ACCESS_ADSERVICES_CUSTOM_AUDIENCE);

        final int callerUid = getCallingUid(apiName, input.getCallerPackageName());
        final DevContext devContext = mDevContextFilter.createDevContext();

        mExecutorService.execute(
                () -> {
                    ScheduleCustomAudienceUpdateImpl impl =
                            new ScheduleCustomAudienceUpdateImpl(
                                    mContext,
                                    mConsentManager,
                                    callerUid,
                                    mFlags,
                                    mAdServicesLogger,
                                    AdServicesExecutors.getBackgroundExecutor(),
                                    mCustomAudienceServiceFilter,
                                    mCustomAudienceImpl.getCustomAudienceDao());

                    impl.doScheduleCustomAudienceUpdate(input, callback, devContext);
                });
    }

    /**
     * Attempts to remove a user from a custom audience.
     *
     * @hide
     */
    @Override
    public void leaveCustomAudience(
            @NonNull String ownerPackageName,
            @NonNull AdTechIdentifier buyer,
            @NonNull String name,
            @NonNull ICustomAudienceCallback callback) {
        final int apiName = AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE;

        try {
            Objects.requireNonNull(ownerPackageName);
            Objects.requireNonNull(buyer);
            Objects.requireNonNull(name);
            Objects.requireNonNull(callback);
        } catch (NullPointerException exception) {
            mAdServicesLogger.logFledgeApiCallStats(
                    apiName, ownerPackageName, STATUS_INVALID_ARGUMENT, /*latencyMs=*/ 0);

            // Rethrow because we want to fail fast
            throw exception;
        }

        // Caller permissions must be checked in the binder thread, before anything else
        mFledgeAuthorizationFilter.assertAppDeclaredPermission(
                mContext,
                ownerPackageName,
                apiName,
                AdServicesPermissions.ACCESS_ADSERVICES_CUSTOM_AUDIENCE);

        final int callerUid = getCallingUid(apiName, ownerPackageName);
        final DevContext devContext = mDevContextFilter.createDevContext();
        mExecutorService.execute(
                () ->
                        doLeaveCustomAudience(
                                ownerPackageName, buyer, name, callback, callerUid, devContext));
    }

    /** Try to leave the custom audience and signal back to the caller using the callback. */
    private void doLeaveCustomAudience(
            @NonNull String ownerPackageName,
            @NonNull AdTechIdentifier buyer,
            @NonNull String name,
            @NonNull ICustomAudienceCallback callback,
            final int callerUid,
            @NonNull final DevContext devContext) {
        Objects.requireNonNull(ownerPackageName);
        Objects.requireNonNull(buyer);
        Objects.requireNonNull(name);
        Objects.requireNonNull(callback);
        Objects.requireNonNull(devContext);

        final int apiName = AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE;
        int resultCode = AdServicesStatusUtils.STATUS_UNSET;
        int failureReason = AdServicesStatusUtils.FAILURE_REASON_UNSET;
        // The filters log internally, so don't accidentally log again
        boolean shouldLog = false;
        try {
            try {
                // Filter and validate request
                mCustomAudienceServiceFilter.filterRequest(
                        buyer,
                        ownerPackageName,
                        mFlags.getEnforceForegroundStatusForFledgeCustomAudience(),
                        false,
                        callerUid,
                        apiName,
                        FLEDGE_API_LEAVE_CUSTOM_AUDIENCE,
                        devContext);

                shouldLog = true;

                // Fail silently for revoked user consent
                if (!mConsentManager.isFledgeConsentRevokedForApp(ownerPackageName)) {
                    sLogger.v("Leaving custom audience");
                    mCustomAudienceImpl.leaveCustomAudience(ownerPackageName, buyer, name);
                    resultCode = AdServicesStatusUtils.STATUS_SUCCESS;
                } else {
                    sLogger.v("Consent revoked");
                    resultCode = AdServicesStatusUtils.STATUS_USER_CONSENT_REVOKED;
                }
            } catch (WrongCallingApplicationStateException
                    | LimitExceededException
                    | FledgeAuthorizationFilter.CallerMismatchException
                    | FledgeAuthorizationFilter.AdTechNotAllowedException
                    | FledgeAllowListsFilter.AppNotAllowedException exception) {
                // Catch these specific exceptions, but report them back to the caller
                sLogger.d(exception, "Error encountered in leaveCustomAudience, notifying caller");
                resultCode = notifyFailure(callback, exception);
                return;
            } catch (Exception exception) {
                // For all other exceptions, report success
                sLogger.e(exception, "Unexpected error leaving custom audience");
                resultCode = STATUS_INTERNAL_ERROR;
            }

            callback.onSuccess();
        } catch (Exception exception) {
            sLogger.e(exception, "Unable to send result to the callback");
            resultCode = STATUS_INTERNAL_ERROR;
        } finally {
            if (shouldLog) {
                mAdServicesLogger.logFledgeApiCallStats(
                        apiName, ownerPackageName, resultCode, /*latencyMs=*/ 0);
            }
        }
    }

    /**
     * Adds a custom audience override with the given information.
     *
     * <p>If the owner does not match the calling package name, fail silently.
     *
     * @hide
     */
    @Override
    public void overrideCustomAudienceRemoteInfo(
            @NonNull String owner,
            @NonNull AdTechIdentifier buyer,
            @NonNull String name,
            @NonNull String biddingLogicJS,
            long biddingLogicJsVersion,
            @NonNull AdSelectionSignals trustedBiddingSignals,
            @NonNull CustomAudienceOverrideCallback callback) {
        final int apiName = AD_SERVICES_API_CALLED__API_NAME__OVERRIDE_CUSTOM_AUDIENCE_REMOTE_INFO;

        try {
            Objects.requireNonNull(owner);
            Objects.requireNonNull(buyer);
            Objects.requireNonNull(name);
            Objects.requireNonNull(biddingLogicJS);
            Objects.requireNonNull(trustedBiddingSignals);
            Objects.requireNonNull(callback);
        } catch (NullPointerException exception) {
            mAdServicesLogger.logFledgeApiCallStats(
                    apiName, STATUS_INVALID_ARGUMENT, /*latencyMs=*/ 0);
            // Rethrow to fail fast
            throw exception;
        }

        DevContext devContext = mDevContextFilter.createDevContext();

        if (!devContext.getDevOptionsEnabled()) {
            mAdServicesLogger.logFledgeApiCallStats(
                    apiName,
                    devContext.getCallingAppPackageName(),
                    STATUS_INTERNAL_ERROR,
                    /*latencyMs=*/ 0);
            throw new SecurityException(API_NOT_AUTHORIZED_MSG);
        }

        // Caller permissions must be checked with a non-null callingAppPackageName
        mFledgeAuthorizationFilter.assertAppDeclaredPermission(
                mContext,
                devContext.getCallingAppPackageName(),
                apiName,
                AdServicesPermissions.ACCESS_ADSERVICES_CUSTOM_AUDIENCE);

        CustomAudienceDao customAudienceDao = mCustomAudienceImpl.getCustomAudienceDao();

        CustomAudienceOverrider overrider =
                new CustomAudienceOverrider(
                        devContext,
                        customAudienceDao,
                        mExecutorService,
                        mContext.getPackageManager(),
                        mConsentManager,
                        mAdServicesLogger,
                        mAppImportanceFilter,
                        mFlags);

        overrider.addOverride(
                owner,
                buyer,
                name,
                biddingLogicJS,
                biddingLogicJsVersion,
                trustedBiddingSignals,
                callback);
    }

    /**
     * Removes a custom audience override with the given information.
     *
     * @hide
     */
    @Override
    public void removeCustomAudienceRemoteInfoOverride(
            @NonNull String owner,
            @NonNull AdTechIdentifier buyer,
            @NonNull String name,
            @NonNull CustomAudienceOverrideCallback callback) {
        final int apiName =
                AD_SERVICES_API_CALLED__API_NAME__REMOVE_CUSTOM_AUDIENCE_REMOTE_INFO_OVERRIDE;

        try {
            Objects.requireNonNull(owner);
            Objects.requireNonNull(buyer);
            Objects.requireNonNull(name);
            Objects.requireNonNull(callback);
        } catch (NullPointerException exception) {
            mAdServicesLogger.logFledgeApiCallStats(
                    apiName, STATUS_INVALID_ARGUMENT, /*latencyMs=*/ 0);
            // Rethrow to fail fast
            throw exception;
        }

        DevContext devContext = mDevContextFilter.createDevContext();

        if (!devContext.getDevOptionsEnabled()) {
            mAdServicesLogger.logFledgeApiCallStats(
                    apiName,
                    devContext.getCallingAppPackageName(),
                    STATUS_INTERNAL_ERROR,
                    /*latencyMs=*/ 0);
            throw new SecurityException(API_NOT_AUTHORIZED_MSG);
        }

        // Caller permissions must be checked with a non-null callingAppPackageName
        mFledgeAuthorizationFilter.assertAppDeclaredPermission(
                mContext,
                devContext.getCallingAppPackageName(),
                apiName,
                AdServicesPermissions.ACCESS_ADSERVICES_CUSTOM_AUDIENCE);

        CustomAudienceDao customAudienceDao = mCustomAudienceImpl.getCustomAudienceDao();

        CustomAudienceOverrider overrider =
                new CustomAudienceOverrider(
                        devContext,
                        customAudienceDao,
                        mExecutorService,
                        mContext.getPackageManager(),
                        mConsentManager,
                        mAdServicesLogger,
                        mAppImportanceFilter,
                        mFlags);

        overrider.removeOverride(owner, buyer, name, callback);
    }


    /**
     * Resets all custom audience overrides for a given caller.
     *
     * @hide
     */
    @Override
    public void resetAllCustomAudienceOverrides(@NonNull CustomAudienceOverrideCallback callback) {
        final int apiName = AD_SERVICES_API_CALLED__API_NAME__RESET_ALL_CUSTOM_AUDIENCE_OVERRIDES;

        try {
            Objects.requireNonNull(callback);
        } catch (NullPointerException exception) {
            mAdServicesLogger.logFledgeApiCallStats(
                    apiName, STATUS_INVALID_ARGUMENT, /*latencyMs=*/ 0);
            // Rethrow to fail fast
            throw exception;
        }

        final int callerUid = getCallingUid(apiName);

        DevContext devContext = mDevContextFilter.createDevContext();

        if (!devContext.getDevOptionsEnabled()) {
            mAdServicesLogger.logFledgeApiCallStats(
                    apiName,
                    devContext.getCallingAppPackageName(),
                    STATUS_INTERNAL_ERROR,
                    /*latencyMs=*/ 0);
            throw new SecurityException(API_NOT_AUTHORIZED_MSG);
        }

        // Caller permissions must be checked with a non-null callingAppPackageName
        mFledgeAuthorizationFilter.assertAppDeclaredPermission(
                mContext,
                devContext.getCallingAppPackageName(),
                apiName,
                AdServicesPermissions.ACCESS_ADSERVICES_CUSTOM_AUDIENCE);

        CustomAudienceDao customAudienceDao = mCustomAudienceImpl.getCustomAudienceDao();

        CustomAudienceOverrider overrider =
                new CustomAudienceOverrider(
                        devContext,
                        customAudienceDao,
                        mExecutorService,
                        mContext.getPackageManager(),
                        mConsentManager,
                        mAdServicesLogger,
                        mAppImportanceFilter,
                        mFlags);

        overrider.removeAllOverrides(callback, callerUid);
    }

    private int getCallingUid(int apiNameLoggingId) throws IllegalStateException {
        return getCallingUid(apiNameLoggingId, null);
    }

    private int getCallingUid(int apiNameLoggingId, String callerAppPackageName)
            throws IllegalStateException {
        try {
            return mCallingAppUidSupplier.getCallingAppUid();
        } catch (IllegalStateException illegalStateException) {
            mAdServicesLogger.logFledgeApiCallStats(
                    apiNameLoggingId,
                    callerAppPackageName,
                    STATUS_INTERNAL_ERROR,
                    /*latencyMs=*/ 0);
            throw illegalStateException;
        }
    }
}
