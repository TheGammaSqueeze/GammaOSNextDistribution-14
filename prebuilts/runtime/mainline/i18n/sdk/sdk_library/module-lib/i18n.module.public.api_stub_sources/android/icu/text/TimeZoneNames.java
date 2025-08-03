/* GENERATED SOURCE. DO NOT MODIFY. */
// ? 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 *******************************************************************************
 * Copyright (C) 2011-2016, International Business Machines Corporation and
 * others. All Rights Reserved.
 *******************************************************************************
 */

package android.icu.text;

import java.util.Locale;
import android.icu.util.TimeZone;
import android.icu.util.ULocale;

/**
 * <code>TimeZoneNames</code> is an abstract class representing the time zone display name data model defined
 * by <a href="http://www.unicode.org/reports/tr35/">UTS#35 Unicode Locale Data Markup Language (LDML)</a>.
 * The model defines meta zone, which is used for storing a set of display names. A meta zone can be shared
 * by multiple time zones. Also a time zone may have multiple meta zone historic mappings.
 * <p>
 * For example, people in the United States refer the zone used by the east part of North America as "Eastern Time".
 * The tz database contains multiple time zones "America/New_York", "America/Detroit", "America/Montreal" and some
 * others that belong to "Eastern Time". However, assigning different display names to these time zones does not make
 * much sense for most of people.
 * <p>
 * In <a href="http://cldr.unicode.org/">CLDR</a> (which uses LDML for representing locale data), the display name
 * "Eastern Time" is stored as long generic display name of a meta zone identified by the ID "America_Eastern".
 * Then, there is another table maintaining the historic mapping to meta zones for each time zone. The time zones in
 * the above example ("America/New_York", "America/Detroit"...) are mapped to the meta zone "America_Eastern".
 * <p>
 * Sometimes, a time zone is mapped to a different time zone in the past. For example, "America/Indiana/Knox"
 * had been moving "Eastern Time" and "Central Time" back and forth. Therefore, it is necessary that time zone
 * to meta zones mapping data are stored by date range.
 *
 * <p><b>Note:</b>
 * <p>
 * {@link android.icu.text.TimeZoneFormat TimeZoneFormat} assumes an instance of <code>TimeZoneNames</code> is immutable. If you want to provide
 * your own <code>TimeZoneNames</code> implementation and use it with {@link android.icu.text.TimeZoneFormat TimeZoneFormat}, you must follow
 * the contract.
 * <p>
 * The methods in this class assume that time zone IDs are already canonicalized. For example, you may not get proper
 * result returned by a method with time zone ID "America/Indiana/Indianapolis", because it's not a canonical time zone
 * ID (the canonical time zone ID for the time zone is "America/Indianapolis". See
 * {@link android.icu.util.TimeZone#getCanonicalID(java.lang.String) TimeZone#getCanonicalID(String)} about ICU canonical time zone IDs.
 *
 * <p>
 * In CLDR, most of time zone display names except location names are provided through meta zones. But a time zone may
 * have a specific name that is not shared with other time zones.
 *
 * For example, time zone "Europe/London" has English long name for standard time "Greenwich Mean Time", which is also
 * shared with other time zones. However, the long name for daylight saving time is "British Summer Time", which is only
 * used for "Europe/London".
 *
 * <p>
 * {@link #getTimeZoneDisplayName(java.lang.String,android.icu.text.TimeZoneNames.NameType)} is designed for accessing a name only used by a single time zone.
 * But is not necessarily mean that a subclass implementation use the same model with CLDR. A subclass implementation
 * may provide time zone names only through {@link #getTimeZoneDisplayName(java.lang.String,android.icu.text.TimeZoneNames.NameType)}, or only through
 * {@link #getMetaZoneDisplayName(java.lang.String,android.icu.text.TimeZoneNames.NameType)}, or both.
 *
 * <p>
 * The default <code>TimeZoneNames</code> implementation returned by {@link #getInstance(android.icu.util.ULocale)} uses the locale data
 * imported from CLDR. In CLDR, set of meta zone IDs and mappings between zone IDs and meta zone IDs are shared by all
 * locales. Therefore, the behavior of {@link #getAvailableMetaZoneIDs()}, {@link #getAvailableMetaZoneIDs(java.lang.String)},
 * {@link #getMetaZoneID(java.lang.String,long)}, and {@link #getReferenceZoneID(java.lang.String,java.lang.String)} won't be changed no matter
 * what locale is used for getting an instance of <code>TimeZoneNames</code>.
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public abstract class TimeZoneNames implements java.io.Serializable {

TimeZoneNames() { throw new RuntimeException("Stub!"); }

/**
 * Returns an instance of <code>TimeZoneNames</code> for the specified locale.
 *
 * @param locale
 *            The locale.
 * @return An instance of <code>TimeZoneNames</code>
 */

public static android.icu.text.TimeZoneNames getInstance(android.icu.util.ULocale locale) { throw new RuntimeException("Stub!"); }

/**
 * Returns an instance of <code>TimeZoneNames</code> for the specified
 * {@link java.util.Locale}.
 *
 * @param locale
 *            The {@link java.util.Locale}.
 * @return An instance of <code>TimeZoneDisplayNames</code>
 */

public static android.icu.text.TimeZoneNames getInstance(java.util.Locale locale) { throw new RuntimeException("Stub!"); }

/**
 * Returns an instance of <code>TimeZoneNames</code> containing only short specific
 * zone names ({@link android.icu.text.TimeZoneNames.NameType#SHORT_STANDARD NameType#SHORT_STANDARD} and {@link android.icu.text.TimeZoneNames.NameType#SHORT_DAYLIGHT NameType#SHORT_DAYLIGHT}),
 * compatible with the IANA tz database's zone abbreviations (not localized).
 * <br>
 * Note: The input locale is used for resolving ambiguous names (e.g. "IST" is parsed
 * as Israel Standard Time for Israel, while it is parsed as India Standard Time for
 * all other regions). The zone names returned by this instance are not localized.
 */

public static android.icu.text.TimeZoneNames getTZDBInstance(android.icu.util.ULocale locale) { throw new RuntimeException("Stub!"); }

/**
 * Returns an immutable set of all available meta zone IDs.
 * @return An immutable set of all available meta zone IDs.
 */

public abstract java.util.Set<java.lang.String> getAvailableMetaZoneIDs();

/**
 * Returns an immutable set of all available meta zone IDs used by the given time zone.
 *
 * @param tzID
 *            The canonical time zone ID.
 * @return An immutable set of all available meta zone IDs used by the given time zone.
 */

public abstract java.util.Set<java.lang.String> getAvailableMetaZoneIDs(java.lang.String tzID);

/**
 * Returns the meta zone ID for the given canonical time zone ID at the given date.
 *
 * @param tzID
 *            The canonical time zone ID.
 * @param date
 *            The date.
 * @return The meta zone ID for the given time zone ID at the given date. If the time zone does not have a
 *         corresponding meta zone at the given date or the implementation does not support meta zones, null is
 *         returned.
 */

public abstract java.lang.String getMetaZoneID(java.lang.String tzID, long date);

/**
 * Returns the reference zone ID for the given meta zone ID for the region.
 *
 * Note: Each meta zone must have a reference zone associated with a special region "001" (world).
 * Some meta zones may have region specific reference zone IDs other than the special region
 * "001". When a meta zone does not have any region specific reference zone IDs, this method
 * return the reference zone ID for the special region "001" (world).
 *
 * @param mzID
 *            The meta zone ID.
 * @param region
 *            The region.
 * @return The reference zone ID ("golden zone" in the LDML specification) for the given time zone ID for the
 *         region. If the meta zone is unknown or the implementation does not support meta zones, null is returned.
 */

public abstract java.lang.String getReferenceZoneID(java.lang.String mzID, java.lang.String region);

/**
 * Returns the display name of the meta zone.
 *
 * @param mzID
 *            The meta zone ID.
 * @param type
 *            The display name type. See {@link android.icu.text.TimeZoneNames.NameType TimeZoneNames.NameType}.
 * @return The display name of the meta zone. When this object does not have a localized display name for the given
 *         meta zone with the specified type or the implementation does not provide any display names associated
 *         with meta zones, null is returned.
 */

public abstract java.lang.String getMetaZoneDisplayName(java.lang.String mzID, android.icu.text.TimeZoneNames.NameType type);

/**
 * Returns the display name of the time zone at the given date.
 *
 * <p>
 * <b>Note:</b> This method calls the subclass's {@link #getTimeZoneDisplayName(java.lang.String,android.icu.text.TimeZoneNames.NameType)} first. When the
 * result is null, this method calls {@link #getMetaZoneID(java.lang.String,long)} to get the meta zone ID mapped from the
 * time zone, then calls {@link #getMetaZoneDisplayName(java.lang.String,android.icu.text.TimeZoneNames.NameType)}.
 *
 * @param tzID
 *            The canonical time zone ID.
 * @param type
 *            The display name type. See {@link android.icu.text.TimeZoneNames.NameType TimeZoneNames.NameType}.
 * @param date
 *            The date
 * @return The display name for the time zone at the given date. When this object does not have a localized display
 *         name for the time zone with the specified type and date, null is returned.
 */

public final java.lang.String getDisplayName(java.lang.String tzID, android.icu.text.TimeZoneNames.NameType type, long date) { throw new RuntimeException("Stub!"); }

/**
 * Returns the display name of the time zone. Unlike {@link #getDisplayName(java.lang.String,android.icu.text.TimeZoneNames.NameType,long)},
 * this method does not get a name from a meta zone used by the time zone.
 *
 * @param tzID
 *            The canonical time zone ID.
 * @param type
 *            The display name type. See {@link android.icu.text.TimeZoneNames.NameType TimeZoneNames.NameType}.
 * @return The display name for the time zone. When this object does not have a localized display name for the given
 *         time zone with the specified type, null is returned.
 */

public abstract java.lang.String getTimeZoneDisplayName(java.lang.String tzID, android.icu.text.TimeZoneNames.NameType type);

/**
 * Returns the exemplar location name for the given time zone. When this object does not have a localized location
 * name, the default implementation may still returns a programmatically generated name with the logic described
 * below.
 * <ol>
 * <li>Check if the ID contains "/". If not, return null.
 * <li>Check if the ID does not start with "Etc/" or "SystemV/". If it does, return null.
 * <li>Extract a substring after the last occurrence of "/".
 * <li>Replace "_" with " ".
 * </ol>
 * For example, "New York" is returned for the time zone ID "America/New_York" when this object does not have the
 * localized location name.
 *
 * @param tzID
 *            The canonical time zone ID
 * @return The exemplar location name for the given time zone, or null when a localized location name is not
 *         available and the fallback logic described above cannot extract location from the ID.
 */

public java.lang.String getExemplarLocationName(java.lang.String tzID) { throw new RuntimeException("Stub!"); }
/**
 * Time zone display name types
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public enum NameType {
/**
 * Long display name, such as "Eastern Time".
 */

LONG_GENERIC,
/**
 * Long display name for standard time, such as "Eastern Standard Time".
 */

LONG_STANDARD,
/**
 * Long display name for daylight saving time, such as "Eastern Daylight Time".
 */

LONG_DAYLIGHT,
/**
 * Short display name, such as "ET".
 */

SHORT_GENERIC,
/**
 * Short display name for standard time, such as "EST".
 */

SHORT_STANDARD,
/**
 * Short display name for daylight saving time, such as "EDT".
 */

SHORT_DAYLIGHT,
/**
 * Exemplar location name, such as "Los Angeles".
 */

EXEMPLAR_LOCATION;
}

}

