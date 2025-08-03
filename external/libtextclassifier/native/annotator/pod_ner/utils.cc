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

#include "annotator/pod_ner/utils.h"

#include <algorithm>
#include <iostream>
#include <unordered_map>

#include "annotator/model_generated.h"
#include "annotator/types.h"
#include "utils/base/logging.h"
#include "absl/strings/str_cat.h"
#include "absl/strings/str_split.h"

namespace libtextclassifier3 {
namespace {

// Returns true if the needle string is contained in the haystack.
bool StrIsOneOf(const std::string &needle,
                const std::vector<std::string> &haystack) {
  return std::find(haystack.begin(), haystack.end(), needle) != haystack.end();
}

// Finds the wordpiece span of the tokens in the given span.
WordpieceSpan CodepointSpanToWordpieceSpan(
    const CodepointSpan &span, const std::vector<Token> &tokens,
    const std::vector<int32_t> &word_starts, int num_wordpieces) {
  int span_first_wordpiece_index = 0;
  int span_last_wordpiece_index = num_wordpieces;
  for (int i = 0; i < tokens.size(); i++) {
    if (tokens[i].start <= span.first && span.first < tokens[i].end) {
      span_first_wordpiece_index = word_starts[i];
    }
    if (tokens[i].start <= span.second && span.second <= tokens[i].end) {
      span_last_wordpiece_index =
          (i + 1) < word_starts.size() ? word_starts[i + 1] : num_wordpieces;
      break;
    }
  }
  return WordpieceSpan(span_first_wordpiece_index, span_last_wordpiece_index);
}

}  // namespace

std::string SaftLabelToCollection(absl::string_view saft_label) {
  return std::string(saft_label.substr(saft_label.rfind('/') + 1));
}

namespace internal {

int FindLastFullTokenIndex(const std::vector<int32_t> &word_starts,
                           int num_wordpieces, int wordpiece_end) {
  if (word_starts.empty()) {
    return 0;
  }
  if (*word_starts.rbegin() < wordpiece_end &&
      num_wordpieces <= wordpiece_end) {
    // Last token.
    return word_starts.size() - 1;
  }
  for (int i = word_starts.size() - 1; i > 0; --i) {
    if (word_starts[i] <= wordpiece_end) {
      return (i - 1);
    }
  }
  return 0;
}

int FindFirstFullTokenIndex(const std::vector<int32_t> &word_starts,
                            int first_wordpiece_index) {
  for (int i = 0; i < word_starts.size(); ++i) {
    if (word_starts[i] == first_wordpiece_index) {
      return i;
    } else if (word_starts[i] > first_wordpiece_index) {
      return std::max(0, i - 1);
    }
  }

  return std::max(0, static_cast<int>(word_starts.size()) - 1);
}

WordpieceSpan ExpandWindowAndAlign(int max_num_wordpieces_in_window,
                                   int num_wordpieces,
                                   WordpieceSpan wordpiece_span_to_expand) {
  if (wordpiece_span_to_expand.length() >= max_num_wordpieces_in_window) {
    return wordpiece_span_to_expand;
  }
  int window_first_wordpiece_index = std::max(
      0, wordpiece_span_to_expand.begin - ((max_num_wordpieces_in_window -
                                            wordpiece_span_to_expand.length()) /
                                           2));
  if ((window_first_wordpiece_index + max_num_wordpieces_in_window) >
      num_wordpieces) {
    window_first_wordpiece_index =
        std::max(num_wordpieces - max_num_wordpieces_in_window, 0);
  }
  return WordpieceSpan(
      window_first_wordpiece_index,
      std::min(window_first_wordpiece_index + max_num_wordpieces_in_window,
               num_wordpieces));
}

WordpieceSpan FindWordpiecesWindowAroundSpan(
    const CodepointSpan &span_of_interest, const std::vector<Token> &tokens,
    const std::vector<int32_t> &word_starts, int num_wordpieces,
    int max_num_wordpieces_in_window) {
  WordpieceSpan wordpiece_span_to_expand = CodepointSpanToWordpieceSpan(
      span_of_interest, tokens, word_starts, num_wordpieces);
  WordpieceSpan max_wordpiece_span = ExpandWindowAndAlign(
      max_num_wordpieces_in_window, num_wordpieces, wordpiece_span_to_expand);
  return max_wordpiece_span;
}

WordpieceSpan FindFullTokensSpanInWindow(
    const std::vector<int32_t> &word_starts,
    const WordpieceSpan &wordpiece_span, int max_num_wordpieces,
    int num_wordpieces, int *first_token_index, int *num_tokens) {
  int window_first_wordpiece_index = wordpiece_span.begin;
  *first_token_index = internal::FindFirstFullTokenIndex(
      word_starts, window_first_wordpiece_index);
  window_first_wordpiece_index = word_starts[*first_token_index];

  // Need to update the last index in case the first moved backward.
  int wordpiece_window_end = std::min(
      wordpiece_span.end, window_first_wordpiece_index + max_num_wordpieces);
  int last_token_index;
  last_token_index = internal::FindLastFullTokenIndex(
      word_starts, num_wordpieces, wordpiece_window_end);
  wordpiece_window_end = last_token_index == (word_starts.size() - 1)
                             ? num_wordpieces
                             : word_starts[last_token_index + 1];

  *num_tokens = last_token_index - *first_token_index + 1;
  return WordpieceSpan(window_first_wordpiece_index, wordpiece_window_end);
}

}  // namespace internal

WindowGenerator::WindowGenerator(const std::vector<int32_t> &wordpiece_indices,
                                 const std::vector<int32_t> &token_starts,
                                 const std::vector<Token> &tokens,
                                 int max_num_wordpieces,
                                 int sliding_window_overlap,
                                 const CodepointSpan &span_of_interest)
    : wordpiece_indices_(&wordpiece_indices),
      token_starts_(&token_starts),
      tokens_(&tokens),
      max_num_effective_wordpieces_(max_num_wordpieces),
      sliding_window_num_wordpieces_overlap_(sliding_window_overlap) {
  entire_wordpiece_span_ = internal::FindWordpiecesWindowAroundSpan(
      span_of_interest, tokens, token_starts, wordpiece_indices.size(),
      max_num_wordpieces);
  next_wordpiece_span_ = WordpieceSpan(
      entire_wordpiece_span_.begin,
      std::min(entire_wordpiece_span_.begin + max_num_effective_wordpieces_,
               entire_wordpiece_span_.end));
  previous_wordpiece_span_ = WordpieceSpan(-1, -1);
}

bool WindowGenerator::Next(VectorSpan<int32_t> *cur_wordpiece_indices,
                           VectorSpan<int32_t> *cur_token_starts,
                           VectorSpan<Token> *cur_tokens) {
  if (Done()) {
    return false;
  }
  // Update the span to cover full tokens.
  int cur_first_token_index, cur_num_tokens;
  next_wordpiece_span_ = internal::FindFullTokensSpanInWindow(
      *token_starts_, next_wordpiece_span_, max_num_effective_wordpieces_,
      wordpiece_indices_->size(), &cur_first_token_index, &cur_num_tokens);
  *cur_token_starts = VectorSpan<int32_t>(
      token_starts_->begin() + cur_first_token_index,
      token_starts_->begin() + cur_first_token_index + cur_num_tokens);
  *cur_tokens = VectorSpan<Token>(
      tokens_->begin() + cur_first_token_index,
      tokens_->begin() + cur_first_token_index + cur_num_tokens);

  // Handle the edge case where the tokens are composed of many wordpieces and
  // the window doesn't advance.
  if (next_wordpiece_span_.begin <= previous_wordpiece_span_.begin ||
      next_wordpiece_span_.end <= previous_wordpiece_span_.end) {
    return false;
  }
  previous_wordpiece_span_ = next_wordpiece_span_;

  int next_wordpiece_first = std::max(
      previous_wordpiece_span_.end - sliding_window_num_wordpieces_overlap_,
      previous_wordpiece_span_.begin + 1);
  next_wordpiece_span_ = WordpieceSpan(
      next_wordpiece_first,
      std::min(next_wordpiece_first + max_num_effective_wordpieces_,
               entire_wordpiece_span_.end));

  *cur_wordpiece_indices = VectorSpan<int>(
      wordpiece_indices_->begin() + previous_wordpiece_span_.begin,
      wordpiece_indices_->begin() + previous_wordpiece_span_.begin +
          previous_wordpiece_span_.length());

  return true;
}

bool ConvertTagsToAnnotatedSpans(const VectorSpan<Token> &tokens,
                                 const std::vector<std::string> &tags,
                                 const std::vector<std::string> &label_filter,
                                 bool relaxed_inside_label_matching,
                                 bool relaxed_label_category_matching,
                                 float priority_score,
                                 std::vector<AnnotatedSpan> *results) {
  AnnotatedSpan current_span;
  std::string current_tag_type;
  if (tags.size() > tokens.size()) {
    return false;
  }
  for (int i = 0; i < tags.size(); i++) {
    if (tags[i].empty()) {
      return false;
    }

    std::vector<absl::string_view> tag_parts = absl::StrSplit(tags[i], '-');
    TC3_CHECK_GT(tag_parts.size(), 0);
    if (tag_parts[0].size() != 1) {
      return false;
    }

    std::string tag_type = "";
    if (tag_parts.size() > 2) {
      // Skip if the current label doesn't match the filter.
      if (!StrIsOneOf(std::string(tag_parts[1]), label_filter)) {
        current_tag_type = "";
        current_span = {};
        continue;
      }

      // Relax the matching of the label category if specified.
      tag_type = relaxed_label_category_matching
                     ? std::string(tag_parts[2])
                     : absl::StrCat(tag_parts[1], "-", tag_parts[2]);
    }

    switch (tag_parts[0][0]) {
      case 'S': {
        if (tag_parts.size() != 3) {
          return false;
        }

        current_span = {};
        current_tag_type = "";
        results->push_back(AnnotatedSpan{
            {tokens[i].start, tokens[i].end},
            {{/*arg_collection=*/SaftLabelToCollection(tag_parts[2]),
              /*arg_score=*/1.0, priority_score}}});
        break;
      };

      case 'B': {
        if (tag_parts.size() != 3) {
          return false;
        }
        current_tag_type = tag_type;
        current_span = {};
        current_span.classification.push_back(
            {/*arg_collection=*/SaftLabelToCollection(tag_parts[2]),
             /*arg_score=*/1.0, priority_score});
        current_span.span.first = tokens[i].start;
        break;
      };

      case 'I': {
        if (tag_parts.size() != 3) {
          return false;
        }
        if (!relaxed_inside_label_matching && current_tag_type != tag_type) {
          current_tag_type = "";
          current_span = {};
        }
        break;
      }

      case 'E': {
        if (tag_parts.size() != 3) {
          return false;
        }
        if (!current_tag_type.empty() && current_tag_type == tag_type) {
          current_span.span.second = tokens[i].end;
          results->push_back(current_span);
          current_span = {};
          current_tag_type = "";
        }
        break;
      };

      case 'O': {
        current_tag_type = "";
        current_span = {};
        break;
      };

      default: {
        TC3_LOG(ERROR) << "Unrecognized tag: " << tags[i];
        return false;
      }
    }
  }
  return true;
}

using PodNerModel_::CollectionT;
using PodNerModel_::LabelT;
using PodNerModel_::Label_::BoiseType;
using PodNerModel_::Label_::MentionType;

bool ConvertTagsToAnnotatedSpans(const VectorSpan<Token> &tokens,
                                 const std::vector<LabelT> &labels,
                                 const std::vector<CollectionT> &collections,
                                 const std::vector<MentionType> &mention_filter,
                                 bool relaxed_inside_label_matching,
                                 bool relaxed_mention_type_matching,
                                 std::vector<AnnotatedSpan> *results) {
  if (labels.size() > tokens.size()) {
    return false;
  }

  AnnotatedSpan current_span;
  std::string current_collection_name = "";

  for (int i = 0; i < labels.size(); i++) {
    const LabelT &label = labels[i];

    if (label.collection_id < 0 || label.collection_id >= collections.size()) {
      return false;
    }

    if (std::find(mention_filter.begin(), mention_filter.end(),
                  label.mention_type) == mention_filter.end()) {
      // Skip if the current label doesn't match the filter.
      current_span = {};
      current_collection_name = "";
      continue;
    }

    switch (label.boise_type) {
      case BoiseType::BoiseType_SINGLE: {
        current_span = {};
        current_collection_name = "";
        results->push_back(AnnotatedSpan{
            {tokens[i].start, tokens[i].end},
            {{/*arg_collection=*/collections[label.collection_id].name,
              /*arg_score=*/1.0,
              collections[label.collection_id].single_token_priority_score}}});
        break;
      };

      case BoiseType::BoiseType_BEGIN: {
        current_span = {};
        current_span.classification.push_back(
            {/*arg_collection=*/collections[label.collection_id].name,
             /*arg_score=*/1.0,
             collections[label.collection_id].multi_token_priority_score});
        current_span.span.first = tokens[i].start;
        current_collection_name = collections[label.collection_id].name;
        break;
      };

      case BoiseType::BoiseType_INTERMEDIATE: {
        if (current_collection_name.empty() ||
            (!relaxed_mention_type_matching &&
             labels[i - 1].mention_type != label.mention_type) ||
            (!relaxed_inside_label_matching &&
             labels[i - 1].collection_id != label.collection_id)) {
          current_span = {};
          current_collection_name = "";
        }
        break;
      }

      case BoiseType::BoiseType_END: {
        if (!current_collection_name.empty() &&
            current_collection_name == collections[label.collection_id].name &&
            (relaxed_mention_type_matching ||
             labels[i - 1].mention_type == label.mention_type)) {
          current_span.span.second = tokens[i].end;
          results->push_back(current_span);
        }
        current_span = {};
        current_collection_name = "";
        break;
      };

      case BoiseType::BoiseType_O: {
        current_span = {};
        current_collection_name = "";
        break;
      };

      default: {
        TC3_LOG(ERROR) << "Unrecognized tag: " << labels[i].boise_type;
        return false;
      }
    }
  }
  return true;
}

bool MergeLabelsIntoLeftSequence(
    const std::vector<PodNerModel_::LabelT> &labels_right,
    int index_first_right_tag_in_left,
    std::vector<PodNerModel_::LabelT> *labels_left) {
  if (index_first_right_tag_in_left > labels_left->size()) {
    return false;
  }

  int overlaping_from_left =
      (labels_left->size() - index_first_right_tag_in_left) / 2;

  labels_left->resize(index_first_right_tag_in_left + labels_right.size());
  std::copy(labels_right.begin() + overlaping_from_left, labels_right.end(),
            labels_left->begin() + index_first_right_tag_in_left +
                overlaping_from_left);
  return true;
}

}  // namespace libtextclassifier3
