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

package com.android.devicelockcontroller.debug;

import static com.android.devicelockcontroller.common.DeviceLockConstants.DeviceProvisionState.PROVISION_STATE_UNSPECIFIED;
import static com.android.devicelockcontroller.common.DeviceLockConstants.READY_FOR_PROVISION;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.ArraySet;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;

import com.android.devicelockcontroller.common.DeviceId;
import com.android.devicelockcontroller.common.DeviceLockConstants.DeviceCheckInStatus;
import com.android.devicelockcontroller.common.DeviceLockConstants.DeviceProvisionState;
import com.android.devicelockcontroller.common.DeviceLockConstants.ProvisionFailureReason;
import com.android.devicelockcontroller.common.DeviceLockConstants.ProvisioningType;
import com.android.devicelockcontroller.provision.grpc.DeviceCheckInClient;
import com.android.devicelockcontroller.provision.grpc.GetDeviceCheckInStatusGrpcResponse;
import com.android.devicelockcontroller.provision.grpc.IsDeviceInApprovedCountryGrpcResponse;
import com.android.devicelockcontroller.provision.grpc.PauseDeviceProvisioningGrpcResponse;
import com.android.devicelockcontroller.provision.grpc.ProvisioningConfiguration;
import com.android.devicelockcontroller.provision.grpc.ReportDeviceProvisionStateGrpcResponse;
import com.android.devicelockcontroller.util.LogUtil;
import com.android.devicelockcontroller.util.ThreadAsserts;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * An implementation of the {@link DeviceCheckInClient} which simulate server responses by
 * reading it from local storage.
 */
@Keep
public final class DeviceCheckInClientDebug extends DeviceCheckInClient {

    public static final String TAG = "DeviceCheckInClientDebug";
    public static final String DEBUG_DEVICELOCK_CHECKIN_STATUS = "debug.devicelock.checkin.status";
    public static final String DEBUG_DEVICELOCK_CHECKIN_RETRY_DELAY =
            "debug.devicelock.checkin.retry-delay";
    public static final String DEBUG_DEVICELOCK_CHECKIN_FORCE_PROVISIONING =
            "debug.devicelock.checkin.force-provisioning";
    public static final String DEBUG_DEVICELOCK_CHECKIN_APPROVED_COUNTRY =
            "debug.devicelock.checkin.approved-country";
    public static final String DEBUG_DEVICELOCK_CHECKIN_NEXT_PROVISION_STATE =
            "debug.devicelock.checkin.next-provision-state";
    public static final String DEBUG_DEVICELOCK_CHECKIN_DAYS_LEFT_UNTIL_RESET =
            "debug.devicelock.checkin.days-left-until-reset";

    static void setDebugClientEnabled(Context context, boolean enabled) {
        getSharedPreferences(context).edit().putBoolean(DEBUG_DEVICELOCK_CHECKIN, enabled).apply();
    }

    static void setDebugCheckInStatus(Context context, @DeviceCheckInStatus int status) {
        getSharedPreferences(context).edit().putInt(DEBUG_DEVICELOCK_CHECKIN_STATUS,
                status).apply();
    }

    static void setDebugForceProvisioning(Context context, boolean isForced) {
        getSharedPreferences(context).edit().putBoolean(DEBUG_DEVICELOCK_CHECKIN_FORCE_PROVISIONING,
                isForced).apply();
    }

    static void setDebugCheckInRetryDelay(Context context, int delayMinute) {
        getSharedPreferences(context).edit().putInt(DEBUG_DEVICELOCK_CHECKIN_RETRY_DELAY,
                delayMinute).apply();
    }

    static void setDebugApprovedCountry(Context context,
            boolean isInApprovedCountry) {
        getSharedPreferences(context).edit().putBoolean(DEBUG_DEVICELOCK_CHECKIN_APPROVED_COUNTRY,
                isInApprovedCountry).apply();
    }

    static void setDebugNextProvisionState(
            Context context, @DeviceProvisionState int provisionState) {
        getSharedPreferences(context).edit().putInt(DEBUG_DEVICELOCK_CHECKIN_NEXT_PROVISION_STATE,
                provisionState).apply();
    }

    static void setDebugDaysLeftUntilReset(Context context,
            int daysLeftUntilReset) {
        getSharedPreferences(context).edit().putInt(DEBUG_DEVICELOCK_CHECKIN_DAYS_LEFT_UNTIL_RESET,
                daysLeftUntilReset).apply();
    }

    static void dumpDebugCheckInClientResponses(Context context) {
        LogUtil.d(TAG,
                "Current Debug Client Responses:\n" + getSharedPreferences(context).getAll());
    }

    static void clear(Context context) {
        getSharedPreferences(context).edit().clear().apply();
    }

    private static <T> T getSharedPreference(String key, T defValue) {
        SharedPreferences preferences = getSharedPreferences(/* context= */ null);
        if (preferences == null) return defValue;
        T value = (T) preferences.getAll().get(key);
        return value == null ? defValue : value;
    }

    /**
     * Check In with DeviceLock backend server and get the next step for the device.
     */
    @Override
    public GetDeviceCheckInStatusGrpcResponse getDeviceCheckInStatus(ArraySet<DeviceId> deviceIds,
            String carrierInfo, @Nullable String fcmRegistrationToken) {
        ThreadAsserts.assertWorkerThread("getDeviceCheckInStatus");
        return new GetDeviceCheckInStatusGrpcResponse() {
            @Override
            @DeviceCheckInStatus
            public int getDeviceCheckInStatus() {
                return DebugLogUtil.logAndReturn(TAG,
                        getSharedPreference(DEBUG_DEVICELOCK_CHECKIN_STATUS, READY_FOR_PROVISION));
            }

            @Nullable
            @Override
            public String getRegisteredDeviceIdentifier() {
                return DebugLogUtil.logAndReturn(TAG,
                        !deviceIds.isEmpty() ? deviceIds.valueAt(0).getId() : null);
            }

            @Nullable
            @Override
            public Instant getNextCheckInTime() {
                Duration delay = Duration.ofMinutes(
                        getSharedPreference(DEBUG_DEVICELOCK_CHECKIN_RETRY_DELAY, /* def= */ 1));
                return DebugLogUtil.logAndReturn(TAG,
                        SystemClock.currentNetworkTimeClock().instant().plus(delay));
            }

            @Nullable
            @Override
            public ProvisioningConfiguration getProvisioningConfig() {
                // This should be overridden using SetupParametersOverrider.
                return new ProvisioningConfiguration(
                        /* kioskAppProviderName= */ "",
                        /* kioskAppPackageName= */ "",
                        /* kioskAppAllowlistPackages= */ List.of(""),
                        /* kioskAppEnableOutgoingCalls= */ false,
                        /* kioskAppEnableEnableNotifications= */ false,
                        /* disallowInstallingFromUnknownSources= */ false,
                        /* termsAndConditionsUrl= */ "",
                        /* supportUrl= */ "");
            }

            @Override
            public @ProvisioningType int getProvisioningType() {
                // This should be overridden using SetupParametersOverrider.
                return ProvisioningType.TYPE_UNDEFINED;
            }

            @Override
            public boolean isProvisioningMandatory() {
                // This should be overridden using SetupParametersOverrider.
                return false;
            }

            @Override
            public boolean isProvisionForced() {
                return DebugLogUtil.logAndReturn(TAG,
                        getSharedPreference(DEBUG_DEVICELOCK_CHECKIN_FORCE_PROVISIONING, false));
            }

            @Override
            public boolean isDeviceInApprovedCountry() {
                return DebugLogUtil.logAndReturn(TAG,
                        getSharedPreference(DEBUG_DEVICELOCK_CHECKIN_APPROVED_COUNTRY, true));
            }
        };
    }

    /**
     * Check if the device is in an approved country for the device lock program.
     */
    @Override
    public IsDeviceInApprovedCountryGrpcResponse isDeviceInApprovedCountry(
            @Nullable String carrierInfo) {
        ThreadAsserts.assertWorkerThread("isDeviceInApprovedCountry");
        return new IsDeviceInApprovedCountryGrpcResponse() {
            @Override
            public boolean isDeviceInApprovedCountry() {
                return DebugLogUtil.logAndReturn(TAG,
                        getSharedPreference(DEBUG_DEVICELOCK_CHECKIN_APPROVED_COUNTRY, true));
            }
        };
    }

    /**
     * Inform the server that device provisioning has been paused for a certain amount of time.
     */
    @Override
    public PauseDeviceProvisioningGrpcResponse pauseDeviceProvisioning(int reason) {
        ThreadAsserts.assertWorkerThread("pauseDeviceProvisioning");
        return new PauseDeviceProvisioningGrpcResponse();
    }

    /**
     * Reports the current provision state of the device.
     */
    @Override
    public ReportDeviceProvisionStateGrpcResponse reportDeviceProvisionState(
            int lastReceivedProvisionState, boolean isSuccessful,
            @ProvisionFailureReason int reason) {
        ThreadAsserts.assertWorkerThread("reportDeviceProvisionState");
        return new ReportDeviceProvisionStateGrpcResponse() {
            @Override
            @DeviceProvisionState
            public int getNextClientProvisionState() {
                return DebugLogUtil.logAndReturn(TAG, getSharedPreference(
                        DEBUG_DEVICELOCK_CHECKIN_NEXT_PROVISION_STATE,
                        PROVISION_STATE_UNSPECIFIED));
            }

            @Override
            public int getDaysLeftUntilReset() {
                return DebugLogUtil.logAndReturn(TAG,
                        getSharedPreference(
                                DEBUG_DEVICELOCK_CHECKIN_DAYS_LEFT_UNTIL_RESET, /* def= */ 1));
            }
        };
    }
}
