/*
 * Copyright (C) 2020 The Android Open Source Project
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


package com.android.icu.text;

import java.util.Locale;

/**
 * Provide functionalities implemented by ICU4C for {@link libcore.icu.TimeZoneNames} and
 * the implementation should be faster than the public APIs provided by
 * {@link android.icu.text.TimeZoneNames}.
 *
 * @hide
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public final class TimeZoneNamesNative {

private TimeZoneNamesNative() { throw new RuntimeException("Stub!"); }

/**
 * For each tzId passed returns a list of arrays of five elements: Olson name, long name,
 * short name, long DST name and short DST name.
 *
 * @param locale Locale object
 * @param tzIds  List of timezone ids
 * @see libcore.icu.TimeZoneNames.ZoneStringsCache#create(java.util.Locale)
 * @return List of [Time zone id, long, short, long DST, short DST] for each id from
 * {@code tzIds}. Any of these value except Olson name can be null.
 *
 * @hide
 */

public static java.lang.String[][] getFilledZoneStrings(java.util.Locale locale, java.lang.String[] tzIds) { throw new RuntimeException("Stub!"); }
}

