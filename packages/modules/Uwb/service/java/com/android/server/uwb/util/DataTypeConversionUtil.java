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
package com.android.server.uwb.util;

import static com.android.server.uwb.data.UwbUciConstants.UWB_DEVICE_EXT_MAC_ADDRESS_LEN;
import static com.android.server.uwb.data.UwbUciConstants.UWB_DEVICE_SHORT_MAC_ADDRESS_LEN;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Utility class for doing conversions, including bytes, hex strings, ints, and ASCII. */
public class DataTypeConversionUtil {

    private static final char[] HEX_ARRAY = {
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
    };

    /**
     * Conver the hex string to byte array.
     */
    public static byte[] hexStringToByteArray(String hex) {
        // remove whitespace in the hex string.
        hex = hex.replaceAll("\\s", "");

        int len = hex.length();
        if (len % 2 != 0) {
            // Pad the hex string with a leading zero.
            hex = String.format("0%s", hex);
            len++;
        }
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] =
                    (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                            | Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    /**
     * Convert the byte array to hex string.
     */
    @NonNull
    public static String byteArrayToHexString(@Nullable byte[] response) {
        if (response == null) {
            return "";
        }
        return byteArrayToHexString(response, 0, response.length);
    }

    /**
     * Convertt part of the byte array to hex string.
     */
    public static String byteArrayToHexString(
            byte[] response, int startIndex, int endIndex) {
        char[] hex = new char[(endIndex - startIndex) * 2];
        int v;
        for (int i = 0; i < endIndex - startIndex; i++) {
            v = unsignedByteToInt(response[startIndex + i]);
            hex[i * 2] = HEX_ARRAY[v >> 4];
            hex[i * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hex);
    }

    /**
     * Convert the byte to int.
     */
    public static int unsignedByteToInt(byte b) {
        return b & 0xFF;
    }

    /**
     * Convert the int to byte.
     */
    public static byte unsignedIntToByte(int n) {
        return (byte) (n & 0xFF);
    }

    /**
     * Convert the byte array to int16 using big endian.
     */
    public static short byteArrayToI16(byte[] bytes) {
        if (bytes.length != 2) {
            throw new NumberFormatException("Expected length 2 but was " + bytes.length);
        }
        return ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).getShort();
    }

    /**
     * Convert the byte array to int using big endian.
     */
    public static int byteArrayToI32(byte[] bytes) {
        if (bytes.length != 4) {
            throw new NumberFormatException("Expected length 4 but was " + bytes.length);
        }
        return ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).getInt();
    }

    /**
     * Convert the byte array with arbitrary size less than 5 to int using big endian.
     */
    public static int arbitraryByteArrayToI32(byte[] bytes) {
        if (bytes.length > 4 || bytes.length < 1) {
            throw new NumberFormatException("Expected length less than 5 but was " + bytes.length);
        }
        ByteBuffer byteBuffer = ByteBuffer.allocate(Integer.BYTES);
        byteBuffer.position(Integer.BYTES - bytes.length);
        byteBuffer.put(bytes).rewind();
        return byteBuffer.getInt();
    }

    /**
     * Convert the int to byte array using big endian.
     */
    public static byte[] i32ToByteArray(int n) {
        return ByteBuffer.allocate(Integer.BYTES).order(ByteOrder.BIG_ENDIAN).putInt(n).array();
    }

    /**
     * Convert the int to byte array using little endian.
     */
    public static byte[] i32ToLeByteArray(int n) {
        return ByteBuffer.allocate(Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN).putInt(n).array();
    }

    /**
     * Convert the byte array (in Little Endian format) to a long. The input array could be: of
     * shorter size (eg: 2 bytes, to represent a shortMacAddress). It could also have length of 8
     * bytes, but have the MSB 6 bytes zeroed out (the 2 LSB bytes contain the MacAddress).
     */
    public static long macAddressByteArrayToLong(byte[] bytes) {
        if (bytes.length == 2) {
            return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getShort();
        } else if (bytes.length == 4) {
            return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
        } else if (bytes.length == 8) {
            if (isExtendedMSBZeroedOut(bytes)) {
                return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getShort();
            } else {
                return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getLong();
            }
        } else {
            throw new NumberFormatException("Expected length one of (2, 4, 8) but was "
                    + bytes.length);
        }
    }

    /**
     * Convert the byte array that contains a Short MacAddress format (2 bytes long), into an
     * Extended MacAddress format (8 bytes long), by padding it with 6 MSB zeroed-out bytes.
     */
    public static byte[] convertShortMacAddressBytesToExtended(byte[] bytes) {
        if (bytes.length == UWB_DEVICE_SHORT_MAC_ADDRESS_LEN) {
            return ByteBuffer.allocate(UWB_DEVICE_EXT_MAC_ADDRESS_LEN).put(bytes).array();
        } else if (bytes.length == UWB_DEVICE_EXT_MAC_ADDRESS_LEN) {
            return bytes;
        } else {
            throw new NumberFormatException("Expected length one of (2, 8) but was "
                    + bytes.length);
        }
    }

    // Check if the MSB bytes are zeroed out.
    private static boolean isExtendedMSBZeroedOut(byte[] bytes) {
        for (int i = UWB_DEVICE_SHORT_MAC_ADDRESS_LEN; i < UWB_DEVICE_EXT_MAC_ADDRESS_LEN; i++) {
            if (bytes[i] != 0) return false;
        }
        return true;
    }
    private DataTypeConversionUtil() {}
}
