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

#include "annotator/pod_ner/utils.h"

#include <iterator>

#include "annotator/model_generated.h"
#include "annotator/types.h"
#include "utils/tokenizer-utils.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "absl/container/flat_hash_map.h"
#include "absl/strings/str_split.h"

namespace libtextclassifier3 {
namespace {

using ::testing::IsEmpty;
using ::testing::Not;

using PodNerModel_::CollectionT;
using PodNerModel_::LabelT;
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

constexpr float kPriorityScore = 0.;
const std::vector<std::string>& kCollectionNames =
    *new std::vector<std::string>{"undefined",    "location", "person", "art",
                                  "organization", "entitiy",  "xxx"};
const auto& kStringToBoiseType = *new absl::flat_hash_map<
    absl::string_view, libtextclassifier3::PodNerModel_::Label_::BoiseType>({
    {"B", libtextclassifier3::PodNerModel_::Label_::BoiseType_BEGIN},
    {"O", libtextclassifier3::PodNerModel_::Label_::BoiseType_O},
    {"I", libtextclassifier3::PodNerModel_::Label_::BoiseType_INTERMEDIATE},
    {"S", libtextclassifier3::PodNerModel_::Label_::BoiseType_SINGLE},
    {"E", libtextclassifier3::PodNerModel_::Label_::BoiseType_END},
});
const auto& kStringToMentionType = *new absl::flat_hash_map<
    absl::string_view, libtextclassifier3::PodNerModel_::Label_::MentionType>(
    {{"NAM", libtextclassifier3::PodNerModel_::Label_::MentionType_NAM},
     {"NOM", libtextclassifier3::PodNerModel_::Label_::MentionType_NOM}});
LabelT CreateLabel(BoiseType boise_type, MentionType mention_type,
                   int collection_id) {
  LabelT label;
  label.boise_type = boise_type;
  label.mention_type = mention_type;
  label.collection_id = collection_id;
  return label;
}
std::vector<PodNerModel_::LabelT> TagsToLabels(
    const std::vector<std::string>& tags) {
  std::vector<PodNerModel_::LabelT> labels;
  for (const auto& tag : tags) {
    if (tag == "O") {
      labels.emplace_back(CreateLabel(BoiseType_O, MentionType_UNDEFINED, 0));
    } else {
      std::vector<absl::string_view> tag_parts = absl::StrSplit(tag, '-');
      labels.emplace_back(CreateLabel(
          kStringToBoiseType.at(tag_parts[0]),
          kStringToMentionType.at(tag_parts[1]),
          std::distance(
              kCollectionNames.begin(),
              std::find(kCollectionNames.begin(), kCollectionNames.end(),
                        std::string(tag_parts[2].substr(
                            tag_parts[2].rfind('/') + 1))))));
    }
  }
  return labels;
}

std::vector<CollectionT> GetCollections() {
  std::vector<CollectionT> collections;
  for (const std::string& collection_name : kCollectionNames) {
    CollectionT collection;
    collection.name = collection_name;
    collection.single_token_priority_score = kPriorityScore;
    collection.multi_token_priority_score = kPriorityScore;
    collections.emplace_back(collection);
  }
  return collections;
}

class ConvertTagsToAnnotatedSpansTest : public testing::TestWithParam<bool> {};
INSTANTIATE_TEST_SUITE_P(TagsAndLabelsTest, ConvertTagsToAnnotatedSpansTest,
                         testing::Values(true, false));

TEST_P(ConvertTagsToAnnotatedSpansTest,
       ConvertTagsToAnnotatedSpansHandlesBIESequence) {
  std::vector<AnnotatedSpan> annotations;
  std::string text = "We met in New York City";
  std::vector<std::string> tags = {"O",
                                   "O",
                                   "O",
                                   "B-NAM-/saft/location",
                                   "I-NAM-/saft/location",
                                   "E-NAM-/saft/location"};
  if (GetParam()) {
    ASSERT_TRUE(ConvertTagsToAnnotatedSpans(
        VectorSpan<Token>(TokenizeOnSpace(text)), tags,
        /*label_filter=*/{"NAM", "NOM"},
        /*relaxed_inside_label_matching=*/false,
        /*relaxed_label_category_matching=*/false, kPriorityScore,
        &annotations));
  } else {
    ASSERT_TRUE(ConvertTagsToAnnotatedSpans(
        VectorSpan<Token>(TokenizeOnSpace(text)), TagsToLabels(tags),
        GetCollections(),
        /*mention_filter=*/{MentionType_NAM, MentionType_NOM},
        /*relaxed_inside_label_matching=*/false,
        /*relaxed_mention_type_matching=*/false, &annotations));
  }

  EXPECT_EQ(annotations.size(), 1);
  EXPECT_EQ(annotations[0].span, CodepointSpan(10, 23));
  EXPECT_EQ(annotations[0].classification[0].collection, "location");
}

TEST_P(ConvertTagsToAnnotatedSpansTest,
       ConvertTagsToAnnotatedSpansHandlesSSequence) {
  std::vector<AnnotatedSpan> annotations;
  std::string text = "His father was it.";
  std::vector<std::string> tags = {"O", "S-NAM-/saft/person", "O", "O"};
  if (GetParam()) {
    ASSERT_TRUE(ConvertTagsToAnnotatedSpans(
        VectorSpan<Token>(TokenizeOnSpace(text)), tags,
        /*label_filter=*/{"NAM", "NOM"},
        /*relaxed_inside_label_matching=*/false,
        /*relaxed_label_category_matching=*/false, kPriorityScore,
        &annotations));
  } else {
    ASSERT_TRUE(ConvertTagsToAnnotatedSpans(
        VectorSpan<Token>(TokenizeOnSpace(text)), TagsToLabels(tags),
        GetCollections(),
        /*mention_filter=*/{MentionType_NAM, MentionType_NOM},
        /*relaxed_inside_label_matching=*/false,
        /*relaxed_mention_type_matching=*/false, &annotations));
  }

  EXPECT_EQ(annotations.size(), 1);
  EXPECT_EQ(annotations[0].span, CodepointSpan(4, 10));
  EXPECT_EQ(annotations[0].classification[0].collection, "person");
}

TEST_P(ConvertTagsToAnnotatedSpansTest,
       ConvertTagsToAnnotatedSpansHandlesMultiple) {
  std::vector<AnnotatedSpan> annotations;
  std::string text =
      "Jaromir Jagr, Barak Obama and I met in Google New York City";
  std::vector<std::string> tags = {"B-NAM-/saft/person",
                                   "E-NAM-/saft/person",
                                   "B-NOM-/saft/person",
                                   "E-NOM-/saft/person",
                                   "O",
                                   "O",
                                   "O",
                                   "O",
                                   "S-NAM-/saft/organization",
                                   "B-NAM-/saft/location",
                                   "I-NAM-/saft/location",
                                   "E-NAM-/saft/location"};
  if (GetParam()) {
    ASSERT_TRUE(ConvertTagsToAnnotatedSpans(
        VectorSpan<Token>(TokenizeOnSpace(text)), tags,
        /*label_filter=*/{"NAM", "NOM"},
        /*relaxed_inside_label_matching=*/false,
        /*relaxed_label_category_matching=*/false, kPriorityScore,
        &annotations));
  } else {
    ASSERT_TRUE(ConvertTagsToAnnotatedSpans(
        VectorSpan<Token>(TokenizeOnSpace(text)), TagsToLabels(tags),
        GetCollections(),
        /*mention_filter=*/{MentionType_NAM, MentionType_NOM},
        /*relaxed_inside_label_matching=*/false,
        /*relaxed_mention_type_matching=*/false, &annotations));

    ASSERT_EQ(annotations.size(), 4);
    EXPECT_EQ(annotations[0].span, CodepointSpan(0, 13));
    ASSERT_THAT(annotations[0].classification, Not(IsEmpty()));
    EXPECT_EQ(annotations[0].classification[0].collection, "person");
    EXPECT_EQ(annotations[1].span, CodepointSpan(14, 25));
    ASSERT_THAT(annotations[1].classification, Not(IsEmpty()));
    EXPECT_EQ(annotations[1].classification[0].collection, "person");
    EXPECT_EQ(annotations[2].span, CodepointSpan(39, 45));
    ASSERT_THAT(annotations[2].classification, Not(IsEmpty()));
    EXPECT_EQ(annotations[2].classification[0].collection, "organization");
    EXPECT_EQ(annotations[3].span, CodepointSpan(46, 59));
    ASSERT_THAT(annotations[3].classification, Not(IsEmpty()));
    EXPECT_EQ(annotations[3].classification[0].collection, "location");
  }
}

TEST_P(ConvertTagsToAnnotatedSpansTest,
       ConvertTagsToAnnotatedSpansHandlesMultipleFirstTokenNotFirst) {
  std::vector<AnnotatedSpan> annotations;
  std::vector<Token> original_tokens = TokenizeOnSpace(
      "Jaromir Jagr, Barak Obama and I met in Google New York City");
  std::vector<std::string> tags = {"B-NOM-/saft/person",
                                   "E-NOM-/saft/person",
                                   "O",
                                   "O",
                                   "O",
                                   "O",
                                   "S-NAM-/saft/organization",
                                   "B-NAM-/saft/location",
                                   "I-NAM-/saft/location",
                                   "E-NAM-/saft/location"};
  if (GetParam()) {
    ASSERT_TRUE(ConvertTagsToAnnotatedSpans(
        VectorSpan<Token>(original_tokens.begin() + 2, original_tokens.end()),
        tags,
        /*label_filter=*/{"NAM", "NOM"},
        /*relaxed_inside_label_matching=*/false,
        /*relaxed_label_category_matching=*/false, kPriorityScore,
        &annotations));
  } else {
    ASSERT_TRUE(ConvertTagsToAnnotatedSpans(
        VectorSpan<Token>(original_tokens.begin() + 2, original_tokens.end()),
        TagsToLabels(tags), GetCollections(),
        /*mention_filter=*/{MentionType_NAM, MentionType_NOM},
        /*relaxed_inside_label_matching=*/false,
        /*relaxed_mention_type_matching=*/false, &annotations));
  }

  ASSERT_EQ(annotations.size(), 3);
  EXPECT_EQ(annotations[0].span, CodepointSpan(14, 25));
  ASSERT_THAT(annotations[0].classification, Not(IsEmpty()));
  EXPECT_EQ(annotations[0].classification[0].collection, "person");
  EXPECT_EQ(annotations[1].span, CodepointSpan(39, 45));
  ASSERT_THAT(annotations[1].classification, Not(IsEmpty()));
  EXPECT_EQ(annotations[1].classification[0].collection, "organization");
  EXPECT_EQ(annotations[2].span, CodepointSpan(46, 59));
  ASSERT_THAT(annotations[2].classification, Not(IsEmpty()));
  EXPECT_EQ(annotations[2].classification[0].collection, "location");
}

TEST(PodNerUtilsTest, ConvertTagsToAnnotatedSpansInvalidCollection) {
  std::vector<AnnotatedSpan> annotations;
  std::string text = "We met in New York City";
  std::vector<std::string> tags = {"O", "O", "S-NAM-/saft/invalid_collection"};

  ASSERT_FALSE(ConvertTagsToAnnotatedSpans(
      VectorSpan<Token>(TokenizeOnSpace(text)), TagsToLabels(tags),
      GetCollections(),
      /*mention_filter=*/{MentionType_NAM, MentionType_NOM},
      /*relaxed_inside_label_matching=*/false,
      /*relaxed_mention_type_matching=*/false, &annotations));
}

TEST_P(ConvertTagsToAnnotatedSpansTest,
       ConvertTagsToAnnotatedSpansIgnoresInconsistentStart) {
  std::vector<AnnotatedSpan> annotations;
  std::string text = "We met in New York City";
  std::vector<std::string> tags = {"O",
                                   "O",
                                   "O",
                                   "B-NAM-/saft/xxx",
                                   "I-NAM-/saft/location",
                                   "E-NAM-/saft/location"};
  if (GetParam()) {
    ASSERT_TRUE(ConvertTagsToAnnotatedSpans(
        VectorSpan<Token>(TokenizeOnSpace(text)), tags,
        /*label_filter=*/{"NAM", "NOM"},
        /*relaxed_inside_label_matching=*/false,
        /*relaxed_label_category_matching=*/false, kPriorityScore,
        &annotations));
  } else {
    ASSERT_TRUE(ConvertTagsToAnnotatedSpans(
        VectorSpan<Token>(TokenizeOnSpace(text)), TagsToLabels(tags),
        GetCollections(),
        /*mention_filter=*/{MentionType_NAM, MentionType_NOM},
        /*relaxed_inside_label_matching=*/false,
        /*relaxed_mention_type_matching=*/false, &annotations));
  }
  EXPECT_THAT(annotations, IsEmpty());
}

TEST_P(ConvertTagsToAnnotatedSpansTest,
       ConvertTagsToAnnotatedSpansIgnoresInconsistentLabelTypeStart) {
  std::vector<AnnotatedSpan> annotations;
  std::string text = "We met in New York City";
  std::vector<std::string> tags = {"O",
                                   "O",
                                   "O",
                                   "B-NOM-/saft/location",
                                   "I-NAM-/saft/location",
                                   "E-NAM-/saft/location"};
  if (GetParam()) {
    ASSERT_TRUE(ConvertTagsToAnnotatedSpans(
        VectorSpan<Token>(TokenizeOnSpace(text)), tags,
        /*label_filter=*/{"NAM", "NOM"},
        /*relaxed_inside_label_matching=*/false,
        /*relaxed_label_category_matching=*/false, kPriorityScore,
        &annotations));
  } else {
    ASSERT_TRUE(ConvertTagsToAnnotatedSpans(
        VectorSpan<Token>(TokenizeOnSpace(text)), TagsToLabels(tags),
        GetCollections(),
        /*mention_filter=*/{MentionType_NAM, MentionType_NOM},
        /*relaxed_inside_label_matching=*/false,
        /*relaxed_mention_type_matching=*/false, &annotations));
  }

  EXPECT_THAT(annotations, IsEmpty());
}

TEST_P(ConvertTagsToAnnotatedSpansTest,
       ConvertTagsToAnnotatedSpansIgnoresInconsistentInside) {
  std::vector<AnnotatedSpan> annotations;
  std::string text = "We met in New York City";
  std::vector<std::string> tags = {"O",
                                   "O",
                                   "O",
                                   "B-NAM-/saft/location",
                                   "I-NAM-/saft/xxx",
                                   "E-NAM-/saft/location"};
  if (GetParam()) {
    ASSERT_TRUE(ConvertTagsToAnnotatedSpans(
        VectorSpan<Token>(TokenizeOnSpace(text)), tags,
        /*label_filter=*/{"NAM", "NOM"},
        /*relaxed_inside_label_matching=*/false,
        /*relaxed_label_category_matching=*/false, kPriorityScore,
        &annotations));
  } else {
    ASSERT_TRUE(ConvertTagsToAnnotatedSpans(
        VectorSpan<Token>(TokenizeOnSpace(text)), TagsToLabels(tags),
        GetCollections(),
        /*mention_filter=*/{MentionType_NAM, MentionType_NOM},
        /*relaxed_inside_label_matching=*/false,
        /*relaxed_mention_type_matching=*/false, &annotations));
  }

  EXPECT_THAT(annotations, IsEmpty());
}

TEST_P(ConvertTagsToAnnotatedSpansTest,
       ConvertTagsToAnnotatedSpansIgnoresInconsistentLabelTypeInside) {
  std::vector<AnnotatedSpan> annotations;
  std::string text = "We met in New York City";
  std::vector<std::string> tags = {"O",
                                   "O",
                                   "O",
                                   "B-NAM-/saft/location",
                                   "I-NOM-/saft/location",
                                   "E-NAM-/saft/location"};
  if (GetParam()) {
    ASSERT_TRUE(ConvertTagsToAnnotatedSpans(
        VectorSpan<Token>(TokenizeOnSpace(text)), tags,
        /*label_filter=*/{"NAM", "NOM"},
        /*relaxed_inside_label_matching=*/false,
        /*relaxed_label_category_matching=*/false, kPriorityScore,
        &annotations));
  } else {
    ASSERT_TRUE(ConvertTagsToAnnotatedSpans(
        VectorSpan<Token>(TokenizeOnSpace(text)), TagsToLabels(tags),
        GetCollections(),
        /*mention_filter=*/{MentionType_NAM, MentionType_NOM},
        /*relaxed_inside_label_matching=*/false,
        /*relaxed_mention_type_matching=*/false, &annotations));
  }
  EXPECT_THAT(annotations, IsEmpty());
}

TEST_P(ConvertTagsToAnnotatedSpansTest,
       ConvertTagsToAnnotatedSpansHandlesInconsistentInside) {
  std::vector<AnnotatedSpan> annotations;
  std::string text = "We met in New York City";
  std::vector<std::string> tags = {"O",
                                   "O",
                                   "O",
                                   "B-NAM-/saft/location",
                                   "I-NAM-/saft/xxx",
                                   "E-NAM-/saft/location"};
  if (GetParam()) {
    ASSERT_TRUE(ConvertTagsToAnnotatedSpans(
        VectorSpan<Token>(TokenizeOnSpace(text)), tags,
        /*label_filter=*/{"NAM", "NOM"},
        /*relaxed_inside_label_matching=*/true,
        /*relaxed_label_category_matching=*/false, kPriorityScore,
        &annotations));
  } else {
    ASSERT_TRUE(ConvertTagsToAnnotatedSpans(
        VectorSpan<Token>(TokenizeOnSpace(text)), TagsToLabels(tags),
        GetCollections(),
        /*mention_filter=*/{MentionType_NAM, MentionType_NOM},
        /*relaxed_inside_label_matching=*/true,
        /*relaxed_mention_type_matching=*/false, &annotations));
  }

  EXPECT_EQ(annotations.size(), 1);
  EXPECT_EQ(annotations[0].span, CodepointSpan(10, 23));
  EXPECT_EQ(annotations[0].classification[0].collection, "location");
}

TEST_P(ConvertTagsToAnnotatedSpansTest,
       ConvertTagsToAnnotatedSpansIgnoresInconsistentEnd) {
  std::vector<AnnotatedSpan> annotations;
  std::string text = "We met in New York City";
  std::vector<std::string> tags = {"O",
                                   "O",
                                   "O",
                                   "B-NAM-/saft/location",
                                   "I-NAM-/saft/location",
                                   "E-NAM-/saft/xxx"};
  if (GetParam()) {
    ASSERT_TRUE(ConvertTagsToAnnotatedSpans(
        VectorSpan<Token>(TokenizeOnSpace(text)), tags,
        /*label_filter=*/{"NAM", "NOM"},
        /*relaxed_inside_label_matching=*/false,
        /*relaxed_label_category_matching=*/false, kPriorityScore,
        &annotations));
  } else {
    ASSERT_TRUE(ConvertTagsToAnnotatedSpans(
        VectorSpan<Token>(TokenizeOnSpace(text)), TagsToLabels(tags),
        GetCollections(),
        /*mention_filter=*/{MentionType_NAM, MentionType_NOM},
        /*relaxed_inside_label_matching=*/false,
        /*relaxed_mention_type_matching=*/false, &annotations));
  }

  EXPECT_THAT(annotations, IsEmpty());
}

TEST_P(ConvertTagsToAnnotatedSpansTest,
       ConvertTagsToAnnotatedSpansIgnoresInconsistentLabelTypeEnd) {
  std::vector<AnnotatedSpan> annotations;
  std::string text = "We met in New York City";
  std::vector<std::string> tags = {"O",
                                   "O",
                                   "O",
                                   "B-NAM-/saft/location",
                                   "I-NAM-/saft/location",
                                   "E-NOM-/saft/location"};
  if (GetParam()) {
    ASSERT_TRUE(ConvertTagsToAnnotatedSpans(
        VectorSpan<Token>(TokenizeOnSpace(text)), tags,
        /*label_filter=*/{"NAM", "NOM"},
        /*relaxed_inside_label_matching=*/false,
        /*relaxed_label_category_matching=*/false, kPriorityScore,
        &annotations));
  } else {
    ASSERT_TRUE(ConvertTagsToAnnotatedSpans(
        VectorSpan<Token>(TokenizeOnSpace(text)), TagsToLabels(tags),
        GetCollections(),
        /*mention_filter=*/{MentionType_NAM, MentionType_NOM},
        /*relaxed_inside_label_matching=*/false,
        /*relaxed_mention_type_matching=*/false, &annotations));
  }

  EXPECT_THAT(annotations, IsEmpty());
}

TEST_P(
    ConvertTagsToAnnotatedSpansTest,
    ConvertTagsToAnnotatedSpansHandlesInconsistentLabelTypeWhenEntityMatches) {
  std::vector<AnnotatedSpan> annotations;
  std::string text = "We met in New York City";
  std::vector<std::string> tags = {"O",
                                   "O",
                                   "O",
                                   "B-NOM-/saft/location",
                                   "I-NOM-/saft/location",
                                   "E-NAM-/saft/location"};
  if (GetParam()) {
    ASSERT_TRUE(ConvertTagsToAnnotatedSpans(
        VectorSpan<Token>(TokenizeOnSpace(text)), tags,
        /*label_filter=*/{"NAM", "NOM"},
        /*relaxed_inside_label_matching=*/false,
        /*relaxed_label_category_matching=*/true, kPriorityScore,
        &annotations));
  } else {
    ASSERT_TRUE(ConvertTagsToAnnotatedSpans(
        VectorSpan<Token>(TokenizeOnSpace(text)), TagsToLabels(tags),
        GetCollections(),
        /*mention_filter=*/{MentionType_NAM, MentionType_NOM},
        /*relaxed_inside_label_matching=*/false,
        /*relaxed_mention_type_matching=*/true, &annotations));
  }

  EXPECT_EQ(annotations.size(), 1);
  EXPECT_EQ(annotations[0].span, CodepointSpan(10, 23));
  EXPECT_EQ(annotations[0].classification[0].collection, "location");
}

TEST_P(ConvertTagsToAnnotatedSpansTest,
       ConvertTagsToAnnotatedSpansIgnoresFilteredLabel) {
  std::vector<AnnotatedSpan> annotations;
  std::string text = "We met in New York City";
  std::vector<std::string> tags = {"O",
                                   "O",
                                   "O",
                                   "B-NAM-/saft/location",
                                   "I-NAM-/saft/location",
                                   "E-NAM-/saft/location"};
  if (GetParam()) {
    ASSERT_TRUE(ConvertTagsToAnnotatedSpans(
        VectorSpan<Token>(TokenizeOnSpace(text)), tags,
        /*label_filter=*/{"NOM"},
        /*relaxed_inside_label_matching=*/false,
        /*relaxed_label_category_matching=*/false, kPriorityScore,
        &annotations));
  } else {
    ASSERT_TRUE(ConvertTagsToAnnotatedSpans(
        VectorSpan<Token>(TokenizeOnSpace(text)), TagsToLabels(tags),
        GetCollections(),
        /*mention_filter=*/{MentionType_NOM},
        /*relaxed_inside_label_matching=*/false,
        /*relaxed_mention_type_matching=*/false, &annotations));
  }

  EXPECT_THAT(annotations, IsEmpty());
}

TEST_P(ConvertTagsToAnnotatedSpansTest,
       ConvertTagsToAnnotatedSpansWithEmptyLabelFilterIgnoresAll) {
  std::vector<AnnotatedSpan> annotations;
  std::string text = "We met in New York City";
  std::vector<std::string> tags = {"O",
                                   "O",
                                   "O",
                                   "B-NOM-/saft/location",
                                   "I-NOM-/saft/location",
                                   "E-NOM-/saft/location"};
  if (GetParam()) {
    ASSERT_TRUE(ConvertTagsToAnnotatedSpans(
        VectorSpan<Token>(TokenizeOnSpace(text)), tags,
        /*label_filter=*/{},
        /*relaxed_inside_label_matching=*/false,
        /*relaxed_label_category_matching=*/false, kPriorityScore,
        &annotations));
  } else {
    ASSERT_TRUE(ConvertTagsToAnnotatedSpans(
        VectorSpan<Token>(TokenizeOnSpace(text)), TagsToLabels(tags),
        GetCollections(),
        /*mention_filter=*/{},
        /*relaxed_inside_label_matching=*/false,
        /*relaxed_mention_type_matching=*/false, &annotations));
  }

  EXPECT_THAT(annotations, IsEmpty());
}

TEST(PodNerUtilsTest, MergeLabelsIntoLeftSequence) {
  std::vector<PodNerModel_::LabelT> original_labels_left;
  original_labels_left.emplace_back(
      CreateLabel(BoiseType_O, MentionType_UNDEFINED, 0));
  original_labels_left.emplace_back(
      CreateLabel(BoiseType_O, MentionType_UNDEFINED, 0));
  original_labels_left.emplace_back(
      CreateLabel(BoiseType_O, MentionType_UNDEFINED, 0));
  original_labels_left.emplace_back(
      CreateLabel(BoiseType_SINGLE, MentionType_NAM, 1));
  original_labels_left.emplace_back(
      CreateLabel(BoiseType_O, MentionType_UNDEFINED, 0));
  original_labels_left.emplace_back(
      CreateLabel(BoiseType_O, MentionType_UNDEFINED, 0));
  original_labels_left.emplace_back(
      CreateLabel(BoiseType_SINGLE, MentionType_NAM, 2));

  std::vector<PodNerModel_::LabelT> labels_right;
  labels_right.emplace_back(
      CreateLabel(BoiseType_BEGIN, MentionType_UNDEFINED, 3));
  labels_right.emplace_back(CreateLabel(BoiseType_O, MentionType_UNDEFINED, 0));
  labels_right.emplace_back(CreateLabel(BoiseType_O, MentionType_UNDEFINED, 0));
  labels_right.emplace_back(CreateLabel(BoiseType_BEGIN, MentionType_NAM, 4));
  labels_right.emplace_back(
      CreateLabel(BoiseType_INTERMEDIATE, MentionType_UNDEFINED, 4));
  labels_right.emplace_back(
      CreateLabel(BoiseType_END, MentionType_UNDEFINED, 4));
  std::vector<PodNerModel_::LabelT> labels_left = original_labels_left;

  ASSERT_TRUE(MergeLabelsIntoLeftSequence(labels_right,
                                          /*index_first_right_tag_in_left=*/3,
                                          &labels_left));
  EXPECT_EQ(labels_left.size(), 9);
  EXPECT_EQ(labels_left[0].collection_id, 0);
  EXPECT_EQ(labels_left[1].collection_id, 0);
  EXPECT_EQ(labels_left[2].collection_id, 0);
  EXPECT_EQ(labels_left[3].collection_id, 1);
  EXPECT_EQ(labels_left[4].collection_id, 0);
  EXPECT_EQ(labels_left[5].collection_id, 0);
  EXPECT_EQ(labels_left[6].collection_id, 4);
  EXPECT_EQ(labels_left[7].collection_id, 4);
  EXPECT_EQ(labels_left[8].collection_id, 4);

  labels_left = original_labels_left;
  ASSERT_TRUE(MergeLabelsIntoLeftSequence(labels_right,
                                          /*index_first_right_tag_in_left=*/2,
                                          &labels_left));
  EXPECT_EQ(labels_left.size(), 8);
  EXPECT_EQ(labels_left[0].collection_id, 0);
  EXPECT_EQ(labels_left[1].collection_id, 0);
  EXPECT_EQ(labels_left[2].collection_id, 0);
  EXPECT_EQ(labels_left[3].collection_id, 1);
  EXPECT_EQ(labels_left[4].collection_id, 0);
  EXPECT_EQ(labels_left[5].collection_id, 4);
  EXPECT_EQ(labels_left[6].collection_id, 4);
  EXPECT_EQ(labels_left[7].collection_id, 4);
}

TEST(PodNerUtilsTest, FindWordpiecesWindowAroundSpanAllWordpices) {
  std::vector<Token> tokens{{"a", 0, 1},    {"b", 2, 3},     {"c", 4, 5},
                            {"d", 6, 7},    {"e", 8, 9},     {"f", 10, 11},
                            {"my", 12, 14}, {"name", 15, 19}};
  std::vector<int32_t> word_starts{0, 2, 3, 5, 6, 7, 10, 11};

  WordpieceSpan wordpieceSpan = internal::FindWordpiecesWindowAroundSpan(
      {2, 3}, tokens, word_starts,
      /*num_wordpieces=*/12,
      /*max_num_wordpieces_in_window=*/15);
  EXPECT_EQ(wordpieceSpan, WordpieceSpan(0, 12));
}

TEST(PodNerUtilsTest, FindWordpiecesWindowAroundSpanInMiddle) {
  std::vector<Token> tokens{{"a", 0, 1},    {"b", 2, 3},     {"c", 4, 5},
                            {"d", 6, 7},    {"e", 8, 9},     {"f", 10, 11},
                            {"my", 12, 14}, {"name", 15, 19}};
  std::vector<int32_t> word_starts{0, 2, 3, 5, 6, 7, 10, 11};

  WordpieceSpan wordpieceSpan = internal::FindWordpiecesWindowAroundSpan(
      {6, 7}, tokens, word_starts,
      /*num_wordpieces=*/12,
      /*max_num_wordpieces_in_window=*/5);
  EXPECT_EQ(wordpieceSpan, WordpieceSpan(3, 8));

  wordpieceSpan = internal::FindWordpiecesWindowAroundSpan(
      {6, 7}, tokens, word_starts,
      /*num_wordpieces=*/12,
      /*max_num_wordpieces_in_window=*/6);
  EXPECT_EQ(wordpieceSpan, WordpieceSpan(3, 9));

  wordpieceSpan = internal::FindWordpiecesWindowAroundSpan(
      {12, 14}, tokens, word_starts,
      /*num_wordpieces=*/12,
      /*max_num_wordpieces_in_window=*/3);
  EXPECT_EQ(wordpieceSpan, WordpieceSpan(9, 12));
}

TEST(PodNerUtilsTest, FindWordpiecesWindowAroundSpanCloseToStart) {
  std::vector<Token> tokens{{"a", 0, 1},    {"b", 2, 3},     {"c", 4, 5},
                            {"d", 6, 7},    {"e", 8, 9},     {"f", 10, 11},
                            {"my", 12, 14}, {"name", 15, 19}};
  std::vector<int32_t> word_starts{0, 2, 3, 5, 6, 7, 10, 11};

  WordpieceSpan wordpieceSpan = internal::FindWordpiecesWindowAroundSpan(
      {2, 3}, tokens, word_starts,
      /*num_wordpieces=*/12,
      /*max_num_wordpieces_in_window=*/7);
  EXPECT_EQ(wordpieceSpan, WordpieceSpan(0, 7));
}

TEST(PodNerUtilsTest, FindWordpiecesWindowAroundSpanCloseToEnd) {
  std::vector<Token> tokens{{"a", 0, 1},    {"b", 2, 3},     {"c", 4, 5},
                            {"d", 6, 7},    {"e", 8, 9},     {"f", 10, 11},
                            {"my", 12, 14}, {"name", 15, 19}};
  std::vector<int32_t> word_starts{0, 2, 3, 5, 6, 7, 10, 11};

  WordpieceSpan wordpieceSpan = internal::FindWordpiecesWindowAroundSpan(
      {15, 19}, tokens, word_starts,
      /*num_wordpieces=*/12,
      /*max_num_wordpieces_in_window=*/7);
  EXPECT_EQ(wordpieceSpan, WordpieceSpan(5, 12));
}

TEST(PodNerUtilsTest, FindWordpiecesWindowAroundSpanBigSpan) {
  std::vector<Token> tokens{{"a", 0, 1},    {"b", 2, 3},     {"c", 4, 5},
                            {"d", 6, 7},    {"e", 8, 9},     {"f", 10, 11},
                            {"my", 12, 14}, {"name", 15, 19}};
  std::vector<int32_t> word_starts{0, 2, 3, 5, 6, 7, 10, 11};

  WordpieceSpan wordpieceSpan = internal::FindWordpiecesWindowAroundSpan(
      {0, 19}, tokens, word_starts,
      /*num_wordpieces=*/12,
      /*max_num_wordpieces_in_window=*/5);
  EXPECT_EQ(wordpieceSpan, WordpieceSpan(0, 12));
}

TEST(PodNerUtilsTest, FindFullTokensSpanInWindow) {
  std::vector<int32_t> word_starts{0, 2, 3, 5, 6, 7, 10, 11};
  int first_token_index, num_tokens;
  WordpieceSpan updated_wordpiece_span = internal::FindFullTokensSpanInWindow(
      word_starts, /*wordpiece_span=*/{0, 6},
      /*max_num_wordpieces=*/6, /*num_wordpieces=*/12, &first_token_index,
      &num_tokens);
  EXPECT_EQ(updated_wordpiece_span, WordpieceSpan(0, 6));
  EXPECT_EQ(first_token_index, 0);
  EXPECT_EQ(num_tokens, 4);

  updated_wordpiece_span = internal::FindFullTokensSpanInWindow(
      word_starts, /*wordpiece_span=*/{2, 6},
      /*max_num_wordpieces=*/6, /*num_wordpieces=*/12, &first_token_index,
      &num_tokens);
  EXPECT_EQ(updated_wordpiece_span, WordpieceSpan(2, 6));
  EXPECT_EQ(first_token_index, 1);
  EXPECT_EQ(num_tokens, 3);
}

TEST(PodNerUtilsTest, FindFullTokensSpanInWindowStartInMiddleOfToken) {
  std::vector<int32_t> word_starts{0, 2, 3, 5, 6, 7, 10, 11};
  int first_token_index, num_tokens;
  WordpieceSpan updated_wordpiece_span = internal::FindFullTokensSpanInWindow(
      word_starts, /*wordpiece_span=*/{1, 6},
      /*max_num_wordpieces=*/6, /*num_wordpieces=*/12, &first_token_index,
      &num_tokens);
  EXPECT_EQ(updated_wordpiece_span, WordpieceSpan(0, 6));
  EXPECT_EQ(first_token_index, 0);
  EXPECT_EQ(num_tokens, 4);
}

TEST(PodNerUtilsTest, FindFullTokensSpanInWindowEndsInMiddleOfToken) {
  std::vector<int32_t> word_starts{0, 2, 3, 5, 6, 7, 10, 11};
  int first_token_index, num_tokens;
  WordpieceSpan updated_wordpiece_span = internal::FindFullTokensSpanInWindow(
      word_starts, /*wordpiece_span=*/{1, 9},
      /*max_num_wordpieces=*/6, /*num_wordpieces=*/12, &first_token_index,
      &num_tokens);
  EXPECT_EQ(updated_wordpiece_span, WordpieceSpan(0, 6));
  EXPECT_EQ(first_token_index, 0);
  EXPECT_EQ(num_tokens, 4);
}
TEST(PodNerUtilsTest, FindFirstFullTokenIndexSizeOne) {
  std::vector<int32_t> word_starts{1, 2, 3, 5, 6, 7, 10, 11};
  int index_first_full_token = internal::FindFirstFullTokenIndex(
      word_starts, /*first_wordpiece_index=*/2);
  EXPECT_EQ(index_first_full_token, 1);
}

TEST(PodNerUtilsTest, FindFirstFullTokenIndexFirst) {
  std::vector<int32_t> word_starts{1, 2, 3, 5, 6, 7, 10, 11};
  int index_first_full_token = internal::FindFirstFullTokenIndex(
      word_starts, /*first_wordpiece_index=*/0);
  EXPECT_EQ(index_first_full_token, 0);
}

TEST(PodNerUtilsTest, FindFirstFullTokenIndexSizeGreaterThanOne) {
  std::vector<int32_t> word_starts{1, 2, 3, 5, 6, 7, 10, 11};
  int index_first_full_token = internal::FindFirstFullTokenIndex(
      word_starts, /*first_wordpiece_index=*/4);
  EXPECT_EQ(index_first_full_token, 2);
}

TEST(PodNerUtilsTest, FindLastFullTokenIndexSizeOne) {
  std::vector<int32_t> word_starts{1, 2, 3, 5, 6, 7, 10, 11};
  int index_last_full_token = internal::FindLastFullTokenIndex(
      word_starts, /*num_wordpieces=*/12, /*wordpiece_end=*/3);
  EXPECT_EQ(index_last_full_token, 1);
}

TEST(PodNerUtilsTest, FindLastFullTokenIndexSizeGreaterThanOne) {
  std::vector<int32_t> word_starts{1, 3, 4, 6, 8, 9};
  int index_last_full_token = internal::FindLastFullTokenIndex(
      word_starts, /*num_wordpieces=*/10, /*wordpiece_end=*/6);
  EXPECT_EQ(index_last_full_token, 2);

  index_last_full_token = internal::FindLastFullTokenIndex(
      word_starts, /*num_wordpieces=*/10, /*wordpiece_end=*/7);
  EXPECT_EQ(index_last_full_token, 2);

  index_last_full_token = internal::FindLastFullTokenIndex(
      word_starts, /*num_wordpieces=*/10, /*wordpiece_end=*/5);
  EXPECT_EQ(index_last_full_token, 1);
}

TEST(PodNerUtilsTest, FindLastFullTokenIndexLast) {
  std::vector<int32_t> word_starts{1, 2, 3, 5, 6, 7, 10, 11};
  int index_last_full_token = internal::FindLastFullTokenIndex(
      word_starts, /*num_wordpieces=*/12, /*wordpiece_end=*/12);
  EXPECT_EQ(index_last_full_token, 7);

  index_last_full_token = internal::FindLastFullTokenIndex(
      word_starts, /*num_wordpieces=*/14, /*wordpiece_end=*/14);
  EXPECT_EQ(index_last_full_token, 7);
}

TEST(PodNerUtilsTest, FindLastFullTokenIndexBeforeLast) {
  std::vector<int32_t> word_starts{1, 2, 3, 5, 6, 7, 10, 11};
  int index_last_full_token = internal::FindLastFullTokenIndex(
      word_starts, /*num_wordpieces=*/15, /*wordpiece_end=*/12);
  EXPECT_EQ(index_last_full_token, 6);
}

TEST(PodNerUtilsTest, ExpandWindowAndAlignSequenceSmallerThanMax) {
  WordpieceSpan maxWordpieceSpan = internal::ExpandWindowAndAlign(
      /*max_num_wordpieces_in_window=*/10, /*num_wordpieces=*/8,
      /*wordpiece_span_to_expand=*/{2, 5});
  EXPECT_EQ(maxWordpieceSpan, WordpieceSpan(0, 8));
}

TEST(PodNerUtilsTest, ExpandWindowAndAlignWindowLengthGreaterThanMax) {
  WordpieceSpan maxWordpieceSpan = internal::ExpandWindowAndAlign(
      /*max_num_wordpieces_in_window=*/10, /*num_wordpieces=*/100,
      /*wordpiece_span_to_expand=*/{2, 51});
  EXPECT_EQ(maxWordpieceSpan, WordpieceSpan(2, 51));
}

TEST(PodNerUtilsTest, ExpandWindowAndAlignFirstIndexCloseToStart) {
  WordpieceSpan maxWordpieceSpan = internal::ExpandWindowAndAlign(
      /*max_num_wordpieces_in_window=*/10, /*num_wordpieces=*/20,
      /*wordpiece_span_to_expand=*/{2, 4});
  EXPECT_EQ(maxWordpieceSpan, WordpieceSpan(0, 10));
}

TEST(PodNerUtilsTest, ExpandWindowAndAlignFirstIndexCloseToEnd) {
  WordpieceSpan maxWordpieceSpan = internal::ExpandWindowAndAlign(
      /*max_num_wordpieces_in_window=*/10, /*num_wordpieces=*/20,
      /*wordpiece_span_to_expand=*/{18, 20});
  EXPECT_EQ(maxWordpieceSpan, WordpieceSpan(10, 20));
}

TEST(PodNerUtilsTest, ExpandWindowAndAlignFirstIndexInTheMiddle) {
  int window_first_wordpiece_index = 10;
  int window_last_wordpiece_index = 11;
  WordpieceSpan maxWordpieceSpan = internal::ExpandWindowAndAlign(
      /*max_num_wordpieces_in_window=*/10, /*num_wordpieces=*/20,
      /*wordpiece_span_to_expand=*/{10, 12});
  EXPECT_EQ(maxWordpieceSpan, WordpieceSpan(6, 16));

  window_first_wordpiece_index = 10;
  window_last_wordpiece_index = 12;
  maxWordpieceSpan = internal::ExpandWindowAndAlign(
      /*max_num_wordpieces_in_window=*/10, /*num_wordpieces=*/20,
      /*wordpiece_span_to_expand=*/{10, 13});
  EXPECT_EQ(maxWordpieceSpan, WordpieceSpan(7, 17));
}

TEST(PodNerUtilsTest, WindowGenerator) {
  std::vector<int32_t> wordpiece_indices = {10, 20, 30, 40, 50, 60, 70, 80};
  std::vector<Token> tokens{{"a", 0, 1}, {"b", 2, 3}, {"c", 4, 5},
                            {"d", 6, 7}, {"e", 8, 9}, {"f", 10, 11}};
  std::vector<int32_t> token_starts{0, 2, 3, 5, 6, 7};
  WindowGenerator window_generator(wordpiece_indices, token_starts, tokens,
                                   /*max_num_wordpieces=*/4,
                                   /*sliding_window_overlap=*/1,
                                   /*span_of_interest=*/{0, 12});
  VectorSpan<int32_t> cur_wordpiece_indices;
  VectorSpan<int32_t> cur_token_starts;
  VectorSpan<Token> cur_tokens;
  ASSERT_TRUE(window_generator.Next(&cur_wordpiece_indices, &cur_token_starts,
                                    &cur_tokens));
  ASSERT_FALSE(window_generator.Done());
  ASSERT_EQ(cur_wordpiece_indices.size(), 3);
  for (int i = 0; i < 3; i++) {
    ASSERT_EQ(cur_wordpiece_indices[i], wordpiece_indices[i]);
  }
  ASSERT_EQ(cur_token_starts.size(), 2);
  ASSERT_EQ(cur_tokens.size(), 2);
  for (int i = 0; i < cur_tokens.size(); i++) {
    ASSERT_EQ(cur_token_starts[i], token_starts[i]);
    ASSERT_EQ(cur_tokens[i], tokens[i]);
  }

  ASSERT_TRUE(window_generator.Next(&cur_wordpiece_indices, &cur_token_starts,
                                    &cur_tokens));
  ASSERT_FALSE(window_generator.Done());
  ASSERT_EQ(cur_wordpiece_indices.size(), 4);
  for (int i = 0; i < cur_wordpiece_indices.size(); i++) {
    ASSERT_EQ(cur_wordpiece_indices[i], wordpiece_indices[i + 2]);
  }
  ASSERT_EQ(cur_token_starts.size(), 3);
  ASSERT_EQ(cur_tokens.size(), 3);
  for (int i = 0; i < cur_tokens.size(); i++) {
    ASSERT_EQ(cur_token_starts[i], token_starts[i + 1]);
    ASSERT_EQ(cur_tokens[i], tokens[i + 1]);
  }

  ASSERT_TRUE(window_generator.Next(&cur_wordpiece_indices, &cur_token_starts,
                                    &cur_tokens));
  ASSERT_TRUE(window_generator.Done());
  ASSERT_EQ(cur_wordpiece_indices.size(), 3);
  for (int i = 0; i < cur_wordpiece_indices.size(); i++) {
    ASSERT_EQ(cur_wordpiece_indices[i], wordpiece_indices[i + 5]);
  }
  ASSERT_EQ(cur_token_starts.size(), 3);
  ASSERT_EQ(cur_tokens.size(), 3);
  for (int i = 0; i < cur_tokens.size(); i++) {
    ASSERT_EQ(cur_token_starts[i], token_starts[i + 3]);
    ASSERT_EQ(cur_tokens[i], tokens[i + 3]);
  }

  ASSERT_FALSE(window_generator.Next(&cur_wordpiece_indices, &cur_token_starts,
                                     &cur_tokens));
}
}  // namespace
}  // namespace libtextclassifier3
