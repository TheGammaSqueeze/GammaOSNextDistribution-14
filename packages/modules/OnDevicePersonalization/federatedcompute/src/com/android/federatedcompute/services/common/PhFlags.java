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

package com.android.federatedcompute.services.common;

import android.annotation.NonNull;
import android.os.SystemProperties;
import android.provider.DeviceConfig;

import com.android.internal.annotations.VisibleForTesting;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** A placeholder class for PhFlag. */
public final class PhFlags implements Flags {
    /*
     * Keys for ALL the flags stored in DeviceConfig.
     */
    // Killswitch keys
    static final String KEY_FEDERATED_COMPUTE_KILL_SWITCH = "federated_compute_kill_switch";

    // SystemProperty prefix. SystemProperty is for overriding OnDevicePersonalization Configs.
    private static final String SYSTEM_PROPERTY_PREFIX = "debug.ondevicepersonalization.";

    // OnDevicePersonalization Namespace String from DeviceConfig class
    static final String NAMESPACE_ON_DEVICE_PERSONALIZATION = "on_device_personalization";

    static final String FEDERATED_COMPUTATION_ENCRYPTION_KEY_DOWNLOAD_URL =
            "fcp_encryption_key_download_url";

    static final String ENABLE_BACKGROUND_ENCRYPTION_KEY_FETCH =
            "enable_background_encryption_key_fetch";

    static final String HTTP_REQUEST_RETRY_LIMIT_CONFIG_NAME = "http_request_retry_limit";

    static final String FCP_ENABLE_ENCRYPTION = "fcp_enable_encryption";

    static final String MIN_SCHEDULING_INTERVAL_SECS_FOR_FEDERATED_COMPUTATION_CONFIG_NAME =
            "min_scheduling_interval_secs_for_federated_computation";

    static final String MAX_SCHEDULING_INTERVAL_SECS_FOR_FEDERATED_COMPUTATION_CONFIG_NAME =
            "max_scheduling_interval_secs_for_federated_computation";

    static final String DEFAULT_SCHEDULING_PERIOD_SECS_CONFIG_NAME =
            "default_scheduling_period_secs";

    static final String MAX_SCHEDULING_PERIOD_SECS_CONFIG_NAME = "max_scheduling_period_secs";

    static final String TRANSIENT_ERROR_RETRY_DELAY_JITTER_PERCENT_CONFIG_NAME =
            "transient_error_retry_delay_jitter_percent";

    static final String TRANSIENT_ERROR_RETRY_DELAY_SECS_CONFIG_NAME =
            "transient_error_retry_delay_secs";
    static final String TRAINING_MIN_BATTERY_LEVEL = "training_min_battery_level";
    static final String TRAINING_THERMAL_STATUS_TO_THROTTLE = "training_thermal_to_throttle";
    static final String ENABLE_ELIGIBILITY_TASK = "enable_eligibility_task";
    static final String TRAINING_CONDITION_CHECK_THROTTLE_PERIOD_MILLIS =
            "training_condition_check_period_throttle_period_mills";
    static final String TASK_HISTORY_TTL_MILLIS = "task_history_ttl_millis";

    static final String FCP_RESCHEDULE_LIMIT_CONFIG_NAME = "reschedule_limit";
    static final String FCP_ENABLE_CLIENT_ERROR_LOGGING = "fcp_enable_client_error_logging";
    static final String FCP_ENABLE_BACKGROUND_JOBS_LOGGING = "fcp_enable_background_jobs_logging";
    static final String FCP_BACKGROUND_JOB_LOGGING_SAMPLING_RATE =
            "fcp_background_job_logging_sampling_rate";

    private static final PhFlags sSingleton = new PhFlags();

    // Flag values here remain stable within a process lifecycle, refresh upon process restart
    private static final Map<String, Object> sStableFlags = new ConcurrentHashMap<>();

    private PhFlags() {
        setStableFlags();
    }

    // Set group of flags that needs to remain stable together at beginning of a workflow
    // You can also set one stable flag value at the flag's read time if don't need this guarantee
    private void setStableFlags() {}

    /** Returns the singleton instance of the PhFlags. */
    @NonNull
    public static PhFlags getInstance() {
        return sSingleton;
    }

    // Group of All Killswitches
    @Override
    public boolean getGlobalKillSwitch() {
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig),
        // then hard-coded value.
        return SystemProperties.getBoolean(
                getSystemPropertyName(KEY_FEDERATED_COMPUTE_KILL_SWITCH),
                DeviceConfig.getBoolean(
                        /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                        /* name= */ KEY_FEDERATED_COMPUTE_KILL_SWITCH,
                        /* defaultValue= */ FEDERATED_COMPUTE_GLOBAL_KILL_SWITCH));
    }

    @VisibleForTesting
    static String getSystemPropertyName(String key) {
        return SYSTEM_PROPERTY_PREFIX + key;
    }

    @Override
    public String getEncryptionKeyFetchUrl() {
        return DeviceConfig.getString(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ FEDERATED_COMPUTATION_ENCRYPTION_KEY_DOWNLOAD_URL,
                /* defaultValue= */ ENCRYPTION_KEY_FETCH_URL);
    }

    @Override
    public Boolean getEnableBackgroundEncryptionKeyFetch() {
        return DeviceConfig.getBoolean(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ ENABLE_BACKGROUND_ENCRYPTION_KEY_FETCH,
                /* defaultValue= */ USE_BACKGROUND_ENCRYPTION_KEY_FETCH);
    }

    @Override
    public int getHttpRequestRetryLimit() {
        return DeviceConfig.getInt(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ HTTP_REQUEST_RETRY_LIMIT_CONFIG_NAME,
                /* defaultValue= */ HTTP_REQUEST_RETRY_LIMIT);
    }

    public Boolean isEncryptionEnabled() {
        return DeviceConfig.getBoolean(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ FCP_ENABLE_ENCRYPTION,
                /* defaultValue= */ ENCRYPTION_ENABLED);
    }

    @Override
    public long getMinSchedulingIntervalSecsForFederatedComputation() {
        return DeviceConfig.getLong(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ MIN_SCHEDULING_INTERVAL_SECS_FOR_FEDERATED_COMPUTATION_CONFIG_NAME,
                /* defaultValue= */ MIN_SCHEDULING_INTERVAL_SECS_FOR_FEDERATED_COMPUTATION);
    }

    @Override
    public long getMaxSchedulingIntervalSecsForFederatedComputation() {
        return DeviceConfig.getLong(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ MAX_SCHEDULING_INTERVAL_SECS_FOR_FEDERATED_COMPUTATION_CONFIG_NAME,
                /* defaultValue= */ MAX_SCHEDULING_INTERVAL_SECS_FOR_FEDERATED_COMPUTATION);
    }

    @Override
    public long getDefaultSchedulingPeriodSecs() {
        return DeviceConfig.getLong(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ DEFAULT_SCHEDULING_PERIOD_SECS_CONFIG_NAME,
                /* defaultValue= */ DEFAULT_SCHEDULING_PERIOD_SECS);
    }

    @Override
    public long getMaxSchedulingPeriodSecs() {
        return DeviceConfig.getLong(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ MAX_SCHEDULING_PERIOD_SECS_CONFIG_NAME,
                /* defaultValue= */ MAX_SCHEDULING_PERIOD_SECS);
    }

    @Override
    public float getTransientErrorRetryDelayJitterPercent() {
        return DeviceConfig.getFloat(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ TRANSIENT_ERROR_RETRY_DELAY_JITTER_PERCENT_CONFIG_NAME,
                /* defaultValue= */ TRANSIENT_ERROR_RETRY_DELAY_JITTER_PERCENT);
    }

    @Override
    public long getTransientErrorRetryDelaySecs() {
        return DeviceConfig.getLong(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ TRANSIENT_ERROR_RETRY_DELAY_SECS_CONFIG_NAME,
                /* defaultValue= */ TRANSIENT_ERROR_RETRY_DELAY_SECS);
    }

    @Override
    public int getTrainingMinBatteryLevel() {
        return DeviceConfig.getInt(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ TRAINING_MIN_BATTERY_LEVEL,
                /* defaultValue= */ DEFAULT_TRAINING_MIN_BATTERY_LEVEL);
    }

    @Override
    public int getThermalStatusToThrottle() {
        return DeviceConfig.getInt(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ TRAINING_THERMAL_STATUS_TO_THROTTLE,
                /* defaultValue= */ DEFAULT_THERMAL_STATUS_TO_THROTTLE);
    }

    @Override
    public boolean isEligibilityTaskEnabled() {
        return DeviceConfig.getBoolean(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ ENABLE_ELIGIBILITY_TASK,
                /* defaultValue= */ DEFAULT_ENABLE_ELIGIBILITY_TASK);
    }

    @Override
    public long getTrainingConditionCheckThrottlePeriodMillis() {
        return DeviceConfig.getLong(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ TRAINING_CONDITION_CHECK_THROTTLE_PERIOD_MILLIS,
                /* defaultValue= */ DEFAULT_TRAINING_CONDITION_CHECK_THROTTLE_PERIOD_MILLIS);
    }

    @Override
    public int getFcpRescheduleLimit() {
        return DeviceConfig.getInt(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ FCP_RESCHEDULE_LIMIT_CONFIG_NAME,
                /* defaultValue= */ FCP_RESCHEDULE_LIMIT);
    }

    @Override
    public long getTaskHistoryTtl() {
        return DeviceConfig.getLong(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ TASK_HISTORY_TTL_MILLIS,
                /* defaultValue= */ DEFAULT_TASK_HISTORY_TTL_MILLIS);
    }

    @Override
    public boolean getEnableClientErrorLogging() {
        return DeviceConfig.getBoolean(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ FCP_ENABLE_CLIENT_ERROR_LOGGING,
                /* defaultValue= */ ENABLE_CLIENT_ERROR_LOGGING);
    }

    @Override
    public boolean getBackgroundJobsLoggingEnabled() {
        // needs stable: execution stats may be less accurate if value changed during job execution
        return (boolean)
                sStableFlags.computeIfAbsent(
                        FCP_ENABLE_BACKGROUND_JOBS_LOGGING,
                        key -> {
                            return DeviceConfig.getBoolean(
                                    /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                                    /* name= */ FCP_ENABLE_BACKGROUND_JOBS_LOGGING,
                                    /* defaultValue= */ DEFAULT_BACKGROUND_JOBS_LOGGING_ENABLED);
                        });
    }

    @Override
    public int getBackgroundJobSamplingLoggingRate() {
        return DeviceConfig.getInt(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ FCP_BACKGROUND_JOB_LOGGING_SAMPLING_RATE,
                /* defaultValue= */ DEFAULT_BACKGROUND_JOB_SAMPLING_LOGGING_RATE);
    }
}
