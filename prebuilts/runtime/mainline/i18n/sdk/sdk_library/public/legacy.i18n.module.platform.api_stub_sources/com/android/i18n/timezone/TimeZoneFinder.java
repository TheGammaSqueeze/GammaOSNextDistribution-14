/*
 * Copyright (C) 2017 The Android Open Source Project
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

import java.io.IOException;

/**
 * A class that can find matching time zones by loading data from the tzlookup.xml file.
 * @hide
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public final class TimeZoneFinder {

private TimeZoneFinder() { throw new RuntimeException("Stub!"); }

/**
 * Obtains an instance for use when resolving time zones. This method handles using the correct
 * file when there are several to choose from. This method never returns {@code null}. No
 * in-depth validation is performed on the file content, see {@link #validate()}.
 */

public static com.android.i18n.timezone.TimeZoneFinder getInstance() { throw new RuntimeException("Stub!"); }

/**
 * Obtains an instance using a specific data file, throwing an IOException if the file does not
 * exist or is not readable. This method never returns {@code null}. No in-depth validation is
 * performed on the file content, see {@link #validate()}.
 */

public static com.android.i18n.timezone.TimeZoneFinder createInstance(java.lang.String path) throws java.io.IOException { throw new RuntimeException("Stub!"); }

/**
 * Parses the data file, throws an exception if it is invalid or cannot be read.
 */

public void validate() throws java.io.IOException { throw new RuntimeException("Stub!"); }

/**
 * Returns the IANA rules version associated with the data. If there is no version information
 * or there is a problem reading the file then {@code null} is returned.
 */

public java.lang.String getIanaVersion() { throw new RuntimeException("Stub!"); }

/**
 * Loads all the country &lt;-&gt; time zone mapping data into memory. This method can return
 * {@code null} in the event of an error while reading the underlying data files.
 */

public com.android.i18n.timezone.CountryZonesFinder getCountryZonesFinder() { throw new RuntimeException("Stub!"); }

/**
 * Returns a {@link com.android.i18n.timezone.CountryTimeZones CountryTimeZones} object associated with the specified country code.
 * Caching is handled as needed. If the country code is not recognized or there is an error
 * during lookup this method can return null.
 */

public com.android.i18n.timezone.CountryTimeZones lookupCountryTimeZones(java.lang.String countryIso) { throw new RuntimeException("Stub!"); }
}

