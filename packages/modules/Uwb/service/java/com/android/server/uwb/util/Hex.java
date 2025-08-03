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

import androidx.annotation.VisibleForTesting;

import com.google.common.base.Preconditions;

/**
 * Utility class for converting hex strings to and from byte arrays.
 *
 * <p>We can't use org.apache.commons.codec.binary.Hex because Android already hides it as part of
 * /system/frameworks/ext.jar
 *
 * <p>Unlike standard org.apache.commons.codec.binary.Hex we allow strings with odd length to be
 * decoded, in order to accommodate unusual partner decisions.
 */
public class Hex {
    /** Base-16 encoding/decoding. */
    @VisibleForTesting static final int RADIX = 16;

    /** Upper-case encoding. */
    @VisibleForTesting
    static final char[] UPPER = {
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F',
    };

    /** Lower-case encoding. */
    @VisibleForTesting
    static final char[] LOWER = {
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f',
    };

    /** No instances. */
    private Hex() {}

    /** Returns a lower-case hex string encoding of the given byte array. */
    public static String encode(byte[] bytes) {
        return doEncode(bytes, LOWER);
    }

    /** Returns an upper-case hex string encoding of the given byte array. */
    public static String encodeUpper(byte[] bytes) {
        return doEncode(bytes, UPPER);
    }

    /**
     * Decode the hex string to byte array.
     */
    public static byte[] decode(String s) throws IllegalArgumentException {
        if (s.length() % 2 != 0) {
            s = "0" + s;
        }
        return decodeEven(s);
    }

    /**
     * Returns the byte array represented by the given string. Can handle both upper- and lower-case
     * ASCII characters.
     *
     * @throws IllegalArgumentException if the string is not of even length, or if it contains a
     *     non-hexadecimal character.
     */
    private static byte[] decodeEven(String s) throws IllegalArgumentException {
        int length = s.length();
        Preconditions.checkArgument(length % 2 == 0, "String not of even length: %s", s);
        byte[] result = new byte[length / 2];
        int resultPos = 0;
        for (int pos = 0; pos < length; pos += 2) {
            char c0 = s.charAt(pos);
            char c1 = s.charAt(pos + 1);
            int n0 = Character.digit(c0, RADIX);
            int n1 = Character.digit(c1, RADIX);
            Preconditions.checkArgument(n0 != -1, "Invalid character: '%s'", String.valueOf(c0));
            Preconditions.checkArgument(n1 != -1, "Invalid character: '%s'", String.valueOf(c1));
            result[resultPos++] = (byte) (n0 << 4 | n1);
        }
        return result;
    }

    /** Returns a hex string encoding of the given byte array using the given alphabet. */
    private static String doEncode(byte[] bytes, char[] alphabet) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(alphabet[(b & 0xF0) >> 4]).append(alphabet[b & 0x0F]);
        }
        return sb.toString();
    }
}
