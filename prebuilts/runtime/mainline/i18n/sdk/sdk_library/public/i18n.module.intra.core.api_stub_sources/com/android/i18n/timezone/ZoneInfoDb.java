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
 * A class used to initialize the time zone database. This implementation uses the
 * Olson tzdata as the source of time zone information. However, to conserve
 * disk space (inodes) and reduce I/O, all the data is concatenated into a single file,
 * with an index to indicate the starting position of each time zone record.
 *
 * @hide - used to implement TimeZone
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public final class ZoneInfoDb {

private ZoneInfoDb() { throw new RuntimeException("Stub!"); }

/**
 * Obtains the singleton instance.
 *
 * @hide
 */

public static com.android.i18n.timezone.ZoneInfoDb getInstance() { throw new RuntimeException("Stub!"); }

/**
 * Returns an array containing all time zone ids sorted in lexicographical order for
 * binary searching.
 *
 * @hide
 */

public java.lang.String[] getAvailableIDs() { throw new RuntimeException("Stub!"); }

/**
 * Returns ids of all time zones with the given raw UTC offset.
 *
 * @hide
 */

public java.lang.String[] getAvailableIDs(int rawUtcOffset) { throw new RuntimeException("Stub!"); }

/**
 * Creates {@link com.android.i18n.timezone.ZoneInfoData ZoneInfoData} object from the time zone {@code id}. Returns null if the id
 * is not found.
 *
 * @hide
 */

public com.android.i18n.timezone.ZoneInfoData makeZoneInfoData(java.lang.String id) { throw new RuntimeException("Stub!"); }

protected void finalize() throws java.lang.Throwable { throw new RuntimeException("Stub!"); }
}

