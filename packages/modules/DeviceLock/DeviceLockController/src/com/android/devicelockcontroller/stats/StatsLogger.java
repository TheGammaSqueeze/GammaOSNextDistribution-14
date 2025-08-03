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

import androidx.annotation.IntDef;

import com.android.devicelockcontroller.DevicelockStatsLog;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Utility class wrapping operations related to Statistics.
 *
 * Please refer to {@link DevicelockStatsLog} class and
 * stats/atoms/devicelock/devicelock_extension_atoms.proto for more information.
 */
public interface StatsLogger {
    /**
     * Logs the analytics event of attempting to get device check in status from the server,
     * regardless if the result is successful.
     */
    void logGetDeviceCheckInStatus();

    /**
     * Logs the analytics event of successfully pausing the device provisioning.
     */
    void logPauseDeviceProvisioning();

    /**
     * Logs the analytics event of successfully reporting the device provisioning state to the
     * server.
     */
    void logReportDeviceProvisionState();

    /**
     * Logs the analytics event of receiving a result from the server of the
     * IsDeviceInApprovedCountry gRPC call.
     */
    void logIsDeviceInApprovedCountry();

    /**
     * Logs the analytics event of receiving a request from the Kisok app.
     *
     * @param uid The UID of the Kiosk app, which can be acquired from the PackageManager.
     */
    void logKioskAppRequest(int uid);

    /**
     * Logs the analytics event of starting the provisioning process, starting the Kiosk app, and
     * the time elapsed in between.
     */
    void logProvisioningComplete(long timeSpentInProvisioningMillis);

    /**
     * Logs the analytics event of resetting the device due to a failed provisioning.
     *
     * @param isProvisioningMandatory True if the provision is mandatory, false otherwise.
     */
    void logDeviceReset(boolean isProvisioningMandatory);

    /**
     * Logs the analytics event of successfully handling a check in response received from the
     * server.
     */
    void logSuccessfulCheckIn();

    /**
     * Logs the analytics event of successfully completing the provisioning.
     */
    void logSuccessfulProvisioning();

    /**
     * Logs the analytics event of retrying a check in request.
     *
     * @param reason The reason of the retry, the enum corresponds to the RetryReason in
     *               frameworks/proto_logging/stats/atoms/devicelock/devicelock_extension_atoms.proto
     */
    void logCheckInRetry(@CheckInRetryReason int reason);

    // TODO(bojiandu): update this definition after updating the atom to match the new design
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            CheckInRetryReason.RESPONSE_UNSPECIFIED,
            CheckInRetryReason.CONFIG_UNAVAILABLE,
            CheckInRetryReason.NETWORK_TIME_UNAVAILABLE,
            CheckInRetryReason.RPC_FAILURE
    })
    @interface CheckInRetryReason {
        int RESPONSE_UNSPECIFIED = 0;
        int CONFIG_UNAVAILABLE = 1;
        int NETWORK_TIME_UNAVAILABLE = 2;
        int RPC_FAILURE = 3;
    }

    /**
     * Logs the analytics event of a provision failure.
     *
     * @param reason The reason of the failure, the enum corresponds to the FailureReason in
     *               frameworks/proto_logging/stats/atoms/devicelock/devicelock_extension_atoms.proto
     */
    void logProvisionFailure(@ProvisionFailureReasonStats int reason);

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            ProvisionFailureReasonStats.UNKNOWN,
            ProvisionFailureReasonStats.PLAY_TASK_UNAVAILABLE,
            ProvisionFailureReasonStats.PLAY_INSTALLATION_FAILED,
            ProvisionFailureReasonStats.COUNTRY_INFO_UNAVAILABLE,
            ProvisionFailureReasonStats.NOT_IN_ELIGIBLE_COUNTRY,
            ProvisionFailureReasonStats.POLICY_ENFORCEMENT_FAILED
    })
    @interface ProvisionFailureReasonStats {
        // Unknown reason
        int UNKNOWN = 0;
        // Failed due to play task unavailable
        int PLAY_TASK_UNAVAILABLE = 1;
        // Failed due to installation from play unsuccessful
        int PLAY_INSTALLATION_FAILED = 2;
        // Failed due to country eligibility unknown
        int COUNTRY_INFO_UNAVAILABLE = 3;
        // Failed due to country not eligible
        int NOT_IN_ELIGIBLE_COUNTRY = 4;
        // Failed due to unable to enforce policies
        int POLICY_ENFORCEMENT_FAILED = 5;
    }

    /**
     * Logs the analytics event of a lock device failure.
     *
     * @param deviceStatePostCommand The device state after the lock device command
     */
    void logLockDeviceFailure(@DeviceStateStats int deviceStatePostCommand);

    /**
     * Logs the analytics event of a unlock device failure.
     *
     * @param deviceStatePostCommand The device state after the unlock device command
     */
    void logUnlockDeviceFailure(@DeviceStateStats int deviceStatePostCommand);

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            DeviceStateStats.UNDEFINED,
            DeviceStateStats.UNLOCKED,
            DeviceStateStats.LOCKED,
            DeviceStateStats.CLEARED,
    })
    @interface DeviceStateStats {
        int UNDEFINED = 0;
        int UNLOCKED = 1;
        int LOCKED = 2;
        int CLEARED = 3;
    }

    /**
     * Logs the analytics event of successfully locking the device.
     */
    void logSuccessfulLockingDevice();

    /**
     * Logs the analytics event of successfully unlocking the device.
     */
    void logSuccessfulUnlockingDevice();
}
