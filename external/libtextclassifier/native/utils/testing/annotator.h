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

// Helper utilities for testing Annotator.

#ifndef LIBTEXTCLASSIFIER_UTILS_TESTING_ANNOTATOR_H_
#define LIBTEXTCLASSIFIER_UTILS_TESTING_ANNOTATOR_H_

#include <fstream>
#include <memory>
#include <string>

#include "annotator/model_generated.h"
#include "annotator/types.h"
#include "flatbuffers/flatbuffers.h"

namespace libtextclassifier3 {

// Loads FlatBuffer model, unpacks it and passes it to the visitor_fn so that it
// can modify it. Afterwards the modified unpacked model is serialized back to a
// flatbuffer.
template <typename Fn>
std::string ModifyAnnotatorModel(const std::string& model_flatbuffer,
                                 Fn visitor_fn) {
  std::unique_ptr<ModelT> unpacked_model =
      UnPackModel(model_flatbuffer.c_str());

  visitor_fn(unpacked_model.get());

  flatbuffers::FlatBufferBuilder builder;
  FinishModelBuffer(builder, Model::Pack(builder, unpacked_model.get()));

  return std::string(reinterpret_cast<char*>(builder.GetBufferPointer()),
                     builder.GetSize());
}

std::string FirstResult(const std::vector<ClassificationResult>& results);

std::string ReadFile(const std::string& file_name);

std::unique_ptr<RegexModel_::PatternT> MakePattern(
    const std::string& collection_name, const std::string& pattern,
    const bool enabled_for_classification, const bool enabled_for_selection,
    const bool enabled_for_annotation, const float score,
    const float priority_score);

// Shortcut function that doesn't need to specify the priority score.
std::unique_ptr<RegexModel_::PatternT> MakePattern(
    const std::string& collection_name, const std::string& pattern,
    const bool enabled_for_classification, const bool enabled_for_selection,
    const bool enabled_for_annotation, const float score);

void AddTestRegexModel(ModelT* unpacked_model);

// Creates empty serialized Annotator model. Takes optional function that can
// modify the unpacked ModelT before the serialization.
std::string CreateEmptyModel(
    const std::function<void(ModelT* model)> model_update_fn =
        [](ModelT* model) {});

// Create fake entity data schema meta data.
void AddTestEntitySchemaData(ModelT* unpacked_model);

AnnotatedSpan MakeAnnotatedSpan(
    CodepointSpan span, const std::string& collection, const float score,
    AnnotatedSpan::Source source = AnnotatedSpan::Source::OTHER);

}  // namespace libtextclassifier3

#endif  // LIBTEXTCLASSIFIER_UTILS_TESTING_ANNOTATOR_H_
