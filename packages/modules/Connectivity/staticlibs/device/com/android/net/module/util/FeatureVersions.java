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

package com.android.net.module.util;

/**
 * Class to centralize feature version control that requires a specific module or a specific
 * module version.
 * @hide
 */
public class FeatureVersions {
    /**
     * This constant is used to do bitwise shift operation to create module ids.
     * The module version is composed with 9 digits which is placed in the lower 36 bits.
     */
    private static final int MODULE_SHIFT = 36;
    /**
     * The bitmask to do bitwise-and(i.e. {@code &}) operation to get the module id.
     */
    public static final long MODULE_MASK = 0xFF0_0000_0000L;
    /**
     * The bitmask to do bitwise-and(i.e. {@code &}) operation to get the module version.
     */
    public static final long VERSION_MASK = 0x00F_FFFF_FFFFL;
    public static final long CONNECTIVITY_MODULE_ID = 0x01L << MODULE_SHIFT;
    public static final long NETWORK_STACK_MODULE_ID = 0x02L << MODULE_SHIFT;
    // CLAT_ADDRESS_TRANSLATE is a feature of the network stack, which doesn't throw when system
    // try to add a NAT-T keepalive packet filter with v6 address, introduced in version
    // M-2023-Sept on July 3rd, 2023.
    public static final long FEATURE_CLAT_ADDRESS_TRANSLATE =
            NETWORK_STACK_MODULE_ID + 34_09_00_000L;

    // IS_UID_NETWORKING_BLOCKED is a feature in ConnectivityManager,
    // which provides an API to access BPF maps to check whether the networking is blocked
    // by BPF for the given uid and conditions, introduced in version M-2024-Feb on Nov 6, 2023.
    public static final long FEATURE_IS_UID_NETWORKING_BLOCKED =
            CONNECTIVITY_MODULE_ID + 34_14_00_000L;
}
