/*
 * Copyright (C) 2018 The Android Open Source Project
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


package com.android.i18n.timezone;


/**
 * A container class for debug information.
 *
 * @hide
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public class DebugInfo {

public DebugInfo() { throw new RuntimeException("Stub!"); }

/**
 * Adds a key / string value.
 *
 * @return {@code this} for chaining calls
 */

public com.android.i18n.timezone.DebugInfo addStringEntry(java.lang.String key, java.lang.String value) { throw new RuntimeException("Stub!"); }

/**
 * Adds a key / string value. Converts the supplied int value to a String.
 *
 * @return {@code this} for chaining calls
 */

public com.android.i18n.timezone.DebugInfo addStringEntry(java.lang.String key, int value) { throw new RuntimeException("Stub!"); }

/** Returns all the debug entries. */

public java.util.List<com.android.i18n.timezone.DebugInfo.DebugEntry> getDebugEntries() { throw new RuntimeException("Stub!"); }
/**
 * A generic key/value for a single piece of debug information.
 *
 * @hide
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public static class DebugEntry {

public DebugEntry(java.lang.String key, java.lang.String stringValue) { throw new RuntimeException("Stub!"); }

public java.lang.String getKey() { throw new RuntimeException("Stub!"); }

public java.lang.String getStringValue() { throw new RuntimeException("Stub!"); }
}

}

