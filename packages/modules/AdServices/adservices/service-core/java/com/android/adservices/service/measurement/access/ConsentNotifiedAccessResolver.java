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

package com.android.adservices.service.measurement.access;

import static android.adservices.common.AdServicesStatusUtils.FAILURE_REASON_UNSET;

import android.adservices.common.AdServicesStatusUtils;
import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;

import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.measurement.CachedFlags;

/**
 * API access gate on whether consent notification was displayed. {@link #getAccessInfo(Context)}
 * will return true if the consent notification was displayed, false otherwise. {@link
 * UserConsentAccessResolver} should be applied after this to get the true user consent value.
 */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
public class ConsentNotifiedAccessResolver implements IAccessResolver {
    private static final String ERROR_MESSAGE = "Consent notification has not been displayed.";
    private final ConsentManager mConsentManager;
    private final CachedFlags mFlags;
    @NonNull private final UserConsentAccessResolver mUserConsentAccessResolver;

    public ConsentNotifiedAccessResolver(
            @NonNull ConsentManager consentManager, @NonNull CachedFlags flags) {
        this(consentManager, flags, new UserConsentAccessResolver(consentManager));
    }

    @VisibleForTesting
    public ConsentNotifiedAccessResolver(
            @NonNull ConsentManager consentManager,
            @NonNull CachedFlags flags,
            @NonNull UserConsentAccessResolver userConsentAccessResolver) {
        mConsentManager = consentManager;
        mFlags = flags;
        mUserConsentAccessResolver = userConsentAccessResolver;
    }

    @Override
    public AccessInfo getAccessInfo(@NonNull Context context) {
        if (mFlags.getConsentNotifiedDebugMode()) {
            return new AccessInfo(true, FAILURE_REASON_UNSET);
        }

        // If the user has already consented, don't check whether the notification was shown
        if (mUserConsentAccessResolver.getAccessInfo(context).isAllowedAccess()) {
            return new AccessInfo(true, FAILURE_REASON_UNSET);
        }

        boolean wasDisplayed =
                mConsentManager.wasNotificationDisplayed()
                        || mConsentManager.wasGaUxNotificationDisplayed()
                        || mConsentManager.wasU18NotificationDisplayed();
        return new AccessInfo(wasDisplayed, FAILURE_REASON_UNSET);
    }

    @Override
    public int getErrorStatusCode() {
        return AdServicesStatusUtils.STATUS_USER_CONSENT_NOTIFICATION_NOT_DISPLAYED_YET;
    }

    @NonNull
    @Override
    public String getErrorMessage() {
        return ERROR_MESSAGE;
    }
}
