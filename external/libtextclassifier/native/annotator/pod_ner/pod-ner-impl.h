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

#ifndef LIBTEXTCLASSIFIER_ANNOTATOR_POD_NER_POD_NER_IMPL_H_
#define LIBTEXTCLASSIFIER_ANNOTATOR_POD_NER_POD_NER_IMPL_H_

#include <memory>

#include "annotator/model_generated.h"
#include "annotator/types.h"
#include "utils/bert_tokenizer.h"
#include "utils/utf8/unicodetext.h"
#include "utils/utf8/unilib.h"
#include "tensorflow/lite/context.h"
#include "tensorflow/lite/interpreter.h"
#include "tensorflow/lite/kernels/register.h"
#include "tensorflow/lite/string_util.h"

namespace libtextclassifier3 {

// Uses POD NER BERT-based model for annotating various types of entities.
class PodNerAnnotator {
 public:
  static std::unique_ptr<PodNerAnnotator> Create(const PodNerModel *model,
                                                 const UniLib &unilib);

  bool Annotate(const UnicodeText &context,
                std::vector<AnnotatedSpan> *results) const;

  // Returns true if an entity was detected under 'click', and the selection
  // indices expanded and assigned to 'result'. Otherwise returns false, and
  // resets 'result'.
  bool SuggestSelection(const UnicodeText &context, CodepointSpan click,
                        AnnotatedSpan *result) const;

  bool ClassifyText(const UnicodeText &context, CodepointSpan click,
                    ClassificationResult *result) const;

  std::vector<std::string> GetSupportedCollections() const;

 private:
  explicit PodNerAnnotator(const UniLib &unilib) : unilib_(unilib) {}

  std::vector<PodNerModel_::LabelT> ReadResultsFromInterpreter(
      tflite::Interpreter &interpreter) const;

  std::vector<PodNerModel_::LabelT> ExecuteModel(
      const VectorSpan<int> &wordpiece_indices,
      const VectorSpan<int32_t> &token_starts,
      const VectorSpan<Token> &tokens) const;

  bool PrepareText(const UnicodeText &text_unicode,
                   std::vector<int32_t> *wordpiece_indices,
                   std::vector<int32_t> *token_starts,
                   std::vector<Token> *tokens) const;

  bool AnnotateAroundSpanOfInterest(const UnicodeText &context,
                                    const CodepointSpan &span_of_interest,
                                    std::vector<AnnotatedSpan> *results) const;

  const UniLib &unilib_;
  bool lowercase_input_;
  int logits_index_in_output_tensor_;
  bool append_final_period_;
  int max_num_effective_wordpieces_;
  int sliding_window_num_wordpieces_overlap_;
  float max_ratio_unknown_wordpieces_;
  int min_number_of_tokens_;
  int min_number_of_wordpieces_;
  int cls_wordpiece_id_;
  int sep_wordpiece_id_;
  int period_wordpiece_id_;
  int unknown_wordpiece_id_;
  std::vector<PodNerModel_::CollectionT> collections_;
  std::vector<PodNerModel_::LabelT> labels_;
  std::unique_ptr<BertTokenizer> tokenizer_;
  const PodNerModel *model_;
};

}  // namespace libtextclassifier3

#endif  // LIBTEXTCLASSIFIER_ANNOTATOR_POD_NER_POD_NER_IMPL_H_
