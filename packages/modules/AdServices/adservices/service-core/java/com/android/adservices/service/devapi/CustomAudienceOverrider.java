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

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__OVERRIDE_CUSTOM_AUDIENCE_REMOTE_INFO;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REMOVE_CUSTOM_AUDIENCE_REMOTE_INFO_OVERRIDE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__RESET_ALL_CUSTOM_AUDIENCE_OVERRIDES;

import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdServicesStatusUtils;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.FledgeErrorResponse;
import android.adservices.customaudience.CustomAudienceOverrideCallback;
import android.annotation.NonNull;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.RemoteException;

import androidx.annotation.RequiresApi;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.AppImportanceFilter;
import com.android.adservices.service.common.AppImportanceFilter.WrongCallingApplicationStateException;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.stats.AdServicesLogger;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.Objects;
import java.util.concurrent.ExecutorService;

/** Encapsulates the Custom Audience Override Logic */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
public class CustomAudienceOverrider {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    @NonNull private final DevContext mDevContext;
    @NonNull private final CustomAudienceDao mCustomAudienceDao;
    @NonNull private final ListeningExecutorService mListeningExecutorService;
    @NonNull private final CustomAudienceDevOverridesHelper mCustomAudienceDevOverridesHelper;
    @NonNull private final PackageManager mPackageManager;
    @NonNull private final ConsentManager mConsentManager;
    @NonNull private final AdServicesLogger mAdServicesLogger;
    @NonNull private final AppImportanceFilter mAppImportanceFilter;
    @NonNull private final Flags mFlags;

    /**
     * Creates an instance of {@link CustomAudienceOverrider} with the given {@link DevContext},
     * {@link CustomAudienceDao}, executor, {@link ConsentManager} and {@link
     * CustomAudienceDevOverridesHelper}.
     */
    public CustomAudienceOverrider(
            @NonNull DevContext devContext,
            @NonNull CustomAudienceDao customAudienceDao,
            @NonNull ExecutorService executorService,
            @NonNull PackageManager packageManager,
            @NonNull ConsentManager consentManager,
            @NonNull AdServicesLogger adServicesLogger,
            @NonNull AppImportanceFilter appImportanceFilter,
            @NonNull Flags flags) {
        Objects.requireNonNull(devContext);
        Objects.requireNonNull(customAudienceDao);
        Objects.requireNonNull(executorService);
        Objects.requireNonNull(packageManager);
        Objects.requireNonNull(consentManager);
        Objects.requireNonNull(adServicesLogger);
        Objects.requireNonNull(appImportanceFilter);
        Objects.requireNonNull(flags);

        this.mDevContext = devContext;
        this.mCustomAudienceDao = customAudienceDao;
        this.mListeningExecutorService = MoreExecutors.listeningDecorator(executorService);
        this.mCustomAudienceDevOverridesHelper =
                new CustomAudienceDevOverridesHelper(devContext, mCustomAudienceDao);
        this.mPackageManager = packageManager;
        this.mConsentManager = consentManager;
        this.mAdServicesLogger = adServicesLogger;
        this.mAppImportanceFilter = appImportanceFilter;
        this.mFlags = flags;
    }

    /**
     * Configures our fetching logic relating to the combination of {@code owner}, {@code buyer},
     * and {@code name} to use {@code biddingLogicJS} and {@code trustedBiddingSignals} instead of
     * fetching from remote servers.
     *
     * <p>If the {@code owner} does not match the package name derived from the calling UID, fail
     * silently.
     *
     * @param callback callback function to be called in case of success or failure
     */
    public void addOverride(
            @NonNull String owner,
            @NonNull AdTechIdentifier buyer,
            @NonNull String name,
            @NonNull String biddingLogicJS,
            long biddingLogicJsVersion,
            @NonNull AdSelectionSignals trustedBiddingSignals,
            @NonNull CustomAudienceOverrideCallback callback) {
        Objects.requireNonNull(callback);

        // Auto-generated variable name is too long for lint check
        final int shortApiName =
                AD_SERVICES_API_CALLED__API_NAME__OVERRIDE_CUSTOM_AUDIENCE_REMOTE_INFO;

        FluentFuture.from(
                        mListeningExecutorService.submit(
                                () -> {
                                    Objects.requireNonNull(owner);
                                    Objects.requireNonNull(buyer);
                                    Objects.requireNonNull(name);
                                    Objects.requireNonNull(biddingLogicJS);
                                    Objects.requireNonNull(trustedBiddingSignals);

                                    if (mFlags.getEnforceForegroundStatusForFledgeOverrides()) {
                                        mAppImportanceFilter.assertCallerIsInForeground(
                                                owner, shortApiName, null);
                                    }
                                    return null;
                                }))
                .transformAsync(
                        ignoredVoid ->
                                callAddOverride(
                                        owner,
                                        buyer,
                                        name,
                                        biddingLogicJS,
                                        biddingLogicJsVersion,
                                        trustedBiddingSignals),
                        mListeningExecutorService)
                .addCallback(
                        new FutureCallback<Integer>() {
                            @Override
                            public void onSuccess(Integer result) {
                                sLogger.d("Add dev override succeeded!");
                                invokeSuccess(callback, shortApiName, result);
                            }

                            @Override
                            public void onFailure(Throwable t) {
                                sLogger.e(t, "Add dev override failed!");
                                notifyFailureToCaller(callback, t, shortApiName);
                            }
                        },
                        mListeningExecutorService);
    }

    /**
     * Removes a bidding logic override matching the combination of {@code owner}, {@code buyer},
     * {@code name}, and {@code appPackageName} derived from the calling UID.
     *
     * @param callback callback function to be called in case of success or failure
     */
    public void removeOverride(
            @NonNull String owner,
            @NonNull AdTechIdentifier buyer,
            @NonNull String name,
            @NonNull CustomAudienceOverrideCallback callback) {
        Objects.requireNonNull(callback);

        // Auto-generated variable name is too long for lint check
        final int shortApiName =
                AD_SERVICES_API_CALLED__API_NAME__REMOVE_CUSTOM_AUDIENCE_REMOTE_INFO_OVERRIDE;

        FluentFuture.from(
                        mListeningExecutorService.submit(
                                () -> {
                                    Objects.requireNonNull(owner);
                                    Objects.requireNonNull(buyer);
                                    Objects.requireNonNull(name);

                                    if (mFlags.getEnforceForegroundStatusForFledgeOverrides()) {
                                        mAppImportanceFilter.assertCallerIsInForeground(
                                                owner, shortApiName, null);
                                    }
                                    return null;
                                }))
                .transformAsync(
                        ignoredVoid -> callRemoveOverride(owner, buyer, name),
                        mListeningExecutorService)
                .addCallback(
                        new FutureCallback<Integer>() {
                            @Override
                            public void onSuccess(Integer result) {
                                sLogger.d("Removing dev override succeeded with status %d", result);
                                invokeSuccess(callback, shortApiName, result);
                            }

                            @Override
                            public void onFailure(Throwable t) {
                                sLogger.e(t, "Removing dev override failed!");
                                notifyFailureToCaller(callback, t, shortApiName);
                            }
                        },
                        mListeningExecutorService);
    }

    /**
     * Removes all custom audience overrides matching the {@code appPackageName} associated with the
     * {@code callerUid}.
     *
     * @param callback callback function to be called in case of success or failure
     */
    public void removeAllOverrides(
            @NonNull CustomAudienceOverrideCallback callback, int callerUid) {
        Objects.requireNonNull(callback);

        // Auto-generated variable name is too long for lint check
        final int shortApiName =
                AD_SERVICES_API_CALLED__API_NAME__RESET_ALL_CUSTOM_AUDIENCE_OVERRIDES;

        FluentFuture.from(
                        mListeningExecutorService.submit(
                                () -> {
                                    if (mFlags.getEnforceForegroundStatusForFledgeOverrides()) {
                                        mAppImportanceFilter.assertCallerIsInForeground(
                                                callerUid, shortApiName, null);
                                    }
                                    return null;
                                }))
                .transformAsync(ignoredVoid -> callRemoveAllOverrides(), mListeningExecutorService)
                .addCallback(
                        new FutureCallback<Integer>() {
                            @Override
                            public void onSuccess(Integer result) {
                                sLogger.d("Removing all dev overrides succeeded!");
                                invokeSuccess(callback, shortApiName, result);
                            }

                            @Override
                            public void onFailure(Throwable t) {
                                sLogger.e(t, "Removing all dev overrides failed!");
                                notifyFailureToCaller(callback, t, shortApiName);
                            }
                        },
                        mListeningExecutorService);
    }

    private FluentFuture<Integer> callAddOverride(
            @NonNull String owner,
            @NonNull AdTechIdentifier buyer,
            @NonNull String name,
            @NonNull String biddingLogicJS,
            long biddingLogicJsVersion,
            @NonNull AdSelectionSignals trustedBiddingData) {
        return FluentFuture.from(
                mListeningExecutorService.submit(
                        () -> {
                            if (mConsentManager.isFledgeConsentRevokedForApp(
                                    mDevContext.getCallingAppPackageName())) {
                                sLogger.v("User consent is revoked!");
                                return AdServicesStatusUtils.STATUS_USER_CONSENT_REVOKED;
                            }

                            mCustomAudienceDevOverridesHelper.addOverride(
                                    owner,
                                    buyer,
                                    name,
                                    biddingLogicJS,
                                    biddingLogicJsVersion,
                                    trustedBiddingData);
                            return AdServicesStatusUtils.STATUS_SUCCESS;
                        }));
    }

    private FluentFuture<Integer> callRemoveOverride(
            @NonNull String owner, @NonNull AdTechIdentifier buyer, @NonNull String name) {
        return FluentFuture.from(
                mListeningExecutorService.submit(
                        () -> {
                            if (mConsentManager.isFledgeConsentRevokedForApp(
                                    mDevContext.getCallingAppPackageName())) {
                                return AdServicesStatusUtils.STATUS_USER_CONSENT_REVOKED;
                            }

                            mCustomAudienceDevOverridesHelper.removeOverride(owner, buyer, name);
                            return AdServicesStatusUtils.STATUS_SUCCESS;
                        }));
    }

    private FluentFuture<Integer> callRemoveAllOverrides() {
        return FluentFuture.from(
                mListeningExecutorService.submit(
                        () -> {
                            if (mConsentManager.isFledgeConsentRevokedForApp(
                                    mDevContext.getCallingAppPackageName())) {
                                return AdServicesStatusUtils.STATUS_USER_CONSENT_REVOKED;
                            }

                            mCustomAudienceDevOverridesHelper.removeAllOverrides();
                            return AdServicesStatusUtils.STATUS_SUCCESS;
                        }));
    }

    /** Invokes the onFailure function from the callback and handles the exception. */
    private void invokeFailure(
            @NonNull CustomAudienceOverrideCallback callback,
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
            throw e.rethrowAsRuntimeException();
        } finally {
            mAdServicesLogger.logFledgeApiCallStats(
                    apiName,
                    this.mDevContext.getCallingAppPackageName(),
                    resultCode,
                    /*latencyMs=*/ 0);
        }
    }

    /** Invokes the onSuccess function from the callback and handles the exception. */
    private void invokeSuccess(
            @NonNull CustomAudienceOverrideCallback callback, int apiName, Integer resultCode) {
        int resultCodeInt = AdServicesStatusUtils.STATUS_UNSET;
        if (resultCode != null) {
            resultCodeInt = resultCode;
        }
        try {
            callback.onSuccess();
        } catch (RemoteException e) {
            sLogger.e(e, "Unable to send successful result to the callback");
            resultCodeInt = AdServicesStatusUtils.STATUS_UNKNOWN_ERROR;
            throw e.rethrowAsRuntimeException();
        } finally {
            mAdServicesLogger.logFledgeApiCallStats(
                    apiName,
                    this.mDevContext.getCallingAppPackageName(),
                    resultCodeInt,
                    /*latencyMs=*/ 0);
        }
    }

    private void notifyFailureToCaller(
            @NonNull CustomAudienceOverrideCallback callback, @NonNull Throwable t, int apiName) {
        int resultCode;
        if (t instanceof IllegalArgumentException) {
            resultCode = AdServicesStatusUtils.STATUS_INVALID_ARGUMENT;
        } else if (t instanceof WrongCallingApplicationStateException) {
            resultCode = AdServicesStatusUtils.STATUS_BACKGROUND_CALLER;
        } else if (t instanceof IllegalStateException) {
            resultCode = AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
        } else if (t instanceof SecurityException) {
            resultCode = AdServicesStatusUtils.STATUS_UNAUTHORIZED;
        } else {
            resultCode = AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
        }
        invokeFailure(callback, resultCode, t.getMessage(), apiName);
    }
}
