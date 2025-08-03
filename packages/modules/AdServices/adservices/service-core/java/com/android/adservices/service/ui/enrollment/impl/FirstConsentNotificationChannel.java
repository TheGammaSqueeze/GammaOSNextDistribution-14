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

import android.content.Context;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.android.adservices.service.common.ConsentNotificationJobService;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.ui.data.UxStatesManager;
import com.android.adservices.service.ui.enrollment.base.PrivacySandboxEnrollmentChannel;
import com.android.adservices.service.ui.ux.collection.PrivacySandboxUxCollection;

/** Enroll through consent notification debug mode. */
@RequiresApi(Build.VERSION_CODES.S)
public class FirstConsentNotificationChannel implements PrivacySandboxEnrollmentChannel {

    /** Determines if user is eligible for the first consent enrollment channel. */
    public boolean isEligible(
            PrivacySandboxUxCollection uxCollection,
            ConsentManager consentManager,
            UxStatesManager uxStatesManager) {
        return !(consentManager.wasNotificationDisplayed()
                || consentManager.wasGaUxNotificationDisplayed()
                || consentManager.wasU18NotificationDisplayed());
    }

    /** Enroll the user through the first consent notification channel. */
    public void enroll(Context context, ConsentManager consentManager) {
        // Reconsent bit does not matter here.
        ConsentNotificationJobService.schedule(
                context,
                /* adidEnabled= */ consentManager.isAdIdEnabled(),
                /* reConsentStatus= */ false);
    }
}
