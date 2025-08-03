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

package com.android.adservices.service.common;

import static com.android.adservices.service.common.AppManifestConfigCall.API_PROTECTED_SIGNALS;

import android.adservices.common.AdTechIdentifier;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.LimitExceededException;

import androidx.annotation.RequiresApi;

import com.android.adservices.LoggerFactory;
import com.android.adservices.service.Flags;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.devapi.DevContext;

import java.util.Objects;

/** Composite filter for protected signals requests. */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
public class ProtectedSignalsServiceFilter extends AbstractFledgeServiceFilter {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();

    public ProtectedSignalsServiceFilter(
            @NonNull Context context,
            @NonNull ConsentManager consentManager,
            @NonNull Flags flags,
            @NonNull AppImportanceFilter appImportanceFilter,
            @NonNull FledgeAuthorizationFilter fledgeAuthorizationFilter,
            @NonNull FledgeAllowListsFilter fledgeAllowListsFilter,
            @NonNull Throttler throttler) {
        super(
                context,
                consentManager,
                flags,
                appImportanceFilter,
                fledgeAuthorizationFilter,
                fledgeAllowListsFilter,
                throttler);
    }

    /** Not used or implemented */
    @Override
    public void filterRequest(
            @Nullable AdTechIdentifier adTech,
            @NonNull String callerPackageName,
            boolean enforceForeground,
            boolean enforceConsent,
            int callerUid,
            int apiName,
            @NonNull Throttler.ApiKey apiKey,
            @NonNull DevContext devContext) {
        throw new UnsupportedOperationException();
    }

    /**
     * Composite filter for updateSignals requests.
     *
     * @param uriForAdTech a {@link Uri} matching the ad tech to check against
     * @param callerPackageName caller package name to be validated
     * @param enforceForeground whether to enforce a foreground check
     * @param enforceConsent whether to enforce per-app consent
     * @param callerUid caller's uid from the Binder thread
     * @param apiName the id of the api being called
     * @param apiKey api-specific throttler key
     * @throws FledgeAuthorizationFilter.CallerMismatchException if the {@code callerPackageName} is
     *     not valid
     * @throws AppImportanceFilter.WrongCallingApplicationStateException if the foreground check is
     *     enabled and fails
     * @throws FledgeAuthorizationFilter.AdTechNotAllowedException if the ad tech is not authorized
     *     to perform the operation
     * @throws FledgeAllowListsFilter.AppNotAllowedException if the package is not authorized.
     * @throws LimitExceededException if the provided {@code callerPackageName} exceeds the rate
     *     limits
     */
    public AdTechIdentifier filterRequestAndExtractIdentifier(
            @NonNull Uri uriForAdTech,
            @NonNull String callerPackageName,
            boolean disableEnrollmentCheck,
            boolean enforceForeground,
            boolean enforceConsent,
            int callerUid,
            int apiName,
            @NonNull Throttler.ApiKey apiKey,
            DevContext devContext) {
        Objects.requireNonNull(uriForAdTech);
        Objects.requireNonNull(callerPackageName);
        Objects.requireNonNull(apiKey);

        sLogger.v("Validating caller package name.");
        assertCallerPackageName(callerPackageName, callerUid, apiName);

        sLogger.v("Validating API is not throttled.");
        assertCallerNotThrottled(callerPackageName, apiKey);

        if (enforceForeground) {
            sLogger.v("Checking caller is in foreground.");
            assertForegroundCaller(callerUid, apiName);
        }

        AdTechIdentifier adTech;
        if (disableEnrollmentCheck) {
            sLogger.v("Using URI host as ad tech's identifier.");
            adTech = AdTechIdentifier.fromString(uriForAdTech.getHost());
        } else {
            sLogger.v("Extracting ad tech's eTLD+1 identifier.");
            adTech =
                    getAndAssertAdTechFromUriAllowed(
                            callerPackageName, uriForAdTech, apiName, API_PROTECTED_SIGNALS);
        }

        sLogger.v("Validating caller package is in allow list.");
        assertAppInAllowList(callerPackageName, apiName, API_PROTECTED_SIGNALS);

        if (enforceConsent) {
            sLogger.v("Validating per-app user consent.");
            assertAndPersistCallerHasUserConsentForApp(callerPackageName);
        }

        return adTech;
    }
}
