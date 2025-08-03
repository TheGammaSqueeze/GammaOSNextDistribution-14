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

package com.android.networkstack.apishim.api29;

import android.os.Build;
import android.provider.Settings;
import android.telephony.CarrierConfigManager;

import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;

/**
 * Utility class for defining and importing constants from the Android platform.
 */
public class ConstantsShim {
    /**
     * Constant that callers can use to determine what version of the shim they are using.
     * Must be the same as the version of the shims.
     * This should only be used by test code. Production code that uses the shims should be using
     * the shimmed objects and methods themselves.
     */
    @VisibleForTesting
    public static final int VERSION = 29;

    // Constants defined in android.net.VpnProfileState
    public static final int VPN_PROFILE_STATE_CONNECTING = 1;
    public static final int VPN_PROFILE_STATE_CONNECTED = 2;

    public static final String ACTION_VPN_MANAGER_EVENT = "android.net.action.VPN_MANAGER_EVENT";

    // Constants defined in android.net.ConnectivityDiagnosticsManager.
    public static final int DETECTION_METHOD_DNS_EVENTS = 1;
    public static final int DETECTION_METHOD_TCP_METRICS = 2;

    // Constants defined in android.net.CaptivePortalData.
    public static final int CAPTIVE_PORTAL_DATA_SOURCE_OTHER = 0;
    public static final int CAPTIVE_PORTAL_DATA_SOURCE_PASSPOINT = 1;

    // Constants defined in android.net.NetworkCapabilities.
    public static final int NET_CAPABILITY_NOT_VCN_MANAGED = 28;

    // Constants defined in android.content.Context
    public static final String NEARBY_SERVICE = "nearby";

    // Constants defined in android.system.OsConstants
    public static final int IFA_F_MANAGETEMPADDR = 0x100;
    public static final int IFA_F_NOPREFIXROUTE = 0x200;

    /** Compatibility class for {@link CarrierConfigManager}. */
    @RequiresApi(Build.VERSION_CODES.Q)
    /** See {@link CarrierManager#KEY_CARRIER_SUPPORTS_TETHERING_BOOL} */
    public static final String KEY_CARRIER_SUPPORTS_TETHERING_BOOL =
            "carrier_supports_tethering_bool";

    /** Compatibility class for {@link Settings}. */
    @RequiresApi(Build.VERSION_CODES.Q)
    /** @see android.provider.Settings#ACTION_TETHER_UNSUPPORTED_CARRIER_UI */
    public static final  String ACTION_TETHER_UNSUPPORTED_CARRIER_UI =
            "android.settings.TETHER_UNSUPPORTED_CARRIER_UI";
}
