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

package com.android.net.module.util.structs;

import com.android.net.module.util.Struct;
import com.android.net.module.util.Struct.Field;
import com.android.net.module.util.Struct.Type;

import java.net.Inet6Address;

/**
 * structure in6_pktinfo
 *
 * see also:
 *
 *     include/uapi/linux/ipv6.h
 */
public class Ipv6PktInfo extends Struct {
    @Field(order = 0, type = Type.Ipv6Address)
    public final Inet6Address addr; // IPv6 source or destination address
    @Field(order = 1, type = Type.S32)
    public final int ifindex;       // interface index

    public Ipv6PktInfo(final Inet6Address addr, final int ifindex) {
        this.addr = addr;
        this.ifindex = ifindex;
    }
}
