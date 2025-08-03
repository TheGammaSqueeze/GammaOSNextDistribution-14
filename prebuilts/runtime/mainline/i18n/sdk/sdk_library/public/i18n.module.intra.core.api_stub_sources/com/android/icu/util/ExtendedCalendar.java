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


package com.android.icu.util;

import android.icu.util.Calendar;
import android.icu.util.ULocale;

/**
 * Provide extra functionalities on top of {@link android.icu.util.Calendar Calendar} public APIs.
 *
 * @hide
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public class ExtendedCalendar {

private ExtendedCalendar() { throw new RuntimeException("Stub!"); }

/**
 * Get an instance
 *
 * @param uLocale non-null ULocale
 * @hide
 */

public static com.android.icu.util.ExtendedCalendar getInstance(android.icu.util.ULocale uLocale) { throw new RuntimeException("Stub!"); }

/**
 * Returns the {@link android.icu.text.DateFormat} pattern for the given date and time styles.
 * Similiar to {@link android.icu.util.Calendar#getDateTimeFormat(int,int,android.icu.util.ULocale) Calendar#getDateTimeFormat(int, int, ULocale)} but returns the pattern
 * string instead of an instance of {@link android.icu.text.DateFormat}.
 *
 * @see {@link android.icu.util.Calendar#getDateTimeFormat(int,int,android.icu.util.ULocale) Calendar#getDateTimeFormat(int, int, ULocale)} for the style parameters.
 *
 * @hide
 */

public java.lang.String getDateTimePattern(int dateStyle, int timeStyle) { throw new RuntimeException("Stub!"); }
}

