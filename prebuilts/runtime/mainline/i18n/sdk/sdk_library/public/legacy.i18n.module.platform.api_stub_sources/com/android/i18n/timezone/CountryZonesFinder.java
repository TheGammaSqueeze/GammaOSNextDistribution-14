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
 * An in-memory representation of country &lt;-&gt; time zone mapping data.
 * @hide
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public final class CountryZonesFinder {

CountryZonesFinder() { throw new RuntimeException("Stub!"); }

/**
 * Returns an immutable list of country ISO codes with time zones. The codes can be passed to
 * {@link #lookupCountryTimeZones(java.lang.String)} and similar methods.
 */

public java.util.List<java.lang.String> lookupAllCountryIsoCodes() { throw new RuntimeException("Stub!"); }

/**
 * Returns an immutable list of {@link com.android.i18n.timezone.CountryTimeZones CountryTimeZones} for countries that use the specified
 * time zone. An exact, case-sensitive match is performed on the zone ID. If the match  but the method also
 * checks for alternative zone IDs. This method never returns null and will usually return a
 * list containing a single element. It can return an empty list if the zone ID is
 * not recognized or it is not associated with a country.
 */

public java.util.List<com.android.i18n.timezone.CountryTimeZones> lookupCountryTimeZonesForZoneId(java.lang.String zoneId) { throw new RuntimeException("Stub!"); }

/**
 * Returns a {@link com.android.i18n.timezone.CountryTimeZones CountryTimeZones} object associated with the specified country code. If one
 * cannot be found this method returns {@code null}.
 */

public com.android.i18n.timezone.CountryTimeZones lookupCountryTimeZones(java.lang.String countryIso) { throw new RuntimeException("Stub!"); }
}

