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

#ifndef ICING_TRANSFORM_ICU_ICU_NORMALIZER_H_
#define ICING_TRANSFORM_ICU_ICU_NORMALIZER_H_

#include <memory>
#include <string>
#include <string_view>

#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/transform/normalizer.h"
#include "icing/util/character-iterator.h"
#include "unicode/unorm2.h"
#include "unicode/utrans.h"

namespace icing {
namespace lib {

// Used to normalize UTF8 strings for text matching. It enforces a set of rules:
//  1. Transforms text to be lowercase UTF8.
//  2. Transforms full-width Latin characters to ASCII characters if possible.
//  3. Transforms hiragana to katakana.
//  4. Removes accent / diacritic marks on Latin characters
//  5. Removes accent / diacritic marks on Greek characters
//  6. Normalized text must be less than or equal to max_term_byte_size,
//     otherwise it will be truncated.
//
// There're some other rules from ICU not listed here, please see .cc file for
// details.
class IcuNormalizer : public Normalizer {
 public:
  // Creates a normalizer with the subcomponents it needs. max_term_byte_size
  // enforces the max size of text after normalization, text will be truncated
  // if exceeds the max size.
  //
  // Returns:
  //   A normalizer on success
  //   INVALID_ARGUMENT if max_term_byte_size <= 0
  //   INTERNAL_ERROR if failed to create any subcomponent
  static libtextclassifier3::StatusOr<std::unique_ptr<IcuNormalizer>> Create(
      int max_term_byte_size);

  // Normalizes the input term based on rules. See .cc file for rule details.
  //
  // NOTE: Term should not mix Latin and non-Latin characters. Doing so may
  // result in the non-Latin characters not properly being normalized
  std::string NormalizeTerm(std::string_view term) const override;

  // Returns a CharacterIterator pointing to one past the end of the segment of
  // term that (once normalized) matches with normalized_term.
  //
  // Ex. FindNormalizedMatchEndPosition("YELLOW", "yell") will return
  // CharacterIterator(u8:4, u16:4, u32:4).
  //
  // Ex. FindNormalizedMatchEndPosition("YELLOW", "red") will return
  // CharacterIterator(u8:0, u16:0, u32:0).
  CharacterIterator FindNormalizedMatchEndPosition(
      std::string_view term, std::string_view normalized_term) const override;

 private:
  // A handler class that helps manage the lifecycle of UTransliterator. It's
  // used in IcuNormalizer to transform terms into the formats we need.
  class TermTransformer {
   public:
    // Creates TermTransformer with a valid UTransliterator instance
    //
    // Returns:
    //   A term transformer on success
    //   INTERNAL_ERROR if failed to create any subcomponent
    static libtextclassifier3::StatusOr<std::unique_ptr<TermTransformer>>
    Create();

    // Closes the UTransliterator instance
    ~TermTransformer();

    // Transforms the text based on our rules described at top of this file
    std::string Transform(std::string_view term) const;

    // Returns a CharacterIterator pointing to one past the end of the segment
    // of a non-latin term that (once normalized) matches with normalized_term.
    CharacterIterator FindNormalizedNonLatinMatchEndPosition(
        std::string_view term, CharacterIterator char_itr,
        std::string_view normalized_term) const;

   private:
    explicit TermTransformer(UTransliterator* u_transliterator);

    // An ICU class to execute custom term transformation / normalization rules.
    // utrans_close() must by called after using.
    UTransliterator* u_transliterator_;
  };

  struct NormalizeLatinResult {
    // A string representing the maximum prefix of term (can be empty or term
    // itself) that can be normalized into ASCII.
    std::string text;
    // The first position of the char within term that normalization failed to
    // transform into an ASCII char, or term.length() if all chars can be
    // transformed.
    size_t end_pos;
  };

  explicit IcuNormalizer(std::unique_ptr<TermTransformer> term_transformer,
                         int max_term_byte_size);

  // Helper method to normalize Latin terms only. Rules applied:
  // 1. Uppercase to lowercase
  // 2. Remove diacritic (accent) marks
  NormalizeLatinResult NormalizeLatin(const UNormalizer2* normalizer2,
                                      std::string_view term) const;

  // Set char_itr and normalized_char_itr to point to one past the end of the
  // segments of term and normalized_term that can match if normalized into
  // ASCII. In this case, true will be returned.
  //
  // The method stops at the position when char_itr cannot be normalized into
  // ASCII and returns false, so that term_transformer can handle the remaining
  // portion.
  bool FindNormalizedLatinMatchEndPosition(
      const UNormalizer2* normalizer2, std::string_view term,
      CharacterIterator& char_itr, std::string_view normalized_term,
      CharacterIterator& normalized_char_itr) const;

  // Used to transform terms into their normalized forms.
  std::unique_ptr<TermTransformer> term_transformer_;

  // The maximum term length allowed after normalization.
  int max_term_byte_size_;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_TRANSFORM_ICU_ICU_NORMALIZER_H_
