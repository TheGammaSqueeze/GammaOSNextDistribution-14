/*
 * Copyright (C) 2011 The Android Open Source Project
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

package libcore.icu;

import com.android.tools.layoutlib.annotations.LayoutlibDelegate;

import java.util.Locale;

/**
 * Delegate implementing the native methods of libcore.icu.ICU
 *
 * Through the layoutlib_create tool, the original native methods of ICU have been replaced
 * by calls to methods of the same name in this delegate class.
 *
 */
public class ICU_Delegate {
    // --- Native methods accessing ICU's database.

    @LayoutlibDelegate
    /*package*/ static String[] getAvailableLocalesNative() {
        return new String[0];
    }

    @LayoutlibDelegate
    /*package*/ static String getISO3Country(String locale) {
        return "";
    }

    @LayoutlibDelegate
    /*package*/ static String getISO3Language(String locale) {
        return "";
    }

    @LayoutlibDelegate
    /*package*/ static String getScript(String locale) {
        return "";
    }

    @LayoutlibDelegate
    /*package*/ static String[] getISOLanguagesNative() {
        return Locale.getISOLanguages();
    }

    @LayoutlibDelegate
    /*package*/ static String[] getISOCountriesNative() {
        return Locale.getISOCountries();
    }

    @LayoutlibDelegate
    /*package*/ static String getDefaultLocale() {
        return Locale.getDefault().toString();
    }

    @LayoutlibDelegate
    /*package*/ static String getCldrVersion() {
        return "";
    }

    @LayoutlibDelegate
    /*package*/ static String getIcuVersion() {
        return "";
    }

    @LayoutlibDelegate
    /*package*/ static String getUnicodeVersion() {
        return "";
    }
}
