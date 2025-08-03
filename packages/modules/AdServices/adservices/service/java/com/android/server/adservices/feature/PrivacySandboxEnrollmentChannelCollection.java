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

package com.android.server.adservices.feature;

/**
 * Privacy sandbox enrollment channels.
 *
 * @hide
 */
public enum PrivacySandboxEnrollmentChannelCollection {
    CONSENT_NOTIFICATION_DEBUG_CHANNEL,

    ALREADY_ENROLLED_CHANNEL,

    FIRST_CONSENT_NOTIFICATION_CHANNEL,

    RECONSENT_NOTIFICATION_CHANNEL,

    GA_GRADUATION_CHANNEL,

    U18_DETENTION_CHANNEL,
}
