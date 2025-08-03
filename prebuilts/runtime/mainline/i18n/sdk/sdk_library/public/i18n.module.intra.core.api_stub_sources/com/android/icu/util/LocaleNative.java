/*
 * Copyright (C) 2008 The Android Open Source Project
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


package com.android.icu.util;

import java.util.Locale;
import android.icu.util.ULocale;
import libcore.api.IntraCoreApi;

/**
 * Provide functionalities implemented by ICU4C for {@link java.util.Locale}, which avoids using
 * {@link android.icu.util.ULocale} to prevent circular runtime dependency.
 *
 * @hide
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public final class LocaleNative {

private LocaleNative() { throw new RuntimeException("Stub!"); }

/**
 * Set the default Locale in ICU4C.
 *
 * Libcore's default locale is synchronized with the ICU4C's default locale. But libicu.so
 * does not expose uloc_setDefault via NDK because app can otherwise break this synchronization.
 * Instead, expose this uloc_setDefault as @IntraCoreApi called by libcore.
 *
 * @param languageTag BCP-47 language tag to be set the default locale.
 *
 * @hide
 */

public static void setDefault(java.lang.String languageTag) { throw new RuntimeException("Stub!"); }

/**
 * Returns localized country name.
 *
 * Behaves the same as {@link android.icu.util.ULocale#getDisplayCountry(ULocale)}, but
 * implemented by ICU4C.
 *
 * @param targetLocale the locale in which to display the name.
 * @param locale  the locale whose country will be displayed
 *
 * @hide
 */

public static java.lang.String getDisplayCountry(java.util.Locale targetLocale, java.util.Locale locale) { throw new RuntimeException("Stub!"); }

/**
 * Returns localized language name.
 *
 * Behaves the same as {@link android.icu.util.ULocale#getDisplayCountry(ULocale)}, but
 * implemented by ICU4C.
 *
 * @param targetLocale the locale in which to display the name.
 * @param locale the locale whose language will be displayed
 *
 * @hide
 */

public static java.lang.String getDisplayLanguage(java.util.Locale targetLocale, java.util.Locale locale) { throw new RuntimeException("Stub!"); }

/**
 * Returns localized variant name.
 *
 * Behaves the same as {@link android.icu.util.ULocale#getDisplayCountry(ULocale)}, but
 * implemented by ICU4C.
 *
 * @param targetLocale the locale in which to display the name.
 * @param locale the locale whose variant will be displayed
 *
 * @hide
 */

public static java.lang.String getDisplayVariant(java.util.Locale targetLocale, java.util.Locale locale) { throw new RuntimeException("Stub!"); }

/**
 * Returns localized script name.
 *
 * Behaves the same as {@link android.icu.util.ULocale#getDisplayScript(ULocale)} (ULocale)},
 * but implemented by ICU4C.
 *
 * @param targetLocale the locale in which to display the name.
 * @param locale  the locale whose script will be displayed
 *
 * @hide
 */

public static java.lang.String getDisplayScript(java.util.Locale targetLocale, java.util.Locale locale) { throw new RuntimeException("Stub!"); }
}

