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

#include "annotator/translate/translate.h"

#include <memory>

#include "annotator/model_generated.h"
#include "utils/test-data-test-utils.h"
#include "lang_id/fb_model/lang-id-from-fb.h"
#include "lang_id/lang-id.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"

namespace libtextclassifier3 {
namespace {

using testing::AllOf;
using testing::Field;

const flatbuffers::DetachedBuffer* CreateOptionsData(ModeFlag enabled_modes) {
  TranslateAnnotatorOptionsT options;
  options.enabled = true;
  options.algorithm = TranslateAnnotatorOptions_::Algorithm_BACKOFF;
  options.backoff_options.reset(
      new TranslateAnnotatorOptions_::BackoffOptionsT());
  options.enabled_modes = enabled_modes;

  flatbuffers::FlatBufferBuilder builder;
  builder.Finish(TranslateAnnotatorOptions::Pack(builder, &options));
  return new flatbuffers::DetachedBuffer(builder.Release());
}

const TranslateAnnotatorOptions* TestingTranslateAnnotatorOptions(
    ModeFlag enabled_modes) {
  static const flatbuffers::DetachedBuffer* options_data_classification =
      CreateOptionsData(ModeFlag_CLASSIFICATION);
  static const flatbuffers::DetachedBuffer* options_data_none =
      CreateOptionsData(ModeFlag_NONE);

  if (enabled_modes == ModeFlag_CLASSIFICATION) {
    return flatbuffers::GetRoot<TranslateAnnotatorOptions>(
        options_data_classification->data());
  } else {
    return flatbuffers::GetRoot<TranslateAnnotatorOptions>(
        options_data_none->data());
  }
}

class TestingTranslateAnnotator : public TranslateAnnotator {
 public:
  // Make these protected members public for tests.
  using TranslateAnnotator::BackoffDetectLanguages;
  using TranslateAnnotator::FindIndexOfNextWhitespaceOrPunctuation;
  using TranslateAnnotator::TokenAlignedSubstringAroundSpan;
  using TranslateAnnotator::TranslateAnnotator;
};

std::string GetModelPath() { return GetTestDataPath("annotator/test_data/"); }

class TranslateAnnotatorTest : public ::testing::Test {
 protected:
  explicit TranslateAnnotatorTest(
      ModeFlag enabled_modes = ModeFlag_CLASSIFICATION)
      : INIT_UNILIB_FOR_TESTING(unilib_),
        langid_model_(libtextclassifier3::mobile::lang_id::GetLangIdFromFlatbufferFile(
            GetModelPath() + "lang_id.smfb")),
        translate_annotator_(TestingTranslateAnnotatorOptions(enabled_modes),
                             langid_model_.get(), &unilib_) {}

  UniLib unilib_;
  std::unique_ptr<libtextclassifier3::mobile::lang_id::LangId> langid_model_;
  TestingTranslateAnnotator translate_annotator_;
};

class TranslateAnnotatorForNoneTest : public TranslateAnnotatorTest {
 protected:
  TranslateAnnotatorForNoneTest() : TranslateAnnotatorTest(ModeFlag_NONE) {}
};

TEST_F(TranslateAnnotatorTest, WhenSpeaksEnglishGetsTranslateActionForCzech) {
  ClassificationResult classification;
  EXPECT_TRUE(translate_annotator_.ClassifyText(
      UTF8ToUnicodeText("Třista třicet tři stříbrných stříkaček."), {18, 28},
      "en", &classification));

  EXPECT_THAT(classification,
              AllOf(Field(&ClassificationResult::collection, "translate")));
  const EntityData* entity_data =
      GetEntityData(classification.serialized_entity_data.data());
  const auto predictions =
      entity_data->translate()->language_prediction_results();
  EXPECT_EQ(predictions->size(), 1);
  EXPECT_EQ(predictions->Get(0)->language_tag()->str(), "cs");
  EXPECT_GT(predictions->Get(0)->confidence_score(), 0);
  EXPECT_LE(predictions->Get(0)->confidence_score(), 1);
}

TEST_F(TranslateAnnotatorTest, EntityDataIsSet) {
  ClassificationResult classification;
  EXPECT_TRUE(translate_annotator_.ClassifyText(UTF8ToUnicodeText("学校"),
                                                {0, 2}, "en", &classification));

  EXPECT_THAT(classification,
              AllOf(Field(&ClassificationResult::collection, "translate")));
  const EntityData* entity_data =
      GetEntityData(classification.serialized_entity_data.data());
  const auto predictions =
      entity_data->translate()->language_prediction_results();
  EXPECT_EQ(predictions->size(), 2);
  EXPECT_EQ(predictions->Get(0)->language_tag()->str(), "zh");
  EXPECT_GT(predictions->Get(0)->confidence_score(), 0);
  EXPECT_LE(predictions->Get(0)->confidence_score(), 1);
  EXPECT_EQ(predictions->Get(1)->language_tag()->str(), "ja");
  EXPECT_TRUE(predictions->Get(0)->confidence_score() >=
              predictions->Get(1)->confidence_score());
}

TEST_F(TranslateAnnotatorForNoneTest,
       ClassifyTextDisabledClassificationReturnsFalse) {
  ClassificationResult classification;
  EXPECT_FALSE(translate_annotator_.ClassifyText(
      UTF8ToUnicodeText("学校"), {0, 2}, "en", &classification));
}

TEST_F(TranslateAnnotatorTest,
       WhenSpeaksEnglishDoesntGetTranslateActionForEnglish) {
  ClassificationResult classification;
  EXPECT_FALSE(translate_annotator_.ClassifyText(
      UTF8ToUnicodeText("This is utterly unutterable."), {8, 15}, "en",
      &classification));
}

TEST_F(TranslateAnnotatorTest,
       WhenSpeaksMultipleAndNotCzechGetsTranslateActionForCzech) {
  ClassificationResult classification;
  EXPECT_TRUE(translate_annotator_.ClassifyText(
      UTF8ToUnicodeText("Třista třicet tři stříbrných stříkaček."), {8, 15},
      "de,en,ja", &classification));

  EXPECT_THAT(classification,
              AllOf(Field(&ClassificationResult::collection, "translate")));
}

TEST_F(TranslateAnnotatorTest,
       WhenSpeaksMultipleAndEnglishDoesntGetTranslateActionForEnglish) {
  ClassificationResult classification;
  EXPECT_FALSE(translate_annotator_.ClassifyText(
      UTF8ToUnicodeText("This is utterly unutterable."), {8, 15}, "cs,en,de,ja",
      &classification));
}

TEST_F(TranslateAnnotatorTest, FindIndexOfNextWhitespaceOrPunctuation) {
  const UnicodeText text =
      UTF8ToUnicodeText("Třista třicet, tři stříbrných stříkaček");

  EXPECT_EQ(
      translate_annotator_.FindIndexOfNextWhitespaceOrPunctuation(text, 0, -1),
      text.begin());
  EXPECT_EQ(
      translate_annotator_.FindIndexOfNextWhitespaceOrPunctuation(text, 35, 1),
      text.end());
  EXPECT_EQ(
      translate_annotator_.FindIndexOfNextWhitespaceOrPunctuation(text, 10, -1),
      std::next(text.begin(), 6));
  EXPECT_EQ(
      translate_annotator_.FindIndexOfNextWhitespaceOrPunctuation(text, 10, 1),
      std::next(text.begin(), 13));
}

TEST_F(TranslateAnnotatorTest, TokenAlignedSubstringAroundSpan) {
  const UnicodeText text =
      UTF8ToUnicodeText("Třista třicet, tři stříbrných stříkaček");

  EXPECT_EQ(translate_annotator_.TokenAlignedSubstringAroundSpan(
                text, {35, 37}, /*minimum_length=*/100),
            text);
  EXPECT_EQ(translate_annotator_.TokenAlignedSubstringAroundSpan(
                text, {35, 37}, /*minimum_length=*/0),
            UTF8ToUnicodeText("ač"));
  EXPECT_EQ(translate_annotator_.TokenAlignedSubstringAroundSpan(
                text, {35, 37}, /*minimum_length=*/3),
            UTF8ToUnicodeText("stříkaček"));
  EXPECT_EQ(translate_annotator_.TokenAlignedSubstringAroundSpan(
                text, {35, 37}, /*minimum_length=*/10),
            UTF8ToUnicodeText("stříkaček"));
  EXPECT_EQ(translate_annotator_.TokenAlignedSubstringAroundSpan(
                text, {35, 37}, /*minimum_length=*/11),
            UTF8ToUnicodeText("stříbrných stříkaček"));

  const UnicodeText text_no_whitespace =
      UTF8ToUnicodeText("reallyreallylongstring");
  EXPECT_EQ(translate_annotator_.TokenAlignedSubstringAroundSpan(
                text_no_whitespace, {10, 11}, /*minimum_length=*/2),
            UTF8ToUnicodeText("reallyreallylongstring"));
}

TEST_F(TranslateAnnotatorTest, TokenAlignedSubstringWhitespaceText) {
  const UnicodeText text = UTF8ToUnicodeText("            ");

  // Shouldn't modify the selection in case it's all whitespace.
  EXPECT_EQ(translate_annotator_.TokenAlignedSubstringAroundSpan(
                text, {5, 7}, /*minimum_length=*/3),
            UTF8ToUnicodeText("  "));
  EXPECT_EQ(translate_annotator_.TokenAlignedSubstringAroundSpan(
                text, {5, 5}, /*minimum_length=*/1),
            UTF8ToUnicodeText(""));
}

TEST_F(TranslateAnnotatorTest, TokenAlignedSubstringMostlyWhitespaceText) {
  const UnicodeText text = UTF8ToUnicodeText("a        a");

  // Should still select the whole text even if pointing to whitespace
  // initially.
  EXPECT_EQ(translate_annotator_.TokenAlignedSubstringAroundSpan(
                text, {5, 7}, /*minimum_length=*/11),
            UTF8ToUnicodeText("a        a"));
}

}  // namespace
}  // namespace libtextclassifier3
