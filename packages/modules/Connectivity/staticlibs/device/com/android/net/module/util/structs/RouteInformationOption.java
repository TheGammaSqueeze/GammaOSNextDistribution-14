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

import static com.android.net.module.util.NetworkStackConstants.ICMPV6_ND_OPTION_RIO;

import android.net.IpPrefix;

import androidx.annotation.NonNull;

import com.android.net.module.util.Struct;
import com.android.net.module.util.Struct.Field;
import com.android.net.module.util.Struct.Type;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * ICMPv6 route information option, as per https://tools.ietf.org/html/rfc4191.
 *
 * 0                   1                   2                   3
 * 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |     Type      |    Length     | Prefix Length |Resvd|Prf|Resvd|
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                        Route Lifetime                         |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                   Prefix (Variable Length)                    |
 * .                                                               .
 * .                                                               .
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 */
public class RouteInformationOption extends Struct {
    public enum Preference {
        HIGH((byte) 0x1),
        MEDIUM((byte) 0x0),
        LOW((byte) 0x3),
        RESERVED((byte) 0x2);

        final byte mValue;
        Preference(byte value) {
            this.mValue = value;
        }
    }

    @Field(order = 0, type = Type.S8)
    public final byte type;
    @Field(order = 1, type = Type.S8)
    public final byte length; // Length in 8-byte octets
    @Field(order = 2, type = Type.U8)
    public final short prefixLen;
    @Field(order = 3, type = Type.S8)
    public final byte prf;
    @Field(order = 4, type = Type.U32)
    public final long routeLifetime;
    @Field(order = 5, type = Type.ByteArray, arraysize = 16)
    public final byte[] prefix;

    RouteInformationOption(final byte type, final byte length, final short prefixLen,
            final byte prf, final long routeLifetime, @NonNull final byte[] prefix) {
        this.type = type;
        this.length = length;
        this.prefixLen = prefixLen;
        this.prf = prf;
        this.routeLifetime = routeLifetime;
        this.prefix = prefix;
    }

    /**
     * Build a Route Information option from the required specified parameters.
     */
    public static ByteBuffer build(final IpPrefix prefix, final Preference prf,
            final long routeLifetime) {
        // The prefix field is always assumed to have 16 bytes, but the number of leading
        // bits in this prefix depends on IpPrefix#prefixLength, then we can simply set the
        // option length to 3.
        final RouteInformationOption option = new RouteInformationOption(
                (byte) ICMPV6_ND_OPTION_RIO, (byte) 3 /* option length */,
                (short) prefix.getPrefixLength(), (byte) (prf.mValue << 3), routeLifetime,
                prefix.getRawAddress());
        return ByteBuffer.wrap(option.writeToBytes(ByteOrder.BIG_ENDIAN));
    }
}
