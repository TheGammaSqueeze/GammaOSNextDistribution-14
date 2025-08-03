/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.server.connectivity;

import android.util.Log;
import android.util.Range;

import com.android.net.module.util.Struct;
import com.android.net.module.util.Struct.Field;
import com.android.net.module.util.Struct.Type;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

/** Value type for DSCP setting and rewriting to DSCP policy BPF maps. */
public class DscpPolicyValue extends Struct {
    private static final String TAG = DscpPolicyValue.class.getSimpleName();

    @Field(order = 0, type = Type.ByteArray, arraysize = 16)
    public final byte[] src46;

    @Field(order = 1, type = Type.ByteArray, arraysize = 16)
    public final byte[] dst46;

    @Field(order = 2, type = Type.S32)
    public final int ifIndex;

    @Field(order = 3, type = Type.UBE16)
    public final int srcPort;

    @Field(order = 4, type = Type.U16)
    public final int dstPortStart;

    @Field(order = 5, type = Type.U16)
    public final int dstPortEnd;

    @Field(order = 6, type = Type.U8)
    public final short proto;

    @Field(order = 7, type = Type.S8)
    public final byte dscp;

    @Field(order = 8, type = Type.U8, padding = 3)
    public final short mask;

    private static final int SRC_IP_MASK = 0x1;
    private static final int DST_IP_MASK = 0x02;
    private static final int SRC_PORT_MASK = 0x4;
    private static final int PROTO_MASK = 0x8;

    private boolean ipEmpty(final byte[] ip) {
        for (int i = 0; i < ip.length; i++) {
            if (ip[i] != 0) return false;
        }
        return true;
    }

    // TODO:  move to frameworks/libs/net and have this and BpfCoordinator import it.
    private byte[] toIpv4MappedAddressBytes(InetAddress ia) {
        final byte[] addr6 = new byte[16];
        if (ia != null) {
            final byte[] addr4 = ia.getAddress();
            addr6[10] = (byte) 0xff;
            addr6[11] = (byte) 0xff;
            addr6[12] = addr4[0];
            addr6[13] = addr4[1];
            addr6[14] = addr4[2];
            addr6[15] = addr4[3];
        }
        return addr6;
    }

    private byte[] toAddressField(InetAddress addr) {
        if (addr == null) {
            return EMPTY_ADDRESS_FIELD;
        } else if (addr instanceof Inet4Address) {
            return toIpv4MappedAddressBytes(addr);
        } else {
            return addr.getAddress();
        }
    }

    private static final byte[] EMPTY_ADDRESS_FIELD =
            InetAddress.parseNumericAddress("::").getAddress();

    private short makeMask(final byte[] src46, final byte[] dst46, final int srcPort,
            final int dstPortStart, final short proto, final byte dscp) {
        short mask = 0;
        if (src46 != EMPTY_ADDRESS_FIELD) {
            mask |= SRC_IP_MASK;
        }
        if (dst46 != EMPTY_ADDRESS_FIELD) {
            mask |=  DST_IP_MASK;
        }
        if (srcPort != -1) {
            mask |=  SRC_PORT_MASK;
        }
        if (proto != -1) {
            mask |=  PROTO_MASK;
        }
        return mask;
    }

    private DscpPolicyValue(final InetAddress src46, final InetAddress dst46, final int ifIndex,
            final int srcPort, final int dstPortStart, final int dstPortEnd, final short proto,
            final byte dscp) {
        this.src46 = toAddressField(src46);
        this.dst46 = toAddressField(dst46);
        this.ifIndex = ifIndex;

        // These params need to be stored as 0 because uints are used in BpfMap.
        // If they are -1 BpfMap write will throw errors.
        this.srcPort = srcPort != -1 ? srcPort : 0;
        this.dstPortStart = dstPortStart != -1 ? dstPortStart : 0;
        this.dstPortEnd = dstPortEnd != -1 ? dstPortEnd : 65535;
        this.proto = proto != -1 ? proto : 0;

        this.dscp = dscp;
        // Use member variables for IP since byte[] is needed and api variables for everything else
        // so -1 is passed into mask if parameter is not present.
        this.mask = makeMask(this.src46, this.dst46, srcPort, dstPortStart, proto, dscp);
    }

    public DscpPolicyValue(final InetAddress src46, final InetAddress dst46, final int ifIndex,
            final int srcPort, final Range<Integer> dstPort, final short proto,
            final byte dscp) {
        this(src46, dst46, ifIndex, srcPort, dstPort != null ? dstPort.getLower() : -1,
                dstPort != null ? dstPort.getUpper() : -1, proto, dscp);
    }

    public static final DscpPolicyValue NONE = new DscpPolicyValue(
            null /* src46 */, null /* dst46 */, 0 /* ifIndex */, -1 /* srcPort */,
            -1 /* dstPortStart */, -1 /* dstPortEnd */, (short) -1 /* proto */,
            (byte) -1 /* dscp */);

    @Override
    public String toString() {
        String srcIpString = "empty";
        String dstIpString = "empty";

        // Separate try/catch for IP's so it's easier to debug.
        try {
            srcIpString = InetAddress.getByAddress(src46).getHostAddress();
        }  catch (UnknownHostException e) {
            Log.e(TAG, "Invalid SRC IP address", e);
        }

        try {
            dstIpString = InetAddress.getByAddress(src46).getHostAddress();
        }  catch (UnknownHostException e) {
            Log.e(TAG, "Invalid DST IP address", e);
        }

        try {
            return String.format(
                    "src46: %s, dst46: %s, ifIndex: %d, srcPort: %d, dstPortStart: %d,"
                    + " dstPortEnd: %d, protocol: %d, dscp %s", srcIpString, dstIpString,
                    ifIndex, srcPort, dstPortStart, dstPortEnd, proto, dscp);
        } catch (IllegalArgumentException e) {
            return String.format("String format error: " + e);
        }
    }
}
