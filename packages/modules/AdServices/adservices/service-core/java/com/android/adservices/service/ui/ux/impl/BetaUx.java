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
package com.android.adservices.service.ui.ux.impl;

import static com.android.adservices.service.FlagsConstants.KEY_GA_UX_FEATURE_ENABLED;

import android.content.Context;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.ui.data.UxStatesManager;
import com.android.adservices.service.ui.enrollment.base.PrivacySandboxEnrollmentChannel;
import com.android.adservices.service.ui.ux.base.PrivacySandboxUx;

import com.google.errorprone.annotations.Immutable;

/** The privacy sandbox beta UX. */
@RequiresApi(Build.VERSION_CODES.S)
@Immutable
public class BetaUx implements PrivacySandboxUx {

    /** Whether a user is eligible for the privacy sandbox Beta UX. */
    public boolean isEligible(ConsentManager consentManager, UxStatesManager uxStatesManager) {
        return !uxStatesManager.getFlag(KEY_GA_UX_FEATURE_ENABLED)
                && consentManager.isAdultAccount();
    }

    /** Enroll user through one of the available Beta UX enrollment channels if needed. */
    public void handleEnrollment(
            PrivacySandboxEnrollmentChannel enrollmentChannel,
            Context context,
            ConsentManager consentManager) {
        enrollmentChannel.enroll(context, consentManager);
    }
}
