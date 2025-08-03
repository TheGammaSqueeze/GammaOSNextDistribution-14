/*
 * Copyright (C) 2009 The Android Open Source Project
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


/**
 * This class is used by android.webkit.DateSorter.
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public class DateSorterBridge {

private DateSorterBridge() { throw new RuntimeException("Stub!"); }

/**
 * @throws java.lang.AssertionError for the legacy behavior when the ICU data is missing
 */

public static com.android.icu.text.DateSorterBridge createInstance(java.util.Locale locale) throws java.lang.AssertionError { throw new RuntimeException("Stub!"); }

public java.lang.String getYesterday() { throw new RuntimeException("Stub!"); }

public java.lang.String getToday() { throw new RuntimeException("Stub!"); }
}

