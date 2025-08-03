/* GENERATED SOURCE. DO NOT MODIFY. */
// ? 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 *******************************************************************************
 * Copyright (C) 2009-2016, International Business Machines Corporation and    *
 * others. All Rights Reserved.                                                *
 *******************************************************************************
 */

package android.icu.text;

import android.icu.util.ULocale;
import java.util.Locale;
import android.icu.text.DisplayContext.Type;
import android.icu.lang.UScript;
import java.util.Set;
import android.icu.util.IllformedLocaleException;

/**
 * Returns display names of ULocales and components of ULocales. For
 * more information on language, script, region, variant, key, and
 * values, see {@link android.icu.util.ULocale}.
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public abstract class LocaleDisplayNames {

LocaleDisplayNames() { throw new RuntimeException("Stub!"); }

/**
 * Convenience overload of {@link #getInstance(android.icu.util.ULocale,android.icu.text.LocaleDisplayNames.DialectHandling)} that specifies
 * STANDARD dialect handling.
 * @param locale the display locale
 * @return a LocaleDisplayNames instance
 */

public static android.icu.text.LocaleDisplayNames getInstance(android.icu.util.ULocale locale) { throw new RuntimeException("Stub!"); }

/**
 * Convenience overload of {@link #getInstance(java.util.Locale,android.icu.text.DisplayContext...)} that specifies
 * {@link android.icu.text.DisplayContext#STANDARD_NAMES DisplayContext#STANDARD_NAMES}.
 * @param locale the display {@link java.util.Locale}
 * @return a LocaleDisplayNames instance
 */

public static android.icu.text.LocaleDisplayNames getInstance(java.util.Locale locale) { throw new RuntimeException("Stub!"); }

/**
 * Returns an instance of LocaleDisplayNames that returns names formatted for the provided locale,
 * using the provided dialectHandling.
 * @param locale the display locale
 * @param dialectHandling how to select names for locales
 * @return a LocaleDisplayNames instance
 */

public static android.icu.text.LocaleDisplayNames getInstance(android.icu.util.ULocale locale, android.icu.text.LocaleDisplayNames.DialectHandling dialectHandling) { throw new RuntimeException("Stub!"); }

/**
 * Returns an instance of LocaleDisplayNames that returns names formatted for the provided locale,
 * using the provided DisplayContext settings
 * @param locale the display locale
 * @param contexts one or more context settings (e.g. for dialect
 *              handling, capitalization, etc.
 * @return a LocaleDisplayNames instance
 */

public static android.icu.text.LocaleDisplayNames getInstance(android.icu.util.ULocale locale, android.icu.text.DisplayContext... contexts) { throw new RuntimeException("Stub!"); }

/**
 * Returns an instance of LocaleDisplayNames that returns names formatted for the provided
 * {@link java.util.Locale}, using the provided DisplayContext settings
 * @param locale the display {@link java.util.Locale}
 * @param contexts one or more context settings (e.g. for dialect
 *              handling, capitalization, etc.
 * @return a LocaleDisplayNames instance
 */

public static android.icu.text.LocaleDisplayNames getInstance(java.util.Locale locale, android.icu.text.DisplayContext... contexts) { throw new RuntimeException("Stub!"); }

/**
 * Returns the locale used to determine the display names. This is not necessarily the same
 * locale passed to {@link #getInstance}.
 * @return the display locale
 */

public abstract android.icu.util.ULocale getLocale();

/**
 * Returns the dialect handling used in the display names.
 * @return the dialect handling enum
 */

public abstract android.icu.text.LocaleDisplayNames.DialectHandling getDialectHandling();

/**
 * Returns the current value for a specified DisplayContext.Type.
 * @param type the DisplayContext.Type whose value to return
 * @return the current DisplayContext setting for the specified type
 */

public abstract android.icu.text.DisplayContext getContext(android.icu.text.DisplayContext.Type type);

/**
 * Returns the display name of the provided ulocale.
 * When no display names are available for all or portions
 * of the original locale ID, those portions may be
 * used directly (possibly in a more canonical form) as
 * part of the  returned display name.
 * @param locale the locale whose display name to return
 * @return the display name of the provided locale
 */

public abstract java.lang.String localeDisplayName(android.icu.util.ULocale locale);

/**
 * Returns the display name of the provided locale.
 * When no display names are available for all or portions
 * of the original locale ID, those portions may be
 * used directly (possibly in a more canonical form) as
 * part of the  returned display name.
 * @param locale the locale whose display name to return
 * @return the display name of the provided locale
 */

public abstract java.lang.String localeDisplayName(java.util.Locale locale);

/**
 * Returns the display name of the provided locale id.
 * When no display names are available for all or portions
 * of the original locale ID, those portions may be
 * used directly (possibly in a more canonical form) as
 * part of the  returned display name.
 * @param localeId the id of the locale whose display name to return
 * @return the display name of the provided locale
 */

public abstract java.lang.String localeDisplayName(java.lang.String localeId);

/**
 * Returns the display name of the provided language code.
 * @param lang the language code
 * @return the display name of the provided language code
 */

public abstract java.lang.String languageDisplayName(java.lang.String lang);

/**
 * Returns the display name of the provided script code.
 * @param script the script code
 * @return the display name of the provided script code
 */

public abstract java.lang.String scriptDisplayName(java.lang.String script);

/**
 * Returns the display name of the provided script code.  See
 * {@link android.icu.lang.UScript} for recognized script codes.
 * @param scriptCode the script code number
 * @return the display name of the provided script code
 */

public abstract java.lang.String scriptDisplayName(int scriptCode);

/**
 * Returns the display name of the provided region code.
 * @param region the region code
 * @return the display name of the provided region code
 */

public abstract java.lang.String regionDisplayName(java.lang.String region);

/**
 * Returns the display name of the provided variant.
 * @param variant the variant string
 * @return the display name of the provided variant
 */

public abstract java.lang.String variantDisplayName(java.lang.String variant);

/**
 * Returns the display name of the provided locale key.
 * @param key the locale key name
 * @return the display name of the provided locale key
 */

public abstract java.lang.String keyDisplayName(java.lang.String key);

/**
 * Returns the display name of the provided value (used with the provided key).
 * @param key the locale key name
 * @param value the locale key's value
 * @return the display name of the provided value
 */

public abstract java.lang.String keyValueDisplayName(java.lang.String key, java.lang.String value);

/**
 * Return a list of information used to construct a UI list of locale names.
 * @param collator how to collate?should normally be Collator.getInstance(getDisplayLocale())
 * @param inSelf if true, compares the nameInSelf, otherwise the nameInDisplayLocale.
 * Set depending on which field (displayLocale vs self) is to show up in the UI.
 * If both are to show up in the UI, then it should be the one used for the primary sort order.
 * @param localeSet a list of locales to present in a UI list. The casing uses the settings in the LocaleDisplayNames instance.
 * @return an ordered list of UiListItems.
 * @throws android.icu.util.IllformedLocaleException if any of the locales in localeSet are malformed.
 */

public java.util.List<android.icu.text.LocaleDisplayNames.UiListItem> getUiList(java.util.Set<android.icu.util.ULocale> localeSet, boolean inSelf, java.util.Comparator<java.lang.Object> collator) { throw new RuntimeException("Stub!"); }

/**
 * Return a list of information used to construct a UI list of locale names, providing more access to control the sorting.
 * Normally use getUiList instead.
 * @param comparator how to sort the UiListItems in the result.
 * @param localeSet a list of locales to present in a UI list. The casing uses the settings in the LocaleDisplayNames instance.
 * @return an ordered list of UiListItems.
 * @throws android.icu.util.IllformedLocaleException if any of the locales in localeSet are malformed.
 */

public abstract java.util.List<android.icu.text.LocaleDisplayNames.UiListItem> getUiListCompareWholeItems(java.util.Set<android.icu.util.ULocale> localeSet, java.util.Comparator<android.icu.text.LocaleDisplayNames.UiListItem> comparator);
/**
 * Enum used in {@link #getInstance(android.icu.util.ULocale,android.icu.text.LocaleDisplayNames.DialectHandling)}.
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public enum DialectHandling {
/**
 * Use standard names when generating a locale name,
 * e.g. en_GB displays as 'English (United Kingdom)'.
 */

STANDARD_NAMES,
/**
 * Use dialect names when generating a locale name,
 * e.g. en_GB displays as 'British English'.
 */

DIALECT_NAMES;
}

/**
 * Struct-like class used to return information for constructing a UI list, each corresponding to a locale.
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public static class UiListItem {

/**
 * Constructor, normally only called internally.
 * @param minimized locale for an input locale
 * @param modified modified for an input locale
 * @param nameInDisplayLocale name of the modified locale in the display locale
 * @param nameInSelf name of the modified locale in itself
 */

public UiListItem(android.icu.util.ULocale minimized, android.icu.util.ULocale modified, java.lang.String nameInDisplayLocale, java.lang.String nameInSelf) { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public boolean equals(java.lang.Object obj) { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public int hashCode() { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public java.lang.String toString() { throw new RuntimeException("Stub!"); }

/**
 * Return a comparator that compares the locale names for the display locale or the in-self names,
 * depending on an input parameter.
 * @param inSelf if true, compares the nameInSelf, otherwise the nameInDisplayLocale
 * @param comparator (meant for strings, but because Java Collator doesn't have &lt;String&gt;...)
 * @return UiListItem comparator
 */

public static java.util.Comparator<android.icu.text.LocaleDisplayNames.UiListItem> getComparator(java.util.Comparator<java.lang.Object> comparator, boolean inSelf) { throw new RuntimeException("Stub!"); }

/**
 * Returns the minimized locale for an input locale, such as sr-Cyrl ? sr
 */

public final android.icu.util.ULocale minimized;
{ minimized = null; }

/**
 * Returns the modified locale for an input locale, such as sr ? sr-Cyrl, where there is also an sr-Latn in the list
 */

public final android.icu.util.ULocale modified;
{ modified = null; }

/**
 * Returns the name of the modified locale in the display locale, such as "Englisch (VS)" (for 'en-US', where the display locale is 'de').
 */

public final java.lang.String nameInDisplayLocale;
{ nameInDisplayLocale = null; }

/**
 * Returns the name of the modified locale in itself, such as "English (US)" (for 'en-US').
 */

public final java.lang.String nameInSelf;
{ nameInSelf = null; }
}

}

