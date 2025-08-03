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

package com.android.networkstack.apishim.common;

import android.net.IpPrefix;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.net.Inet4Address;
import java.util.List;

/**
 * Compatibility interface for network info classes such as {@link LinkProperties} and
 * {@link NetworkCapabilities}.
 */
public interface NetworkInformationShim {
    /**
     * @see LinkProperties#getCaptivePortalApiUrl()
     */
    @Nullable
    Uri getCaptivePortalApiUrl(@Nullable LinkProperties lp);

    /**
     * @see LinkProperties#setCaptivePortalApiUrl(Uri)
     */
    void setCaptivePortalApiUrl(@NonNull LinkProperties lp, @Nullable Uri url);

    /**
     * @see LinkProperties#getCaptivePortalData()
     */
    @Nullable
    CaptivePortalDataShim getCaptivePortalData(@Nullable LinkProperties lp);

    /**
     * @see LinkProperties#getNat64Prefix()
     */
    @Nullable
    IpPrefix getNat64Prefix(@NonNull LinkProperties lp);

    /**
     * @see LinkProperties#setNat64Prefix()
     */
    void setNat64Prefix(@NonNull LinkProperties lp, @Nullable IpPrefix prefix);

    /**
     * @see NetworkCapabilities#getSSID()
     */
    @Nullable
    String getSsid(@Nullable NetworkCapabilities nc);

    /**
     * @see LinkProperties#LinkProperties(LinkProperties, boolean)
     */
    @NonNull
    LinkProperties makeSensitiveFieldsParcelingCopy(@NonNull LinkProperties lp);

    /**
     * @see LinkProperties#setDhcpServerAddress()
     */
    void setDhcpServerAddress(@NonNull LinkProperties lp, @NonNull Inet4Address serverAddress);

    /**
     * Set captive portal data in {@link LinkProperties}
     * @param lp Link properties object to be updated
     * @param captivePortalData Captive portal data to be used
     */
    void setCaptivePortalData(@NonNull LinkProperties lp,
            @Nullable CaptivePortalDataShim captivePortalData);

    /**
     * Get the name of the given capability that carriers use.
     * If the capability does not have a carrier-name, returns null.
     *
     * @param capability The capability to get the carrier-name of.
     * @return The carrier-name of the capability, or null if it doesn't exist.
     * @hide
     */
    @Nullable
    default String getCapabilityCarrierName(int capability) {
        return null;
    }

    /**
     * @see NetworkCapabilities#getUnderlyingNetworks()
     */
    @Nullable
    default List<Network> getUnderlyingNetworks(@NonNull NetworkCapabilities nc) {
        return null;
    }
}
