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
import android.icu.text.ListFormatter;
import android.icu.text.ListFormatter.FormattedList;
import android.icu.util.ULocale;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@MainTestShard
@RunWith(JUnit4.class)
public class ListFormatterTest {

    @Test
    public void testFormattedListAppendTo() {
        List<String> items = Arrays.asList("1", "2");
        ListFormatter formatter = ListFormatter.getInstance(ULocale.US);
        FormattedList formattedList = formatter.formatToValue(items);

        StringBuilder sb = new StringBuilder();
        sb = formattedList.appendTo(sb);
        // formatter.formatToValue should produce the same string as formatter.format
        assertEquals(formatter.format(items), sb.toString());
    }

    @Test
    public void testFormatToValue() {
        ListFormatter formatter = ListFormatter.getInstance(ULocale.US);
        FormattedList formattedList = formatter.formatToValue("apple", "orange", "pear");
        assertEquals("apple, orange, and pear", formattedList.toString());
    }

}
