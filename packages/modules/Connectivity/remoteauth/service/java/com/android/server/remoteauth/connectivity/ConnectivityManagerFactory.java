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

package com.android.server.remoteauth.connectivity;

import android.annotation.NonNull;
import android.content.Context;

import com.android.internal.util.Preconditions;

import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Factory class to create different types of connectivity managers based on the underlying
 * network transports (for example CompanionDeviceManager).
 */
public final class ConnectivityManagerFactory {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    /**
     * Creates and returns a ConnectivityManager object depending on connection type.
     *
     * @param context of the caller.
     * @return ConnectivityManager object.
     */
    public static ConnectivityManager getConnectivityManager(@NonNull Context context) {
        Preconditions.checkNotNull(context);

        // For now, we only have one case, but ideally this should create a new type based on some
        // feature flag.
        return new CdmConnectivityManager(EXECUTOR, new CompanionDeviceManagerWrapper(
                new WeakReference<>(context.getApplicationContext()).get()));
    }

    private ConnectivityManagerFactory() {}
}
