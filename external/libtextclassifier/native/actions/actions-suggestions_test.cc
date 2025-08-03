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

#include "actions/actions-suggestions.h"

#include <fstream>
#include <iterator>
#include <memory>
#include <string>

#include "actions/actions_model_generated.h"
#include "actions/test-utils.h"
#include "actions/zlib-utils.h"
#include "annotator/collections.h"
#include "annotator/types.h"
#include "utils/flatbuffers/flatbuffers.h"
#include "utils/flatbuffers/flatbuffers_generated.h"
#include "utils/flatbuffers/mutable.h"
#include "utils/grammar/utils/locale-shard-map.h"
#include "utils/grammar/utils/rules.h"
#include "utils/hash/farmhash.h"
#include "utils/jvm-test-utils.h"
#include "utils/test-data-test-utils.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "flatbuffers/flatbuffers.h"
#include "flatbuffers/reflection.h"

namespace libtextclassifier3 {
namespace {

using ::testing::ElementsAre;
using ::testing::FloatEq;
using ::testing::IsEmpty;
using ::testing::NotNull;
using ::testing::SizeIs;

constexpr char kModelFileName[] = "actions_suggestions_test.model";
constexpr char kModelGrammarFileName[] =
    "actions_suggestions_grammar_test.model";
constexpr char kMultiTaskTF2TestModelFileName[] =
    "actions_suggestions_test.multi_task_tf2_test.model";
constexpr char kMultiTaskModelFileName[] =
    "actions_suggestions_test.multi_task_9heads.model";
constexpr char kHashGramModelFileName[] =
    "actions_suggestions_test.hashgram.model";
constexpr char kMultiTaskSrP13nModelFileName[] =
    "actions_suggestions_test.multi_task_sr_p13n.model";
constexpr char kMultiTaskSrEmojiModelFileName[] =
    "actions_suggestions_test.multi_task_sr_emoji.model";
constexpr char kMultiTaskSrEmojiConceptModelFileName[] =
    "actions_suggestions_test.multi_task_sr_emoji_concept.model";
constexpr char kSensitiveTFliteModelFileName[] =
    "actions_suggestions_test.sensitive_tflite.model";
constexpr char kLiveRelayTFLiteModelFileName[] =
    "actions_suggestions_test.live_relay.model";

std::string ReadFile(const std::string& file_name) {
  std::ifstream file_stream(file_name);
  return std::string(std::istreambuf_iterator<char>(file_stream), {});
}

std::string GetModelPath() { return GetTestDataPath("actions/test_data/"); }

class ActionsSuggestionsTest : public testing::Test {
 protected:
  explicit ActionsSuggestionsTest() : unilib_(CreateUniLibForTesting()) {}
  std::unique_ptr<ActionsSuggestions> LoadTestModel(
      const std::string model_file_name) {
    return ActionsSuggestions::FromPath(GetModelPath() + model_file_name,
                                        unilib_.get());
  }
  std::unique_ptr<ActionsSuggestions> LoadHashGramTestModel() {
    return ActionsSuggestions::FromPath(GetModelPath() + kHashGramModelFileName,
                                        unilib_.get());
  }
  std::unique_ptr<ActionsSuggestions> LoadMultiTaskTestModel() {
    return ActionsSuggestions::FromPath(
        GetModelPath() + kMultiTaskModelFileName, unilib_.get());
  }

  std::unique_ptr<ActionsSuggestions> LoadMultiTaskSrP13nTestModel() {
    return ActionsSuggestions::FromPath(
        GetModelPath() + kMultiTaskSrP13nModelFileName, unilib_.get());
  }
  std::unique_ptr<UniLib> unilib_;
};

TEST_F(ActionsSuggestionsTest, InstantiateActionSuggestions) {
  EXPECT_THAT(LoadTestModel(kModelFileName), NotNull());
}

TEST_F(ActionsSuggestionsTest, ProducesEmptyResponseOnInvalidInput) {
  std::unique_ptr<ActionsSuggestions> actions_suggestions =
      LoadTestModel(kModelFileName);
  const ActionsSuggestionsResponse response =
      actions_suggestions->SuggestActions(
          {{{/*user_id=*/1, "Where are you?\xf0\x9f",
             /*reference_time_ms_utc=*/0,
             /*reference_timezone=*/"Europe/Zurich",
             /*annotations=*/{}, /*locales=*/"en"}}});
  EXPECT_THAT(response.actions, IsEmpty());
}

TEST_F(ActionsSuggestionsTest, ProducesEmptyResponseOnInvalidUtf8) {
  std::unique_ptr<ActionsSuggestions> actions_suggestions =
      LoadTestModel(kModelFileName);

  const ActionsSuggestionsResponse response =
      actions_suggestions->SuggestActions(
          {{{/*user_id=*/1,
             "(857) 225-3556 \xed\xa0\x80\xed\xa0\x80\xed\xa0\x80\xed\xa0\x80",
             /*reference_time_ms_utc=*/0,
             /*reference_timezone=*/"Europe/Zurich",
             /*annotations=*/{}, /*locales=*/"en"}}});
  EXPECT_THAT(response.actions, IsEmpty());
}

TEST_F(ActionsSuggestionsTest, SuggestsActions) {
  std::unique_ptr<ActionsSuggestions> actions_suggestions =
      LoadTestModel(kModelFileName);
  const ActionsSuggestionsResponse response =
      actions_suggestions->SuggestActions(
          {{{/*user_id=*/1, "Where are you?", /*reference_time_ms_utc=*/0,
             /*reference_timezone=*/"Europe/Zurich",
             /*annotations=*/{}, /*locales=*/"en"}}});
  EXPECT_EQ(response.actions.size(), 3 /* share_location + 2 smart replies*/);
}

TEST_F(ActionsSuggestionsTest, SuggestsNoActionsForUnknownLocale) {
  std::unique_ptr<ActionsSuggestions> actions_suggestions =
      LoadTestModel(kModelFileName);
  const ActionsSuggestionsResponse response =
      actions_suggestions->SuggestActions(
          {{{/*user_id=*/1, "Where are you?", /*reference_time_ms_utc=*/0,
             /*reference_timezone=*/"Europe/Zurich",
             /*annotations=*/{}, /*locales=*/"zz"}}});
  EXPECT_THAT(response.actions, testing::IsEmpty());
}

TEST_F(ActionsSuggestionsTest, SuggestsActionsFromAnnotations) {
  std::unique_ptr<ActionsSuggestions> actions_suggestions =
      LoadTestModel(kModelFileName);
  AnnotatedSpan annotation;
  annotation.span = {11, 15};
  annotation.classification = {ClassificationResult("address", 1.0)};
  const ActionsSuggestionsResponse response =
      actions_suggestions->SuggestActions(
          {{{/*user_id=*/1, "are you at home?",
             /*reference_time_ms_utc=*/0,
             /*reference_timezone=*/"Europe/Zurich",
             /*annotations=*/{annotation},
             /*locales=*/"en"}}});
  ASSERT_GE(response.actions.size(), 1);
  EXPECT_EQ(response.actions.front().type, "view_map");
  EXPECT_EQ(response.actions.front().score, 1.0);
}

TEST_F(ActionsSuggestionsTest, SuggestsActionsFromAnnotationsWithEntityData) {
  const std::string actions_model_string =
      ReadFile(GetModelPath() + kModelFileName);
  std::unique_ptr<ActionsModelT> actions_model =
      UnPackActionsModel(actions_model_string.c_str());
  SetTestEntityDataSchema(actions_model.get());

  // Set custom actions from annotations config.
  actions_model->annotation_actions_spec->annotation_mapping.clear();
  actions_model->annotation_actions_spec->annotation_mapping.emplace_back(
      new AnnotationActionsSpec_::AnnotationMappingT);
  AnnotationActionsSpec_::AnnotationMappingT* mapping =
      actions_model->annotation_actions_spec->annotation_mapping.back().get();
  mapping->annotation_collection = "address";
  mapping->action.reset(new ActionSuggestionSpecT);
  mapping->action->type = "save_location";
  mapping->action->score = 1.0;
  mapping->action->priority_score = 2.0;
  mapping->entity_field.reset(new FlatbufferFieldPathT);
  mapping->entity_field->field.emplace_back(new FlatbufferFieldT);
  mapping->entity_field->field.back()->field_name = "location";

  flatbuffers::FlatBufferBuilder builder;
  FinishActionsModelBuffer(builder,
                           ActionsModel::Pack(builder, actions_model.get()));
  std::unique_ptr<ActionsSuggestions> actions_suggestions =
      ActionsSuggestions::FromUnownedBuffer(
          reinterpret_cast<const uint8_t*>(builder.GetBufferPointer()),
          builder.GetSize(), unilib_.get());

  AnnotatedSpan annotation;
  annotation.span = {11, 15};
  annotation.classification = {ClassificationResult("address", 1.0)};
  const ActionsSuggestionsResponse response =
      actions_suggestions->SuggestActions(
          {{{/*user_id=*/1, "are you at home?",
             /*reference_time_ms_utc=*/0,
             /*reference_timezone=*/"Europe/Zurich",
             /*annotations=*/{annotation},
             /*locales=*/"en"}}});
  ASSERT_GE(response.actions.size(), 1);
  EXPECT_EQ(response.actions.front().type, "save_location");
  EXPECT_EQ(response.actions.front().score, 1.0);

  // Check that the `location` entity field holds the text from the address
  // annotation.
  const flatbuffers::Table* entity =
      flatbuffers::GetAnyRoot(reinterpret_cast<const unsigned char*>(
          response.actions.front().serialized_entity_data.data()));
  EXPECT_EQ(entity->GetPointer<const flatbuffers::String*>(/*field=*/6)->str(),
            "home");
}

TEST_F(ActionsSuggestionsTest,
       SuggestsActionsFromAnnotationsWithNormalization) {
  const std::string actions_model_string =
      ReadFile(GetModelPath() + kModelFileName);
  std::unique_ptr<ActionsModelT> actions_model =
      UnPackActionsModel(actions_model_string.c_str());
  SetTestEntityDataSchema(actions_model.get());

  // Set custom actions from annotations config.
  actions_model->annotation_actions_spec->annotation_mapping.clear();
  actions_model->annotation_actions_spec->annotation_mapping.emplace_back(
      new AnnotationActionsSpec_::AnnotationMappingT);
  AnnotationActionsSpec_::AnnotationMappingT* mapping =
      actions_model->annotation_actions_spec->annotation_mapping.back().get();
  mapping->annotation_collection = "address";
  mapping->action.reset(new ActionSuggestionSpecT);
  mapping->action->type = "save_location";
  mapping->action->score = 1.0;
  mapping->action->priority_score = 2.0;
  mapping->entity_field.reset(new FlatbufferFieldPathT);
  mapping->entity_field->field.emplace_back(new FlatbufferFieldT);
  mapping->entity_field->field.back()->field_name = "location";
  mapping->normalization_options.reset(new NormalizationOptionsT);
  mapping->normalization_options->codepointwise_normalization =
      NormalizationOptions_::CodepointwiseNormalizationOp_UPPERCASE;

  flatbuffers::FlatBufferBuilder builder;
  FinishActionsModelBuffer(builder,
                           ActionsModel::Pack(builder, actions_model.get()));
  std::unique_ptr<ActionsSuggestions> actions_suggestions =
      ActionsSuggestions::FromUnownedBuffer(
          reinterpret_cast<const uint8_t*>(builder.GetBufferPointer()),
          builder.GetSize(), unilib_.get());

  AnnotatedSpan annotation;
  annotation.span = {11, 15};
  annotation.classification = {ClassificationResult("address", 1.0)};
  const ActionsSuggestionsResponse response =
      actions_suggestions->SuggestActions(
          {{{/*user_id=*/1, "are you at home?",
             /*reference_time_ms_utc=*/0,
             /*reference_timezone=*/"Europe/Zurich",
             /*annotations=*/{annotation},
             /*locales=*/"en"}}});
  ASSERT_GE(response.actions.size(), 1);
  EXPECT_EQ(response.actions.front().type, "save_location");
  EXPECT_EQ(response.actions.front().score, 1.0);

  // Check that the `location` entity field holds the normalized text of the
  // annotation.
  const flatbuffers::Table* entity =
      flatbuffers::GetAnyRoot(reinterpret_cast<const unsigned char*>(
          response.actions.front().serialized_entity_data.data()));
  EXPECT_EQ(entity->GetPointer<const flatbuffers::String*>(/*field=*/6)->str(),
            "HOME");
}

TEST_F(ActionsSuggestionsTest, SuggestsActionsFromDuplicatedAnnotations) {
  std::unique_ptr<ActionsSuggestions> actions_suggestions =
      LoadTestModel(kModelFileName);
  AnnotatedSpan flight_annotation;
  flight_annotation.span = {11, 15};
  flight_annotation.classification = {ClassificationResult("flight", 2.5)};
  AnnotatedSpan flight_annotation2;
  flight_annotation2.span = {35, 39};
  flight_annotation2.classification = {ClassificationResult("flight", 3.0)};
  AnnotatedSpan email_annotation;
  email_annotation.span = {43, 56};
  email_annotation.classification = {ClassificationResult("email", 2.0)};

  const ActionsSuggestionsResponse response =
      actions_suggestions->SuggestActions(
          {{{/*user_id=*/1,
             "call me at LX38 or send message to LX38 or test@test.com.",
             /*reference_time_ms_utc=*/0,
             /*reference_timezone=*/"Europe/Zurich",
             /*annotations=*/
             {flight_annotation, flight_annotation2, email_annotation},
             /*locales=*/"en"}}});

  ASSERT_GE(response.actions.size(), 2);
  EXPECT_EQ(response.actions[0].type, "track_flight");
  EXPECT_EQ(response.actions[0].score, 3.0);
  EXPECT_EQ(response.actions[1].type, "send_email");
  EXPECT_EQ(response.actions[1].score, 2.0);
}

TEST_F(ActionsSuggestionsTest, SuggestsActionsAnnotationsWithNoDeduplication) {
  const std::string actions_model_string =
      ReadFile(GetModelPath() + kModelFileName);
  std::unique_ptr<ActionsModelT> actions_model =
      UnPackActionsModel(actions_model_string.c_str());
  // Disable deduplication.
  actions_model->annotation_actions_spec->deduplicate_annotations = false;
  flatbuffers::FlatBufferBuilder builder;
  FinishActionsModelBuffer(builder,
                           ActionsModel::Pack(builder, actions_model.get()));
  std::unique_ptr<ActionsSuggestions> actions_suggestions =
      ActionsSuggestions::FromUnownedBuffer(
          reinterpret_cast<const uint8_t*>(builder.GetBufferPointer()),
          builder.GetSize(), unilib_.get());
  AnnotatedSpan flight_annotation;
  flight_annotation.span = {11, 15};
  flight_annotation.classification = {ClassificationResult("flight", 2.5)};
  AnnotatedSpan flight_annotation2;
  flight_annotation2.span = {35, 39};
  flight_annotation2.classification = {ClassificationResult("flight", 3.0)};
  AnnotatedSpan email_annotation;
  email_annotation.span = {43, 56};
  email_annotation.classification = {ClassificationResult("email", 2.0)};

  const ActionsSuggestionsResponse response =
      actions_suggestions->SuggestActions(
          {{{/*user_id=*/1,
             "call me at LX38 or send message to LX38 or test@test.com.",
             /*reference_time_ms_utc=*/0,
             /*reference_timezone=*/"Europe/Zurich",
             /*annotations=*/
             {flight_annotation, flight_annotation2, email_annotation},
             /*locales=*/"en"}}});

  ASSERT_GE(response.actions.size(), 3);
  EXPECT_EQ(response.actions[0].type, "track_flight");
  EXPECT_EQ(response.actions[0].score, 3.0);
  EXPECT_EQ(response.actions[1].type, "track_flight");
  EXPECT_EQ(response.actions[1].score, 2.5);
  EXPECT_EQ(response.actions[2].type, "send_email");
  EXPECT_EQ(response.actions[2].score, 2.0);
}

ActionsSuggestionsResponse TestSuggestActionsFromAnnotations(
    const std::function<void(ActionsModelT*)>& set_config_fn,
    const UniLib* unilib = nullptr) {
  const std::string actions_model_string =
      ReadFile(GetModelPath() + kModelFileName);
  std::unique_ptr<ActionsModelT> actions_model =
      UnPackActionsModel(actions_model_string.c_str());

  // Set custom config.
  set_config_fn(actions_model.get());

  // Disable smart reply for easier testing.
  actions_model->preconditions->min_smart_reply_triggering_score = 1.0;

  flatbuffers::FlatBufferBuilder builder;
  FinishActionsModelBuffer(builder,
                           ActionsModel::Pack(builder, actions_model.get()));
  std::unique_ptr<ActionsSuggestions> actions_suggestions =
      ActionsSuggestions::FromUnownedBuffer(
          reinterpret_cast<const uint8_t*>(builder.GetBufferPointer()),
          builder.GetSize(), unilib);

  AnnotatedSpan flight_annotation;
  flight_annotation.span = {15, 19};
  flight_annotation.classification = {ClassificationResult("flight", 2.0)};
  AnnotatedSpan email_annotation;
  email_annotation.span = {0, 16};
  email_annotation.classification = {ClassificationResult("email", 1.0)};

  return actions_suggestions->SuggestActions(
      {{{/*user_id=*/ActionsSuggestions::kLocalUserId,
         "hehe@android.com",
         /*reference_time_ms_utc=*/0,
         /*reference_timezone=*/"Europe/Zurich",
         /*annotations=*/
         {email_annotation},
         /*locales=*/"en"},
        {/*user_id=*/2,
         "yoyo@android.com",
         /*reference_time_ms_utc=*/0,
         /*reference_timezone=*/"Europe/Zurich",
         /*annotations=*/
         {email_annotation},
         /*locales=*/"en"},
        {/*user_id=*/1,
         "test@android.com",
         /*reference_time_ms_utc=*/0,
         /*reference_timezone=*/"Europe/Zurich",
         /*annotations=*/
         {email_annotation},
         /*locales=*/"en"},
        {/*user_id=*/1,
         "I am on flight LX38.",
         /*reference_time_ms_utc=*/0,
         /*reference_timezone=*/"Europe/Zurich",
         /*annotations=*/
         {flight_annotation},
         /*locales=*/"en"}}});
}

TEST_F(ActionsSuggestionsTest, SuggestsActionsWithAnnotationsOnlyLastMessage) {
  const ActionsSuggestionsResponse response = TestSuggestActionsFromAnnotations(
      [](ActionsModelT* actions_model) {
        actions_model->annotation_actions_spec->include_local_user_messages =
            false;
        actions_model->annotation_actions_spec->only_until_last_sent = true;
        actions_model->annotation_actions_spec->max_history_from_any_person = 1;
        actions_model->annotation_actions_spec->max_history_from_last_person =
            1;
      },
      unilib_.get());
  EXPECT_THAT(response.actions, SizeIs(1));
  EXPECT_EQ(response.actions[0].type, "track_flight");
}

TEST_F(ActionsSuggestionsTest, SuggestsActionsWithAnnotationsOnlyLastPerson) {
  const ActionsSuggestionsResponse response = TestSuggestActionsFromAnnotations(
      [](ActionsModelT* actions_model) {
        actions_model->annotation_actions_spec->include_local_user_messages =
            false;
        actions_model->annotation_actions_spec->only_until_last_sent = true;
        actions_model->annotation_actions_spec->max_history_from_any_person = 1;
        actions_model->annotation_actions_spec->max_history_from_last_person =
            3;
      },
      unilib_.get());
  EXPECT_THAT(response.actions, SizeIs(2));
  EXPECT_EQ(response.actions[0].type, "track_flight");
  EXPECT_EQ(response.actions[1].type, "send_email");
}

TEST_F(ActionsSuggestionsTest, SuggestsActionsWithAnnotationsFromAny) {
  const ActionsSuggestionsResponse response = TestSuggestActionsFromAnnotations(
      [](ActionsModelT* actions_model) {
        actions_model->annotation_actions_spec->include_local_user_messages =
            false;
        actions_model->annotation_actions_spec->only_until_last_sent = true;
        actions_model->annotation_actions_spec->max_history_from_any_person = 2;
        actions_model->annotation_actions_spec->max_history_from_last_person =
            1;
      },
      unilib_.get());
  EXPECT_THAT(response.actions, SizeIs(2));
  EXPECT_EQ(response.actions[0].type, "track_flight");
  EXPECT_EQ(response.actions[1].type, "send_email");
}

TEST_F(ActionsSuggestionsTest,
       SuggestsActionsWithAnnotationsFromAnyManyMessages) {
  const ActionsSuggestionsResponse response = TestSuggestActionsFromAnnotations(
      [](ActionsModelT* actions_model) {
        actions_model->annotation_actions_spec->include_local_user_messages =
            false;
        actions_model->annotation_actions_spec->only_until_last_sent = true;
        actions_model->annotation_actions_spec->max_history_from_any_person = 3;
        actions_model->annotation_actions_spec->max_history_from_last_person =
            1;
      },
      unilib_.get());
  EXPECT_THAT(response.actions, SizeIs(3));
  EXPECT_EQ(response.actions[0].type, "track_flight");
  EXPECT_EQ(response.actions[1].type, "send_email");
  EXPECT_EQ(response.actions[2].type, "send_email");
}

TEST_F(ActionsSuggestionsTest,
       SuggestsActionsWithAnnotationsFromAnyManyMessagesButNotLocalUser) {
  const ActionsSuggestionsResponse response = TestSuggestActionsFromAnnotations(
      [](ActionsModelT* actions_model) {
        actions_model->annotation_actions_spec->include_local_user_messages =
            false;
        actions_model->annotation_actions_spec->only_until_last_sent = true;
        actions_model->annotation_actions_spec->max_history_from_any_person = 5;
        actions_model->annotation_actions_spec->max_history_from_last_person =
            1;
      },
      unilib_.get());
  EXPECT_THAT(response.actions, SizeIs(3));
  EXPECT_EQ(response.actions[0].type, "track_flight");
  EXPECT_EQ(response.actions[1].type, "send_email");
  EXPECT_EQ(response.actions[2].type, "send_email");
}

TEST_F(ActionsSuggestionsTest,
       SuggestsActionsWithAnnotationsFromAnyManyMessagesAlsoFromLocalUser) {
  const ActionsSuggestionsResponse response = TestSuggestActionsFromAnnotations(
      [](ActionsModelT* actions_model) {
        actions_model->annotation_actions_spec->include_local_user_messages =
            true;
        actions_model->annotation_actions_spec->only_until_last_sent = false;
        actions_model->annotation_actions_spec->max_history_from_any_person = 5;
        actions_model->annotation_actions_spec->max_history_from_last_person =
            1;
      },
      unilib_.get());
  EXPECT_THAT(response.actions, SizeIs(4));
  EXPECT_EQ(response.actions[0].type, "track_flight");
  EXPECT_EQ(response.actions[1].type, "send_email");
  EXPECT_EQ(response.actions[2].type, "send_email");
  EXPECT_EQ(response.actions[3].type, "send_email");
}

void TestSuggestActionsWithThreshold(
    const std::function<void(ActionsModelT*)>& set_value_fn,
    const UniLib* unilib = nullptr, const int expected_size = 0,
    const std::string& preconditions_overwrite = "") {
  const std::string actions_model_string =
      ReadFile(GetModelPath() + kModelFileName);
  std::unique_ptr<ActionsModelT> actions_model =
      UnPackActionsModel(actions_model_string.c_str());
  set_value_fn(actions_model.get());
  flatbuffers::FlatBufferBuilder builder;
  FinishActionsModelBuffer(builder,
                           ActionsModel::Pack(builder, actions_model.get()));
  std::unique_ptr<ActionsSuggestions> actions_suggestions =
      ActionsSuggestions::FromUnownedBuffer(
          reinterpret_cast<const uint8_t*>(builder.GetBufferPointer()),
          builder.GetSize(), unilib, preconditions_overwrite);
  ASSERT_TRUE(actions_suggestions);
  const ActionsSuggestionsResponse response =
      actions_suggestions->SuggestActions(
          {{{/*user_id=*/1, "I have the low-ground. Where are you?",
             /*reference_time_ms_utc=*/0,
             /*reference_timezone=*/"Europe/Zurich",
             /*annotations=*/{}, /*locales=*/"en"}}});
  EXPECT_LE(response.actions.size(), expected_size);
}

TEST_F(ActionsSuggestionsTest, SuggestsActionsWithTriggeringScore) {
  TestSuggestActionsWithThreshold(
      [](ActionsModelT* actions_model) {
        actions_model->preconditions->min_smart_reply_triggering_score = 1.0;
      },
      unilib_.get(),
      /*expected_size=*/1 /*no smart reply, only actions*/
  );
}

TEST_F(ActionsSuggestionsTest, SuggestsActionsWithMinReplyScore) {
  TestSuggestActionsWithThreshold(
      [](ActionsModelT* actions_model) {
        actions_model->preconditions->min_reply_score_threshold = 1.0;
      },
      unilib_.get(),
      /*expected_size=*/1 /*no smart reply, only actions*/
  );
}

TEST_F(ActionsSuggestionsTest, SuggestsActionsWithSensitiveTopicScore) {
  TestSuggestActionsWithThreshold(
      [](ActionsModelT* actions_model) {
        actions_model->preconditions->max_sensitive_topic_score = 0.0;
      },
      unilib_.get(),
      /*expected_size=*/4 /* no sensitive prediction in test model*/);
}

TEST_F(ActionsSuggestionsTest, SuggestsActionsWithMaxInputLength) {
  TestSuggestActionsWithThreshold(
      [](ActionsModelT* actions_model) {
        actions_model->preconditions->max_input_length = 0;
      },
      unilib_.get());
}

TEST_F(ActionsSuggestionsTest, SuggestsActionsWithMinInputLength) {
  TestSuggestActionsWithThreshold(
      [](ActionsModelT* actions_model) {
        actions_model->preconditions->min_input_length = 100;
      },
      unilib_.get());
}

TEST_F(ActionsSuggestionsTest, SuggestsActionsWithPreconditionsOverwrite) {
  TriggeringPreconditionsT preconditions_overwrite;
  preconditions_overwrite.max_input_length = 0;
  flatbuffers::FlatBufferBuilder builder;
  builder.Finish(
      TriggeringPreconditions::Pack(builder, &preconditions_overwrite));
  TestSuggestActionsWithThreshold(
      // Keep model untouched.
      [](ActionsModelT* actions_model) {}, unilib_.get(),
      /*expected_size=*/0,
      std::string(reinterpret_cast<const char*>(builder.GetBufferPointer()),
                  builder.GetSize()));
}

#ifdef TC3_UNILIB_ICU
TEST_F(ActionsSuggestionsTest, SuggestsActionsLowConfidence) {
  TestSuggestActionsWithThreshold(
      [](ActionsModelT* actions_model) {
        actions_model->preconditions->suppress_on_low_confidence_input = true;
        actions_model->low_confidence_rules.reset(new RulesModelT);
        actions_model->low_confidence_rules->regex_rule.emplace_back(
            new RulesModel_::RegexRuleT);
        actions_model->low_confidence_rules->regex_rule.back()->pattern =
            "low-ground";
      },
      unilib_.get());
}

TEST_F(ActionsSuggestionsTest, SuggestsActionsLowConfidenceInputOutput) {
  const std::string actions_model_string =
      ReadFile(GetModelPath() + kModelFileName);
  std::unique_ptr<ActionsModelT> actions_model =
      UnPackActionsModel(actions_model_string.c_str());
  // Add custom triggering rule.
  actions_model->rules.reset(new RulesModelT());
  actions_model->rules->regex_rule.emplace_back(new RulesModel_::RegexRuleT);
  RulesModel_::RegexRuleT* rule = actions_model->rules->regex_rule.back().get();
  rule->pattern = "^(?i:hello\\s(there))$";
  {
    std::unique_ptr<RulesModel_::RuleActionSpecT> rule_action(
        new RulesModel_::RuleActionSpecT);
    rule_action->action.reset(new ActionSuggestionSpecT);
    rule_action->action->type = "text_reply";
    rule_action->action->response_text = "General Desaster!";
    rule_action->action->score = 1.0f;
    rule_action->action->priority_score = 1.0f;
    rule->actions.push_back(std::move(rule_action));
  }
  {
    std::unique_ptr<RulesModel_::RuleActionSpecT> rule_action(
        new RulesModel_::RuleActionSpecT);
    rule_action->action.reset(new ActionSuggestionSpecT);
    rule_action->action->type = "text_reply";
    rule_action->action->response_text = "General Kenobi!";
    rule_action->action->score = 1.0f;
    rule_action->action->priority_score = 1.0f;
    rule->actions.push_back(std::move(rule_action));
  }

  // Add input-output low confidence rule.
  actions_model->preconditions->suppress_on_low_confidence_input = true;
  actions_model->low_confidence_rules.reset(new RulesModelT);
  actions_model->low_confidence_rules->regex_rule.emplace_back(
      new RulesModel_::RegexRuleT);
  actions_model->low_confidence_rules->regex_rule.back()->pattern = "hello";
  actions_model->low_confidence_rules->regex_rule.back()->output_pattern =
      "(?i:desaster)";

  flatbuffers::FlatBufferBuilder builder;
  FinishActionsModelBuffer(builder,
                           ActionsModel::Pack(builder, actions_model.get()));
  std::unique_ptr<ActionsSuggestions> actions_suggestions =
      ActionsSuggestions::FromUnownedBuffer(
          reinterpret_cast<const uint8_t*>(builder.GetBufferPointer()),
          builder.GetSize(), unilib_.get());
  ASSERT_TRUE(actions_suggestions);
  const ActionsSuggestionsResponse response =
      actions_suggestions->SuggestActions(
          {{{/*user_id=*/1, "hello there",
             /*reference_time_ms_utc=*/0,
             /*reference_timezone=*/"Europe/Zurich",
             /*annotations=*/{}, /*locales=*/"en"}}});
  ASSERT_GE(response.actions.size(), 1);
  EXPECT_EQ(response.actions[0].response_text, "General Kenobi!");
}

TEST_F(ActionsSuggestionsTest,
       SuggestsActionsLowConfidenceInputOutputOverwrite) {
  const std::string actions_model_string =
      ReadFile(GetModelPath() + kModelFileName);
  std::unique_ptr<ActionsModelT> actions_model =
      UnPackActionsModel(actions_model_string.c_str());
  actions_model->low_confidence_rules.reset();

  // Add custom triggering rule.
  actions_model->rules.reset(new RulesModelT());
  actions_model->rules->regex_rule.emplace_back(new RulesModel_::RegexRuleT);
  RulesModel_::RegexRuleT* rule = actions_model->rules->regex_rule.back().get();
  rule->pattern = "^(?i:hello\\s(there))$";
  {
    std::unique_ptr<RulesModel_::RuleActionSpecT> rule_action(
        new RulesModel_::RuleActionSpecT);
    rule_action->action.reset(new ActionSuggestionSpecT);
    rule_action->action->type = "text_reply";
    rule_action->action->response_text = "General Desaster!";
    rule_action->action->score = 1.0f;
    rule_action->action->priority_score = 1.0f;
    rule->actions.push_back(std::move(rule_action));
  }
  {
    std::unique_ptr<RulesModel_::RuleActionSpecT> rule_action(
        new RulesModel_::RuleActionSpecT);
    rule_action->action.reset(new ActionSuggestionSpecT);
    rule_action->action->type = "text_reply";
    rule_action->action->response_text = "General Kenobi!";
    rule_action->action->score = 1.0f;
    rule_action->action->priority_score = 1.0f;
    rule->actions.push_back(std::move(rule_action));
  }

  // Add custom triggering rule via overwrite.
  actions_model->preconditions->low_confidence_rules.reset();
  TriggeringPreconditionsT preconditions;
  preconditions.suppress_on_low_confidence_input = true;
  preconditions.low_confidence_rules.reset(new RulesModelT);
  preconditions.low_confidence_rules->regex_rule.emplace_back(
      new RulesModel_::RegexRuleT);
  preconditions.low_confidence_rules->regex_rule.back()->pattern = "hello";
  preconditions.low_confidence_rules->regex_rule.back()->output_pattern =
      "(?i:desaster)";
  flatbuffers::FlatBufferBuilder preconditions_builder;
  preconditions_builder.Finish(
      TriggeringPreconditions::Pack(preconditions_builder, &preconditions));
  std::string serialize_preconditions = std::string(
      reinterpret_cast<const char*>(preconditions_builder.GetBufferPointer()),
      preconditions_builder.GetSize());

  flatbuffers::FlatBufferBuilder builder;
  FinishActionsModelBuffer(builder,
                           ActionsModel::Pack(builder, actions_model.get()));
  std::unique_ptr<ActionsSuggestions> actions_suggestions =
      ActionsSuggestions::FromUnownedBuffer(
          reinterpret_cast<const uint8_t*>(builder.GetBufferPointer()),
          builder.GetSize(), unilib_.get(), serialize_preconditions);

  ASSERT_TRUE(actions_suggestions);
  const ActionsSuggestionsResponse response =
      actions_suggestions->SuggestActions(
          {{{/*user_id=*/1, "hello there",
             /*reference_time_ms_utc=*/0,
             /*reference_timezone=*/"Europe/Zurich",
             /*annotations=*/{}, /*locales=*/"en"}}});
  ASSERT_GE(response.actions.size(), 1);
  EXPECT_EQ(response.actions[0].response_text, "General Kenobi!");
}
#endif

TEST_F(ActionsSuggestionsTest, SuppressActionsFromAnnotationsOnSensitiveTopic) {
  const std::string actions_model_string =
      ReadFile(GetModelPath() + kModelFileName);
  std::unique_ptr<ActionsModelT> actions_model =
      UnPackActionsModel(actions_model_string.c_str());

  // Don't test if no sensitivity score is produced
  if (actions_model->tflite_model_spec->output_sensitive_topic_score < 0) {
    return;
  }

  actions_model->preconditions->max_sensitive_topic_score = 0.0;
  actions_model->preconditions->suppress_on_sensitive_topic = true;
  flatbuffers::FlatBufferBuilder builder;
  FinishActionsModelBuffer(builder,
                           ActionsModel::Pack(builder, actions_model.get()));
  std::unique_ptr<ActionsSuggestions> actions_suggestions =
      ActionsSuggestions::FromUnownedBuffer(
          reinterpret_cast<const uint8_t*>(builder.GetBufferPointer()),
          builder.GetSize(), unilib_.get());
  AnnotatedSpan annotation;
  annotation.span = {11, 15};
  annotation.classification = {
      ClassificationResult(Collections::Address(), 1.0)};
  const ActionsSuggestionsResponse response =
      actions_suggestions->SuggestActions(
          {{{/*user_id=*/1, "are you at home?",
             /*reference_time_ms_utc=*/0,
             /*reference_timezone=*/"Europe/Zurich",
             /*annotations=*/{annotation},
             /*locales=*/"en"}}});
  EXPECT_THAT(response.actions, testing::IsEmpty());
}

TEST_F(ActionsSuggestionsTest, SuggestsActionsWithLongerConversation) {
  const std::string actions_model_string =
      ReadFile(GetModelPath() + kModelFileName);
  std::unique_ptr<ActionsModelT> actions_model =
      UnPackActionsModel(actions_model_string.c_str());

  // Allow a larger conversation context.
  actions_model->max_conversation_history_length = 10;

  flatbuffers::FlatBufferBuilder builder;
  FinishActionsModelBuffer(builder,
                           ActionsModel::Pack(builder, actions_model.get()));
  std::unique_ptr<ActionsSuggestions> actions_suggestions =
      ActionsSuggestions::FromUnownedBuffer(
          reinterpret_cast<const uint8_t*>(builder.GetBufferPointer()),
          builder.GetSize(), unilib_.get());
  AnnotatedSpan annotation;
  annotation.span = {11, 15};
  annotation.classification = {
      ClassificationResult(Collections::Address(), 1.0)};
  const ActionsSuggestionsResponse response =
      actions_suggestions->SuggestActions(
          {{{/*user_id=*/ActionsSuggestions::kLocalUserId, "hi, how are you?",
             /*reference_time_ms_utc=*/10000,
             /*reference_timezone=*/"Europe/Zurich",
             /*annotations=*/{}, /*locales=*/"en"},
            {/*user_id=*/1, "good! are you at home?",
             /*reference_time_ms_utc=*/15000,
             /*reference_timezone=*/"Europe/Zurich",
             /*annotations=*/{annotation},
             /*locales=*/"en"}}});
  ASSERT_GE(response.actions.size(), 1);
  EXPECT_EQ(response.actions[0].type, "view_map");
  EXPECT_EQ(response.actions[0].score, 1.0);
}

TEST_F(ActionsSuggestionsTest, SuggestsActionsFromTF2MultiTaskModel) {
  std::unique_ptr<ActionsSuggestions> actions_suggestions =
      LoadTestModel(kMultiTaskTF2TestModelFileName);
  const ActionsSuggestionsResponse response =
      actions_suggestions->SuggestActions(
          {{{/*user_id=*/1, "Hello how are you",
             /*reference_time_ms_utc=*/0,
             /*reference_timezone=*/"Europe/Zurich",
             /*annotations=*/{},
             /*locales=*/"en"}}});
  EXPECT_EQ(response.actions.size(), 4);
  EXPECT_EQ(response.actions[0].response_text, "Okay");
  EXPECT_EQ(response.actions[0].type, "REPLY_SUGGESTION");
  EXPECT_EQ(response.actions[3].type, "TEST_CLASSIFIER_INTENT");
}

TEST_F(ActionsSuggestionsTest, SuggestsActionsFromPhoneGrammarAnnotations) {
  std::unique_ptr<ActionsSuggestions> actions_suggestions =
      LoadTestModel(kModelGrammarFileName);
  AnnotatedSpan annotation;
  annotation.span = {11, 15};
  annotation.classification = {ClassificationResult("phone", 0.0)};
  const ActionsSuggestionsResponse response =
      actions_suggestions->SuggestActions(
          {{{/*user_id=*/1, "Contact us at: *1234",
             /*reference_time_ms_utc=*/0,
             /*reference_timezone=*/"Europe/Zurich",
             /*annotations=*/{annotation},
             /*locales=*/"en"}}});
  ASSERT_GE(response.actions.size(), 1);
  EXPECT_EQ(response.actions.front().type, "call_phone");
  EXPECT_EQ(response.actions.front().score, 0.0);
  EXPECT_EQ(response.actions.front().priority_score, 0.0);
  EXPECT_EQ(response.actions.front().annotations.size(), 1);
  EXPECT_EQ(response.actions.front().annotations.front().span.span.first, 15);
  EXPECT_EQ(response.actions.front().annotations.front().span.span.second, 20);
}

TEST_F(ActionsSuggestionsTest, CreateActionsFromClassificationResult) {
  std::unique_ptr<ActionsSuggestions> actions_suggestions =
      LoadTestModel(kModelFileName);
  AnnotatedSpan annotation;
  annotation.span = {8, 12};
  annotation.classification = {
      ClassificationResult(Collections::Flight(), 1.0)};

  const ActionsSuggestionsResponse response =
      actions_suggestions->SuggestActions(
          {{{/*user_id=*/1, "I'm on LX38?",
             /*reference_time_ms_utc=*/0,
             /*reference_timezone=*/"Europe/Zurich",
             /*annotations=*/{annotation},
             /*locales=*/"en"}}});

  ASSERT_GE(response.actions.size(), 2);
  EXPECT_EQ(response.actions[0].type, "track_flight");
  EXPECT_EQ(response.actions[0].score, 1.0);
  EXPECT_THAT(response.actions[0].annotations, SizeIs(1));
  EXPECT_EQ(response.actions[0].annotations[0].span.message_index, 0);
  EXPECT_EQ(response.actions[0].annotations[0].span.span, annotation.span);
}

#ifdef TC3_UNILIB_ICU
TEST_F(ActionsSuggestionsTest, CreateActionsFromRules) {
  const std::string actions_model_string =
      ReadFile(GetModelPath() + kModelFileName);
  std::unique_ptr<ActionsModelT> actions_model =
      UnPackActionsModel(actions_model_string.c_str());
  ASSERT_TRUE(DecompressActionsModel(actions_model.get()));

  actions_model->rules.reset(new RulesModelT());
  actions_model->rules->regex_rule.emplace_back(new RulesModel_::RegexRuleT);
  RulesModel_::RegexRuleT* rule = actions_model->rules->regex_rule.back().get();
  rule->pattern = "^(?i:hello\\s(there))$";
  rule->actions.emplace_back(new RulesModel_::RuleActionSpecT);
  rule->actions.back()->action.reset(new ActionSuggestionSpecT);
  ActionSuggestionSpecT* action = rule->actions.back()->action.get();
  action->type = "text_reply";
  action->response_text = "General Kenobi!";
  action->score = 1.0f;
  action->priority_score = 1.0f;

  // Set capturing groups for entity data.
  rule->actions.back()->capturing_group.emplace_back(
      new RulesModel_::RuleActionSpec_::RuleCapturingGroupT);
  RulesModel_::RuleActionSpec_::RuleCapturingGroupT* greeting_group =
      rule->actions.back()->capturing_group.back().get();
  greeting_group->group_id = 0;
  greeting_group->entity_field.reset(new FlatbufferFieldPathT);
  greeting_group->entity_field->field.emplace_back(new FlatbufferFieldT);
  greeting_group->entity_field->field.back()->field_name = "greeting";
  rule->actions.back()->capturing_group.emplace_back(
      new RulesModel_::RuleActionSpec_::RuleCapturingGroupT);
  RulesModel_::RuleActionSpec_::RuleCapturingGroupT* location_group =
      rule->actions.back()->capturing_group.back().get();
  location_group->group_id = 1;
  location_group->entity_field.reset(new FlatbufferFieldPathT);
  location_group->entity_field->field.emplace_back(new FlatbufferFieldT);
  location_group->entity_field->field.back()->field_name = "location";

  // Set test entity data schema.
  SetTestEntityDataSchema(actions_model.get());

  // Use meta data to generate custom serialized entity data.
  MutableFlatbufferBuilder entity_data_builder(
      flatbuffers::GetRoot<reflection::Schema>(
          actions_model->actions_entity_data_schema.data()));
  std::unique_ptr<MutableFlatbuffer> entity_data =
      entity_data_builder.NewRoot();
  entity_data->Set("person", "Kenobi");
  action->serialized_entity_data = entity_data->Serialize();

  flatbuffers::FlatBufferBuilder builder;
  FinishActionsModelBuffer(builder,
                           ActionsModel::Pack(builder, actions_model.get()));
  std::unique_ptr<ActionsSuggestions> actions_suggestions =
      ActionsSuggestions::FromUnownedBuffer(
          reinterpret_cast<const uint8_t*>(builder.GetBufferPointer()),
          builder.GetSize(), unilib_.get());

  const ActionsSuggestionsResponse response =
      actions_suggestions->SuggestActions(
          {{{/*user_id=*/1, "hello there", /*reference_time_ms_utc=*/0,
             /*reference_timezone=*/"Europe/Zurich",
             /*annotations=*/{}, /*locales=*/"en"}}});
  EXPECT_GE(response.actions.size(), 1);
  EXPECT_EQ(response.actions[0].response_text, "General Kenobi!");

  // Check entity data.
  const flatbuffers::Table* entity =
      flatbuffers::GetAnyRoot(reinterpret_cast<const unsigned char*>(
          response.actions[0].serialized_entity_data.data()));
  EXPECT_EQ(entity->GetPointer<const flatbuffers::String*>(/*field=*/4)->str(),
            "hello there");
  EXPECT_EQ(entity->GetPointer<const flatbuffers::String*>(/*field=*/6)->str(),
            "there");
  EXPECT_EQ(entity->GetPointer<const flatbuffers::String*>(/*field=*/8)->str(),
            "Kenobi");
}

TEST_F(ActionsSuggestionsTest, CreateActionsFromRulesWithNormalization) {
  const std::string actions_model_string =
      ReadFile(GetModelPath() + kModelFileName);
  std::unique_ptr<ActionsModelT> actions_model =
      UnPackActionsModel(actions_model_string.c_str());
  ASSERT_TRUE(DecompressActionsModel(actions_model.get()));

  actions_model->rules.reset(new RulesModelT());
  actions_model->rules->regex_rule.emplace_back(new RulesModel_::RegexRuleT);
  RulesModel_::RegexRuleT* rule = actions_model->rules->regex_rule.back().get();
  rule->pattern = "^(?i:hello\\sthere)$";
  rule->actions.emplace_back(new RulesModel_::RuleActionSpecT);
  rule->actions.back()->action.reset(new ActionSuggestionSpecT);
  ActionSuggestionSpecT* action = rule->actions.back()->action.get();
  action->type = "text_reply";
  action->response_text = "General Kenobi!";
  action->score = 1.0f;
  action->priority_score = 1.0f;

  // Set capturing groups for entity data.
  rule->actions.back()->capturing_group.emplace_back(
      new RulesModel_::RuleActionSpec_::RuleCapturingGroupT);
  RulesModel_::RuleActionSpec_::RuleCapturingGroupT* greeting_group =
      rule->actions.back()->capturing_group.back().get();
  greeting_group->group_id = 0;
  greeting_group->entity_field.reset(new FlatbufferFieldPathT);
  greeting_group->entity_field->field.emplace_back(new FlatbufferFieldT);
  greeting_group->entity_field->field.back()->field_name = "greeting";
  greeting_group->normalization_options.reset(new NormalizationOptionsT);
  greeting_group->normalization_options->codepointwise_normalization =
      NormalizationOptions_::CodepointwiseNormalizationOp_DROP_WHITESPACE |
      NormalizationOptions_::CodepointwiseNormalizationOp_UPPERCASE;

  // Set test entity data schema.
  SetTestEntityDataSchema(actions_model.get());

  flatbuffers::FlatBufferBuilder builder;
  FinishActionsModelBuffer(builder,
                           ActionsModel::Pack(builder, actions_model.get()));
  std::unique_ptr<ActionsSuggestions> actions_suggestions =
      ActionsSuggestions::FromUnownedBuffer(
          reinterpret_cast<const uint8_t*>(builder.GetBufferPointer()),
          builder.GetSize(), unilib_.get());

  const ActionsSuggestionsResponse response =
      actions_suggestions->SuggestActions(
          {{{/*user_id=*/1, "hello there", /*reference_time_ms_utc=*/0,
             /*reference_timezone=*/"Europe/Zurich",
             /*annotations=*/{}, /*locales=*/"en"}}});
  EXPECT_GE(response.actions.size(), 1);
  EXPECT_EQ(response.actions[0].response_text, "General Kenobi!");

  // Check entity data.
  const flatbuffers::Table* entity =
      flatbuffers::GetAnyRoot(reinterpret_cast<const unsigned char*>(
          response.actions[0].serialized_entity_data.data()));
  EXPECT_EQ(entity->GetPointer<const flatbuffers::String*>(/*field=*/4)->str(),
            "HELLOTHERE");
}

TEST_F(ActionsSuggestionsTest, CreatesTextRepliesFromRules) {
  const std::string actions_model_string =
      ReadFile(GetModelPath() + kModelFileName);
  std::unique_ptr<ActionsModelT> actions_model =
      UnPackActionsModel(actions_model_string.c_str());
  ASSERT_TRUE(DecompressActionsModel(actions_model.get()));

  actions_model->rules.reset(new RulesModelT());
  actions_model->rules->regex_rule.emplace_back(new RulesModel_::RegexRuleT);
  RulesModel_::RegexRuleT* rule = actions_model->rules->regex_rule.back().get();
  rule->pattern = "(?i:reply (stop|quit|end) (?:to|for) )";
  rule->actions.emplace_back(new RulesModel_::RuleActionSpecT);

  // Set capturing groups for entity data.
  rule->actions.back()->capturing_group.emplace_back(
      new RulesModel_::RuleActionSpec_::RuleCapturingGroupT);
  RulesModel_::RuleActionSpec_::RuleCapturingGroupT* code_group =
      rule->actions.back()->capturing_group.back().get();
  code_group->group_id = 1;
  code_group->text_reply.reset(new ActionSuggestionSpecT);
  code_group->text_reply->score = 1.0f;
  code_group->text_reply->priority_score = 1.0f;
  code_group->normalization_options.reset(new NormalizationOptionsT);
  code_group->normalization_options->codepointwise_normalization =
      NormalizationOptions_::CodepointwiseNormalizationOp_LOWERCASE;

  flatbuffers::FlatBufferBuilder builder;
  FinishActionsModelBuffer(builder,
                           ActionsModel::Pack(builder, actions_model.get()));
  std::unique_ptr<ActionsSuggestions> actions_suggestions =
      ActionsSuggestions::FromUnownedBuffer(
          reinterpret_cast<const uint8_t*>(builder.GetBufferPointer()),
          builder.GetSize(), unilib_.get());

  const ActionsSuggestionsResponse response =
      actions_suggestions->SuggestActions(
          {{{/*user_id=*/1,
             "visit test.com or reply STOP to cancel your subscription",
             /*reference_time_ms_utc=*/0,
             /*reference_timezone=*/"Europe/Zurich",
             /*annotations=*/{}, /*locales=*/"en"}}});
  EXPECT_GE(response.actions.size(), 1);
  EXPECT_EQ(response.actions[0].response_text, "stop");
}

TEST_F(ActionsSuggestionsTest, CreatesActionsFromGrammarRules) {
  const std::string actions_model_string =
      ReadFile(GetModelPath() + kModelFileName);
  std::unique_ptr<ActionsModelT> actions_model =
      UnPackActionsModel(actions_model_string.c_str());
  ASSERT_TRUE(DecompressActionsModel(actions_model.get()));

  actions_model->rules->grammar_rules.reset(new RulesModel_::GrammarRulesT);

  // Set tokenizer options.
  RulesModel_::GrammarRulesT* action_grammar_rules =
      actions_model->rules->grammar_rules.get();
  action_grammar_rules->tokenizer_options.reset(new ActionsTokenizerOptionsT);
  action_grammar_rules->tokenizer_options->type = TokenizationType_ICU;
  action_grammar_rules->tokenizer_options->icu_preserve_whitespace_tokens =
      false;

  // Setup test rules.
  action_grammar_rules->rules.reset(new grammar::RulesSetT);
  grammar::LocaleShardMap locale_shard_map =
      grammar::LocaleShardMap::CreateLocaleShardMap({""});
  grammar::Rules rules(locale_shard_map);
  rules.Add(
      "<knock>", {"<^>", "ventura", "!?", "<$>"},
      /*callback=*/
      static_cast<grammar::CallbackId>(grammar::DefaultCallback::kRootRule),
      /*callback_param=*/0);
  rules.Finalize().Serialize(/*include_debug_information=*/false,
                             action_grammar_rules->rules.get());
  action_grammar_rules->actions.emplace_back(new RulesModel_::RuleActionSpecT);
  RulesModel_::RuleActionSpecT* actions_spec =
      action_grammar_rules->actions.back().get();
  actions_spec->action.reset(new ActionSuggestionSpecT);
  actions_spec->action->response_text = "Yes, Satan?";
  actions_spec->action->priority_score = 1.0;
  actions_spec->action->score = 1.0;
  actions_spec->action->type = "text_reply";
  action_grammar_rules->rule_match.emplace_back(
      new RulesModel_::GrammarRules_::RuleMatchT);
  action_grammar_rules->rule_match.back()->action_id.push_back(0);

  flatbuffers::FlatBufferBuilder builder;
  FinishActionsModelBuffer(builder,
                           ActionsModel::Pack(builder, actions_model.get()));
  std::unique_ptr<ActionsSuggestions> actions_suggestions =
      ActionsSuggestions::FromUnownedBuffer(
          reinterpret_cast<const uint8_t*>(builder.GetBufferPointer()),
          builder.GetSize(), unilib_.get());

  const ActionsSuggestionsResponse response =
      actions_suggestions->SuggestActions(
          {{{/*user_id=*/1, "Ventura!",
             /*reference_time_ms_utc=*/0,
             /*reference_timezone=*/"Europe/Zurich",
             /*annotations=*/{}, /*locales=*/"en"}}});

  EXPECT_THAT(response.actions, ElementsAre(IsSmartReply("Yes, Satan?")));
}

#if defined(TC3_UNILIB_ICU) && !defined(TEST_NO_DATETIME)
TEST_F(ActionsSuggestionsTest, CreatesActionsWithAnnotationsFromGrammarRules) {
  std::unique_ptr<Annotator> annotator =
      Annotator::FromPath(GetModelPath() + "en.fb", unilib_.get());
  const std::string actions_model_string =
      ReadFile(GetModelPath() + kModelFileName);
  std::unique_ptr<ActionsModelT> actions_model =
      UnPackActionsModel(actions_model_string.c_str());
  ASSERT_TRUE(DecompressActionsModel(actions_model.get()));

  actions_model->rules->grammar_rules.reset(new RulesModel_::GrammarRulesT);

  // Set tokenizer options.
  RulesModel_::GrammarRulesT* action_grammar_rules =
      actions_model->rules->grammar_rules.get();
  action_grammar_rules->tokenizer_options.reset(new ActionsTokenizerOptionsT);
  action_grammar_rules->tokenizer_options->type = TokenizationType_ICU;
  action_grammar_rules->tokenizer_options->icu_preserve_whitespace_tokens =
      false;

  // Setup test rules.
  action_grammar_rules->rules.reset(new grammar::RulesSetT);
  grammar::LocaleShardMap locale_shard_map =
      grammar::LocaleShardMap::CreateLocaleShardMap({""});
  grammar::Rules rules(locale_shard_map);
  rules.Add(
      "<event>", {"it", "is", "at", "<time>"},
      /*callback=*/
      static_cast<grammar::CallbackId>(grammar::DefaultCallback::kRootRule),
      /*callback_param=*/0);
  rules.BindAnnotation("<time>", "time");
  rules.AddAnnotation("datetime");
  rules.Finalize().Serialize(/*include_debug_information=*/false,
                             action_grammar_rules->rules.get());
  action_grammar_rules->actions.emplace_back(new RulesModel_::RuleActionSpecT);
  RulesModel_::RuleActionSpecT* actions_spec =
      action_grammar_rules->actions.back().get();
  actions_spec->action.reset(new ActionSuggestionSpecT);
  actions_spec->action->priority_score = 1.0;
  actions_spec->action->score = 1.0;
  actions_spec->action->type = "create_event";
  action_grammar_rules->rule_match.emplace_back(
      new RulesModel_::GrammarRules_::RuleMatchT);
  action_grammar_rules->rule_match.back()->action_id.push_back(0);

  flatbuffers::FlatBufferBuilder builder;
  FinishActionsModelBuffer(builder,
                           ActionsModel::Pack(builder, actions_model.get()));
  std::unique_ptr<ActionsSuggestions> actions_suggestions =
      ActionsSuggestions::FromUnownedBuffer(
          reinterpret_cast<const uint8_t*>(builder.GetBufferPointer()),
          builder.GetSize(), unilib_.get());

  const ActionsSuggestionsResponse response =
      actions_suggestions->SuggestActions(
          {{{/*user_id=*/1, "it is at 10:30",
             /*reference_time_ms_utc=*/0,
             /*reference_timezone=*/"Europe/Zurich",
             /*annotations=*/{}, /*locales=*/"en"}}},
          annotator.get());

  EXPECT_THAT(response.actions, ElementsAre(IsActionOfType("create_event")));
}
#endif

TEST_F(ActionsSuggestionsTest, DeduplicateActions) {
  std::unique_ptr<ActionsSuggestions> actions_suggestions =
      LoadTestModel(kModelFileName);
  ActionsSuggestionsResponse response = actions_suggestions->SuggestActions(
      {{{/*user_id=*/1, "Where are you?", /*reference_time_ms_utc=*/0,
         /*reference_timezone=*/"Europe/Zurich",
         /*annotations=*/{}, /*locales=*/"en"}}});

  // Check that the location sharing model triggered.
  bool has_location_sharing_action = false;
  for (const ActionSuggestion& action : response.actions) {
    if (action.type == ActionsSuggestionsTypes::ShareLocation()) {
      has_location_sharing_action = true;
      break;
    }
  }
  EXPECT_TRUE(has_location_sharing_action);
  const int num_actions = response.actions.size();

  // Add custom rule for location sharing.
  const std::string actions_model_string =
      ReadFile(GetModelPath() + kModelFileName);
  std::unique_ptr<ActionsModelT> actions_model =
      UnPackActionsModel(actions_model_string.c_str());
  ASSERT_TRUE(DecompressActionsModel(actions_model.get()));

  actions_model->rules.reset(new RulesModelT());
  actions_model->rules->regex_rule.emplace_back(new RulesModel_::RegexRuleT);
  actions_model->rules->regex_rule.back()->pattern =
      "^(?i:where are you[.?]?)$";
  actions_model->rules->regex_rule.back()->actions.emplace_back(
      new RulesModel_::RuleActionSpecT);
  actions_model->rules->regex_rule.back()->actions.back()->action.reset(
      new ActionSuggestionSpecT);
  ActionSuggestionSpecT* action =
      actions_model->rules->regex_rule.back()->actions.back()->action.get();
  action->score = 1.0f;
  action->type = ActionsSuggestionsTypes::ShareLocation();

  flatbuffers::FlatBufferBuilder builder;
  FinishActionsModelBuffer(builder,
                           ActionsModel::Pack(builder, actions_model.get()));
  actions_suggestions = ActionsSuggestions::FromUnownedBuffer(
      reinterpret_cast<const uint8_t*>(builder.GetBufferPointer()),
      builder.GetSize(), unilib_.get());

  response = actions_suggestions->SuggestActions(
      {{{/*user_id=*/1, "Where are you?", /*reference_time_ms_utc=*/0,
         /*reference_timezone=*/"Europe/Zurich",
         /*annotations=*/{}, /*locales=*/"en"}}});
  EXPECT_THAT(response.actions, SizeIs(num_actions));
}

TEST_F(ActionsSuggestionsTest, DeduplicateConflictingActions) {
  std::unique_ptr<ActionsSuggestions> actions_suggestions =
      LoadTestModel(kModelFileName);
  AnnotatedSpan annotation;
  annotation.span = {7, 11};
  annotation.classification = {
      ClassificationResult(Collections::Flight(), 1.0)};
  ActionsSuggestionsResponse response = actions_suggestions->SuggestActions(
      {{{/*user_id=*/1, "I'm on LX38",
         /*reference_time_ms_utc=*/0,
         /*reference_timezone=*/"Europe/Zurich",
         /*annotations=*/{annotation},
         /*locales=*/"en"}}});

  // Check that the phone actions are present.
  EXPECT_GE(response.actions.size(), 1);
  EXPECT_EQ(response.actions[0].type, "track_flight");

  // Add custom rule.
  const std::string actions_model_string =
      ReadFile(GetModelPath() + kModelFileName);
  std::unique_ptr<ActionsModelT> actions_model =
      UnPackActionsModel(actions_model_string.c_str());
  ASSERT_TRUE(DecompressActionsModel(actions_model.get()));

  actions_model->rules.reset(new RulesModelT());
  actions_model->rules->regex_rule.emplace_back(new RulesModel_::RegexRuleT);
  RulesModel_::RegexRuleT* rule = actions_model->rules->regex_rule.back().get();
  rule->pattern = "^(?i:I'm on ([a-z0-9]+))$";
  rule->actions.emplace_back(new RulesModel_::RuleActionSpecT);
  rule->actions.back()->action.reset(new ActionSuggestionSpecT);
  ActionSuggestionSpecT* action = rule->actions.back()->action.get();
  action->score = 1.0f;
  action->priority_score = 2.0f;
  action->type = "test_code";
  rule->actions.back()->capturing_group.emplace_back(
      new RulesModel_::RuleActionSpec_::RuleCapturingGroupT);
  RulesModel_::RuleActionSpec_::RuleCapturingGroupT* code_group =
      rule->actions.back()->capturing_group.back().get();
  code_group->group_id = 1;
  code_group->annotation_name = "code";
  code_group->annotation_type = "code";

  flatbuffers::FlatBufferBuilder builder;
  FinishActionsModelBuffer(builder,
                           ActionsModel::Pack(builder, actions_model.get()));
  actions_suggestions = ActionsSuggestions::FromUnownedBuffer(
      reinterpret_cast<const uint8_t*>(builder.GetBufferPointer()),
      builder.GetSize(), unilib_.get());

  response = actions_suggestions->SuggestActions(
      {{{/*user_id=*/1, "I'm on LX38",
         /*reference_time_ms_utc=*/0,
         /*reference_timezone=*/"Europe/Zurich",
         /*annotations=*/{annotation},
         /*locales=*/"en"}}});
  EXPECT_GE(response.actions.size(), 1);
  EXPECT_EQ(response.actions[0].type, "test_code");
}
#endif

TEST_F(ActionsSuggestionsTest, RanksActions) {
  std::unique_ptr<ActionsSuggestions> actions_suggestions =
      LoadTestModel(kModelFileName);
  std::vector<AnnotatedSpan> annotations(2);
  annotations[0].span = {11, 15};
  annotations[0].classification = {ClassificationResult("address", 1.0)};
  annotations[1].span = {19, 23};
  annotations[1].classification = {ClassificationResult("address", 2.0)};
  const ActionsSuggestionsResponse response =
      actions_suggestions->SuggestActions(
          {{{/*user_id=*/1, "are you at home or work?",
             /*reference_time_ms_utc=*/0,
             /*reference_timezone=*/"Europe/Zurich",
             /*annotations=*/annotations,
             /*locales=*/"en"}}});
  EXPECT_GE(response.actions.size(), 2);
  EXPECT_EQ(response.actions[0].type, "view_map");
  EXPECT_EQ(response.actions[0].score, 2.0);
  EXPECT_EQ(response.actions[1].type, "view_map");
  EXPECT_EQ(response.actions[1].score, 1.0);
}

TEST_F(ActionsSuggestionsTest, VisitActionsModel) {
  EXPECT_TRUE(VisitActionsModel<bool>(GetModelPath() + kModelFileName,
                                      [](const ActionsModel* model) {
                                        if (model == nullptr) {
                                          return false;
                                        }
                                        return true;
                                      }));
  EXPECT_FALSE(VisitActionsModel<bool>(GetModelPath() + "non_existing_model.fb",
                                       [](const ActionsModel* model) {
                                         if (model == nullptr) {
                                           return false;
                                         }
                                         return true;
                                       }));
}

TEST_F(ActionsSuggestionsTest, SuggestsActionsWithHashGramModel) {
  std::unique_ptr<ActionsSuggestions> actions_suggestions =
      LoadHashGramTestModel();
  ASSERT_TRUE(actions_suggestions != nullptr);
  {
    const ActionsSuggestionsResponse response =
        actions_suggestions->SuggestActions(
            {{{/*user_id=*/1, "hello",
               /*reference_time_ms_utc=*/0,
               /*reference_timezone=*/"Europe/Zurich",
               /*annotations=*/{},
               /*locales=*/"en"}}});
    EXPECT_THAT(response.actions, testing::IsEmpty());
  }
  {
    const ActionsSuggestionsResponse response =
        actions_suggestions->SuggestActions(
            {{{/*user_id=*/1, "where are you",
               /*reference_time_ms_utc=*/0,
               /*reference_timezone=*/"Europe/Zurich",
               /*annotations=*/{},
               /*locales=*/"en"}}});
    EXPECT_THAT(
        response.actions,
        ElementsAre(testing::Field(&ActionSuggestion::type, "share_location")));
  }
  {
    const ActionsSuggestionsResponse response =
        actions_suggestions->SuggestActions(
            {{{/*user_id=*/1, "do you know johns number",
               /*reference_time_ms_utc=*/0,
               /*reference_timezone=*/"Europe/Zurich",
               /*annotations=*/{},
               /*locales=*/"en"}}});
    EXPECT_THAT(
        response.actions,
        ElementsAre(testing::Field(&ActionSuggestion::type, "share_contact")));
  }
}

// Test class to expose token embedding methods for testing.
class TestingMessageEmbedder : private ActionsSuggestions {
 public:
  explicit TestingMessageEmbedder(const ActionsModel* model);

  using ActionsSuggestions::EmbedAndFlattenTokens;
  using ActionsSuggestions::EmbedTokensPerMessage;

 protected:
  // EmbeddingExecutor that always returns features based on
  // the id of the sparse features.
  class FakeEmbeddingExecutor : public EmbeddingExecutor {
   public:
    bool AddEmbedding(const TensorView<int>& sparse_features, float* dest,
                      const int dest_size) const override {
      TC3_CHECK_GE(dest_size, 1);
      EXPECT_EQ(sparse_features.size(), 1);
      dest[0] = sparse_features.data()[0];
      return true;
    }
  };

  std::unique_ptr<UniLib> unilib_;
};

TestingMessageEmbedder::TestingMessageEmbedder(const ActionsModel* model)
    : unilib_(CreateUniLibForTesting()) {
  model_ = model;
  const ActionsTokenFeatureProcessorOptions* options =
      model->feature_processor_options();
  feature_processor_.reset(new ActionsFeatureProcessor(options, unilib_.get()));
  embedding_executor_.reset(new FakeEmbeddingExecutor());
  EXPECT_TRUE(
      EmbedTokenId(options->padding_token_id(), &embedded_padding_token_));
  EXPECT_TRUE(EmbedTokenId(options->start_token_id(), &embedded_start_token_));
  EXPECT_TRUE(EmbedTokenId(options->end_token_id(), &embedded_end_token_));
  token_embedding_size_ = feature_processor_->GetTokenEmbeddingSize();
  EXPECT_EQ(token_embedding_size_, 1);
}

class EmbeddingTest : public testing::Test {
 protected:
  explicit EmbeddingTest() {
    model_.feature_processor_options.reset(
        new ActionsTokenFeatureProcessorOptionsT);
    options_ = model_.feature_processor_options.get();
    options_->chargram_orders = {1};
    options_->num_buckets = 1000;
    options_->embedding_size = 1;
    options_->start_token_id = 0;
    options_->end_token_id = 1;
    options_->padding_token_id = 2;
    options_->tokenizer_options.reset(new ActionsTokenizerOptionsT);
  }

  TestingMessageEmbedder CreateTestingMessageEmbedder() {
    flatbuffers::FlatBufferBuilder builder;
    FinishActionsModelBuffer(builder, ActionsModel::Pack(builder, &model_));
    buffer_ = builder.Release();
    return TestingMessageEmbedder(
        flatbuffers::GetRoot<ActionsModel>(buffer_.data()));
  }

  flatbuffers::DetachedBuffer buffer_;
  ActionsModelT model_;
  ActionsTokenFeatureProcessorOptionsT* options_;
};

TEST_F(EmbeddingTest, EmbedsTokensPerMessageWithNoBounds) {
  const TestingMessageEmbedder embedder = CreateTestingMessageEmbedder();
  std::vector<std::vector<Token>> tokens = {
      {Token("a", 0, 1), Token("b", 2, 3), Token("c", 4, 5)}};
  std::vector<float> embeddings;
  int max_num_tokens_per_message = 0;

  EXPECT_TRUE(embedder.EmbedTokensPerMessage(tokens, &embeddings,
                                             &max_num_tokens_per_message));

  EXPECT_EQ(max_num_tokens_per_message, 3);
  EXPECT_EQ(embeddings.size(), 3);
  EXPECT_THAT(embeddings[0], FloatEq(tc3farmhash::Fingerprint64("a", 1) %
                                     options_->num_buckets));
  EXPECT_THAT(embeddings[1], FloatEq(tc3farmhash::Fingerprint64("b", 1) %
                                     options_->num_buckets));
  EXPECT_THAT(embeddings[2], FloatEq(tc3farmhash::Fingerprint64("c", 1) %
                                     options_->num_buckets));
}

TEST_F(EmbeddingTest, EmbedsTokensPerMessageWithPadding) {
  options_->min_num_tokens_per_message = 5;
  const TestingMessageEmbedder embedder = CreateTestingMessageEmbedder();
  std::vector<std::vector<Token>> tokens = {
      {Token("a", 0, 1), Token("b", 2, 3), Token("c", 4, 5)}};
  std::vector<float> embeddings;
  int max_num_tokens_per_message = 0;

  EXPECT_TRUE(embedder.EmbedTokensPerMessage(tokens, &embeddings,
                                             &max_num_tokens_per_message));

  EXPECT_EQ(max_num_tokens_per_message, 5);
  EXPECT_EQ(embeddings.size(), 5);
  EXPECT_THAT(embeddings[0], FloatEq(tc3farmhash::Fingerprint64("a", 1) %
                                     options_->num_buckets));
  EXPECT_THAT(embeddings[1], FloatEq(tc3farmhash::Fingerprint64("b", 1) %
                                     options_->num_buckets));
  EXPECT_THAT(embeddings[2], FloatEq(tc3farmhash::Fingerprint64("c", 1) %
                                     options_->num_buckets));
  EXPECT_THAT(embeddings[3], FloatEq(options_->padding_token_id));
  EXPECT_THAT(embeddings[4], FloatEq(options_->padding_token_id));
}

TEST_F(EmbeddingTest, EmbedsTokensPerMessageDropsAtBeginning) {
  options_->max_num_tokens_per_message = 2;
  const TestingMessageEmbedder embedder = CreateTestingMessageEmbedder();
  std::vector<std::vector<Token>> tokens = {
      {Token("a", 0, 1), Token("b", 2, 3), Token("c", 4, 5)}};
  std::vector<float> embeddings;
  int max_num_tokens_per_message = 0;

  EXPECT_TRUE(embedder.EmbedTokensPerMessage(tokens, &embeddings,
                                             &max_num_tokens_per_message));

  EXPECT_EQ(max_num_tokens_per_message, 2);
  EXPECT_EQ(embeddings.size(), 2);
  EXPECT_THAT(embeddings[0], FloatEq(tc3farmhash::Fingerprint64("b", 1) %
                                     options_->num_buckets));
  EXPECT_THAT(embeddings[1], FloatEq(tc3farmhash::Fingerprint64("c", 1) %
                                     options_->num_buckets));
}

TEST_F(EmbeddingTest, EmbedsTokensPerMessageWithMultipleMessagesNoBounds) {
  const TestingMessageEmbedder embedder = CreateTestingMessageEmbedder();
  std::vector<std::vector<Token>> tokens = {
      {Token("a", 0, 1), Token("b", 2, 3), Token("c", 4, 5)},
      {Token("d", 0, 1), Token("e", 2, 3)}};
  std::vector<float> embeddings;
  int max_num_tokens_per_message = 0;

  EXPECT_TRUE(embedder.EmbedTokensPerMessage(tokens, &embeddings,
                                             &max_num_tokens_per_message));

  EXPECT_EQ(max_num_tokens_per_message, 3);
  EXPECT_THAT(embeddings[0], FloatEq(tc3farmhash::Fingerprint64("a", 1) %
                                     options_->num_buckets));
  EXPECT_THAT(embeddings[1], FloatEq(tc3farmhash::Fingerprint64("b", 1) %
                                     options_->num_buckets));
  EXPECT_THAT(embeddings[2], FloatEq(tc3farmhash::Fingerprint64("c", 1) %
                                     options_->num_buckets));
  EXPECT_THAT(embeddings[3], FloatEq(tc3farmhash::Fingerprint64("d", 1) %
                                     options_->num_buckets));
  EXPECT_THAT(embeddings[4], FloatEq(tc3farmhash::Fingerprint64("e", 1) %
                                     options_->num_buckets));
  EXPECT_THAT(embeddings[5], FloatEq(options_->padding_token_id));
}

TEST_F(EmbeddingTest, EmbedsFlattenedTokensWithNoBounds) {
  const TestingMessageEmbedder embedder = CreateTestingMessageEmbedder();
  std::vector<std::vector<Token>> tokens = {
      {Token("a", 0, 1), Token("b", 2, 3), Token("c", 4, 5)}};
  std::vector<float> embeddings;
  int total_token_count = 0;

  EXPECT_TRUE(
      embedder.EmbedAndFlattenTokens(tokens, &embeddings, &total_token_count));

  EXPECT_EQ(total_token_count, 5);
  EXPECT_EQ(embeddings.size(), 5);
  EXPECT_THAT(embeddings[0], FloatEq(options_->start_token_id));
  EXPECT_THAT(embeddings[1], FloatEq(tc3farmhash::Fingerprint64("a", 1) %
                                     options_->num_buckets));
  EXPECT_THAT(embeddings[2], FloatEq(tc3farmhash::Fingerprint64("b", 1) %
                                     options_->num_buckets));
  EXPECT_THAT(embeddings[3], FloatEq(tc3farmhash::Fingerprint64("c", 1) %
                                     options_->num_buckets));
  EXPECT_THAT(embeddings[4], FloatEq(options_->end_token_id));
}

TEST_F(EmbeddingTest, EmbedsFlattenedTokensWithPadding) {
  options_->min_num_total_tokens = 7;
  const TestingMessageEmbedder embedder = CreateTestingMessageEmbedder();
  std::vector<std::vector<Token>> tokens = {
      {Token("a", 0, 1), Token("b", 2, 3), Token("c", 4, 5)}};
  std::vector<float> embeddings;
  int total_token_count = 0;

  EXPECT_TRUE(
      embedder.EmbedAndFlattenTokens(tokens, &embeddings, &total_token_count));

  EXPECT_EQ(total_token_count, 7);
  EXPECT_EQ(embeddings.size(), 7);
  EXPECT_THAT(embeddings[0], FloatEq(options_->start_token_id));
  EXPECT_THAT(embeddings[1], FloatEq(tc3farmhash::Fingerprint64("a", 1) %
                                     options_->num_buckets));
  EXPECT_THAT(embeddings[2], FloatEq(tc3farmhash::Fingerprint64("b", 1) %
                                     options_->num_buckets));
  EXPECT_THAT(embeddings[3], FloatEq(tc3farmhash::Fingerprint64("c", 1) %
                                     options_->num_buckets));
  EXPECT_THAT(embeddings[4], FloatEq(options_->end_token_id));
  EXPECT_THAT(embeddings[5], FloatEq(options_->padding_token_id));
  EXPECT_THAT(embeddings[6], FloatEq(options_->padding_token_id));
}

TEST_F(EmbeddingTest, EmbedsFlattenedTokensDropsAtBeginning) {
  options_->max_num_total_tokens = 3;
  const TestingMessageEmbedder embedder = CreateTestingMessageEmbedder();
  std::vector<std::vector<Token>> tokens = {
      {Token("a", 0, 1), Token("b", 2, 3), Token("c", 4, 5)}};
  std::vector<float> embeddings;
  int total_token_count = 0;

  EXPECT_TRUE(
      embedder.EmbedAndFlattenTokens(tokens, &embeddings, &total_token_count));

  EXPECT_EQ(total_token_count, 3);
  EXPECT_EQ(embeddings.size(), 3);
  EXPECT_THAT(embeddings[0], FloatEq(tc3farmhash::Fingerprint64("b", 1) %
                                     options_->num_buckets));
  EXPECT_THAT(embeddings[1], FloatEq(tc3farmhash::Fingerprint64("c", 1) %
                                     options_->num_buckets));
  EXPECT_THAT(embeddings[2], FloatEq(options_->end_token_id));
}

TEST_F(EmbeddingTest, EmbedsFlattenedTokensWithMultipleMessagesNoBounds) {
  const TestingMessageEmbedder embedder = CreateTestingMessageEmbedder();
  std::vector<std::vector<Token>> tokens = {
      {Token("a", 0, 1), Token("b", 2, 3), Token("c", 4, 5)},
      {Token("d", 0, 1), Token("e", 2, 3)}};
  std::vector<float> embeddings;
  int total_token_count = 0;

  EXPECT_TRUE(
      embedder.EmbedAndFlattenTokens(tokens, &embeddings, &total_token_count));

  EXPECT_EQ(total_token_count, 9);
  EXPECT_EQ(embeddings.size(), 9);
  EXPECT_THAT(embeddings[0], FloatEq(options_->start_token_id));
  EXPECT_THAT(embeddings[1], FloatEq(tc3farmhash::Fingerprint64("a", 1) %
                                     options_->num_buckets));
  EXPECT_THAT(embeddings[2], FloatEq(tc3farmhash::Fingerprint64("b", 1) %
                                     options_->num_buckets));
  EXPECT_THAT(embeddings[3], FloatEq(tc3farmhash::Fingerprint64("c", 1) %
                                     options_->num_buckets));
  EXPECT_THAT(embeddings[4], FloatEq(options_->end_token_id));
  EXPECT_THAT(embeddings[5], FloatEq(options_->start_token_id));
  EXPECT_THAT(embeddings[6], FloatEq(tc3farmhash::Fingerprint64("d", 1) %
                                     options_->num_buckets));
  EXPECT_THAT(embeddings[7], FloatEq(tc3farmhash::Fingerprint64("e", 1) %
                                     options_->num_buckets));
  EXPECT_THAT(embeddings[8], FloatEq(options_->end_token_id));
}

TEST_F(EmbeddingTest,
       EmbedsFlattenedTokensWithMultipleMessagesDropsAtBeginning) {
  options_->max_num_total_tokens = 7;
  const TestingMessageEmbedder embedder = CreateTestingMessageEmbedder();
  std::vector<std::vector<Token>> tokens = {
      {Token("a", 0, 1), Token("b", 2, 3), Token("c", 4, 5)},
      {Token("d", 0, 1), Token("e", 2, 3), Token("f", 4, 5)}};
  std::vector<float> embeddings;
  int total_token_count = 0;

  EXPECT_TRUE(
      embedder.EmbedAndFlattenTokens(tokens, &embeddings, &total_token_count));

  EXPECT_EQ(total_token_count, 7);
  EXPECT_EQ(embeddings.size(), 7);
  EXPECT_THAT(embeddings[0], FloatEq(tc3farmhash::Fingerprint64("c", 1) %
                                     options_->num_buckets));
  EXPECT_THAT(embeddings[1], FloatEq(options_->end_token_id));
  EXPECT_THAT(embeddings[2], FloatEq(options_->start_token_id));
  EXPECT_THAT(embeddings[3], FloatEq(tc3farmhash::Fingerprint64("d", 1) %
                                     options_->num_buckets));
  EXPECT_THAT(embeddings[4], FloatEq(tc3farmhash::Fingerprint64("e", 1) %
                                     options_->num_buckets));
  EXPECT_THAT(embeddings[5], FloatEq(tc3farmhash::Fingerprint64("f", 1) %
                                     options_->num_buckets));
  EXPECT_THAT(embeddings[6], FloatEq(options_->end_token_id));
}

TEST_F(ActionsSuggestionsTest, MultiTaskSuggestActionsDefault) {
  std::unique_ptr<ActionsSuggestions> actions_suggestions =
      LoadMultiTaskTestModel();
  const ActionsSuggestionsResponse response =
      actions_suggestions->SuggestActions(
          {{{/*user_id=*/1, "Where are you?", /*reference_time_ms_utc=*/0,
             /*reference_timezone=*/"Europe/Zurich",
             /*annotations=*/{}, /*locales=*/"en"}}});
  EXPECT_EQ(response.actions.size(),
            11 /* 8 binary classification + 3 smart replies*/);
}

const float kDisableThresholdVal = 2.0;

constexpr char kSpamThreshold[] = "spam_confidence_threshold";
constexpr char kLocationThreshold[] = "location_confidence_threshold";
constexpr char kPhoneThreshold[] = "phone_confidence_threshold";
constexpr char kWeatherThreshold[] = "weather_confidence_threshold";
constexpr char kRestaurantsThreshold[] = "restaurants_confidence_threshold";
constexpr char kMoviesThreshold[] = "movies_confidence_threshold";
constexpr char kTtrThreshold[] = "time_to_reply_binary_threshold";
constexpr char kReminderThreshold[] = "reminder_intent_confidence_threshold";
constexpr char kDiversificationParm[] = "diversification_distance_threshold";
constexpr char kEmpiricalProbFactor[] = "empirical_probability_factor";

ActionSuggestionOptions GetOptionsToDisableAllClassification() {
  ActionSuggestionOptions options;
  // Disable all classification heads.
  options.model_parameters.insert(
      {kSpamThreshold, libtextclassifier3::Variant(kDisableThresholdVal)});
  options.model_parameters.insert(
      {kLocationThreshold, libtextclassifier3::Variant(kDisableThresholdVal)});
  options.model_parameters.insert(
      {kPhoneThreshold, libtextclassifier3::Variant(kDisableThresholdVal)});
  options.model_parameters.insert(
      {kWeatherThreshold, libtextclassifier3::Variant(kDisableThresholdVal)});
  options.model_parameters.insert(
      {kRestaurantsThreshold,
       libtextclassifier3::Variant(kDisableThresholdVal)});
  options.model_parameters.insert(
      {kMoviesThreshold, libtextclassifier3::Variant(kDisableThresholdVal)});
  options.model_parameters.insert(
      {kTtrThreshold, libtextclassifier3::Variant(kDisableThresholdVal)});
  options.model_parameters.insert(
      {kReminderThreshold, libtextclassifier3::Variant(kDisableThresholdVal)});
  return options;
}

TEST_F(ActionsSuggestionsTest, MultiTaskSuggestActionsSmartReplyOnly) {
  std::unique_ptr<ActionsSuggestions> actions_suggestions =
      LoadMultiTaskTestModel();
  const ActionSuggestionOptions options =
      GetOptionsToDisableAllClassification();
  const ActionsSuggestionsResponse response =
      actions_suggestions->SuggestActions(
          {{{/*user_id=*/1, "Where are you?", /*reference_time_ms_utc=*/0,
             /*reference_timezone=*/"Europe/Zurich",
             /*annotations=*/{}, /*locales=*/"en"}}},
          /*annotator=*/nullptr, options);
  EXPECT_THAT(response.actions,
              ElementsAre(IsSmartReply("Here"), IsSmartReply("I'm here"),
                          IsSmartReply("I'm home")));
  EXPECT_EQ(response.actions.size(), 3 /*3 smart replies*/);
}

const int kUserProfileSize = 1000;
constexpr char kUserProfileTokenIndex[] = "user_profile_token_index";
constexpr char kUserProfileTokenWeight[] = "user_profile_token_weight";

ActionSuggestionOptions GetOptionsForSmartReplyP13nModel() {
  ActionSuggestionOptions options;
  const std::vector<int> user_profile_token_indexes(kUserProfileSize, 1);
  const std::vector<float> user_profile_token_weights(kUserProfileSize, 0.1f);
  options.model_parameters.insert(
      {kUserProfileTokenIndex,
       libtextclassifier3::Variant(user_profile_token_indexes)});
  options.model_parameters.insert(
      {kUserProfileTokenWeight,
       libtextclassifier3::Variant(user_profile_token_weights)});
  return options;
}

TEST_F(ActionsSuggestionsTest, MultiTaskSuggestActionsSmartReplyP13n) {
  std::unique_ptr<ActionsSuggestions> actions_suggestions =
      LoadMultiTaskSrP13nTestModel();
  const ActionSuggestionOptions options = GetOptionsForSmartReplyP13nModel();
  const ActionsSuggestionsResponse response =
      actions_suggestions->SuggestActions(
          {{{/*user_id=*/1, "How are you?", /*reference_time_ms_utc=*/0,
             /*reference_timezone=*/"Europe/Zurich",
             /*annotations=*/{}, /*locales=*/"en"}}},
          /*annotator=*/nullptr, options);
  EXPECT_EQ(response.actions.size(), 3 /*3 smart replies*/);
}

TEST_F(ActionsSuggestionsTest,
       MultiTaskSuggestActionsDiversifiedSmartReplyAndLocation) {
  std::unique_ptr<ActionsSuggestions> actions_suggestions =
      LoadMultiTaskTestModel();
  ActionSuggestionOptions options = GetOptionsToDisableAllClassification();
  options.model_parameters[kLocationThreshold] =
      libtextclassifier3::Variant(0.35f);
  options.model_parameters.insert(
      {kDiversificationParm, libtextclassifier3::Variant(0.5f)});
  const ActionsSuggestionsResponse response =
      actions_suggestions->SuggestActions(
          {{{/*user_id=*/1, "Where are you?", /*reference_time_ms_utc=*/0,
             /*reference_timezone=*/"Europe/Zurich",
             /*annotations=*/{}, /*locales=*/"en"}}},
          /*annotator=*/nullptr, options);
  EXPECT_THAT(
      response.actions,
      ElementsAre(IsActionOfType("LOCATION_SHARE"), IsSmartReply("Here"),
                  IsSmartReply("Yes"), IsSmartReply("😟")));
  EXPECT_EQ(response.actions.size(), 4 /*1 location share + 3 smart replies*/);
}

TEST_F(ActionsSuggestionsTest,
       MultiTaskSuggestActionsEmProBoostedSmartReplyAndLocationAndReminder) {
  std::unique_ptr<ActionsSuggestions> actions_suggestions =
      LoadMultiTaskTestModel();
  ActionSuggestionOptions options = GetOptionsToDisableAllClassification();
  options.model_parameters[kLocationThreshold] =
      libtextclassifier3::Variant(0.35f);
  // reminder head always trigger since the threshold is zero.
  options.model_parameters[kReminderThreshold] =
      libtextclassifier3::Variant(0.0f);
  options.model_parameters.insert(
      {kEmpiricalProbFactor, libtextclassifier3::Variant(2.0f)});
  const ActionsSuggestionsResponse response =
      actions_suggestions->SuggestActions(
          {{{/*user_id=*/1, "Where are you?", /*reference_time_ms_utc=*/0,
             /*reference_timezone=*/"Europe/Zurich",
             /*annotations=*/{}, /*locales=*/"en"}}},
          /*annotator=*/nullptr, options);
  EXPECT_THAT(
      response.actions,
      ElementsAre(IsSmartReply("Okay"), IsActionOfType("LOCATION_SHARE"),
                  IsSmartReply("Yes"),
                  /*Different emoji than previous test*/ IsSmartReply("😊"),
                  IsActionOfType("REMINDER_INTENT")));
  EXPECT_EQ(response.actions.size(), 5 /*1 location share + 3 smart replies*/);
}

TEST_F(ActionsSuggestionsTest, SuggestsActionsFromMultiTaskSrEmojiModel) {
  std::unique_ptr<ActionsSuggestions> actions_suggestions =
      LoadTestModel(kMultiTaskSrEmojiModelFileName);

  const ActionsSuggestionsResponse response =
      actions_suggestions->SuggestActions(
          {{{/*user_id=*/1, "hello?",
             /*reference_time_ms_utc=*/0,
             /*reference_timezone=*/"Europe/Zurich",
             /*annotations=*/{},
             /*locales=*/"en"}}});
  EXPECT_EQ(response.actions.size(), 5);
  EXPECT_EQ(response.actions[0].response_text, "😁");
  EXPECT_EQ(response.actions[0].type, "text_reply");
  EXPECT_EQ(response.actions[1].response_text, "👋");
  EXPECT_EQ(response.actions[1].type, "text_reply");
  EXPECT_EQ(response.actions[2].response_text, "Yes");
  EXPECT_EQ(response.actions[2].type, "text_reply");
}

TEST_F(ActionsSuggestionsTest, MultiTaskSrEmojiModelRemovesTextHeadEmoji) {
  std::unique_ptr<ActionsSuggestions> actions_suggestions =
      LoadTestModel(kMultiTaskSrEmojiModelFileName);

  const ActionsSuggestionsResponse response =
      actions_suggestions->SuggestActions(
          {{{/*user_id=*/1, "a pleasure chatting",
             /*reference_time_ms_utc=*/0,
             /*reference_timezone=*/"Europe/Zurich",
             /*annotations=*/{},
             /*locales=*/"en"}}});
  EXPECT_EQ(response.actions.size(), 3);
  EXPECT_EQ(response.actions[0].response_text, "😁");
  EXPECT_EQ(response.actions[0].type, "text_reply");
  EXPECT_EQ(response.actions[1].response_text, "😘");
  EXPECT_EQ(response.actions[1].type, "text_reply");
  EXPECT_EQ(response.actions[2].response_text, "Okay");
  EXPECT_EQ(response.actions[2].type, "text_reply");
}

TEST_F(ActionsSuggestionsTest, MultiTaskSrEmojiModelUsesConcepts) {
  std::unique_ptr<ActionsSuggestions> actions_suggestions =
      LoadTestModel(kMultiTaskSrEmojiConceptModelFileName);

  const ActionsSuggestionsResponse response =
      actions_suggestions->SuggestActions(
          {{{/*user_id=*/1, "i am tired",
             /*reference_time_ms_utc=*/0,
             /*reference_timezone=*/"Europe/Zurich",
             /*annotations=*/{},
             /*locales=*/"en"}}});
  std::vector<std::string> sigh_emojis = {"😔", "😞"};

  EXPECT_TRUE(std::find(sigh_emojis.begin(), sigh_emojis.end(),
                        response.actions[0].response_text) !=
              sigh_emojis.end());
  EXPECT_EQ(response.actions[0].type, "emoji_reply");
}

TEST_F(ActionsSuggestionsTest, LiveRelayModel) {
  std::unique_ptr<ActionsSuggestions> actions_suggestions =
      LoadTestModel(kLiveRelayTFLiteModelFileName);
  const ActionsSuggestionsResponse response =
      actions_suggestions->SuggestActions(
          {{{/*user_id=*/1, "Hi",
             /*reference_time_ms_utc=*/0,
             /*reference_timezone=*/"Europe/Zurich",
             /*annotations=*/{},
             /*locales=*/"en"}}});
  EXPECT_EQ(response.actions.size(), 3);
  EXPECT_EQ(response.actions[0].response_text, "Hi how are you doing");
  EXPECT_EQ(response.actions[0].type, "text_reply");
  EXPECT_EQ(response.actions[1].response_text, "Hi whats up");
  EXPECT_EQ(response.actions[1].type, "text_reply");
}

TEST_F(ActionsSuggestionsTest, SuggestsActionsFromSensitiveTfLiteModel) {
  std::unique_ptr<ActionsSuggestions> actions_suggestions =
      LoadTestModel(kSensitiveTFliteModelFileName);
  const ActionsSuggestionsResponse response =
      actions_suggestions->SuggestActions(
          {{{/*user_id=*/1, "I want to kill myself",
             /*reference_time_ms_utc=*/0,
             /*reference_timezone=*/"Europe/Zurich",
             /*annotations=*/{},
             /*locales=*/"en"}}});
  EXPECT_EQ(response.actions.size(), 0);
  EXPECT_TRUE(response.is_sensitive);
  EXPECT_FALSE(response.output_filtered_low_confidence);
}

}  // namespace
}  // namespace libtextclassifier3
