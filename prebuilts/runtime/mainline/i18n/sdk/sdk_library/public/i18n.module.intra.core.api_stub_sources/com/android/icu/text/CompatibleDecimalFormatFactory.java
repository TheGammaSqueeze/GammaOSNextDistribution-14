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

import android.icu.text.DecimalFormat;

/**
 * Factory class to create {@link android.icu.text.DecimalFormat DecimalFormat} instances compatible with
 * {@link java.text.DecimalFormat}.
 *
 * @hide
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public class CompatibleDecimalFormatFactory {

private CompatibleDecimalFormatFactory() { throw new RuntimeException("Stub!"); }

/**
 * Creates an instance compatible with {@link java.text.DecimalFormat}.
 *
 * WARNING: Do not call the following methods, and otherwise the internal states for the
 * compatibility could be disrupted.
 *   - {@link android.icu.text.DecimalFormat#setParseStrict(boolean) DecimalFormat#setParseStrict(boolean)}
 *
 * @hide
 */

public static android.icu.text.DecimalFormat create(java.lang.String pattern, android.icu.text.DecimalFormatSymbols dfs) { throw new RuntimeException("Stub!"); }
}

