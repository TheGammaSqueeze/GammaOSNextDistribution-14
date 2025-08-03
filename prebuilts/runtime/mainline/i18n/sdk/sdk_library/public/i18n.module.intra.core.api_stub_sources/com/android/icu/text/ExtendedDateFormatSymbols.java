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

import android.icu.text.DateFormatSymbols;

/**
 * Provides extra data not in {@link android.icu.text.DateFormatSymbols DateFormatSymbols}.
 *
 * @hide used by {@link java.time.format.DateTimeTextProvider}.
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public class ExtendedDateFormatSymbols {

private ExtendedDateFormatSymbols() { throw new RuntimeException("Stub!"); }

/**
 * Get an instance.
 * @hide
 */

public static com.android.icu.text.ExtendedDateFormatSymbols getInstance(android.icu.util.ULocale locale) { throw new RuntimeException("Stub!"); }

/**
 * Get the {@link android.icu.text.DateFormatSymbols DateFormatSymbols}.
 * @hide
 */

public android.icu.text.DateFormatSymbols getDateFormatSymbols() { throw new RuntimeException("Stub!"); }

/**
 * Returns {@link android.icu.text.DateFormatSymbols#NARROW DateFormatSymbols#NARROW} quarter strings.
 *
 * @param context {@link android.icu.text.DateFormatSymbols#FORMAT DateFormatSymbols#FORMAT} or {@link android.icu.text.DateFormatSymbols#STANDALONE DateFormatSymbols#STANDALONE}.
 * @throws java.lang.IllegalArgumentException for bad context or no data.
 * @hide
 */

public java.lang.String[] getNarrowQuarters(int context) { throw new RuntimeException("Stub!"); }
}

