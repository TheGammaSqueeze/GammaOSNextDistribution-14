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
import androidx.annotation.VisibleForTesting;

import com.android.net.module.util.Struct;
import com.android.net.module.util.Struct.Field;
import com.android.net.module.util.Struct.Type;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Struct xfrm_replay_state_esn
 *
 * <p>see include/uapi/linux/xfrm.h
 *
 * <pre>
 * struct xfrm_replay_state_esn {
 *      unsigned int bmp_len;
 *      __u32 oseq;
 *      __u32 seq;
 *      __u32 oseq_hi;
 *      __u32 seq_hi;
 *      __u32 replay_window;
 *      __u32 bmp[];
 * };
 * </pre>
 *
 * @hide
 */
public class StructXfrmReplayStateEsn {
    // include/uapi/linux/xfrm.h XFRMA_REPLAY_ESN_MAX
    private static final int XFRMA_REPLAY_ESN_BMP_LEN_MAX = 128;

    @NonNull private final StructXfrmReplayStateEsnWithoutBitmap mWithoutBitmap;
    @NonNull private final byte[] mBitmap;

    private StructXfrmReplayStateEsn(
            @NonNull final StructXfrmReplayStateEsnWithoutBitmap withoutBitmap,
            @NonNull final byte[] bitmap) {
        mWithoutBitmap = withoutBitmap;
        mBitmap = bitmap;
        validate();
    }

    /** Constructor to build a new message */
    public StructXfrmReplayStateEsn(
            long bmpLen,
            long oSeq,
            long seq,
            long oSeqHi,
            long seqHi,
            long replayWindow,
            @NonNull final byte[] bitmap) {
        mWithoutBitmap =
                new StructXfrmReplayStateEsnWithoutBitmap(
                        bmpLen, oSeq, seq, oSeqHi, seqHi, replayWindow);
        mBitmap = bitmap.clone();
        validate();
    }

    private void validate() {
        if (mWithoutBitmap.mBmpLenInBytes != mBitmap.length) {
            throw new IllegalArgumentException(
                    "mWithoutBitmap.mBmpLenInBytes not aligned with bitmap."
                            + " mWithoutBitmap.mBmpLenInBytes: "
                            + mWithoutBitmap.mBmpLenInBytes
                            + " bitmap.length "
                            + mBitmap.length);
        }
    }

    /** Parse IpSecStructXfrmReplayStateEsn from ByteBuffer. */
    @Nullable
    public static StructXfrmReplayStateEsn parse(@NonNull final ByteBuffer buf) {
        final StructXfrmReplayStateEsnWithoutBitmap withoutBitmap =
                Struct.parse(StructXfrmReplayStateEsnWithoutBitmap.class, buf);
        if (withoutBitmap == null) {
            return null;
        }

        final byte[] bitmap = new byte[withoutBitmap.mBmpLenInBytes];
        buf.get(bitmap);

        return new StructXfrmReplayStateEsn(withoutBitmap, bitmap);
    }

    /** Convert the parsed object to ByteBuffer. */
    public void writeToByteBuffer(@NonNull final ByteBuffer buf) {
        mWithoutBitmap.writeToByteBuffer(buf);
        buf.put(mBitmap);
    }

    /** Return the struct size */
    public int getStructSize() {
        return StructXfrmReplayStateEsnWithoutBitmap.STRUCT_SIZE + mBitmap.length;
    }

    /** Return the bitmap */
    public byte[] getBitmap() {
        return mBitmap.clone();
    }

    /** Return the bmp_len */
    public long getBmpLen() {
        return mWithoutBitmap.bmpLen;
    }

    /** Return the replay_window */
    public long getReplayWindow() {
        return mWithoutBitmap.replayWindow;
    }

    /** Return the TX sequence number in unisgned long */
    public long getTxSequenceNumber() {
        return getSequenceNumber(mWithoutBitmap.oSeqHi, mWithoutBitmap.oSeq);
    }

    /** Return the RX sequence number in unisgned long */
    public long getRxSequenceNumber() {
        return getSequenceNumber(mWithoutBitmap.seqHi, mWithoutBitmap.seq);
    }

    @VisibleForTesting
    static long getSequenceNumber(long hi, long low) {
        final ByteBuffer buffer = ByteBuffer.allocate(8);
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.putInt((int) hi).putInt((int) low);
        buffer.rewind();

        return buffer.getLong();
    }

    /** The xfrm_replay_state_esn struct without the bitmap */
    // Because the size of the bitmap is decided at runtime, it cannot be included in a Struct
    // subclass. Therefore, this nested class is defined to include all other fields supported by
    // Struct for code reuse.
    public static class StructXfrmReplayStateEsnWithoutBitmap extends Struct {
        public static final int STRUCT_SIZE = 24;

        @Field(order = 0, type = Type.U32)
        public final long bmpLen; // replay bitmap length in 32-bit integers

        @Field(order = 1, type = Type.U32)
        public final long oSeq;

        @Field(order = 2, type = Type.U32)
        public final long seq;

        @Field(order = 3, type = Type.U32)
        public final long oSeqHi;

        @Field(order = 4, type = Type.U32)
        public final long seqHi;

        @Field(order = 5, type = Type.U32)
        public final long replayWindow; // replay bitmap length in bit

        @Computed private final int mBmpLenInBytes; // replay bitmap length in bytes

        public StructXfrmReplayStateEsnWithoutBitmap(
                long bmpLen, long oSeq, long seq, long oSeqHi, long seqHi, long replayWindow) {
            this.bmpLen = bmpLen;
            this.oSeq = oSeq;
            this.seq = seq;
            this.oSeqHi = oSeqHi;
            this.seqHi = seqHi;
            this.replayWindow = replayWindow;

            if (bmpLen > XFRMA_REPLAY_ESN_BMP_LEN_MAX) {
                throw new IllegalArgumentException("Invalid bmpLen " + bmpLen);
            }

            if (bmpLen * 4 * 8 != replayWindow) {
                throw new IllegalArgumentException(
                        "bmpLen not aligned with replayWindow. bmpLen: "
                                + bmpLen
                                + " replayWindow "
                                + replayWindow);
            }

            mBmpLenInBytes = (int) bmpLen * 4;
        }
    }
}
