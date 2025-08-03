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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.filters.SmallTest;

import com.android.server.wifi.WifiBaseTest;

import org.junit.Test;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * Unit tests for {@link com.android.server.wifi.util.StringUtil}.
 */
@SmallTest
public class StringUtilTest extends WifiBaseTest {
    static final byte ASCII_UNIT_SEPARATOR = 31;
    static final byte ASCII_DEL = 127;

    /** Verifies that isAsciiPrintable() does not crash when passed a null array. */
    @Test
    public void nullArrayDoesNotCauseCrash() {
        assertTrue(StringUtil.isAsciiPrintable(null));
    }

    /** Verifies that isAsciiPrintable() considers an empty array to be printable. */
    @Test
    public void emptyArrayIsPrintable() {
        assertTrue(StringUtil.isAsciiPrintable(new byte[]{}));
    }

    /** Verifies that isAsciiPrintable() considers a single printable byte to be printable. */
    @Test
    public void arrayWithSinglePrintableByteIsPrintable() {
        assertTrue(StringUtil.isAsciiPrintable(new byte[]{'a'}));
    }

    /**
     * Verifies that isAsciiPrintable() considers an array of multiple printable bytes to be
     * printable.
     */
    @Test
    public void arrayWithMultiplePrintableBytesIsPrintable() {
        assertTrue(StringUtil.isAsciiPrintable(new byte[]{'a', 'b'}));
    }

    /**
     * Verifies that isAsciiPrintable() considers bell, form feed, newline, horizontal tab,
     * and vertical tab to be printable.
     */
    @Test
    public void printableControlCharactersAreConsideredPrintable() {
        assertTrue(StringUtil.isAsciiPrintable(new byte[]{0x07}));  // bell
        assertTrue(StringUtil.isAsciiPrintable(new byte[]{'\f'}));  // form feed
        assertTrue(StringUtil.isAsciiPrintable(new byte[]{'\n'}));
        assertTrue(StringUtil.isAsciiPrintable(new byte[]{'\t'}));
        assertTrue(StringUtil.isAsciiPrintable(new byte[]{0x0b}));  // vertical tab
    }

    /** Verifies that isAsciiPrintable() considers a newline to be printable. */
    @Test
    public void arrayWithNewlineIsPrintable() {
        assertTrue(StringUtil.isAsciiPrintable(new byte[]{'\n'}));
    }

    /** Verifies that isAsciiPrintable() considers a space to be printable. */
    @Test
    public void arrayWithSpaceIsPrintable() {
        assertTrue(StringUtil.isAsciiPrintable(new byte[]{' '}));
    }

    /** Verifies that isAsciiPrintable() considers a tilde to be printable. */
    @Test
    public void arrayWithTildeIsPrintable() {
        assertTrue(StringUtil.isAsciiPrintable(new byte[]{'~'}));
    }

    /** Verifies that isAsciiPrintable() considers a null to be unprintable. */
    @Test
    public void arrayWithNullByteIsNotPrintable() {
        assertFalse(StringUtil.isAsciiPrintable(new byte[]{0}));
    }

    /** Verifies that isAsciiPrintable() considers (space-1) to be unprintable. */
    @Test
    public void arrayWithUnitSeparatorIsNotPrintable() {
        assertFalse(StringUtil.isAsciiPrintable(new byte[]{ASCII_UNIT_SEPARATOR}));
    }

    /** Verifies that isAsciiPrintable() considers (tilde+1) to be unprintable. */
    @Test
    public void arrayWithDelIsNotPrintable() {
        assertFalse(StringUtil.isAsciiPrintable(new byte[]{ASCII_DEL}));
    }

    /**
     * Verifies that isAsciiPrintable() considers negative bytes to be unprintable.
     * (In unsigned representation, these are values greater than DEL.)
     */
    @Test
    public void arrayWithNegativeByteIsNotPrintable() {
        assertFalse(StringUtil.isAsciiPrintable(new byte[]{-128}));
        assertFalse(StringUtil.isAsciiPrintable(new byte[]{-1}));
    }

    @Test
    public void verifyCalendarToStringFormat() throws Exception {
        Calendar c = Calendar.getInstance();
        c.setTimeZone(TimeZone.getTimeZone("UTC"));
        c.setTimeInMillis(1654647373799L);
        assertEquals("6-8 0:16:13.799", StringUtil.calendarToString(c));
    }

    @Test
    public void verifyDoubleToStringFormat() throws Exception {
        assertEquals("3", StringUtil.doubleToString(3.1415926, 0));
        assertEquals("3.1", StringUtil.doubleToString(3.1415926, 1));
        assertEquals("3.14", StringUtil.doubleToString(3.1415926, 2));
        assertEquals("-3", StringUtil.doubleToString(-3.1415926, 0));
        assertEquals("-3.1", StringUtil.doubleToString(-3.1415926, 1));
        assertEquals("-3.14", StringUtil.doubleToString(-3.1415926, 2));
        assertEquals("-65.03", StringUtil.doubleToString(-65.03218, 2));
        assertEquals("-65.00", StringUtil.doubleToString(-65.00018, 2));
        assertEquals("199.001", StringUtil.doubleToString(199.00181, 3));
        assertEquals("-199.00", StringUtil.doubleToString(-199.00181, 2));
        assertEquals("0", StringUtil.doubleToString(0.0, 0));
        assertEquals("0.0", StringUtil.doubleToString(0.0, 1));
        assertEquals("-99.0099", StringUtil.doubleToString(-99.0099, 4));
        assertEquals("Err", StringUtil.doubleToString(-99.0099, 5));
    }
}
