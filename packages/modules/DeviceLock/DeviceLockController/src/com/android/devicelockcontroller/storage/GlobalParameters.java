/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.devicelockcontroller.storage;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.annotation.Nullable;

import com.android.devicelockcontroller.common.DeviceLockConstants.DeviceProvisionState;
import com.android.devicelockcontroller.policy.DeviceStateController.DeviceState;
import com.android.devicelockcontroller.policy.FinalizationControllerImpl.FinalizationState;
import com.android.devicelockcontroller.util.LogUtil;

import java.util.Locale;

/**
 * Stores global parameters.
 * <p>
 * Note that, these parameter values are common across all users which means any users can read or
 * write them. Due to this reason, unlike {@link UserParameters}, they must be accessed all the time
 * via the {@link GlobalParametersClient}.
 */
final class GlobalParameters {
    private static final String FILENAME = "global-params";
    private static final String KEY_REGISTERED_DEVICE_ID = "registered_device_id";
    private static final String KEY_FORCED_PROVISION = "forced_provision";
    private static final String KEY_LAST_RECEIVED_PROVISION_STATE = "last-received-provision-state";
    private static final String TAG = "GlobalParameters";
    private static final String KEY_DEVICE_STATE = "device_state";
    private static final String KEY_FINALIZATION_STATE = "finalization_state";
    public static final String KEY_IS_PROVISION_READY = "key-is-provision-ready";


    private GlobalParameters() {
    }

    private static SharedPreferences getSharedPreferences(Context context) {
        final Context deviceContext = context.createDeviceProtectedStorageContext();

        return deviceContext.getSharedPreferences(FILENAME, Context.MODE_PRIVATE);
    }

    static boolean isProvisionReady(Context context) {
        return getSharedPreferences(context).getBoolean(KEY_IS_PROVISION_READY, false);
    }

    static void setProvisionReady(Context context, boolean isProvisionReady) {
        getSharedPreferences(context).edit().putBoolean(KEY_IS_PROVISION_READY,
                isProvisionReady).apply();
    }

    /**
     * Gets the unique identifier that is regisered to DeviceLock backend server.
     *
     * @param context Context used to get the shared preferences.
     * @return The registered device unique identifier; null if device has never checked in with
     * backed server.
     */
    @Nullable
    static String getRegisteredDeviceId(Context context) {
        SharedPreferences preferences = getSharedPreferences(context);
        return preferences.getString(KEY_REGISTERED_DEVICE_ID, null);
    }

    /**
     * Set the unique identifier that is registered to DeviceLock backend server.
     *
     * @param context            Context used to get the shared preferences.
     * @param registeredDeviceId The registered device unique identifier.
     */
    static void setRegisteredDeviceId(Context context, String registeredDeviceId) {
        getSharedPreferences(context)
                .edit()
                .putString(KEY_REGISTERED_DEVICE_ID, registeredDeviceId)
                .apply();
    }

    /**
     * Check if provision should be forced.
     *
     * @param context Context used to get the shared preferences.
     * @return True if the provision should be forced without any delays.
     */
    static boolean isProvisionForced(Context context) {
        return getSharedPreferences(context).getBoolean(KEY_FORCED_PROVISION, false);
    }

    /**
     * Gets the current device state.
     */
    @DeviceState
    static int getDeviceState(Context context) {
        return getSharedPreferences(context).getInt(KEY_DEVICE_STATE, DeviceState.UNDEFINED);
    }

    /**
     * Sets the current device state.
     */
    static void setDeviceState(Context context, @DeviceState int state) {
        getSharedPreferences(context).edit().putInt(KEY_DEVICE_STATE, state).apply();
    }

    /**
     * Gets the current {@link FinalizationState}.
     */
    @FinalizationState
    static int getFinalizationState(Context context) {
        return getSharedPreferences(context).getInt(
                KEY_FINALIZATION_STATE, FinalizationState.UNFINALIZED);
    }

    /**
     * Sets the current {@link FinalizationState}.
     */
    static void setFinalizationState(Context context, @FinalizationState int state) {
        getSharedPreferences(context).edit().putInt(KEY_FINALIZATION_STATE, state).apply();
    }

    /**
     * Set provision is forced
     *
     * @param context  Context used to get the shared preferences.
     * @param isForced The new value of the forced provision flag.
     */
    static void setProvisionForced(Context context, boolean isForced) {
        getSharedPreferences(context)
                .edit()
                .putBoolean(KEY_FORCED_PROVISION, isForced)
                .apply();
    }

    @DeviceProvisionState
    static int getLastReceivedProvisionState(Context context) {
        return getSharedPreferences(context).getInt(KEY_LAST_RECEIVED_PROVISION_STATE,
                DeviceProvisionState.PROVISION_STATE_UNSPECIFIED);
    }

    static void setLastReceivedProvisionState(Context context,
            @DeviceProvisionState int provisionState) {
        getSharedPreferences(context)
                .edit()
                .putInt(KEY_LAST_RECEIVED_PROVISION_STATE, provisionState)
                .apply();
    }

    static void clear(Context context) {
        if (!Build.isDebuggable()) {
            throw new SecurityException("Clear is not allowed in non-debuggable build!");
        }
        getSharedPreferences(context).edit().clear().commit();
    }

    static void dump(Context context) {
        LogUtil.d(TAG, String.format(Locale.US,
                "Dumping GlobalParameters ...\n"
                        + "%s: %s\n"    // registered_device_id:
                        + "%s: %s\n"    // forced_provision:
                        + "%s: %s\n"    // last-received-provision-state:
                        + "%s: %s\n"    // device_state:
                        + "%s: %s\n",    // is-provision-ready:
                KEY_REGISTERED_DEVICE_ID, getRegisteredDeviceId(context),
                KEY_FORCED_PROVISION, isProvisionForced(context),
                KEY_LAST_RECEIVED_PROVISION_STATE, getLastReceivedProvisionState(context),
                KEY_DEVICE_STATE, getDeviceState(context),
                KEY_IS_PROVISION_READY, isProvisionReady(context)
        ));
    }
}
