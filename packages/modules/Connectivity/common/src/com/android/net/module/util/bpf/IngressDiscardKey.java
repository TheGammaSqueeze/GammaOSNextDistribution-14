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

package com.android.net.module.util.bpf;

import com.android.net.module.util.InetAddressUtils;
import com.android.net.module.util.Struct;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;

/** Key type for ingress discard map */
public class IngressDiscardKey extends Struct {
    // The destination ip of the incoming packet. IPv4 uses IPv4-mapped IPv6 address.
    @Field(order = 0, type = Type.Ipv6Address)
    public final Inet6Address dstAddr;

    public IngressDiscardKey(final Inet6Address dstAddr) {
        this.dstAddr = dstAddr;
    }

    private static Inet6Address getInet6Address(final InetAddress addr) {
        return (addr instanceof Inet4Address)
                ? InetAddressUtils.v4MappedV6Address((Inet4Address) addr)
                : (Inet6Address) addr;
    }

    public IngressDiscardKey(final InetAddress dstAddr) {
        this(getInet6Address(dstAddr));
    }
}
