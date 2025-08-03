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

import static com.android.adservices.service.consent.ConsentManager.NO_MANUAL_INTERACTIONS_RECORDED;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.text.TextUtils;

import androidx.annotation.RequiresApi;

import com.android.adservices.LogUtil;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.ui.data.UxStatesManager;
import com.android.adservices.service.ui.enrollment.base.PrivacySandboxEnrollmentChannel;
import com.android.adservices.service.ui.ux.collection.PrivacySandboxUxCollection;
import com.android.modules.utils.build.SdkLevel;

import java.util.Objects;

/**
 * Enrollment channel for resetting consent notification info, similar to the consent notification
 * debug channel, this channel is only used for testing. Unlike the debug channel, this channel
 * resets the consent data exacty once per token.
 */
@RequiresApi(Build.VERSION_CODES.S)
public class ConsentNotificationResetChannel implements PrivacySandboxEnrollmentChannel {

    public static final String CONSENT_NOTIFICATION_RESET_TOKEN =
            "CONSENT_NOTIFICATION_RESET_TOKEN";

    /** Determines if user is eligible for the consent notification reset channel */
    public boolean isEligible(
            PrivacySandboxUxCollection uxCollection,
            ConsentManager consentManager,
            UxStatesManager uxStatesManager) {
        String currentConsentNotificationResetToken =
                FlagsFactory.getFlags().getConsentNotificationResetToken();
        // Explicitly ignore default token and to avoid double-reset.
        if (TextUtils.isEmpty(currentConsentNotificationResetToken)) {
            return false;
        }

        SharedPreferences uxSharedPreferences = uxStatesManager.getUxSharedPreferences();
        String storedConsentNotificationResetToken = uxSharedPreferences.getString(
                CONSENT_NOTIFICATION_RESET_TOKEN, /* defValue= */ "");

        if (!Objects.equals(storedConsentNotificationResetToken,
                currentConsentNotificationResetToken)) {
            SharedPreferences.Editor editor = uxSharedPreferences.edit();
            editor.putString(CONSENT_NOTIFICATION_RESET_TOKEN,
                    currentConsentNotificationResetToken);
            // Consent notification reset can proceed only if the write operation succeeded.
            return editor.commit();
        }
        return false;
    }

    /** Perform enrollment logic for the reset channel. */
    public void enroll(Context context, ConsentManager consentManager) {
        consentManager.recordUserManualInteractionWithConsent(NO_MANUAL_INTERACTIONS_RECORDED);
        if (SdkLevel.isAtLeastS()) {
            consentManager.recordNotificationDisplayed(false);
            consentManager.recordGaUxNotificationDisplayed(false);
        }
        if (SdkLevel.isAtLeastT()) {
            consentManager.recordPasNotificationDisplayed(false);
        }

        consentManager.setU18NotificationDisplayed(false);
        consentManager.setU18Account(false);
        LogUtil.d("Consent data has been reset.");
    }
}
