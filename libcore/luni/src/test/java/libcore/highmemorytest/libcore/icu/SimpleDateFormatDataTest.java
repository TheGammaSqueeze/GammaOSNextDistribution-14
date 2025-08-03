/*
 * Copyright (C) 2012 The Android Open Source Project
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

package libcore.highmemorytest.libcore.icu;

import static org.junit.Assert.assertNotEquals;

import java.text.DateFormat;

import libcore.icu.SimpleDateFormatData;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Locale;

@RunWith(Parameterized.class)
public class SimpleDateFormatDataTest {

    @Parameterized.Parameters(name = "{0}")
    public static Locale[] getAllLocales() {
        return Locale.getAvailableLocales();
    }

    @Parameterized.Parameter(0)
    public Locale locale;

    @Test
    public void testLongDateTimeFormat() {
        SimpleDateFormatData d = SimpleDateFormatData.getInstance(locale);

        assertNotEquals(0, d.getDateFormat(DateFormat.FULL).length());
        assertNotEquals(0, d.getDateFormat(DateFormat.LONG).length());
        assertNotEquals(0, d.getDateFormat(DateFormat.MEDIUM).length());
        assertNotEquals(0, d.getDateFormat(DateFormat.SHORT).length());

        assertNotEquals(0, d.getTimeFormat(DateFormat.FULL).length());
        assertNotEquals(0, d.getTimeFormat(DateFormat.LONG).length());
        assertNotEquals(0, d.getTimeFormat(DateFormat.MEDIUM).length());
        assertNotEquals(0, d.getTimeFormat(DateFormat.SHORT).length());
    }
}
