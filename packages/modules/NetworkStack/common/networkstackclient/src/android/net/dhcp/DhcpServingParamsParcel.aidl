/**
 *
 * Copyright (C) 2018 The Android Open Source Project
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

package android.net.dhcp;

@JavaDerive(toString=true)
parcelable DhcpServingParamsParcel {
    int serverAddr;
    int serverAddrPrefixLength;
    int[] defaultRouters;
    int[] dnsServers;
    int[] excludedAddrs;
    long dhcpLeaseTimeSecs;
    int linkMtu;
    boolean metered;
    /** IP address to assign to a single client. 0.0.0.0 means support multiple clients.. */
    int singleClientAddr = 0;
    boolean changePrefixOnDecline = false;
    /**
     * This is used to restrict the range of addresses offered by the server to a subset of its
     * prefix. The acceptable value is  >= serverAddrPrefixLength and <= 30, "0" means ignore this
     * configuration in which case serverAddrPrefixLength is used instead.
     */
    int leasesSubnetPrefixLength = 0;
}
