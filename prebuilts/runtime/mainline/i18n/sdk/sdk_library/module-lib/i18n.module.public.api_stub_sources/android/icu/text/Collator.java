/* GENERATED SOURCE. DO NOT MODIFY. */
// ? 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/**
*******************************************************************************
* Copyright (C) 1996-2016, International Business Machines Corporation and
* others. All Rights Reserved.
*******************************************************************************
*/

package android.icu.text;

import java.util.Locale;
import android.icu.util.Freezable;
import android.icu.lang.UScript;
import android.icu.util.ULocale;
import android.icu.util.ULocale.Category;

/**
 * <strong>[icu enhancement]</strong> ICU's replacement for {@link java.text.Collator}.&nbsp;Methods, fields, and other functionality specific to ICU are labeled '<strong>[icu]</strong>'.
 *
 * <p>Collator performs locale-sensitive string comparison. A concrete
 * subclass, RuleBasedCollator, allows customization of the collation
 * ordering by the use of rule sets.
 *
 * <p>A Collator is thread-safe only when frozen. See {@link #isFrozen()} and {@link android.icu.util.Freezable Freezable}.
 *
 * <p>Following the <a href=http://www.unicode.org>Unicode
 * Consortium</a>'s specifications for the
 * <a href="http://www.unicode.org/unicode/reports/tr10/">Unicode Collation
 * Algorithm (UCA)</a>, there are 5 different levels of strength used
 * in comparisons:
 *
 * <ul>
 * <li>PRIMARY strength: Typically, this is used to denote differences between
 *     base characters (for example, "a" &lt; "b").
 *     It is the strongest difference. For example, dictionaries are divided
 *     into different sections by base character.
 * <li>SECONDARY strength: Accents in the characters are considered secondary
 *     differences (for example, "as" &lt; "&agrave;s" &lt; "at"). Other
 *     differences
 *     between letters can also be considered secondary differences, depending
 *     on the language. A secondary difference is ignored when there is a
 *     primary difference anywhere in the strings.
 * <li>TERTIARY strength: Upper and lower case differences in characters are
 *     distinguished at tertiary strength (for example, "ao" &lt; "Ao" &lt;
 *     "a&ograve;"). In addition, a variant of a letter differs from the base
 *     form on the tertiary strength (such as "A" and "?"). Another
 *     example is the
 *     difference between large and small Kana. A tertiary difference is ignored
 *     when there is a primary or secondary difference anywhere in the strings.
 * <li>QUATERNARY strength: When punctuation is ignored
 *     (see <a href="https://unicode-org.github.io/icu/userguide/collation/concepts#ignoring-punctuation">
 *     Ignoring Punctuations in the User Guide</a>) at PRIMARY to TERTIARY
 *     strength, an additional strength level can
 *     be used to distinguish words with and without punctuation (for example,
 *     "ab" &lt; "a-b" &lt; "aB").
 *     This difference is ignored when there is a PRIMARY, SECONDARY or TERTIARY
 *     difference. The QUATERNARY strength should only be used if ignoring
 *     punctuation is required.
 * <li>IDENTICAL strength:
 *     When all other strengths are equal, the IDENTICAL strength is used as a
 *     tiebreaker. The Unicode code point values of the NFD form of each string
 *     are compared, just in case there is no difference.
 *     For example, Hebrew cantellation marks are only distinguished at this
 *     strength. This strength should be used sparingly, as only code point
 *     value differences between two strings is an extremely rare occurrence.
 *     Using this strength substantially decreases the performance for both
 *     comparison and collation key generation APIs. This strength also
 *     increases the size of the collation key.
 * </ul>
 *
 * Unlike the JDK, ICU4J's Collator deals only with 2 decomposition modes,
 * the canonical decomposition mode and one that does not use any decomposition.
 * The compatibility decomposition mode, java.text.Collator.FULL_DECOMPOSITION
 * is not supported here. If the canonical
 * decomposition mode is set, the Collator handles un-normalized text properly,
 * producing the same results as if the text were normalized in NFD. If
 * canonical decomposition is turned off, it is the user's responsibility to
 * ensure that all text is already in the appropriate form before performing
 * a comparison or before getting a CollationKey.
 *
 * <p>For more information about the collation service see the
 * <a href="https://unicode-org.github.io/icu/userguide/collation">User Guide</a>.
 *
 * <p>Examples of use
 * <pre>
 * // Get the Collator for US English and set its strength to PRIMARY
 * Collator usCollator = Collator.getInstance(Locale.US);
 * usCollator.setStrength(Collator.PRIMARY);
 * if (usCollator.compare("abc", "ABC") == 0) {
 *     System.out.println("Strings are equivalent");
 * }
 *
 * The following example shows how to compare two strings using the
 * Collator for the default locale.
 *
 * // Compare two strings in the default locale
 * Collator myCollator = Collator.getInstance();
 * myCollator.setDecomposition(NO_DECOMPOSITION);
 * if (myCollator.compare("&agrave;&#92;u0325", "a&#92;u0325&#768;") != 0) {
 *     System.out.println("&agrave;&#92;u0325 is not equals to a&#92;u0325&#768; without decomposition");
 *     myCollator.setDecomposition(CANONICAL_DECOMPOSITION);
 *     if (myCollator.compare("&agrave;&#92;u0325", "a&#92;u0325&#768;") != 0) {
 *         System.out.println("Error: &agrave;&#92;u0325 should be equals to a&#92;u0325&#768; with decomposition");
 *     }
 *     else {
 *         System.out.println("&agrave;&#92;u0325 is equals to a&#92;u0325&#768; with decomposition");
 *     }
 * }
 * else {
 *     System.out.println("Error: &agrave;&#92;u0325 should be not equals to a&#92;u0325&#768; without decomposition");
 * }
 * </pre>
 *
 * @see android.icu.text.RuleBasedCollator
 * @see android.icu.text.CollationKey
 * @author Syn Wee Quek
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public abstract class Collator implements java.util.Comparator<java.lang.Object>, android.icu.util.Freezable<android.icu.text.Collator>, java.lang.Cloneable {

/**
 * Empty default constructor to make javadocs happy
 */

protected Collator() { throw new RuntimeException("Stub!"); }

/**
 * Compares the equality of two Collator objects. Collator objects are equal if they have the same
 * collation (sorting &amp; searching) behavior.
 *
 * <p>The base class checks for null and for equal types.
 * Subclasses should override.
 *
 * @param obj the Collator to compare to.
 * @return true if this Collator has exactly the same collation behavior as obj, false otherwise.
 */

public boolean equals(java.lang.Object obj) { throw new RuntimeException("Stub!"); }

/**
 * Generates a hash code for this Collator object.
 *
 * <p>The implementation exists just for consistency with {@link #equals(java.lang.Object)}
 * implementation in this class and does not generate a useful hash code.
 * Subclasses should override this implementation.
 *
 * @return a hash code value.
 */

public int hashCode() { throw new RuntimeException("Stub!"); }

/**
 * Sets this Collator's strength attribute. The strength attribute
 * determines the minimum level of difference considered significant
 * during comparison.
 *
 * <p>The base class method does nothing. Subclasses should override it if appropriate.
 *
 * <p>See the Collator class description for an example of use.
 * @param newStrength the new strength value.
 * @see #getStrength
 * @see #PRIMARY
 * @see #SECONDARY
 * @see #TERTIARY
 * @see #QUATERNARY
 * @see #IDENTICAL
 * @throws java.lang.IllegalArgumentException if the new strength value is not valid.
 */

public void setStrength(int newStrength) { throw new RuntimeException("Stub!"); }

/**
 * Sets the decomposition mode of this Collator.  Setting this
 * decomposition attribute with CANONICAL_DECOMPOSITION allows the
 * Collator to handle un-normalized text properly, producing the
 * same results as if the text were normalized. If
 * NO_DECOMPOSITION is set, it is the user's responsibility to
 * insure that all text is already in the appropriate form before
 * a comparison or before getting a CollationKey. Adjusting
 * decomposition mode allows the user to select between faster and
 * more complete collation behavior.
 *
 * <p>Since a great many of the world's languages do not require
 * text normalization, most locales set NO_DECOMPOSITION as the
 * default decomposition mode.
 *
 * <p>The base class method does nothing. Subclasses should override it if appropriate.
 *
 * <p>See getDecomposition for a description of decomposition
 * mode.
 *
 * @param decomposition the new decomposition mode
 * @see #getDecomposition
 * @see #NO_DECOMPOSITION
 * @see #CANONICAL_DECOMPOSITION
 * @throws java.lang.IllegalArgumentException If the given value is not a valid
 *            decomposition mode.
 */

public void setDecomposition(int decomposition) { throw new RuntimeException("Stub!"); }

/**
 * Sets the reordering codes for this collator.
 * Collation reordering allows scripts and some other groups of characters
 * to be moved relative to each other. This reordering is done on top of
 * the DUCET/CLDR standard collation order. Reordering can specify groups to be placed
 * at the start and/or the end of the collation order. These groups are specified using
 * UScript codes and {@link android.icu.text.Collator.ReorderCodes Collator.ReorderCodes} entries.
 *
 * <p>By default, reordering codes specified for the start of the order are placed in the
 * order given after several special non-script blocks. These special groups of characters
 * are space, punctuation, symbol, currency, and digit. These special groups are represented with
 * {@link android.icu.text.Collator.ReorderCodes Collator.ReorderCodes} entries. Script groups can be intermingled with
 * these special non-script groups if those special groups are explicitly specified in the reordering.
 *
 * <p>The special code {@link android.icu.text.Collator.ReorderCodes#OTHERS OTHERS}
 * stands for any script that is not explicitly
 * mentioned in the list of reordering codes given. Anything that is after OTHERS
 * will go at the very end of the reordering in the order given.
 *
 * <p>The special reorder code {@link android.icu.text.Collator.ReorderCodes#DEFAULT DEFAULT}
 * will reset the reordering for this collator
 * to the default for this collator. The default reordering may be the DUCET/CLDR order or may be a reordering that
 * was specified when this collator was created from resource data or from rules. The
 * DEFAULT code <b>must</b> be the sole code supplied when it is used.
 * If not, then an {@link java.lang.IllegalArgumentException IllegalArgumentException} will be thrown.
 *
 * <p>The special reorder code {@link android.icu.text.Collator.ReorderCodes#NONE NONE}
 * will remove any reordering for this collator.
 * The result of setting no reordering will be to have the DUCET/CLDR ordering used. The
 * NONE code <b>must</b> be the sole code supplied when it is used.
 *
 * @param order the reordering codes to apply to this collator; if this is null or an empty array
 * then this clears any existing reordering
 * @see #getReorderCodes
 * @see #getEquivalentReorderCodes
 * @see android.icu.text.Collator.ReorderCodes
 * @see android.icu.lang.UScript
 */

public void setReorderCodes(int... order) { throw new RuntimeException("Stub!"); }

/**
 * Returns the Collator for the current default locale.
 * The default locale is determined by java.util.Locale.getDefault().
 * @return the Collator for the default locale (for example, en_US) if it
 *         is created successfully. Otherwise if there is no Collator
 *         associated with the current locale, the root collator
 *         will be returned.
 * @see java.util.Locale#getDefault()
 * @see #getInstance(Locale)
 */

public static final android.icu.text.Collator getInstance() { throw new RuntimeException("Stub!"); }

/**
 * Clones the collator.
 * @return a clone of this collator.
 */

public java.lang.Object clone() throws java.lang.CloneNotSupportedException { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns the Collator for the desired locale.
 *
 * <p>For some languages, multiple collation types are available;
 * for example, "de@collation=phonebook".
 * Starting with ICU 54, collation attributes can be specified via locale keywords as well,
 * in the old locale extension syntax ("el@colCaseFirst=upper")
 * or in language tag syntax ("el-u-kf-upper").
 * See <a href="https://unicode-org.github.io/icu/userguide/collation/api">User Guide: Collation API</a>.
 *
 * @param locale the desired locale.
 * @return Collator for the desired locale if it is created successfully.
 *         Otherwise if there is no Collator
 *         associated with the current locale, the root collator will
 *         be returned.
 * @see java.util.Locale
 * @see java.util.ResourceBundle
 * @see #getInstance(Locale)
 * @see #getInstance()
 */

public static final android.icu.text.Collator getInstance(android.icu.util.ULocale locale) { throw new RuntimeException("Stub!"); }

/**
 * Returns the Collator for the desired locale.
 *
 * <p>For some languages, multiple collation types are available;
 * for example, "de-u-co-phonebk".
 * Starting with ICU 54, collation attributes can be specified via locale keywords as well,
 * in the old locale extension syntax ("el@colCaseFirst=upper", only with {@link android.icu.util.ULocale ULocale})
 * or in language tag syntax ("el-u-kf-upper").
 * See <a href="https://unicode-org.github.io/icu/userguide/collation/api">User Guide: Collation API</a>.
 *
 * @param locale the desired locale.
 * @return Collator for the desired locale if it is created successfully.
 *         Otherwise if there is no Collator
 *         associated with the current locale, the root collator will
 *         be returned.
 * @see java.util.Locale
 * @see java.util.ResourceBundle
 * @see #getInstance(ULocale)
 * @see #getInstance()
 */

public static final android.icu.text.Collator getInstance(java.util.Locale locale) { throw new RuntimeException("Stub!"); }

/**
 * Returns the set of locales, as Locale objects, for which collators
 * are installed.  Note that Locale objects do not support RFC 3066.
 * @return the list of locales in which collators are installed.
 * This list includes any that have been registered, in addition to
 * those that are installed with ICU4J.
 */

public static java.util.Locale[] getAvailableLocales() { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns the set of locales, as ULocale objects, for which collators
 * are installed.  ULocale objects support RFC 3066.
 * @return the list of locales in which collators are installed.
 * This list includes any that have been registered, in addition to
 * those that are installed with ICU4J.
 */

public static final android.icu.util.ULocale[] getAvailableULocales() { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns an array of all possible keywords that are relevant to
 * collation. At this point, the only recognized keyword for this
 * service is "collation".
 * @return an array of valid collation keywords.
 * @see #getKeywordValues
 */

public static final java.lang.String[] getKeywords() { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Given a keyword, returns an array of all values for
 * that keyword that are currently in use.
 * @param keyword one of the keywords returned by getKeywords.
 * @see #getKeywords
 */

public static final java.lang.String[] getKeywordValues(java.lang.String keyword) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Given a key and a locale, returns an array of string values in a preferred
 * order that would make a difference. These are all and only those values where
 * the open (creation) of the service with the locale formed from the input locale
 * plus input keyword and that value has different behavior than creation with the
 * input locale alone.
 * @param key           one of the keys supported by this service.  For now, only
 *                      "collation" is supported.
 * @param locale        the locale
 * @param commonlyUsed  if set to true it will return only commonly used values
 *                      with the given locale in preferred order.  Otherwise,
 *                      it will return all the available values for the locale.
 * @return an array of string values for the given key and the locale.
 */

public static final java.lang.String[] getKeywordValuesForLocale(java.lang.String key, android.icu.util.ULocale locale, boolean commonlyUsed) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns the functionally equivalent locale for the given
 * requested locale, with respect to given keyword, for the
 * collation service.  If two locales return the same result, then
 * collators instantiated for these locales will behave
 * equivalently.  The converse is not always true; two collators
 * may in fact be equivalent, but return different results, due to
 * internal details.  The return result has no other meaning than
 * that stated above, and implies nothing as to the relationship
 * between the two locales.  This is intended for use by
 * applications who wish to cache collators, or otherwise reuse
 * collators when possible.  The functional equivalent may change
 * over time.  For more information, please see the <a
 * href="https://unicode-org.github.io/icu/userguide/locale#locales-and-services">
 * Locales and Services</a> section of the ICU User Guide.
 * @param keyword a particular keyword as enumerated by
 * getKeywords.
 * @param locID The requested locale
 * @param isAvailable If non-null, isAvailable[0] will receive and
 * output boolean that indicates whether the requested locale was
 * 'available' to the collation service. If non-null, isAvailable
 * must have length &gt;= 1.
 * @return the locale
 */

public static final android.icu.util.ULocale getFunctionalEquivalent(java.lang.String keyword, android.icu.util.ULocale locID, boolean[] isAvailable) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns the functionally equivalent locale for the given
 * requested locale, with respect to given keyword, for the
 * collation service.
 * @param keyword a particular keyword as enumerated by
 * getKeywords.
 * @param locID The requested locale
 * @return the locale
 * @see #getFunctionalEquivalent(String,ULocale,boolean[])
 */

public static final android.icu.util.ULocale getFunctionalEquivalent(java.lang.String keyword, android.icu.util.ULocale locID) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns the name of the collator for the objectLocale, localized for the
 * displayLocale.
 * @param objectLocale the locale of the collator
 * @param displayLocale the locale for the collator's display name
 * @return the display name
 */

public static java.lang.String getDisplayName(java.util.Locale objectLocale, java.util.Locale displayLocale) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns the name of the collator for the objectLocale, localized for the
 * displayLocale.
 * @param objectLocale the locale of the collator
 * @param displayLocale the locale for the collator's display name
 * @return the display name
 */

public static java.lang.String getDisplayName(android.icu.util.ULocale objectLocale, android.icu.util.ULocale displayLocale) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns the name of the collator for the objectLocale, localized for the
 * default <code>DISPLAY</code> locale.
 * @param objectLocale the locale of the collator
 * @return the display name
 * @see android.icu.util.ULocale.Category#DISPLAY
 */

public static java.lang.String getDisplayName(java.util.Locale objectLocale) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns the name of the collator for the objectLocale, localized for the
 * default <code>DISPLAY</code> locale.
 * @param objectLocale the locale of the collator
 * @return the display name
 * @see android.icu.util.ULocale.Category#DISPLAY
 */

public static java.lang.String getDisplayName(android.icu.util.ULocale objectLocale) { throw new RuntimeException("Stub!"); }

/**
 * Returns this Collator's strength attribute. The strength attribute
 * determines the minimum level of difference considered significant.
 * <strong>[icu] Note:</strong> This can return QUATERNARY strength, which is not supported by the
 * JDK version.
 * <p>
 * See the Collator class description for more details.
 * <p>The base class method always returns {@link #TERTIARY}.
 * Subclasses should override it if appropriate.
 *
 * @return this Collator's current strength attribute.
 * @see #setStrength
 * @see #PRIMARY
 * @see #SECONDARY
 * @see #TERTIARY
 * @see #QUATERNARY
 * @see #IDENTICAL
 */

public int getStrength() { throw new RuntimeException("Stub!"); }

/**
 * Returns the decomposition mode of this Collator. The decomposition mode
 * determines how Unicode composed characters are handled.
 * <p>
 * See the Collator class description for more details.
 * <p>The base class method always returns {@link #NO_DECOMPOSITION}.
 * Subclasses should override it if appropriate.
 *
 * @return the decomposition mode
 * @see #setDecomposition
 * @see #NO_DECOMPOSITION
 * @see #CANONICAL_DECOMPOSITION
 */

public int getDecomposition() { throw new RuntimeException("Stub!"); }

/**
 * Compares the equality of two text Strings using
 * this Collator's rules, strength and decomposition mode.  Convenience method.
 * @param source the source string to be compared.
 * @param target the target string to be compared.
 * @return true if the strings are equal according to the collation
 *         rules, otherwise false.
 * @see #compare
 * @throws java.lang.NullPointerException thrown if either arguments is null.
 */

public boolean equals(java.lang.String source, java.lang.String target) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns a UnicodeSet that contains all the characters and sequences tailored
 * in this collator.
 * @return a pointer to a UnicodeSet object containing all the
 *         code points and sequences that may sort differently than
 *         in the root collator.
 */

public android.icu.text.UnicodeSet getTailoredSet() { throw new RuntimeException("Stub!"); }

/**
 * Compares the source text String to the target text String according to
 * this Collator's rules, strength and decomposition mode.
 * Returns an integer less than,
 * equal to or greater than zero depending on whether the source String is
 * less than, equal to or greater than the target String. See the Collator
 * class description for an example of use.
 *
 * @param source the source String.
 * @param target the target String.
 * @return Returns an integer value. Value is less than zero if source is
 *         less than target, value is zero if source and target are equal,
 *         value is greater than zero if source is greater than target.
 * @see android.icu.text.CollationKey
 * @see #getCollationKey
 * @throws java.lang.NullPointerException thrown if either argument is null.
 */

public abstract int compare(java.lang.String source, java.lang.String target);

/**
 * Compares the source Object to the target Object.
 *
 * @param source the source Object.
 * @param target the target Object.
 * @return Returns an integer value. Value is less than zero if source is
 *         less than target, value is zero if source and target are equal,
 *         value is greater than zero if source is greater than target.
 * @throws java.lang.ClassCastException thrown if either arguments cannot be cast to CharSequence.
 */

public int compare(java.lang.Object source, java.lang.Object target) { throw new RuntimeException("Stub!"); }

/**
 * <p>
 * Transforms the String into a CollationKey suitable for efficient
 * repeated comparison.  The resulting key depends on the collator's
 * rules, strength and decomposition mode.
 *
 * <p>Note that collation keys are often less efficient than simply doing comparison.
 * For more details, see the ICU User Guide.
 *
 * <p>See the CollationKey class documentation for more information.
 * @param source the string to be transformed into a CollationKey.
 * @return the CollationKey for the given String based on this Collator's
 *         collation rules. If the source String is null, a null
 *         CollationKey is returned.
 * @see android.icu.text.CollationKey
 * @see #compare(String, String)
 */

public abstract android.icu.text.CollationKey getCollationKey(java.lang.String source);

/**
 * <strong>[icu]</strong> Sets the variable top to the top of the specified reordering group.
 * The variable top determines the highest-sorting character
 * which is affected by the alternate handling behavior.
 * If that attribute is set to UCOL_NON_IGNORABLE, then the variable top has no effect.
 *
 * <p>The base class implementation throws an UnsupportedOperationException.
 * @param group one of Collator.ReorderCodes.SPACE, Collator.ReorderCodes.PUNCTUATION,
 *              Collator.ReorderCodes.SYMBOL, Collator.ReorderCodes.CURRENCY;
 *              or Collator.ReorderCodes.DEFAULT to restore the default max variable group
 * @return this
 * @see #getMaxVariable
 */

public android.icu.text.Collator setMaxVariable(int group) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns the maximum reordering group whose characters are affected by
 * the alternate handling behavior.
 *
 * <p>The base class implementation returns Collator.ReorderCodes.PUNCTUATION.
 * @return the maximum variable reordering group.
 * @see #setMaxVariable
 */

public int getMaxVariable() { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Gets the variable top value of a Collator.
 *
 * @return the variable top primary weight
 * @see #getMaxVariable
 */

public abstract int getVariableTop();

/**
 * <strong>[icu]</strong> Returns the version of this collator object.
 * @return the version object associated with this collator
 */

public abstract android.icu.util.VersionInfo getVersion();

/**
 * <strong>[icu]</strong> Returns the UCA version of this collator object.
 * @return the version object associated with this collator
 */

public abstract android.icu.util.VersionInfo getUCAVersion();

/**
 * Retrieves the reordering codes for this collator.
 * These reordering codes are a combination of UScript codes and ReorderCodes.
 * @return a copy of the reordering codes for this collator;
 * if none are set then returns an empty array
 * @see #setReorderCodes
 * @see #getEquivalentReorderCodes
 * @see android.icu.text.Collator.ReorderCodes
 * @see android.icu.lang.UScript
 */

public int[] getReorderCodes() { throw new RuntimeException("Stub!"); }

/**
 * Retrieves all the reorder codes that are grouped with the given reorder code. Some reorder
 * codes are grouped and must reorder together.
 * Beginning with ICU 55, scripts only reorder together if they are primary-equal,
 * for example Hiragana and Katakana.
 *
 * @param reorderCode The reorder code to determine equivalence for.
 * @return the set of all reorder codes in the same group as the given reorder code.
 * @see #setReorderCodes
 * @see #getReorderCodes
 * @see android.icu.text.Collator.ReorderCodes
 * @see android.icu.lang.UScript
 */

public static int[] getEquivalentReorderCodes(int reorderCode) { throw new RuntimeException("Stub!"); }

/**
 * Determines whether the object has been frozen or not.
 *
 * <p>An unfrozen Collator is mutable and not thread-safe.
 * A frozen Collator is immutable and thread-safe.
 */

public boolean isFrozen() { throw new RuntimeException("Stub!"); }

/**
 * Freezes the collator.
 * @return the collator itself.
 */

public android.icu.text.Collator freeze() { throw new RuntimeException("Stub!"); }

/**
 * Provides for the clone operation. Any clone is initially unfrozen.
 */

public android.icu.text.Collator cloneAsThawed() { throw new RuntimeException("Stub!"); }

/**
 * Decomposition mode value. With CANONICAL_DECOMPOSITION set,
 * characters that are canonical variants according to the Unicode standard
 * will be decomposed for collation.
 *
 * <p>CANONICAL_DECOMPOSITION corresponds to Normalization Form D as
 * described in <a href="http://www.unicode.org/unicode/reports/tr15/">
 * Unicode Technical Report #15</a>.
 *
 * @see #NO_DECOMPOSITION
 * @see #getDecomposition
 * @see #setDecomposition
 */

public static final int CANONICAL_DECOMPOSITION = 17; // 0x11

/**
 * <strong>[icu] Note:</strong> This is for backwards compatibility with Java APIs only.  It
 * should not be used, IDENTICAL should be used instead.  ICU's
 * collation does not support Java's FULL_DECOMPOSITION mode.
 */

public static final int FULL_DECOMPOSITION = 15; // 0xf

/**
 * Smallest Collator strength value. When all other strengths are equal,
 * the IDENTICAL strength is used as a tiebreaker. The Unicode code point
 * values of the NFD form of each string are compared, just in case there
 * is no difference.
 * See class documentation for more explanation.
 * <p>
 * Note this value is different from JDK's
 */

public static final int IDENTICAL = 15; // 0xf

/**
 * Decomposition mode value. With NO_DECOMPOSITION set, Strings
 * will not be decomposed for collation. This is the default
 * decomposition setting unless otherwise specified by the locale
 * used to create the Collator.
 *
 * <p><strong>Note</strong> this value is different from the JDK's.
 * @see #CANONICAL_DECOMPOSITION
 * @see #getDecomposition
 * @see #setDecomposition
 */

public static final int NO_DECOMPOSITION = 16; // 0x10

/**
 * Strongest collator strength value. Typically used to denote differences
 * between base characters. See class documentation for more explanation.
 * @see #setStrength
 * @see #getStrength
 */

public static final int PRIMARY = 0; // 0x0

/**
 * <strong>[icu]</strong> Fourth level collator strength value.
 * When punctuation is ignored
 * (see <a href="https://unicode-org.github.io/icu/userguide/collation/concepts#ignoring-punctuation">
 * Ignoring Punctuation in the User Guide</a>) at PRIMARY to TERTIARY
 * strength, an additional strength level can
 * be used to distinguish words with and without punctuation.
 * See class documentation for more explanation.
 * @see #setStrength
 * @see #getStrength
 */

public static final int QUATERNARY = 3; // 0x3

/**
 * Second level collator strength value.
 * Accents in the characters are considered secondary differences.
 * Other differences between letters can also be considered secondary
 * differences, depending on the language.
 * See class documentation for more explanation.
 * @see #setStrength
 * @see #getStrength
 */

public static final int SECONDARY = 1; // 0x1

/**
 * Third level collator strength value.
 * Upper and lower case differences in characters are distinguished at this
 * strength level. In addition, a variant of a letter differs from the base
 * form on the tertiary level.
 * See class documentation for more explanation.
 * @see #setStrength
 * @see #getStrength
 */

public static final int TERTIARY = 2; // 0x2
/**
 * Reordering codes for non-script groups that can be reordered under collation.
 *
 * @see #getReorderCodes
 * @see #setReorderCodes
 * @see #getEquivalentReorderCodes
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public static interface ReorderCodes {

/**
 * Characters with the currency property.
 * This is equivalent to the rule value "currency".
 */

public static final int CURRENCY = 4099; // 0x1003

/**
 * A special reordering code that is used to specify the default reordering codes for a locale.
 */

public static final int DEFAULT = -1; // 0xffffffff

/**
 * Characters with the digit property.
 * This is equivalent to the rule value "digit".
 */

public static final int DIGIT = 4100; // 0x1004

/**
 * The first entry in the enumeration of reordering groups. This is intended for use in
 * range checking and enumeration of the reorder codes.
 */

public static final int FIRST = 4096; // 0x1000

/**
 * A special reordering code that is used to specify no reordering codes.
 */

public static final int NONE = 103; // 0x67

/**
 * A special reordering code that is used to specify all other codes used for reordering except
 * for the codes listed as ReorderingCodes and those listed explicitly in a reordering.
 */

public static final int OTHERS = 103; // 0x67

/**
 * Characters with the punctuation property.
 * This is equivalent to the rule value "punct".
 */

public static final int PUNCTUATION = 4097; // 0x1001

/**
 * Characters with the space property.
 * This is equivalent to the rule value "space".
 */

public static final int SPACE = 4096; // 0x1000

/**
 * Characters with the symbol property.
 * This is equivalent to the rule value "symbol".
 */

public static final int SYMBOL = 4098; // 0x1002
}

}

