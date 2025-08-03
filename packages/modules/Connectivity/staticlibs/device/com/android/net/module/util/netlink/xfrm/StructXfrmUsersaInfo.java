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

import android.system.OsConstants;

import androidx.annotation.NonNull;

import com.android.net.module.util.Struct;
import com.android.net.module.util.Struct.Computed;
import com.android.net.module.util.Struct.Field;
import com.android.net.module.util.Struct.Type;

import java.math.BigInteger;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Struct xfrm_usersa_info
 *
 * <p>see include/uapi/linux/xfrm.h
 *
 * <pre>
 * struct xfrm_usersa_info {
 *      struct xfrm_selector sel;
 *      struct xfrm_id id;
 *      xfrm_address_t saddr;
 *      struct xfrm_lifetime_cfg lft;
 *      struct xfrm_lifetime_cur curlft;
 *      struct xfrm_stats stats;
 *      __u32 seq;
 *      __u32 reqid;
 *      __u16 family;
 *      __u8 mode;
 *      __u8 replay_window;
 *      __u8 flags;
 * };
 * </pre>
 *
 * @hide
 */
public class StructXfrmUsersaInfo extends Struct {
    private static final int NESTED_STRUCTS_SIZE =
            StructXfrmSelector.STRUCT_SIZE
                    + StructXfrmId.STRUCT_SIZE
                    + StructXfrmAddressT.STRUCT_SIZE
                    + StructXfrmLifetimeCfg.STRUCT_SIZE
                    + StructXfrmLifetimeCur.STRUCT_SIZE
                    + StructXfrmStats.STRUCT_SIZE;

    public static final int STRUCT_SIZE = NESTED_STRUCTS_SIZE + 20;

    @Computed private final StructXfrmSelector mXfrmSelector;
    @Computed private final StructXfrmId mXfrmId;
    @Computed private final StructXfrmAddressT mSourceXfrmAddressT;
    @Computed private final StructXfrmLifetimeCfg mXfrmLifetime;
    @Computed private final StructXfrmLifetimeCur mXfrmCurrentLifetime;
    @Computed private final StructXfrmStats mXfrmStats;

    @Field(order = 0, type = Type.ByteArray, arraysize = NESTED_STRUCTS_SIZE)
    public final byte[] nestedStructs;

    @Field(order = 1, type = Type.U32)
    public final long seq;

    @Field(order = 2, type = Type.U32)
    public final long reqId;

    @Field(order = 3, type = Type.U16)
    public final int family;

    @Field(order = 4, type = Type.U8)
    public final short mode;

    @Field(order = 5, type = Type.U8)
    public final short replayWindowLegacy;

    @Field(order = 6, type = Type.U8, padding = 7)
    public final short flags;

    // Constructor that allows Strutc.parse(Class<T>, ByteBuffer) to work
    public StructXfrmUsersaInfo(
            @NonNull final byte[] nestedStructs,
            long seq,
            long reqId,
            int family,
            short mode,
            short replayWindowLegacy,
            short flags) {
        this.nestedStructs = nestedStructs.clone();
        this.seq = seq;
        this.reqId = reqId;
        this.family = family;
        this.mode = mode;
        this.replayWindowLegacy = replayWindowLegacy;
        this.flags = flags;

        final ByteBuffer nestedStructsBuff = ByteBuffer.wrap(nestedStructs);
        nestedStructsBuff.order(ByteOrder.nativeOrder());

        // The parsing order matters
        mXfrmSelector = Struct.parse(StructXfrmSelector.class, nestedStructsBuff);
        mXfrmId = Struct.parse(StructXfrmId.class, nestedStructsBuff);
        mSourceXfrmAddressT = Struct.parse(StructXfrmAddressT.class, nestedStructsBuff);
        mXfrmLifetime = Struct.parse(StructXfrmLifetimeCfg.class, nestedStructsBuff);
        mXfrmCurrentLifetime = Struct.parse(StructXfrmLifetimeCur.class, nestedStructsBuff);
        mXfrmStats = Struct.parse(StructXfrmStats.class, nestedStructsBuff);
    }

    // Constructor to build a new message for TESTING
    StructXfrmUsersaInfo(
            @NonNull final InetAddress dAddr,
            @NonNull final InetAddress sAddr,
            @NonNull final BigInteger addTime,
            int selectorFamily,
            long spi,
            long seq,
            long reqId,
            short proto,
            short mode,
            short replayWindowLegacy,
            short flags) {
        this.seq = seq;
        this.reqId = reqId;
        this.family = dAddr instanceof Inet4Address ? OsConstants.AF_INET : OsConstants.AF_INET6;
        this.mode = mode;
        this.replayWindowLegacy = replayWindowLegacy;
        this.flags = flags;

        mXfrmSelector = new StructXfrmSelector(selectorFamily);
        mXfrmId = new StructXfrmId(dAddr, spi, proto);
        mSourceXfrmAddressT = new StructXfrmAddressT(sAddr);
        mXfrmLifetime = new StructXfrmLifetimeCfg();
        mXfrmCurrentLifetime =
                new StructXfrmLifetimeCur(
                        BigInteger.ZERO, BigInteger.ZERO, addTime, BigInteger.ZERO);
        mXfrmStats = new StructXfrmStats();

        final ByteBuffer nestedStructsBuff = ByteBuffer.allocate(NESTED_STRUCTS_SIZE);
        nestedStructsBuff.order(ByteOrder.nativeOrder());

        mXfrmSelector.writeToByteBuffer(nestedStructsBuff);
        mXfrmId.writeToByteBuffer(nestedStructsBuff);
        mSourceXfrmAddressT.writeToByteBuffer(nestedStructsBuff);
        mXfrmLifetime.writeToByteBuffer(nestedStructsBuff);
        mXfrmCurrentLifetime.writeToByteBuffer(nestedStructsBuff);
        mXfrmStats.writeToByteBuffer(nestedStructsBuff);

        this.nestedStructs = nestedStructsBuff.array();
    }

    // Constructor to build a new message
    public StructXfrmUsersaInfo(
            @NonNull final InetAddress dAddr,
            @NonNull final InetAddress sAddr,
            long spi,
            long seq,
            long reqId,
            short proto,
            short mode,
            short replayWindowLegacy,
            short flags) {
        // Use AF_UNSPEC for all SAs selectors. In transport mode, kernel picks selector family
        // based on usersa->family, while in tunnel mode, the XFRM_STATE_AF_UNSPEC flag allows
        // dual-stack SAs.
        this(
                dAddr,
                sAddr,
                BigInteger.ZERO,
                OsConstants.AF_UNSPEC,
                spi,
                seq,
                reqId,
                proto,
                mode,
                replayWindowLegacy,
                flags);
    }

    /** Return the destination address */
    public InetAddress getDestAddress() {
        return mXfrmId.getDestAddress(family);
    }

    /** Return the source address */
    public InetAddress getSrcAddress() {
        return mSourceXfrmAddressT.getAddress(family);
    }

    /** Return the SPI */
    public long getSpi() {
        return mXfrmId.spi;
    }

    /** Return the current lifetime */
    public StructXfrmLifetimeCur getCurrentLifetime() {
        return mXfrmCurrentLifetime;
    }
}
