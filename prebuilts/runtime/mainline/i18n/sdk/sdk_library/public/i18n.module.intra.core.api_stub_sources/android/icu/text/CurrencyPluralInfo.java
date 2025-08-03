/* GENERATED SOURCE. DO NOT MODIFY. */
// ? 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 *******************************************************************************
 * Copyright (C) 2009-2014, International Business Machines Corporation and    *
 * others. All Rights Reserved.                                                *
 *******************************************************************************
 */

package android.icu.text;

import android.icu.util.ULocale.Category;

/**
 * This class represents the information needed by
 * DecimalFormat to format currency plural,
 * such as "3.00 US dollars" or "1.00 US dollar".
 * DecimalFormat creates for itself an instance of
 * CurrencyPluralInfo from its locale data.
 * If you need to change any of these symbols, you can get the
 * CurrencyPluralInfo object from your
 * DecimalFormat and modify it.
 *
 * Following are the information needed for currency plural format and parse:
 * locale information,
 * plural rule of the locale,
 * currency plural pattern of the locale.
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public class CurrencyPluralInfo implements java.lang.Cloneable, java.io.Serializable {

/**
 * Create a CurrencyPluralInfo object for the default <code>FORMAT</code> locale.
 * @see android.icu.util.ULocale.Category#FORMAT
 */

public CurrencyPluralInfo() { throw new RuntimeException("Stub!"); }

/**
 * Create a CurrencyPluralInfo object for the given locale.
 * @param locale the locale
 */

public CurrencyPluralInfo(java.util.Locale locale) { throw new RuntimeException("Stub!"); }

/**
 * Create a CurrencyPluralInfo object for the given locale.
 * @param locale the locale
 */

public CurrencyPluralInfo(android.icu.util.ULocale locale) { throw new RuntimeException("Stub!"); }

/**
 * Gets a CurrencyPluralInfo instance for the default locale.
 *
 * @return A CurrencyPluralInfo instance.
 */

public static android.icu.text.CurrencyPluralInfo getInstance() { throw new RuntimeException("Stub!"); }

/**
 * Gets a CurrencyPluralInfo instance for the given locale.
 *
 * @param locale the locale.
 * @return A CurrencyPluralInfo instance.
 */

public static android.icu.text.CurrencyPluralInfo getInstance(java.util.Locale locale) { throw new RuntimeException("Stub!"); }

/**
 * Gets a CurrencyPluralInfo instance for the given locale.
 *
 * @param locale the locale.
 * @return A CurrencyPluralInfo instance.
 */

public static android.icu.text.CurrencyPluralInfo getInstance(android.icu.util.ULocale locale) { throw new RuntimeException("Stub!"); }

/**
 * Gets plural rules of this locale, used for currency plural format
 *
 * @return plural rule
 */

public android.icu.text.PluralRules getPluralRules() { throw new RuntimeException("Stub!"); }

/**
 * Given a plural count, gets currency plural pattern of this locale,
 * used for currency plural format
 *
 * @param  pluralCount currency plural count
 * @return a currency plural pattern based on plural count
 */

public java.lang.String getCurrencyPluralPattern(java.lang.String pluralCount) { throw new RuntimeException("Stub!"); }

/**
 * Get locale
 *
 * @return locale
 */

public android.icu.util.ULocale getLocale() { throw new RuntimeException("Stub!"); }

/**
 * Set plural rules.  These are initially set in the constructor based on the locale,
 * and usually do not need to be changed.
 *
 * @param ruleDescription new plural rule description
 */

public void setPluralRules(java.lang.String ruleDescription) { throw new RuntimeException("Stub!"); }

/**
 * Set currency plural patterns.  These are initially set in the constructor based on the
 * locale, and usually do not need to be changed.
 *
 * The decimal digits part of the pattern cannot be specified via this method.  All plural
 * forms will use the same decimal pattern as set in the constructor of DecimalFormat.  For
 * example, you can't set "0.0" for plural "few" but "0.00" for plural "many".
 *
 * @param pluralCount the plural count for which the currency pattern will
 *                    be overridden.
 * @param pattern     the new currency plural pattern
 */

public void setCurrencyPluralPattern(java.lang.String pluralCount, java.lang.String pattern) { throw new RuntimeException("Stub!"); }

/**
 * Set locale.  This also sets both the plural rules and the currency plural patterns to be
 * the defaults for the locale.
 *
 * @param loc the new locale to set
 */

public void setLocale(android.icu.util.ULocale loc) { throw new RuntimeException("Stub!"); }

/**
 * Standard override
 */

public java.lang.Object clone() { throw new RuntimeException("Stub!"); }

/**
 * Override equals
 */

public boolean equals(java.lang.Object a) { throw new RuntimeException("Stub!"); }

/**
 * Override hashCode
 *
 * @hide original deprecated declaration
 */

public int hashCode() { throw new RuntimeException("Stub!"); }
}

