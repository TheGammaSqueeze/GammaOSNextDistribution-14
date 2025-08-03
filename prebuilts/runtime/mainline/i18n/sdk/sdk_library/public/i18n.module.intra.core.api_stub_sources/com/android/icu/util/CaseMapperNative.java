/*
 * Copyright (C) 2019 The Android Open Source Project
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

/**
 * Provide functionalities implemented by ICU4C for {@link java.lang.String}, which avoids using
 * {@link android.icu.lang.UCharacter} to prevent circular runtime dependency.
 *
 * @hide
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public class CaseMapperNative {

private CaseMapperNative() { throw new RuntimeException("Stub!"); }

/**
 * Returns the lower-case version of the string in the {@code locale}.
 *
 * Behaves the same as {@link android.icu.lang.UCharacter#toLowerCase(Locale, String)}, but
 * implemented by ICU4C.
 *
 * @hide
 */

public static java.lang.String toLowerCase(java.lang.String s, java.util.Locale locale) { throw new RuntimeException("Stub!"); }

/**
 * Returns the upper-case version of the string in the {@code locale}.
 *
 * Behaves the same as {@link android.icu.lang.UCharacter#toUpperCase(Locale, String)}, but
 * implemented by ICU4C.
 *
 * @hide
 */

public static java.lang.String toUpperCase(java.lang.String s, java.util.Locale locale) { throw new RuntimeException("Stub!"); }
}

