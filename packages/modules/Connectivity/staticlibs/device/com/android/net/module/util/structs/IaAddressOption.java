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

import static com.android.net.module.util.NetworkStackConstants.DHCP6_OPTION_IA_ADDR;

import com.android.net.module.util.Struct;
import com.android.net.module.util.Struct.Field;
import com.android.net.module.util.Struct.Type;

import java.net.Inet6Address;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * DHCPv6 IA Address option.
 * https://tools.ietf.org/html/rfc8415. This does not contain any option.
 *
 * 0                   1                   2                   3
 * 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |          OPTION_IAADDR        |          option-len           |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                                                               |
 * |                         IPv6-address                          |
 * |                                                               |
 * |                                                               |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                      preferred-lifetime                       |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                        valid-lifetime                         |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * .                                                               .
 * .                        IAaddr-options                         .
 * .                                                               .
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 */
public class IaAddressOption extends Struct {
    public static final int LENGTH = 24; // option length excluding IAaddr-options

    @Field(order = 0, type = Type.S16)
    public final short code;
    @Field(order = 1, type = Type.S16)
    public final short length;
    @Field(order = 2, type = Type.Ipv6Address)
    public final Inet6Address address;
    @Field(order = 3, type = Type.U32)
    public final long preferred;
    @Field(order = 4, type = Type.U32)
    public final long valid;

    IaAddressOption(final short code, final short length, final Inet6Address address,
            final long preferred, final long valid) {
        this.code = code;
        this.length = length;
        this.address = address;
        this.preferred = preferred;
        this.valid = valid;
    }

    /**
     * Build an IA Address option from the required specific parameters.
     */
    public static ByteBuffer build(final short length, final long id, final Inet6Address address,
            final long preferred, final long valid) {
        final IaAddressOption option = new IaAddressOption((short) DHCP6_OPTION_IA_ADDR,
                length /* 24 + IAaddr-options length */, address, preferred, valid);
        return ByteBuffer.wrap(option.writeToBytes(ByteOrder.BIG_ENDIAN));
    }
}
