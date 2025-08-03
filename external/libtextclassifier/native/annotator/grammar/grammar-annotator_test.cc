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

#include "annotator/grammar/grammar-annotator.h"

#include <memory>

#include "annotator/grammar/test-utils.h"
#include "annotator/grammar/utils.h"
#include "annotator/model_generated.h"
#include "utils/flatbuffers/flatbuffers.h"
#include "utils/flatbuffers/mutable.h"
#include "utils/grammar/utils/locale-shard-map.h"
#include "utils/grammar/utils/rules.h"
#include "utils/tokenizer.h"
#include "utils/utf8/unicodetext.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"

namespace libtextclassifier3 {
namespace {

using testing::ElementsAre;

flatbuffers::DetachedBuffer PackModel(const GrammarModelT& model) {
  flatbuffers::FlatBufferBuilder builder;
  builder.Finish(GrammarModel::Pack(builder, &model));
  return builder.Release();
}

TEST_F(GrammarAnnotatorTest, AnnotesWithGrammarRules) {
  // Create test rules.
  GrammarModelT grammar_model;
  SetTestTokenizerOptions(&grammar_model);
  grammar_model.rules.reset(new grammar::RulesSetT);
  grammar::LocaleShardMap locale_shard_map =
      grammar::LocaleShardMap::CreateLocaleShardMap({""});
  grammar::Rules rules(locale_shard_map);
  rules.Add("<carrier>", {"lx"});
  rules.Add("<carrier>", {"aa"});
  rules.Add("<flight_code>", {"<2_digits>"});
  rules.Add("<flight_code>", {"<3_digits>"});
  rules.Add("<flight_code>", {"<4_digits>"});
  rules.Add(
      "<flight>", {"<carrier>", "<flight_code>"},
      /*callback=*/
      static_cast<grammar::CallbackId>(grammar::DefaultCallback::kRootRule),
      /*callback_param=*/
      AddRuleClassificationResult("flight", ModeFlag_ALL, 1.0, &grammar_model));
  rules.Finalize().Serialize(/*include_debug_information=*/false,
                             grammar_model.rules.get());
  flatbuffers::DetachedBuffer serialized_model = PackModel(grammar_model);
  GrammarAnnotator annotator(CreateGrammarAnnotator(serialized_model));

  std::vector<AnnotatedSpan> result;
  EXPECT_TRUE(annotator.Annotate(
      {Locale::FromBCP47("en")},
      UTF8ToUnicodeText(
          "My flight: LX 38 arriving at 4pm, I'll fly back on AA2014",
          /*do_copy=*/false),
      &result));

  EXPECT_THAT(result, ElementsAre(IsAnnotatedSpan(11, 16, "flight"),
                                  IsAnnotatedSpan(51, 57, "flight")));
}

TEST_F(GrammarAnnotatorTest, HandlesAssertions) {
  // Create test rules.
  GrammarModelT grammar_model;
  SetTestTokenizerOptions(&grammar_model);
  grammar_model.rules.reset(new grammar::RulesSetT);
  grammar::LocaleShardMap locale_shard_map =
      grammar::LocaleShardMap::CreateLocaleShardMap({""});
  grammar::Rules rules(locale_shard_map);
  rules.Add("<carrier>", {"lx"});
  rules.Add("<carrier>", {"aa"});
  rules.Add("<flight_code>", {"<2_digits>"});
  rules.Add("<flight_code>", {"<3_digits>"});
  rules.Add("<flight_code>", {"<4_digits>"});

  // Flight: carrier + flight code and check right context.
  rules.Add(
      "<flight>", {"<carrier>", "<flight_code>", "<context_assertion>?"},
      /*callback=*/
      static_cast<grammar::CallbackId>(grammar::DefaultCallback::kRootRule),
      /*callback_param=*/
      AddRuleClassificationResult("flight", ModeFlag_ALL, 1.0, &grammar_model));

  // Exclude matches like: LX 38.00 etc.
  rules.AddAssertion("<context_assertion>", {".?", "<digits>"},
                     /*negative=*/true);

  rules.Finalize().Serialize(/*include_debug_information=*/false,
                             grammar_model.rules.get());
  flatbuffers::DetachedBuffer serialized_model = PackModel(grammar_model);
  GrammarAnnotator annotator(CreateGrammarAnnotator(serialized_model));

  std::vector<AnnotatedSpan> result;
  EXPECT_TRUE(annotator.Annotate(
      {Locale::FromBCP47("en")},
      UTF8ToUnicodeText("My flight: LX 38 arriving at 4pm, I'll fly back on "
                        "AA2014 on LX 38.00",
                        /*do_copy=*/false),
      &result));

  EXPECT_THAT(result, ElementsAre(IsAnnotatedSpan(11, 16, "flight"),
                                  IsAnnotatedSpan(51, 57, "flight")));
}

TEST_F(GrammarAnnotatorTest, HandlesCapturingGroups) {
  // Create test rules.
  GrammarModelT grammar_model;
  SetTestTokenizerOptions(&grammar_model);
  grammar_model.rules.reset(new grammar::RulesSetT);
  grammar::LocaleShardMap locale_shard_map =
      grammar::LocaleShardMap::CreateLocaleShardMap({""});
  grammar::Rules rules(locale_shard_map);
  rules.AddValueMapping("<low_confidence_phone>", {"<digits>"},
                        /*value=*/0);

  // Create rule result.
  const int classification_result_id =
      AddRuleClassificationResult("phone", ModeFlag_ALL, 1.0, &grammar_model);
  grammar_model.rule_classification_result[classification_result_id]
      ->capturing_group.emplace_back(new CapturingGroupT);
  grammar_model.rule_classification_result[classification_result_id]
      ->capturing_group.back()
      ->extend_selection = true;

  rules.Add(
      "<phone>", {"please", "call", "<low_confidence_phone>"},
      /*callback=*/
      static_cast<grammar::CallbackId>(grammar::DefaultCallback::kRootRule),
      /*callback_param=*/classification_result_id);

  rules.Finalize().Serialize(/*include_debug_information=*/false,
                             grammar_model.rules.get());
  flatbuffers::DetachedBuffer serialized_model = PackModel(grammar_model);
  GrammarAnnotator annotator(CreateGrammarAnnotator(serialized_model));

  std::vector<AnnotatedSpan> result;
  EXPECT_TRUE(annotator.Annotate(
      {Locale::FromBCP47("en")},
      UTF8ToUnicodeText("Please call 911 before 10 am!", /*do_copy=*/false),
      &result));
  EXPECT_THAT(result, ElementsAre(IsAnnotatedSpan(12, 15, "phone")));
}

TEST_F(GrammarAnnotatorTest, ClassifiesTextWithGrammarRules) {
  // Create test rules.
  GrammarModelT grammar_model;
  SetTestTokenizerOptions(&grammar_model);
  grammar_model.rules.reset(new grammar::RulesSetT);
  grammar::LocaleShardMap locale_shard_map =
      grammar::LocaleShardMap::CreateLocaleShardMap({""});
  grammar::Rules rules(locale_shard_map);
  rules.Add("<carrier>", {"lx"});
  rules.Add("<carrier>", {"aa"});
  rules.Add("<flight_code>", {"<2_digits>"});
  rules.Add("<flight_code>", {"<3_digits>"});
  rules.Add("<flight_code>", {"<4_digits>"});
  rules.Add(
      "<flight>", {"<carrier>", "<flight_code>"},
      /*callback=*/
      static_cast<grammar::CallbackId>(grammar::DefaultCallback::kRootRule),
      /*callback_param=*/
      AddRuleClassificationResult("flight", ModeFlag_ALL, 1.0, &grammar_model));
  rules.Finalize().Serialize(/*include_debug_information=*/false,
                             grammar_model.rules.get());
  flatbuffers::DetachedBuffer serialized_model = PackModel(grammar_model);
  GrammarAnnotator annotator(CreateGrammarAnnotator(serialized_model));

  ClassificationResult result;
  EXPECT_TRUE(annotator.ClassifyText(
      {Locale::FromBCP47("en")},
      UTF8ToUnicodeText(
          "My flight: LX 38 arriving at 4pm, I'll fly back on AA2014",
          /*do_copy=*/false),
      CodepointSpan{11, 16}, &result));
  EXPECT_THAT(result, IsClassificationResult("flight"));
}

TEST_F(GrammarAnnotatorTest, ClassifiesTextWithAssertions) {
  // Create test rules.
  GrammarModelT grammar_model;
  SetTestTokenizerOptions(&grammar_model);
  grammar_model.rules.reset(new grammar::RulesSetT);

  // Use unbounded context.
  grammar_model.context_left_num_tokens = -1;
  grammar_model.context_right_num_tokens = -1;

  grammar::LocaleShardMap locale_shard_map =
      grammar::LocaleShardMap::CreateLocaleShardMap({""});
  grammar::Rules rules(locale_shard_map);
  rules.Add("<carrier>", {"lx"});
  rules.Add("<carrier>", {"aa"});
  rules.Add("<flight_code>", {"<2_digits>"});
  rules.Add("<flight_code>", {"<3_digits>"});
  rules.Add("<flight_code>", {"<4_digits>"});
  rules.AddValueMapping("<flight_selection>", {"<carrier>", "<flight_code>"},
                        /*value=*/0);

  // Flight: carrier + flight code and check right context.
  const int classification_result_id =
      AddRuleClassificationResult("flight", ModeFlag_ALL, 1.0, &grammar_model);
  rules.Add(
      "<flight>", {"<flight_selection>", "<context_assertion>?"},
      /*callback=*/
      static_cast<grammar::CallbackId>(grammar::DefaultCallback::kRootRule),
      /*callback_param=*/
      classification_result_id);

  grammar_model.rule_classification_result[classification_result_id]
      ->capturing_group.emplace_back(new CapturingGroupT);
  grammar_model.rule_classification_result[classification_result_id]
      ->capturing_group.back()
      ->extend_selection = true;

  // Exclude matches like: LX 38.00 etc.
  rules.AddAssertion("<context_assertion>", {".?", "<digits>"},
                     /*negative=*/true);

  rules.Finalize().Serialize(/*include_debug_information=*/false,
                             grammar_model.rules.get());
  flatbuffers::DetachedBuffer serialized_model = PackModel(grammar_model);
  GrammarAnnotator annotator(CreateGrammarAnnotator(serialized_model));

  EXPECT_FALSE(annotator.ClassifyText(
      {Locale::FromBCP47("en")},
      UTF8ToUnicodeText("See LX 38.00", /*do_copy=*/false), CodepointSpan{4, 9},
      nullptr));
  EXPECT_FALSE(annotator.ClassifyText(
      {Locale::FromBCP47("en")},
      UTF8ToUnicodeText("See LX 38 00", /*do_copy=*/false), CodepointSpan{4, 9},
      nullptr));
  ClassificationResult result;
  EXPECT_TRUE(annotator.ClassifyText(
      {Locale::FromBCP47("en")},
      UTF8ToUnicodeText("See LX 38, seat 5", /*do_copy=*/false),
      CodepointSpan{4, 9}, &result));
  EXPECT_THAT(result, IsClassificationResult("flight"));
}

TEST_F(GrammarAnnotatorTest, ClassifiesTextWithContext) {
  // Create test rules.
  GrammarModelT grammar_model;
  SetTestTokenizerOptions(&grammar_model);
  grammar_model.rules.reset(new grammar::RulesSetT);

  // Max three tokens to the left ("tracking number: ...").
  grammar_model.context_left_num_tokens = 3;
  grammar_model.context_right_num_tokens = 0;

  grammar::LocaleShardMap locale_shard_map =
      grammar::LocaleShardMap::CreateLocaleShardMap({""});
  grammar::Rules rules(locale_shard_map);
  rules.Add("<tracking_number>", {"<5_digits>"});
  rules.Add("<tracking_number>", {"<6_digits>"});
  rules.Add("<tracking_number>", {"<7_digits>"});
  rules.Add("<tracking_number>", {"<8_digits>"});
  rules.Add("<tracking_number>", {"<9_digits>"});
  rules.Add("<tracking_number>", {"<10_digits>"});
  rules.AddValueMapping("<captured_tracking_number>", {"<tracking_number>"},
                        /*value=*/0);
  rules.Add("<parcel_tracking_trigger>", {"tracking", "number?", ":?"});

  const int classification_result_id = AddRuleClassificationResult(
      "parcel_tracking", ModeFlag_ALL, 1.0, &grammar_model);
  rules.Add(
      "<parcel_tracking>",
      {"<parcel_tracking_trigger>", "<captured_tracking_number>"},
      /*callback=*/
      static_cast<grammar::CallbackId>(grammar::DefaultCallback::kRootRule),
      /*callback_param=*/
      classification_result_id);

  grammar_model.rule_classification_result[classification_result_id]
      ->capturing_group.emplace_back(new CapturingGroupT);
  grammar_model.rule_classification_result[classification_result_id]
      ->capturing_group.back()
      ->extend_selection = true;

  rules.Finalize().Serialize(/*include_debug_information=*/false,
                             grammar_model.rules.get());
  flatbuffers::DetachedBuffer serialized_model = PackModel(grammar_model);
  GrammarAnnotator annotator(CreateGrammarAnnotator(serialized_model));

  ClassificationResult result;
  EXPECT_TRUE(annotator.ClassifyText(
      {Locale::FromBCP47("en")},
      UTF8ToUnicodeText("Use tracking number 012345 for live parcel tracking.",
                        /*do_copy=*/false),
      CodepointSpan{20, 26}, &result));
  EXPECT_THAT(result, IsClassificationResult("parcel_tracking"));

  EXPECT_FALSE(annotator.ClassifyText(
      {Locale::FromBCP47("en")},
      UTF8ToUnicodeText("Call phone 012345 for live parcel tracking.",
                        /*do_copy=*/false),
      CodepointSpan{11, 17}, &result));
}

TEST_F(GrammarAnnotatorTest, SuggestsTextSelection) {
  // Create test rules.
  GrammarModelT grammar_model;
  SetTestTokenizerOptions(&grammar_model);
  grammar_model.rules.reset(new grammar::RulesSetT);
  grammar::LocaleShardMap locale_shard_map =
      grammar::LocaleShardMap::CreateLocaleShardMap({""});
  grammar::Rules rules(locale_shard_map);
  rules.Add("<carrier>", {"lx"});
  rules.Add("<carrier>", {"aa"});
  rules.Add("<flight_code>", {"<2_digits>"});
  rules.Add("<flight_code>", {"<3_digits>"});
  rules.Add("<flight_code>", {"<4_digits>"});
  rules.Add(
      "<flight>", {"<carrier>", "<flight_code>"},
      /*callback=*/
      static_cast<grammar::CallbackId>(grammar::DefaultCallback::kRootRule),
      /*callback_param=*/
      AddRuleClassificationResult("flight", ModeFlag_ALL, 1.0, &grammar_model));
  rules.Finalize().Serialize(/*include_debug_information=*/false,
                             grammar_model.rules.get());
  flatbuffers::DetachedBuffer serialized_model = PackModel(grammar_model);
  GrammarAnnotator annotator(CreateGrammarAnnotator(serialized_model));

  AnnotatedSpan selection;
  EXPECT_TRUE(annotator.SuggestSelection(
      {Locale::FromBCP47("en")},
      UTF8ToUnicodeText(
          "My flight: LX 38 arriving at 4pm, I'll fly back on AA2014",
          /*do_copy=*/false),
      /*selection=*/CodepointSpan{14, 15}, &selection));
  EXPECT_THAT(selection, IsAnnotatedSpan(11, 16, "flight"));
}

TEST_F(GrammarAnnotatorTest, SetsFixedEntityData) {
  // Create test rules.
  GrammarModelT grammar_model;
  SetTestTokenizerOptions(&grammar_model);
  grammar_model.rules.reset(new grammar::RulesSetT);
  grammar::LocaleShardMap locale_shard_map =
      grammar::LocaleShardMap::CreateLocaleShardMap({""});
  grammar::Rules rules(locale_shard_map);
  const int person_result =
      AddRuleClassificationResult("person", ModeFlag_ALL, 1.0, &grammar_model);
  rules.Add(
      "<person>", {"barack", "obama"},
      /*callback=*/
      static_cast<grammar::CallbackId>(grammar::DefaultCallback::kRootRule),
      /*callback_param=*/person_result);

  // Add test entity data.
  std::unique_ptr<MutableFlatbuffer> entity_data =
      entity_data_builder_->NewRoot();
  entity_data->Set("person", "Former President Barack Obama");
  grammar_model.rule_classification_result[person_result]
      ->serialized_entity_data = entity_data->Serialize();

  rules.Finalize().Serialize(/*include_debug_information=*/false,
                             grammar_model.rules.get());
  flatbuffers::DetachedBuffer serialized_model = PackModel(grammar_model);
  GrammarAnnotator annotator(CreateGrammarAnnotator(serialized_model));

  std::vector<AnnotatedSpan> result;
  EXPECT_TRUE(annotator.Annotate(
      {Locale::FromBCP47("en")},
      UTF8ToUnicodeText("I saw Barack Obama today", /*do_copy=*/false),
      &result));
  EXPECT_THAT(result, ElementsAre(IsAnnotatedSpan(6, 18, "person")));

  // Check entity data.
  // As we don't have generated code for the ad-hoc generated entity data
  // schema, we have to check manually using field offsets.
  const flatbuffers::Table* entity =
      flatbuffers::GetAnyRoot(reinterpret_cast<const unsigned char*>(
          result.front().classification.front().serialized_entity_data.data()));
  EXPECT_THAT(
      entity->GetPointer<const flatbuffers::String*>(/*field=*/8)->str(),
      "Former President Barack Obama");
}

TEST_F(GrammarAnnotatorTest, SetsEntityDataFromCapturingMatches) {
  // Create test rules.
  GrammarModelT grammar_model;
  SetTestTokenizerOptions(&grammar_model);
  grammar_model.rules.reset(new grammar::RulesSetT);
  grammar::LocaleShardMap locale_shard_map =
      grammar::LocaleShardMap::CreateLocaleShardMap({""});
  grammar::Rules rules(locale_shard_map);
  const int person_result =
      AddRuleClassificationResult("person", ModeFlag_ALL, 1.0, &grammar_model);

  rules.Add("<person>", {"barack?", "obama"});
  rules.Add("<person>", {"zapp?", "brannigan"});
  rules.AddValueMapping("<captured_person>", {"<person>"},
                        /*value=*/0);
  rules.Add(
      "<test>", {"<captured_person>"},
      /*callback=*/
      static_cast<grammar::CallbackId>(grammar::DefaultCallback::kRootRule),
      /*callback_param=*/person_result);

  // Set capturing group entity data information.
  grammar_model.rule_classification_result[person_result]
      ->capturing_group.emplace_back(new CapturingGroupT);
  CapturingGroupT* group =
      grammar_model.rule_classification_result[person_result]
          ->capturing_group.back()
          .get();
  group->entity_field_path.reset(new FlatbufferFieldPathT);
  group->entity_field_path->field.emplace_back(new FlatbufferFieldT);
  group->entity_field_path->field.back()->field_name = "person";
  group->normalization_options.reset(new NormalizationOptionsT);
  group->normalization_options->codepointwise_normalization =
      NormalizationOptions_::CodepointwiseNormalizationOp_UPPERCASE;

  rules.Finalize().Serialize(/*include_debug_information=*/false,
                             grammar_model.rules.get());
  flatbuffers::DetachedBuffer serialized_model = PackModel(grammar_model);
  GrammarAnnotator annotator(CreateGrammarAnnotator(serialized_model));

  std::vector<AnnotatedSpan> result;
  EXPECT_TRUE(annotator.Annotate(
      {Locale::FromBCP47("en")},
      UTF8ToUnicodeText("I saw Zapp Brannigan today", /*do_copy=*/false),
      &result));
  EXPECT_THAT(result, ElementsAre(IsAnnotatedSpan(6, 20, "person")));

  // Check entity data.
  // As we don't have generated code for the ad-hoc generated entity data
  // schema, we have to check manually using field offsets.
  const flatbuffers::Table* entity =
      flatbuffers::GetAnyRoot(reinterpret_cast<const unsigned char*>(
          result.front().classification.front().serialized_entity_data.data()));
  EXPECT_THAT(
      entity->GetPointer<const flatbuffers::String*>(/*field=*/8)->str(),
      "ZAPP BRANNIGAN");
}

TEST_F(GrammarAnnotatorTest, RespectsRuleModes) {
  // Create test rules.
  GrammarModelT grammar_model;
  SetTestTokenizerOptions(&grammar_model);
  grammar_model.rules.reset(new grammar::RulesSetT);
  grammar::LocaleShardMap locale_shard_map =
      grammar::LocaleShardMap::CreateLocaleShardMap({""});
  grammar::Rules rules(locale_shard_map);
  rules.Add("<classification_carrier>", {"ei"});
  rules.Add("<classification_carrier>", {"en"});
  rules.Add("<selection_carrier>", {"ai"});
  rules.Add("<selection_carrier>", {"bx"});
  rules.Add("<annotation_carrier>", {"aa"});
  rules.Add("<annotation_carrier>", {"lx"});
  rules.Add("<flight_code>", {"<2_digits>"});
  rules.Add("<flight_code>", {"<3_digits>"});
  rules.Add("<flight_code>", {"<4_digits>"});
  rules.Add(
      "<flight>", {"<annotation_carrier>", "<flight_code>"},
      /*callback=*/
      static_cast<grammar::CallbackId>(grammar::DefaultCallback::kRootRule),
      /*callback_param=*/
      AddRuleClassificationResult("flight", ModeFlag_ALL, 1.0, &grammar_model));
  rules.Add(
      "<flight>", {"<selection_carrier>", "<flight_code>"},
      /*callback=*/
      static_cast<grammar::CallbackId>(grammar::DefaultCallback::kRootRule),
      /*callback_param=*/
      AddRuleClassificationResult("flight",
                                  ModeFlag_CLASSIFICATION_AND_SELECTION, 1.0,
                                  &grammar_model));
  rules.Add(
      "<flight>", {"<classification_carrier>", "<flight_code>"},
      /*callback=*/
      static_cast<grammar::CallbackId>(grammar::DefaultCallback::kRootRule),
      /*callback_param=*/
      AddRuleClassificationResult("flight", ModeFlag_CLASSIFICATION, 1.0,
                                  &grammar_model));
  rules.Finalize().Serialize(/*include_debug_information=*/false,
                             grammar_model.rules.get());
  flatbuffers::DetachedBuffer serialized_model = PackModel(grammar_model);
  GrammarAnnotator annotator(CreateGrammarAnnotator(serialized_model));

  const UnicodeText text = UTF8ToUnicodeText(
      "My flight: LX 38 arriving at 4pm, I'll fly back on EI2014 but maybe "
      "also on bx 222");
  const std::vector<Locale> locales = {Locale::FromBCP47("en")};

  // Annotation, only high confidence pattern.
  {
    std::vector<AnnotatedSpan> result;
    EXPECT_TRUE(annotator.Annotate(locales, text, &result));
    EXPECT_THAT(result, ElementsAre(IsAnnotatedSpan(11, 16, "flight")));
  }

  // Selection, annotation patterns + selection.
  {
    AnnotatedSpan selection;

    // Selects 'LX 38'.
    EXPECT_TRUE(annotator.SuggestSelection(locales, text,
                                           /*selection=*/CodepointSpan{14, 15},
                                           &selection));
    EXPECT_THAT(selection, IsAnnotatedSpan(11, 16, "flight"));

    // Selects 'bx 222'.
    EXPECT_TRUE(annotator.SuggestSelection(locales, text,
                                           /*selection=*/CodepointSpan{76, 77},
                                           &selection));
    EXPECT_THAT(selection, IsAnnotatedSpan(76, 82, "flight"));

    // Doesn't select 'EI2014'.
    EXPECT_FALSE(annotator.SuggestSelection(locales, text,
                                            /*selection=*/CodepointSpan{51, 51},
                                            &selection));
  }

  // Classification, all patterns.
  {
    ClassificationResult result;

    // Classifies 'LX 38'.
    EXPECT_TRUE(
        annotator.ClassifyText(locales, text, CodepointSpan{11, 16}, &result));
    EXPECT_THAT(result, IsClassificationResult("flight"));

    // Classifies 'EI2014'.
    EXPECT_TRUE(
        annotator.ClassifyText(locales, text, CodepointSpan{51, 57}, &result));
    EXPECT_THAT(result, IsClassificationResult("flight"));

    // Classifies 'bx 222'.
    EXPECT_TRUE(
        annotator.ClassifyText(locales, text, CodepointSpan{76, 82}, &result));
    EXPECT_THAT(result, IsClassificationResult("flight"));
  }
}

}  // namespace
}  // namespace libtextclassifier3
