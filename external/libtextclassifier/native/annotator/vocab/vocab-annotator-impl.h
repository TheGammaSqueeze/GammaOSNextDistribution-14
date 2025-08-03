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

#ifndef LIBTEXTCLASSIFIER_ANNOTATOR_VOCAB_VOCAB_ANNOTATOR_IMPL_H_
#define LIBTEXTCLASSIFIER_ANNOTATOR_VOCAB_VOCAB_ANNOTATOR_IMPL_H_

#include "annotator/feature-processor.h"
#include "annotator/model_generated.h"
#include "annotator/types.h"
#include "annotator/vocab/vocab-level-table.h"
#include "utils/i18n/locale.h"
#include "utils/utf8/unicodetext.h"
#include "utils/utf8/unilib.h"

namespace libtextclassifier3 {

// Annotates vocabs of different levels which users may want to look them up
// in a dictionary.
class VocabAnnotator {
 public:
  static std::unique_ptr<VocabAnnotator> Create(
      const VocabModel *model, const FeatureProcessor &feature_processor,
      const UniLib &unilib);

  bool Annotate(const UnicodeText &context,
                const std::vector<Locale> detected_text_language_tags,
                bool trigger_on_beginner_words,
                std::vector<AnnotatedSpan> *results) const;

  bool ClassifyText(const UnicodeText &context, CodepointSpan click,
                    const std::vector<Locale> detected_text_language_tags,
                    bool trigger_on_beginner_words,
                    ClassificationResult *result) const;

 private:
  explicit VocabAnnotator(std::unique_ptr<VocabLevelTable> vocab_level_table,
                          const std::vector<Locale> &triggering_locales,
                          const FeatureProcessor &feature_processor,
                          const UniLib &unilib, const VocabModel *model);

  bool ClassifyTextInternal(
      const UnicodeText &context, const CodepointSpan click,
      const std::vector<Locale> detected_text_language_tags,
      bool trigger_on_beginner_words,
      ClassificationResult *classification_result,
      CodepointSpan *classified_span) const;
  bool ShouldTriggerOnBeginnerVocabs() const;

  const std::unique_ptr<VocabLevelTable> vocab_level_table_;
  // Locales for which this annotator triggers.
  const std::vector<Locale> triggering_locales_;
  const FeatureProcessor &feature_processor_;
  const UniLib &unilib_;
  const VocabModel *model_;
};

}  // namespace libtextclassifier3

#endif  // LIBTEXTCLASSIFIER_ANNOTATOR_VOCAB_VOCAB_ANNOTATOR_IMPL_H_
