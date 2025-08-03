// Copyright (C) 2019 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include "icing/transform/icu/icu-normalizer.h"

#include <cctype>
#include <memory>
#include <string>
#include <string_view>
#include <utility>

#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/absl_ports/canonical_errors.h"
#include "icing/absl_ports/str_cat.h"
#include "icing/transform/normalizer.h"
#include "icing/util/i18n-utils.h"
#include "icing/util/logging.h"
#include "icing/util/status-macros.h"
#include "unicode/umachine.h"
#include "unicode/unorm2.h"
#include "unicode/ustring.h"
#include "unicode/utrans.h"

namespace icing {
namespace lib {

namespace {

// The following is the compound id used to tell UTransliterator how to
// transform terms. The basic normalization forms NFD (canonical normalization
// form decomposition) and NFKC (compatible normalization form composition)
// are applied as well as some other rules we need. More information at
// http://www.unicode.org/reports/tr15/
//
// Please note that the following rules don't support small hiragana to katakana
// transformation.
constexpr UChar kTransformRulesUtf16[] =
    u"Lower; "                      // Lowercase
    "Latin-ASCII; "                 // Map Latin characters to ASCII characters
    "Hiragana-Katakana; "           // Map hiragana to katakana
    "[:Latin:] NFD; "               // Decompose Latin letters
    "[:Greek:] NFD; "               // Decompose Greek letters
    "[:Nonspacing Mark:] Remove; "  // Remove accent / diacritic marks
    "NFKC";                         // Decompose and compose everything

// Length of the transform rules excluding the terminating NULL.
constexpr int kTransformRulesLength =
    sizeof(kTransformRulesUtf16) / sizeof(kTransformRulesUtf16[0]) - 1;

// Transforms a Unicode character with diacritics to its counterpart in ASCII
// range. E.g. "ü" -> "u". Result will be set to char_out. Returns true if
// the transformation is successful.
//
// NOTE: According to our convention this function should have returned
// StatusOr<char>. However, this function is performance-sensitive because is
// could be called on every Latin character in normalization, so we make it
// return a bool here to save a bit more time and memory.
bool DiacriticCharToAscii(const UNormalizer2* normalizer2, UChar32 uchar32_in,
                          char* char_out) {
  if (i18n_utils::IsAscii(uchar32_in)) {
    // The Unicode character is within ASCII range
    if (char_out != nullptr) {
      *char_out = uchar32_in;
    }
    return true;
  }

  // Maximum number of pieces a Unicode character can be decomposed into.
  // TODO(tjbarron) figure out if this number is proper.
  constexpr int kDecompositionBufferCapacity = 5;

  // A buffer used to store Unicode decomposition mappings of only one
  // character.
  UChar decomposition_buffer[kDecompositionBufferCapacity];

  // Decomposes the Unicode character, trying to get an ASCII char and some
  // diacritic chars.
  UErrorCode status = U_ZERO_ERROR;
  if (unorm2_getDecomposition(normalizer2, uchar32_in, &decomposition_buffer[0],
                              kDecompositionBufferCapacity, &status) > 0 &&
      !U_FAILURE(status) && i18n_utils::IsAscii(decomposition_buffer[0])) {
    if (char_out != nullptr) {
      *char_out = decomposition_buffer[0];
    }
    return true;
  }
  return false;
}

}  // namespace

// Creates a IcuNormalizer with a valid TermTransformer instance.
//
// Note: UTokenizer2 is also an option to normalize Unicode strings, but since
// we need some custom transform rules other than NFC/NFKC we have to use
// TermTransformer as a custom transform rule executor.
libtextclassifier3::StatusOr<std::unique_ptr<IcuNormalizer>>
IcuNormalizer::Create(int max_term_byte_size) {
  ICING_ASSIGN_OR_RETURN(
      std::unique_ptr<IcuNormalizer::TermTransformer> term_transformer,
      IcuNormalizer::TermTransformer::Create());

  return std::unique_ptr<IcuNormalizer>(
      new IcuNormalizer(std::move(term_transformer), max_term_byte_size));
}

IcuNormalizer::IcuNormalizer(
    std::unique_ptr<IcuNormalizer::TermTransformer> term_transformer,
    int max_term_byte_size)
    : term_transformer_(std::move(term_transformer)),
      max_term_byte_size_(max_term_byte_size) {}

std::string IcuNormalizer::NormalizeTerm(const std::string_view term) const {
  std::string normalized_text;

  if (term.empty()) {
    return normalized_text;
  }

  UErrorCode status = U_ZERO_ERROR;
  // ICU manages the singleton instance
  const UNormalizer2* normalizer2 = unorm2_getNFCInstance(&status);
  if (U_FAILURE(status)) {
    ICING_LOG(WARNING) << "Failed to create a UNormalizer2 instance";
  }

  // Normalize the prefix that can be transformed into ASCII.
  // This is a faster method to normalize Latin terms.
  NormalizeLatinResult result = NormalizeLatin(normalizer2, term);
  normalized_text = std::move(result.text);
  if (result.end_pos < term.length()) {
    // Some portion of term couldn't be normalized via NormalizeLatin. Use
    // term_transformer to handle this portion.
    std::string_view rest_term = term.substr(result.end_pos);
    absl_ports::StrAppend(&normalized_text,
                          term_transformer_->Transform(rest_term));
  }

  if (normalized_text.length() > max_term_byte_size_) {
    i18n_utils::SafeTruncateUtf8(&normalized_text, max_term_byte_size_);
  }

  return normalized_text;
}

IcuNormalizer::NormalizeLatinResult IcuNormalizer::NormalizeLatin(
    const UNormalizer2* normalizer2, const std::string_view term) const {
  NormalizeLatinResult result = {};
  if (normalizer2 == nullptr) {
    return result;
  }
  CharacterIterator char_itr(term);
  result.text.reserve(term.length());
  char ascii_char;
  while (char_itr.utf8_index() < term.length()) {
    UChar32 c = char_itr.GetCurrentChar();
    if (i18n_utils::IsAscii(c)) {
      result.text.push_back(std::tolower(c));
    } else if (DiacriticCharToAscii(normalizer2, c, &ascii_char)) {
      result.text.push_back(std::tolower(ascii_char));
    } else {
      // We don't know how to transform / decompose this Unicode character, it
      // probably means that some other Unicode characters are mixed with Latin
      // characters. We return the partial result here and let the caller handle
      // the rest.
      result.end_pos = char_itr.utf8_index();
      return result;
    }
    char_itr.AdvanceToUtf32(char_itr.utf32_index() + 1);
  }
  result.end_pos = term.length();
  return result;
}

libtextclassifier3::StatusOr<std::unique_ptr<IcuNormalizer::TermTransformer>>
IcuNormalizer::TermTransformer::Create() {
  UErrorCode status = U_ZERO_ERROR;
  UTransliterator* term_transformer = utrans_openU(
      kTransformRulesUtf16, kTransformRulesLength, UTRANS_FORWARD,
      /*rules=*/nullptr, /*rulesLength=*/0, /*parseError=*/nullptr, &status);

  if (U_FAILURE(status)) {
    return absl_ports::InternalError("Failed to create UTransliterator.");
  }

  return std::unique_ptr<IcuNormalizer::TermTransformer>(
      new IcuNormalizer::TermTransformer(term_transformer));
}

IcuNormalizer::TermTransformer::TermTransformer(
    UTransliterator* u_transliterator)
    : u_transliterator_(u_transliterator) {}

IcuNormalizer::TermTransformer::~TermTransformer() {
  if (u_transliterator_ != nullptr) {
    utrans_close(u_transliterator_);
  }
}

std::string IcuNormalizer::TermTransformer::Transform(
    const std::string_view term) const {
  auto utf16_term_or = i18n_utils::Utf8ToUtf16(term);
  if (!utf16_term_or.ok()) {
    ICING_VLOG(0) << "Failed to convert UTF8 term '" << term << "' to UTF16";
    return std::string(term);
  }
  std::u16string utf16_term = std::move(utf16_term_or).ValueOrDie();
  UErrorCode status = U_ZERO_ERROR;
  int utf16_term_desired_length = utf16_term.length();
  int limit = utf16_term.length();
  utrans_transUChars(u_transliterator_, &utf16_term[0],
                     &utf16_term_desired_length, utf16_term.length(),
                     /*start=*/0, &limit, &status);

  // For most cases, one Unicode character is normalized to exact one Unicode
  // character according to our transformation rules. However, there could be
  // some rare cases where the normalized text is longer than the original
  // one. E.g. "¼" (1 character) -> "1/4" (3 characters). That causes a buffer
  // overflow error and we need to increase our buffer size and try again.
  if (status == U_BUFFER_OVERFLOW_ERROR) {
    // 'utf16_term_desired_length' has already been set to the desired value
    // by utrans_transUChars(), here we increase the buffer size to that
    // value.
    //
    // NOTE: we need to call resize() but not reserve() because values can't
    // be set at positions after length().
    int original_content_length = utf16_term.length();
    utf16_term.resize(utf16_term_desired_length);
    utf16_term_desired_length = original_content_length;
    limit = original_content_length;
    status = U_ZERO_ERROR;
    utrans_transUChars(u_transliterator_, &utf16_term[0],
                       &utf16_term_desired_length, utf16_term.length(),
                       /*start=*/0, &limit, &status);
  }

  if (U_FAILURE(status)) {
    // Failed to transform, return its original form.
    ICING_LOG(WARNING) << "Failed to normalize UTF8 term: " << term;
    return std::string(term);
  }

  auto utf8_term_or = i18n_utils::Utf16ToUtf8(utf16_term);
  if (!utf8_term_or.ok()) {
    ICING_VLOG(0) << "Failed to convert UTF16 term '" << term << "' to UTF8";
    return std::string(term);
  }
  return std::move(utf8_term_or).ValueOrDie();
}

bool IcuNormalizer::FindNormalizedLatinMatchEndPosition(
    const UNormalizer2* normalizer2, std::string_view term,
    CharacterIterator& char_itr, std::string_view normalized_term,
    CharacterIterator& normalized_char_itr) const {
  if (normalizer2 == nullptr) {
    return false;
  }
  char ascii_char;
  while (char_itr.utf8_index() < term.length() &&
         normalized_char_itr.utf8_index() < normalized_term.length()) {
    UChar32 c = char_itr.GetCurrentChar();
    if (i18n_utils::IsAscii(c)) {
      c = std::tolower(c);
    } else if (DiacriticCharToAscii(normalizer2, c, &ascii_char)) {
      c = std::tolower(ascii_char);
    } else {
      return false;
    }
    UChar32 normalized_c = normalized_char_itr.GetCurrentChar();
    if (c != normalized_c) {
      return true;
    }
    char_itr.AdvanceToUtf32(char_itr.utf32_index() + 1);
    normalized_char_itr.AdvanceToUtf32(normalized_char_itr.utf32_index() + 1);
  }
  return true;
}

CharacterIterator
IcuNormalizer::TermTransformer::FindNormalizedNonLatinMatchEndPosition(
    std::string_view term, CharacterIterator char_itr,
    std::string_view normalized_term) const {
  CharacterIterator normalized_char_itr(normalized_term);
  UErrorCode status = U_ZERO_ERROR;

  constexpr int kUtf16CharBufferLength = 6;
  UChar c16[kUtf16CharBufferLength];
  int32_t c16_length;
  int32_t limit;

  constexpr int kCharBufferLength = 3 * 4;
  char normalized_buffer[kCharBufferLength];
  int32_t c8_length;
  while (char_itr.utf8_index() < term.length() &&
         normalized_char_itr.utf8_index() < normalized_term.length()) {
    UChar32 c = char_itr.GetCurrentChar();
    int c_lenth = i18n_utils::GetUtf8Length(c);
    u_strFromUTF8(c16, kUtf16CharBufferLength, &c16_length,
                  term.data() + char_itr.utf8_index(),
                  /*srcLength=*/c_lenth, &status);
    if (U_FAILURE(status)) {
      break;
    }

    limit = c16_length;
    utrans_transUChars(u_transliterator_, c16, &c16_length,
                       kUtf16CharBufferLength,
                       /*start=*/0, &limit, &status);
    if (U_FAILURE(status)) {
      break;
    }

    u_strToUTF8(normalized_buffer, kCharBufferLength, &c8_length, c16,
                c16_length, &status);
    if (U_FAILURE(status)) {
      break;
    }

    for (int i = 0; i < c8_length; ++i) {
      if (normalized_buffer[i] !=
          normalized_term[normalized_char_itr.utf8_index() + i]) {
        return char_itr;
      }
    }
    normalized_char_itr.AdvanceToUtf8(normalized_char_itr.utf8_index() +
                                      c8_length);
    char_itr.AdvanceToUtf32(char_itr.utf32_index() + 1);
  }
  if (U_FAILURE(status)) {
    // Failed to transform, return its original form.
    ICING_LOG(WARNING) << "Failed to normalize UTF8 term: " << term;
  }
  return char_itr;
}

CharacterIterator IcuNormalizer::FindNormalizedMatchEndPosition(
    std::string_view term, std::string_view normalized_term) const {
  UErrorCode status = U_ZERO_ERROR;
  // ICU manages the singleton instance
  const UNormalizer2* normalizer2 = unorm2_getNFCInstance(&status);
  if (U_FAILURE(status)) {
    ICING_LOG(WARNING) << "Failed to create a UNormalizer2 instance";
  }

  CharacterIterator char_itr(term);
  CharacterIterator normalized_char_itr(normalized_term);
  if (FindNormalizedLatinMatchEndPosition(
          normalizer2, term, char_itr, normalized_term, normalized_char_itr)) {
    return char_itr;
  }
  // Some portion of term couldn't be normalized via
  // FindNormalizedLatinMatchEndPosition. Use term_transformer to handle this
  // portion.
  std::string_view rest_normalized_term =
      normalized_term.substr(normalized_char_itr.utf8_index());
  return term_transformer_->FindNormalizedNonLatinMatchEndPosition(
      term, char_itr, rest_normalized_term);
}

}  // namespace lib
}  // namespace icing
