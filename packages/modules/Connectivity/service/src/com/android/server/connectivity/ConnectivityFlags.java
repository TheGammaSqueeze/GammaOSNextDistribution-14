/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.server.connectivity;

import android.content.Context;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.ConnectivityService;

/**
 * Collection of constants for the connectivity module.
 */
public final class ConnectivityFlags {
    /**
     * Minimum module version at which to avoid rematching all requests when a network request is
     * registered, and rematch only the registered requests instead.
     */
    @VisibleForTesting
    public static final String NO_REMATCH_ALL_REQUESTS_ON_REGISTER =
            "no_rematch_all_requests_on_register";

    public static final String CARRIER_SERVICE_CHANGED_USE_CALLBACK =
            "carrier_service_changed_use_callback_version";

    public static final String REQUEST_RESTRICTED_WIFI =
            "request_restricted_wifi";
    private boolean mNoRematchAllRequestsOnRegister;

    /**
     * Whether ConnectivityService should avoid avoid rematching all requests when a network
     * request is registered, and rematch only the registered requests instead.
     *
     * This flag is disabled by default.
     *
     * IMPORTANT NOTE: This flag is false by default and will only be loaded in ConnectivityService
     * systemReady. It is also not volatile for performance reasons, so for most threads it may
     * only change to true after some time. This is fine for this particular flag because it only
     * controls whether all requests or a subset of requests should be rematched, which is only
     * a performance optimization, so its value does not need to be consistent over time; but most
     * flags will not have these properties and should not use the same model.
     *
     * TODO: when adding other flags, consider the appropriate timing to load them, and necessary
     * threading guarantees according to the semantics of the flags.
     */
    public boolean noRematchAllRequestsOnRegister() {
        return mNoRematchAllRequestsOnRegister;
    }

    /**
     * Load flag values. Should only be called once, and can only be called once PackageManager is
     * ready.
     */
    public void loadFlags(ConnectivityService.Dependencies deps, Context ctx) {
        mNoRematchAllRequestsOnRegister = deps.isFeatureEnabled(
                ctx, NO_REMATCH_ALL_REQUESTS_ON_REGISTER);
    }
}
