/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.server.wifi.util;

import java.util.Calendar;
import java.util.Random;
import java.util.stream.Collectors;

/** Basic string utilities */
public class StringUtil {
    static final byte ASCII_PRINTABLE_MIN = ' ';
    static final byte ASCII_PRINTABLE_MAX = '~';

    /** Returns true if-and-only-if |byteArray| can be safely printed as ASCII. */
    public static boolean isAsciiPrintable(byte[] byteArray) {
        if (byteArray == null) {
            return true;
        }

        for (byte b : byteArray) {
            switch (b) {
                // Control characters which actually are printable. Fall-throughs are deliberate.
                case 0x07:      // bell ('\a' not recognized in Java)
                case '\f':      // form feed
                case '\n':      // new line
                case '\t':      // horizontal tab
                case 0x0b:      // vertical tab ('\v' not recognized in Java)
                    continue;
            }

            if (b < ASCII_PRINTABLE_MIN || b > ASCII_PRINTABLE_MAX) {
                return false;
            }
        }

        return true;
    }

    /** Returns a random number string. */
    public static String generateRandomNumberString(int length) {
        final String pool = "0123456789";
        return new Random(System.currentTimeMillis())
                .ints(length, 0, pool.length())
                .mapToObj(i -> Character.toString(pool.charAt(i)))
                .collect(Collectors.joining());
    }

    /** Returns a random string which consists of alphabets and numbers. */
    public static String generateRandomString(int length) {
        final String pool = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        return new Random(System.currentTimeMillis())
                .ints(length, 0, pool.length())
                .mapToObj(i -> Character.toString(pool.charAt(i)))
                .collect(Collectors.joining());
    }

    /** Returns the date and time of the calendar as a formatted string. */
    public static String calendarToString(Calendar c) {
        // Date format: "%tm-%td %tH:%tM:%tS.%tL"
        return new StringBuilder().append(c.get(Calendar.MONTH) + 1 - Calendar.JANUARY).append("-")
                .append(c.get(Calendar.DAY_OF_MONTH)).append(" ")
                .append(c.get(Calendar.HOUR_OF_DAY)).append(":")
                .append(c.get(Calendar.MINUTE)).append(":")
                .append(c.get(Calendar.SECOND)).append(".")
                .append(c.get(Calendar.MILLISECOND)).toString();
    }

    /** Returns the string representation of the float number, with specified digits (up to 4)
     * after the decimal point.
     * @param num the float number
     * @param digits the number of digits after the decimal point
     * @return the string representation
     */
    public static String doubleToString(double num, int digits) {
        if (digits < 0 || digits > 4) return "Err";

        final boolean isNegative = num < 0;
        num = isNegative ? -num : num;
        long mask = 1;
        for (int i = 0; i < digits; i++) {
            mask *= 10;
        }
        long integral = (long) num;
        long fraction = (long) (num * mask) - integral * mask;

        StringBuilder sb = new StringBuilder();
        if (isNegative) sb.append("-");
        sb.append(integral);
        if (digits == 0) return sb.toString();
        sb.append(".");
        for (long f1 = fraction; f1 > 0; f1 /= 10) {
            digits--;
        }
        for (int i = 0; i < digits; i++) {
            sb.append(0);
        }
        return fraction == 0 ? sb.toString() : sb.append(fraction).toString();
    }
}
