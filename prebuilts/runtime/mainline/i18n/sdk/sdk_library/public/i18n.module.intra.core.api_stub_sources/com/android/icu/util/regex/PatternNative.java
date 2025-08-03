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


package com.android.icu.util.regex;


/**
 * Provide an entry point to use ICU4C icu::RegexPattern.
 *
 * @hide
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public class PatternNative {

private PatternNative() { throw new RuntimeException("Stub!"); }

/**
 * Create an {@link com.android.icu.util.regex.PatternNative PatternNative} with a regular expression string.
 *
 * @param pattern the regular expression to be compiled
 * @param flags a bit set of {@link java.util.regex.Pattern#UNIX_LINES},
 *              {@link java.util.regex.Pattern#CASE_INSENSITIVE},
 *              {@link java.util.regex.Pattern#COMMENTS},
 *              {@link java.util.regex.Pattern#MULTILINE} and
 *              {@link java.util.regex.Pattern#DOTALL}.
 *
 * @hide
 */

public static com.android.icu.util.regex.PatternNative create(java.lang.String pattern, int flags) { throw new RuntimeException("Stub!"); }
}

