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

import static android.adservices.common.AdServicesStatusUtils.RATE_LIMIT_REACHED_ERROR_MESSAGE;

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
import com.android.adservices.service.consent.AdServicesApiConsent;
import com.android.adservices.service.consent.AdServicesApiType;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.exception.FilterException;

import java.util.Objects;

/** Utility class to filter FLEDGE requests. */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
public abstract class AbstractFledgeServiceFilter {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    @NonNull private final Context mContext;
    @NonNull private final ConsentManager mConsentManager;
    @NonNull private final Flags mFlags;
    @NonNull private final AppImportanceFilter mAppImportanceFilter;
    @NonNull private final FledgeAuthorizationFilter mFledgeAuthorizationFilter;
    @NonNull private final FledgeAllowListsFilter mFledgeAllowListsFilter;
    @NonNull private final Throttler mThrottler;

    public AbstractFledgeServiceFilter(
            @NonNull Context context,
            @NonNull ConsentManager consentManager,
            @NonNull Flags flags,
            @NonNull AppImportanceFilter appImportanceFilter,
            @NonNull FledgeAuthorizationFilter fledgeAuthorizationFilter,
            @NonNull FledgeAllowListsFilter fledgeAllowListsFilter,
            @NonNull Throttler throttler) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(consentManager);
        Objects.requireNonNull(flags);
        Objects.requireNonNull(appImportanceFilter);
        Objects.requireNonNull(fledgeAuthorizationFilter);
        Objects.requireNonNull(fledgeAllowListsFilter);
        Objects.requireNonNull(throttler);

        mContext = context;
        mConsentManager = consentManager;
        mFlags = flags;
        mAppImportanceFilter = appImportanceFilter;
        mFledgeAuthorizationFilter = fledgeAuthorizationFilter;
        mFledgeAllowListsFilter = fledgeAllowListsFilter;
        mThrottler = throttler;
    }

    /**
     * Asserts that FLEDGE APIs and the Privacy Sandbox as a whole have user consent.
     *
     * @throws ConsentManager.RevokedConsentException if FLEDGE or the Privacy Sandbox do not have
     *     user consent
     */
    protected void assertCallerHasUserConsent() throws ConsentManager.RevokedConsentException {
        AdServicesApiConsent userConsent = mConsentManager.getConsent(AdServicesApiType.FLEDGE);

        if (!userConsent.isGiven()) {
            throw new ConsentManager.RevokedConsentException();
        }
    }

    /**
     * Asserts caller has user consent to use FLEDGE APIs in the calling app and persists consent
     * for.
     *
     * @throws ConsentManager.RevokedConsentException if FLEDGE or the Privacy Sandbox do not have
     *     user consent
     */
    protected void assertAndPersistCallerHasUserConsentForApp(String callerPackageName)
            throws ConsentManager.RevokedConsentException {
        if (mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(callerPackageName)) {
            throw new ConsentManager.RevokedConsentException();
        }
    }

    /**
     * Asserts that the caller has the appropriate foreground status.
     *
     * @throws AppImportanceFilter.WrongCallingApplicationStateException if the foreground check
     *     fails
     */
    protected void assertForegroundCaller(int callerUid, int apiName)
            throws AppImportanceFilter.WrongCallingApplicationStateException {
        mAppImportanceFilter.assertCallerIsInForeground(callerUid, apiName, null);
    }

    /**
     * Asserts that the package name provided by the caller is one of the packages of the calling
     * uid.
     *
     * @param callerPackageName caller package name from the request
     * @throws FledgeAuthorizationFilter.CallerMismatchException if the provided {@code
     *     callerPackageName} is not valid
     */
    protected void assertCallerPackageName(String callerPackageName, int callerUid, int apiName)
            throws FledgeAuthorizationFilter.CallerMismatchException {
        mFledgeAuthorizationFilter.assertCallingPackageName(callerPackageName, callerUid, apiName);
    }

    /**
     * Extract and return an {@link AdTechIdentifier} from the given {@link Uri} after checking if
     * the ad tech is enrolled and authorized to perform the operation for the package.
     *
     * @param uriForAdTech a {@link Uri} matching the ad tech to check against
     * @param callerPackageName the package name to check against
     * @param apiType The type of API calling: custom audience or protect signals
     * @throws FledgeAuthorizationFilter.AdTechNotAllowedException if the ad tech is not authorized
     *     to perform the operation
     */
    protected AdTechIdentifier getAndAssertAdTechFromUriAllowed(
            String callerPackageName,
            Uri uriForAdTech,
            int apiName,
            @AppManifestConfigCall.ApiType int apiType)
            throws FledgeAuthorizationFilter.AdTechNotAllowedException {
        return mFledgeAuthorizationFilter.getAndAssertAdTechFromUriAllowed(
                mContext, callerPackageName, uriForAdTech, apiName, apiType);
    }

    /**
     * Check if a certain ad tech is enrolled and authorized to perform the operation for the
     * package.
     *
     * @param adTech ad tech to check against
     * @param callerPackageName the package name to check against
     * @throws FledgeAuthorizationFilter.AdTechNotAllowedException if the ad tech is not authorized
     *     to perform the operation
     */
    protected void assertFledgeEnrollment(
            AdTechIdentifier adTech,
            String callerPackageName,
            int apiName,
            @NonNull DevContext devContext,
            @AppManifestConfigCall.ApiType int apiType)
            throws FledgeAuthorizationFilter.AdTechNotAllowedException {
        Uri adTechUri = Uri.parse("https://" + adTech.toString());
        boolean isLocalhostAddress =
                WebAddresses.isLocalhost(adTechUri) || WebAddresses.isLocalhostIp(adTechUri);
        boolean isDeveloperMode = devContext.getDevOptionsEnabled();
        if (isLocalhostAddress && isDeveloperMode) {
            // Skip check for localhost and 127.0.0.1 addresses for debuggable CTS.
            return;
        }

        if (!mFlags.getDisableFledgeEnrollmentCheck()) {
            mFledgeAuthorizationFilter.assertAdTechAllowed(
                    mContext, callerPackageName, adTech, apiName, apiType);
        }
    }

    /**
     * Asserts the package is allowed to call PPAPI.
     *
     * @param callerPackageName the package name to be validated.
     * @throws FledgeAllowListsFilter.AppNotAllowedException if the package is not authorized.
     */
    protected void assertAppInAllowList(
            String callerPackageName, int apiName, @AppManifestConfigCall.ApiType int apiType)
            throws FledgeAllowListsFilter.AppNotAllowedException {
        mFledgeAllowListsFilter.assertAppInAllowlist(callerPackageName, apiName, apiType);
    }

    /**
     * Ensures that the caller package is not throttled from calling current the API
     *
     * @param callerPackageName the package name, which should be verified
     * @throws LimitExceededException if the provided {@code callerPackageName} exceeds its rate
     *     limits
     */
    protected void assertCallerNotThrottled(final String callerPackageName, Throttler.ApiKey apiKey)
            throws LimitExceededException {
        sLogger.v("Checking if API is throttled for package: %s ", callerPackageName);
        boolean isThrottled = !mThrottler.tryAcquire(apiKey, callerPackageName);

        if (isThrottled) {
            sLogger.e(String.format("Rate Limit Reached for API: %s", apiKey));
            throw new LimitExceededException(RATE_LIMIT_REACHED_ERROR_MESSAGE);
        }
    }

    /**
     * Applies the filtering operations to the context of a FLEDGE request. The specific filtering
     * operations are discussed in the comments below.
     *
     * @param adTech the adTech associated with the request. This parameter is nullable, and the
     *     enrollment check will not be applied if it is null.
     * @param callerPackageName caller package name to be validated
     * @param enforceForeground whether to enforce a foreground check
     * @param enforceConsent whether to enforce a consent check
     * @throws FilterException if any filter assertion fails and wraps the exception thrown by the
     *     failing filter
     */
    public abstract void filterRequest(
            @Nullable AdTechIdentifier adTech,
            @NonNull String callerPackageName,
            boolean enforceForeground,
            boolean enforceConsent,
            int callerUid,
            int apiName,
            @NonNull Throttler.ApiKey apiKey,
            @NonNull DevContext devContext);
}
