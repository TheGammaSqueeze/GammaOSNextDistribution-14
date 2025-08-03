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
import androidx.annotation.Nullable;

import com.android.net.module.util.netlink.NetlinkMessage;
import com.android.net.module.util.netlink.StructNlMsgHdr;

import java.math.BigInteger;
import java.nio.ByteBuffer;

/** Base calss for XFRM netlink messages */
// Developer notes: The Linux kernel includes a number of XFRM structs that are not standard netlink
// attributes (e.g., xfrm_usersa_id). These structs are unlikely to change size, so this XFRM
// netlink message implementation assumes their sizes will remain stable. If any non-attribute
// struct size changes, it should be caught by CTS and then developers should add
// kernel-version-based behvaiours.
public abstract class XfrmNetlinkMessage extends NetlinkMessage {
    // TODO: b/312498032 Remove it when OsConstants.IPPROTO_ESP is stable
    public static final int IPPROTO_ESP = 50;
    // TODO: b/312498032 Remove it when OsConstants.NETLINK_XFRM is stable
    public static final int NETLINK_XFRM = 6;

    /* see include/uapi/linux/xfrm.h */
    public static final short XFRM_MSG_NEWSA = 16;
    public static final short XFRM_MSG_GETSA = 18;

    public static final int XFRM_MODE_TRANSPORT = 0;
    public static final int XFRM_MODE_TUNNEL = 1;

    public static final short XFRMA_REPLAY_VAL = 10;
    public static final short XFRMA_REPLAY_ESN_VAL = 23;

    public static final BigInteger XFRM_INF = new BigInteger("FFFFFFFFFFFFFFFF", 16);

    public XfrmNetlinkMessage(@NonNull final StructNlMsgHdr header) {
        super(header);
    }

    /**
     * Parse XFRM message from ByteBuffer.
     *
     * <p>This method should be called from NetlinkMessage#parse(ByteBuffer, int) for generic
     * message validation and processing
     *
     * @param nlmsghdr netlink message header.
     * @param byteBuffer the ByteBuffer instance that wraps the raw netlink message bytes. MUST be
     *                   host order
     */
    @Nullable
    public static XfrmNetlinkMessage parseXfrmInternal(
            @NonNull final StructNlMsgHdr nlmsghdr, @NonNull final ByteBuffer byteBuffer) {
        switch (nlmsghdr.nlmsg_type) {
            case XFRM_MSG_NEWSA:
                return XfrmNetlinkNewSaMessage.parseInternal(nlmsghdr, byteBuffer);
            case XFRM_MSG_GETSA:
                return XfrmNetlinkGetSaMessage.parseInternal(nlmsghdr, byteBuffer);
            default:
                return null;
        }
    }

    protected abstract void packPayload(@NonNull final ByteBuffer byteBuffer);

    /** Write a XFRM message to {@link ByteBuffer}. */
    public void pack(@NonNull final ByteBuffer byteBuffer) {
        getHeader().pack(byteBuffer);
        packPayload(byteBuffer);
    }
}
