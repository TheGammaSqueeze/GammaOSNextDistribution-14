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

import static com.android.net.module.util.netlink.xfrm.XfrmNetlinkMessage.XFRMA_REPLAY_ESN_VAL;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.net.module.util.Struct;
import com.android.net.module.util.netlink.StructNlAttr;
import com.android.net.module.util.netlink.StructNlMsgHdr;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * A NetlinkMessage subclass for XFRM_MSG_NEWSA messages.
 *
 * <p>see also: &lt;linux_src&gt;/include/uapi/linux/xfrm.h
 *
 * <p>XFRM_MSG_NEWSA syntax
 *
 * <ul>
 *   <li>TLV: xfrm_usersa_info
 *   <li>Attributes: XFRMA_ALG_CRYPT, XFRMA_ALG_AUTH, XFRMA_OUTPUT_MARK, XFRMA_IF_ID,
 *       XFRMA_REPLAY_ESN_VAL,XFRMA_REPLAY_VAL
 * </ul>
 *
 * @hide
 */
public class XfrmNetlinkNewSaMessage extends XfrmNetlinkMessage {
    private static final String TAG = XfrmNetlinkNewSaMessage.class.getSimpleName();
    @NonNull private final StructXfrmUsersaInfo mXfrmUsersaInfo;

    @NonNull private final StructXfrmReplayStateEsn mXfrmReplayStateEsn;

    private XfrmNetlinkNewSaMessage(
            @NonNull final StructNlMsgHdr header,
            @NonNull final StructXfrmUsersaInfo xfrmUsersaInfo,
            @NonNull final StructXfrmReplayStateEsn xfrmReplayStateEsn) {
        super(header);
        mXfrmUsersaInfo = xfrmUsersaInfo;
        mXfrmReplayStateEsn = xfrmReplayStateEsn;
    }

    @Override
    protected void packPayload(@NonNull final ByteBuffer byteBuffer) {
        mXfrmUsersaInfo.writeToByteBuffer(byteBuffer);
        if (mXfrmReplayStateEsn != null) {
            mXfrmReplayStateEsn.writeToByteBuffer(byteBuffer);
        }
    }

    /**
     * Parse XFRM_MSG_NEWSA message from ByteBuffer.
     *
     * <p>This method should be called from NetlinkMessage#parse(ByteBuffer, int) for generic
     * message validation and processing
     *
     * @param nlmsghdr netlink message header.
     * @param byteBuffer the ByteBuffer instance that wraps the raw netlink message bytes. MUST be
     *                   host order
     */
    @Nullable
    static XfrmNetlinkNewSaMessage parseInternal(
            @NonNull final StructNlMsgHdr nlmsghdr, @NonNull final ByteBuffer byteBuffer) {
        final StructXfrmUsersaInfo xfrmUsersaInfo =
                Struct.parse(StructXfrmUsersaInfo.class, byteBuffer);
        if (xfrmUsersaInfo == null) {
            Log.d(TAG, "parse: fail to parse xfrmUsersaInfo");
            return null;
        }

        StructXfrmReplayStateEsn xfrmReplayStateEsn = null;

        final int payloadLen = nlmsghdr.nlmsg_len - StructNlMsgHdr.STRUCT_SIZE;
        int parsedLength = StructXfrmUsersaInfo.STRUCT_SIZE;
        while (parsedLength < payloadLen) {
            final StructNlAttr attr = StructNlAttr.parse(byteBuffer);

            if (attr == null) {
                Log.d(TAG, "parse: fail to parse netlink attributes");
                return null;
            }

            final ByteBuffer attrValueBuff = ByteBuffer.wrap(attr.nla_value);
            attrValueBuff.order(ByteOrder.nativeOrder());

            if (attr.nla_type == XFRMA_REPLAY_ESN_VAL) {
                xfrmReplayStateEsn = StructXfrmReplayStateEsn.parse(attrValueBuff);
            }

            parsedLength += attr.nla_len;
        }

        // TODO: Add the support of XFRMA_REPLAY_VAL

        if (xfrmReplayStateEsn == null) {
            Log.d(TAG, "parse: xfrmReplayStateEsn not found");
            return null;
        }

        final XfrmNetlinkNewSaMessage msg =
                new XfrmNetlinkNewSaMessage(nlmsghdr, xfrmUsersaInfo, xfrmReplayStateEsn);

        return msg;
    }

    /** Return the TX sequence number in unisgned long */
    public long getTxSequenceNumber() {
        return mXfrmReplayStateEsn.getTxSequenceNumber();
    }

    /** Return the RX sequence number in unisgned long */
    public long getRxSequenceNumber() {
        return mXfrmReplayStateEsn.getRxSequenceNumber();
    }

    /** Return the bitmap */
    public byte[] getBitmap() {
        return mXfrmReplayStateEsn.getBitmap();
    }

    /** Return the packet count in unsigned long */
    public long getPacketCount() {
        // It is safe because "packets" is a 64-bit value
        return mXfrmUsersaInfo.getCurrentLifetime().packets.longValue();
    }

    /** Return the byte count in unsigned long */
    public long getByteCount() {
        // It is safe because "bytes" is a 64-bit value
        return mXfrmUsersaInfo.getCurrentLifetime().bytes.longValue();
    }

    /** Return the xfrm_usersa_info */
    public StructXfrmUsersaInfo getXfrmUsersaInfo() {
        return mXfrmUsersaInfo;
    }
}
