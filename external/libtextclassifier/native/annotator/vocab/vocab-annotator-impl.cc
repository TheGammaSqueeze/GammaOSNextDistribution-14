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

#include "annotator/vocab/vocab-annotator-impl.h"

#include "annotator/feature-processor.h"
#include "annotator/model_generated.h"
#include "utils/base/logging.h"
#include "utils/optional.h"
#include "utils/strings/numbers.h"

namespace libtextclassifier3 {

VocabAnnotator::VocabAnnotator(
    std::unique_ptr<VocabLevelTable> vocab_level_table,
    const std::vector<Locale>& triggering_locales,
    const FeatureProcessor& feature_processor, const UniLib& unilib,
    const VocabModel* model)
    : vocab_level_table_(std::move(vocab_level_table)),
      triggering_locales_(triggering_locales),
      feature_processor_(feature_processor),
      unilib_(unilib),
      model_(model) {}

std::unique_ptr<VocabAnnotator> VocabAnnotator::Create(
    const VocabModel* model, const FeatureProcessor& feature_processor,
    const UniLib& unilib) {
  std::unique_ptr<VocabLevelTable> vocab_lebel_table =
      VocabLevelTable::Create(model);
  if (vocab_lebel_table == nullptr) {
    TC3_LOG(ERROR) << "Failed to create vocab level table.";
    return nullptr;
  }
  std::vector<Locale> triggering_locales;
  if (model->triggering_locales() &&
      !ParseLocales(model->triggering_locales()->c_str(),
                    &triggering_locales)) {
    TC3_LOG(ERROR) << "Could not parse model supported locales.";
    return nullptr;
  }

  return std::unique_ptr<VocabAnnotator>(
      new VocabAnnotator(std::move(vocab_lebel_table), triggering_locales,
                         feature_processor, unilib, model));
}

bool VocabAnnotator::Annotate(
    const UnicodeText& context,
    const std::vector<Locale> detected_text_language_tags,
    bool trigger_on_beginner_words, std::vector<AnnotatedSpan>* results) const {
  if (!(model_->enabled_modes() & ModeFlag_ANNOTATION)) {
    return true;
  }
  std::vector<Token> tokens = feature_processor_.Tokenize(context);
  for (const Token& token : tokens) {
    ClassificationResult classification_result;
    CodepointSpan stripped_span;
    bool found = ClassifyTextInternal(
        context, {token.start, token.end}, detected_text_language_tags,
        trigger_on_beginner_words, &classification_result, &stripped_span);
    if (found) {
      results->push_back(AnnotatedSpan{stripped_span, {classification_result}});
    }
  }
  return true;
}

bool VocabAnnotator::ClassifyText(
    const UnicodeText& context, CodepointSpan click,
    const std::vector<Locale> detected_text_language_tags,
    bool trigger_on_beginner_words, ClassificationResult* result) const {
  CodepointSpan stripped_span;
  return ClassifyTextInternal(context, click, detected_text_language_tags,
                              trigger_on_beginner_words, result,
                              &stripped_span);
}

bool VocabAnnotator::ClassifyTextInternal(
    const UnicodeText& context, const CodepointSpan click,
    const std::vector<Locale> detected_text_language_tags,
    bool trigger_on_beginner_words, ClassificationResult* classification_result,
    CodepointSpan* classified_span) const {
  if (!(model_->enabled_modes() & ModeFlag_CLASSIFICATION)) {
    return false;
  }
  if (vocab_level_table_ == nullptr) {
    return false;
  }

  if (!Locale::IsAnyLocaleSupported(detected_text_language_tags,
                                    triggering_locales_,
                                    /*default_value=*/false)) {
    return false;
  }
  const CodepointSpan stripped_span =
      feature_processor_.StripBoundaryCodepoints(context,
                                                 {click.first, click.second});
  const UnicodeText stripped_token = UnicodeText::Substring(
      context, stripped_span.first, stripped_span.second, /*do_copy=*/false);
  const std::string lower_token =
      unilib_.ToLowerText(stripped_token).ToUTF8String();

  const Optional<LookupResult> result = vocab_level_table_->Lookup(lower_token);
  if (!result.has_value()) {
    return false;
  }
  if (result.value().do_not_trigger_in_upper_case &&
      unilib_.IsUpper(*stripped_token.begin())) {
    TC3_VLOG(INFO) << "Not trigger define: proper noun in upper case.";
    return false;
  }
  if (result.value().beginner_level && !trigger_on_beginner_words) {
    TC3_VLOG(INFO) << "Not trigger define: for beginner only.";
    return false;
  }
  *classification_result =
      ClassificationResult("dictionary", model_->target_classification_score(),
                           model_->priority_score());
  *classified_span = stripped_span;

  return true;
}
}  // namespace libtextclassifier3
