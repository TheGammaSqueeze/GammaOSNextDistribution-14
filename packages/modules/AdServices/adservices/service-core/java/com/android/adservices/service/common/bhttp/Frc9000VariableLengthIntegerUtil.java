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

package com.android.adservices.service.common.bhttp;

import android.annotation.NonNull;

/**
 * Utility class read or write FRC 9000 variable length integer.
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc9000#name-variable-length-integer-enc">FRC
 *     9000 Variable Length Integer</a>
 */
public class Frc9000VariableLengthIntegerUtil {

    /** Returns the byte array represents the given integer. */
    @NonNull
    public static byte[] toFrc9000Int(long i) {
        if ((i & 0xc000000000000000L) != 0) {
            throw new IllegalArgumentException(
                    "FRC 9000 can not represents larger then 0x4000000000000000L or negative"
                            + " integer.");
        }
        if (i >= 0x40000000) {
            return new byte[] {
                (byte) (0xc0 | (i >> 56)), // Replace first 2 bits as 0b11.
                (byte) (i >> 48),
                (byte) (i >> 40),
                (byte) (i >> 32),
                (byte) (i >> 24),
                (byte) (i >> 16),
                (byte) (i >> 8),
                (byte) i
            };
        }
        if (i >= 0x00004000) {
            return new byte[] {
                (byte) (0x80 | (i >> 24)), // Replace first 2 bits as 0b10.
                (byte) (i >> 16),
                (byte) (i >> 8),
                (byte) i
            };
        }
        if (i >= 0x00000040) {
            return new byte[] {
                (byte) (0x40 | (i >> 8)), // Replace first 2 bits as 0b01.
                (byte) i
            };
        }
        return new byte[] {(byte) i};
    }
}
