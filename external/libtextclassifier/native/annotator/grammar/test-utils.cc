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

#include "annotator/grammar/test-utils.h"

#include "utils/tokenizer.h"

namespace libtextclassifier3 {

GrammarAnnotator GrammarAnnotatorTest::CreateGrammarAnnotator(
    const ::flatbuffers::DetachedBuffer& serialized_model) {
  return GrammarAnnotator(
      unilib_.get(),
      flatbuffers::GetRoot<GrammarModel>(serialized_model.data()),
      entity_data_builder_.get());
}

void SetTestTokenizerOptions(GrammarModelT* model) {
  model->tokenizer_options.reset(new GrammarTokenizerOptionsT);
  model->tokenizer_options->tokenization_type = TokenizationType_ICU;
  model->tokenizer_options->icu_preserve_whitespace_tokens = false;
  model->tokenizer_options->tokenize_on_script_change = true;
}

}  // namespace libtextclassifier3
