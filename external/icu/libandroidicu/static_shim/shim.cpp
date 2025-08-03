/* GENERATED SOURCE. DO NOT MODIFY. */
/* Run external/icu/tools/icu4c_srcgen/generate_libandroidicu.py to regenerate */
// © 2018 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html

#include <unicode/icudataver.h>
#include <unicode/putil.h>
#include <unicode/ubidi.h>
#include <unicode/ubiditransform.h>
#include <unicode/ubrk.h>
#include <unicode/ucal.h>
#include <unicode/ucasemap.h>
#include <unicode/ucat.h>
#include <unicode/uchar.h>
#include <unicode/uclean.h>
#include <unicode/ucnv.h>
#include <unicode/ucnv_cb.h>
#include <unicode/ucnv_err.h>
#include <unicode/ucnvsel.h>
#include <unicode/ucol.h>
#include <unicode/ucoleitr.h>
#include <unicode/ucpmap.h>
#include <unicode/ucptrie.h>
#include <unicode/ucsdet.h>
#include <unicode/ucurr.h>
#include <unicode/udat.h>
#include <unicode/udata.h>
#include <unicode/udateintervalformat.h>
#include <unicode/udatpg.h>
#include <unicode/uenum.h>
#include <unicode/ufieldpositer.h>
#include <unicode/uformattable.h>
#include <unicode/uformattedvalue.h>
#include <unicode/ugender.h>
#include <unicode/uidna.h>
#include <unicode/uiter.h>
#include <unicode/uldnames.h>
#include <unicode/ulistformatter.h>
#include <unicode/uloc.h>
#include <unicode/ulocdata.h>
#include <unicode/umsg.h>
#include <unicode/umutablecptrie.h>
#include <unicode/unorm2.h>
#include <unicode/unum.h>
#include <unicode/unumberformatter.h>
#include <unicode/unumberrangeformatter.h>
#include <unicode/unumsys.h>
#include <unicode/upluralrules.h>
#include <unicode/uregex.h>
#include <unicode/uregion.h>
#include <unicode/ureldatefmt.h>
#include <unicode/ures.h>
#include <unicode/uscript.h>
#include <unicode/usearch.h>
#include <unicode/uset.h>
#include <unicode/ushape.h>
#include <unicode/uspoof.h>
#include <unicode/usprep.h>
#include <unicode/ustring.h>
#include <unicode/utext.h>
#include <unicode/utmscale.h>
#include <unicode/utrace.h>
#include <unicode/utrans.h>
#include <unicode/utypes.h>
#include <unicode/uversion.h>

extern "C" {
void UCNV_FROM_U_CALLBACK_STOP_android(const void * context, UConverterFromUnicodeArgs * fromUArgs, const UChar * codeUnits, int32_t length, UChar32 codePoint, UConverterCallbackReason reason, UErrorCode * err) {
  UCNV_FROM_U_CALLBACK_STOP(context, fromUArgs, codeUnits, length, codePoint, reason, err);
}
void UCNV_TO_U_CALLBACK_STOP_android(const void * context, UConverterToUnicodeArgs * toUArgs, const char * codeUnits, int32_t length, UConverterCallbackReason reason, UErrorCode * err) {
  UCNV_TO_U_CALLBACK_STOP(context, toUArgs, codeUnits, length, reason, err);
}
UCharDirection u_charDirection_android(UChar32 c) {
  return u_charDirection(c);
}
UChar32 u_charMirror_android(UChar32 c) {
  return u_charMirror(c);
}
int32_t u_charName_android(UChar32 code, UCharNameChoice nameChoice, char * buffer, int32_t bufferLength, UErrorCode * pErrorCode) {
  return u_charName(code, nameChoice, buffer, bufferLength, pErrorCode);
}
int8_t u_charType_android(UChar32 c) {
  return u_charType(c);
}
int32_t u_countChar32_android(const UChar * s, int32_t length) {
  return u_countChar32(s, length);
}
int32_t u_digit_android(UChar32 ch, int8_t radix) {
  return u_digit(ch, radix);
}
const char * u_errorName_android(UErrorCode code) {
  return u_errorName(code);
}
UChar32 u_foldCase_android(UChar32 c, uint32_t options) {
  return u_foldCase(c, options);
}
uint8_t u_getCombiningClass_android(UChar32 c) {
  return u_getCombiningClass(c);
}
int32_t u_getIntPropertyMaxValue_android(UProperty which) {
  return u_getIntPropertyMaxValue(which);
}
int32_t u_getIntPropertyValue_android(UChar32 c, UProperty which) {
  return u_getIntPropertyValue(c, which);
}
double u_getNumericValue_android(UChar32 c) {
  return u_getNumericValue(c);
}
UBool u_hasBinaryProperty_android(UChar32 c, UProperty which) {
  return u_hasBinaryProperty(c, which);
}
UBool u_isIDIgnorable_android(UChar32 c) {
  return u_isIDIgnorable(c);
}
UBool u_isIDPart_android(UChar32 c) {
  return u_isIDPart(c);
}
UBool u_isIDStart_android(UChar32 c) {
  return u_isIDStart(c);
}
UBool u_isJavaSpaceChar_android(UChar32 c) {
  return u_isJavaSpaceChar(c);
}
UBool u_isMirrored_android(UChar32 c) {
  return u_isMirrored(c);
}
UBool u_isUAlphabetic_android(UChar32 c) {
  return u_isUAlphabetic(c);
}
UBool u_isUWhiteSpace_android(UChar32 c) {
  return u_isUWhiteSpace(c);
}
UBool u_isWhitespace_android(UChar32 c) {
  return u_isWhitespace(c);
}
UBool u_isalnum_android(UChar32 c) {
  return u_isalnum(c);
}
UBool u_isalpha_android(UChar32 c) {
  return u_isalpha(c);
}
UBool u_iscntrl_android(UChar32 c) {
  return u_iscntrl(c);
}
UBool u_isdefined_android(UChar32 c) {
  return u_isdefined(c);
}
UBool u_isdigit_android(UChar32 c) {
  return u_isdigit(c);
}
UBool u_islower_android(UChar32 c) {
  return u_islower(c);
}
UBool u_ispunct_android(UChar32 c) {
  return u_ispunct(c);
}
UBool u_isspace_android(UChar32 c) {
  return u_isspace(c);
}
UBool u_istitle_android(UChar32 c) {
  return u_istitle(c);
}
UBool u_isupper_android(UChar32 c) {
  return u_isupper(c);
}
UChar * u_strFromUTF8_android(UChar * dest, int32_t destCapacity, int32_t * pDestLength, const char * src, int32_t srcLength, UErrorCode * pErrorCode) {
  return u_strFromUTF8(dest, destCapacity, pDestLength, src, srcLength, pErrorCode);
}
UChar * u_strFromUTF8WithSub_android(UChar * dest, int32_t destCapacity, int32_t * pDestLength, const char * src, int32_t srcLength, UChar32 subchar, int32_t * pNumSubstitutions, UErrorCode * pErrorCode) {
  return u_strFromUTF8WithSub(dest, destCapacity, pDestLength, src, srcLength, subchar, pNumSubstitutions, pErrorCode);
}
int32_t u_strToLower_android(UChar * dest, int32_t destCapacity, const UChar * src, int32_t srcLength, const char * locale, UErrorCode * pErrorCode) {
  return u_strToLower(dest, destCapacity, src, srcLength, locale, pErrorCode);
}
char * u_strToUTF8_android(char * dest, int32_t destCapacity, int32_t * pDestLength, const UChar * src, int32_t srcLength, UErrorCode * pErrorCode) {
  return u_strToUTF8(dest, destCapacity, pDestLength, src, srcLength, pErrorCode);
}
int32_t u_strToUpper_android(UChar * dest, int32_t destCapacity, const UChar * src, int32_t srcLength, const char * locale, UErrorCode * pErrorCode) {
  return u_strToUpper(dest, destCapacity, src, srcLength, locale, pErrorCode);
}
UChar32 u_tolower_android(UChar32 c) {
  return u_tolower(c);
}
UChar32 u_totitle_android(UChar32 c) {
  return u_totitle(c);
}
UChar32 u_toupper_android(UChar32 c) {
  return u_toupper(c);
}
void ubidi_close_android(UBiDi * pBiDi) {
  ubidi_close(pBiDi);
}
int32_t ubidi_countRuns_android(UBiDi * pBiDi, UErrorCode * pErrorCode) {
  return ubidi_countRuns(pBiDi, pErrorCode);
}
UBiDiDirection ubidi_getDirection_android(const UBiDi * pBiDi) {
  return ubidi_getDirection(pBiDi);
}
int32_t ubidi_getLength_android(const UBiDi * pBiDi) {
  return ubidi_getLength(pBiDi);
}
UBiDiLevel ubidi_getLevelAt_android(const UBiDi * pBiDi, int32_t charIndex) {
  return ubidi_getLevelAt(pBiDi, charIndex);
}
UBiDiLevel ubidi_getParaLevel_android(const UBiDi * pBiDi) {
  return ubidi_getParaLevel(pBiDi);
}
UBiDiDirection ubidi_getVisualRun_android(UBiDi * pBiDi, int32_t runIndex, int32_t * pLogicalStart, int32_t * pLength) {
  return ubidi_getVisualRun(pBiDi, runIndex, pLogicalStart, pLength);
}
UBiDi * ubidi_open_android() {
  return ubidi_open();
}
UBiDi * ubidi_openSized_android(int32_t maxLength, int32_t maxRunCount, UErrorCode * pErrorCode) {
  return ubidi_openSized(maxLength, maxRunCount, pErrorCode);
}
void ubidi_reorderVisual_android(const UBiDiLevel * levels, int32_t length, int32_t * indexMap) {
  ubidi_reorderVisual(levels, length, indexMap);
}
void ubidi_setClassCallback_android(UBiDi * pBiDi, UBiDiClassCallback * newFn, const void * newContext, UBiDiClassCallback ** oldFn, const void ** oldContext, UErrorCode * pErrorCode) {
  ubidi_setClassCallback(pBiDi, newFn, newContext, oldFn, oldContext, pErrorCode);
}
void ubidi_setPara_android(UBiDi * pBiDi, const UChar * text, int32_t length, UBiDiLevel paraLevel, UBiDiLevel * embeddingLevels, UErrorCode * pErrorCode) {
  ubidi_setPara(pBiDi, text, length, paraLevel, embeddingLevels, pErrorCode);
}
UBreakIterator * ubrk_clone_android(const UBreakIterator * bi, UErrorCode * status) {
  return ubrk_clone(bi, status);
}
void ubrk_close_android(UBreakIterator * bi) {
  ubrk_close(bi);
}
int32_t ubrk_current_android(const UBreakIterator * bi) {
  return ubrk_current(bi);
}
int32_t ubrk_first_android(UBreakIterator * bi) {
  return ubrk_first(bi);
}
int32_t ubrk_following_android(UBreakIterator * bi, int32_t offset) {
  return ubrk_following(bi, offset);
}
int32_t ubrk_getRuleStatus_android(UBreakIterator * bi) {
  return ubrk_getRuleStatus(bi);
}
UBool ubrk_isBoundary_android(UBreakIterator * bi, int32_t offset) {
  return ubrk_isBoundary(bi, offset);
}
int32_t ubrk_next_android(UBreakIterator * bi) {
  return ubrk_next(bi);
}
UBreakIterator * ubrk_open_android(UBreakIteratorType type, const char * locale, const UChar * text, int32_t textLength, UErrorCode * status) {
  return ubrk_open(type, locale, text, textLength, status);
}
int32_t ubrk_preceding_android(UBreakIterator * bi, int32_t offset) {
  return ubrk_preceding(bi, offset);
}
void ubrk_setText_android(UBreakIterator * bi, const UChar * text, int32_t textLength, UErrorCode * status) {
  ubrk_setText(bi, text, textLength, status);
}
void ubrk_setUText_android(UBreakIterator * bi, UText * text, UErrorCode * status) {
  ubrk_setUText(bi, text, status);
}
void ucnv_close_android(UConverter * converter) {
  ucnv_close(converter);
}
int32_t ucnv_convert_android(const char * toConverterName, const char * fromConverterName, char * target, int32_t targetCapacity, const char * source, int32_t sourceLength, UErrorCode * pErrorCode) {
  return ucnv_convert(toConverterName, fromConverterName, target, targetCapacity, source, sourceLength, pErrorCode);
}
void ucnv_convertEx_android(UConverter * targetCnv, UConverter * sourceCnv, char ** target, const char * targetLimit, const char ** source, const char * sourceLimit, UChar * pivotStart, UChar ** pivotSource, UChar ** pivotTarget, const UChar * pivotLimit, UBool reset, UBool flush, UErrorCode * pErrorCode) {
  ucnv_convertEx(targetCnv, sourceCnv, target, targetLimit, source, sourceLimit, pivotStart, pivotSource, pivotTarget, pivotLimit, reset, flush, pErrorCode);
}
UChar32 ucnv_getNextUChar_android(UConverter * converter, const char ** source, const char * sourceLimit, UErrorCode * err) {
  return ucnv_getNextUChar(converter, source, sourceLimit, err);
}
UConverter * ucnv_open_android(const char * converterName, UErrorCode * err) {
  return ucnv_open(converterName, err);
}
void ucnv_setFromUCallBack_android(UConverter * converter, UConverterFromUCallback newAction, const void * newContext, UConverterFromUCallback * oldAction, const void ** oldContext, UErrorCode * err) {
  ucnv_setFromUCallBack(converter, newAction, newContext, oldAction, oldContext, err);
}
void ucnv_setToUCallBack_android(UConverter * converter, UConverterToUCallback newAction, const void * newContext, UConverterToUCallback * oldAction, const void ** oldContext, UErrorCode * err) {
  ucnv_setToUCallBack(converter, newAction, newContext, oldAction, oldContext, err);
}
void ucol_close_android(UCollator * coll) {
  ucol_close(coll);
}
UCollator * ucol_open_android(const char * loc, UErrorCode * status) {
  return ucol_open(loc, status);
}
void ucol_setAttribute_android(UCollator * coll, UColAttribute attr, UColAttributeValue value, UErrorCode * status) {
  ucol_setAttribute(coll, attr, value, status);
}
UCollationResult ucol_strcoll_android(const UCollator * coll, const UChar * source, int32_t sourceLength, const UChar * target, int32_t targetLength) {
  return ucol_strcoll(coll, source, sourceLength, target, targetLength);
}
UCollationResult ucol_strcollIter_android(const UCollator * coll, UCharIterator * sIter, UCharIterator * tIter, UErrorCode * status) {
  return ucol_strcollIter(coll, sIter, tIter, status);
}
void ucsdet_close_android(UCharsetDetector * ucsd) {
  ucsdet_close(ucsd);
}
const UCharsetMatch * ucsdet_detect_android(UCharsetDetector * ucsd, UErrorCode * status) {
  return ucsdet_detect(ucsd, status);
}
const UCharsetMatch ** ucsdet_detectAll_android(UCharsetDetector * ucsd, int32_t * matchesFound, UErrorCode * status) {
  return ucsdet_detectAll(ucsd, matchesFound, status);
}
int32_t ucsdet_getConfidence_android(const UCharsetMatch * ucsm, UErrorCode * status) {
  return ucsdet_getConfidence(ucsm, status);
}
const char * ucsdet_getName_android(const UCharsetMatch * ucsm, UErrorCode * status) {
  return ucsdet_getName(ucsm, status);
}
UCharsetDetector * ucsdet_open_android(UErrorCode * status) {
  return ucsdet_open(status);
}
void ucsdet_setText_android(UCharsetDetector * ucsd, const char * textIn, int32_t len, UErrorCode * status) {
  ucsdet_setText(ucsd, textIn, len, status);
}
void udatpg_close_android(UDateTimePatternGenerator * dtpg) {
  udatpg_close(dtpg);
}
int32_t udatpg_getBestPattern_android(UDateTimePatternGenerator * dtpg, const UChar * skeleton, int32_t length, UChar * bestPattern, int32_t capacity, UErrorCode * pErrorCode) {
  return udatpg_getBestPattern(dtpg, skeleton, length, bestPattern, capacity, pErrorCode);
}
UDateTimePatternGenerator * udatpg_open_android(const char * locale, UErrorCode * pErrorCode) {
  return udatpg_open(locale, pErrorCode);
}
void uiter_setUTF8_android(UCharIterator * iter, const char * s, int32_t length) {
  uiter_setUTF8(iter, s, length);
}
int32_t uloc_addLikelySubtags_android(const char * localeID, char * maximizedLocaleID, int32_t maximizedLocaleIDCapacity, UErrorCode * err) {
  return uloc_addLikelySubtags(localeID, maximizedLocaleID, maximizedLocaleIDCapacity, err);
}
int32_t uloc_canonicalize_android(const char * localeID, char * name, int32_t nameCapacity, UErrorCode * err) {
  return uloc_canonicalize(localeID, name, nameCapacity, err);
}
int32_t uloc_countAvailable_android() {
  return uloc_countAvailable();
}
int32_t uloc_forLanguageTag_android(const char * langtag, char * localeID, int32_t localeIDCapacity, int32_t * parsedLength, UErrorCode * err) {
  return uloc_forLanguageTag(langtag, localeID, localeIDCapacity, parsedLength, err);
}
const char * uloc_getAvailable_android(int32_t n) {
  return uloc_getAvailable(n);
}
const char * uloc_getDefault_android() {
  return uloc_getDefault();
}
const char * uloc_getISO3Country_android(const char * localeID) {
  return uloc_getISO3Country(localeID);
}
const char * uloc_getISO3Language_android(const char * localeID) {
  return uloc_getISO3Language(localeID);
}
const char *const * uloc_getISOCountries_android() {
  return uloc_getISOCountries();
}
const char *const * uloc_getISOLanguages_android() {
  return uloc_getISOLanguages();
}
int32_t uloc_getName_android(const char * localeID, char * name, int32_t nameCapacity, UErrorCode * err) {
  return uloc_getName(localeID, name, nameCapacity, err);
}
int32_t uloc_getScript_android(const char * localeID, char * script, int32_t scriptCapacity, UErrorCode * err) {
  return uloc_getScript(localeID, script, scriptCapacity, err);
}
void uloc_setDefault_android(const char * localeID, UErrorCode * status) {
  uloc_setDefault(localeID, status);
}
int32_t uloc_toLanguageTag_android(const char * localeID, char * langtag, int32_t langtagCapacity, UBool strict, UErrorCode * err) {
  return uloc_toLanguageTag(localeID, langtag, langtagCapacity, strict, err);
}
UChar32 unorm2_composePair_android(const UNormalizer2 * norm2, UChar32 a, UChar32 b) {
  return unorm2_composePair(norm2, a, b);
}
int32_t unorm2_getDecomposition_android(const UNormalizer2 * norm2, UChar32 c, UChar * decomposition, int32_t capacity, UErrorCode * pErrorCode) {
  return unorm2_getDecomposition(norm2, c, decomposition, capacity, pErrorCode);
}
const UNormalizer2 * unorm2_getNFCInstance_android(UErrorCode * pErrorCode) {
  return unorm2_getNFCInstance(pErrorCode);
}
const UNormalizer2 * unorm2_getNFDInstance_android(UErrorCode * pErrorCode) {
  return unorm2_getNFDInstance(pErrorCode);
}
int32_t unorm2_getRawDecomposition_android(const UNormalizer2 * norm2, UChar32 c, UChar * decomposition, int32_t capacity, UErrorCode * pErrorCode) {
  return unorm2_getRawDecomposition(norm2, c, decomposition, capacity, pErrorCode);
}
void uregex_close_android(URegularExpression * regexp) {
  uregex_close(regexp);
}
UBool uregex_matches_android(URegularExpression * regexp, int32_t startIndex, UErrorCode * status) {
  return uregex_matches(regexp, startIndex, status);
}
URegularExpression * uregex_open_android(const UChar * pattern, int32_t patternLength, uint32_t flags, UParseError * pe, UErrorCode * status) {
  return uregex_open(pattern, patternLength, flags, pe, status);
}
void uregex_setText_android(URegularExpression * regexp, const UChar * text, int32_t textLength, UErrorCode * status) {
  uregex_setText(regexp, text, textLength, status);
}
void ures_close_android(UResourceBundle * resourceBundle) {
  ures_close(resourceBundle);
}
UResourceBundle * ures_getByIndex_android(const UResourceBundle * resourceBundle, int32_t indexR, UResourceBundle * fillIn, UErrorCode * status) {
  return ures_getByIndex(resourceBundle, indexR, fillIn, status);
}
UResourceBundle * ures_getByKey_android(const UResourceBundle * resourceBundle, const char * key, UResourceBundle * fillIn, UErrorCode * status) {
  return ures_getByKey(resourceBundle, key, fillIn, status);
}
const UChar * ures_getString_android(const UResourceBundle * resourceBundle, int32_t * len, UErrorCode * status) {
  return ures_getString(resourceBundle, len, status);
}
UResourceBundle * ures_openDirect_android(const char * packageName, const char * locale, UErrorCode * status) {
  return ures_openDirect(packageName, locale, status);
}
UScriptCode uscript_getScript_android(UChar32 codepoint, UErrorCode * err) {
  return uscript_getScript(codepoint, err);
}
const char * uscript_getShortName_android(UScriptCode scriptCode) {
  return uscript_getShortName(scriptCode);
}
UText * utext_close_android(UText * ut) {
  return utext_close(ut);
}
UText * utext_openUChars_android(UText * ut, const UChar * s, int64_t length, UErrorCode * status) {
  return utext_openUChars(ut, s, length, status);
}
UText * utext_openUTF8_android(UText * ut, const char * s, int64_t length, UErrorCode * status) {
  return utext_openUTF8(ut, s, length, status);
}
void utrans_close_android(UTransliterator * trans) {
  utrans_close(trans);
}
UTransliterator * utrans_openU_android(const UChar * id, int32_t idLength, UTransDirection dir, const UChar * rules, int32_t rulesLength, UParseError * parseError, UErrorCode * pErrorCode) {
  return utrans_openU(id, idLength, dir, rules, rulesLength, parseError, pErrorCode);
}
void utrans_transUChars_android(const UTransliterator * trans, UChar * text, int32_t * textLength, int32_t textCapacity, int32_t start, int32_t * limit, UErrorCode * status) {
  utrans_transUChars(trans, text, textLength, textCapacity, start, limit, status);
}
}