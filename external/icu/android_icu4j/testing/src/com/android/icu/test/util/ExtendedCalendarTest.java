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

package com.android.icu.test.util;

import static org.junit.Assert.assertEquals;

import android.icu.testsharding.MainTestShard;
import android.icu.text.DateFormat;
import android.icu.text.SimpleDateFormat;
import android.icu.util.Calendar;
import android.icu.util.ULocale;

import com.android.icu.util.ExtendedCalendar;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@MainTestShard
@RunWith(JUnit4.class)
public class ExtendedCalendarTest {

    /**
     * Test {@link ExtendedCalendar#getDateTimePattern(int, int)} by testing the consistency
     * against with the public API {@link Calendar#getDateTimeFormat(int, int, ULocale)}.
     */
    @Test
    public void testGetDateTimeFormatString() {
        for (ULocale uLocale : ULocale.getAvailableLocales()) {
            ExtendedCalendar extendedCalendar = ExtendedCalendar.getInstance(uLocale);
            Calendar calendar = Calendar.getInstance(uLocale);
            for (int timeStyle = DateFormat.NONE; timeStyle <= DateFormat.SHORT; timeStyle++) {
                for (int dateStyle = DateFormat.NONE; dateStyle <= DateFormat.SHORT; dateStyle++) {
                    // date and time styles can't be both null.
                    if (timeStyle == DateFormat.NONE && dateStyle == DateFormat.NONE) {
                        continue;
                    }
                    String pattern1 = extendedCalendar.getDateTimePattern(dateStyle, timeStyle);
                    DateFormat dateFormat = calendar.getDateTimeFormat(
                            dateStyle, timeStyle, uLocale);
                    String pattern2 = ((SimpleDateFormat) dateFormat).toPattern();
                    String msg = "Locale:" + uLocale + " date style:" + dateStyle
                            + " time style:" + timeStyle;
                    assertEquals(msg, pattern1, pattern2);
                }
            }
        }
    }
}
