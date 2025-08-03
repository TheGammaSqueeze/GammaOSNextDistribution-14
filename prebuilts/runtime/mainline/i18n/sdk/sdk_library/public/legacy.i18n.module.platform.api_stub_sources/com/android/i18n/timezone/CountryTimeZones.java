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

import java.util.List;
import android.icu.util.TimeZone;

/**
 * Information about a country's time zones.
 *
 * @hide
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public final class CountryTimeZones {

private CountryTimeZones() { throw new RuntimeException("Stub!"); }

/**
 * Returns the ISO code for the country.
 */

public java.lang.String getCountryIso() { throw new RuntimeException("Stub!"); }

/**
 * Returns true if the ISO code for the country is a case-insensitive match for the one
 * supplied.
 */

public boolean matchesCountryCode(java.lang.String countryIso) { throw new RuntimeException("Stub!"); }

/**
 * Returns the default time zone ID for the country. Can return {@code null} in cases when no
 * data is available or the time zone ID provided to
 * {@link #createValidated(java.lang.String,java.lang.String,boolean,boolean,java.util.List,java.lang.String)} was not recognized.
 */

public java.lang.String getDefaultTimeZoneId() { throw new RuntimeException("Stub!"); }

/**
 * Returns the default time zone for the country. Can return {@code null} in cases when no data
 * is available or the time zone ID provided to
 * {@link #createValidated(java.lang.String,java.lang.String,boolean,boolean,java.util.List,java.lang.String)} was not recognized.
 */

public synchronized android.icu.util.TimeZone getDefaultTimeZone() { throw new RuntimeException("Stub!"); }

/**
 * Qualifier for a country's default time zone. {@code true} indicates that the country's
 * default time zone would be a good choice <em>generally</em> when there's no UTC offset
 * information available. This will only be {@code true} in countries with multiple zones where
 * a large majority of the population is covered by only one of them.
 */

public boolean isDefaultTimeZoneBoosted() { throw new RuntimeException("Stub!"); }

/**
 * Returns an immutable, ordered list of time zone mappings for the country in an undefined but
 * "priority" order. The list can be empty if there were no zones configured or the configured
 * zone IDs were not recognized.
 */

public java.util.List<com.android.i18n.timezone.CountryTimeZones.TimeZoneMapping> getTimeZoneMappings() { throw new RuntimeException("Stub!"); }

/**
 * Returns {@code true} if the country has at least one time zone that uses UTC at the given
 * time. This is an efficient check when trying to validate received UTC offset information.
 * For example, there are situations when a detected zero UTC offset cannot be distinguished
 * from "no information available" or a corrupted signal. This method is useful because checking
 * offset information for large countries is relatively expensive but it is generally only the
 * countries close to the prime meridian that use UTC at <em>any</em> time of the year.
 *
 * @param whenMillis the time the offset information is for in milliseconds since the beginning
 *     of the Unix epoch
 */

public boolean hasUtcZone(long whenMillis) { throw new RuntimeException("Stub!"); }

/**
 * Returns a time zone for the country, if there is one, that matches the supplied properties.
 * If there are multiple matches and the {@code bias} is one of them then it is returned,
 * otherwise an arbitrary match is returned based on the {@link
 * #getEffectiveTimeZoneMappingsAt(long)} ordering.
 *
 * @param whenMillis the UTC time to match against
 * @param bias the time zone to prefer, can be {@code null} to indicate there is no preference
 * @param totalOffsetMillis the offset from UTC at {@code whenMillis}
 * @param isDst the Daylight Savings Time state at {@code whenMillis}. {@code true} means DST,
 *     {@code false} means not DST
 * @return an {@link com.android.i18n.timezone.CountryTimeZones.OffsetResult OffsetResult} with information about a matching zone, or {@code null} if
 *     there is no match
 */

public com.android.i18n.timezone.CountryTimeZones.OffsetResult lookupByOffsetWithBias(long whenMillis, android.icu.util.TimeZone bias, int totalOffsetMillis, boolean isDst) { throw new RuntimeException("Stub!"); }

/**
 * Returns a time zone for the country, if there is one, that matches the supplied properties.
 * If there are multiple matches and the {@code bias} is one of them then it is returned,
 * otherwise an arbitrary match is returned based on the {@link
 * #getEffectiveTimeZoneMappingsAt(long)} ordering.
 *
 * @param whenMillis the UTC time to match against
 * @param bias the time zone to prefer, can be {@code null} to indicate there is no preference
 * @param totalOffsetMillis the offset from UTC at {@code whenMillis}
 * @return an {@link com.android.i18n.timezone.CountryTimeZones.OffsetResult OffsetResult} with information about a matching zone, or {@code null} if
 *     there is no match
 */

public com.android.i18n.timezone.CountryTimeZones.OffsetResult lookupByOffsetWithBias(long whenMillis, android.icu.util.TimeZone bias, int totalOffsetMillis) { throw new RuntimeException("Stub!"); }

/**
 * Returns an immutable, ordered list of time zone mappings for the country in an undefined but
 * "priority" order, filtered so that only "effective" time zone IDs are returned. An
 * "effective" time zone is one that differs from another time zone used in the country after
 * {@code whenMillis}. The list can be empty if there were no zones configured or the configured
 * zone IDs were not recognized.
 */

public java.util.List<com.android.i18n.timezone.CountryTimeZones.TimeZoneMapping> getEffectiveTimeZoneMappingsAt(long whenMillis) { throw new RuntimeException("Stub!"); }

public boolean equals(java.lang.Object o) { throw new RuntimeException("Stub!"); }

public int hashCode() { throw new RuntimeException("Stub!"); }

public java.lang.String toString() { throw new RuntimeException("Stub!"); }
/**
 * The result of lookup up a time zone using offset information (and possibly more).
 *
 * @hide
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public static final class OffsetResult {

OffsetResult() { throw new RuntimeException("Stub!"); }

public android.icu.util.TimeZone getTimeZone() { throw new RuntimeException("Stub!"); }

public boolean isOnlyMatch() { throw new RuntimeException("Stub!"); }

public java.lang.String toString() { throw new RuntimeException("Stub!"); }
}

/**
 * A mapping to a time zone ID with some associated metadata.
 *
 * @hide
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public static final class TimeZoneMapping {

TimeZoneMapping() { throw new RuntimeException("Stub!"); }

public java.lang.String getTimeZoneId() { throw new RuntimeException("Stub!"); }

public boolean isShownInPickerAt(java.time.Instant time) { throw new RuntimeException("Stub!"); }

/**
 * Returns a list of alternative time zone IDs that are linked to this one. Can be empty,
 * never returns null.
 */

public java.util.List<java.lang.String> getAlternativeIds() { throw new RuntimeException("Stub!"); }

/**
 * Returns a {@link android.icu.util.TimeZone TimeZone} object for this mapping, or {@code null} if the ID is unknown.
 */

public android.icu.util.TimeZone getTimeZone() { throw new RuntimeException("Stub!"); }

public static com.android.i18n.timezone.CountryTimeZones.TimeZoneMapping createForTests(java.lang.String timeZoneId, boolean showInPicker, java.lang.Long notUsedAfter, java.util.List<java.lang.String> alternativeIds) { throw new RuntimeException("Stub!"); }

public boolean equals(java.lang.Object o) { throw new RuntimeException("Stub!"); }

public int hashCode() { throw new RuntimeException("Stub!"); }

public java.lang.String toString() { throw new RuntimeException("Stub!"); }
}

}

