/* GENERATED SOURCE. DO NOT MODIFY. */
// ? 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/**
*******************************************************************************
* Copyright (C) 1996-2016, International Business Machines Corporation and
* others. All Rights Reserved.
*******************************************************************************
*/


package android.icu.lang;


/**
 * <p>Selection constants for Unicode properties.
 * <p>These constants are used in functions like
 * UCharacter.hasBinaryProperty(int) to select one of the Unicode properties.
 *
 * <p>The properties APIs are intended to reflect Unicode properties as
 * defined in the Unicode Character Database (UCD) and Unicode Technical
 * Reports (UTR).
 * <p>For details about the properties see
 * <a href="http://www.unicode.org/reports/tr44/">UAX #44: Unicode Character Database</a>.
 *
 * <p>Important: If ICU is built with UCD files from Unicode versions below
 * 3.2, then properties marked with "new" are not or not fully
 * available. Check UCharacter.getUnicodeVersion() to be sure.
 * @author Syn Wee Quek
 * @see android.icu.lang.UCharacter
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public interface UProperty {

/**
 * String property Age.
 * Corresponds to UCharacter.getAge(int).
 */

public static final int AGE = 16384; // 0x4000

/**
 * <p>Binary property Alphabetic.
 * <p>Property for UCharacter.isUAlphabetic(), different from the property
 * in UCharacter.isalpha().
 * <p>Lu + Ll + Lt + Lm + Lo + Nl + Other_Alphabetic.
 */

public static final int ALPHABETIC = 0; // 0x0

/**
 * Binary property ASCII_Hex_Digit (0-9 A-F a-f).
 */

public static final int ASCII_HEX_DIGIT = 1; // 0x1

/**
 * Enumerated property Bidi_Class.
 * Same as UCharacter.getDirection(int), returns UCharacterDirection values.
 */

public static final int BIDI_CLASS = 4096; // 0x1000

/**
 * <p>Binary property Bidi_Control.
 * <p>Format controls which have specific functions in the Bidi Algorithm.
 */

public static final int BIDI_CONTROL = 2; // 0x2

/**
 * <p>Binary property Bidi_Mirrored.
 * <p>Characters that may change display in RTL text.
 * <p>Property for UCharacter.isMirrored().
 * <p>See Bidi Algorithm; UTR 9.
 */

public static final int BIDI_MIRRORED = 3; // 0x3

/**
 * String property Bidi_Mirroring_Glyph.
 * Corresponds to UCharacter.getMirror(int).
 */

public static final int BIDI_MIRRORING_GLYPH = 16385; // 0x4001

/**
 * String property Bidi_Paired_Bracket (new in Unicode 6.3).
 * Corresponds to UCharacter.getBidiPairedBracket.
 */

public static final int BIDI_PAIRED_BRACKET = 16397; // 0x400d

/**
 * Enumerated property Bidi_Paired_Bracket_Type (new in Unicode 6.3).
 * Used in UAX #9: Unicode Bidirectional Algorithm
 * (http://www.unicode.org/reports/tr9/)
 * Returns UCharacter.BidiPairedBracketType values.
 */

public static final int BIDI_PAIRED_BRACKET_TYPE = 4117; // 0x1015

/**
 * First constant for binary Unicode properties.
 */

public static final int BINARY_START = 0; // 0x0

/**
 * Enumerated property Block.
 * Same as UCharacter.UnicodeBlock.of(int), returns UCharacter.UnicodeBlock
 * values.
 */

public static final int BLOCK = 4097; // 0x1001

/**
 * Enumerated property Canonical_Combining_Class.
 * Same as UCharacter.getCombiningClass(int), returns 8-bit numeric values.
 */

public static final int CANONICAL_COMBINING_CLASS = 4098; // 0x1002

/**
 * Binary property Cased.
 * For Lowercase, Uppercase and Titlecase characters.
 */

public static final int CASED = 49; // 0x31

/**
 * String property Case_Folding.
 * Corresponds to UCharacter.foldCase(String, boolean).
 */

public static final int CASE_FOLDING = 16386; // 0x4002

/**
 * Binary property Case_Ignorable.
 * Used in context-sensitive case mappings.
 */

public static final int CASE_IGNORABLE = 50; // 0x32

/**
 * <p>Binary property Case_Sensitive.
 * <p>Either the source of a case
 * mapping or _in_ the target of a case mapping. Not the same as
 * the general category Cased_Letter.
 */

public static final int CASE_SENSITIVE = 34; // 0x22

/**
 * Binary property Changes_When_Casefolded.
 */

public static final int CHANGES_WHEN_CASEFOLDED = 54; // 0x36

/**
 * Binary property Changes_When_Casemapped.
 */

public static final int CHANGES_WHEN_CASEMAPPED = 55; // 0x37

/**
 * Binary property Changes_When_Lowercased.
 */

public static final int CHANGES_WHEN_LOWERCASED = 51; // 0x33

/**
 * Binary property Changes_When_NFKC_Casefolded.
 */

public static final int CHANGES_WHEN_NFKC_CASEFOLDED = 56; // 0x38

/**
 * Binary property Changes_When_Titlecased.
 */

public static final int CHANGES_WHEN_TITLECASED = 53; // 0x35

/**
 * Binary property Changes_When_Uppercased.
 */

public static final int CHANGES_WHEN_UPPERCASED = 52; // 0x34

/**
 * <p>Binary property Dash.
 * <p>Variations of dashes.
 */

public static final int DASH = 4; // 0x4

/**
 * Enumerated property Decomposition_Type.
 * Returns UCharacter.DecompositionType values.
 */

public static final int DECOMPOSITION_TYPE = 4099; // 0x1003

/**
 * <p>Binary property Default_Ignorable_Code_Point (new).
 *
 * <p>Property that indicates codepoint is ignorable in most processing.
 *
 * <p>Codepoints (2060..206F, FFF0..FFFB, E0000..E0FFF) +
 * Other_Default_Ignorable_Code_Point + (Cf + Cc + Cs - White_Space)
 */

public static final int DEFAULT_IGNORABLE_CODE_POINT = 5; // 0x5

/**
 * <p>Binary property Deprecated (new).
 * <p>The usage of deprecated characters is strongly discouraged.
 */

public static final int DEPRECATED = 6; // 0x6

/**
 * <p>Binary property Diacritic.
 * <p>Characters that linguistically modify the meaning of another
 * character to which they apply.
 */

public static final int DIACRITIC = 7; // 0x7

/**
 * First constant for double Unicode properties.
 */

public static final int DOUBLE_START = 12288; // 0x3000

/**
 * Enumerated property East_Asian_Width.
 * See http://www.unicode.org/reports/tr11/
 * Returns UCharacter.EastAsianWidth values.
 */

public static final int EAST_ASIAN_WIDTH = 4100; // 0x1004

/**
 * Binary property Emoji.
 * See http://www.unicode.org/reports/tr51/#Emoji_Properties
 */

public static final int EMOJI = 57; // 0x39

/**
 * Binary property Emoji_Component.
 * See http://www.unicode.org/reports/tr51/#Emoji_Properties
 */

public static final int EMOJI_COMPONENT = 61; // 0x3d

/**
 * Binary property Emoji_Modifier.
 * See http://www.unicode.org/reports/tr51/#Emoji_Properties
 */

public static final int EMOJI_MODIFIER = 59; // 0x3b

/**
 * Binary property Emoji_Modifier_Base.
 * See http://www.unicode.org/reports/tr51/#Emoji_Properties
 */

public static final int EMOJI_MODIFIER_BASE = 60; // 0x3c

/**
 * Binary property Emoji_Presentation.
 * See http://www.unicode.org/reports/tr51/#Emoji_Properties
 */

public static final int EMOJI_PRESENTATION = 58; // 0x3a

/**
 * Binary property Extended_Pictographic.
 * See http://www.unicode.org/reports/tr51/#Emoji_Properties
 */

public static final int EXTENDED_PICTOGRAPHIC = 64; // 0x40

/**
 * <p>Binary property Extender.
 * <p>Extend the value or shape of a preceding alphabetic character, e.g.
 * length and iteration marks.
 */

public static final int EXTENDER = 8; // 0x8

/**
 * <p>Binary property Full_Composition_Exclusion.
 * <p>CompositionExclusions.txt + Singleton Decompositions +
 * Non-Starter Decompositions.
 */

public static final int FULL_COMPOSITION_EXCLUSION = 9; // 0x9

/**
 * Enumerated property General_Category.
 * Same as UCharacter.getType(int), returns UCharacterCategory values.
 */

public static final int GENERAL_CATEGORY = 4101; // 0x1005

/**
 * Bitmask property General_Category_Mask.
 * This is the General_Category property returned as a bit mask.
 * When used in UCharacter.getIntPropertyValue(c),
 * returns bit masks for UCharacterCategory values where exactly one bit is set.
 * When used with UCharacter.getPropertyValueName() and UCharacter.getPropertyValueEnum(),
 * a multi-bit mask is used for sets of categories like "Letters".
 */

public static final int GENERAL_CATEGORY_MASK = 8192; // 0x2000

/**
 * <p>Binary property Grapheme_Base (new).
 * <p>For programmatic determination of grapheme cluster boundaries.
 * [0..10FFFF]-Cc-Cf-Cs-Co-Cn-Zl-Zp-Grapheme_Link-Grapheme_Extend-CGJ
 */

public static final int GRAPHEME_BASE = 10; // 0xa

/**
 * Enumerated property Grapheme_Cluster_Break (new in Unicode 4.1).
 * Used in UAX #29: Text Boundaries
 * (http://www.unicode.org/reports/tr29/)
 * Returns UCharacter.GraphemeClusterBreak values.
 */

public static final int GRAPHEME_CLUSTER_BREAK = 4114; // 0x1012

/**
 * <p>Binary property Grapheme_Extend (new).
 * <p>For programmatic determination of grapheme cluster boundaries.
 * <p>Me+Mn+Mc+Other_Grapheme_Extend-Grapheme_Link-CGJ
 */

public static final int GRAPHEME_EXTEND = 11; // 0xb

/**
 * <p>Binary property Grapheme_Link (new).
 * <p>For programmatic determination of grapheme cluster boundaries.
 */

public static final int GRAPHEME_LINK = 12; // 0xc

/**
 * Enumerated property Hangul_Syllable_Type, new in Unicode 4.
 * Returns UCharacter.HangulSyllableType values.
 */

public static final int HANGUL_SYLLABLE_TYPE = 4107; // 0x100b

/**
 * <p>Binary property Hex_Digit.
 * <p>Characters commonly used for hexadecimal numbers.
 */

public static final int HEX_DIGIT = 13; // 0xd

/**
 * <p>Binary property Hyphen.
 * <p>Dashes used to mark connections between pieces of words, plus the
 * Katakana middle dot.
 */

public static final int HYPHEN = 14; // 0xe

/**
 * <p>Binary property Ideographic.
 * <p>CJKV ideographs.
 */

public static final int IDEOGRAPHIC = 17; // 0x11

/**
 * <p>Binary property IDS_Binary_Operator (new).
 * <p>For programmatic determination of Ideographic Description Sequences.
 */

public static final int IDS_BINARY_OPERATOR = 18; // 0x12

/**
 * <p>Binary property IDS_Trinary_Operator (new).
 * <p>For programmatic determination of Ideographic Description
 * Sequences.
 */

public static final int IDS_TRINARY_OPERATOR = 19; // 0x13

/**
 * <p>Binary property ID_Continue.
 * <p>Characters that can continue an identifier.
 * <p>ID_Start+Mn+Mc+Nd+Pc
 */

public static final int ID_CONTINUE = 15; // 0xf

/**
 * <p>Binary property ID_Start.
 * <p>Characters that can start an identifier.
 * <p>Lu+Ll+Lt+Lm+Lo+Nl
 */

public static final int ID_START = 16; // 0x10

/**
 * Enumerated property Indic_Positional_Category.
 * New in Unicode 6.0 as provisional property Indic_Matra_Category;
 * renamed and changed to informative in Unicode 8.0.
 * See http://www.unicode.org/reports/tr44/#IndicPositionalCategory.txt
 */

public static final int INDIC_POSITIONAL_CATEGORY = 4118; // 0x1016

/**
 * Enumerated property Indic_Syllabic_Category.
 * New in Unicode 6.0 as provisional; informative since Unicode 8.0.
 * See http://www.unicode.org/reports/tr44/#IndicSyllabicCategory.txt
 */

public static final int INDIC_SYLLABIC_CATEGORY = 4119; // 0x1017

/**
 * First constant for enumerated/integer Unicode properties.
 */

public static final int INT_START = 4096; // 0x1000

/**
 * Enumerated property Joining_Group.
 * Returns UCharacter.JoiningGroup values.
 */

public static final int JOINING_GROUP = 4102; // 0x1006

/**
 * Enumerated property Joining_Type.
 * Returns UCharacter.JoiningType values.
 */

public static final int JOINING_TYPE = 4103; // 0x1007

/**
 * <p>Binary property Join_Control.
 * <p>Format controls for cursive joining and ligation.
 */

public static final int JOIN_CONTROL = 20; // 0x14

/**
 * Enumerated property Lead_Canonical_Combining_Class.
 * ICU-specific property for the ccc of the first code point
 * of the decomposition, or lccc(c)=ccc(NFD(c)[0]).
 * Useful for checking for canonically ordered text;
 * see Normalizer.FCD and http://www.unicode.org/notes/tn5/#FCD .
 * Returns 8-bit numeric values like CANONICAL_COMBINING_CLASS.
 */

public static final int LEAD_CANONICAL_COMBINING_CLASS = 4112; // 0x1010

/**
 * Enumerated property Line_Break.
 * Returns UCharacter.LineBreak values.
 */

public static final int LINE_BREAK = 4104; // 0x1008

/**
 * <p>Binary property Logical_Order_Exception (new).
 * <p>Characters that do not use logical order and require special
 * handling in most processing.
 */

public static final int LOGICAL_ORDER_EXCEPTION = 21; // 0x15

/**
 * <p>Binary property Lowercase.
 * <p>Same as UCharacter.isULowercase(), different from
 * UCharacter.islower().
 * <p>Ll+Other_Lowercase
 */

public static final int LOWERCASE = 22; // 0x16

/**
 * String property Lowercase_Mapping.
 * Corresponds to UCharacter.toLowerCase(String).
 */

public static final int LOWERCASE_MAPPING = 16388; // 0x4004

/**
 * First constant for bit-mask Unicode properties.
 */

public static final int MASK_START = 8192; // 0x2000

/** <p>Binary property Math.
 * <p>Sm+Other_Math
 */

public static final int MATH = 23; // 0x17

/**
 * String property Name.
 * Corresponds to UCharacter.getName(int).
 */

public static final int NAME = 16389; // 0x4005

/**
 * Binary property NFC_Inert.
 * ICU-specific property for characters that are inert under NFC,
 * i.e., they do not interact with adjacent characters.
 * Used for example in normalizing transforms in incremental mode
 * to find the boundary of safely normalizable text despite possible
 * text additions.
 * @see #NFD_INERT
 */

public static final int NFC_INERT = 39; // 0x27

/**
 * Enumerated property NFC_Quick_Check.
 * Returns numeric values compatible with Normalizer.QuickCheckResult.
 */

public static final int NFC_QUICK_CHECK = 4110; // 0x100e

/**
 * Binary property NFD_Inert.
 * ICU-specific property for characters that are inert under NFD,
 * i.e., they do not interact with adjacent characters.
 * Used for example in normalizing transforms in incremental mode
 * to find the boundary of safely normalizable text despite possible
 * text additions.
 *
 * There is one such property per normalization form.
 * These properties are computed as follows - an inert character is:
 * a) unassigned, or ALL of the following:
 * b) of combining class 0.
 * c) not decomposed by this normalization form.
 * AND if NFC or NFKC,
 * d) can never compose with a previous character.
 * e) can never compose with a following character.
 * f) can never change if another character is added.
 * Example: a-breve might satisfy all but f, but if you
 * add an ogonek it changes to a-ogonek + breve
 *
 * See also com.ibm.text.UCD.NFSkippable in the ICU4J repository,
 * and icu/source/common/unormimp.h .
 */

public static final int NFD_INERT = 37; // 0x25

/**
 * Enumerated property NFD_Quick_Check.
 * Returns numeric values compatible with Normalizer.QuickCheckResult.
 */

public static final int NFD_QUICK_CHECK = 4108; // 0x100c

/**
 * Binary property NFKC_Inert.
 * ICU-specific property for characters that are inert under NFKC,
 * i.e., they do not interact with adjacent characters.
 * Used for example in normalizing transforms in incremental mode
 * to find the boundary of safely normalizable text despite possible
 * text additions.
 * @see #NFD_INERT
 */

public static final int NFKC_INERT = 40; // 0x28

/**
 * Enumerated property NFKC_Quick_Check.
 * Returns numeric values compatible with Normalizer.QuickCheckResult.
 */

public static final int NFKC_QUICK_CHECK = 4111; // 0x100f

/**
 * Binary property NFKD_Inert.
 * ICU-specific property for characters that are inert under NFKD,
 * i.e., they do not interact with adjacent characters.
 * Used for example in normalizing transforms in incremental mode
 * to find the boundary of safely normalizable text despite possible
 * text additions.
 * @see #NFD_INERT
 */

public static final int NFKD_INERT = 38; // 0x26

/**
 * Enumerated property NFKD_Quick_Check.
 * Returns numeric values compatible with Normalizer.QuickCheckResult.
 */

public static final int NFKD_QUICK_CHECK = 4109; // 0x100d

/**
 * <p>Binary property Noncharacter_Code_Point.
 * <p>Code points that are explicitly defined as illegal for the encoding
 * of characters.
 */

public static final int NONCHARACTER_CODE_POINT = 24; // 0x18

/**
 * Enumerated property Numeric_Type.
 * Returns UCharacter.NumericType values.
 */

public static final int NUMERIC_TYPE = 4105; // 0x1009

/**
 * Double property Numeric_Value.
 * Corresponds to UCharacter.getUnicodeNumericValue(int).
 */

public static final int NUMERIC_VALUE = 12288; // 0x3000

/**
 * First constant for Unicode properties with unusual value types.
 */

public static final int OTHER_PROPERTY_START = 28672; // 0x7000

/**
 * Binary property Pattern_Syntax (new in Unicode 4.1).
 * See UAX #31 Identifier and Pattern Syntax
 * (http://www.unicode.org/reports/tr31/)
 */

public static final int PATTERN_SYNTAX = 42; // 0x2a

/**
 * Binary property Pattern_White_Space (new in Unicode 4.1).
 * See UAX #31 Identifier and Pattern Syntax
 * (http://www.unicode.org/reports/tr31/)
 */

public static final int PATTERN_WHITE_SPACE = 43; // 0x2b

/**
 * Binary property alnum (a C/POSIX character class).
 * Implemented according to the UTS #18 Annex C Standard Recommendation.
 * See the UCharacter class documentation.
 */

public static final int POSIX_ALNUM = 44; // 0x2c

/**
 * Binary property blank (a C/POSIX character class).
 * Implemented according to the UTS #18 Annex C Standard Recommendation.
 * See the UCharacter class documentation.
 */

public static final int POSIX_BLANK = 45; // 0x2d

/**
 * Binary property graph (a C/POSIX character class).
 * Implemented according to the UTS #18 Annex C Standard Recommendation.
 * See the UCharacter class documentation.
 */

public static final int POSIX_GRAPH = 46; // 0x2e

/**
 * Binary property print (a C/POSIX character class).
 * Implemented according to the UTS #18 Annex C Standard Recommendation.
 * See the UCharacter class documentation.
 */

public static final int POSIX_PRINT = 47; // 0x2f

/**
 * Binary property xdigit (a C/POSIX character class).
 * Implemented according to the UTS #18 Annex C Standard Recommendation.
 * See the UCharacter class documentation.
 */

public static final int POSIX_XDIGIT = 48; // 0x30

/**
 * Binary property Prepended_Concatenation_Mark.
 */

public static final int PREPENDED_CONCATENATION_MARK = 63; // 0x3f

/**
 * <p>Binary property Quotation_Mark.
 */

public static final int QUOTATION_MARK = 25; // 0x19

/**
 * <p>Binary property Radical (new).
 * <p>For programmatic determination of Ideographic Description
 * Sequences.
 */

public static final int RADICAL = 26; // 0x1a

/**
 * Binary property Regional_Indicator.
 */

public static final int REGIONAL_INDICATOR = 62; // 0x3e

/**
 * Enumerated property Script.
 * Same as UScript.getScript(int), returns UScript values.
 */

public static final int SCRIPT = 4106; // 0x100a

/**
 * Miscellaneous property Script_Extensions (new in Unicode 6.0).
 * Some characters are commonly used in multiple scripts.
 * For more information, see UAX #24: http://www.unicode.org/reports/tr24/.
 * Corresponds to UScript.hasScript and UScript.getScriptExtensions.
 */

public static final int SCRIPT_EXTENSIONS = 28672; // 0x7000

/**
 * Binary Property Segment_Starter.
 * ICU-specific property for characters that are starters in terms of
 * Unicode normalization and combining character sequences.
 * They have ccc=0 and do not occur in non-initial position of the
 * canonical decomposition of any character
 * (like " in NFD(a-umlaut) and a Jamo T in an NFD(Hangul LVT)).
 * ICU uses this property for segmenting a string for generating a set of
 * canonically equivalent strings, e.g. for canonical closure while
 * processing collation tailoring rules.
 */

public static final int SEGMENT_STARTER = 41; // 0x29

/**
 * Enumerated property Sentence_Break (new in Unicode 4.1).
 * Used in UAX #29: Text Boundaries
 * (http://www.unicode.org/reports/tr29/)
 * Returns UCharacter.SentenceBreak values.
 */

public static final int SENTENCE_BREAK = 4115; // 0x1013

/**
 * String property Simple_Case_Folding.
 * Corresponds to UCharacter.foldCase(int, boolean).
 */

public static final int SIMPLE_CASE_FOLDING = 16390; // 0x4006

/**
 * String property Simple_Lowercase_Mapping.
 * Corresponds to UCharacter.toLowerCase(int).
 */

public static final int SIMPLE_LOWERCASE_MAPPING = 16391; // 0x4007

/**
 * String property Simple_Titlecase_Mapping.
 * Corresponds to UCharacter.toTitleCase(int).
 */

public static final int SIMPLE_TITLECASE_MAPPING = 16392; // 0x4008

/**
 * String property Simple_Uppercase_Mapping.
 * Corresponds to UCharacter.toUpperCase(int).
 */

public static final int SIMPLE_UPPERCASE_MAPPING = 16393; // 0x4009

/**
 * <p>Binary property Soft_Dotted (new).
 * <p>Characters with a "soft dot", like i or j.
 * <p>An accent placed on these characters causes the dot to disappear.
 */

public static final int SOFT_DOTTED = 27; // 0x1b

/**
 * First constant for string Unicode properties.
 */

public static final int STRING_START = 16384; // 0x4000

/**
 * Binary property STerm (new in Unicode 4.0.1).
 * Sentence Terminal. Used in UAX #29: Text Boundaries
 * (http://www.unicode.org/reports/tr29/)
 */

public static final int S_TERM = 35; // 0x23

/**
 * <p>Binary property Terminal_Punctuation.
 * <p>Punctuation characters that generally mark the end of textual
 * units.
 */

public static final int TERMINAL_PUNCTUATION = 28; // 0x1c

/**
 * String property Titlecase_Mapping.
 * Corresponds to UCharacter.toTitleCase(String).
 */

public static final int TITLECASE_MAPPING = 16394; // 0x400a

/**
 * Enumerated property Trail_Canonical_Combining_Class.
 * ICU-specific property for the ccc of the last code point
 * of the decomposition, or lccc(c)=ccc(NFD(c)[last]).
 * Useful for checking for canonically ordered text;
 * see Normalizer.FCD and http://www.unicode.org/notes/tn5/#FCD .
 * Returns 8-bit numeric values like CANONICAL_COMBINING_CLASS.
 */

public static final int TRAIL_CANONICAL_COMBINING_CLASS = 4113; // 0x1011

/**
 * <p>Binary property Unified_Ideograph (new).
 * <p>For programmatic determination of Ideographic Description
 * Sequences.
 */

public static final int UNIFIED_IDEOGRAPH = 29; // 0x1d

/**
 * <p>Binary property Uppercase.
 * <p>Same as UCharacter.isUUppercase(), different from
 * UCharacter.isUpperCase().
 * <p>Lu+Other_Uppercase
 */

public static final int UPPERCASE = 30; // 0x1e

/**
 * String property Uppercase_Mapping.
 * Corresponds to UCharacter.toUpperCase(String).
 */

public static final int UPPERCASE_MAPPING = 16396; // 0x400c

/**
 * Binary property Variation_Selector (new in Unicode 4.0.1).
 * Indicates all those characters that qualify as Variation Selectors.
 * For details on the behavior of these characters,
 * see StandardizedVariants.html and 15.6 Variation Selectors.
 */

public static final int VARIATION_SELECTOR = 36; // 0x24

/**
 * Enumerated property Vertical_Orientation.
 * Used for UAX #50 Unicode Vertical Text Layout (https://www.unicode.org/reports/tr50/).
 * New as a UCD property in Unicode 10.0.
 */

public static final int VERTICAL_ORIENTATION = 4120; // 0x1018

/**
 * <p>Binary property White_Space.
 * <p>Same as UCharacter.isUWhiteSpace(), different from
 * UCharacter.isSpace() and UCharacter.isWhitespace().
 * Space characters+TAB+CR+LF-ZWSP-ZWNBSP
 */

public static final int WHITE_SPACE = 31; // 0x1f

/**
 * Enumerated property Word_Break (new in Unicode 4.1).
 * Used in UAX #29: Text Boundaries
 * (http://www.unicode.org/reports/tr29/)
 * Returns UCharacter.WordBreak values.
 */

public static final int WORD_BREAK = 4116; // 0x1014

/**
 * <p>Binary property XID_Continue.
 * <p>ID_Continue modified to allow closure under normalization forms
 * NFKC and NFKD.
 */

public static final int XID_CONTINUE = 32; // 0x20

/**
 * <p>Binary property XID_Start.
 * <p>ID_Start modified to allow closure under normalization forms NFKC
 * and NFKD.
 */

public static final int XID_START = 33; // 0x21
/**
 * Selector constants for UCharacter.getPropertyName() and
 * UCharacter.getPropertyValueName().  These selectors are used to
 * choose which name is returned for a given property or value.
 * All properties and values have a long name.  Most have a short
 * name, but some do not.  Unicode allows for additional names,
 * beyond the long and short name, which would be indicated by
 * LONG + i, where i=1, 2,...
 *
 * @see android.icu.lang.UCharacter#getPropertyName
 * @see android.icu.lang.UCharacter#getPropertyValueName
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public static interface NameChoice {

/**
 * Selector for the long name of a property or value.  All
 * properties and values have a long name.
 */

public static final int LONG = 1; // 0x1

/**
 * Selector for the abbreviated name of a property or value.
 * Most properties and values have a short name; those that do
 * not return null.
 */

public static final int SHORT = 0; // 0x0
}

}

