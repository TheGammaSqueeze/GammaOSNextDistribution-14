/* GENERATED SOURCE. DO NOT MODIFY. */
// ? 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/**
 *******************************************************************************
 * Copyright (C) 2001-2016 International Business Machines Corporation and
 * others. All Rights Reserved.
 *******************************************************************************
 */


package android.icu.lang;

import java.util.Locale;
import android.icu.util.ULocale;
import java.util.BitSet;

/**
 * Constants for ISO 15924 script codes, and related functions.
 *
 * <p>The current set of script code constants supports at least all scripts
 * that are encoded in the version of Unicode which ICU currently supports.
 * The names of the constants are usually derived from the
 * Unicode script property value aliases.
 * See UAX #24 Unicode Script Property (http://www.unicode.org/reports/tr24/)
 * and http://www.unicode.org/Public/UCD/latest/ucd/PropertyValueAliases.txt .
 *
 * <p>In addition, constants for many ISO 15924 script codes
 * are included, for use with language tags, CLDR data, and similar.
 * Some of those codes are not used in the Unicode Character Database (UCD).
 * For example, there are no characters that have a UCD script property value of
 * Hans or Hant. All Han ideographs have the Hani script property value in Unicode.
 *
 * <p>Private-use codes Qaaa..Qabx are not included, except as used in the UCD or in CLDR.
 *
 * <p>Starting with ICU 55, script codes are only added when their scripts
 * have been or will certainly be encoded in Unicode,
 * and have been assigned Unicode script property value aliases,
 * to ensure that their script names are stable and match the names of the constants.
 * Script codes like Latf and Aran that are not subject to separate encoding
 * may be added at any time.
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public final class UScript {

private UScript() { throw new RuntimeException("Stub!"); }

/**
 * Gets a script codes associated with the given locale or ISO 15924 abbreviation or name.
 * Returns MALAYAM given "Malayam" OR "Mlym".
 * Returns LATIN given "en" OR "en_US"
 * @param locale Locale
 * @return The script codes array. null if the the code cannot be found.
 */

public static int[] getCode(java.util.Locale locale) { throw new RuntimeException("Stub!"); }

/**
 * Gets a script codes associated with the given locale or ISO 15924 abbreviation or name.
 * Returns MALAYAM given "Malayam" OR "Mlym".
 * Returns LATIN given "en" OR "en_US"
 * @param locale ULocale
 * @return The script codes array. null if the the code cannot be found.
 */

public static int[] getCode(android.icu.util.ULocale locale) { throw new RuntimeException("Stub!"); }

/**
 * Gets the script codes associated with the given locale or ISO 15924 abbreviation or name.
 * Returns MALAYAM given "Malayam" OR "Mlym".
 * Returns LATIN given "en" OR "en_US"
 *
 * <p>Note: To search by short or long script alias only, use
 * {@link #getCodeFromName(java.lang.String)} instead.
 * That does a fast lookup with no access of the locale data.
 *
 * @param nameOrAbbrOrLocale name of the script or ISO 15924 code or locale
 * @return The script codes array. null if the the code cannot be found.
 */

public static int[] getCode(java.lang.String nameOrAbbrOrLocale) { throw new RuntimeException("Stub!"); }

/**
 * Returns the script code associated with the given Unicode script property alias
 * (name or abbreviation).
 * Short aliases are ISO 15924 script codes.
 * Returns MALAYAM given "Malayam" OR "Mlym".
 *
 * @param nameOrAbbr name of the script or ISO 15924 code
 * @return The script code value, or INVALID_CODE if the code cannot be found.
 */

public static int getCodeFromName(java.lang.String nameOrAbbr) { throw new RuntimeException("Stub!"); }

/**
 * Gets the script code associated with the given codepoint.
 * Returns UScript.MALAYAM given 0x0D02
 * @param codepoint UChar32 codepoint
 * @return The script code
 */

public static int getScript(int codepoint) { throw new RuntimeException("Stub!"); }

/**
 * Do the Script_Extensions of code point c contain script sc?
 * If c does not have explicit Script_Extensions, then this tests whether
 * c has the Script property value sc.
 *
 * <p>Some characters are commonly used in multiple scripts.
 * For more information, see UAX #24: http://www.unicode.org/reports/tr24/.
 *
 * @param c code point
 * @param sc script code
 * @return true if sc is in Script_Extensions(c)
 */

public static boolean hasScript(int c, int sc) { throw new RuntimeException("Stub!"); }

/**
 * Sets code point c's Script_Extensions as script code integers into the output BitSet.
 * <ul>
 * <li>If c does have Script_Extensions, then the return value is
 * the negative number of Script_Extensions codes (= -set.cardinality());
 * in this case, the Script property value
 * (normally Common or Inherited) is not included in the set.
 * <li>If c does not have Script_Extensions, then the one Script code is put into the set
 * and also returned.
 * <li>If c is not a valid code point, then the one {@link #UNKNOWN} code is put into the set
 * and also returned.
 * </ul>
 * In other words, if the return value is non-negative, it is c's single Script code
 * and the set contains exactly this Script code.
 * If the return value is -n, then the set contains c's n&gt;=2 Script_Extensions script codes.
 *
 * <p>Some characters are commonly used in multiple scripts.
 * For more information, see UAX #24: http://www.unicode.org/reports/tr24/.
 *
 * @param c code point
 * @param set set of script code integers; will be cleared, then bits are set
 *            corresponding to c's Script_Extensions
 * @return negative number of script codes in c's Script_Extensions,
 *         or the non-negative single Script value
 */

public static int getScriptExtensions(int c, java.util.BitSet set) { throw new RuntimeException("Stub!"); }

/**
 * Returns the long Unicode script name, if there is one.
 * Otherwise returns the 4-letter ISO 15924 script code.
 * Returns "Malayam" given MALAYALAM.
 *
 * @param scriptCode int script code
 * @return long script name as given in PropertyValueAliases.txt, or the 4-letter code
 * @throws java.lang.IllegalArgumentException if the script code is not valid
 */

public static java.lang.String getName(int scriptCode) { throw new RuntimeException("Stub!"); }

/**
 * Returns the 4-letter ISO 15924 script code,
 * which is the same as the short Unicode script name if Unicode has names for the script.
 * Returns "Mlym" given MALAYALAM.
 *
 * @param scriptCode int script code
 * @return short script name (4-letter code)
 * @throws java.lang.IllegalArgumentException if the script code is not valid
 */

public static java.lang.String getShortName(int scriptCode) { throw new RuntimeException("Stub!"); }

/**
 * Returns the script sample character string.
 * This string normally consists of one code point but might be longer.
 * The string is empty if the script is not encoded.
 *
 * @param script script code
 * @return the sample character string
 */

public static java.lang.String getSampleString(int script) { throw new RuntimeException("Stub!"); }

/**
 * Returns the script usage according to UAX #31 Unicode Identifier and Pattern Syntax.
 * Returns {@link android.icu.lang.UScript.ScriptUsage#NOT_ENCODED ScriptUsage#NOT_ENCODED} if the script is not encoded in Unicode.
 *
 * @param script script code
 * @return script usage
 * @see android.icu.lang.UScript.ScriptUsage
 */

public static android.icu.lang.UScript.ScriptUsage getUsage(int script) { throw new RuntimeException("Stub!"); }

/**
 * Returns true if the script is written right-to-left.
 * For example, Arab and Hebr.
 *
 * @param script script code
 * @return true if the script is right-to-left
 */

public static boolean isRightToLeft(int script) { throw new RuntimeException("Stub!"); }

/**
 * Returns true if the script allows line breaks between letters (excluding hyphenation).
 * Such a script typically requires dictionary-based line breaking.
 * For example, Hani and Thai.
 *
 * @param script script code
 * @return true if the script allows line breaks between letters
 */

public static boolean breaksBetweenLetters(int script) { throw new RuntimeException("Stub!"); }

/**
 * Returns true if in modern (or most recent) usage of the script case distinctions are customary.
 * For example, Latn and Cyrl.
 *
 * @param script script code
 * @return true if the script is cased
 */

public static boolean isCased(int script) { throw new RuntimeException("Stub!"); }

/**
 * ISO 15924 script code
 */

public static final int ADLAM = 167; // 0xa7

/**
 * ISO 15924 script code
 */

public static final int AFAKA = 147; // 0x93

/**
 * ISO 15924 script code
 */

public static final int AHOM = 161; // 0xa1

/**
 * ISO 15924 script code
 */

public static final int ANATOLIAN_HIEROGLYPHS = 156; // 0x9c

/**
 * Arabic
 */

public static final int ARABIC = 2; // 0x2

/**
 * Armenian
 */

public static final int ARMENIAN = 3; // 0x3

/**
 * ISO 15924 script code
 */

public static final int AVESTAN = 117; // 0x75

/**
 * ISO 15924 script code
 */

public static final int BALINESE = 62; // 0x3e

/**
 * ISO 15924 script code
 */

public static final int BAMUM = 130; // 0x82

/**
 * ISO 15924 script code
 */

public static final int BASSA_VAH = 134; // 0x86

/**
 * ISO 15924 script code
 */

public static final int BATAK = 63; // 0x3f

/**
 * Bengali
 */

public static final int BENGALI = 4; // 0x4

/**
 * ISO 15924 script code
 */

public static final int BHAIKSUKI = 168; // 0xa8

/**
 * ISO 15924 script code
 */

public static final int BLISSYMBOLS = 64; // 0x40

/**
 * ISO 15924 script code
 */

public static final int BOOK_PAHLAVI = 124; // 0x7c

/**
 * Bopomofo
 */

public static final int BOPOMOFO = 5; // 0x5

/**
 * ISO 15924 script code
 */

public static final int BRAHMI = 65; // 0x41

/**
 * Braille
 * Script in Unicode 4
 *
 */

public static final int BRAILLE = 46; // 0x2e

/**
 * Script in Unicode 4.1
 */

public static final int BUGINESE = 55; // 0x37

/**
 * Buhid
 */

public static final int BUHID = 44; // 0x2c

/**
 * Unified Canadian Aboriginal Symbols
 */

public static final int CANADIAN_ABORIGINAL = 40; // 0x28

/**
 * ISO 15924 script code
 */

public static final int CARIAN = 104; // 0x68

/**
 * ISO 15924 script code
 */

public static final int CAUCASIAN_ALBANIAN = 159; // 0x9f

/**
 * ISO 15924 script code
 */

public static final int CHAKMA = 118; // 0x76

/**
 * ISO 15924 script code
 */

public static final int CHAM = 66; // 0x42

/**
 * Cherokee
 */

public static final int CHEROKEE = 6; // 0x6

/***/

public static final int CHORASMIAN = 189; // 0xbd

/**
 * ISO 15924 script code
 */

public static final int CIRTH = 67; // 0x43

/**
 * Common
 */

public static final int COMMON = 0; // 0x0

/**
 * Coptic
 */

public static final int COPTIC = 7; // 0x7

/**
 * ISO 15924 script code
 */

public static final int CUNEIFORM = 101; // 0x65

/**
 * Cypriot
 * Script in Unicode 4
 *
 */

public static final int CYPRIOT = 47; // 0x2f

/**
 * Cyrillic
 */

public static final int CYRILLIC = 8; // 0x8

/**
 * ISO 15924 script code
 */

public static final int DEMOTIC_EGYPTIAN = 69; // 0x45

/**
 * Deseret
 */

public static final int DESERET = 9; // 0x9

/**
 * Devanagari
 */

public static final int DEVANAGARI = 10; // 0xa

/***/

public static final int DIVES_AKURU = 190; // 0xbe

/**
 * ISO 15924 script code
 */

public static final int DOGRA = 178; // 0xb2

/**
 * ISO 15924 script code
 */

public static final int DUPLOYAN = 135; // 0x87

/**
 * ISO 15924 script code
 */

public static final int EASTERN_SYRIAC = 97; // 0x61

/**
 * ISO 15924 script code
 */

public static final int EGYPTIAN_HIEROGLYPHS = 71; // 0x47

/**
 * ISO 15924 script code
 */

public static final int ELBASAN = 136; // 0x88

/***/

public static final int ELYMAIC = 185; // 0xb9

/**
 * ISO 15924 script code
 */

public static final int ESTRANGELO_SYRIAC = 95; // 0x5f

/**
 * Ethiopic
 */

public static final int ETHIOPIC = 11; // 0xb

/**
 * Georgian
 */

public static final int GEORGIAN = 12; // 0xc

/**
 * Script in Unicode 4.1
 */

public static final int GLAGOLITIC = 56; // 0x38

/**
 * Gothic
 */

public static final int GOTHIC = 13; // 0xd

/**
 * ISO 15924 script code
 */

public static final int GRANTHA = 137; // 0x89

/**
 * Greek
 */

public static final int GREEK = 14; // 0xe

/**
 * Gujarati
 */

public static final int GUJARATI = 15; // 0xf

/***/

public static final int GUNJALA_GONDI = 179; // 0xb3

/**
 * Gurmukhi
 */

public static final int GURMUKHI = 16; // 0x10

/**
 * Han
 */

public static final int HAN = 17; // 0x11

/**
 * Hangul
 */

public static final int HANGUL = 18; // 0x12

/***/

public static final int HANIFI_ROHINGYA = 182; // 0xb6

/**
 * Hanunooo
 */

public static final int HANUNOO = 43; // 0x2b

/**
 * ISO 15924 script code
 */

public static final int HAN_WITH_BOPOMOFO = 172; // 0xac

/**
 * ISO 15924 script code
 */

public static final int HARAPPAN_INDUS = 77; // 0x4d

/**
 * ISO 15924 script code
 */

public static final int HATRAN = 162; // 0xa2

/**
 * Hebrew
 */

public static final int HEBREW = 19; // 0x13

/**
 * ISO 15924 script code
 */

public static final int HIERATIC_EGYPTIAN = 70; // 0x46

/**
 * Hiragana
 */

public static final int HIRAGANA = 20; // 0x14

/**
 * ISO 15924 script code
 */

public static final int IMPERIAL_ARAMAIC = 116; // 0x74

/**
 * Inherited
 */

public static final int INHERITED = 1; // 0x1

/**
 * ISO 15924 script code
 */

public static final int INSCRIPTIONAL_PAHLAVI = 122; // 0x7a

/**
 * ISO 15924 script code
 */

public static final int INSCRIPTIONAL_PARTHIAN = 125; // 0x7d

/**
 * Invalid code
 */

public static final int INVALID_CODE = -1; // 0xffffffff

/**
 * ISO 15924 script code
 */

public static final int JAMO = 173; // 0xad

/**
 * ISO 15924 script code
 */

public static final int JAPANESE = 105; // 0x69

/**
 * ISO 15924 script code
 */

public static final int JAVANESE = 78; // 0x4e

/**
 * ISO 15924 script code
 */

public static final int JURCHEN = 148; // 0x94

/**
 * ISO 15924 script code
 */

public static final int KAITHI = 120; // 0x78

/**
 * Kannada
 */

public static final int KANNADA = 21; // 0x15

/**
 * Katakana
 */

public static final int KATAKANA = 22; // 0x16

/**
 * Script in Unicode 4.0.1
 */

public static final int KATAKANA_OR_HIRAGANA = 54; // 0x36

/**
 * ISO 15924 script code
 */

public static final int KAYAH_LI = 79; // 0x4f

/**
 * Script in Unicode 4.1
 */

public static final int KHAROSHTHI = 57; // 0x39

/***/

public static final int KHITAN_SMALL_SCRIPT = 191; // 0xbf

/**
 * Khmer
 */

public static final int KHMER = 23; // 0x17

/**
 * ISO 15924 script code
 */

public static final int KHOJKI = 157; // 0x9d

/**
 * ISO 15924 script code
 */

public static final int KHUDAWADI = 145; // 0x91

/**
 * ISO 15924 script code
 */

public static final int KHUTSURI = 72; // 0x48

/**
 * ISO 15924 script code
 */

public static final int KOREAN = 119; // 0x77

/**
 * ISO 15924 script code
 */

public static final int KPELLE = 138; // 0x8a

/**
 * ISO 15924 script code
 */

public static final int LANNA = 106; // 0x6a

/**
 * Lao
 */

public static final int LAO = 24; // 0x18

/**
 * Latin
 */

public static final int LATIN = 25; // 0x19

/**
 * ISO 15924 script code
 */

public static final int LATIN_FRAKTUR = 80; // 0x50

/**
 * ISO 15924 script code
 */

public static final int LATIN_GAELIC = 81; // 0x51

/**
 * ISO 15924 script code
 */

public static final int LEPCHA = 82; // 0x52

/**
 * Limbu
 * Script in Unicode 4
 *
 */

public static final int LIMBU = 48; // 0x30

/**
 * ISO 15924 script code
 */

public static final int LINEAR_A = 83; // 0x53

/**
 * Linear B
 * Script in Unicode 4
 *
 */

public static final int LINEAR_B = 49; // 0x31

/**
 * ISO 15924 script code
 */

public static final int LISU = 131; // 0x83

/**
 * ISO 15924 script code
 */

public static final int LOMA = 139; // 0x8b

/**
 * ISO 15924 script code
 */

public static final int LYCIAN = 107; // 0x6b

/**
 * ISO 15924 script code
 */

public static final int LYDIAN = 108; // 0x6c

/**
 * ISO 15924 script code
 */

public static final int MAHAJANI = 160; // 0xa0

/***/

public static final int MAKASAR = 180; // 0xb4

/**
 * Malayalam
 */

public static final int MALAYALAM = 26; // 0x1a

/**
 * ISO 15924 script code
 */

public static final int MANDAEAN = 84; // 0x54

/**
 * ISO 15924 script code
 */

public static final int MANDAIC = 84; // 0x54

/**
 * ISO 15924 script code
 */

public static final int MANICHAEAN = 121; // 0x79

/**
 * ISO 15924 script code
 */

public static final int MARCHEN = 169; // 0xa9

/**
 * ISO 15924 script code
 */

public static final int MASARAM_GONDI = 175; // 0xaf

/**
 * ISO 15924 script code
 */

public static final int MATHEMATICAL_NOTATION = 128; // 0x80

/**
 * ISO 15924 script code
 */

public static final int MAYAN_HIEROGLYPHS = 85; // 0x55

/***/

public static final int MEDEFAIDRIN = 181; // 0xb5

/**
 * ISO 15924 script code
 */

public static final int MEITEI_MAYEK = 115; // 0x73

/**
 * Mende Kikakui
 * ISO 15924 script code
 */

public static final int MENDE = 140; // 0x8c

/**
 * ISO 15924 script code
 */

public static final int MEROITIC = 86; // 0x56

/**
 * ISO 15924 script code
 */

public static final int MEROITIC_CURSIVE = 141; // 0x8d

/**
 * ISO 15924 script code
 */

public static final int MEROITIC_HIEROGLYPHS = 86; // 0x56

/**
 * ISO 15924 script code
 */

public static final int MIAO = 92; // 0x5c

/**
 * ISO 15924 script code
 */

public static final int MODI = 163; // 0xa3

/**
 * Mangolian
 */

public static final int MONGOLIAN = 27; // 0x1b

/**
 * ISO 15924 script code
 */

public static final int MOON = 114; // 0x72

/**
 * ISO 15924 script code
 */

public static final int MRO = 149; // 0x95

/**
 * ISO 15924 script code
 */

public static final int MULTANI = 164; // 0xa4

/**
 * Myammar
 */

public static final int MYANMAR = 28; // 0x1c

/**
 * ISO 15924 script code
 */

public static final int NABATAEAN = 143; // 0x8f

/**
 * ISO 15924 script code
 */

public static final int NAKHI_GEBA = 132; // 0x84

/***/

public static final int NANDINAGARI = 187; // 0xbb

/**
 * ISO 15924 script code
 */

public static final int NEWA = 170; // 0xaa

/**
 * Script in Unicode 4.1
 */

public static final int NEW_TAI_LUE = 59; // 0x3b

/**
 * ISO 15924 script code
 */

public static final int NKO = 87; // 0x57

/**
 * ISO 15924 script code
 */

public static final int NUSHU = 150; // 0x96

/***/

public static final int NYIAKENG_PUACHUE_HMONG = 186; // 0xba

/**
 * Ogham
 */

public static final int OGHAM = 29; // 0x1d

/**
 * ISO 15924 script code
 */

public static final int OLD_CHURCH_SLAVONIC_CYRILLIC = 68; // 0x44

/**
 * ISO 15924 script code
 */

public static final int OLD_HUNGARIAN = 76; // 0x4c

/**
 * Old Itallic
 */

public static final int OLD_ITALIC = 30; // 0x1e

/**
 * ISO 15924 script code
 */

public static final int OLD_NORTH_ARABIAN = 142; // 0x8e

/**
 * ISO 15924 script code
 */

public static final int OLD_PERMIC = 89; // 0x59

/**
 * Script in Unicode 4.1
 */

public static final int OLD_PERSIAN = 61; // 0x3d

/***/

public static final int OLD_SOGDIAN = 184; // 0xb8

/**
 * ISO 15924 script code
 */

public static final int OLD_SOUTH_ARABIAN = 133; // 0x85

/**
 * ISO 15924 script code
 */

public static final int OL_CHIKI = 109; // 0x6d

/**
 * Oriya
 */

public static final int ORIYA = 31; // 0x1f

/**
 * ISO 15924 script code
 */

public static final int ORKHON = 88; // 0x58

/**
 * ISO 15924 script code
 */

public static final int OSAGE = 171; // 0xab

/**
 * Osmanya
 * Script in Unicode 4
 *
 */

public static final int OSMANYA = 50; // 0x32

/**
 * ISO 15924 script code
 */

public static final int PAHAWH_HMONG = 75; // 0x4b

/**
 * ISO 15924 script code
 */

public static final int PALMYRENE = 144; // 0x90

/**
 * ISO 15924 script code
 */

public static final int PAU_CIN_HAU = 165; // 0xa5

/**
 * ISO 15924 script code
 */

public static final int PHAGS_PA = 90; // 0x5a

/**
 * ISO 15924 script code
 */

public static final int PHOENICIAN = 91; // 0x5b

/**
 * ISO 15924 script code
 */

public static final int PHONETIC_POLLARD = 92; // 0x5c

/**
 * ISO 15924 script code
 */

public static final int PSALTER_PAHLAVI = 123; // 0x7b

/**
 * ISO 15924 script code
 */

public static final int REJANG = 110; // 0x6e

/**
 * ISO 15924 script code
 */

public static final int RONGORONGO = 93; // 0x5d

/**
 * Runic
 */

public static final int RUNIC = 32; // 0x20

/**
 * ISO 15924 script code
 */

public static final int SAMARITAN = 126; // 0x7e

/**
 * ISO 15924 script code
 */

public static final int SARATI = 94; // 0x5e

/**
 * ISO 15924 script code
 */

public static final int SAURASHTRA = 111; // 0x6f

/**
 * ISO 15924 script code
 */

public static final int SHARADA = 151; // 0x97

/**
 * Shavian
 * Script in Unicode 4
 *
 */

public static final int SHAVIAN = 51; // 0x33

/**
 * ISO 15924 script code
 */

public static final int SIDDHAM = 166; // 0xa6

/**
 * ISO 15924 script code for Sutton SignWriting
 */

public static final int SIGN_WRITING = 112; // 0x70

/**
 * ISO 15924 script code
 */

public static final int SIMPLIFIED_HAN = 73; // 0x49

/**
 * ISO 15924 script code
 */

public static final int SINDHI = 145; // 0x91

/**
 * Sinhala
 */

public static final int SINHALA = 33; // 0x21

/***/

public static final int SOGDIAN = 183; // 0xb7

/**
 * ISO 15924 script code
 */

public static final int SORA_SOMPENG = 152; // 0x98

/**
 * ISO 15924 script code
 */

public static final int SOYOMBO = 176; // 0xb0

/**
 * ISO 15924 script code
 */

public static final int SUNDANESE = 113; // 0x71

/**
 * Script in Unicode 4.1
 */

public static final int SYLOTI_NAGRI = 58; // 0x3a

/**
 * ISO 15924 script code
 */

public static final int SYMBOLS = 129; // 0x81

/**
 * ISO 15924 script code
 */

public static final int SYMBOLS_EMOJI = 174; // 0xae

/**
 * Syriac
 */

public static final int SYRIAC = 34; // 0x22

/**
 * Tagalog
 */

public static final int TAGALOG = 42; // 0x2a

/**
 * Tagbanwa
 */

public static final int TAGBANWA = 45; // 0x2d

/**
 * Tai Le
 * Script in Unicode 4
 *
 */

public static final int TAI_LE = 52; // 0x34

/**
 * ISO 15924 script code
 */

public static final int TAI_VIET = 127; // 0x7f

/**
 * ISO 15924 script code
 */

public static final int TAKRI = 153; // 0x99

/**
 * Tamil
 */

public static final int TAMIL = 35; // 0x23

/**
 * ISO 15924 script code
 */

public static final int TANGUT = 154; // 0x9a

/**
 * Telugu
 */

public static final int TELUGU = 36; // 0x24

/**
 * ISO 15924 script code
 */

public static final int TENGWAR = 98; // 0x62

/**
 * Thana
 */

public static final int THAANA = 37; // 0x25

/**
 * Thai
 */

public static final int THAI = 38; // 0x26

/**
 * Tibetan
 */

public static final int TIBETAN = 39; // 0x27

/**
 * Script in Unicode 4.1
 */

public static final int TIFINAGH = 60; // 0x3c

/**
 * ISO 15924 script code
 */

public static final int TIRHUTA = 158; // 0x9e

/**
 * ISO 15924 script code
 */

public static final int TRADITIONAL_HAN = 74; // 0x4a

/**
 * Unified Canadian Aboriginal Symbols (alias)
 */

public static final int UCAS = 40; // 0x28

/**
 * Ugaritic
 * Script in Unicode 4
 *
 */

public static final int UGARITIC = 53; // 0x35

/**
 * ISO 15924 script code
 */

public static final int UNKNOWN = 103; // 0x67

/**
 * ISO 15924 script code
 */

public static final int UNWRITTEN_LANGUAGES = 102; // 0x66

/**
 * ISO 15924 script code
 */

public static final int VAI = 99; // 0x63

/**
 * ISO 15924 script code
 */

public static final int VISIBLE_SPEECH = 100; // 0x64

/***/

public static final int WANCHO = 188; // 0xbc

/**
 * ISO 15924 script code
 */

public static final int WARANG_CITI = 146; // 0x92

/**
 * ISO 15924 script code
 */

public static final int WESTERN_SYRIAC = 96; // 0x60

/**
 * ISO 15924 script code
 */

public static final int WOLEAI = 155; // 0x9b

/***/

public static final int YEZIDI = 192; // 0xc0

/**
 * Yi syllables
 */

public static final int YI = 41; // 0x29

/**
 * ISO 15924 script code
 */

public static final int ZANABAZAR_SQUARE = 177; // 0xb1
/**
 * Script usage constants.
 * See UAX #31 Unicode Identifier and Pattern Syntax.
 * http://www.unicode.org/reports/tr31/#Table_Candidate_Characters_for_Exclusion_from_Identifiers
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public enum ScriptUsage {
/**
 * Not encoded in Unicode.
 */

NOT_ENCODED,
/**
 * Unknown script usage.
 */

UNKNOWN,
/**
 * Candidate for Exclusion from Identifiers.
 */

EXCLUDED,
/**
 * Limited Use script.
 */

LIMITED_USE,
/**
 * Aspirational Use script.
 */

ASPIRATIONAL,
/**
 * Recommended script.
 */

RECOMMENDED;
}

}

