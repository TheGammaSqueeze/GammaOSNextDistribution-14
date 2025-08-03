/* GENERATED SOURCE. DO NOT MODIFY. */
// ? 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 *******************************************************************************
 * Copyright (C) 2014-2016, International Business Machines Corporation and
 * others. All Rights Reserved.
 *******************************************************************************
 */

package android.icu.text;

import android.icu.util.ULocale;

/**
 *A formatter that formats numbers in user-friendly scientific notation.
 *
 * ScientificNumberFormatter instances are immutable and thread-safe.
 *
 * Sample code:
 * <pre>
 * ULocale en = new ULocale("en");
 * ScientificNumberFormatter fmt = ScientificNumberFormatter.getMarkupInstance(
 *         en, "&lt;sup&gt;", "&lt;/sup&gt;");
 * </pre>
 * <pre>
 * // Output: "1.23456?10&lt;sup&gt;-78&lt;/sup&gt;"
 * System.out.println(fmt.format(1.23456e-78));
 * </pre>
 *
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public final class ScientificNumberFormatter {

private ScientificNumberFormatter() { throw new RuntimeException("Stub!"); }

/**
 * Gets a ScientificNumberFormatter instance that uses
 * superscript characters for exponents for this locale.
 * @param locale The locale
 * @return The ScientificNumberFormatter instance.
 */

public static android.icu.text.ScientificNumberFormatter getSuperscriptInstance(android.icu.util.ULocale locale) { throw new RuntimeException("Stub!"); }

/**
 * Gets a ScientificNumberFormatter instance that uses
 * superscript characters for exponents.
 * @param df The DecimalFormat must be configured for scientific
 *   notation. Caller may safely change df after this call as this method
 *   clones it when creating the ScientificNumberFormatter.
 * @return the ScientificNumberFormatter instance.
 */

public static android.icu.text.ScientificNumberFormatter getSuperscriptInstance(android.icu.text.DecimalFormat df) { throw new RuntimeException("Stub!"); }

/**
 * Gets a ScientificNumberFormatter instance that uses
 * markup for exponents for this locale.
 * @param locale The locale
 * @param beginMarkup the markup to start superscript e.g {@code <sup>}
 * @param endMarkup the markup to end superscript e.g {@code </sup>}
 * @return The ScientificNumberFormatter instance.
 */

public static android.icu.text.ScientificNumberFormatter getMarkupInstance(android.icu.util.ULocale locale, java.lang.String beginMarkup, java.lang.String endMarkup) { throw new RuntimeException("Stub!"); }

/**
 * Gets a ScientificNumberFormatter instance that uses
 * markup for exponents.
 * @param df The DecimalFormat must be configured for scientific
 *   notation. Caller may safely change df after this call as this method
 *   clones it when creating the ScientificNumberFormatter.
 * @param beginMarkup the markup to start superscript e.g {@code <sup>}
 * @param endMarkup the markup to end superscript e.g {@code </sup>}
 * @return The ScientificNumberFormatter instance.
 */

public static android.icu.text.ScientificNumberFormatter getMarkupInstance(android.icu.text.DecimalFormat df, java.lang.String beginMarkup, java.lang.String endMarkup) { throw new RuntimeException("Stub!"); }

/**
 * Formats a number
 * @param number Can be a double, int, Number or
 *  anything that DecimalFormat#format(Object) accepts.
 * @return the formatted string.
 */

public java.lang.String format(java.lang.Object number) { throw new RuntimeException("Stub!"); }
}

