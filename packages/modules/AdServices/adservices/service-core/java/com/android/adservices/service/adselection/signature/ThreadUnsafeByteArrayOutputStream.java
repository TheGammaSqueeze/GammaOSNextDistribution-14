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

package com.android.adservices.service.adselection.signature;

import com.android.internal.annotations.VisibleForTesting;

import java.util.Arrays;

/** ByteArrayStream implementation that reduces amount of copy operation */
public class ThreadUnsafeByteArrayOutputStream {
    private static final int ONE_KILOBYTE = 1024;
    private static final int DEFAULT_INITIAL_CAPACITY = 100 * ONE_KILOBYTE;
    private static final int SOFT_MAX_ARRAY_LENGTH = Integer.MAX_VALUE - 8;
    private byte[] mBuffer;
    private int mCount;

    public ThreadUnsafeByteArrayOutputStream() {
        this.mBuffer = new byte[DEFAULT_INITIAL_CAPACITY];
        this.mCount = 0;
    }

    public ThreadUnsafeByteArrayOutputStream(int initialCapacity) {
        this.mBuffer = new byte[initialCapacity];
        this.mCount = 0;
    }

    /** Returns a copy of the exact bytes inserted into the stream */
    public byte[] getBytes() {
        return Arrays.copyOfRange(mBuffer, 0, mCount);
    }

    /** Return the internal buffer object */
    @VisibleForTesting
    byte[] getBuffer() {
        return mBuffer;
    }

    /** Return the internal count of inserted bytes */
    @VisibleForTesting
    int getCount() {
        return mCount;
    }

    /** Writes a byte[] into the stream */
    public void writeBytes(byte[] bytes) {
        ensureCapacity(mCount + bytes.length);
        System.arraycopy(bytes, 0, mBuffer, mCount, bytes.length);
        mCount += bytes.length;
    }

    /** Writes a single byte into the stream */
    public void write(int b) {
        ensureCapacity(mCount + 1);
        mBuffer[mCount] = (byte) b;
        mCount++;
    }

    private static int calculateNewLength(int oldLength, int minGrowth, int prefGrowth) {
        int newLength = oldLength + Math.max(minGrowth, prefGrowth);
        if (newLength - SOFT_MAX_ARRAY_LENGTH <= 0) {
            return newLength;
        } else {
            return hugeLength(oldLength, minGrowth);
        }
    }

    private static int hugeLength(int oldLength, int minGrowth) {
        int newLength = oldLength + minGrowth;
        if (newLength < 0) {
            throw new OutOfMemoryError("Required array length too large");
        }
        return Math.max(newLength, SOFT_MAX_ARRAY_LENGTH);
    }

    private void ensureCapacity(int minCapacity) {
        if (minCapacity - mBuffer.length > 0) {
            mBuffer =
                    Arrays.copyOf(
                            mBuffer,
                            calculateNewLength(
                                    mBuffer.length, minCapacity - mBuffer.length, mBuffer.length));
        }
    }
}
