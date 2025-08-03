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

import android.icu.util.ULocale;
import android.icu.testsharding.MainTestShard;

import com.android.icu.util.LocaleNative;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Locale;

/**
 * Verify the result from ICU4C against ICU4J
 */
@MainTestShard
@RunWith(JUnit4.class)
public class LocaleNativeTest {

    private static final String[] TEST_LANGUAGE_TAGS = {
            "en-US",
            "fr-CA",
            "fr-FR",
            "es-401",
            "zh-CN",
            "es-ES",
    };

    @Test
    public void testGetDisplayCountry() {
        assertSameDisplayNames(LocaleNative::getDisplayCountry,
                (targetLocale, displayLocale)
                        -> ULocale.forLocale(targetLocale).getDisplayCountry(
                                ULocale.forLocale(displayLocale))
        );
    }

    @Test
    public void testGetDisplayLanguage() {
        assertSameDisplayNames(LocaleNative::getDisplayLanguage,
                (targetLocale, displayLocale)
                        -> ULocale.forLocale(targetLocale).getDisplayLanguage(
                        ULocale.forLocale(displayLocale))
        );
    }

    @Test
    public void testGetDisplayScript() {
        assertSameDisplayNames(LocaleNative::getDisplayScript,
                (targetLocale, displayLocale)
                        -> ULocale.forLocale(targetLocale).getDisplayScript(
                        ULocale.forLocale(displayLocale))
        );
    }

    @Test
    public void testGetDisplayVariant() {
        assertSameDisplayNames(LocaleNative::getDisplayVariant,
                (targetLocale, displayLocale)
                        -> ULocale.forLocale(targetLocale).getDisplayVariant(
                        ULocale.forLocale(displayLocale))
        );
    }


    private static void assertSameDisplayNames(NameFunction icu4cGetDisplayName,
            NameFunction icu4jGetDisplayName) {
        for (String languageTag : TEST_LANGUAGE_TAGS) {
            Locale displayLocale = Locale.forLanguageTag(languageTag);
            for (Locale targetLocale : Locale.getAvailableLocales()) {
                String icu4cName = icu4cGetDisplayName.get(targetLocale, displayLocale);
                String icu4jName = icu4jGetDisplayName.get(targetLocale, displayLocale);
                assertEquals("targetLocale:" + targetLocale + " displayLocale:" + displayLocale,
                        icu4cName, icu4jName);
            }
        }
    }

    private interface NameFunction {
        String get(Locale targetLocale, Locale displayLocale);
    }
}
