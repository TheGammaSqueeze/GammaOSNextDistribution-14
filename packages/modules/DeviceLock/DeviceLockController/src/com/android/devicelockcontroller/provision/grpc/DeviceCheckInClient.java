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

package com.android.devicelockcontroller.provision.grpc;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.UserHandle;
import android.util.ArraySet;
import android.util.Pair;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.android.devicelockcontroller.common.DeviceId;
import com.android.devicelockcontroller.common.DeviceLockConstants.DeviceProvisionState;
import com.android.devicelockcontroller.common.DeviceLockConstants.PauseDeviceProvisioningReason;
import com.android.devicelockcontroller.common.DeviceLockConstants.ProvisionFailureReason;
import com.android.devicelockcontroller.util.LogUtil;

/**
 * An abstract class that's intended for implementation of class that manages communication with
 * DeviceLock backend server.
 */
public abstract class DeviceCheckInClient {
    private static final String TAG = "DeviceCheckInClient";
    private static final String FILENAME = "debug-check-in-preferences";

    public static final String DEVICE_CHECK_IN_CLIENT_DEBUG_CLASS_NAME =
            "com.android.devicelockcontroller.debug.DeviceCheckInClientDebug";
    protected static final String DEBUG_DEVICELOCK_CHECKIN = "debug.devicelock.checkin";
    private static final String HOST_NAME_OVERRIDE = "host.name.override";
    private static volatile DeviceCheckInClient sClient;

    @Nullable
    protected static String sRegisteredId;
    protected static String sHostName = "";
    protected static int sPortNumber = 0;
    protected static Pair<String, String> sApiKey = new Pair<>("", "");
    private static volatile boolean sUseDebugClient;

    @Nullable
    private static volatile SharedPreferences sSharedPreferences;

    @Nullable
    protected static synchronized SharedPreferences getSharedPreferences(
            @Nullable Context context) {
        if (sSharedPreferences == null && context != null) {
            sSharedPreferences =
                    context.createContextAsUser(UserHandle.SYSTEM, /* flags= */
                            0).createDeviceProtectedStorageContext().getSharedPreferences(FILENAME,
                            Context.MODE_PRIVATE);
        }
        return sSharedPreferences;
    }

    /**
     * Override the host name so that the client always connects to it instead
     */
    public static void setHostNameOverride(Context context, String override) {
        getSharedPreferences(context).edit().putString(HOST_NAME_OVERRIDE, override).apply();
    }

    /**
     * Get an instance of DeviceCheckInClient object.
     */
    public static DeviceCheckInClient getInstance(
            Context context,
            String className,
            String hostName,
            int portNumber,
            Pair<String, String> apiKey,
            @Nullable String registeredId) {
        boolean useDebugClient = false;
        String hostNameOverride = "";
        if (Build.isDebuggable()) {
            useDebugClient = getSharedPreferences(context).getBoolean(
                    DEBUG_DEVICELOCK_CHECKIN, /* def= */ false);
            hostNameOverride = getSharedPreferences(context).getString(
                    HOST_NAME_OVERRIDE, /* def= */ "");
            if (!hostNameOverride.isEmpty()) {
                hostName = hostNameOverride;
            }
        }
        synchronized (DeviceCheckInClient.class) {
            try {
                boolean createRequired =
                        (sClient == null || sUseDebugClient != useDebugClient)
                                || (registeredId != null && !registeredId.equals(sRegisteredId))
                                || (hostName != null && !hostName.equals(sHostName))
                                || (apiKey != null && !apiKey.equals(sApiKey));

                if (createRequired) {
                    sHostName = hostName;
                    sPortNumber = portNumber;
                    sRegisteredId = registeredId;
                    sApiKey = apiKey;
                    sUseDebugClient = useDebugClient;
                    if (Build.isDebuggable() && sUseDebugClient) {
                        className = DEVICE_CHECK_IN_CLIENT_DEBUG_CLASS_NAME;
                    }
                    LogUtil.d(TAG, "Creating instance for " + className);
                    Class<?> clazz = Class.forName(className);
                    sClient = (DeviceCheckInClient) clazz.getDeclaredConstructor().newInstance();
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to get DeviceCheckInClient instance", e);
            }
        }
        return sClient;
    }

    /**
     * Check In with DeviceLock backend server and get the next step for the device
     *
     * @param deviceIds            A set of all device unique identifiers, this could include IMEIs,
     *                             MEIDs, etc.
     * @param carrierInfo          The information of the device's sim operator which is used to
     *                             determine the device's geological location and eventually
     *                             eligibility of the DeviceLock program.
     * @param fcmRegistrationToken The fcm registration token
     * @return A class that encapsulate the response from the backend server.
     */
    @WorkerThread
    public abstract GetDeviceCheckInStatusGrpcResponse getDeviceCheckInStatus(
            ArraySet<DeviceId> deviceIds, String carrierInfo,
            @Nullable String fcmRegistrationToken);

    /**
     * Check if the device is in an approved country for the device lock program.
     *
     * @param carrierInfo The information of the device's sim operator which is used to determine
     *                    the device's geological location and eventually eligibility of the
     *                    DeviceLock program. Could be null if unavailable.
     * @return A class that encapsulate the response from the backend server.
     */
    @WorkerThread
    public abstract IsDeviceInApprovedCountryGrpcResponse isDeviceInApprovedCountry(
            @Nullable String carrierInfo);

    /**
     * Inform the server that device provisioning has been paused for a certain amount of time.
     *
     * @param reason The reason that provisioning has been paused.
     * @return A class that encapsulate the response from the backend sever.
     */
    @WorkerThread
    public abstract PauseDeviceProvisioningGrpcResponse pauseDeviceProvisioning(
            @PauseDeviceProvisioningReason int reason);

    /**
     * Reports the current provision state of the device.
     *
     * @param lastReceivedProvisionState one of {@link DeviceProvisionState}.
     *                                   It must be the value from the response when this API
     *                                   was called last time. If this API is called for the first
     *                                   time, then
     *                                   {@link
     *                                   DeviceProvisionState#PROVISION_STATE_UNSPECIFIED }
     *                                   must be used.
     * @param isSuccessful               true if the device has been setup for DeviceLock program
     *                                   successful; false otherwise.
     * @return A class that encapsulate the response from the backend server.
     */
    @WorkerThread
    public abstract ReportDeviceProvisionStateGrpcResponse reportDeviceProvisionState(
            @DeviceProvisionState int lastReceivedProvisionState,
            boolean isSuccessful, @ProvisionFailureReason int failureReason);
}
