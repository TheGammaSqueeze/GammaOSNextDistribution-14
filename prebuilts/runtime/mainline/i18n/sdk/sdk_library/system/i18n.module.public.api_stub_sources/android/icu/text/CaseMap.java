/* GENERATED SOURCE. DO NOT MODIFY. */
// ? 2017 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html

package android.icu.text;

import java.util.Locale;
import android.icu.util.ULocale;
import android.icu.lang.UCharacter;

/**
 * Low-level case mapping options and methods. Immutable.
 * "Setters" return instances with the union of the current and new options set.
 *
 * This class is not intended for public subclassing.
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public abstract class CaseMap {

private CaseMap() { throw new RuntimeException("Stub!"); }

/**
 * @return Lowercasing object with default options.
 */

public static android.icu.text.CaseMap.Lower toLower() { throw new RuntimeException("Stub!"); }

/**
 * @return Uppercasing object with default options.
 */

public static android.icu.text.CaseMap.Upper toUpper() { throw new RuntimeException("Stub!"); }

/**
 * @return Titlecasing object with default options.
 */

public static android.icu.text.CaseMap.Title toTitle() { throw new RuntimeException("Stub!"); }

/**
 * @return Case folding object with default options.
 */

public static android.icu.text.CaseMap.Fold fold() { throw new RuntimeException("Stub!"); }

/**
 * Returns an instance that behaves like this one but
 * omits unchanged text when case-mapping with {@link android.icu.text.Edits Edits}.
 *
 * @return an options object with this option.
 */

public abstract android.icu.text.CaseMap omitUnchangedText();
/**
 * Case folding options and methods. Immutable.
 *
 * @see #fold()
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public static final class Fold extends android.icu.text.CaseMap {

private Fold() { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public android.icu.text.CaseMap.Fold omitUnchangedText() { throw new RuntimeException("Stub!"); }

/**
 * Returns an instance that behaves like this one but
 * handles dotted I and dotless i appropriately for Turkic languages (tr, az).
 *
 * <p>Uses the Unicode CaseFolding.txt mappings marked with 'T' that
 * are to be excluded for default mappings and
 * included for the Turkic-specific mappings.
 *
 * @return an options object with this option.
 * @see android.icu.lang.UCharacter#FOLD_CASE_EXCLUDE_SPECIAL_I
 */

public android.icu.text.CaseMap.Fold turkic() { throw new RuntimeException("Stub!"); }

/**
 * Case-folds a string.
 * The result may be longer or shorter than the original.
 *
 * <p>Case-folding is locale-independent and not context-sensitive,
 * but there is an option for whether to include or exclude mappings for dotted I
 * and dotless i that are marked with 'T' in CaseFolding.txt.
 *
 * @param src       The original string.
 * @return the result string.
 *
 * @see android.icu.lang.UCharacter#foldCase(String, int)
 */

public java.lang.String apply(java.lang.CharSequence src) { throw new RuntimeException("Stub!"); }

/**
 * Case-folds a string and optionally records edits (see {@link #omitUnchangedText}).
 * The result may be longer or shorter than the original.
 *
 * <p>Case-folding is locale-independent and not context-sensitive,
 * but there is an option for whether to include or exclude mappings for dotted I
 * and dotless i that are marked with 'T' in CaseFolding.txt.
 *
 * @param src       The original string.
 * @param dest      A buffer for the result string. Must not be null.
 * @param edits     Records edits for index mapping, working with styled text,
 *                  and getting only changes (if any).
 *                  This function calls edits.reset() first. edits can be null.
 * @return dest with the result string (or only changes) appended.
 *
 * @see android.icu.lang.UCharacter#foldCase(String, int)
 */

public <A extends java.lang.Appendable> A apply(java.lang.CharSequence src, A dest, android.icu.text.Edits edits) { throw new RuntimeException("Stub!"); }
}

/**
 * Lowercasing options and methods. Immutable.
 *
 * @see #toLower()
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public static final class Lower extends android.icu.text.CaseMap {

private Lower() { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public android.icu.text.CaseMap.Lower omitUnchangedText() { throw new RuntimeException("Stub!"); }

/**
 * Lowercases a string.
 * Casing is locale-dependent and context-sensitive.
 * The result may be longer or shorter than the original.
 *
 * @param locale    The locale ID. Can be null for {@link java.util.Locale#getDefault Locale#getDefault}.
 *                  (See {@link android.icu.util.ULocale#toLocale ULocale#toLocale}.)
 * @param src       The original string.
 * @return the result string.
 *
 * @see android.icu.lang.UCharacter#toLowerCase(Locale, String)
 */

public java.lang.String apply(java.util.Locale locale, java.lang.CharSequence src) { throw new RuntimeException("Stub!"); }

/**
 * Lowercases a string and optionally records edits (see {@link #omitUnchangedText}).
 * Casing is locale-dependent and context-sensitive.
 * The result may be longer or shorter than the original.
 *
 * @param locale    The locale ID. Can be null for {@link java.util.Locale#getDefault Locale#getDefault}.
 *                  (See {@link android.icu.util.ULocale#toLocale ULocale#toLocale}.)
 * @param src       The original string.
 * @param dest      A buffer for the result string. Must not be null.
 * @param edits     Records edits for index mapping, working with styled text,
 *                  and getting only changes (if any).
 *                  This function calls edits.reset() first. edits can be null.
 * @return dest with the result string (or only changes) appended.
 *
 * @see android.icu.lang.UCharacter#toLowerCase(Locale, String)
 */

public <A extends java.lang.Appendable> A apply(java.util.Locale locale, java.lang.CharSequence src, A dest, android.icu.text.Edits edits) { throw new RuntimeException("Stub!"); }
}

/**
 * Titlecasing options and methods. Immutable.
 *
 * @see #toTitle()
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public static final class Title extends android.icu.text.CaseMap {

private Title() { throw new RuntimeException("Stub!"); }

/**
 * Returns an instance that behaves like this one but
 * titlecases the string as a whole rather than each word.
 * (Titlecases only the character at index 0, possibly adjusted.)
 *
 * <p>It is an error to specify multiple titlecasing iterator options together,
 * including both an option and an explicit BreakIterator.
 *
 * @return an options object with this option.
 * @see #adjustToCased()
 */

public android.icu.text.CaseMap.Title wholeString() { throw new RuntimeException("Stub!"); }

/**
 * Returns an instance that behaves like this one but
 * titlecases sentences rather than words.
 * (Titlecases only the first character of each sentence, possibly adjusted.)
 *
 * <p>It is an error to specify multiple titlecasing iterator options together,
 * including both an option and an explicit BreakIterator.
 *
 * @return an options object with this option.
 * @see #adjustToCased()
 */

public android.icu.text.CaseMap.Title sentences() { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public android.icu.text.CaseMap.Title omitUnchangedText() { throw new RuntimeException("Stub!"); }

/**
 * Returns an instance that behaves like this one but
 * does not lowercase non-initial parts of words when titlecasing.
 *
 * <p>By default, titlecasing will titlecase the character at each
 * (possibly adjusted) BreakIterator index and
 * lowercase all other characters up to the next iterator index.
 * With this option, the other characters will not be modified.
 *
 * @return an options object with this option.
 * @see android.icu.lang.UCharacter#TITLECASE_NO_LOWERCASE
 * @see #adjustToCased()
 */

public android.icu.text.CaseMap.Title noLowercase() { throw new RuntimeException("Stub!"); }

/**
 * Returns an instance that behaves like this one but
 * does not adjust the titlecasing BreakIterator indexes;
 * titlecases exactly the characters at breaks from the iterator.
 *
 * <p>By default, titlecasing will take each break iterator index,
 * adjust it to the next relevant character (see {@link #adjustToCased()}),
 * and titlecase that one.
 *
 * <p>Other characters are lowercased.
 *
 * @return an options object with this option.
 * @see android.icu.lang.UCharacter#TITLECASE_NO_BREAK_ADJUSTMENT
 */

public android.icu.text.CaseMap.Title noBreakAdjustment() { throw new RuntimeException("Stub!"); }

/**
 * Returns an instance that behaves like this one but
 * adjusts each titlecasing BreakIterator index to the next cased character.
 * (See the Unicode Standard, chapter 3, Default Case Conversion, R3 toTitlecase(X).)
 *
 * <p>This used to be the default index adjustment in ICU.
 * Since ICU 60, the default index adjustment is to the next character that is
 * a letter, number, symbol, or private use code point.
 * (Uncased modifier letters are skipped.)
 * The difference in behavior is small for word titlecasing,
 * but the new adjustment is much better for whole-string and sentence titlecasing:
 * It yields "49ers" and "??(abc)?" instead of "49Ers" and "??(Abc)?".
 *
 * <p>It is an error to specify multiple titlecasing adjustment options together.
 *
 * @return an options object with this option.
 * @see #noBreakAdjustment()
 */

public android.icu.text.CaseMap.Title adjustToCased() { throw new RuntimeException("Stub!"); }

/**
 * Titlecases a string.
 * Casing is locale-dependent and context-sensitive.
 * The result may be longer or shorter than the original.
 *
 * <p>Titlecasing uses a break iterator to find the first characters of words
 * that are to be titlecased. It titlecases those characters and lowercases
 * all others. (This can be modified with options bits.)
 *
 * @param locale    The locale ID. Can be null for {@link java.util.Locale#getDefault Locale#getDefault}.
 *                  (See {@link android.icu.util.ULocale#toLocale ULocale#toLocale}.)
 * @param iter      A break iterator to find the first characters of words that are to be titlecased.
 *                  It is set to the source string (setText())
 *                  and used one or more times for iteration (first() and next()).
 *                  If null, then a word break iterator for the locale is used
 *                  (or something equivalent).
 * @param src       The original string.
 * @return the result string.
 *
 * @see android.icu.lang.UCharacter#toUpperCase(Locale, String)
 */

public java.lang.String apply(java.util.Locale locale, android.icu.text.BreakIterator iter, java.lang.CharSequence src) { throw new RuntimeException("Stub!"); }

/**
 * Titlecases a string and optionally records edits (see {@link #omitUnchangedText}).
 * Casing is locale-dependent and context-sensitive.
 * The result may be longer or shorter than the original.
 *
 * <p>Titlecasing uses a break iterator to find the first characters of words
 * that are to be titlecased. It titlecases those characters and lowercases
 * all others. (This can be modified with options bits.)
 *
 * @param locale    The locale ID. Can be null for {@link java.util.Locale#getDefault Locale#getDefault}.
 *                  (See {@link android.icu.util.ULocale#toLocale ULocale#toLocale}.)
 * @param iter      A break iterator to find the first characters of words that are to be titlecased.
 *                  It is set to the source string (setText())
 *                  and used one or more times for iteration (first() and next()).
 *                  If null, then a word break iterator for the locale is used
 *                  (or something equivalent).
 * @param src       The original string.
 * @param dest      A buffer for the result string. Must not be null.
 * @param edits     Records edits for index mapping, working with styled text,
 *                  and getting only changes (if any).
 *                  This function calls edits.reset() first. edits can be null.
 * @return dest with the result string (or only changes) appended.
 *
 * @see android.icu.lang.UCharacter#toTitleCase(Locale, String, BreakIterator, int)
 */

public <A extends java.lang.Appendable> A apply(java.util.Locale locale, android.icu.text.BreakIterator iter, java.lang.CharSequence src, A dest, android.icu.text.Edits edits) { throw new RuntimeException("Stub!"); }
}

/**
 * Uppercasing options and methods. Immutable.
 *
 * @see #toUpper()
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public static final class Upper extends android.icu.text.CaseMap {

private Upper() { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public android.icu.text.CaseMap.Upper omitUnchangedText() { throw new RuntimeException("Stub!"); }

/**
 * Uppercases a string.
 * Casing is locale-dependent and context-sensitive.
 * The result may be longer or shorter than the original.
 *
 * @param locale    The locale ID. Can be null for {@link java.util.Locale#getDefault Locale#getDefault}.
 *                  (See {@link android.icu.util.ULocale#toLocale ULocale#toLocale}.)
 * @param src       The original string.
 * @return the result string.
 *
 * @see android.icu.lang.UCharacter#toUpperCase(Locale, String)
 */

public java.lang.String apply(java.util.Locale locale, java.lang.CharSequence src) { throw new RuntimeException("Stub!"); }

/**
 * Uppercases a string and optionally records edits (see {@link #omitUnchangedText}).
 * Casing is locale-dependent and context-sensitive.
 * The result may be longer or shorter than the original.
 *
 * @param locale    The locale ID. Can be null for {@link java.util.Locale#getDefault Locale#getDefault}.
 *                  (See {@link android.icu.util.ULocale#toLocale ULocale#toLocale}.)
 * @param src       The original string.
 * @param dest      A buffer for the result string. Must not be null.
 * @param edits     Records edits for index mapping, working with styled text,
 *                  and getting only changes (if any).
 *                  This function calls edits.reset() first. edits can be null.
 * @return dest with the result string (or only changes) appended.
 *
 * @see android.icu.lang.UCharacter#toUpperCase(Locale, String)
 */

public <A extends java.lang.Appendable> A apply(java.util.Locale locale, java.lang.CharSequence src, A dest, android.icu.text.Edits edits) { throw new RuntimeException("Stub!"); }
}

}

