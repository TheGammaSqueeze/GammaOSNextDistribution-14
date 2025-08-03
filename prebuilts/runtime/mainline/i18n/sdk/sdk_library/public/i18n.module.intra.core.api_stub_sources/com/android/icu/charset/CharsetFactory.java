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


package com.android.icu.charset;

import java.nio.charset.Charset;

/**
 * Provides an implementation of {@link java.nio.charset.Charset Charset} using ICU.
 *
 * @hide
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public class CharsetFactory {

private CharsetFactory() { throw new RuntimeException("Stub!"); }

/**
 * Returns the names of available charsets.
 *
 * @hide
 */

public static java.lang.String[] getAvailableCharsetNames() { throw new RuntimeException("Stub!"); }

/**
 * Create an {@link java.nio.charset.Charset Charset} instance from its name.
 *
 * @hide
 */

public static java.nio.charset.Charset create(java.lang.String charsetName) { throw new RuntimeException("Stub!"); }
}

