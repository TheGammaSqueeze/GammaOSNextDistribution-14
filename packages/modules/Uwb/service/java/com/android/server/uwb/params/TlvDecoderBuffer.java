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

import android.annotation.Nullable;
import android.util.ArrayMap;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.uwb.config.ConfigParam;
import com.android.server.uwb.util.UwbUtil;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;

/***
 * This assumes little endian data and 1 byte tags. This is intended for handling UCI interface
 * data.
 * @see com.android.server.uwb.secure.iso7816.TlvParser
 */
public class TlvDecoderBuffer {
    private static final String TAG = "TlvDecoderBuffer";
    private final ByteBuffer mBuffer;
    private final int mNumParams;
    private final Map<Byte, Tlv> mTlvs = new ArrayMap<>();

    @VisibleForTesting
    public static class Tlv {
        public final byte tagType;
        public final byte length;
        public final byte[] value;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Tlv)) return false;
            Tlv tlv = (Tlv) o;
            return tagType == tlv.tagType && length == tlv.length && Arrays.equals(value,
                    tlv.value);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(tagType, length);
            result = 31 * result + Arrays.hashCode(value);
            return result;
        }

        Tlv(byte tagType, byte length, byte[] value) {
            this.tagType = tagType;
            this.length = length;
            this.value = value;
        }

        @Override
        public String toString() {
            return "Tlv[tagType: " + tagType + ", length: " + length + ", value: "
                    + UwbUtil.toHexString(value) + "]";
        }
    }

    public TlvDecoderBuffer(byte[] tlvArray, int noOfParams) {
        mBuffer = ByteBuffer.wrap(tlvArray);
        mNumParams = noOfParams;
    }

    @VisibleForTesting
    public byte[] getByteArray() {
        return mBuffer.array();
    }

    @VisibleForTesting
    public int getNumParams() {
        return mNumParams;
    }

    @VisibleForTesting
    public Collection<Tlv> getTlvs() {
        return mTlvs.values();
    }

    public boolean parse() {
        if (mBuffer.capacity() == 0) return false;
        while (mBuffer.hasRemaining()) {
            try {
                byte tagType = mBuffer.get();
                byte length = mBuffer.get();
                byte[] value = new byte[length];
                mBuffer.get(value);
                Log.i(TAG, "Parsed TLV: " + new Tlv(tagType, length, value));
                mTlvs.put(tagType, new Tlv(tagType, length, value));
            } catch (BufferUnderflowException e) {
                Log.e(TAG, "Failed to parse buffer at position: " + mBuffer.position(), e);
                return false;
            }
        }
        if (mNumParams != mTlvs.size()) {
            Log.e(TAG, "Num TLVs parsed does not equal the num params, tlvs: " + mTlvs.size()
                    + ", num params: " + mNumParams);
            return false;
        }
        return true;
    }

    @Nullable
    private Tlv getTlv(int tagType) {
        byte[] tagTypeByte = ConfigParam.getTagBytes(tagType);
        if (tagTypeByte.length > 1) {
            throw new IllegalArgumentException("Invalid tagType: " + Arrays.toString(tagTypeByte));
        }
        Tlv tlv = mTlvs.get(tagTypeByte[0]);
        if (tlv == null) {
            throw new IllegalArgumentException("Tag type: " + tagType + " not present");
        }
        return tlv;
    }

    public Byte getByte(int tagType) {
        Tlv tlv = getTlv(tagType);
        if (tlv.length != Byte.BYTES) {
            throw new IllegalArgumentException(
                    "Mismatch in value type, expected byte found len: " + tlv.length);
        }
        try {
            return ByteBuffer.wrap(tlv.value).get();
        } catch (BufferUnderflowException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public Short getShort(int tagType) {
        Tlv tlv = getTlv(tagType);
        if (tlv.length != Short.BYTES) {
            throw new IllegalArgumentException(
                    "Mismatch in value type, expected short found len: " + tlv.length);
        }
        try {
            return ByteBuffer.wrap(tlv.value).order(ByteOrder.LITTLE_ENDIAN).getShort();
        } catch (BufferUnderflowException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public Integer getInt(int tagType) {
        Tlv tlv = getTlv(tagType);
        if (tlv.length != Integer.BYTES) {
            throw new IllegalArgumentException(
                    "Mismatch in value type, expected int found len: " + tlv.length);
        }
        try {
            return ByteBuffer.wrap(tlv.value).order(ByteOrder.LITTLE_ENDIAN).getInt();
        } catch (BufferUnderflowException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public Long getLong(int tagType) {
        Tlv tlv = getTlv(tagType);
        if (tlv.length != Long.BYTES) {
            throw new IllegalArgumentException(
                    "Mismatch in value long, expected int found len: " + tlv.length);
        }
        try {
            return ByteBuffer.wrap(tlv.value).order(ByteOrder.LITTLE_ENDIAN).getLong();
        } catch (BufferUnderflowException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public byte[] getByteArray(int tagType) {
        Tlv tlv = getTlv(tagType);
        byte[] value = new byte[tlv.length];
        try {
            ByteBuffer.wrap(tlv.value).get(value);
            return value;
        } catch (BufferUnderflowException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
