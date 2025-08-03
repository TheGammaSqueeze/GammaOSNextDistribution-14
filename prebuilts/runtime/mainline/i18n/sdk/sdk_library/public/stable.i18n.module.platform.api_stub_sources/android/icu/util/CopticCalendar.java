/* GENERATED SOURCE. DO NOT MODIFY. */
// ? 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 *******************************************************************************
 * Copyright (C) 2005-2016, International Business Machines Corporation and    *
 * others. All Rights Reserved.                                                *
 *******************************************************************************
 */


package android.icu.util;


/**
 * Implement the Coptic calendar system.
 * <p>
 * CopticCalendar usually should be instantiated using
 * {@link android.icu.util.Calendar#getInstance(ULocale)} passing in a <code>ULocale</code>
 * with the tag <code>"@calendar=coptic"</code>.</p>
 *
 * @see android.icu.util.Calendar
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public final class CopticCalendar extends android.icu.util.Calendar {

/**
 * Constructs a default <code>CopticCalendar</code> using the current time
 * in the default time zone with the default locale.
 */

public CopticCalendar() { throw new RuntimeException("Stub!"); }

/**
 * Constructs a <code>CopticCalendar</code> based on the current time
 * in the given time zone with the default locale.
 *
 * @param zone The time zone for the new calendar.
 */

public CopticCalendar(android.icu.util.TimeZone zone) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a <code>CopticCalendar</code> based on the current time
 * in the default time zone with the given locale.
 *
 * @param aLocale The locale for the new calendar.
 */

public CopticCalendar(java.util.Locale aLocale) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a <code>CopticCalendar</code> based on the current time
 * in the default time zone with the given locale.
 *
 * @param locale The icu locale for the new calendar.
 */

public CopticCalendar(android.icu.util.ULocale locale) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a <code>CopticCalendar</code> based on the current time
 * in the given time zone with the given locale.
 *
 * @param zone The time zone for the new calendar.
 * @param aLocale The locale for the new calendar.
 */

public CopticCalendar(android.icu.util.TimeZone zone, java.util.Locale aLocale) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a <code>CopticCalendar</code> based on the current time
 * in the given time zone with the given locale.
 *
 * @param zone The time zone for the new calendar.
 * @param locale The icu locale for the new calendar.
 */

public CopticCalendar(android.icu.util.TimeZone zone, android.icu.util.ULocale locale) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a <code>CopticCalendar</code> with the given date set
 * in the default time zone with the default locale.
 *
 * @param year      The value used to set the calendar's {@link #YEAR YEAR} time field.
 * @param month     The value used to set the calendar's {@link #MONTH MONTH} time field.
 *                  The value is 0-based. e.g., 0 for Tout.
 * @param date      The value used to set the calendar's {@link #DATE DATE} time field.
 */

public CopticCalendar(int year, int month, int date) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a <code>CopticCalendar</code> with the given date set
 * in the default time zone with the default locale.
 *
 * @param date      The date to which the new calendar is set.
 */

public CopticCalendar(java.util.Date date) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a <code>CopticCalendar</code> with the given date
 * and time set for the default time zone with the default locale.
 *
 * @param year      The value used to set the calendar's {@link #YEAR YEAR} time field.
 * @param month     The value used to set the calendar's {@link #MONTH MONTH} time field.
 *                  The value is 0-based. e.g., 0 for Tout.
 * @param date      The value used to set the calendar's {@link #DATE DATE} time field.
 * @param hour      The value used to set the calendar's {@link #HOUR_OF_DAY HOUR_OF_DAY} time field.
 * @param minute    The value used to set the calendar's {@link #MINUTE MINUTE} time field.
 * @param second    The value used to set the calendar's {@link #SECOND SECOND} time field.
 */

public CopticCalendar(int year, int month, int date, int hour, int minute, int second) { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public java.lang.String getType() { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 * @deprecated This API is ICU internal only.
 * @hide original deprecated declaration
 * @hide draft / provisional / internal are hidden on Android
 */

@Deprecated
protected int handleGetExtendedYear() { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 * @deprecated This API is ICU internal only.
 * @hide original deprecated declaration
 * @hide draft / provisional / internal are hidden on Android
 */

@Deprecated
protected void handleComputeFields(int julianDay) { throw new RuntimeException("Stub!"); }

/**
 * Return JD of start of given month/extended year
 */

protected int handleComputeMonthStart(int eyear, int emonth, boolean useMonth) { throw new RuntimeException("Stub!"); }

/**
 * Calculate the limit for a specified type of limit and field
 */

protected int handleGetLimit(int field, int limitType) { throw new RuntimeException("Stub!"); }

/** 
 * Constant for ????? / ?????,
 * the 6th month of the Coptic year.
 */

public static final int AMSHIR = 5; // 0x5

/** 
 * Constant for ????? / ????,
 * the 2nd month of the Coptic year.
 */

public static final int BABA = 1; // 0x1

/** 
 * Constant for ???????? / ??????,
 * the 7th month of the Coptic year.
 */

public static final int BARAMHAT = 6; // 0x6

/** 
 * Constant for ??????? / ??????,
 * the 8th month of the Coptic year.
 */

public static final int BARAMOUDA = 7; // 0x7

/** 
 * Constant for ????? / ????;,
 * the 9th month of the Coptic year.
 */

public static final int BASHANS = 8; // 0x8

/** 
 * Constant for ???? / ????,
 * the 11th month of the Coptic year.
 */

public static final int EPEP = 10; // 0xa

/** 
 * Constant for ???? / ?????,
 * the 3rd month of the Coptic year.
 */

public static final int HATOR = 2; // 0x2

/** 
 * Constant for ????? / ????;,
 * the 4th month of the Coptic year.
 */

public static final int KIAHK = 3; // 0x3

/** 
 * Constant for ?????? / ????,
 * the 12th month of the Coptic year.
 */

public static final int MESRA = 11; // 0xb

/** 
 * Constant for ??????? ????? / ???????????,
 * the 13th month of the Coptic year.
 */

public static final int NASIE = 12; // 0xc

/** 
 * Constant for ????? / ?????,
 * the 10th month of the Coptic year.
 */

public static final int PAONA = 9; // 0x9

/** 
 * Constant for ???? / ????,
 * the 5th month of the Coptic year.
 */

public static final int TOBA = 4; // 0x4

/** 
 * Constant for ???? / ???,
 * the 1st month of the Coptic year.
 */

public static final int TOUT = 0; // 0x0
}

