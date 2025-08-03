/*
 * Copyright 2020 The Android Open Source Project
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

package com.google.android.iwlan.epdg;

import android.net.LinkAddress;
import android.telephony.data.ApnSetting;
import android.telephony.data.NetworkSliceInfo;
import android.util.Log;

import com.google.auto.value.AutoValue;

import java.net.InetAddress;
import java.util.List;
import java.util.Optional;

@AutoValue
public abstract class TunnelLinkProperties {
    private static final String TAG = TunnelLinkProperties.class.getSimpleName();

    public abstract List<LinkAddress> internalAddresses();

    public abstract List<InetAddress> dnsAddresses();

    public abstract List<InetAddress> pcscfAddresses();

    public abstract String ifaceName();

    public abstract Optional<NetworkSliceInfo> sliceInfo();

    public int getProtocolType() {
        int protocolType = ApnSetting.PROTOCOL_UNKNOWN;
        if (!internalAddresses().isEmpty()) {
            boolean hasIpv4 = internalAddresses().stream().anyMatch(LinkAddress::isIpv4);
            boolean hasIpv6 = internalAddresses().stream().anyMatch(LinkAddress::isIpv6);

            if (hasIpv4 && hasIpv6) {
                protocolType = ApnSetting.PROTOCOL_IPV4V6;
            } else if (hasIpv4) {
                protocolType = ApnSetting.PROTOCOL_IP;
            } else if (hasIpv6) {
                protocolType = ApnSetting.PROTOCOL_IPV6;
            } else {
                Log.w(
                        TAG,
                        "Internal Addresses do not contain IPv4 or IPv6 addresses, set protocol"
                                + " type as: "
                                + protocolType);
            }
        } else {
            Log.w(TAG, "Internal Addresses is empty, set protocol type as: " + protocolType);
        }
        return protocolType;
    }

    public static Builder builder() {
        return new AutoValue_TunnelLinkProperties.Builder().setSliceInfo(Optional.empty());
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder setInternalAddresses(List<LinkAddress> internalAddresses);

        public abstract Builder setDnsAddresses(List<InetAddress> dnsAddresses);

        public abstract Builder setPcscfAddresses(List<InetAddress> pcscfAddresses);

        public abstract Builder setIfaceName(String ifaceName);

        public Builder setSliceInfo(NetworkSliceInfo si) {
            return setSliceInfo(Optional.ofNullable(si));
        }

        public abstract Builder setSliceInfo(Optional<NetworkSliceInfo> si);

        public abstract TunnelLinkProperties build();
    }
}
