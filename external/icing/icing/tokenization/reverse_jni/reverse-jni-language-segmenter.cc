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

#include "icing/tokenization/reverse_jni/reverse-jni-language-segmenter.h"

#include <cctype>
#include <memory>
#include <string>
#include <string_view>

#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/absl_ports/canonical_errors.h"
#include "icing/legacy/core/icing-string-util.h"
#include "icing/tokenization/language-segmenter.h"
#include "icing/tokenization/reverse_jni/reverse-jni-break-iterator.h"
#include "icing/util/character-iterator.h"
#include "icing/util/i18n-utils.h"
#include "icing/util/status-macros.h"

namespace icing {
namespace lib {

class ReverseJniLanguageSegmenterIterator : public LanguageSegmenter::Iterator {
 public:
  explicit ReverseJniLanguageSegmenterIterator(
      std::string_view text,
      std::unique_ptr<ReverseJniBreakIterator> break_iterator)
      : break_iterator_(std::move(break_iterator)),
        text_(text),
        term_start_(text),
        term_end_exclusive_(text) {}

  // Advances to the next term. Returns false if it has reached the end.
  bool Advance() override {
    // Prerequisite check
    if (IsDone()) {
      return false;
    }

    if (term_end_exclusive_.utf16_index() == 0) {
      int first = break_iterator_->First();
      if (!term_start_.MoveToUtf16(first)) {
        // First is guaranteed to succeed and return a position within bonds.
        // So the only possible failure could be an invalid sequence. Mark as
        // DONE and return.
        MarkAsDone();
        return false;
      }
    } else {
      term_start_ = term_end_exclusive_;
    }

    int next_utf16_index_exclusive = break_iterator_->Next();
    // Reached the end
    if (next_utf16_index_exclusive == ReverseJniBreakIterator::kDone) {
      MarkAsDone();
      return false;
    }
    if (!term_end_exclusive_.MoveToUtf16(next_utf16_index_exclusive)) {
      // next_utf16_index_exclusive is guaranteed to be within bonds thanks to
      // the check for kDone above. So the only possible failure could be an
      // invalid sequence. Mark as DONE and return.
      MarkAsDone();
      return false;
    }

    return true;
  }

  // Returns the current term. It can be called only when Advance() returns
  // true.
  std::string_view GetTerm() const override {
    if (IsDone()) {
      return text_.substr(0, 0);
    }
    int term_length =
        term_end_exclusive_.utf8_index() - term_start_.utf8_index();
    if (term_length > 0 && std::isspace(text_[term_start_.utf8_index()])) {
      // Rule 3: multiple continuous whitespaces are treated as one.
      term_length = 1;
    }
    return text_.substr(term_start_.utf8_index(), term_length);
  }

  libtextclassifier3::StatusOr<CharacterIterator> CalculateTermStart()
      override {
    return term_start_;
  }

  libtextclassifier3::StatusOr<CharacterIterator> CalculateTermEndExclusive()
      override {
    return term_end_exclusive_;
  }

  // Resets the iterator to point to the first term that starts after offset.
  // GetTerm will now return that term.
  //
  // Returns:
  //   On success, the starting position of the first term that starts after
  //   offset.
  //   NOT_FOUND if an error occurred or there are no terms that start after
  //   offset.
  //   INVALID_ARGUMENT if offset is out of bounds for the provided text.
  //   ABORTED if an invalid unicode character is encountered while
  //   traversing the text.
  libtextclassifier3::StatusOr<int32_t> ResetToTermStartingAfterUtf32(
      int32_t offset) override {
    if (offset < 0) {
      // Very simple. The first term start after a negative offset is the first
      // term. So just reset to start.
      return ResetToStartUtf32();
    }
    if (IsDone()) {
      // We're done. Need to start from the beginning if we're going to reset
      // properly.
      term_start_ = CharacterIterator(text_);
      term_end_exclusive_ = CharacterIterator(text_);
    }

    // 1. Find the unicode character that contains the byte at offset.
    CharacterIterator offset_iterator = (offset < term_start_.utf32_index())
                                            ? term_start_
                                            : term_end_exclusive_;
    if (!offset_iterator.MoveToUtf32(offset)) {
      if (offset_iterator.utf8_index() != text_.length()) {
        // We returned false for some reason other than hitting the end. This is
        // a real error. Just return.
        MarkAsDone();
        return absl_ports::AbortedError(
            "Could not retrieve valid utf8 character!");
      }
    }
    // Check to see if offset is past the end of the text. If it is, then
    // there's no term starting after it. Return an invalid argument.
    if (offset_iterator.utf8_index() == text_.length()) {
      return absl_ports::InvalidArgumentError(IcingStringUtil::StringPrintf(
          "Illegal offset provided! Offset utf-32:%d, utf-8:%d is not within "
          "bounds of string of length %zu",
          offset_iterator.utf32_index(), offset_iterator.utf8_index(),
          text_.length()));
    }

    // 2. We've got the unicode character containing byte offset. Now, we need
    // to point to the segment that starts after this character.
    int following_utf16_index =
        break_iterator_->Following(offset_iterator.utf16_index());
    if (following_utf16_index == ReverseJniBreakIterator::kDone ||
        !offset_iterator.MoveToUtf16(following_utf16_index)) {
      MarkAsDone();
      return absl_ports::NotFoundError(IcingStringUtil::StringPrintf(
          "No segments begin after provided offset %d.", offset));
    }
    term_end_exclusive_ = offset_iterator;

    // 3. The term_end_exclusive_ points to the start of the term that we want
    // to return. We need to Advance so that term_start_ will now point to this
    // term.
    if (!Advance()) {
      return absl_ports::NotFoundError(IcingStringUtil::StringPrintf(
          "No segments begin after provided offset %d.", offset));
    }
    return term_start_.utf32_index();
  }

  // Resets the iterator to point to the first term that ends before offset.
  // GetTerm will now return that term.
  //
  // Returns:
  //   On success, the starting position of the first term that ends before
  //   offset.
  //   NOT_FOUND if an error occurred or there are no terms that end before
  //   offset.
  //   INVALID_ARGUMENT if offset is out of bounds for the provided text.
  //   ABORTED if an invalid unicode character is encountered while
  //   traversing the text.
  libtextclassifier3::StatusOr<int32_t> ResetToTermEndingBeforeUtf32(
      int32_t offset) override {
    if (offset < 0) {
      return absl_ports::InvalidArgumentError(IcingStringUtil::StringPrintf(
          "Illegal offset provided! Offset %d is not within bounds of string "
          "of length %zu",
          offset, text_.length()));
    }
    if (IsDone()) {
      // We're done. Need to start from the beginning if we're going to reset
      // properly.
      term_start_ = CharacterIterator(text_);
      term_end_exclusive_ = CharacterIterator(text_);
    }

    CharacterIterator offset_iterator = (offset < term_start_.utf32_index())
                                            ? term_start_
                                            : term_end_exclusive_;
    if (!offset_iterator.MoveToUtf32(offset)) {
      // An error occurred. Mark as DONE
      if (offset_iterator.utf8_index() != text_.length()) {
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
    int starting_utf16_index =
        break_iterator_->Preceding(offset_iterator.utf16_index());
    if (starting_utf16_index == ReverseJniBreakIterator::kDone ||
        !offset_iterator.MoveToUtf16(starting_utf16_index)) {
      // Rewind the end indices.
      MarkAsDone();
      return absl_ports::NotFoundError(IcingStringUtil::StringPrintf(
          "No segments end before provided offset %d.", offset));
    }
    term_start_ = offset_iterator;

    // 3. We've correctly set the start index and the iterator currently points
    // to that position. Now we need to find the correct end position and
    // advance the iterator to that position.
    int end_utf16_index = break_iterator_->Next();
    term_end_exclusive_ = term_start_;
    term_end_exclusive_.MoveToUtf16(end_utf16_index);

    // 4. The start and end indices point to a segment, but we need to ensure
    // that this segment is 1) valid and 2) ends before offset. Otherwise, we'll
    // need a segment prior to this one.
    if (term_end_exclusive_.utf32_index() > offset) {
      return ResetToTermEndingBeforeUtf32(term_start_.utf32_index());
    }
    return term_start_.utf32_index();
  }

  libtextclassifier3::StatusOr<int32_t> ResetToStartUtf32() override {
    term_start_ = CharacterIterator(text_);
    term_end_exclusive_ = CharacterIterator(text_);
    if (!Advance()) {
      return absl_ports::NotFoundError(
          "Unable to find any valid terms in text.");
    }
    return term_start_.utf32_index();
  }

 private:
  // Ensures that all members are consistent with the 'Done' state.
  // In the 'Done' state, both term_start_.utf8_index() and
  // term_end_exclusive_.utf8_index() will point to the same character, causing
  // GetTerm() to return an empty string and term_start_.utf16_index() and
  // term_end_exclusive_.utf16_index() will be marked with the kDone value.
  // break_iterator_ may be in any state.
  void MarkAsDone() {
    term_start_ =
        CharacterIterator(text_, /*utf8_index=*/ReverseJniBreakIterator::kDone,
                          /*utf16_index=*/ReverseJniBreakIterator::kDone,
                          /*utf32_index=*/ReverseJniBreakIterator::kDone);
    term_end_exclusive_ =
        CharacterIterator(text_, /*utf8_index=*/ReverseJniBreakIterator::kDone,
                          /*utf16_index=*/ReverseJniBreakIterator::kDone,
                          /*utf32_index=*/ReverseJniBreakIterator::kDone);
  }
  bool IsDone() const {
    // We could just as easily check the other utf indices or the values in
    // term_start_ to check for done. There's no particular reason to choose any
    // one since they should all hold kDone.
    return term_end_exclusive_.utf16_index() == ReverseJniBreakIterator::kDone;
  }

  // All of ReverseJniBreakIterator's functions return UTF-16 boundaries. So
  // this class needs to maintain state to convert between UTF-16 and UTF-8.
  std::unique_ptr<ReverseJniBreakIterator> break_iterator_;

  // Text to be segmented
  std::string_view text_;

  // Index used to track the start position of current term.
  CharacterIterator term_start_;

  // Index used to track the end position of current term.
  CharacterIterator term_end_exclusive_;
};

libtextclassifier3::StatusOr<std::unique_ptr<LanguageSegmenter::Iterator>>
ReverseJniLanguageSegmenter::Segment(const std::string_view text) const {
  ICING_ASSIGN_OR_RETURN(
      std::unique_ptr<ReverseJniBreakIterator> break_iterator,
      ReverseJniBreakIterator::Create(jni_cache_, text, locale_));
  return std::make_unique<ReverseJniLanguageSegmenterIterator>(
      text, std::move(break_iterator));
}

libtextclassifier3::StatusOr<std::vector<std::string_view>>
ReverseJniLanguageSegmenter::GetAllTerms(const std::string_view text) const {
  ICING_ASSIGN_OR_RETURN(std::unique_ptr<LanguageSegmenter::Iterator> iterator,
                         Segment(text));
  std::vector<std::string_view> terms;
  while (iterator->Advance()) {
    terms.push_back(iterator->GetTerm());
  }
  return terms;
}

}  // namespace lib
}  // namespace icing
