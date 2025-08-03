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

package com.android.icu.test.text;

import static android.icu.text.DateFormatSymbols.ABBREVIATED;
import static android.icu.text.DateFormatSymbols.FORMAT;
import static android.icu.text.DateFormatSymbols.STANDALONE;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.icu.testsharding.MainTestShard;
import android.icu.text.DateFormatSymbols;
import android.icu.util.ULocale;

import com.android.icu.text.ExtendedDateFormatSymbols;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@MainTestShard
@RunWith(JUnit4.class)
public class ExtendedDateFormatSymbolsTest {
    @Test
    public void testAll() {
        for(ULocale locale : DateFormatSymbols.getAvailableULocales()) {
            ExtendedDateFormatSymbols extendedDfs = ExtendedDateFormatSymbols.getInstance(locale);
            DateFormatSymbols dfs = extendedDfs.getDateFormatSymbols();
            assertNotNullQuarters(locale, extendedDfs.getNarrowQuarters(FORMAT));
            assertNotNullQuarters(locale, extendedDfs.getNarrowQuarters(STANDALONE));

            // Test 1 getter arbitrarily in DateFormatSymbols
            assertNotNullQuarters(locale, dfs.getQuarters(FORMAT, ABBREVIATED));
        }
    }

    @Test
    public void test_en_US() {
        ULocale locale = ULocale.US;
        ExtendedDateFormatSymbols extendedDfs = ExtendedDateFormatSymbols.getInstance(locale);
        String[] expected = new String[] {"1", "2", "3", "4"};
        assertArrayEquals(expected, extendedDfs.getNarrowQuarters(FORMAT));
        assertArrayEquals(expected, extendedDfs.getNarrowQuarters(STANDALONE));
    }

    @Test
    public void test_arabicQuarters() {
        ULocale locale = ULocale.forLanguageTag("ar-EG-u-nu-arab");
        ExtendedDateFormatSymbols extendedDfs = ExtendedDateFormatSymbols.getInstance(locale);
        String[] expected = new String[] {"١", "٢", "٣", "٤"};
        assertArrayEquals(expected, extendedDfs.getNarrowQuarters(FORMAT));
        assertArrayEquals(expected, extendedDfs.getNarrowQuarters(STANDALONE));
    }

    private static void assertNotNullQuarters(ULocale locale, String[] quarters) {
        String baseMsg = "Locale: " + locale + " ";
        assertNotNull(baseMsg, quarters);
        assertEquals(baseMsg + "Quarters should have 4 elements", 4, quarters.length);
        for(int i = 0; i < quarters.length; i++) {
            assertNotNull(baseMsg + "index:" + i, quarters[i]);
        }
    }
}
