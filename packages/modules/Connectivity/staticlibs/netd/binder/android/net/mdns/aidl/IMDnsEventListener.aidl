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

package android.net.mdns.aidl;

import android.net.mdns.aidl.DiscoveryInfo;
import android.net.mdns.aidl.GetAddressInfo;
import android.net.mdns.aidl.RegistrationInfo;
import android.net.mdns.aidl.ResolutionInfo;

/**
 * MDNS events which are reported by the MDNSResponder.
 * This one-way interface defines the asynchronous notifications sent by mdns service to any process
 * that registered itself via IMDns.registerEventListener.
 *
 * {@hide}
 */
oneway interface IMDnsEventListener {
    /**
     * Types for MDNS operation result.
     * These are in sync with packages/modules/Connectivity/staticlibs/netd/libnetdutils/include/\
     * netdutils/ResponseCode.h
     */
    const int SERVICE_DISCOVERY_FAILED     = 602;
    const int SERVICE_FOUND                = 603;
    const int SERVICE_LOST                 = 604;
    const int SERVICE_REGISTRATION_FAILED  = 605;
    const int SERVICE_REGISTERED           = 606;
    const int SERVICE_RESOLUTION_FAILED    = 607;
    const int SERVICE_RESOLVED             = 608;
    const int SERVICE_GET_ADDR_FAILED      = 611;
    const int SERVICE_GET_ADDR_SUCCESS     = 612;

    /**
     * Notify service registration status.
     *
     * @deprecated this is implemented for backward compatibility. Don't use it in new code.
     */
    void onServiceRegistrationStatus(in RegistrationInfo status);

    /**
     * Notify service discovery status.
     *
     * @deprecated this is implemented for backward compatibility. Don't use it in new code.
     */
    void onServiceDiscoveryStatus(in DiscoveryInfo status);

    /**
     * Notify service resolution status.
     *
     * @deprecated this is implemented for backward compatibility. Don't use it in new code.
     */
    void onServiceResolutionStatus(in ResolutionInfo status);

    /**
     * Notify getting service address status.
     *
     * @deprecated this is implemented for backward compatibility. Don't use it in new code.
     */
    void onGettingServiceAddressStatus(in GetAddressInfo status);
}
