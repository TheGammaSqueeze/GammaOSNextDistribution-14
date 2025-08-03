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

package com.android.server.nearby.injector;

import android.hardware.location.ContextHubClient;
import android.hardware.location.ContextHubClientCallback;
import android.hardware.location.ContextHubInfo;
import android.hardware.location.ContextHubManager;
import android.hardware.location.ContextHubTransaction;
import android.hardware.location.NanoAppState;

import java.util.List;
import java.util.concurrent.Executor;

/** Wrap {@link ContextHubManager} for dependence injection. */
public class ContextHubManagerAdapter {
    private final ContextHubManager mManager;

    public ContextHubManagerAdapter(ContextHubManager manager) {
        mManager = manager;
    }

    /**
     * Returns the list of ContextHubInfo objects describing the available Context Hubs.
     *
     * @return the list of ContextHubInfo objects
     * @see ContextHubInfo
     */
    public List<ContextHubInfo> getContextHubs() {
        return mManager.getContextHubs();
    }

    /**
     * Requests a query for nanoapps loaded at the specified Context Hub.
     *
     * @param hubInfo the hub to query a list of nanoapps from
     * @return the ContextHubTransaction of the request
     * @throws NullPointerException if hubInfo is null
     */
    public ContextHubTransaction<List<NanoAppState>> queryNanoApps(ContextHubInfo hubInfo) {
        return mManager.queryNanoApps(hubInfo);
    }

    /**
     * Creates and registers a client and its callback with the Context Hub Service.
     *
     * <p>A client is registered with the Context Hub Service for a specified Context Hub. When the
     * registration succeeds, the client can send messages to nanoapps through the returned {@link
     * ContextHubClient} object, and receive notifications through the provided callback.
     *
     * @param hubInfo the hub to attach this client to
     * @param executor the executor to invoke the callback
     * @param callback the notification callback to register
     * @return the registered client object
     * @throws IllegalArgumentException if hubInfo does not represent a valid hub
     * @throws IllegalStateException if there were too many registered clients at the service
     * @throws NullPointerException if callback, hubInfo, or executor is null
     */
    public ContextHubClient createClient(
            ContextHubInfo hubInfo, ContextHubClientCallback callback, Executor executor) {
        return mManager.createClient(hubInfo, callback, executor);
    }
}
