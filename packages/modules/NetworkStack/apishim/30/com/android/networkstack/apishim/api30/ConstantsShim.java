/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.networkstack.apishim.api30;

import static android.net.ConnectivityDiagnosticsManager.DataStallReport;

import androidx.annotation.VisibleForTesting;

/**
 * Utility class for defining and importing constants from the Android platform.
 */
public class ConstantsShim extends com.android.networkstack.apishim.api29.ConstantsShim {
    /**
     * Constant that callers can use to determine what version of the shim they are using.
     * Must be the same as the version of the shims.
     * This should only be used by test code. Production code that uses the shims should be using
     * the shimmed objects and methods themselves.
     */
    @VisibleForTesting
    public static final int VERSION = 30;

    public static final int DETECTION_METHOD_DNS_EVENTS =
            DataStallReport.DETECTION_METHOD_DNS_EVENTS;
    public static final int DETECTION_METHOD_TCP_METRICS =
            DataStallReport.DETECTION_METHOD_TCP_METRICS;

    // Constants defined in android.net.ConnectivityManager.
    public static final int BLOCKED_REASON_NONE = 0;
    public static final int BLOCKED_REASON_LOCKDOWN_VPN = 16;

    // Constants defined in android.net.NetworkCapabilities.
    public static final int NET_CAPABILITY_NOT_VCN_MANAGED = 28;
    public static final int NET_CAPABILITY_ENTERPRISE = 29;
    public static final int TRANSPORT_TEST = 7;

    // Constants defined in android.content.pm.PackageManager
    public static final String PROPERTY_SELF_CERTIFIED_NETWORK_CAPABILITIES =
            "android.net.PROPERTY_SELF_CERTIFIED_NETWORK_CAPABILITIES";
}
