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

package android.net.mdns.aidl;

import android.net.mdns.aidl.DiscoveryInfo;
import android.net.mdns.aidl.GetAddressInfo;
import android.net.mdns.aidl.IMDnsEventListener;
import android.net.mdns.aidl.RegistrationInfo;
import android.net.mdns.aidl.ResolutionInfo;

/** {@hide} */
interface IMDns {
    /**
     * Start the MDNSResponder daemon.
     *
     * @throws ServiceSpecificException with unix errno EALREADY if daemon is already running.
     * @throws UnsupportedOperationException on Android V and after.
     * @deprecated unimplemented on V+.
     */
    void startDaemon();

    /**
     * Stop the MDNSResponder daemon.
     *
     * @throws ServiceSpecificException with unix errno EBUSY if daemon is still in use.
     * @throws UnsupportedOperationException on Android V and after.
     * @deprecated unimplemented on V+.
     */
    void stopDaemon();

    /**
     * Start registering a service.
     * This operation will send a service registration request to MDNSResponder. Register a listener
     * via IMDns#registerEventListener to get the registration result SERVICE_REGISTERED/
     * SERVICE_REGISTRATION_FAILED from callback IMDnsEventListener#onServiceRegistrationStatus.
     *
     * @param info The service information to register.
     *
     * @throws ServiceSpecificException with one of the following error values:
     *         - Unix errno EBUSY if request id is already in use.
     *         - kDNSServiceErr_* list in dns_sd.h if registration fail.
     * @throws UnsupportedOperationException on Android U and after.
     * @deprecated unimplemented on U+.
     */
    void registerService(in RegistrationInfo info);

    /**
     * Start discovering services.
     * This operation will send a request to MDNSResponder to discover services. Register a listener
     * via IMDns#registerEventListener to get the discovery result SERVICE_FOUND/SERVICE_LOST/
     * SERVICE_DISCOVERY_FAILED from callback IMDnsEventListener#onServiceDiscoveryStatus.
     *
     * @param info The service to discover.
     *
     * @throws ServiceSpecificException with one of the following error values:
     *         - Unix errno EBUSY if request id is already in use.
     *         - kDNSServiceErr_* list in dns_sd.h if discovery fail.
     * @throws UnsupportedOperationException on Android U and after.
     * @deprecated unimplemented on U+.
     */
    void discover(in DiscoveryInfo info);

    /**
     * Start resolving the target service.
     * This operation will send a request to MDNSResponder to resolve the target service. Register a
     * listener via IMDns#registerEventListener to get the resolution result SERVICE_RESOLVED/
     * SERVICE_RESOLUTION_FAILED from callback IMDnsEventListener#onServiceResolutionStatus.
     *
     * @param info The service to resolve.
     *
     * @throws ServiceSpecificException with one of the following error values:
     *         - Unix errno EBUSY if request id is already in use.
     *         - kDNSServiceErr_* list in dns_sd.h if resolution fail.
     * @throws UnsupportedOperationException on Android U and after.
     * @deprecated unimplemented on U+.
     */
    void resolve(in ResolutionInfo info);

    /**
     * Start getting the target service address.
     * This operation will send a request to MDNSResponder to get the target service address.
     * Register a listener via IMDns#registerEventListener to get the query result
     * SERVICE_GET_ADDR_SUCCESS/SERVICE_GET_ADDR_FAILED from callback
     * IMDnsEventListener#onGettingServiceAddressStatus.
     *
     * @param info the getting service address information.
     *
     * @throws ServiceSpecificException with one of the following error values:
     *         - Unix errno EBUSY if request id is already in use.
     *         - kDNSServiceErr_* list in dns_sd.h if getting address fail.
     * @throws UnsupportedOperationException on Android U and after.
     * @deprecated unimplemented on U+.
     */
    void getServiceAddress(in GetAddressInfo info);

    /**
     * Stop a operation which's requested before.
     *
     * @param id the operation id to be stopped.
     *
     * @throws ServiceSpecificException with unix errno ESRCH if request id is not in use.
     * @throws UnsupportedOperationException on Android U and after.
     * @deprecated unimplemented on U+.
     */
    void stopOperation(int id);

    /**
     * Register an event listener.
     *
     * @param listener The listener to be registered.
     *
     * @throws ServiceSpecificException with one of the following error values:
     *         - Unix errno EINVAL if listener is null.
     *         - Unix errno EEXIST if register duplicated listener.
     * @throws UnsupportedOperationException on Android U and after.
     * @deprecated unimplemented on U+.
     */
    void registerEventListener(in IMDnsEventListener listener);

    /**
     * Unregister an event listener.
     *
     * @param listener The listener to be unregistered.
     *
     * @throws ServiceSpecificException with unix errno EINVAL if listener is null.
     * @throws UnsupportedOperationException on Android U and after.
     * @deprecated unimplemented on U+.
     */
    void unregisterEventListener(in IMDnsEventListener listener);
}

