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

#include "annotator/annotator_test-include.h"

#include <iostream>
#include <memory>
#include <string>
#include <type_traits>

#include "annotator/annotator.h"
#include "annotator/collections.h"
#include "annotator/model_generated.h"
#include "annotator/test-utils.h"
#include "annotator/types-test-util.h"
#include "annotator/types.h"
#include "utils/grammar/utils/locale-shard-map.h"
#include "utils/grammar/utils/rules.h"
#include "utils/testing/annotator.h"
#include "lang_id/fb_model/lang-id-from-fb.h"
#include "lang_id/lang-id.h"

namespace libtextclassifier3 {
namespace test_internal {

using ::testing::Contains;
using ::testing::ElementsAre;
using ::testing::ElementsAreArray;
using ::testing::Eq;
using ::testing::IsEmpty;
using ::testing::UnorderedElementsAreArray;

std::string GetTestModelPath() { return GetModelPath() + "test_model.fb"; }

std::string GetModelWithVocabPath() {
  return GetModelPath() + "test_vocab_model.fb";
}

std::string GetTestModelWithDatetimeRegEx() {
  std::string model_buffer = ReadFile(GetTestModelPath());
  model_buffer = ModifyAnnotatorModel(model_buffer, [](ModelT* model) {
    model->datetime_grammar_model.reset(nullptr);
  });
  return model_buffer;
}

void ExpectFirstEntityIsMoney(const std::vector<AnnotatedSpan>& result,
                              const std::string& currency,
                              const std::string& amount, const int whole_part,
                              const int decimal_part, const int nanos) {
  ASSERT_GT(result.size(), 0);
  ASSERT_GT(result[0].classification.size(), 0);
  ASSERT_EQ(result[0].classification[0].collection, "money");

  const EntityData* entity_data =
      GetEntityData(result[0].classification[0].serialized_entity_data.data());
  ASSERT_NE(entity_data, nullptr);
  ASSERT_NE(entity_data->money(), nullptr);
  EXPECT_EQ(entity_data->money()->unnormalized_currency()->str(), currency);
  EXPECT_EQ(entity_data->money()->unnormalized_amount()->str(), amount);
  EXPECT_EQ(entity_data->money()->amount_whole_part(), whole_part);
  EXPECT_EQ(entity_data->money()->amount_decimal_part(), decimal_part);
  EXPECT_EQ(entity_data->money()->nanos(), nanos);
}

TEST_F(AnnotatorTest, EmbeddingExecutorLoadingFails) {
  std::unique_ptr<Annotator> classifier =
      Annotator::FromPath(GetModelPath() + "wrong_embeddings.fb", unilib_.get(),
                          calendarlib_.get());
  EXPECT_FALSE(classifier);
}

void VerifyClassifyText(const Annotator* classifier) {
  ASSERT_TRUE(classifier);

  EXPECT_EQ("other",
            FirstResult(classifier->ClassifyText(
                "this afternoon Barack Obama gave a speech at", {15, 27})));
  EXPECT_EQ("phone", FirstResult(classifier->ClassifyText(
                         "Call me at (800) 123-456 today", {11, 24})));

  // More lines.
  EXPECT_EQ("other",
            FirstResult(classifier->ClassifyText(
                "this afternoon Barack Obama gave a speech at|Visit "
                "www.google.com every today!|Call me at (800) 123-456 today.",
                {15, 27})));
  EXPECT_EQ("phone",
            FirstResult(classifier->ClassifyText(
                "this afternoon Barack Obama gave a speech at|Visit "
                "www.google.com every today!|Call me at (800) 123-456 today.",
                {90, 103})));

  // Single word.
  EXPECT_EQ("other", FirstResult(classifier->ClassifyText("obama", {0, 5})));
  EXPECT_EQ("other", FirstResult(classifier->ClassifyText("asdf", {0, 4})));

  // Junk. These should not crash the test.
  classifier->ClassifyText("", {0, 0});
  classifier->ClassifyText("asdf", {0, 0});
  classifier->ClassifyText("asdf", {0, 27});
  classifier->ClassifyText("asdf", {-30, 300});
  classifier->ClassifyText("asdf", {-10, -1});
  classifier->ClassifyText("asdf", {100, 17});
  classifier->ClassifyText("a\n\n\n\nx x x\n\n\n\n\n\n", {1, 5});

  // Test invalid utf8 input.
  EXPECT_EQ("<INVALID RESULTS>", FirstResult(classifier->ClassifyText(
                                     "\xf0\x9f\x98\x8b\x8b", {0, 0})));
}

TEST_F(AnnotatorTest, ClassifyText) {
  std::unique_ptr<Annotator> classifier = Annotator::FromPath(
      GetTestModelPath(), unilib_.get(), calendarlib_.get());
  VerifyClassifyText(classifier.get());
}

TEST_F(AnnotatorTest, ClassifyTextLocalesAndDictionary) {
  std::unique_ptr<Annotator> classifier = Annotator::FromPath(
      GetTestModelPath(), unilib_.get(), calendarlib_.get());
  ASSERT_TRUE(classifier);

  EXPECT_EQ("other", FirstResult(classifier->ClassifyText("isotope", {0, 7})));

  ClassificationOptions classification_options;
  classification_options.detected_text_language_tags = "en";
  EXPECT_EQ("dictionary", FirstResult(classifier->ClassifyText(
                              "isotope", {0, 7}, classification_options)));

  classification_options.detected_text_language_tags = "uz";
  EXPECT_EQ("other", FirstResult(classifier->ClassifyText(
                         "isotope", {0, 7}, classification_options)));
}

TEST_F(AnnotatorTest, ClassifyTextUseVocabAnnotatorWithoutVocabModel) {
  std::unique_ptr<Annotator> classifier = Annotator::FromPath(
      GetTestModelPath(), unilib_.get(), calendarlib_.get());
  ASSERT_TRUE(classifier);

  ClassificationOptions classification_options;
  classification_options.detected_text_language_tags = "en";
  classification_options.use_vocab_annotator = true;

  EXPECT_EQ("dictionary", FirstResult(classifier->ClassifyText(
                              "isotope", {0, 7}, classification_options)));
}

#ifdef TC3_VOCAB_ANNOTATOR_IMPL
TEST_F(AnnotatorTest, ClassifyTextWithVocabModel) {
  std::unique_ptr<Annotator> classifier = Annotator::FromPath(
      GetModelWithVocabPath(), unilib_.get(), calendarlib_.get());
  ASSERT_TRUE(classifier);

  ClassificationOptions classification_options;
  classification_options.detected_text_language_tags = "en";

  // The FFModel model does not annotate "integrity" as "dictionary", but the
  // vocab annotator does. So we can use that to check if the vocab annotator is
  // in use.
  classification_options.use_vocab_annotator = true;
  EXPECT_EQ("dictionary", FirstResult(classifier->ClassifyText(
                              "integrity", {0, 9}, classification_options)));
  classification_options.use_vocab_annotator = false;
  EXPECT_EQ("other", FirstResult(classifier->ClassifyText(
                         "integrity", {0, 9}, classification_options)));
}
#endif  // TC3_VOCAB_ANNOTATOR_IMPL

TEST_F(AnnotatorTest, ClassifyTextDisabledFail) {
  const std::string test_model = ReadFile(GetTestModelPath());
  std::unique_ptr<ModelT> unpacked_model = UnPackModel(test_model.c_str());
  TC3_CHECK(unpacked_model != nullptr);

  unpacked_model->classification_model.clear();
  unpacked_model->triggering_options.reset(new ModelTriggeringOptionsT);
  unpacked_model->triggering_options->enabled_modes = ModeFlag_SELECTION;

  flatbuffers::FlatBufferBuilder builder;
  FinishModelBuffer(builder, Model::Pack(builder, unpacked_model.get()));

  std::unique_ptr<Annotator> classifier = Annotator::FromUnownedBuffer(
      reinterpret_cast<const char*>(builder.GetBufferPointer()),
      builder.GetSize(), unilib_.get(), calendarlib_.get());

  // The classification model is still needed for selection scores.
  ASSERT_FALSE(classifier);
}

TEST_F(AnnotatorTest, ClassifyTextDisabled) {
  const std::string test_model = ReadFile(GetTestModelPath());
  std::unique_ptr<ModelT> unpacked_model = UnPackModel(test_model.c_str());

  unpacked_model->enabled_modes = ModeFlag_ANNOTATION_AND_SELECTION;

  flatbuffers::FlatBufferBuilder builder;
  FinishModelBuffer(builder, Model::Pack(builder, unpacked_model.get()));

  std::unique_ptr<Annotator> classifier = Annotator::FromUnownedBuffer(
      reinterpret_cast<const char*>(builder.GetBufferPointer()),
      builder.GetSize(), unilib_.get(), calendarlib_.get());
  ASSERT_TRUE(classifier);

  EXPECT_THAT(
      classifier->ClassifyText("Call me at (800) 123-456 today", {11, 24}),
      IsEmpty());
}

TEST_F(AnnotatorTest, ClassifyTextFilteredCollections) {
  const std::string test_model = ReadFile(GetTestModelPath());

  std::unique_ptr<Annotator> classifier = Annotator::FromUnownedBuffer(
      test_model.c_str(), test_model.size(), unilib_.get(), calendarlib_.get());
  ASSERT_TRUE(classifier);

  EXPECT_EQ("phone", FirstResult(classifier->ClassifyText(
                         "Call me at (800) 123-456 today", {11, 24})));

  std::unique_ptr<ModelT> unpacked_model = UnPackModel(test_model.c_str());
  unpacked_model->output_options.reset(new OutputOptionsT);

  // Disable phone classification
  unpacked_model->output_options->filtered_collections_classification.push_back(
      "phone");

  flatbuffers::FlatBufferBuilder builder;
  FinishModelBuffer(builder, Model::Pack(builder, unpacked_model.get()));

  classifier = Annotator::FromUnownedBuffer(
      reinterpret_cast<const char*>(builder.GetBufferPointer()),
      builder.GetSize(), unilib_.get(), calendarlib_.get());
  ASSERT_TRUE(classifier);

  EXPECT_EQ("other", FirstResult(classifier->ClassifyText(
                         "Call me at (800) 123-456 today", {11, 24})));

  // Check that the address classification still passes.
  EXPECT_EQ("address", FirstResult(classifier->ClassifyText(
                           "350 Third Street, Cambridge", {0, 27})));
}

TEST_F(AnnotatorTest, ClassifyTextRegularExpression) {
  const std::string test_model = ReadFile(GetTestModelPath());
  std::unique_ptr<ModelT> unpacked_model = UnPackModel(test_model.c_str());

  // Add test regex models.
  unpacked_model->regex_model->patterns.push_back(MakePattern(
      "person", "Barack Obama", /*enabled_for_classification=*/true,
      /*enabled_for_selection=*/false, /*enabled_for_annotation=*/false, 1.0));
  unpacked_model->regex_model->patterns.push_back(MakePattern(
      "flight", "[a-zA-Z]{2}\\d{2,4}", /*enabled_for_classification=*/true,
      /*enabled_for_selection=*/false, /*enabled_for_annotation=*/false, 0.5));
  std::unique_ptr<RegexModel_::PatternT> verified_pattern =
      MakePattern("payment_card", "\\d{4}(?: \\d{4}){3}",
                  /*enabled_for_classification=*/true,
                  /*enabled_for_selection=*/false,
                  /*enabled_for_annotation=*/false, 1.0);
  verified_pattern->verification_options.reset(new VerificationOptionsT);
  verified_pattern->verification_options->verify_luhn_checksum = true;
  unpacked_model->regex_model->patterns.push_back(std::move(verified_pattern));

  flatbuffers::FlatBufferBuilder builder;
  FinishModelBuffer(builder, Model::Pack(builder, unpacked_model.get()));

  std::unique_ptr<Annotator> classifier = Annotator::FromUnownedBuffer(
      reinterpret_cast<const char*>(builder.GetBufferPointer()),
      builder.GetSize(), unilib_.get(), calendarlib_.get());
  ASSERT_TRUE(classifier);

  EXPECT_EQ("flight",
            FirstResult(classifier->ClassifyText(
                "Your flight LX373 is delayed by 3 hours.", {12, 17})));
  EXPECT_EQ("person",
            FirstResult(classifier->ClassifyText(
                "this afternoon Barack Obama gave a speech at", {15, 27})));
  EXPECT_EQ("email",
            FirstResult(classifier->ClassifyText("you@android.com", {0, 15})));
  EXPECT_EQ("email", FirstResult(classifier->ClassifyText(
                         "Contact me at you@android.com", {14, 29})));

  EXPECT_EQ("url", FirstResult(classifier->ClassifyText(
                       "Visit www.google.com every today!", {6, 20})));

  EXPECT_EQ("flight", FirstResult(classifier->ClassifyText("LX 37", {0, 5})));
  EXPECT_EQ("flight", FirstResult(classifier->ClassifyText("flight LX 37 abcd",
                                                           {7, 12})));
  EXPECT_EQ("payment_card", FirstResult(classifier->ClassifyText(
                                "cc: 4012 8888 8888 1881", {4, 23})));
  EXPECT_EQ("payment_card", FirstResult(classifier->ClassifyText(
                                "2221 0067 4735 6281", {0, 19})));
  // Luhn check fails.
  EXPECT_EQ("other", FirstResult(classifier->ClassifyText("2221 0067 4735 6282",
                                                          {0, 19})));

  // More lines.
  EXPECT_EQ("url",
            FirstResult(classifier->ClassifyText(
                "this afternoon Barack Obama gave a speech at|Visit "
                "www.google.com every today!|Call me at (800) 123-456 today.",
                {51, 65})));
}

#ifndef TC3_DISABLE_LUA
TEST_F(AnnotatorTest, ClassifyTextRegularExpressionLuaVerification) {
  const std::string test_model = ReadFile(GetTestModelPath());
  std::unique_ptr<ModelT> unpacked_model = UnPackModel(test_model.c_str());

  // Add test regex models.
  std::unique_ptr<RegexModel_::PatternT> verified_pattern =
      MakePattern("parcel_tracking", "((\\d{2})-00-\\d{6}-\\d{8})",
                  /*enabled_for_classification=*/true,
                  /*enabled_for_selection=*/false,
                  /*enabled_for_annotation=*/false, 1.0);
  verified_pattern->verification_options.reset(new VerificationOptionsT);
  verified_pattern->verification_options->lua_verifier = 0;
  unpacked_model->regex_model->patterns.push_back(std::move(verified_pattern));
  unpacked_model->regex_model->lua_verifier.push_back(
      "return match[2].text==\"99\"");

  flatbuffers::FlatBufferBuilder builder;
  FinishModelBuffer(builder, Model::Pack(builder, unpacked_model.get()));

  std::unique_ptr<Annotator> classifier = Annotator::FromUnownedBuffer(
      reinterpret_cast<const char*>(builder.GetBufferPointer()),
      builder.GetSize(), unilib_.get(), calendarlib_.get());
  ASSERT_TRUE(classifier);

  // Custom rule triggers and is correctly verified.
  EXPECT_EQ("parcel_tracking", FirstResult(classifier->ClassifyText(
                                   "99-00-123456-12345678", {0, 21})));

  // Custom verification fails.
  EXPECT_EQ("other", FirstResult(classifier->ClassifyText(
                         "90-00-123456-12345678", {0, 21})));
}
#endif  // TC3_DISABLE_LUA

TEST_F(AnnotatorTest, ClassifyTextRegularExpressionEntityData) {
  const std::string test_model = ReadFile(GetTestModelPath());
  std::unique_ptr<ModelT> unpacked_model = UnPackModel(test_model.c_str());

  // Add fake entity schema metadata.
  AddTestEntitySchemaData(unpacked_model.get());

  AddTestRegexModel(unpacked_model.get());

  flatbuffers::FlatBufferBuilder builder;
  FinishModelBuffer(builder, Model::Pack(builder, unpacked_model.get()));

  std::unique_ptr<Annotator> classifier = Annotator::FromUnownedBuffer(
      reinterpret_cast<const char*>(builder.GetBufferPointer()),
      builder.GetSize(), unilib_.get(), calendarlib_.get());
  ASSERT_TRUE(classifier);

  // Check with full name.
  {
    auto classifications =
        classifier->ClassifyText("Barack Obama is 57 years old", {0, 28});
    EXPECT_EQ(1, classifications.size());
    EXPECT_EQ("person_with_age", classifications[0].collection);

    // Check entity data.
    const flatbuffers::Table* entity =
        flatbuffers::GetAnyRoot(reinterpret_cast<const unsigned char*>(
            classifications[0].serialized_entity_data.data()));
    EXPECT_EQ(
        entity->GetPointer<const flatbuffers::String*>(/*field=*/4)->str(),
        "Barack");
    EXPECT_EQ(
        entity->GetPointer<const flatbuffers::String*>(/*field=*/8)->str(),
        "Obama");
    // Check `age`.
    EXPECT_EQ(entity->GetField<int>(/*field=*/10, /*defaultval=*/0), 57);

    // Check `is_alive`.
    EXPECT_TRUE(entity->GetField<bool>(/*field=*/6, /*defaultval=*/false));

    // Check `former_us_president`.
    EXPECT_TRUE(entity->GetField<bool>(/*field=*/12, /*defaultval=*/false));
  }

  // Check only with first name.
  {
    auto classifications =
        classifier->ClassifyText("Barack is 57 years old", {0, 22});
    EXPECT_EQ(1, classifications.size());
    EXPECT_EQ("person_with_age", classifications[0].collection);

    // Check entity data.
    const flatbuffers::Table* entity =
        flatbuffers::GetAnyRoot(reinterpret_cast<const unsigned char*>(
            classifications[0].serialized_entity_data.data()));
    EXPECT_EQ(
        entity->GetPointer<const flatbuffers::String*>(/*field=*/4)->str(),
        "Barack");

    // Check `age`.
    EXPECT_EQ(entity->GetField<int>(/*field=*/10, /*defaultval=*/0), 57);

    // Check `is_alive`.
    EXPECT_TRUE(entity->GetField<bool>(/*field=*/6, /*defaultval=*/false));

    // Check `former_us_president`.
    EXPECT_FALSE(entity->GetField<bool>(/*field=*/12, /*defaultval=*/false));
  }
}

TEST_F(AnnotatorTest, ClassifyTextRegularExpressionEntityDataNormalization) {
  const std::string test_model = ReadFile(GetTestModelPath());
  std::unique_ptr<ModelT> unpacked_model = UnPackModel(test_model.c_str());

  // Add fake entity schema metadata.
  AddTestEntitySchemaData(unpacked_model.get());

  AddTestRegexModel(unpacked_model.get());

  // Upper case last name as post-processing.
  RegexModel_::PatternT* pattern =
      unpacked_model->regex_model->patterns.back().get();
  pattern->capturing_group[2]->normalization_options.reset(
      new NormalizationOptionsT);
  pattern->capturing_group[2]
      ->normalization_options->codepointwise_normalization =
      NormalizationOptions_::CodepointwiseNormalizationOp_UPPERCASE;

  flatbuffers::FlatBufferBuilder builder;
  FinishModelBuffer(builder, Model::Pack(builder, unpacked_model.get()));

  std::unique_ptr<Annotator> classifier = Annotator::FromUnownedBuffer(
      reinterpret_cast<const char*>(builder.GetBufferPointer()),
      builder.GetSize(), unilib_.get(), calendarlib_.get());
  ASSERT_TRUE(classifier);

  auto classifications =
      classifier->ClassifyText("Barack Obama is 57 years old", {0, 28});
  EXPECT_EQ(1, classifications.size());
  EXPECT_EQ("person_with_age", classifications[0].collection);

  // Check entity data normalization.
  const flatbuffers::Table* entity =
      flatbuffers::GetAnyRoot(reinterpret_cast<const unsigned char*>(
          classifications[0].serialized_entity_data.data()));
  EXPECT_EQ(entity->GetPointer<const flatbuffers::String*>(/*field=*/8)->str(),
            "OBAMA");
}

TEST_F(AnnotatorTest, ClassifyTextPriorityResolution) {
  const std::string test_model = ReadFile(GetTestModelPath());
  std::unique_ptr<ModelT> unpacked_model = UnPackModel(test_model.c_str());
  TC3_CHECK(libtextclassifier3::DecompressModel(unpacked_model.get()));
  // Add test regex models.
  unpacked_model->regex_model->patterns.clear();
  unpacked_model->regex_model->patterns.push_back(MakePattern(
      "flight1", "[a-zA-Z]{2}\\d{2,4}", /*enabled_for_classification=*/true,
      /*enabled_for_selection=*/false, /*enabled_for_annotation=*/false,
      /*score=*/1.0, /*priority_score=*/1.0));
  unpacked_model->regex_model->patterns.push_back(MakePattern(
      "flight2", "[a-zA-Z]{2}\\d{2,4}", /*enabled_for_classification=*/true,
      /*enabled_for_selection=*/false, /*enabled_for_annotation=*/false,
      /*score=*/1.0, /*priority_score=*/0.0));

  {
    flatbuffers::FlatBufferBuilder builder;
    FinishModelBuffer(builder, Model::Pack(builder, unpacked_model.get()));
    std::unique_ptr<Annotator> classifier = Annotator::FromUnownedBuffer(
        reinterpret_cast<const char*>(builder.GetBufferPointer()),
        builder.GetSize(), unilib_.get(), calendarlib_.get());
    ASSERT_TRUE(classifier);

    EXPECT_EQ("flight1",
              FirstResult(classifier->ClassifyText(
                  "Your flight LX373 is delayed by 3 hours.", {12, 17})));
  }

  unpacked_model->regex_model->patterns.back()->priority_score = 3.0;
  {
    flatbuffers::FlatBufferBuilder builder;
    FinishModelBuffer(builder, Model::Pack(builder, unpacked_model.get()));
    std::unique_ptr<Annotator> classifier = Annotator::FromUnownedBuffer(
        reinterpret_cast<const char*>(builder.GetBufferPointer()),
        builder.GetSize(), unilib_.get(), calendarlib_.get());
    ASSERT_TRUE(classifier);

    EXPECT_EQ("flight2",
              FirstResult(classifier->ClassifyText(
                  "Your flight LX373 is delayed by 3 hours.", {12, 17})));
  }
}

TEST_F(AnnotatorTest, AnnotatePriorityResolution) {
  const std::string test_model = ReadFile(GetTestModelPath());
  std::unique_ptr<ModelT> unpacked_model = UnPackModel(test_model.c_str());
  TC3_CHECK(libtextclassifier3::DecompressModel(unpacked_model.get()));
  // Add test regex models. One of them  has higher priority score than
  // the other. We'll test that always the one with higher priority score
  // ends up winning.
  unpacked_model->regex_model->patterns.clear();
  const std::string flight_regex = "([a-zA-Z]{2}\\d{2,4})";
  unpacked_model->regex_model->patterns.push_back(MakePattern(
      "flight", flight_regex, /*enabled_for_classification=*/true,
      /*enabled_for_selection=*/true, /*enabled_for_annotation=*/true,
      /*score=*/1.0, /*priority_score=*/1.0));
  unpacked_model->regex_model->patterns.push_back(MakePattern(
      "flight", flight_regex, /*enabled_for_classification=*/true,
      /*enabled_for_selection=*/true, /*enabled_for_annotation=*/true,
      /*score=*/1.0, /*priority_score=*/0.0));

  // "flight" that wins should have a priority score of 1.0.
  {
    flatbuffers::FlatBufferBuilder builder;
    FinishModelBuffer(builder, Model::Pack(builder, unpacked_model.get()));
    std::unique_ptr<Annotator> classifier = Annotator::FromUnownedBuffer(
        reinterpret_cast<const char*>(builder.GetBufferPointer()),
        builder.GetSize(), unilib_.get(), calendarlib_.get());
    ASSERT_TRUE(classifier);

    const std::vector<AnnotatedSpan> results =
        classifier->Annotate("Your flight LX373 is delayed by 3 hours.");
    ASSERT_THAT(results, Not(IsEmpty()));
    EXPECT_THAT(results[0].classification, Not(IsEmpty()));
    EXPECT_GE(results[0].classification[0].priority_score, 0.9);
  }

  // When we increase the priority score, the "flight" that wins should have a
  // priority score of 3.0.
  unpacked_model->regex_model->patterns.back()->priority_score = 3.0;
  {
    flatbuffers::FlatBufferBuilder builder;
    FinishModelBuffer(builder, Model::Pack(builder, unpacked_model.get()));
    std::unique_ptr<Annotator> classifier = Annotator::FromUnownedBuffer(
        reinterpret_cast<const char*>(builder.GetBufferPointer()),
        builder.GetSize(), unilib_.get(), calendarlib_.get());
    ASSERT_TRUE(classifier);

    const std::vector<AnnotatedSpan> results =
        classifier->Annotate("Your flight LX373 is delayed by 3 hours.");
    ASSERT_THAT(results, Not(IsEmpty()));
    EXPECT_THAT(results[0].classification, Not(IsEmpty()));
    EXPECT_GE(results[0].classification[0].priority_score, 2.9);
  }
}

TEST_F(AnnotatorTest, SuggestSelectionRegularExpression) {
  const std::string test_model = ReadFile(GetTestModelPath());
  std::unique_ptr<ModelT> unpacked_model = UnPackModel(test_model.c_str());

  // Add test regex models.
  unpacked_model->regex_model->patterns.push_back(MakePattern(
      "person", " (Barack Obama) ", /*enabled_for_classification=*/false,
      /*enabled_for_selection=*/true, /*enabled_for_annotation=*/false, 1.0));
  unpacked_model->regex_model->patterns.push_back(MakePattern(
      "flight", "([a-zA-Z]{2} ?\\d{2,4})", /*enabled_for_classification=*/false,
      /*enabled_for_selection=*/true, /*enabled_for_annotation=*/false, 1.0));
  unpacked_model->regex_model->patterns.back()->priority_score = 1.1;
  std::unique_ptr<RegexModel_::PatternT> verified_pattern =
      MakePattern("payment_card", "(\\d{4}(?: \\d{4}){3})",
                  /*enabled_for_classification=*/false,
                  /*enabled_for_selection=*/true,
                  /*enabled_for_annotation=*/false, 1.0);
  verified_pattern->verification_options.reset(new VerificationOptionsT);
  verified_pattern->verification_options->verify_luhn_checksum = true;
  unpacked_model->regex_model->patterns.push_back(std::move(verified_pattern));

  flatbuffers::FlatBufferBuilder builder;
  FinishModelBuffer(builder, Model::Pack(builder, unpacked_model.get()));

  std::unique_ptr<Annotator> classifier = Annotator::FromUnownedBuffer(
      reinterpret_cast<const char*>(builder.GetBufferPointer()),
      builder.GetSize(), unilib_.get(), calendarlib_.get());
  ASSERT_TRUE(classifier);

  // Check regular expression selection.
  EXPECT_EQ(classifier->SuggestSelection(
                "Your flight MA 0123 is delayed by 3 hours.", {12, 14}),
            CodepointSpan(12, 19));
  EXPECT_EQ(classifier->SuggestSelection(
                "this afternoon Barack Obama gave a speech at", {15, 21}),
            CodepointSpan(15, 27));
  EXPECT_EQ(classifier->SuggestSelection("cc: 4012 8888 8888 1881", {9, 14}),
            CodepointSpan(4, 23));
}

TEST_F(AnnotatorTest, SuggestSelectionRegularExpressionCustomSelectionBounds) {
  const std::string test_model = ReadFile(GetTestModelPath());
  std::unique_ptr<ModelT> unpacked_model = UnPackModel(test_model.c_str());

  // Add test regex models.
  std::unique_ptr<RegexModel_::PatternT> custom_selection_bounds_pattern =
      MakePattern("date_range",
                  "(?:(?:from )?(\\d{2}\\/\\d{2}\\/\\d{4}) to "
                  "(\\d{2}\\/\\d{2}\\/\\d{4}))|(for ever)",
                  /*enabled_for_classification=*/false,
                  /*enabled_for_selection=*/true,
                  /*enabled_for_annotation=*/false, 1.0);
  custom_selection_bounds_pattern->capturing_group.emplace_back(
      new CapturingGroupT);
  custom_selection_bounds_pattern->capturing_group.emplace_back(
      new CapturingGroupT);
  custom_selection_bounds_pattern->capturing_group.emplace_back(
      new CapturingGroupT);
  custom_selection_bounds_pattern->capturing_group.emplace_back(
      new CapturingGroupT);
  custom_selection_bounds_pattern->capturing_group[0]->extend_selection = false;
  custom_selection_bounds_pattern->capturing_group[1]->extend_selection = true;
  custom_selection_bounds_pattern->capturing_group[2]->extend_selection = true;
  custom_selection_bounds_pattern->capturing_group[3]->extend_selection = true;
  unpacked_model->regex_model->patterns.push_back(
      std::move(custom_selection_bounds_pattern));

  flatbuffers::FlatBufferBuilder builder;
  FinishModelBuffer(builder, Model::Pack(builder, unpacked_model.get()));

  std::unique_ptr<Annotator> classifier = Annotator::FromUnownedBuffer(
      reinterpret_cast<const char*>(builder.GetBufferPointer()),
      builder.GetSize(), unilib_.get(), calendarlib_.get());
  ASSERT_TRUE(classifier);

  // Check regular expression selection.
  EXPECT_EQ(classifier->SuggestSelection("it's from 04/30/1789 to 03/04/1797",
                                         {21, 23}),
            CodepointSpan(10, 34));
  EXPECT_EQ(classifier->SuggestSelection("it takes for ever", {9, 12}),
            CodepointSpan(9, 17));
}

TEST_F(AnnotatorTest, SuggestSelectionRegularExpressionConflictsModelWins) {
  const std::string test_model = ReadFile(GetTestModelPath());
  std::unique_ptr<ModelT> unpacked_model = UnPackModel(test_model.c_str());

  // Add test regex models.
  unpacked_model->regex_model->patterns.push_back(MakePattern(
      "person", " (Barack Obama) ", /*enabled_for_classification=*/false,
      /*enabled_for_selection=*/true, /*enabled_for_annotation=*/false, 1.0));
  unpacked_model->regex_model->patterns.push_back(MakePattern(
      "flight", "([a-zA-Z]{2} ?\\d{2,4})", /*enabled_for_classification=*/false,
      /*enabled_for_selection=*/true, /*enabled_for_annotation=*/false, 1.0));
  unpacked_model->regex_model->patterns.back()->priority_score = 0.5;

  flatbuffers::FlatBufferBuilder builder;
  FinishModelBuffer(builder, Model::Pack(builder, unpacked_model.get()));

  std::unique_ptr<Annotator> classifier = Annotator::FromUnownedBuffer(
      reinterpret_cast<const char*>(builder.GetBufferPointer()),
      builder.GetSize(), unilib_.get(), calendarlib_.get());
  ASSERT_TRUE(classifier);

  // Check conflict resolution.
  EXPECT_EQ(
      classifier->SuggestSelection(
          "saw Barack Obama today .. 350 Third Street, Cambridge, MA 0123",
          {55, 57}),
      CodepointSpan(26, 62));
}

TEST_F(AnnotatorTest, SuggestSelectionRegularExpressionConflictsRegexWins) {
  const std::string test_model = ReadFile(GetTestModelPath());
  std::unique_ptr<ModelT> unpacked_model = UnPackModel(test_model.c_str());

  // Add test regex models.
  unpacked_model->regex_model->patterns.push_back(MakePattern(
      "person", " (Barack Obama) ", /*enabled_for_classification=*/false,
      /*enabled_for_selection=*/true, /*enabled_for_annotation=*/false, 1.0));
  unpacked_model->regex_model->patterns.push_back(MakePattern(
      "flight", "([a-zA-Z]{2} ?\\d{2,4})", /*enabled_for_classification=*/false,
      /*enabled_for_selection=*/true, /*enabled_for_annotation=*/false, 1.0));
  unpacked_model->regex_model->patterns.back()->priority_score = 1.1;

  flatbuffers::FlatBufferBuilder builder;
  FinishModelBuffer(builder, Model::Pack(builder, unpacked_model.get()));

  std::unique_ptr<Annotator> classifier = Annotator::FromUnownedBuffer(
      reinterpret_cast<const char*>(builder.GetBufferPointer()),
      builder.GetSize(), unilib_.get(), calendarlib_.get());
  ASSERT_TRUE(classifier);

  // Check conflict resolution.
  EXPECT_EQ(
      classifier->SuggestSelection(
          "saw Barack Obama today .. 350 Third Street, Cambridge, MA 0123",
          {55, 57}),
      CodepointSpan(55, 62));
}

TEST_F(AnnotatorTest, AnnotateRegex) {
  const std::string test_model = ReadFile(GetTestModelPath());
  std::unique_ptr<ModelT> unpacked_model = UnPackModel(test_model.c_str());

  // Add test regex models.
  unpacked_model->regex_model->patterns.push_back(MakePattern(
      "person", " (Barack Obama) ", /*enabled_for_classification=*/false,
      /*enabled_for_selection=*/false, /*enabled_for_annotation=*/true, 1.0));
  unpacked_model->regex_model->patterns.push_back(MakePattern(
      "flight", "([a-zA-Z]{2} ?\\d{2,4})", /*enabled_for_classification=*/false,
      /*enabled_for_selection=*/false, /*enabled_for_annotation=*/true, 0.5));
  std::unique_ptr<RegexModel_::PatternT> verified_pattern =
      MakePattern("payment_card", "(\\d{4}(?: \\d{4}){3})",
                  /*enabled_for_classification=*/false,
                  /*enabled_for_selection=*/false,
                  /*enabled_for_annotation=*/true, 1.0);
  verified_pattern->verification_options.reset(new VerificationOptionsT);
  verified_pattern->verification_options->verify_luhn_checksum = true;
  unpacked_model->regex_model->patterns.push_back(std::move(verified_pattern));
  flatbuffers::FlatBufferBuilder builder;
  FinishModelBuffer(builder, Model::Pack(builder, unpacked_model.get()));

  std::unique_ptr<Annotator> classifier = Annotator::FromUnownedBuffer(
      reinterpret_cast<const char*>(builder.GetBufferPointer()),
      builder.GetSize(), unilib_.get(), calendarlib_.get());
  ASSERT_TRUE(classifier);

  const std::string test_string =
      "& saw Barack Obama today .. 350 Third Street, Cambridge\nand my phone "
      "number is 853 225 3556\nand my card is 4012 8888 8888 1881.\n";
  EXPECT_THAT(classifier->Annotate(test_string),
              ElementsAreArray({IsAnnotatedSpan(6, 18, "person"),
                                IsAnnotatedSpan(28, 55, "address"),
                                IsAnnotatedSpan(79, 91, "phone"),
                                IsAnnotatedSpan(107, 126, "payment_card")}));
}

TEST_F(AnnotatorTest, AnnotatesFlightNumbers) {
  std::unique_ptr<Annotator> classifier = Annotator::FromPath(
      GetTestModelPath(), unilib_.get(), calendarlib_.get());
  ASSERT_TRUE(classifier);

  // ICAO is only used for selected airlines.
  // Expected: LX373, EZY1234 and U21234.
  const std::string test_string = "flights LX373, SWR373, EZY1234, U21234";
  EXPECT_THAT(classifier->Annotate(test_string),
              ElementsAreArray({IsAnnotatedSpan(8, 13, "flight"),
                                IsAnnotatedSpan(23, 30, "flight"),
                                IsAnnotatedSpan(32, 38, "flight")}));
}

#ifndef TC3_DISABLE_LUA
TEST_F(AnnotatorTest, AnnotateRegexLuaVerification) {
  const std::string test_model = ReadFile(GetTestModelPath());
  std::unique_ptr<ModelT> unpacked_model = UnPackModel(test_model.c_str());

  // Add test regex models.
  std::unique_ptr<RegexModel_::PatternT> verified_pattern =
      MakePattern("parcel_tracking", "((\\d{2})-00-\\d{6}-\\d{8})",
                  /*enabled_for_classification=*/true,
                  /*enabled_for_selection=*/true,
                  /*enabled_for_annotation=*/true, 1.0);
  verified_pattern->verification_options.reset(new VerificationOptionsT);
  verified_pattern->verification_options->lua_verifier = 0;
  unpacked_model->regex_model->patterns.push_back(std::move(verified_pattern));
  unpacked_model->regex_model->lua_verifier.push_back(
      "return match[2].text==\"99\"");

  flatbuffers::FlatBufferBuilder builder;
  FinishModelBuffer(builder, Model::Pack(builder, unpacked_model.get()));

  std::unique_ptr<Annotator> classifier = Annotator::FromUnownedBuffer(
      reinterpret_cast<const char*>(builder.GetBufferPointer()),
      builder.GetSize(), unilib_.get(), calendarlib_.get());
  ASSERT_TRUE(classifier);

  const std::string test_string =
      "your parcel is on the way: 99-00-123456-12345678";
  EXPECT_THAT(classifier->Annotate(test_string),
              ElementsAreArray({IsAnnotatedSpan(27, 48, "parcel_tracking")}));
}
#endif  // TC3_DISABLE_LUA

TEST_F(AnnotatorTest, AnnotateTextRegularExpressionEntityData) {
  const std::string test_model = ReadFile(GetTestModelPath());
  std::unique_ptr<ModelT> unpacked_model = UnPackModel(test_model.c_str());

  // Add fake entity schema metadata.
  AddTestEntitySchemaData(unpacked_model.get());

  AddTestRegexModel(unpacked_model.get());

  flatbuffers::FlatBufferBuilder builder;
  FinishModelBuffer(builder, Model::Pack(builder, unpacked_model.get()));

  std::unique_ptr<Annotator> classifier = Annotator::FromUnownedBuffer(
      reinterpret_cast<const char*>(builder.GetBufferPointer()),
      builder.GetSize(), unilib_.get(), calendarlib_.get());
  ASSERT_TRUE(classifier);

  AnnotationOptions options;
  options.is_serialized_entity_data_enabled = true;
  auto annotations =
      classifier->Annotate("Barack Obama is 57 years old", options);
  EXPECT_EQ(1, annotations.size());
  EXPECT_EQ(1, annotations[0].classification.size());
  EXPECT_EQ("person_with_age", annotations[0].classification[0].collection);

  // Check entity data.
  const flatbuffers::Table* entity =
      flatbuffers::GetAnyRoot(reinterpret_cast<const unsigned char*>(
          annotations[0].classification[0].serialized_entity_data.data()));
  EXPECT_EQ(entity->GetPointer<const flatbuffers::String*>(/*field=*/4)->str(),
            "Barack");
  EXPECT_EQ(entity->GetPointer<const flatbuffers::String*>(/*field=*/8)->str(),
            "Obama");
  // Check `age`.
  EXPECT_EQ(entity->GetField<int>(/*field=*/10, /*defaultval=*/0), 57);

  // Check `is_alive`.
  EXPECT_TRUE(entity->GetField<bool>(/*field=*/6, /*defaultval=*/false));

  // Check `former_us_president`.
  EXPECT_TRUE(entity->GetField<bool>(/*field=*/12, /*defaultval=*/false));
}

TEST_F(AnnotatorTest, AnnotateTextRegularExpressionEntityDataNormalization) {
  const std::string test_model = ReadFile(GetTestModelPath());
  std::unique_ptr<ModelT> unpacked_model = UnPackModel(test_model.c_str());

  // Add fake entity schema metadata.
  AddTestEntitySchemaData(unpacked_model.get());

  AddTestRegexModel(unpacked_model.get());

  // Upper case last name as post-processing.
  RegexModel_::PatternT* pattern =
      unpacked_model->regex_model->patterns.back().get();
  pattern->capturing_group[2]->normalization_options.reset(
      new NormalizationOptionsT);
  pattern->capturing_group[2]
      ->normalization_options->codepointwise_normalization =
      NormalizationOptions_::CodepointwiseNormalizationOp_UPPERCASE;

  flatbuffers::FlatBufferBuilder builder;
  FinishModelBuffer(builder, Model::Pack(builder, unpacked_model.get()));

  std::unique_ptr<Annotator> classifier = Annotator::FromUnownedBuffer(
      reinterpret_cast<const char*>(builder.GetBufferPointer()),
      builder.GetSize(), unilib_.get(), calendarlib_.get());
  ASSERT_TRUE(classifier);

  AnnotationOptions options;
  options.is_serialized_entity_data_enabled = true;
  auto annotations =
      classifier->Annotate("Barack Obama is 57 years old", options);
  EXPECT_EQ(1, annotations.size());
  EXPECT_EQ(1, annotations[0].classification.size());
  EXPECT_EQ("person_with_age", annotations[0].classification[0].collection);

  // Check normalization.
  const flatbuffers::Table* entity =
      flatbuffers::GetAnyRoot(reinterpret_cast<const unsigned char*>(
          annotations[0].classification[0].serialized_entity_data.data()));
  EXPECT_EQ(entity->GetPointer<const flatbuffers::String*>(/*field=*/8)->str(),
            "OBAMA");
}

TEST_F(AnnotatorTest, AnnotateTextRegularExpressionEntityDataDisabled) {
  const std::string test_model = ReadFile(GetTestModelPath());
  std::unique_ptr<ModelT> unpacked_model = UnPackModel(test_model.c_str());

  // Add fake entity schema metadata.
  AddTestEntitySchemaData(unpacked_model.get());

  AddTestRegexModel(unpacked_model.get());

  flatbuffers::FlatBufferBuilder builder;
  FinishModelBuffer(builder, Model::Pack(builder, unpacked_model.get()));

  std::unique_ptr<Annotator> classifier = Annotator::FromUnownedBuffer(
      reinterpret_cast<const char*>(builder.GetBufferPointer()),
      builder.GetSize(), unilib_.get(), calendarlib_.get());
  ASSERT_TRUE(classifier);

  AnnotationOptions options;
  options.is_serialized_entity_data_enabled = false;
  auto annotations =
      classifier->Annotate("Barack Obama is 57 years old", options);
  EXPECT_EQ(1, annotations.size());
  EXPECT_EQ(1, annotations[0].classification.size());
  EXPECT_EQ("person_with_age", annotations[0].classification[0].collection);

  // Check entity data.
  EXPECT_EQ("", annotations[0].classification[0].serialized_entity_data);
}

TEST_F(AnnotatorTest, PhoneFiltering) {
  std::unique_ptr<Annotator> classifier = Annotator::FromPath(
      GetTestModelPath(), unilib_.get(), calendarlib_.get());
  ASSERT_TRUE(classifier);

  EXPECT_EQ("phone", FirstResult(classifier->ClassifyText(
                         "phone: (123) 456 789", {7, 20})));
  EXPECT_EQ("phone", FirstResult(classifier->ClassifyText(
                         "phone: (123) 456 789,0001112", {7, 25})));
  EXPECT_EQ("other", FirstResult(classifier->ClassifyText(
                         "phone: (123) 456 789,0001112", {7, 28})));
}

TEST_F(AnnotatorTest, SuggestSelection) {
  std::unique_ptr<Annotator> classifier = Annotator::FromPath(
      GetTestModelPath(), unilib_.get(), calendarlib_.get());
  ASSERT_TRUE(classifier);

  EXPECT_EQ(classifier->SuggestSelection(
                "this afternoon Barack Obama gave a speech at", {15, 21}),
            CodepointSpan(15, 21));

  // Try passing whole string.
  // If more than 1 token is specified, we should return back what entered.
  EXPECT_EQ(
      classifier->SuggestSelection("350 Third Street, Cambridge", {0, 27}),
      CodepointSpan(0, 27));

  // Single letter.
  EXPECT_EQ(classifier->SuggestSelection("a", {0, 1}), CodepointSpan(0, 1));

  // Single word.
  EXPECT_EQ(classifier->SuggestSelection("asdf", {0, 4}), CodepointSpan(0, 4));

  EXPECT_EQ(
      classifier->SuggestSelection("call me at 857 225 3556 today", {11, 14}),
      CodepointSpan(11, 23));

  // Unpaired bracket stripping.
  EXPECT_EQ(
      classifier->SuggestSelection("call me at (857) 225 3556 today", {12, 14}),
      CodepointSpan(11, 25));
  EXPECT_EQ(classifier->SuggestSelection("call me at (857 today", {12, 14}),
            CodepointSpan(12, 15));
  EXPECT_EQ(classifier->SuggestSelection("call me at 3556) today", {12, 14}),
            CodepointSpan(11, 15));
  EXPECT_EQ(classifier->SuggestSelection("call me at )857( today", {12, 14}),
            CodepointSpan(12, 15));

  // If the resulting selection would be empty, the original span is returned.
  EXPECT_EQ(classifier->SuggestSelection("call me at )( today", {11, 13}),
            CodepointSpan(11, 13));
  EXPECT_EQ(classifier->SuggestSelection("call me at ( today", {11, 12}),
            CodepointSpan(11, 12));
  EXPECT_EQ(classifier->SuggestSelection("call me at ) today", {11, 12}),
            CodepointSpan(11, 12));

  // If the original span is larger than the found selection, the original span
  // is returned.
  EXPECT_EQ(
      classifier->SuggestSelection("call me at 857 225 3556 today", {5, 24}),
      CodepointSpan(5, 24));
}

TEST_F(AnnotatorTest, SuggestSelectionDisabledFail) {
  const std::string test_model = ReadFile(GetTestModelPath());
  std::unique_ptr<ModelT> unpacked_model = UnPackModel(test_model.c_str());

  // Disable the selection model.
  unpacked_model->selection_model.clear();
  unpacked_model->triggering_options.reset(new ModelTriggeringOptionsT);
  unpacked_model->triggering_options->enabled_modes = ModeFlag_ANNOTATION;

  flatbuffers::FlatBufferBuilder builder;
  FinishModelBuffer(builder, Model::Pack(builder, unpacked_model.get()));

  std::unique_ptr<Annotator> classifier = Annotator::FromUnownedBuffer(
      reinterpret_cast<const char*>(builder.GetBufferPointer()),
      builder.GetSize(), unilib_.get(), calendarlib_.get());
  // Selection model needs to be present for annotation.
  ASSERT_FALSE(classifier);
}

TEST_F(AnnotatorTest, SuggestSelectionDisabled) {
  const std::string test_model = ReadFile(GetTestModelPath());
  std::unique_ptr<ModelT> unpacked_model = UnPackModel(test_model.c_str());

  // Disable the selection model.
  unpacked_model->selection_model.clear();
  unpacked_model->triggering_options.reset(new ModelTriggeringOptionsT);
  unpacked_model->triggering_options->enabled_modes = ModeFlag_CLASSIFICATION;
  unpacked_model->enabled_modes = ModeFlag_CLASSIFICATION;

  // Disable the number annotator. With the selection model disabled, there is
  // no feature processor, which is required for the number annotator.
  unpacked_model->number_annotator_options->enabled = false;

  flatbuffers::FlatBufferBuilder builder;
  FinishModelBuffer(builder, Model::Pack(builder, unpacked_model.get()));

  std::unique_ptr<Annotator> classifier = Annotator::FromUnownedBuffer(
      reinterpret_cast<const char*>(builder.GetBufferPointer()),
      builder.GetSize(), unilib_.get(), calendarlib_.get());
  ASSERT_TRUE(classifier);

  EXPECT_EQ(
      classifier->SuggestSelection("call me at 857 225 3556 today", {11, 14}),
      CodepointSpan(11, 14));

  EXPECT_EQ("phone", FirstResult(classifier->ClassifyText(
                         "call me at (800) 123-456 today", {11, 24})));

  EXPECT_THAT(classifier->Annotate("call me at (800) 123-456 today"),
              IsEmpty());
}

TEST_F(AnnotatorTest, SuggestSelectionFilteredCollections) {
  const std::string test_model = ReadFile(GetTestModelPath());

  std::unique_ptr<Annotator> classifier = Annotator::FromUnownedBuffer(
      test_model.c_str(), test_model.size(), unilib_.get(), calendarlib_.get());
  ASSERT_TRUE(classifier);

  EXPECT_EQ(
      classifier->SuggestSelection("call me at 857 225 3556 today", {11, 14}),
      CodepointSpan(11, 23));

  std::unique_ptr<ModelT> unpacked_model = UnPackModel(test_model.c_str());
  unpacked_model->output_options.reset(new OutputOptionsT);

  // Disable phone selection
  unpacked_model->output_options->filtered_collections_selection.push_back(
      "phone");
  // We need to force this for filtering.
  unpacked_model->selection_options->always_classify_suggested_selection = true;

  flatbuffers::FlatBufferBuilder builder;
  FinishModelBuffer(builder, Model::Pack(builder, unpacked_model.get()));

  classifier = Annotator::FromUnownedBuffer(
      reinterpret_cast<const char*>(builder.GetBufferPointer()),
      builder.GetSize(), unilib_.get(), calendarlib_.get());
  ASSERT_TRUE(classifier);

  EXPECT_EQ(
      classifier->SuggestSelection("call me at 857 225 3556 today", {11, 14}),
      CodepointSpan(11, 14));

  // Address selection should still work.
  EXPECT_EQ(classifier->SuggestSelection("350 Third Street, Cambridge", {4, 9}),
            CodepointSpan(0, 27));
}

TEST_F(AnnotatorTest, SuggestSelectionsAreSymmetric) {
  std::unique_ptr<Annotator> classifier = Annotator::FromPath(
      GetTestModelPath(), unilib_.get(), calendarlib_.get());
  ASSERT_TRUE(classifier);

  EXPECT_EQ(classifier->SuggestSelection("350 Third Street, Cambridge", {0, 3}),
            CodepointSpan(0, 27));
  EXPECT_EQ(classifier->SuggestSelection("350 Third Street, Cambridge", {4, 9}),
            CodepointSpan(0, 27));
  EXPECT_EQ(
      classifier->SuggestSelection("350 Third Street, Cambridge", {10, 16}),
      CodepointSpan(0, 27));
  EXPECT_EQ(classifier->SuggestSelection("a\nb\nc\n350 Third Street, Cambridge",
                                         {16, 22}),
            CodepointSpan(6, 33));
}

TEST_F(AnnotatorTest, SuggestSelectionWithNewLine) {
  std::unique_ptr<Annotator> classifier = Annotator::FromPath(
      GetTestModelPath(), unilib_.get(), calendarlib_.get());
  ASSERT_TRUE(classifier);

  EXPECT_EQ(classifier->SuggestSelection("abc\n857 225 3556", {4, 7}),
            CodepointSpan(4, 16));
  EXPECT_EQ(classifier->SuggestSelection("857 225 3556\nabc", {0, 3}),
            CodepointSpan(0, 12));

  SelectionOptions options;
  EXPECT_EQ(classifier->SuggestSelection("857 225\n3556\nabc", {0, 3}, options),
            CodepointSpan(0, 12));
}

TEST_F(AnnotatorTest, SuggestSelectionWithPunctuation) {
  std::unique_ptr<Annotator> classifier = Annotator::FromPath(
      GetTestModelPath(), unilib_.get(), calendarlib_.get());
  ASSERT_TRUE(classifier);

  // From the right.
  EXPECT_EQ(classifier->SuggestSelection(
                "this afternoon BarackObama, gave a speech at", {15, 26}),
            CodepointSpan(15, 26));

  // From the right multiple.
  EXPECT_EQ(classifier->SuggestSelection(
                "this afternoon BarackObama,.,.,, gave a speech at", {15, 26}),
            CodepointSpan(15, 26));

  // From the left multiple.
  EXPECT_EQ(classifier->SuggestSelection(
                "this afternoon ,.,.,,BarackObama gave a speech at", {21, 32}),
            CodepointSpan(21, 32));

  // From both sides.
  EXPECT_EQ(classifier->SuggestSelection(
                "this afternoon !BarackObama,- gave a speech at", {16, 27}),
            CodepointSpan(16, 27));
}

TEST_F(AnnotatorTest, SuggestSelectionNoCrashWithJunk) {
  std::unique_ptr<Annotator> classifier = Annotator::FromPath(
      GetTestModelPath(), unilib_.get(), calendarlib_.get());
  ASSERT_TRUE(classifier);

  // Try passing in bunch of invalid selections.
  EXPECT_EQ(classifier->SuggestSelection("", {0, 27}), CodepointSpan(0, 27));
  EXPECT_EQ(classifier->SuggestSelection("", {-10, 27}),
            CodepointSpan(-10, 27));
  EXPECT_EQ(classifier->SuggestSelection("Word 1 2 3 hello!", {0, 27}),
            CodepointSpan(0, 27));
  EXPECT_EQ(classifier->SuggestSelection("Word 1 2 3 hello!", {-30, 300}),
            CodepointSpan(-30, 300));
  EXPECT_EQ(classifier->SuggestSelection("Word 1 2 3 hello!", {-10, -1}),
            CodepointSpan(-10, -1));
  EXPECT_EQ(classifier->SuggestSelection("Word 1 2 3 hello!", {100, 17}),
            CodepointSpan(100, 17));

  // Try passing invalid utf8.
  EXPECT_EQ(classifier->SuggestSelection("\xf0\x9f\x98\x8b\x8b", {-1, -1}),
            CodepointSpan(-1, -1));
}

TEST_F(AnnotatorTest, SuggestSelectionSelectSpace) {
  std::unique_ptr<Annotator> classifier = Annotator::FromPath(
      GetTestModelPath(), unilib_.get(), calendarlib_.get());
  ASSERT_TRUE(classifier);

  EXPECT_EQ(
      classifier->SuggestSelection("call me at 857 225 3556 today", {14, 15}),
      CodepointSpan(11, 23));
  EXPECT_EQ(
      classifier->SuggestSelection("call me at 857 225 3556 today", {10, 11}),
      CodepointSpan(10, 11));
  EXPECT_EQ(
      classifier->SuggestSelection("call me at 857 225 3556 today", {23, 24}),
      CodepointSpan(23, 24));
  EXPECT_EQ(
      classifier->SuggestSelection("call me at 857 225 3556, today", {23, 24}),
      CodepointSpan(23, 24));
  EXPECT_EQ(classifier->SuggestSelection("call me at 857   225 3556, today",
                                         {14, 17}),
            CodepointSpan(11, 25));
  EXPECT_EQ(
      classifier->SuggestSelection("call me at 857-225 3556, today", {14, 17}),
      CodepointSpan(11, 23));
  EXPECT_EQ(
      classifier->SuggestSelection(
          "let's meet at 350 Third Street Cambridge and go there", {30, 31}),
      CodepointSpan(14, 40));
  EXPECT_EQ(classifier->SuggestSelection("call me today", {4, 5}),
            CodepointSpan(4, 5));
  EXPECT_EQ(classifier->SuggestSelection("call me today", {7, 8}),
            CodepointSpan(7, 8));

  // With a punctuation around the selected whitespace.
  EXPECT_EQ(
      classifier->SuggestSelection(
          "let's meet at 350 Third Street, Cambridge and go there", {31, 32}),
      CodepointSpan(14, 41));

  // When all's whitespace, should return the original indices.
  EXPECT_EQ(classifier->SuggestSelection("      ", {0, 1}),
            CodepointSpan(0, 1));
  EXPECT_EQ(classifier->SuggestSelection("      ", {0, 3}),
            CodepointSpan(0, 3));
  EXPECT_EQ(classifier->SuggestSelection("      ", {2, 3}),
            CodepointSpan(2, 3));
  EXPECT_EQ(classifier->SuggestSelection("      ", {5, 6}),
            CodepointSpan(5, 6));
}

TEST_F(AnnotatorTest, SnapLeftIfWhitespaceSelection) {
  UnicodeText text;

  text = UTF8ToUnicodeText("abcd efgh", /*do_copy=*/false);
  EXPECT_EQ(internal::SnapLeftIfWhitespaceSelection({4, 5}, text, *unilib_),
            CodepointSpan(3, 4));
  text = UTF8ToUnicodeText("abcd     ", /*do_copy=*/false);
  EXPECT_EQ(internal::SnapLeftIfWhitespaceSelection({4, 5}, text, *unilib_),
            CodepointSpan(3, 4));

  // Nothing on the left.
  text = UTF8ToUnicodeText("     efgh", /*do_copy=*/false);
  EXPECT_EQ(internal::SnapLeftIfWhitespaceSelection({4, 5}, text, *unilib_),
            CodepointSpan(4, 5));
  text = UTF8ToUnicodeText("     efgh", /*do_copy=*/false);
  EXPECT_EQ(internal::SnapLeftIfWhitespaceSelection({0, 1}, text, *unilib_),
            CodepointSpan(0, 1));

  // Whitespace only.
  text = UTF8ToUnicodeText("     ", /*do_copy=*/false);
  EXPECT_EQ(internal::SnapLeftIfWhitespaceSelection({2, 3}, text, *unilib_),
            CodepointSpan(2, 3));
  text = UTF8ToUnicodeText("     ", /*do_copy=*/false);
  EXPECT_EQ(internal::SnapLeftIfWhitespaceSelection({4, 5}, text, *unilib_),
            CodepointSpan(4, 5));
  text = UTF8ToUnicodeText("     ", /*do_copy=*/false);
  EXPECT_EQ(internal::SnapLeftIfWhitespaceSelection({0, 1}, text, *unilib_),
            CodepointSpan(0, 1));
}

TEST_F(AnnotatorTest, Annotate) {
  std::unique_ptr<Annotator> classifier = Annotator::FromPath(
      GetTestModelPath(), unilib_.get(), calendarlib_.get());
  ASSERT_TRUE(classifier);

  const std::string test_string =
      "& saw Barack Obama today .. 350 Third Street, Cambridge\nand my phone "
      "number is 853 225 3556";
  EXPECT_THAT(classifier->Annotate(test_string),
              ElementsAreArray({
                  IsAnnotatedSpan(28, 55, "address"),
                  IsAnnotatedSpan(79, 91, "phone"),
              }));

  AnnotationOptions options;
  EXPECT_THAT(classifier->Annotate("853 225 3556", options),
              ElementsAreArray({IsAnnotatedSpan(0, 12, "phone")}));
  EXPECT_THAT(classifier->Annotate("853 225\n3556", options),
              ElementsAreArray({IsAnnotatedSpan(0, 12, "phone")}));
  // Try passing invalid utf8.
  EXPECT_TRUE(
      classifier->Annotate("853 225 3556\n\xf0\x9f\x98\x8b\x8b", options)
          .empty());
}

TEST_F(AnnotatorTest, AnnotatesWithBracketStripping) {
  std::unique_ptr<Annotator> classifier = Annotator::FromPath(
      GetTestModelPath(), unilib_.get(), calendarlib_.get());
  ASSERT_TRUE(classifier);

  EXPECT_THAT(classifier->Annotate("call me at (0845) 100 1000 today"),
              ElementsAreArray({
                  IsAnnotatedSpan(11, 26, "phone"),
              }));

  // Unpaired bracket stripping.
  EXPECT_THAT(classifier->Annotate("call me at (07038201818 today"),
              ElementsAreArray({
                  IsAnnotatedSpan(12, 23, "phone"),
              }));
  EXPECT_THAT(classifier->Annotate("call me at 07038201818) today"),
              ElementsAreArray({
                  IsAnnotatedSpan(11, 22, "phone"),
              }));
  EXPECT_THAT(classifier->Annotate("call me at )07038201818( today"),
              ElementsAreArray({
                  IsAnnotatedSpan(12, 23, "phone"),
              }));
}

TEST_F(AnnotatorTest, AnnotatesOverlappingNumbers) {
  std::unique_ptr<Annotator> classifier = Annotator::FromPath(
      GetTestModelPath(), unilib_.get(), calendarlib_.get());
  ASSERT_TRUE(classifier);
  AnnotationOptions options;
  options.annotation_usecase = AnnotationUsecase_ANNOTATION_USECASE_RAW;

  // Number, float number and percentage annotator.
  EXPECT_THAT(
      classifier->Annotate("853 225 3556 and then turn it up 99%, 99 "
                           "number, 12345.12345 float number",
                           options),
      UnorderedElementsAreArray(
          {IsAnnotatedSpan(0, 12, "phone"), IsAnnotatedSpan(0, 3, "number"),
           IsAnnotatedSpan(4, 7, "number"), IsAnnotatedSpan(8, 12, "number"),
           IsAnnotatedSpan(33, 35, "number"),
           IsAnnotatedSpan(33, 36, "percentage"),
           IsAnnotatedSpan(38, 40, "number"), IsAnnotatedSpan(49, 60, "number"),
           IsAnnotatedSpan(49, 60, "phone")}));
}

TEST_F(AnnotatorTest, DoesNotAnnotateNumbersInSmartUsecase) {
  std::unique_ptr<Annotator> classifier = Annotator::FromPath(
      GetTestModelPath(), unilib_.get(), calendarlib_.get());
  ASSERT_TRUE(classifier);
  AnnotationOptions options;
  options.annotation_usecase = AnnotationUsecase_ANNOTATION_USECASE_SMART;

  EXPECT_THAT(classifier->Annotate(
                  "853 225 3556 and then turn it up 99%, 99 number", options),
              ElementsAreArray({IsAnnotatedSpan(0, 12, "phone"),
                                IsAnnotatedSpan(33, 36, "percentage")}));
}

void VerifyAnnotatesDurationsInRawMode(const Annotator* classifier) {
  ASSERT_TRUE(classifier);
  AnnotationOptions options;
  options.annotation_usecase = AnnotationUsecase_ANNOTATION_USECASE_RAW;

  // Duration annotator.
  EXPECT_THAT(classifier->Annotate(
                  "it took 9 minutes and 7 seconds to get there", options),
              Contains(IsDurationSpan(
                  /*start=*/8, /*end=*/31,
                  /*duration_ms=*/9 * 60 * 1000 + 7 * 1000)));
}

TEST_F(AnnotatorTest, AnnotatesDurationsInRawMode) {
  std::unique_ptr<Annotator> classifier = Annotator::FromPath(
      GetTestModelPath(), unilib_.get(), calendarlib_.get());
  VerifyAnnotatesDurationsInRawMode(classifier.get());
}

void VerifyDurationAndRelativeTimeCanOverlapInRawMode(
    const Annotator* classifier) {
  ASSERT_TRUE(classifier);
  AnnotationOptions options;
  options.annotation_usecase = AnnotationUsecase_ANNOTATION_USECASE_RAW;
  options.locales = "en";

  const std::vector<AnnotatedSpan> annotations =
      classifier->Annotate("let's meet in 3 hours", options);

  EXPECT_THAT(annotations,
              Contains(IsDatetimeSpan(/*start=*/11, /*end=*/21,
                                      /*time_ms_utc=*/10800000L,
                                      DatetimeGranularity::GRANULARITY_HOUR)));
  EXPECT_THAT(annotations,
              Contains(IsDurationSpan(/*start=*/14, /*end=*/21,
                                      /*duration_ms=*/3 * 60 * 60 * 1000)));
}

TEST_F(AnnotatorTest, DurationAndRelativeTimeCanOverlapInRawMode) {
  std::unique_ptr<Annotator> classifier = Annotator::FromPath(
      GetTestModelPath(), unilib_.get(), calendarlib_.get());
  VerifyDurationAndRelativeTimeCanOverlapInRawMode(classifier.get());
}

TEST_F(AnnotatorTest,
       DurationAndRelativeTimeCanOverlapInRawModeWithDatetimeRegEx) {
  std::string model_buffer = GetTestModelWithDatetimeRegEx();
  std::unique_ptr<Annotator> classifier =
      Annotator::FromUnownedBuffer(model_buffer.data(), model_buffer.size(),
                                   unilib_.get(), calendarlib_.get());
  VerifyDurationAndRelativeTimeCanOverlapInRawMode(classifier.get());
}

TEST_F(AnnotatorTest, AnnotateSplitLines) {
  std::string model_buffer = ReadFile(GetTestModelPath());
  model_buffer = ModifyAnnotatorModel(model_buffer, [](ModelT* model) {
    model->selection_feature_options->only_use_line_with_click = true;
  });
  std::unique_ptr<Annotator> classifier =
      Annotator::FromUnownedBuffer(model_buffer.data(), model_buffer.size(),
                                   unilib_.get(), calendarlib_.get());

  ASSERT_TRUE(classifier);

  const std::string str1 =
      "hey, sorry, just finished up. i didn't hear back from you in time.";
  const std::string str2 = "2000 Main Avenue, Apt #201, San Mateo";

  const int kAnnotationLength = 26;
  EXPECT_THAT(classifier->Annotate(str1), IsEmpty());
  EXPECT_THAT(
      classifier->Annotate(str2),
      ElementsAreArray({IsAnnotatedSpan(0, kAnnotationLength, "address")}));

  const std::string str3 = str1 + "\n" + str2;
  EXPECT_THAT(
      classifier->Annotate(str3),
      ElementsAreArray({IsAnnotatedSpan(
          str1.size() + 1, str1.size() + 1 + kAnnotationLength, "address")}));
}

TEST_F(AnnotatorTest, UsePipeAsNewLineCharacterShouldAnnotateSplitLines) {
  std::string model_buffer = ReadFile(GetTestModelPath());
  model_buffer = ModifyAnnotatorModel(model_buffer, [](ModelT* model) {
    model->selection_feature_options->only_use_line_with_click = true;
    model->selection_feature_options->use_pipe_character_for_newline = true;
  });
  std::unique_ptr<Annotator> classifier =
      Annotator::FromUnownedBuffer(model_buffer.data(), model_buffer.size(),
                                   unilib_.get(), calendarlib_.get());

  ASSERT_TRUE(classifier);

  const std::string str1 = "hey, this is my phone number 853 225 3556";
  const std::string str2 = "2000 Main Avenue, Apt #201, San Mateo";
  const std::string str3 = str1 + "|" + str2;
  const int kAnnotationLengthPhone = 12;
  const int kAnnotationLengthAddress = 26;
  // Splitting the lines on `str3` should have the same behavior (e.g. find the
  // phone and address spans) as if we would annotate `str1` and `str2`
  // individually.
  const std::vector<AnnotatedSpan>& annotated_spans =
      classifier->Annotate(str3);
  EXPECT_THAT(annotated_spans,
              ElementsAreArray(
                  {IsAnnotatedSpan(29, 29 + kAnnotationLengthPhone, "phone"),
                   IsAnnotatedSpan(static_cast<int>(str1.size()) + 1,
                                   static_cast<int>(str1.size() + 1 +
                                                    kAnnotationLengthAddress),
                                   "address")}));
}

TEST_F(AnnotatorTest,
       NotUsingPipeAsNewLineCharacterShouldNotAnnotateSplitLines) {
  std::string model_buffer = ReadFile(GetTestModelPath());
  model_buffer = ModifyAnnotatorModel(model_buffer, [](ModelT* model) {
    model->selection_feature_options->only_use_line_with_click = true;
    model->selection_feature_options->use_pipe_character_for_newline = false;
  });
  std::unique_ptr<Annotator> classifier =
      Annotator::FromUnownedBuffer(model_buffer.data(), model_buffer.size(),
                                   unilib_.get(), calendarlib_.get());

  ASSERT_TRUE(classifier);

  const std::string str1 = "hey, this is my phone number 853 225 3556";
  const std::string str2 = "2000 Main Avenue, Apt #201, San Mateo";
  const std::string str3 = str1 + "|" + str2;
  const std::vector<AnnotatedSpan>& annotated_spans =
      classifier->Annotate(str3);
  // Note: We only check that we get a single annotated span here when the '|'
  // character is not used to split lines. The reason behind this is that the
  // model is not precise for such example and the resulted annotated span might
  // change when the model changes.
  EXPECT_THAT(annotated_spans.size(), 1);
}

TEST_F(AnnotatorTest, AnnotateSmallBatches) {
  const std::string test_model = ReadFile(GetTestModelPath());
  std::unique_ptr<ModelT> unpacked_model = UnPackModel(test_model.c_str());

  // Set the batch size.
  unpacked_model->selection_options->batch_size = 4;
  flatbuffers::FlatBufferBuilder builder;
  FinishModelBuffer(builder, Model::Pack(builder, unpacked_model.get()));

  std::unique_ptr<Annotator> classifier = Annotator::FromUnownedBuffer(
      reinterpret_cast<const char*>(builder.GetBufferPointer()),
      builder.GetSize(), unilib_.get(), calendarlib_.get());
  ASSERT_TRUE(classifier);

  const std::string test_string =
      "& saw Barack Obama today .. 350 Third Street, Cambridge\nand my phone "
      "number is 853 225 3556";
  EXPECT_THAT(classifier->Annotate(test_string),
              ElementsAreArray({
                  IsAnnotatedSpan(28, 55, "address"),
                  IsAnnotatedSpan(79, 91, "phone"),
              }));

  AnnotationOptions options;
  EXPECT_THAT(classifier->Annotate("853 225 3556", options),
              ElementsAreArray({IsAnnotatedSpan(0, 12, "phone")}));
  EXPECT_THAT(classifier->Annotate("853 225\n3556", options),
              ElementsAreArray({IsAnnotatedSpan(0, 12, "phone")}));
}

TEST_F(AnnotatorTest, AnnotateFilteringDiscardAll) {
  const std::string test_model = ReadFile(GetTestModelPath());
  std::unique_ptr<ModelT> unpacked_model = UnPackModel(test_model.c_str());

  unpacked_model->triggering_options.reset(new ModelTriggeringOptionsT);
  // Add test threshold.
  unpacked_model->triggering_options->min_annotate_confidence =
      2.f;  // Discards all results.
  flatbuffers::FlatBufferBuilder builder;
  FinishModelBuffer(builder, Model::Pack(builder, unpacked_model.get()));

  std::unique_ptr<Annotator> classifier = Annotator::FromUnownedBuffer(
      reinterpret_cast<const char*>(builder.GetBufferPointer()),
      builder.GetSize(), unilib_.get(), calendarlib_.get());
  ASSERT_TRUE(classifier);

  const std::string test_string =
      "& saw Barack Obama today .. 350 Third Street, Cambridge\nand my phone "
      "number is 853 225 3556";

  EXPECT_EQ(classifier->Annotate(test_string).size(), 0);
}

TEST_F(AnnotatorTest, AnnotateFilteringKeepAll) {
  const std::string test_model = ReadFile(GetTestModelPath());
  std::unique_ptr<ModelT> unpacked_model = UnPackModel(test_model.c_str());

  // Add test thresholds.
  unpacked_model->triggering_options.reset(new ModelTriggeringOptionsT);
  unpacked_model->triggering_options->min_annotate_confidence =
      0.f;  // Keeps all results.
  unpacked_model->triggering_options->enabled_modes = ModeFlag_ALL;
  flatbuffers::FlatBufferBuilder builder;
  FinishModelBuffer(builder, Model::Pack(builder, unpacked_model.get()));

  std::unique_ptr<Annotator> classifier = Annotator::FromUnownedBuffer(
      reinterpret_cast<const char*>(builder.GetBufferPointer()),
      builder.GetSize(), unilib_.get(), calendarlib_.get());
  ASSERT_TRUE(classifier);

  const std::string test_string =
      "& saw Barack Obama today .. 350 Third Street, Cambridge\nand my phone "
      "number is 853 225 3556";
  EXPECT_EQ(classifier->Annotate(test_string).size(), 2);
}

TEST_F(AnnotatorTest, AnnotateDisabled) {
  const std::string test_model = ReadFile(GetTestModelPath());
  std::unique_ptr<ModelT> unpacked_model = UnPackModel(test_model.c_str());

  // Disable the model for annotation.
  unpacked_model->enabled_modes = ModeFlag_CLASSIFICATION_AND_SELECTION;
  flatbuffers::FlatBufferBuilder builder;
  FinishModelBuffer(builder, Model::Pack(builder, unpacked_model.get()));

  std::unique_ptr<Annotator> classifier = Annotator::FromUnownedBuffer(
      reinterpret_cast<const char*>(builder.GetBufferPointer()),
      builder.GetSize(), unilib_.get(), calendarlib_.get());
  ASSERT_TRUE(classifier);
  const std::string test_string =
      "& saw Barack Obama today .. 350 Third Street, Cambridge\nand my phone "
      "number is 853 225 3556";
  EXPECT_THAT(classifier->Annotate(test_string), IsEmpty());
}

TEST_F(AnnotatorTest, AnnotateFilteredCollections) {
  const std::string test_model = ReadFile(GetTestModelPath());

  std::unique_ptr<Annotator> classifier = Annotator::FromUnownedBuffer(
      test_model.c_str(), test_model.size(), unilib_.get(), calendarlib_.get());
  ASSERT_TRUE(classifier);

  const std::string test_string =
      "& saw Barack Obama today .. 350 Third Street, Cambridge\nand my phone "
      "number is 853 225 3556";

  EXPECT_THAT(classifier->Annotate(test_string),
              ElementsAreArray({
                  IsAnnotatedSpan(28, 55, "address"),
                  IsAnnotatedSpan(79, 91, "phone"),
              }));

  std::unique_ptr<ModelT> unpacked_model = UnPackModel(test_model.c_str());
  unpacked_model->output_options.reset(new OutputOptionsT);

  // Disable phone annotation
  unpacked_model->output_options->filtered_collections_annotation.push_back(
      "phone");

  flatbuffers::FlatBufferBuilder builder;
  FinishModelBuffer(builder, Model::Pack(builder, unpacked_model.get()));

  classifier = Annotator::FromUnownedBuffer(
      reinterpret_cast<const char*>(builder.GetBufferPointer()),
      builder.GetSize(), unilib_.get(), calendarlib_.get());
  ASSERT_TRUE(classifier);

  EXPECT_THAT(classifier->Annotate(test_string),
              ElementsAreArray({
                  IsAnnotatedSpan(28, 55, "address"),
              }));
}

TEST_F(AnnotatorTest, AnnotateFilteredCollectionsSuppress) {
  const std::string test_model = ReadFile(GetTestModelPath());

  std::unique_ptr<Annotator> classifier = Annotator::FromUnownedBuffer(
      test_model.c_str(), test_model.size(), unilib_.get(), calendarlib_.get());
  ASSERT_TRUE(classifier);

  const std::string test_string =
      "& saw Barack Obama today .. 350 Third Street, Cambridge\nand my phone "
      "number is 853 225 3556";

  EXPECT_THAT(classifier->Annotate(test_string),
              ElementsAreArray({
                  IsAnnotatedSpan(28, 55, "address"),
                  IsAnnotatedSpan(79, 91, "phone"),
              }));

  std::unique_ptr<ModelT> unpacked_model = UnPackModel(test_model.c_str());
  unpacked_model->output_options.reset(new OutputOptionsT);

  // We add a custom annotator that wins against the phone classification
  // below and that we subsequently suppress.
  unpacked_model->output_options->filtered_collections_annotation.push_back(
      "suppress");

  unpacked_model->regex_model->patterns.push_back(MakePattern(
      "suppress", "(\\d{3} ?\\d{4})",
      /*enabled_for_classification=*/false,
      /*enabled_for_selection=*/false, /*enabled_for_annotation=*/true, 2.0));

  flatbuffers::FlatBufferBuilder builder;
  FinishModelBuffer(builder, Model::Pack(builder, unpacked_model.get()));

  classifier = Annotator::FromUnownedBuffer(
      reinterpret_cast<const char*>(builder.GetBufferPointer()),
      builder.GetSize(), unilib_.get(), calendarlib_.get());
  ASSERT_TRUE(classifier);

  EXPECT_THAT(classifier->Annotate(test_string),
              ElementsAreArray({
                  IsAnnotatedSpan(28, 55, "address"),
              }));
}

void VerifyClassifyTextDateInZurichTimezone(const Annotator* classifier) {
  EXPECT_TRUE(classifier);
  ClassificationOptions options;
  options.reference_timezone = "Europe/Zurich";
  options.locales = "en";

  std::vector<ClassificationResult> result =
      classifier->ClassifyText("january 1, 2017", {0, 15}, options);

  EXPECT_THAT(result,
              ElementsAre(IsDateResult(1483225200000,
                                       DatetimeGranularity::GRANULARITY_DAY)));
}

TEST_F(AnnotatorTest, ClassifyTextDateInZurichTimezone) {
  std::unique_ptr<Annotator> classifier = Annotator::FromPath(
      GetTestModelPath(), unilib_.get(), calendarlib_.get());
  VerifyClassifyTextDateInZurichTimezone(classifier.get());
}

TEST_F(AnnotatorTest, ClassifyTextDateInZurichTimezoneWithDatetimeRegEx) {
  std::string model_buffer = GetTestModelWithDatetimeRegEx();
  std::unique_ptr<Annotator> classifier =
      Annotator::FromUnownedBuffer(model_buffer.data(), model_buffer.size(),
                                   unilib_.get(), calendarlib_.get());
  VerifyClassifyTextDateInZurichTimezone(classifier.get());
}

void VerifyClassifyTextDateInLATimezone(const Annotator* classifier) {
  EXPECT_TRUE(classifier);
  ClassificationOptions options;
  options.reference_timezone = "America/Los_Angeles";
  options.locales = "en";

  std::vector<ClassificationResult> result =
      classifier->ClassifyText("march 1, 2017", {0, 13}, options);

  EXPECT_THAT(result,
              ElementsAre(IsDateResult(1488355200000,
                                       DatetimeGranularity::GRANULARITY_DAY)));
}

TEST_F(AnnotatorTest, ClassifyTextDateInLATimezoneWithDatetimeRegEx) {
  std::string model_buffer = GetTestModelWithDatetimeRegEx();
  std::unique_ptr<Annotator> classifier =
      Annotator::FromUnownedBuffer(model_buffer.data(), model_buffer.size(),
                                   unilib_.get(), calendarlib_.get());
  VerifyClassifyTextDateInLATimezone(classifier.get());
}

TEST_F(AnnotatorTest, ClassifyTextDateInLATimezone) {
  std::unique_ptr<Annotator> classifier = Annotator::FromPath(
      GetTestModelPath(), unilib_.get(), calendarlib_.get());
  VerifyClassifyTextDateInLATimezone(classifier.get());
}

void VerifyClassifyTextDateOnAotherLine(const Annotator* classifier) {
  EXPECT_TRUE(classifier);
  ClassificationOptions options;
  options.reference_timezone = "Europe/Zurich";
  options.locales = "en";

  std::vector<ClassificationResult> result = classifier->ClassifyText(
      "hello world this is the first line\n"
      "january 1, 2017",
      {35, 50}, options);

  EXPECT_THAT(result,
              ElementsAre(IsDateResult(1483225200000,
                                       DatetimeGranularity::GRANULARITY_DAY)));
}

TEST_F(AnnotatorTest, ClassifyTextDateOnAotherLineWithDatetimeRegEx) {
  std::string model_buffer = GetTestModelWithDatetimeRegEx();
  std::unique_ptr<Annotator> classifier =
      Annotator::FromUnownedBuffer(model_buffer.data(), model_buffer.size(),
                                   unilib_.get(), calendarlib_.get());
  VerifyClassifyTextDateOnAotherLine(classifier.get());
}

TEST_F(AnnotatorTest, ClassifyTextDateOnAotherLine) {
  std::unique_ptr<Annotator> classifier = Annotator::FromPath(
      GetTestModelPath(), unilib_.get(), calendarlib_.get());
  VerifyClassifyTextDateOnAotherLine(classifier.get());
}

void VerifyClassifyTextWhenLocaleUSParsesDateAsMonthDay(
    const Annotator* classifier) {
  EXPECT_TRUE(classifier);
  std::vector<ClassificationResult> result;
  ClassificationOptions options;

  options.reference_timezone = "Europe/Zurich";
  options.locales = "en-US";
  result = classifier->ClassifyText("03.05.1970 00:00am", {0, 18}, options);

  // In US, the date should be interpreted as <month>.<day>.
  EXPECT_THAT(result,
              ElementsAre(IsDatetimeResult(
                  5439600000, DatetimeGranularity::GRANULARITY_MINUTE)));
}

TEST_F(AnnotatorTest, ClassifyTextWhenLocaleUSParsesDateAsMonthDay) {
  std::unique_ptr<Annotator> classifier = Annotator::FromPath(
      GetTestModelPath(), unilib_.get(), calendarlib_.get());
  VerifyClassifyTextWhenLocaleUSParsesDateAsMonthDay(classifier.get());
}

TEST_F(AnnotatorTest,
       ClassifyTextWhenLocaleUSParsesDateAsMonthDayWithDatetimeRegEx) {
  std::string model_buffer = GetTestModelWithDatetimeRegEx();
  std::unique_ptr<Annotator> classifier =
      Annotator::FromUnownedBuffer(model_buffer.data(), model_buffer.size(),
                                   unilib_.get(), calendarlib_.get());
  VerifyClassifyTextWhenLocaleUSParsesDateAsMonthDay(classifier.get());
}

TEST_F(AnnotatorTest, ClassifyTextWhenLocaleGermanyParsesDateAsMonthDay) {
  std::string model_buffer = GetTestModelWithDatetimeRegEx();
  std::unique_ptr<Annotator> classifier =
      Annotator::FromUnownedBuffer(model_buffer.data(), model_buffer.size(),
                                   unilib_.get(), calendarlib_.get());
  EXPECT_TRUE(classifier);
  std::vector<ClassificationResult> result;
  ClassificationOptions options;

  options.reference_timezone = "Europe/Zurich";
  options.locales = "de";
  result = classifier->ClassifyText("03.05.1970 00:00vorm", {0, 20}, options);

  // In Germany, the date should be interpreted as <day>.<month>.
  EXPECT_THAT(result,
              ElementsAre(IsDatetimeResult(
                  10537200000, DatetimeGranularity::GRANULARITY_MINUTE)));
}

TEST_F(AnnotatorTest, ClassifyTextAmbiguousDatetime) {
  std::unique_ptr<Annotator> classifier = Annotator::FromPath(
      GetTestModelPath(), unilib_.get(), calendarlib_.get());
  EXPECT_TRUE(classifier);
  ClassificationOptions options;
  options.reference_timezone = "Europe/Zurich";
  options.locales = "en-US";
  const std::vector<ClassificationResult> result =
      classifier->ClassifyText("set an alarm for 10:30", {17, 22}, options);

  EXPECT_THAT(
      result,
      ElementsAre(
          IsDatetimeResult(34200000, DatetimeGranularity::GRANULARITY_MINUTE),
          IsDatetimeResult(77400000, DatetimeGranularity::GRANULARITY_MINUTE)));
}

TEST_F(AnnotatorTest, AnnotateAmbiguousDatetime) {
  std::unique_ptr<Annotator> classifier = Annotator::FromPath(
      GetTestModelPath(), unilib_.get(), calendarlib_.get());
  EXPECT_TRUE(classifier);
  AnnotationOptions options;
  options.reference_timezone = "Europe/Zurich";
  options.locales = "en-US";
  const std::vector<AnnotatedSpan> spans =
      classifier->Annotate("set an alarm for 10:30", options);

  ASSERT_EQ(spans.size(), 1);
  const std::vector<ClassificationResult> result = spans[0].classification;
  EXPECT_THAT(
      result,
      ElementsAre(
          IsDatetimeResult(34200000, DatetimeGranularity::GRANULARITY_MINUTE),
          IsDatetimeResult(77400000, DatetimeGranularity::GRANULARITY_MINUTE)));
}

TEST_F(AnnotatorTest, SuggestTextDateDisabled) {
  std::string test_model = GetTestModelWithDatetimeRegEx();
  std::unique_ptr<ModelT> unpacked_model = UnPackModel(test_model.c_str());

  // Disable the patterns for selection.
  for (int i = 0; i < unpacked_model->datetime_model->patterns.size(); i++) {
    unpacked_model->datetime_model->patterns[i]->enabled_modes =
        ModeFlag_ANNOTATION_AND_CLASSIFICATION;
  }
  flatbuffers::FlatBufferBuilder builder;
  FinishModelBuffer(builder, Model::Pack(builder, unpacked_model.get()));

  std::unique_ptr<Annotator> classifier = Annotator::FromUnownedBuffer(
      reinterpret_cast<const char*>(builder.GetBufferPointer()),
      builder.GetSize(), unilib_.get(), calendarlib_.get());
  ASSERT_TRUE(classifier);
  EXPECT_EQ("date",
            FirstResult(classifier->ClassifyText("january 1, 2017", {0, 15})));
  EXPECT_EQ(classifier->SuggestSelection("january 1, 2017", {0, 7}),
            CodepointSpan(0, 7));
  EXPECT_THAT(classifier->Annotate("january 1, 2017"),
              ElementsAreArray({IsAnnotatedSpan(0, 15, "date")}));
}

TEST_F(AnnotatorTest, AnnotatesWithGrammarModel) {
  const std::string test_model = ReadFile(GetTestModelPath());
  std::unique_ptr<ModelT> unpacked_model = UnPackModel(test_model.c_str());

  // Add test grammar model.
  unpacked_model->grammar_model.reset(new GrammarModelT);
  GrammarModelT* grammar_model = unpacked_model->grammar_model.get();
  grammar_model->tokenizer_options.reset(new GrammarTokenizerOptionsT);
  grammar_model->tokenizer_options->tokenization_type = TokenizationType_ICU;
  grammar_model->tokenizer_options->icu_preserve_whitespace_tokens = false;
  grammar_model->tokenizer_options->tokenize_on_script_change = true;

  // Add test rules.
  grammar_model->rules.reset(new grammar::RulesSetT);
  grammar::LocaleShardMap locale_shard_map =
      grammar::LocaleShardMap::CreateLocaleShardMap({""});
  grammar::Rules rules(locale_shard_map);
  rules.Add("<tv_detective>", {"jessica", "fletcher"});
  rules.Add("<tv_detective>", {"columbo"});
  rules.Add("<tv_detective>", {"magnum"});
  rules.Add(
      "<famous_person>", {"<tv_detective>"},
      /*callback=*/
      static_cast<grammar::CallbackId>(grammar::DefaultCallback::kRootRule),
      /*callback_param=*/0 /* rule classification result */);

  // Set result.
  grammar_model->rule_classification_result.emplace_back(
      new GrammarModel_::RuleClassificationResultT);
  GrammarModel_::RuleClassificationResultT* result =
      grammar_model->rule_classification_result.back().get();
  result->collection_name = "famous person";
  result->enabled_modes = ModeFlag_ALL;
  rules.Finalize().Serialize(/*include_debug_information=*/false,
                             grammar_model->rules.get());
  flatbuffers::FlatBufferBuilder builder;
  FinishModelBuffer(builder, Model::Pack(builder, unpacked_model.get()));

  std::unique_ptr<Annotator> classifier = Annotator::FromUnownedBuffer(
      reinterpret_cast<const char*>(builder.GetBufferPointer()),
      builder.GetSize(), unilib_.get(), calendarlib_.get());
  ASSERT_TRUE(classifier);

  const std::string test_string =
      "Did you see the Novel Connection episode where Jessica Fletcher helps "
      "Magnum solve the case? I thought that was with Columbo ...";

  EXPECT_THAT(classifier->Annotate(test_string),
              ElementsAre(IsAnnotatedSpan(47, 63, "famous person"),
                          IsAnnotatedSpan(70, 76, "famous person"),
                          IsAnnotatedSpan(117, 124, "famous person")));
  EXPECT_THAT(FirstResult(classifier->ClassifyText("Jessica Fletcher",
                                                   CodepointSpan{0, 16})),
              Eq("famous person"));
  EXPECT_THAT(classifier->SuggestSelection("Jessica Fletcher", {0, 7}),
              Eq(CodepointSpan{0, 16}));
}

TEST_F(AnnotatorTest, ResolveConflictsTrivial) {
  TestingAnnotator classifier(unilib_.get(), calendarlib_.get());

  std::vector<AnnotatedSpan> candidates{
      {MakeAnnotatedSpan({0, 1}, "phone", 1.0)}};
  std::vector<Locale> locales = {Locale::FromBCP47("en")};

  BaseOptions options;
  options.annotation_usecase = AnnotationUsecase_ANNOTATION_USECASE_SMART;
  std::vector<int> chosen;
  classifier.ResolveConflicts(candidates, /*context=*/"", /*cached_tokens=*/{},
                              locales, options,
                              /*interpreter_manager=*/nullptr, &chosen);
  EXPECT_THAT(chosen, ElementsAreArray({0}));
}

TEST_F(AnnotatorTest, ResolveConflictsSequence) {
  TestingAnnotator classifier(unilib_.get(), calendarlib_.get());

  std::vector<AnnotatedSpan> candidates{{
      MakeAnnotatedSpan({0, 1}, "phone", 1.0),
      MakeAnnotatedSpan({1, 2}, "phone", 1.0),
      MakeAnnotatedSpan({2, 3}, "phone", 1.0),
      MakeAnnotatedSpan({3, 4}, "phone", 1.0),
      MakeAnnotatedSpan({4, 5}, "phone", 1.0),
  }};
  std::vector<Locale> locales = {Locale::FromBCP47("en")};

  BaseOptions options;
  options.annotation_usecase = AnnotationUsecase_ANNOTATION_USECASE_SMART;
  std::vector<int> chosen;
  classifier.ResolveConflicts(candidates, /*context=*/"", /*cached_tokens=*/{},
                              locales, options,
                              /*interpreter_manager=*/nullptr, &chosen);
  EXPECT_THAT(chosen, ElementsAreArray({0, 1, 2, 3, 4}));
}

TEST_F(AnnotatorTest, ResolveConflictsThreeSpans) {
  TestingAnnotator classifier(unilib_.get(), calendarlib_.get());

  std::vector<AnnotatedSpan> candidates{{
      MakeAnnotatedSpan({0, 3}, "phone", 1.0),
      MakeAnnotatedSpan({1, 5}, "phone", 0.5),  // Looser!
      MakeAnnotatedSpan({3, 7}, "phone", 1.0),
  }};
  std::vector<Locale> locales = {Locale::FromBCP47("en")};

  BaseOptions options;
  options.annotation_usecase = AnnotationUsecase_ANNOTATION_USECASE_SMART;
  std::vector<int> chosen;
  classifier.ResolveConflicts(candidates, /*context=*/"", /*cached_tokens=*/{},
                              locales, options,
                              /*interpreter_manager=*/nullptr, &chosen);
  EXPECT_THAT(chosen, ElementsAreArray({0, 2}));
}

TEST_F(AnnotatorTest, ResolveConflictsThreeSpansReversed) {
  TestingAnnotator classifier(unilib_.get(), calendarlib_.get());

  std::vector<AnnotatedSpan> candidates{{
      MakeAnnotatedSpan({0, 3}, "phone", 0.5),  // Looser!
      MakeAnnotatedSpan({1, 5}, "phone", 1.0),
      MakeAnnotatedSpan({3, 7}, "phone", 0.6),  // Looser!
  }};
  std::vector<Locale> locales = {Locale::FromBCP47("en")};

  BaseOptions options;
  options.annotation_usecase = AnnotationUsecase_ANNOTATION_USECASE_SMART;
  std::vector<int> chosen;
  classifier.ResolveConflicts(candidates, /*context=*/"", /*cached_tokens=*/{},
                              locales, options,
                              /*interpreter_manager=*/nullptr, &chosen);
  EXPECT_THAT(chosen, ElementsAreArray({1}));
}

TEST_F(AnnotatorTest, DoesNotPrioritizeLongerSpanWhenDoingConflictResolution) {
  TestingAnnotator classifier(unilib_.get(), calendarlib_.get());

  std::vector<AnnotatedSpan> candidates{{
      MakeAnnotatedSpan({3, 7}, "unit", 1),
      MakeAnnotatedSpan({5, 13}, "unit", 1),  // Looser!
      MakeAnnotatedSpan({5, 30}, "url", 1),   // Looser!
      MakeAnnotatedSpan({14, 20}, "email", 1),
  }};
  std::vector<Locale> locales = {Locale::FromBCP47("en")};

  BaseOptions options;
  options.annotation_usecase = AnnotationUsecase_ANNOTATION_USECASE_SMART;
  std::vector<int> chosen;
  classifier.ResolveConflicts(candidates, /*context=*/"", /*cached_tokens=*/{},
                              locales, options,
                              /*interpreter_manager=*/nullptr, &chosen);
  // Picks the first and the last annotations because they do not overlap.
  EXPECT_THAT(chosen, ElementsAreArray({0, 3}));
}

TEST_F(AnnotatorTest, PrioritizeLongerSpanWhenDoingConflictResolution) {
  const std::string test_model = ReadFile(GetTestModelPath());
  std::unique_ptr<ModelT> unpacked_model = UnPackModel(test_model.c_str());
  TC3_CHECK(libtextclassifier3::DecompressModel(unpacked_model.get()));
  unpacked_model->conflict_resolution_options.reset(
      new Model_::ConflictResolutionOptionsT);
  unpacked_model->conflict_resolution_options->prioritize_longest_annotation =
      true;

  flatbuffers::FlatBufferBuilder builder;
  FinishModelBuffer(builder, Model::Pack(builder, unpacked_model.get()));

  std::unique_ptr<TestingAnnotator> classifier =
      TestingAnnotator::FromUnownedBuffer(
          reinterpret_cast<const char*>(builder.GetBufferPointer()),
          builder.GetSize(), unilib_.get(), calendarlib_.get());
  TC3_CHECK(classifier != nullptr);

  std::vector<AnnotatedSpan> candidates{{
      MakeAnnotatedSpan({3, 7}, "unit", 1),     // Looser!
      MakeAnnotatedSpan({5, 13}, "unit", 1),    // Looser!
      MakeAnnotatedSpan({5, 30}, "url", 1),     // Pick longest match.
      MakeAnnotatedSpan({14, 20}, "email", 1),  // Looser!
  }};
  std::vector<Locale> locales = {Locale::FromBCP47("en")};

  BaseOptions options;
  options.annotation_usecase = AnnotationUsecase_ANNOTATION_USECASE_SMART;
  std::vector<int> chosen;
  classifier->ResolveConflicts(candidates, /*context=*/"", /*cached_tokens=*/{},
                               locales, options,
                               /*interpreter_manager=*/nullptr, &chosen);
  EXPECT_THAT(chosen, ElementsAreArray({2}));
}

TEST_F(AnnotatorTest, ResolveConflictsFiveSpans) {
  TestingAnnotator classifier(unilib_.get(), calendarlib_.get());

  std::vector<AnnotatedSpan> candidates{{
      MakeAnnotatedSpan({0, 3}, "phone", 0.5),
      MakeAnnotatedSpan({1, 5}, "other", 1.0),  // Looser!
      MakeAnnotatedSpan({3, 7}, "phone", 0.6),
      MakeAnnotatedSpan({8, 12}, "phone", 0.6),  // Looser!
      MakeAnnotatedSpan({11, 15}, "phone", 0.9),
  }};
  std::vector<Locale> locales = {Locale::FromBCP47("en")};

  BaseOptions options;
  options.annotation_usecase = AnnotationUsecase_ANNOTATION_USECASE_SMART;
  std::vector<int> chosen;
  classifier.ResolveConflicts(candidates, /*context=*/"", /*cached_tokens=*/{},
                              locales, options,
                              /*interpreter_manager=*/nullptr, &chosen);
  EXPECT_THAT(chosen, ElementsAreArray({0, 2, 4}));
}

TEST_F(AnnotatorTest, ResolveConflictsRawModeOverlapsAllowedKnowledgeFirst) {
  TestingAnnotator classifier(unilib_.get(), calendarlib_.get());

  std::vector<AnnotatedSpan> candidates{{
      MakeAnnotatedSpan({0, 15}, "entity", 0.7,
                        AnnotatedSpan::Source::KNOWLEDGE),
      MakeAnnotatedSpan({5, 10}, "address", 0.6),
  }};
  std::vector<Locale> locales = {Locale::FromBCP47("en")};

  BaseOptions options;
  options.annotation_usecase = AnnotationUsecase_ANNOTATION_USECASE_RAW;
  std::vector<int> chosen;
  classifier.ResolveConflicts(candidates, /*context=*/"", /*cached_tokens=*/{},
                              locales, options,
                              /*interpreter_manager=*/nullptr, &chosen);
  EXPECT_THAT(chosen, ElementsAreArray({0, 1}));
}

TEST_F(AnnotatorTest, ResolveConflictsRawModeOverlapsAllowedKnowledgeSecond) {
  TestingAnnotator classifier(unilib_.get(), calendarlib_.get());

  std::vector<AnnotatedSpan> candidates{{
      MakeAnnotatedSpan({0, 15}, "address", 0.7),
      MakeAnnotatedSpan({5, 10}, "entity", 0.6,
                        AnnotatedSpan::Source::KNOWLEDGE),
  }};
  std::vector<Locale> locales = {Locale::FromBCP47("en")};

  BaseOptions options;
  options.annotation_usecase = AnnotationUsecase_ANNOTATION_USECASE_RAW;
  std::vector<int> chosen;
  classifier.ResolveConflicts(candidates, /*context=*/"", /*cached_tokens=*/{},
                              locales, options,
                              /*interpreter_manager=*/nullptr, &chosen);
  EXPECT_THAT(chosen, ElementsAreArray({0, 1}));
}

TEST_F(AnnotatorTest, ResolveConflictsRawModeOverlapsAllowedBothKnowledge) {
  TestingAnnotator classifier(unilib_.get(), calendarlib_.get());

  std::vector<AnnotatedSpan> candidates{{
      MakeAnnotatedSpan({0, 15}, "entity", 0.7,
                        AnnotatedSpan::Source::KNOWLEDGE),
      MakeAnnotatedSpan({5, 10}, "entity", 0.6,
                        AnnotatedSpan::Source::KNOWLEDGE),
  }};
  std::vector<Locale> locales = {Locale::FromBCP47("en")};

  BaseOptions options;
  options.annotation_usecase = AnnotationUsecase_ANNOTATION_USECASE_RAW;
  std::vector<int> chosen;
  classifier.ResolveConflicts(candidates, /*context=*/"", /*cached_tokens=*/{},
                              locales, options,
                              /*interpreter_manager=*/nullptr, &chosen);
  EXPECT_THAT(chosen, ElementsAreArray({0, 1}));
}

TEST_F(AnnotatorTest, ResolveConflictsRawModeOverlapsNotAllowed) {
  TestingAnnotator classifier(unilib_.get(), calendarlib_.get());

  std::vector<AnnotatedSpan> candidates{{
      MakeAnnotatedSpan({0, 15}, "address", 0.7),
      MakeAnnotatedSpan({5, 10}, "date", 0.6),
  }};
  std::vector<Locale> locales = {Locale::FromBCP47("en")};

  BaseOptions options;
  options.annotation_usecase = AnnotationUsecase_ANNOTATION_USECASE_RAW;
  std::vector<int> chosen;
  classifier.ResolveConflicts(candidates, /*context=*/"", /*cached_tokens=*/{},
                              locales, options,
                              /*interpreter_manager=*/nullptr, &chosen);
  EXPECT_THAT(chosen, ElementsAreArray({0}));
}

TEST_F(AnnotatorTest, ResolveConflictsRawModeGeneralOverlapsAllowed) {
  TestingAnnotator classifier(
      unilib_.get(), calendarlib_.get(), [](ModelT* model) {
        model->conflict_resolution_options.reset(
            new Model_::ConflictResolutionOptionsT);
        model->conflict_resolution_options->do_conflict_resolution_in_raw_mode =
            false;
      });

  std::vector<AnnotatedSpan> candidates{{
      MakeAnnotatedSpan({0, 15}, "address", 0.7),
      MakeAnnotatedSpan({5, 10}, "date", 0.6),
  }};
  std::vector<Locale> locales = {Locale::FromBCP47("en")};

  BaseOptions options;
  options.annotation_usecase = AnnotationUsecase_ANNOTATION_USECASE_RAW;
  std::vector<int> chosen;
  classifier.ResolveConflicts(candidates, /*context=*/"", /*cached_tokens=*/{},
                              locales, options,
                              /*interpreter_manager=*/nullptr, &chosen);
  EXPECT_THAT(chosen, ElementsAreArray({0, 1}));
}

void VerifyLongInput(const Annotator* classifier) {
  ASSERT_TRUE(classifier);

  for (const auto& type_value_pair :
       std::vector<std::pair<std::string, std::string>>{
           {"address", "350 Third Street, Cambridge"},
           {"phone", "123 456-7890"},
           {"url", "www.google.com"},
           {"email", "someone@gmail.com"},
           {"flight", "LX 38"},
           {"date", "September 1, 2018"}}) {
    const std::string input_100k = std::string(50000, ' ') +
                                   type_value_pair.second +
                                   std::string(50000, ' ');
    const int value_length = type_value_pair.second.size();

    AnnotationOptions annotation_options;
    annotation_options.locales = "en";
    EXPECT_THAT(classifier->Annotate(input_100k, annotation_options),
                ElementsAreArray({IsAnnotatedSpan(50000, 50000 + value_length,
                                                  type_value_pair.first)}));
    SelectionOptions selection_options;
    selection_options.locales = "en";
    EXPECT_EQ(classifier->SuggestSelection(input_100k, {50000, 50001},
                                           selection_options),
              CodepointSpan(50000, 50000 + value_length));

    ClassificationOptions classification_options;
    classification_options.locales = "en";
    EXPECT_EQ(type_value_pair.first,
              FirstResult(classifier->ClassifyText(
                  input_100k, {50000, 50000 + value_length},
                  classification_options)));
  }
}

TEST_F(AnnotatorTest, LongInput) {
  std::unique_ptr<Annotator> classifier = Annotator::FromPath(
      GetTestModelPath(), unilib_.get(), calendarlib_.get());
  VerifyLongInput(classifier.get());
}

TEST_F(AnnotatorTest, LongInputWithRegExDatetime) {
  std::string model_buffer = GetTestModelWithDatetimeRegEx();
  std::unique_ptr<Annotator> classifier =
      Annotator::FromUnownedBuffer(model_buffer.data(), model_buffer.size(),
                                   unilib_.get(), calendarlib_.get());
  VerifyLongInput(classifier.get());
}

// These coarse tests are there only to make sure the execution happens in
// reasonable amount of time.
TEST_F(AnnotatorTest, LongInputNoResultCheck) {
  std::unique_ptr<Annotator> classifier = Annotator::FromPath(
      GetTestModelPath(), unilib_.get(), calendarlib_.get());
  ASSERT_TRUE(classifier);

  for (const std::string& value :
       std::vector<std::string>{"http://www.aaaaaaaaaaaaaaaaaaaa.com "}) {
    const std::string input_100k =
        std::string(50000, ' ') + value + std::string(50000, ' ');
    const int value_length = value.size();

    classifier->Annotate(input_100k);
    classifier->SuggestSelection(input_100k, {50000, 50001});
    classifier->ClassifyText(input_100k, {50000, 50000 + value_length});
  }
}

TEST_F(AnnotatorTest, MaxTokenLength) {
  const std::string test_model = ReadFile(GetTestModelPath());
  std::unique_ptr<ModelT> unpacked_model = UnPackModel(test_model.c_str());

  std::unique_ptr<Annotator> classifier;

  // With unrestricted number of tokens should behave normally.
  unpacked_model->classification_options->max_num_tokens = -1;

  flatbuffers::FlatBufferBuilder builder;
  FinishModelBuffer(builder, Model::Pack(builder, unpacked_model.get()));
  classifier = Annotator::FromUnownedBuffer(
      reinterpret_cast<const char*>(builder.GetBufferPointer()),
      builder.GetSize(), unilib_.get(), calendarlib_.get());
  ASSERT_TRUE(classifier);

  EXPECT_EQ(FirstResult(classifier->ClassifyText(
                "I live at 350 Third Street, Cambridge.", {10, 37})),
            "address");

  // Raise the maximum number of tokens to suppress the classification.
  unpacked_model->classification_options->max_num_tokens = 3;

  flatbuffers::FlatBufferBuilder builder2;
  FinishModelBuffer(builder2, Model::Pack(builder2, unpacked_model.get()));
  classifier = Annotator::FromUnownedBuffer(
      reinterpret_cast<const char*>(builder2.GetBufferPointer()),
      builder2.GetSize(), unilib_.get(), calendarlib_.get());
  ASSERT_TRUE(classifier);

  EXPECT_EQ(FirstResult(classifier->ClassifyText(
                "I live at 350 Third Street, Cambridge.", {10, 37})),
            "other");
}

TEST_F(AnnotatorTest, MinAddressTokenLength) {
  const std::string test_model = ReadFile(GetTestModelPath());
  std::unique_ptr<ModelT> unpacked_model = UnPackModel(test_model.c_str());

  std::unique_ptr<Annotator> classifier;

  // With unrestricted number of address tokens should behave normally.
  unpacked_model->classification_options->address_min_num_tokens = 0;

  flatbuffers::FlatBufferBuilder builder;
  FinishModelBuffer(builder, Model::Pack(builder, unpacked_model.get()));
  classifier = Annotator::FromUnownedBuffer(
      reinterpret_cast<const char*>(builder.GetBufferPointer()),
      builder.GetSize(), unilib_.get(), calendarlib_.get());
  ASSERT_TRUE(classifier);

  EXPECT_EQ(FirstResult(classifier->ClassifyText(
                "I live at 350 Third Street, Cambridge.", {10, 37})),
            "address");

  // Raise number of address tokens to suppress the address classification.
  unpacked_model->classification_options->address_min_num_tokens = 5;

  flatbuffers::FlatBufferBuilder builder2;
  FinishModelBuffer(builder2, Model::Pack(builder2, unpacked_model.get()));
  classifier = Annotator::FromUnownedBuffer(
      reinterpret_cast<const char*>(builder2.GetBufferPointer()),
      builder2.GetSize(), unilib_.get(), calendarlib_.get());
  ASSERT_TRUE(classifier);

  EXPECT_EQ(FirstResult(classifier->ClassifyText(
                "I live at 350 Third Street, Cambridge.", {10, 37})),
            "other");
}

TEST_F(AnnotatorTest, WhenOtherCollectionPriorityHighOtherIsPreferredToFlight) {
  const std::string test_model = ReadFile(GetTestModelPath());
  std::unique_ptr<ModelT> unpacked_model = UnPackModel(test_model.c_str());
  unpacked_model->triggering_options->other_collection_priority_score = 1.0;

  flatbuffers::FlatBufferBuilder builder;
  FinishModelBuffer(builder, Model::Pack(builder, unpacked_model.get()));
  std::unique_ptr<Annotator> classifier = Annotator::FromUnownedBuffer(
      reinterpret_cast<const char*>(builder.GetBufferPointer()),
      builder.GetSize(), unilib_.get(), calendarlib_.get());
  ASSERT_TRUE(classifier);

  EXPECT_EQ(FirstResult(classifier->ClassifyText("LX37", {0, 4})), "other");
}

TEST_F(AnnotatorTest, WhenOtherCollectionPriorityHighFlightIsPreferredToOther) {
  const std::string test_model = ReadFile(GetTestModelPath());
  std::unique_ptr<ModelT> unpacked_model = UnPackModel(test_model.c_str());
  unpacked_model->triggering_options->other_collection_priority_score = -100.0;

  flatbuffers::FlatBufferBuilder builder;
  FinishModelBuffer(builder, Model::Pack(builder, unpacked_model.get()));
  std::unique_ptr<Annotator> classifier = Annotator::FromUnownedBuffer(
      reinterpret_cast<const char*>(builder.GetBufferPointer()),
      builder.GetSize(), unilib_.get(), calendarlib_.get());
  ASSERT_TRUE(classifier);

  EXPECT_EQ(FirstResult(classifier->ClassifyText("LX37", {0, 4})), "flight");
}

TEST_F(AnnotatorTest, VisitAnnotatorModel) {
  EXPECT_TRUE(
      VisitAnnotatorModel<bool>(GetTestModelPath(), [](const Model* model) {
        if (model == nullptr) {
          return false;
        }
        return true;
      }));
  EXPECT_FALSE(VisitAnnotatorModel<bool>(
      GetModelPath() + "non_existing_model.fb", [](const Model* model) {
        if (model == nullptr) {
          return false;
        }
        return true;
      }));
}

TEST_F(AnnotatorTest, TriggersWhenNoLanguageDetected) {
  std::string model_buffer = ReadFile(GetTestModelPath());
  model_buffer = ModifyAnnotatorModel(
      model_buffer, [](ModelT* model) { model->triggering_locales = "en,cs"; });
  std::unique_ptr<Annotator> classifier =
      Annotator::FromUnownedBuffer(model_buffer.data(), model_buffer.size(),
                                   unilib_.get(), calendarlib_.get());
  ASSERT_TRUE(classifier);

  EXPECT_THAT(classifier->Annotate("(555) 225-3556"),
              ElementsAreArray({IsAnnotatedSpan(0, 14, "phone")}));
  EXPECT_EQ("phone",
            FirstResult(classifier->ClassifyText("(555) 225-3556", {0, 14})));
  EXPECT_EQ(classifier->SuggestSelection("(555) 225-3556", {6, 9}),
            CodepointSpan(0, 14));
}

TEST_F(AnnotatorTest, AnnotateTriggersWhenSupportedLanguageDetected) {
  std::string model_buffer = ReadFile(GetTestModelPath());
  model_buffer = ModifyAnnotatorModel(
      model_buffer, [](ModelT* model) { model->triggering_locales = "en,cs"; });
  std::unique_ptr<Annotator> classifier =
      Annotator::FromUnownedBuffer(model_buffer.data(), model_buffer.size(),
                                   unilib_.get(), calendarlib_.get());
  ASSERT_TRUE(classifier);
  AnnotationOptions options;
  options.detected_text_language_tags = "cs";

  EXPECT_THAT(classifier->Annotate("(555) 225-3556", options),
              ElementsAreArray({IsAnnotatedSpan(0, 14, "phone")}));
}

TEST_F(AnnotatorTest, AnnotateDoesntTriggerWhenUnsupportedLanguageDetected) {
  std::string model_buffer = ReadFile(GetTestModelPath());
  model_buffer = ModifyAnnotatorModel(
      model_buffer, [](ModelT* model) { model->triggering_locales = "en,cs"; });
  std::unique_ptr<Annotator> classifier =
      Annotator::FromUnownedBuffer(model_buffer.data(), model_buffer.size(),
                                   unilib_.get(), calendarlib_.get());
  ASSERT_TRUE(classifier);
  AnnotationOptions options;
  options.detected_text_language_tags = "de";

  EXPECT_THAT(classifier->Annotate("(555) 225-3556", options), IsEmpty());
}

TEST_F(AnnotatorTest, ClassifyTextTriggersWhenSupportedLanguageDetected) {
  std::string model_buffer = ReadFile(GetTestModelPath());
  model_buffer = ModifyAnnotatorModel(
      model_buffer, [](ModelT* model) { model->triggering_locales = "en,cs"; });
  std::unique_ptr<Annotator> classifier =
      Annotator::FromUnownedBuffer(model_buffer.data(), model_buffer.size(),
                                   unilib_.get(), calendarlib_.get());
  ASSERT_TRUE(classifier);
  ClassificationOptions options;
  options.detected_text_language_tags = "cs";

  EXPECT_EQ("phone", FirstResult(classifier->ClassifyText("(555) 225-3556",
                                                          {0, 14}, options)));
}

TEST_F(AnnotatorTest,
       ClassifyTextDoesntTriggerWhenUnsupportedLanguageDetected) {
  std::string model_buffer = ReadFile(GetTestModelPath());
  model_buffer = ModifyAnnotatorModel(
      model_buffer, [](ModelT* model) { model->triggering_locales = "en,cs"; });
  std::unique_ptr<Annotator> classifier =
      Annotator::FromUnownedBuffer(model_buffer.data(), model_buffer.size(),
                                   unilib_.get(), calendarlib_.get());
  ASSERT_TRUE(classifier);
  ClassificationOptions options;
  options.detected_text_language_tags = "de";

  EXPECT_THAT(classifier->ClassifyText("(555) 225-3556", {0, 14}, options),
              IsEmpty());
}

TEST_F(AnnotatorTest, SuggestSelectionTriggersWhenSupportedLanguageDetected) {
  std::string model_buffer = ReadFile(GetTestModelPath());
  model_buffer = ModifyAnnotatorModel(
      model_buffer, [](ModelT* model) { model->triggering_locales = "en,cs"; });
  std::unique_ptr<Annotator> classifier =
      Annotator::FromUnownedBuffer(model_buffer.data(), model_buffer.size(),
                                   unilib_.get(), calendarlib_.get());
  ASSERT_TRUE(classifier);
  SelectionOptions options;
  options.detected_text_language_tags = "cs";

  EXPECT_EQ(classifier->SuggestSelection("(555) 225-3556", {6, 9}, options),
            CodepointSpan(0, 14));
}

TEST_F(AnnotatorTest,
       SuggestSelectionDoesntTriggerWhenUnsupportedLanguageDetected) {
  std::string model_buffer = ReadFile(GetTestModelPath());
  model_buffer = ModifyAnnotatorModel(
      model_buffer, [](ModelT* model) { model->triggering_locales = "en,cs"; });
  std::unique_ptr<Annotator> classifier =
      Annotator::FromUnownedBuffer(model_buffer.data(), model_buffer.size(),
                                   unilib_.get(), calendarlib_.get());
  ASSERT_TRUE(classifier);
  SelectionOptions options;
  options.detected_text_language_tags = "de";

  EXPECT_EQ(classifier->SuggestSelection("(555) 225-3556", {6, 9}, options),
            CodepointSpan(6, 9));
}

TEST_F(AnnotatorTest, MlModelTriggersWhenNoLanguageDetected) {
  std::string model_buffer = ReadFile(GetTestModelPath());
  model_buffer = ModifyAnnotatorModel(model_buffer, [](ModelT* model) {
    model->triggering_locales = "en,cs";
    model->triggering_options->locales = "en,cs";
  });
  std::unique_ptr<Annotator> classifier =
      Annotator::FromUnownedBuffer(model_buffer.data(), model_buffer.size(),
                                   unilib_.get(), calendarlib_.get());
  ASSERT_TRUE(classifier);

  EXPECT_THAT(classifier->Annotate("350 Third Street, Cambridge"),
              ElementsAreArray({IsAnnotatedSpan(0, 27, "address")}));
  EXPECT_EQ("address", FirstResult(classifier->ClassifyText(
                           "350 Third Street, Cambridge", {0, 27})));
  EXPECT_EQ(classifier->SuggestSelection("350 Third Street, Cambridge", {4, 9}),
            CodepointSpan(0, 27));
}

TEST_F(AnnotatorTest, MlModelAnnotateTriggersWhenSupportedLanguageDetected) {
  std::string model_buffer = ReadFile(GetTestModelPath());
  model_buffer = ModifyAnnotatorModel(model_buffer, [](ModelT* model) {
    model->triggering_locales = "en,cs";
    model->triggering_options->locales = "en,cs";
  });
  std::unique_ptr<Annotator> classifier =
      Annotator::FromUnownedBuffer(model_buffer.data(), model_buffer.size(),
                                   unilib_.get(), calendarlib_.get());
  ASSERT_TRUE(classifier);
  AnnotationOptions options;
  options.detected_text_language_tags = "cs";

  EXPECT_THAT(classifier->Annotate("350 Third Street, Cambridge", options),
              ElementsAreArray({IsAnnotatedSpan(0, 27, "address")}));
}

TEST_F(AnnotatorTest,
       MlModelAnnotateDoesntTriggerWhenUnsupportedLanguageDetected) {
  std::string model_buffer = ReadFile(GetTestModelPath());
  model_buffer = ModifyAnnotatorModel(model_buffer, [](ModelT* model) {
    model->triggering_locales = "en,cs";
    model->triggering_options->locales = "en,cs";
  });
  std::unique_ptr<Annotator> classifier =
      Annotator::FromUnownedBuffer(model_buffer.data(), model_buffer.size(),
                                   unilib_.get(), calendarlib_.get());
  ASSERT_TRUE(classifier);
  AnnotationOptions options;
  options.detected_text_language_tags = "de";

  EXPECT_THAT(classifier->Annotate("350 Third Street, Cambridge", options),
              IsEmpty());
}

TEST_F(AnnotatorTest,
       MlModelClassifyTextTriggersWhenSupportedLanguageDetected) {
  std::string model_buffer = ReadFile(GetTestModelPath());
  model_buffer = ModifyAnnotatorModel(model_buffer, [](ModelT* model) {
    model->triggering_locales = "en,cs";
    model->triggering_options->locales = "en,cs";
  });
  std::unique_ptr<Annotator> classifier =
      Annotator::FromUnownedBuffer(model_buffer.data(), model_buffer.size(),
                                   unilib_.get(), calendarlib_.get());
  ASSERT_TRUE(classifier);
  ClassificationOptions options;
  options.detected_text_language_tags = "cs";

  EXPECT_EQ("address", FirstResult(classifier->ClassifyText(
                           "350 Third Street, Cambridge", {0, 27}, options)));
}

TEST_F(AnnotatorTest,
       MlModelClassifyTextDoesntTriggerWhenUnsupportedLanguageDetected) {
  std::string model_buffer = ReadFile(GetTestModelPath());
  model_buffer = ModifyAnnotatorModel(model_buffer, [](ModelT* model) {
    model->triggering_locales = "en,cs";
    model->triggering_options->locales = "en,cs";
  });
  std::unique_ptr<Annotator> classifier =
      Annotator::FromUnownedBuffer(model_buffer.data(), model_buffer.size(),
                                   unilib_.get(), calendarlib_.get());
  ASSERT_TRUE(classifier);
  ClassificationOptions options;
  options.detected_text_language_tags = "de";

  EXPECT_THAT(
      classifier->ClassifyText("350 Third Street, Cambridge", {0, 27}, options),
      IsEmpty());
}

TEST_F(AnnotatorTest,
       MlModelSuggestSelectionTriggersWhenSupportedLanguageDetected) {
  std::string model_buffer = ReadFile(GetTestModelPath());
  model_buffer = ModifyAnnotatorModel(model_buffer, [](ModelT* model) {
    model->triggering_locales = "en,cs";
    model->triggering_options->locales = "en,cs";
  });
  std::unique_ptr<Annotator> classifier =
      Annotator::FromUnownedBuffer(model_buffer.data(), model_buffer.size(),
                                   unilib_.get(), calendarlib_.get());
  ASSERT_TRUE(classifier);
  SelectionOptions options;
  options.detected_text_language_tags = "cs";

  EXPECT_EQ(classifier->SuggestSelection("350 Third Street, Cambridge", {4, 9},
                                         options),
            CodepointSpan(0, 27));
}

TEST_F(AnnotatorTest,
       MlModelSuggestSelectionDoesntTriggerWhenUnsupportedLanguageDetected) {
  std::string model_buffer = ReadFile(GetTestModelPath());
  model_buffer = ModifyAnnotatorModel(model_buffer, [](ModelT* model) {
    model->triggering_locales = "en,cs";
    model->triggering_options->locales = "en,cs";
  });
  std::unique_ptr<Annotator> classifier =
      Annotator::FromUnownedBuffer(model_buffer.data(), model_buffer.size(),
                                   unilib_.get(), calendarlib_.get());
  ASSERT_TRUE(classifier);
  SelectionOptions options;
  options.detected_text_language_tags = "de";

  EXPECT_EQ(classifier->SuggestSelection("350 Third Street, Cambridge", {4, 9},
                                         options),
            CodepointSpan(4, 9));
}

void VerifyClassifyTextOutputsDatetimeEntityData(const Annotator* classifier) {
  EXPECT_TRUE(classifier);
  std::vector<ClassificationResult> result;
  ClassificationOptions options;
  options.locales = "en-US";

  result = classifier->ClassifyText("03.05.1970 00:00am", {0, 18}, options);

  ASSERT_GE(result.size(), 0);
  const EntityData* entity_data =
      GetEntityData(result[0].serialized_entity_data.data());
  ASSERT_NE(entity_data, nullptr);
  ASSERT_NE(entity_data->datetime(), nullptr);
  EXPECT_EQ(entity_data->datetime()->time_ms_utc(), 5443200000L);
  EXPECT_EQ(entity_data->datetime()->granularity(),
            EntityData_::Datetime_::Granularity_GRANULARITY_MINUTE);
  EXPECT_EQ(entity_data->datetime()->datetime_component()->size(), 6);

  auto* meridiem = entity_data->datetime()->datetime_component()->Get(0);
  EXPECT_EQ(meridiem->component_type(),
            EntityData_::Datetime_::DatetimeComponent_::ComponentType_MERIDIEM);
  EXPECT_EQ(meridiem->absolute_value(), 0);
  EXPECT_EQ(meridiem->relative_count(), 0);
  EXPECT_EQ(meridiem->relation_type(),
            EntityData_::Datetime_::DatetimeComponent_::RelationType_ABSOLUTE);

  auto* minute = entity_data->datetime()->datetime_component()->Get(1);
  EXPECT_EQ(minute->component_type(),
            EntityData_::Datetime_::DatetimeComponent_::ComponentType_MINUTE);
  EXPECT_EQ(minute->absolute_value(), 0);
  EXPECT_EQ(minute->relative_count(), 0);
  EXPECT_EQ(minute->relation_type(),
            EntityData_::Datetime_::DatetimeComponent_::RelationType_ABSOLUTE);

  auto* hour = entity_data->datetime()->datetime_component()->Get(2);
  EXPECT_EQ(hour->component_type(),
            EntityData_::Datetime_::DatetimeComponent_::ComponentType_HOUR);
  EXPECT_EQ(hour->absolute_value(), 0);
  EXPECT_EQ(hour->relative_count(), 0);
  EXPECT_EQ(hour->relation_type(),
            EntityData_::Datetime_::DatetimeComponent_::RelationType_ABSOLUTE);

  auto* day = entity_data->datetime()->datetime_component()->Get(3);
  EXPECT_EQ(
      day->component_type(),
      EntityData_::Datetime_::DatetimeComponent_::ComponentType_DAY_OF_MONTH);
  EXPECT_EQ(day->absolute_value(), 5);
  EXPECT_EQ(day->relative_count(), 0);
  EXPECT_EQ(day->relation_type(),
            EntityData_::Datetime_::DatetimeComponent_::RelationType_ABSOLUTE);

  auto* month = entity_data->datetime()->datetime_component()->Get(4);
  EXPECT_EQ(month->component_type(),
            EntityData_::Datetime_::DatetimeComponent_::ComponentType_MONTH);
  EXPECT_EQ(month->absolute_value(), 3);
  EXPECT_EQ(month->relative_count(), 0);
  EXPECT_EQ(month->relation_type(),
            EntityData_::Datetime_::DatetimeComponent_::RelationType_ABSOLUTE);

  auto* year = entity_data->datetime()->datetime_component()->Get(5);
  EXPECT_EQ(year->component_type(),
            EntityData_::Datetime_::DatetimeComponent_::ComponentType_YEAR);
  EXPECT_EQ(year->absolute_value(), 1970);
  EXPECT_EQ(year->relative_count(), 0);
  EXPECT_EQ(year->relation_type(),
            EntityData_::Datetime_::DatetimeComponent_::RelationType_ABSOLUTE);
}

TEST_F(AnnotatorTest, ClassifyTextOutputsDatetimeEntityData) {
  std::unique_ptr<Annotator> classifier = Annotator::FromPath(
      GetTestModelPath(), unilib_.get(), calendarlib_.get());
  VerifyClassifyTextOutputsDatetimeEntityData(classifier.get());
}

TEST_F(AnnotatorTest, ClassifyTextOutputsDatetimeEntityDataWithDatetimeRegEx) {
  std::string model_buffer = GetTestModelWithDatetimeRegEx();
  std::unique_ptr<Annotator> classifier =
      Annotator::FromUnownedBuffer(model_buffer.data(), model_buffer.size(),
                                   unilib_.get(), calendarlib_.get());
  VerifyClassifyTextOutputsDatetimeEntityData(classifier.get());
}

void VerifyAnnotateOutputsDatetimeEntityData(const Annotator* classifier) {
  EXPECT_TRUE(classifier);
  std::vector<AnnotatedSpan> result;
  AnnotationOptions options;
  options.is_serialized_entity_data_enabled = true;
  options.locales = "en";

  result = classifier->Annotate("September 1, 2019", options);

  ASSERT_GE(result.size(), 0);
  ASSERT_GE(result[0].classification.size(), 0);
  ASSERT_EQ(result[0].classification[0].collection, "date");
  const EntityData* entity_data =
      GetEntityData(result[0].classification[0].serialized_entity_data.data());
  ASSERT_NE(entity_data, nullptr);
  ASSERT_NE(entity_data->datetime(), nullptr);
  EXPECT_EQ(entity_data->datetime()->time_ms_utc(), 1567296000000L);
  EXPECT_EQ(entity_data->datetime()->granularity(),
            EntityData_::Datetime_::Granularity_GRANULARITY_DAY);
  EXPECT_EQ(entity_data->datetime()->datetime_component()->size(), 3);

  auto* day = entity_data->datetime()->datetime_component()->Get(0);
  EXPECT_EQ(
      day->component_type(),
      EntityData_::Datetime_::DatetimeComponent_::ComponentType_DAY_OF_MONTH);
  EXPECT_EQ(day->absolute_value(), 1);
  EXPECT_EQ(day->relative_count(), 0);
  EXPECT_EQ(day->relation_type(),
            EntityData_::Datetime_::DatetimeComponent_::RelationType_ABSOLUTE);

  auto* month = entity_data->datetime()->datetime_component()->Get(1);
  EXPECT_EQ(month->component_type(),
            EntityData_::Datetime_::DatetimeComponent_::ComponentType_MONTH);
  EXPECT_EQ(month->absolute_value(), 9);
  EXPECT_EQ(month->relative_count(), 0);
  EXPECT_EQ(month->relation_type(),
            EntityData_::Datetime_::DatetimeComponent_::RelationType_ABSOLUTE);

  auto* year = entity_data->datetime()->datetime_component()->Get(2);
  EXPECT_EQ(year->component_type(),
            EntityData_::Datetime_::DatetimeComponent_::ComponentType_YEAR);
  EXPECT_EQ(year->absolute_value(), 2019);
  EXPECT_EQ(year->relative_count(), 0);
  EXPECT_EQ(year->relation_type(),
            EntityData_::Datetime_::DatetimeComponent_::RelationType_ABSOLUTE);
}

TEST_F(AnnotatorTest, AnnotateOutputsDatetimeEntityData) {
  std::unique_ptr<Annotator> classifier = Annotator::FromPath(
      GetTestModelPath(), unilib_.get(), calendarlib_.get());
  VerifyAnnotateOutputsDatetimeEntityData(classifier.get());
}

TEST_F(AnnotatorTest, AnnotateOutputsDatetimeEntityDataWithDatatimeRegEx) {
  std::string model_buffer = GetTestModelWithDatetimeRegEx();
  std::unique_ptr<Annotator> classifier =
      Annotator::FromUnownedBuffer(model_buffer.data(), model_buffer.size(),
                                   unilib_.get(), calendarlib_.get());
  VerifyAnnotateOutputsDatetimeEntityData(classifier.get());
}

TEST_F(AnnotatorTest, AnnotateOutputsMoneyEntityData) {
  // std::string model_buffer = GetTestModelWithDatetimeRegEx();
  // std::unique_ptr<Annotator> classifier =
  //     Annotator::FromUnownedBuffer(model_buffer.data(), model_buffer.size(),
  //                                  unilib_.get(), calendarlib_.get());
  std::unique_ptr<Annotator> classifier = Annotator::FromPath(
      GetTestModelPath(), unilib_.get(), calendarlib_.get());
  EXPECT_TRUE(classifier);
  AnnotationOptions options;
  options.is_serialized_entity_data_enabled = true;

  ExpectFirstEntityIsMoney(classifier->Annotate("3.5 CHF", options), "CHF",
                           /*amount=*/"3.5", /*whole_part=*/3,
                           /*decimal_part=*/5, /*nanos=*/500000000);
  ExpectFirstEntityIsMoney(classifier->Annotate("CHF 3.5", options), "CHF",
                           /*amount=*/"3.5", /*whole_part=*/3,
                           /*decimal_part=*/5, /*nanos=*/500000000);
  ExpectFirstEntityIsMoney(
      classifier->Annotate("For online purchase of CHF 23.00 enter", options),
      "CHF", /*amount=*/"23.00", /*whole_part=*/23, /*decimal_part=*/0,
      /*nanos=*/0);
  ExpectFirstEntityIsMoney(
      classifier->Annotate("For online purchase of 23.00 CHF enter", options),
      "CHF", /*amount=*/"23.00", /*whole_part=*/23, /*decimal_part=*/0,
      /*nanos=*/0);
  ExpectFirstEntityIsMoney(classifier->Annotate("4.8198£", options), "£",
                           /*amount=*/"4.8198", /*whole_part=*/4,
                           /*decimal_part=*/8198, /*nanos=*/819800000);
  ExpectFirstEntityIsMoney(classifier->Annotate("£4.8198", options), "£",
                           /*amount=*/"4.8198", /*whole_part=*/4,
                           /*decimal_part=*/8198, /*nanos=*/819800000);
  ExpectFirstEntityIsMoney(classifier->Annotate("$0.0255", options), "$",
                           /*amount=*/"0.0255", /*whole_part=*/0,
                           /*decimal_part=*/255, /*nanos=*/25500000);
  ExpectFirstEntityIsMoney(classifier->Annotate("$0.0255", options), "$",
                           /*amount=*/"0.0255", /*whole_part=*/0,
                           /*decimal_part=*/255, /*nanos=*/25500000);
  ExpectFirstEntityIsMoney(
      classifier->Annotate("for txn of INR 000.00 at RAZOR-PAY ZOMATO ONLINE "
                           "OR on card ending 0000.",
                           options),
      "INR", /*amount=*/"000.00", /*whole_part=*/0, /*decimal_part=*/0,
      /*nanos=*/0);
  ExpectFirstEntityIsMoney(
      classifier->Annotate("for txn of 000.00 INR at RAZOR-PAY ZOMATO ONLINE "
                           "OR on card ending 0000.",
                           options),
      "INR", /*amount=*/"000.00", /*whole_part=*/0, /*decimal_part=*/0,
      /*nanos=*/0);

  ExpectFirstEntityIsMoney(classifier->Annotate("35 CHF", options), "CHF",
                           /*amount=*/"35",
                           /*whole_part=*/35, /*decimal_part=*/0, /*nanos=*/0);
  ExpectFirstEntityIsMoney(classifier->Annotate("CHF 35", options), "CHF",
                           /*amount=*/"35", /*whole_part=*/35,
                           /*decimal_part=*/0, /*nanos=*/0);
  ExpectFirstEntityIsMoney(
      classifier->Annotate("and win back up to CHF 150 - with digitec",
                           options),
      "CHF", /*amount=*/"150", /*whole_part=*/150, /*decimal_part=*/0,
      /*nanos=*/0);
  ExpectFirstEntityIsMoney(
      classifier->Annotate("and win back up to 150 CHF - with digitec",
                           options),
      "CHF", /*amount=*/"150", /*whole_part=*/150, /*decimal_part=*/0,
      /*nanos=*/0);

  ExpectFirstEntityIsMoney(classifier->Annotate("3.555.333 CHF", options),
                           "CHF", /*amount=*/"3.555.333",
                           /*whole_part=*/3555333, /*decimal_part=*/0,
                           /*nanos=*/0);
  ExpectFirstEntityIsMoney(classifier->Annotate("CHF 3.555.333", options),
                           "CHF", /*amount=*/"3.555.333",
                           /*whole_part=*/3555333, /*decimal_part=*/0,
                           /*nanos=*/0);
  ExpectFirstEntityIsMoney(classifier->Annotate("10,000 CHF", options), "CHF",
                           /*amount=*/"10,000", /*whole_part=*/10000,
                           /*decimal_part=*/0, /*nanos=*/0);
  ExpectFirstEntityIsMoney(classifier->Annotate("CHF 10,000", options), "CHF",
                           /*amount=*/"10,000", /*whole_part=*/10000,
                           /*decimal_part=*/0, /*nanos=*/0);

  ExpectFirstEntityIsMoney(classifier->Annotate("3,555.33 CHF", options), "CHF",
                           /*amount=*/"3,555.33", /*whole_part=*/3555,
                           /*decimal_part=*/33, /*nanos=*/330000000);
  ExpectFirstEntityIsMoney(classifier->Annotate("CHF 3,555.33", options), "CHF",
                           /*amount=*/"3,555.33", /*whole_part=*/3555,
                           /*decimal_part=*/33, /*nanos=*/330000000);
  ExpectFirstEntityIsMoney(classifier->Annotate("$3,000.00", options), "$",
                           /*amount=*/"3,000.00", /*whole_part=*/3000,
                           /*decimal_part=*/0, /*nanos=*/0);
  ExpectFirstEntityIsMoney(classifier->Annotate("3,000.00$", options), "$",
                           /*amount=*/"3,000.00", /*whole_part=*/3000,
                           /*decimal_part=*/0, /*nanos=*/0);

  ExpectFirstEntityIsMoney(classifier->Annotate("1.2 CHF", options), "CHF",
                           /*amount=*/"1.2", /*whole_part=*/1,
                           /*decimal_part=*/2, /*nanos=*/200000000);
  ExpectFirstEntityIsMoney(classifier->Annotate("CHF１.２", options), "CHF",
                           /*amount=*/"１.２", /*whole_part=*/1,
                           /*decimal_part=*/2, /*nanos=*/200000000);

  ExpectFirstEntityIsMoney(classifier->Annotate("$1.123456789", options), "$",
                           /*amount=*/"1.123456789", /*whole_part=*/1,
                           /*decimal_part=*/123456789, /*nanos=*/123456789);
  ExpectFirstEntityIsMoney(classifier->Annotate("10.01 CHF", options), "CHF",
                           /*amount=*/"10.01", /*whole_part=*/10,
                           /*decimal_part=*/1, /*nanos=*/10000000);

  ExpectFirstEntityIsMoney(classifier->Annotate("$59 Million", options), "$",
                           /*amount=*/"59 million", /*whole_part=*/59000000,
                           /*decimal_part=*/0, /*nanos=*/0);
  ExpectFirstEntityIsMoney(classifier->Annotate("7.05k €", options), "€",
                           /*amount=*/"7.05 k", /*whole_part=*/7050,
                           /*decimal_part=*/5, /*nanos=*/0);
  ExpectFirstEntityIsMoney(classifier->Annotate("7.123456789m €", options), "€",
                           /*amount=*/"7.123456789 m", /*whole_part=*/7123456,
                           /*decimal_part=*/123456789, /*nanos=*/789000000);
  ExpectFirstEntityIsMoney(classifier->Annotate("7.000056789k €", options), "€",
                           /*amount=*/"7.000056789 k", /*whole_part=*/7000,
                           /*decimal_part=*/56789, /*nanos=*/56789000);

  ExpectFirstEntityIsMoney(classifier->Annotate("$59.3 Billion", options), "$",
                           /*amount=*/"59.3 billion", /*whole_part=*/59,
                           /*decimal_part=*/3, /*nanos=*/300000000);
  ExpectFirstEntityIsMoney(classifier->Annotate("$1.5 Billion", options), "$",
                           /*amount=*/"1.5 billion", /*whole_part=*/1500000000,
                           /*decimal_part=*/5, /*nanos=*/0);
}

TEST_F(AnnotatorTest, TranslateAction) {
  std::unique_ptr<Annotator> classifier = Annotator::FromPath(
      GetTestModelPath(), unilib_.get(), calendarlib_.get());
  std::unique_ptr<libtextclassifier3::mobile::lang_id::LangId> langid_model =
      libtextclassifier3::mobile::lang_id::GetLangIdFromFlatbufferFile(GetModelPath() +
                                                             "lang_id.smfb");
  classifier->SetLangId(langid_model.get());

  ClassificationOptions options;
  options.user_familiar_language_tags = "de";

  std::vector<ClassificationResult> classifications =
      classifier->ClassifyText("hello, how are you doing?", {11, 14}, options);
  EXPECT_EQ(classifications.size(), 1);
  EXPECT_EQ(classifications[0].collection, "translate");
}

TEST_F(AnnotatorTest, AnnotateStructuredInputCallsMultipleAnnotators) {
  std::unique_ptr<Annotator> classifier = Annotator::FromPath(
      GetTestModelPath(), unilib_.get(), calendarlib_.get());

  std::vector<InputFragment> string_fragments = {
      {.text = "He owes me 3.5 CHF."},
      {.text = "...was born on 13/12/1989."},
  };

  AnnotationOptions annotation_options;
  annotation_options.locales = "en";
  StatusOr<Annotations> annotations_status =
      classifier->AnnotateStructuredInput(string_fragments, annotation_options);
  ASSERT_TRUE(annotations_status.ok());
  Annotations annotations = annotations_status.ValueOrDie();
  ASSERT_EQ(annotations.annotated_spans.size(), 2);
  EXPECT_THAT(annotations.annotated_spans[0],
              ElementsAreArray({IsAnnotatedSpan(11, 18, "money")}));
  EXPECT_THAT(annotations.annotated_spans[1],
              ElementsAreArray({IsAnnotatedSpan(15, 25, "date")}));
}

void VerifyInputFragmentTimestampOverridesAnnotationOptions(
    const Annotator* classifier) {
  AnnotationOptions annotation_options;
  annotation_options.locales = "en";
  annotation_options.reference_time_ms_utc =
      1554465190000;                             // 04/05/2019 11:53 am
  int64 fragment_reference_time = 946727580000;  // 01/01/2000 11:53 am
  std::vector<InputFragment> string_fragments = {
      {.text = "New event at 17:20"},
      {
          .text = "New event at 17:20",
          .datetime_options = Optional<DatetimeOptions>(
              {.reference_time_ms_utc = fragment_reference_time}),
      }};
  StatusOr<Annotations> annotations_status =
      classifier->AnnotateStructuredInput(string_fragments, annotation_options);
  ASSERT_TRUE(annotations_status.ok());
  Annotations annotations = annotations_status.ValueOrDie();
  ASSERT_EQ(annotations.annotated_spans.size(), 2);
  EXPECT_THAT(annotations.annotated_spans[0],
              ElementsAreArray({IsDatetimeSpan(
                  /*start=*/13, /*end=*/18, /*time_ms_utc=*/1554484800000,
                  DatetimeGranularity::GRANULARITY_MINUTE)}));
  EXPECT_THAT(annotations.annotated_spans[1],
              ElementsAreArray({IsDatetimeSpan(
                  /*start=*/13, /*end=*/18, /*time_ms_utc=*/946747200000,
                  DatetimeGranularity::GRANULARITY_MINUTE)}));
}

TEST_F(AnnotatorTest,
       InputFragmentTimestampOverridesAnnotationOptionsWithDatetimeRegEx) {
  std::string model_buffer = GetTestModelWithDatetimeRegEx();
  std::unique_ptr<Annotator> classifier =
      Annotator::FromUnownedBuffer(model_buffer.data(), model_buffer.size(),
                                   unilib_.get(), calendarlib_.get());
  VerifyInputFragmentTimestampOverridesAnnotationOptions(classifier.get());
}

TEST_F(AnnotatorTest, InputFragmentTimestampOverridesAnnotationOptions) {
  std::unique_ptr<Annotator> classifier = Annotator::FromPath(
      GetTestModelPath(), unilib_.get(), calendarlib_.get());
  VerifyInputFragmentTimestampOverridesAnnotationOptions(classifier.get());
}

void VerifyInputFragmentTimezoneOverridesAnnotationOptions(
    const Annotator* classifier) {
  std::vector<InputFragment> string_fragments = {
      {.text = "11/12/2020 17:20"},
      {
          .text = "11/12/2020 17:20",
          .datetime_options = Optional<DatetimeOptions>(
              {.reference_timezone = "Europe/Zurich"}),
      }};
  AnnotationOptions annotation_options;
  annotation_options.locales = "en-US";
  StatusOr<Annotations> annotations_status =
      classifier->AnnotateStructuredInput(string_fragments, annotation_options);
  ASSERT_TRUE(annotations_status.ok());
  Annotations annotations = annotations_status.ValueOrDie();
  ASSERT_EQ(annotations.annotated_spans.size(), 2);
  EXPECT_THAT(annotations.annotated_spans[0],
              ElementsAreArray({IsDatetimeSpan(
                  /*start=*/0, /*end=*/16, /*time_ms_utc=*/1605201600000,
                  DatetimeGranularity::GRANULARITY_MINUTE)}));
  EXPECT_THAT(annotations.annotated_spans[1],
              ElementsAreArray({IsDatetimeSpan(
                  /*start=*/0, /*end=*/16, /*time_ms_utc=*/1605198000000,
                  DatetimeGranularity::GRANULARITY_MINUTE)}));
}

TEST_F(AnnotatorTest, InputFragmentTimezoneOverridesAnnotationOptions) {
  std::unique_ptr<Annotator> classifier = Annotator::FromPath(
      GetTestModelPath(), unilib_.get(), calendarlib_.get());
  VerifyInputFragmentTimezoneOverridesAnnotationOptions(classifier.get());
}

TEST_F(AnnotatorTest,
       InputFragmentTimezoneOverridesAnnotationOptionsWithDatetimeRegEx) {
  std::string model_buffer = GetTestModelWithDatetimeRegEx();
  std::unique_ptr<Annotator> classifier =
      Annotator::FromUnownedBuffer(model_buffer.data(), model_buffer.size(),
                                   unilib_.get(), calendarlib_.get());
  VerifyInputFragmentTimezoneOverridesAnnotationOptions(classifier.get());
}

namespace {
void AddDummyRegexDatetimeModel(ModelT* unpacked_model) {
  unpacked_model->datetime_model.reset(new DatetimeModelT);
  // This needs to be false otherwise we'd have to define some extractor. When
  // this is false, the 0-th capturing group (whole match) from the pattern is
  // used to come up with the indices.
  unpacked_model->datetime_model->use_extractors_for_locating = false;
  unpacked_model->datetime_model->locales.push_back("en-US");
  unpacked_model->datetime_model->default_locales.push_back(0);  // en-US
  unpacked_model->datetime_model->patterns.push_back(
      std::unique_ptr<DatetimeModelPatternT>(new DatetimeModelPatternT));
  unpacked_model->datetime_model->patterns.back()->locales.push_back(
      0);  // en-US
  unpacked_model->datetime_model->patterns.back()->regexes.push_back(
      std::unique_ptr<DatetimeModelPattern_::RegexT>(
          new DatetimeModelPattern_::RegexT));
  unpacked_model->datetime_model->patterns.back()->regexes.back()->pattern =
      "THIS_MATCHES_IN_REGEX_MODEL";
  unpacked_model->datetime_model->patterns.back()
      ->regexes.back()
      ->groups.push_back(DatetimeGroupType_GROUP_UNUSED);
}
}  // namespace

TEST_F(AnnotatorTest, AnnotateFiltersOutExactDuplicates) {
  std::unique_ptr<Annotator> classifier = Annotator::FromPath(
      GetTestModelPath(), unilib_.get(), calendarlib_.get());
  ASSERT_TRUE(classifier);

  // This test assumes that both ML model and Regex model trigger on the
  // following text and output "phone" annotation for it.
  const std::string test_string = "1000000000";
  AnnotationOptions options;
  options.annotation_usecase = ANNOTATION_USECASE_RAW;
  int num_phones = 0;
  for (const AnnotatedSpan& span : classifier->Annotate(test_string, options)) {
    if (span.classification[0].collection == "phone") {
      num_phones++;
    }
  }

  EXPECT_EQ(num_phones, 1);
}

// This test tests the optimizations in Annotator, which make some of the
// annotators not run in the RAW mode when not requested. We test here that the
// results indeed don't contain such annotations. However, this is a bick hacky,
// since one could also add post-filtering, in which case these tests would
// trivially pass.
TEST_F(AnnotatorTest, RawModeOptimizationWorks) {
  std::unique_ptr<Annotator> classifier = Annotator::FromPath(
      GetTestModelPath(), unilib_.get(), calendarlib_.get());
  ASSERT_TRUE(classifier);

  AnnotationOptions options;
  options.annotation_usecase = AnnotationUsecase_ANNOTATION_USECASE_RAW;
  // Requesting a non-existing type to avoid overlap with existing types.
  options.entity_types.insert("some_unknown_entity_type");

  // Normally, the following command would produce the following annotations:
  //   Span(19, 24, date, 1.000000),
  //   Span(53, 56, number, 1.000000),
  //   Span(53, 80, address, 1.000000),
  //   Span(128, 142, phone, 1.000000),
  //   Span(129, 132, number, 1.000000),
  //   Span(192, 200, phone, 1.000000),
  //   Span(192, 206, datetime, 1.000000),
  //   Span(246, 253, number, 1.000000),
  //   Span(246, 253, phone, 1.000000),
  //   Span(292, 293, number, 1.000000),
  //   Span(292, 301, duration, 1.000000) }
  // But because of the optimizations, it doesn't produce anything, since
  // we didn't request any of these entities.
  EXPECT_THAT(classifier->Annotate(R"--(I saw Barack Obama today
                            350 Third Street, Cambridge
                            my phone number is (853) 225-3556
                            this is when we met: 1.9.2021 13:00
                            my number: 1234567
                            duration: 3 minutes
                            )--",
                                   options),
              IsEmpty());
}

void VerifyAnnotateSupportsPointwiseCollectionFilteringInRawMode(
    const Annotator* classifier) {
  ASSERT_TRUE(classifier);
  struct Example {
    std::string collection;
    std::string text;
  };

  // These examples contain one example per annotator, to check that each of
  // the annotators can work in the RAW mode on its own.
  //
  // WARNING: This list doesn't contain yet entries for the app, contact, and
  // person annotators. Hopefully this won't be needed once b/155214735 is
  // fixed and the piping shared across annotators.
  std::vector<Example> examples{
      // ML Model.
      {.collection = Collections::Address(),
       .text = "... 350 Third Street, Cambridge ..."},
      // Datetime annotator.
      {.collection = Collections::DateTime(), .text = "... 1.9.2020 10:00 ..."},
      // Duration annotator.
      {.collection = Collections::Duration(),
       .text = "... 3 hours and 9 seconds ..."},
      // Regex annotator.
      {.collection = Collections::Email(),
       .text = "... platypus@theanimal.org ..."},
      // Number annotator.
      {.collection = Collections::Number(), .text = "... 100 ..."},
  };

  for (const Example& example : examples) {
    AnnotationOptions options;
    options.locales = "en";
    options.annotation_usecase = AnnotationUsecase_ANNOTATION_USECASE_RAW;
    options.entity_types.insert(example.collection);

    EXPECT_THAT(classifier->Annotate(example.text, options),
                Contains(IsAnnotationWithType(example.collection)))
        << " text: '" << example.text
        << "', collection: " << example.collection;
  }
}

TEST_F(AnnotatorTest, AnnotateSupportsPointwiseCollectionFilteringInRawMode) {
  std::unique_ptr<Annotator> classifier = Annotator::FromPath(
      GetTestModelPath(), unilib_.get(), calendarlib_.get());
  VerifyAnnotateSupportsPointwiseCollectionFilteringInRawMode(classifier.get());
}

TEST_F(AnnotatorTest,
       AnnotateSupportsPointwiseCollectionFilteringInRawModeWithDatetimeRegEx) {
  std::string model_buffer = GetTestModelWithDatetimeRegEx();
  std::unique_ptr<Annotator> classifier =
      Annotator::FromUnownedBuffer(model_buffer.data(), model_buffer.size(),
                                   unilib_.get(), calendarlib_.get());
  VerifyAnnotateSupportsPointwiseCollectionFilteringInRawMode(classifier.get());
}

TEST_F(AnnotatorTest, InitializeFromString) {
  const std::string test_model = ReadFile(GetTestModelPath());
  std::unique_ptr<Annotator> classifier =
      Annotator::FromString(test_model, unilib_.get(), calendarlib_.get());
  ASSERT_TRUE(classifier);
  EXPECT_THAT(classifier->Annotate("(857) 225-3556"), Not(IsEmpty()));
}

// Regression test for cl/338280366. Enabling only_use_line_with_click had
// the effect, that some annotators in the previous code releases would
// receive only the last line of the input text. This test has the entity on the
// first line (duration).
TEST_F(AnnotatorTest, RegressionTestOnlyUseLineWithClickLastLine) {
  const std::string test_model = ReadFile(GetTestModelPath());
  std::unique_ptr<ModelT> unpacked_model = UnPackModel(test_model.c_str());

  std::unique_ptr<Annotator> classifier;

  // With unrestricted number of tokens should behave normally.
  unpacked_model->selection_feature_options->only_use_line_with_click = true;

  flatbuffers::FlatBufferBuilder builder;
  FinishModelBuffer(builder, Model::Pack(builder, unpacked_model.get()));
  classifier = Annotator::FromUnownedBuffer(
      reinterpret_cast<const char*>(builder.GetBufferPointer()),
      builder.GetSize(), unilib_.get(), calendarlib_.get());
  ASSERT_TRUE(classifier);

  AnnotationOptions options;
  options.annotation_usecase = AnnotationUsecase_ANNOTATION_USECASE_RAW;

  const std::vector<AnnotatedSpan> annotations =
      classifier->Annotate("let's meet in 3 hours\nbut not now", options);

  EXPECT_THAT(annotations, Contains(IsDurationSpan(
                               /*start=*/14, /*end=*/21,
                               /*duration_ms=*/3 * 60 * 60 * 1000)));
}

TEST_F(AnnotatorTest, DoesntProcessInvalidUtf8) {
  const std::string test_model = ReadFile(GetTestModelPath());
  const std::string invalid_utf8_text_with_phone_number =
      "(857) 225-3556 \xed\xa0\x80\xed\xa0\x80\xed\xa0\x80\xed\xa0\x80";

  std::unique_ptr<Annotator> classifier =
      Annotator::FromString(test_model, unilib_.get(), calendarlib_.get());
  ASSERT_TRUE(classifier);
  EXPECT_THAT(classifier->Annotate(invalid_utf8_text_with_phone_number),
              IsEmpty());
  EXPECT_THAT(
      classifier->SuggestSelection(invalid_utf8_text_with_phone_number, {1, 4}),
      Eq(CodepointSpan{1, 4}));
  EXPECT_THAT(
      classifier->ClassifyText(invalid_utf8_text_with_phone_number, {0, 14}),
      IsEmpty());
}

}  // namespace test_internal
}  // namespace libtextclassifier3
