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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class TlvUtil {
    public static final byte[] getBytes(byte data) {
        ByteBuffer buffer = ByteBuffer.allocate(Byte.BYTES).put(data);
        return buffer.array();
    }

    public static final byte[] getBytes(short data) {
        ByteBuffer buffer = ByteBuffer.allocate(Short.BYTES).order(
                ByteOrder.BIG_ENDIAN).putShort(data);
        return buffer.array();
    }

    public static final byte[] getLeBytes(short data) {
        ByteBuffer buffer = ByteBuffer.allocate(Short.BYTES).order(
                ByteOrder.LITTLE_ENDIAN).putShort(data);
        return buffer.array();
    }

    public static final byte[] getBytes(int data) {
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES).order(
                ByteOrder.BIG_ENDIAN).putInt(data);
        return buffer.array();
    }

    public static final byte[] getLeBytes(int data) {
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES).order(
                ByteOrder.LITTLE_ENDIAN).putInt(data);
        return buffer.array();
    }

    public static final byte[] getBytes(long data) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES).order(
                ByteOrder.BIG_ENDIAN).putLong(data);
        return buffer.array();
    }

    public static final byte[] getLeBytes(long data) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES).order(
                ByteOrder.LITTLE_ENDIAN).putLong(data);
        return buffer.array();
    }

    public static final byte[] getReverseBytes(byte[] data) {
        byte[] buffer = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            buffer[i] = data[data.length - 1 - i];
        }
        return buffer;
    }

    public static final byte[] getBytes(int data, int start, int length) {
        ByteBuffer srcBuf = ByteBuffer.allocate(Integer.BYTES).putInt(data);
        ByteBuffer dstBuf = ByteBuffer.allocate(length);
        srcBuf.position(start);
        dstBuf.put(srcBuf);
        return dstBuf.array();
    }

    public static final byte[] getBytesWithLeftPadding(int size, byte[] data) {
        ByteBuffer buffer = ByteBuffer.allocate(size);
        int startOffset = size - data.length;
        buffer.position(startOffset);
        buffer.put(data);
        return buffer.array();
    }

    public static final byte[] getBytesWithRightPadding(int size, byte[] data) {
        ByteBuffer buffer = ByteBuffer.allocate(size).put(data);
        return buffer.array();
    }

    /** Convert RSTU to microsecond */
    public static final int rstuToUs(int value) {
        return (int) (value * 416 / 499.2);
    }
}
