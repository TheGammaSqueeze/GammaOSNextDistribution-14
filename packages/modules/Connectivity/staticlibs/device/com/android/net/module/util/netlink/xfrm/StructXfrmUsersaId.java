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

package com.android.net.module.util.netlink.xfrm;

import androidx.annotation.NonNull;

import com.android.net.module.util.Struct;
import com.android.net.module.util.Struct.Field;
import com.android.net.module.util.Struct.Type;

import java.net.InetAddress;

/**
 * Struct xfrm_usersa_id
 *
 * <p>see include/uapi/linux/xfrm.h
 *
 * <pre>
 * struct xfrm_usersa_id {
 *      xfrm_address_t      daddr;
 *      __be32              spi;
 *      __u16               family;
 *      __u8                proto;
 * };
 * </pre>
 *
 * @hide
 */
public class StructXfrmUsersaId extends Struct {
    public static final int STRUCT_SIZE = 24;

    @Field(order = 0, type = Type.ByteArray, arraysize = 16)
    public final byte[] nestedStructDAddr; // xfrm_address_t

    @Field(order = 1, type = Type.UBE32)
    public final long spi;

    @Field(order = 2, type = Type.U16)
    public final int family;

    @Field(order = 3, type = Type.U8, padding = 1)
    public final short proto;

    @Computed private final StructXfrmAddressT mDestXfrmAddressT;

    // Constructor that allows Strutc.parse(Class<T>, ByteBuffer) to work
    public StructXfrmUsersaId(
            @NonNull final byte[] nestedStructDAddr, long spi, int family, short proto) {
        this.nestedStructDAddr = nestedStructDAddr.clone();
        this.spi = spi;
        this.family = family;
        this.proto = proto;

        mDestXfrmAddressT = new StructXfrmAddressT(this.nestedStructDAddr);
    }

    // Constructor to build a new message
    public StructXfrmUsersaId(
            @NonNull final InetAddress destAddress, long spi, int family, short proto) {
        this(new StructXfrmAddressT(destAddress).writeToBytes(), spi, family, proto);
    }

    /** Return the destination address */
    public InetAddress getDestAddress() {
        return mDestXfrmAddressT.getAddress(family);
    }
}
