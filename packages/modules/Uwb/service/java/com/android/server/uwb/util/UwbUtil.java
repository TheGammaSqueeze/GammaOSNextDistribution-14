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
// TODO: deprecated UwbUtil, consider to use com.android.server.uwb.util.Hex
// and com.android.server.uwb.util.DataTypeConversionUtil
public final class UwbUtil {
    private static final char[] HEXCHARS = {'0', '1', '2', '3', '4', '5', '6', '7',
            '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

    public static String toHexString(byte b) {
        StringBuffer sb = new StringBuffer(2);
        sb.append(HEXCHARS[(b >> 4) & 0xF]);
        sb.append(HEXCHARS[b & 0xF]);
        return sb.toString();
    }

    public static String toHexString(byte[] data) {
        StringBuffer sb = new StringBuffer();
        if (data == null) {
            return null;
        }
        for (int i = 0; i != data.length; i++) {
            int b = data[i] & 0xff;
            sb.append(HEXCHARS[(b >> 4) & 0xF]);
            sb.append(HEXCHARS[b & 0xF]);
        }
        return sb.toString();
    }

    /** Convert the given int to a 4-byte hex-string */
    public static String toHexString(int var) {
        byte[] byteArray = new byte[4];
        byteArray[0] = (byte) (var & 0xff);
        byteArray[1] = (byte) ((var >> 8) & 0xff);
        byteArray[2] = (byte) ((var >> 16) & 0xff);
        byteArray[3] = (byte) ((var >> 24) & 0xff);
        StringBuilder sb = new StringBuilder();
        for (byte b : byteArray) {
            sb.append(HEXCHARS[(b >> 4) & 0xF]);
            sb.append(HEXCHARS[b & 0xF]);
        }
        return sb.toString();
    }

    /** Convert the given long to an 8-byte hex-string */
    public static String toHexString(long var) {
        byte[] byteArray = new byte[8];
        byteArray[0] = (byte) (var & 0xff);
        byteArray[1] = (byte) ((var >> 8) & 0xff);
        byteArray[2] = (byte) ((var >> 16) & 0xff);
        byteArray[3] = (byte) ((var >> 24) & 0xff);
        byteArray[4] = (byte) ((var >> 32) & 0xff);
        byteArray[5] = (byte) ((var >> 40) & 0xff);
        byteArray[6] = (byte) ((var >> 48) & 0xff);
        byteArray[7] = (byte) ((var >> 56) & 0xff);

        StringBuilder sb = new StringBuilder();
        for (byte b : byteArray) {
            sb.append(HEXCHARS[(b >> 4) & 0xF]);
            sb.append(HEXCHARS[b & 0xF]);
        }
        return sb.toString();
    }

    public static byte[] getByteArray(String valueString) {
        int len = valueString.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(valueString.charAt(i), 16) << 4)
                    + Character.digit(valueString.charAt(i + 1), 16));
        }
        return data;
    }

    public static float degreeToRadian(float angleInDegrees) {
        return (float) ((angleInDegrees) * Math.PI / 180.0);
    }

    public static float radianTodegree(double angleInRadians) {
        return (float) ((angleInRadians) * 180 / Math.PI);
    }

    /**
     * Fixed point Q format to float conversion. In Q format  Fixed point integer,
     * integer and fractional bits are specified together.
     * Q10.6 format = > 10 bits integer and 6 bits fractional
     *
     * @param qIn    Integer in Qformat
     * @param nInts  number of integer bits
     * @param nFracs number of fractional bits
     * @return converted float value
     */
    public static float convertQFormatToFloat(int qIn, int nInts, int nFracs) {
        int intPart = (qIn >> nFracs); // extract integer part
        double fracPart = qIn & ((1 << nFracs) - 1); //extract fractional part
        fracPart = Math.pow(2, -nFracs) * fracPart; //convert fractional bits to float
        return (float) ((float) intPart + fracPart);
    }

    public static float toSignedFloat(int nInput, int nBits, int nDivider) {
        float value = 0;
        if (nDivider > 0) {
            value = (float) (nInput - nBits) / nDivider;
        } else {
            value = (float) nInput;
        }
        return value;
    }

    /**
     * Get Two's complement of a number for signed conversion
     *
     * @param nInput Integer
     * @param nBits  number of bits an number
     * @return two complement of given number value
     */
    public static int twos_compliment(int nInput, int nBits) {
        if ((nInput & (1 << (nBits - 1))) != 0)  { // if sign bit is set, Eg- nInput=1111, nBits=4
            nInput -= 1 << nBits;                  // compute negative value ,0b1111-0b10000= -1
        }
        return nInput;                             // return positive value as is
    }

    /**
     * Fixed point float to Q format conversion. In Q format Fixed point integer,
     * integer and fractional bits are specified together.
     * Q10.6 format = > 10 bits integer and 6 bits fractional
     *
     * @param in     signed Float
     * @param nInts  number of integer bits
     * @param nFracs number of fractional bits
     * @return converted Q format value
     */
    public static int convertFloatToQFormat(float in, int nInts, int nFracs) {
        int qInt, qFracs, inputStream;
        if (in >= 0) {
            qInt = (int) in;
            qFracs = (int) ((in - qInt) * (1 << (nFracs)));
            inputStream = (qInt << nFracs) + qFracs;
        } else {
            qInt = (int) Math.floor(in);
            qFracs = (int) ((in - qInt) * (1 << (nFracs)));
            inputStream = (((1 << (nInts + 1)) + qInt) << nFracs) + qFracs;
        }

        return inputStream;
    }

    public static boolean isBitSet(int flags, int mask) {
        return (flags & mask) != 0;
    }
}
