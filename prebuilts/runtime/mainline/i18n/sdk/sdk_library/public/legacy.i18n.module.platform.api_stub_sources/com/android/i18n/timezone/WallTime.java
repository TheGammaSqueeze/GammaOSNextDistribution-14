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
/*
 * Elements of the WallTime class are a port of Bionic's localtime.c to Java. That code had the
 * following header:
 *
 * This file is in the public domain, so clarified as of
 * 1996-06-05 by Arthur David Olson.
 */

package com.android.i18n.timezone;


/**
 * A class that represents a "wall time". This class is modeled on the C tm struct and
 * is used to support android.text.format.Time behavior. Unlike the tm struct the year is
 * represented as the full year, not the years since 1900.
 *
 * <p>This class contains a rewrite of various native functions that android.text.format.Time
 * once relied on such as mktime_tz and localtime_tz. This replacement does not support leap
 * seconds but does try to preserve behavior around ambiguous date/times found in the BSD
 * version of mktime that was previously used.
 *
 * <p>The original native code used a 32-bit value for time_t on 32-bit Android, which
 * was the only variant of Android available at the time. To preserve old behavior this code
 * deliberately uses {@code int} rather than {@code long} for most things and performs
 * calculations in seconds. This creates deliberate truncation issues for date / times before
 * 1901 and after 2038. This is intentional but might be fixed in future if all the knock-ons
 * can be resolved: Application code may have come to rely on the range so previously values
 * like zero for year could indicate an invalid date but if we move to long the year zero would
 * be valid.
 *
 * <p>All offsets are considered to be safe for addition / subtraction / multiplication without
 * worrying about overflow. All absolute time arithmetic is checked for overflow / underflow.
 *
 * @hide
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public class WallTime {

public WallTime() { throw new RuntimeException("Stub!"); }

/**
 * Sets the wall time to a point in time using the time zone information provided. This
 * is a replacement for the old native localtime_tz() function.
 *
 * <p>When going from an instant to a wall time it is always unambiguous because there
 * is only one offset rule acting at any given instant. We do not consider leap seconds.
 */

public void localtime(int timeSeconds, com.android.i18n.timezone.ZoneInfoData zoneInfo) { throw new RuntimeException("Stub!"); }

/**
 * Returns the time in seconds since beginning of the Unix epoch for the wall time using the
 * time zone information provided. This is a replacement for an old native mktime_tz() C
 * function.
 *
 * <p>When going from a wall time to an instant the answer can be ambiguous. A wall
 * time can map to zero, one or two instants given rational date/time transitions. Rational
 * in this case means that transitions occur less frequently than the offset
 * differences between them (which could cause all sorts of craziness like the
 * skipping out of transitions).
 *
 * <p>For example, this is not fully supported:
 * <ul>
 *     <li>t1 { time = 1, offset = 0 }
 *     <li>t2 { time = 2, offset = -1 }
 *     <li>t3 { time = 3, offset = -2 }
 * </ul>
 * A wall time in this case might map to t1, t2 or t3.
 *
 * <p>We do not handle leap seconds.
 * <p>We assume that no timezone offset transition has an absolute offset > 24 hours.
 * <p>We do not assume that adjacent transitions modify the DST state; adjustments can
 * occur for other reasons such as when a zone changes its raw offset.
 */

public int mktime(com.android.i18n.timezone.ZoneInfoData zoneInfo) { throw new RuntimeException("Stub!"); }

public void setYear(int year) { throw new RuntimeException("Stub!"); }

public void setMonth(int month) { throw new RuntimeException("Stub!"); }

public void setMonthDay(int monthDay) { throw new RuntimeException("Stub!"); }

public void setHour(int hour) { throw new RuntimeException("Stub!"); }

public void setMinute(int minute) { throw new RuntimeException("Stub!"); }

public void setSecond(int second) { throw new RuntimeException("Stub!"); }

public void setWeekDay(int weekDay) { throw new RuntimeException("Stub!"); }

public void setYearDay(int yearDay) { throw new RuntimeException("Stub!"); }

public void setIsDst(int isDst) { throw new RuntimeException("Stub!"); }

public void setGmtOffset(int gmtoff) { throw new RuntimeException("Stub!"); }

public int getYear() { throw new RuntimeException("Stub!"); }

public int getMonth() { throw new RuntimeException("Stub!"); }

public int getMonthDay() { throw new RuntimeException("Stub!"); }

public int getHour() { throw new RuntimeException("Stub!"); }

public int getMinute() { throw new RuntimeException("Stub!"); }

public int getSecond() { throw new RuntimeException("Stub!"); }

public int getWeekDay() { throw new RuntimeException("Stub!"); }

public int getYearDay() { throw new RuntimeException("Stub!"); }

public int getGmtOffset() { throw new RuntimeException("Stub!"); }

public int getIsDst() { throw new RuntimeException("Stub!"); }
}

