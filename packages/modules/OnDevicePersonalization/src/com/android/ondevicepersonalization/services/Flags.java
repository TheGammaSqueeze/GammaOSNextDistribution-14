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

package com.android.ondevicepersonalization.services;

import com.android.adservices.shared.common.flags.ModuleSharedFlags;

/**
 * OnDevicePersonalization Feature Flags interface. This Flags interface hold the default values
 * of flags. The default values in this class must match with the default values in PH since we
 * will migrate to Flag Codegen in the future. With that migration, the Flags.java file will be
 * generated from the GCL.
 */
public interface Flags extends ModuleSharedFlags {
    /**
     * Global OnDevicePersonalization Kill Switch. This overrides all other killswitches.
     * The default value is true which means OnDevicePersonalization is disabled.
     * This flag is used for ramp-up and emergency turning off the whole module.
     */
    boolean GLOBAL_KILL_SWITCH = true;

    /**
     * P/H flag to override the personalization status for end-to-end tests.
     * The default value is false, which means UserPrivacyStatus#personalizationStatus is not
     * override by PERSONALIZATION_STATUS_OVERRIDE_VALUE. If true, returns the personalization
     * status in PERSONALIZATION_STATUS_OVERRIDE_VALUE.
     */
    boolean ENABLE_PERSONALIZATION_STATUS_OVERRIDE = false;

    /**
     * Value of the personalization status, if ENABLE_PERSONALIZATION_STATUS_OVERRIDE is true.
     */
    boolean PERSONALIZATION_STATUS_OVERRIDE_VALUE = false;

    /**
     * Deadline for calls from ODP to isolated services.
     */
    int ISOLATED_SERVICE_DEADLINE_SECONDS = 30;

    /**
     * Execution deadline for app request flow.
     */
    int APP_REQUEST_FLOW_DEADLINE_SECONDS = 30;

    /**
     * Executiton deadline for render flow.
     */
    int RENDER_FLOW_DEADLINE_SECONDS = 30;

    /**
     * Executiton deadline for web view flow.
     */
    int WEB_VIEW_FLOW_DEADLINE_SECONDS = 30;

    /**
     * Executiton deadline for web trigger flow.
     */
    int WEB_TRIGGER_FLOW_DEADLINE_SECONDS = 30;


    /**
     * Default value for the list of trusted partner app names.
     */
    String DEFAULT_TRUSTED_PARTNER_APPS_LIST = "";

    /**
     * Default value for the shared isolated process feature.
     */
    boolean DEFAULT_SHARED_ISOLATED_PROCESS_FEATURE_ENABLED = true;

    /**
     * Default value for enabling client error logging.
     */
    boolean DEFAULT_CLIENT_ERROR_LOGGING_ENABLED = false;

    /**
     * Default value for enabling background jobs logging.
     */
    boolean DEFAULT_BACKGROUND_JOBS_LOGGING_ENABLED = false;

    /**
     * Default value for background job sampling logging rate.
     */
    int DEFAULT_BACKGROUND_JOB_SAMPLING_LOGGING_RATE = 5;

    /**
     * Default value for isolated service debugging flag.
     */
    boolean DEFAULT_ISOLATED_SERVICE_DEBUGGING_ENABLED = false;

    String DEFAULT_CALLER_APP_ALLOW_LIST =
            "android.ondevicepersonalization,"
                    + "android.ondevicepersonalization.test.scenario,"
                    + "com.android.federatedcompute.services,"
                    + "com.android.libraries.pcc.chronicle.test,"
                    + "com.android.ondevicepersonalization,"
                    + "com.android.ondevicepersonalization.cts.e2e,"
                    + "com.android.ondevicepersonalization.federatedcomputetests,"
                    + "com.android.ondevicepersonalization.libraries.plugin,"
                    + "com.android.ondevicepersonalization.manualtests,"
                    + "com.android.ondevicepersonalization.plugintests,"
                    + "com.android.ondevicepersonalization.services,"
                    + "com.android.ondevicepersonalization.servicetests,"
                    + "com.android.ondevicepersonalization.systemserviceapitests,"
                    + "com.android.ondevicepersonalization.systemserviceimpltests,"
                    + "com.android.ondevicepersonalization.testing.sampleservice,"
                    + "com.example.odpclient,"
                    + "com.example.odpsamplenetwork,"
                    + "com.example.odptargetingapp1,"
                    + "com.example.odptargetingapp2";

    String DEFAULT_ISOLATED_SERVICE_ALLOW_LIST =
            "android.ondevicepersonalization,"
                    + "android.ondevicepersonalization.test.scenario,"
                    + "com.android.federatedcompute.services,"
                    + "com.android.libraries.pcc.chronicle.test,"
                    + "com.android.ondevicepersonalization,"
                    + "com.android.ondevicepersonalization.cts.e2e,"
                    + "com.android.ondevicepersonalization.federatedcomputetests,"
                    + "com.android.ondevicepersonalization.libraries.plugin,"
                    + "com.android.ondevicepersonalization.manualtests,"
                    + "com.android.ondevicepersonalization.plugintests,"
                    + "com.android.ondevicepersonalization.services,"
                    + "com.android.ondevicepersonalization.servicetests,"
                    + "com.android.ondevicepersonalization.systemserviceapitests,"
                    + "com.android.ondevicepersonalization.systemserviceimpltests,"
                    + "com.android.ondevicepersonalization.testing.sampleservice,"
                    + "com.example.odpclient,"
                    + "com.example.odpsamplenetwork,"
                    + "com.example.odptargetingapp1,"
                    + "com.example.odptargetingapp2";

    String DEFAULT_OUTPUT_DATA_ALLOW_LIST = "";

    /**
     * Default value of valid duration of user consent cache in milliseconds (10 minutes).
     */
    long USER_CONSENT_CACHE_IN_MILLIS = 600000;

    default boolean getGlobalKillSwitch() {
        return GLOBAL_KILL_SWITCH;
    }

    default boolean isPersonalizationStatusOverrideEnabled() {
        return ENABLE_PERSONALIZATION_STATUS_OVERRIDE;
    }

    default boolean getPersonalizationStatusOverrideValue() {
        return PERSONALIZATION_STATUS_OVERRIDE_VALUE;
    }

    default int getIsolatedServiceDeadlineSeconds() {
        return ISOLATED_SERVICE_DEADLINE_SECONDS;
    }

    default int getAppRequestFlowDeadlineSeconds() {
        return APP_REQUEST_FLOW_DEADLINE_SECONDS;
    }

    default int getRenderFlowDeadlineSeconds() {
        return RENDER_FLOW_DEADLINE_SECONDS;
    }

    default int getWebViewFlowDeadlineSeconds() {
        return WEB_VIEW_FLOW_DEADLINE_SECONDS;
    }

    default int getWebTriggerFlowDeadlineSeconds() {
        return WEB_TRIGGER_FLOW_DEADLINE_SECONDS;
    }

    /**
     * Executiton deadline for example store flow.
     */
    int EXAMPLE_STORE_FLOW_DEADLINE_SECONDS = 30;

    default int getExampleStoreFlowDeadlineSeconds() {
        return EXAMPLE_STORE_FLOW_DEADLINE_SECONDS;
    }

    /**
     * Executiton deadline for download flow.
     */
    int DOWNLOAD_FLOW_DEADLINE_SECONDS = 30;

    default int getDownloadFlowDeadlineSeconds() {
        return DOWNLOAD_FLOW_DEADLINE_SECONDS;
    }

    default String getTrustedPartnerAppsList() {
        return DEFAULT_TRUSTED_PARTNER_APPS_LIST;
    }

    default boolean isSharedIsolatedProcessFeatureEnabled() {
        return DEFAULT_SHARED_ISOLATED_PROCESS_FEATURE_ENABLED;
    }

    /**
     * The ART image loading optimization is disabled by default.
     */
    boolean IS_ART_IMAGE_LOADING_OPTIMIZATION_ENABLED = false;

    default boolean isArtImageLoadingOptimizationEnabled() {
        return IS_ART_IMAGE_LOADING_OPTIMIZATION_ENABLED;
    }

    default String getCallerAppAllowList() {
        return DEFAULT_CALLER_APP_ALLOW_LIST;
    }

    default String getIsolatedServiceAllowList() {
        return DEFAULT_ISOLATED_SERVICE_ALLOW_LIST;
    }

    default long getUserConsentCacheInMillis() {
        return USER_CONSENT_CACHE_IN_MILLIS;
    }

    default String getOutputDataAllowList() {
        return DEFAULT_OUTPUT_DATA_ALLOW_LIST;
    }

    default boolean isIsolatedServiceDebuggingEnabled() {
        return DEFAULT_ISOLATED_SERVICE_DEBUGGING_ENABLED;
    }

    /** Set all stable flags. */
    default void setStableFlags() {}

    /** Get a stable flag based on the flag name. */
    default Object getStableFlag(String flagName) {
        return null;
    }

    default boolean getEnableClientErrorLogging() {
        return DEFAULT_CLIENT_ERROR_LOGGING_ENABLED;
    }
}
