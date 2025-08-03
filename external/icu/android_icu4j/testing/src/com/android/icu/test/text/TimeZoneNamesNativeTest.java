/*
 * Copyright (C) 2018 The Android Open Source Project
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

import android.icu.dev.test.TestFmwk;
import android.icu.testsharding.MainTestShard;
import android.icu.text.TimeZoneNames;
import android.icu.text.TimeZoneNames.NameType;
import com.android.icu.text.TimeZoneNamesNative;
import java.time.Instant;
import java.util.Locale;
import java.util.TimeZone;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@MainTestShard
@RunWith(JUnit4.class)
public class TimeZoneNamesNativeTest extends TestFmwk {

    private static final Locale[] LOCALES = new Locale[]{
        Locale.CANADA,
        Locale.CANADA_FRENCH,
        Locale.CHINA,
        Locale.CHINESE,
        Locale.ENGLISH,
        Locale.FRANCE,
        Locale.FRENCH,
        Locale.GERMAN,
        Locale.GERMANY
    };

    private static final String[] availableTimeZoneIds = TimeZone.getAvailableIDs();

    @Test
    public void testGetFilledZoneStrings() {
        for (Locale locale : LOCALES) {
            TimeZoneNames tznJava = TimeZoneNames.getInstance(locale);
            String[][] tznNative = TimeZoneNamesNative.getFilledZoneStrings(locale,
                availableTimeZoneIds);

            final long now = Instant.now().toEpochMilli();
            for (int i = 0; i < tznNative.length; i++) {
                final String zid = availableTimeZoneIds[i];
                final String lng = tznJava.getDisplayName(zid, NameType.LONG_STANDARD, now);
                final String shrt = tznJava.getDisplayName(zid, NameType.SHORT_STANDARD, now);
                final String lngDst = tznJava.getDisplayName(zid, NameType.LONG_DAYLIGHT, now);
                final String shrtDst = tznJava.getDisplayName(zid, NameType.SHORT_DAYLIGHT, now);

                // TimeZoneNamesNative can provide null values in more cases than java version, so
                // assert only present values.
                if (tznNative[i][1] != null && lng != null) {
                    assertEquals("LONG_STANDARD", lng, tznNative[i][1]);
                }
                if (tznNative[i][2] != null && shrt != null) {
                    assertEquals("SHORT_STANDARD", shrt, tznNative[i][2]);
                }
                if (tznNative[i][3] != null && lngDst != null) {
                    assertEquals("LONG_DAYLIGHT", lngDst, tznNative[i][3]);
                }
                if (tznNative[i][4] != null && shrtDst != null) {
                    assertEquals("SHORT_DAYLIGHT", shrtDst, tznNative[i][4]);
                }
            }
        }
    }

}
