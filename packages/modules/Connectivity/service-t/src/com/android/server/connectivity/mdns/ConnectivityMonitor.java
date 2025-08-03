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

package com.android.server.connectivity.mdns;

import android.net.Network;

/** Interface for monitoring connectivity changes. */
public interface ConnectivityMonitor {
    /**
     * Starts monitoring changes of connectivity of this device, which may indicate that the list of
     * network interfaces available for multi-cast messaging has changed.
     */
    void startWatchingConnectivityChanges();

    /** Stops monitoring changes of connectivity. */
    void stopWatchingConnectivityChanges();

    void notifyConnectivityChange();

    /** Get available network which is received from connectivity change. */
    Network getAvailableNetwork();

    /** Listener interface for receiving connectivity changes. */
    interface Listener {
        void onConnectivityChanged();
    }
}