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

#include "annotator/number/number_test-include.h"

#include <set>
#include <string>
#include <vector>

#include "annotator/collections.h"
#include "annotator/model_generated.h"
#include "annotator/types-test-util.h"
#include "annotator/types.h"
#include "utils/tokenizer-utils.h"
#include "utils/utf8/unicodetext.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"

namespace libtextclassifier3 {
namespace test_internal {

using ::testing::AllOf;
using ::testing::ElementsAre;
using ::testing::Field;
using ::testing::IsEmpty;
using ::testing::Matcher;
using ::testing::UnorderedElementsAre;

namespace {
const flatbuffers::DetachedBuffer* CreateOptionsData(ModeFlag enabled_modes) {
  NumberAnnotatorOptionsT options;
  options.enabled = true;
  options.priority_score = -10.0;
  options.float_number_priority_score = 1.0;
  options.enabled_annotation_usecases =
      1 << AnnotationUsecase_ANNOTATION_USECASE_RAW;
  options.max_number_of_digits = 20;
  options.enabled_modes = enabled_modes;

  options.percentage_priority_score = 1.0;
  options.percentage_annotation_usecases =
      (1 << AnnotationUsecase_ANNOTATION_USECASE_RAW) +
      (1 << AnnotationUsecase_ANNOTATION_USECASE_SMART);
  std::set<std::string> percent_suffixes(
      {"パーセント", "percent", "pércént", "pc", "pct", "%", "٪", "﹪", "％"});
  for (const std::string& string_value : percent_suffixes) {
    options.percentage_pieces_string.append(string_value);
    options.percentage_pieces_string.push_back('\0');
  }

  flatbuffers::FlatBufferBuilder builder;
  builder.Finish(NumberAnnotatorOptions::Pack(builder, &options));
  return new flatbuffers::DetachedBuffer(builder.Release());
}
}  // namespace

const NumberAnnotatorOptions*
NumberAnnotatorTest::TestingNumberAnnotatorOptions(ModeFlag enabled_modes) {
  static const flatbuffers::DetachedBuffer* options_data_selection =
      CreateOptionsData(ModeFlag_SELECTION);
  static const flatbuffers::DetachedBuffer* options_data_no_selection =
      CreateOptionsData(ModeFlag_ANNOTATION_AND_CLASSIFICATION);
  static const flatbuffers::DetachedBuffer* options_data_all =
      CreateOptionsData(ModeFlag_ALL);

  if (enabled_modes == ModeFlag_SELECTION) {
    return flatbuffers::GetRoot<NumberAnnotatorOptions>(
        options_data_selection->data());
  } else if (enabled_modes == ModeFlag_ANNOTATION_AND_CLASSIFICATION) {
    return flatbuffers::GetRoot<NumberAnnotatorOptions>(
        options_data_no_selection->data());
  } else {
    return flatbuffers::GetRoot<NumberAnnotatorOptions>(
        options_data_all->data());
  }
}

MATCHER_P(IsCorrectCollection, collection, "collection is " + collection) {
  return arg.collection == collection;
}

MATCHER_P(IsCorrectNumericValue, numeric_value,
          "numeric value is " + std::to_string(numeric_value)) {
  return arg.numeric_value == numeric_value;
}

MATCHER_P(IsCorrectNumericDoubleValue, numeric_double_value,
          "numeric double value is " + std::to_string(numeric_double_value)) {
  return arg.numeric_double_value == numeric_double_value;
}

MATCHER_P(IsCorrectScore, score, "score is " + std::to_string(score)) {
  return arg.score == score;
}

MATCHER_P(IsCorrectPriortyScore, priority_score,
          "priority score is " + std::to_string(priority_score)) {
  return arg.priority_score == priority_score;
}

MATCHER_P(IsCorrectSpan, span,
          "span is (" + std::to_string(span.first) + "," +
              std::to_string(span.second) + ")") {
  return arg.span == span;
}

MATCHER_P(Classification, inner, "") {
  return testing::ExplainMatchResult(inner, arg.classification,
                                     result_listener);
}

static Matcher<AnnotatedSpan> IsAnnotatedSpan(
    const CodepointSpan& codepoint_span, const std::string& collection,
    const int int_value, const double double_value,
    const float priority_score = -10, const float score = 1) {
  return AllOf(
      IsCorrectSpan(codepoint_span),
      Classification(ElementsAre(AllOf(
          IsCorrectCollection(collection), IsCorrectNumericValue(int_value),
          IsCorrectNumericDoubleValue(double_value), IsCorrectScore(score),
          IsCorrectPriortyScore(priority_score)))));
}

TEST_F(NumberAnnotatorTest, ClassifiesAndParsesNumberCorrectly) {
  ClassificationResult classification_result;
  EXPECT_TRUE(number_annotator_.ClassifyText(
      UTF8ToUnicodeText("... 12345 ..."), {4, 9},
      AnnotationUsecase_ANNOTATION_USECASE_RAW, &classification_result));

  EXPECT_EQ(classification_result.collection, "number");
  EXPECT_EQ(classification_result.numeric_value, 12345);
  EXPECT_FLOAT_EQ(classification_result.numeric_double_value, 12345);
}

TEST_F(NumberAnnotatorForSelectionTest,
       ClassifyTextDisabledClassificationReturnsFalse) {
  ClassificationResult classification_result;
  EXPECT_FALSE(number_annotator_.ClassifyText(
      UTF8ToUnicodeText("... 12345 ..."), {4, 9},
      AnnotationUsecase_ANNOTATION_USECASE_RAW, &classification_result));
}

TEST_F(NumberAnnotatorTest, ClassifiesAndParsesNumberAsFloatCorrectly) {
  ClassificationResult classification_result;
  EXPECT_TRUE(number_annotator_.ClassifyText(
      UTF8ToUnicodeText("... 12345.12345 ..."), {4, 15},
      AnnotationUsecase_ANNOTATION_USECASE_RAW, &classification_result));

  EXPECT_EQ(classification_result.collection, "number");
  EXPECT_EQ(classification_result.numeric_value, 12345);
  EXPECT_FLOAT_EQ(classification_result.numeric_double_value, 12345.12345);
}

TEST_F(NumberAnnotatorTest,
       ClassifiesAndParsesNumberAsFloatCorrectlyWithoutDecimals) {
  ClassificationResult classification_result;
  // The dot after a number is considered punctuation, not part of a floating
  // number.
  EXPECT_TRUE(number_annotator_.ClassifyText(
      UTF8ToUnicodeText("... 12345. ..."), {4, 9},
      AnnotationUsecase_ANNOTATION_USECASE_RAW, &classification_result));
  EXPECT_FALSE(number_annotator_.ClassifyText(
      UTF8ToUnicodeText("... 12345. ..."), {4, 10},
      AnnotationUsecase_ANNOTATION_USECASE_RAW, &classification_result));

  EXPECT_EQ(classification_result.collection, "number");
  EXPECT_EQ(classification_result.numeric_value, 12345);
  EXPECT_FLOAT_EQ(classification_result.numeric_double_value, 12345);

  EXPECT_TRUE(number_annotator_.ClassifyText(
      UTF8ToUnicodeText("... 12345. ..."), {4, 9},
      AnnotationUsecase_ANNOTATION_USECASE_RAW, &classification_result));
  EXPECT_EQ(classification_result.collection, "number");
  EXPECT_EQ(classification_result.numeric_value, 12345);
  EXPECT_FLOAT_EQ(classification_result.numeric_double_value, 12345);
}

TEST_F(NumberAnnotatorTest, FindsAllIntegerAndFloatNumbersInText) {
  std::vector<AnnotatedSpan> result;
  // In the context "68.9#" -> 68.9 is a number because # is punctuation.
  // In the context "68.9#?" -> 68.9 is not a number because is followed by two
  // punctuation signs.
  EXPECT_TRUE(number_annotator_.FindAll(
      UTF8ToUnicodeText("how much is 2 plus 5 divided by 7% minus 3.14 "
                        "what about 68.9# or 68.9#?"),
      AnnotationUsecase_ANNOTATION_USECASE_RAW, ModeFlag_ANNOTATION, &result));

  EXPECT_THAT(result,
              UnorderedElementsAre(
                  IsAnnotatedSpan(CodepointSpan(12, 13), "number",
                                  /*int_value=*/2, /*double_value=*/2.0),
                  IsAnnotatedSpan(CodepointSpan(19, 20), "number",
                                  /*int_value=*/5, /*double_value=*/5.0),
                  IsAnnotatedSpan(CodepointSpan(32, 33), "number",
                                  /*int_value=*/7, /*double_value=*/7.0),
                  IsAnnotatedSpan(CodepointSpan(32, 34), "percentage",
                                  /*int_value=*/7, /*double_value=*/7.0,
                                  /*priority_score=*/1),
                  IsAnnotatedSpan(CodepointSpan(41, 45), "number",
                                  /*int_value=*/3, /*double_value=*/3.14,
                                  /*priority_score=*/1),
                  IsAnnotatedSpan(CodepointSpan(57, 61), "number",
                                  /*int_value=*/68, /*double_value=*/68.9,
                                  /*priority_score=*/1)));
}

TEST_F(NumberAnnotatorTest, ClassifiesNonNumberCorrectly) {
  ClassificationResult classification_result;
  EXPECT_FALSE(number_annotator_.ClassifyText(
      UTF8ToUnicodeText("... 123a45 ..."), {4, 10},
      AnnotationUsecase_ANNOTATION_USECASE_RAW, &classification_result));
  EXPECT_FALSE(number_annotator_.ClassifyText(
      UTF8ToUnicodeText("... 12345..12345 ..."), {4, 16},
      AnnotationUsecase_ANNOTATION_USECASE_RAW, &classification_result));
  EXPECT_FALSE(number_annotator_.ClassifyText(
      UTF8ToUnicodeText("... 12345a ..."), {4, 11},
      AnnotationUsecase_ANNOTATION_USECASE_RAW, &classification_result));
}

TEST_F(NumberAnnotatorTest, ClassifiesNumberSelectionCorrectly) {
  ClassificationResult classification_result;
  // Punctuation after a number is not part of the number.
  EXPECT_TRUE(number_annotator_.ClassifyText(
      UTF8ToUnicodeText("... 14, ..."), {4, 6},
      AnnotationUsecase_ANNOTATION_USECASE_RAW, &classification_result));
  EXPECT_EQ(classification_result.collection, "number");
  EXPECT_EQ(classification_result.numeric_value, 14);
  EXPECT_EQ(classification_result.numeric_double_value, 14);

  EXPECT_FALSE(number_annotator_.ClassifyText(
      UTF8ToUnicodeText("... 14, ..."), {4, 7},
      AnnotationUsecase_ANNOTATION_USECASE_RAW, &classification_result));
}

TEST_F(NumberAnnotatorTest, ClassifiesPercentageSignCorrectly) {
  ClassificationResult classification_result;
  EXPECT_TRUE(number_annotator_.ClassifyText(
      UTF8ToUnicodeText("... 99% ..."), {4, 7},
      AnnotationUsecase_ANNOTATION_USECASE_RAW, &classification_result));

  EXPECT_EQ(classification_result.collection, "percentage");
  EXPECT_EQ(classification_result.numeric_value, 99);
  EXPECT_EQ(classification_result.numeric_double_value, 99);
}

TEST_F(NumberAnnotatorTest, ClassifiesPercentageWordCorrectly) {
  ClassificationResult classification_result;
  EXPECT_TRUE(number_annotator_.ClassifyText(
      UTF8ToUnicodeText("... 15 percent ..."), {4, 14},
      AnnotationUsecase_ANNOTATION_USECASE_RAW, &classification_result));

  EXPECT_EQ(classification_result.collection, "percentage");
  EXPECT_EQ(classification_result.numeric_value, 15);
  EXPECT_EQ(classification_result.numeric_double_value, 15);
}

TEST_F(NumberAnnotatorTest, ClassifiesNonAsciiPercentageIncorrectSuffix) {
  ClassificationResult classification_result;
  EXPECT_FALSE(number_annotator_.ClassifyText(
      UTF8ToUnicodeText("15 café"), {0, 7},
      AnnotationUsecase_ANNOTATION_USECASE_RAW, &classification_result));
}

TEST_F(NumberAnnotatorTest, ClassifiesNonAsciiFrPercentageCorrectSuffix) {
  ClassificationResult classification_result;
  EXPECT_TRUE(number_annotator_.ClassifyText(
      UTF8ToUnicodeText("25 pércént"), {0, 10},
      AnnotationUsecase_ANNOTATION_USECASE_RAW, &classification_result));

  EXPECT_EQ(classification_result.collection, "percentage");
  EXPECT_EQ(classification_result.numeric_value, 25);
  EXPECT_EQ(classification_result.numeric_double_value, 25);
}

TEST_F(NumberAnnotatorTest, ClassifiesNonAsciiJaPercentageCorrectSuffix) {
  ClassificationResult classification_result;
  EXPECT_TRUE(number_annotator_.ClassifyText(
      UTF8ToUnicodeText("10パーセント"), {0, 7},
      AnnotationUsecase_ANNOTATION_USECASE_RAW, &classification_result));
  EXPECT_EQ(classification_result.collection, "percentage");
  EXPECT_EQ(classification_result.numeric_value, 10);
  EXPECT_EQ(classification_result.numeric_double_value, 10);

  std::vector<AnnotatedSpan> result;
  EXPECT_TRUE(number_annotator_.FindAll(
      UTF8ToUnicodeText("明日の降水確率は10パーセント  音量を12にセット"),
      AnnotationUsecase_ANNOTATION_USECASE_RAW, ModeFlag_CLASSIFICATION,
      &result));
  EXPECT_THAT(result,
              UnorderedElementsAre(
                  IsAnnotatedSpan(CodepointSpan(8, 10), "number",
                                  /*int_value=*/10, /*double_value=*/10.0),
                  IsAnnotatedSpan(CodepointSpan(8, 15), "percentage",
                                  /*int_value=*/10, /*double_value=*/10.0,
                                  /*priority_score=*/1),
                  IsAnnotatedSpan(CodepointSpan(20, 22), "number",
                                  /*int_value=*/12, /*double_value=*/12.0)));
}

TEST_F(NumberAnnotatorTest, FindsAllNumbersInText) {
  std::vector<AnnotatedSpan> result;
  EXPECT_TRUE(number_annotator_.FindAll(
      UTF8ToUnicodeText("... 12345 ... 9 is my number and 27% or 68# #38 ＃39 "
                        "but not $99."),
      AnnotationUsecase_ANNOTATION_USECASE_RAW, ModeFlag_ANNOTATION, &result));

  EXPECT_THAT(
      result,
      UnorderedElementsAre(
          IsAnnotatedSpan(CodepointSpan(4, 9), "number",
                          /*int_value=*/12345, /*double_value=*/12345.0),
          IsAnnotatedSpan(CodepointSpan(14, 15), "number",
                          /*int_value=*/9, /*double_value=*/9.0),
          IsAnnotatedSpan(CodepointSpan(33, 35), "number",
                          /*int_value=*/27, /*double_value=*/27.0),
          IsAnnotatedSpan(CodepointSpan(33, 36), "percentage",
                          /*int_value=*/27, /*double_value=*/27.0,
                          /*priority_score=*/1),
          IsAnnotatedSpan(CodepointSpan(40, 42), "number",
                          /*int_value=*/68, /*double_value=*/68.0),
          IsAnnotatedSpan(CodepointSpan(45, 47), "number",
                          /*int_value=*/38, /*double_value=*/38.0),
          IsAnnotatedSpan(CodepointSpan(49, 51), "number",
                          /*int_value=*/39, /*double_value=*/39.0)));
}

TEST_F(NumberAnnotatorForAnnotationAndClassificationTest,
       FindsAllDisabledModeReturnsNoResults) {
  std::vector<AnnotatedSpan> result;
  EXPECT_TRUE(number_annotator_.FindAll(
      UTF8ToUnicodeText("... 12345 ... 9 is my number and 27% or 68# #38 ＃39 "
                        "but not $99."),
      AnnotationUsecase_ANNOTATION_USECASE_RAW, ModeFlag_SELECTION, &result));

  EXPECT_THAT(result, IsEmpty());
}

TEST_F(NumberAnnotatorTest, FindsNoNumberInText) {
  std::vector<AnnotatedSpan> result;
  EXPECT_TRUE(number_annotator_.FindAll(
      UTF8ToUnicodeText("... 12345a ... 12345..12345 and 123a45 are not valid. "
                        "And -#5% is also bad."),
      AnnotationUsecase_ANNOTATION_USECASE_RAW, ModeFlag_SELECTION, &result));
  ASSERT_EQ(result.size(), 0);
}

TEST_F(NumberAnnotatorTest, FindsNumberWithPunctuation) {
  std::vector<AnnotatedSpan> result;
  // A number should be followed by only one punctuation signs => 15 is not a
  // number.
  EXPECT_TRUE(number_annotator_.FindAll(
      UTF8ToUnicodeText(
          "It's 12, 13, 14! Or 15??? For sure 16: 17; 18. and －19"),
      AnnotationUsecase_ANNOTATION_USECASE_RAW, ModeFlag_CLASSIFICATION,
      &result));

  EXPECT_THAT(result,
              UnorderedElementsAre(
                  IsAnnotatedSpan(CodepointSpan(5, 7), "number",
                                  /*int_value=*/12, /*double_value=*/12.0),
                  IsAnnotatedSpan(CodepointSpan(9, 11), "number",
                                  /*int_value=*/13, /*double_value=*/13.0),
                  IsAnnotatedSpan(CodepointSpan(13, 15), "number",
                                  /*int_value=*/14, /*double_value=*/14.0),
                  IsAnnotatedSpan(CodepointSpan(35, 37), "number",
                                  /*int_value=*/16, /*double_value=*/16.0),
                  IsAnnotatedSpan(CodepointSpan(39, 41), "number",
                                  /*int_value=*/17, /*double_value=*/17.0),
                  IsAnnotatedSpan(CodepointSpan(43, 45), "number",
                                  /*int_value=*/18, /*double_value=*/18.0),
                  IsAnnotatedSpan(CodepointSpan(51, 54), "number",
                                  /*int_value=*/-19, /*double_value=*/-19.0)));
}

TEST_F(NumberAnnotatorTest, FindsFloatNumberWithPunctuation) {
  std::vector<AnnotatedSpan> result;
  EXPECT_TRUE(number_annotator_.FindAll(
      UTF8ToUnicodeText("It's 12.123, 13.45, 14.54321! Or 15.1? Maybe 16.33: "
                        "17.21; but for sure 18.90."),
      AnnotationUsecase_ANNOTATION_USECASE_RAW, ModeFlag_ANNOTATION, &result));

  EXPECT_THAT(result,
              UnorderedElementsAre(
                  IsAnnotatedSpan(CodepointSpan(5, 11), "number",
                                  /*int_value=*/12, /*double_value=*/12.123,
                                  /*priority_score=*/1),
                  IsAnnotatedSpan(CodepointSpan(13, 18), "number",
                                  /*int_value=*/13, /*double_value=*/13.45,
                                  /*priority_score=*/1),
                  IsAnnotatedSpan(CodepointSpan(20, 28), "number",
                                  /*int_value=*/14, /*double_value=*/14.54321,
                                  /*priority_score=*/1),
                  IsAnnotatedSpan(CodepointSpan(33, 37), "number",
                                  /*int_value=*/15, /*double_value=*/15.1,
                                  /*priority_score=*/1),
                  IsAnnotatedSpan(CodepointSpan(45, 50), "number",
                                  /*int_value=*/16, /*double_value=*/16.33,
                                  /*priority_score=*/1),
                  IsAnnotatedSpan(CodepointSpan(52, 57), "number",
                                  /*int_value=*/17, /*double_value=*/17.21,
                                  /*priority_score=*/1),
                  IsAnnotatedSpan(CodepointSpan(72, 77), "number",
                                  /*int_value=*/18, /*double_value=*/18.9,
                                  /*priority_score=*/1)));
}

TEST_F(NumberAnnotatorTest, HandlesNumbersAtBeginning) {
  std::vector<AnnotatedSpan> result;
  EXPECT_TRUE(number_annotator_.FindAll(
      UTF8ToUnicodeText("-5"), AnnotationUsecase_ANNOTATION_USECASE_RAW,
      ModeFlag_SELECTION, &result));

  EXPECT_THAT(result, UnorderedElementsAre(IsAnnotatedSpan(
                          CodepointSpan(0, 2), "number",
                          /*int_value=*/-5, /*double_value=*/-5)));
}

TEST_F(NumberAnnotatorTest, HandlesNegativeNumbers) {
  std::vector<AnnotatedSpan> result;
  EXPECT_TRUE(number_annotator_.FindAll(
      UTF8ToUnicodeText("Number -5 and -5% and not number --5%"),
      AnnotationUsecase_ANNOTATION_USECASE_RAW, ModeFlag_ANNOTATION, &result));

  EXPECT_THAT(result,
              UnorderedElementsAre(
                  IsAnnotatedSpan(CodepointSpan(7, 9), "number",
                                  /*int_value=*/-5, /*double_value=*/-5),
                  IsAnnotatedSpan(CodepointSpan(14, 16), "number",
                                  /*int_value=*/-5, /*double_value=*/-5),
                  IsAnnotatedSpan(CodepointSpan(14, 17), "percentage",
                                  /*int_value=*/-5, /*double_value=*/-5,
                                  /*priority_score=*/1)));
}

TEST_F(NumberAnnotatorTest, FindGoodPercentageContexts) {
  std::vector<AnnotatedSpan> result;
  EXPECT_TRUE(number_annotator_.FindAll(
      UTF8ToUnicodeText(
          "5 percent, 10 pct, 25 pc and 17%, -5 percent, 10％ are percentages"),
      AnnotationUsecase_ANNOTATION_USECASE_RAW, ModeFlag_SELECTION, &result));

  EXPECT_THAT(result,
              UnorderedElementsAre(
                  IsAnnotatedSpan(CodepointSpan(0, 1), "number",
                                  /*int_value=*/5, /*double_value=*/5),
                  IsAnnotatedSpan(CodepointSpan(0, 9), "percentage",
                                  /*int_value=*/5, /*double_value=*/5,
                                  /*priority_score=*/1),
                  IsAnnotatedSpan(CodepointSpan(11, 13), "number",
                                  /*int_value=*/10, /*double_value=*/10),
                  IsAnnotatedSpan(CodepointSpan(11, 17), "percentage",
                                  /*int_value=*/10, /*double_value=*/10,
                                  /*priority_score=*/1),
                  IsAnnotatedSpan(CodepointSpan(19, 21), "number",
                                  /*int_value=*/25, /*double_value=*/25),
                  IsAnnotatedSpan(CodepointSpan(19, 24), "percentage",
                                  /*int_value=*/25, /*double_value=*/25,
                                  /*priority_score=*/1),
                  IsAnnotatedSpan(CodepointSpan(29, 31), "number",
                                  /*int_value=*/17, /*double_value=*/17),
                  IsAnnotatedSpan(CodepointSpan(29, 32), "percentage",
                                  /*int_value=*/17, /*double_value=*/17,
                                  /*priority_score=*/1),
                  IsAnnotatedSpan(CodepointSpan(34, 36), "number",
                                  /*int_value=*/-5, /*double_value=*/-5),
                  IsAnnotatedSpan(CodepointSpan(34, 44), "percentage",
                                  /*int_value=*/-5, /*double_value=*/-5,
                                  /*priority_score=*/1),
                  IsAnnotatedSpan(CodepointSpan(46, 48), "number",
                                  /*int_value=*/10, /*double_value=*/10),
                  IsAnnotatedSpan(CodepointSpan(46, 49), "percentage",
                                  /*int_value=*/10, /*double_value=*/10,
                                  /*priority_score=*/1)));
}

TEST_F(NumberAnnotatorTest, FindSinglePercentageInContext) {
  std::vector<AnnotatedSpan> result;
  EXPECT_TRUE(number_annotator_.FindAll(
      UTF8ToUnicodeText("5%"), AnnotationUsecase_ANNOTATION_USECASE_RAW,
      ModeFlag_ANNOTATION, &result));

  EXPECT_THAT(result, UnorderedElementsAre(
                          IsAnnotatedSpan(CodepointSpan(0, 1), "number",
                                          /*int_value=*/5, /*double_value=*/5),
                          IsAnnotatedSpan(CodepointSpan(0, 2), "percentage",
                                          /*int_value=*/5, /*double_value=*/5,
                                          /*priority_score=*/1)));
}

TEST_F(NumberAnnotatorTest, IgnoreBadPercentageContexts) {
  std::vector<AnnotatedSpan> result;
  // A valid number is followed by only one punctuation element.
  EXPECT_TRUE(number_annotator_.FindAll(
      UTF8ToUnicodeText("10, pct, 25 prc, 5#: percentage are not percentages"),
      AnnotationUsecase_ANNOTATION_USECASE_RAW, ModeFlag_ANNOTATION, &result));

  EXPECT_THAT(result,
              UnorderedElementsAre(
                  IsAnnotatedSpan(CodepointSpan(0, 2), "number",
                                  /*int_value=*/10, /*double_value=*/10),
                  IsAnnotatedSpan(CodepointSpan(9, 11), "number",
                                  /*int_value=*/25, /*double_value=*/25)));
}

TEST_F(NumberAnnotatorTest, IgnoreBadPercentagePunctuationContexts) {
  std::vector<AnnotatedSpan> result;
  EXPECT_TRUE(number_annotator_.FindAll(
      UTF8ToUnicodeText(
          "#!24% or :?33 percent are not valid percentages, nor numbers."),
      AnnotationUsecase_ANNOTATION_USECASE_RAW, ModeFlag_ANNOTATION, &result));

  EXPECT_TRUE(result.empty());
}

TEST_F(NumberAnnotatorTest, FindPercentageInNonAsciiContext) {
  std::vector<AnnotatedSpan> result;
  EXPECT_TRUE(number_annotator_.FindAll(
      UTF8ToUnicodeText(
          "At the café 10% or 25 percent of people are nice. Only 10%!"),
      AnnotationUsecase_ANNOTATION_USECASE_RAW, ModeFlag_ANNOTATION, &result));

  EXPECT_THAT(result,
              UnorderedElementsAre(
                  IsAnnotatedSpan(CodepointSpan(12, 14), "number",
                                  /*int_value=*/10, /*double_value=*/10),
                  IsAnnotatedSpan(CodepointSpan(12, 15), "percentage",
                                  /*int_value=*/10, /*double_value=*/10,
                                  /*priority_score=*/1),
                  IsAnnotatedSpan(CodepointSpan(19, 21), "number",
                                  /*int_value=*/25, /*double_value=*/25),
                  IsAnnotatedSpan(CodepointSpan(19, 29), "percentage",
                                  /*int_value=*/25, /*double_value=*/25,
                                  /*priority_score=*/1),
                  IsAnnotatedSpan(CodepointSpan(55, 57), "number",
                                  /*int_value=*/10, /*double_value=*/10),
                  IsAnnotatedSpan(CodepointSpan(55, 58), "percentage",
                                  /*int_value=*/10, /*double_value=*/10,
                                  /*priority_score=*/1)));
}

TEST_F(NumberAnnotatorTest,
       WhenPercentSuffixWithAdditionalIgnoredCharactersDoesNotParseIt) {
  ClassificationResult classification_result;
  EXPECT_FALSE(number_annotator_.ClassifyText(
      UTF8ToUnicodeText("23#!? percent"), {0, 13},
      AnnotationUsecase_ANNOTATION_USECASE_RAW, &classification_result));
}

TEST_F(NumberAnnotatorTest,
       WhenPercentSuffixWithAdditionalRandomTokensDoesNotParseIt) {
  ClassificationResult classification_result;
  EXPECT_FALSE(number_annotator_.ClassifyText(
      UTF8ToUnicodeText("23 asdf 3.14 pct asdf"), {0, 21},
      AnnotationUsecase_ANNOTATION_USECASE_RAW, &classification_result));
}

TEST_F(NumberAnnotatorTest,
       WhenPercentSuffixWithAdditionalRandomPrefixSuffixDoesNotParseIt) {
  ClassificationResult classification_result;
  EXPECT_FALSE(number_annotator_.ClassifyText(
      UTF8ToUnicodeText("abdf23 percentabdf"), {0, 18},
      AnnotationUsecase_ANNOTATION_USECASE_RAW, &classification_result));
}

TEST_F(NumberAnnotatorTest,
       WhenPercentSuffixWithAdditionalRandomStringsDoesNotParsesIt) {
  ClassificationResult classification_result;
  EXPECT_FALSE(number_annotator_.ClassifyText(
      UTF8ToUnicodeText("#?!23 percent#!?"), {0, 16},
      AnnotationUsecase_ANNOTATION_USECASE_RAW, &classification_result));
}

TEST_F(NumberAnnotatorTest, WhenBothPercentSymbolAndSuffixDoesNotParseIt) {
  ClassificationResult classification_result;
  EXPECT_FALSE(number_annotator_.ClassifyText(
      UTF8ToUnicodeText("23% percent"), {0, 11},
      AnnotationUsecase_ANNOTATION_USECASE_RAW, &classification_result));
}

TEST_F(NumberAnnotatorTest,
       WhenPercentSymbolWithAdditionalPrefixCharactersDoesNotParsesIt) {
  ClassificationResult classification_result;
  EXPECT_FALSE(number_annotator_.ClassifyText(
      UTF8ToUnicodeText("#?23%"), {0, 5},
      AnnotationUsecase_ANNOTATION_USECASE_RAW, &classification_result));
}

TEST_F(NumberAnnotatorTest, WhenNumberWithAdditionalCharactersDoesNotParsesIt) {
  ClassificationResult classification_result;
  EXPECT_FALSE(number_annotator_.ClassifyText(
      UTF8ToUnicodeText("23#!?"), {0, 5},
      AnnotationUsecase_ANNOTATION_USECASE_RAW, &classification_result));
}

TEST_F(NumberAnnotatorTest,
       WhenPercentSymbolWithAdditionalCharactersDoesNotParsesIt) {
  ClassificationResult classification_result;
  // ! does not belong to the percentage annotation
  EXPECT_TRUE(number_annotator_.ClassifyText(
      UTF8ToUnicodeText("23%!"), {0, 3},
      AnnotationUsecase_ANNOTATION_USECASE_RAW, &classification_result));
  EXPECT_EQ(classification_result.collection, "percentage");
  EXPECT_EQ(classification_result.numeric_value, 23);
  EXPECT_EQ(classification_result.numeric_double_value, 23);

  EXPECT_FALSE(number_annotator_.ClassifyText(
      UTF8ToUnicodeText("23%!"), {0, 4},
      AnnotationUsecase_ANNOTATION_USECASE_RAW, &classification_result));
}

TEST_F(NumberAnnotatorTest,
       WhenAdditionalCharactersWithMisplacedPercentSymbolDoesNotParsesIt) {
  ClassificationResult classification_result;
  EXPECT_FALSE(number_annotator_.ClassifyText(
      UTF8ToUnicodeText("23.:;%"), {0, 6},
      AnnotationUsecase_ANNOTATION_USECASE_RAW, &classification_result));
}

TEST_F(NumberAnnotatorTest, WhenMultipleMinusSignsDoesNotParsesIt) {
  ClassificationResult classification_result;
  EXPECT_TRUE(number_annotator_.ClassifyText(
      UTF8ToUnicodeText("--11"), {1, 4},
      AnnotationUsecase_ANNOTATION_USECASE_RAW, &classification_result));
  EXPECT_THAT(classification_result,
              AllOf(Field(&ClassificationResult::collection, "number"),
                    Field(&ClassificationResult::numeric_value, -11),
                    Field(&ClassificationResult::numeric_double_value, -11)));

  EXPECT_FALSE(number_annotator_.ClassifyText(
      UTF8ToUnicodeText("--11"), {0, 4},
      AnnotationUsecase_ANNOTATION_USECASE_RAW, &classification_result));
}

TEST_F(NumberAnnotatorTest, WhenMultipleMinusSignsPercentSignDoesNotParsesIt) {
  ClassificationResult classification_result;
  EXPECT_TRUE(number_annotator_.ClassifyText(
      UTF8ToUnicodeText("--11%"), {1, 5},
      AnnotationUsecase_ANNOTATION_USECASE_RAW, &classification_result));
  EXPECT_THAT(classification_result,
              AllOf(Field(&ClassificationResult::collection, "percentage"),
                    Field(&ClassificationResult::numeric_value, -11),
                    Field(&ClassificationResult::numeric_double_value, -11)));

  EXPECT_FALSE(number_annotator_.ClassifyText(
      UTF8ToUnicodeText("--11%"), {0, 5},
      AnnotationUsecase_ANNOTATION_USECASE_RAW, &classification_result));
}

TEST_F(NumberAnnotatorTest, WhenPlusMinusSignsDoesNotParsesIt) {
  ClassificationResult classification_result;
  EXPECT_TRUE(number_annotator_.ClassifyText(
      UTF8ToUnicodeText("+-11"), {1, 4},
      AnnotationUsecase_ANNOTATION_USECASE_RAW, &classification_result));
  EXPECT_THAT(classification_result,
              AllOf(Field(&ClassificationResult::collection, "number"),
                    Field(&ClassificationResult::numeric_value, -11),
                    Field(&ClassificationResult::numeric_double_value, -11)));

  EXPECT_FALSE(number_annotator_.ClassifyText(
      UTF8ToUnicodeText("+-11"), {0, 4},
      AnnotationUsecase_ANNOTATION_USECASE_RAW, &classification_result));
}

TEST_F(NumberAnnotatorTest, WhenMinusPlusSignsDoesNotParsesIt) {
  ClassificationResult classification_result;
  // + right before a number is not included in the number annotation
  EXPECT_FALSE(number_annotator_.ClassifyText(
      UTF8ToUnicodeText("-+11"), {1, 4},
      AnnotationUsecase_ANNOTATION_USECASE_RAW, &classification_result));
  EXPECT_FALSE(number_annotator_.ClassifyText(
      UTF8ToUnicodeText("-+11"), {0, 4},
      AnnotationUsecase_ANNOTATION_USECASE_RAW, &classification_result));
}

TEST_F(NumberAnnotatorTest, WhenMinusSignSuffixDoesNotParsesIt) {
  ClassificationResult classification_result;
  EXPECT_FALSE(number_annotator_.ClassifyText(
      UTF8ToUnicodeText("10-"), {0, 3},
      AnnotationUsecase_ANNOTATION_USECASE_RAW, &classification_result));
}

TEST_F(NumberAnnotatorTest, WhenMultipleCharSuffixDoesNotParsesIt) {
  ClassificationResult classification_result;
  EXPECT_TRUE(number_annotator_.ClassifyText(
      UTF8ToUnicodeText("10**"), {0, 2},
      AnnotationUsecase_ANNOTATION_USECASE_RAW, &classification_result));
  EXPECT_THAT(classification_result,
              AllOf(Field(&ClassificationResult::collection, "number"),
                    Field(&ClassificationResult::numeric_value, 10),
                    Field(&ClassificationResult::numeric_double_value, 10)));

  EXPECT_FALSE(number_annotator_.ClassifyText(
      UTF8ToUnicodeText("10**"), {0, 3},
      AnnotationUsecase_ANNOTATION_USECASE_RAW, &classification_result));
  EXPECT_FALSE(number_annotator_.ClassifyText(
      UTF8ToUnicodeText("10**"), {0, 4},
      AnnotationUsecase_ANNOTATION_USECASE_RAW, &classification_result));
}

TEST_F(NumberAnnotatorTest, WhenMultipleCharPrefixDoesNotParsesIt) {
  ClassificationResult classification_result;
  EXPECT_FALSE(number_annotator_.ClassifyText(
      UTF8ToUnicodeText("**10"), {1, 4},
      AnnotationUsecase_ANNOTATION_USECASE_RAW, &classification_result));
  EXPECT_FALSE(number_annotator_.ClassifyText(
      UTF8ToUnicodeText("**10"), {0, 4},
      AnnotationUsecase_ANNOTATION_USECASE_RAW, &classification_result));
}

TEST_F(NumberAnnotatorTest, WhenLowestSupportedNumberParsesIt) {
  ClassificationResult classification_result;
  EXPECT_TRUE(number_annotator_.ClassifyText(
      UTF8ToUnicodeText("-1000000000"), {0, 11},
      AnnotationUsecase_ANNOTATION_USECASE_RAW, &classification_result));

  EXPECT_THAT(
      classification_result,
      AllOf(Field(&ClassificationResult::collection, "number"),
            Field(&ClassificationResult::numeric_value, -1000000000),
            Field(&ClassificationResult::numeric_double_value, -1000000000)));
}

TEST_F(NumberAnnotatorTest, WhenLargestSupportedNumberParsesIt) {
  ClassificationResult classification_result;
  EXPECT_TRUE(number_annotator_.ClassifyText(
      UTF8ToUnicodeText("1000000000"), {0, 10},
      AnnotationUsecase_ANNOTATION_USECASE_RAW, &classification_result));

  EXPECT_THAT(
      classification_result,
      AllOf(Field(&ClassificationResult::collection, "number"),
            Field(&ClassificationResult::numeric_value, 1000000000),
            Field(&ClassificationResult::numeric_double_value, 1000000000)));
}

TEST_F(NumberAnnotatorTest, WhenLowestSupportedFloatNumberParsesIt) {
  ClassificationResult classification_result;
  EXPECT_TRUE(number_annotator_.ClassifyText(
      UTF8ToUnicodeText("-999999999.999999999"), {0, 20},
      AnnotationUsecase_ANNOTATION_USECASE_RAW, &classification_result));

  EXPECT_THAT(classification_result,
              AllOf(Field(&ClassificationResult::collection, "number"),
                    Field(&ClassificationResult::numeric_value, -1000000000),
                    Field(&ClassificationResult::numeric_double_value,
                          -999999999.999999999)));
}

TEST_F(NumberAnnotatorTest, WhenLargestFloatSupportedNumberParsesIt) {
  ClassificationResult classification_result;
  EXPECT_TRUE(number_annotator_.ClassifyText(
      UTF8ToUnicodeText("999999999.999999999"), {0, 19},
      AnnotationUsecase_ANNOTATION_USECASE_RAW, &classification_result));

  EXPECT_THAT(classification_result,
              AllOf(Field(&ClassificationResult::collection, "number"),
                    Field(&ClassificationResult::numeric_value, 1000000000),
                    Field(&ClassificationResult::numeric_double_value,
                          999999999.999999999)));
}

TEST_F(NumberAnnotatorTest, WhenLargeNumberDoesNotParseIt) {
  ClassificationResult classification_result;
  EXPECT_FALSE(number_annotator_.ClassifyText(
      UTF8ToUnicodeText("1234567890123456789012345678901234567890"), {0, 40},
      AnnotationUsecase_ANNOTATION_USECASE_RAW, &classification_result));
}

TEST_F(NumberAnnotatorTest, WhenMinusInTheMiddleDoesNotParseIt) {
  ClassificationResult classification_result;
  EXPECT_FALSE(number_annotator_.ClassifyText(
      UTF8ToUnicodeText("2016-2017"), {0, 9},
      AnnotationUsecase_ANNOTATION_USECASE_RAW, &classification_result));
}

TEST_F(NumberAnnotatorTest, WhenSuffixWithoutNumberDoesNotParseIt) {
  std::vector<AnnotatedSpan> result;
  EXPECT_TRUE(number_annotator_.FindAll(
      UTF8ToUnicodeText("... % ..."), AnnotationUsecase_ANNOTATION_USECASE_RAW,
      ModeFlag_ANNOTATION, &result));

  ASSERT_EQ(result.size(), 0);
}

TEST_F(NumberAnnotatorTest, WhenPrefixWithoutNumberDoesNotParseIt) {
  std::vector<AnnotatedSpan> result;
  EXPECT_TRUE(number_annotator_.FindAll(
      UTF8ToUnicodeText("... $ ..."), AnnotationUsecase_ANNOTATION_USECASE_RAW,
      ModeFlag_ANNOTATION, &result));

  ASSERT_EQ(result.size(), 0);
}

TEST_F(NumberAnnotatorTest, WhenPrefixAndSuffixWithoutNumberDoesNotParseIt) {
  std::vector<AnnotatedSpan> result;
  EXPECT_TRUE(number_annotator_.FindAll(
      UTF8ToUnicodeText("... $% ..."), AnnotationUsecase_ANNOTATION_USECASE_RAW,
      ModeFlag_ANNOTATION, &result));

  ASSERT_EQ(result.size(), 0);
}

TEST_F(NumberAnnotatorTest, ForNumberAnnotationsSetsScoreAndPriorityScore) {
  ClassificationResult classification_result;
  EXPECT_TRUE(number_annotator_.ClassifyText(
      UTF8ToUnicodeText("... 12345 ..."), {4, 9},
      AnnotationUsecase_ANNOTATION_USECASE_RAW, &classification_result));

  EXPECT_EQ(classification_result.collection, "number");
  EXPECT_EQ(classification_result.numeric_value, 12345);
  EXPECT_EQ(classification_result.numeric_double_value, 12345);
  EXPECT_EQ(classification_result.score, 1);
  EXPECT_EQ(classification_result.priority_score, -10);

  std::vector<AnnotatedSpan> result;
  EXPECT_TRUE(number_annotator_.FindAll(
      UTF8ToUnicodeText("Come at 9 or 10 ok?"),
      AnnotationUsecase_ANNOTATION_USECASE_RAW, ModeFlag_ANNOTATION, &result));

  EXPECT_THAT(result,
              UnorderedElementsAre(
                  IsAnnotatedSpan(CodepointSpan(8, 9), "number",
                                  /*int_value=*/9, /*double_value=*/9),
                  IsAnnotatedSpan(CodepointSpan(13, 15), "number",
                                  /*int_value=*/10, /*double_value=*/10)));
}

TEST_F(NumberAnnotatorTest,
       ForFloatNumberAnnotationsSetsScoreAndPriorityScore) {
  ClassificationResult classification_result;
  EXPECT_TRUE(number_annotator_.ClassifyText(
      UTF8ToUnicodeText("... 12345.12345 ..."), {4, 15},
      AnnotationUsecase_ANNOTATION_USECASE_RAW, &classification_result));
  EXPECT_EQ(classification_result.collection, "number");
  EXPECT_EQ(classification_result.numeric_value, 12345);
  EXPECT_EQ(classification_result.numeric_double_value, 12345.12345);
  EXPECT_EQ(classification_result.score, 1);
  EXPECT_EQ(classification_result.priority_score, 1);

  std::vector<AnnotatedSpan> result;
  EXPECT_TRUE(number_annotator_.FindAll(
      UTF8ToUnicodeText("Results are between 12.5 and 13.5, right?"),
      AnnotationUsecase_ANNOTATION_USECASE_RAW, ModeFlag_ANNOTATION, &result));
  EXPECT_THAT(result,
              UnorderedElementsAre(
                  IsAnnotatedSpan(CodepointSpan(20, 24), "number",
                                  /*int_value=*/12, /*double_value=*/12.5,
                                  /*priority_score=*/1),
                  IsAnnotatedSpan(CodepointSpan(29, 33), "number",
                                  /*int_value=*/13, /*double_value=*/13.5,
                                  /*priority_score=*/1)));
}

TEST_F(NumberAnnotatorTest, ForPercentageAnnotationsSetsScoreAndPriorityScore) {
  ClassificationResult classification_result;
  EXPECT_TRUE(number_annotator_.ClassifyText(
      UTF8ToUnicodeText("... 12345% ..."), {4, 10},
      AnnotationUsecase_ANNOTATION_USECASE_RAW, &classification_result));
  EXPECT_EQ(classification_result.collection, "percentage");
  EXPECT_EQ(classification_result.numeric_value, 12345);
  EXPECT_EQ(classification_result.numeric_double_value, 12345);
  EXPECT_EQ(classification_result.score, 1);
  EXPECT_EQ(classification_result.priority_score, 1);

  EXPECT_TRUE(number_annotator_.ClassifyText(
      UTF8ToUnicodeText("... 12345 percent ..."), {4, 17},
      AnnotationUsecase_ANNOTATION_USECASE_RAW, &classification_result));
  EXPECT_EQ(classification_result.collection, "percentage");
  EXPECT_EQ(classification_result.numeric_value, 12345);
  EXPECT_EQ(classification_result.numeric_double_value, 12345);
  EXPECT_EQ(classification_result.score, 1);
  EXPECT_EQ(classification_result.priority_score, 1);

  std::vector<AnnotatedSpan> result;
  EXPECT_TRUE(number_annotator_.FindAll(
      UTF8ToUnicodeText("Results are between 9% and 10 percent."),
      AnnotationUsecase_ANNOTATION_USECASE_RAW, ModeFlag_ANNOTATION, &result));
  EXPECT_THAT(result,
              UnorderedElementsAre(
                  IsAnnotatedSpan(CodepointSpan(20, 21), "number",
                                  /*int_value=*/9, /*double_value=*/9),
                  IsAnnotatedSpan(CodepointSpan(20, 22), "percentage",
                                  /*int_value=*/9, /*double_value=*/9,
                                  /*priority_score=*/1),
                  IsAnnotatedSpan(CodepointSpan(27, 29), "number",
                                  /*int_value=*/10, /*double_value=*/10),
                  IsAnnotatedSpan(CodepointSpan(27, 37), "percentage",
                                  /*int_value=*/10, /*double_value=*/10,
                                  /*priority_score=*/1)));
}

TEST_F(NumberAnnotatorTest, NumberDisabledPercentageEnabledForSmartUsecase) {
  ClassificationResult classification_result;
  EXPECT_FALSE(number_annotator_.ClassifyText(
      UTF8ToUnicodeText("... 12345 ..."), {4, 9},
      AnnotationUsecase_ANNOTATION_USECASE_SMART, &classification_result));

  EXPECT_TRUE(number_annotator_.ClassifyText(
      UTF8ToUnicodeText("... 12345% ..."), {4, 10},
      AnnotationUsecase_ANNOTATION_USECASE_SMART, &classification_result));
  EXPECT_EQ(classification_result.collection, "percentage");
  EXPECT_EQ(classification_result.numeric_value, 12345);
  EXPECT_EQ(classification_result.numeric_double_value, 12345.0);
  EXPECT_EQ(classification_result.score, 1);
  EXPECT_EQ(classification_result.priority_score, 1);

  EXPECT_TRUE(number_annotator_.ClassifyText(
      UTF8ToUnicodeText("... 12345percent ..."), {4, 16},
      AnnotationUsecase_ANNOTATION_USECASE_SMART, &classification_result));
  EXPECT_EQ(classification_result.collection, "percentage");
  EXPECT_EQ(classification_result.numeric_value, 12345);
  EXPECT_EQ(classification_result.numeric_double_value, 12345);
  EXPECT_EQ(classification_result.score, 1);
  EXPECT_EQ(classification_result.priority_score, 1);

  std::vector<AnnotatedSpan> result;
  EXPECT_TRUE(number_annotator_.FindAll(
      UTF8ToUnicodeText("Accuracy for experiment 3 is 9%."),
      AnnotationUsecase_ANNOTATION_USECASE_SMART, ModeFlag_ANNOTATION,
      &result));
  EXPECT_THAT(result, UnorderedElementsAre(
                          IsAnnotatedSpan(CodepointSpan(29, 31), "percentage",
                                          /*int_value=*/9, /*double_value=*/9.0,
                                          /*priority_score=*/1)));
}

TEST_F(NumberAnnotatorTest, MathOperatorsNotAnnotatedAsNumbersFindAll) {
  std::vector<AnnotatedSpan> result;
  EXPECT_TRUE(number_annotator_.FindAll(
      UTF8ToUnicodeText("how much is 2 + 2 or 5 - 96 * 89"),
      AnnotationUsecase_ANNOTATION_USECASE_RAW, ModeFlag_ANNOTATION, &result));

  EXPECT_THAT(result,
              UnorderedElementsAre(
                  IsAnnotatedSpan(CodepointSpan(12, 13), "number",
                                  /*int_value=*/2, /*double_value=*/2),
                  IsAnnotatedSpan(CodepointSpan(16, 17), "number",
                                  /*int_value=*/2, /*double_value=*/2),
                  IsAnnotatedSpan(CodepointSpan(21, 22), "number",
                                  /*int_value=*/5, /*double_value=*/5),
                  IsAnnotatedSpan(CodepointSpan(25, 27), "number",
                                  /*int_value=*/96, /*double_value=*/96),
                  IsAnnotatedSpan(CodepointSpan(30, 32), "number",
                                  /*int_value=*/89, /*double_value=*/89)));
}

TEST_F(NumberAnnotatorTest, MathOperatorsNotAnnotatedAsNumbersClassifyText) {
  ClassificationResult classification_result;
  EXPECT_FALSE(number_annotator_.ClassifyText(
      UTF8ToUnicodeText("2 + 2"), {2, 3},
      AnnotationUsecase_ANNOTATION_USECASE_RAW, &classification_result));
  EXPECT_FALSE(number_annotator_.ClassifyText(
      UTF8ToUnicodeText("2 - 96 * 89"), {2, 3},
      AnnotationUsecase_ANNOTATION_USECASE_RAW, &classification_result));
}

TEST_F(NumberAnnotatorTest, SlashSeparatesTwoNumbersFindAll) {
  std::vector<AnnotatedSpan> result;
  EXPECT_TRUE(number_annotator_.FindAll(
      UTF8ToUnicodeText("what's 1 + 2/3 * 4／5 * 6 / 7"),
      AnnotationUsecase_ANNOTATION_USECASE_RAW, ModeFlag_ANNOTATION, &result));

  EXPECT_THAT(result,
              UnorderedElementsAre(
                  IsAnnotatedSpan(CodepointSpan(7, 8), "number",
                                  /*int_value=*/1, /*double_value=*/1),
                  IsAnnotatedSpan(CodepointSpan(11, 12), "number",
                                  /*int_value=*/2, /*double_value=*/2),
                  IsAnnotatedSpan(CodepointSpan(13, 14), "number",
                                  /*int_value=*/3, /*double_value=*/3),
                  IsAnnotatedSpan(CodepointSpan(17, 18), "number",
                                  /*int_value=*/4, /*double_value=*/4),
                  IsAnnotatedSpan(CodepointSpan(19, 20), "number",
                                  /*int_value=*/5, /*double_value=*/5),
                  IsAnnotatedSpan(CodepointSpan(23, 24), "number",
                                  /*int_value=*/6, /*double_value=*/6),
                  IsAnnotatedSpan(CodepointSpan(27, 28), "number",
                                  /*int_value=*/7, /*double_value=*/7)));
}

TEST_F(NumberAnnotatorTest, SlashSeparatesTwoNumbersClassifyText) {
  ClassificationResult classification_result;
  EXPECT_TRUE(number_annotator_.ClassifyText(
      UTF8ToUnicodeText("what's 1 + 2/3 * 4"), {11, 12},
      AnnotationUsecase_ANNOTATION_USECASE_RAW, &classification_result));
  EXPECT_EQ(classification_result.collection, "number");
  EXPECT_EQ(classification_result.numeric_value, 2);
  EXPECT_EQ(classification_result.numeric_double_value, 2);
  EXPECT_EQ(classification_result.score, 1);

  EXPECT_TRUE(number_annotator_.ClassifyText(
      UTF8ToUnicodeText("what's 1 + 2/3 * 4"), {13, 14},
      AnnotationUsecase_ANNOTATION_USECASE_RAW, &classification_result));
  EXPECT_EQ(classification_result.collection, "number");
  EXPECT_EQ(classification_result.numeric_value, 3);
  EXPECT_EQ(classification_result.numeric_double_value, 3);
  EXPECT_EQ(classification_result.score, 1);
}

TEST_F(NumberAnnotatorTest, SlashDoesNotSeparatesTwoNumbersFindAll) {
  std::vector<AnnotatedSpan> result;
  // 2 in the "2/" context is a number because / is punctuation
  EXPECT_TRUE(number_annotator_.FindAll(
      UTF8ToUnicodeText("what's 2a2/3 or 2/s4 or 2/ or /3 or //3 or 2//"),
      AnnotationUsecase_ANNOTATION_USECASE_RAW, ModeFlag_ANNOTATION, &result));

  EXPECT_THAT(result, UnorderedElementsAre(IsAnnotatedSpan(
                          CodepointSpan(24, 25), "number",
                          /*int_value=*/2, /*double_value=*/2)));
}

TEST_F(NumberAnnotatorTest, BracketsContextAnnotatedFindAll) {
  std::vector<AnnotatedSpan> result;
  EXPECT_TRUE(number_annotator_.FindAll(
      UTF8ToUnicodeText("The interval is: (12, 13) or [-12, -4.5)"),
      AnnotationUsecase_ANNOTATION_USECASE_RAW, ModeFlag_ANNOTATION, &result));

  EXPECT_THAT(result,
              UnorderedElementsAre(
                  IsAnnotatedSpan(CodepointSpan(18, 20), "number",
                                  /*int_value=*/12, /*double_value=*/12),
                  IsAnnotatedSpan(CodepointSpan(22, 24), "number",
                                  /*int_value=*/13, /*double_value=*/13),
                  IsAnnotatedSpan(CodepointSpan(30, 33), "number",
                                  /*int_value=*/-12, /*double_value=*/-12),
                  IsAnnotatedSpan(CodepointSpan(35, 39), "number",
                                  /*int_value=*/-4, /*double_value=*/-4.5,
                                  /*priority_score=*/1)));
}

TEST_F(NumberAnnotatorTest, BracketsContextNotAnnotatedFindAll) {
  std::vector<AnnotatedSpan> result;
  EXPECT_TRUE(number_annotator_.FindAll(
      UTF8ToUnicodeText("The interval is: -(12, 138*)"),
      AnnotationUsecase_ANNOTATION_USECASE_RAW, ModeFlag_ANNOTATION, &result));

  EXPECT_TRUE(result.empty());
}

TEST_F(NumberAnnotatorTest, FractionalNumberDotsFindAll) {
  std::vector<AnnotatedSpan> result;
  // Dots source: https://unicode-search.net/unicode-namesearch.pl?term=period
  EXPECT_TRUE(number_annotator_.FindAll(
      UTF8ToUnicodeText("3.1 3﹒2 3．3"),
      AnnotationUsecase_ANNOTATION_USECASE_RAW, ModeFlag_ANNOTATION, &result));

  EXPECT_THAT(result, UnorderedElementsAre(
                          IsAnnotatedSpan(CodepointSpan(0, 3), "number",
                                          /*int_value=*/3, /*double_value=*/3.1,
                                          /*priority_score=*/1),
                          IsAnnotatedSpan(CodepointSpan(4, 7), "number",
                                          /*int_value=*/3, /*double_value=*/3.2,
                                          /*priority_score=*/1),
                          IsAnnotatedSpan(CodepointSpan(8, 11), "number",
                                          /*int_value=*/3, /*double_value=*/3.3,
                                          /*priority_score=*/1)));
}

TEST_F(NumberAnnotatorTest, NonAsciiDigitsFindAll) {
  std::vector<AnnotatedSpan> result;
  // Dots source: https://unicode-search.net/unicode-namesearch.pl?term=period
  // Digits source: https://unicode-search.net/unicode-namesearch.pl?term=digit
  EXPECT_TRUE(number_annotator_.FindAll(
      UTF8ToUnicodeText("３ ３﹒２ ３．３%"),
      AnnotationUsecase_ANNOTATION_USECASE_RAW, ModeFlag_ANNOTATION, &result));

  EXPECT_THAT(result, UnorderedElementsAre(
                          IsAnnotatedSpan(CodepointSpan(0, 1), "number",
                                          /*int_value=*/3, /*double_value=*/3),
                          IsAnnotatedSpan(CodepointSpan(2, 5), "number",
                                          /*int_value=*/3, /*double_value=*/3.2,
                                          /*priority_score=*/1),
                          IsAnnotatedSpan(CodepointSpan(6, 9), "number",
                                          /*int_value=*/3, /*double_value=*/3.3,
                                          /*priority_score=*/1),
                          IsAnnotatedSpan(CodepointSpan(6, 10), "percentage",
                                          /*int_value=*/3, /*double_value=*/3.3,
                                          /*priority_score=*/1)));
}

TEST_F(NumberAnnotatorTest, AnnotatedZeroPrecededNumbersFindAll) {
  std::vector<AnnotatedSpan> result;
  EXPECT_TRUE(number_annotator_.FindAll(
      UTF8ToUnicodeText("Numbers: 0.9 or 09 or 09.9 or 032310"),
      AnnotationUsecase_ANNOTATION_USECASE_RAW, ModeFlag_ANNOTATION, &result));

  EXPECT_THAT(result, UnorderedElementsAre(
                          IsAnnotatedSpan(CodepointSpan(9, 12), "number",
                                          /*int_value=*/0, /*double_value=*/0.9,
                                          /*priority_score=*/1),
                          IsAnnotatedSpan(CodepointSpan(16, 18), "number",
                                          /*int_value=*/9, /*double_value=*/9),
                          IsAnnotatedSpan(CodepointSpan(22, 26), "number",
                                          /*int_value=*/9, /*double_value=*/9.9,
                                          /*priority_score=*/1),
                          IsAnnotatedSpan(CodepointSpan(30, 36), "number",
                                          /*int_value=*/32310,
                                          /*double_value=*/32310)));
}

TEST_F(NumberAnnotatorTest, ZeroAfterDotFindAll) {
  std::vector<AnnotatedSpan> result;
  EXPECT_TRUE(number_annotator_.FindAll(
      UTF8ToUnicodeText("15.0 16.00"), AnnotationUsecase_ANNOTATION_USECASE_RAW,
      ModeFlag_ANNOTATION, &result));

  EXPECT_THAT(result,
              UnorderedElementsAre(
                  IsAnnotatedSpan(CodepointSpan(0, 4), "number",
                                  /*int_value=*/15, /*double_value=*/15),
                  IsAnnotatedSpan(CodepointSpan(5, 10), "number",
                                  /*int_value=*/16, /*double_value=*/16)));
}

TEST_F(NumberAnnotatorTest, NineDotNineFindAll) {
  std::vector<AnnotatedSpan> result;
  EXPECT_TRUE(number_annotator_.FindAll(
      UTF8ToUnicodeText("9.9 9.99 99.99 99.999 99.9999"),
      AnnotationUsecase_ANNOTATION_USECASE_RAW, ModeFlag_ANNOTATION, &result));

  EXPECT_THAT(result,
              UnorderedElementsAre(
                  IsAnnotatedSpan(CodepointSpan(0, 3), "number",
                                  /*int_value=*/9, /*double_value=*/9.9,
                                  /*priority_score=*/1),
                  IsAnnotatedSpan(CodepointSpan(4, 8), "number",
                                  /*int_value=*/9, /*double_value=*/9.99,
                                  /*priority_score=*/1),
                  IsAnnotatedSpan(CodepointSpan(9, 14), "number",
                                  /*int_value=*/99, /*double_value=*/99.99,
                                  /*priority_score=*/1),
                  IsAnnotatedSpan(CodepointSpan(15, 21), "number",
                                  /*int_value=*/99, /*double_value=*/99.999,
                                  /*priority_score=*/1),
                  IsAnnotatedSpan(CodepointSpan(22, 29), "number",
                                  /*int_value=*/99, /*double_value=*/99.9999,
                                  /*priority_score=*/1)));
}

}  // namespace test_internal
}  // namespace libtextclassifier3
