/*
 * Copyright(C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" (short)0IS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.javacard.keymaster;

import com.android.javacard.seprovider.KMException;
import javacard.framework.Util;

/**
 * This is a utility class which helps in converting date to UTC format and doing some arithmetic
 * Operations.
 */
public class KMUtils {

  // 64 bit unsigned calculations for time
  public static final byte[] oneSecMsec = {0, 0, 0, 0, 0, 0, 0x03, (byte) 0xE8}; // 1000 msec
  public static final byte[] oneMinMsec = {0, 0, 0, 0, 0, 0, (byte) 0xEA, 0x60}; // 60000 msec
  public static final byte[] oneHourMsec = {
    0, 0, 0, 0, 0, 0x36, (byte) 0xEE, (byte) 0x80
  }; // 3600000 msec
  public static final byte[] oneDayMsec = {0, 0, 0, 0, 0x05, 0x26, 0x5C, 0x00}; // 86400000 msec
  public static final byte[] oneMonthMsec = {
    0, 0, 0, 0, (byte) 0x9C, (byte) 0xBE, (byte) 0xBD, 0x50
  }; // 2629746000 msec
  public static final byte[] leapYearMsec = {
    0, 0, 0, 0x07, (byte) 0x5C, (byte) 0xD7, (byte) 0x88, 0x00
  }; // 31622400000;
  public static final byte[] yearMsec = {
    0, 0, 0, 0x07, 0x57, (byte) 0xB1, 0x2C, 0x00
  }; // 31536000000
  // Leap year(366) + 3 * 365
  public static final byte[] fourYrsMsec = {
    0, 0, 0, 0x1D, 0x63, (byte) 0xEB, 0x0C, 0x00
  }; // 126230400000
  // msec
  public static final byte[] febMonthLeapMSec = {
    0, 0, 0, 0, (byte) 0x95, 0x58, 0x6C, 0x00
  }; // 2505600000
  public static final byte[] febMonthMsec = {
    0, 0, 0, 0, (byte) 0x90, 0x32, 0x10, 0x00
  }; // 2419200000
  public static final byte[] ThirtyOneDaysMonthMsec = {
    0, 0, 0, 0, (byte) 0x9F, (byte) 0xA5, 0x24, 0x00
  }; // 2678400000
  public static final byte[] ThirtDaysMonthMsec = {
    0, 0, 0, 0, (byte) 0x9A, 0x7E, (byte) 0xC8, 0x00
  }; // 2592000000
  public static final byte[] firstJan2000 = {
    0, 0, 0, (byte) 0xDC, 0x6A, (byte) 0xCF, (byte) 0xAC, 0x00
  }; // 946684800000
  private static final byte[] dec319999Ms = {
    (byte) 0, (byte) 0, (byte) 0xE6, 0x77, (byte) 0xD2, 0x1F, (byte) 0xD8, 0x18
  }; // 253402300799000
  public static final byte[] fourHundredYrsMSec = {
    0x00, 0x00, 0x0B, 0x7A, (byte) 0xF8, 0x5D, (byte) 0x9C, 0x00
  }; // 12622780800000 ((365×400 + 100 - 3) * 24 * 60 * 60 * 1000)
  public static final byte[] centuryWithLeapMSec = {
    0x00, 0x00, 0x02, (byte) 0xDE, (byte) 0xC1, (byte) 0xF4, (byte) 0x2C, 0x00
  }; // 3155760000000 ((100×365 + 25) * 24 * 60 * 60 * 1000)
  public static final byte[] centuryMSec = {
    0x00, 0x00, 0x02, (byte) 0xDE, (byte) 0xBC, (byte) 0xCD, (byte) 0xD0, 0x00
  }; // 3155673600000 ((100×365 + 24) * 24 * 60 * 60 * 1000)
  public static final short year1970 = 1970;
  public static final short year2000 = 2000;
  public static final short year2050 = 2050;
  // Convert to milliseconds constants
  public static final byte[] SEC_TO_MILLIS_SHIFT_POS = {9, 8, 7, 6, 5, 3};
  // Represents long integer size
  public static final byte UINT8 = 8;

  // --------------------------------------
  public static short convertToDate(short time, byte[] scratchPad) {

    short yrsCount = 0;
    short monthCount = 1;
    short dayCount = 1;
    short hhCount = 0;
    short mmCount = 0;
    short ssCount = 0;
    short inputOffset = 0;
    byte Z = 0x5A;
    Util.arrayFillNonAtomic(scratchPad, (short) 0, (short) 256, (byte) 0);
    Util.arrayCopyNonAtomic(
        KMInteger.cast(time).getBuffer(),
        KMInteger.cast(time).getStartOff(),
        scratchPad,
        (short) (8 - KMInteger.cast(time).length()),
        KMInteger.cast(time).length());
    if (KMInteger.unsignedByteArrayCompare(scratchPad, inputOffset, dec319999Ms, (short) 0, UINT8)
        > 0) {
      KMException.throwIt(KMError.INVALID_ARGUMENT);
    }
    short quotient = 0;
    short endYear = 0;
    short baseYear = year1970;
    if (KMInteger.unsignedByteArrayCompare(scratchPad, inputOffset, firstJan2000, (short) 0, UINT8)
        >= 0) {
      baseYear = year2000;
      // difference in millis from year 2000
      subtractAndCopy(scratchPad, inputOffset, firstJan2000, (short) 0);
      // divide the time with 400 year milliseconds
      quotient = divideAndCopy(scratchPad, inputOffset, fourHundredYrsMSec, (short) 0);
      yrsCount = (short) (400 * quotient);
      // divide the remaining time with 100 year milliseconds
      endYear = (short) (yrsCount + 400);
      for (; yrsCount <= endYear; yrsCount += 100) {
        byte[] centuryMillis = centuryToMillis((short) (baseYear + yrsCount));
        if ((KMInteger.unsignedByteArrayCompare(
                scratchPad, inputOffset, centuryMillis, (short) 0, UINT8)
            < 0)) {
          break;
        }
        subtractAndCopy(scratchPad, inputOffset, centuryMillis, (short) 0);
      }
    }
    yrsCount += baseYear;
    yrsCount = adjustBaseYearToLeapYearsRange(yrsCount, scratchPad, inputOffset);
    // divide the given time with four years msec count
    quotient = divideAndCopy(scratchPad, inputOffset, fourYrsMsec, (short) 0);
    yrsCount += (short) (quotient * 4); // number of yrs.
    // divide the given time with one year msec
    endYear = (short) (yrsCount + 4);
    for (; yrsCount <= endYear; yrsCount++) {
      byte[] yearMillis = yearToMillis(yrsCount);
      if ((KMInteger.unsignedByteArrayCompare(scratchPad, inputOffset, yearMillis, (short) 0, UINT8)
          < 0)) {
        break;
      }
      subtractAndCopy(scratchPad, inputOffset, yearMillis, (short) 0);
    }
    // divide the given time with one month msec count
    for (; monthCount <= 12; monthCount++) {
      byte[] monthMillis = monthToMillis(yrsCount, monthCount);
      if ((KMInteger.unsignedByteArrayCompare(
              scratchPad, inputOffset, monthMillis, (short) 0, UINT8)
          < 0)) {
        break;
      }
      subtractAndCopy(scratchPad, inputOffset, monthMillis, (short) 0);
    }

    // divide the given time with one day msec count
    dayCount = divideAndCopy(scratchPad, inputOffset, oneDayMsec, (short) 0);
    dayCount++;

    // divide the given time with one hour msec count
    hhCount = divideAndCopy(scratchPad, inputOffset, oneHourMsec, (short) 0);

    // divide the given time with one minute msec count
    mmCount = divideAndCopy(scratchPad, inputOffset, oneMinMsec, (short) 0);

    // divide the given time with one second msec count
    ssCount = divideAndCopy(scratchPad, inputOffset, oneSecMsec, (short) 0);

    // Now convert to ascii string YYMMDDhhmmssZ or YYYYMMDDhhmmssZ
    Util.arrayFillNonAtomic(scratchPad, inputOffset, (short) 256, (byte) 0);
    short len = numberToString(yrsCount, scratchPad, inputOffset); // returns YYYY
    len += numberToString(monthCount, scratchPad, len);
    len += numberToString(dayCount, scratchPad, len);
    len += numberToString(hhCount, scratchPad, len);
    len += numberToString(mmCount, scratchPad, len);
    len += numberToString(ssCount, scratchPad, len);
    scratchPad[len] = Z;
    len++;
    if (yrsCount < year2050) {
      return KMByteBlob.instance(scratchPad, (short) 2, (short) (len - 2)); // YY
    } else {
      return KMByteBlob.instance(scratchPad, (short) 0, len); // YYYY
    }
  }

  public static short numberToString(short number, byte[] scratchPad, short offset) {
    byte zero = 0x30;
    byte len = 2;
    byte digit;
    if (number > 999) {
      len = 4;
    }
    byte index = len;
    while (index > 0) {
      digit = (byte) (number % 10);
      number = (short) (number / 10);
      scratchPad[(short) (offset + index - 1)] = (byte) (digit + zero);
      index--;
    }
    return len;
  }

  // Divide the given input with the divisor and copy the remainder back to the
  // input buffer from inputOff
  private static short divideAndCopy(
      byte[] scratchPad, short inputOff, byte[] divisor, short offset) {
    short scratchPadOff = (short) (inputOff + 8);
    Util.arrayCopyNonAtomic(divisor, offset, scratchPad, scratchPadOff, UINT8);
    short q = divide(scratchPad, inputOff, scratchPadOff, (short) (scratchPadOff + 8));
    if (q != 0) {
      Util.arrayCopyNonAtomic(scratchPad, (short) (scratchPadOff + 8), scratchPad, inputOff, UINT8);
    }
    return q;
  }

  // Use Euclid's formula: dividend = quotient*divisor + remainder
  // i.e. dividend - quotient*divisor = remainder where remainder < divisor.
  // so this is division by subtraction until remainder remains.
  public static short divide(byte[] buf, short dividend, short divisor, short remainder) {
    short expCnt = 1;
    short q = 0;
    // first increase divisor so that it becomes greater then dividend.
    while (compare(buf, divisor, dividend) < 0) {
      shiftLeft(buf, divisor);
      expCnt = (short) (expCnt << 1);
    }
    // Now subtract divisor from dividend if dividend is greater then divisor.
    // Copy remainder in the dividend and repeat.
    while (expCnt != 0) {
      if (compare(buf, dividend, divisor) >= 0) {
        subtract(buf, dividend, divisor, remainder, (byte) 8);
        copy(buf, remainder, dividend);
        q = (short) (q + expCnt);
      }
      expCnt = (short) (expCnt >> 1);
      shiftRight(buf, divisor);
    }
    return q;
  }

  public static void copy(byte[] buf, short from, short to) {
    Util.arrayCopyNonAtomic(buf, from, buf, to, (short) 8);
  }

  public static byte compare(byte[] buf, short lhs, short rhs) {
    return KMInteger.unsignedByteArrayCompare(buf, lhs, buf, rhs, (short) 8);
  }

  public static void shiftLeft(byte[] buf, short start, short count) {
    short index = 0;
    while (index < count) {
      shiftLeft(buf, start);
      index++;
    }
  }

  public static void shiftLeft(byte[] buf, short start) {
    byte index = 7;
    byte carry = 0;
    byte tmp;
    while (index >= 0) {
      tmp = buf[(short) (start + index)];
      buf[(short) (start + index)] = (byte) (buf[(short) (start + index)] << 1);
      buf[(short) (start + index)] = (byte) (buf[(short) (start + index)] + carry);
      if (tmp < 0) {
        carry = 1;
      } else {
        carry = 0;
      }
      index--;
    }
  }

  public static void shiftRight(byte[] buf, short start) {
    byte index = 0;
    byte carry = 0;
    byte tmp;
    while (index < 8) {
      tmp = (byte) (buf[(short) (start + index)] & 0x01);
      buf[(short) (start + index)] = (byte) (buf[(short) (start + index)] >> 1);
      buf[(short) (start + index)] = (byte) (buf[(short) (start + index)] & 0x7F);
      buf[(short) (start + index)] = (byte) (buf[(short) (start + index)] | carry);
      if (tmp == 1) {
        carry = (byte) 0x80;
      } else {
        carry = 0;
      }
      index++;
    }
  }

  public static void add(byte[] buf, short op1, short op2, short result) {
    byte index = 7;
    byte carry = 0;
    short tmp;
    short val1 = 0;
    short val2 = 0;
    while (index >= 0) {
      val1 = (short) (buf[(short) (op1 + index)] & 0x00FF);
      val2 = (short) (buf[(short) (op2 + index)] & 0x00FF);
      tmp = (short) (val1 + val2 + carry);
      carry = 0;
      if (tmp > 255) {
        carry = 1; // max unsigned byte value is 255
      }
      buf[(short) (result + index)] = (byte) (tmp & (byte) 0xFF);
      index--;
    }
  }

  // Subtract the two operands and copy the difference back to the input buffer from inputOff
  private static void subtractAndCopy(byte[] scratchPad, short inputOff, byte[] buf, short bufOff) {
    short scratchpadOff = (short) (inputOff + 8);
    Util.arrayCopyNonAtomic(buf, bufOff, scratchPad, scratchpadOff, UINT8);
    subtract(scratchPad, inputOff, scratchpadOff, (short) (scratchpadOff + 8), UINT8);
    Util.arrayCopyNonAtomic(scratchPad, (short) (scratchpadOff + 8), scratchPad, inputOff, UINT8);
  }

  // subtraction by borrowing.
  public static void subtract(byte[] buf, short op1, short op2, short result, byte sizeBytes) {
    byte borrow = 0;
    byte index = (byte) (sizeBytes - 1);
    short r;
    short x;
    short y;
    while (index >= 0) {
      x = (short) (buf[(short) (op1 + index)] & 0xFF);
      y = (short) (buf[(short) (op2 + index)] & 0xFF);
      r = (short) (x - y - borrow);
      borrow = 0;
      if (r < 0) {
        borrow = 1;
        r = (short) (r + 256); // max unsigned byte value is 255
      }
      buf[(short) (result + index)] = (byte) (r & 0xFF);
      index--;
    }
  }

  public static short countTemporalCount(
      byte[] bufTime, short timeOff, short timeLen, byte[] scratchPad, short offset) {
    Util.arrayFillNonAtomic(scratchPad, (short) offset, (short) 24, (byte) 0);
    Util.arrayCopyNonAtomic(bufTime, timeOff, scratchPad, (short) (offset + 8 - timeLen), timeLen);
    Util.arrayCopyNonAtomic(
        ThirtDaysMonthMsec, (short) 0, scratchPad, (short) (offset + 8), (short) 8);
    return divide(scratchPad, (short) 0, (short) 8, (short) 16);
  }

  public static boolean isLeapYear(short year) {
    if ((short) (year % 4) == (short) 0) {
      if (((short) (year % 100) == (short) 0) && ((short) (year % 400)) != (short) 0) {
        return false;
      }
      return true;
    }
    return false;
  }

  private static byte[] yearToMillis(short year) {
    if (isLeapYear(year)) {
      return leapYearMsec;
    } else {
      return yearMsec;
    }
  }

  private static byte[] centuryToMillis(short year) {
    if (isLeapYear(year)) {
      return centuryWithLeapMSec;
    } else {
      return centuryMSec;
    }
  }

  private static byte[] monthToMillis(short year, short month) {
    if (month == 2) {
      if (isLeapYear(year)) {
        return febMonthLeapMSec;
      } else {
        return febMonthMsec;
      }
    } else if (((month <= 7) && ((month % 2 == 1))) || ((month > 7) && ((month % 2 == 0)))) {
      return ThirtyOneDaysMonthMsec;
    } else {
      return ThirtDaysMonthMsec;
    }
  }

  private static short adjustBaseYearToLeapYearsRange(
      short year, byte[] scratchPad, short inputOffset) {
    if (!isLeapYear(year)) {
      // The rounded base year must fall within the range of leap years, which occur every
      // four years. If the rounded base year is not a leap year then add one year to it
      // so that it comes in the range of leap years. This is necessary when we divide the
      // difference of the given time and rounded base year with four year milliseconds
      // value.
      if (KMInteger.unsignedByteArrayCompare(scratchPad, inputOffset, yearMsec, (short) 0, UINT8)
          >= 0) {
        subtractAndCopy(scratchPad, inputOffset, yearMsec, (short) 0);
        year += 1;
      }
    }
    return year;
  }

  public static void computeOnesCompliment(byte[] buf, short offset, short len) {
    short index = offset;
    // Compute 1s compliment
    while (index < (short) (len + offset)) {
      buf[index] = (byte) ~buf[index];
      index++;
    }
  }

  // i * 1000 = (i << 9) + (i << 8) + (i << 7) + (i << 6) + (i << 5) + ( i << 3)
  public static void convertToMilliseconds(
      byte[] buf, short inputOff, short outputOff, short scratchPadOff) {
    short index = 0;
    short length = (short) SEC_TO_MILLIS_SHIFT_POS.length;
    while (index < length) {
      Util.arrayCopyNonAtomic(buf, inputOff, buf, scratchPadOff, (short) 8);
      shiftLeft(buf, scratchPadOff, SEC_TO_MILLIS_SHIFT_POS[index]);
      Util.arrayCopyNonAtomic(buf, outputOff, buf, (short) (scratchPadOff + 8), (short) 8);
      add(buf, scratchPadOff, (short) (8 + scratchPadOff), (short) (16 + scratchPadOff));
      Util.arrayCopyNonAtomic(buf, (short) (scratchPadOff + 16), buf, outputOff, (short) 8);
      Util.arrayFillNonAtomic(buf, scratchPadOff, (short) 24, (byte) 0);
      index++;
    }
  }
}
