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

import android.icu.text.DecimalFormatSymbols;

/**
 * Provide extra functionalities on top of {@link android.icu.text.DecimalFormatSymbols DecimalFormatSymbols} public APIs.
 *
 * @hide
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public class ExtendedDecimalFormatSymbols {

private ExtendedDecimalFormatSymbols() { throw new RuntimeException("Stub!"); }

/**
 * Returns an instance of {@link com.android.icu.text.ExtendedDecimalFormatSymbols ExtendedDecimalFormatSymbols}.
 *
 * @hide
 */

public static com.android.icu.text.ExtendedDecimalFormatSymbols getInstance(android.icu.util.ULocale uLocale, android.icu.text.NumberingSystem ns) { throw new RuntimeException("Stub!"); }

/**
 * Returns the localized pattern separator. Note that the public API
 * {@link android.icu.text.DecimalFormatSymbols#getPatternSeparator() DecimalFormatSymbols#getPatternSeparator()} does not localize pattern separator.
 *
 * @hide
 */

public java.lang.String getLocalizedPatternSeparator() { throw new RuntimeException("Stub!"); }
}

