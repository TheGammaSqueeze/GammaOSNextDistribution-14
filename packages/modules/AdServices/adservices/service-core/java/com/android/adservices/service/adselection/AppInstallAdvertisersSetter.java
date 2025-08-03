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

package com.android.adservices.service.adselection;

import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;
import static android.adservices.common.AdServicesStatusUtils.STATUS_USER_CONSENT_REVOKED;

import static com.android.adservices.service.common.Throttler.ApiKey.FLEDGE_API_SET_APP_INSTALL_ADVERTISERS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__SET_APP_INSTALL_ADVERTISERS;

import android.adservices.adselection.SetAppInstallAdvertisersCallback;
import android.adservices.adselection.SetAppInstallAdvertisersInput;
import android.adservices.common.AdServicesStatusUtils;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.FledgeErrorResponse;
import android.annotation.NonNull;
import android.os.Build;
import android.os.RemoteException;

import androidx.annotation.RequiresApi;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.adselection.AppInstallDao;
import com.android.adservices.data.adselection.DBAppInstallPermissions;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.AdSelectionServiceFilter;
import com.android.adservices.service.common.AdTechIdentifierValidator;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.exception.FilterException;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;

/** Encapsulates the Set App Install Advertisers logic */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
public class AppInstallAdvertisersSetter {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();

    @VisibleForTesting
    public static final String FILTERING_IS_DISABLED = "Ad selection filtering disabled";

    private static final String AD_TECH_IDENTIFIER_ERROR_MESSAGE_SCOPE = "app install adtech set";
    private static final String AD_TECH_IDENTIFIER_ERROR_MESSAGE_ROLE = "adtech";

    @NonNull private final AppInstallDao mAppInstallDao;
    @NonNull private final ListeningExecutorService mExecutorService;
    @NonNull private final AdServicesLogger mAdServicesLogger;
    @NonNull private final AdSelectionServiceFilter mAdSelectionServiceFilter;
    @NonNull private final ConsentManager mConsentManager;
    private final int mCallerUid;
    private DevContext mDevContext;
    @NonNull private final Flags mFlags;

    public AppInstallAdvertisersSetter(
            @NonNull AppInstallDao appInstallDao,
            @NonNull ExecutorService executor,
            @NonNull AdServicesLogger adServicesLogger,
            @NonNull final Flags flags,
            @NonNull final AdSelectionServiceFilter adSelectionServiceFilter,
            @NonNull final ConsentManager consentManager,
            int callerUid,
            @NonNull DevContext devContext) {
        Objects.requireNonNull(appInstallDao);
        Objects.requireNonNull(executor);
        Objects.requireNonNull(adServicesLogger);
        Objects.requireNonNull(flags);
        Objects.requireNonNull(adSelectionServiceFilter);
        Objects.requireNonNull(consentManager);
        Objects.requireNonNull(devContext);

        mAppInstallDao = appInstallDao;
        mExecutorService = MoreExecutors.listeningDecorator(executor);
        mAdServicesLogger = adServicesLogger;
        mCallerUid = callerUid;
        mAdSelectionServiceFilter = adSelectionServiceFilter;
        mConsentManager = consentManager;
        mDevContext = devContext;
        mFlags = flags;
    }

    /**
     * Sets the app install advertisers for the caller.
     *
     * <p>Stores the association between the listed adtechs and the caller in the app install
     * database.
     *
     * @param input object containing the package name of the caller and a list of adtechs
     * @param callback callback function to be called in case of success or failure
     */
    public void setAppInstallAdvertisers(
            @NonNull SetAppInstallAdvertisersInput input,
            @NonNull SetAppInstallAdvertisersCallback callback) {
        sLogger.v("Executing setAppInstallAdvertisers API");

        // Auto-generated variable name is too long for lint check
        int shortApiName = AD_SERVICES_API_CALLED__API_NAME__SET_APP_INSTALL_ADVERTISERS;

        FluentFuture.from(
                        mExecutorService.submit(
                                () ->
                                        doSetAppInstallAdvertisers(
                                                input.getAdvertisers(),
                                                input.getCallerPackageName())))
                .addCallback(
                        new FutureCallback<Void>() {
                            @Override
                            public void onSuccess(Void result) {
                                sLogger.v("SetAppInstallAdvertisers succeeded!");
                                // Note: Success is logged before the callback to ensure
                                // deterministic testing.
                                mAdServicesLogger.logFledgeApiCallStats(
                                        shortApiName,
                                        input.getCallerPackageName(),
                                        STATUS_SUCCESS,
                                        /*latencyMs=*/ 0);
                                invokeSuccess(callback);
                            }

                            @Override
                            public void onFailure(Throwable t) {
                                sLogger.e(t, "SetAppInstallAdvertisers invocation failed!");
                                if ((t instanceof FilterException
                                        && t.getCause()
                                                instanceof
                                                ConsentManager.RevokedConsentException)) {
                                    invokeSuccess(callback);
                                } else if (t instanceof ConsentManager.RevokedConsentException) {
                                    // TODO(b/271921887): Remove the duplicate check once
                                    // app-specific consent check has been moved to a shared
                                    // validation component.
                                    // TODO(b/271921887): Remove the failure log once app-specific
                                    //  consent check has been moved to a shared validation
                                    // component.
                                    mAdServicesLogger.logFledgeApiCallStats(
                                            shortApiName,
                                            input.getCallerPackageName(),
                                            STATUS_USER_CONSENT_REVOKED,
                                            /*latencyMs=*/ 0);
                                    invokeSuccess(callback);
                                } else {
                                    notifyFailureToCaller(
                                            input.getCallerPackageName(), callback, t);
                                }
                            }
                        },
                        mExecutorService);
    }

    /** Invokes the onFailure function from the callback and handles the exception. */
    private void invokeFailure(
            @NonNull SetAppInstallAdvertisersCallback callback,
            int statusCode,
            String errorMessage) {
        try {
            callback.onFailure(
                    new FledgeErrorResponse.Builder()
                            .setStatusCode(statusCode)
                            .setErrorMessage(errorMessage)
                            .build());
        } catch (RemoteException e) {
            // TODO(b/269724912) Unit test this block
            sLogger.e(e, "Unable to send failed result to the callback");
            throw e.rethrowFromSystemServer();
        }
    }

    /** Invokes the onSuccess function from the callback and handles the exception. */
    private void invokeSuccess(@NonNull SetAppInstallAdvertisersCallback callback) {
        try {
            callback.onSuccess();
        } catch (RemoteException e) {
            // TODO(b/269724912) Unit test this block
            sLogger.e(e, "Unable to send successful result to the callback");
            throw e.rethrowFromSystemServer();
        }
    }

    private void notifyFailureToCaller(
            @NonNull String callerAppPackageName,
            @NonNull SetAppInstallAdvertisersCallback callback,
            @NonNull Throwable t) {
        int resultCode;

        boolean isFilterException = t instanceof FilterException;

        if (isFilterException) {
            resultCode = FilterException.getResultCode(t);
        } else if (t instanceof IllegalArgumentException) {
            resultCode = AdServicesStatusUtils.STATUS_INVALID_ARGUMENT;
        } else {
            resultCode = AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
        }

        // Skip logging if a FilterException occurs.
        // AdSelectionServiceFilter ensures the failing assertion is logged internally.
        // Note: Failure is logged before the callback to ensure deterministic testing.
        if (!isFilterException) {
            mAdServicesLogger.logFledgeApiCallStats(
                    AD_SERVICES_API_CALLED__API_NAME__SET_APP_INSTALL_ADVERTISERS,
                    callerAppPackageName,
                    resultCode,
                    /*latencyMs=*/ 0);
        }

        invokeFailure(callback, resultCode, t.getMessage());
    }

    private Void doSetAppInstallAdvertisers(
            Set<AdTechIdentifier> advertisers, String callerPackageName) {
        if (!mFlags.getFledgeAdSelectionFilteringEnabled()) {
            sLogger.v(FILTERING_IS_DISABLED);
            throw new IllegalStateException(FILTERING_IS_DISABLED);
        }

        validateRequest(advertisers, callerPackageName);

        sLogger.v(
                "Writing %d adtechs to the calling app's app install permission list",
                advertisers.size());
        ArrayList<DBAppInstallPermissions> permissions = new ArrayList<>();
        for (AdTechIdentifier advertiser : advertisers) {
            permissions.add(
                    new DBAppInstallPermissions.Builder()
                            .setPackageName(callerPackageName)
                            .setBuyer(advertiser)
                            .build());
        }
        mAppInstallDao.setAdTechsForPackage(callerPackageName, permissions);
        sLogger.v(
                "Wrote %d adtechs to the calling app's app install permission list",
                advertisers.size());
        return null;
    }

    private void validateRequest(Set<AdTechIdentifier> advertisers, String callerPackageName) {
        sLogger.v("Validating setAppInstallAdvertisers Request");
        mAdSelectionServiceFilter.filterRequest(
                null,
                callerPackageName,
                true,
                false,
                mCallerUid,
                AD_SERVICES_API_CALLED__API_NAME__SET_APP_INSTALL_ADVERTISERS,
                FLEDGE_API_SET_APP_INSTALL_ADVERTISERS,
                mDevContext);
        (new AdvertiserSetValidator(
                        new AdTechIdentifierValidator(
                                AD_TECH_IDENTIFIER_ERROR_MESSAGE_SCOPE,
                                AD_TECH_IDENTIFIER_ERROR_MESSAGE_ROLE)))
                .validate(advertisers);
        if (mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(callerPackageName)) {
            throw new ConsentManager.RevokedConsentException();
        }
    }
}
