/**
 * Copyright (c) 2022, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.net.connectivity.aidl;

interface ConnectivityNative {
    /**
     * Blocks a port from being assigned during bind(). The caller is responsible for updating
     * /proc/sys/net/ipv4/ip_local_port_range with the port being blocked so that calls to connect()
     * will not automatically assign one of the blocked ports.
     * Will return success even if port was already blocked.
     *
     * @param port Int corresponding to port number.
     *
     * @throws IllegalArgumentException if the port is invalid.
     * @throws SecurityException if the UID of the client doesn't have network stack permission.
     * @throws ServiceSpecificException in case of failure, with an error code corresponding to the
     *         unix errno.
     */
    void blockPortForBind(in int port);

    /**
     * Unblocks a port that has previously been blocked.
     * Will return success even if port was already unblocked.
     *
     * @param port Int corresponding to port number.
     *
     * @throws IllegalArgumentException if the port is invalid.
     * @throws SecurityException if the UID of the client doesn't have network stack permission.
     * @throws ServiceSpecificException in case of failure, with an error code corresponding to the
     *         unix errno.
     */
    void unblockPortForBind(in int port);

    /**
     * Unblocks all ports that have previously been blocked.
     */
    void unblockAllPortsForBind();

    /**
     * Gets the list of ports that have been blocked.
     *
     * @return List of blocked ports.
     */
    int[] getPortsBlockedForBind();
}