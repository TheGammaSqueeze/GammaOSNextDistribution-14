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

package android.net;

import android.net.NativeNetworkType;
import android.net.NativeVpnType;

/**
 * The configuration to create a network.
 *
 * {@hide}
 */
@JavaDerive(toString=true, equals=true)
@JavaOnlyImmutable
parcelable NativeNetworkConfig {
    /** The networkId to create. */
    int netId;

    /**
     * The type of network : virtual, physical or physical local network.
     */
    NativeNetworkType networkType = NativeNetworkType.PHYSICAL;

    /**
     * The permission necessary to use the network. Must be PERMISSION_NONE, PERMISSION_NETWORK
     * or PERMISSION_SYSTEM. Ignored for virtual network types.
     */
    int permission;

    /**
     *  For virtual networks. Whether unprivileged apps are allowed to bypass the VPN. Ignored for
     *  all other network types.
     */
    boolean secure;

    /** For virtual networks. The type of VPN to create.  Ignored for all other network types. */
    NativeVpnType vpnType = NativeVpnType.PLATFORM;

    /**
     * For virtual networks. Whether local traffic is excluded from the VPN.
     */
    boolean excludeLocalRoutes = false;
}
