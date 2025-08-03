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

/**
 * Create an instance from the serialized fields from {@link libcore.util.ZoneInfo}
 * for backward app compatibility.
 *
 * @hide
 */

public static com.android.i18n.timezone.ZoneInfoData createFromSerializationFields(java.lang.String id, java.io.ObjectInputStream.GetField getField) throws java.io.IOException { throw new RuntimeException("Stub!"); }

/**
 * Serialize {@link libcore.util.ZoneInfo} into backward app compatible form.
 *
 * @hide
 */

public void writeToSerializationFields(java.io.ObjectOutputStream.PutField putField) { throw new RuntimeException("Stub!"); }

/**
 * Get the raw and DST offsets for the specified time in milliseconds since
 * 1st Jan 1970 00:00:00.000 UTC.
 *
 * <p>The raw offset, i.e. that part of the total offset which is not due to DST, is stored at
 * index 0 of the {@code offsets} array and the DST offset, i.e. that part of the offset which
 * is due to DST is stored at index 1.
 *
 * @param utcTimeInMillis the UTC time in milliseconds.
 * @param offsets the array whose length must be greater than or equal to 2.
 * @return the total offset which is the sum of the raw and DST offsets.
 *
 * @hide
 */

public int getOffsetsByUtcTime(long utcTimeInMillis, int[] offsets) { throw new RuntimeException("Stub!"); }

/**
 * Returns the offset from UTC in milliseconds at the specified time {@when}.
 *
 * @param when the number of milliseconds since January 1, 1970, 00:00:00 GMT
 *
 * @hide
 */

public int getOffset(long when) { throw new RuntimeException("Stub!"); }

/**
 * Returns whether the given {@code when} is in daylight saving time in this time zone.
 *
 * @param when the number of milliseconds since January 1, 1970, 00:00:00 GMT
 *
 * @hide
 */

public boolean isInDaylightTime(long when) { throw new RuntimeException("Stub!"); }

/**
 * Returns the raw offset in milliseconds. The value is not affected by daylight saving.
 *
 * @hide
 */

public int getRawOffset() { throw new RuntimeException("Stub!"); }

/**
 * Returns the offset of daylight saving in milliseconds in the latest Daylight Savings Time
 * after the time {@code when}. If no known DST occurs after {@code when}, it returns
 * {@code null}.
 *
 * @param when the number of milliseconds since January 1, 1970, 00:00:00 GMT
 *
 * @hide
 */

public java.lang.Integer getLatestDstSavingsMillis(long when) { throw new RuntimeException("Stub!"); }

/**
 * Returns {@code true} if 2 time zones have the same time zone rule.
 *
 * @hide
 */

public boolean hasSameRules(com.android.i18n.timezone.ZoneInfoData other) { throw new RuntimeException("Stub!"); }

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

/**
 * Create a deep copy of this object with a new raw offset.
 *
 * @hide
 */

public com.android.i18n.timezone.ZoneInfoData createCopyWithRawOffset(int newRawOffset) { throw new RuntimeException("Stub!"); }

/**
 * Returns the times (in seconds) at which the offsets changes for any reason, whether that is a
 * change in the offset from UTC or a change in the DST.
 *
 * WARNING: This API is exposed only for app compat usage in @link libcore.util.ZoneInfo}.
 *
 * @hide
 */

public long[] getTransitions() { throw new RuntimeException("Stub!"); }

/**
 * Creates an instance. This method is only for testing purposes.
 *
 * @param transitions The times (in seconds) since beginning of the Unix epoch at which
 *                    the offsets changes
 * @param types the type of the transition. The offsets and standard/daylight states are
 *              represented in the corresponding entry in <code>offsets</code> and
 *              <code>isDsts</code> respectively
 * @param offsets the total offsets of each type. The max allowed size of this array is 256.
 * @param isDsts an entry is {@code true} if the type is daylight saving time. The max allowed
 *               size of this array is 256.
 * @hide
 */

public static com.android.i18n.timezone.ZoneInfoData createInstance(java.lang.String id, long[] transitions, byte[] types, int[] offsets, boolean[] isDsts) { throw new RuntimeException("Stub!"); }

/**
 * The serialized fields in {@link libcore.util.ZoneInfo} kept for backward app compatibility.
 *
 * @hide
 */

public static final java.io.ObjectStreamField[] ZONEINFO_SERIALIZED_FIELDS;
static { ZONEINFO_SERIALIZED_FIELDS = new java.io.ObjectStreamField[0]; }
}

