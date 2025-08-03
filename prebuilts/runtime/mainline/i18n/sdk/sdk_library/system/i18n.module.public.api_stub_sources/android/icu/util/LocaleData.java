/* GENERATED SOURCE. DO NOT MODIFY. */
// ? 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 **************************************************************************************
 * Copyright (C) 2009-2016, International Business Machines Corporation,
 * Google, Inc. and others. All Rights Reserved.
 **************************************************************************************
 */

package android.icu.util;

import android.icu.util.ULocale.Category;

/**
 * A class for accessing miscellaneous data in the locale bundles
 * @author ram
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public final class LocaleData {

private LocaleData() { throw new RuntimeException("Stub!"); }

/**
 * Gets the LocaleData object associated with the ULocale specified in locale
 *
 * @param locale    Locale with thich the locale data object is associated.
 * @return          A locale data object.
 */

public static android.icu.util.LocaleData getInstance(android.icu.util.ULocale locale) { throw new RuntimeException("Stub!"); }

/**
 * Gets the LocaleData object associated with the default <code>FORMAT</code> locale
 *
 * @return          A locale data object.
 * @see android.icu.util.ULocale.Category#FORMAT
 */

public static android.icu.util.LocaleData getInstance() { throw new RuntimeException("Stub!"); }

/**
 * Sets the "no substitute" behavior of this locale data object.
 *
 * @param setting   Value for the no substitute behavior.  If TRUE,
 *                  methods of this locale data object will return
 *                  an error when no data is available for that method,
 *                  given the locale ID supplied to the constructor.
 */

public void setNoSubstitute(boolean setting) { throw new RuntimeException("Stub!"); }

/**
 * Gets the "no substitute" behavior of this locale data object.
 *
 * @return          Value for the no substitute behavior.  If TRUE,
 *                  methods of this locale data object will return
 *                  an error when no data is available for that method,
 *                  given the locale ID supplied to the constructor.
 */

public boolean getNoSubstitute() { throw new RuntimeException("Stub!"); }

/**
 * Retrieves a delimiter string from the locale data.
 *
 * @param type      The type of delimiter string desired.  Currently,
 *                  the valid choices are QUOTATION_START, QUOTATION_END,
 *                  ALT_QUOTATION_START, or ALT_QUOTATION_END.
 * @return          The desired delimiter string.
 */

public java.lang.String getDelimiter(int type) { throw new RuntimeException("Stub!"); }

/**
 * Returns the measurement system used in the locale specified by the locale.
 *
 * @param locale      The locale for which the measurement system to be retrieved.
 * @return MeasurementSystem the measurement system used in the locale.
 */

public static android.icu.util.LocaleData.MeasurementSystem getMeasurementSystem(android.icu.util.ULocale locale) { throw new RuntimeException("Stub!"); }

/**
 * Returns the size of paper used in the locale. The paper sizes returned are always in
 * <em>milli-meters</em>.
 * @param locale The locale for which the measurement system to be retrieved.
 * @return The paper size used in the locale
 */

public static android.icu.util.LocaleData.PaperSize getPaperSize(android.icu.util.ULocale locale) { throw new RuntimeException("Stub!"); }

/**
 * Returns the current CLDR version
 */

public static android.icu.util.VersionInfo getCLDRVersion() { throw new RuntimeException("Stub!"); }

/**
 * Delimiter type for {@link #getDelimiter(int)}.
 */

public static final int ALT_QUOTATION_END = 3; // 0x3

/**
 * Delimiter type for {@link #getDelimiter(int)}.
 */

public static final int ALT_QUOTATION_START = 2; // 0x2

/**
 * Delimiter type for {@link #getDelimiter(int)}.
 */

public static final int QUOTATION_END = 1; // 0x1

/**
 * Delimiter type for {@link #getDelimiter(int)}.
 */

public static final int QUOTATION_START = 0; // 0x0
/**
 * Enumeration for representing the measurement systems.
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public static final class MeasurementSystem {

private MeasurementSystem() { throw new RuntimeException("Stub!"); }

/**
 * Measurement system specified by Le Syst&#x00E8;me International d'Unit&#x00E9;s (SI)
 * otherwise known as Metric system.
 */

public static final android.icu.util.LocaleData.MeasurementSystem SI;
static { SI = null; }

/**
 * Mix of metric and imperial units used in Great Britain.
 */

public static final android.icu.util.LocaleData.MeasurementSystem UK;
static { UK = null; }

/**
 * Measurement system followed in the United States of America.
 */

public static final android.icu.util.LocaleData.MeasurementSystem US;
static { US = null; }
}

/**
 * A class that represents the size of letter head
 * used in the country
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public static final class PaperSize {

private PaperSize() { throw new RuntimeException("Stub!"); }

/**
 * Retruns the height of the paper
 * @return the height
 */

public int getHeight() { throw new RuntimeException("Stub!"); }

/**
 * Returns the width of the paper
 * @return the width
 */

public int getWidth() { throw new RuntimeException("Stub!"); }
}

}

