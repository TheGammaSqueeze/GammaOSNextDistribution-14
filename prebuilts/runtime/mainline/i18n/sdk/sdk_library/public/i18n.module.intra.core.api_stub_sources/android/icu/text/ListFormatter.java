/* GENERATED SOURCE. DO NOT MODIFY. */
// ? 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 *******************************************************************************
 * Copyright (C) 2012-2016, Google, International Business Machines Corporation and
 * others. All Rights Reserved.
 *******************************************************************************
 */

package android.icu.text;

import java.text.Format;

/**
 * Immutable class for formatting a list, using data from CLDR (or supplied
 * separately). The class is not subclassable.
 *
 * @author Mark Davis
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public final class ListFormatter {

ListFormatter() { throw new RuntimeException("Stub!"); }

/**
 * Create a list formatter that is appropriate for a locale.
 *
 * @param locale
 *            the locale in question.
 * @return ListFormatter
 */

public static android.icu.text.ListFormatter getInstance(android.icu.util.ULocale locale) { throw new RuntimeException("Stub!"); }

/**
 * Create a list formatter that is appropriate for a locale.
 *
 * @param locale
 *            the locale in question.
 * @return ListFormatter
 */

public static android.icu.text.ListFormatter getInstance(java.util.Locale locale) { throw new RuntimeException("Stub!"); }

/**
 * Create a list formatter that is appropriate for the default FORMAT locale.
 *
 * @return ListFormatter
 */

public static android.icu.text.ListFormatter getInstance() { throw new RuntimeException("Stub!"); }

/**
 * Format a list of objects.
 *
 * @param items
 *            items to format. The toString() method is called on each.
 * @return items formatted into a string
 */

public java.lang.String format(java.lang.Object... items) { throw new RuntimeException("Stub!"); }

/**
 * Format a collection of objects. The toString() method is called on each.
 *
 * @param items
 *            items to format. The toString() method is called on each.
 * @return items formatted into a string
 */

public java.lang.String format(java.util.Collection<?> items) { throw new RuntimeException("Stub!"); }

/**
 * Returns the pattern to use for a particular item count.
 * @param count the item count.
 * @return the pattern with {0}, {1}, {2}, etc. For English,
 * getPatternForNumItems(3) == "{0}, {1}, and {2}"
 * @throws java.lang.IllegalArgumentException when count is 0 or negative.
 */

public java.lang.String getPatternForNumItems(int count) { throw new RuntimeException("Stub!"); }
}

