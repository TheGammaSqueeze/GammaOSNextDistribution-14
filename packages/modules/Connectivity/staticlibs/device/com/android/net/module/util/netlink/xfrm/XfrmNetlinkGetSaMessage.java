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

import static com.android.net.module.util.netlink.StructNlMsgHdr.NLM_F_REQUEST;
import static com.android.net.module.util.netlink.xfrm.XfrmNetlinkMessage.XFRM_MSG_GETSA;

import android.system.OsConstants;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.net.module.util.Struct;
import com.android.net.module.util.netlink.StructNlMsgHdr;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.nio.ByteBuffer;

/**
 * An XfrmNetlinkMessage subclass for XFRM_MSG_GETSA messages.
 *
 * <p>see include/uapi/linux/xfrm.h
 *
 * <p>XFRM_MSG_GETSA syntax
 *
 * <ul>
 *   <li>TLV: xfrm_usersa_id
 *   <li>Optional Attributes: XFRMA_MARK, XFRMA_SRCADDR
 * </ul>
 *
 * @hide
 */
public class XfrmNetlinkGetSaMessage extends XfrmNetlinkMessage {
    @NonNull private final StructXfrmUsersaId mXfrmUsersaId;

    private XfrmNetlinkGetSaMessage(
            @NonNull final StructNlMsgHdr header, @NonNull final StructXfrmUsersaId xfrmUsersaId) {
        super(header);
        mXfrmUsersaId = xfrmUsersaId;
    }

    private XfrmNetlinkGetSaMessage(
            @NonNull final StructNlMsgHdr header,
            @NonNull final InetAddress destAddress,
            long spi,
            short proto) {
        super(header);

        final int family =
                destAddress instanceof Inet4Address ? OsConstants.AF_INET : OsConstants.AF_INET6;
        mXfrmUsersaId = new StructXfrmUsersaId(destAddress, spi, family, proto);
    }

    @Override
    protected void packPayload(@NonNull final ByteBuffer byteBuffer) {
        mXfrmUsersaId.writeToByteBuffer(byteBuffer);
    }

    /**
     * Parse XFRM_MSG_GETSA message from ByteBuffer.
     *
     * <p>This method should be called from NetlinkMessage#parse(ByteBuffer, int) for generic
     * message validation and processing
     *
     * @param nlmsghdr netlink message header.
     * @param byteBuffer the ByteBuffer instance that wraps the raw netlink message bytes. MUST be
     *                   host order
     */
    @Nullable
    static XfrmNetlinkGetSaMessage parseInternal(
            @NonNull final StructNlMsgHdr nlmsghdr, @NonNull final ByteBuffer byteBuffer) {
        final StructXfrmUsersaId xfrmUsersaId = Struct.parse(StructXfrmUsersaId.class, byteBuffer);
        if (xfrmUsersaId == null) {
            return null;
        }

        // Attributes not supported. Don't bother handling them.

        return new XfrmNetlinkGetSaMessage(nlmsghdr, xfrmUsersaId);
    }

    /** A convenient method to create a XFRM_MSG_GETSA message. */
    public static byte[] newXfrmNetlinkGetSaMessage(
            @NonNull final InetAddress destAddress, long spi, short proto) {
        final int payloadLen = StructXfrmUsersaId.STRUCT_SIZE;

        final StructNlMsgHdr nlmsghdr =
                new StructNlMsgHdr(payloadLen, XFRM_MSG_GETSA, NLM_F_REQUEST, 0);
        final XfrmNetlinkGetSaMessage message =
                new XfrmNetlinkGetSaMessage(nlmsghdr, destAddress, spi, proto);

        final ByteBuffer byteBuffer = newNlMsgByteBuffer(payloadLen);
        message.pack(byteBuffer);

        return byteBuffer.array();
    }

    public StructXfrmUsersaId getStructXfrmUsersaId() {
        return mXfrmUsersaId;
    }
}
