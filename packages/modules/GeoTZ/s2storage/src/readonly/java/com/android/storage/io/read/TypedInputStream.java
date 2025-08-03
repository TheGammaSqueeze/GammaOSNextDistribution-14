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

package com.android.storage.io.read;

import com.android.storage.block.read.BlockData;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * A typed stream-based typed data accessor.
 *
 * See {@link BlockData} for a random-access equivalent.
 */
public final class TypedInputStream implements Closeable {

    private final DataInputStream mDataInputStream;

    /** Wraps an InputStream. */
    public TypedInputStream(InputStream is) {
        mDataInputStream = new DataInputStream(new BufferedInputStream(is, 8192));
    }

    /** Skip forward the specified number of bytes. */
    public int skipBytes(int n) throws IOException {
        return mDataInputStream.skipBytes(n);
    }

    /** Reads the next byte as an unsigned value. */
    public int readUnsignedByte() throws IOException {
        return mDataInputStream.readByte() & 0xFF;
    }

    /** Reads the next byte as a signed value. */
    public byte readSignedByte() throws IOException {
        return mDataInputStream.readByte();
    }

    /**
     * Reads a tiny (<= 255 entries) array of signed bytes where the length is encoded in the
     * data.
     */
    public byte[] readTinyVarByteArray() throws IOException {
        int arraySize = readUnsignedByte();
        byte[] bytes = new byte[arraySize];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) mDataInputStream.readUnsignedByte();
        }
        return bytes;
    }

    /**
     * Reads a tiny (<= 255 entries) array of chars where the length is encoded in the data.
     */
    public char[] readTinyVarCharArray() throws IOException {
        int arraySize = readUnsignedByte();
        char[] chars = new char[arraySize];
        for (int i = 0; i < chars.length; i++) {
            chars[i] = mDataInputStream.readChar();
        }
        return chars;
    }

    /**
     * Reads a char in network byte order.
     */
    public char readChar() throws IOException {
        return mDataInputStream.readChar();
    }

    /**
     * Reads a signed int in network byte order.
     */
    public int readInt() throws IOException {
        return mDataInputStream.readInt();
    }

    /**
     * Reads a signed long in network byte order.
     */
    public long readLong() throws IOException {
        return mDataInputStream.readLong();
    }

    @Override
    public void close() throws IOException {
        mDataInputStream.close();
    }
}
