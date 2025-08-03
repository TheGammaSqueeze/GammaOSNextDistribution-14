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

package com.android.adservices.service.consent;

import com.android.adservices.service.common.compat.FileCompatUtils;

/** ConsentManager related Constants. */
public class ConsentConstants {

    public static final String NOTIFICATION_DISPLAYED_ONCE = "NOTIFICATION-DISPLAYED-ONCE";

    public static final String GA_UX_NOTIFICATION_DISPLAYED_ONCE =
            "GA-UX-NOTIFICATION-DISPLAYED-ONCE";

    public static final String DEFAULT_CONSENT = "DEFAULT_CONSENT";

    public static final String TOPICS_DEFAULT_CONSENT = "TOPICS_DEFAULT_CONSENT";

    public static final String FLEDGE_DEFAULT_CONSENT = "FLEDGE_DEFAULT_CONSENT";

    public static final String MEASUREMENT_DEFAULT_CONSENT = "MEASUREMENT_DEFAULT_CONSENT";

    public static final String DEFAULT_AD_ID_STATE = "DEFAULT_AD_ID_STATE";

    public static final String MANUAL_INTERACTION_WITH_CONSENT_RECORDED =
            "MANUAL_INTERACTION_WITH_CONSENT_RECORDED";

    public static final String CONSENT_KEY = "CONSENT";

    // When persisting data to AppSearch, the key cannot be a proper subset of other keys since
    // Search does not support full match.
    public static final String CONSENT_KEY_FOR_ALL = "CONSENT-ALL";

    // Internal datastore version
    static final int STORAGE_VERSION = 1;

    // Internal datastore filename. The name should be unique to avoid multiple threads or
    // processes to update the same file.
    static final String STORAGE_XML_IDENTIFIER =
            FileCompatUtils.getAdservicesFilename("ConsentManagerStorageIdentifier.xml");

    // The name of shared preferences file to store status of one-time migrations.
    // Once a migration has happened, it marks corresponding shared preferences to prevent it
    // happens again.
    static final String SHARED_PREFS_CONSENT = "PPAPI_Consent";

    public static final String SHARED_PREFS_KEY_HAS_MIGRATED_TO_APP_SEARCH =
            "CONSENT_HAS_MIGRATED_TO_APP_SEARCH";

    // Shared preferences to mark whether consent data from AppSearch has migrated to AdServices.
    public static final String SHARED_PREFS_KEY_APPSEARCH_HAS_MIGRATED =
            "CONSENT_HAS_MIGRATED_FROM_APPSEARCH";

    // Shared preferences to mark whether PPAPI consent has been migrated to system server
    public static final String SHARED_PREFS_KEY_HAS_MIGRATED =
            "CONSENT_HAS_MIGRATED_TO_SYSTEM_SERVER";

    // Shared preferences to mark whether AdExtData has been migrated to system server
    public static final String SHARED_PREFS_KEY_MIGRATED_FROM_ADEXTDATA_TO_SYSTEM_SERVER =
            "CONSENT_HAS_MIGRATED_FROM_ADEXTDATA_TO_SYSTEM_SERVER";

    // Shared preferences to mark whether PPAPI consent has been cleared.
    static final String SHARED_PREFS_KEY_PPAPI_HAS_CLEARED = "CONSENT_HAS_CLEARED_IN_PPAPI";

    static final String ERROR_MESSAGE_WHILE_GET_CONTENT =
            "getConsent method failed. Revoked consent is returned as fallback.";

    static final String ERROR_MESSAGE_WHILE_SET_CONTENT = "setConsent method failed.";

    static final String ERROR_MESSAGE_INVALID_CONSENT_SOURCE_OF_TRUTH =
            "Invalid type of consent source of truth.";

    public static final String ERROR_MESSAGE_INVALID_BLOCKED_TOPICS_SOURCE_OF_TRUTH =
            "Invalid type of blocked topics source of truth.";

    public static final String ERROR_MESSAGE_APPSEARCH_FAILURE =
            "Failed to persist data to AppSearch.";

    public static final String IS_AD_ID_ENABLED = "IS_AD_ID_ENABLED";

    public static final String IS_U18_ACCOUNT = "IS_U18_ACCOUNT";

    public static final String IS_ENTRY_POINT_ENABLED = "IS_ENTRY_POINT_ENABLED";

    public static final String IS_ADULT_ACCOUNT = "IS_ADULT_ACCOUNT";

    public static final String WAS_U18_NOTIFICATION_DISPLAYED = "WAS_U18_NOTIFICATION_DISPLAYED";

    public static final String PAS_NOTIFICATION_DISPLAYED_ONCE = "PAS_NOTIFICATION_DISPLAYED_ONCE";
}
