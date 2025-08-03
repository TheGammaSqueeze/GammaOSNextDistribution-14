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

import java.text.ParseException;
import java.text.CharacterIterator;
import android.icu.lang.UScript;

/**
 * <p>
 * RuleBasedCollator is a concrete subclass of Collator. It allows customization of the Collator via user-specified rule
 * sets. RuleBasedCollator is designed to be fully compliant to the <a
 * href="http://www.unicode.org/unicode/reports/tr10/">Unicode Collation Algorithm (UCA)</a> and conforms to ISO 14651.
 *
 * <p>A Collator is thread-safe only when frozen. See {{@link #isFrozen()} and {@link android.icu.util.Freezable}.
 *
 * <p>
 * Users are strongly encouraged to read the <a href="https://unicode-org.github.io/icu/userguide/collation">User
 * Guide</a> for more information about the collation service before using this class.
 *
 * <p>
 * Create a RuleBasedCollator from a locale by calling the getInstance(Locale) factory method in the base class
 * Collator. Collator.getInstance(Locale) creates a RuleBasedCollator object based on the collation rules defined by the
 * argument locale. If a customized collation ordering or attributes is required, use the RuleBasedCollator(String)
 * constructor with the appropriate rules. The customized RuleBasedCollator will base its ordering on the CLDR root collation, while
 * re-adjusting the attributes and orders of the characters in the specified rule accordingly.
 *
 * <p>
 * RuleBasedCollator provides correct collation orders for most locales supported in ICU. If specific data for a locale
 * is not available, the orders eventually falls back to the
 * <a href="http://www.unicode.org/reports/tr35/tr35-collation.html#Root_Collation">CLDR root sort order</a>.
 *
 * <p>
 * For information about the collation rule syntax and details about customization, please refer to the <a
 * href="https://unicode-org.github.io/icu/userguide/collation/customization">Collation customization</a>
 * section of the User Guide.
 *
 * <p>
 * <strong>Note</strong> that there are some differences between the Collation rule syntax used in Java and ICU4J:
 *
 * <ul>
 * <li>According to the JDK documentation: <br>
 * <i>Modifier '!' : Turns on Thai/Lao vowel-consonant swapping. If this rule is in force when a Thai vowel of the range
 * &#92;U0E40-&#92;U0E44 precedes a Thai consonant of the range &#92;U0E01-&#92;U0E2E OR a Lao vowel of the range
 * &#92;U0EC0-&#92;U0EC4 precedes a Lao consonant of the range &#92;U0E81-&#92;U0EAE then the vowel is placed after the
 * consonant for collation purposes.
 * <br>
 * If a rule is without the modifier '!', the Thai/Lao vowel-consonant swapping is not turned on.
 * </i>
 * <br>
 * ICU4J's RuleBasedCollator does not support turning off the Thai/Lao vowel-consonant swapping, since the UCA clearly
 * states that it has to be supported to ensure a correct sorting order. If a '!' is encountered, it is ignored.</li>
 * <li>As mentioned in the documentation of the base class Collator, compatibility decomposition mode is not supported.</li>
 * </ul>
 * <p>
 * <strong>Examples</strong>
 * <p>
 * Creating Customized RuleBasedCollators: <blockquote>
 *
 * <pre>
 * String simple = "&amp; a &lt; b &lt; c &lt; d";
 * RuleBasedCollator simpleCollator = new RuleBasedCollator(simple);
 *
 * String norwegian = "&amp; a , A &lt; b , B &lt; c , C &lt; d , D &lt; e , E "
 *                    + "&lt; f , F &lt; g , G &lt; h , H &lt; i , I &lt; j , "
 *                    + "J &lt; k , K &lt; l , L &lt; m , M &lt; n , N &lt; "
 *                    + "o , O &lt; p , P &lt; q , Q &lt;r , R &lt;s , S &lt; "
 *                    + "t , T &lt; u , U &lt; v , V &lt; w , W &lt; x , X "
 *                    + "&lt; y , Y &lt; z , Z &lt; &#92;u00E5 = a&#92;u030A "
 *                    + ", &#92;u00C5 = A&#92;u030A ; aa , AA &lt; &#92;u00E6 "
 *                    + ", &#92;u00C6 &lt; &#92;u00F8 , &#92;u00D8";
 * RuleBasedCollator norwegianCollator = new RuleBasedCollator(norwegian);
 * </pre>
 *
 * </blockquote>
 *
 * Concatenating rules to combine <code>Collator</code>s: <blockquote>
 *
 * <pre>
 * // Create an en_US Collator object
 * RuleBasedCollator en_USCollator = (RuleBasedCollator)
 *     Collator.getInstance(new Locale("en", "US", ""));
 * // Create a da_DK Collator object
 * RuleBasedCollator da_DKCollator = (RuleBasedCollator)
 *     Collator.getInstance(new Locale("da", "DK", ""));
 * // Combine the two
 * // First, get the collation rules from en_USCollator
 * String en_USRules = en_USCollator.getRules();
 * // Second, get the collation rules from da_DKCollator
 * String da_DKRules = da_DKCollator.getRules();
 * RuleBasedCollator newCollator =
 *                             new RuleBasedCollator(en_USRules + da_DKRules);
 * // newCollator has the combined rules
 * </pre>
 *
 * </blockquote>
 *
 * Making changes to an existing RuleBasedCollator to create a new <code>Collator</code> object, by appending changes to
 * the existing rule: <blockquote>
 *
 * <pre>
 * // Create a new Collator object with additional rules
 * String addRules = "&amp; C &lt; ch, cH, Ch, CH";
 * RuleBasedCollator myCollator =
 *     new RuleBasedCollator(en_USCollator.getRules() + addRules);
 * // myCollator contains the new rules
 * </pre>
 *
 * </blockquote>
 *
 * How to change the order of non-spacing accents: <blockquote>
 *
 * <pre>
 * // old rule with main accents
 * String oldRules = "= &#92;u0301 ; &#92;u0300 ; &#92;u0302 ; &#92;u0308 "
 *                 + "; &#92;u0327 ; &#92;u0303 ; &#92;u0304 ; &#92;u0305 "
 *                 + "; &#92;u0306 ; &#92;u0307 ; &#92;u0309 ; &#92;u030A "
 *                 + "; &#92;u030B ; &#92;u030C ; &#92;u030D ; &#92;u030E "
 *                 + "; &#92;u030F ; &#92;u0310 ; &#92;u0311 ; &#92;u0312 "
 *                 + "&lt; a , A ; ae, AE ; &#92;u00e6 , &#92;u00c6 "
 *                 + "&lt; b , B &lt; c, C &lt; e, E &amp; C &lt; d , D";
 * // change the order of accent characters
 * String addOn = "&amp; &#92;u0300 ; &#92;u0308 ; &#92;u0302";
 * RuleBasedCollator myCollator = new RuleBasedCollator(oldRules + addOn);
 * </pre>
 *
 * </blockquote>
 *
 * Putting in a new primary ordering before the default setting, e.g. sort English characters before or after Japanese
 * characters in the Japanese <code>Collator</code>: <blockquote>
 *
 * <pre>
 * // get en_US Collator rules
 * RuleBasedCollator en_USCollator
 *                        = (RuleBasedCollator)Collator.getInstance(Locale.US);
 * // add a few Japanese characters to sort before English characters
 * // suppose the last character before the first base letter 'a' in
 * // the English collation rule is &#92;u2212
 * String jaString = "&amp; &#92;u2212 &lt;&#92;u3041, &#92;u3042 &lt;&#92;u3043, "
 *                   + "&#92;u3044";
 * RuleBasedCollator myJapaneseCollator
 *              = new RuleBasedCollator(en_USCollator.getRules() + jaString);
 * </pre>
 *
 * </blockquote>
 * <p>
 * This class is not subclassable
 *
 * @author Syn Wee Quek
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public final class RuleBasedCollator extends android.icu.text.Collator {

/**
 * <p>
 * Constructor that takes the argument rules for customization.
 * The collator will be based on the CLDR root collation, with the
 * attributes and re-ordering of the characters specified in the argument rules.
 * <p>
 * See the User Guide's section on <a href="https://unicode-org.github.io/icu/userguide/collation/customization">
 * Collation Customization</a> for details on the rule syntax.
 *
 * @param rules
 *            the collation rules to build the collation table from.
 * @exception java.text.ParseException
 *                and IOException thrown. ParseException thrown when argument rules have an invalid syntax.
 *                IOException thrown when an error occurred while reading internal data.
 */

public RuleBasedCollator(java.lang.String rules) throws java.lang.Exception { throw new RuntimeException("Stub!"); }

/**
 * Clones the RuleBasedCollator
 *
 * @return a new instance of this RuleBasedCollator object
 */

public java.lang.Object clone() throws java.lang.CloneNotSupportedException { throw new RuntimeException("Stub!"); }

/**
 * Return a CollationElementIterator for the given String.
 *
 * @see android.icu.text.CollationElementIterator
 */

public android.icu.text.CollationElementIterator getCollationElementIterator(java.lang.String source) { throw new RuntimeException("Stub!"); }

/**
 * Return a CollationElementIterator for the given CharacterIterator. The source iterator's integrity will be
 * preserved since a new copy will be created for use.
 *
 * @see android.icu.text.CollationElementIterator
 */

public android.icu.text.CollationElementIterator getCollationElementIterator(java.text.CharacterIterator source) { throw new RuntimeException("Stub!"); }

/**
 * Return a CollationElementIterator for the given UCharacterIterator. The source iterator's integrity will be
 * preserved since a new copy will be created for use.
 *
 * @see android.icu.text.CollationElementIterator
 */

public android.icu.text.CollationElementIterator getCollationElementIterator(android.icu.text.UCharacterIterator source) { throw new RuntimeException("Stub!"); }

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

public android.icu.text.RuleBasedCollator cloneAsThawed() { throw new RuntimeException("Stub!"); }

/**
 * Sets whether uppercase characters sort before lowercase characters or vice versa, in strength TERTIARY. The
 * default mode is false, and so lowercase characters sort before uppercase characters. If true, sort upper case
 * characters first.
 *
 * @param upperfirst
 *            true to sort uppercase characters before lowercase characters, false to sort lowercase characters
 *            before uppercase characters
 * @see #isLowerCaseFirst
 * @see #isUpperCaseFirst
 * @see #setLowerCaseFirst
 * @see #setCaseFirstDefault
 */

public void setUpperCaseFirst(boolean upperfirst) { throw new RuntimeException("Stub!"); }

/**
 * Sets the orders of lower cased characters to sort before upper cased characters, in strength TERTIARY. The
 * default mode is false. If true is set, the RuleBasedCollator will sort lower cased characters before the upper
 * cased ones. Otherwise, if false is set, the RuleBasedCollator will ignore case preferences.
 *
 * @param lowerfirst
 *            true for sorting lower cased characters before upper cased characters, false to ignore case
 *            preferences.
 * @see #isLowerCaseFirst
 * @see #isUpperCaseFirst
 * @see #setUpperCaseFirst
 * @see #setCaseFirstDefault
 */

public void setLowerCaseFirst(boolean lowerfirst) { throw new RuntimeException("Stub!"); }

/**
 * Sets the case first mode to the initial mode set during construction of the RuleBasedCollator. See
 * setUpperCaseFirst(boolean) and setLowerCaseFirst(boolean) for more details.
 *
 * @see #isLowerCaseFirst
 * @see #isUpperCaseFirst
 * @see #setLowerCaseFirst(boolean)
 * @see #setUpperCaseFirst(boolean)
 */

public void setCaseFirstDefault() { throw new RuntimeException("Stub!"); }

/**
 * Sets the alternate handling mode to the initial mode set during construction of the RuleBasedCollator. See
 * setAlternateHandling(boolean) for more details.
 *
 * @see #setAlternateHandlingShifted(boolean)
 * @see #isAlternateHandlingShifted()
 */

public void setAlternateHandlingDefault() { throw new RuntimeException("Stub!"); }

/**
 * Sets the case level mode to the initial mode set during construction of the RuleBasedCollator. See
 * setCaseLevel(boolean) for more details.
 *
 * @see #setCaseLevel(boolean)
 * @see #isCaseLevel
 */

public void setCaseLevelDefault() { throw new RuntimeException("Stub!"); }

/**
 * Sets the decomposition mode to the initial mode set during construction of the RuleBasedCollator. See
 * setDecomposition(int) for more details.
 *
 * @see #getDecomposition
 * @see #setDecomposition(int)
 */

public void setDecompositionDefault() { throw new RuntimeException("Stub!"); }

/**
 * Sets the French collation mode to the initial mode set during construction of the RuleBasedCollator. See
 * setFrenchCollation(boolean) for more details.
 *
 * @see #isFrenchCollation
 * @see #setFrenchCollation(boolean)
 */

public void setFrenchCollationDefault() { throw new RuntimeException("Stub!"); }

/**
 * Sets the collation strength to the initial mode set during the construction of the RuleBasedCollator. See
 * setStrength(int) for more details.
 *
 * @see #setStrength(int)
 * @see #getStrength
 */

public void setStrengthDefault() { throw new RuntimeException("Stub!"); }

/**
 * Method to set numeric collation to its default value.
 *
 * @see #getNumericCollation
 * @see #setNumericCollation
 */

public void setNumericCollationDefault() { throw new RuntimeException("Stub!"); }

/**
 * Sets the mode for the direction of SECONDARY weights to be used in French collation. The default value is false,
 * which treats SECONDARY weights in the order they appear. If set to true, the SECONDARY weights will be sorted
 * backwards. See the section on <a href="https://unicode-org.github.io/icu/userguide/collation/architecture">
 * French collation</a> for more information.
 *
 * @param flag
 *            true to set the French collation on, false to set it off
 * @see #isFrenchCollation
 * @see #setFrenchCollationDefault
 */

public void setFrenchCollation(boolean flag) { throw new RuntimeException("Stub!"); }

/**
 * Sets the alternate handling for QUATERNARY strength to be either shifted or non-ignorable. See the UCA definition
 * on <a href="http://www.unicode.org/unicode/reports/tr10/#Variable_Weighting">Variable Weighting</a>. This
 * attribute will only be effective when QUATERNARY strength is set. The default value for this mode is false,
 * corresponding to the NON_IGNORABLE mode in UCA. In the NON_IGNORABLE mode, the RuleBasedCollator treats all
 * the code points with non-ignorable primary weights in the same way. If the mode is set to true, the behavior
 * corresponds to SHIFTED defined in UCA, this causes code points with PRIMARY orders that are equal or below the
 * variable top value to be ignored in PRIMARY order and moved to the QUATERNARY order.
 *
 * @param shifted
 *            true if SHIFTED behavior for alternate handling is desired, false for the NON_IGNORABLE behavior.
 * @see #isAlternateHandlingShifted
 * @see #setAlternateHandlingDefault
 */

public void setAlternateHandlingShifted(boolean shifted) { throw new RuntimeException("Stub!"); }

/**
 * <p>
 * When case level is set to true, an additional weight is formed between the SECONDARY and TERTIARY weight, known
 * as the case level. The case level is used to distinguish large and small Japanese Kana characters. Case level
 * could also be used in other situations. For example to distinguish certain Pinyin characters. The default value
 * is false, which means the case level is not generated. The contents of the case level are affected by the case
 * first mode. A simple way to ignore accent differences in a string is to set the strength to PRIMARY and enable
 * case level.
 * <p>
 * See the section on <a href="https://unicode-org.github.io/icu/userguide/collation/architecture">case
 * level</a> for more information.
 *
 * @param flag
 *            true if case level sorting is required, false otherwise
 * @see #setCaseLevelDefault
 * @see #isCaseLevel
 */

public void setCaseLevel(boolean flag) { throw new RuntimeException("Stub!"); }

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
 * The default decompositon mode for the Collator is
 * NO_DECOMPOSITON, unless specified otherwise by the locale used
 * to create the Collator.
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
 * Sets this Collator's strength attribute. The strength attribute determines the minimum level of difference
 * considered significant during comparison.
 *
 * <p>See the Collator class description for an example of use.
 *
 * @param newStrength
 *            the new strength value.
 * @see #getStrength
 * @see #setStrengthDefault
 * @see #PRIMARY
 * @see #SECONDARY
 * @see #TERTIARY
 * @see #QUATERNARY
 * @see #IDENTICAL
 * @exception java.lang.IllegalArgumentException
 *                If the new strength value is not one of PRIMARY, SECONDARY, TERTIARY, QUATERNARY or IDENTICAL.
 */

public void setStrength(int newStrength) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Sets the variable top to the top of the specified reordering group.
 * The variable top determines the highest-sorting character
 * which is affected by the alternate handling behavior.
 * If that attribute is set to NON_IGNORABLE, then the variable top has no effect.
 * @param group one of Collator.ReorderCodes.SPACE, Collator.ReorderCodes.PUNCTUATION,
 *              Collator.ReorderCodes.SYMBOL, Collator.ReorderCodes.CURRENCY;
 *              or Collator.ReorderCodes.DEFAULT to restore the default max variable group
 * @return this
 * @see #getMaxVariable
 */

public android.icu.text.RuleBasedCollator setMaxVariable(int group) { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Returns the maximum reordering group whose characters are affected by
 * the alternate handling behavior.
 * @return the maximum variable reordering group.
 * @see #setMaxVariable
 */

public int getMaxVariable() { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> When numeric collation is turned on, this Collator makes
 * substrings of digits sort according to their numeric values.
 *
 * <p>This is a way to get '100' to sort AFTER '2'. Note that the longest
 * digit substring that can be treated as a single unit is
 * 254 digits (not counting leading zeros). If a digit substring is
 * longer than that, the digits beyond the limit will be treated as a
 * separate digit substring.
 *
 * <p>A "digit" in this sense is a code point with General_Category=Nd,
 * which does not include circled numbers, roman numerals, etc.
 * Only a contiguous digit substring is considered, that is,
 * non-negative integers without separators.
 * There is no support for plus/minus signs, decimals, exponents, etc.
 *
 * @param flag
 *            true to turn numeric collation on and false to turn it off
 * @see #getNumericCollation
 * @see #setNumericCollationDefault
 */

public void setNumericCollation(boolean flag) { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 *
 * @param order the reordering codes to apply to this collator; if this is null or an empty array
 * then this clears any existing reordering
 * @throws java.lang.IllegalArgumentException if the reordering codes are malformed in any way (e.g. duplicates, multiple reset codes, overlapping equivalent scripts)
 * @see #getReorderCodes
 * @see android.icu.text.Collator#getEquivalentReorderCodes
 * @see android.icu.text.Collator.ReorderCodes
 * @see android.icu.lang.UScript
 */

public void setReorderCodes(int... order) { throw new RuntimeException("Stub!"); }

/**
 * Gets the collation tailoring rules for this RuleBasedCollator.
 * Equivalent to String getRules(false).
 *
 * <p>On Android, the returned string will be empty unless this instance was
 * constructed using {@link #RuleBasedCollator(java.lang.String)}.
 *
 * @return the collation tailoring rules
 * @see #getRules(boolean)
 */

public java.lang.String getRules() { throw new RuntimeException("Stub!"); }

/**
 * Returns current rules.
 * The argument defines whether full rules (root collation + tailored) rules are returned
 * or just the tailoring.
 *
 * <p>The root collation rules are an <i>approximation</i> of the root collator's sort order.
 * They are almost never used or useful at runtime and can be removed from the data.
 * See <a href="https://unicode-org.github.io/icu/userguide/collation/customization#building-on-existing-locales">User Guide:
 * Collation Customization, Building on Existing Locales</a>
 *
 * <p>{@link #getRules()} should normally be used instead.
 * @param fullrules
 *            true if the rules that defines the full set of collation order is required, otherwise false for
 *            returning only the tailored rules
 * @return the current rules that defines this Collator.
 * @see #getRules()
 */

public java.lang.String getRules(boolean fullrules) { throw new RuntimeException("Stub!"); }

/**
 * Get a UnicodeSet that contains all the characters and sequences tailored in this collator.
 *
 * @return a pointer to a UnicodeSet object containing all the code points and sequences that may sort differently
 *         than in the root collator.
 */

public android.icu.text.UnicodeSet getTailoredSet() { throw new RuntimeException("Stub!"); }

/**
 * Gets unicode sets containing contractions and/or expansions of a collator
 *
 * @param contractions
 *            if not null, set to contain contractions
 * @param expansions
 *            if not null, set to contain expansions
 * @param addPrefixes
 *            add the prefix contextual elements to contractions
 * @throws java.lang.Exception
 *             Throws an exception if any errors occurs.
 */

public void getContractionsAndExpansions(android.icu.text.UnicodeSet contractions, android.icu.text.UnicodeSet expansions, boolean addPrefixes) throws java.lang.Exception { throw new RuntimeException("Stub!"); }

/**
 * <p>
 * Get a Collation key for the argument String source from this RuleBasedCollator.
 * <p>
 * General recommendation: <br>
 * If comparison are to be done to the same String multiple times, it would be more efficient to generate
 * CollationKeys for the Strings and use CollationKey.compareTo(CollationKey) for the comparisons. If the each
 * Strings are compared to only once, using the method RuleBasedCollator.compare(String, String) will have a better
 * performance.
 * <p>
 * See the class documentation for an explanation about CollationKeys.
 *
 * @param source
 *            the text String to be transformed into a collation key.
 * @return the CollationKey for the given String based on this RuleBasedCollator's collation rules. If the source
 *         String is null, a null CollationKey is returned.
 * @see android.icu.text.CollationKey
 * @see #compare(String, String)
 */

public android.icu.text.CollationKey getCollationKey(java.lang.String source) { throw new RuntimeException("Stub!"); }

/**
 * Returns this Collator's strength attribute. The strength attribute
 * determines the minimum level of difference considered significant.
 *
 * <p><strong>[icu] Note:</strong> This can return QUATERNARY strength, which is not supported by the
 * JDK version.
 *
 * <p>See the Collator class description for more details.
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
 *
 * <p>See the Collator class description for more details.
 *
 * @return the decomposition mode
 * @see #setDecomposition
 * @see #NO_DECOMPOSITION
 * @see #CANONICAL_DECOMPOSITION
 */

public int getDecomposition() { throw new RuntimeException("Stub!"); }

/**
 * Return true if an uppercase character is sorted before the corresponding lowercase character. See
 * setCaseFirst(boolean) for details.
 *
 * @see #setUpperCaseFirst
 * @see #setLowerCaseFirst
 * @see #isLowerCaseFirst
 * @see #setCaseFirstDefault
 * @return true if upper cased characters are sorted before lower cased characters, false otherwise
 */

public boolean isUpperCaseFirst() { throw new RuntimeException("Stub!"); }

/**
 * Return true if a lowercase character is sorted before the corresponding uppercase character. See
 * setCaseFirst(boolean) for details.
 *
 * @see #setUpperCaseFirst
 * @see #setLowerCaseFirst
 * @see #isUpperCaseFirst
 * @see #setCaseFirstDefault
 * @return true lower cased characters are sorted before upper cased characters, false otherwise
 */

public boolean isLowerCaseFirst() { throw new RuntimeException("Stub!"); }

/**
 * Checks if the alternate handling behavior is the UCA defined SHIFTED or NON_IGNORABLE. If return value is true,
 * then the alternate handling attribute for the Collator is SHIFTED. Otherwise if return value is false, then the
 * alternate handling attribute for the Collator is NON_IGNORABLE See setAlternateHandlingShifted(boolean) for more
 * details.
 *
 * @return true or false
 * @see #setAlternateHandlingShifted(boolean)
 * @see #setAlternateHandlingDefault
 */

public boolean isAlternateHandlingShifted() { throw new RuntimeException("Stub!"); }

/**
 * Checks if case level is set to true. See setCaseLevel(boolean) for details.
 *
 * @return the case level mode
 * @see #setCaseLevelDefault
 * @see #isCaseLevel
 * @see #setCaseLevel(boolean)
 */

public boolean isCaseLevel() { throw new RuntimeException("Stub!"); }

/**
 * Checks if French Collation is set to true. See setFrenchCollation(boolean) for details.
 *
 * @return true if French Collation is set to true, false otherwise
 * @see #setFrenchCollation(boolean)
 * @see #setFrenchCollationDefault
 */

public boolean isFrenchCollation() { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Gets the variable top value of a Collator.
 *
 * @return the variable top primary weight
 * @see #getMaxVariable
 */

public int getVariableTop() { throw new RuntimeException("Stub!"); }

/**
 * Method to retrieve the numeric collation value. When numeric collation is turned on, this Collator generates a
 * collation key for the numeric value of substrings of digits. This is a way to get '100' to sort AFTER '2'
 *
 * @see #setNumericCollation
 * @see #setNumericCollationDefault
 * @return true if numeric collation is turned on, false otherwise
 */

public boolean getNumericCollation() { throw new RuntimeException("Stub!"); }

/**
 * Retrieves the reordering codes for this collator.
 * These reordering codes are a combination of UScript codes and ReorderCodes.
 * @return a copy of the reordering codes for this collator;
 * if none are set then returns an empty array
 * @see #setReorderCodes
 * @see android.icu.text.Collator#getEquivalentReorderCodes
 */

public int[] getReorderCodes() { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public boolean equals(java.lang.Object obj) { throw new RuntimeException("Stub!"); }

/**
 * Generates a unique hash code for this RuleBasedCollator.
 *
 * @return the unique hash code for this Collator
 */

public int hashCode() { throw new RuntimeException("Stub!"); }

/**
 * Compares the source text String to the target text String according to the collation rules, strength and
 * decomposition mode for this RuleBasedCollator. Returns an integer less than, equal to or greater than zero
 * depending on whether the source String is less than, equal to or greater than the target String. See the Collator
 * class description for an example of use.
 * <p>
 * General recommendation: <br>
 * If comparison are to be done to the same String multiple times, it would be more efficient to generate
 * CollationKeys for the Strings and use CollationKey.compareTo(CollationKey) for the comparisons. If speed
 * performance is critical and object instantiation is to be reduced, further optimization may be achieved by
 * generating a simpler key of the form RawCollationKey and reusing this RawCollationKey object with the method
 * RuleBasedCollator.getRawCollationKey. Internal byte representation can be directly accessed via RawCollationKey
 * and stored for future use. Like CollationKey, RawCollationKey provides a method RawCollationKey.compareTo for key
 * comparisons. If the each Strings are compared to only once, using the method RuleBasedCollator.compare(String,
 * String) will have a better performance.
 *
 * @param source
 *            the source text String.
 * @param target
 *            the target text String.
 * @return Returns an integer value. Value is less than zero if source is less than target, value is zero if source
 *         and target are equal, value is greater than zero if source is greater than target.
 * @see android.icu.text.CollationKey
 * @see #getCollationKey
 */

public int compare(java.lang.String source, java.lang.String target) { throw new RuntimeException("Stub!"); }

/**
 * Get the version of this collator object.
 *
 * @return the version object associated with this collator
 */

public android.icu.util.VersionInfo getVersion() { throw new RuntimeException("Stub!"); }

/**
 * Get the UCA version of this collator object.
 *
 * @return the version object associated with this collator
 */

public android.icu.util.VersionInfo getUCAVersion() { throw new RuntimeException("Stub!"); }
}

