/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef LIBTEXTCLASSIFIER_ANNOTATOR_POD_NER_UTILS_H_
#define LIBTEXTCLASSIFIER_ANNOTATOR_POD_NER_UTILS_H_

#include <string>
#include <vector>

#include "annotator/model_generated.h"
#include "annotator/types.h"
#include "absl/strings/string_view.h"

namespace libtextclassifier3 {
// Converts saft labels like /saft/person to collection name 'person'.
std::string SaftLabelToCollection(absl::string_view saft_label);

struct WordpieceSpan {
  // Beginning index is inclusive, end index is exclusive.
  WordpieceSpan() : begin(0), end(0) {}
  WordpieceSpan(int begin, int end) : begin(begin), end(end) {}
  int begin;
  int end;
  bool operator==(const WordpieceSpan &other) const {
    return this->begin == other.begin && this->end == other.end;
  }
  int length() { return end - begin; }
};

namespace internal {
// Finds the wordpiece window arond the given span_of_interest. If the number
// of wordpieces in this window is smaller than max_num_wordpieces_in_window
// it is expanded around the span of interest.
WordpieceSpan FindWordpiecesWindowAroundSpan(
    const CodepointSpan &span_of_interest, const std::vector<Token> &tokens,
    const std::vector<int32_t> &word_starts, int num_wordpieces,
    int max_num_wordpieces_in_window);
// Expands the given wordpiece window around the given window to the be
// maximal possible while making sure it includes only full tokens.
WordpieceSpan ExpandWindowAndAlign(int max_num_wordpieces_in_window,
                                   int num_wordpieces,
                                   WordpieceSpan wordpiece_span_to_expand);
// Returns the index of the last token which ends before wordpiece_end.
int FindLastFullTokenIndex(const std::vector<int32_t> &word_starts,
                           int num_wordpieces, int wordpiece_end);
// Returns the index of the token which includes first_wordpiece_index.
int FindFirstFullTokenIndex(const std::vector<int32_t> &word_starts,
                            int first_wordpiece_index);
// Given wordpiece_span, and max_num_wordpieces, finds:
//   1. The first token which includes wordpiece_span.begin.
//   2. The length of tokens sequence which starts from this token and:
//      a. Its last token's last wordpiece index ends before wordpiece_span.end.
//      b. Its overall number of wordpieces is at most max_num_wordpieces.
// Returns the updated wordpiece_span: begin and end wordpieces of this token
// sequence.
WordpieceSpan FindFullTokensSpanInWindow(
    const std::vector<int32_t> &word_starts,
    const WordpieceSpan &wordpiece_span, int max_num_wordpieces,
    int num_wordpieces, int *first_token_index, int *num_tokens);

}  // namespace internal
// Converts sequence of IOB tags to AnnotatedSpans. Ignores illegal sequences.
// Setting label_filter can also help ignore certain label tags like "NAM" or
// "NOM".
// The inside tag can be ignored when setting relaxed_inside_label_matching,
// e.g. B-NAM-location, I-NAM-other, E-NAM-location would be considered a valid
// sequence.
// The label category matching can be ignored when setting
// relaxed_label_category_matching. The matching will only operate at the entity
// level, e.g. B-NAM-location, E-NOM-location would be considered a valid
// sequence.
bool ConvertTagsToAnnotatedSpans(const VectorSpan<Token> &tokens,
                                 const std::vector<std::string> &tags,
                                 const std::vector<std::string> &label_filter,
                                 bool relaxed_inside_label_matching,
                                 bool relaxed_label_category_matching,
                                 float priority_score,
                                 std::vector<AnnotatedSpan> *results);

// Like the previous function but instead of getting the tags as strings
// the input is PodNerModel_::LabelT along with the collections vector which
// hold the collection name and priorities. e.g. a tag was "B-NAM-location" and
// the priority_score was 1.0 it would be Label(BoiseType_BEGIN,
// MentionType_NAM, 1) and collections={{"xxx", 1., 1.},
// {"location", 1., 1.}, {"yyy", 1., 1.}, ...}.
bool ConvertTagsToAnnotatedSpans(
    const VectorSpan<Token> &tokens,
    const std::vector<PodNerModel_::LabelT> &labels,
    const std::vector<PodNerModel_::CollectionT> &collections,
    const std::vector<PodNerModel_::Label_::MentionType> &mention_filter,
    bool relaxed_inside_label_matching, bool relaxed_mention_type_matching,
    std::vector<AnnotatedSpan> *results);

// Merge two overlaping sequences of labels, the result is placed into the left
// sequence. In the overlapping part takes the labels from the left sequence on
// the first half and from the right on the second half.
bool MergeLabelsIntoLeftSequence(
    const std::vector<PodNerModel_::LabelT> &labels_right,
    int index_first_right_tag_in_left,
    std::vector<PodNerModel_::LabelT> *labels_left);

// This class is used to slide over {wordpiece_indices, token_starts, tokens} in
// windows of at most max_num_wordpieces while assuring that each window
// contains only full tokens.
class WindowGenerator {
 public:
  WindowGenerator(const std::vector<int32_t> &wordpiece_indices,
                  const std::vector<int32_t> &token_starts,
                  const std::vector<Token> &tokens, int max_num_wordpieces,
                  int sliding_window_overlap,
                  const CodepointSpan &span_of_interest);

  bool Next(VectorSpan<int32_t> *cur_wordpiece_indices,
            VectorSpan<int32_t> *cur_token_starts,
            VectorSpan<Token> *cur_tokens);

  bool Done() const {
    return previous_wordpiece_span_.end >= entire_wordpiece_span_.end;
  }

 private:
  const std::vector<int32_t> *wordpiece_indices_;
  const std::vector<int32_t> *token_starts_;
  const std::vector<Token> *tokens_;
  int max_num_effective_wordpieces_;
  int sliding_window_num_wordpieces_overlap_;
  WordpieceSpan entire_wordpiece_span_;
  WordpieceSpan next_wordpiece_span_;
  WordpieceSpan previous_wordpiece_span_;
};

}  // namespace libtextclassifier3

#endif  // LIBTEXTCLASSIFIER_ANNOTATOR_POD_NER_UTILS_H_
