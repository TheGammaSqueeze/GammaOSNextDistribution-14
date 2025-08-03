/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.server.nearby.util;

import java.util.Arrays;

/**
 * ArrayUtils class that help manipulate array.
 */
public class ArrayUtils {
    private static final char[] HEX_UPPERCASE = {
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
    };

    /** Concatenate N arrays of bytes into a single array. */
    public static byte[] concatByteArrays(byte[]... arrays) {
        // Degenerate case - no input provided.
        if (arrays.length == 0) {
            return new byte[0];
        }

        // Compute the total size.
        int totalSize = 0;
        for (int i = 0; i < arrays.length; i++) {
            totalSize += arrays[i].length;
        }

        // Copy the arrays into the new array.
        byte[] result = Arrays.copyOf(arrays[0], totalSize);
        int pos = arrays[0].length;
        for (int i = 1; i < arrays.length; i++) {
            byte[] current = arrays[i];
            System.arraycopy(current, 0, result, pos, current.length);
            pos += current.length;
        }
        return result;
    }

    /**
     * @return true when the array is null or length is 0
     */
    public static boolean isEmpty(byte[] bytes) {
        return bytes == null || bytes.length == 0;
    }

    /** Appends a byte array to a byte. */
    public static byte[] append(byte a, byte[] b) {
        if (b == null) {
            return new byte[]{a};
        }

        int length = b.length;
        byte[] result = new byte[length + 1];
        result[0] = a;
        System.arraycopy(b, 0, result, 1, length);
        return result;
    }

    /**
     * Converts an Integer to a 2-byte array.
     */
    public static byte[] intToByteArray(int value) {
        return new byte[] {(byte) (value >> 8), (byte) value};
    }

    /** Appends a byte to a byte array. */
    public static byte[] append(byte[] a, byte b) {
        if (a == null) {
            return new byte[]{b};
        }

        int length = a.length;
        byte[] result = new byte[length + 1];
        System.arraycopy(a, 0, result, 0, length);
        result[length] = b;
        return result;
    }

    /** Convert an hex string to a byte array. */

    public static byte[] stringToBytes(String hex) throws IllegalArgumentException {
        int length = hex.length();
        if (length % 2 != 0) {
            throw new IllegalArgumentException("Hex string has odd number of characters");
        }
        byte[] out = new byte[length / 2];
        for (int i = 0; i < length; i += 2) {
            // Byte.parseByte() doesn't work here because it expects a hex value in -128, 127, and
            // our hex values are in 0, 255.
            out[i / 2] = (byte) Integer.parseInt(hex.substring(i, i + 2), 16);
        }
        return out;
    }

    /** Encodes a byte array as a hexadecimal representation of bytes. */
    public static String bytesToStringUppercase(byte[] bytes) {
        int length = bytes.length;
        StringBuilder out = new StringBuilder(length * 2);
        for (int i = 0; i < length; i++) {
            if (i == length - 1 && (bytes[i] & 0xff) == 0) {
                break;
            }
            out.append(HEX_UPPERCASE[(bytes[i] & 0xf0) >>> 4]);
            out.append(HEX_UPPERCASE[bytes[i] & 0x0f]);
        }
        return out.toString();
    }
}
