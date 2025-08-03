/* GENERATED SOURCE. DO NOT MODIFY. */
// ? 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/**
 *******************************************************************************
 * Copyright (C) 2001-2016, International Business Machines Corporation and
 * others. All Rights Reserved.
 *******************************************************************************
 */

package android.icu.util;

import java.util.Locale;
import android.icu.util.ULocale.Category;

/**
 * A class encapsulating a currency, as defined by ISO 4217.  A
 * <tt>Currency</tt> object can be created given a <tt>Locale</tt> or
 * given an ISO 4217 code.  Once created, the <tt>Currency</tt> object
 * can return various data necessary to its proper display:
 *
 * <ul><li>A display symbol, for a specific locale
 * <li>The number of fraction digits to display
 * <li>A rounding increment
 * </ul>
 *
 * The <tt>DecimalFormat</tt> class uses these data to display
 * currencies.
 *
 * <p>Note: This class deliberately resembles
 * <tt>java.util.Currency</tt> but it has a completely independent
 * implementation, and adds features not present in the JDK.
 * @author Alan Liu
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public class Currency extends android.icu.util.MeasureUnit {

/**
 * Constructs a currency object for the given ISO 4217 3-letter
 * code.  This constructor assumes that the code is valid.
 *
 * @param theISOCode The iso code used to construct the currency.
 */

protected Currency(java.lang.String theISOCode) { throw new RuntimeException("Stub!"); }

/**
 * Returns a currency object for the default currency in the given
 * locale.
 * @param locale the locale
 * @return the currency object for this locale
 */

public static android.icu.util.Currency getInstance(java.util.Locale locale) { throw new RuntimeException("Stub!"); }

/**
 * Returns a currency object for the default currency in the given
 * locale.
 */

public static android.icu.util.Currency getInstance(android.icu.util.ULocale locale) { throw new RuntimeException("Stub!"); }

/**
 * Returns an array of Strings which contain the currency
 * identifiers that are valid for the given locale on the
 * given date.  If there are no such identifiers, returns null.
 * Returned identifiers are in preference order.
 * @param loc the locale for which to retrieve currency codes.
 * @param d the date for which to retrieve currency codes for the given locale.
 * @return The array of ISO currency codes.
 */

public static java.lang.String[] getAvailableCurrencyCodes(android.icu.util.ULocale loc, java.util.Date d) { throw new RuntimeException("Stub!"); }

/**
 * Returns an array of Strings which contain the currency
 * identifiers that are valid for the given {@link java.util.Locale} on the
 * given date.  If there are no such identifiers, returns null.
 * Returned identifiers are in preference order.
 * @param loc the {@link java.util.Locale} for which to retrieve currency codes.
 * @param d the date for which to retrieve currency codes for the given locale.
 * @return The array of ISO currency codes.
 */

public static java.lang.String[] getAvailableCurrencyCodes(java.util.Locale loc, java.util.Date d) { throw new RuntimeException("Stub!"); }

/**
 * Returns the set of available currencies. The returned set of currencies contains all of the
 * available currencies, including obsolete ones. The result set can be modified without
 * affecting the available currencies in the runtime.
 *
 * @return The set of available currencies. The returned set could be empty if there is no
 * currency data available.
 */

public static java.util.Set<android.icu.util.Currency> getAvailableCurrencies() { throw new RuntimeException("Stub!"); }

/**
 * Returns a currency object given an ISO 4217 3-letter code.
 * @param theISOCode the iso code
 * @return the currency for this iso code
 * @throws java.lang.NullPointerException if <code>theISOCode</code> is null.
 * @throws java.lang.IllegalArgumentException if <code>theISOCode</code> is not a
 *         3-letter alpha code.
 */

public static android.icu.util.Currency getInstance(java.lang.String theISOCode) { throw new RuntimeException("Stub!"); }

/**
 * Returns a Currency object based on the currency represented by the given java.util.Currency.
 *
 * @param currency The Java currency object to convert.
 * @return An equivalent ICU currency object.
 */

public static android.icu.util.Currency fromJavaCurrency(java.util.Currency currency) { throw new RuntimeException("Stub!"); }

/**
 * Returns a java.util.Currency object based on the currency represented by this Currency.
 *
 * @return An equivalent Java currency object.
 */

public java.util.Currency toJavaCurrency() { throw new RuntimeException("Stub!"); }

/**
 * Return an array of the locales for which a currency
 * is defined.
 * @return an array of the available locales
 */

public static java.util.Locale[] getAvailableLocales() { throw new RuntimeException("Stub!"); }

/**
 * Return an array of the ulocales for which a currency
 * is defined.
 * @return an array of the available ulocales
 */

public static android.icu.util.ULocale[] getAvailableULocales() { throw new RuntimeException("Stub!"); }

/**
 * Given a key and a locale, returns an array of values for the key for which data
 * exists.  If commonlyUsed is true, these are the values that typically are used
 * with this locale, otherwise these are all values for which data exists.
 * This is a common service API.
 * <p>
 * The only supported key is "currency", other values return an empty array.
 * <p>
 * Currency information is based on the region of the locale.  If the locale does not
 * indicate a region, {@link android.icu.util.ULocale#addLikelySubtags(android.icu.util.ULocale) ULocale#addLikelySubtags(ULocale)} is used to infer a region,
 * except for the 'und' locale.
 * <p>
 * If commonlyUsed is true, only the currencies known to be in use as of the current date
 * are returned.  When there are more than one, these are returned in preference order
 * (typically, this occurs when a country is transitioning to a new currency, and the
 * newer currency is preferred), see
 * <a href="http://unicode.org/reports/tr35/#Supplemental_Currency_Data">Unicode TR#35 Sec. C1</a>.
 * If commonlyUsed is false, all currencies ever used in any locale are returned, in no
 * particular order.
 *
 * @param key           key whose values to look up.  the only recognized key is "currency"
 * @param locale        the locale
 * @param commonlyUsed  if true, return only values that are currently used in the locale.
 *                      Otherwise returns all values.
 * @return an array of values for the given key and the locale.  If there is no data, the
 *   array will be empty.
 */

public static final java.lang.String[] getKeywordValuesForLocale(java.lang.String key, android.icu.util.ULocale locale, boolean commonlyUsed) { throw new RuntimeException("Stub!"); }

/**
 * Returns the ISO 4217 3-letter code for this currency object.
 */

public java.lang.String getCurrencyCode() { throw new RuntimeException("Stub!"); }

/**
 * Returns the ISO 4217 numeric code for this currency object.
 * <p>Note: If the ISO 4217 numeric code is not assigned for the currency or
 * the currency is unknown, this method returns 0.</p>
 * @return The ISO 4217 numeric code of this currency.
 */

public int getNumericCode() { throw new RuntimeException("Stub!"); }

/**
 * Convenience and compatibility override of getName that
 * requests the symbol name for the default <code>DISPLAY</code> locale.
 * @see #getName
 * @see android.icu.util.ULocale.Category#DISPLAY
 */

public java.lang.String getSymbol() { throw new RuntimeException("Stub!"); }

/**
 * Convenience and compatibility override of getName that
 * requests the symbol name.
 * @param loc the Locale for the symbol
 * @see #getName
 */

public java.lang.String getSymbol(java.util.Locale loc) { throw new RuntimeException("Stub!"); }

/**
 * Convenience and compatibility override of getName that
 * requests the symbol name.
 * @param uloc the ULocale for the symbol
 * @see #getName
 */

public java.lang.String getSymbol(android.icu.util.ULocale uloc) { throw new RuntimeException("Stub!"); }

/**
 * Returns the display name for the given currency in the
 * given locale.
 * This is a convenient method for
 * getName(ULocale, int, boolean[]);
 *
 * @param locale locale in which to display currency
 * @param nameStyle selector for which kind of name to return.
 *                  The nameStyle should be SYMBOL_NAME, NARROW_SYMBOL_NAME,
 *                  or LONG_NAME. Otherwise, throw IllegalArgumentException.
 * @param isChoiceFormat isChoiceFormat[0] is always set to false, or isChoiceFormat can be null;
 *     display names are static strings;
 *     since ICU 4.4, ChoiceFormat patterns are no longer supported
 * @return display string for this currency.  If the resource data
 * contains no entry for this currency, then the ISO 4217 code is
 * returned.
 */

public java.lang.String getName(java.util.Locale locale, int nameStyle, boolean[] isChoiceFormat) { throw new RuntimeException("Stub!"); }

/**
 * Returns the display name for the given currency in the
 * given locale.  For example, the display name for the USD
 * currency object in the en_US locale is "$".
 *
 * @param locale locale in which to display currency
 * @param nameStyle selector for which kind of name to return.
 *                  The nameStyle should be SYMBOL_NAME, NARROW_SYMBOL_NAME,
 *                  or LONG_NAME. Otherwise, throw IllegalArgumentException.
 * @param isChoiceFormat isChoiceFormat[0] is always set to false, or isChoiceFormat can be null;
 *     display names are static strings;
 *     since ICU 4.4, ChoiceFormat patterns are no longer supported
 * @return display string for this currency.  If the resource data
 * contains no entry for this currency, then the ISO 4217 code is
 * returned.
 * <p>
 * @throws  java.lang.IllegalArgumentException  if the nameStyle is not SYMBOL_NAME
 *                                    or LONG_NAME.
 * @see #getName(ULocale, int, String, boolean[])
 */

public java.lang.String getName(android.icu.util.ULocale locale, int nameStyle, boolean[] isChoiceFormat) { throw new RuntimeException("Stub!"); }

/**
 * Returns the display name for the given currency in the given locale.
 * This is a convenience overload of getName(ULocale, int, String, boolean[]);
 *
 * @param locale locale in which to display currency
 * @param nameStyle selector for which kind of name to return
 * @param pluralCount plural count string for this locale
 * @param isChoiceFormat isChoiceFormat[0] is always set to false, or isChoiceFormat can be null;
 *     display names are static strings;
 *     since ICU 4.4, ChoiceFormat patterns are no longer supported
 * @return display string for this currency.  If the resource data
 * contains no entry for this currency, then the ISO 4217 code is
 * returned.
 */

public java.lang.String getName(java.util.Locale locale, int nameStyle, java.lang.String pluralCount, boolean[] isChoiceFormat) { throw new RuntimeException("Stub!"); }

/**
 * Returns the display name for the given currency in the
 * given locale.  For example, the SYMBOL_NAME for the USD
 * currency object in the en_US locale is "$".
 * The PLURAL_LONG_NAME for the USD currency object when the currency
 * amount is plural is "US dollars", such as in "3.00 US dollars";
 * while the PLURAL_LONG_NAME for the USD currency object when the currency
 * amount is singular is "US dollar", such as in "1.00 US dollar".
 *
 * @param locale locale in which to display currency
 * @param nameStyle selector for which kind of name to return
 * @param pluralCount plural count string for this locale
 * @param isChoiceFormat isChoiceFormat[0] is always set to false, or isChoiceFormat can be null;
 *     display names are static strings;
 *     since ICU 4.4, ChoiceFormat patterns are no longer supported
 * @return display string for this currency.  If the resource data
 * contains no entry for this currency, then the ISO 4217 code is
 * returned.
 * @throws  java.lang.IllegalArgumentException  if the nameStyle is not SYMBOL_NAME,
 *                                    LONG_NAME, or PLURAL_LONG_NAME.
 */

public java.lang.String getName(android.icu.util.ULocale locale, int nameStyle, java.lang.String pluralCount, boolean[] isChoiceFormat) { throw new RuntimeException("Stub!"); }

/**
 * Returns the display name for this currency in the default locale.
 * If the resource data for the default locale contains no entry for this currency,
 * then the ISO 4217 code is returned.
 * <p>
 * Note: This method is a convenience equivalent for
 * {@link java.util.Currency#getDisplayName()} and is equivalent to
 * <code>getName(Locale.getDefault(), LONG_NAME, null)</code>.
 *
 * @return The display name of this currency
 * @see #getDisplayName(Locale)
 * @see #getName(Locale, int, boolean[])
 */

public java.lang.String getDisplayName() { throw new RuntimeException("Stub!"); }

/**
 * Returns the display name for this currency in the given locale.
 * If the resource data for the given locale contains no entry for this currency,
 * then the ISO 4217 code is returned.
 * <p>
 * Note: This method is a convenience equivalent for
 * {@link java.util.Currency#getDisplayName(java.util.Locale)} and is equivalent
 * to <code>getName(locale, LONG_NAME, null)</code>.
 *
 * @param locale locale in which to display currency
 * @return The display name of this currency for the specified locale
 * @see #getDisplayName(Locale)
 * @see #getName(Locale, int, boolean[])
 */

public java.lang.String getDisplayName(java.util.Locale locale) { throw new RuntimeException("Stub!"); }

/**
 * Returns the number of the number of fraction digits that should
 * be displayed for this currency.
 * This is equivalent to getDefaultFractionDigits(CurrencyUsage.STANDARD);
 *
 * Important: The number of fraction digits for a given currency is NOT
 * guaranteed to be constant across versions of ICU or CLDR. For example,
 * do NOT use this value as a mechanism for deciding the magnitude used
 * to store currency values in a database. You should use this value for
 * display purposes only.
 *
 * @return a non-negative number of fraction digits to be
 * displayed
 */

public int getDefaultFractionDigits() { throw new RuntimeException("Stub!"); }

/**
 * Returns the number of the number of fraction digits that should
 * be displayed for this currency with Usage.
 *
 * Important: The number of fraction digits for a given currency is NOT
 * guaranteed to be constant across versions of ICU or CLDR. For example,
 * do NOT use this value as a mechanism for deciding the magnitude used
 * to store currency values in a database. You should use this value for
 * display purposes only.
 *
 * @param Usage the usage of currency(Standard or Cash)
 * @return a non-negative number of fraction digits to be
 * displayed
 */

public int getDefaultFractionDigits(android.icu.util.Currency.CurrencyUsage Usage) { throw new RuntimeException("Stub!"); }

/**
 * Returns the rounding increment for this currency, or 0.0 if no
 * rounding is done by this currency.
 * This is equivalent to getRoundingIncrement(CurrencyUsage.STANDARD);
 * @return the non-negative rounding increment, or 0.0 if none
 */

public double getRoundingIncrement() { throw new RuntimeException("Stub!"); }

/**
 * Returns the rounding increment for this currency, or 0.0 if no
 * rounding is done by this currency with the Usage.
 * @param Usage the usage of currency(Standard or Cash)
 * @return the non-negative rounding increment, or 0.0 if none
 */

public double getRoundingIncrement(android.icu.util.Currency.CurrencyUsage Usage) { throw new RuntimeException("Stub!"); }

/**
 * Returns the ISO 4217 code for this currency.
 */

public java.lang.String toString() { throw new RuntimeException("Stub!"); }

/**
 * Queries if the given ISO 4217 3-letter code is available on the specified date range.
 * <p>
 * Note: For checking availability of a currency on a specific date, specify the date on both <code>from</code> and
 * <code>to</code>. When both <code>from</code> and <code>to</code> are null, this method checks if the specified
 * currency is available all time.
 *
 * @param code
 *            The ISO 4217 3-letter code.
 * @param from
 *            The lower bound of the date range, inclusive. When <code>from</code> is null, check the availability
 *            of the currency any date before <code>to</code>
 * @param to
 *            The upper bound of the date range, inclusive. When <code>to</code> is null, check the availability of
 *            the currency any date after <code>from</code>
 * @return true if the given ISO 4217 3-letter code is supported on the specified date range.
 * @throws java.lang.IllegalArgumentException when <code>to</code> is before <code>from</code>.
 */

public static boolean isAvailable(java.lang.String code, java.util.Date from, java.util.Date to) { throw new RuntimeException("Stub!"); }

/**
 * Selector for getName() indicating the long name for a
 * currency, such as "US Dollar" for USD.
 */

public static final int LONG_NAME = 1; // 0x1

/**
 * Selector for getName() indicating the narrow currency symbol.
 * <p>
 * The narrow currency symbol is similar to the regular currency symbol,
 * but it always takes the shortest form;
 * for example, "$" instead of "US$" for USD in en-CA.
 */

public static final int NARROW_SYMBOL_NAME = 3; // 0x3

/**
 * Selector for getName() indicating the plural long name for a
 * currency, such as "US dollar" for USD in "1 US dollar",
 * and "US dollars" for USD in "2 US dollars".
 */

public static final int PLURAL_LONG_NAME = 2; // 0x2

/**
 * Selector for getName() indicating a symbolic name for a
 * currency, such as "$" for USD.
 */

public static final int SYMBOL_NAME = 0; // 0x0
/**
 * Currency Usage used for Decimal Format
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public enum CurrencyUsage {
/**
 * a setting to specify currency usage which determines currency digit and rounding
 * for standard usage, for example: "50.00 NT$"
 */

STANDARD,
/**
 * a setting to specify currency usage which determines currency digit and rounding
 * for cash usage, for example: "50 NT$"
 */

CASH;
}

}

