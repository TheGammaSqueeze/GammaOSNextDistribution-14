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

package com.android.permissioncontroller.permission.utils;

import android.content.Context;

import androidx.annotation.NonNull;

import com.android.modules.utils.build.SdkLevel;

/**
 * Helper Context compat class for {@link Context}.
 */
public class ContextCompat {
    /**
     * The default device ID, which is the ID of the primary (non-virtual) device.
     *
     * @see Context#DEVICE_ID_DEFAULT
     */
    public static final int DEVICE_ID_DEFAULT = 0;

    private ContextCompat() {
    }

    /**
     * @return The default device ID for pre V platforms, otherwise returns the device ID from
     * the context.
     */
    public static int getDeviceId(@NonNull Context context) {
        if (SdkLevel.isAtLeastU()) {
            return context.getDeviceId();
        } else {
            return DEVICE_ID_DEFAULT;
        }

    }

    /**
     * Creates a new device context, if needed.
     *
     * @return A new context if the input context is for a different device, otherwise
     * return the same context object. See {@link Context#DEVICE_ID_DEFAULT}
     */
    @NonNull
    public static Context createDeviceContext(@NonNull Context context, int deviceId) {
        if (SdkLevel.isAtLeastU()) {
            return deviceId == context.getDeviceId()
                    ? context : context.createDeviceContext(deviceId);
        } else {
            if (deviceId != DEVICE_ID_DEFAULT) {
                throw new IllegalArgumentException("Invalid device ID " + deviceId);
            }
            return context;
        }
    }
}
