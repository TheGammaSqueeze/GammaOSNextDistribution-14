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

#ifndef LIBTEXTCLASSIFIER_ANNOTATOR_GRAMMAR_TEST_UTILS_H_
#define LIBTEXTCLASSIFIER_ANNOTATOR_GRAMMAR_TEST_UTILS_H_

#include <memory>

#include "actions/test-utils.h"
#include "annotator/grammar/grammar-annotator.h"
#include "utils/flatbuffers/mutable.h"
#include "utils/jvm-test-utils.h"
#include "utils/utf8/unilib.h"
#include "gtest/gtest.h"

namespace libtextclassifier3 {

// TODO(sofian): Move this matchers to a level up library, useable for more
// tests in text_classifier.
MATCHER_P3(IsAnnotatedSpan, start, end, collection,
           "is annotated span with begin that " +
               ::testing::DescribeMatcher<int>(start, negation) +
               ", end that " + ::testing::DescribeMatcher<int>(end, negation) +
               ", collection that " +
               ::testing::DescribeMatcher<std::string>(collection, negation)) {
  return ::testing::ExplainMatchResult(CodepointSpan(start, end), arg.span,
                                       result_listener) &&
         ::testing::ExplainMatchResult(::testing::StrEq(collection),
                                       arg.classification.front().collection,
                                       result_listener);
}

MATCHER_P(IsClassificationResult, collection,
          "is classification result with collection that " +
              ::testing::DescribeMatcher<std::string>(collection, negation)) {
  return ::testing::ExplainMatchResult(::testing::StrEq(collection),
                                       arg.collection, result_listener);
}

class GrammarAnnotatorTest : public ::testing::Test {
 protected:
  GrammarAnnotatorTest()
      : unilib_(CreateUniLibForTesting()),
        serialized_entity_data_schema_(TestEntityDataSchema()),
        entity_data_builder_(new MutableFlatbufferBuilder(
            flatbuffers::GetRoot<reflection::Schema>(
                serialized_entity_data_schema_.data()))) {}

  GrammarAnnotator CreateGrammarAnnotator(
      const ::flatbuffers::DetachedBuffer& serialized_model);

  std::unique_ptr<UniLib> unilib_;
  const std::string serialized_entity_data_schema_;
  std::unique_ptr<MutableFlatbufferBuilder> entity_data_builder_;
};

void SetTestTokenizerOptions(GrammarModelT* model);

}  // namespace libtextclassifier3

#endif  // LIBTEXTCLASSIFIER_ANNOTATOR_GRAMMAR_TEST_UTILS_H_
