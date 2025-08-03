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

#include "annotator/pod_ner/pod-ner-impl.h"

#include <algorithm>
#include <cstdint>
#include <ctime>
#include <iostream>
#include <memory>
#include <ostream>
#include <unordered_set>
#include <vector>

#include "annotator/model_generated.h"
#include "annotator/pod_ner/utils.h"
#include "annotator/types.h"
#include "utils/base/logging.h"
#include "utils/bert_tokenizer.h"
#include "utils/tflite-model-executor.h"
#include "utils/tokenizer-utils.h"
#include "utils/utf8/unicodetext.h"
#include "absl/strings/ascii.h"
#include "tensorflow/lite/kernels/builtin_op_kernels.h"
#include "tensorflow/lite/mutable_op_resolver.h"
#include "tensorflow_lite_support/cc/text/tokenizers/tokenizer.h"
#include "tensorflow_models/seq_flow_lite/tflite_ops/layer_norm.h"
#include "tensorflow_models/seq_flow_lite/tflite_ops/quantization_util.h"

namespace libtextclassifier3 {

using PodNerModel_::CollectionT;
using PodNerModel_::LabelT;
using ::tflite::support::text::tokenizer::TokenizerResult;

namespace {

using PodNerModel_::Label_::BoiseType;
using PodNerModel_::Label_::BoiseType_BEGIN;
using PodNerModel_::Label_::BoiseType_END;
using PodNerModel_::Label_::BoiseType_INTERMEDIATE;
using PodNerModel_::Label_::BoiseType_O;
using PodNerModel_::Label_::BoiseType_SINGLE;
using PodNerModel_::Label_::MentionType;
using PodNerModel_::Label_::MentionType_NAM;
using PodNerModel_::Label_::MentionType_NOM;
using PodNerModel_::Label_::MentionType_UNDEFINED;

void EmplaceToLabelVector(BoiseType boise_type, MentionType mention_type,
                          int collection_id, std::vector<LabelT> *labels) {
  labels->emplace_back();
  labels->back().boise_type = boise_type;
  labels->back().mention_type = mention_type;
  labels->back().collection_id = collection_id;
}

void FillDefaultLabelsAndCollections(float default_priority,
                                     std::vector<LabelT> *labels,
                                     std::vector<CollectionT> *collections) {
  std::vector<std::string> collection_names = {
      "art",          "consumer_good", "event",  "location",
      "organization", "ner_entity",    "person", "undefined"};
  collections->clear();
  for (const std::string &collection_name : collection_names) {
    collections->emplace_back();
    collections->back().name = collection_name;
    collections->back().single_token_priority_score = default_priority;
    collections->back().multi_token_priority_score = default_priority;
  }

  labels->clear();
  for (auto boise_type :
       {BoiseType_BEGIN, BoiseType_END, BoiseType_INTERMEDIATE}) {
    for (auto mention_type : {MentionType_NAM, MentionType_NOM}) {
      for (int i = 0; i < collections->size() - 1; ++i) {  // skip undefined
        EmplaceToLabelVector(boise_type, mention_type, i, labels);
      }
    }
  }
  EmplaceToLabelVector(BoiseType_O, MentionType_UNDEFINED, 7, labels);
  for (auto mention_type : {MentionType_NAM, MentionType_NOM}) {
    for (int i = 0; i < collections->size() - 1; ++i) {  // skip undefined
      EmplaceToLabelVector(BoiseType_SINGLE, mention_type, i, labels);
    }
  }
}

std::unique_ptr<tflite::Interpreter> CreateInterpreter(
    const PodNerModel *model) {
  TC3_CHECK(model != nullptr);
  if (model->tflite_model() == nullptr) {
    TC3_LOG(ERROR) << "Unable to create tf.lite interpreter, model is null.";
    return nullptr;
  }

  const tflite::Model *tflite_model =
      tflite::GetModel(model->tflite_model()->Data());
  if (tflite_model == nullptr) {
    TC3_LOG(ERROR) << "Unable to create tf.lite interpreter, model is null.";
    return nullptr;
  }

  std::unique_ptr<tflite::OpResolver> resolver =
      BuildOpResolver([](tflite::MutableOpResolver *mutable_resolver) {
        mutable_resolver->AddBuiltin(::tflite::BuiltinOperator_SHAPE,
                                     ::tflite::ops::builtin::Register_SHAPE());
        mutable_resolver->AddBuiltin(::tflite::BuiltinOperator_RANGE,
                                     ::tflite::ops::builtin::Register_RANGE());
        mutable_resolver->AddBuiltin(
            ::tflite::BuiltinOperator_ARG_MAX,
            ::tflite::ops::builtin::Register_ARG_MAX());
        mutable_resolver->AddBuiltin(
            ::tflite::BuiltinOperator_EXPAND_DIMS,
            ::tflite::ops::builtin::Register_EXPAND_DIMS());
        mutable_resolver->AddCustom(
            "LayerNorm", ::seq_flow_lite::ops::custom::Register_LAYER_NORM());
      });

  std::unique_ptr<tflite::Interpreter> tflite_interpreter;
  tflite::InterpreterBuilder(tflite_model, *resolver,
                             nullptr)(&tflite_interpreter);
  if (tflite_interpreter == nullptr) {
    TC3_LOG(ERROR) << "Unable to create tf.lite interpreter.";
    return nullptr;
  }
  return tflite_interpreter;
}

bool FindSpecialWordpieceIds(const std::unique_ptr<BertTokenizer> &tokenizer,
                             int *cls_id, int *sep_id, int *period_id,
                             int *unknown_id) {
  if (!tokenizer->LookupId("[CLS]", cls_id)) {
    TC3_LOG(ERROR) << "Couldn't find [CLS] wordpiece.";
    return false;
  }
  if (!tokenizer->LookupId("[SEP]", sep_id)) {
    TC3_LOG(ERROR) << "Couldn't find [SEP] wordpiece.";
    return false;
  }
  if (!tokenizer->LookupId(".", period_id)) {
    TC3_LOG(ERROR) << "Couldn't find [.] wordpiece.";
    return false;
  }
  if (!tokenizer->LookupId("[UNK]", unknown_id)) {
    TC3_LOG(ERROR) << "Couldn't find [UNK] wordpiece.";
    return false;
  }
  return true;
}
// WARNING: This tokenizer is not exactly the one the model was trained with
// so there might be nuances.
std::unique_ptr<BertTokenizer> CreateTokenizer(const PodNerModel *model) {
  TC3_CHECK(model != nullptr);
  if (model->word_piece_vocab() == nullptr) {
    TC3_LOG(ERROR)
        << "Unable to create tokenizer, model or word_pieces is null.";
    return nullptr;
  }

  return std::unique_ptr<BertTokenizer>(new BertTokenizer(
      reinterpret_cast<const char *>(model->word_piece_vocab()->Data()),
      model->word_piece_vocab()->size()));
}

}  // namespace

std::unique_ptr<PodNerAnnotator> PodNerAnnotator::Create(
    const PodNerModel *model, const UniLib &unilib) {
  if (model == nullptr) {
    TC3_LOG(ERROR) << "Create received null model.";
    return nullptr;
  }

  std::unique_ptr<BertTokenizer> tokenizer = CreateTokenizer(model);
  if (tokenizer == nullptr) {
    return nullptr;
  }

  int cls_id, sep_id, period_id, unknown_wordpiece_id;
  if (!FindSpecialWordpieceIds(tokenizer, &cls_id, &sep_id, &period_id,
                               &unknown_wordpiece_id)) {
    return nullptr;
  }

  std::unique_ptr<PodNerAnnotator> annotator(new PodNerAnnotator(unilib));
  annotator->tokenizer_ = std::move(tokenizer);
  annotator->lowercase_input_ = model->lowercase_input();
  annotator->logits_index_in_output_tensor_ =
      model->logits_index_in_output_tensor();
  annotator->append_final_period_ = model->append_final_period();
  if (model->labels() && model->labels()->size() > 0 && model->collections() &&
      model->collections()->size() > 0) {
    annotator->labels_.clear();
    for (const PodNerModel_::Label *label : *model->labels()) {
      annotator->labels_.emplace_back();
      annotator->labels_.back().boise_type = label->boise_type();
      annotator->labels_.back().mention_type = label->mention_type();
      annotator->labels_.back().collection_id = label->collection_id();
    }
    for (const PodNerModel_::Collection *collection : *model->collections()) {
      annotator->collections_.emplace_back();
      annotator->collections_.back().name = collection->name()->str();
      annotator->collections_.back().single_token_priority_score =
          collection->single_token_priority_score();
      annotator->collections_.back().multi_token_priority_score =
          collection->multi_token_priority_score();
    }
  } else {
    FillDefaultLabelsAndCollections(
        model->priority_score(), &annotator->labels_, &annotator->collections_);
  }
  int max_num_surrounding_wordpieces = model->append_final_period() ? 3 : 2;
  annotator->max_num_effective_wordpieces_ =
      model->max_num_wordpieces() - max_num_surrounding_wordpieces;
  annotator->sliding_window_num_wordpieces_overlap_ =
      model->sliding_window_num_wordpieces_overlap();
  annotator->max_ratio_unknown_wordpieces_ =
      model->max_ratio_unknown_wordpieces();
  annotator->min_number_of_tokens_ = model->min_number_of_tokens();
  annotator->min_number_of_wordpieces_ = model->min_number_of_wordpieces();
  annotator->cls_wordpiece_id_ = cls_id;
  annotator->sep_wordpiece_id_ = sep_id;
  annotator->period_wordpiece_id_ = period_id;
  annotator->unknown_wordpiece_id_ = unknown_wordpiece_id;
  annotator->model_ = model;

  return annotator;
}

std::vector<LabelT> PodNerAnnotator::ReadResultsFromInterpreter(
    tflite::Interpreter &interpreter) const {
  TfLiteTensor *output =
      interpreter.tensor(interpreter.outputs()[logits_index_in_output_tensor_]);
  TC3_CHECK_EQ(output->dims->size, 3);
  TC3_CHECK_EQ(output->dims->data[0], 1);
  TC3_CHECK_EQ(output->dims->data[2], labels_.size());
  std::vector<LabelT> return_value(output->dims->data[1]);
  std::vector<float> probs(output->dims->data[1]);
  for (int step = 0, index = 0; step < output->dims->data[1]; ++step) {
    float max_prob = 0.0f;
    int max_index = 0;
    for (int cindex = 0; cindex < output->dims->data[2]; ++cindex) {
      const float probability =
          ::seq_flow_lite::PodDequantize(*output, index++);
      if (probability > max_prob) {
        max_prob = probability;
        max_index = cindex;
      }
    }
    return_value[step] = labels_[max_index];
    probs[step] = max_prob;
  }
  return return_value;
}

std::vector<LabelT> PodNerAnnotator::ExecuteModel(
    const VectorSpan<int> &wordpiece_indices,
    const VectorSpan<int32_t> &token_starts,
    const VectorSpan<Token> &tokens) const {
  // Check that there are not more input indices than supported.
  if (wordpiece_indices.size() > max_num_effective_wordpieces_) {
    TC3_LOG(ERROR) << "More than " << max_num_effective_wordpieces_
                   << " indices passed to POD NER model.";
    return {};
  }
  if (wordpiece_indices.size() <= 0 || token_starts.size() <= 0 ||
      tokens.size() <= 0) {
    TC3_LOG(ERROR) << "ExecuteModel received illegal input, #wordpiece_indices="
                   << wordpiece_indices.size()
                   << " #token_starts=" << token_starts.size()
                   << " #tokens=" << tokens.size();
    return {};
  }

  // For the CLS (at the beginning) and SEP (at the end) wordpieces.
  int num_additional_wordpieces = 2;
  bool should_append_final_period = false;
  // Optionally add a final period wordpiece if the final token is not
  // already punctuation. This can improve performance for models trained on
  // data mostly ending in sentence-final punctuation.
  const std::string &last_token = (tokens.end() - 1)->value;
  if (append_final_period_ &&
      (last_token.size() != 1 || !unilib_.IsPunctuation(last_token.at(0)))) {
    should_append_final_period = true;
    num_additional_wordpieces++;
  }

  // Interpreter needs to be created for each inference call separately,
  // otherwise the class is not thread-safe.
  std::unique_ptr<tflite::Interpreter> interpreter = CreateInterpreter(model_);
  if (interpreter == nullptr) {
    TC3_LOG(ERROR) << "Couldn't create Interpreter.";
    return {};
  }

  TfLiteStatus status;
  status = interpreter->ResizeInputTensor(
      interpreter->inputs()[0],
      {1, wordpiece_indices.size() + num_additional_wordpieces});
  TC3_CHECK_EQ(status, kTfLiteOk);
  status = interpreter->ResizeInputTensor(interpreter->inputs()[1],
                                          {1, token_starts.size()});
  TC3_CHECK_EQ(status, kTfLiteOk);

  status = interpreter->AllocateTensors();
  TC3_CHECK_EQ(status, kTfLiteOk);

  TfLiteTensor *tensor = interpreter->tensor(interpreter->inputs()[0]);
  int wordpiece_tensor_index = 0;
  tensor->data.i32[wordpiece_tensor_index++] = cls_wordpiece_id_;
  for (int wordpiece_index : wordpiece_indices) {
    tensor->data.i32[wordpiece_tensor_index++] = wordpiece_index;
  }

  if (should_append_final_period) {
    tensor->data.i32[wordpiece_tensor_index++] = period_wordpiece_id_;
  }
  tensor->data.i32[wordpiece_tensor_index++] = sep_wordpiece_id_;

  tensor = interpreter->tensor(interpreter->inputs()[1]);
  for (int i = 0; i < token_starts.size(); ++i) {
    // Need to add one because of the starting CLS wordpiece and reduce the
    // offset from the first wordpiece.
    tensor->data.i32[i] = token_starts[i] + 1 - token_starts[0];
  }

  status = interpreter->Invoke();
  TC3_CHECK_EQ(status, kTfLiteOk);

  return ReadResultsFromInterpreter(*interpreter);
}

bool PodNerAnnotator::PrepareText(const UnicodeText &text_unicode,
                                  std::vector<int32_t> *wordpiece_indices,
                                  std::vector<int32_t> *token_starts,
                                  std::vector<Token> *tokens) const {
  *tokens = TokenizeOnWhiteSpacePunctuationAndChineseLetter(
      text_unicode.ToUTF8String());
  tokens->erase(std::remove_if(tokens->begin(), tokens->end(),
                               [](const Token &token) {
                                 return token.start == token.end;
                               }),
                tokens->end());

  for (const Token &token : *tokens) {
    const std::string token_text =
        lowercase_input_ ? unilib_
                               .ToLowerText(UTF8ToUnicodeText(
                                   token.value, /*do_copy=*/false))
                               .ToUTF8String()
                         : token.value;

    const TokenizerResult wordpiece_tokenization =
        tokenizer_->TokenizeSingleToken(token_text);

    std::vector<int> wordpiece_ids;
    for (const std::string &wordpiece : wordpiece_tokenization.subwords) {
      if (!tokenizer_->LookupId(wordpiece, &(wordpiece_ids.emplace_back()))) {
        TC3_LOG(ERROR) << "Couldn't find wordpiece " << wordpiece;
        return false;
      }
    }

    if (wordpiece_ids.empty()) {
      TC3_LOG(ERROR) << "wordpiece_ids.empty()";
      return false;
    }
    token_starts->push_back(wordpiece_indices->size());
    for (const int64 wordpiece_id : wordpiece_ids) {
      wordpiece_indices->push_back(wordpiece_id);
    }
  }

  return true;
}

bool PodNerAnnotator::Annotate(const UnicodeText &context,
                               std::vector<AnnotatedSpan> *results) const {
  return AnnotateAroundSpanOfInterest(context, {0, context.size_codepoints()},
                                      results);
}

bool PodNerAnnotator::AnnotateAroundSpanOfInterest(
    const UnicodeText &context, const CodepointSpan &span_of_interest,
    std::vector<AnnotatedSpan> *results) const {
  TC3_CHECK(results != nullptr);

  if (!(model_->enabled_modes() & ModeFlag_ANNOTATION)) {
    return true;
  }

  std::vector<int32_t> wordpiece_indices;
  std::vector<int32_t> token_starts;
  std::vector<Token> tokens;
  if (!PrepareText(context, &wordpiece_indices, &token_starts, &tokens)) {
    TC3_LOG(ERROR) << "PodNerAnnotator PrepareText(...) failed.";
    return false;
  }
  const int unknown_wordpieces_count =
      std::count(wordpiece_indices.begin(), wordpiece_indices.end(),
                 unknown_wordpiece_id_);
  if (tokens.empty() || tokens.size() < min_number_of_tokens_ ||
      wordpiece_indices.size() < min_number_of_wordpieces_ ||
      (static_cast<float>(unknown_wordpieces_count) /
       wordpiece_indices.size()) > max_ratio_unknown_wordpieces_) {
    return true;
  }

  std::vector<LabelT> labels;
  int first_token_index_entire_window = 0;

  WindowGenerator window_generator(
      wordpiece_indices, token_starts, tokens, max_num_effective_wordpieces_,
      sliding_window_num_wordpieces_overlap_, span_of_interest);
  while (!window_generator.Done()) {
    VectorSpan<int32_t> cur_wordpiece_indices;
    VectorSpan<int32_t> cur_token_starts;
    VectorSpan<Token> cur_tokens;
    if (!window_generator.Next(&cur_wordpiece_indices, &cur_token_starts,
                               &cur_tokens) ||
        cur_tokens.size() <= 0 || cur_token_starts.size() <= 0 ||
        cur_wordpiece_indices.size() <= 0) {
      return false;
    }
    std::vector<LabelT> new_labels =
        ExecuteModel(cur_wordpiece_indices, cur_token_starts, cur_tokens);
    if (labels.empty()) {  // First loop.
      first_token_index_entire_window = cur_tokens.begin() - tokens.begin();
    }
    if (!MergeLabelsIntoLeftSequence(
            /*labels_right=*/new_labels,
            /*index_first_right_tag_in_left=*/cur_tokens.begin() -
                tokens.begin() - first_token_index_entire_window,
            /*labels_left=*/&labels)) {
      return false;
    }
  }

  if (labels.empty()) {
    return false;
  }
  ConvertTagsToAnnotatedSpans(
      VectorSpan<Token>(tokens.begin() + first_token_index_entire_window,
                        tokens.end()),
      labels, collections_, {PodNerModel_::Label_::MentionType_NAM},
      /*relaxed_inside_label_matching=*/false,
      /*relaxed_mention_type_matching=*/false, results);

  return true;
}

bool PodNerAnnotator::SuggestSelection(const UnicodeText &context,
                                       CodepointSpan click,
                                       AnnotatedSpan *result) const {
  TC3_VLOG(INFO) << "POD NER SuggestSelection " << click;
  std::vector<AnnotatedSpan> annotations;
  if (!AnnotateAroundSpanOfInterest(context, click, &annotations)) {
    TC3_VLOG(INFO) << "POD NER SuggestSelection: Annotate error. Returning: "
                   << click;
    *result = {};
    return false;
  }

  if (!(model_->enabled_modes() & ModeFlag_SELECTION)) {
    *result = {};
    return false;
  }

  for (const AnnotatedSpan &annotation : annotations) {
    TC3_VLOG(INFO) << "POD NER SuggestSelection: " << annotation;
    if (annotation.span.first <= click.first &&
        annotation.span.second >= click.second) {
      TC3_VLOG(INFO) << "POD NER SuggestSelection: Accepted.";
      *result = annotation;
      return true;
    }
  }

  TC3_VLOG(INFO)
      << "POD NER SuggestSelection: No annotation matched click. Returning: "
      << click;
  *result = {};
  return false;
}

bool PodNerAnnotator::ClassifyText(const UnicodeText &context,
                                   CodepointSpan click,
                                   ClassificationResult *result) const {
  TC3_VLOG(INFO) << "POD NER ClassifyText " << click;
  if (!(model_->enabled_modes() & ModeFlag_CLASSIFICATION)) {
    return false;
  }

  std::vector<AnnotatedSpan> annotations;
  if (!AnnotateAroundSpanOfInterest(context, click, &annotations)) {
    return false;
  }

  for (const AnnotatedSpan &annotation : annotations) {
    if (annotation.span.first <= click.first &&
        annotation.span.second >= click.second) {
      if (annotation.classification.empty()) {
        return false;
      }
      *result = annotation.classification[0];
      return true;
    }
  }
  return false;
}

std::vector<std::string> PodNerAnnotator::GetSupportedCollections() const {
  std::vector<std::string> result;
  for (const PodNerModel_::CollectionT &collection : collections_) {
    result.push_back(collection.name);
  }
  return result;
}

}  // namespace libtextclassifier3
