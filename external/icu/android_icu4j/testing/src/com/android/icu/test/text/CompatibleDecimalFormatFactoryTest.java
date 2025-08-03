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

package com.android.icu.test.text;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.icu.testsharding.MainTestShard;
import android.icu.text.DecimalFormat;
import android.icu.text.DecimalFormatSymbols;
import android.icu.text.NumberFormat;
import android.icu.util.ULocale;

import com.android.icu.text.CompatibleDecimalFormatFactory;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.text.ParseException;
import java.text.ParsePosition;

@MainTestShard
@RunWith(JUnit4.class)
public class CompatibleDecimalFormatFactoryTest {
    @Test
    public void testCreate() throws ParseException {
        ULocale locale = ULocale.US;
        String pattern = NumberFormat.getPatternForStyle(locale, NumberFormat.NUMBERSTYLE);
        assertNotNull(pattern);
        DecimalFormatSymbols dfs = DecimalFormatSymbols.getInstance(locale);
        DecimalFormat decimalFormat = CompatibleDecimalFormatFactory.create(pattern, dfs);

        double value = 1234.56;
        String formatted = decimalFormat.format(value);
        assertEquals("1,234.56", formatted);

        Number parsed = decimalFormat.parse(formatted);
        assertEquals(value, parsed.doubleValue(), 0.01);


        ParsePosition parsePosition = new ParsePosition(0);
        // Parsing with a leading space should return error
        decimalFormat.parse(" 1,234.56", parsePosition);
        assertEquals(2, parsePosition.getIndex());

        parsePosition = new ParsePosition(0);
        // Parsing with a bidi character should not be parsed properly
        decimalFormat.parse("1,\u200e234.56", parsePosition);
        assertEquals(1, parsePosition.getIndex());
    }
}
