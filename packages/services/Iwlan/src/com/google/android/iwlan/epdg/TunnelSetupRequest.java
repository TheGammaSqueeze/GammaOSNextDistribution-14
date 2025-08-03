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

import com.android.internal.annotations.VisibleForTesting;

import com.google.auto.value.AutoValue;

import java.net.InetAddress;
import java.util.Optional;

@AutoValue
public abstract class TunnelSetupRequest {
    abstract String apnName();

    abstract int apnIpProtocol();

    abstract Optional<InetAddress> srcIpv4Address();

    abstract Optional<InetAddress> srcIpv6Address();

    abstract int srcIpv6AddressPrefixLength();

    abstract int pduSessionId();

    abstract boolean isRoaming();

    abstract boolean isEmergency();

    abstract boolean requestPcscf();

    @VisibleForTesting
    public int getPduSessionId() {
        return pduSessionId();
    }

    public static Builder builder() {
        return new AutoValue_TunnelSetupRequest.Builder()
                .setSrcIpv4Address(Optional.empty())
                .setSrcIpv6Address(Optional.empty())
                .setSrcIpv6AddressPrefixLength(0);
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder setApnName(String apnName);

        public abstract Builder setApnIpProtocol(int protocol);

        public Builder setSrcIpv4Address(InetAddress srcIpv4Address) {
            return setSrcIpv4Address(Optional.ofNullable(srcIpv4Address));
        }

        public abstract Builder setSrcIpv4Address(Optional<InetAddress> srcIpv4Address);

        public Builder setSrcIpv6Address(InetAddress srcIpv6Address) {
            return setSrcIpv6Address(Optional.ofNullable(srcIpv6Address));
        }

        public abstract Builder setSrcIpv6Address(Optional<InetAddress> srcIpv6Address);

        public abstract Builder setSrcIpv6AddressPrefixLength(int srcIpv6AddressPrefixLength);

        public abstract Builder setPduSessionId(int pduSessionId);

        public abstract Builder setIsRoaming(boolean isRoaming);

        public abstract Builder setIsEmergency(boolean isEmergency);

        public abstract Builder setRequestPcscf(boolean requestPcscf);

        public abstract TunnelSetupRequest build();
    }
}
