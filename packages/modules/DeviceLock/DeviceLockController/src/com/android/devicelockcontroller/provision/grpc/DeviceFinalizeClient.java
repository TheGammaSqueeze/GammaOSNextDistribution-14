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
import android.os.SystemProperties;
import android.os.UserHandle;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.android.devicelockcontroller.util.LogUtil;

import io.grpc.Status;

/**
 * An abstract class that's intended for implementation of class that manages communication with
 * Device finalize service.
 */
public abstract class DeviceFinalizeClient {
    public static final String TAG = "DeviceFinalizeClient";
    private static final String FILENAME = "debug-finalize-preferences";
    private static final String HOST_NAME_OVERRIDE = "host.name.override";
    public static final String DEVICE_FINALIZE_CLIENT_DEBUG_CLASS_NAME =
            "com.android.devicelockcontroller.debug.DeviceFinalizeClientDebug";
    private static volatile DeviceFinalizeClient sClient;
    protected static String sRegisteredId = "";
    protected static String sHostName = "";
    protected static int sPortNumber = 0;
    protected static Pair<String, String> sApiKey = new Pair<>("", "");
    private static volatile boolean sUseDebugClient;
    @Nullable
    private static volatile SharedPreferences sSharedPreferences;

    @Nullable
    private static synchronized SharedPreferences getSharedPreferences(
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
     * Get an instance of {@link DeviceFinalizeClient} object.
     * Note that, the arguments will be ignored after first initialization.
     */
    public static DeviceFinalizeClient getInstance(
            Context context,
            String className,
            String hostName,
            int portNumber,
            Pair<String, String> apiKey,
            String registeredId) {
        boolean useDebugClient = false;
        String hostNameOverride = "";
        if (Build.isDebuggable()) {
            useDebugClient =
                    SystemProperties.getBoolean("debug.devicelock.finalize", /* def= */ false);
            hostNameOverride = getSharedPreferences(context).getString(
                    HOST_NAME_OVERRIDE, /* def= */ "");
            if (!hostNameOverride.isEmpty()) {
                hostName = hostNameOverride;
            }
        }
        if (sClient == null || sUseDebugClient != useDebugClient) {
            synchronized (DeviceFinalizeClient.class) {
                // In case the initialization is already done by other thread use existing
                // instance.
                if (sClient != null && sUseDebugClient == useDebugClient) {
                    return sClient;
                }
                sHostName = hostName;
                sPortNumber = portNumber;
                sRegisteredId = registeredId;
                sApiKey = apiKey;
                sUseDebugClient = useDebugClient;
                try {
                    if (Build.isDebuggable() && sUseDebugClient) {
                        className = DEVICE_FINALIZE_CLIENT_DEBUG_CLASS_NAME;
                    }
                    LogUtil.d(TAG, "Creating instance for " + className);
                    Class<?> clazz = Class.forName(className);
                    sClient = (DeviceFinalizeClient) clazz.getDeclaredConstructor().newInstance();
                } catch (Exception e) {
                    throw new RuntimeException("Failed to get DeviceFinalizeClient instance", e);
                }
            }
        }
        return sClient;
    }

    /**
     * Reports that a device completed a Device Lock program.
     */
    @WorkerThread
    public abstract ReportDeviceProgramCompleteResponse reportDeviceProgramComplete();

    /**
     * Class that used to indicate the successfulness / failure status of the response.
     */
    public static final class ReportDeviceProgramCompleteResponse extends
            GrpcResponse {
        public ReportDeviceProgramCompleteResponse() {
            super();
        }

        public ReportDeviceProgramCompleteResponse(@NonNull Status status) {
            super(status);
        }
    }
}
