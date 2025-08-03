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

import static android.system.OsConstants.AF_INET6;

import com.android.net.module.util.Struct;
import java.net.Inet6Address;
import java.util.Set;

/*
 * Implements the mf6cctl structure which is used to add a multicast forwarding
 * cache, see /usr/include/linux/mroute6.h
 */
public class StructMf6cctl extends Struct {
    // struct sockaddr_in6 mf6cc_origin, added the fields directly as Struct
    // doesn't support nested Structs
    @Field(order = 0, type = Type.U16)
    public final int originFamily; // AF_INET6
    @Field(order = 1, type = Type.U16)
    public final int originPort; // Transport layer port # of origin
    @Field(order = 2, type = Type.U32)
    public final long originFlowinfo; // IPv6 flow information
    @Field(order = 3, type = Type.ByteArray, arraysize = 16)
    public final byte[] originAddress; //the IPv6 address of origin
    @Field(order = 4, type = Type.U32)
    public final long originScopeId; // scope id, not used

    // struct sockaddr_in6 mf6cc_mcastgrp
    @Field(order = 5, type = Type.U16)
    public final int groupFamily; // AF_INET6
    @Field(order = 6, type = Type.U16)
    public final int groupPort; // Transport layer port # of multicast group
    @Field(order = 7, type = Type.U32)
    public final long groupFlowinfo; // IPv6 flow information
    @Field(order = 8, type = Type.ByteArray, arraysize = 16)
    public final byte[] groupAddress; //the IPv6 address of multicast group
    @Field(order = 9, type = Type.U32)
    public final long groupScopeId; // scope id, not used

    @Field(order = 10, type = Type.U16, padding = 2)
    public final int mf6ccParent; // incoming interface
    @Field(order = 11, type = Type.ByteArray, arraysize = 32)
    public final byte[] mf6ccIfset; // outgoing interfaces

    public StructMf6cctl(final Inet6Address origin, final Inet6Address group,
            final int mf6ccParent, final Set<Integer> oifset) {
        this(AF_INET6, 0, (long) 0, origin.getAddress(), (long) 0, AF_INET6,
                0, (long) 0, group.getAddress(), (long) 0, mf6ccParent,
                getMf6ccIfsetBytes(oifset));
    }

    private StructMf6cctl(int originFamily, int originPort, long originFlowinfo,
            byte[] originAddress, long originScopeId, int groupFamily, int groupPort,
            long groupFlowinfo, byte[] groupAddress, long groupScopeId, int mf6ccParent,
            byte[] mf6ccIfset) {
        this.originFamily = originFamily;
        this.originPort = originPort;
        this.originFlowinfo = originFlowinfo;
        this.originAddress = originAddress;
        this.originScopeId = originScopeId;
        this.groupFamily = groupFamily;
        this.groupPort = groupPort;
        this.groupFlowinfo = groupFlowinfo;
        this.groupAddress = groupAddress;
        this.groupScopeId = groupScopeId;
        this.mf6ccParent = mf6ccParent;
        this.mf6ccIfset = mf6ccIfset;
    }

    private static byte[] getMf6ccIfsetBytes(final Set<Integer> oifs)
            throws IllegalArgumentException {
        byte[] mf6ccIfset = new byte[32];
        for (int oif : oifs) {
            int idx = oif / 8;
            if (idx >= 32) {
                // invalid oif index, too big to fit in mf6ccIfset
                throw new IllegalArgumentException("Invalid oif index" + oif);
            }
            int offset = oif % 8;
            mf6ccIfset[idx] |= (byte) (1 << offset);
        }
        return mf6ccIfset;
    }
}
