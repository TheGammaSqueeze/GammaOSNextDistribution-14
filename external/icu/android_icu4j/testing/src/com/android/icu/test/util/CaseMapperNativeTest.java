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

package com.android.icu.test.util;

import static org.junit.Assert.assertEquals;

import android.icu.testsharding.MainTestShard;
import android.icu.text.CaseMap;
import android.icu.util.LocaleData;
import android.icu.util.ULocale;

import com.android.icu.util.CaseMapperNative;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import java.util.Locale;

@MainTestShard
@RunWith(JUnit4.class)
public class CaseMapperNativeTest {

    private static final Locale[] LOCALES = new Locale[]{
            Locale.ROOT,
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

    @Test
    public void testLowerCase() {
        CaseMap.Lower icu4jMapper = CaseMap.toLower();
        for (Locale locale : LOCALES) {
            ULocale uLocale = ULocale.forLocale(locale);
            String exampleString = String.join("", LocaleData.getExemplarSet(uLocale, 0));
            assertEquals("Locale:" + locale,
                    icu4jMapper.apply(locale, exampleString),
                    CaseMapperNative.toLowerCase(exampleString, locale));
        }
    }

    @Test
    public void testUpperCase() {
        CaseMap.Upper icu4jMapper = CaseMap.toUpper();
        for (Locale locale : LOCALES) {
            ULocale uLocale = ULocale.forLocale(locale);
            String exampleString = String.join("", LocaleData.getExemplarSet(uLocale, 0));
            assertEquals("Locale:" + locale,
                    icu4jMapper.apply(locale, exampleString),
                    CaseMapperNative.toUpperCase(exampleString, locale));
        }
    }
}
