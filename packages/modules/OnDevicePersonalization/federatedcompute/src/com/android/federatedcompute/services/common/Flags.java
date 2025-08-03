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

import com.android.adservices.shared.common.flags.ModuleSharedFlags;

import java.util.concurrent.TimeUnit;

/** FederatedCompute feature flags interface. This Flags interface hold the default values */
public interface Flags extends ModuleSharedFlags {
    /**
     * Global FederatedCompute APK Kill Switch. This overrides all other killswitches under
     * federatedcompute APK. The default value is false which means FederatedCompute is enabled.
     * This flag is used for emergency turning off.
     */
    boolean FEDERATED_COMPUTE_GLOBAL_KILL_SWITCH = true;

    default boolean getGlobalKillSwitch() {
        return FEDERATED_COMPUTE_GLOBAL_KILL_SWITCH;
    }

    /**
     * Flags for {@link
     * com.android.federatedcompute.services.scheduling.FederatedComputeJobManager}.
     */
    long DEFAULT_SCHEDULING_PERIOD_SECS = 60 * 5; // 5 minutes

    default long getDefaultSchedulingPeriodSecs() {
        return DEFAULT_SCHEDULING_PERIOD_SECS;
    }

    long MIN_SCHEDULING_INTERVAL_SECS_FOR_FEDERATED_COMPUTATION = 1 * 60; // 1 min

    default long getMinSchedulingIntervalSecsForFederatedComputation() {
        return MIN_SCHEDULING_INTERVAL_SECS_FOR_FEDERATED_COMPUTATION;
    }

    long MAX_SCHEDULING_INTERVAL_SECS_FOR_FEDERATED_COMPUTATION =
            6 * 24 * 60 * 60; // 6 days (< default ttl 7d)

    default long getMaxSchedulingIntervalSecsForFederatedComputation() {
        return MAX_SCHEDULING_INTERVAL_SECS_FOR_FEDERATED_COMPUTATION;
    }

    long MAX_SCHEDULING_PERIOD_SECS = 60 * 60 * 24 * 2; // 2 days

    default long getMaxSchedulingPeriodSecs() {
        return MAX_SCHEDULING_PERIOD_SECS;
    }

    long TRAINING_TIME_FOR_LIVE_SECONDS = 7 * 24 * 60 * 60; // one week

    default long getTrainingTimeForLiveSeconds() {
        return TRAINING_TIME_FOR_LIVE_SECONDS;
    }

    long TRAINING_SERVICE_RESULT_CALLBACK_TIMEOUT_SEC =
            60 * 9 + 45; // 9 minutes 45 seconds, leaving ~15 seconds to clean up.

    default long getTrainingServiceResultCallbackTimeoutSecs() {
        return TRAINING_SERVICE_RESULT_CALLBACK_TIMEOUT_SEC;
    }

    float TRANSIENT_ERROR_RETRY_DELAY_JITTER_PERCENT = 0.2f;

    default float getTransientErrorRetryDelayJitterPercent() {
        return TRANSIENT_ERROR_RETRY_DELAY_JITTER_PERCENT;
    }

    long TRANSIENT_ERROR_RETRY_DELAY_SECS = 15 * 60; // 15 minutes

    default long getTransientErrorRetryDelaySecs() {
        return TRANSIENT_ERROR_RETRY_DELAY_SECS;
    }

    /**
     * The minimum percentage (expressed as an integer between 0 and 100) of battery charge that
     * must be remaining in order start training as well as continue it once started.
     */
    int DEFAULT_TRAINING_MIN_BATTERY_LEVEL = 30;

    default int getTrainingMinBatteryLevel() {
        return DEFAULT_TRAINING_MIN_BATTERY_LEVEL;
    }

    /**
     * The thermal status reported by `PowerManager#getCurrentThermalStatus()` at which to interrupt
     * training. Must be one of:
     *
     * <p>THERMAL_STATUS_NONE = 0;<br>
     * THERMAL_STATUS_LIGHT = 1; <br>
     * THERMAL_STATUS_MODERATE = 2; <br>
     * THERMAL_STATUS_SEVERE = 3; <br>
     * THERMAL_STATUS_CRITICAL = 4;<br>
     * THERMAL_STATUS_EMERGENCY = 5; <br>
     * THERMAL_STATUS_SHUTDOWN = 6; <br>
     */
    int DEFAULT_THERMAL_STATUS_TO_THROTTLE = 2;

    default int getThermalStatusToThrottle() {
        return DEFAULT_THERMAL_STATUS_TO_THROTTLE;
    }

    /** The minimum duration between two training condition checks in milliseconds. */
    long DEFAULT_TRAINING_CONDITION_CHECK_THROTTLE_PERIOD_MILLIS = 1000;

    default long getTrainingConditionCheckThrottlePeriodMillis() {
        return DEFAULT_TRAINING_CONDITION_CHECK_THROTTLE_PERIOD_MILLIS;
    }

    String ENCRYPTION_KEY_FETCH_URL = "https://fake-coordinator/v1alpha/publicKeys";

    /**
     * @return Url to fetch encryption key for federated compute.
     */
    default String getEncryptionKeyFetchUrl() {
        return ENCRYPTION_KEY_FETCH_URL;
    }

    Long FEDERATED_COMPUTE_ENCRYPTION_KEY_MAX_AGE_SECONDS =
            TimeUnit.DAYS.toSeconds(14/* duration= */ );

    /**
     * @return default max age in seconds for federated compute ecryption keys.
     */
    default Long getFederatedComputeEncryptionKeyMaxAgeSeconds() {
        return FEDERATED_COMPUTE_ENCRYPTION_KEY_MAX_AGE_SECONDS;
    }

    Long ENCRYPTION_KEY_FETCH_PERIOD_SECONDS = 60 * 60 * 24L; // every 24 h

    default Long getEncryptionKeyFetchPeriodSeconds() {
        return ENCRYPTION_KEY_FETCH_PERIOD_SECONDS;
    }

    Boolean USE_BACKGROUND_ENCRYPTION_KEY_FETCH = true;

    default Boolean getEnableBackgroundEncryptionKeyFetch() {
        return USE_BACKGROUND_ENCRYPTION_KEY_FETCH;
    }

    Long ODP_AUTHORIZATION_TOKEN_DELETION_PERIOD_SECONDs =
            TimeUnit.DAYS.toSeconds(1/* duration= */ );

    default Long getAuthorizationTokenDeletionPeriodSeconds() {
        return ODP_AUTHORIZATION_TOKEN_DELETION_PERIOD_SECONDs;
    }

    int HTTP_REQUEST_RETRY_LIMIT = 3;

    default int getHttpRequestRetryLimit() {
        return HTTP_REQUEST_RETRY_LIMIT;
    }

    Boolean ENCRYPTION_ENABLED = true;

    /** Whether to enable encryption when uploading results. */
    default Boolean isEncryptionEnabled() {
        return ENCRYPTION_ENABLED;
    }

    boolean DEFAULT_ENABLE_ELIGIBILITY_TASK = true;

    default boolean isEligibilityTaskEnabled() {
        return DEFAULT_ENABLE_ELIGIBILITY_TASK;
    }

    int FCP_RESCHEDULE_LIMIT = 6;

    /**
     * Limitation of how many times that FCP task job can be rescheduled if it failed, if federated
     * compute job retry times exceeds this limit, the job will be canceled/abort.
     */
    default int getFcpRescheduleLimit() {
        return FCP_RESCHEDULE_LIMIT;
    }

    // 7 days in milliseconds
    long ODP_AUTHORIZATION_TOKEN_TTL = 7 * 24 * 60 * 60 * 1000L;

    default long getOdpAuthorizationTokenTtl() {
        return ODP_AUTHORIZATION_TOKEN_TTL;
    }

    boolean ENABLE_CLIENT_ERROR_LOGGING = false;

    default boolean getEnableClientErrorLogging() {
        return ENABLE_CLIENT_ERROR_LOGGING;
    }

    // 7 days in milliseconds
    long DEFAULT_TASK_HISTORY_TTL_MILLIS = 7 * 24 * 60 * 60 * 1000L;

    default long getTaskHistoryTtl() {
        return DEFAULT_TASK_HISTORY_TTL_MILLIS;
    }

    boolean DEFAULT_BACKGROUND_JOBS_LOGGING_ENABLED = false;

    default boolean getBackgroundJobsLoggingEnabled() {
        return DEFAULT_BACKGROUND_JOBS_LOGGING_ENABLED;
    }

    /** Default logging rate in percent */
    int DEFAULT_BACKGROUND_JOB_SAMPLING_LOGGING_RATE = 5;

    default int getBackgroundJobSamplingLoggingRate() {
        return DEFAULT_BACKGROUND_JOB_SAMPLING_LOGGING_RATE;
    }
}
