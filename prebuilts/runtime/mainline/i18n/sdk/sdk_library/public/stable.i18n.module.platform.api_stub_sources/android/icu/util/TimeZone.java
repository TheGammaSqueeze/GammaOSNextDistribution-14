/* GENERATED SOURCE. DO NOT MODIFY. */
// ? 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 * @(#)TimeZone.java    1.51 00/01/19
 *
 * Copyright (C) 1996-2016, International Business Machines
 * Corporation and others.  All Rights Reserved.
 */


package android.icu.util;

import android.icu.util.ULocale.Category;
import java.util.Date;
import java.util.Set;
import java.util.MissingResourceException;

/**
 * <strong>[icu enhancement]</strong> ICU's replacement for {@link java.util.TimeZone}.&nbsp;Methods, fields, and other functionality specific to ICU are labeled '<strong>[icu]</strong>'.
 *
 * <p><code>TimeZone</code> represents a time zone offset, and also computes daylight
 * savings.
 *
 * <p>Typically, you get a <code>TimeZone</code> using {@link #getDefault()}
 * which creates a <code>TimeZone</code> based on the time zone where the program
 * is running. For example, for a program running in Japan, <code>getDefault</code>
 * creates a <code>TimeZone</code> object based on Japanese Standard Time.
 *
 * <p>You can also get a <code>TimeZone</code> using {@link #getTimeZone(java.lang.String)}
 * along with a time zone ID. For instance, the time zone ID for the
 * U.S. Pacific Time zone is "America/Los_Angeles". So, you can get a
 * U.S. Pacific Time <code>TimeZone</code> object with:
 *
 * <blockquote>
 * <pre>
 * TimeZone tz = TimeZone.getTimeZone("America/Los_Angeles");
 * </pre>
 * </blockquote>
 * You can use the {@link #getAvailableIDs()} method to iterate through
 * all the supported time zone IDs, or getCanonicalID method to check
 * if a time zone ID is supported or not. You can then choose a
 * supported ID to get a <code>TimeZone</code>.
 * If the time zone you want is not represented by one of the
 * supported IDs, then you can create a custom time zone ID with
 * the following syntax:
 *
 * <blockquote>
 * <pre>
 * GMT[+|-]hh[[:]mm]
 * </pre>
 * </blockquote>
 *
 * For example, you might specify GMT+14:00 as a custom
 * time zone ID.  The <code>TimeZone</code> that is returned
 * when you specify a custom time zone ID uses the specified
 * offset from GMT(=UTC) and does not observe daylight saving
 * time. For example, you might specify GMT+14:00 as a custom
 * time zone ID to create a TimeZone representing 14 hours ahead
 * of GMT (with no daylight saving time). In addition,
 * <code>getCanonicalID</code> can also be used to
 * normalize a custom time zone ID.
 *
 * <p>For compatibility with JDK 1.1.x, some other three-letter time zone IDs
 * (such as "PST", "CTT", "AST") are also supported. However, <strong>their
 * use is deprecated</strong> because the same abbreviation is often used
 * for multiple time zones (for example, "CST" could be U.S. "Central Standard
 * Time" and "China Standard Time"), and the Java platform can then only
 * recognize one of them.
 *
 * @see          android.icu.util.Calendar
 * @see          android.icu.util.GregorianCalendar
 * @author       Mark Davis, Deborah Goldsmith, Chen-Lieh Huang, Alan Liu
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public abstract class TimeZone implements java.io.Serializable, java.lang.Cloneable, android.icu.util.Freezable<android.icu.util.TimeZone> {

/**
 * Default constructor.  (For invocation by subclass constructors,
 * typically implicit.)
 */

public TimeZone() { throw new RuntimeException("Stub!"); }

/**
 * Gets the time zone offset, for current date, modified in case of
 * daylight savings. This is the offset to add *to* UTC to get local time.
 * @param era the era of the given date.
 * @param year the year in the given date.
 * @param month the month in the given date.
 * Month is 0-based. e.g., 0 for January.
 * @param day the day-in-month of the given date.
 * @param dayOfWeek the day-of-week of the given date.
 * @param milliseconds the millis in day in <em>standard</em> local time.
 * @return the offset to add *to* GMT to get local time.
 */

public abstract int getOffset(int era, int year, int month, int day, int dayOfWeek, int milliseconds);

/**
 * Returns the offset of this time zone from UTC at the specified
 * date. If Daylight Saving Time is in effect at the specified
 * date, the offset value is adjusted with the amount of daylight
 * saving.
 *
 * @param date the date represented in milliseconds since January 1, 1970 00:00:00 GMT
 * @return the amount of time in milliseconds to add to UTC to get local time.
 *
 * @see android.icu.util.Calendar#ZONE_OFFSET
 * @see android.icu.util.Calendar#DST_OFFSET
 * @see #getOffset(long, boolean, int[])
 */

public int getOffset(long date) { throw new RuntimeException("Stub!"); }

/**
 * Returns the time zone raw and GMT offset for the given moment
 * in time.  Upon return, local-millis = GMT-millis + rawOffset +
 * dstOffset.  All computations are performed in the proleptic
 * Gregorian calendar.  The default implementation in the TimeZone
 * class delegates to the 8-argument getOffset().
 *
 * @param date moment in time for which to return offsets, in
 * units of milliseconds from January 1, 1970 0:00 GMT, either GMT
 * time or local wall time, depending on `local'.
 * @param local if true, `date' is local wall time; otherwise it
 * is in GMT time.
 * @param offsets output parameter to receive the raw offset, that
 * is, the offset not including DST adjustments, in offsets[0],
 * and the DST offset, that is, the offset to be added to
 * `rawOffset' to obtain the total offset between local and GMT
 * time, in offsets[1]. If DST is not in effect, the DST offset is
 * zero; otherwise it is a positive value, typically one hour.
 */

public void getOffset(long date, boolean local, int[] offsets) { throw new RuntimeException("Stub!"); }

/**
 * Sets the base time zone offset to GMT.
 * This is the offset to add *to* UTC to get local time.
 * @param offsetMillis the given base time zone offset to GMT.
 */

public abstract void setRawOffset(int offsetMillis);

/**
 * Gets unmodified offset, NOT modified in case of daylight savings.
 * This is the offset to add *to* UTC to get local time.
 * @return the unmodified offset to add *to* UTC to get local time.
 */

public abstract int getRawOffset();

/**
 * Gets the ID of this time zone.
 * @return the ID of this time zone.
 */

public java.lang.String getID() { throw new RuntimeException("Stub!"); }

/**
 * Sets the time zone ID. This does not change any other data in
 * the time zone object.
 * @param ID the new time zone ID.
 */

public void setID(java.lang.String ID) { throw new RuntimeException("Stub!"); }

/**
 * Returns a name of this time zone suitable for presentation to the user
 * in the default <code>DISPLAY</code> locale.
 * This method returns the long generic name.
 * If the display name is not available for the locale,
 * a fallback based on the country, city, or time zone id will be used.
 * @return the human-readable name of this time zone in the default locale.
 * @see android.icu.util.ULocale.Category#DISPLAY
 */

public final java.lang.String getDisplayName() { throw new RuntimeException("Stub!"); }

/**
 * Returns a name of this time zone suitable for presentation to the user
 * in the specified locale.
 * This method returns the long generic name.
 * If the display name is not available for the locale,
 * a fallback based on the country, city, or time zone id will be used.
 * @param locale the locale in which to supply the display name.
 * @return the human-readable name of this time zone in the given locale
 * or in the default locale if the given locale is not recognized.
 */

public final java.lang.String getDisplayName(java.util.Locale locale) { throw new RuntimeException("Stub!"); }

/**
 * Returns a name of this time zone suitable for presentation to the user
 * in the specified locale.
 * This method returns the long name, not including daylight savings.
 * If the display name is not available for the locale,
 * a fallback based on the country, city, or time zone id will be used.
 * @param locale the ulocale in which to supply the display name.
 * @return the human-readable name of this time zone in the given locale
 * or in the default ulocale if the given ulocale is not recognized.
 */

public final java.lang.String getDisplayName(android.icu.util.ULocale locale) { throw new RuntimeException("Stub!"); }

/**
 * Returns a name of this time zone suitable for presentation to the user
 * in the default <code>DISPLAY</code> locale.
 * If the display name is not available for the locale,
 * then this method returns a string in the localized GMT offset format
 * such as <code>GMT[+-]HH:mm</code>.
 * @param daylight if true, return the daylight savings name.
 * @param style the output style of the display name.  Valid styles are
 * <code>SHORT</code>, <code>LONG</code>, <code>SHORT_GENERIC</code>,
 * <code>LONG_GENERIC</code>, <code>SHORT_GMT</code>, <code>LONG_GMT</code>,
 * <code>SHORT_COMMONLY_USED</code> or <code>GENERIC_LOCATION</code>.
 * @return the human-readable name of this time zone in the default locale.
 * @see android.icu.util.ULocale.Category#DISPLAY
 */

public final java.lang.String getDisplayName(boolean daylight, int style) { throw new RuntimeException("Stub!"); }

/**
 * Returns a name of this time zone suitable for presentation to the user
 * in the specified locale.
 * If the display name is not available for the locale,
 * then this method returns a string in the localized GMT offset format
 * such as <code>GMT[+-]HH:mm</code>.
 * @param daylight if true, return the daylight savings name.
 * @param style the output style of the display name.  Valid styles are
 * <code>SHORT</code>, <code>LONG</code>, <code>SHORT_GENERIC</code>,
 * <code>LONG_GENERIC</code>, <code>SHORT_GMT</code>, <code>LONG_GMT</code>,
 * <code>SHORT_COMMONLY_USED</code> or <code>GENERIC_LOCATION</code>.
 * @param locale the locale in which to supply the display name.
 * @return the human-readable name of this time zone in the given locale
 * or in the default locale if the given locale is not recognized.
 * @exception java.lang.IllegalArgumentException style is invalid.
 */

public java.lang.String getDisplayName(boolean daylight, int style, java.util.Locale locale) { throw new RuntimeException("Stub!"); }

/**
 * Returns a name of this time zone suitable for presentation to the user
 * in the specified locale.
 * If the display name is not available for the locale,
 * then this method returns a string in the localized GMT offset format
 * such as <code>GMT[+-]HH:mm</code>.
 * @param daylight if true, return the daylight savings name.
 * @param style the output style of the display name.  Valid styles are
 * <code>SHORT</code>, <code>LONG</code>, <code>SHORT_GENERIC</code>,
 * <code>LONG_GENERIC</code>, <code>SHORT_GMT</code>, <code>LONG_GMT</code>,
 * <code>SHORT_COMMONLY_USED</code> or <code>GENERIC_LOCATION</code>.
 * @param locale the locale in which to supply the display name.
 * @return the human-readable name of this time zone in the given locale
 * or in the default locale if the given locale is not recognized.
 * @exception java.lang.IllegalArgumentException style is invalid.
 */

public java.lang.String getDisplayName(boolean daylight, int style, android.icu.util.ULocale locale) { throw new RuntimeException("Stub!"); }

/**
 * Returns the amount of time to be added to local standard time
 * to get local wall clock time.
 * <p>
 * The default implementation always returns 3600000 milliseconds
 * (i.e., one hour) if this time zone observes Daylight Saving
 * Time. Otherwise, 0 (zero) is returned.
 * <p>
 * If an underlying TimeZone implementation subclass supports
 * historical Daylight Saving Time changes, this method returns
 * the known latest daylight saving value.
 *
 * @return the amount of saving time in milliseconds
 */

public int getDSTSavings() { throw new RuntimeException("Stub!"); }

/**
 * Queries if this time zone uses daylight savings time.
 * @return true if this time zone uses daylight savings time,
 * false, otherwise.
 * <p><strong>Note:</strong>The default implementation of
 * ICU TimeZone uses the tz database, which supports historic
 * rule changes, for system time zones. With the implementation,
 * there are time zones that used daylight savings time in the
 * past, but no longer used currently. For example, Asia/Tokyo has
 * never used daylight savings time since 1951. Most clients would
 * expect that this method to return <code>false</code> for such case.
 * The default implementation of this method returns <code>true</code>
 * when the time zone uses daylight savings time in the current
 * (Gregorian) calendar year.
 */

public abstract boolean useDaylightTime();

/**
 * Queries if this time zone is in daylight saving time or will observe
 * daylight saving time at any future time.
 * <p>The default implementation in this class returns <code>true</code> if {@link #useDaylightTime()}
 * or {@link #inDaylightTime(java.util.Date) inDaylightTime(new Date())} returns <code>true</code>.
 * <p>
 * <strong>Note:</strong> This method was added for {@link java.util.TimeZone} compatibility
 * support. The {@link java.util.TimeZone#useDaylightTime()} method only checks the last known
 * rule(s), therefore it may return false even the zone observes daylight saving time currently.
 * {@link java.util.TimeZone} added <code>observesDaylightTime()</code> to resolve the issue.
 * In ICU, {@link #useDaylightTime()} works differently. The ICU implementation checks if the
 * zone uses daylight saving time in the current calendar year. Therefore, it will never return
 * <code>false</code> if daylight saving time is currently used.
 * <p>
 * ICU's TimeZone subclass implementations override this method to support the same behavior
 * with {@link java.util.TimeZone#observesDaylightTime()}. Unlike {@link #useDaylightTime()},
 * the implementation does not take past daylight saving time into account, so
 * that this method may return <code>false</code> even when {@link #useDaylightTime()} returns
 * <code>true</code>.
 *
 * @return <code>true</code> if this time zone is in daylight saving time or will observe
 * daylight saving time at any future time.
 * @see #useDaylightTime
 */

public boolean observesDaylightTime() { throw new RuntimeException("Stub!"); }

/**
 * Queries if the given date is in daylight savings time in
 * this time zone.
 * @param date the given Date.
 * @return true if the given date is in daylight savings time,
 * false, otherwise.
 */

public abstract boolean inDaylightTime(java.util.Date date);

/**
 * Gets the <code>TimeZone</code> for the given ID.
 *
 * @param ID the ID for a <code>TimeZone</code>, such as "America/Los_Angeles",
 * or a custom ID such as "GMT-8:00". Note that the support of abbreviations,
 * such as "PST", is for JDK 1.1.x compatibility only and full names should be used.
 *
 * @return the specified <code>TimeZone</code>, or a mutable clone of the UNKNOWN_ZONE
 * if the given ID cannot be understood or if the given ID is "Etc/Unknown".
 * @see #UNKNOWN_ZONE
 */

public static android.icu.util.TimeZone getTimeZone(java.lang.String ID) { throw new RuntimeException("Stub!"); }

/**
 * Gets the <code>TimeZone</code> for the given ID. The instance of <code>TimeZone</code>
 * returned by this method is immutable. Any methods mutate the instance({@link #setID(java.lang.String)},
 * {@link #setRawOffset(int)}) will throw <code>UnsupportedOperationException</code> upon its
 * invocation.
 *
 * @param ID the ID for a <code>TimeZone</code>, such as "America/Los_Angeles",
 * or a custom ID such as "GMT-8:00". Note that the support of abbreviations,
 * such as "PST", is for JDK 1.1.x compatibility only and full names should be used.
 *
 * @return the specified <code>TimeZone</code>, or the UNKNOWN_ZONE
 * if the given ID cannot be understood.
 * @see #UNKNOWN_ZONE
 */

public static android.icu.util.TimeZone getFrozenTimeZone(java.lang.String ID) { throw new RuntimeException("Stub!"); }

/**
 * Gets the <code>TimeZone</code> for the given ID and the timezone type.
 * @param ID the ID for a <code>TimeZone</code>, such as "America/Los_Angeles", or a
 * custom ID such as "GMT-8:00". Note that the support of abbreviations, such as
 * "PST", is for JDK 1.1.x compatibility only and full names should be used.
 * @param type Time zone type, either <code>TIMEZONE_ICU</code> or
 * <code>TIMEZONE_JDK</code>.
 * @return the specified <code>TimeZone</code>, or a mutable clone of the UNKNOWN_ZONE if the given ID
 * cannot be understood or if the given ID is "Etc/Unknown".
 * @see #UNKNOWN_ZONE
 */

public static android.icu.util.TimeZone getTimeZone(java.lang.String ID, int type) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns a set of time zone ID strings with the given filter conditions.
 * <p><b>Note:</b>A <code>Set</code> returned by this method is
 * immutable.
 * @param zoneType      The system time zone type.
 * @param region        The ISO 3166 two-letter country code or UN M.49 three-digit area code.
 *                      When null, no filtering done by region.
 * @param rawOffset     An offset from GMT in milliseconds, ignoring the effect of daylight savings
 *                      time, if any. When null, no filtering done by zone offset.
 * @return an immutable set of system time zone IDs.
 * @see android.icu.util.TimeZone.SystemTimeZoneType
 */

public static java.util.Set<java.lang.String> getAvailableIDs(android.icu.util.TimeZone.SystemTimeZoneType zoneType, java.lang.String region, java.lang.Integer rawOffset) { throw new RuntimeException("Stub!"); }

/**
 * Return a new String array containing all system TimeZone IDs
 * with the given raw offset from GMT.  These IDs may be passed to
 * <code>get()</code> to construct the corresponding TimeZone
 * object.
 * @param rawOffset the offset in milliseconds from GMT
 * @return an array of IDs for system TimeZones with the given
 * raw offset.  If there are none, return a zero-length array.
 * @see #getAvailableIDs(SystemTimeZoneType, String, Integer)
 */

public static java.lang.String[] getAvailableIDs(int rawOffset) { throw new RuntimeException("Stub!"); }

/**
 * Return a new String array containing all system TimeZone IDs
 * associated with the given country.  These IDs may be passed to
 * <code>get()</code> to construct the corresponding TimeZone
 * object.
 * @param country a two-letter ISO 3166 country code, or <code>null</code>
 * to return zones not associated with any country
 * @return an array of IDs for system TimeZones in the given
 * country.  If there are none, return a zero-length array.
 * @see #getAvailableIDs(SystemTimeZoneType, String, Integer)
 */

public static java.lang.String[] getAvailableIDs(java.lang.String country) { throw new RuntimeException("Stub!"); }

/**
 * Return a new String array containing all system TimeZone IDs.
 * These IDs (and only these IDs) may be passed to
 * <code>get()</code> to construct the corresponding TimeZone
 * object.
 * @return an array of all system TimeZone IDs
 * @see #getAvailableIDs(SystemTimeZoneType, String, Integer)
 */

public static java.lang.String[] getAvailableIDs() { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns the number of IDs in the equivalency group that
 * includes the given ID.  An equivalency group contains zones
 * that have the same GMT offset and rules.
 *
 * <p>The returned count includes the given ID; it is always &gt;= 1
 * for valid IDs.  The given ID must be a system time zone.  If it
 * is not, returns zero.
 * @param id a system time zone ID
 * @return the number of zones in the equivalency group containing
 * 'id', or zero if 'id' is not a valid system ID
 * @see #getEquivalentID
 */

public static int countEquivalentIDs(java.lang.String id) { throw new RuntimeException("Stub!"); }

/**
 * Returns an ID in the equivalency group that
 * includes the given ID.  An equivalency group contains zones
 * that have the same GMT offset and rules.
 *
 * <p>The given index must be in the range 0..n-1, where n is the
 * value returned by <code>countEquivalentIDs(id)</code>.  For
 * some value of 'index', the returned value will be equal to the
 * given id.  If the given id is not a valid system time zone, or
 * if 'index' is out of range, then returns an empty string.
 * @param id a system time zone ID
 * @param index a value from 0 to n-1, where n is the value
 * returned by <code>countEquivalentIDs(id)</code>
 * @return the ID of the index-th zone in the equivalency group
 * containing 'id', or an empty string if 'id' is not a valid
 * system ID or 'index' is out of range
 * @see #countEquivalentIDs
 */

public static java.lang.String getEquivalentID(java.lang.String id, int index) { throw new RuntimeException("Stub!"); }

/**
 * Gets the default <code>TimeZone</code> for this host.
 * The source of the default <code>TimeZone</code>
 * may vary with implementation.
 * @return a default <code>TimeZone</code>.
 */

public static android.icu.util.TimeZone getDefault() { throw new RuntimeException("Stub!"); }

/**
 * Returns true if this zone has the same rule and offset as another zone.
 * That is, if this zone differs only in ID, if at all.  Returns false
 * if the other zone is null.
 * @param other the <code>TimeZone</code> object to be compared with
 * @return true if the other zone is not null and is the same as this one,
 * with the possible exception of the ID
 */

public boolean hasSameRules(android.icu.util.TimeZone other) { throw new RuntimeException("Stub!"); }

/**
 * Overrides clone.
 */

public java.lang.Object clone() { throw new RuntimeException("Stub!"); }

/**
 * Overrides equals.
 * @return <code>true</code> if this object is the same as the obj argument; <code>false</code> otherwise.
 */

public boolean equals(java.lang.Object obj) { throw new RuntimeException("Stub!"); }

/**
 * Overrides hashCode.
 * @return a hash code value for this object.
 */

public int hashCode() { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns the time zone data version currently used by ICU.
 *
 * @return the version string, such as "2007f"
 * @throws java.util.MissingResourceException if ICU time zone resource bundle
 * is missing or the version information is not available.
 */

public static java.lang.String getTZDataVersion() { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns the canonical system time zone ID or the normalized
 * custom time zone ID for the given time zone ID.
 * @param id The input time zone ID to be canonicalized.
 * @return The canonical system time zone ID or the custom time zone ID
 * in normalized format for the given time zone ID.  When the given time zone ID
 * is neither a known system time zone ID nor a valid custom time zone ID,
 * null is returned.
 */

public static java.lang.String getCanonicalID(java.lang.String id) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns the canonical system time zone ID or the normalized
 * custom time zone ID for the given time zone ID.
 * @param id The input time zone ID to be canonicalized.
 * @param isSystemID When non-null boolean array is specified and
 * the given ID is a known system time zone ID, true is set to <code>isSystemID[0]</code>
 * @return The canonical system time zone ID or the custom time zone ID
 * in normalized format for the given time zone ID.  When the given time zone ID
 * is neither a known system time zone ID nor a valid custom time zone ID,
 * null is returned.
 */

public static java.lang.String getCanonicalID(java.lang.String id, boolean[] isSystemID) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns the region code associated with the given
 * system time zone ID. The region code is either ISO 3166
 * 2-letter country code or UN M.49 3-digit area code.
 * When the time zone is not associated with a specific location,
 * for example - "Etc/UTC", "EST5EDT", then this method returns
 * "001" (UN M.49 area code for World).
 * @param id the system time zone ID.
 * @return the region code associated with the given
 * system time zone ID.
 * @throws java.lang.IllegalArgumentException if <code>id</code> is not a known system ID.
 * @see #getAvailableIDs(String)
 */

public static java.lang.String getRegion(java.lang.String id) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Converts a system time zone ID to an equivalent Windows time zone ID. For example,
 * Windows time zone ID "Pacific Standard Time" is returned for input "America/Los_Angeles".
 *
 * <p>There are system time zones that cannot be mapped to Windows zones. When the input
 * system time zone ID is unknown or unmappable to a Windows time zone, then this
 * method returns <code>null</code>.
 *
 * <p>This implementation utilizes <a href="http://unicode.org/cldr/charts/supplemental/zone_tzid.html">
 * Zone-Tzid mapping data</a>. The mapping data is updated time to time. To get the latest changes,
 * please read the ICU user guide section <a href="https://unicode-org.github.io/icu/userguide/datetime/timezone#updating-the-time-zone-data">
 * Updating the Time Zone Data</a>.
 *
 * @param id A system time zone ID
 * @return A Windows time zone ID mapped from the input system time zone ID,
 * or <code>null</code> when the input ID is unknown or unmappable.
 * @see #getIDForWindowsID(String, String)
 */

public static java.lang.String getWindowsID(java.lang.String id) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Converts a Windows time zone ID to an equivalent system time zone ID
 * for a region. For example, system time zone ID "America/Los_Angeles" is returned
 * for input Windows ID "Pacific Standard Time" and region "US" (or <code>null</code>),
 * "America/Vancouver" is returned for the same Windows ID "Pacific Standard Time" and
 * region "CA".
 *
 * <p>Not all Windows time zones can be mapped to system time zones. When the input
 * Windows time zone ID is unknown or unmappable to a system time zone, then this
 * method returns <code>null</code>.
 *
 * <p>This implementation utilizes <a href="http://unicode.org/cldr/charts/supplemental/zone_tzid.html">
 * Zone-Tzid mapping data</a>. The mapping data is updated time to time. To get the latest changes,
 * please read the ICU user guide section <a href="https://unicode-org.github.io/icu/userguide/datetime/timezone#updating-the-time-zone-data">
 * Updating the Time Zone Data</a>.
 *
 * @param winid A Windows time zone ID
 * @param region A region code, or <code>null</code> if no regional preference.
 * @return A system time zone ID mapped from the input Windows time zone ID,
 * or <code>null</code> when the input ID is unknown or unmappable.
 * @see #getWindowsID(String)
 */

public static java.lang.String getIDForWindowsID(java.lang.String winid, java.lang.String region) { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public boolean isFrozen() { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public android.icu.util.TimeZone freeze() { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public android.icu.util.TimeZone cloneAsThawed() { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> A style specifier for <code>getDisplayName()</code> indicating
 * a long name derived from the timezone's fallback name, such as
 * "United States (Los Angeles)."
 */

public static final int GENERIC_LOCATION = 7; // 0x7

/**
 * <strong>[icu]</strong> The immutable GMT (=UTC) time zone. Its ID is "Etc/GMT".
 */

public static final android.icu.util.TimeZone GMT_ZONE;
static { GMT_ZONE = null; }

/**
 * A style specifier for <code>getDisplayName()</code> indicating
 * a long name, such as "Pacific Standard Time."
 * @see #SHORT
 */

public static final int LONG = 1; // 0x1

/**
 * <strong>[icu]</strong> A style specifier for <code>getDisplayName()</code> indicating
 * a long generic name, such as "Pacific Time."
 * @see #SHORT_GENERIC
 */

public static final int LONG_GENERIC = 3; // 0x3

/**
 * <strong>[icu]</strong> A style specifier for <code>getDisplayName()</code> indicating
 * a long name derived from the timezone's offset, such as "GMT-08:00."
 * @see #SHORT_GMT
 */

public static final int LONG_GMT = 5; // 0x5

/**
 * A style specifier for <code>getDisplayName()</code> indicating
 * a short name, such as "PST."
 * @see #LONG
 */

public static final int SHORT = 0; // 0x0

/**
 * <strong>[icu]</strong> A style specifier for <code>getDisplayName()</code> indicating
 * a short name derived from the timezone's short standard or daylight
 * timezone name ignoring commonlyUsed, such as "PDT."
 */

public static final int SHORT_COMMONLY_USED = 6; // 0x6

/**
 * <strong>[icu]</strong> A style specifier for <code>getDisplayName()</code> indicating
 * a short generic name, such as "PT."
 * @see #LONG_GENERIC
 */

public static final int SHORT_GENERIC = 2; // 0x2

/**
 * <strong>[icu]</strong> A style specifier for <code>getDisplayName()</code> indicating
 * a short name derived from the timezone's offset, such as "-0800."
 * @see #LONG_GMT
 */

public static final int SHORT_GMT = 4; // 0x4

/**
 * <strong>[icu]</strong> A time zone implementation type indicating ICU's own TimeZone used by
 * <code>getTimeZone</code>.
 */

public static final int TIMEZONE_ICU = 0; // 0x0

/**
 * <strong>[icu]</strong> A time zone implementation type indicating the {@link java.util.TimeZone}
 * used by <code>getTimeZone</code>.
 */

public static final int TIMEZONE_JDK = 1; // 0x1

/**
 * <strong>[icu]</strong> The immutable (frozen) "unknown" time zone.
 * It behaves like the GMT/UTC time zone but has the UNKNOWN_ZONE_ID = "Etc/Unknown".
 * {@link android.icu.util.TimeZone#getTimeZone(java.lang.String) TimeZone#getTimeZone(String)} returns a mutable clone of this
 * time zone if the input ID is not recognized.
 *
 * @see #UNKNOWN_ZONE_ID
 * @see #getTimeZone(String)
 */

public static final android.icu.util.TimeZone UNKNOWN_ZONE;
static { UNKNOWN_ZONE = null; }

/**
 * <strong>[icu]</strong> The time zone ID reserved for unknown time zone.
 * @see #getTimeZone(String)
 */

public static final java.lang.String UNKNOWN_ZONE_ID = "Etc/Unknown";
/**
 * <strong>[icu]</strong> System time zone type constants used by filtering zones in
 * {@link android.icu.util.TimeZone#getAvailableIDs(android.icu.util.TimeZone.SystemTimeZoneType,java.lang.String,java.lang.Integer) TimeZone#getAvailableIDs(SystemTimeZoneType, String, Integer)}
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public enum SystemTimeZoneType {
/**
 * Any system zones.
 */

ANY,
/**
 * Canonical system zones.
 */

CANONICAL,
/**
 * Canonical system zones associated with actual locations.
 */

CANONICAL_LOCATION;
}

}

