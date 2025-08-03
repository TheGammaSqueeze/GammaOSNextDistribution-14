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

/**
 * Struct xfrm_selector
 *
 * <p>see include/uapi/linux/xfrm.h
 *
 * <pre>
 * struct xfrm_selector {
 *      xfrm_address_t daddr;
 *      xfrm_address_t saddr;
 *      __be16 dport;
 *      __be16 dport_mask;
 *      __be16 sport;
 *      __be16 sport_mask;
 *      __u16 family;
 *      __u8 prefixlen_d;
 *      __u8 prefixlen_s;
 *      __u8 proto;
 *      int ifindex;
 *      __kernel_uid32_t user;
 * };
 * </pre>
 *
 * @hide
 */
public class StructXfrmSelector extends Struct {
    public static final int STRUCT_SIZE = 56;

    @Field(order = 0, type = Type.ByteArray, arraysize = 16)
    public final byte[] nestedStructDAddr;

    @Field(order = 1, type = Type.ByteArray, arraysize = 16)
    public final byte[] nestedStructSAddr;

    @Field(order = 2, type = Type.UBE16)
    public final int dPort;

    @Field(order = 3, type = Type.UBE16)
    public final int dPortMask;

    @Field(order = 4, type = Type.UBE16)
    public final int sPort;

    @Field(order = 5, type = Type.UBE16)
    public final int sPortMask;

    @Field(order = 6, type = Type.U16)
    public final int selectorFamily;

    @Field(order = 7, type = Type.U8, padding = 1)
    public final short prefixlenD;

    @Field(order = 8, type = Type.U8, padding = 1)
    public final short prefixlenS;

    @Field(order = 9, type = Type.U8, padding = 1)
    public final short proto;

    @Field(order = 10, type = Type.S32)
    public final int ifIndex;

    @Field(order = 11, type = Type.S32)
    public final int user;

    // Constructor that allows Strutc.parse(Class<T>, ByteBuffer) to work
    public StructXfrmSelector(
            @NonNull final byte[] nestedStructDAddr,
            @NonNull final byte[] nestedStructSAddr,
            int dPort,
            int dPortMask,
            int sPort,
            int sPortMask,
            int selectorFamily,
            short prefixlenD,
            short prefixlenS,
            short proto,
            int ifIndex,
            int user) {
        this.nestedStructDAddr = nestedStructDAddr.clone();
        this.nestedStructSAddr = nestedStructSAddr.clone();
        this.dPort = dPort;
        this.dPortMask = dPortMask;
        this.sPort = sPort;
        this.sPortMask = sPortMask;
        this.selectorFamily = selectorFamily;
        this.prefixlenD = prefixlenD;
        this.prefixlenS = prefixlenS;
        this.proto = proto;
        this.ifIndex = ifIndex;
        this.user = user;
    }

    // Constructor to build a new message
    public StructXfrmSelector(int selectorFamily) {
        this(
                new byte[StructXfrmAddressT.STRUCT_SIZE],
                new byte[StructXfrmAddressT.STRUCT_SIZE],
                0 /* dPort */,
                0 /* dPortMask */,
                0 /* sPort */,
                0 /* sPortMask */,
                selectorFamily,
                (short) 0 /* prefixlenD */,
                (short) 0 /* prefixlenS */,
                (short) 0 /* proto */,
                0 /* ifIndex */,
                0 /* user */);
    }
}
