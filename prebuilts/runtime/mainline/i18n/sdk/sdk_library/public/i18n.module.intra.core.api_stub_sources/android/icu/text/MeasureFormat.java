/* GENERATED SOURCE. DO NOT MODIFY. */
// ? 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 **********************************************************************
 * Copyright (c) 2004-2016, International Business Machines
 * Corporation and others.  All Rights Reserved.
 **********************************************************************
 * Author: Alan Liu
 * Created: April 20, 2004
 * Since: ICU 3.0
 **********************************************************************
 */

package android.icu.text;

import android.icu.util.Measure;
import android.icu.util.ULocale;
import android.icu.util.MeasureUnit;
import java.util.Locale;
import java.text.FieldPosition;
import java.util.Collection;
import android.icu.util.ULocale.Category;

/**
 * A formatter for Measure objects.
 *
 * <p>
 * To format a Measure object, first create a formatter object using a MeasureFormat factory method. Then
 * use that object's format or formatMeasures methods.
 *
 * Here is sample code:
 *
 * <pre>
 * MeasureFormat fmtFr = MeasureFormat.getInstance(ULocale.FRENCH, FormatWidth.SHORT);
 * Measure measure = new Measure(23, MeasureUnit.CELSIUS);
 *
 * // Output: 23 ?C
 * System.out.println(fmtFr.format(measure));
 *
 * Measure measureF = new Measure(70, MeasureUnit.FAHRENHEIT);
 *
 * // Output: 70 ?F
 * System.out.println(fmtFr.format(measureF));
 *
 * MeasureFormat fmtFrFull = MeasureFormat.getInstance(ULocale.FRENCH, FormatWidth.WIDE);
 * // Output: 70 pieds et 5,3 pouces
 * System.out.println(fmtFrFull.formatMeasures(new Measure(70, MeasureUnit.FOOT),
 *         new Measure(5.3, MeasureUnit.INCH)));
 *
 * // Output: 1 pied et 1 pouce
 * System.out.println(
 *         fmtFrFull.formatMeasures(new Measure(1, MeasureUnit.FOOT), new Measure(1, MeasureUnit.INCH)));
 *
 * MeasureFormat fmtFrNarrow = MeasureFormat.getInstance(ULocale.FRENCH, FormatWidth.NARROW);
 * // Output: 1? 1?
 * System.out.println(fmtFrNarrow.formatMeasures(new Measure(1, MeasureUnit.FOOT),
 *         new Measure(1, MeasureUnit.INCH)));
 *
 * MeasureFormat fmtEn = MeasureFormat.getInstance(ULocale.ENGLISH, FormatWidth.WIDE);
 *
 * // Output: 1 inch, 2 feet
 * fmtEn.formatMeasures(new Measure(1, MeasureUnit.INCH), new Measure(2, MeasureUnit.FOOT));
 * </pre>
 * <p>
 * This class does not do conversions from one unit to another. It simply formats whatever units it is
 * given
 * <p>
 * This class is immutable and thread-safe so long as its deprecated subclass, TimeUnitFormat, is never
 * used. TimeUnitFormat is not thread-safe, and is mutable. Although this class has existing subclasses,
 * this class does not support new sub-classes.
 *
 * @see android.icu.text.UFormat
 * @author Alan Liu
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public class MeasureFormat extends android.icu.text.UFormat {

MeasureFormat() { throw new RuntimeException("Stub!"); }

/**
 * Create a format from the locale, formatWidth, and format.
 *
 * @param locale
 *            the locale.
 * @param formatWidth
 *            hints how long formatted strings should be.
 * @return The new MeasureFormat object.
 */

public static android.icu.text.MeasureFormat getInstance(android.icu.util.ULocale locale, android.icu.text.MeasureFormat.FormatWidth formatWidth) { throw new RuntimeException("Stub!"); }

/**
 * Create a format from the {@link java.util.Locale} and formatWidth.
 *
 * @param locale
 *            the {@link java.util.Locale}.
 * @param formatWidth
 *            hints how long formatted strings should be.
 * @return The new MeasureFormat object.
 */

public static android.icu.text.MeasureFormat getInstance(java.util.Locale locale, android.icu.text.MeasureFormat.FormatWidth formatWidth) { throw new RuntimeException("Stub!"); }

/**
 * Create a format from the locale, formatWidth, and format.
 *
 * @param locale
 *            the locale.
 * @param formatWidth
 *            hints how long formatted strings should be.
 * @param format
 *            This is defensively copied.
 * @return The new MeasureFormat object.
 */

public static android.icu.text.MeasureFormat getInstance(android.icu.util.ULocale locale, android.icu.text.MeasureFormat.FormatWidth formatWidth, android.icu.text.NumberFormat format) { throw new RuntimeException("Stub!"); }

/**
 * Create a format from the {@link java.util.Locale}, formatWidth, and format.
 *
 * @param locale
 *            the {@link java.util.Locale}.
 * @param formatWidth
 *            hints how long formatted strings should be.
 * @param format
 *            This is defensively copied.
 * @return The new MeasureFormat object.
 */

public static android.icu.text.MeasureFormat getInstance(java.util.Locale locale, android.icu.text.MeasureFormat.FormatWidth formatWidth, android.icu.text.NumberFormat format) { throw new RuntimeException("Stub!"); }

/**
 * Able to format Collection&lt;? extends Measure&gt;, Measure[], and Measure by delegating to
 * formatMeasures. If the pos argument identifies a NumberFormat field, then its indices are set to
 * the beginning and end of the first such field encountered. MeasureFormat itself does not supply
 * any fields.
 *
 * Calling a <code>formatMeasures</code> method is preferred over calling this method as they give
 * better performance.
 *
 * @param obj
 *            must be a Collection&lt;? extends Measure&gt;, Measure[], or Measure object.
 * @param toAppendTo
 *            Formatted string appended here.
 * @param fpos
 *            Identifies a field in the formatted text.
 * @see java.text.Format#format(java.lang.Object, java.lang.StringBuffer, java.text.FieldPosition)
 */

public java.lang.StringBuffer format(java.lang.Object obj, java.lang.StringBuffer toAppendTo, java.text.FieldPosition fpos) { throw new RuntimeException("Stub!"); }

/**
 * Parses text from a string to produce a <code>Measure</code>.
 *
 * @see java.text.Format#parseObject(java.lang.String, java.text.ParsePosition)
 * @throws java.lang.UnsupportedOperationException
 *             Not supported.
 * @hide draft / provisional / internal are hidden on Android
 */

public android.icu.util.Measure parseObject(java.lang.String source, java.text.ParsePosition pos) { throw new RuntimeException("Stub!"); }

/**
 * Format a sequence of measures. Uses the ListFormatter unit lists. So, for example, one could
 * format ?3 feet, 2 inches?. Zero values are formatted (eg, ?3 feet, 0 inches?). It is the caller?s
 * responsibility to have the appropriate values in appropriate order, and using the appropriate
 * Number values. Typically the units should be in descending order, with all but the last Measure
 * having integer values (eg, not ?3.2 feet, 2 inches?).
 *
 * @param measures
 *            a sequence of one or more measures.
 * @return the formatted string.
 */

public final java.lang.String formatMeasures(android.icu.util.Measure... measures) { throw new RuntimeException("Stub!"); }

/**
 * Formats a single measure per unit.
 *
 * An example of such a formatted string is "3.5 meters per second."
 *
 * @param measure
 *            the measure object. In above example, 3.5 meters.
 * @param perUnit
 *            the per unit. In above example, it is MeasureUnit.SECOND
 * @param appendTo
 *            formatted string appended here.
 * @param pos
 *            The field position.
 * @return appendTo.
 */

public java.lang.StringBuilder formatMeasurePerUnit(android.icu.util.Measure measure, android.icu.util.MeasureUnit perUnit, java.lang.StringBuilder appendTo, java.text.FieldPosition pos) { throw new RuntimeException("Stub!"); }

/**
 * Formats a sequence of measures.
 *
 * If the fieldPosition argument identifies a NumberFormat field, then its indices are set to the
 * beginning and end of the first such field encountered. MeasureFormat itself does not supply any
 * fields.
 *
 * @param appendTo
 *            the formatted string appended here.
 * @param fpos
 *            Identifies a field in the formatted text.
 * @param measures
 *            the measures to format.
 * @return appendTo.
 * @see android.icu.text.MeasureFormat#formatMeasures(Measure...)
 */

public java.lang.StringBuilder formatMeasures(java.lang.StringBuilder appendTo, java.text.FieldPosition fpos, android.icu.util.Measure... measures) { throw new RuntimeException("Stub!"); }

/**
 * Gets the display name of the specified {@link android.icu.util.MeasureUnit MeasureUnit} corresponding to the current locale and
 * format width.
 *
 * @param unit
 *            The unit for which to get a display name.
 * @return The display name in the locale and width specified in {@link android.icu.text.MeasureFormat#getInstance MeasureFormat#getInstance},
 *         or null if there is no display name available for the specified unit.
 */

public java.lang.String getUnitDisplayName(android.icu.util.MeasureUnit unit) { throw new RuntimeException("Stub!"); }

/**
 * Two MeasureFormats, a and b, are equal if and only if they have the same formatWidth, locale, and
 * equal number formats.
 */

public final boolean equals(java.lang.Object other) { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public final int hashCode() { throw new RuntimeException("Stub!"); }

/**
 * Get the format width this instance is using.
 */

public android.icu.text.MeasureFormat.FormatWidth getWidth() { throw new RuntimeException("Stub!"); }

/**
 * Get the locale of this instance.
 */

public final android.icu.util.ULocale getLocale() { throw new RuntimeException("Stub!"); }

/**
 * Get a copy of the number format.
 */

public android.icu.text.NumberFormat getNumberFormat() { throw new RuntimeException("Stub!"); }

/**
 * Return a formatter for CurrencyAmount objects in the given locale.
 *
 * @param locale
 *            desired locale
 * @return a formatter object
 */

public static android.icu.text.MeasureFormat getCurrencyFormat(android.icu.util.ULocale locale) { throw new RuntimeException("Stub!"); }

/**
 * Return a formatter for CurrencyAmount objects in the given {@link java.util.Locale}.
 *
 * @param locale
 *            desired {@link java.util.Locale}
 * @return a formatter object
 */

public static android.icu.text.MeasureFormat getCurrencyFormat(java.util.Locale locale) { throw new RuntimeException("Stub!"); }

/**
 * Return a formatter for CurrencyAmount objects in the default <code>FORMAT</code> locale.
 *
 * @return a formatter object
 * @see android.icu.util.ULocale.Category#FORMAT
 */

public static android.icu.text.MeasureFormat getCurrencyFormat() { throw new RuntimeException("Stub!"); }
/**
 * Formatting width enum.
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public enum FormatWidth {
/**
 * Spell out everything.
 */

WIDE,
/**
 * Abbreviate when possible.
 */

SHORT,
/**
 * Brief. Use only a symbol for the unit when possible.
 */

NARROW,
/**
 * Identical to NARROW except when formatMeasures is called with an hour and minute; minute and
 * second; or hour, minute, and second Measures. In these cases formatMeasures formats as 5:37:23
 * instead of 5h, 37m, 23s.
 */

NUMERIC;
}

}

