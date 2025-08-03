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

import android.annotation.NonNull;
import android.provider.DeviceConfig;

import com.android.modules.utils.build.SdkLevel;

import java.util.HashMap;
import java.util.Map;

/** Flags Implementation that delegates to DeviceConfig. */
public final class PhFlags implements Flags {
    /*
     * Keys for ALL the flags stored in DeviceConfig.
     */
    // Killswitch keys
    public static final String KEY_GLOBAL_KILL_SWITCH = "global_kill_switch";

    public static final String KEY_ENABLE_PERSONALIZATION_STATUS_OVERRIDE =
            "enable_personalization_status_override";

    public static final String KEY_PERSONALIZATION_STATUS_OVERRIDE_VALUE =
            "personalization_status_override_value";

    public static final String KEY_ISOLATED_SERVICE_DEADLINE_SECONDS =
            "isolated_service_deadline_seconds";

    public static final String KEY_APP_REQUEST_FLOW_DEADLINE_SECONDS =
            "app_request_flow_deadline_seconds";

    public static final String KEY_RENDER_FLOW_DEADLINE_SECONDS =
            "render_flow_deadline_seconds";

    public static final String KEY_WEB_VIEW_FLOW_DEADLINE_SECONDS =
            "web_view_flow_deadline_seconds";

    public static final String KEY_WEB_TRIGGER_FLOW_DEADLINE_SECONDS =
            "web_trigger_flow_deadline_seconds";

    public static final String KEY_TRUSTED_PARTNER_APPS_LIST = "trusted_partner_apps_list";

    public static final String KEY_SHARED_ISOLATED_PROCESS_FEATURE_ENABLED =
            "shared_isolated_process_feature_enabled";

    public static final String KEY_CALLER_APP_ALLOW_LIST = "caller_app_allow_list";

    public static final String KEY_ISOLATED_SERVICE_ALLOW_LIST = "isolated_service_allow_list";

    public static final String KEY_OUTPUT_DATA_ALLOW_LIST = "output_data_allow_list";

    public static final String KEY_USER_CONSENT_CACHE_IN_MILLIS =
            "user_consent_cache_duration_millis";

    public static final String KEY_ODP_ENABLE_CLIENT_ERROR_LOGGING =
            "odp_enable_client_error_logging";

    public static final String KEY_ODP_BACKGROUND_JOBS_LOGGING_ENABLED =
            "odp_background_jobs_logging_enabled";

    public static final String KEY_ODP_BACKGROUND_JOB_SAMPLING_LOGGING_RATE =
            "odp_background_job_sampling_logging_rate";

    public static final String KEY_IS_ART_IMAGE_LOADING_OPTIMIZATION_ENABLED =
            "is_art_image_loading_optimization_enabled";

    public static final String KEY_ISOLATED_SERVICE_DEBUGGING_ENABLED =
            "isolated_service_debugging_enabled";

    // OnDevicePersonalization Namespace String from DeviceConfig class
    public static final String NAMESPACE_ON_DEVICE_PERSONALIZATION = "on_device_personalization";

    private final Map<String, Object> mStableFlags = new HashMap<>();

    PhFlags() {
        // This is only called onece so stable flags require process restart to be reset.
        setStableFlags();
    }

    /** Returns the singleton instance of the PhFlags. */
    @NonNull
    public static PhFlags getInstance() {
        return PhFlagsLazyInstanceHolder.sSingleton;
    }

    private static class PhFlagsLazyInstanceHolder {
        private static final PhFlags sSingleton = new PhFlags();
    }

    /** Sets the stable flag map. */
    public void setStableFlags() {
        mStableFlags.put(KEY_APP_REQUEST_FLOW_DEADLINE_SECONDS,
                getAppRequestFlowDeadlineSeconds());
        mStableFlags.put(KEY_RENDER_FLOW_DEADLINE_SECONDS,
                getRenderFlowDeadlineSeconds());
        mStableFlags.put(KEY_WEB_TRIGGER_FLOW_DEADLINE_SECONDS,
                getWebTriggerFlowDeadlineSeconds());
        mStableFlags.put(KEY_WEB_VIEW_FLOW_DEADLINE_SECONDS,
                getWebViewFlowDeadlineSeconds());
        mStableFlags.put(KEY_EXAMPLE_STORE_FLOW_DEADLINE_SECONDS,
                getExampleStoreFlowDeadlineSeconds());
        mStableFlags.put(KEY_DOWNLOAD_FLOW_DEADLINE_SECONDS,
                getDownloadFlowDeadlineSeconds());
        mStableFlags.put(KEY_SHARED_ISOLATED_PROCESS_FEATURE_ENABLED,
                isSharedIsolatedProcessFeatureEnabled());
        mStableFlags.put(KEY_TRUSTED_PARTNER_APPS_LIST,
                getTrustedPartnerAppsList());
        mStableFlags.put(KEY_IS_ART_IMAGE_LOADING_OPTIMIZATION_ENABLED,
                isArtImageLoadingOptimizationEnabled());
    }

    /** Gets a stable flag value based on flag name. */
    public Object getStableFlag(String flagName) {
        if (!mStableFlags.containsKey(flagName)) {
            throw new IllegalArgumentException("Flag " + flagName + " is not stable.");
        }
        return mStableFlags.get(flagName);
    }

    // Group of All Killswitches
    @Override
    public boolean getGlobalKillSwitch() {
        // The priority of applying the flag values: PH (DeviceConfig), then hard-coded value.
        return DeviceConfig.getBoolean(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ KEY_GLOBAL_KILL_SWITCH,
                /* defaultValue= */ GLOBAL_KILL_SWITCH);
    }

    @Override
    public boolean isPersonalizationStatusOverrideEnabled() {
        if (getGlobalKillSwitch()) {
            return false;
        }
        // The priority of applying the flag values: PH (DeviceConfig), then user hard-coded value.
        return DeviceConfig.getBoolean(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ KEY_ENABLE_PERSONALIZATION_STATUS_OVERRIDE,
                /* defaultValue= */ ENABLE_PERSONALIZATION_STATUS_OVERRIDE);
    }

    @Override
    public boolean getPersonalizationStatusOverrideValue() {
        if (getGlobalKillSwitch()) {
            return false;
        }
        return DeviceConfig.getBoolean(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ KEY_PERSONALIZATION_STATUS_OVERRIDE_VALUE,
                /* defaultValue= */ PERSONALIZATION_STATUS_OVERRIDE_VALUE);
    }

    @Override
    public int getIsolatedServiceDeadlineSeconds() {
        return DeviceConfig.getInt(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ KEY_ISOLATED_SERVICE_DEADLINE_SECONDS,
                /* defaultValue= */ ISOLATED_SERVICE_DEADLINE_SECONDS);
    }

    @Override
    public int getAppRequestFlowDeadlineSeconds() {
        return DeviceConfig.getInt(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ KEY_APP_REQUEST_FLOW_DEADLINE_SECONDS,
                /* defaultValue= */ APP_REQUEST_FLOW_DEADLINE_SECONDS);
    }

    @Override
    public int getRenderFlowDeadlineSeconds() {
        return DeviceConfig.getInt(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ KEY_RENDER_FLOW_DEADLINE_SECONDS,
                /* defaultValue= */ RENDER_FLOW_DEADLINE_SECONDS);
    }

    @Override
    public int getWebViewFlowDeadlineSeconds() {
        return DeviceConfig.getInt(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ KEY_WEB_VIEW_FLOW_DEADLINE_SECONDS,
                /* defaultValue= */ WEB_VIEW_FLOW_DEADLINE_SECONDS);
    }

    @Override
    public int getWebTriggerFlowDeadlineSeconds() {
        return DeviceConfig.getInt(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ KEY_WEB_TRIGGER_FLOW_DEADLINE_SECONDS,
                /* defaultValue= */ WEB_TRIGGER_FLOW_DEADLINE_SECONDS);
    }

    public static final String KEY_EXAMPLE_STORE_FLOW_DEADLINE_SECONDS =
            "example_store_flow_deadline_seconds";

    @Override
    public int getExampleStoreFlowDeadlineSeconds() {
        return DeviceConfig.getInt(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ KEY_EXAMPLE_STORE_FLOW_DEADLINE_SECONDS,
                /* defaultValue= */ EXAMPLE_STORE_FLOW_DEADLINE_SECONDS);
    }

    public static final String KEY_DOWNLOAD_FLOW_DEADLINE_SECONDS =
            "download_flow_deadline_seconds";

    @Override
    public int getDownloadFlowDeadlineSeconds() {
        return DeviceConfig.getInt(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ KEY_DOWNLOAD_FLOW_DEADLINE_SECONDS,
                /* defaultValue= */ DOWNLOAD_FLOW_DEADLINE_SECONDS);
    }

    @Override
    public String getTrustedPartnerAppsList() {
        return SdkLevel.isAtLeastU()
                ? DeviceConfig.getString(
                    /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                    /* name= */ KEY_TRUSTED_PARTNER_APPS_LIST,
                    /* defaultValue */ DEFAULT_TRUSTED_PARTNER_APPS_LIST)
                : "";
    }

    @Override
    public boolean isSharedIsolatedProcessFeatureEnabled() {
        return SdkLevel.isAtLeastU() && DeviceConfig.getBoolean(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ KEY_SHARED_ISOLATED_PROCESS_FEATURE_ENABLED,
                /* defaultValue= */ DEFAULT_SHARED_ISOLATED_PROCESS_FEATURE_ENABLED);
    }

    @Override
    public boolean isIsolatedServiceDebuggingEnabled() {
        return DeviceConfig.getBoolean(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ KEY_ISOLATED_SERVICE_DEBUGGING_ENABLED,
                /* defaultValue= */ DEFAULT_ISOLATED_SERVICE_DEBUGGING_ENABLED);
    }

    @Override
    public String getCallerAppAllowList() {
        return DeviceConfig.getString(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ KEY_CALLER_APP_ALLOW_LIST,
                /* defaultValue= */ DEFAULT_CALLER_APP_ALLOW_LIST);
    }

    @Override
    public String getIsolatedServiceAllowList() {
        return DeviceConfig.getString(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ KEY_ISOLATED_SERVICE_ALLOW_LIST,
                /* defaultValue= */ DEFAULT_ISOLATED_SERVICE_ALLOW_LIST);
    }

    @Override
    public String getOutputDataAllowList() {
        return DeviceConfig.getString(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name */ KEY_OUTPUT_DATA_ALLOW_LIST,
                /* defaultValue */ DEFAULT_OUTPUT_DATA_ALLOW_LIST);
    }

    @Override
    public long getUserConsentCacheInMillis() {
        return DeviceConfig.getLong(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ KEY_USER_CONSENT_CACHE_IN_MILLIS,
                /* defaultValue= */ USER_CONSENT_CACHE_IN_MILLIS);
    }

    @Override
    public boolean getEnableClientErrorLogging() {
        return DeviceConfig.getBoolean(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ KEY_ODP_ENABLE_CLIENT_ERROR_LOGGING,
                /* defaultValue= */ DEFAULT_CLIENT_ERROR_LOGGING_ENABLED);
    }

    @Override
    public boolean getBackgroundJobsLoggingEnabled() {
        // needs stable: execution stats may be less accurate if flag changed during job execution
        return (boolean) mStableFlags.computeIfAbsent(KEY_ODP_BACKGROUND_JOBS_LOGGING_ENABLED,
                key -> {
                    return DeviceConfig.getBoolean(
                            /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                            /* name= */ KEY_ODP_BACKGROUND_JOBS_LOGGING_ENABLED,
                            /* defaultValue= */ DEFAULT_BACKGROUND_JOBS_LOGGING_ENABLED);
                });
    }

    @Override
    public int getBackgroundJobSamplingLoggingRate() {
        return DeviceConfig.getInt(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ KEY_ODP_BACKGROUND_JOB_SAMPLING_LOGGING_RATE,
                /* defaultValue= */ DEFAULT_BACKGROUND_JOB_SAMPLING_LOGGING_RATE);
    }

    @Override
    public boolean isArtImageLoadingOptimizationEnabled() {
        return DeviceConfig.getBoolean(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ KEY_IS_ART_IMAGE_LOADING_OPTIMIZATION_ENABLED,
                /* defaultValue= */ IS_ART_IMAGE_LOADING_OPTIMIZATION_ENABLED);
    }
}
