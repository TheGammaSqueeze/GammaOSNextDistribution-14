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

#ifndef ICING_TOKENIZATION_ICU_ICU_LANGUAGE_SEGMENTER_H_
#define ICING_TOKENIZATION_ICU_ICU_LANGUAGE_SEGMENTER_H_

#include <cstdint>
#include <memory>
#include <string>
#include <string_view>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/absl_ports/mutex.h"
#include "icing/tokenization/language-segmenter.h"
#include "unicode/ubrk.h"

namespace icing {
namespace lib {

// This class is used to segment sentences into words based on rules
// (https://unicode.org/reports/tr29/#Word_Boundaries) and language
// understanding. Based on the basic segmentation done by UBreakIterator,
// some extra rules are applied in this class:
//
// 1. All ASCII terms will be returned.
// 2. For non-ASCII terms, only the alphabetic terms are returned, which means
//    non-ASCII punctuation and special characters are left out.
// 3. Multiple continuous whitespaces are treated as one.
//
// The rules above are common to the high-level tokenizers that might use this
// class. Other special tokenization logic will be in each tokenizer.
class IcuLanguageSegmenter : public LanguageSegmenter {
 public:
  static libtextclassifier3::StatusOr<std::unique_ptr<IcuLanguageSegmenter>>
  Create(std::string&& locale);

  ~IcuLanguageSegmenter() override {
    if (cached_break_iterator_ != nullptr) {
      ubrk_close(cached_break_iterator_);
    }
  }

  IcuLanguageSegmenter(const IcuLanguageSegmenter&) = delete;
  IcuLanguageSegmenter& operator=(const IcuLanguageSegmenter&) = delete;

  // The segmentation depends on the language detected in the input text.
  //
  // Note: It could happen that the language detected from text is wrong, then
  // there would be a small chance that the text is segmented incorrectly.
  //
  // Returns:
  //   An iterator of terms on success
  //   INTERNAL_ERROR if any error occurs
  libtextclassifier3::StatusOr<std::unique_ptr<LanguageSegmenter::Iterator>>
  Segment(std::string_view text) const override;

  // The segmentation depends on the language detected in the input text.
  //
  // Note: It could happen that the language detected from text is wrong, then
  // there would be a small chance that the text is segmented incorrectly.
  //
  // Returns:
  //   A list of terms on success
  //   INTERNAL_ERROR if any error occurs
  libtextclassifier3::StatusOr<std::vector<std::string_view>> GetAllTerms(
      std::string_view text) const override;

 private:
  // Declared a friend so that it can call AcceptBreakIterator.
  friend class IcuLanguageSegmenterIterator;

  explicit IcuLanguageSegmenter(std::string&& locale, UBreakIterator* iterator)
      : locale_(std::move(locale)), cached_break_iterator_(iterator) {}

  // Returns a UBreakIterator that the caller owns.
  // If cached_break_iterator_ is non-null, transfers ownership to caller and
  // sets cached_break_iterator_ to null.
  // If cached_break_iterator is null, creates a new UBreakIterator and
  // transfers ownership to caller.
  UBreakIterator* ProduceBreakIterator() const;

  // Caller transfers ownership of itr to IcuLanguageSegmenter.
  // If cached_break_iterator_ is null, itr becomes the cached_break_iterator_
  // If cached_break_iterator_ is non-null, then itr will be closed.
  void ReturnBreakIterator(UBreakIterator* itr) const;

  // Used to help segment text
  const std::string locale_;

  // The underlying class that does the segmentation, ubrk_close() must be
  // called after using.
  mutable UBreakIterator* cached_break_iterator_ ICING_GUARDED_BY(mutex_);

  mutable absl_ports::shared_mutex mutex_;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_TOKENIZATION_ICU_ICU_LANGUAGE_SEGMENTER_H_
