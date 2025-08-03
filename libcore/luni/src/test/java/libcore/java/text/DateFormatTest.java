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

package libcore.java.text;

import android.icu.util.VersionInfo;

import dalvik.annotation.compat.VersionCodes;

import libcore.test.annotation.NonMts;
import libcore.test.reasons.NonMtsReasons;

import junit.framework.TestCase;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class DateFormatTest extends TestCase {

    // Regression test for http://b/31762542. If this test fails it implies that changes to
    // DateFormat.is24Hour will not be effective.
    public void testIs24Hour_notCached() throws Exception {
        char sep = ' ';

        Boolean originalIs24Hour = DateFormat.is24Hour;
        try {
            // These tests hardcode expectations for Locale.US.
            DateFormat.is24Hour = null; // null == locale default (12 hour for US)
            checkTimePattern(DateFormat.SHORT, "h:mm" + sep + "a");
            checkTimePattern(DateFormat.MEDIUM, "h:mm:ss" + sep + "a");

            DateFormat.is24Hour = true; // Explicit 24 hour.
            checkTimePattern(DateFormat.SHORT, "HH:mm");
            checkTimePattern(DateFormat.MEDIUM, "HH:mm:ss");

            DateFormat.is24Hour = false; // Explicit 12 hour.
            checkTimePattern(DateFormat.SHORT, "h:mm" + sep + "a");
            checkTimePattern(DateFormat.MEDIUM, "h:mm:ss" + sep + "a");
        } finally {
            DateFormat.is24Hour = originalIs24Hour;
        }
    }

    private static void checkTimePattern(int style, String expectedPattern) {
        final Locale locale = Locale.US;
        final Date current = new Date(1468250177000L); // 20160711 15:16:17 GMT
        DateFormat format = DateFormat.getTimeInstance(style, locale);
        String actualDateString = format.format(current);
        SimpleDateFormat sdf = new SimpleDateFormat(expectedPattern, locale);
        String expectedDateString = sdf.format(current);
        assertEquals(expectedDateString, actualDateString);
    }

    public void testGetTimeInstance_withLocaleExtension() {
        Locale locale = Locale.forLanguageTag("en-u-tz-usden");
        DateFormat df = DateFormat.getTimeInstance(DateFormat.SHORT, locale);
        assertEquals("America/Denver", df.getCalendar().getTimeZone().getID());
    }

    /** Regression test for http://b/266731719. */
    @NonMts(bug = 320622741, disabledUntilSdk = VersionCodes.VANILLA_ICE_CREAM,
            reason = NonMtsReasons.OEM_CUSTOMIZATION)
    public void testParse_lenient_en() throws ParseException {
        assertParse_lenient_en(Locale.ENGLISH);
        assertParse_lenient_en(Locale.US);
        assertParse_lenient_en(Locale.forLanguageTag("en-Latn"));
        assertParse_lenient_en(Locale.forLanguageTag("en-US-u-ca-gregory"));
        assertParse_lenient_en(Locale.forLanguageTag("en-US-u-va-posix"));
    }

    private static void assertParse_lenient_en(Locale locale) throws ParseException {
        // Since this input is used for serialization in the wild, we expect that
        // DateFormat.parse(String) has to parse this string successfully forever,
        // and never breaks this behavior. Don't change this input unless it's proven
        // to be safe to do so. See http://b/266731719 for details.
        String input = "Jan 1, 2023 9:55:48 PM";
        DateFormat df = DateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.DEFAULT,
                locale);
        df.setTimeZone(TimeZone.getTimeZone("GMT"));
        Date date = df.parse(input);
        assertEquals(1672610148000L, date.getTime());
    }

    /** Regression test for http://b/266731719. */
    @NonMts(bug = 320622741, disabledUntilSdk = VersionCodes.VANILLA_ICE_CREAM,
            reason = NonMtsReasons.OEM_CUSTOMIZATION)
    public void testFormat_forBug266731719() {
        assertFormat_forBug266731719(Locale.ENGLISH);
        assertFormat_forBug266731719(Locale.US);
        assertFormat_forBug266731719(Locale.forLanguageTag("en-Latn"));
        assertFormat_forBug266731719(Locale.forLanguageTag("en-US-u-ca-gregory"));
        assertFormat_forBug266731719(Locale.forLanguageTag("en-US-u-va-posix"));
    }

    private static void assertFormat_forBug266731719(Locale locale) {
        DateFormat df = DateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.DEFAULT,
                locale);
        df.setTimeZone(TimeZone.getTimeZone("GMT"));
        String formatted = df.format(new Date(1672610148000L));
        assertEquals("Jan 1, 2023 9:55:48 PM", formatted);
    }
}
