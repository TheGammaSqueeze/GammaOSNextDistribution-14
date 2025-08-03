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

package android.icu.extratest.text;

import static org.junit.Assert.assertEquals;

import android.icu.testsharding.MainTestShard;
import android.icu.text.DateIntervalFormat;
import android.icu.util.Calendar;
import android.icu.util.ULocale;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.StringWriter;

@MainTestShard
@RunWith(JUnit4.class)
public class DateIntervalFormatTest {

    @Test
    public void testAppendTo() {
        DateIntervalFormat fmt = DateIntervalFormat.getInstance("dMMMMy", ULocale.US);
        Calendar input1 = Calendar.getInstance(ULocale.US);
        Calendar input2 = Calendar.getInstance(ULocale.US);
        input1.set(2019, 6, 20);
        input2.set(2019, 6, 25);
        DateIntervalFormat.FormattedDateInterval result = fmt.formatToValue(input1, input2);

        StringWriter writer = new StringWriter();
        result.appendTo(writer);
        assertEquals("July 20\u2009\u2013\u200925, 2019", writer.toString());
    }
}
