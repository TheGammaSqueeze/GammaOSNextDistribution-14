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

package com.android.icu.test.text;

import static org.junit.Assert.assertEquals;

import android.icu.testsharding.MainTestShard;
import com.android.icu.text.DateSorterBridge;
import java.util.Locale;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@MainTestShard
@RunWith(JUnit4.class)
public class DateSorterBridgeTest {

    @Test
    public void testAll() {
        // Test that we can get the locale data for all known locales.
        for (Locale l : Locale.getAvailableLocales()) {
            DateSorterBridge d = DateSorterBridge.createInstance(l);
            System.err.format("%20s %s %s\n", l, d.getYesterday(), d.getToday());
        }
    }

    @Test
    public void test_en_US() {
        DateSorterBridge d = DateSorterBridge.createInstance(Locale.US);
        assertEquals("Yesterday", d.getYesterday());
        assertEquals("Today", d.getToday());
    }

    @Test
    public void test_de_DE() {
        DateSorterBridge d = DateSorterBridge.createInstance(new Locale("de", "DE"));
        assertEquals("Gestern", d.getYesterday());
        assertEquals("Heute", d.getToday());
    }

    @Test
    public void test_ko_KR() {
        DateSorterBridge d = DateSorterBridge.createInstance(new Locale("ko", "KR"));
        // Ensure the fix for http://b/14493853 doesn't mangle Hangul.
        assertEquals("어제", d.getYesterday());
        assertEquals("오늘", d.getToday());
    }
}
