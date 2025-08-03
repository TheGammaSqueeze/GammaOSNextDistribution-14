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

package com.android.storage.table.packed.write;

import com.android.storage.io.write.TypedOutputStream;
import com.android.storage.table.packed.read.PackedTableReader;
import com.android.storage.util.BitwiseUtils;

import java.io.IOException;
import java.io.OutputStream;

/**
 * A class that knows how to write packed tables, i.e. tables where the entries made up of a fixed
 * width record made up of a key of N bits and a value making up the remaining bits. The table is
 * ordered by key.
 *
 * <p>This class is limited in that it can write entries (including the key bits) up to 8 bytes
 * wide. Keys are signed 31-bit numeric values. Entries can only be multiples of a byte.
 */
public final class PackedTableWriter implements AutoCloseable {

    private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

    private final TypedOutputStream mBlockDataOutputStream;

    private final int mEntrySizeBitCount;

    private final int mEntrySizeByteCount;

    private final int mKeySizeBits;

    private final boolean mSignedValue;

    private int mLastKeyAdded = -1;

    private int mEntryCount = 0;

    private boolean mIsOpen = true;

    private PackedTableWriter(OutputStream blockDataOutputStream, int entrySizeByteCount,
            int keySizeBits, boolean signedValue) {
        mBlockDataOutputStream = new TypedOutputStream(blockDataOutputStream);
        mEntrySizeByteCount = entrySizeByteCount;
        if (entrySizeByteCount < 1 || entrySizeByteCount > 8) {
            throw new IllegalArgumentException(
                    "Entry size cannot exceed 8 bytes, entrySizeByteCount=" + entrySizeByteCount);
        }

        mEntrySizeBitCount = entrySizeByteCount * 8;
        mKeySizeBits = keySizeBits;
        int maxKeySizeBits = Math.min(31, mEntrySizeBitCount - 1);
        if (keySizeBits < 0 || keySizeBits > maxKeySizeBits) {
            throw new IllegalArgumentException("Keys can be up to " + maxKeySizeBits
                    + " bits in length, keySizeBits=" + keySizeBits);
        }
        mSignedValue = signedValue;
    }

    /**
     * Creates a {@link PackedTableWriter} to write to the specified stream with the specified
     * parameters. Entries must be added in key order via {@link #addEntry(int, long)}.
     *
     * @param outputStream the stream to write to
     * @param entrySizeBytes the number of bytes per entry
     * @param keyBits the number of bits in the key
     * @param signedValue true if the values written via {@link #addEntry(int, long)} should be
     * sign extended
     * @param sharedData data to write into the table's header, i.e. that applies to all entries in
     * the table
     * @throws IllegalArgumentException if the parameters are invalid
     */
    public static PackedTableWriter create(OutputStream outputStream, int entrySizeBytes,
            int keyBits, boolean signedValue, byte[] sharedData) throws IOException {
        PackedTableWriter packedTableWriter =
                new PackedTableWriter(outputStream, entrySizeBytes, keyBits, signedValue);
        packedTableWriter.writeHeader(sharedData);
        return packedTableWriter;
    }

    private void writeHeader(byte[] sharedData) throws IOException {
        if (sharedData == null) {
            sharedData = EMPTY_BYTE_ARRAY;
        }
        mBlockDataOutputStream.writeTinyByteArray(sharedData);

        int bitField = 0;
        if (mSignedValue) {
            bitField |= PackedTableReader.BIT_FIELD_SIGNED_VALUE_MASK;
        }
        mBlockDataOutputStream.writeUnsignedByte(bitField);
        mBlockDataOutputStream.writeUnsignedByte(mEntrySizeByteCount);
        mBlockDataOutputStream.writeUnsignedByte(mKeySizeBits);
    }

    /**
     * Adds an entry to the table. Keys must be added in order but multiple entries can have the
     * same key.
     */
    public void addEntry(int key, long value) throws IOException {
        checkIsOpen();

        if (key < mLastKeyAdded) {
            throw new IllegalArgumentException("Entries must be added in key order."
                    + " lastKeyAdded=" + mLastKeyAdded + ", key=" + key);
        }

        long entryBytes = createEntryBytes(mEntrySizeBitCount, mKeySizeBits, key, value,
                mSignedValue);
        mBlockDataOutputStream.writeVarByteValue(mEntrySizeByteCount, entryBytes);
        mLastKeyAdded = key;
        mEntryCount++;
    }

    private static long createEntryBytes(
            int entrySizeBits, int keySizeBits, int key, long value, boolean signedValue) {
        if (keySizeBits >= entrySizeBits) {
            throw new IllegalArgumentException("keySizeBits=" + keySizeBits
                    + " must not exceed " + entrySizeBits);
        }
        BitwiseUtils.checkUnsignedValueInRange(keySizeBits, key);
        int valueSizeBits = entrySizeBits - keySizeBits;
        BitwiseUtils.checkValueInRange(valueSizeBits, value, signedValue);

        long entryBytes = ((long) key) << valueSizeBits;

        long valueMask = BitwiseUtils.getLowBitsMask(valueSizeBits);
        entryBytes |= (value & valueMask);
        return entryBytes;
    }

    /** Returns the number of entries written so far. */
    public int getEntryCount() {
        return mEntryCount;
    }

    /** Returns {@code true} unless {@link #close()} has been called. */
    public boolean isOpen() {
        return mIsOpen;
    }

    @Override
    public void close() throws IOException {
        mBlockDataOutputStream.close();
        mIsOpen = false;
    }

    private void checkIsOpen() {
        if (!mIsOpen) {
            throw new IllegalStateException("closed");
        }
    }
}
