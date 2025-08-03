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

package com.android.networkstack.tethering;

import android.annotation.NonNull;
import android.os.NativeHandle;

import com.android.networkstack.tethering.OffloadHardwareInterface.ForwardedStats;
import com.android.networkstack.tethering.OffloadHardwareInterface.OffloadHalCallback;

import java.util.ArrayList;

/** Abstraction of Tetheroffload HAL interface */
interface IOffloadHal {
    /*
     * Initialize the Tetheroffload HAL. Offload management process need to know conntrack rules to
     * support NAT, but it may not have permission to create netlink netfilter sockets. Create two
     * netlink netfilter sockets and share them with offload management process.
     */
    boolean initOffload(@NonNull NativeHandle handle1, @NonNull NativeHandle handle2,
            @NonNull OffloadHalCallback callback);

    /** Stop the Tetheroffload HAL. */
    boolean stopOffload();

    /** Get HAL interface version number. */
    int getVersion();

    /** Get Tx/Rx usage from last query. */
    ForwardedStats getForwardedStats(@NonNull String upstream);

    /** Set local prefixes to offload management process. */
    boolean setLocalPrefixes(@NonNull ArrayList<String> localPrefixes);

    /** Set data limit value to offload management process. */
    boolean setDataLimit(@NonNull String iface, long limit);

    /** Set data warning and limit value to offload management process. */
    boolean setDataWarningAndLimit(@NonNull String iface, long warning, long limit);

    /** Set upstream parameters to offload management process. */
    boolean setUpstreamParameters(@NonNull String iface, @NonNull String v4addr,
            @NonNull String v4gateway, @NonNull ArrayList<String> v6gws);

    /** Add downstream prefix to offload management process. */
    boolean addDownstream(@NonNull String ifname, @NonNull String prefix);

    /** Remove downstream prefix from offload management process. */
    boolean removeDownstream(@NonNull String ifname, @NonNull String prefix);
}
