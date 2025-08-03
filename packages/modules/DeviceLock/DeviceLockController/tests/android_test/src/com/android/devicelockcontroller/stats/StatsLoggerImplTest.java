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
import static com.android.devicelockcontroller.DevicelockStatsLog.LOCK_UNLOCK_DEVICE_FAILURE_REPORTED__STATE_POST_COMMAND__LOCKED;
import static com.android.devicelockcontroller.DevicelockStatsLog.LOCK_UNLOCK_DEVICE_FAILURE_REPORTED__STATE_POST_COMMAND__UNLOCKED;
import static com.android.devicelockcontroller.DevicelockStatsLog.PROVISION_FAILURE_REPORTED__REASON__COUNTRY_INFO_UNAVAILABLE;
import static com.android.devicelockcontroller.DevicelockStatsLog.PROVISION_FAILURE_REPORTED__REASON__NOT_IN_ELIGIBLE_COUNTRY;
import static com.android.devicelockcontroller.DevicelockStatsLog.PROVISION_FAILURE_REPORTED__REASON__PLAY_INSTALLATION_FAILED;
import static com.android.devicelockcontroller.DevicelockStatsLog.PROVISION_FAILURE_REPORTED__REASON__PLAY_TASK_UNAVAILABLE;
import static com.android.devicelockcontroller.DevicelockStatsLog.PROVISION_FAILURE_REPORTED__REASON__POLICY_ENFORCEMENT_FAILED;
import static com.android.devicelockcontroller.DevicelockStatsLog.PROVISION_FAILURE_REPORTED__REASON__UNKNOWN;
import static com.android.devicelockcontroller.stats.StatsLogger.ProvisionFailureReasonStats.COUNTRY_INFO_UNAVAILABLE;
import static com.android.devicelockcontroller.stats.StatsLogger.ProvisionFailureReasonStats.NOT_IN_ELIGIBLE_COUNTRY;
import static com.android.devicelockcontroller.stats.StatsLogger.ProvisionFailureReasonStats.PLAY_INSTALLATION_FAILED;
import static com.android.devicelockcontroller.stats.StatsLogger.ProvisionFailureReasonStats.PLAY_TASK_UNAVAILABLE;
import static com.android.devicelockcontroller.stats.StatsLogger.ProvisionFailureReasonStats.POLICY_ENFORCEMENT_FAILED;
import static com.android.devicelockcontroller.stats.StatsLogger.ProvisionFailureReasonStats.UNKNOWN;
import static com.android.devicelockcontroller.stats.StatsLoggerImpl.TEX_ID_DEVICE_RESET_PROVISION_DEFERRED;
import static com.android.devicelockcontroller.stats.StatsLoggerImpl.TEX_ID_DEVICE_RESET_PROVISION_MANDATORY;
import static com.android.devicelockcontroller.stats.StatsLoggerImpl.TEX_ID_SUCCESSFUL_CHECK_IN_RESPONSE_COUNT;
import static com.android.devicelockcontroller.stats.StatsLoggerImpl.TEX_ID_SUCCESSFUL_LOCKING_COUNT;
import static com.android.devicelockcontroller.stats.StatsLoggerImpl.TEX_ID_SUCCESSFUL_PROVISIONING_COUNT;
import static com.android.devicelockcontroller.stats.StatsLoggerImpl.TEX_ID_SUCCESSFUL_UNLOCKING_COUNT;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import com.android.modules.expresslog.Counter;
import com.android.modules.utils.testing.ExtendedMockitoRule;

import org.junit.Rule;
import org.junit.Test;

import com.android.devicelockcontroller.DevicelockStatsLog;

import java.util.concurrent.TimeUnit;

public final class StatsLoggerImplTest {
    private static final int UID = 123;
    private static final long PROVISIONING_TIME_MILLIS = 2000;
    private final StatsLogger mStatsLogger = new StatsLoggerImpl();

    @Rule
    public final ExtendedMockitoRule mExtendedMockitoRule =
            new ExtendedMockitoRule.Builder(this)
                    .mockStatic(DevicelockStatsLog.class)
                    .mockStatic(Counter.class)
                    .build();

    @Test
    public void logGetDeviceCheckInStatus_shouldWriteCorrectLog() {
        mStatsLogger.logGetDeviceCheckInStatus();
        verify(() -> DevicelockStatsLog.write(
                DevicelockStatsLog.DEVICE_LOCK_CHECK_IN_REQUEST_REPORTED,
                DEVICE_LOCK_CHECK_IN_REQUEST_REPORTED__TYPE__GET_DEVICE_CHECK_IN_STATUS));
    }

    @Test
    public void logPauseDeviceProvisioning_shouldWriteCorrectLog() {
        mStatsLogger.logPauseDeviceProvisioning();
        verify(() -> DevicelockStatsLog.write(
                DevicelockStatsLog.DEVICE_LOCK_CHECK_IN_REQUEST_REPORTED,
                DEVICE_LOCK_CHECK_IN_REQUEST_REPORTED__TYPE__PAUSE_DEVICE_PROVISIONING));
    }

    @Test
    public void logReportDeviceProvisionState_shouldWriteCorrectLog() {
        mStatsLogger.logReportDeviceProvisionState();
        verify(() -> DevicelockStatsLog.write(
                DevicelockStatsLog.DEVICE_LOCK_CHECK_IN_REQUEST_REPORTED,
                DEVICE_LOCK_CHECK_IN_REQUEST_REPORTED__TYPE__REPORT_DEVICE_PROVISION_STATE));
    }

    @Test
    public void logKioskAppRequest_shouldWriteCorrectLog() {
        mStatsLogger.logKioskAppRequest(UID);
        verify(() -> DevicelockStatsLog.write(DEVICE_LOCK_KIOSK_APP_REQUEST_REPORTED, UID));
    }

    @Test
    public void logProvisioningComplete_afterStartTimerForProvisioning_shouldWriteCorrectLog() {
        mStatsLogger.logProvisioningComplete(PROVISIONING_TIME_MILLIS);

        verify(() -> DevicelockStatsLog.write(DEVICE_LOCK_PROVISIONING_COMPLETE_REPORTED,
                TimeUnit.MILLISECONDS.toSeconds(PROVISIONING_TIME_MILLIS)));
    }

    @Test
    public void logIsDeviceInApprovedCountry_shouldWriteCorrectLog() {
        mStatsLogger.logIsDeviceInApprovedCountry();
        verify(() -> DevicelockStatsLog.write(
                DevicelockStatsLog.DEVICE_LOCK_CHECK_IN_REQUEST_REPORTED,
                DEVICE_LOCK_CHECK_IN_REQUEST_REPORTED__TYPE__IS_DEVICE_IN_APPROVED_COUNTRY));
    }

    @Test
    public void logDeviceReset_provisionMandatory_shouldLogToTelemetryExpress() {
        mStatsLogger.logDeviceReset(/* isProvisionMandatory */true);

        verify(() -> Counter.logIncrement(TEX_ID_DEVICE_RESET_PROVISION_MANDATORY));
    }

    @Test
    public void logDeviceReset_deferredProvision_shouldLogToTelemetryExpress() {
        mStatsLogger.logDeviceReset(/* isProvisionMandatory */false);

        verify(() -> Counter.logIncrement(TEX_ID_DEVICE_RESET_PROVISION_DEFERRED));
    }

    @Test
    public void logSuccessfulCheckIn_shouldLogToTelemetryExpress() {
        mStatsLogger.logSuccessfulCheckIn();

        verify(() -> Counter.logIncrement(TEX_ID_SUCCESSFUL_CHECK_IN_RESPONSE_COUNT));
    }

    @Test
    public void logSuccessfulProvisioning_shouldLogToTelemetryExpress() {
        mStatsLogger.logSuccessfulProvisioning();

        verify(() -> Counter.logIncrement(TEX_ID_SUCCESSFUL_PROVISIONING_COUNT));
    }

    @Test
    public void logCheckInRetry_shouldWriteCorrectLogWhenReasonUnspecified() {
        mStatsLogger.logCheckInRetry(StatsLogger.CheckInRetryReason.RESPONSE_UNSPECIFIED);

        verify(() -> DevicelockStatsLog.write(DEVICE_LOCK_CHECK_IN_RETRY_REPORTED,
                CHECK_IN_RETRY_REPORTED__REASON__RESPONSE_UNSPECIFIED));
    }

    @Test
    public void logCheckInRetry_shouldWriteCorrectLogWhenReasonConfigUnavailable() {
        mStatsLogger.logCheckInRetry(StatsLogger.CheckInRetryReason.CONFIG_UNAVAILABLE);

        verify(() -> DevicelockStatsLog.write(DEVICE_LOCK_CHECK_IN_RETRY_REPORTED,
                CHECK_IN_RETRY_REPORTED__REASON__COUNFIGURATION_UNAVAILABLE));
    }

    @Test
    public void logCheckInRetry_shouldWriteCorrectLogWhenReasonNetworkTimeUnavailable() {
        mStatsLogger.logCheckInRetry(StatsLogger.CheckInRetryReason.NETWORK_TIME_UNAVAILABLE);

        verify(() -> DevicelockStatsLog.write(DEVICE_LOCK_CHECK_IN_RETRY_REPORTED,
                CHECK_IN_RETRY_REPORTED__REASON__NETWORK_TIME_UNAVAILABLE));
    }

    @Test
    public void logCheckInRetry_shouldWriteCorrectLogWhenReasonRpcFailure() {
        mStatsLogger.logCheckInRetry(StatsLogger.CheckInRetryReason.RPC_FAILURE);

        verify(() -> DevicelockStatsLog.write(DEVICE_LOCK_CHECK_IN_RETRY_REPORTED,
                CHECK_IN_RETRY_REPORTED__REASON__RPC_FAILURE));
    }

    @Test
    public void logProvisionFailure_shouldWriteCorrectLog_whenReasonPolicyEnforcementFailed() {
        mStatsLogger.logProvisionFailure(POLICY_ENFORCEMENT_FAILED);

        verify(() -> DevicelockStatsLog.write(DEVICE_LOCK_PROVISION_FAILURE_REPORTED,
                PROVISION_FAILURE_REPORTED__REASON__POLICY_ENFORCEMENT_FAILED));
    }

    @Test
    public void logProvisionFailure_shouldWriteCorrectLog_whenReasonPlayTaskUnavailable() {
        mStatsLogger.logProvisionFailure(PLAY_TASK_UNAVAILABLE);

        verify(() -> DevicelockStatsLog.write(DEVICE_LOCK_PROVISION_FAILURE_REPORTED,
                PROVISION_FAILURE_REPORTED__REASON__PLAY_TASK_UNAVAILABLE));
    }

    @Test
    public void logProvisionFailure_shouldWriteCorrectLog_whenReasonNotInEligibleCountry() {
        mStatsLogger.logProvisionFailure(NOT_IN_ELIGIBLE_COUNTRY);

        verify(() -> DevicelockStatsLog.write(DEVICE_LOCK_PROVISION_FAILURE_REPORTED,
                PROVISION_FAILURE_REPORTED__REASON__NOT_IN_ELIGIBLE_COUNTRY));
    }

    @Test
    public void logProvisionFailure_shouldWriteCorrectLog_whenReasonCountryInfoNotAvailable() {
        mStatsLogger.logProvisionFailure(COUNTRY_INFO_UNAVAILABLE);

        verify(() -> DevicelockStatsLog.write(DEVICE_LOCK_PROVISION_FAILURE_REPORTED,
                PROVISION_FAILURE_REPORTED__REASON__COUNTRY_INFO_UNAVAILABLE));
    }

    @Test
    public void logProvisionFailure_shouldWriteCorrectLog_whenReasonPlayInstallationFailed() {
        mStatsLogger.logProvisionFailure(PLAY_INSTALLATION_FAILED);

        verify(() -> DevicelockStatsLog.write(DEVICE_LOCK_PROVISION_FAILURE_REPORTED,
                PROVISION_FAILURE_REPORTED__REASON__PLAY_INSTALLATION_FAILED));
    }

    @Test
    public void logProvisionFailure_shouldWriteCorrectLog_whenReasonUnknown() {
        mStatsLogger.logProvisionFailure(UNKNOWN);

        verify(() -> DevicelockStatsLog.write(DEVICE_LOCK_PROVISION_FAILURE_REPORTED,
                PROVISION_FAILURE_REPORTED__REASON__UNKNOWN));
    }

    @Test
    public void logLockDeviceFailure_shouldWriteCorrectLog() {
        mStatsLogger.logLockDeviceFailure(StatsLogger.DeviceStateStats.LOCKED);

        verify(() -> DevicelockStatsLog.write(DEVICE_LOCK_LOCK_UNLOCK_DEVICE_FAILURE_REPORTED,
                /* arg1= (isLock)*/true,
                LOCK_UNLOCK_DEVICE_FAILURE_REPORTED__STATE_POST_COMMAND__LOCKED));
    }

    @Test
    public void logUnlockDeviceFailure_shouldWriteCorrectLog() {
        mStatsLogger.logUnlockDeviceFailure(StatsLogger.DeviceStateStats.UNLOCKED);

        verify(() -> DevicelockStatsLog.write(DEVICE_LOCK_LOCK_UNLOCK_DEVICE_FAILURE_REPORTED,
                /* arg1= (isLock)*/false,
                LOCK_UNLOCK_DEVICE_FAILURE_REPORTED__STATE_POST_COMMAND__UNLOCKED));
    }

    @Test
    public void logSuccessfulLockingDevice_shouldWriteCorrectLog() {
        mStatsLogger.logSuccessfulLockingDevice();

        verify(() -> Counter.logIncrement(TEX_ID_SUCCESSFUL_LOCKING_COUNT));
    }

    @Test
    public void logSuccessfulUnlockingDevice_shouldWriteCorrectLog() {
        mStatsLogger.logSuccessfulUnlockingDevice();

        verify(() -> Counter.logIncrement(TEX_ID_SUCCESSFUL_UNLOCKING_COUNT));
    }
}
