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
package com.android.adservices.service.ui.enrollment.base;

import android.content.Context;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.ui.data.UxStatesManager;
import com.android.adservices.service.ui.ux.collection.PrivacySandboxUxCollection;

import com.google.errorprone.annotations.Immutable;

/** Base enrollment channel for all privacy sandbox UXs. */
@RequiresApi(Build.VERSION_CODES.S)
@Immutable
public interface PrivacySandboxEnrollmentChannel {

    /** Is a user eligible for a particular UX enrollment channel. */
    boolean isEligible(
            PrivacySandboxUxCollection ux,
            ConsentManager consentManager,
            UxStatesManager uxStatesManager);

    /** Enroll a user through the notification channel. */
    void enroll(Context context, ConsentManager consentManager);
}
