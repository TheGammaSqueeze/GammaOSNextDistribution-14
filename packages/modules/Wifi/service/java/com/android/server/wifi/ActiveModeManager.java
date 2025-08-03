/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.server.wifi;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.WorkSource;

import java.io.FileDescriptor;
import java.io.PrintWriter;

/**
 * Base class for available WiFi operating modes.
 *
 * Currently supported modes include Client, ScanOnly and SoftAp.
 */
public interface ActiveModeManager {
    /**
     * Listener for ActiveModeManager state changes.
     * @param <T> type of ActiveModeManager that is being listened
     */
    interface Listener<T extends ActiveModeManager> {
        /**
         * Invoked when mode manager completes start.
         */
        void onStarted(@NonNull T activeModeManager);
        /**
         * Invoked when mode manager completes stop.
         */
        void onStopped(@NonNull T activeModeManager);
        /**
         * Invoked when mode manager completes a role switch.
         */
        void onRoleChanged(@NonNull T activeModeManager);
        /**
         * Invoked when mode manager encountered a failure on start or on mode switch.
         */
        void onStartFailure(@NonNull T activeModeManager);
    }

    /**
     * Method used to stop the Manager for a given Wifi operational mode.
     */
    void stop();

    // Hierarchy of roles - note that currently, the roles form a tree: no role has more than 1
    // parent. However, since interfaces support multiple inheritance, a role could have more than 1
    // parent if needed.

    /** Roles assigned to each mode manager. */
    interface Role {}

    /** SoftAp roles */
    interface SoftApRole extends Role {}
    /** SoftApManager - Tethering, will respond to public APIs. */
    SoftApRole ROLE_SOFTAP_TETHERED = new SoftApRole() {
        @Override
        public String toString() {
            return "ROLE_SOFTAP_TETHERED";
        }
    };
    /** SoftApManager - Local only hotspot. */
    SoftApRole ROLE_SOFTAP_LOCAL_ONLY = new SoftApRole() {
        @Override
        public String toString() {
            return "ROLE_SOFTAP_LOCAL_ONLY";
        }
    };

    /** Client roles */
    interface ClientRole extends Role {}
    /** ClientModeManager, STA created for scans only. */
    ClientRole ROLE_CLIENT_SCAN_ONLY = new ClientRole() {
        @Override
        public String toString() {
            return "ROLE_CLIENT_SCAN_ONLY";
        }
    };

    /** Client roles that could initiate a wifi connection */
    interface ClientConnectivityRole extends ClientRole {}
    /**
     * ClientModeManager, secondary STA used for make before break, can switch to primary later.
     * Note: ClientModeManagers in this role will call {@link #stop()} upon disconnecting from Wifi.
     */
    ClientConnectivityRole ROLE_CLIENT_SECONDARY_TRANSIENT = new ClientConnectivityRole() {
        @Override
        public String toString() {
            return "ROLE_CLIENT_SECONDARY_TRANSIENT";
        }
    };
    /** ClientModeManager, secondary STA created for local connection (no internet connectivity). */
    ClientConnectivityRole ROLE_CLIENT_LOCAL_ONLY = new ClientConnectivityRole() {
        @Override
        public String toString() {
            return "ROLE_CLIENT_LOCAL_ONLY";
        }
    };

    /** Long running Client roles that could initiate a wifi connection for internet connectivity */
    interface ClientInternetConnectivityRole extends ClientConnectivityRole {}
    /**
     * ClientModeManager, primary STA, will respond to public WifiManager APIs
     * Note: Primary STA can be used to satisfy any of the other client roles whenever it is not
     * possible to create a concurrent ClientModeManager for the specified role. This is only true
     * for primary role. ClientModeManager in any of the other roles are dedicated to the
     * corresponding role.
     */
    ClientInternetConnectivityRole ROLE_CLIENT_PRIMARY =
            new ClientInternetConnectivityRole() {
                @Override
                public String toString() {
                    return "ROLE_CLIENT_PRIMARY";
                }
            };
    /**
     * ClientModeManager, secondary STA used for duplication/bonding use cases, will not respond to
     * public WifiManager APIs.
     *
     * Note: ClientModeManagers in this role will call {@link #stop()} upon disconnecting from Wifi.
     */
    ClientInternetConnectivityRole ROLE_CLIENT_SECONDARY_LONG_LIVED =
            new ClientInternetConnectivityRole() {
                @Override
                public String toString() {
                    return "ROLE_CLIENT_SECONDARY_LONG_LIVED";
                }
            };

    /**
     * Method to get the role for a mode manager.
     */
    @Nullable Role getRole();

    /**
     * Method to get the previous role a mode manager.
     */
    @Nullable Role getPreviousRole();

    /**
     * Get the time in ms since boot of the last role change.
     */
    long getLastRoleChangeSinceBootMs();

    /**
     * Method to get the iface name for the mode manager.
     */
    String getInterfaceName();

    /**
     * Method to retrieve the original requestorWs
     */
    WorkSource getRequestorWs();

    /**
     * Method to dump for logging state.
     */
    void dump(FileDescriptor fd, PrintWriter pw, String[] args);

    /**
     * Method to enable verbose logging.
     */
    void enableVerboseLogging(boolean verbose);

    /** Unique ID for this ActiveModeManager instance, used for debugging. */
    long getId();
}
