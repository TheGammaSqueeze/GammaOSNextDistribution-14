/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.net.shared;

import static android.net.ConnectivitySettingsManager.PRIVATE_DNS_MODE_OFF;
import static android.net.ConnectivitySettingsManager.PRIVATE_DNS_MODE_OPPORTUNISTIC;
import static android.net.ConnectivitySettingsManager.PRIVATE_DNS_MODE_PROVIDER_HOSTNAME;
import static android.net.shared.ParcelableUtil.fromParcelableArray;
import static android.net.shared.ParcelableUtil.toParcelableArray;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.PrivateDnsConfigParcel;
import android.text.TextUtils;

import java.net.InetAddress;
import java.util.Arrays;

/** @hide */
public class PrivateDnsConfig {
    // These fields store the private DNS configuration from setting.
    public final int mode;
    @NonNull
    public final String hostname;

    // Stores the DoT server IP addresses resolved from A/AAAA lookups.
    @NonNull
    public final InetAddress[] ips;

    // These fields store the DoH information discovered from SVCB lookups.
    @NonNull
    public final String dohName;
    @NonNull
    public final InetAddress[] dohIps;
    @NonNull
    public final String dohPath;
    public final int dohPort;

    /**
     * A constructor for off mode private DNS configuration.
     * TODO(b/261404136): Consider simplifying the constructors. One possible way is to
     * use constants to represent private DNS modes:
     *   public static PrivateDnsConfig OFF = new PrivateDnsConfig(false);
     *   public static PrivateDnsConfig OPPORTUNISTIC = new PrivateDnsConfig(true);
     *   public static PrivateDnsConfig STRICT = new PrivateDnsConfig(String hostname);
     */
    public PrivateDnsConfig() {
        this(false);
    }

    /**
     * A constructor for off/opportunistic mode private DNS configuration depending on `useTls`.
     */
    public PrivateDnsConfig(boolean useTls) {
        this(useTls ? PRIVATE_DNS_MODE_OPPORTUNISTIC : PRIVATE_DNS_MODE_OFF, null /* hostname */,
                null /* ips */, null /* dohName */, null /* dohIps */, null /* dohPath */,
                -1 /* dohPort */);
    }

    /**
     * A constructor for off/strict mode private DNS configuration depending on `hostname`.
     * If `hostname` is empty or null, this constructor creates a PrivateDnsConfig for off mode;
     * otherwise, it creates a PrivateDnsConfig for strict mode.
     */
    public PrivateDnsConfig(@Nullable String hostname, @Nullable InetAddress[] ips) {
        this(TextUtils.isEmpty(hostname) ? PRIVATE_DNS_MODE_OFF :
                PRIVATE_DNS_MODE_PROVIDER_HOSTNAME, hostname, ips, null /* dohName */,
                null /* dohIps */, null /* dohPath */, -1 /* dohPort */);
    }

    /**
     * A constructor for all kinds of private DNS configuration with given DoH information.
     * It treats both null values and empty strings as equivalent. Similarly, treats null values
     * and empty arrays as equivalent.
     */
    public PrivateDnsConfig(int mode, @Nullable String hostname, @Nullable InetAddress[] ips,
            @Nullable String dohName, @Nullable InetAddress[] dohIps, @Nullable String dohPath,
            int dohPort) {
        this.mode = mode;
        this.hostname = (hostname != null) ? hostname : "";
        this.ips = (ips != null) ? ips.clone() : new InetAddress[0];
        this.dohName = (dohName != null) ? dohName : "";
        this.dohIps = (dohIps != null) ? dohIps.clone() : new InetAddress[0];
        this.dohPath = (dohPath != null) ? dohPath : "";
        this.dohPort = dohPort;
    }

    public PrivateDnsConfig(PrivateDnsConfig cfg) {
        mode = cfg.mode;
        hostname = cfg.hostname;
        ips = cfg.ips;
        dohName = cfg.dohName;
        dohIps = cfg.dohIps;
        dohPath = cfg.dohPath;
        dohPort = cfg.dohPort;
    }

    /**
     * Indicates whether this is a strict mode private DNS configuration.
     */
    public boolean inStrictMode() {
        return mode == PRIVATE_DNS_MODE_PROVIDER_HOSTNAME;
    }

    /**
     * Indicates whether this is an opportunistic mode private DNS configuration.
     */
    public boolean inOpportunisticMode() {
        return mode == PRIVATE_DNS_MODE_OPPORTUNISTIC;
    }

    @Override
    public String toString() {
        return PrivateDnsConfig.class.getSimpleName()
                + "{" + modeAsString(mode) + ":" + hostname + "/" + Arrays.toString(ips)
                + ", dohName=" + dohName
                + ", dohIps=" + Arrays.toString(dohIps)
                + ", dohPath=" + dohPath
                + ", dohPort=" + dohPort
                + "}";
    }

    @NonNull
    private static String modeAsString(int mode) {
        switch (mode) {
            case PRIVATE_DNS_MODE_OFF: return "off";
            case PRIVATE_DNS_MODE_OPPORTUNISTIC: return "opportunistic";
            case PRIVATE_DNS_MODE_PROVIDER_HOSTNAME: return "strict";
            default: return "unknown";
        }
    }

    /**
     * Create a stable AIDL-compatible parcel from the current instance.
     */
    public PrivateDnsConfigParcel toParcel() {
        final PrivateDnsConfigParcel parcel = new PrivateDnsConfigParcel();
        parcel.hostname = hostname;
        parcel.ips = toParcelableArray(
                Arrays.asList(ips), IpConfigurationParcelableUtil::parcelAddress, String.class);
        parcel.privateDnsMode = mode;
        parcel.dohName = dohName;
        parcel.dohIps = toParcelableArray(
                Arrays.asList(dohIps), IpConfigurationParcelableUtil::parcelAddress, String.class);
        parcel.dohPath = dohPath;
        parcel.dohPort = dohPort;
        return parcel;
    }

    /**
     * Build a configuration from a stable AIDL-compatible parcel.
     */
    public static PrivateDnsConfig fromParcel(PrivateDnsConfigParcel parcel) {
        InetAddress[] ips = new InetAddress[parcel.ips.length];
        ips = fromParcelableArray(parcel.ips, IpConfigurationParcelableUtil::unparcelAddress)
                .toArray(ips);

        // For compatibility. If the sender (Tethering module) is using an old version (< 19) of
        // NetworkStack AIDL that `privateDnsMode` field is not present, `privateDnsMode` will be
        // assigned from the default value -1. Let `privateDnsMode` assigned based on the hostname.
        // In this case, there is a harmless bug that the receiver (NetworkStack module) can't
        // convert the parcel to a PrivateDnsConfig that indicates opportunistic mode.
        // The bug is harmless because 1) the bug exists for years without any problems and
        // 2) NetworkMonitor cares PrivateDnsConfig that indicates strict/off mode only.
        // If the sender is using new version (>=19) while the receiver is using an old version,
        // the above mentioned harmless bug will persist. Except for that harmless bug, there
        // should be no other issues. New version's toParcel() doesn't change how the pre-existing
        // fields `hostname` and `ips` are assigned.
        if (parcel.privateDnsMode == -1) {
            return new PrivateDnsConfig(parcel.hostname, ips);
        }

        InetAddress[] dohIps = new InetAddress[parcel.dohIps.length];
        dohIps = fromParcelableArray(parcel.dohIps,
                IpConfigurationParcelableUtil::unparcelAddress).toArray(dohIps);
        return new PrivateDnsConfig(parcel.privateDnsMode, parcel.hostname, ips, parcel.dohName,
                dohIps, parcel.dohPath, parcel.dohPort);
    }
}
