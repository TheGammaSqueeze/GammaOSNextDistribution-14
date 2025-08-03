/* GENERATED SOURCE. DO NOT MODIFY. */
// ? 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 *******************************************************************************
 * Copyright (C) 2007-2016, International Business Machines Corporation and    *
 * others. All Rights Reserved.                                                *
 *******************************************************************************
 */

package android.icu.util;


/**
 * <code>TimeZoneRule</code> is an abstract class representing a rule for time zone.
 * <code>TimeZoneRule</code> has a set of time zone attributes, such as zone name,
 * raw offset (UTC offset for standard time) and daylight saving time offset.
 *
 * @see android.icu.util.TimeZoneTransition
 * @see android.icu.util.RuleBasedTimeZone
 *
 * @hide Only a subset of ICU is exposed in Android
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public abstract class TimeZoneRule implements java.io.Serializable {

TimeZoneRule() { throw new RuntimeException("Stub!"); }

/**
 * Gets the amount of daylight saving delta time from the standard time.
 *
 * @return  The amount of daylight saving offset used by this rule
 *          in milliseconds.
 */

public int getDSTSavings() { throw new RuntimeException("Stub!"); }

/**
 * Returns a <code>String</code> representation of this <code>TimeZoneRule</code> object.
 * This method is used for debugging purpose only.  The string representation can be changed
 * in future version of ICU without any notice.
 */

public java.lang.String toString() { throw new RuntimeException("Stub!"); }
}

