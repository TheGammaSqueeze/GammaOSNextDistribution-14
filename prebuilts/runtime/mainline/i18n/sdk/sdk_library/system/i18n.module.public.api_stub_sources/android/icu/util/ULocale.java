/* GENERATED SOURCE. DO NOT MODIFY. */
// ? 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 ******************************************************************************
 * Copyright (C) 2003-2016, International Business Machines Corporation and
 * others. All Rights Reserved.
 ******************************************************************************
 */


package android.icu.util;

import java.util.Locale;
import java.util.MissingResourceException;
import android.icu.text.LocaleDisplayNames;

/**
 * <strong>[icu enhancement]</strong> ICU's replacement for {@link java.util.Locale}.&nbsp;Methods, fields, and other functionality specific to ICU are labeled '<strong>[icu]</strong>'.
 *
 * A class analogous to {@link java.util.Locale} that provides additional
 * support for ICU protocol.  In ICU 3.0 this class is enhanced to support
 * RFC 3066 language identifiers.
 *
 * <p>Many classes and services in ICU follow a factory idiom, in
 * which a factory method or object responds to a client request with
 * an object.  The request includes a locale (the <i>requested</i>
 * locale), and the returned object is constructed using data for that
 * locale.  The system may lack data for the requested locale, in
 * which case the locale fallback mechanism will be invoked until a
 * populated locale is found (the <i>valid</i> locale).  Furthermore,
 * even when a populated locale is found (the <i>valid</i> locale),
 * further fallback may be required to reach a locale containing the
 * specific data required by the service (the <i>actual</i> locale).
 *
 * <p>ULocale performs <b>'normalization'</b> and <b>'canonicalization'</b> of locale ids.
 * Normalization 'cleans up' ICU locale ids as follows:
 * <ul>
 * <li>language, script, country, variant, and keywords are properly cased<br>
 * (lower, title, upper, upper, and lower case respectively)</li>
 * <li>hyphens used as separators are converted to underscores</li>
 * <li>three-letter language and country ids are converted to two-letter
 * equivalents where available</li>
 * <li>surrounding spaces are removed from keywords and values</li>
 * <li>if there are multiple keywords, they are put in sorted order</li>
 * </ul>
 * Canonicalization additionally performs the following:
 * <ul>
 * <li>POSIX ids are converted to ICU format IDs</li>
 * <li>Legacy language tags (marked as ?Type: grandfathered? in BCP 47)
 * are converted to ICU standard form</li>
 * </ul>
 * All ULocale constructors automatically normalize the locale id.  To handle
 * POSIX ids, <code>canonicalize</code> can be called to convert the id
 * to canonical form, or the <code>canonicalInstance</code> factory method
 * can be called.
 *
 * <p>Note: The <i>actual</i> locale is returned correctly, but the <i>valid</i>
 * locale is not, in most cases.
 *
 * @see java.util.Locale
 * @author weiv
 * @author Alan Liu
 * @author Ram Viswanadha
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public final class ULocale implements java.io.Serializable, java.lang.Comparable<android.icu.util.ULocale> {

/**
 * <strong>[icu]</strong> Constructs a ULocale from a RFC 3066 locale ID. The locale ID consists
 * of optional language, script, country, and variant fields in that order,
 * separated by underscores, followed by an optional keyword list.  The
 * script, if present, is four characters long-- this distinguishes it
 * from a country code, which is two characters long.  Other fields
 * are distinguished by position as indicated by the underscores.  The
 * start of the keyword list is indicated by '@', and consists of two
 * or more keyword/value pairs separated by semicolons(';').
 *
 * <p>This constructor does not canonicalize the localeID.  So, for
 * example, "zh__pinyin" remains unchanged instead of converting
 * to "zh@collation=pinyin".  By default ICU only recognizes the
 * latter as specifying pinyin collation.  Use {@link #createCanonical}
 * or {@link #canonicalize} if you need to canonicalize the localeID.
 *
 * @param localeID string representation of the locale, e.g:
 * "en_US", "sy_Cyrl_YU", "zh__pinyin", "es_ES@currency=EUR;collation=traditional"
 */

public ULocale(java.lang.String localeID) { throw new RuntimeException("Stub!"); }

/**
 * Convenience overload of ULocale(String, String, String) for
 * compatibility with java.util.Locale.
 * @see #ULocale(String, String, String)
 */

public ULocale(java.lang.String a, java.lang.String b) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a ULocale from a localeID constructed from the three 'fields' a, b, and
 * c.  These fields are concatenated using underscores to form a localeID of the form
 * a_b_c, which is then handled like the localeID passed to <code>ULocale(String
 * localeID)</code>.
 *
 * <p>Java locale strings consisting of language, country, and
 * variant will be handled by this form, since the country code
 * (being shorter than four letters long) will not be interpreted
 * as a script code.  If a script code is present, the final
 * argument ('c') will be interpreted as the country code.  It is
 * recommended that this constructor only be used to ease porting,
 * and that clients instead use the single-argument constructor
 * when constructing a ULocale from a localeID.
 * @param a first component of the locale id
 * @param b second component of the locale id
 * @param c third component of the locale id
 * @see #ULocale(String)
 */

public ULocale(java.lang.String a, java.lang.String b, java.lang.String c) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns a ULocale object for a {@link java.util.Locale}.
 * The ULocale is canonicalized.
 * @param loc a {@link java.util.Locale}
 */

public static android.icu.util.ULocale forLocale(java.util.Locale loc) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Creates a ULocale from the id by first canonicalizing the id according to CLDR.
 * @param nonCanonicalID the locale id to canonicalize
 * @return the locale created from the canonical version of the ID.
 */

public static android.icu.util.ULocale createCanonical(java.lang.String nonCanonicalID) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Converts this ULocale object to a {@link java.util.Locale}.
 * @return a {@link java.util.Locale} that either exactly represents this object
 * or is the closest approximation.
 */

public java.util.Locale toLocale() { throw new RuntimeException("Stub!"); }

/**
 * Returns the current default ULocale.
 * <p>
 * The default ULocale is synchronized to the default Java Locale. This method checks
 * the current default Java Locale and returns an equivalent ULocale.
 *
 * @return the default ULocale.
 */

public static android.icu.util.ULocale getDefault() { throw new RuntimeException("Stub!"); }

/**
 * Returns the current default ULocale for the specified category.
 *
 * @param category the category
 * @return the default ULocale for the specified category.
 */

public static android.icu.util.ULocale getDefault(android.icu.util.ULocale.Category category) { throw new RuntimeException("Stub!"); }

/**
 * This is for compatibility with Locale-- in actuality, since ULocale is
 * immutable, there is no reason to clone it, so this API returns 'this'.
 */

public java.lang.Object clone() { throw new RuntimeException("Stub!"); }

/**
 * Returns the hashCode.
 * @return a hash code value for this object.
 */

public int hashCode() { throw new RuntimeException("Stub!"); }

/**
 * Returns true if the other object is another ULocale with the
 * same full name.
 * Note that since names are not canonicalized, two ULocales that
 * function identically might not compare equal.
 *
 * @return true if this Locale is equal to the specified object.
 */

public boolean equals(java.lang.Object obj) { throw new RuntimeException("Stub!"); }

/**
 * Compares two ULocale for ordering.
 * <p><b>Note:</b> The order might change in future.
 *
 * @param other the ULocale to be compared.
 * @return a negative integer, zero, or a positive integer as this ULocale is less than, equal to, or greater
 * than the specified ULocale.
 * @throws java.lang.NullPointerException if <code>other</code> is null.
 */

public int compareTo(android.icu.util.ULocale other) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu] Note:</strong> Unlike the Locale API, this returns an array of <code>ULocale</code>,
 * not <code>Locale</code>.
 *
 * <p>Returns a list of all installed locales.
 */

public static android.icu.util.ULocale[] getAvailableLocales() { throw new RuntimeException("Stub!"); }

/**
 * Returns a list of all installed locales according to the specified type.
 */

public static java.util.Collection<android.icu.util.ULocale> getAvailableLocalesByType(android.icu.util.ULocale.AvailableType type) { throw new RuntimeException("Stub!"); }

/**
 * Returns a list of all 2-letter country codes defined in ISO 3166.
 * Can be used to create Locales.
 */

public static java.lang.String[] getISOCountries() { throw new RuntimeException("Stub!"); }

/**
 * Returns a list of all 2-letter language codes defined in ISO 639.
 * Can be used to create Locales.
 * [NOTE:  ISO 639 is not a stable standard-- some languages' codes have changed.
 * The list this function returns includes both the new and the old codes for the
 * languages whose codes have changed.]
 */

public static java.lang.String[] getISOLanguages() { throw new RuntimeException("Stub!"); }

/**
 * Returns the language code for this locale, which will either be the empty string
 * or a lowercase ISO 639 code.
 * @see #getDisplayLanguage()
 * @see #getDisplayLanguage(ULocale)
 */

public java.lang.String getLanguage() { throw new RuntimeException("Stub!"); }

/**
 * Returns the language code for the locale ID,
 * which will either be the empty string
 * or a lowercase ISO 639 code.
 * @see #getDisplayLanguage()
 * @see #getDisplayLanguage(ULocale)
 */

public static java.lang.String getLanguage(java.lang.String localeID) { throw new RuntimeException("Stub!"); }

/**
 * Returns the script code for this locale, which might be the empty string.
 * @see #getDisplayScript()
 * @see #getDisplayScript(ULocale)
 */

public java.lang.String getScript() { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns the script code for the specified locale, which might be the empty
 * string.
 * @see #getDisplayScript()
 * @see #getDisplayScript(ULocale)
 */

public static java.lang.String getScript(java.lang.String localeID) { throw new RuntimeException("Stub!"); }

/**
 * Returns the country/region code for this locale, which will either be the empty string
 * or an uppercase ISO 3166 2-letter code.
 * @see #getDisplayCountry()
 * @see #getDisplayCountry(ULocale)
 */

public java.lang.String getCountry() { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns the country/region code for this locale, which will either be the empty string
 * or an uppercase ISO 3166 2-letter code.
 * @param localeID The locale identification string.
 * @see #getDisplayCountry()
 * @see #getDisplayCountry(ULocale)
 */

public static java.lang.String getCountry(java.lang.String localeID) { throw new RuntimeException("Stub!"); }

/**
 * Returns the variant code for this locale, which might be the empty string.
 * @see #getDisplayVariant()
 * @see #getDisplayVariant(ULocale)
 */

public java.lang.String getVariant() { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns the variant code for the specified locale, which might be the empty string.
 * @see #getDisplayVariant()
 * @see #getDisplayVariant(ULocale)
 */

public static java.lang.String getVariant(java.lang.String localeID) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns the fallback locale for the specified locale, which might be the
 * empty string.
 */

public static java.lang.String getFallback(java.lang.String localeID) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns the fallback locale for this locale.  If this locale is root,
 * returns null.
 */

public android.icu.util.ULocale getFallback() { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns the (normalized) base name for this locale,
 * like {@link #getName()}, but without keywords.
 *
 * @return the base name as a String.
 */

public java.lang.String getBaseName() { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns the (normalized) base name for the specified locale,
 * like {@link #getName(java.lang.String)}, but without keywords.
 *
 * @param localeID the locale ID as a string
 * @return the base name as a String.
 */

public static java.lang.String getBaseName(java.lang.String localeID) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns the (normalized) full name for this locale.
 *
 * @return String the full name of the localeID
 */

public java.lang.String getName() { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns the (normalized) full name for the specified locale.
 *
 * @param localeID the localeID as a string
 * @return String the full name of the localeID
 */

public static java.lang.String getName(java.lang.String localeID) { throw new RuntimeException("Stub!"); }

/**
 * Returns a string representation of this object.
 * @return a string representation of the object.
 */

public java.lang.String toString() { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns an iterator over keywords for this locale.  If there
 * are no keywords, returns null.
 * @return iterator over keywords, or null if there are no keywords.
 */

public java.util.Iterator<java.lang.String> getKeywords() { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns an iterator over keywords for the specified locale.  If there
 * are no keywords, returns null.
 * @return an iterator over the keywords in the specified locale, or null
 * if there are no keywords.
 */

public static java.util.Iterator<java.lang.String> getKeywords(java.lang.String localeID) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns the value for a keyword in this locale. If the keyword is not
 * defined, returns null.
 * @param keywordName name of the keyword whose value is desired. Case insensitive.
 * @return the value of the keyword, or null.
 */

public java.lang.String getKeywordValue(java.lang.String keywordName) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns the value for a keyword in the specified locale. If the keyword is
 * not defined, returns null.  The locale name does not need to be normalized.
 * @param keywordName name of the keyword whose value is desired. Case insensitive.
 * @return String the value of the keyword as a string
 */

public static java.lang.String getKeywordValue(java.lang.String localeID, java.lang.String keywordName) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns the canonical name according to CLDR for the specified locale ID.
 * This is used to convert POSIX and other legacy IDs to standard ICU form.
 * @param localeID the locale id
 * @return the canonicalized id
 */

public static java.lang.String canonicalize(java.lang.String localeID) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Given a keyword and a value, return a new locale with an updated
 * keyword and value.  If the keyword is null, this removes all keywords from the locale id.
 * Otherwise, if the value is null, this removes the value for this keyword from the
 * locale id.  Otherwise, this adds/replaces the value for this keyword in the locale id.
 * The keyword and value must not be empty.
 *
 * <p>Related: {@link #getBaseName()} returns the locale ID string with all keywords removed.
 *
 * @param keyword the keyword to add/remove, or null to remove all keywords.
 * @param value the value to add/set, or null to remove this particular keyword.
 * @return the updated locale
 */

public android.icu.util.ULocale setKeywordValue(java.lang.String keyword, java.lang.String value) { throw new RuntimeException("Stub!"); }

/**
 * Given a locale id, a keyword, and a value, return a new locale id with an updated
 * keyword and value.  If the keyword is null, this removes all keywords from the locale id.
 * Otherwise, if the value is null, this removes the value for this keyword from the
 * locale id.  Otherwise, this adds/replaces the value for this keyword in the locale id.
 * The keyword and value must not be empty.
 *
 * <p>Related: {@link #getBaseName(java.lang.String)} returns the locale ID string with all keywords removed.
 *
 * @param localeID the locale id to modify
 * @param keyword the keyword to add/remove, or null to remove all keywords.
 * @param value the value to add/set, or null to remove this particular keyword.
 * @return the updated locale id
 */

public static java.lang.String setKeywordValue(java.lang.String localeID, java.lang.String keyword, java.lang.String value) { throw new RuntimeException("Stub!"); }

/**
 * Returns a three-letter abbreviation for this locale's language.  If the locale
 * doesn't specify a language, returns the empty string.  Otherwise, returns
 * a lowercase ISO 639-2/T language code.
 * The ISO 639-2 language codes can be found on-line at
 *   <a href="ftp://dkuug.dk/i18n/iso-639-2.txt"><code>ftp://dkuug.dk/i18n/iso-639-2.txt</code></a>
 * @exception java.util.MissingResourceException Throws MissingResourceException if the
 * three-letter language abbreviation is not available for this locale.
 */

public java.lang.String getISO3Language() { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns a three-letter abbreviation for this locale's language.  If the locale
 * doesn't specify a language, returns the empty string.  Otherwise, returns
 * a lowercase ISO 639-2/T language code.
 * The ISO 639-2 language codes can be found on-line at
 *   <a href="ftp://dkuug.dk/i18n/iso-639-2.txt"><code>ftp://dkuug.dk/i18n/iso-639-2.txt</code></a>
 * @exception java.util.MissingResourceException Throws MissingResourceException if the
 * three-letter language abbreviation is not available for this locale.
 */

public static java.lang.String getISO3Language(java.lang.String localeID) { throw new RuntimeException("Stub!"); }

/**
 * Returns a three-letter abbreviation for this locale's country/region.  If the locale
 * doesn't specify a country, returns the empty string.  Otherwise, returns
 * an uppercase ISO 3166 3-letter country code.
 * @exception java.util.MissingResourceException Throws MissingResourceException if the
 * three-letter country abbreviation is not available for this locale.
 */

public java.lang.String getISO3Country() { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns a three-letter abbreviation for this locale's country/region.  If the locale
 * doesn't specify a country, returns the empty string.  Otherwise, returns
 * an uppercase ISO 3166 3-letter country code.
 * @exception java.util.MissingResourceException Throws MissingResourceException if the
 * three-letter country abbreviation is not available for this locale.
 */

public static java.lang.String getISO3Country(java.lang.String localeID) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns whether this locale's script is written right-to-left.
 * If there is no script subtag, then the likely script is used,
 * see {@link #addLikelySubtags(android.icu.util.ULocale)}.
 * If no likely script is known, then false is returned.
 *
 * <p>A script is right-to-left according to the CLDR script metadata
 * which corresponds to whether the script's letters have Bidi_Class=R or AL.
 *
 * <p>Returns true for "ar" and "en-Hebr", false for "zh" and "fa-Cyrl".
 *
 * @return true if the locale's script is written right-to-left
 */

public boolean isRightToLeft() { throw new RuntimeException("Stub!"); }

/**
 * Returns this locale's language localized for display in the default <code>DISPLAY</code> locale.
 * @return the localized language name.
 * @see android.icu.util.ULocale.Category#DISPLAY
 */

public java.lang.String getDisplayLanguage() { throw new RuntimeException("Stub!"); }

/**
 * Returns this locale's language localized for display in the provided locale.
 * @param displayLocale the locale in which to display the name.
 * @return the localized language name.
 */

public java.lang.String getDisplayLanguage(android.icu.util.ULocale displayLocale) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns a locale's language localized for display in the provided locale.
 * This is a cover for the ICU4C API.
 * @param localeID the id of the locale whose language will be displayed
 * @param displayLocaleID the id of the locale in which to display the name.
 * @return the localized language name.
 */

public static java.lang.String getDisplayLanguage(java.lang.String localeID, java.lang.String displayLocaleID) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns a locale's language localized for display in the provided locale.
 * This is a cover for the ICU4C API.
 * @param localeID the id of the locale whose language will be displayed.
 * @param displayLocale the locale in which to display the name.
 * @return the localized language name.
 */

public static java.lang.String getDisplayLanguage(java.lang.String localeID, android.icu.util.ULocale displayLocale) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns this locale's language localized for display in the default <code>DISPLAY</code> locale.
 * If a dialect name is present in the data, then it is returned.
 * @return the localized language name.
 * @see android.icu.util.ULocale.Category#DISPLAY
 */

public java.lang.String getDisplayLanguageWithDialect() { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns this locale's language localized for display in the provided locale.
 * If a dialect name is present in the data, then it is returned.
 * @param displayLocale the locale in which to display the name.
 * @return the localized language name.
 */

public java.lang.String getDisplayLanguageWithDialect(android.icu.util.ULocale displayLocale) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns a locale's language localized for display in the provided locale.
 * If a dialect name is present in the data, then it is returned.
 * This is a cover for the ICU4C API.
 * @param localeID the id of the locale whose language will be displayed
 * @param displayLocaleID the id of the locale in which to display the name.
 * @return the localized language name.
 */

public static java.lang.String getDisplayLanguageWithDialect(java.lang.String localeID, java.lang.String displayLocaleID) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns a locale's language localized for display in the provided locale.
 * If a dialect name is present in the data, then it is returned.
 * This is a cover for the ICU4C API.
 * @param localeID the id of the locale whose language will be displayed.
 * @param displayLocale the locale in which to display the name.
 * @return the localized language name.
 */

public static java.lang.String getDisplayLanguageWithDialect(java.lang.String localeID, android.icu.util.ULocale displayLocale) { throw new RuntimeException("Stub!"); }

/**
 * Returns this locale's script localized for display in the default <code>DISPLAY</code> locale.
 * @return the localized script name.
 * @see android.icu.util.ULocale.Category#DISPLAY
 */

public java.lang.String getDisplayScript() { throw new RuntimeException("Stub!"); }

/**
 * Returns this locale's script localized for display in the provided locale.
 * @param displayLocale the locale in which to display the name.
 * @return the localized script name.
 */

public java.lang.String getDisplayScript(android.icu.util.ULocale displayLocale) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns a locale's script localized for display in the provided locale.
 * This is a cover for the ICU4C API.
 * @param localeID the id of the locale whose script will be displayed
 * @param displayLocaleID the id of the locale in which to display the name.
 * @return the localized script name.
 */

public static java.lang.String getDisplayScript(java.lang.String localeID, java.lang.String displayLocaleID) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns a locale's script localized for display in the provided locale.
 * @param localeID the id of the locale whose script will be displayed.
 * @param displayLocale the locale in which to display the name.
 * @return the localized script name.
 */

public static java.lang.String getDisplayScript(java.lang.String localeID, android.icu.util.ULocale displayLocale) { throw new RuntimeException("Stub!"); }

/**
 * Returns this locale's country localized for display in the default <code>DISPLAY</code> locale.
 * <b>Warning: </b>this is for the region part of a valid locale ID; it cannot just be the region code (like "FR").
 * To get the display name for a region alone, or for other options, use {@link android.icu.text.LocaleDisplayNames LocaleDisplayNames} instead.
 * @return the localized country name.
 * @see android.icu.util.ULocale.Category#DISPLAY
 */

public java.lang.String getDisplayCountry() { throw new RuntimeException("Stub!"); }

/**
 * Returns this locale's country localized for display in the provided locale.
 * <b>Warning: </b>this is for the region part of a valid locale ID; it cannot just be the region code (like "FR").
 * To get the display name for a region alone, or for other options, use {@link android.icu.text.LocaleDisplayNames LocaleDisplayNames} instead.
 * @param displayLocale the locale in which to display the name.
 * @return the localized country name.
 */

public java.lang.String getDisplayCountry(android.icu.util.ULocale displayLocale) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns a locale's country localized for display in the provided locale.
 * <b>Warning: </b>this is for the region part of a valid locale ID; it cannot just be the region code (like "FR").
 * To get the display name for a region alone, or for other options, use {@link android.icu.text.LocaleDisplayNames LocaleDisplayNames} instead.
 * This is a cover for the ICU4C API.
 * @param localeID the id of the locale whose country will be displayed
 * @param displayLocaleID the id of the locale in which to display the name.
 * @return the localized country name.
 */

public static java.lang.String getDisplayCountry(java.lang.String localeID, java.lang.String displayLocaleID) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns a locale's country localized for display in the provided locale.
 * <b>Warning: </b>this is for the region part of a valid locale ID; it cannot just be the region code (like "FR").
 * To get the display name for a region alone, or for other options, use {@link android.icu.text.LocaleDisplayNames LocaleDisplayNames} instead.
 * This is a cover for the ICU4C API.
 * @param localeID the id of the locale whose country will be displayed.
 * @param displayLocale the locale in which to display the name.
 * @return the localized country name.
 */

public static java.lang.String getDisplayCountry(java.lang.String localeID, android.icu.util.ULocale displayLocale) { throw new RuntimeException("Stub!"); }

/**
 * Returns this locale's variant localized for display in the default <code>DISPLAY</code> locale.
 * @return the localized variant name.
 * @see android.icu.util.ULocale.Category#DISPLAY
 */

public java.lang.String getDisplayVariant() { throw new RuntimeException("Stub!"); }

/**
 * Returns this locale's variant localized for display in the provided locale.
 * @param displayLocale the locale in which to display the name.
 * @return the localized variant name.
 */

public java.lang.String getDisplayVariant(android.icu.util.ULocale displayLocale) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns a locale's variant localized for display in the provided locale.
 * This is a cover for the ICU4C API.
 * @param localeID the id of the locale whose variant will be displayed
 * @param displayLocaleID the id of the locale in which to display the name.
 * @return the localized variant name.
 */

public static java.lang.String getDisplayVariant(java.lang.String localeID, java.lang.String displayLocaleID) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns a locale's variant localized for display in the provided locale.
 * This is a cover for the ICU4C API.
 * @param localeID the id of the locale whose variant will be displayed.
 * @param displayLocale the locale in which to display the name.
 * @return the localized variant name.
 */

public static java.lang.String getDisplayVariant(java.lang.String localeID, android.icu.util.ULocale displayLocale) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns a keyword localized for display in the default <code>DISPLAY</code> locale.
 * @param keyword the keyword to be displayed.
 * @return the localized keyword name.
 * @see #getKeywords()
 * @see android.icu.util.ULocale.Category#DISPLAY
 */

public static java.lang.String getDisplayKeyword(java.lang.String keyword) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns a keyword localized for display in the specified locale.
 * @param keyword the keyword to be displayed.
 * @param displayLocaleID the id of the locale in which to display the keyword.
 * @return the localized keyword name.
 * @see #getKeywords(String)
 */

public static java.lang.String getDisplayKeyword(java.lang.String keyword, java.lang.String displayLocaleID) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns a keyword localized for display in the specified locale.
 * @param keyword the keyword to be displayed.
 * @param displayLocale the locale in which to display the keyword.
 * @return the localized keyword name.
 * @see #getKeywords(String)
 */

public static java.lang.String getDisplayKeyword(java.lang.String keyword, android.icu.util.ULocale displayLocale) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns a keyword value localized for display in the default <code>DISPLAY</code> locale.
 * @param keyword the keyword whose value is to be displayed.
 * @return the localized value name.
 * @see android.icu.util.ULocale.Category#DISPLAY
 */

public java.lang.String getDisplayKeywordValue(java.lang.String keyword) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns a keyword value localized for display in the specified locale.
 * @param keyword the keyword whose value is to be displayed.
 * @param displayLocale the locale in which to display the value.
 * @return the localized value name.
 */

public java.lang.String getDisplayKeywordValue(java.lang.String keyword, android.icu.util.ULocale displayLocale) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns a keyword value localized for display in the specified locale.
 * This is a cover for the ICU4C API.
 * @param localeID the id of the locale whose keyword value is to be displayed.
 * @param keyword the keyword whose value is to be displayed.
 * @param displayLocaleID the id of the locale in which to display the value.
 * @return the localized value name.
 */

public static java.lang.String getDisplayKeywordValue(java.lang.String localeID, java.lang.String keyword, java.lang.String displayLocaleID) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns a keyword value localized for display in the specified locale.
 * This is a cover for the ICU4C API.
 * @param localeID the id of the locale whose keyword value is to be displayed.
 * @param keyword the keyword whose value is to be displayed.
 * @param displayLocale the id of the locale in which to display the value.
 * @return the localized value name.
 */

public static java.lang.String getDisplayKeywordValue(java.lang.String localeID, java.lang.String keyword, android.icu.util.ULocale displayLocale) { throw new RuntimeException("Stub!"); }

/**
 * Returns this locale name localized for display in the default <code>DISPLAY</code> locale.
 * @return the localized locale name.
 * @see android.icu.util.ULocale.Category#DISPLAY
 */

public java.lang.String getDisplayName() { throw new RuntimeException("Stub!"); }

/**
 * Returns this locale name localized for display in the provided locale.
 * @param displayLocale the locale in which to display the locale name.
 * @return the localized locale name.
 */

public java.lang.String getDisplayName(android.icu.util.ULocale displayLocale) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns the locale ID localized for display in the provided locale.
 * This is a cover for the ICU4C API.
 * @param localeID the locale whose name is to be displayed.
 * @param displayLocaleID the id of the locale in which to display the locale name.
 * @return the localized locale name.
 */

public static java.lang.String getDisplayName(java.lang.String localeID, java.lang.String displayLocaleID) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns the locale ID localized for display in the provided locale.
 * This is a cover for the ICU4C API.
 * @param localeID the locale whose name is to be displayed.
 * @param displayLocale the locale in which to display the locale name.
 * @return the localized locale name.
 */

public static java.lang.String getDisplayName(java.lang.String localeID, android.icu.util.ULocale displayLocale) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns this locale name localized for display in the default <code>DISPLAY</code> locale.
 * If a dialect name is present in the locale data, then it is returned.
 * @return the localized locale name.
 * @see android.icu.util.ULocale.Category#DISPLAY
 */

public java.lang.String getDisplayNameWithDialect() { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns this locale name localized for display in the provided locale.
 * If a dialect name is present in the locale data, then it is returned.
 * @param displayLocale the locale in which to display the locale name.
 * @return the localized locale name.
 */

public java.lang.String getDisplayNameWithDialect(android.icu.util.ULocale displayLocale) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns the locale ID localized for display in the provided locale.
 * If a dialect name is present in the locale data, then it is returned.
 * This is a cover for the ICU4C API.
 * @param localeID the locale whose name is to be displayed.
 * @param displayLocaleID the id of the locale in which to display the locale name.
 * @return the localized locale name.
 */

public static java.lang.String getDisplayNameWithDialect(java.lang.String localeID, java.lang.String displayLocaleID) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns the locale ID localized for display in the provided locale.
 * If a dialect name is present in the locale data, then it is returned.
 * This is a cover for the ICU4C API.
 * @param localeID the locale whose name is to be displayed.
 * @param displayLocale the locale in which to display the locale name.
 * @return the localized locale name.
 */

public static java.lang.String getDisplayNameWithDialect(java.lang.String localeID, android.icu.util.ULocale displayLocale) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns this locale's layout orientation for characters.  The possible
 * values are "left-to-right", "right-to-left", "top-to-bottom" or
 * "bottom-to-top".
 * @return The locale's layout orientation for characters.
 */

public java.lang.String getCharacterOrientation() { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns this locale's layout orientation for lines.  The possible
 * values are "left-to-right", "right-to-left", "top-to-bottom" or
 * "bottom-to-top".
 * @return The locale's layout orientation for lines.
 */

public java.lang.String getLineOrientation() { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Based on a HTTP formatted list of acceptable locales, determine an available
 * locale for the user.  NullPointerException is thrown if acceptLanguageList or
 * availableLocales is null.  If fallback is non-null, it will contain true if a
 * fallback locale (one not in the acceptLanguageList) was returned.  The value on
 * entry is ignored.  ULocale will be one of the locales in availableLocales, or the
 * ROOT ULocale if if a ROOT locale was used as a fallback (because nothing else in
 * availableLocales matched).  No ULocale array element should be null; behavior is
 * undefined if this is the case.
 *
 * @param acceptLanguageList list in HTTP "Accept-Language:" format of acceptable locales
 * @param availableLocales list of available locales. One of these will be returned.
 * @param fallback if non-null, a 1-element array containing a boolean to be set with
 * the fallback status
 * @return one of the locales from the availableLocales list, or null if none match
 */

public static android.icu.util.ULocale acceptLanguage(java.lang.String acceptLanguageList, android.icu.util.ULocale[] availableLocales, boolean[] fallback) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Based on a list of acceptable locales, determine an available locale for the
 * user.  NullPointerException is thrown if acceptLanguageList or availableLocales is
 * null.  If fallback is non-null, it will contain true if a fallback locale (one not
 * in the acceptLanguageList) was returned.  The value on entry is ignored.  ULocale
 * will be one of the locales in availableLocales, or the ROOT ULocale if if a ROOT
 * locale was used as a fallback (because nothing else in availableLocales matched).
 * No ULocale array element should be null; behavior is undefined if this is the case.
 *
 * @param acceptLanguageList list of acceptable locales
 * @param availableLocales list of available locales. One of these will be returned.
 * @param fallback if non-null, a 1-element array containing a boolean to be set with
 * the fallback status
 * @return one of the locales from the availableLocales list, or null if none match
 */

public static android.icu.util.ULocale acceptLanguage(android.icu.util.ULocale[] acceptLanguageList, android.icu.util.ULocale[] availableLocales, boolean[] fallback) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Based on a HTTP formatted list of acceptable locales, determine an available
 * locale for the user.  NullPointerException is thrown if acceptLanguageList or
 * availableLocales is null.  If fallback is non-null, it will contain true if a
 * fallback locale (one not in the acceptLanguageList) was returned.  The value on
 * entry is ignored.  ULocale will be one of the locales in availableLocales, or the
 * ROOT ULocale if if a ROOT locale was used as a fallback (because nothing else in
 * availableLocales matched).  No ULocale array element should be null; behavior is
 * undefined if this is the case.  This function will choose a locale from the
 * ULocale.getAvailableLocales() list as available.
 *
 * @param acceptLanguageList list in HTTP "Accept-Language:" format of acceptable locales
 * @param fallback if non-null, a 1-element array containing a boolean to be set with
 * the fallback status
 * @return one of the locales from the ULocale.getAvailableLocales() list, or null if
 * none match
 */

public static android.icu.util.ULocale acceptLanguage(java.lang.String acceptLanguageList, boolean[] fallback) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Based on an ordered array of acceptable locales, determine an available
 * locale for the user.  NullPointerException is thrown if acceptLanguageList or
 * availableLocales is null.  If fallback is non-null, it will contain true if a
 * fallback locale (one not in the acceptLanguageList) was returned.  The value on
 * entry is ignored.  ULocale will be one of the locales in availableLocales, or the
 * ROOT ULocale if if a ROOT locale was used as a fallback (because nothing else in
 * availableLocales matched).  No ULocale array element should be null; behavior is
 * undefined if this is the case.  This function will choose a locale from the
 * ULocale.getAvailableLocales() list as available.
 *
 * @param acceptLanguageList ordered array of acceptable locales (preferred are listed first)
 * @param fallback if non-null, a 1-element array containing a boolean to be set with
 * the fallback status
 * @return one of the locales from the ULocale.getAvailableLocales() list, or null if none match
 */

public static android.icu.util.ULocale acceptLanguage(android.icu.util.ULocale[] acceptLanguageList, boolean[] fallback) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Adds the likely subtags for a provided locale ID, per the algorithm
 * described in the following CLDR technical report:
 *
 *   http://www.unicode.org/reports/tr35/#Likely_Subtags
 *
 * If the provided ULocale instance is already in the maximal form, or there is no
 * data available available for maximization, it will be returned.  For example,
 * "und-Zzzz" cannot be maximized, since there is no reasonable maximization.
 * Otherwise, a new ULocale instance with the maximal form is returned.
 *
 * Examples:
 *
 * "en" maximizes to "en_Latn_US"
 *
 * "de" maximizes to "de_Latn_US"
 *
 * "sr" maximizes to "sr_Cyrl_RS"
 *
 * "sh" maximizes to "sr_Latn_RS" (Note this will not reverse.)
 *
 * "zh_Hani" maximizes to "zh_Hans_CN" (Note this will not reverse.)
 *
 * @param loc The ULocale to maximize
 * @return The maximized ULocale instance.
 */

public static android.icu.util.ULocale addLikelySubtags(android.icu.util.ULocale loc) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Minimizes the subtags for a provided locale ID, per the algorithm described
 * in the following CLDR technical report:<blockquote>
 *
 *   <a href="http://www.unicode.org/reports/tr35/#Likely_Subtags"
 *>http://www.unicode.org/reports/tr35/#Likely_Subtags</a></blockquote>
 *
 * If the provided ULocale instance is already in the minimal form, or there
 * is no data available for minimization, it will be returned.  Since the
 * minimization algorithm relies on proper maximization, see the comments
 * for addLikelySubtags for reasons why there might not be any data.
 *
 * Examples:<pre>
 *
 * "en_Latn_US" minimizes to "en"
 *
 * "de_Latn_US" minimizes to "de"
 *
 * "sr_Cyrl_RS" minimizes to "sr"
 *
 * "zh_Hant_TW" minimizes to "zh_TW" (The region is preferred to the
 * script, and minimizing to "zh" would imply "zh_Hans_CN".) </pre>
 *
 * @param loc The ULocale to minimize
 * @return The minimized ULocale instance.
 */

public static android.icu.util.ULocale minimizeSubtags(android.icu.util.ULocale loc) { throw new RuntimeException("Stub!"); }

/**
 * Returns the extension (or private use) value associated with
 * the specified key, or null if there is no extension
 * associated with the key. To be well-formed, the key must be one
 * of <code>[0-9A-Za-z]</code>. Keys are case-insensitive, so
 * for example 'z' and 'Z' represent the same extension.
 *
 * @param key the extension key
 * @return The extension, or null if this locale defines no
 * extension for the specified key.
 * @throws java.lang.IllegalArgumentException if key is not well-formed
 * @see #PRIVATE_USE_EXTENSION
 * @see #UNICODE_LOCALE_EXTENSION
 */

public java.lang.String getExtension(char key) { throw new RuntimeException("Stub!"); }

/**
 * Returns the set of extension keys associated with this locale, or the
 * empty set if it has no extensions. The returned set is unmodifiable.
 * The keys will all be lower-case.
 *
 * @return the set of extension keys, or the empty set if this locale has
 * no extensions
 */

public java.util.Set<java.lang.Character> getExtensionKeys() { throw new RuntimeException("Stub!"); }

/**
 * Returns the set of unicode locale attributes associated with
 * this locale, or the empty set if it has no attributes. The
 * returned set is unmodifiable.
 *
 * @return The set of attributes.
 */

public java.util.Set<java.lang.String> getUnicodeLocaleAttributes() { throw new RuntimeException("Stub!"); }

/**
 * Returns the Unicode locale type associated with the specified Unicode locale key
 * for this locale. Returns the empty string for keys that are defined with no type.
 * Returns null if the key is not defined. Keys are case-insensitive. The key must
 * be two alphanumeric characters ([0-9a-zA-Z]), or an IllegalArgumentException is
 * thrown.
 *
 * @param key the Unicode locale key
 * @return The Unicode locale type associated with the key, or null if the
 * locale does not define the key.
 * @throws java.lang.IllegalArgumentException if the key is not well-formed
 * @throws java.lang.NullPointerException if <code>key</code> is null
 */

public java.lang.String getUnicodeLocaleType(java.lang.String key) { throw new RuntimeException("Stub!"); }

/**
 * Returns the set of Unicode locale keys defined by this locale, or the empty set if
 * this locale has none.  The returned set is immutable.  Keys are all lower case.
 *
 * @return The set of Unicode locale keys, or the empty set if this locale has
 * no Unicode locale keywords.
 */

public java.util.Set<java.lang.String> getUnicodeLocaleKeys() { throw new RuntimeException("Stub!"); }

/**
 * Returns a well-formed IETF BCP 47 language tag representing
 * this locale.
 *
 * <p>If this <code>ULocale</code> has a language, script, country, or
 * variant that does not satisfy the IETF BCP 47 language tag
 * syntax requirements, this method handles these fields as
 * described below:
 *
 * <p><b>Language:</b> If language is empty, or not well-formed
 * (for example "a" or "e2"), it will be emitted as "und" (Undetermined).
 *
 * <p><b>Script:</b> If script is not well-formed (for example "12"
 * or "Latin"), it will be omitted.
 *
 * <p><b>Country:</b> If country is not well-formed (for example "12"
 * or "USA"), it will be omitted.
 *
 * <p><b>Variant:</b> If variant <b>is</b> well-formed, each sub-segment
 * (delimited by '-' or '_') is emitted as a subtag.  Otherwise:
 * <ul>
 *
 * <li>if all sub-segments match <code>[0-9a-zA-Z]{1,8}</code>
 * (for example "WIN" or "Oracle_JDK_Standard_Edition"), the first
 * ill-formed sub-segment and all following will be appended to
 * the private use subtag.  The first appended subtag will be
 * "lvariant", followed by the sub-segments in order, separated by
 * hyphen. For example, "x-lvariant-WIN",
 * "Oracle-x-lvariant-JDK-Standard-Edition".
 *
 * <li>if any sub-segment does not match
 * <code>[0-9a-zA-Z]{1,8}</code>, the variant will be truncated
 * and the problematic sub-segment and all following sub-segments
 * will be omitted.  If the remainder is non-empty, it will be
 * emitted as a private use subtag as above (even if the remainder
 * turns out to be well-formed).  For example,
 * "Solaris_isjustthecoolestthing" is emitted as
 * "x-lvariant-Solaris", not as "solaris".</li></ul>
 *
 * <p><b>Note:</b> Although the language tag created by this
 * method is well-formed (satisfies the syntax requirements
 * defined by the IETF BCP 47 specification), it is not
 * necessarily a valid BCP 47 language tag.  For example,
 * <pre>
 *   new Locale("xx", "YY").toLanguageTag();</pre>
 *
 * will return "xx-YY", but the language subtag "xx" and the
 * region subtag "YY" are invalid because they are not registered
 * in the IANA Language Subtag Registry.
 *
 * @return a BCP47 language tag representing the locale
 * @see #forLanguageTag(String)
 */

public java.lang.String toLanguageTag() { throw new RuntimeException("Stub!"); }

/**
 * Returns a locale for the specified IETF BCP 47 language tag string.
 *
 * <p>If the specified language tag contains any ill-formed subtags,
 * the first such subtag and all following subtags are ignored.  Compare
 * to {@link android.icu.util.ULocale.Builder#setLanguageTag ULocale.Builder#setLanguageTag} which throws an exception
 * in this case.
 *
 * <p>The following <b>conversions</b> are performed:
 * <ul>
 *
 * <li>The language code "und" is mapped to language "".
 *
 * <li>The portion of a private use subtag prefixed by "lvariant",
 * if any, is removed and appended to the variant field in the
 * result locale (without case normalization).  If it is then
 * empty, the private use subtag is discarded:
 *
 * <pre>
 *     ULocale loc;
 *     loc = ULocale.forLanguageTag("en-US-x-lvariant-icu4j);
 *     loc.getVariant(); // returns "ICU4J"
 *     loc.getExtension('x'); // returns null
 *
 *     loc = Locale.forLanguageTag("de-icu4j-x-URP-lvariant-Abc-Def");
 *     loc.getVariant(); // returns "ICU4J_ABC_DEF"
 *     loc.getExtension('x'); // returns "urp"
 * </pre>
 *
 * <li>When the languageTag argument contains an extlang subtag,
 * the first such subtag is used as the language, and the primary
 * language subtag and other extlang subtags are ignored:
 *
 * <pre>
 *     ULocale.forLanguageTag("ar-aao").getLanguage(); // returns "aao"
 *     ULocale.forLanguageTag("en-abc-def-us").toString(); // returns "abc_US"
 * </pre>
 *
 * <li>Case is normalized. Language is normalized to lower case,
 * script to title case, country to upper case, variant to upper case,
 * and extensions to lower case.
 *
 * </ul>
 *
 * <p>This implements the 'Language-Tag' production of BCP 47, and so
 * supports legacy language tags (marked as ?Type: grandfathered? in BCP 47)
 * (regular and irregular) as well as private use language tags.
 *
 * <p>Stand-alone private use tags are represented as empty language and extension 'x-whatever',
 * and legacy tags are converted to their canonical replacements where they exist.
 *
 * <p>Note that a few legacy tags have no modern replacement;
 * these will be converted using the fallback described in
 * the first paragraph, so some information might be lost.
 *
 * <p><b>Note</b>: there is no guarantee that <code>toLanguageTag</code>
 * and <code>forLanguageTag</code> will round-trip.
 *
 * @param languageTag the language tag
 * @return The locale that best represents the language tag.
 * @throws java.lang.NullPointerException if <code>languageTag</code> is <code>null</code>
 * @see #toLanguageTag()
 * @see android.icu.util.ULocale.Builder#setLanguageTag(String)
 */

public static android.icu.util.ULocale forLanguageTag(java.lang.String languageTag) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Converts the specified keyword (legacy key, or BCP 47 Unicode locale
 * extension key) to the equivalent BCP 47 Unicode locale extension key.
 * For example, BCP 47 Unicode locale extension key "co" is returned for
 * the input keyword "collation".
 * <p>
 * When the specified keyword is unknown, but satisfies the BCP syntax,
 * then the lower-case version of the input keyword will be returned.
 * For example,
 * <code>toUnicodeLocaleKey("ZZ")</code> returns "zz".
 *
 * @param keyword       the input locale keyword (either legacy key
 *                      such as "collation" or BCP 47 Unicode locale extension
 *                      key such as "co").
 * @return              the well-formed BCP 47 Unicode locale extension key,
 *                      or null if the specified locale keyword cannot be mapped
 *                      to a well-formed BCP 47 Unicode locale extension key.
 * @see #toLegacyKey(String)
 */

public static java.lang.String toUnicodeLocaleKey(java.lang.String keyword) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Converts the specified keyword value (legacy type, or BCP 47
 * Unicode locale extension type) to the well-formed BCP 47 Unicode locale
 * extension type for the specified keyword (category). For example, BCP 47
 * Unicode locale extension type "phonebk" is returned for the input
 * keyword value "phonebook", with the keyword "collation" (or "co").
 * <p>
 * When the specified keyword is not recognized, but the specified value
 * satisfies the syntax of the BCP 47 Unicode locale extension type,
 * or when the specified keyword allows 'variable' type and the specified
 * value satisfies the syntax, the lower-case version of the input value
 * will be returned. For example,
 * <code>toUnicodeLocaleType("Foo", "Bar")</code> returns "bar",
 * <code>toUnicodeLocaleType("variableTop", "00A4")</code> returns "00a4".
 *
 * @param keyword       the locale keyword (either legacy key such as
 *                      "collation" or BCP 47 Unicode locale extension
 *                      key such as "co").
 * @param value         the locale keyword value (either legacy type
 *                      such as "phonebook" or BCP 47 Unicode locale extension
 *                      type such as "phonebk").
 * @return              the well-formed BCP47 Unicode locale extension type,
 *                      or null if the locale keyword value cannot be mapped to
 *                      a well-formed BCP 47 Unicode locale extension type.
 * @see #toLegacyType(String, String)
 */

public static java.lang.String toUnicodeLocaleType(java.lang.String keyword, java.lang.String value) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Converts the specified keyword (BCP 47 Unicode locale extension key, or
 * legacy key) to the legacy key. For example, legacy key "collation" is
 * returned for the input BCP 47 Unicode locale extension key "co".
 *
 * @param keyword       the input locale keyword (either BCP 47 Unicode locale
 *                      extension key or legacy key).
 * @return              the well-formed legacy key, or null if the specified
 *                      keyword cannot be mapped to a well-formed legacy key.
 * @see #toUnicodeLocaleKey(String)
 */

public static java.lang.String toLegacyKey(java.lang.String keyword) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Converts the specified keyword value (BCP 47 Unicode locale extension type,
 * or legacy type or type alias) to the canonical legacy type. For example,
 * the legacy type "phonebook" is returned for the input BCP 47 Unicode
 * locale extension type "phonebk" with the keyword "collation" (or "co").
 * <p>
 * When the specified keyword is not recognized, but the specified value
 * satisfies the syntax of legacy key, or when the specified keyword
 * allows 'variable' type and the specified value satisfies the syntax,
 * the lower-case version of the input value will be returned.
 * For example,
 * <code>toLegacyType("Foo", "Bar")</code> returns "bar",
 * <code>toLegacyType("vt", "00A4")</code> returns "00a4".
 *
 * @param keyword       the locale keyword (either legacy keyword such as
 *                      "collation" or BCP 47 Unicode locale extension
 *                      key such as "co").
 * @param value         the locale keyword value (either BCP 47 Unicode locale
 *                      extension type such as "phonebk" or legacy keyword value
 *                      such as "phonebook").
 * @return              the well-formed legacy type, or null if the specified
 *                      keyword value cannot be mapped to a well-formed legacy
 *                      type.
 * @see #toUnicodeLocaleType(String, String)
 */

public static java.lang.String toLegacyType(java.lang.String keyword, java.lang.String value) { throw new RuntimeException("Stub!"); }

/**
 * Useful constant for country/region.
 */

public static final android.icu.util.ULocale CANADA;
static { CANADA = null; }

/**
 * Useful constant for country/region.
 */

public static final android.icu.util.ULocale CANADA_FRENCH;
static { CANADA_FRENCH = null; }

/**
 * Useful constant for country/region.
 */

public static final android.icu.util.ULocale CHINA;
static { CHINA = null; }

/**
 * Useful constant for language.
 */

public static final android.icu.util.ULocale CHINESE;
static { CHINESE = null; }

/**
 * Useful constant for language.
 */

public static final android.icu.util.ULocale ENGLISH;
static { ENGLISH = null; }

/**
 * Useful constant for country/region.
 */

public static final android.icu.util.ULocale FRANCE;
static { FRANCE = null; }

/**
 * Useful constant for language.
 */

public static final android.icu.util.ULocale FRENCH;
static { FRENCH = null; }

/**
 * Useful constant for language.
 */

public static final android.icu.util.ULocale GERMAN;
static { GERMAN = null; }

/**
 * Useful constant for country/region.
 */

public static final android.icu.util.ULocale GERMANY;
static { GERMANY = null; }

/**
 * Useful constant for language.
 */

public static final android.icu.util.ULocale ITALIAN;
static { ITALIAN = null; }

/**
 * Useful constant for country/region.
 */

public static final android.icu.util.ULocale ITALY;
static { ITALY = null; }

/**
 * Useful constant for country/region.
 */

public static final android.icu.util.ULocale JAPAN;
static { JAPAN = null; }

/**
 * Useful constant for language.
 */

public static final android.icu.util.ULocale JAPANESE;
static { JAPANESE = null; }

/**
 * Useful constant for country/region.
 */

public static final android.icu.util.ULocale KOREA;
static { KOREA = null; }

/**
 * Useful constant for language.
 */

public static final android.icu.util.ULocale KOREAN;
static { KOREAN = null; }

/**
 * Useful constant for country/region.
 */

public static final android.icu.util.ULocale PRC;
static { PRC = null; }

/**
 * The key for the private use locale extension ('x').
 *
 * @see #getExtension(char)
 * @see android.icu.util.ULocale.Builder#setExtension(char, String)
 */

public static final char PRIVATE_USE_EXTENSION = 120; // 0x0078 'x'

/**
 * The root ULocale.
 */

public static final android.icu.util.ULocale ROOT;
static { ROOT = null; }

/**
 * Useful constant for language.
 */

public static final android.icu.util.ULocale SIMPLIFIED_CHINESE;
static { SIMPLIFIED_CHINESE = null; }

/**
 * Useful constant for country/region.
 */

public static final android.icu.util.ULocale TAIWAN;
static { TAIWAN = null; }

/**
 * Useful constant for language.
 */

public static final android.icu.util.ULocale TRADITIONAL_CHINESE;
static { TRADITIONAL_CHINESE = null; }

/**
 * Useful constant for country/region.
 */

public static final android.icu.util.ULocale UK;
static { UK = null; }

/**
 * The key for Unicode locale extension ('u').
 *
 * @see #getExtension(char)
 * @see android.icu.util.ULocale.Builder#setExtension(char, String)
 */

public static final char UNICODE_LOCALE_EXTENSION = 117; // 0x0075 'u'

/**
 * Useful constant for country/region.
 */

public static final android.icu.util.ULocale US;
static { US = null; }
/**
 * Types for {@link android.icu.util.ULocale#getAvailableLocalesByType ULocale#getAvailableLocalesByType}
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public enum AvailableType {
/**
 * Locales that return data when passed to ICU APIs,
 * but not including legacy or alias locales.
 */

DEFAULT,
/**
 * Legacy or alias locales that return data when passed to ICU APIs.
 * Examples of supported legacy or alias locales:
 *
 * <ul>
 * <li>iw (alias to he)
 * <li>mo (alias to ro)
 * <li>zh_CN (alias to zh_Hans_CN)
 * <li>sr_BA (alias to sr_Cyrl_BA)
 * <li>ars (alias to ar_SA)
 * </ul>
 *
 * The locales in this set are disjoint from the ones in
 * DEFAULT. To get both sets at the same time, use
 * WITH_LEGACY_ALIASES.
 */

ONLY_LEGACY_ALIASES,
/**
 * The union of the locales in DEFAULT and ONLY_LEGACY_ALIASES.
 */

WITH_LEGACY_ALIASES;
}

/**
 * <code>Builder</code> is used to build instances of <code>ULocale</code>
 * from values configured by the setters.  Unlike the <code>ULocale</code>
 * constructors, the <code>Builder</code> checks if a value configured by a
 * setter satisfies the syntax requirements defined by the <code>ULocale</code>
 * class.  A <code>ULocale</code> object created by a <code>Builder</code> is
 * well-formed and can be transformed to a well-formed IETF BCP 47 language tag
 * without losing information.
 *
 * <p><b>Note:</b> The <code>ULocale</code> class does not provide any
 * syntactic restrictions on variant, while BCP 47 requires each variant
 * subtag to be 5 to 8 alphanumerics or a single numeric followed by 3
 * alphanumerics.  The method <code>setVariant</code> throws
 * <code>IllformedLocaleException</code> for a variant that does not satisfy
 * this restriction. If it is necessary to support such a variant, use a
 * ULocale constructor.  However, keep in mind that a <code>ULocale</code>
 * object created this way might lose the variant information when
 * transformed to a BCP 47 language tag.
 *
 * <p>The following example shows how to create a <code>Locale</code> object
 * with the <code>Builder</code>.
 * <blockquote>
 * <pre>
 *     ULocale aLocale = new Builder().setLanguage("sr").setScript("Latn").setRegion("RS").build();
 * </pre>
 * </blockquote>
 *
 * <p>Builders can be reused; <code>clear()</code> resets all
 * fields to their default values.
 *
 * @see android.icu.util.ULocale#toLanguageTag()
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public static final class Builder {

/**
 * Constructs an empty Builder. The default value of all
 * fields, extensions, and private use information is the
 * empty string.
 */

public Builder() { throw new RuntimeException("Stub!"); }

/**
 * Resets the <code>Builder</code> to match the provided
 * <code>locale</code>.  Existing state is discarded.
 *
 * <p>All fields of the locale must be well-formed, see {@link java.util.Locale Locale}.
 *
 * <p>Locales with any ill-formed fields cause
 * <code>IllformedLocaleException</code> to be thrown.
 *
 * @param locale the locale
 * @return This builder.
 * @throws android.icu.util.IllformedLocaleException if <code>locale</code> has
 * any ill-formed fields.
 * @throws java.lang.NullPointerException if <code>locale</code> is null.
 */

public android.icu.util.ULocale.Builder setLocale(android.icu.util.ULocale locale) { throw new RuntimeException("Stub!"); }

/**
 * Resets the Builder to match the provided IETF BCP 47
 * language tag.  Discards the existing state.  Null and the
 * empty string cause the builder to be reset, like {@link
 * #clear}.  Legacy tags (see {@link android.icu.util.ULocale#forLanguageTag  }) are converted to their canonical
 * form before being processed.  Otherwise, the language tag
 * must be well-formed (see {@link android.icu.util.ULocale ULocale}) or an exception is
 * thrown (unlike <code>ULocale.forLanguageTag</code>, which
 * just discards ill-formed and following portions of the
 * tag).
 *
 * @param languageTag the language tag
 * @return This builder.
 * @throws android.icu.util.IllformedLocaleException if <code>languageTag</code> is ill-formed
 * @see android.icu.util.ULocale#forLanguageTag(String)
 */

public android.icu.util.ULocale.Builder setLanguageTag(java.lang.String languageTag) { throw new RuntimeException("Stub!"); }

/**
 * Sets the language.  If <code>language</code> is the empty string or
 * null, the language in this <code>Builder</code> is removed.  Otherwise,
 * the language must be <a href="./Locale.html#def_language">well-formed</a>
 * or an exception is thrown.
 *
 * <p>The typical language value is a two or three-letter language
 * code as defined in ISO639.
 *
 * @param language the language
 * @return This builder.
 * @throws android.icu.util.IllformedLocaleException if <code>language</code> is ill-formed
 */

public android.icu.util.ULocale.Builder setLanguage(java.lang.String language) { throw new RuntimeException("Stub!"); }

/**
 * Sets the script. If <code>script</code> is null or the empty string,
 * the script in this <code>Builder</code> is removed.
 * Otherwise, the script must be well-formed or an exception is thrown.
 *
 * <p>The typical script value is a four-letter script code as defined by ISO 15924.
 *
 * @param script the script
 * @return This builder.
 * @throws android.icu.util.IllformedLocaleException if <code>script</code> is ill-formed
 */

public android.icu.util.ULocale.Builder setScript(java.lang.String script) { throw new RuntimeException("Stub!"); }

/**
 * Sets the region.  If region is null or the empty string, the region
 * in this <code>Builder</code> is removed.  Otherwise,
 * the region must be well-formed or an exception is thrown.
 *
 * <p>The typical region value is a two-letter ISO 3166 code or a
 * three-digit UN M.49 area code.
 *
 * <p>The country value in the <code>Locale</code> created by the
 * <code>Builder</code> is always normalized to upper case.
 *
 * @param region the region
 * @return This builder.
 * @throws android.icu.util.IllformedLocaleException if <code>region</code> is ill-formed
 */

public android.icu.util.ULocale.Builder setRegion(java.lang.String region) { throw new RuntimeException("Stub!"); }

/**
 * Sets the variant.  If variant is null or the empty string, the
 * variant in this <code>Builder</code> is removed.  Otherwise, it
 * must consist of one or more well-formed subtags, or an exception is thrown.
 *
 * <p><b>Note:</b> This method checks if <code>variant</code>
 * satisfies the IETF BCP 47 variant subtag's syntax requirements,
 * and normalizes the value to lowercase letters.  However,
 * the <code>ULocale</code> class does not impose any syntactic
 * restriction on variant.  To set such a variant,
 * use a ULocale constructor.
 *
 * @param variant the variant
 * @return This builder.
 * @throws android.icu.util.IllformedLocaleException if <code>variant</code> is ill-formed
 */

public android.icu.util.ULocale.Builder setVariant(java.lang.String variant) { throw new RuntimeException("Stub!"); }

/**
 * Sets the extension for the given key. If the value is null or the
 * empty string, the extension is removed.  Otherwise, the extension
 * must be well-formed or an exception is thrown.
 *
 * <p><b>Note:</b> The key {@link android.icu.util.ULocale#UNICODE_LOCALE_EXTENSION  UNICODE_LOCALE_EXTENSION} ('u') is used for the Unicode locale extension.
 * Setting a value for this key replaces any existing Unicode locale key/type
 * pairs with those defined in the extension.
 *
 * <p><b>Note:</b> The key {@link android.icu.util.ULocale#PRIVATE_USE_EXTENSION  PRIVATE_USE_EXTENSION} ('x') is used for the private use code. To be
 * well-formed, the value for this key needs only to have subtags of one to
 * eight alphanumeric characters, not two to eight as in the general case.
 *
 * @param key the extension key
 * @param value the extension value
 * @return This builder.
 * @throws android.icu.util.IllformedLocaleException if <code>key</code> is illegal
 * or <code>value</code> is ill-formed
 * @see #setUnicodeLocaleKeyword(String, String)
 */

public android.icu.util.ULocale.Builder setExtension(char key, java.lang.String value) { throw new RuntimeException("Stub!"); }

/**
 * Sets the Unicode locale keyword type for the given key.  If the type
 * is null, the Unicode keyword is removed.  Otherwise, the key must be
 * non-null and both key and type must be well-formed or an exception
 * is thrown.
 *
 * <p>Keys and types are converted to lower case.
 *
 * <p><b>Note</b>:Setting the 'u' extension via {@link #setExtension}
 * replaces all Unicode locale keywords with those defined in the
 * extension.
 *
 * @param key the Unicode locale key
 * @param type the Unicode locale type
 * @return This builder.
 * @throws android.icu.util.IllformedLocaleException if <code>key</code> or <code>type</code>
 * is ill-formed
 * @throws java.lang.NullPointerException if <code>key</code> is null
 * @see #setExtension(char, String)
 */

public android.icu.util.ULocale.Builder setUnicodeLocaleKeyword(java.lang.String key, java.lang.String type) { throw new RuntimeException("Stub!"); }

/**
 * Adds a unicode locale attribute, if not already present, otherwise
 * has no effect.  The attribute must not be null and must be well-formed
 * or an exception is thrown.
 *
 * @param attribute the attribute
 * @return This builder.
 * @throws java.lang.NullPointerException if <code>attribute</code> is null
 * @throws android.icu.util.IllformedLocaleException if <code>attribute</code> is ill-formed
 * @see #setExtension(char, String)
 */

public android.icu.util.ULocale.Builder addUnicodeLocaleAttribute(java.lang.String attribute) { throw new RuntimeException("Stub!"); }

/**
 * Removes a unicode locale attribute, if present, otherwise has no
 * effect.  The attribute must not be null and must be well-formed
 * or an exception is thrown.
 *
 * <p>Attribute comparision for removal is case-insensitive.
 *
 * @param attribute the attribute
 * @return This builder.
 * @throws java.lang.NullPointerException if <code>attribute</code> is null
 * @throws android.icu.util.IllformedLocaleException if <code>attribute</code> is ill-formed
 * @see #setExtension(char, String)
 */

public android.icu.util.ULocale.Builder removeUnicodeLocaleAttribute(java.lang.String attribute) { throw new RuntimeException("Stub!"); }

/**
 * Resets the builder to its initial, empty state.
 *
 * @return this builder
 */

public android.icu.util.ULocale.Builder clear() { throw new RuntimeException("Stub!"); }

/**
 * Resets the extensions to their initial, empty state.
 * Language, script, region and variant are unchanged.
 *
 * @return this builder
 * @see #setExtension(char, String)
 */

public android.icu.util.ULocale.Builder clearExtensions() { throw new RuntimeException("Stub!"); }

/**
 * Returns an instance of <code>ULocale</code> created from the fields set
 * on this builder.
 *
 * @return a new Locale
 */

public android.icu.util.ULocale build() { throw new RuntimeException("Stub!"); }
}

/**
 * Enum for locale categories. These locale categories are used to get/set the default locale for
 * the specific functionality represented by the category.
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public enum Category {
/**
 * Category used to represent the default locale for displaying user interfaces.
 */

DISPLAY,
/**
 * Category used to represent the default locale for formatting date, number and/or currency.
 */

FORMAT;
}

}

