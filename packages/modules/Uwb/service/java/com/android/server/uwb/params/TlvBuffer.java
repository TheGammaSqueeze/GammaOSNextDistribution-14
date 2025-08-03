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

package com.android.server.uwb.params;

import com.android.server.uwb.config.ConfigParam;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/***
 * This assumes little endian data and 1 byte tags. This is intended for handling UCI interface
 * data.
 */
public class TlvBuffer {
    private static final String TAG = "TlvBuffer";
    private static final int MAX_BUFFER_SIZE = 512;
    private final ByteBuffer mBuffer;
    private final int mNoOfParams;

    public TlvBuffer(byte[] tlvArray, int noOfParams) {
        mBuffer = ByteBuffer.wrap(tlvArray);
        mNoOfParams = noOfParams;
    }

    public byte[] getByteArray() {
        return mBuffer.array();
    }

    public int getNoOfParams() {
        return mNoOfParams;
    }

    public static final class Builder {
        ByteBuffer mBuffer = ByteBuffer.allocate(MAX_BUFFER_SIZE);
        int mNoOfParams = 0;
        ByteOrder mOrder = ByteOrder.BIG_ENDIAN;

        public TlvBuffer.Builder putOrder(ByteOrder order) {
            mOrder = order;
            return this;
        }

        public TlvBuffer.Builder putByte(int tagType, byte b) {
            addHeader(tagType, Byte.BYTES);
            this.mBuffer.put(b);
            this.mNoOfParams++;
            return this;
        }

        public TlvBuffer.Builder putByteArray(int tagType, byte[] bArray) {
            if (bArray == null) return this;
            return putByteArray(tagType, bArray.length, bArray);
        }

        public TlvBuffer.Builder putByteArray(int tagType, int length, byte[] bArray) {
            addHeader(tagType, length);
            this.mBuffer.put(bArray);
            this.mNoOfParams++;
            return this;
        }

        public TlvBuffer.Builder putShort(int tagType, short data) {
            addHeader(tagType, Short.BYTES);
            this.mBuffer.put(TlvUtil.getLeBytes(data));
            this.mNoOfParams++;
            return this;
        }

        public TlvBuffer.Builder putShortArray(int tagType, short[] sArray) {
            if (sArray == null) return this;
            return putShortArray(tagType, sArray.length, sArray);
        }

        public TlvBuffer.Builder putShortArray(int tagType, int length, short[] sArray) {
            addHeader(tagType, length * Short.BYTES);
            for (int i = 0; i < length; i++) {
                this.mBuffer.put(TlvUtil.getLeBytes(sArray[i]));
            }
            this.mNoOfParams++;
            return this;
        }

        public TlvBuffer.Builder putInt(int tagType, int data) {
            addHeader(tagType, Integer.BYTES);
            this.mBuffer.put(TlvUtil.getLeBytes(data));
            this.mNoOfParams++;
            return this;
        }

        public TlvBuffer.Builder putLong(int tagType, long data) {
            addHeader(tagType, Long.BYTES);
            this.mBuffer.put(TlvUtil.getLeBytes(data));
            this.mNoOfParams++;
            return this;
        }

        public TlvBuffer build() {
            return new TlvBuffer(Arrays.copyOf(this.mBuffer.array(), this.mBuffer.position()),
                    this.mNoOfParams);
        }

        private void addHeader(int tagType, int length) {
            mBuffer.put(ConfigParam.getTagBytes(tagType));
            mBuffer.put((byte) length);
        }
    }
}
