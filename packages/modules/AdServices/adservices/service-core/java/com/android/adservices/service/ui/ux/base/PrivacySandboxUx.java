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
package com.android.adservices.service.ui.ux.base;

import android.content.Context;

import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.ui.data.UxStatesManager;
import com.android.adservices.service.ui.enrollment.base.PrivacySandboxEnrollmentChannel;

import com.google.errorprone.annotations.Immutable;

/** Base UX for all privacy sandbox UXs. */
@Immutable
public interface PrivacySandboxUx {

    /** Whether a user is eligible for a particular privacy sandbox UX. */
    boolean isEligible(ConsentManager consentManager, UxStatesManager uxStatesManager);

    /** Enroll a user through the selected the enrollment channel. */
    void handleEnrollment(
            PrivacySandboxEnrollmentChannel enrollmentChannel,
            Context context,
            ConsentManager consentManager);
}
