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

package com.google.android.tv.btservices.remote;

public class TransportUtils {

    /**
     * Converts an array of bytes to a hex string.
     * @param bytes A byte array
     * @return A string of hex digits showing the content of the byte array.
     */
    public static String bytesToString(byte[] bytes) {
        if (bytes == null) {
            return "null";
        }

        StringBuilder builder = new StringBuilder();
        if (bytes.length > 0) {
            builder.append("0x");
        }
        for (byte b: bytes) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }

    /**
     * Converts a byte to a hex string.
     * @param b Input byte
     * @return A string representing the hex digits of this byte.
     */
    public static String byteToString(byte b) {
        return String.format("0x%02x", b);
    }

    /**
     * Parses an unsigned short (2 bytes) from an array of bytes.
     * @param bytes A byte array
     * @param st The starting index of the short.
     * @return An unsigned short (as int) parsed by reading two consecutive bytes in an array.
     */
    public static int parseUnsignedShort(byte[] bytes, int st) {
        return (bytes[st] & 0xff) + ((bytes[st + 1] & 0xff) << 8);
    }

    /**
     * Writes a short (2 bytes) to an array of bytes.
     * @param bytes A byte array to be written
     * @param st The starting index of the short.
     * @param val The value to be written to the array.
     */
    public static void writeUnsignedShort(byte[] bytes, int st, int val) {
        bytes[st] = (byte) (val & 0xff);
        bytes[st + 1] = (byte) ((val >> 8) & 0xff);
    }
}
