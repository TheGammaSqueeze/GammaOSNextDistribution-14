/* GENERATED SOURCE. DO NOT MODIFY. */
// ? 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 *******************************************************************************
 * Copyright (C) 2007-2010, International Business Machines Corporation and    *
 * others. All Rights Reserved.                                                *
 *******************************************************************************
 */

package android.icu.util;


/**
 * <code>TimeZoneTransition</code> is a class representing a time zone transition.
 * An instance has a time of transition and rules for both before and
 * after the transition.
 *
 * @hide Only a subset of ICU is exposed in Android
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public class TimeZoneTransition {

TimeZoneTransition() { throw new RuntimeException("Stub!"); }

/**
 * Returns the time of transition in milliseconds since the base time.
 *
 * @return The time of the transition in milliseconds since the base time.
 */

public long getTime() { throw new RuntimeException("Stub!"); }

/**
 * Returns the rule used after the transition.
 *
 * @return The time zone rule used after the transition.
 */

public android.icu.util.TimeZoneRule getTo() { throw new RuntimeException("Stub!"); }

/**
 * Returns the rule used before the transition.
 *
 * @return The time zone rule used after the transition.
 */

public android.icu.util.TimeZoneRule getFrom() { throw new RuntimeException("Stub!"); }

/**
 * Returns a <code>String</code> representation of this <code>TimeZoneTransition</code> object.
 * This method is used for debugging purpose only.  The string representation can be changed
 * in future version of ICU without any notice.
 */

public java.lang.String toString() { throw new RuntimeException("Stub!"); }
}

