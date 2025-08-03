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

import static com.android.net.module.util.netlink.xfrm.XfrmNetlinkMessage.XFRM_INF;

import androidx.annotation.NonNull;

import com.android.net.module.util.Struct;

import java.math.BigInteger;

/**
 * Struct xfrm_lifetime_cfg
 *
 * <p>see include/uapi/linux/xfrm.h
 *
 * <pre>
 * struct xfrm_lifetime_cfg {
 *      __u64 soft_byte_limit;
 *      __u64 hard_byte_limit;
 *      __u64 soft_packet_limit;
 *      __u64 hard_packet_limit;
 *      __u64 soft_add_expires_seconds;
 *      __u64 hard_add_expires_seconds;
 *      __u64 soft_use_expires_seconds;
 *      __u64 hard_use_expires_seconds;
 * };
 * </pre>
 *
 * @hide
 */
public class StructXfrmLifetimeCfg extends Struct {
    public static final int STRUCT_SIZE = 64;

    @Field(order = 0, type = Type.U64)
    public final BigInteger softByteLimit;

    @Field(order = 1, type = Type.U64)
    public final BigInteger hardByteLimit;

    @Field(order = 2, type = Type.U64)
    public final BigInteger softPacketLimit;

    @Field(order = 3, type = Type.U64)
    public final BigInteger hardPacketLimit;

    @Field(order = 4, type = Type.U64)
    public final BigInteger softAddExpiresSeconds;

    @Field(order = 5, type = Type.U64)
    public final BigInteger hardAddExpiresSeconds;

    @Field(order = 6, type = Type.U64)
    public final BigInteger softUseExpiresSeconds;

    @Field(order = 7, type = Type.U64)
    public final BigInteger hardUseExpiresSeconds;

    // Constructor that allows Strutc.parse(Class<T>, ByteBuffer) to work
    public StructXfrmLifetimeCfg(
            @NonNull final BigInteger softByteLimit,
            @NonNull final BigInteger hardByteLimit,
            @NonNull final BigInteger softPacketLimit,
            @NonNull final BigInteger hardPacketLimit,
            @NonNull final BigInteger softAddExpiresSeconds,
            @NonNull final BigInteger hardAddExpiresSeconds,
            @NonNull final BigInteger softUseExpiresSeconds,
            @NonNull final BigInteger hardUseExpiresSeconds) {
        this.softByteLimit = softByteLimit;
        this.hardByteLimit = hardByteLimit;
        this.softPacketLimit = softPacketLimit;
        this.hardPacketLimit = hardPacketLimit;
        this.softAddExpiresSeconds = softAddExpiresSeconds;
        this.hardAddExpiresSeconds = hardAddExpiresSeconds;
        this.softUseExpiresSeconds = softUseExpiresSeconds;
        this.hardUseExpiresSeconds = hardUseExpiresSeconds;
    }

    // Constructor to build a new message
    public StructXfrmLifetimeCfg() {
        this(
                XFRM_INF,
                XFRM_INF,
                XFRM_INF,
                XFRM_INF,
                BigInteger.ZERO,
                BigInteger.ZERO,
                BigInteger.ZERO,
                BigInteger.ZERO);
    }
}
