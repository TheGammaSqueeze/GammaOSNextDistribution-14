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

#include "icing/tokenization/icu/icu-language-segmenter.h"

#include <cstdint>
#include <memory>
#include <string>
#include <string_view>
#include <utility>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/absl_ports/canonical_errors.h"
#include "icing/absl_ports/mutex.h"
#include "icing/legacy/core/icing-string-util.h"
#include "icing/util/character-iterator.h"
#include "icing/util/i18n-utils.h"
#include "icing/util/status-macros.h"
#include "unicode/ubrk.h"
#include "unicode/uchar.h"
#include "unicode/umachine.h"

namespace icing {
namespace lib {

namespace {
constexpr char kASCIISpace = ' ';
}  // namespace

class IcuLanguageSegmenterIterator : public LanguageSegmenter::Iterator {
 public:
  // Factory function to create a segment iterator based on the given locale.
  //
  // Returns:
  //   An iterator on success
  //   INTERNAL_ERROR if unable to create
  static libtextclassifier3::StatusOr<
      std::unique_ptr<LanguageSegmenter::Iterator>>
  Create(const IcuLanguageSegmenter* creator, UBreakIterator* break_iterator,
         std::string_view text, std::string_view locale) {
    std::unique_ptr<IcuLanguageSegmenterIterator> iterator(
        new IcuLanguageSegmenterIterator(creator, break_iterator, text,
                                         locale));
    if (iterator->Initialize()) {
      return iterator;
    }
    return absl_ports::InternalError("Unable to create a term iterator");
  }

  ~IcuLanguageSegmenterIterator() {
    utext_close(u_text_);
    creator_.ReturnBreakIterator(break_iterator_);
  }

  // Advances to the next term. Returns false if it has reached the end.
  bool Advance() override {
    // Prerequisite check
    if (term_end_index_exclusive_ == UBRK_DONE) {
      return false;
    }

    if (term_end_index_exclusive_ == 0) {
      // First Advance() call
      term_start_index_ = ubrk_first(break_iterator_);
    } else {
      term_start_index_ = term_end_index_exclusive_;
    }
    term_end_index_exclusive_ = ubrk_next(break_iterator_);

    // Reached the end
    if (term_end_index_exclusive_ == UBRK_DONE) {
      MarkAsDone();
      return false;
    }

    return true;
  }

  // Returns the current term. It can be called only when Advance() returns
  // true.
  std::string_view GetTerm() const override {
    int term_length = term_end_index_exclusive_ - term_start_index_;
    if (term_end_index_exclusive_ == UBRK_DONE) {
      term_length = 0;
    } else if (text_[term_start_index_] == kASCIISpace) {
      // Rule 3: multiple continuous whitespaces are treated as one.
      term_length = 1;
    }
    return text_.substr(term_start_index_, term_length);
  }

  libtextclassifier3::StatusOr<CharacterIterator> CalculateTermStart()
      override {
    if (!offset_iterator_.MoveToUtf8(term_start_index_)) {
      return absl_ports::AbortedError(
          "Could not retrieve valid utf8 character!");
    }
    return offset_iterator_;
  }

  libtextclassifier3::StatusOr<CharacterIterator> CalculateTermEndExclusive()
      override {
    if (!offset_iterator_.MoveToUtf8(term_end_index_exclusive_)) {
      return absl_ports::AbortedError(
          "Could not retrieve valid utf8 character!");
    }
    return offset_iterator_;
  }

  libtextclassifier3::StatusOr<int32_t> ResetToTermStartingAfterUtf32(
      int32_t offset) override {
    if (offset < 0) {
      // Very simple. The first term start after a negative offset is the first
      // term. So just reset to start and Advance.
      return ResetToStartUtf32();
    }

    // 1. Find the unicode character that contains the byte at offset.
    if (!offset_iterator_.MoveToUtf32(offset)) {
      // An error occurred. Mark as DONE
      if (offset_iterator_.utf8_index() != text_.length()) {
        // We returned false for some reason other than hitting the end. This is
        // a real error. Just return.
        MarkAsDone();
        return absl_ports::AbortedError(
            "Could not retrieve valid utf8 character!");
      }
    }
    if (offset_iterator_.utf8_index() == text_.length()) {
      return absl_ports::InvalidArgumentError(IcingStringUtil::StringPrintf(
          "Illegal offset provided! Offset utf-32:%d, utf-8:%d is not within "
          "bounds of string of length %zu",
          offset_iterator_.utf32_index(), offset_iterator_.utf8_index(),
          text_.length()));
    }

    // 2. We've got the unicode character containing byte offset. Now, we need
    // to point to the segment that starts after this character.
    int following_utf8_index =
        ubrk_following(break_iterator_, offset_iterator_.utf8_index());
    if (following_utf8_index == UBRK_DONE) {
      MarkAsDone();
      return absl_ports::NotFoundError(IcingStringUtil::StringPrintf(
          "No segments begin after provided offset %d.", offset));
    }
    term_end_index_exclusive_ = following_utf8_index;

    // 3. The term_end_exclusive_ points to the start of the term that we want
    // to return. We need to Advance so that term_start_ will now point to this
    // term.
    if (!Advance()) {
      return absl_ports::NotFoundError(IcingStringUtil::StringPrintf(
          "No segments begin after provided offset %d.", offset));
    }
    if (!offset_iterator_.MoveToUtf8(term_start_index_)) {
      return absl_ports::AbortedError(
          "Could not retrieve valid utf8 character!");
    }
    return offset_iterator_.utf32_index();
  }

  libtextclassifier3::StatusOr<int32_t> ResetToTermEndingBeforeUtf32(
      int32_t offset) override {
    if (offset < 0) {
      return absl_ports::InvalidArgumentError(IcingStringUtil::StringPrintf(
          "Illegal offset provided! Offset %d is not within bounds of string "
          "of length %zu",
          offset, text_.length()));
    }

    if (!offset_iterator_.MoveToUtf32(offset)) {
      // An error occurred. Mark as DONE
      if (offset_iterator_.utf8_index() != text_.length()) {
        // We returned false for some reason other than hitting the end. This is
        // a real error. Just return.
        MarkAsDone();
        return absl_ports::AbortedError(
            "Could not retrieve valid utf8 character!");
      }
      // If it returned false because we hit the end. Then that's fine. We'll
      // just treat it as if the request was for the end.
    }

    // 2. We've got the unicode character containing byte offset. Now, we need
    // to point to the segment that ends before this character.
    int starting_utf8_index =
        ubrk_preceding(break_iterator_, offset_iterator_.utf8_index());
    if (starting_utf8_index == UBRK_DONE) {
      // Rewind the end indices.
      MarkAsDone();
      return absl_ports::NotFoundError(IcingStringUtil::StringPrintf(
          "No segments end before provided offset %d.", offset));
    }
    term_start_index_ = starting_utf8_index;

    // 3. We've correctly set the start index and the iterator currently points
    // to that position. Now we need to find the correct end position and
    // advance the iterator to that position.
    int ending_utf8_index = ubrk_next(break_iterator_);
    if (ending_utf8_index == UBRK_DONE) {
      // This shouldn't ever happen.
      MarkAsDone();
      return absl_ports::AbortedError(IcingStringUtil::StringPrintf(
          "No segments end before provided offset %d.", offset));
    }
    term_end_index_exclusive_ = ending_utf8_index;

    // 4. The start and end indices point to a segment, but we need to ensure
    // that this segment is 1) valid and 2) ends before offset. Otherwise, we'll
    // need a segment prior to this one.
    CharacterIterator term_start_iterator = offset_iterator_;
    if (!term_start_iterator.MoveToUtf8(term_start_index_)) {
      return absl_ports::AbortedError(
          "Could not retrieve valid utf8 character!");
    }
    if (term_end_index_exclusive_ > offset_iterator_.utf8_index()) {
      return ResetToTermEndingBeforeUtf32(term_start_iterator.utf32_index());
    }
    return term_start_iterator.utf32_index();
  }

  libtextclassifier3::StatusOr<int32_t> ResetToStartUtf32() override {
    term_start_index_ = 0;
    term_end_index_exclusive_ = 0;
    if (!Advance()) {
      return absl_ports::NotFoundError(
          "Unable to find any valid terms in text.");
    }
    if (!offset_iterator_.MoveToUtf8(term_start_index_)) {
      return absl_ports::AbortedError(
          "Could not retrieve valid utf8 character!");
    }
    return offset_iterator_.utf32_index();
  }

 private:
  explicit IcuLanguageSegmenterIterator(const IcuLanguageSegmenter* creator,
                                        UBreakIterator* break_iterator,
                                        std::string_view text,
                                        std::string_view locale)
      : creator_(*creator),
        break_iterator_(break_iterator),
        text_(text),
        locale_(locale),
        u_text_(nullptr),
        offset_iterator_(text),
        term_start_index_(0),
        term_end_index_exclusive_(0) {}

  // Returns true on success
  bool Initialize() {
    if (break_iterator_ == nullptr) {
      return false;
    }
    UErrorCode status = U_ZERO_ERROR;
    u_text_ = utext_openUTF8(nullptr, text_.data(), text_.length(), &status);
    if (u_text_ == nullptr) {
      return false;
    }
    ubrk_setUText(break_iterator_, u_text_, &status);
    return !U_FAILURE(status);
  }

  libtextclassifier3::Status ResetToTermStartingBefore(int32_t offset) {
    term_start_index_ = ubrk_preceding(break_iterator_, offset);
    if (term_start_index_ == UBRK_DONE) {
      MarkAsDone();
      return absl_ports::NotFoundError("");
    }
    term_end_index_exclusive_ = ubrk_next(break_iterator_);
    if (term_end_index_exclusive_ == UBRK_DONE) {
      MarkAsDone();
      return absl_ports::NotFoundError("");
    }
    return libtextclassifier3::Status::OK;
  }

  // Ensures that all members are consistent with the 'Done' state.
  // In the 'Done' state, term_start_index_ will point to the first character
  // and term_end_index_exclusive_ will be marked with the kDone value.
  // break_iterator_ may be in any state.
  void MarkAsDone() {
    term_end_index_exclusive_ = UBRK_DONE;
    term_start_index_ = 0;
  }

  const IcuLanguageSegmenter& creator_;  // Does not own.

  // The underlying class that does the segmentation, ubrk_close() must be
  // called after using.
  UBreakIterator* break_iterator_;  // Does not own

  // Text to be segmented
  std::string_view text_;

  // Locale of the input text, used to help segment more accurately. If a
  // wrong locale is set, text could probably still be segmented correctly
  // because the default break iterator behavior is used for most locales.
  std::string_view locale_;

  // A thin wrapper around the input UTF8 text, needed by break_iterator_.
  // Allocated by calling utext_openUtf8() and freed by calling utext_close().
  UText* u_text_;

  // Offset iterator. This iterator is not guaranteed to point to any particular
  // character, but is guaranteed to point to a valid UTF character sequence.
  //
  // This iterator is used to save some amount of linear traversal when seeking
  // to a specific UTF-32 offset. Each function that uses it could just create
  // a CharacterIterator starting at the beginning of the text and traverse
  // forward from there.
  CharacterIterator offset_iterator_;

  // The start and end indices are used to track the positions of current
  // term.
  int term_start_index_;
  int term_end_index_exclusive_;
};

/* static */ libtextclassifier3::StatusOr<std::unique_ptr<IcuLanguageSegmenter>>
IcuLanguageSegmenter::Create(std::string&& locale) {
  UErrorCode status = U_ZERO_ERROR;
  UBreakIterator* break_iterator = ubrk_open(
      UBRK_WORD, locale.c_str(), /*text=*/nullptr, /*textLength=*/0, &status);
  if (U_FAILURE(status) || break_iterator == nullptr) {
    return absl_ports::AbortedError(
        "Unable to create ICU break_iterator for language segmentation");
  }
  return std::unique_ptr<IcuLanguageSegmenter>(
      new IcuLanguageSegmenter(std::move(locale), break_iterator));
}

UBreakIterator* IcuLanguageSegmenter::ProduceBreakIterator() const {
  UBreakIterator* itr = nullptr;
  {
    absl_ports::unique_lock l(&mutex_);
    if (cached_break_iterator_ != nullptr) {
      itr = cached_break_iterator_;
      cached_break_iterator_ = nullptr;
    }
  }
  if (itr == nullptr) {
    UErrorCode status = U_ZERO_ERROR;
    itr = ubrk_open(UBRK_WORD, locale_.c_str(), /*text=*/nullptr,
                    /*textLength=*/0, &status);
    if (U_FAILURE(status)) {
      itr = nullptr;
    }
  }
  return itr;
}

void IcuLanguageSegmenter::ReturnBreakIterator(UBreakIterator* itr) const {
  {
    absl_ports::unique_lock l(&mutex_);
    if (cached_break_iterator_ == nullptr) {
      cached_break_iterator_ = itr;
      return;
    }
  }
  ubrk_close(itr);
}

libtextclassifier3::StatusOr<std::unique_ptr<LanguageSegmenter::Iterator>>
IcuLanguageSegmenter::Segment(const std::string_view text) const {
  return IcuLanguageSegmenterIterator::Create(this, ProduceBreakIterator(),
                                              text, locale_);
}

libtextclassifier3::StatusOr<std::vector<std::string_view>>
IcuLanguageSegmenter::GetAllTerms(const std::string_view text) const {
  ICING_ASSIGN_OR_RETURN(
      std::unique_ptr<LanguageSegmenter::Iterator> iterator,
      Segment(text));
  std::vector<std::string_view> terms;
  while (iterator->Advance()) {
    terms.push_back(iterator->GetTerm());
  }
  return terms;
}

}  // namespace lib
}  // namespace icing
