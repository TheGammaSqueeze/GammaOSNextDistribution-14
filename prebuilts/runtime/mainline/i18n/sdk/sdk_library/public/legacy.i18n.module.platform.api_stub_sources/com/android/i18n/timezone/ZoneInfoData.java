/*
 * Copyright (C) 2007 The Android Open Source Project
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
 * This class holds the data of a time zone backed by the tzfiles. An instance is immutable.
 *
 * <p>This reads time zone information from a binary file stored on the platform. The binary file
 * is essentially a single file containing compacted versions of all the tzfiles produced by the
 * zone info compiler (zic) tool (see {@code man 5 tzfile} for details of the format and
 * {@code man 8 zic}) and an index by long name, e.g. Europe/London.
 *
 * <p>The compacted form is created by
 * {@code system/timezone/input_tools/android/zone_compactor/main/java/ZoneCompactor.java} and is
 * used by both this and Bionic. {@link com.android.i18n.timezone.ZoneInfoDb ZoneInfoDb} is responsible for mapping the binary file, and
 * reading the index and creating a {@link com.android.i18n.timezone.internal.BufferIterator BufferIterator} that provides access to an entry for a
 * specific file. This class is responsible for reading the data from that {@link com.android.i18n.timezone.internal.BufferIterator BufferIterator}
 * and storing it a representation to support the {@link java.util.TimeZone} and
 * {@link java.util.GregorianCalendar} implementations. See
 * {@link com.android.i18n.timezone.ZoneInfoData#readTimeZone(java.lang.String,com.android.i18n.timezone.internal.BufferIterator) ZoneInfoData#readTimeZone(String, BufferIterator)}.
 *
 * <p>This class does not use all the information from the {@code tzfile}; it uses:
 * {@code tzh_timecnt} and the associated transition times and type information. For each type
 * (described by {@code struct ttinfo}) it uses {@code tt_gmtoff} and {@code tt_isdst}.
 *
 * @hide
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public final class ZoneInfoData {

private ZoneInfoData() { throw new RuntimeException("Stub!"); }

public boolean equals(java.lang.Object obj) { throw new RuntimeException("Stub!"); }

public int hashCode() { throw new RuntimeException("Stub!"); }

/**
 * Returns a string containing the internal states for debug purpose.
 */

public java.lang.String toString() { throw new RuntimeException("Stub!"); }

/**
 * Returns the time zone id.
 *
 * @hide
 */

public java.lang.String getID() { throw new RuntimeException("Stub!"); }
}

