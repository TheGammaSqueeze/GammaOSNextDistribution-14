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

#include "annotator/pod_ner/pod-ner-impl.h"

#include <iostream>
#include <memory>
#include <thread>  // NOLINT(build/c++11)

#include "annotator/model_generated.h"
#include "annotator/types.h"
#include "utils/jvm-test-utils.h"
#include "utils/test-data-test-utils.h"
#include "utils/tokenizer-utils.h"
#include "utils/utf8/unicodetext.h"
#include "utils/utf8/unilib.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"

namespace libtextclassifier3 {
namespace {

using ::testing::IsEmpty;
using ::testing::Not;

using PodNerModel_::Label_::BoiseType;
using PodNerModel_::Label_::BoiseType_BEGIN;
using PodNerModel_::Label_::BoiseType_END;
using PodNerModel_::Label_::BoiseType_INTERMEDIATE;
using PodNerModel_::Label_::BoiseType_O;
using PodNerModel_::Label_::BoiseType_SINGLE;
using PodNerModel_::Label_::MentionType;
using PodNerModel_::Label_::MentionType_NAM;
using PodNerModel_::Label_::MentionType_NOM;
using PodNerModel_::Label_::MentionType_UNDEFINED;

constexpr int kMinNumberOfTokens = 1;
constexpr int kMinNumberOfWordpieces = 1;
constexpr float kDefaultPriorityScore = 0.5;

class PodNerTest : public testing::Test {
 protected:
  explicit PodNerTest(ModeFlag enabled_modes = ModeFlag_ALL) {
    PodNerModelT model;

    model.min_number_of_tokens = kMinNumberOfTokens;
    model.min_number_of_wordpieces = kMinNumberOfWordpieces;
    model.priority_score = kDefaultPriorityScore;

    const std::string tflite_model_buffer =
        GetTestFileContent("annotator/pod_ner/test_data/tflite_model.tflite");
    model.tflite_model = std::vector<uint8_t>(tflite_model_buffer.begin(),
                                              tflite_model_buffer.end());
    const std::string word_piece_vocab_buffer =
        GetTestFileContent("annotator/pod_ner/test_data/vocab.txt");
    model.word_piece_vocab = std::vector<uint8_t>(
        word_piece_vocab_buffer.begin(), word_piece_vocab_buffer.end());
    model.enabled_modes = enabled_modes;

    flatbuffers::FlatBufferBuilder builder;
    builder.Finish(PodNerModel::Pack(builder, &model));

    model_buffer_ =
        std::string(reinterpret_cast<const char*>(builder.GetBufferPointer()),
                    builder.GetSize());
    model_ = static_cast<const PodNerModel*>(
        flatbuffers::GetRoot<PodNerModel>(model_buffer_.data()));

    model.append_final_period = true;
    flatbuffers::FlatBufferBuilder builder_append_final_period;
    builder_append_final_period.Finish(
        PodNerModel::Pack(builder_append_final_period, &model));

    model_buffer_append_final_period_ =
        std::string(reinterpret_cast<const char*>(
                        builder_append_final_period.GetBufferPointer()),
                    builder_append_final_period.GetSize());
    model_append_final_period_ =
        static_cast<const PodNerModel*>(flatbuffers::GetRoot<PodNerModel>(
            model_buffer_append_final_period_.data()));

    unilib_ = CreateUniLibForTesting();
  }

  std::string model_buffer_;
  const PodNerModel* model_;
  std::string model_buffer_append_final_period_;
  const PodNerModel* model_append_final_period_;
  std::unique_ptr<UniLib> unilib_;
};

class PodNerForAnnotationAndClassificationTest : public PodNerTest {
 protected:
  PodNerForAnnotationAndClassificationTest()
      : PodNerTest(ModeFlag_ANNOTATION_AND_CLASSIFICATION) {}
};

class PodNerForSelectionTest : public PodNerTest {
 protected:
  PodNerForSelectionTest() : PodNerTest(ModeFlag_SELECTION) {}
};

TEST_F(PodNerTest, AnnotateSmokeTest) {
  std::unique_ptr<PodNerAnnotator> annotator =
      PodNerAnnotator::Create(model_, *unilib_);
  ASSERT_TRUE(annotator != nullptr);

  {
    std::vector<AnnotatedSpan> annotations;
    ASSERT_TRUE(annotator->Annotate(
        UTF8ToUnicodeText("Google New York , in New York"), &annotations));
    EXPECT_THAT(annotations, Not(IsEmpty()));
  }

  {
    std::vector<AnnotatedSpan> annotations;
    ASSERT_TRUE(annotator->Annotate(
        UTF8ToUnicodeText("Jamie I'm in the first picture and Cameron and Zach "
                          "are in the second "
                          "picture."),
        &annotations));
    EXPECT_THAT(annotations, Not(IsEmpty()));
  }
}

TEST_F(PodNerTest, AnnotateEmptyInput) {
  std::unique_ptr<PodNerAnnotator> annotator =
      PodNerAnnotator::Create(model_, *unilib_);
  ASSERT_TRUE(annotator != nullptr);

  {
    std::vector<AnnotatedSpan> annotations;
    ASSERT_TRUE(annotator->Annotate(UTF8ToUnicodeText(""), &annotations));
    EXPECT_THAT(annotations, IsEmpty());
  }
}

void FillCollections(
    const std::vector<std::string>& collection_names,
    const std::vector<float>& single_token_priority_scores,
    const std::vector<float>& multi_token_priority_scores,
    std::vector<std::unique_ptr<PodNerModel_::CollectionT>>* collections) {
  ASSERT_TRUE(collection_names.size() == single_token_priority_scores.size() &&
              collection_names.size() == multi_token_priority_scores.size());
  collections->clear();
  for (int i = 0; i < collection_names.size(); ++i) {
    collections->push_back(std::make_unique<PodNerModel_::CollectionT>());
    collections->back()->name = collection_names[i];
    collections->back()->single_token_priority_score =
        single_token_priority_scores[i];
    collections->back()->multi_token_priority_score =
        multi_token_priority_scores[i];
  }
}

void EmplaceToLabelVector(
    BoiseType boise_type, MentionType mention_type, int collection_id,
    std::vector<std::unique_ptr<PodNerModel_::LabelT>>* labels) {
  labels->push_back(std::make_unique<PodNerModel_::LabelT>());
  labels->back()->boise_type = boise_type;
  labels->back()->mention_type = mention_type;
  labels->back()->collection_id = collection_id;
}

void FillLabels(int num_collections,
                std::vector<std::unique_ptr<PodNerModel_::LabelT>>* labels) {
  labels->clear();
  for (auto boise_type :
       {BoiseType_BEGIN, BoiseType_END, BoiseType_INTERMEDIATE}) {
    for (auto mention_type : {MentionType_NAM, MentionType_NOM}) {
      for (int i = 0; i < num_collections - 1; ++i) {  // skip undefined
        EmplaceToLabelVector(boise_type, mention_type, i, labels);
      }
    }
  }
  EmplaceToLabelVector(BoiseType_O, MentionType_UNDEFINED, num_collections - 1,
                       labels);
  for (auto mention_type : {MentionType_NAM, MentionType_NOM}) {
    for (int i = 0; i < num_collections - 1; ++i) {  // skip undefined
      EmplaceToLabelVector(BoiseType_SINGLE, mention_type, i, labels);
    }
  }
}

TEST_F(PodNerTest, AnnotateDefaultCollections) {
  std::unique_ptr<PodNerAnnotator> annotator =
      PodNerAnnotator::Create(model_, *unilib_);
  ASSERT_TRUE(annotator != nullptr);

  std::string multi_word_location = "I live in New York";
  std::string single_word_location = "I live in Zurich";
  {
    std::vector<AnnotatedSpan> annotations;
    ASSERT_TRUE(annotator->Annotate(UTF8ToUnicodeText(multi_word_location),
                                    &annotations));
    EXPECT_THAT(annotations, Not(IsEmpty()));
    EXPECT_EQ(annotations[0].classification[0].collection, "location");
    EXPECT_EQ(annotations[0].classification[0].priority_score,
              kDefaultPriorityScore);

    annotations.clear();
    ASSERT_TRUE(annotator->Annotate(UTF8ToUnicodeText(single_word_location),
                                    &annotations));
    EXPECT_THAT(annotations, Not(IsEmpty()));
    EXPECT_EQ(annotations[0].classification[0].collection, "location");
    EXPECT_EQ(annotations[0].classification[0].priority_score,
              kDefaultPriorityScore);
  }
}

TEST_F(PodNerForSelectionTest, AnnotateWithDisabledAnnotationReturnsNoResults) {
  std::unique_ptr<PodNerAnnotator> annotator =
      PodNerAnnotator::Create(model_, *unilib_);
  ASSERT_TRUE(annotator != nullptr);

  std::string multi_word_location = "I live in New York";
  std::vector<AnnotatedSpan> annotations;
  ASSERT_TRUE(annotator->Annotate(UTF8ToUnicodeText(multi_word_location),
                                  &annotations));
  EXPECT_THAT(annotations, IsEmpty());
}

TEST_F(PodNerTest, AnnotateConfigurableCollections) {
  std::unique_ptr<PodNerModelT> unpacked_model(model_->UnPack());
  ASSERT_TRUE(unpacked_model != nullptr);

  float xxx_single_token_priority = 0.9;
  float xxx_multi_token_priority = 1.7;
  const std::vector<std::string> collection_names = {
      "art",          "consumer_good", "event",  "xxx",
      "organization", "ner_entity",    "person", "undefined"};
  FillCollections(collection_names,
                  /*single_token_priority_scores=*/
                  {0., 0., 0., xxx_single_token_priority, 0., 0., 0., 0.},
                  /*multi_token_priority_scores=*/
                  {0., 0., 0., xxx_multi_token_priority, 0., 0., 0., 0.},
                  &(unpacked_model->collections));
  FillLabels(collection_names.size(), &(unpacked_model->labels));
  flatbuffers::FlatBufferBuilder builder;
  builder.Finish(PodNerModel::Pack(builder, unpacked_model.get()));
  std::string model_buffer =
      std::string(reinterpret_cast<const char*>(builder.GetBufferPointer()),
                  builder.GetSize());
  std::unique_ptr<PodNerAnnotator> annotator = PodNerAnnotator::Create(
      static_cast<const PodNerModel*>(
          flatbuffers::GetRoot<PodNerModel>(model_buffer.data())),
      *unilib_);
  ASSERT_TRUE(annotator != nullptr);

  std::string multi_word_location = "I live in New York";
  std::string single_word_location = "I live in Zurich";
  {
    std::vector<AnnotatedSpan> annotations;
    ASSERT_TRUE(annotator->Annotate(UTF8ToUnicodeText(multi_word_location),
                                    &annotations));
    EXPECT_THAT(annotations, Not(IsEmpty()));
    EXPECT_EQ(annotations[0].classification[0].collection, "xxx");
    EXPECT_EQ(annotations[0].classification[0].priority_score,
              xxx_multi_token_priority);

    annotations.clear();
    ASSERT_TRUE(annotator->Annotate(UTF8ToUnicodeText(single_word_location),
                                    &annotations));
    EXPECT_THAT(annotations, Not(IsEmpty()));
    EXPECT_EQ(annotations[0].classification[0].collection, "xxx");
    EXPECT_EQ(annotations[0].classification[0].priority_score,
              xxx_single_token_priority);
  }
}

TEST_F(PodNerTest, AnnotateMinNumTokens) {
  std::unique_ptr<PodNerAnnotator> annotator =
      PodNerAnnotator::Create(model_, *unilib_);
  ASSERT_TRUE(annotator != nullptr);

  std::string text = "in New York";
  {
    std::vector<AnnotatedSpan> annotations;
    ASSERT_TRUE(annotator->Annotate(UTF8ToUnicodeText(text), &annotations));
    EXPECT_THAT(annotations, Not(IsEmpty()));
  }

  std::unique_ptr<PodNerModelT> unpacked_model(model_->UnPack());
  ASSERT_TRUE(unpacked_model != nullptr);

  unpacked_model->min_number_of_tokens = 4;
  flatbuffers::FlatBufferBuilder builder;
  builder.Finish(PodNerModel::Pack(builder, unpacked_model.get()));

  std::string model_buffer =
      std::string(reinterpret_cast<const char*>(builder.GetBufferPointer()),
                  builder.GetSize());
  annotator = PodNerAnnotator::Create(
      static_cast<const PodNerModel*>(
          flatbuffers::GetRoot<PodNerModel>(model_buffer.data())),
      *unilib_);
  ASSERT_TRUE(annotator != nullptr);
  {
    std::vector<AnnotatedSpan> annotations;
    ASSERT_TRUE(annotator->Annotate(UTF8ToUnicodeText(text), &annotations));
    EXPECT_THAT(annotations, IsEmpty());
  }
}

TEST_F(PodNerTest, AnnotateMinNumWordpieces) {
  std::unique_ptr<PodNerAnnotator> annotator =
      PodNerAnnotator::Create(model_, *unilib_);
  ASSERT_TRUE(annotator != nullptr);

  std::string text = "in New York";
  {
    std::vector<AnnotatedSpan> annotations;
    ASSERT_TRUE(annotator->Annotate(UTF8ToUnicodeText(text), &annotations));
    EXPECT_THAT(annotations, Not(IsEmpty()));
  }

  std::unique_ptr<PodNerModelT> unpacked_model(model_->UnPack());
  ASSERT_TRUE(unpacked_model != nullptr);

  unpacked_model->min_number_of_wordpieces = 10;
  flatbuffers::FlatBufferBuilder builder;
  builder.Finish(PodNerModel::Pack(builder, unpacked_model.get()));

  std::string model_buffer =
      std::string(reinterpret_cast<const char*>(builder.GetBufferPointer()),
                  builder.GetSize());
  annotator = PodNerAnnotator::Create(
      static_cast<const PodNerModel*>(
          flatbuffers::GetRoot<PodNerModel>(model_buffer.data())),
      *unilib_);
  ASSERT_TRUE(annotator != nullptr);
  {
    std::vector<AnnotatedSpan> annotations;
    ASSERT_TRUE(annotator->Annotate(UTF8ToUnicodeText(text), &annotations));
    EXPECT_THAT(annotations, IsEmpty());
  }
}

TEST_F(PodNerTest, AnnotateNonstandardText) {
  std::unique_ptr<PodNerAnnotator> annotator =
      PodNerAnnotator::Create(model_, *unilib_);
  ASSERT_TRUE(annotator != nullptr);

  const std::string nonstandard_text =
      "abcNxCDU1RWNvbXByLXI4NS8xNzcwLzE3NzA4NDY2L3J1Ymluby1raWRzLXJlY2xpbmVyLXd"
      "pdGgtY3VwLWhvbGRlci5qcGc=/"
      "UnViaW5vIEtpZHMgUmVjbGluZXIgd2l0aCBDdXAgSG9sZGVyIGJ5IEhhcnJpZXQgQmVl."
      "html>";
  std::vector<AnnotatedSpan> annotations;
  ASSERT_TRUE(
      annotator->Annotate(UTF8ToUnicodeText(nonstandard_text), &annotations));
  EXPECT_THAT(annotations, IsEmpty());
}

TEST_F(PodNerTest, AnnotateTextWithLinefeed) {
  std::unique_ptr<PodNerAnnotator> annotator =
      PodNerAnnotator::Create(model_, *unilib_);
  ASSERT_TRUE(annotator != nullptr);

  std::string nonstandard_text = "My name is Kuba\x09";
  nonstandard_text += "and this is a test.";
  std::vector<AnnotatedSpan> annotations;
  ASSERT_TRUE(
      annotator->Annotate(UTF8ToUnicodeText(nonstandard_text), &annotations));
  EXPECT_THAT(annotations, Not(IsEmpty()));
  EXPECT_EQ(annotations[0].span, CodepointSpan(11, 15));

  nonstandard_text = "My name is Kuba\x09 and this is a test.";
  ASSERT_TRUE(
      annotator->Annotate(UTF8ToUnicodeText(nonstandard_text), &annotations));
  EXPECT_THAT(annotations, Not(IsEmpty()));
  EXPECT_EQ(annotations[0].span, CodepointSpan(11, 15));
}

TEST_F(PodNerTest, AnnotateWithUnknownWordpieces) {
  std::unique_ptr<PodNerAnnotator> annotator =
      PodNerAnnotator::Create(model_, *unilib_);
  ASSERT_TRUE(annotator != nullptr);

  const std::string long_text =
      "It is easy to spend a fun and exciting day in Seattle without a car.  "
      "There are lots of ways to modify this itinerary. Add a ferry ride "
      "from the waterfront. Spending the day at the Seattle Center or at the "
      "aquarium could easily extend this from one to several days. Take the "
      "Underground Tour in Pioneer Square. Visit the Klondike Gold Rush "
      "Museum which is fun and free.  In the summer months you can ride the "
      "passenger-only Water Taxi from the waterfront to West Seattle and "
      "Alki Beach. Here's a sample one day itinerary: Start at the Space "
      "Needle by taking the Seattle Monorail from downtown. Look around the "
      "Seattle Center or go to the Space Needle.";
  const std::string text_with_unknown_wordpieces = "před chvílí";

  std::vector<AnnotatedSpan> annotations;
  ASSERT_TRUE(
      annotator->Annotate(UTF8ToUnicodeText("Google New York , in New York. " +
                                            text_with_unknown_wordpieces),
                          &annotations));
  EXPECT_THAT(annotations, IsEmpty());
  ASSERT_TRUE(annotator->Annotate(
      UTF8ToUnicodeText(long_text + " " + text_with_unknown_wordpieces),
      &annotations));
  EXPECT_THAT(annotations, Not(IsEmpty()));
}

class PodNerTestWithOrWithoutFinalPeriod
    : public PodNerTest,
      public testing::WithParamInterface<bool> {};

INSTANTIATE_TEST_SUITE_P(TestAnnotateLongText,
                         PodNerTestWithOrWithoutFinalPeriod,
                         testing::Values(true, false));

TEST_P(PodNerTestWithOrWithoutFinalPeriod, AnnotateLongText) {
  std::unique_ptr<PodNerAnnotator> annotator = PodNerAnnotator::Create(
      GetParam() ? model_append_final_period_ : model_, *unilib_);
  ASSERT_TRUE(annotator != nullptr);

  const std::string long_text =
      "It is easy to spend a fun and exciting day in Seattle without a car.  "
      "There are lots of ways to modify this itinerary. Add a ferry ride "
      "from the waterfront. Spending the day at the Seattle Center or at the "
      "aquarium could easily extend this from one to several days. Take the "
      "Underground Tour in Pioneer Square. Visit the Klondike Gold Rush "
      "Museum which is fun and free.  In the summer months you can ride the "
      "passenger-only Water Taxi from the waterfront to West Seattle and "
      "Alki Beach. Here's a sample one day itinerary: Start at the Space "
      "Needle by taking the Seattle Monorail from downtown. Look around the "
      "Seattle Center or go to the Space Needle. If you're interested in "
      "music the EMP-SFM (Experience Music Project - Science Fiction Musuem) "
      "is located at the foot of the Space Needle.  It has a lot of rock'n "
      "roll memorabilia that you may find interesting.  The Chihuly Garden "
      "and Glass musuem is near the Space Needle and you can get a "
      "combination ticket for both.  It gets really good reviews.  If you're "
      "interested, then the Bill & Melinda Gates Foundation is across from "
      "the EMP and has a visitors center that is free.  Come see how Bill "
      "Gates is giving away his millions. Take the Monorail back downtown.  "
      "You will be at 5th and Pine (Westlake Center). Head west to the Pike "
      "Place Market. Look around then head for the Pike Place hill climb "
      "which is a series of steps that walk down to the waterfront. You will "
      "end up across the street from the Seattle Aquarium. Plenty of things "
      "to do on the waterfront, boat cruises, seafood restaurants, the "
      "Aquarium, or your typical tourist activities. You can walk or take "
      "the waterfront trolley bus.  Note that waterfront construction has "
      "relocated the  trolley Metro bus route 99 that will take you from "
      "Pioneer Square all the way to the end of the waterfront where you can "
      "visit the Seattle Art Musuem's XXX Sculpture Garden just north of "
      "Pier 70. The route goes thru Chinatown/International District, "
      "through Pioneer Square, up 1st ave past the Pike Place Market and to "
      "1st and Cedar which is walking distance to the Space Needle.  It then "
      "goes down Broad Street toward the Olympic Sculpture Garden.   It runs "
      "approximately every 30 minutes during the day and early evening.";
  std::vector<AnnotatedSpan> annotations;
  ASSERT_TRUE(annotator->Annotate(UTF8ToUnicodeText(long_text), &annotations));
  EXPECT_THAT(annotations, Not(IsEmpty()));

  const std::string location_from_beginning = "Seattle";
  int start_span_location_from_beginning =
      long_text.find(location_from_beginning);
  EXPECT_EQ(annotations[0].span,
            CodepointSpan(start_span_location_from_beginning,
                          start_span_location_from_beginning +
                              location_from_beginning.length()));

  const std::string location_from_end = "Olympic Sculpture Garden";
  int start_span_location_from_end = long_text.find(location_from_end);
  const AnnotatedSpan& last_annotation = *annotations.rbegin();
  EXPECT_EQ(
      last_annotation.span,
      CodepointSpan(start_span_location_from_end,
                    start_span_location_from_end + location_from_end.length()));
}

TEST_F(PodNerTest, SuggestSelectionLongText) {
  std::unique_ptr<PodNerAnnotator> annotator =
      PodNerAnnotator::Create(model_, *unilib_);
  ASSERT_TRUE(annotator != nullptr);

  const std::string long_text =
      "It is easy to spend a fun and exciting day in Seattle without a car.  "
      "There are lots of ways to modify this itinerary. Add a ferry ride "
      "from the waterfront. Spending the day at the Seattle Center or at the "
      "aquarium could easily extend this from one to several days. Take the "
      "Underground Tour in Pioneer Square. Visit the Klondike Gold Rush "
      "Museum which is fun and free.  In the summer months you can ride the "
      "passenger-only Water Taxi from the waterfront to West Seattle and "
      "Alki Beach. Here's a sample one day itinerary: Start at the Space "
      "Needle by taking the Seattle Monorail from downtown. Look around the "
      "Seattle Center or go to the Space Needle. If you're interested in "
      "music the EMP-SFM (Experience Music Project - Science Fiction Musuem) "
      "is located at the foot of the Space Needle.  It has a lot of rock'n "
      "roll memorabilia that you may find interesting.  The Chihuly Garden "
      "and Glass musuem is near the Space Needle and you can get a "
      "combination ticket for both.  It gets really good reviews.  If you're "
      "interested, then the Bill & Melinda Gates Foundation is across from "
      "the EMP and has a visitors center that is free.  Come see how Bill "
      "Gates is giving away his millions. Take the Monorail back downtown.  "
      "You will be at 5th and Pine (Westlake Center). Head west to the Pike "
      "Place Market. Look around then head for the Pike Place hill climb "
      "which is a series of steps that walk down to the waterfront. You will "
      "end up across the street from the Seattle Aquarium. Plenty of things "
      "to do on the waterfront, boat cruises, seafood restaurants, the "
      "Aquarium, or your typical tourist activities. You can walk or take "
      "the waterfront trolley bus.  Note that waterfront construction has "
      "relocated the  trolley Metro bus route 99 that will take you from "
      "Pioneer Square all the way to the end of the waterfront where you can "
      "visit the Seattle Art Musuem's XXX Sculpture Garden just north of "
      "Pier 70. The route goes thru Chinatown/International District, "
      "through Pioneer Square, up 1st ave past the Pike Place Market and to "
      "1st and Cedar which is walking distance to the Space Needle.  It then "
      "goes down Broad Street toward the Olympic Sculpture Garden.   It runs "
      "approximately every 30 minutes during the day and early evening.";
  const std::string klondike = "Klondike Gold Rush Museum";
  int klondike_start = long_text.find(klondike);

  AnnotatedSpan suggested_span;
  EXPECT_TRUE(annotator->SuggestSelection(UTF8ToUnicodeText(long_text),
                                          {klondike_start, klondike_start + 8},
                                          &suggested_span));
  EXPECT_EQ(suggested_span.span,
            CodepointSpan(klondike_start, klondike_start + klondike.length()));
}

TEST_F(PodNerTest, SuggestSelectionTest) {
  std::unique_ptr<PodNerAnnotator> annotator =
      PodNerAnnotator::Create(model_, *unilib_);
  ASSERT_TRUE(annotator != nullptr);

  AnnotatedSpan suggested_span;
  EXPECT_TRUE(annotator->SuggestSelection(
      UTF8ToUnicodeText("Google New York, in New York"), {7, 10},
      &suggested_span));
  EXPECT_EQ(suggested_span.span, CodepointSpan(7, 15));
  EXPECT_FALSE(annotator->SuggestSelection(
      UTF8ToUnicodeText("Google New York, in New York"), {17, 19},
      &suggested_span));
  EXPECT_EQ(suggested_span.span, CodepointSpan(kInvalidIndex, kInvalidIndex));
}

TEST_F(PodNerForAnnotationAndClassificationTest,
       SuggestSelectionWithDisabledSelectionReturnsNoResults) {
  std::unique_ptr<PodNerAnnotator> annotator =
      PodNerAnnotator::Create(model_, *unilib_);
  ASSERT_TRUE(annotator != nullptr);

  AnnotatedSpan suggested_span;
  EXPECT_FALSE(annotator->SuggestSelection(
      UTF8ToUnicodeText("Google New York, in New York"), {7, 10},
      &suggested_span));
}

TEST_F(PodNerTest, ClassifyTextTest) {
  std::unique_ptr<PodNerAnnotator> annotator =
      PodNerAnnotator::Create(model_, *unilib_);
  ASSERT_TRUE(annotator != nullptr);

  ClassificationResult result;
  ASSERT_TRUE(annotator->ClassifyText(UTF8ToUnicodeText("We met in New York"),
                                      {10, 18}, &result));
  EXPECT_EQ(result.collection, "location");
}

TEST_F(PodNerForSelectionTest,
       ClassifyTextWithDisabledClassificationReturnsFalse) {
  std::unique_ptr<PodNerAnnotator> annotator =
      PodNerAnnotator::Create(model_, *unilib_);
  ASSERT_TRUE(annotator != nullptr);

  ClassificationResult result;
  ASSERT_FALSE(annotator->ClassifyText(UTF8ToUnicodeText("We met in New York"),
                                       {10, 18}, &result));
}

TEST_F(PodNerTest, ThreadSafety) {
  std::unique_ptr<PodNerAnnotator> annotator =
      PodNerAnnotator::Create(model_, *unilib_);
  ASSERT_TRUE(annotator != nullptr);

  // Do inference in 20 threads. When run with --config=tsan, this should fire
  // if there's a problem.
  std::vector<std::thread> thread_pool(20);
  for (std::thread& thread : thread_pool) {
    thread = std::thread([&annotator]() {
      AnnotatedSpan suggested_span;
      EXPECT_TRUE(annotator->SuggestSelection(
          UTF8ToUnicodeText("Google New York, in New York"), {7, 10},
          &suggested_span));
      EXPECT_EQ(suggested_span.span, CodepointSpan(7, 15));
    });
  }
  for (std::thread& thread : thread_pool) {
    thread.join();
  }
}

}  // namespace
}  // namespace libtextclassifier3
