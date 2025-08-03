/* GENERATED SOURCE. DO NOT MODIFY. */
/* Run external/icu/tools/icu4c_srcgen/generate_ndk.py to regenerate */
// © 2018 and later: Unicode, Inc. and others.'
// License & terms of use: http://www.unicode.org/copyright.html

#include <unicode/ubidi.h>
#include <unicode/ubrk.h>
#include <unicode/uchar.h>
#include <unicode/ucol.h>
#include <unicode/uenum.h>
#include <unicode/uldnames.h>
#include <unicode/uloc.h>
#include <unicode/ulocdata.h>
#include <unicode/unorm2.h>
#include <unicode/uscript.h>
#include <unicode/ustring.h>
#include <unicode/utext.h>
#include <unicode/utrans.h>
#include <unicode/utypes.h>
#include <unicode/uversion.h>

/* Disable the macros in urename.h renaming the below C function definitions. */
#undef u_charAge
#undef u_charDigitValue
#undef u_charDirection
#undef u_charFromName
#undef u_charMirror
#undef u_charName
#undef u_charType
#undef u_countChar32
#undef u_digit
#undef u_enumCharNames
#undef u_enumCharTypes
#undef u_errorName
#undef u_foldCase
#undef u_forDigit
#undef u_getBidiPairedBracket
#undef u_getCombiningClass
#undef u_getIntPropertyMaxValue
#undef u_getIntPropertyMinValue
#undef u_getIntPropertyValue
#undef u_getNumericValue
#undef u_getPropertyEnum
#undef u_getPropertyName
#undef u_getPropertyValueEnum
#undef u_getPropertyValueName
#undef u_getUnicodeVersion
#undef u_getVersion
#undef u_hasBinaryProperty
#undef u_isIDIgnorable
#undef u_isIDPart
#undef u_isIDStart
#undef u_isISOControl
#undef u_isJavaIDPart
#undef u_isJavaIDStart
#undef u_isJavaSpaceChar
#undef u_isMirrored
#undef u_isUAlphabetic
#undef u_isULowercase
#undef u_isUUppercase
#undef u_isUWhiteSpace
#undef u_isWhitespace
#undef u_isalnum
#undef u_isalpha
#undef u_isbase
#undef u_isblank
#undef u_iscntrl
#undef u_isdefined
#undef u_isdigit
#undef u_isgraph
#undef u_islower
#undef u_isprint
#undef u_ispunct
#undef u_isspace
#undef u_istitle
#undef u_isupper
#undef u_isxdigit
#undef u_memcasecmp
#undef u_memchr
#undef u_memchr32
#undef u_memcmp
#undef u_memcmpCodePointOrder
#undef u_memcpy
#undef u_memmove
#undef u_memrchr
#undef u_memrchr32
#undef u_memset
#undef u_strCaseCompare
#undef u_strCompare
#undef u_strFindFirst
#undef u_strFindLast
#undef u_strFoldCase
#undef u_strFromUTF32
#undef u_strFromUTF32WithSub
#undef u_strFromUTF8
#undef u_strFromUTF8Lenient
#undef u_strFromUTF8WithSub
#undef u_strHasMoreChar32Than
#undef u_strToLower
#undef u_strToTitle
#undef u_strToUTF32
#undef u_strToUTF32WithSub
#undef u_strToUTF8
#undef u_strToUTF8WithSub
#undef u_strToUpper
#undef u_strcasecmp
#undef u_strcat
#undef u_strchr
#undef u_strchr32
#undef u_strcmp
#undef u_strcmpCodePointOrder
#undef u_strcpy
#undef u_strcspn
#undef u_strlen
#undef u_strncasecmp
#undef u_strncat
#undef u_strncmp
#undef u_strncmpCodePointOrder
#undef u_strncpy
#undef u_strpbrk
#undef u_strrchr
#undef u_strrchr32
#undef u_strrstr
#undef u_strspn
#undef u_strstr
#undef u_strtok_r
#undef u_tolower
#undef u_totitle
#undef u_toupper
#undef u_versionToString
#undef ubidi_close
#undef ubidi_countParagraphs
#undef ubidi_countRuns
#undef ubidi_getBaseDirection
#undef ubidi_getClassCallback
#undef ubidi_getCustomizedClass
#undef ubidi_getDirection
#undef ubidi_getLength
#undef ubidi_getLevelAt
#undef ubidi_getLevels
#undef ubidi_getLogicalIndex
#undef ubidi_getLogicalMap
#undef ubidi_getLogicalRun
#undef ubidi_getParaLevel
#undef ubidi_getParagraph
#undef ubidi_getParagraphByIndex
#undef ubidi_getProcessedLength
#undef ubidi_getReorderingMode
#undef ubidi_getReorderingOptions
#undef ubidi_getResultLength
#undef ubidi_getText
#undef ubidi_getVisualIndex
#undef ubidi_getVisualMap
#undef ubidi_getVisualRun
#undef ubidi_invertMap
#undef ubidi_isInverse
#undef ubidi_isOrderParagraphsLTR
#undef ubidi_open
#undef ubidi_openSized
#undef ubidi_orderParagraphsLTR
#undef ubidi_reorderLogical
#undef ubidi_reorderVisual
#undef ubidi_setClassCallback
#undef ubidi_setContext
#undef ubidi_setInverse
#undef ubidi_setLine
#undef ubidi_setPara
#undef ubidi_setReorderingMode
#undef ubidi_setReorderingOptions
#undef ubidi_writeReordered
#undef ubidi_writeReverse
#undef ubrk_clone
#undef ubrk_close
#undef ubrk_countAvailable
#undef ubrk_current
#undef ubrk_first
#undef ubrk_following
#undef ubrk_getAvailable
#undef ubrk_getRuleStatus
#undef ubrk_getRuleStatusVec
#undef ubrk_isBoundary
#undef ubrk_last
#undef ubrk_next
#undef ubrk_open
#undef ubrk_preceding
#undef ubrk_previous
#undef ubrk_setText
#undef ubrk_setUText
#undef ucol_clone
#undef ucol_close
#undef ucol_countAvailable
#undef ucol_getAttribute
#undef ucol_getAvailable
#undef ucol_getDisplayName
#undef ucol_getEquivalentReorderCodes
#undef ucol_getFunctionalEquivalent
#undef ucol_getKeywordValues
#undef ucol_getKeywordValuesForLocale
#undef ucol_getKeywords
#undef ucol_getMaxVariable
#undef ucol_getReorderCodes
#undef ucol_getSortKey
#undef ucol_getStrength
#undef ucol_mergeSortkeys
#undef ucol_open
#undef ucol_openAvailableLocales
#undef ucol_openRules
#undef ucol_setAttribute
#undef ucol_setMaxVariable
#undef ucol_setReorderCodes
#undef ucol_setStrength
#undef ucol_strcoll
#undef ucol_strcollUTF8
#undef uenum_close
#undef uenum_count
#undef uenum_next
#undef uenum_openCharStringsEnumeration
#undef uenum_openUCharStringsEnumeration
#undef uenum_reset
#undef uenum_unext
#undef uldn_close
#undef uldn_getContext
#undef uldn_getDialectHandling
#undef uldn_getLocale
#undef uldn_keyDisplayName
#undef uldn_keyValueDisplayName
#undef uldn_languageDisplayName
#undef uldn_localeDisplayName
#undef uldn_open
#undef uldn_openForContext
#undef uldn_regionDisplayName
#undef uldn_scriptCodeDisplayName
#undef uldn_scriptDisplayName
#undef uldn_variantDisplayName
#undef uloc_acceptLanguage
#undef uloc_addLikelySubtags
#undef uloc_canonicalize
#undef uloc_countAvailable
#undef uloc_forLanguageTag
#undef uloc_getAvailable
#undef uloc_getBaseName
#undef uloc_getCharacterOrientation
#undef uloc_getCountry
#undef uloc_getDefault
#undef uloc_getDisplayCountry
#undef uloc_getDisplayKeyword
#undef uloc_getDisplayKeywordValue
#undef uloc_getDisplayLanguage
#undef uloc_getDisplayName
#undef uloc_getDisplayScript
#undef uloc_getDisplayVariant
#undef uloc_getISO3Country
#undef uloc_getISO3Language
#undef uloc_getISOCountries
#undef uloc_getISOLanguages
#undef uloc_getKeywordValue
#undef uloc_getLanguage
#undef uloc_getLineOrientation
#undef uloc_getName
#undef uloc_getScript
#undef uloc_getVariant
#undef uloc_isRightToLeft
#undef uloc_minimizeSubtags
#undef uloc_openKeywords
#undef uloc_setKeywordValue
#undef uloc_toLanguageTag
#undef uloc_toLegacyKey
#undef uloc_toLegacyType
#undef uloc_toUnicodeLocaleKey
#undef uloc_toUnicodeLocaleType
#undef ulocdata_getCLDRVersion
#undef unorm2_append
#undef unorm2_close
#undef unorm2_composePair
#undef unorm2_getCombiningClass
#undef unorm2_getDecomposition
#undef unorm2_getNFCInstance
#undef unorm2_getNFDInstance
#undef unorm2_getNFKCCasefoldInstance
#undef unorm2_getNFKCInstance
#undef unorm2_getNFKDInstance
#undef unorm2_getRawDecomposition
#undef unorm2_hasBoundaryAfter
#undef unorm2_hasBoundaryBefore
#undef unorm2_isInert
#undef unorm2_isNormalized
#undef unorm2_normalize
#undef unorm2_normalizeSecondAndAppend
#undef unorm2_quickCheck
#undef unorm2_spanQuickCheckYes
#undef uscript_breaksBetweenLetters
#undef uscript_getCode
#undef uscript_getName
#undef uscript_getSampleString
#undef uscript_getScript
#undef uscript_getScriptExtensions
#undef uscript_getShortName
#undef uscript_getUsage
#undef uscript_hasScript
#undef uscript_isCased
#undef uscript_isRightToLeft
#undef utext_char32At
#undef utext_clone
#undef utext_close
#undef utext_current32
#undef utext_equals
#undef utext_extract
#undef utext_getNativeIndex
#undef utext_moveIndex32
#undef utext_nativeLength
#undef utext_next32
#undef utext_next32From
#undef utext_openUChars
#undef utext_openUTF8
#undef utext_previous32
#undef utext_previous32From
#undef utext_setNativeIndex
#undef utrans_clone
#undef utrans_close
#undef utrans_openIDs
#undef utrans_openInverse
#undef utrans_openU
#undef utrans_setFilter
#undef utrans_toRules
#undef utrans_trans
#undef utrans_transIncremental
#undef utrans_transIncrementalUChars
#undef utrans_transUChars

extern "C" {
void u_charAge(UChar32 c, UVersionInfo versionArray) {
  U_ICU_ENTRY_POINT_RENAME(u_charAge)(c, versionArray);
}
int32_t u_charDigitValue(UChar32 c) {
  return U_ICU_ENTRY_POINT_RENAME(u_charDigitValue)(c);
}
UCharDirection u_charDirection(UChar32 c) {
  return U_ICU_ENTRY_POINT_RENAME(u_charDirection)(c);
}
UChar32 u_charFromName(UCharNameChoice nameChoice, const char * name, UErrorCode * pErrorCode) {
  return U_ICU_ENTRY_POINT_RENAME(u_charFromName)(nameChoice, name, pErrorCode);
}
UChar32 u_charMirror(UChar32 c) {
  return U_ICU_ENTRY_POINT_RENAME(u_charMirror)(c);
}
int32_t u_charName(UChar32 code, UCharNameChoice nameChoice, char * buffer, int32_t bufferLength, UErrorCode * pErrorCode) {
  return U_ICU_ENTRY_POINT_RENAME(u_charName)(code, nameChoice, buffer, bufferLength, pErrorCode);
}
int8_t u_charType(UChar32 c) {
  return U_ICU_ENTRY_POINT_RENAME(u_charType)(c);
}
int32_t u_countChar32(const UChar * s, int32_t length) {
  return U_ICU_ENTRY_POINT_RENAME(u_countChar32)(s, length);
}
int32_t u_digit(UChar32 ch, int8_t radix) {
  return U_ICU_ENTRY_POINT_RENAME(u_digit)(ch, radix);
}
void u_enumCharNames(UChar32 start, UChar32 limit, UEnumCharNamesFn * fn, void * context, UCharNameChoice nameChoice, UErrorCode * pErrorCode) {
  U_ICU_ENTRY_POINT_RENAME(u_enumCharNames)(start, limit, fn, context, nameChoice, pErrorCode);
}
void u_enumCharTypes(UCharEnumTypeRange * enumRange, const void * context) {
  U_ICU_ENTRY_POINT_RENAME(u_enumCharTypes)(enumRange, context);
}
const char * u_errorName(UErrorCode code) {
  return U_ICU_ENTRY_POINT_RENAME(u_errorName)(code);
}
UChar32 u_foldCase(UChar32 c, uint32_t options) {
  return U_ICU_ENTRY_POINT_RENAME(u_foldCase)(c, options);
}
UChar32 u_forDigit(int32_t digit, int8_t radix) {
  return U_ICU_ENTRY_POINT_RENAME(u_forDigit)(digit, radix);
}
UChar32 u_getBidiPairedBracket(UChar32 c) {
  return U_ICU_ENTRY_POINT_RENAME(u_getBidiPairedBracket)(c);
}
uint8_t u_getCombiningClass(UChar32 c) {
  return U_ICU_ENTRY_POINT_RENAME(u_getCombiningClass)(c);
}
int32_t u_getIntPropertyMaxValue(UProperty which) {
  return U_ICU_ENTRY_POINT_RENAME(u_getIntPropertyMaxValue)(which);
}
int32_t u_getIntPropertyMinValue(UProperty which) {
  return U_ICU_ENTRY_POINT_RENAME(u_getIntPropertyMinValue)(which);
}
int32_t u_getIntPropertyValue(UChar32 c, UProperty which) {
  return U_ICU_ENTRY_POINT_RENAME(u_getIntPropertyValue)(c, which);
}
double u_getNumericValue(UChar32 c) {
  return U_ICU_ENTRY_POINT_RENAME(u_getNumericValue)(c);
}
UProperty u_getPropertyEnum(const char * alias) {
  return U_ICU_ENTRY_POINT_RENAME(u_getPropertyEnum)(alias);
}
const char * u_getPropertyName(UProperty property, UPropertyNameChoice nameChoice) {
  return U_ICU_ENTRY_POINT_RENAME(u_getPropertyName)(property, nameChoice);
}
int32_t u_getPropertyValueEnum(UProperty property, const char * alias) {
  return U_ICU_ENTRY_POINT_RENAME(u_getPropertyValueEnum)(property, alias);
}
const char * u_getPropertyValueName(UProperty property, int32_t value, UPropertyNameChoice nameChoice) {
  return U_ICU_ENTRY_POINT_RENAME(u_getPropertyValueName)(property, value, nameChoice);
}
void u_getUnicodeVersion(UVersionInfo versionArray) {
  U_ICU_ENTRY_POINT_RENAME(u_getUnicodeVersion)(versionArray);
}
void u_getVersion(UVersionInfo versionArray) {
  U_ICU_ENTRY_POINT_RENAME(u_getVersion)(versionArray);
}
UBool u_hasBinaryProperty(UChar32 c, UProperty which) {
  return U_ICU_ENTRY_POINT_RENAME(u_hasBinaryProperty)(c, which);
}
UBool u_isIDIgnorable(UChar32 c) {
  return U_ICU_ENTRY_POINT_RENAME(u_isIDIgnorable)(c);
}
UBool u_isIDPart(UChar32 c) {
  return U_ICU_ENTRY_POINT_RENAME(u_isIDPart)(c);
}
UBool u_isIDStart(UChar32 c) {
  return U_ICU_ENTRY_POINT_RENAME(u_isIDStart)(c);
}
UBool u_isISOControl(UChar32 c) {
  return U_ICU_ENTRY_POINT_RENAME(u_isISOControl)(c);
}
UBool u_isJavaIDPart(UChar32 c) {
  return U_ICU_ENTRY_POINT_RENAME(u_isJavaIDPart)(c);
}
UBool u_isJavaIDStart(UChar32 c) {
  return U_ICU_ENTRY_POINT_RENAME(u_isJavaIDStart)(c);
}
UBool u_isJavaSpaceChar(UChar32 c) {
  return U_ICU_ENTRY_POINT_RENAME(u_isJavaSpaceChar)(c);
}
UBool u_isMirrored(UChar32 c) {
  return U_ICU_ENTRY_POINT_RENAME(u_isMirrored)(c);
}
UBool u_isUAlphabetic(UChar32 c) {
  return U_ICU_ENTRY_POINT_RENAME(u_isUAlphabetic)(c);
}
UBool u_isULowercase(UChar32 c) {
  return U_ICU_ENTRY_POINT_RENAME(u_isULowercase)(c);
}
UBool u_isUUppercase(UChar32 c) {
  return U_ICU_ENTRY_POINT_RENAME(u_isUUppercase)(c);
}
UBool u_isUWhiteSpace(UChar32 c) {
  return U_ICU_ENTRY_POINT_RENAME(u_isUWhiteSpace)(c);
}
UBool u_isWhitespace(UChar32 c) {
  return U_ICU_ENTRY_POINT_RENAME(u_isWhitespace)(c);
}
UBool u_isalnum(UChar32 c) {
  return U_ICU_ENTRY_POINT_RENAME(u_isalnum)(c);
}
UBool u_isalpha(UChar32 c) {
  return U_ICU_ENTRY_POINT_RENAME(u_isalpha)(c);
}
UBool u_isbase(UChar32 c) {
  return U_ICU_ENTRY_POINT_RENAME(u_isbase)(c);
}
UBool u_isblank(UChar32 c) {
  return U_ICU_ENTRY_POINT_RENAME(u_isblank)(c);
}
UBool u_iscntrl(UChar32 c) {
  return U_ICU_ENTRY_POINT_RENAME(u_iscntrl)(c);
}
UBool u_isdefined(UChar32 c) {
  return U_ICU_ENTRY_POINT_RENAME(u_isdefined)(c);
}
UBool u_isdigit(UChar32 c) {
  return U_ICU_ENTRY_POINT_RENAME(u_isdigit)(c);
}
UBool u_isgraph(UChar32 c) {
  return U_ICU_ENTRY_POINT_RENAME(u_isgraph)(c);
}
UBool u_islower(UChar32 c) {
  return U_ICU_ENTRY_POINT_RENAME(u_islower)(c);
}
UBool u_isprint(UChar32 c) {
  return U_ICU_ENTRY_POINT_RENAME(u_isprint)(c);
}
UBool u_ispunct(UChar32 c) {
  return U_ICU_ENTRY_POINT_RENAME(u_ispunct)(c);
}
UBool u_isspace(UChar32 c) {
  return U_ICU_ENTRY_POINT_RENAME(u_isspace)(c);
}
UBool u_istitle(UChar32 c) {
  return U_ICU_ENTRY_POINT_RENAME(u_istitle)(c);
}
UBool u_isupper(UChar32 c) {
  return U_ICU_ENTRY_POINT_RENAME(u_isupper)(c);
}
UBool u_isxdigit(UChar32 c) {
  return U_ICU_ENTRY_POINT_RENAME(u_isxdigit)(c);
}
int32_t u_memcasecmp(const UChar * s1, const UChar * s2, int32_t length, uint32_t options) {
  return U_ICU_ENTRY_POINT_RENAME(u_memcasecmp)(s1, s2, length, options);
}
UChar * u_memchr(const UChar * s, UChar c, int32_t count) {
  return U_ICU_ENTRY_POINT_RENAME(u_memchr)(s, c, count);
}
UChar * u_memchr32(const UChar * s, UChar32 c, int32_t count) {
  return U_ICU_ENTRY_POINT_RENAME(u_memchr32)(s, c, count);
}
int32_t u_memcmp(const UChar * buf1, const UChar * buf2, int32_t count) {
  return U_ICU_ENTRY_POINT_RENAME(u_memcmp)(buf1, buf2, count);
}
int32_t u_memcmpCodePointOrder(const UChar * s1, const UChar * s2, int32_t count) {
  return U_ICU_ENTRY_POINT_RENAME(u_memcmpCodePointOrder)(s1, s2, count);
}
UChar * u_memcpy(UChar * dest, const UChar * src, int32_t count) {
  return U_ICU_ENTRY_POINT_RENAME(u_memcpy)(dest, src, count);
}
UChar * u_memmove(UChar * dest, const UChar * src, int32_t count) {
  return U_ICU_ENTRY_POINT_RENAME(u_memmove)(dest, src, count);
}
UChar * u_memrchr(const UChar * s, UChar c, int32_t count) {
  return U_ICU_ENTRY_POINT_RENAME(u_memrchr)(s, c, count);
}
UChar * u_memrchr32(const UChar * s, UChar32 c, int32_t count) {
  return U_ICU_ENTRY_POINT_RENAME(u_memrchr32)(s, c, count);
}
UChar * u_memset(UChar * dest, UChar c, int32_t count) {
  return U_ICU_ENTRY_POINT_RENAME(u_memset)(dest, c, count);
}
int32_t u_strCaseCompare(const UChar * s1, int32_t length1, const UChar * s2, int32_t length2, uint32_t options, UErrorCode * pErrorCode) {
  return U_ICU_ENTRY_POINT_RENAME(u_strCaseCompare)(s1, length1, s2, length2, options, pErrorCode);
}
int32_t u_strCompare(const UChar * s1, int32_t length1, const UChar * s2, int32_t length2, UBool codePointOrder) {
  return U_ICU_ENTRY_POINT_RENAME(u_strCompare)(s1, length1, s2, length2, codePointOrder);
}
UChar * u_strFindFirst(const UChar * s, int32_t length, const UChar * substring, int32_t subLength) {
  return U_ICU_ENTRY_POINT_RENAME(u_strFindFirst)(s, length, substring, subLength);
}
UChar * u_strFindLast(const UChar * s, int32_t length, const UChar * substring, int32_t subLength) {
  return U_ICU_ENTRY_POINT_RENAME(u_strFindLast)(s, length, substring, subLength);
}
int32_t u_strFoldCase(UChar * dest, int32_t destCapacity, const UChar * src, int32_t srcLength, uint32_t options, UErrorCode * pErrorCode) {
  return U_ICU_ENTRY_POINT_RENAME(u_strFoldCase)(dest, destCapacity, src, srcLength, options, pErrorCode);
}
UChar * u_strFromUTF32(UChar * dest, int32_t destCapacity, int32_t * pDestLength, const UChar32 * src, int32_t srcLength, UErrorCode * pErrorCode) {
  return U_ICU_ENTRY_POINT_RENAME(u_strFromUTF32)(dest, destCapacity, pDestLength, src, srcLength, pErrorCode);
}
UChar * u_strFromUTF32WithSub(UChar * dest, int32_t destCapacity, int32_t * pDestLength, const UChar32 * src, int32_t srcLength, UChar32 subchar, int32_t * pNumSubstitutions, UErrorCode * pErrorCode) {
  return U_ICU_ENTRY_POINT_RENAME(u_strFromUTF32WithSub)(dest, destCapacity, pDestLength, src, srcLength, subchar, pNumSubstitutions, pErrorCode);
}
UChar * u_strFromUTF8(UChar * dest, int32_t destCapacity, int32_t * pDestLength, const char * src, int32_t srcLength, UErrorCode * pErrorCode) {
  return U_ICU_ENTRY_POINT_RENAME(u_strFromUTF8)(dest, destCapacity, pDestLength, src, srcLength, pErrorCode);
}
UChar * u_strFromUTF8Lenient(UChar * dest, int32_t destCapacity, int32_t * pDestLength, const char * src, int32_t srcLength, UErrorCode * pErrorCode) {
  return U_ICU_ENTRY_POINT_RENAME(u_strFromUTF8Lenient)(dest, destCapacity, pDestLength, src, srcLength, pErrorCode);
}
UChar * u_strFromUTF8WithSub(UChar * dest, int32_t destCapacity, int32_t * pDestLength, const char * src, int32_t srcLength, UChar32 subchar, int32_t * pNumSubstitutions, UErrorCode * pErrorCode) {
  return U_ICU_ENTRY_POINT_RENAME(u_strFromUTF8WithSub)(dest, destCapacity, pDestLength, src, srcLength, subchar, pNumSubstitutions, pErrorCode);
}
UBool u_strHasMoreChar32Than(const UChar * s, int32_t length, int32_t number) {
  return U_ICU_ENTRY_POINT_RENAME(u_strHasMoreChar32Than)(s, length, number);
}
int32_t u_strToLower(UChar * dest, int32_t destCapacity, const UChar * src, int32_t srcLength, const char * locale, UErrorCode * pErrorCode) {
  return U_ICU_ENTRY_POINT_RENAME(u_strToLower)(dest, destCapacity, src, srcLength, locale, pErrorCode);
}
int32_t u_strToTitle(UChar * dest, int32_t destCapacity, const UChar * src, int32_t srcLength, UBreakIterator * titleIter, const char * locale, UErrorCode * pErrorCode) {
  return U_ICU_ENTRY_POINT_RENAME(u_strToTitle)(dest, destCapacity, src, srcLength, titleIter, locale, pErrorCode);
}
UChar32 * u_strToUTF32(UChar32 * dest, int32_t destCapacity, int32_t * pDestLength, const UChar * src, int32_t srcLength, UErrorCode * pErrorCode) {
  return U_ICU_ENTRY_POINT_RENAME(u_strToUTF32)(dest, destCapacity, pDestLength, src, srcLength, pErrorCode);
}
UChar32 * u_strToUTF32WithSub(UChar32 * dest, int32_t destCapacity, int32_t * pDestLength, const UChar * src, int32_t srcLength, UChar32 subchar, int32_t * pNumSubstitutions, UErrorCode * pErrorCode) {
  return U_ICU_ENTRY_POINT_RENAME(u_strToUTF32WithSub)(dest, destCapacity, pDestLength, src, srcLength, subchar, pNumSubstitutions, pErrorCode);
}
char * u_strToUTF8(char * dest, int32_t destCapacity, int32_t * pDestLength, const UChar * src, int32_t srcLength, UErrorCode * pErrorCode) {
  return U_ICU_ENTRY_POINT_RENAME(u_strToUTF8)(dest, destCapacity, pDestLength, src, srcLength, pErrorCode);
}
char * u_strToUTF8WithSub(char * dest, int32_t destCapacity, int32_t * pDestLength, const UChar * src, int32_t srcLength, UChar32 subchar, int32_t * pNumSubstitutions, UErrorCode * pErrorCode) {
  return U_ICU_ENTRY_POINT_RENAME(u_strToUTF8WithSub)(dest, destCapacity, pDestLength, src, srcLength, subchar, pNumSubstitutions, pErrorCode);
}
int32_t u_strToUpper(UChar * dest, int32_t destCapacity, const UChar * src, int32_t srcLength, const char * locale, UErrorCode * pErrorCode) {
  return U_ICU_ENTRY_POINT_RENAME(u_strToUpper)(dest, destCapacity, src, srcLength, locale, pErrorCode);
}
int32_t u_strcasecmp(const UChar * s1, const UChar * s2, uint32_t options) {
  return U_ICU_ENTRY_POINT_RENAME(u_strcasecmp)(s1, s2, options);
}
UChar * u_strcat(UChar * dst, const UChar * src) {
  return U_ICU_ENTRY_POINT_RENAME(u_strcat)(dst, src);
}
UChar * u_strchr(const UChar * s, UChar c) {
  return U_ICU_ENTRY_POINT_RENAME(u_strchr)(s, c);
}
UChar * u_strchr32(const UChar * s, UChar32 c) {
  return U_ICU_ENTRY_POINT_RENAME(u_strchr32)(s, c);
}
int32_t u_strcmp(const UChar * s1, const UChar * s2) {
  return U_ICU_ENTRY_POINT_RENAME(u_strcmp)(s1, s2);
}
int32_t u_strcmpCodePointOrder(const UChar * s1, const UChar * s2) {
  return U_ICU_ENTRY_POINT_RENAME(u_strcmpCodePointOrder)(s1, s2);
}
UChar * u_strcpy(UChar * dst, const UChar * src) {
  return U_ICU_ENTRY_POINT_RENAME(u_strcpy)(dst, src);
}
int32_t u_strcspn(const UChar * string, const UChar * matchSet) {
  return U_ICU_ENTRY_POINT_RENAME(u_strcspn)(string, matchSet);
}
int32_t u_strlen(const UChar * s) {
  return U_ICU_ENTRY_POINT_RENAME(u_strlen)(s);
}
int32_t u_strncasecmp(const UChar * s1, const UChar * s2, int32_t n, uint32_t options) {
  return U_ICU_ENTRY_POINT_RENAME(u_strncasecmp)(s1, s2, n, options);
}
UChar * u_strncat(UChar * dst, const UChar * src, int32_t n) {
  return U_ICU_ENTRY_POINT_RENAME(u_strncat)(dst, src, n);
}
int32_t u_strncmp(const UChar * ucs1, const UChar * ucs2, int32_t n) {
  return U_ICU_ENTRY_POINT_RENAME(u_strncmp)(ucs1, ucs2, n);
}
int32_t u_strncmpCodePointOrder(const UChar * s1, const UChar * s2, int32_t n) {
  return U_ICU_ENTRY_POINT_RENAME(u_strncmpCodePointOrder)(s1, s2, n);
}
UChar * u_strncpy(UChar * dst, const UChar * src, int32_t n) {
  return U_ICU_ENTRY_POINT_RENAME(u_strncpy)(dst, src, n);
}
UChar * u_strpbrk(const UChar * string, const UChar * matchSet) {
  return U_ICU_ENTRY_POINT_RENAME(u_strpbrk)(string, matchSet);
}
UChar * u_strrchr(const UChar * s, UChar c) {
  return U_ICU_ENTRY_POINT_RENAME(u_strrchr)(s, c);
}
UChar * u_strrchr32(const UChar * s, UChar32 c) {
  return U_ICU_ENTRY_POINT_RENAME(u_strrchr32)(s, c);
}
UChar * u_strrstr(const UChar * s, const UChar * substring) {
  return U_ICU_ENTRY_POINT_RENAME(u_strrstr)(s, substring);
}
int32_t u_strspn(const UChar * string, const UChar * matchSet) {
  return U_ICU_ENTRY_POINT_RENAME(u_strspn)(string, matchSet);
}
UChar * u_strstr(const UChar * s, const UChar * substring) {
  return U_ICU_ENTRY_POINT_RENAME(u_strstr)(s, substring);
}
UChar * u_strtok_r(UChar * src, const UChar * delim, UChar ** saveState) {
  return U_ICU_ENTRY_POINT_RENAME(u_strtok_r)(src, delim, saveState);
}
UChar32 u_tolower(UChar32 c) {
  return U_ICU_ENTRY_POINT_RENAME(u_tolower)(c);
}
UChar32 u_totitle(UChar32 c) {
  return U_ICU_ENTRY_POINT_RENAME(u_totitle)(c);
}
UChar32 u_toupper(UChar32 c) {
  return U_ICU_ENTRY_POINT_RENAME(u_toupper)(c);
}
void u_versionToString(const UVersionInfo versionArray, char * versionString) {
  U_ICU_ENTRY_POINT_RENAME(u_versionToString)(versionArray, versionString);
}
void ubidi_close(UBiDi * pBiDi) {
  U_ICU_ENTRY_POINT_RENAME(ubidi_close)(pBiDi);
}
int32_t ubidi_countParagraphs(UBiDi * pBiDi) {
  return U_ICU_ENTRY_POINT_RENAME(ubidi_countParagraphs)(pBiDi);
}
int32_t ubidi_countRuns(UBiDi * pBiDi, UErrorCode * pErrorCode) {
  return U_ICU_ENTRY_POINT_RENAME(ubidi_countRuns)(pBiDi, pErrorCode);
}
UBiDiDirection ubidi_getBaseDirection(const UChar * text, int32_t length) {
  return U_ICU_ENTRY_POINT_RENAME(ubidi_getBaseDirection)(text, length);
}
void ubidi_getClassCallback(UBiDi * pBiDi, UBiDiClassCallback ** fn, const void ** context) {
  U_ICU_ENTRY_POINT_RENAME(ubidi_getClassCallback)(pBiDi, fn, context);
}
UCharDirection ubidi_getCustomizedClass(UBiDi * pBiDi, UChar32 c) {
  return U_ICU_ENTRY_POINT_RENAME(ubidi_getCustomizedClass)(pBiDi, c);
}
UBiDiDirection ubidi_getDirection(const UBiDi * pBiDi) {
  return U_ICU_ENTRY_POINT_RENAME(ubidi_getDirection)(pBiDi);
}
int32_t ubidi_getLength(const UBiDi * pBiDi) {
  return U_ICU_ENTRY_POINT_RENAME(ubidi_getLength)(pBiDi);
}
UBiDiLevel ubidi_getLevelAt(const UBiDi * pBiDi, int32_t charIndex) {
  return U_ICU_ENTRY_POINT_RENAME(ubidi_getLevelAt)(pBiDi, charIndex);
}
const UBiDiLevel * ubidi_getLevels(UBiDi * pBiDi, UErrorCode * pErrorCode) {
  return U_ICU_ENTRY_POINT_RENAME(ubidi_getLevels)(pBiDi, pErrorCode);
}
int32_t ubidi_getLogicalIndex(UBiDi * pBiDi, int32_t visualIndex, UErrorCode * pErrorCode) {
  return U_ICU_ENTRY_POINT_RENAME(ubidi_getLogicalIndex)(pBiDi, visualIndex, pErrorCode);
}
void ubidi_getLogicalMap(UBiDi * pBiDi, int32_t * indexMap, UErrorCode * pErrorCode) {
  U_ICU_ENTRY_POINT_RENAME(ubidi_getLogicalMap)(pBiDi, indexMap, pErrorCode);
}
void ubidi_getLogicalRun(const UBiDi * pBiDi, int32_t logicalPosition, int32_t * pLogicalLimit, UBiDiLevel * pLevel) {
  U_ICU_ENTRY_POINT_RENAME(ubidi_getLogicalRun)(pBiDi, logicalPosition, pLogicalLimit, pLevel);
}
UBiDiLevel ubidi_getParaLevel(const UBiDi * pBiDi) {
  return U_ICU_ENTRY_POINT_RENAME(ubidi_getParaLevel)(pBiDi);
}
int32_t ubidi_getParagraph(const UBiDi * pBiDi, int32_t charIndex, int32_t * pParaStart, int32_t * pParaLimit, UBiDiLevel * pParaLevel, UErrorCode * pErrorCode) {
  return U_ICU_ENTRY_POINT_RENAME(ubidi_getParagraph)(pBiDi, charIndex, pParaStart, pParaLimit, pParaLevel, pErrorCode);
}
void ubidi_getParagraphByIndex(const UBiDi * pBiDi, int32_t paraIndex, int32_t * pParaStart, int32_t * pParaLimit, UBiDiLevel * pParaLevel, UErrorCode * pErrorCode) {
  U_ICU_ENTRY_POINT_RENAME(ubidi_getParagraphByIndex)(pBiDi, paraIndex, pParaStart, pParaLimit, pParaLevel, pErrorCode);
}
int32_t ubidi_getProcessedLength(const UBiDi * pBiDi) {
  return U_ICU_ENTRY_POINT_RENAME(ubidi_getProcessedLength)(pBiDi);
}
UBiDiReorderingMode ubidi_getReorderingMode(UBiDi * pBiDi) {
  return U_ICU_ENTRY_POINT_RENAME(ubidi_getReorderingMode)(pBiDi);
}
uint32_t ubidi_getReorderingOptions(UBiDi * pBiDi) {
  return U_ICU_ENTRY_POINT_RENAME(ubidi_getReorderingOptions)(pBiDi);
}
int32_t ubidi_getResultLength(const UBiDi * pBiDi) {
  return U_ICU_ENTRY_POINT_RENAME(ubidi_getResultLength)(pBiDi);
}
const UChar * ubidi_getText(const UBiDi * pBiDi) {
  return U_ICU_ENTRY_POINT_RENAME(ubidi_getText)(pBiDi);
}
int32_t ubidi_getVisualIndex(UBiDi * pBiDi, int32_t logicalIndex, UErrorCode * pErrorCode) {
  return U_ICU_ENTRY_POINT_RENAME(ubidi_getVisualIndex)(pBiDi, logicalIndex, pErrorCode);
}
void ubidi_getVisualMap(UBiDi * pBiDi, int32_t * indexMap, UErrorCode * pErrorCode) {
  U_ICU_ENTRY_POINT_RENAME(ubidi_getVisualMap)(pBiDi, indexMap, pErrorCode);
}
UBiDiDirection ubidi_getVisualRun(UBiDi * pBiDi, int32_t runIndex, int32_t * pLogicalStart, int32_t * pLength) {
  return U_ICU_ENTRY_POINT_RENAME(ubidi_getVisualRun)(pBiDi, runIndex, pLogicalStart, pLength);
}
void ubidi_invertMap(const int32_t * srcMap, int32_t * destMap, int32_t length) {
  U_ICU_ENTRY_POINT_RENAME(ubidi_invertMap)(srcMap, destMap, length);
}
UBool ubidi_isInverse(UBiDi * pBiDi) {
  return U_ICU_ENTRY_POINT_RENAME(ubidi_isInverse)(pBiDi);
}
UBool ubidi_isOrderParagraphsLTR(UBiDi * pBiDi) {
  return U_ICU_ENTRY_POINT_RENAME(ubidi_isOrderParagraphsLTR)(pBiDi);
}
UBiDi * ubidi_open() {
  return U_ICU_ENTRY_POINT_RENAME(ubidi_open)();
}
UBiDi * ubidi_openSized(int32_t maxLength, int32_t maxRunCount, UErrorCode * pErrorCode) {
  return U_ICU_ENTRY_POINT_RENAME(ubidi_openSized)(maxLength, maxRunCount, pErrorCode);
}
void ubidi_orderParagraphsLTR(UBiDi * pBiDi, UBool orderParagraphsLTR) {
  U_ICU_ENTRY_POINT_RENAME(ubidi_orderParagraphsLTR)(pBiDi, orderParagraphsLTR);
}
void ubidi_reorderLogical(const UBiDiLevel * levels, int32_t length, int32_t * indexMap) {
  U_ICU_ENTRY_POINT_RENAME(ubidi_reorderLogical)(levels, length, indexMap);
}
void ubidi_reorderVisual(const UBiDiLevel * levels, int32_t length, int32_t * indexMap) {
  U_ICU_ENTRY_POINT_RENAME(ubidi_reorderVisual)(levels, length, indexMap);
}
void ubidi_setClassCallback(UBiDi * pBiDi, UBiDiClassCallback * newFn, const void * newContext, UBiDiClassCallback ** oldFn, const void ** oldContext, UErrorCode * pErrorCode) {
  U_ICU_ENTRY_POINT_RENAME(ubidi_setClassCallback)(pBiDi, newFn, newContext, oldFn, oldContext, pErrorCode);
}
void ubidi_setContext(UBiDi * pBiDi, const UChar * prologue, int32_t proLength, const UChar * epilogue, int32_t epiLength, UErrorCode * pErrorCode) {
  U_ICU_ENTRY_POINT_RENAME(ubidi_setContext)(pBiDi, prologue, proLength, epilogue, epiLength, pErrorCode);
}
void ubidi_setInverse(UBiDi * pBiDi, UBool isInverse) {
  U_ICU_ENTRY_POINT_RENAME(ubidi_setInverse)(pBiDi, isInverse);
}
void ubidi_setLine(const UBiDi * pParaBiDi, int32_t start, int32_t limit, UBiDi * pLineBiDi, UErrorCode * pErrorCode) {
  U_ICU_ENTRY_POINT_RENAME(ubidi_setLine)(pParaBiDi, start, limit, pLineBiDi, pErrorCode);
}
void ubidi_setPara(UBiDi * pBiDi, const UChar * text, int32_t length, UBiDiLevel paraLevel, UBiDiLevel * embeddingLevels, UErrorCode * pErrorCode) {
  U_ICU_ENTRY_POINT_RENAME(ubidi_setPara)(pBiDi, text, length, paraLevel, embeddingLevels, pErrorCode);
}
void ubidi_setReorderingMode(UBiDi * pBiDi, UBiDiReorderingMode reorderingMode) {
  U_ICU_ENTRY_POINT_RENAME(ubidi_setReorderingMode)(pBiDi, reorderingMode);
}
void ubidi_setReorderingOptions(UBiDi * pBiDi, uint32_t reorderingOptions) {
  U_ICU_ENTRY_POINT_RENAME(ubidi_setReorderingOptions)(pBiDi, reorderingOptions);
}
int32_t ubidi_writeReordered(UBiDi * pBiDi, UChar * dest, int32_t destSize, uint16_t options, UErrorCode * pErrorCode) {
  return U_ICU_ENTRY_POINT_RENAME(ubidi_writeReordered)(pBiDi, dest, destSize, options, pErrorCode);
}
int32_t ubidi_writeReverse(const UChar * src, int32_t srcLength, UChar * dest, int32_t destSize, uint16_t options, UErrorCode * pErrorCode) {
  return U_ICU_ENTRY_POINT_RENAME(ubidi_writeReverse)(src, srcLength, dest, destSize, options, pErrorCode);
}
UBreakIterator * ubrk_clone(const UBreakIterator * bi, UErrorCode * status) {
  return U_ICU_ENTRY_POINT_RENAME(ubrk_clone)(bi, status);
}
void ubrk_close(UBreakIterator * bi) {
  U_ICU_ENTRY_POINT_RENAME(ubrk_close)(bi);
}
int32_t ubrk_countAvailable() {
  return U_ICU_ENTRY_POINT_RENAME(ubrk_countAvailable)();
}
int32_t ubrk_current(const UBreakIterator * bi) {
  return U_ICU_ENTRY_POINT_RENAME(ubrk_current)(bi);
}
int32_t ubrk_first(UBreakIterator * bi) {
  return U_ICU_ENTRY_POINT_RENAME(ubrk_first)(bi);
}
int32_t ubrk_following(UBreakIterator * bi, int32_t offset) {
  return U_ICU_ENTRY_POINT_RENAME(ubrk_following)(bi, offset);
}
const char * ubrk_getAvailable(int32_t index) {
  return U_ICU_ENTRY_POINT_RENAME(ubrk_getAvailable)(index);
}
int32_t ubrk_getRuleStatus(UBreakIterator * bi) {
  return U_ICU_ENTRY_POINT_RENAME(ubrk_getRuleStatus)(bi);
}
int32_t ubrk_getRuleStatusVec(UBreakIterator * bi, int32_t * fillInVec, int32_t capacity, UErrorCode * status) {
  return U_ICU_ENTRY_POINT_RENAME(ubrk_getRuleStatusVec)(bi, fillInVec, capacity, status);
}
UBool ubrk_isBoundary(UBreakIterator * bi, int32_t offset) {
  return U_ICU_ENTRY_POINT_RENAME(ubrk_isBoundary)(bi, offset);
}
int32_t ubrk_last(UBreakIterator * bi) {
  return U_ICU_ENTRY_POINT_RENAME(ubrk_last)(bi);
}
int32_t ubrk_next(UBreakIterator * bi) {
  return U_ICU_ENTRY_POINT_RENAME(ubrk_next)(bi);
}
UBreakIterator * ubrk_open(UBreakIteratorType type, const char * locale, const UChar * text, int32_t textLength, UErrorCode * status) {
  return U_ICU_ENTRY_POINT_RENAME(ubrk_open)(type, locale, text, textLength, status);
}
int32_t ubrk_preceding(UBreakIterator * bi, int32_t offset) {
  return U_ICU_ENTRY_POINT_RENAME(ubrk_preceding)(bi, offset);
}
int32_t ubrk_previous(UBreakIterator * bi) {
  return U_ICU_ENTRY_POINT_RENAME(ubrk_previous)(bi);
}
void ubrk_setText(UBreakIterator * bi, const UChar * text, int32_t textLength, UErrorCode * status) {
  U_ICU_ENTRY_POINT_RENAME(ubrk_setText)(bi, text, textLength, status);
}
void ubrk_setUText(UBreakIterator * bi, UText * text, UErrorCode * status) {
  U_ICU_ENTRY_POINT_RENAME(ubrk_setUText)(bi, text, status);
}
UCollator * ucol_clone(const UCollator * coll, UErrorCode * status) {
  return U_ICU_ENTRY_POINT_RENAME(ucol_clone)(coll, status);
}
void ucol_close(UCollator * coll) {
  U_ICU_ENTRY_POINT_RENAME(ucol_close)(coll);
}
int32_t ucol_countAvailable() {
  return U_ICU_ENTRY_POINT_RENAME(ucol_countAvailable)();
}
UColAttributeValue ucol_getAttribute(const UCollator * coll, UColAttribute attr, UErrorCode * status) {
  return U_ICU_ENTRY_POINT_RENAME(ucol_getAttribute)(coll, attr, status);
}
const char * ucol_getAvailable(int32_t localeIndex) {
  return U_ICU_ENTRY_POINT_RENAME(ucol_getAvailable)(localeIndex);
}
int32_t ucol_getDisplayName(const char * objLoc, const char * dispLoc, UChar * result, int32_t resultLength, UErrorCode * status) {
  return U_ICU_ENTRY_POINT_RENAME(ucol_getDisplayName)(objLoc, dispLoc, result, resultLength, status);
}
int32_t ucol_getEquivalentReorderCodes(int32_t reorderCode, int32_t * dest, int32_t destCapacity, UErrorCode * pErrorCode) {
  return U_ICU_ENTRY_POINT_RENAME(ucol_getEquivalentReorderCodes)(reorderCode, dest, destCapacity, pErrorCode);
}
int32_t ucol_getFunctionalEquivalent(char * result, int32_t resultCapacity, const char * keyword, const char * locale, UBool * isAvailable, UErrorCode * status) {
  return U_ICU_ENTRY_POINT_RENAME(ucol_getFunctionalEquivalent)(result, resultCapacity, keyword, locale, isAvailable, status);
}
UEnumeration * ucol_getKeywordValues(const char * keyword, UErrorCode * status) {
  return U_ICU_ENTRY_POINT_RENAME(ucol_getKeywordValues)(keyword, status);
}
UEnumeration * ucol_getKeywordValuesForLocale(const char * key, const char * locale, UBool commonlyUsed, UErrorCode * status) {
  return U_ICU_ENTRY_POINT_RENAME(ucol_getKeywordValuesForLocale)(key, locale, commonlyUsed, status);
}
UEnumeration * ucol_getKeywords(UErrorCode * status) {
  return U_ICU_ENTRY_POINT_RENAME(ucol_getKeywords)(status);
}
UColReorderCode ucol_getMaxVariable(const UCollator * coll) {
  return U_ICU_ENTRY_POINT_RENAME(ucol_getMaxVariable)(coll);
}
int32_t ucol_getReorderCodes(const UCollator * coll, int32_t * dest, int32_t destCapacity, UErrorCode * pErrorCode) {
  return U_ICU_ENTRY_POINT_RENAME(ucol_getReorderCodes)(coll, dest, destCapacity, pErrorCode);
}
int32_t ucol_getSortKey(const UCollator * coll, const UChar * source, int32_t sourceLength, uint8_t * result, int32_t resultLength) {
  return U_ICU_ENTRY_POINT_RENAME(ucol_getSortKey)(coll, source, sourceLength, result, resultLength);
}
UCollationStrength ucol_getStrength(const UCollator * coll) {
  return U_ICU_ENTRY_POINT_RENAME(ucol_getStrength)(coll);
}
int32_t ucol_mergeSortkeys(const uint8_t * src1, int32_t src1Length, const uint8_t * src2, int32_t src2Length, uint8_t * dest, int32_t destCapacity) {
  return U_ICU_ENTRY_POINT_RENAME(ucol_mergeSortkeys)(src1, src1Length, src2, src2Length, dest, destCapacity);
}
UCollator * ucol_open(const char * loc, UErrorCode * status) {
  return U_ICU_ENTRY_POINT_RENAME(ucol_open)(loc, status);
}
UEnumeration * ucol_openAvailableLocales(UErrorCode * status) {
  return U_ICU_ENTRY_POINT_RENAME(ucol_openAvailableLocales)(status);
}
UCollator * ucol_openRules(const UChar * rules, int32_t rulesLength, UColAttributeValue normalizationMode, UCollationStrength strength, UParseError * parseError, UErrorCode * status) {
  return U_ICU_ENTRY_POINT_RENAME(ucol_openRules)(rules, rulesLength, normalizationMode, strength, parseError, status);
}
void ucol_setAttribute(UCollator * coll, UColAttribute attr, UColAttributeValue value, UErrorCode * status) {
  U_ICU_ENTRY_POINT_RENAME(ucol_setAttribute)(coll, attr, value, status);
}
void ucol_setMaxVariable(UCollator * coll, UColReorderCode group, UErrorCode * pErrorCode) {
  U_ICU_ENTRY_POINT_RENAME(ucol_setMaxVariable)(coll, group, pErrorCode);
}
void ucol_setReorderCodes(UCollator * coll, const int32_t * reorderCodes, int32_t reorderCodesLength, UErrorCode * pErrorCode) {
  U_ICU_ENTRY_POINT_RENAME(ucol_setReorderCodes)(coll, reorderCodes, reorderCodesLength, pErrorCode);
}
void ucol_setStrength(UCollator * coll, UCollationStrength strength) {
  U_ICU_ENTRY_POINT_RENAME(ucol_setStrength)(coll, strength);
}
UCollationResult ucol_strcoll(const UCollator * coll, const UChar * source, int32_t sourceLength, const UChar * target, int32_t targetLength) {
  return U_ICU_ENTRY_POINT_RENAME(ucol_strcoll)(coll, source, sourceLength, target, targetLength);
}
UCollationResult ucol_strcollUTF8(const UCollator * coll, const char * source, int32_t sourceLength, const char * target, int32_t targetLength, UErrorCode * status) {
  return U_ICU_ENTRY_POINT_RENAME(ucol_strcollUTF8)(coll, source, sourceLength, target, targetLength, status);
}
void uenum_close(UEnumeration * en) {
  U_ICU_ENTRY_POINT_RENAME(uenum_close)(en);
}
int32_t uenum_count(UEnumeration * en, UErrorCode * status) {
  return U_ICU_ENTRY_POINT_RENAME(uenum_count)(en, status);
}
const char * uenum_next(UEnumeration * en, int32_t * resultLength, UErrorCode * status) {
  return U_ICU_ENTRY_POINT_RENAME(uenum_next)(en, resultLength, status);
}
UEnumeration * uenum_openCharStringsEnumeration(const char *const strings[], int32_t count, UErrorCode * ec) {
  return U_ICU_ENTRY_POINT_RENAME(uenum_openCharStringsEnumeration)(strings, count, ec);
}
UEnumeration * uenum_openUCharStringsEnumeration(const UChar *const strings[], int32_t count, UErrorCode * ec) {
  return U_ICU_ENTRY_POINT_RENAME(uenum_openUCharStringsEnumeration)(strings, count, ec);
}
void uenum_reset(UEnumeration * en, UErrorCode * status) {
  U_ICU_ENTRY_POINT_RENAME(uenum_reset)(en, status);
}
const UChar * uenum_unext(UEnumeration * en, int32_t * resultLength, UErrorCode * status) {
  return U_ICU_ENTRY_POINT_RENAME(uenum_unext)(en, resultLength, status);
}
void uldn_close(ULocaleDisplayNames * ldn) {
  U_ICU_ENTRY_POINT_RENAME(uldn_close)(ldn);
}
UDisplayContext uldn_getContext(const ULocaleDisplayNames * ldn, UDisplayContextType type, UErrorCode * pErrorCode) {
  return U_ICU_ENTRY_POINT_RENAME(uldn_getContext)(ldn, type, pErrorCode);
}
UDialectHandling uldn_getDialectHandling(const ULocaleDisplayNames * ldn) {
  return U_ICU_ENTRY_POINT_RENAME(uldn_getDialectHandling)(ldn);
}
const char * uldn_getLocale(const ULocaleDisplayNames * ldn) {
  return U_ICU_ENTRY_POINT_RENAME(uldn_getLocale)(ldn);
}
int32_t uldn_keyDisplayName(const ULocaleDisplayNames * ldn, const char * key, UChar * result, int32_t maxResultSize, UErrorCode * pErrorCode) {
  return U_ICU_ENTRY_POINT_RENAME(uldn_keyDisplayName)(ldn, key, result, maxResultSize, pErrorCode);
}
int32_t uldn_keyValueDisplayName(const ULocaleDisplayNames * ldn, const char * key, const char * value, UChar * result, int32_t maxResultSize, UErrorCode * pErrorCode) {
  return U_ICU_ENTRY_POINT_RENAME(uldn_keyValueDisplayName)(ldn, key, value, result, maxResultSize, pErrorCode);
}
int32_t uldn_languageDisplayName(const ULocaleDisplayNames * ldn, const char * lang, UChar * result, int32_t maxResultSize, UErrorCode * pErrorCode) {
  return U_ICU_ENTRY_POINT_RENAME(uldn_languageDisplayName)(ldn, lang, result, maxResultSize, pErrorCode);
}
int32_t uldn_localeDisplayName(const ULocaleDisplayNames * ldn, const char * locale, UChar * result, int32_t maxResultSize, UErrorCode * pErrorCode) {
  return U_ICU_ENTRY_POINT_RENAME(uldn_localeDisplayName)(ldn, locale, result, maxResultSize, pErrorCode);
}
ULocaleDisplayNames * uldn_open(const char * locale, UDialectHandling dialectHandling, UErrorCode * pErrorCode) {
  return U_ICU_ENTRY_POINT_RENAME(uldn_open)(locale, dialectHandling, pErrorCode);
}
ULocaleDisplayNames * uldn_openForContext(const char * locale, UDisplayContext * contexts, int32_t length, UErrorCode * pErrorCode) {
  return U_ICU_ENTRY_POINT_RENAME(uldn_openForContext)(locale, contexts, length, pErrorCode);
}
int32_t uldn_regionDisplayName(const ULocaleDisplayNames * ldn, const char * region, UChar * result, int32_t maxResultSize, UErrorCode * pErrorCode) {
  return U_ICU_ENTRY_POINT_RENAME(uldn_regionDisplayName)(ldn, region, result, maxResultSize, pErrorCode);
}
int32_t uldn_scriptCodeDisplayName(const ULocaleDisplayNames * ldn, UScriptCode scriptCode, UChar * result, int32_t maxResultSize, UErrorCode * pErrorCode) {
  return U_ICU_ENTRY_POINT_RENAME(uldn_scriptCodeDisplayName)(ldn, scriptCode, result, maxResultSize, pErrorCode);
}
int32_t uldn_scriptDisplayName(const ULocaleDisplayNames * ldn, const char * script, UChar * result, int32_t maxResultSize, UErrorCode * pErrorCode) {
  return U_ICU_ENTRY_POINT_RENAME(uldn_scriptDisplayName)(ldn, script, result, maxResultSize, pErrorCode);
}
int32_t uldn_variantDisplayName(const ULocaleDisplayNames * ldn, const char * variant, UChar * result, int32_t maxResultSize, UErrorCode * pErrorCode) {
  return U_ICU_ENTRY_POINT_RENAME(uldn_variantDisplayName)(ldn, variant, result, maxResultSize, pErrorCode);
}
int32_t uloc_acceptLanguage(char * result, int32_t resultAvailable, UAcceptResult * outResult, const char ** acceptList, int32_t acceptListCount, UEnumeration * availableLocales, UErrorCode * status) {
  return U_ICU_ENTRY_POINT_RENAME(uloc_acceptLanguage)(result, resultAvailable, outResult, acceptList, acceptListCount, availableLocales, status);
}
int32_t uloc_addLikelySubtags(const char * localeID, char * maximizedLocaleID, int32_t maximizedLocaleIDCapacity, UErrorCode * err) {
  return U_ICU_ENTRY_POINT_RENAME(uloc_addLikelySubtags)(localeID, maximizedLocaleID, maximizedLocaleIDCapacity, err);
}
int32_t uloc_canonicalize(const char * localeID, char * name, int32_t nameCapacity, UErrorCode * err) {
  return U_ICU_ENTRY_POINT_RENAME(uloc_canonicalize)(localeID, name, nameCapacity, err);
}
int32_t uloc_countAvailable() {
  return U_ICU_ENTRY_POINT_RENAME(uloc_countAvailable)();
}
int32_t uloc_forLanguageTag(const char * langtag, char * localeID, int32_t localeIDCapacity, int32_t * parsedLength, UErrorCode * err) {
  return U_ICU_ENTRY_POINT_RENAME(uloc_forLanguageTag)(langtag, localeID, localeIDCapacity, parsedLength, err);
}
const char * uloc_getAvailable(int32_t n) {
  return U_ICU_ENTRY_POINT_RENAME(uloc_getAvailable)(n);
}
int32_t uloc_getBaseName(const char * localeID, char * name, int32_t nameCapacity, UErrorCode * err) {
  return U_ICU_ENTRY_POINT_RENAME(uloc_getBaseName)(localeID, name, nameCapacity, err);
}
ULayoutType uloc_getCharacterOrientation(const char * localeId, UErrorCode * status) {
  return U_ICU_ENTRY_POINT_RENAME(uloc_getCharacterOrientation)(localeId, status);
}
int32_t uloc_getCountry(const char * localeID, char * country, int32_t countryCapacity, UErrorCode * err) {
  return U_ICU_ENTRY_POINT_RENAME(uloc_getCountry)(localeID, country, countryCapacity, err);
}
const char * uloc_getDefault() {
  return U_ICU_ENTRY_POINT_RENAME(uloc_getDefault)();
}
int32_t uloc_getDisplayCountry(const char * locale, const char * displayLocale, UChar * country, int32_t countryCapacity, UErrorCode * status) {
  return U_ICU_ENTRY_POINT_RENAME(uloc_getDisplayCountry)(locale, displayLocale, country, countryCapacity, status);
}
int32_t uloc_getDisplayKeyword(const char * keyword, const char * displayLocale, UChar * dest, int32_t destCapacity, UErrorCode * status) {
  return U_ICU_ENTRY_POINT_RENAME(uloc_getDisplayKeyword)(keyword, displayLocale, dest, destCapacity, status);
}
int32_t uloc_getDisplayKeywordValue(const char * locale, const char * keyword, const char * displayLocale, UChar * dest, int32_t destCapacity, UErrorCode * status) {
  return U_ICU_ENTRY_POINT_RENAME(uloc_getDisplayKeywordValue)(locale, keyword, displayLocale, dest, destCapacity, status);
}
int32_t uloc_getDisplayLanguage(const char * locale, const char * displayLocale, UChar * language, int32_t languageCapacity, UErrorCode * status) {
  return U_ICU_ENTRY_POINT_RENAME(uloc_getDisplayLanguage)(locale, displayLocale, language, languageCapacity, status);
}
int32_t uloc_getDisplayName(const char * localeID, const char * inLocaleID, UChar * result, int32_t maxResultSize, UErrorCode * err) {
  return U_ICU_ENTRY_POINT_RENAME(uloc_getDisplayName)(localeID, inLocaleID, result, maxResultSize, err);
}
int32_t uloc_getDisplayScript(const char * locale, const char * displayLocale, UChar * script, int32_t scriptCapacity, UErrorCode * status) {
  return U_ICU_ENTRY_POINT_RENAME(uloc_getDisplayScript)(locale, displayLocale, script, scriptCapacity, status);
}
int32_t uloc_getDisplayVariant(const char * locale, const char * displayLocale, UChar * variant, int32_t variantCapacity, UErrorCode * status) {
  return U_ICU_ENTRY_POINT_RENAME(uloc_getDisplayVariant)(locale, displayLocale, variant, variantCapacity, status);
}
const char * uloc_getISO3Country(const char * localeID) {
  return U_ICU_ENTRY_POINT_RENAME(uloc_getISO3Country)(localeID);
}
const char * uloc_getISO3Language(const char * localeID) {
  return U_ICU_ENTRY_POINT_RENAME(uloc_getISO3Language)(localeID);
}
const char *const * uloc_getISOCountries() {
  return U_ICU_ENTRY_POINT_RENAME(uloc_getISOCountries)();
}
const char *const * uloc_getISOLanguages() {
  return U_ICU_ENTRY_POINT_RENAME(uloc_getISOLanguages)();
}
int32_t uloc_getKeywordValue(const char * localeID, const char * keywordName, char * buffer, int32_t bufferCapacity, UErrorCode * status) {
  return U_ICU_ENTRY_POINT_RENAME(uloc_getKeywordValue)(localeID, keywordName, buffer, bufferCapacity, status);
}
int32_t uloc_getLanguage(const char * localeID, char * language, int32_t languageCapacity, UErrorCode * err) {
  return U_ICU_ENTRY_POINT_RENAME(uloc_getLanguage)(localeID, language, languageCapacity, err);
}
ULayoutType uloc_getLineOrientation(const char * localeId, UErrorCode * status) {
  return U_ICU_ENTRY_POINT_RENAME(uloc_getLineOrientation)(localeId, status);
}
int32_t uloc_getName(const char * localeID, char * name, int32_t nameCapacity, UErrorCode * err) {
  return U_ICU_ENTRY_POINT_RENAME(uloc_getName)(localeID, name, nameCapacity, err);
}
int32_t uloc_getScript(const char * localeID, char * script, int32_t scriptCapacity, UErrorCode * err) {
  return U_ICU_ENTRY_POINT_RENAME(uloc_getScript)(localeID, script, scriptCapacity, err);
}
int32_t uloc_getVariant(const char * localeID, char * variant, int32_t variantCapacity, UErrorCode * err) {
  return U_ICU_ENTRY_POINT_RENAME(uloc_getVariant)(localeID, variant, variantCapacity, err);
}
UBool uloc_isRightToLeft(const char * locale) {
  return U_ICU_ENTRY_POINT_RENAME(uloc_isRightToLeft)(locale);
}
int32_t uloc_minimizeSubtags(const char * localeID, char * minimizedLocaleID, int32_t minimizedLocaleIDCapacity, UErrorCode * err) {
  return U_ICU_ENTRY_POINT_RENAME(uloc_minimizeSubtags)(localeID, minimizedLocaleID, minimizedLocaleIDCapacity, err);
}
UEnumeration * uloc_openKeywords(const char * localeID, UErrorCode * status) {
  return U_ICU_ENTRY_POINT_RENAME(uloc_openKeywords)(localeID, status);
}
int32_t uloc_setKeywordValue(const char * keywordName, const char * keywordValue, char * buffer, int32_t bufferCapacity, UErrorCode * status) {
  return U_ICU_ENTRY_POINT_RENAME(uloc_setKeywordValue)(keywordName, keywordValue, buffer, bufferCapacity, status);
}
int32_t uloc_toLanguageTag(const char * localeID, char * langtag, int32_t langtagCapacity, UBool strict, UErrorCode * err) {
  return U_ICU_ENTRY_POINT_RENAME(uloc_toLanguageTag)(localeID, langtag, langtagCapacity, strict, err);
}
const char * uloc_toLegacyKey(const char * keyword) {
  return U_ICU_ENTRY_POINT_RENAME(uloc_toLegacyKey)(keyword);
}
const char * uloc_toLegacyType(const char * keyword, const char * value) {
  return U_ICU_ENTRY_POINT_RENAME(uloc_toLegacyType)(keyword, value);
}
const char * uloc_toUnicodeLocaleKey(const char * keyword) {
  return U_ICU_ENTRY_POINT_RENAME(uloc_toUnicodeLocaleKey)(keyword);
}
const char * uloc_toUnicodeLocaleType(const char * keyword, const char * value) {
  return U_ICU_ENTRY_POINT_RENAME(uloc_toUnicodeLocaleType)(keyword, value);
}
void ulocdata_getCLDRVersion(UVersionInfo versionArray, UErrorCode * status) {
  U_ICU_ENTRY_POINT_RENAME(ulocdata_getCLDRVersion)(versionArray, status);
}
int32_t unorm2_append(const UNormalizer2 * norm2, UChar * first, int32_t firstLength, int32_t firstCapacity, const UChar * second, int32_t secondLength, UErrorCode * pErrorCode) {
  return U_ICU_ENTRY_POINT_RENAME(unorm2_append)(norm2, first, firstLength, firstCapacity, second, secondLength, pErrorCode);
}
void unorm2_close(UNormalizer2 * norm2) {
  U_ICU_ENTRY_POINT_RENAME(unorm2_close)(norm2);
}
UChar32 unorm2_composePair(const UNormalizer2 * norm2, UChar32 a, UChar32 b) {
  return U_ICU_ENTRY_POINT_RENAME(unorm2_composePair)(norm2, a, b);
}
uint8_t unorm2_getCombiningClass(const UNormalizer2 * norm2, UChar32 c) {
  return U_ICU_ENTRY_POINT_RENAME(unorm2_getCombiningClass)(norm2, c);
}
int32_t unorm2_getDecomposition(const UNormalizer2 * norm2, UChar32 c, UChar * decomposition, int32_t capacity, UErrorCode * pErrorCode) {
  return U_ICU_ENTRY_POINT_RENAME(unorm2_getDecomposition)(norm2, c, decomposition, capacity, pErrorCode);
}
const UNormalizer2 * unorm2_getNFCInstance(UErrorCode * pErrorCode) {
  return U_ICU_ENTRY_POINT_RENAME(unorm2_getNFCInstance)(pErrorCode);
}
const UNormalizer2 * unorm2_getNFDInstance(UErrorCode * pErrorCode) {
  return U_ICU_ENTRY_POINT_RENAME(unorm2_getNFDInstance)(pErrorCode);
}
const UNormalizer2 * unorm2_getNFKCCasefoldInstance(UErrorCode * pErrorCode) {
  return U_ICU_ENTRY_POINT_RENAME(unorm2_getNFKCCasefoldInstance)(pErrorCode);
}
const UNormalizer2 * unorm2_getNFKCInstance(UErrorCode * pErrorCode) {
  return U_ICU_ENTRY_POINT_RENAME(unorm2_getNFKCInstance)(pErrorCode);
}
const UNormalizer2 * unorm2_getNFKDInstance(UErrorCode * pErrorCode) {
  return U_ICU_ENTRY_POINT_RENAME(unorm2_getNFKDInstance)(pErrorCode);
}
int32_t unorm2_getRawDecomposition(const UNormalizer2 * norm2, UChar32 c, UChar * decomposition, int32_t capacity, UErrorCode * pErrorCode) {
  return U_ICU_ENTRY_POINT_RENAME(unorm2_getRawDecomposition)(norm2, c, decomposition, capacity, pErrorCode);
}
UBool unorm2_hasBoundaryAfter(const UNormalizer2 * norm2, UChar32 c) {
  return U_ICU_ENTRY_POINT_RENAME(unorm2_hasBoundaryAfter)(norm2, c);
}
UBool unorm2_hasBoundaryBefore(const UNormalizer2 * norm2, UChar32 c) {
  return U_ICU_ENTRY_POINT_RENAME(unorm2_hasBoundaryBefore)(norm2, c);
}
UBool unorm2_isInert(const UNormalizer2 * norm2, UChar32 c) {
  return U_ICU_ENTRY_POINT_RENAME(unorm2_isInert)(norm2, c);
}
UBool unorm2_isNormalized(const UNormalizer2 * norm2, const UChar * s, int32_t length, UErrorCode * pErrorCode) {
  return U_ICU_ENTRY_POINT_RENAME(unorm2_isNormalized)(norm2, s, length, pErrorCode);
}
int32_t unorm2_normalize(const UNormalizer2 * norm2, const UChar * src, int32_t length, UChar * dest, int32_t capacity, UErrorCode * pErrorCode) {
  return U_ICU_ENTRY_POINT_RENAME(unorm2_normalize)(norm2, src, length, dest, capacity, pErrorCode);
}
int32_t unorm2_normalizeSecondAndAppend(const UNormalizer2 * norm2, UChar * first, int32_t firstLength, int32_t firstCapacity, const UChar * second, int32_t secondLength, UErrorCode * pErrorCode) {
  return U_ICU_ENTRY_POINT_RENAME(unorm2_normalizeSecondAndAppend)(norm2, first, firstLength, firstCapacity, second, secondLength, pErrorCode);
}
UNormalizationCheckResult unorm2_quickCheck(const UNormalizer2 * norm2, const UChar * s, int32_t length, UErrorCode * pErrorCode) {
  return U_ICU_ENTRY_POINT_RENAME(unorm2_quickCheck)(norm2, s, length, pErrorCode);
}
int32_t unorm2_spanQuickCheckYes(const UNormalizer2 * norm2, const UChar * s, int32_t length, UErrorCode * pErrorCode) {
  return U_ICU_ENTRY_POINT_RENAME(unorm2_spanQuickCheckYes)(norm2, s, length, pErrorCode);
}
UBool uscript_breaksBetweenLetters(UScriptCode script) {
  return U_ICU_ENTRY_POINT_RENAME(uscript_breaksBetweenLetters)(script);
}
int32_t uscript_getCode(const char * nameOrAbbrOrLocale, UScriptCode * fillIn, int32_t capacity, UErrorCode * err) {
  return U_ICU_ENTRY_POINT_RENAME(uscript_getCode)(nameOrAbbrOrLocale, fillIn, capacity, err);
}
const char * uscript_getName(UScriptCode scriptCode) {
  return U_ICU_ENTRY_POINT_RENAME(uscript_getName)(scriptCode);
}
int32_t uscript_getSampleString(UScriptCode script, UChar * dest, int32_t capacity, UErrorCode * pErrorCode) {
  return U_ICU_ENTRY_POINT_RENAME(uscript_getSampleString)(script, dest, capacity, pErrorCode);
}
UScriptCode uscript_getScript(UChar32 codepoint, UErrorCode * err) {
  return U_ICU_ENTRY_POINT_RENAME(uscript_getScript)(codepoint, err);
}
int32_t uscript_getScriptExtensions(UChar32 c, UScriptCode * scripts, int32_t capacity, UErrorCode * errorCode) {
  return U_ICU_ENTRY_POINT_RENAME(uscript_getScriptExtensions)(c, scripts, capacity, errorCode);
}
const char * uscript_getShortName(UScriptCode scriptCode) {
  return U_ICU_ENTRY_POINT_RENAME(uscript_getShortName)(scriptCode);
}
UScriptUsage uscript_getUsage(UScriptCode script) {
  return U_ICU_ENTRY_POINT_RENAME(uscript_getUsage)(script);
}
UBool uscript_hasScript(UChar32 c, UScriptCode sc) {
  return U_ICU_ENTRY_POINT_RENAME(uscript_hasScript)(c, sc);
}
UBool uscript_isCased(UScriptCode script) {
  return U_ICU_ENTRY_POINT_RENAME(uscript_isCased)(script);
}
UBool uscript_isRightToLeft(UScriptCode script) {
  return U_ICU_ENTRY_POINT_RENAME(uscript_isRightToLeft)(script);
}
UChar32 utext_char32At(UText * ut, int64_t nativeIndex) {
  return U_ICU_ENTRY_POINT_RENAME(utext_char32At)(ut, nativeIndex);
}
UText * utext_clone(UText * dest, const UText * src, UBool deep, UBool readOnly, UErrorCode * status) {
  return U_ICU_ENTRY_POINT_RENAME(utext_clone)(dest, src, deep, readOnly, status);
}
UText * utext_close(UText * ut) {
  return U_ICU_ENTRY_POINT_RENAME(utext_close)(ut);
}
UChar32 utext_current32(UText * ut) {
  return U_ICU_ENTRY_POINT_RENAME(utext_current32)(ut);
}
UBool utext_equals(const UText * a, const UText * b) {
  return U_ICU_ENTRY_POINT_RENAME(utext_equals)(a, b);
}
int32_t utext_extract(UText * ut, int64_t nativeStart, int64_t nativeLimit, UChar * dest, int32_t destCapacity, UErrorCode * status) {
  return U_ICU_ENTRY_POINT_RENAME(utext_extract)(ut, nativeStart, nativeLimit, dest, destCapacity, status);
}
int64_t utext_getNativeIndex(const UText * ut) {
  return U_ICU_ENTRY_POINT_RENAME(utext_getNativeIndex)(ut);
}
UBool utext_moveIndex32(UText * ut, int32_t delta) {
  return U_ICU_ENTRY_POINT_RENAME(utext_moveIndex32)(ut, delta);
}
int64_t utext_nativeLength(UText * ut) {
  return U_ICU_ENTRY_POINT_RENAME(utext_nativeLength)(ut);
}
UChar32 utext_next32(UText * ut) {
  return U_ICU_ENTRY_POINT_RENAME(utext_next32)(ut);
}
UChar32 utext_next32From(UText * ut, int64_t nativeIndex) {
  return U_ICU_ENTRY_POINT_RENAME(utext_next32From)(ut, nativeIndex);
}
UText * utext_openUChars(UText * ut, const UChar * s, int64_t length, UErrorCode * status) {
  return U_ICU_ENTRY_POINT_RENAME(utext_openUChars)(ut, s, length, status);
}
UText * utext_openUTF8(UText * ut, const char * s, int64_t length, UErrorCode * status) {
  return U_ICU_ENTRY_POINT_RENAME(utext_openUTF8)(ut, s, length, status);
}
UChar32 utext_previous32(UText * ut) {
  return U_ICU_ENTRY_POINT_RENAME(utext_previous32)(ut);
}
UChar32 utext_previous32From(UText * ut, int64_t nativeIndex) {
  return U_ICU_ENTRY_POINT_RENAME(utext_previous32From)(ut, nativeIndex);
}
void utext_setNativeIndex(UText * ut, int64_t nativeIndex) {
  U_ICU_ENTRY_POINT_RENAME(utext_setNativeIndex)(ut, nativeIndex);
}
UTransliterator * utrans_clone(const UTransliterator * trans, UErrorCode * status) {
  return U_ICU_ENTRY_POINT_RENAME(utrans_clone)(trans, status);
}
void utrans_close(UTransliterator * trans) {
  U_ICU_ENTRY_POINT_RENAME(utrans_close)(trans);
}
UEnumeration * utrans_openIDs(UErrorCode * pErrorCode) {
  return U_ICU_ENTRY_POINT_RENAME(utrans_openIDs)(pErrorCode);
}
UTransliterator * utrans_openInverse(const UTransliterator * trans, UErrorCode * status) {
  return U_ICU_ENTRY_POINT_RENAME(utrans_openInverse)(trans, status);
}
UTransliterator * utrans_openU(const UChar * id, int32_t idLength, UTransDirection dir, const UChar * rules, int32_t rulesLength, UParseError * parseError, UErrorCode * pErrorCode) {
  return U_ICU_ENTRY_POINT_RENAME(utrans_openU)(id, idLength, dir, rules, rulesLength, parseError, pErrorCode);
}
void utrans_setFilter(UTransliterator * trans, const UChar * filterPattern, int32_t filterPatternLen, UErrorCode * status) {
  U_ICU_ENTRY_POINT_RENAME(utrans_setFilter)(trans, filterPattern, filterPatternLen, status);
}
int32_t utrans_toRules(const UTransliterator * trans, UBool escapeUnprintable, UChar * result, int32_t resultLength, UErrorCode * status) {
  return U_ICU_ENTRY_POINT_RENAME(utrans_toRules)(trans, escapeUnprintable, result, resultLength, status);
}
void utrans_trans(const UTransliterator * trans, UReplaceable * rep, const UReplaceableCallbacks * repFunc, int32_t start, int32_t * limit, UErrorCode * status) {
  U_ICU_ENTRY_POINT_RENAME(utrans_trans)(trans, rep, repFunc, start, limit, status);
}
void utrans_transIncremental(const UTransliterator * trans, UReplaceable * rep, const UReplaceableCallbacks * repFunc, UTransPosition * pos, UErrorCode * status) {
  U_ICU_ENTRY_POINT_RENAME(utrans_transIncremental)(trans, rep, repFunc, pos, status);
}
void utrans_transIncrementalUChars(const UTransliterator * trans, UChar * text, int32_t * textLength, int32_t textCapacity, UTransPosition * pos, UErrorCode * status) {
  U_ICU_ENTRY_POINT_RENAME(utrans_transIncrementalUChars)(trans, text, textLength, textCapacity, pos, status);
}
void utrans_transUChars(const UTransliterator * trans, UChar * text, int32_t * textLength, int32_t textCapacity, int32_t start, int32_t * limit, UErrorCode * status) {
  U_ICU_ENTRY_POINT_RENAME(utrans_transUChars)(trans, text, textLength, textCapacity, start, limit, status);
}
}