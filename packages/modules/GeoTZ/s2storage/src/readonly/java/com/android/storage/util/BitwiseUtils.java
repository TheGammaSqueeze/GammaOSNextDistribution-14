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

package com.android.storage.util;

/** Utility functions for bit twiddling. */
public final class BitwiseUtils {

    // [0]  = ...00001
    // [1]  = ...00010
    // [63] = 10000...
    private static final long[] SINGLE_BIT_MASK;

    static {
        SINGLE_BIT_MASK = new long[64];
        long bit = 1;
        for (int i = 0; i < 64; i++) {
            SINGLE_BIT_MASK[i] = bit;
            bit <<= 1;
        }
    }

    // [{How many LSB bits are 1}] = mask
    // [0]  = 00...000
    // [1]  = 00...001
    // [2]  = 00...011
    // [3]  = 00...111
    // ...
    // [63] = 01...111
    // [64] = 11...111
    private static final long[] LOW_BITS_MASK;

    static {
        LOW_BITS_MASK = new long[65];
        long value = 0;
        for (int i = 0; i < 65; i++) {
            LOW_BITS_MASK[i] = value;
            value = (value << 1) | 1;
        }
    }

    // [{How many MSB bits are 1}] = mask
    // [0]  = 000...00
    // [1]  = 100...00
    // [2]  = 110...00
    // [3]  = 111...00
    // ...
    // [63] = 111...10
    // [64] = 111...11
    private static final long[] HIGH_BITS_MASK;

    static {
        HIGH_BITS_MASK = new long[65];
        long value = ~0;
        for (int i = 0; i < 65; i++) {
            HIGH_BITS_MASK[64 - i] = value;
            value <<= 1;
        }
    }

    private BitwiseUtils() {
    }

    /**
     * Returns a mask covering the specified number of "low" bits. {@code bitCount} must be between
     * 0 and 64 inclusive. An argument of zero returns a mask with no bits set. Otherwise, numbering
     * bits from LSB = 0, this method returns bits 0 to (bitCount - 1) set to 1.
     */
    public static long getLowBitsMask(int bitCount) {
        if (bitCount < 0 || bitCount > 64) {
            throw new IllegalArgumentException("bitCount " + bitCount + " out of range");
        }
        // Bits | Bit pattern
        //    1 | 000...001
        //    2 | 000...011
        //    3 | 000...111
        // ...
        //   63 | 011...111
        //   64 | 111...111
        return LOW_BITS_MASK[bitCount];
    }

    /**
     * Returns (only) the {@code highBits} set of a {@code totalBitCount} value.
     */
    public static long getMidBitsMask(int totalBitCount, int highBitCount) {
        if (totalBitCount < 1 || totalBitCount > 64) {
            throw new IllegalArgumentException("totalBitCount " + totalBitCount + " out of range");
        }
        if (highBitCount < 1 || highBitCount > totalBitCount) {
            throw new IllegalArgumentException("highBitCount " + highBitCount + " out of range");
        }
        return HIGH_BITS_MASK[(64 - totalBitCount) + highBitCount] & LOW_BITS_MASK[totalBitCount];
    }

    /**
     * Returns the most significant {@code highBits} bits of a long.
     */
    public static long getHighBitsMask(int bitCount) {
        if (bitCount < 1 || bitCount > 64) {
            throw new IllegalArgumentException("bitCount " + bitCount + " out of range");
        }
        return HIGH_BITS_MASK[bitCount];
    }

    /** Returns the least significant {@code bitCount} bits of the specified value. */
    public static long extractLowBits(int bitCount, long value) {
        if (bitCount < 1 || bitCount > 64) {
            throw new IllegalArgumentException("bitCount " + bitCount + " out of range");
        } else if (bitCount == 64) {
            return value;
        }
        long mask = getLowBitsMask(bitCount);
        return mask & value;
    }

    /**
     * Returns a mask with only the numbered bit set to 1. Bits are numbered from LSB = 0 to
     * MSB = {type width - 1}.
     */
    public static long getSingleBitMask(int bit) {
        if (bit < 0 || bit > 63) {
            throw new IllegalArgumentException("bit " + bit + " out of range");
        }
        return SINGLE_BIT_MASK[bit];
    }

    /**
     * Throws an {@link IllegalArgumentException} if value is outside of the range representable in
     * {@code bitCount} bits as a signed or unsigned integer. Bit count must be between 1 and 64,
     * inclusive otherwise an {@link IllegalArgumentException} is thrown.
     */
    public static void checkValueInRange(int bitCount, long value, boolean signedValue) {
        if (signedValue) {
            checkSignedValueInRange(bitCount, value);
        } else {
            checkUnsignedValueInRange(bitCount, value);
        }
    }

    /**
     * Throws an {@link IllegalArgumentException} if value is outside of the range representable in
     * {@code bitCount} bits as a signed integer. Bit count must be between 1 and 64, inclusive
     * otherwise an {@link IllegalArgumentException} is thrown.
     */
    public static void checkSignedValueInRange(int bitCount, long value) {
        if (bitCount < 1 || bitCount > 64) {
            throw new IllegalArgumentException("bitCount " + bitCount + " out of range");
        }
        long minSignedValue = minSignedValue(bitCount);
        long maxSignedValue = maxSignedValue(bitCount);
        if (value < minSignedValue || value > maxSignedValue) {
            throw new IllegalArgumentException("value " + value + " out of range");
        }
    }

    /**
     * Throws an {@link IllegalArgumentException} if value is outside of the range representable in
     * {@code bitCount} bits as a signed integer. Bit count must be between 1 and 64, inclusive
     * otherwise an {@link IllegalArgumentException} is thrown.
     */
    public static void checkUnsignedValueInRange(int bitCount, long value) {
        if (bitCount < 0 || bitCount > 63) {
            throw new IllegalArgumentException("bitCount " + bitCount + " out of range");
        }
        long maxUnsignedValue = maxUnsignedValue(bitCount);
        if (value < 0 || value > maxUnsignedValue) {
            throw new IllegalArgumentException("value " + value + " out of range");
        }
    }

    /** Sign extend the {@code value} of {@code bitCount} to be a long. */
    public static long signExtendToLong(int bitCount, long value) {
        if (bitCount > 64) {
            throw new IllegalArgumentException("bitCount " + bitCount + " out of range");
        } else if (bitCount == 64) {
            return value;
        }
        long signBitMask = getSingleBitMask(bitCount - 1);
        // Is sign bit set?
        if ((signBitMask & value) == signBitMask) {
            // Extend value to long.
            value |= getHighBitsMask(Long.SIZE - bitCount);
        } else {
            // This clears bits above the sign bit.
            value &= getLowBitsMask(bitCount);
        }
        return value;
    }

    /**
     * Returns the most positive numeric value representable in {@code bitCount} when signed or
     * unsigned. {@code bitCount} must be between 1 and 64 inclusive for signed, or 1 and 63
     * inclusive for unsigned, or an {@link IllegalArgumentException} will be thrown.
     */
    public static long maxValue(int bitCount, boolean signedValue) {
        if (signedValue) {
            return maxSignedValue(bitCount);
        } else {
            return maxUnsignedValue(bitCount);
        }
    }

    /**
     * Returns the most positive numeric value that can be represented with the specified number of
     * bits when signed. {@code bitCount} must be between 1 and 64 inclusive or an
     * {@link IllegalArgumentException} will be thrown.
     *
     * Like {@link #maxUnsignedValue(int)} but returns the answer for signed types.
     */
    public static long maxSignedValue(int bitCount) {
        if (bitCount < 1 || bitCount > 64) {
            throw new IllegalArgumentException("bitCount " + bitCount + " out of range");
        }
        // Bits | Numeric | Bit pattern
        //    1 |       0 | 000...000
        //    2 |       1 | 000...001
        //    3 |       3 | 000...011
        //    4 |       7 | 000...111
        // ...
        //   64 |(2^63)-1 | 011...111
        return LOW_BITS_MASK[bitCount - 1];
    }

    /**
     * Returns the most positive numeric value that can be represented with the specified number of
     * bits when unsigned. {@code bitCount} must be between 1 and 63 inclusive or an
     * {@link IllegalArgumentException} will be thrown.
     *
     * Like {@link #getLowBitsMask(int)} but intended for numeric types.
     */
    public static long maxUnsignedValue(int bitCount) {
        if (bitCount < 1 || bitCount > 63) {
            throw new IllegalArgumentException("bitCount " + bitCount + " out of range");
        }
        // Bits | Numeric | Bit pattern
        //    1 |       1 | 000...001
        //    2 |       3 | 000...011
        //    3 |       7 | 000...111
        // ...
        //   63 |  (2^63) | 011...111
        return LOW_BITS_MASK[bitCount];
    }

    /**
     * Returns the most negative value representable in {@code bitCount} when signed or unsigned.
     * {@code bitCount} must be between 1 and 64 inclusive for signed, or 1 and 63 inclusive for
     * unsigned, or an {@link IllegalArgumentException} will be thrown.
     */
    public static long minValue(int bitCount, boolean signedValue) {
        if (signedValue) {
            return minSignedValue(bitCount);
        } else {
            if (bitCount < 1 || bitCount > 63) {
                throw new IllegalArgumentException("bitCount " + bitCount + " out of range");
            }
            return 0;
        }
    }

    /**
     * Returns the most negative numeric value that can be represented with the specified number of
     * bits. {@code bitCount} must be between 1 and 64 inclusive or an {@link
     * IllegalArgumentException} will be thrown.
     */
    public static long minSignedValue(int bitCount) {
        if (bitCount < 1 || bitCount > 64) {
            throw new IllegalArgumentException("bitCount " + bitCount + " out of range");
        }
        // Bits | Numeric | Bit pattern
        //    1 |      -1 | {sign extended} 1     (64 bits)
        //    2 |      -2 | {sign extended} 10    (63 bits)
        //    3 |      -4 | {sign extended} 100   (62 bits)
        //    4 |      -8 | {sign extended} 1000  (61 bits)
        // ...
        //   64 |  -(2^63)| 100...000               (1 bit)
        return HIGH_BITS_MASK[65 - bitCount];
    }

    /**
     * Prints out a human readable binary string for {@code value}, leading-zero padded to the
     * specified length. If {@code value} cannot be represented in {@code bitCount} characters, an
     * {@link IllegalArgumentException} is thrown.
     */
    public static String toUnsignedString(int bitCount, long value) {
        String binaryString = Long.toBinaryString(value);
        if (binaryString.length() > bitCount) {
            throw new IllegalArgumentException(
                    "value=" + value + " is not representable in " + bitCount + " bits");
        }
        StringBuilder builder = new StringBuilder(bitCount);
        for (int i = 0; i < bitCount - binaryString.length(); i++) {
            builder.append('0');
        }
        builder.append(binaryString);
        return builder.toString();
    }
}
