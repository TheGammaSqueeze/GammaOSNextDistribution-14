/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.storage.block.read;

import com.android.storage.io.read.TypedInputStream;
import com.android.storage.util.BitwiseUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;

/**
 * Provides typed, absolute position, random access to a block's data.
 *
 * <p>See also {@link TypedInputStream} for a streamed
 * equivalent.
 */
public final class BlockData {

    private final ByteBuffer mDataBytes;

    /** Wraps a read-only, big-endian {@link ByteBuffer}. */
    public BlockData(ByteBuffer dataBytes) {
        if (!dataBytes.isReadOnly()) {
            throw new IllegalArgumentException("dataBytes must be readonly");
        }
        if (dataBytes.order() != ByteOrder.BIG_ENDIAN) {
            throw new IllegalArgumentException("dataBytes must be big-endian");
        }
        mDataBytes = Objects.requireNonNull(dataBytes);
    }

    /** Returns a copy of the underlying {@link ByteBuffer}. */
    public ByteBuffer getByteBuffer() {
        return mDataBytes.duplicate();
    }

    /** Returns the value of the byte at the specified position. */
    public byte getByte(int byteOffset) {
        return mDataBytes.get(byteOffset);
    }

    /** Returns the value of the byte at the specified position as an unsigned value. */
    public int getUnsignedByte(int byteOffset) {
        return mDataBytes.get(byteOffset) & 0xFF;
    }

    /** Returns the value of the 16-bit char at the specified position as an unsigned value. */
    public char getChar(int byteOffset) {
        return mDataBytes.getChar(byteOffset);
    }

    /** Returns the value of the 32-bit int at the specified position as an signed value. */
    public int getInt(int byteOffset) {
        return mDataBytes.getInt(byteOffset);
    }

    /** Returns the value of the 64-bit long at the specified position as an signed value. */
    public long getLong(int byteOffset) {
        return mDataBytes.getLong(byteOffset);
    }

    /**
     * Returns a tiny (<= 255 entries) array of signed bytes starting at the specified position,
     * where the length is encoded in the data.
     */
    public byte[] getTinyByteArray(int byteOffset) {
        int size = getUnsignedByte(byteOffset);
        return getBytes(byteOffset + 1, size);
    }

    /**
     * Returns an array of signed bytes starting at the specified position.
     */
    public byte[] getBytes(int byteOffset, int byteCount) {
        byte[] bytes = new byte[byteCount];
        for (int i = 0; i < byteCount; i++) {
            bytes[i] = mDataBytes.get(byteOffset + i);
        }
        return bytes;
    }

    /**
     * Returns a tiny (<= 255 entries) array of chars starting at the specified position, where the
     * length is encoded in the data.
     */
    public char[] getTinyCharArray(int byteOffset) {
        int size = getUnsignedByte(byteOffset);
        return getChars(byteOffset + 1, size);
    }

    /**
     * Returns an array of chars starting at the specified position.
     */
    public char[] getChars(int byteOffset, int charCount) {
        char[] array = new char[charCount];
        for (int i = 0; i < charCount; i++) {
            array[i] = getChar(byteOffset);
            byteOffset += Character.BYTES;
        }
        return array;
    }

    /**
     * Returns 1-8 bytes ({@code valueSizeBytes}) starting as the specified position as a
     * {@code long}. The value can be interpreted as signed or unsigned depending on
     * {@code signExtend}.
     */
    public long getValueAsLong(int valueSizeBytes, int byteOffset, boolean signExtend) {
        if (valueSizeBytes < 0 || valueSizeBytes > Long.BYTES) {
            throw new IllegalArgumentException("valueSizeBytes must be <= 8 bytes");
        }
        return getValueInternal(valueSizeBytes, byteOffset, signExtend);
    }

    /** Returns the size of the block data. */
    public int getSize() {
        return mDataBytes.limit();
    }

    private long getValueInternal(int valueSizeBytes, int byteOffset, boolean signExtend) {
        if (byteOffset < 0) {
            throw new IllegalArgumentException(
                    "byteOffset=" + byteOffset + " must not be negative");
        }

        // High bytes read first.
        long value = 0;
        int bytesRead = 0;
        while (bytesRead++ < valueSizeBytes) {
            value <<= 8;
            value |= (mDataBytes.get(byteOffset++) & 0xFF);
        }
        if (valueSizeBytes < 8 && signExtend) {
            int entrySizeBits = valueSizeBytes * Byte.SIZE;
            value = BitwiseUtils.signExtendToLong(entrySizeBits, value);
        }
        return value;
    }
}
