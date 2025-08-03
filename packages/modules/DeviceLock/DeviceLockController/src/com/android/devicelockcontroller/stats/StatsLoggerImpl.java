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

package com.android.devicelockcontroller.stats;

import static com.android.devicelockcontroller.DevicelockStatsLog.CHECK_IN_RETRY_REPORTED__REASON__COUNFIGURATION_UNAVAILABLE;
import static com.android.devicelockcontroller.DevicelockStatsLog.CHECK_IN_RETRY_REPORTED__REASON__NETWORK_TIME_UNAVAILABLE;
import static com.android.devicelockcontroller.DevicelockStatsLog.CHECK_IN_RETRY_REPORTED__REASON__RESPONSE_UNSPECIFIED;
import static com.android.devicelockcontroller.DevicelockStatsLog.CHECK_IN_RETRY_REPORTED__REASON__RPC_FAILURE;
import static com.android.devicelockcontroller.DevicelockStatsLog.DEVICE_LOCK_CHECK_IN_REQUEST_REPORTED__TYPE__GET_DEVICE_CHECK_IN_STATUS;
import static com.android.devicelockcontroller.DevicelockStatsLog.DEVICE_LOCK_CHECK_IN_REQUEST_REPORTED__TYPE__IS_DEVICE_IN_APPROVED_COUNTRY;
import static com.android.devicelockcontroller.DevicelockStatsLog.DEVICE_LOCK_CHECK_IN_REQUEST_REPORTED__TYPE__PAUSE_DEVICE_PROVISIONING;
import static com.android.devicelockcontroller.DevicelockStatsLog.DEVICE_LOCK_CHECK_IN_REQUEST_REPORTED__TYPE__REPORT_DEVICE_PROVISION_STATE;
import static com.android.devicelockcontroller.DevicelockStatsLog.DEVICE_LOCK_CHECK_IN_RETRY_REPORTED;
import static com.android.devicelockcontroller.DevicelockStatsLog.DEVICE_LOCK_KIOSK_APP_REQUEST_REPORTED;
import static com.android.devicelockcontroller.DevicelockStatsLog.DEVICE_LOCK_LOCK_UNLOCK_DEVICE_FAILURE_REPORTED;
import static com.android.devicelockcontroller.DevicelockStatsLog.DEVICE_LOCK_PROVISIONING_COMPLETE_REPORTED;
import static com.android.devicelockcontroller.DevicelockStatsLog.DEVICE_LOCK_PROVISION_FAILURE_REPORTED;
import static com.android.devicelockcontroller.DevicelockStatsLog.LOCK_UNLOCK_DEVICE_FAILURE_REPORTED__STATE_POST_COMMAND__CLEARED;
import static com.android.devicelockcontroller.DevicelockStatsLog.LOCK_UNLOCK_DEVICE_FAILURE_REPORTED__STATE_POST_COMMAND__LOCKED;
import static com.android.devicelockcontroller.DevicelockStatsLog.LOCK_UNLOCK_DEVICE_FAILURE_REPORTED__STATE_POST_COMMAND__UNDEFINED;
import static com.android.devicelockcontroller.DevicelockStatsLog.LOCK_UNLOCK_DEVICE_FAILURE_REPORTED__STATE_POST_COMMAND__UNLOCKED;
import static com.android.devicelockcontroller.DevicelockStatsLog.PROVISION_FAILURE_REPORTED__REASON__COUNTRY_INFO_UNAVAILABLE;
import static com.android.devicelockcontroller.DevicelockStatsLog.PROVISION_FAILURE_REPORTED__REASON__NOT_IN_ELIGIBLE_COUNTRY;
import static com.android.devicelockcontroller.DevicelockStatsLog.PROVISION_FAILURE_REPORTED__REASON__PLAY_INSTALLATION_FAILED;
import static com.android.devicelockcontroller.DevicelockStatsLog.PROVISION_FAILURE_REPORTED__REASON__PLAY_TASK_UNAVAILABLE;
import static com.android.devicelockcontroller.DevicelockStatsLog.PROVISION_FAILURE_REPORTED__REASON__POLICY_ENFORCEMENT_FAILED;
import static com.android.devicelockcontroller.DevicelockStatsLog.PROVISION_FAILURE_REPORTED__REASON__UNKNOWN;
import static com.android.devicelockcontroller.stats.StatsLogger.CheckInRetryReason.CONFIG_UNAVAILABLE;
import static com.android.devicelockcontroller.stats.StatsLogger.CheckInRetryReason.NETWORK_TIME_UNAVAILABLE;
import static com.android.devicelockcontroller.stats.StatsLogger.CheckInRetryReason.RESPONSE_UNSPECIFIED;
import static com.android.devicelockcontroller.stats.StatsLogger.CheckInRetryReason.RPC_FAILURE;
import static com.android.devicelockcontroller.stats.StatsLogger.ProvisionFailureReasonStats.COUNTRY_INFO_UNAVAILABLE;
import static com.android.devicelockcontroller.stats.StatsLogger.ProvisionFailureReasonStats.NOT_IN_ELIGIBLE_COUNTRY;
import static com.android.devicelockcontroller.stats.StatsLogger.ProvisionFailureReasonStats.PLAY_INSTALLATION_FAILED;
import static com.android.devicelockcontroller.stats.StatsLogger.ProvisionFailureReasonStats.PLAY_TASK_UNAVAILABLE;
import static com.android.devicelockcontroller.stats.StatsLogger.ProvisionFailureReasonStats.POLICY_ENFORCEMENT_FAILED;
import static com.android.devicelockcontroller.stats.StatsLogger.ProvisionFailureReasonStats.UNKNOWN;

import com.android.devicelockcontroller.DevicelockStatsLog;

import com.android.modules.expresslog.Counter;

import java.util.concurrent.TimeUnit;

public final class StatsLoggerImpl implements StatsLogger{
    // The Telemetry Express metric ID for the counter of device reset due to failure of mandatory
    // provisioning. As defined in
    // platform/frameworks/proto_logging/stats/express/catalog/device_lock.cfg
    static final String TEX_ID_DEVICE_RESET_PROVISION_MANDATORY =
            "device_lock.value_resets_unsuccessful_provisioning_mandatory";
    // The Telemetry Express metric ID for the counter of device reset due to failure of deferred
    // provisioning. As defined in
    // platform/frameworks/proto_logging/stats/express/catalog/device_lock.cfg
    static final String TEX_ID_DEVICE_RESET_PROVISION_DEFERRED =
            "device_lock.value_resets_unsuccessful_provisioning_deferred";
    // The Telemetry Express metric ID for the counter of a successful check in request. As
    // defined in platform/frameworks/proto_logging/stats/express/catalog/device_lock.cfg
    static final String TEX_ID_SUCCESSFUL_CHECK_IN_RESPONSE_COUNT =
            "device_lock.value_successful_check_in_response_count";
    // The Telemetry Express metric ID for the counter of a successful provisioning. As
    // defined in platform/frameworks/proto_logging/stats/express/catalog/device_lock.cfg
    static final String TEX_ID_SUCCESSFUL_PROVISIONING_COUNT =
            "device_lock.value_successful_provisioning_count";
    // The Telemetry Express metric ID for the counter of a successful locking. As
    // defined in platform/frameworks/proto_logging/stats/express/catalog/device_lock.cfg
    static final String TEX_ID_SUCCESSFUL_LOCKING_COUNT =
            "device_lock.value_successful_locking_count";
    // The Telemetry Express metric ID for the counter of a successful unlocking. As
    // defined in platform/frameworks/proto_logging/stats/express/catalog/device_lock.cfg
    static final String TEX_ID_SUCCESSFUL_UNLOCKING_COUNT =
            "device_lock.value_successful_unlocking_count";
    private static final String TAG = "StatsLogger";

    @Override
    public void logGetDeviceCheckInStatus() {
        DevicelockStatsLog.write(DevicelockStatsLog.DEVICE_LOCK_CHECK_IN_REQUEST_REPORTED,
                DEVICE_LOCK_CHECK_IN_REQUEST_REPORTED__TYPE__GET_DEVICE_CHECK_IN_STATUS);
    }

    @Override
    public void logPauseDeviceProvisioning() {
        DevicelockStatsLog.write(DevicelockStatsLog.DEVICE_LOCK_CHECK_IN_REQUEST_REPORTED,
                DEVICE_LOCK_CHECK_IN_REQUEST_REPORTED__TYPE__PAUSE_DEVICE_PROVISIONING);
    }

    @Override
    public void logReportDeviceProvisionState() {
        DevicelockStatsLog.write(DevicelockStatsLog.DEVICE_LOCK_CHECK_IN_REQUEST_REPORTED,
                DEVICE_LOCK_CHECK_IN_REQUEST_REPORTED__TYPE__REPORT_DEVICE_PROVISION_STATE);
    }

    @Override
    public void logIsDeviceInApprovedCountry() {
        DevicelockStatsLog.write(DevicelockStatsLog.DEVICE_LOCK_CHECK_IN_REQUEST_REPORTED,
                DEVICE_LOCK_CHECK_IN_REQUEST_REPORTED__TYPE__IS_DEVICE_IN_APPROVED_COUNTRY);
    }

    @Override
    public void logKioskAppRequest(int uid) {
        DevicelockStatsLog.write(DEVICE_LOCK_KIOSK_APP_REQUEST_REPORTED, uid);
    }

    @Override
    public void logProvisioningComplete(long timeSpentInProvisioningMillis) {
        DevicelockStatsLog.write(DEVICE_LOCK_PROVISIONING_COMPLETE_REPORTED,
                TimeUnit.MILLISECONDS.toSeconds(timeSpentInProvisioningMillis));
    }

    @Override
    public void logDeviceReset(boolean isProvisioningMandatory) {
        if (isProvisioningMandatory) {
            Counter.logIncrement(TEX_ID_DEVICE_RESET_PROVISION_MANDATORY);
        } else {
            Counter.logIncrement(TEX_ID_DEVICE_RESET_PROVISION_DEFERRED);
        }
    }

    @Override
    public void logSuccessfulCheckIn() {
        Counter.logIncrement(TEX_ID_SUCCESSFUL_CHECK_IN_RESPONSE_COUNT);
    }

    @Override
    public void logSuccessfulProvisioning() {
        Counter.logIncrement(TEX_ID_SUCCESSFUL_PROVISIONING_COUNT);
    }

    @Override
    public void logCheckInRetry(@CheckInRetryReason int reason) {
        int checkInRetryReason;
        switch (reason) {
            case CONFIG_UNAVAILABLE -> checkInRetryReason =
                    CHECK_IN_RETRY_REPORTED__REASON__COUNFIGURATION_UNAVAILABLE;
            case NETWORK_TIME_UNAVAILABLE -> checkInRetryReason =
                    CHECK_IN_RETRY_REPORTED__REASON__NETWORK_TIME_UNAVAILABLE;
            case RESPONSE_UNSPECIFIED -> checkInRetryReason =
                    CHECK_IN_RETRY_REPORTED__REASON__RESPONSE_UNSPECIFIED;
            case RPC_FAILURE -> checkInRetryReason = CHECK_IN_RETRY_REPORTED__REASON__RPC_FAILURE;
            default -> checkInRetryReason = CHECK_IN_RETRY_REPORTED__REASON__RESPONSE_UNSPECIFIED;
        }
        DevicelockStatsLog.write(DEVICE_LOCK_CHECK_IN_RETRY_REPORTED, checkInRetryReason);
    }

    @Override
    public void logProvisionFailure(@ProvisionFailureReasonStats int reason) {
        int provisionFailureReason;
        switch (reason) {
            case POLICY_ENFORCEMENT_FAILED -> provisionFailureReason =
                    PROVISION_FAILURE_REPORTED__REASON__POLICY_ENFORCEMENT_FAILED;
            case PLAY_TASK_UNAVAILABLE -> provisionFailureReason =
                    PROVISION_FAILURE_REPORTED__REASON__PLAY_TASK_UNAVAILABLE;
            case NOT_IN_ELIGIBLE_COUNTRY -> provisionFailureReason =
                    PROVISION_FAILURE_REPORTED__REASON__NOT_IN_ELIGIBLE_COUNTRY;
            case COUNTRY_INFO_UNAVAILABLE -> provisionFailureReason =
                    PROVISION_FAILURE_REPORTED__REASON__COUNTRY_INFO_UNAVAILABLE;
            case PLAY_INSTALLATION_FAILED -> provisionFailureReason =
                    PROVISION_FAILURE_REPORTED__REASON__PLAY_INSTALLATION_FAILED;
            case UNKNOWN -> provisionFailureReason = PROVISION_FAILURE_REPORTED__REASON__UNKNOWN;
            default -> provisionFailureReason = PROVISION_FAILURE_REPORTED__REASON__UNKNOWN;
        }
        DevicelockStatsLog.write(DEVICE_LOCK_PROVISION_FAILURE_REPORTED, provisionFailureReason);
    }

    @Override
    public void logLockDeviceFailure(@DeviceStateStats int deviceStatePostCommand) {
        DevicelockStatsLog.write(DEVICE_LOCK_LOCK_UNLOCK_DEVICE_FAILURE_REPORTED,
                /* arg1 = (isLock)*/ true,
                getStatePostCommandForLockUnlockDeviceFailure(deviceStatePostCommand));
    }

    @Override
    public void logUnlockDeviceFailure(@DeviceStateStats int deviceStatePostCommand) {
        DevicelockStatsLog.write(DEVICE_LOCK_LOCK_UNLOCK_DEVICE_FAILURE_REPORTED,
                /* arg1 = (isLock)*/ false,
                getStatePostCommandForLockUnlockDeviceFailure(deviceStatePostCommand));
    }

    private int getStatePostCommandForLockUnlockDeviceFailure(@DeviceStateStats int deviceState) {
        switch (deviceState) {
            case DeviceStateStats.UNDEFINED -> {
                return LOCK_UNLOCK_DEVICE_FAILURE_REPORTED__STATE_POST_COMMAND__UNDEFINED;
            }
            case DeviceStateStats.UNLOCKED -> {
                return LOCK_UNLOCK_DEVICE_FAILURE_REPORTED__STATE_POST_COMMAND__UNLOCKED;
            }
            case DeviceStateStats.LOCKED -> {
                return LOCK_UNLOCK_DEVICE_FAILURE_REPORTED__STATE_POST_COMMAND__LOCKED;
            }
            case DeviceStateStats.CLEARED -> {
                return LOCK_UNLOCK_DEVICE_FAILURE_REPORTED__STATE_POST_COMMAND__CLEARED;
            }
            default -> {
                return LOCK_UNLOCK_DEVICE_FAILURE_REPORTED__STATE_POST_COMMAND__UNDEFINED;
            }
        }
    }

    @Override
    public void logSuccessfulLockingDevice() {
        Counter.logIncrement(TEX_ID_SUCCESSFUL_LOCKING_COUNT);
    }

    @Override
    public void logSuccessfulUnlockingDevice() {
        Counter.logIncrement(TEX_ID_SUCCESSFUL_UNLOCKING_COUNT);
    }
}
