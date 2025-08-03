/* GENERATED SOURCE. DO NOT MODIFY. */
// ? 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 **********************************************************************
 * Copyright (c) 2003-2010, International Business Machines
 * Corporation and others.  All Rights Reserved.
 **********************************************************************
 * Author: Alan Liu
 * Created: October 2 2003
 * Since: ICU 2.8
 **********************************************************************
 */


package android.icu.impl;

import android.icu.util.TimeZone;
import java.util.Date;

/**
 * <code>TimeZoneAdapter</code> wraps a android.icu.util.TimeZone
 * subclass and inherits from java.util.TimeZone.
 * Without this class, we would need to 'port' java.util.Date to
 * android.icu.util as well, so that Date could interoperate properly
 * with the android.icu.util TimeZone and Calendar classes.  With this
 * class, we can use java.util.Date together with android.icu.util
 * classes.
 *
 * @see android.icu.util.TimeZone#setDefault
 * @author Alan Liu
 * @hide Only a subset of ICU is exposed in Android
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public class TimeZoneAdapter extends java.util.TimeZone {

TimeZoneAdapter() { throw new RuntimeException("Stub!"); }

/**
 * Given a java.util.TimeZone, wrap it in the appropriate adapter
 * subclass of android.icu.util.TimeZone and return the adapter.
 */

public static java.util.TimeZone wrap(android.icu.util.TimeZone tz) { throw new RuntimeException("Stub!"); }

/**
 * TimeZone API; calls through to wrapped time zone.
 */

public void setID(java.lang.String ID) { throw new RuntimeException("Stub!"); }

/**
 * TimeZone API; calls through to wrapped time zone.
 */

public boolean hasSameRules(java.util.TimeZone other) { throw new RuntimeException("Stub!"); }

/**
 * TimeZone API; calls through to wrapped time zone.
 */

public int getOffset(int era, int year, int month, int day, int dayOfWeek, int millis) { throw new RuntimeException("Stub!"); }

/**
 * TimeZone API; calls through to wrapped time zone.
 */

public int getRawOffset() { throw new RuntimeException("Stub!"); }

/**
 * TimeZone API; calls through to wrapped time zone.
 */

public void setRawOffset(int offsetMillis) { throw new RuntimeException("Stub!"); }

/**
 * TimeZone API; calls through to wrapped time zone.
 */

public boolean useDaylightTime() { throw new RuntimeException("Stub!"); }

/**
 * TimeZone API; calls through to wrapped time zone.
 */

public boolean inDaylightTime(java.util.Date date) { throw new RuntimeException("Stub!"); }

/**
 * Boilerplate API; calls through to wrapped object.
 */

public java.lang.Object clone() { throw new RuntimeException("Stub!"); }

/**
 * Boilerplate API; calls through to wrapped object.
 */

public synchronized int hashCode() { throw new RuntimeException("Stub!"); }

/**
 * Boilerplate API; calls through to wrapped object.
 */

public boolean equals(java.lang.Object obj) { throw new RuntimeException("Stub!"); }

/**
 * Returns a string representation of this object.
 * @return  a string representation of this object.
 */

public java.lang.String toString() { throw new RuntimeException("Stub!"); }
}

