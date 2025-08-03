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

import static com.android.net.module.util.NetworkStackConstants.DHCP6_OPTION_IAPREFIX;

import android.net.IpPrefix;
import android.util.Log;

import com.android.net.module.util.Struct;
import com.android.net.module.util.Struct.Computed;
import com.android.net.module.util.Struct.Field;
import com.android.net.module.util.Struct.Type;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * DHCPv6 IA Prefix Option.
 * https://tools.ietf.org/html/rfc8415. This does not contain any option.
 *
 * 0                   1                   2                   3
 * 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |        OPTION_IAPREFIX        |           option-len          |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                      preferred-lifetime                       |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                        valid-lifetime                         |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * | prefix-length |                                               |
 * +-+-+-+-+-+-+-+-+          IPv6-prefix                          |
 * |                           (16 octets)                         |
 * |                                                               |
 * |                                                               |
 * |                                                               |
 * |               +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |               |                                               .
 * +-+-+-+-+-+-+-+-+                                               .
 * .                       IAprefix-options                        .
 * .                                                               .
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 */
public class IaPrefixOption extends Struct {
    private static final String TAG = IaPrefixOption.class.getSimpleName();
    public static final int LENGTH = 25; // option length excluding IAprefix-options

    @Field(order = 0, type = Type.S16)
    public final short code;
    @Field(order = 1, type = Type.S16)
    public final short length;
    @Field(order = 2, type = Type.U32)
    public final long preferred;
    @Field(order = 3, type = Type.U32)
    public final long valid;
    @Field(order = 4, type = Type.S8)
    public final byte prefixLen;
    @Field(order = 5, type = Type.ByteArray, arraysize = 16)
    public final byte[] prefix;

    @Computed
    private final IpPrefix mIpPrefix;

    // Constructor used by Struct.parse()
    protected IaPrefixOption(final short code, final short length, final long preferred,
            final long valid, final byte prefixLen, final byte[] prefix) {
        this.code = code;
        this.length = length;
        this.preferred = preferred;
        this.valid = valid;
        this.prefixLen = prefixLen;
        this.prefix = prefix.clone();

        try {
            final Inet6Address addr = (Inet6Address) InetAddress.getByAddress(prefix);
            mIpPrefix = new IpPrefix(addr, prefixLen);
        } catch (UnknownHostException | ClassCastException e) {
            // UnknownHostException should never happen unless prefix is null.
            // ClassCastException can occur when prefix is an IPv6 mapped IPv4 address.
            // Both scenarios should throw an exception in the context of Struct#parse().
            throw new IllegalArgumentException(e);
        }
    }

    public IaPrefixOption(final short length, final long preferred, final long valid,
            final byte prefixLen, final byte[] prefix) {
        this((byte) DHCP6_OPTION_IAPREFIX, length, preferred, valid, prefixLen, prefix);
    }

    /**
     * Check whether or not IA Prefix option in IA_PD option is valid per RFC8415#section-21.22.
     *
     * Note: an expired prefix can still be valid.
     */
    public boolean isValid() {
        if (preferred < 0) {
            Log.w(TAG, "Invalid preferred lifetime: " + this);
            return false;
        }
        if (valid < 0) {
            Log.w(TAG, "Invalid valid lifetime: " + this);
            return false;
        }
        if (preferred > valid) {
            Log.w(TAG, "Invalid lifetime. Preferred lifetime > valid lifetime: " + this);
            return false;
        }
        if (prefixLen > 64) {
            Log.w(TAG, "Invalid prefix length: " + this);
            return false;
        }
        return true;
    }

    public IpPrefix getIpPrefix() {
        return mIpPrefix;
    }

    /**
     * Check whether or not IA Prefix option has 0 preferred and valid lifetimes.
     */
    public boolean withZeroLifetimes() {
        return preferred == 0 && valid == 0;
    }

    /**
     * Build an IA_PD prefix option with given specific parameters.
     */
    public static ByteBuffer build(final short length, final long preferred, final long valid,
            final byte prefixLen, final byte[] prefix) {
        final IaPrefixOption option = new IaPrefixOption(
                length /* 25 + IAPrefix options length */, preferred, valid, prefixLen, prefix);
        return ByteBuffer.wrap(option.writeToBytes(ByteOrder.BIG_ENDIAN));
    }

    @Override
    public String toString() {
        return "IA Prefix, length " + length + ": " + mIpPrefix + ", pref " + preferred + ", valid "
                + valid;
    }
}
