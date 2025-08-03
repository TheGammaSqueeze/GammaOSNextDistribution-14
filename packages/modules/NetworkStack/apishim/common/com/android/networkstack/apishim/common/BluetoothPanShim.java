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

package com.android.networkstack.apishim.common;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.Executor;

/**
 * Interface for accessing API methods in {@link android.bluetooth.BluetoothPan} by different SDK
 * level.
 */
public interface BluetoothPanShim {
    /** Use to deal with the TetheringManager#TetheredInterfaceRequest by different sdk version. */
    public interface TetheredInterfaceRequestShim {
        /** Release the request to tear down tethered interface */
        default void release() {}
    }

    /** Use to deal with the TetheringManager#TetheredInterfaceCallback by different sdk version. */
    public interface TetheredInterfaceCallbackShim {
        /** Called when the tethered interface is available. */
        default void onAvailable(@NonNull String iface) {}

        /** Called when the tethered interface is now unavailable. */
        default void onUnavailable() {}
    }

    /**
     * Use to deal with the BluetoothPan#setBluetoothTethering and
     * BluetoothPan#requestTetheredInterface by different sdk version. This can return null if the
     * service is not available.
     */
    @Nullable
    TetheredInterfaceRequestShim requestTetheredInterface(@NonNull Executor executor,
            @NonNull TetheredInterfaceCallbackShim callback) throws UnsupportedApiLevelException;
}
