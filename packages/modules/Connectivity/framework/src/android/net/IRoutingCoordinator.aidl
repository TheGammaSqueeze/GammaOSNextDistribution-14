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

package android.net;

import android.net.RouteInfo;

/** @hide */
interface IRoutingCoordinator {
   /**
    * Add a route for specific network
    *
    * @param netId the network to add the route to
    * @param route the route to add
    * @throws ServiceSpecificException in case of failure, with an error code indicating the
    *         cause of the failure.
    */
    void addRoute(int netId, in RouteInfo route);

   /**
    * Remove a route for specific network
    *
    * @param netId the network to remove the route from
    * @param route the route to remove
    * @throws ServiceSpecificException in case of failure, with an error code indicating the
    *         cause of the failure.
    */
    void removeRoute(int netId, in RouteInfo route);

    /**
    * Update a route for specific network
    *
    * @param netId the network to update the route for
    * @param route parcelable with route information
    * @throws ServiceSpecificException in case of failure, with an error code indicating the
    *         cause of the failure.
    */
    void updateRoute(int netId, in RouteInfo route);

    /**
     * Adds an interface to a network. The interface must not be assigned to any network, including
     * the specified network.
     *
     * @param netId the network to add the interface to.
     * @param iface the name of the interface to add.
     *
     * @throws ServiceSpecificException in case of failure, with an error code corresponding to the
     *         unix errno.
     */
    void addInterfaceToNetwork(int netId, in String iface);

    /**
     * Removes an interface from a network. The interface must be assigned to the specified network.
     *
     * @param netId the network to remove the interface from.
     * @param iface the name of the interface to remove.
     *
     * @throws ServiceSpecificException in case of failure, with an error code corresponding to the
     *         unix errno.
     */
     void removeInterfaceFromNetwork(int netId, in String iface);

   /**
    * Add forwarding ip rule
    *
    * @param fromIface interface name to add forwarding ip rule
    * @param toIface interface name to add forwarding ip rule
    * @throws ServiceSpecificException in case of failure, with an error code indicating the
    *         cause of the failure.
    */
    void addInterfaceForward(in String fromIface, in String toIface);

   /**
    * Remove forwarding ip rule
    *
    * @param fromIface interface name to remove forwarding ip rule
    * @param toIface interface name to remove forwarding ip rule
    * @throws ServiceSpecificException in case of failure, with an error code indicating the
    *         cause of the failure.
    */
    void removeInterfaceForward(in String fromIface, in String toIface);
}
