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

import java.math.BigInteger;

/**
 * Struct xfrm_lifetime_cur
 *
 * <p>see include/uapi/linux/xfrm.h
 *
 * <pre>
 * struct xfrm_lifetime_cur {
 *      __u64 bytes;
 *      __u64 packets;
 *      __u64 add_time;
 *      __u64 use_time;
 * };
 * </pre>
 *
 * @hide
 */
public class StructXfrmLifetimeCur extends Struct {
    public static final int STRUCT_SIZE = 32;

    @Field(order = 0, type = Type.U64)
    public final BigInteger bytes;

    @Field(order = 1, type = Type.U64)
    public final BigInteger packets;

    @Field(order = 2, type = Type.U64)
    public final BigInteger addTime;

    @Field(order = 3, type = Type.U64)
    public final BigInteger useTime;

    public StructXfrmLifetimeCur(
            @NonNull final BigInteger bytes,
            @NonNull final BigInteger packets,
            @NonNull final BigInteger addTime,
            @NonNull final BigInteger useTime) {
        this.bytes = bytes;
        this.packets = packets;
        this.addTime = addTime;
        this.useTime = useTime;
    }
}
