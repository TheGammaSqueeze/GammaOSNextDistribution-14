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

#include "utils/testing/annotator.h"

#include "utils/flatbuffers/mutable.h"
#include "flatbuffers/reflection.h"

namespace libtextclassifier3 {

std::string FirstResult(const std::vector<ClassificationResult>& results) {
  if (results.empty()) {
    return "<INVALID RESULTS>";
  }
  return results[0].collection;
}

std::string ReadFile(const std::string& file_name) {
  std::ifstream file_stream(file_name);
  return std::string(std::istreambuf_iterator<char>(file_stream), {});
}

std::unique_ptr<RegexModel_::PatternT> MakePattern(
    const std::string& collection_name, const std::string& pattern,
    const bool enabled_for_classification, const bool enabled_for_selection,
    const bool enabled_for_annotation, const float score,
    const float priority_score) {
  std::unique_ptr<RegexModel_::PatternT> result(new RegexModel_::PatternT);
  result->collection_name = collection_name;
  result->pattern = pattern;
  // We cannot directly operate with |= on the flag, so use an int here.
  int enabled_modes = ModeFlag_NONE;
  if (enabled_for_annotation) enabled_modes |= ModeFlag_ANNOTATION;
  if (enabled_for_classification) enabled_modes |= ModeFlag_CLASSIFICATION;
  if (enabled_for_selection) enabled_modes |= ModeFlag_SELECTION;
  result->enabled_modes = static_cast<ModeFlag>(enabled_modes);
  result->target_classification_score = score;
  result->priority_score = priority_score;
  return result;
}

// Shortcut function that doesn't need to specify the priority score.
std::unique_ptr<RegexModel_::PatternT> MakePattern(
    const std::string& collection_name, const std::string& pattern,
    const bool enabled_for_classification, const bool enabled_for_selection,
    const bool enabled_for_annotation, const float score) {
  return MakePattern(collection_name, pattern, enabled_for_classification,
                     enabled_for_selection, enabled_for_annotation,
                     /*score=*/score,
                     /*priority_score=*/score);
}

void AddTestRegexModel(ModelT* unpacked_model) {
  // Add test regex models.
  unpacked_model->regex_model->patterns.push_back(MakePattern(
      "person_with_age", "(Barack) (?:(Obama) )?is (\\d+) years old",
      /*enabled_for_classification=*/true,
      /*enabled_for_selection=*/true, /*enabled_for_annotation=*/true, 1.0));

  // Use meta data to generate custom serialized entity data.
  MutableFlatbufferBuilder entity_data_builder(
      flatbuffers::GetRoot<reflection::Schema>(
          unpacked_model->entity_data_schema.data()));
  RegexModel_::PatternT* pattern =
      unpacked_model->regex_model->patterns.back().get();

  {
    std::unique_ptr<MutableFlatbuffer> entity_data =
        entity_data_builder.NewRoot();
    entity_data->Set("is_alive", true);
    pattern->serialized_entity_data = entity_data->Serialize();
  }
  pattern->capturing_group.emplace_back(new CapturingGroupT);
  pattern->capturing_group.emplace_back(new CapturingGroupT);
  pattern->capturing_group.emplace_back(new CapturingGroupT);
  pattern->capturing_group.emplace_back(new CapturingGroupT);
  // Group 0 is the full match, capturing groups starting at 1.
  pattern->capturing_group[1]->entity_field_path.reset(
      new FlatbufferFieldPathT);
  pattern->capturing_group[1]->entity_field_path->field.emplace_back(
      new FlatbufferFieldT);
  pattern->capturing_group[1]->entity_field_path->field.back()->field_name =
      "first_name";
  pattern->capturing_group[2]->entity_field_path.reset(
      new FlatbufferFieldPathT);
  pattern->capturing_group[2]->entity_field_path->field.emplace_back(
      new FlatbufferFieldT);
  pattern->capturing_group[2]->entity_field_path->field.back()->field_name =
      "last_name";
  // Set `former_us_president` field if we match Obama.
  {
    std::unique_ptr<MutableFlatbuffer> entity_data =
        entity_data_builder.NewRoot();
    entity_data->Set("former_us_president", true);
    pattern->capturing_group[2]->serialized_entity_data =
        entity_data->Serialize();
  }
  pattern->capturing_group[3]->entity_field_path.reset(
      new FlatbufferFieldPathT);
  pattern->capturing_group[3]->entity_field_path->field.emplace_back(
      new FlatbufferFieldT);
  pattern->capturing_group[3]->entity_field_path->field.back()->field_name =
      "age";
}

std::string CreateEmptyModel(
    const std::function<void(ModelT* model)> model_update_fn) {
  ModelT model;
  model_update_fn(&model);

  flatbuffers::FlatBufferBuilder builder;
  FinishModelBuffer(builder, Model::Pack(builder, &model));
  return std::string(reinterpret_cast<const char*>(builder.GetBufferPointer()),
                     builder.GetSize());
}

// Create fake entity data schema meta data.
void AddTestEntitySchemaData(ModelT* unpacked_model) {
  // Cannot use object oriented API here as that is not available for the
  // reflection schema.
  flatbuffers::FlatBufferBuilder schema_builder;
  std::vector<flatbuffers::Offset<reflection::Field>> fields = {
      reflection::CreateField(
          schema_builder,
          /*name=*/schema_builder.CreateString("first_name"),
          /*type=*/
          reflection::CreateType(schema_builder,
                                 /*base_type=*/reflection::String),
          /*id=*/0,
          /*offset=*/4),
      reflection::CreateField(
          schema_builder,
          /*name=*/schema_builder.CreateString("is_alive"),
          /*type=*/
          reflection::CreateType(schema_builder,
                                 /*base_type=*/reflection::Bool),
          /*id=*/1,
          /*offset=*/6),
      reflection::CreateField(
          schema_builder,
          /*name=*/schema_builder.CreateString("last_name"),
          /*type=*/
          reflection::CreateType(schema_builder,
                                 /*base_type=*/reflection::String),
          /*id=*/2,
          /*offset=*/8),
      reflection::CreateField(
          schema_builder,
          /*name=*/schema_builder.CreateString("age"),
          /*type=*/
          reflection::CreateType(schema_builder,
                                 /*base_type=*/reflection::Int),
          /*id=*/3,
          /*offset=*/10),
      reflection::CreateField(
          schema_builder,
          /*name=*/schema_builder.CreateString("former_us_president"),
          /*type=*/
          reflection::CreateType(schema_builder,
                                 /*base_type=*/reflection::Bool),
          /*id=*/4,
          /*offset=*/12)};
  std::vector<flatbuffers::Offset<reflection::Enum>> enums;
  std::vector<flatbuffers::Offset<reflection::Object>> objects = {
      reflection::CreateObject(
          schema_builder,
          /*name=*/schema_builder.CreateString("EntityData"),
          /*fields=*/
          schema_builder.CreateVectorOfSortedTables(&fields))};
  schema_builder.Finish(reflection::CreateSchema(
      schema_builder, schema_builder.CreateVectorOfSortedTables(&objects),
      schema_builder.CreateVectorOfSortedTables(&enums),
      /*(unused) file_ident=*/0,
      /*(unused) file_ext=*/0,
      /*root_table*/ objects[0]));

  unpacked_model->entity_data_schema.assign(
      schema_builder.GetBufferPointer(),
      schema_builder.GetBufferPointer() + schema_builder.GetSize());
}

AnnotatedSpan MakeAnnotatedSpan(CodepointSpan span,
                                const std::string& collection,
                                const float score,
                                AnnotatedSpan::Source source) {
  AnnotatedSpan result;
  result.span = span;
  result.classification.push_back({collection, score});
  result.source = source;
  return result;
}

}  // namespace libtextclassifier3
