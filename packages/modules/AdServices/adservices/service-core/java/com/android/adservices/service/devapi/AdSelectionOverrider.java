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

package com.android.adservices.service.devapi;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__OVERRIDE_AD_SELECTION_CONFIG_REMOTE_INFO;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REMOVE_AD_SELECTION_CONFIG_REMOTE_INFO_OVERRIDE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__RESET_ALL_AD_SELECTION_CONFIG_REMOTE_OVERRIDES;

import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionFromOutcomesConfig;
import android.adservices.adselection.AdSelectionOverrideCallback;
import android.adservices.adselection.PerBuyerDecisionLogic;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdServicesStatusUtils;
import android.adservices.common.FledgeErrorResponse;
import android.annotation.NonNull;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.RemoteException;

import androidx.annotation.RequiresApi;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.AppImportanceFilter;
import com.android.adservices.service.common.AppImportanceFilter.WrongCallingApplicationStateException;
import com.android.adservices.service.consent.AdServicesApiConsent;
import com.android.adservices.service.consent.AdServicesApiType;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.stats.AdServicesLogger;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.Objects;
import java.util.concurrent.ExecutorService;

/** Encapsulates the AdSelection Override Logic */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
public class AdSelectionOverrider {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    @NonNull private final AdSelectionEntryDao mAdSelectionEntryDao;
    @NonNull private final ListeningExecutorService mLightweightExecutorService;
    @NonNull private final ListeningExecutorService mBackgroundExecutorService;
    @NonNull private final AdSelectionDevOverridesHelper mAdSelectionDevOverridesHelper;
    @NonNull private final PackageManager mPackageManager;
    @NonNull private final ConsentManager mConsentManager;
    @NonNull private final AdServicesLogger mAdServicesLogger;
    @NonNull private final Flags mFlags;
    @NonNull private final AppImportanceFilter mAppImportanceFilter;
    private final int mCallerUid;
    @NonNull private final String mCallerAppPackageName;

    /**
     * Creates an instance of {@link AdSelectionOverrider} with the given {@link DevContext}, {@link
     * AdSelectionEntryDao}, executor, and {@link AdSelectionDevOverridesHelper}.
     */
    public AdSelectionOverrider(
            @NonNull DevContext devContext,
            @NonNull AdSelectionEntryDao adSelectionEntryDao,
            @NonNull ExecutorService lightweightExecutorService,
            @NonNull ExecutorService backgroundExecutorService,
            @NonNull PackageManager packageManager,
            @NonNull ConsentManager consentManager,
            @NonNull AdServicesLogger adServicesLogger,
            @NonNull AppImportanceFilter appImportanceFilter,
            @NonNull final Flags flags,
            int callerUid) {
        Objects.requireNonNull(devContext);
        Objects.requireNonNull(adSelectionEntryDao);
        Objects.requireNonNull(lightweightExecutorService);
        Objects.requireNonNull(backgroundExecutorService);
        Objects.requireNonNull(packageManager);
        Objects.requireNonNull(consentManager);
        Objects.requireNonNull(adServicesLogger);
        Objects.requireNonNull(appImportanceFilter);
        Objects.requireNonNull(flags);

        this.mAdSelectionEntryDao = adSelectionEntryDao;
        this.mLightweightExecutorService =
                MoreExecutors.listeningDecorator(lightweightExecutorService);
        this.mBackgroundExecutorService =
                MoreExecutors.listeningDecorator(backgroundExecutorService);
        this.mAdSelectionDevOverridesHelper =
                new AdSelectionDevOverridesHelper(devContext, mAdSelectionEntryDao);
        this.mPackageManager = packageManager;
        this.mConsentManager = consentManager;
        this.mAdServicesLogger = adServicesLogger;
        mFlags = flags;
        mAppImportanceFilter = appImportanceFilter;
        mCallerUid = callerUid;
        mCallerAppPackageName = devContext.getCallingAppPackageName();
    }

    /**
     * Configures our fetching logic relating to {@code adSelectionConfig} to use {@code
     * decisionLogicJS} instead of fetching from remote servers
     *
     * @param callback callback function to be called in case of success or failure
     */
    public void addOverride(
            @NonNull AdSelectionConfig adSelectionConfig,
            @NonNull String decisionLogicJS,
            @NonNull AdSelectionSignals trustedScoringSignals,
            @NonNull PerBuyerDecisionLogic perBuyerDecisionLogic,
            @NonNull AdSelectionOverrideCallback callback) {
        // Auto-generated variable name is too long for lint check
        int shortApiName =
                AD_SERVICES_API_CALLED__API_NAME__OVERRIDE_AD_SELECTION_CONFIG_REMOTE_INFO;

        FluentFuture.from(
                        mLightweightExecutorService.submit(
                                () -> {
                                    // Cannot read pH flags in the binder thread so this
                                    // checks will be done in a spawn thread.
                                    if (mFlags.getEnforceForegroundStatusForFledgeOverrides()) {
                                        mAppImportanceFilter.assertCallerIsInForeground(
                                                mCallerUid, shortApiName, null);
                                    }

                                    return null;
                                }))
                .transformAsync(
                        ignoredVoid ->
                                callAddOverride(
                                        adSelectionConfig,
                                        decisionLogicJS,
                                        trustedScoringSignals,
                                        perBuyerDecisionLogic),
                        mLightweightExecutorService)
                .addCallback(
                        new FutureCallback<Integer>() {
                            @Override
                            public void onSuccess(Integer result) {
                                sLogger.d("Add dev override for ad selection config succeeded!");
                                invokeSuccess(callback, shortApiName, result);
                            }

                            @Override
                            public void onFailure(Throwable t) {
                                sLogger.e(t, "Add dev override for ad selection config failed!");
                                notifyFailureToCaller(callback, t, shortApiName);
                            }
                        },
                        mLightweightExecutorService);
    }

    /**
     * Removes a decision logic override matching this {@code adSelectionConfig} and {@code
     * appPackageName}
     *
     * @param callback callback function to be called in case of success or failure
     */
    public void removeOverride(
            @NonNull AdSelectionConfig adSelectionConfig,
            @NonNull AdSelectionOverrideCallback callback) {
        // Auto-generated variable name is too long for lint check
        int shortApiName =
                AD_SERVICES_API_CALLED__API_NAME__REMOVE_AD_SELECTION_CONFIG_REMOTE_INFO_OVERRIDE;

        FluentFuture.from(
                        mLightweightExecutorService.submit(
                                () -> {
                                    // Cannot read pH flags in the binder thread so this
                                    // checks will be done in a spawn thread.
                                    if (mFlags.getEnforceForegroundStatusForFledgeOverrides()) {
                                        mAppImportanceFilter.assertCallerIsInForeground(
                                                mCallerUid, shortApiName, null);
                                    }

                                    return null;
                                }))
                .transformAsync(
                        ignoredVoid -> callRemoveOverride(adSelectionConfig),
                        mLightweightExecutorService)
                .addCallback(
                        new FutureCallback<Integer>() {
                            @Override
                            public void onSuccess(Integer result) {
                                sLogger.d(
                                        "Removing dev override for ad selection config succeeded!");
                                invokeSuccess(callback, shortApiName, result);
                            }

                            @Override
                            public void onFailure(Throwable t) {
                                sLogger.e(
                                        t, "Removing dev override for ad selection config failed!");
                                notifyFailureToCaller(callback, t, shortApiName);
                            }
                        },
                        mLightweightExecutorService);
    }

    /**
     * Removes all ad selection overrides matching the {@code appPackageName}
     *
     * @param callback callback function to be called in case of success or failure
     */
    public void removeAllOverridesForAdSelectionConfig(
            @NonNull AdSelectionOverrideCallback callback) {
        // Auto-generated variable name is too long for lint check
        int shortApiName =
                AD_SERVICES_API_CALLED__API_NAME__RESET_ALL_AD_SELECTION_CONFIG_REMOTE_OVERRIDES;

        FluentFuture.from(
                        mLightweightExecutorService.submit(
                                () -> {
                                    // Cannot read pH flags in the binder thread so this
                                    // checks will be done in a spawn thread.
                                    if (mFlags.getEnforceForegroundStatusForFledgeOverrides()) {
                                        mAppImportanceFilter.assertCallerIsInForeground(
                                                mCallerUid, shortApiName, null);
                                    }

                                    return null;
                                }))
                .transformAsync(
                        ignoredVoid -> callRemoveAllOverrides(), mLightweightExecutorService)
                .addCallback(
                        new FutureCallback<Integer>() {
                            @Override
                            public void onSuccess(Integer result) {
                                sLogger.d(
                                        "Removing all dev overrides for ad selection config"
                                                + " succeeded!");
                                invokeSuccess(callback, shortApiName, result);
                            }

                            @Override
                            public void onFailure(Throwable t) {
                                sLogger.e(
                                        t,
                                        "Removing all dev overrides for ad selection config"
                                                + " failed!");
                                notifyFailureToCaller(callback, t, shortApiName);
                            }
                        },
                        mLightweightExecutorService);
    }

    /**
     * Configures our fetching logic relating to {@code config} to use {@code selectionLogicJs}
     * instead of fetching from remote servers
     *
     * @param callback callback function to be called in case of success or failure
     */
    public void addOverride(
            @NonNull AdSelectionFromOutcomesConfig config,
            @NonNull String selectionLogicJs,
            @NonNull AdSelectionSignals selectionSignals,
            @NonNull AdSelectionOverrideCallback callback) {
        // Auto-generated variable name is too long for lint check
        int shortApiName = AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN;

        FluentFuture.from(
                        mLightweightExecutorService.submit(
                                () -> {
                                    // Cannot read pH flags in the binder thread so this
                                    // checks will be done in a spawn thread.
                                    if (mFlags.getEnforceForegroundStatusForFledgeOverrides()) {
                                        mAppImportanceFilter.assertCallerIsInForeground(
                                                mCallerUid, shortApiName, null);
                                    }
                                    return null;
                                }))
                .transformAsync(
                        ignoredVoid -> callAddOverride(config, selectionLogicJs, selectionSignals),
                        mLightweightExecutorService)
                .addCallback(
                        new FutureCallback<Integer>() {
                            @Override
                            public void onSuccess(Integer result) {
                                sLogger.d(
                                        "Add dev override for ad selection config from outcomes"
                                                + " succeeded!");
                                invokeSuccess(callback, shortApiName, result);
                            }

                            @Override
                            public void onFailure(Throwable t) {
                                sLogger.e(
                                        t,
                                        "Add dev override for ad selection config from outcomes"
                                                + " failed!");
                                notifyFailureToCaller(callback, t, shortApiName);
                            }
                        },
                        mLightweightExecutorService);
    }

    /**
     * Removes a decision logic override matching this {@code config} and {@code appPackageName}
     *
     * @param callback callback function to be called in case of success or failure
     */
    public void removeOverride(
            @NonNull AdSelectionFromOutcomesConfig config,
            @NonNull AdSelectionOverrideCallback callback) {
        // Auto-generated variable name is too long for lint check
        int shortApiName = AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN;

        FluentFuture.from(
                        mLightweightExecutorService.submit(
                                () -> {
                                    // Cannot read pH flags in the binder thread so this
                                    // checks will be done in a spawn thread.
                                    if (mFlags.getEnforceForegroundStatusForFledgeOverrides()) {
                                        mAppImportanceFilter.assertCallerIsInForeground(
                                                mCallerUid, shortApiName, null);
                                    }

                                    return null;
                                }))
                .transformAsync(
                        ignoredVoid -> callRemoveOverride(config), mLightweightExecutorService)
                .addCallback(
                        new FutureCallback<Integer>() {
                            @Override
                            public void onSuccess(Integer result) {
                                sLogger.d(
                                        "Removing dev override for ad selection config from"
                                                + " outcomes succeeded!");
                                invokeSuccess(callback, shortApiName, result);
                            }

                            @Override
                            public void onFailure(Throwable t) {
                                sLogger.e(
                                        t,
                                        "Removing dev override for ad selection config from"
                                                + " outcomes failed!");
                                notifyFailureToCaller(callback, t, shortApiName);
                            }
                        },
                        mLightweightExecutorService);
    }

    /**
     * Removes all ad selection overrides matching the {@code appPackageName}
     *
     * @param callback callback function to be called in case of success or failure
     */
    public void removeAllOverridesForAdSelectionFromOutcomes(
            @NonNull AdSelectionOverrideCallback callback) {
        // Auto-generated variable name is too long for lint check
        int shortApiName = AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN;

        FluentFuture.from(
                        mLightweightExecutorService.submit(
                                () -> {
                                    // Cannot read pH flags in the binder thread so this
                                    // checks will be done in a spawn thread.
                                    if (mFlags.getEnforceForegroundStatusForFledgeOverrides()) {
                                        mAppImportanceFilter.assertCallerIsInForeground(
                                                mCallerUid, shortApiName, null);
                                    }

                                    return null;
                                }))
                .transformAsync(
                        ignoredVoid -> callRemoveAllSelectionLogicOverrides(),
                        mLightweightExecutorService)
                .addCallback(
                        new FutureCallback<Integer>() {
                            @Override
                            public void onSuccess(Integer result) {
                                sLogger.d(
                                        "Removing all dev overrides for ad selection config from"
                                                + " outcomes succeeded!");
                                invokeSuccess(callback, shortApiName, result);
                            }

                            @Override
                            public void onFailure(Throwable t) {
                                sLogger.e(
                                        t,
                                        "Removing all dev overrides for ad selection config from"
                                                + " outcomes failed!");
                                notifyFailureToCaller(callback, t, shortApiName);
                            }
                        },
                        mLightweightExecutorService);
    }

    private FluentFuture<Integer> callAddOverride(
            @NonNull AdSelectionConfig adSelectionConfig,
            @NonNull String decisionLogicJS,
            @NonNull AdSelectionSignals trustedScoringSignals,
            @NonNull PerBuyerDecisionLogic perBuyerDecisionLogic) {
        return FluentFuture.from(
                mBackgroundExecutorService.submit(
                        () -> {
                            AdServicesApiConsent userConsent = getAdServicesApiConsent();

                            if (!userConsent.isGiven()) {
                                return AdServicesStatusUtils.STATUS_USER_CONSENT_REVOKED;
                            }

                            mAdSelectionDevOverridesHelper.addAdSelectionSellerOverride(
                                    adSelectionConfig,
                                    decisionLogicJS,
                                    trustedScoringSignals,
                                    perBuyerDecisionLogic);
                            return AdServicesStatusUtils.STATUS_SUCCESS;
                        }));
    }

    private FluentFuture<Integer> callRemoveOverride(@NonNull AdSelectionConfig adSelectionConfig) {
        return FluentFuture.from(
                mBackgroundExecutorService.submit(
                        () -> {
                            AdServicesApiConsent userConsent = getAdServicesApiConsent();

                            if (!userConsent.isGiven()) {
                                return AdServicesStatusUtils.STATUS_USER_CONSENT_REVOKED;
                            }

                            mAdSelectionDevOverridesHelper.removeAdSelectionSellerOverride(
                                    adSelectionConfig);
                            return AdServicesStatusUtils.STATUS_SUCCESS;
                        }));
    }

    private FluentFuture<Integer> callRemoveAllOverrides() {
        return FluentFuture.from(
                mBackgroundExecutorService.submit(
                        () -> {
                            AdServicesApiConsent userConsent = getAdServicesApiConsent();

                            if (!userConsent.isGiven()) {
                                return AdServicesStatusUtils.STATUS_USER_CONSENT_REVOKED;
                            }

                            mAdSelectionDevOverridesHelper.removeAllDecisionLogicOverrides();
                            return AdServicesStatusUtils.STATUS_SUCCESS;
                        }));
    }

    private FluentFuture<Integer> callAddOverride(
            @NonNull AdSelectionFromOutcomesConfig config,
            @NonNull String selectionLogicJs,
            @NonNull AdSelectionSignals selectionSignals) {
        return FluentFuture.from(
                mBackgroundExecutorService.submit(
                        () -> {
                            AdServicesApiConsent userConsent = getAdServicesApiConsent();

                            if (!userConsent.isGiven()) {
                                return AdServicesStatusUtils.STATUS_USER_CONSENT_REVOKED;
                            }

                            mAdSelectionDevOverridesHelper.addAdSelectionOutcomeSelectorOverride(
                                    config, selectionLogicJs, selectionSignals);
                            return AdServicesStatusUtils.STATUS_SUCCESS;
                        }));
    }

    private FluentFuture<Integer> callRemoveOverride(
            @NonNull AdSelectionFromOutcomesConfig config) {
        return FluentFuture.from(
                mBackgroundExecutorService.submit(
                        () -> {
                            AdServicesApiConsent userConsent = getAdServicesApiConsent();

                            if (!userConsent.isGiven()) {
                                return AdServicesStatusUtils.STATUS_USER_CONSENT_REVOKED;
                            }

                            mAdSelectionDevOverridesHelper.removeAdSelectionOutcomeSelectorOverride(
                                    config);
                            return AdServicesStatusUtils.STATUS_SUCCESS;
                        }));
    }

    private FluentFuture<Integer> callRemoveAllSelectionLogicOverrides() {
        return FluentFuture.from(
                mBackgroundExecutorService.submit(
                        () -> {
                            AdServicesApiConsent userConsent = getAdServicesApiConsent();

                            if (!userConsent.isGiven()) {
                                return AdServicesStatusUtils.STATUS_USER_CONSENT_REVOKED;
                            }

                            mAdSelectionDevOverridesHelper.removeAllSelectionLogicOverrides();
                            return AdServicesStatusUtils.STATUS_SUCCESS;
                        }));
    }

    private AdServicesApiConsent getAdServicesApiConsent() {
        AdServicesApiConsent userConsent = mConsentManager.getConsent(AdServicesApiType.FLEDGE);

        return userConsent;
    }

    /** Invokes the onFailure function from the callback and handles the exception. */
    private void invokeFailure(
            @NonNull AdSelectionOverrideCallback callback,
            int statusCode,
            String errorMessage,
            int apiName) {
        int resultCode = statusCode;
        try {
            callback.onFailure(
                    new FledgeErrorResponse.Builder()
                            .setStatusCode(statusCode)
                            .setErrorMessage(errorMessage)
                            .build());
        } catch (RemoteException e) {
            sLogger.e(e, "Unable to send failed result to the callback");
            resultCode = AdServicesStatusUtils.STATUS_UNKNOWN_ERROR;
            throw e.rethrowFromSystemServer();
        } finally {
            mAdServicesLogger.logFledgeApiCallStats(
                    apiName, mCallerAppPackageName, resultCode, /*latencyMs=*/ 0);
        }
    }

    /** Invokes the onSuccess function from the callback and handles the exception. */
    private void invokeSuccess(
            @NonNull AdSelectionOverrideCallback callback, int apiName, Integer resultCode) {
        int resultCodeInt = AdServicesStatusUtils.STATUS_UNSET;
        if (resultCode != null) {
            resultCodeInt = resultCode;
        }
        try {
            callback.onSuccess();
        } catch (RemoteException e) {
            sLogger.e(e, "Unable to send successful result to the callback");
            resultCodeInt = AdServicesStatusUtils.STATUS_UNKNOWN_ERROR;
            throw e.rethrowFromSystemServer();
        } finally {
            mAdServicesLogger.logFledgeApiCallStats(
                    apiName, mCallerAppPackageName, resultCodeInt, /*latencyMs=*/ 0);
        }
    }

    private void notifyFailureToCaller(
            @NonNull AdSelectionOverrideCallback callback, @NonNull Throwable t, int apiName) {
        if (t instanceof IllegalArgumentException) {
            invokeFailure(
                    callback,
                    AdServicesStatusUtils.STATUS_INVALID_ARGUMENT,
                    t.getMessage(),
                    apiName);
        } else if (t instanceof WrongCallingApplicationStateException) {
            invokeFailure(
                    callback,
                    AdServicesStatusUtils.STATUS_BACKGROUND_CALLER,
                    t.getMessage(),
                    apiName);
        } else if (t instanceof IllegalStateException) {
            invokeFailure(
                    callback, AdServicesStatusUtils.STATUS_INTERNAL_ERROR, t.getMessage(), apiName);
        } else {
            invokeFailure(
                    callback, AdServicesStatusUtils.STATUS_UNAUTHORIZED, t.getMessage(), apiName);
        }
    }
}
