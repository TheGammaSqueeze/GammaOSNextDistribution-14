/* GENERATED SOURCE. DO NOT MODIFY. */
// ? 2018 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html

package android.icu.number;

import java.util.Locale;
import android.icu.util.ULocale;

/**
 * A NumberRangeFormatter that does not yet have a locale. In order to format, a locale must be specified.
 *
 * Instances of this class are immutable and thread-safe.
 *
 * @author sffc
 * @see android.icu.number.NumberRangeFormatter
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public class UnlocalizedNumberRangeFormatter extends android.icu.number.NumberRangeFormatterSettings<android.icu.number.UnlocalizedNumberRangeFormatter> {

UnlocalizedNumberRangeFormatter() { throw new RuntimeException("Stub!"); }

/**
 * Associate the given locale with the number range formatter. The locale is used for picking the
 * appropriate symbols, formats, and other data for number display.
 *
 * <p>
 * To use the Java default locale, call Locale.getDefault():
 *
 * <pre>
 * NumberFormatter.with(). ... .locale(Locale.getDefault())
 * </pre>
 *
 * @param locale
 *            The locale to use when loading data for number range formatting.
 * @return The fluent chain
 */

public android.icu.number.LocalizedNumberRangeFormatter locale(java.util.Locale locale) { throw new RuntimeException("Stub!"); }

/**
 * ULocale version of the {@link #locale(java.util.Locale)} setter above.
 *
 * @param locale
 *            The locale to use when loading data for number range formatting.
 * @return The fluent chain
 * @see #locale(Locale)
 */

public android.icu.number.LocalizedNumberRangeFormatter locale(android.icu.util.ULocale locale) { throw new RuntimeException("Stub!"); }
}

