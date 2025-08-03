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

import android.icu.text.IDNA;

/**
 * Provide extra functionalities on top of {@link android.icu.text.IDNA IDNA} public APIs.
 *
 * @hide
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public class ExtendedIDNA {

private ExtendedIDNA() { throw new RuntimeException("Stub!"); }

/**
 * Converts an International Domain Name to Ascii conforming the IDNA2003 spec.
 *
 * @param src input string
 * @param options A bit set of 0, {@link java.net.IDN#ALLOW_UNASSIGNED IDN#ALLOW_UNASSIGNED}
 *                and {@link java.net.IDN#USE_STD3_ASCII_RULES IDN#USE_STD3_ASCII_RULES}. Undefined behavior if other flag is
 *                passed.
 * @return the converted string
 *
 * @hide
 */

public static java.lang.StringBuffer convertIDNToASCII(java.lang.String src, int options) throws android.icu.text.StringPrepParseException { throw new RuntimeException("Stub!"); }

/**
 * Converts an International Domain Name to a Unicode string conforming the IDNA2003 spec.
 *
 * @param src input string
 * @param options A bit set of 0, {@link java.net.IDN#ALLOW_UNASSIGNED IDN#ALLOW_UNASSIGNED}
 *                and {@link java.net.IDN#USE_STD3_ASCII_RULES IDN#USE_STD3_ASCII_RULES}. Undefined behavior if other flag is
 *                passed.
 * @return the converted string
 *
 * @hide
 */

public static java.lang.StringBuffer convertIDNToUnicode(java.lang.String src, int options) throws android.icu.text.StringPrepParseException { throw new RuntimeException("Stub!"); }
}

