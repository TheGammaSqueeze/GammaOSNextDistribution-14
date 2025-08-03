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

package com.android.adservices.service.ui.enrollment.impl;

import static com.android.adservices.service.FlagsConstants.KEY_RVC_POST_OTA_NOTIFICATION_ENABLED;

import android.content.Context;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.android.adservices.service.common.ConsentNotificationJobService;
import com.android.adservices.service.consent.AdServicesApiType;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.ui.data.UxStatesManager;
import com.android.adservices.service.ui.enrollment.base.PrivacySandboxEnrollmentChannel;
import com.android.adservices.service.ui.ux.collection.PrivacySandboxUxCollection;

/** Enroll through 2nd GA notification post R OTA channel. */
@RequiresApi(Build.VERSION_CODES.S)
public class RvcPostOTAChannel implements PrivacySandboxEnrollmentChannel {

    /** Checks if user is eligible for the 2nd GA notification post R OTA channel. */
    public boolean isEligible(
            PrivacySandboxUxCollection uxCollection,
            ConsentManager consentManager,
            UxStatesManager uxStatesManager) {
        // Rvc user should be matched to RvcPostOTAChannel on S+
        return uxStatesManager.getFlag(KEY_RVC_POST_OTA_NOTIFICATION_ENABLED)
                && consentManager.isOtaAdultUserFromRvc();
    }

    /** Enroll users with GA notification. */
    public void enroll(Context context, ConsentManager consentManager) {
        // Only rvc user who opted in msmt API is eligible for enrollment
        // Reconsent bit does not matter here.
        if (consentManager.getConsent(AdServicesApiType.MEASUREMENTS).isGiven()) {
            ConsentNotificationJobService.schedule(
                    context,
                    /* adidEnabled= */ consentManager.isAdIdEnabled(),
                    /* reConsentStatus= */ false);
        }
    }
}
