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

import java.net.InetAddress;

/**
 * Struct xfrm_id
 *
 * <p>see include/uapi/linux/xfrm.h
 *
 * <pre>
 * struct xfrm_id {
 *      xfrm_address_t daddr;
 *      __be32 spi;
 *      __u8 proto;
 * };
 * </pre>
 *
 * @hide
 */
public class StructXfrmId extends Struct {
    public static final int STRUCT_SIZE = 24;

    @Field(order = 0, type = Type.ByteArray, arraysize = 16)
    public final byte[] nestedStructDAddr;

    @Field(order = 1, type = Type.UBE32)
    public final long spi;

    @Field(order = 2, type = Type.U8, padding = 3)
    public final short proto;

    @Computed private final StructXfrmAddressT mDestXfrmAddressT;

    // Constructor that allows Strutc.parse(Class<T>, ByteBuffer) to work
    public StructXfrmId(@NonNull final byte[] nestedStructDAddr, long spi, short proto) {
        this.nestedStructDAddr = nestedStructDAddr.clone();
        this.spi = spi;
        this.proto = proto;

        mDestXfrmAddressT = new StructXfrmAddressT(this.nestedStructDAddr);
    }

    // Constructor to build a new message
    public StructXfrmId(@NonNull final InetAddress destAddress, long spi, short proto) {
        this(new StructXfrmAddressT(destAddress).writeToBytes(), spi, proto);
    }

    /** Return the destination address */
    public InetAddress getDestAddress(int family) {
        return mDestXfrmAddressT.getAddress(family);
    }
}
