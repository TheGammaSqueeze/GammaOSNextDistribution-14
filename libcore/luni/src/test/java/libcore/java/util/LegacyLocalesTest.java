/*
 * Copyright (C) 2023 The Android Open Source Project
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

package libcore.java.util;

import static junit.framework.Assert.assertEquals;

import static org.junit.Assume.assumeTrue;

import dalvik.annotation.compat.VersionCodes;
import dalvik.system.VMRuntime;

import libcore.junit.util.compat.CoreCompatChangeRule;
import libcore.junit.util.compat.CoreCompatChangeRule.DisableCompatChanges;
import libcore.junit.util.compat.CoreCompatChangeRule.EnableCompatChanges;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.text.BreakIterator;
import java.text.Collator;
import java.text.DateFormat;
import java.text.DateFormatSymbols;
import java.text.NumberFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.Map;

import sun.util.locale.BaseLocale;

public class LegacyLocalesTest {

    @Rule
    public final TestRule compatChangeRule = new CoreCompatChangeRule();

    // http://b/3452611; Locale.getDisplayLanguage fails for the obsolete language codes.
    @Test
    @DisableCompatChanges({BaseLocale.USE_NEW_ISO_LOCALE_CODES})
    public void test_getDisplayName_obsolete() {
        // he (new) -> iw (obsolete)
        assertObsolete("he", "iw", "עברית");
        // id (new) -> in (obsolete)
        assertObsolete("id", "in", "Indonesia");
    }

    @Test
    @EnableCompatChanges({BaseLocale.USE_NEW_ISO_LOCALE_CODES})
    public void obsoleteLocales_withFlagEnabled() {
        var msg = "Test should run on V+ only, current SDK level=" + VMRuntime.getSdkVersion();
        assumeTrue(msg, VMRuntime.getSdkVersion() >= VersionCodes.VANILLA_ICE_CREAM);

        assertNew(/* newCode= */ "he", /* oldCode= */ "iw", "עברית");
        assertNew(/* newCode= */ "id", /* oldCode= */ "in", "Indonesia");
    }

    private static void assertObsolete(String newCode, String oldCode, String displayName) {
        // Either code should get you the same locale.
        Locale newLocale = new Locale(newCode);
        Locale oldLocale = new Locale(oldCode);
        assertEquals(newLocale, oldLocale);

        // No matter what code you used to create the locale, you should get the old code back.
        assertEquals(oldCode, newLocale.getLanguage());
        assertEquals(oldCode, oldLocale.getLanguage());

        // Check we get the right display name.
        assertEquals(displayName, newLocale.getDisplayLanguage(newLocale));
        assertEquals(displayName, oldLocale.getDisplayLanguage(newLocale));
        assertEquals(displayName, newLocale.getDisplayLanguage(oldLocale));
        assertEquals(displayName, oldLocale.getDisplayLanguage(oldLocale));

        // Check that none of the 'getAvailableLocales' methods are accidentally returning two
        // equal locales (because to ICU they're different, but we mangle one into the other).
        assertOnce(newLocale, BreakIterator.getAvailableLocales());
        assertOnce(newLocale, Calendar.getAvailableLocales());
        assertOnce(newLocale, Collator.getAvailableLocales());
        assertOnce(newLocale, DateFormat.getAvailableLocales());
        assertOnce(newLocale, DateFormatSymbols.getAvailableLocales());
        assertOnce(newLocale, NumberFormat.getAvailableLocales());
        assertOnce(newLocale, Locale.getAvailableLocales());
    }

    private static void assertNew(String newCode, String oldCode, String displayName) {
        Locale newLocale = new Locale(newCode);
        Locale oldLocale = new Locale(oldCode);
        assertEquals(newLocale, oldLocale);

        assertEquals(newCode, newLocale.getLanguage());
        assertEquals(newCode, oldLocale.getLanguage());

        // Check we get the right display name.
        assertEquals(displayName, newLocale.getDisplayLanguage(newLocale));
        assertEquals(displayName, oldLocale.getDisplayLanguage(newLocale));
        assertEquals(displayName, newLocale.getDisplayLanguage(oldLocale));
        assertEquals(displayName, oldLocale.getDisplayLanguage(oldLocale));

        assertOnce(newLocale, BreakIterator.getAvailableLocales());
        assertOnce(newLocale, Calendar.getAvailableLocales());
        assertOnce(newLocale, Collator.getAvailableLocales());
        assertOnce(newLocale, DateFormat.getAvailableLocales());
        assertOnce(newLocale, DateFormatSymbols.getAvailableLocales());
        assertOnce(newLocale, NumberFormat.getAvailableLocales());
        assertOnce(newLocale, Locale.getAvailableLocales());
    }

    private static void assertOnce(Locale element, Locale[] array) {
        int count = 0;
        for (Locale l : array) {
            if (l.equals(element)) {
                ++count;
            }
        }
        assertEquals(element + " was not found", 1, count);
    }

    @Before
    public void setup() throws Exception {
        clearLocalesCache();
        clearCollator();
    }

    @After
    public void cleanup() throws Exception {
        clearLocalesCache();
        clearCollator();
    }

    // There are 2 test cases. Both of them rely on ICU caches. So depending on the execution
    // order caches might be initialized with USE_NEW_ISO_LOCALE_CODES set to true (or false)
    // and other test case assumes that its value was false (or true). Resetting caches to
    // make them consistent. Such functionality is not assumed by ICU hence reflection is used
    // heavily. Future ICU updates might break cleanup methods.
    // Cleaning before execution as they might have been already initialized with the opposite
    // state.
    public void clearLocalesCache() {
        libcore.icu.ICU.clearAvailableLocales();
    }

    public void clearCollator() throws Exception {
        var collatorClass = android.icu.text.Collator.class;
        var shimField = collatorClass.getDeclaredField("shim");
        shimField.setAccessible(true);
        shimField.set(null, null);

        var icuResourceBundleClass = Class.forName("android.icu.impl.ICUResourceBundle");
        var cacheField = icuResourceBundleClass.getDeclaredField("GET_AVAILABLE_CACHE");
        cacheField.setAccessible(true);
        var cache = cacheField.get(null);

        var mapField = Class.forName("android.icu.impl.SoftCache").getDeclaredField("map");
        mapField.setAccessible(true);
        var map = (Map) mapField.get(cache);
        map.clear();
    }
}
