/* GENERATED SOURCE. DO NOT MODIFY. */
// ? 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 *******************************************************************************
 * Copyright (C) 2005-2016, International Business Machines Corporation and
 * others. All Rights Reserved.
 *******************************************************************************
 */


package android.icu.util;


/**
 * Implement the Ethiopic calendar system.
 * <p>
 * EthiopicCalendar usually should be instantiated using
 * {@link android.icu.util.Calendar#getInstance(ULocale)} passing in a <code>ULocale</code>
 * with the tag <code>"@calendar=ethiopic"</code>.</p>
 *
 * @see android.icu.util.Calendar
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public final class EthiopicCalendar extends android.icu.util.Calendar {

/**
 * Constructs a default <code>EthiopicCalendar</code> using the current time
 * in the default time zone with the default locale.
 */

public EthiopicCalendar() { throw new RuntimeException("Stub!"); }

/**
 * Constructs a <code>EthiopicCalendar</code> based on the current time
 * in the given time zone with the default locale.
 *
 * @param zone The time zone for the new calendar.
 */

public EthiopicCalendar(android.icu.util.TimeZone zone) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a <code>EthiopicCalendar</code> based on the current time
 * in the default time zone with the given locale.
 *
 * @param aLocale The locale for the new calendar.
 */

public EthiopicCalendar(java.util.Locale aLocale) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a <code>EthiopicCalendar</code> based on the current time
 * in the default time zone with the given locale.
 *
 * @param locale The icu locale for the new calendar.
 */

public EthiopicCalendar(android.icu.util.ULocale locale) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a <code>EthiopicCalendar</code> based on the current time
 * in the given time zone with the given locale.
 *
 * @param zone The time zone for the new calendar.
 * @param aLocale The locale for the new calendar.
 */

public EthiopicCalendar(android.icu.util.TimeZone zone, java.util.Locale aLocale) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a <code>EthiopicCalendar</code> based on the current time
 * in the given time zone with the given locale.
 *
 * @param zone The time zone for the new calendar.
 * @param locale The icu locale for the new calendar.
 */

public EthiopicCalendar(android.icu.util.TimeZone zone, android.icu.util.ULocale locale) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a <code>EthiopicCalendar</code> with the given date set
 * in the default time zone with the default locale.
 *
 * @param year      The value used to set the calendar's {@link #YEAR YEAR} time field.
 * @param month     The value used to set the calendar's {@link #MONTH MONTH} time field.
 *                  The value is 0-based. e.g., 0 for Meskerem.
 * @param date      The value used to set the calendar's {@link #DATE DATE} time field.
 */

public EthiopicCalendar(int year, int month, int date) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a <code>EthiopicCalendar</code> with the given date set
 * in the default time zone with the default locale.
 *
 * @param date      The date to which the new calendar is set.
 */

public EthiopicCalendar(java.util.Date date) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a <code>EthiopicCalendar</code> with the given date
 * and time set for the default time zone with the default locale.
 *
 * @param year      The value used to set the calendar's {@link #YEAR YEAR} time field.
 * @param month     The value used to set the calendar's {@link #MONTH MONTH} time field.
 *                  The value is 0-based. e.g., 0 for Meskerem.
 * @param date      The value used to set the calendar's {@link #DATE DATE} time field.
 * @param hour      The value used to set the calendar's {@link #HOUR_OF_DAY HOUR_OF_DAY} time field.
 * @param minute    The value used to set the calendar's {@link #MINUTE MINUTE} time field.
 * @param second    The value used to set the calendar's {@link #SECOND SECOND} time field.
 */

public EthiopicCalendar(int year, int month, int date, int hour, int minute, int second) { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public java.lang.String getType() { throw new RuntimeException("Stub!"); }

/**
 * Set Alem or Mihret era.
 *
 * @param onOff Set Amete Alem era if true, otherwise set Amete Mihret era.
 */

public void setAmeteAlemEra(boolean onOff) { throw new RuntimeException("Stub!"); }

/**
 * Return true if this calendar is set to the Amete Alem era.
 *
 * @return true if set to the Amete Alem era.
 */

public boolean isAmeteAlemEra() { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 * @deprecated This API is ICU internal only.
 * @hide draft / provisional / internal are hidden on Android
 */

@Deprecated
protected int handleGetExtendedYear() { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 * @deprecated This API is ICU internal only.
 * @hide draft / provisional / internal are hidden on Android
 */

@Deprecated
protected void handleComputeFields(int julianDay) { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 * @deprecated This API is ICU internal only.
 * @hide draft / provisional / internal are hidden on Android
 */

@Deprecated
protected int handleGetLimit(int field, int limitType) { throw new RuntimeException("Stub!"); }

/**
 * Return JD of start of given month/extended year
 */

protected int handleComputeMonthStart(int eyear, int emonth, boolean useMonth) { throw new RuntimeException("Stub!"); }

/** 
 * Constant for ????, the 9th month of the Ethiopic year.
 */

public static final int GENBOT = 8; // 0x8

/** 
 * Constant for ???, the 11th month of the Ethiopic year.
 */

public static final int HAMLE = 10; // 0xa

/** 
 * Constant for ???, the 3rd month of the Ethiopic year.
 */

public static final int HEDAR = 2; // 0x2

/** 
 * Constant for ????, the 7th month of the Ethiopic year.
 */

public static final int MEGABIT = 6; // 0x6

/** 
 * Constant for ?????, the 1st month of the Ethiopic year.
 */

public static final int MESKEREM = 0; // 0x0

/** 
 * Constant for ????, the 8th month of the Ethiopic year.
 */

public static final int MIAZIA = 7; // 0x7

/** 
 * Constant for ???, the 12th month of the Ethiopic year.
 */

public static final int NEHASSE = 11; // 0xb

/** 
 * Constant for ????, the 13th month of the Ethiopic year.
 */

public static final int PAGUMEN = 12; // 0xc

/** 
 * Constant for ??, the 10th month of the Ethiopic year.
 */

public static final int SENE = 9; // 0x9

/** 
 * Constant for ????, the 4th month of the Ethiopic year.
 */

public static final int TAHSAS = 3; // 0x3

/** 
 * Constant for ????, the 2nd month of the Ethiopic year.
 */

public static final int TEKEMT = 1; // 0x1

/** 
 * Constant for ??, the 5th month of the Ethiopic year.
 */

public static final int TER = 4; // 0x4

/** 
 * Constant for ????, the 6th month of the Ethiopic year.
 */

public static final int YEKATIT = 5; // 0x5
}

