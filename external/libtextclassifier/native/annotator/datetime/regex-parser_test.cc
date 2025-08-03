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

#include "annotator/datetime/regex-parser.h"

#include <time.h>

#include <fstream>
#include <iostream>
#include <memory>
#include <string>

#include "annotator/annotator.h"
#include "annotator/datetime/testing/base-parser-test.h"
#include "annotator/datetime/testing/datetime-component-builder.h"
#include "annotator/model_generated.h"
#include "annotator/types-test-util.h"
#include "utils/i18n/locale-list.h"
#include "utils/jvm-test-utils.h"
#include "utils/test-data-test-utils.h"
#include "utils/testing/annotator.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"

using std::vector;

namespace libtextclassifier3 {
namespace {
std::string GetModelPath() { return GetTestDataPath("annotator/test_data/"); }

std::string ReadFile(const std::string& file_name) {
  std::ifstream file_stream(file_name);
  return std::string(std::istreambuf_iterator<char>(file_stream), {});
}

class RegexDatetimeParserTest : public DateTimeParserTest {
 public:
  void SetUp() override {
    // Loads default unmodified model. Individual tests can call LoadModel to
    // make changes.
    LoadModel(
        [](ModelT* model) { model->datetime_grammar_model.reset(nullptr); });
  }

  template <typename Fn>
  void LoadModel(Fn model_visitor_fn) {
    std::string model_buffer = ReadFile(GetModelPath() + "test_model.fb");
    model_buffer_ = ModifyAnnotatorModel(model_buffer, model_visitor_fn);
    unilib_ = CreateUniLibForTesting();
    calendarlib_ = CreateCalendarLibForTesting();
    classifier_ =
        Annotator::FromUnownedBuffer(model_buffer_.data(), model_buffer_.size(),
                                     unilib_.get(), calendarlib_.get());
    TC3_CHECK(classifier_);
    parser_ = classifier_->DatetimeParserForTests();
    TC3_CHECK(parser_);
  }

  // Exposes the date time parser for tests and evaluations.
  const DatetimeParser* DatetimeParserForTests() const override {
    return classifier_->DatetimeParserForTests();
  }

 private:
  std::string model_buffer_;
  std::unique_ptr<Annotator> classifier_;
  const DatetimeParser* parser_;
  std::unique_ptr<UniLib> unilib_;
  std::unique_ptr<CalendarLib> calendarlib_;
};

// Test with just a few cases to make debugging of general failures easier.
TEST_F(RegexDatetimeParserTest, ParseShort) {
  EXPECT_TRUE(ParsesCorrectly(
      "{January 1, 1988}", 567990000000, GRANULARITY_DAY,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 1)
           .Add(DatetimeComponent::ComponentType::MONTH, 1)
           .Add(DatetimeComponent::ComponentType::YEAR, 1988)
           .Build()}));
}

TEST_F(RegexDatetimeParserTest, Parse) {
  EXPECT_TRUE(ParsesCorrectly(
      "{January 1, 1988}", 567990000000, GRANULARITY_DAY,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 1)
           .Add(DatetimeComponent::ComponentType::MONTH, 1)
           .Add(DatetimeComponent::ComponentType::YEAR, 1988)
           .Build()}));
  EXPECT_TRUE(ParsesCorrectly(
      "{january 31 2018}", 1517353200000, GRANULARITY_DAY,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 31)
           .Add(DatetimeComponent::ComponentType::MONTH, 1)
           .Add(DatetimeComponent::ComponentType::YEAR, 2018)
           .Build()}));
  EXPECT_TRUE(ParsesCorrectly(
      "lorem {1 january 2018} ipsum", 1514761200000, GRANULARITY_DAY,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 1)
           .Add(DatetimeComponent::ComponentType::MONTH, 1)
           .Add(DatetimeComponent::ComponentType::YEAR, 2018)
           .Build()}));
  EXPECT_TRUE(ParsesCorrectly(
      "{09/Mar/2004 22:02:40}", 1078866160000, GRANULARITY_SECOND,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::SECOND, 40)
           .Add(DatetimeComponent::ComponentType::MINUTE, 02)
           .Add(DatetimeComponent::ComponentType::HOUR, 22)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 9)
           .Add(DatetimeComponent::ComponentType::MONTH, 3)
           .Add(DatetimeComponent::ComponentType::YEAR, 2004)
           .Build()}));
  EXPECT_TRUE(ParsesCorrectly(
      "{Dec 2, 2010 2:39:58 AM}", 1291253998000, GRANULARITY_SECOND,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 0)
           .Add(DatetimeComponent::ComponentType::SECOND, 58)
           .Add(DatetimeComponent::ComponentType::MINUTE, 39)
           .Add(DatetimeComponent::ComponentType::HOUR, 2)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 2)
           .Add(DatetimeComponent::ComponentType::MONTH, 12)
           .Add(DatetimeComponent::ComponentType::YEAR, 2010)
           .Build()}));
  EXPECT_TRUE(ParsesCorrectly(
      "{Jun 09 2011 15:28:14}", 1307626094000, GRANULARITY_SECOND,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::SECOND, 14)
           .Add(DatetimeComponent::ComponentType::MINUTE, 28)
           .Add(DatetimeComponent::ComponentType::HOUR, 15)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 9)
           .Add(DatetimeComponent::ComponentType::MONTH, 6)
           .Add(DatetimeComponent::ComponentType::YEAR, 2011)
           .Build()}));
  EXPECT_TRUE(ParsesCorrectly(
      "{Mar 16 08:12:04}", {6419524000, 6462724000}, GRANULARITY_SECOND,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 0)
           .Add(DatetimeComponent::ComponentType::SECOND, 4)
           .Add(DatetimeComponent::ComponentType::MINUTE, 12)
           .Add(DatetimeComponent::ComponentType::HOUR, 8)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 16)
           .Add(DatetimeComponent::ComponentType::MONTH, 3)
           .Build(),
       DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 1)
           .Add(DatetimeComponent::ComponentType::SECOND, 4)
           .Add(DatetimeComponent::ComponentType::MINUTE, 12)
           .Add(DatetimeComponent::ComponentType::HOUR, 8)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 16)
           .Add(DatetimeComponent::ComponentType::MONTH, 3)
           .Build()}));
  EXPECT_TRUE(ParsesCorrectly(
      "{2010-06-26 02:31:29}", {1277512289000, 1277555489000},
      GRANULARITY_SECOND,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 0)
           .Add(DatetimeComponent::ComponentType::SECOND, 29)
           .Add(DatetimeComponent::ComponentType::MINUTE, 31)
           .Add(DatetimeComponent::ComponentType::HOUR, 2)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 26)
           .Add(DatetimeComponent::ComponentType::MONTH, 6)
           .Add(DatetimeComponent::ComponentType::YEAR, 2010)
           .Build(),
       DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 1)
           .Add(DatetimeComponent::ComponentType::SECOND, 29)
           .Add(DatetimeComponent::ComponentType::MINUTE, 31)
           .Add(DatetimeComponent::ComponentType::HOUR, 2)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 26)
           .Add(DatetimeComponent::ComponentType::MONTH, 6)
           .Add(DatetimeComponent::ComponentType::YEAR, 2010)
           .Build()}));
  EXPECT_TRUE(ParsesCorrectly(
      "{2006/01/22 04:11:05}", {1137899465000, 1137942665000},
      GRANULARITY_SECOND,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 0)
           .Add(DatetimeComponent::ComponentType::SECOND, 5)
           .Add(DatetimeComponent::ComponentType::MINUTE, 11)
           .Add(DatetimeComponent::ComponentType::HOUR, 4)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 22)
           .Add(DatetimeComponent::ComponentType::MONTH, 1)
           .Add(DatetimeComponent::ComponentType::YEAR, 2006)
           .Build(),
       DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 1)
           .Add(DatetimeComponent::ComponentType::SECOND, 5)
           .Add(DatetimeComponent::ComponentType::MINUTE, 11)
           .Add(DatetimeComponent::ComponentType::HOUR, 4)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 22)
           .Add(DatetimeComponent::ComponentType::MONTH, 1)
           .Add(DatetimeComponent::ComponentType::YEAR, 2006)
           .Build()}));
  EXPECT_TRUE(
      ParsesCorrectly("{11:42:35}", {38555000, 81755000}, GRANULARITY_SECOND,
                      {DatetimeComponentsBuilder()
                           .Add(DatetimeComponent::ComponentType::MERIDIEM, 0)
                           .Add(DatetimeComponent::ComponentType::SECOND, 35)
                           .Add(DatetimeComponent::ComponentType::MINUTE, 42)
                           .Add(DatetimeComponent::ComponentType::HOUR, 11)
                           .Build(),
                       DatetimeComponentsBuilder()
                           .Add(DatetimeComponent::ComponentType::MERIDIEM, 1)
                           .Add(DatetimeComponent::ComponentType::SECOND, 35)
                           .Add(DatetimeComponent::ComponentType::MINUTE, 42)
                           .Add(DatetimeComponent::ComponentType::HOUR, 11)
                           .Build()}));
  EXPECT_TRUE(ParsesCorrectly(
      "{23/Apr 11:42:35}", {9715355000, 9758555000}, GRANULARITY_SECOND,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 0)
           .Add(DatetimeComponent::ComponentType::SECOND, 35)
           .Add(DatetimeComponent::ComponentType::MINUTE, 42)
           .Add(DatetimeComponent::ComponentType::HOUR, 11)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 23)
           .Add(DatetimeComponent::ComponentType::MONTH, 4)
           .Build(),
       DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 1)
           .Add(DatetimeComponent::ComponentType::SECOND, 35)
           .Add(DatetimeComponent::ComponentType::MINUTE, 42)
           .Add(DatetimeComponent::ComponentType::HOUR, 11)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 23)
           .Add(DatetimeComponent::ComponentType::MONTH, 4)
           .Build()}));
  EXPECT_TRUE(ParsesCorrectly(
      "{23/Apr/2015 11:42:35}", {1429782155000, 1429825355000},
      GRANULARITY_SECOND,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 0)
           .Add(DatetimeComponent::ComponentType::SECOND, 35)
           .Add(DatetimeComponent::ComponentType::MINUTE, 42)
           .Add(DatetimeComponent::ComponentType::HOUR, 11)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 23)
           .Add(DatetimeComponent::ComponentType::MONTH, 4)
           .Add(DatetimeComponent::ComponentType::YEAR, 2015)
           .Build(),
       DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 1)
           .Add(DatetimeComponent::ComponentType::SECOND, 35)
           .Add(DatetimeComponent::ComponentType::MINUTE, 42)
           .Add(DatetimeComponent::ComponentType::HOUR, 11)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 23)
           .Add(DatetimeComponent::ComponentType::MONTH, 4)
           .Add(DatetimeComponent::ComponentType::YEAR, 2015)
           .Build()}));
  EXPECT_TRUE(ParsesCorrectly(
      "{23-Apr-2015 11:42:35}", {1429782155000, 1429825355000},
      GRANULARITY_SECOND,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 0)
           .Add(DatetimeComponent::ComponentType::SECOND, 35)
           .Add(DatetimeComponent::ComponentType::MINUTE, 42)
           .Add(DatetimeComponent::ComponentType::HOUR, 11)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 23)
           .Add(DatetimeComponent::ComponentType::MONTH, 4)
           .Add(DatetimeComponent::ComponentType::YEAR, 2015)
           .Build(),
       DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 1)
           .Add(DatetimeComponent::ComponentType::SECOND, 35)
           .Add(DatetimeComponent::ComponentType::MINUTE, 42)
           .Add(DatetimeComponent::ComponentType::HOUR, 11)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 23)
           .Add(DatetimeComponent::ComponentType::MONTH, 4)
           .Add(DatetimeComponent::ComponentType::YEAR, 2015)
           .Build()}));
  EXPECT_TRUE(ParsesCorrectly(
      "{23 Apr 2015 11:42:35}", {1429782155000, 1429825355000},
      GRANULARITY_SECOND,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 0)
           .Add(DatetimeComponent::ComponentType::SECOND, 35)
           .Add(DatetimeComponent::ComponentType::MINUTE, 42)
           .Add(DatetimeComponent::ComponentType::HOUR, 11)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 23)
           .Add(DatetimeComponent::ComponentType::MONTH, 4)
           .Add(DatetimeComponent::ComponentType::YEAR, 2015)
           .Build(),
       DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 1)
           .Add(DatetimeComponent::ComponentType::SECOND, 35)
           .Add(DatetimeComponent::ComponentType::MINUTE, 42)
           .Add(DatetimeComponent::ComponentType::HOUR, 11)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 23)
           .Add(DatetimeComponent::ComponentType::MONTH, 4)
           .Add(DatetimeComponent::ComponentType::YEAR, 2015)
           .Build()}));
  EXPECT_TRUE(ParsesCorrectly(
      "{04/23/15 11:42:35}", {1429782155000, 1429825355000}, GRANULARITY_SECOND,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 0)
           .Add(DatetimeComponent::ComponentType::SECOND, 35)
           .Add(DatetimeComponent::ComponentType::MINUTE, 42)
           .Add(DatetimeComponent::ComponentType::HOUR, 11)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 23)
           .Add(DatetimeComponent::ComponentType::MONTH, 4)
           .Add(DatetimeComponent::ComponentType::YEAR, 2015)
           .Build(),
       DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 1)
           .Add(DatetimeComponent::ComponentType::SECOND, 35)
           .Add(DatetimeComponent::ComponentType::MINUTE, 42)
           .Add(DatetimeComponent::ComponentType::HOUR, 11)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 23)
           .Add(DatetimeComponent::ComponentType::MONTH, 4)
           .Add(DatetimeComponent::ComponentType::YEAR, 2015)
           .Build()}));
  EXPECT_TRUE(ParsesCorrectly(
      "{04/23/2015 11:42:35}", {1429782155000, 1429825355000},
      GRANULARITY_SECOND,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 0)
           .Add(DatetimeComponent::ComponentType::SECOND, 35)
           .Add(DatetimeComponent::ComponentType::MINUTE, 42)
           .Add(DatetimeComponent::ComponentType::HOUR, 11)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 23)
           .Add(DatetimeComponent::ComponentType::MONTH, 4)
           .Add(DatetimeComponent::ComponentType::YEAR, 2015)
           .Build(),
       DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 1)
           .Add(DatetimeComponent::ComponentType::SECOND, 35)
           .Add(DatetimeComponent::ComponentType::MINUTE, 42)
           .Add(DatetimeComponent::ComponentType::HOUR, 11)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 23)
           .Add(DatetimeComponent::ComponentType::MONTH, 4)
           .Add(DatetimeComponent::ComponentType::YEAR, 2015)
           .Build()}));
  EXPECT_TRUE(ParsesCorrectly(
      "{9/28/2011 2:23:15 PM}", 1317212595000, GRANULARITY_SECOND,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 1)
           .Add(DatetimeComponent::ComponentType::SECOND, 15)
           .Add(DatetimeComponent::ComponentType::MINUTE, 23)
           .Add(DatetimeComponent::ComponentType::HOUR, 2)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 28)
           .Add(DatetimeComponent::ComponentType::MONTH, 9)
           .Add(DatetimeComponent::ComponentType::YEAR, 2011)
           .Build()}));
  EXPECT_TRUE(ParsesCorrectly(
      "Are sentiments apartments decisively the especially alteration. "
      "Thrown shy denote ten ladies though ask saw. Or by to he going "
      "think order event music. Incommode so intention defective at "
      "convinced. Led income months itself and houses you. After nor "
      "you leave might share court balls. {19/apr/2010 06:36:15} Are "
      "sentiments apartments decisively the especially alteration. "
      "Thrown shy denote ten ladies though ask saw. Or by to he going "
      "think order event music. Incommode so intention defective at "
      "convinced. Led income months itself and houses you. After nor "
      "you leave might share court balls. ",
      {1271651775000, 1271694975000}, GRANULARITY_SECOND,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 0)
           .Add(DatetimeComponent::ComponentType::SECOND, 15)
           .Add(DatetimeComponent::ComponentType::MINUTE, 36)
           .Add(DatetimeComponent::ComponentType::HOUR, 6)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 19)
           .Add(DatetimeComponent::ComponentType::MONTH, 4)
           .Add(DatetimeComponent::ComponentType::YEAR, 2010)
           .Build(),
       DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 1)
           .Add(DatetimeComponent::ComponentType::SECOND, 15)
           .Add(DatetimeComponent::ComponentType::MINUTE, 36)
           .Add(DatetimeComponent::ComponentType::HOUR, 6)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 19)
           .Add(DatetimeComponent::ComponentType::MONTH, 4)
           .Add(DatetimeComponent::ComponentType::YEAR, 2010)
           .Build()}));
  EXPECT_TRUE(ParsesCorrectly(
      "{january 1 2018 at 4:30}", {1514777400000, 1514820600000},
      GRANULARITY_MINUTE,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 0)
           .Add(DatetimeComponent::ComponentType::MINUTE, 30)
           .Add(DatetimeComponent::ComponentType::HOUR, 4)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 1)
           .Add(DatetimeComponent::ComponentType::MONTH, 1)
           .Add(DatetimeComponent::ComponentType::YEAR, 2018)
           .Build(),
       DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 1)
           .Add(DatetimeComponent::ComponentType::MINUTE, 30)
           .Add(DatetimeComponent::ComponentType::HOUR, 4)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 1)
           .Add(DatetimeComponent::ComponentType::MONTH, 1)
           .Add(DatetimeComponent::ComponentType::YEAR, 2018)
           .Build()}));
  EXPECT_TRUE(ParsesCorrectly(
      "{january 1 2018 at 4:30 am}", 1514777400000, GRANULARITY_MINUTE,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 0)
           .Add(DatetimeComponent::ComponentType::MINUTE, 30)
           .Add(DatetimeComponent::ComponentType::HOUR, 4)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 1)
           .Add(DatetimeComponent::ComponentType::MONTH, 1)
           .Add(DatetimeComponent::ComponentType::YEAR, 2018)
           .Build()}));
  EXPECT_TRUE(ParsesCorrectly(
      "{january 1 2018 at 4pm}", 1514818800000, GRANULARITY_HOUR,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 1)
           .Add(DatetimeComponent::ComponentType::HOUR, 4)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 1)
           .Add(DatetimeComponent::ComponentType::MONTH, 1)
           .Add(DatetimeComponent::ComponentType::YEAR, 2018)
           .Build()}));

  EXPECT_TRUE(ParsesCorrectly(
      "{today at 0:00}", {-3600000, 39600000}, GRANULARITY_MINUTE,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 0)
           .Add(DatetimeComponent::ComponentType::MINUTE, 0)
           .Add(DatetimeComponent::ComponentType::HOUR, 0)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 0,
                DatetimeComponent::RelativeQualifier::NOW, 0)
           .Build(),
       DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 1)
           .Add(DatetimeComponent::ComponentType::MINUTE, 0)
           .Add(DatetimeComponent::ComponentType::HOUR, 0)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 0,
                DatetimeComponent::RelativeQualifier::NOW, 0)
           .Build()}));
  EXPECT_TRUE(ParsesCorrectly(
      "{today at 0:00}", {-57600000, -14400000}, GRANULARITY_MINUTE,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 0)
           .Add(DatetimeComponent::ComponentType::MINUTE, 0)
           .Add(DatetimeComponent::ComponentType::HOUR, 0)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 0,
                DatetimeComponent::RelativeQualifier::NOW, 0)
           .Build(),
       DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 1)
           .Add(DatetimeComponent::ComponentType::MINUTE, 0)
           .Add(DatetimeComponent::ComponentType::HOUR, 0)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 0,
                DatetimeComponent::RelativeQualifier::NOW, 0)
           .Build()},
      /*anchor_start_end=*/false, "America/Los_Angeles"));
  EXPECT_TRUE(ParsesCorrectly(
      "{tomorrow at 4:00}", {97200000, 140400000}, GRANULARITY_MINUTE,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 0)
           .Add(DatetimeComponent::ComponentType::MINUTE, 0)
           .Add(DatetimeComponent::ComponentType::HOUR, 4)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 0,
                DatetimeComponent::RelativeQualifier::TOMORROW, 1)
           .Build(),
       DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 1)
           .Add(DatetimeComponent::ComponentType::MINUTE, 0)
           .Add(DatetimeComponent::ComponentType::HOUR, 4)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 0,
                DatetimeComponent::RelativeQualifier::TOMORROW, 1)
           .Build()}));
  EXPECT_TRUE(ParsesCorrectly(
      "{tomorrow at 4am}", 97200000, GRANULARITY_HOUR,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 0)
           .Add(DatetimeComponent::ComponentType::HOUR, 4)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 0,
                DatetimeComponent::RelativeQualifier::TOMORROW, 1)
           .Build()}));
  EXPECT_TRUE(ParsesCorrectly(
      "{wednesday at 4am}", 529200000, GRANULARITY_HOUR,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 0)
           .Add(DatetimeComponent::ComponentType::HOUR, 4)
           .Add(DatetimeComponent::ComponentType::DAY_OF_WEEK, 4,
                DatetimeComponent::RelativeQualifier::THIS, 0)
           .Build()}));
  EXPECT_TRUE(ParsesCorrectly(
      "last seen {today at 9:01 PM}", 72060000, GRANULARITY_MINUTE,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 1)
           .Add(DatetimeComponent::ComponentType::MINUTE, 1)
           .Add(DatetimeComponent::ComponentType::HOUR, 9)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 0,
                DatetimeComponent::RelativeQualifier::NOW, 0)
           .Build()}));
  EXPECT_TRUE(ParsesCorrectly(
      "set an alarm for {7am tomorrow}", 108000000, GRANULARITY_HOUR,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 0)
           .Add(DatetimeComponent::ComponentType::HOUR, 7)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 0,
                DatetimeComponent::RelativeQualifier::TOMORROW, 1)
           .Build()}));
  EXPECT_TRUE(
      ParsesCorrectly("set an alarm for {7 a.m}", 21600000, GRANULARITY_HOUR,
                      {DatetimeComponentsBuilder()
                           .Add(DatetimeComponent::ComponentType::MERIDIEM, 0)
                           .Add(DatetimeComponent::ComponentType::HOUR, 7)
                           .Build()}));

  EXPECT_TRUE(ParsesCorrectly(
      "lets meet by {noon}", 39600000, GRANULARITY_MINUTE,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 1)
           .Add(DatetimeComponent::ComponentType::MINUTE, 0)
           .Add(DatetimeComponent::ComponentType::HOUR, 12)
           .Build()},
      /*anchor_start_end=*/false,
      /*timezone=*/"Europe/Zurich", /*locales=*/"en-US",
      /*annotation_usecase=*/AnnotationUsecase_ANNOTATION_USECASE_RAW));

  EXPECT_TRUE(ParsesCorrectly(
      "at {midnight}", 82800000, GRANULARITY_MINUTE,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 0)
           .Add(DatetimeComponent::ComponentType::MINUTE, 0)
           .Add(DatetimeComponent::ComponentType::HOUR, 0)
           .Build()},
      /*anchor_start_end=*/false,
      /*timezone=*/"Europe/Zurich", /*locales=*/"en-US",
      /*annotation_usecase=*/AnnotationUsecase_ANNOTATION_USECASE_RAW));
}

TEST_F(RegexDatetimeParserTest, ParseWithAnchor) {
  EXPECT_TRUE(ParsesCorrectly(
      "{January 1, 1988}", 567990000000, GRANULARITY_DAY,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 1)
           .Add(DatetimeComponent::ComponentType::MONTH, 1)
           .Add(DatetimeComponent::ComponentType::YEAR, 1988)
           .Build()},
      /*anchor_start_end=*/false));
  EXPECT_TRUE(ParsesCorrectly(
      "{January 1, 1988}", 567990000000, GRANULARITY_DAY,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 1)
           .Add(DatetimeComponent::ComponentType::MONTH, 1)
           .Add(DatetimeComponent::ComponentType::YEAR, 1988)
           .Build()},
      /*anchor_start_end=*/true));
  EXPECT_TRUE(ParsesCorrectly(
      "lorem {1 january 2018} ipsum", 1514761200000, GRANULARITY_DAY,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 1)
           .Add(DatetimeComponent::ComponentType::MONTH, 1)
           .Add(DatetimeComponent::ComponentType::YEAR, 2018)
           .Build()},
      /*anchor_start_end=*/false));
  EXPECT_TRUE(HasNoResult("lorem 1 january 2018 ipsum",
                          /*anchor_start_end=*/true));
}

TEST_F(RegexDatetimeParserTest, ParseWithRawUsecase) {
  // Annotated for RAW usecase.
  EXPECT_TRUE(ParsesCorrectly(
      "{tomorrow}", 82800000, GRANULARITY_DAY,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 0,
                DatetimeComponent::RelativeQualifier::TOMORROW, 1)
           .Build()},
      /*anchor_start_end=*/false,
      /*timezone=*/"Europe/Zurich", /*locales=*/"en-US",
      /*annotation_usecase=*/AnnotationUsecase_ANNOTATION_USECASE_RAW));

  EXPECT_TRUE(ParsesCorrectly(
      "call me {in two hours}", 7200000, GRANULARITY_HOUR,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::HOUR, 0,
                DatetimeComponent::RelativeQualifier::FUTURE, 2)
           .Build()},
      /*anchor_start_end=*/false,
      /*timezone=*/"Europe/Zurich", /*locales=*/"en-US",
      /*annotation_usecase=*/AnnotationUsecase_ANNOTATION_USECASE_RAW));

  EXPECT_TRUE(ParsesCorrectly(
      "call me {next month}", 2674800000, GRANULARITY_MONTH,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MONTH, 0,
                DatetimeComponent::RelativeQualifier::NEXT, 1)
           .Build()},
      /*anchor_start_end=*/false,
      /*timezone=*/"Europe/Zurich", /*locales=*/"en-US",
      /*annotation_usecase=*/AnnotationUsecase_ANNOTATION_USECASE_RAW));
  EXPECT_TRUE(ParsesCorrectly(
      "what's the time {now}", -3600000, GRANULARITY_DAY,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 0,
                DatetimeComponent::RelativeQualifier::NOW, 0)
           .Build()},
      /*anchor_start_end=*/false,
      /*timezone=*/"Europe/Zurich", /*locales=*/"en-US",
      /*annotation_usecase=*/AnnotationUsecase_ANNOTATION_USECASE_RAW));

  EXPECT_TRUE(ParsesCorrectly(
      "call me on {Saturday}", 169200000, GRANULARITY_DAY,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::DAY_OF_WEEK, 7,
                DatetimeComponent::RelativeQualifier::THIS, 0)
           .Build()},
      /*anchor_start_end=*/false,
      /*timezone=*/"Europe/Zurich", /*locales=*/"en-US",
      /*annotation_usecase=*/AnnotationUsecase_ANNOTATION_USECASE_RAW));

  // Not annotated for Smart usecase.
  EXPECT_TRUE(HasNoResult(
      "{tomorrow}", /*anchor_start_end=*/false,
      /*timezone=*/"Europe/Zurich",
      /*annotation_usecase=*/AnnotationUsecase_ANNOTATION_USECASE_SMART));
}

// For details please see b/155437137
TEST_F(RegexDatetimeParserTest, PastRelativeDatetime) {
  EXPECT_TRUE(ParsesCorrectly(
      "called you {last Saturday}",
      -432000000 /* Fri 1969-12-26 16:00:00 PST */, GRANULARITY_DAY,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::DAY_OF_WEEK, 7,
                DatetimeComponent::RelativeQualifier::PAST, -1)
           .Build()},
      /*anchor_start_end=*/false,
      /*timezone=*/"Europe/Zurich", /*locales=*/"en-US",
      /*annotation_usecase=*/AnnotationUsecase_ANNOTATION_USECASE_RAW));

  EXPECT_TRUE(ParsesCorrectly(
      "called you {last year}", -31536000000 /* Tue 1968-12-31 16:00:00 PST */,
      GRANULARITY_YEAR,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::YEAR, 0,
                DatetimeComponent::RelativeQualifier::PAST, -1)
           .Build()},
      /*anchor_start_end=*/false,
      /*timezone=*/"Europe/Zurich", /*locales=*/"en-US",
      /*annotation_usecase=*/AnnotationUsecase_ANNOTATION_USECASE_RAW));

  EXPECT_TRUE(ParsesCorrectly(
      "called you {last month}", -2678400000 /* Sun 1969-11-30 16:00:00 PST */,
      GRANULARITY_MONTH,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MONTH, 0,
                DatetimeComponent::RelativeQualifier::PAST, -1)
           .Build()},
      /*anchor_start_end=*/false,
      /*timezone=*/"Europe/Zurich", /*locales=*/"en-US",
      /*annotation_usecase=*/AnnotationUsecase_ANNOTATION_USECASE_RAW));

  EXPECT_TRUE(ParsesCorrectly(
      "called you {yesterday}", -90000000, /* Tue 1969-12-30 15:00:00 PST */
      GRANULARITY_DAY,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 0,
                DatetimeComponent::RelativeQualifier::YESTERDAY, -1)
           .Build()},
      /*anchor_start_end=*/false,
      /*timezone=*/"Europe/Zurich", /*locales=*/"en-US",
      /*annotation_usecase=*/AnnotationUsecase_ANNOTATION_USECASE_RAW));
}

TEST_F(RegexDatetimeParserTest, AddsADayWhenTimeInThePastAndDayNotSpecified) {
  // ParsesCorrectly uses 0 as the reference time, which corresponds to:
  // "Thu Jan 01 1970 01:00:00" Zurich time. So if we pass "0:30" here, it means
  // it is in the past, and so the parser should move this to the next day ->
  // "Fri Jan 02 1970 00:30:00" Zurich time (b/139112907).
  EXPECT_TRUE(ParsesCorrectly(
      "{0:30am}", 84600000L /* 23.5 hours from reference time */,
      GRANULARITY_MINUTE,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 0)
           .Add(DatetimeComponent::ComponentType::MINUTE, 30)
           .Add(DatetimeComponent::ComponentType::HOUR, 0)
           .Build()}));
}

TEST_F(RegexDatetimeParserTest,
       DoesNotAddADayWhenTimeInThePastAndDayNotSpecifiedDisabled) {
  // ParsesCorrectly uses 0 as the reference time, which corresponds to:
  // "Thu Jan 01 1970 01:00:00" Zurich time. So if we pass "0:30" here, it means
  // it is in the past. The parameter prefer_future_when_unspecified_day is
  // disabled, so the parser should annotate this to the same day: "Thu Jan 01
  // 1970 00:30:00" Zurich time.
  LoadModel([](ModelT* model) {
    // In the test model, the prefer_future_for_unspecified_date is true; make
    // it false only for this test.
    model->datetime_model->prefer_future_for_unspecified_date = false;
    model->datetime_grammar_model.reset(nullptr);
  });

  EXPECT_TRUE(ParsesCorrectly(
      "{0:30am}", -1800000L /* -30 minutes from reference time */,
      GRANULARITY_MINUTE,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 0)
           .Add(DatetimeComponent::ComponentType::MINUTE, 30)
           .Add(DatetimeComponent::ComponentType::HOUR, 0)
           .Build()}));
}

TEST_F(RegexDatetimeParserTest, ParsesNoonAndMidnightCorrectly) {
  EXPECT_TRUE(ParsesCorrectly(
      "{January 1, 1988 12:30am}", 567991800000, GRANULARITY_MINUTE,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 0)
           .Add(DatetimeComponent::ComponentType::MINUTE, 30)
           .Add(DatetimeComponent::ComponentType::HOUR, 12)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 1)
           .Add(DatetimeComponent::ComponentType::MONTH, 1)
           .Add(DatetimeComponent::ComponentType::YEAR, 1988)
           .Build()}));
  EXPECT_TRUE(ParsesCorrectly(
      "{January 1, 1988 12:30pm}", 568035000000, GRANULARITY_MINUTE,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 1)
           .Add(DatetimeComponent::ComponentType::MINUTE, 30)
           .Add(DatetimeComponent::ComponentType::HOUR, 12)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 1)
           .Add(DatetimeComponent::ComponentType::MONTH, 1)
           .Add(DatetimeComponent::ComponentType::YEAR, 1988)
           .Build()}));
  EXPECT_TRUE(ParsesCorrectly(
      "{tomorrow at 12:00 am}", 82800000, GRANULARITY_MINUTE,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 0)
           .Add(DatetimeComponent::ComponentType::MINUTE, 0)
           .Add(DatetimeComponent::ComponentType::HOUR, 12)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 0,
                DatetimeComponent::RelativeQualifier::TOMORROW, 1)
           .Build()}));
}

TEST_F(RegexDatetimeParserTest, ParseGerman) {
  EXPECT_TRUE(ParsesCorrectlyGerman(
      "{Januar 1 2018}", 1514761200000, GRANULARITY_DAY,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 1)
           .Add(DatetimeComponent::ComponentType::MONTH, 1)
           .Add(DatetimeComponent::ComponentType::YEAR, 2018)
           .Build()}));
  EXPECT_TRUE(ParsesCorrectlyGerman(
      "{1/2/2018}", 1517439600000, GRANULARITY_DAY,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 1)
           .Add(DatetimeComponent::ComponentType::MONTH, 2)
           .Add(DatetimeComponent::ComponentType::YEAR, 2018)
           .Build()}));
  EXPECT_TRUE(ParsesCorrectlyGerman(
      "lorem {1 Januar 2018} ipsum", 1514761200000, GRANULARITY_DAY,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 1)
           .Add(DatetimeComponent::ComponentType::MONTH, 1)
           .Add(DatetimeComponent::ComponentType::YEAR, 2018)
           .Build()}));
  EXPECT_TRUE(ParsesCorrectlyGerman(
      "{19/Apr/2010:06:36:15}", {1271651775000, 1271694975000},
      GRANULARITY_SECOND,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 0)
           .Add(DatetimeComponent::ComponentType::SECOND, 15)
           .Add(DatetimeComponent::ComponentType::MINUTE, 36)
           .Add(DatetimeComponent::ComponentType::HOUR, 6)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 19)
           .Add(DatetimeComponent::ComponentType::MONTH, 4)
           .Add(DatetimeComponent::ComponentType::YEAR, 2010)
           .Build(),
       DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 1)
           .Add(DatetimeComponent::ComponentType::SECOND, 15)
           .Add(DatetimeComponent::ComponentType::MINUTE, 36)
           .Add(DatetimeComponent::ComponentType::HOUR, 6)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 19)
           .Add(DatetimeComponent::ComponentType::MONTH, 4)
           .Add(DatetimeComponent::ComponentType::YEAR, 2010)
           .Build()}));
  EXPECT_TRUE(ParsesCorrectlyGerman(
      "{09/März/2004 22:02:40}", 1078866160000, GRANULARITY_SECOND,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::SECOND, 40)
           .Add(DatetimeComponent::ComponentType::MINUTE, 02)
           .Add(DatetimeComponent::ComponentType::HOUR, 22)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 9)
           .Add(DatetimeComponent::ComponentType::MONTH, 3)
           .Add(DatetimeComponent::ComponentType::YEAR, 2004)
           .Build()}));
  EXPECT_TRUE(ParsesCorrectlyGerman(
      "{Dez 2, 2010 2:39:58}", {1291253998000, 1291297198000},
      GRANULARITY_SECOND,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 0)
           .Add(DatetimeComponent::ComponentType::SECOND, 58)
           .Add(DatetimeComponent::ComponentType::MINUTE, 39)
           .Add(DatetimeComponent::ComponentType::HOUR, 2)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 2)
           .Add(DatetimeComponent::ComponentType::MONTH, 12)
           .Add(DatetimeComponent::ComponentType::YEAR, 2010)
           .Build(),
       DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 1)
           .Add(DatetimeComponent::ComponentType::SECOND, 58)
           .Add(DatetimeComponent::ComponentType::MINUTE, 39)
           .Add(DatetimeComponent::ComponentType::HOUR, 2)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 2)
           .Add(DatetimeComponent::ComponentType::MONTH, 12)
           .Add(DatetimeComponent::ComponentType::YEAR, 2010)
           .Build()}));
  EXPECT_TRUE(ParsesCorrectlyGerman(
      "{Juni 09 2011 15:28:14}", 1307626094000, GRANULARITY_SECOND,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::SECOND, 14)
           .Add(DatetimeComponent::ComponentType::MINUTE, 28)
           .Add(DatetimeComponent::ComponentType::HOUR, 15)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 9)
           .Add(DatetimeComponent::ComponentType::MONTH, 6)
           .Add(DatetimeComponent::ComponentType::YEAR, 2011)
           .Build()}));
  EXPECT_TRUE(ParsesCorrectlyGerman(
      "{März 16 08:12:04}", {6419524000, 6462724000}, GRANULARITY_SECOND,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 0)
           .Add(DatetimeComponent::ComponentType::SECOND, 4)
           .Add(DatetimeComponent::ComponentType::MINUTE, 12)
           .Add(DatetimeComponent::ComponentType::HOUR, 8)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 16)
           .Add(DatetimeComponent::ComponentType::MONTH, 3)
           .Build(),
       DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 1)
           .Add(DatetimeComponent::ComponentType::SECOND, 4)
           .Add(DatetimeComponent::ComponentType::MINUTE, 12)
           .Add(DatetimeComponent::ComponentType::HOUR, 8)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 16)
           .Add(DatetimeComponent::ComponentType::MONTH, 3)
           .Build()}));
  EXPECT_TRUE(ParsesCorrectlyGerman(
      "{2010-06-26 02:31:29}", {1277512289000, 1277555489000},
      GRANULARITY_SECOND,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 0)
           .Add(DatetimeComponent::ComponentType::SECOND, 29)
           .Add(DatetimeComponent::ComponentType::MINUTE, 31)
           .Add(DatetimeComponent::ComponentType::HOUR, 2)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 26)
           .Add(DatetimeComponent::ComponentType::MONTH, 6)
           .Add(DatetimeComponent::ComponentType::YEAR, 2010)
           .Build(),
       DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 1)
           .Add(DatetimeComponent::ComponentType::SECOND, 29)
           .Add(DatetimeComponent::ComponentType::MINUTE, 31)
           .Add(DatetimeComponent::ComponentType::HOUR, 2)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 26)
           .Add(DatetimeComponent::ComponentType::MONTH, 6)
           .Add(DatetimeComponent::ComponentType::YEAR, 2010)
           .Build()}));
  EXPECT_TRUE(ParsesCorrectlyGerman(
      "{2006/01/22 04:11:05}", {1137899465000, 1137942665000},
      GRANULARITY_SECOND,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 0)
           .Add(DatetimeComponent::ComponentType::SECOND, 5)
           .Add(DatetimeComponent::ComponentType::MINUTE, 11)
           .Add(DatetimeComponent::ComponentType::HOUR, 4)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 22)
           .Add(DatetimeComponent::ComponentType::MONTH, 1)
           .Add(DatetimeComponent::ComponentType::YEAR, 2006)
           .Build(),
       DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 1)
           .Add(DatetimeComponent::ComponentType::SECOND, 5)
           .Add(DatetimeComponent::ComponentType::MINUTE, 11)
           .Add(DatetimeComponent::ComponentType::HOUR, 4)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 22)
           .Add(DatetimeComponent::ComponentType::MONTH, 1)
           .Add(DatetimeComponent::ComponentType::YEAR, 2006)
           .Build()}));
  EXPECT_TRUE(ParsesCorrectlyGerman(
      "{11:42:35}", {38555000, 81755000}, GRANULARITY_SECOND,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 0)
           .Add(DatetimeComponent::ComponentType::SECOND, 35)
           .Add(DatetimeComponent::ComponentType::MINUTE, 42)
           .Add(DatetimeComponent::ComponentType::HOUR, 11)
           .Build(),
       DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 1)
           .Add(DatetimeComponent::ComponentType::SECOND, 35)
           .Add(DatetimeComponent::ComponentType::MINUTE, 42)
           .Add(DatetimeComponent::ComponentType::HOUR, 11)
           .Build()}));
  EXPECT_TRUE(ParsesCorrectlyGerman(
      "{23/Apr 11:42:35}", {9715355000, 9758555000}, GRANULARITY_SECOND,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 0)
           .Add(DatetimeComponent::ComponentType::SECOND, 35)
           .Add(DatetimeComponent::ComponentType::MINUTE, 42)
           .Add(DatetimeComponent::ComponentType::HOUR, 11)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 23)
           .Add(DatetimeComponent::ComponentType::MONTH, 4)
           .Build(),
       DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 1)
           .Add(DatetimeComponent::ComponentType::SECOND, 35)
           .Add(DatetimeComponent::ComponentType::MINUTE, 42)
           .Add(DatetimeComponent::ComponentType::HOUR, 11)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 23)
           .Add(DatetimeComponent::ComponentType::MONTH, 4)
           .Build()}));
  EXPECT_TRUE(ParsesCorrectlyGerman(
      "{23/Apr/2015:11:42:35}", {1429782155000, 1429825355000},
      GRANULARITY_SECOND,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 0)
           .Add(DatetimeComponent::ComponentType::SECOND, 35)
           .Add(DatetimeComponent::ComponentType::MINUTE, 42)
           .Add(DatetimeComponent::ComponentType::HOUR, 11)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 23)
           .Add(DatetimeComponent::ComponentType::MONTH, 4)
           .Add(DatetimeComponent::ComponentType::YEAR, 2015)
           .Build(),
       DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 1)
           .Add(DatetimeComponent::ComponentType::SECOND, 35)
           .Add(DatetimeComponent::ComponentType::MINUTE, 42)
           .Add(DatetimeComponent::ComponentType::HOUR, 11)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 23)
           .Add(DatetimeComponent::ComponentType::MONTH, 4)
           .Add(DatetimeComponent::ComponentType::YEAR, 2015)
           .Build()}));
  EXPECT_TRUE(ParsesCorrectlyGerman(
      "{23/Apr/2015 11:42:35}", {1429782155000, 1429825355000},
      GRANULARITY_SECOND,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 0)
           .Add(DatetimeComponent::ComponentType::SECOND, 35)
           .Add(DatetimeComponent::ComponentType::MINUTE, 42)
           .Add(DatetimeComponent::ComponentType::HOUR, 11)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 23)
           .Add(DatetimeComponent::ComponentType::MONTH, 4)
           .Add(DatetimeComponent::ComponentType::YEAR, 2015)
           .Build(),
       DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 1)
           .Add(DatetimeComponent::ComponentType::SECOND, 35)
           .Add(DatetimeComponent::ComponentType::MINUTE, 42)
           .Add(DatetimeComponent::ComponentType::HOUR, 11)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 23)
           .Add(DatetimeComponent::ComponentType::MONTH, 4)
           .Add(DatetimeComponent::ComponentType::YEAR, 2015)
           .Build()}));
  EXPECT_TRUE(ParsesCorrectlyGerman(
      "{23-Apr-2015 11:42:35}", {1429782155000, 1429825355000},
      GRANULARITY_SECOND,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 0)
           .Add(DatetimeComponent::ComponentType::SECOND, 35)
           .Add(DatetimeComponent::ComponentType::MINUTE, 42)
           .Add(DatetimeComponent::ComponentType::HOUR, 11)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 23)
           .Add(DatetimeComponent::ComponentType::MONTH, 4)
           .Add(DatetimeComponent::ComponentType::YEAR, 2015)
           .Build(),
       DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 1)
           .Add(DatetimeComponent::ComponentType::SECOND, 35)
           .Add(DatetimeComponent::ComponentType::MINUTE, 42)
           .Add(DatetimeComponent::ComponentType::HOUR, 11)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 23)
           .Add(DatetimeComponent::ComponentType::MONTH, 4)
           .Add(DatetimeComponent::ComponentType::YEAR, 2015)
           .Build()}));
  EXPECT_TRUE(ParsesCorrectlyGerman(
      "{23 Apr 2015 11:42:35}", {1429782155000, 1429825355000},
      GRANULARITY_SECOND,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 0)
           .Add(DatetimeComponent::ComponentType::SECOND, 35)
           .Add(DatetimeComponent::ComponentType::MINUTE, 42)
           .Add(DatetimeComponent::ComponentType::HOUR, 11)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 23)
           .Add(DatetimeComponent::ComponentType::MONTH, 4)
           .Add(DatetimeComponent::ComponentType::YEAR, 2015)
           .Build(),
       DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 1)
           .Add(DatetimeComponent::ComponentType::SECOND, 35)
           .Add(DatetimeComponent::ComponentType::MINUTE, 42)
           .Add(DatetimeComponent::ComponentType::HOUR, 11)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 23)
           .Add(DatetimeComponent::ComponentType::MONTH, 4)
           .Add(DatetimeComponent::ComponentType::YEAR, 2015)
           .Build()}));
  EXPECT_TRUE(ParsesCorrectlyGerman(
      "{04/23/15 11:42:35}", {1429782155000, 1429825355000}, GRANULARITY_SECOND,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 0)
           .Add(DatetimeComponent::ComponentType::SECOND, 35)
           .Add(DatetimeComponent::ComponentType::MINUTE, 42)
           .Add(DatetimeComponent::ComponentType::HOUR, 11)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 23)
           .Add(DatetimeComponent::ComponentType::MONTH, 4)
           .Add(DatetimeComponent::ComponentType::YEAR, 2015)
           .Build(),
       DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 1)
           .Add(DatetimeComponent::ComponentType::SECOND, 35)
           .Add(DatetimeComponent::ComponentType::MINUTE, 42)
           .Add(DatetimeComponent::ComponentType::HOUR, 11)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 23)
           .Add(DatetimeComponent::ComponentType::MONTH, 4)
           .Add(DatetimeComponent::ComponentType::YEAR, 2015)
           .Build()}));
  EXPECT_TRUE(ParsesCorrectlyGerman(
      "{04/23/2015 11:42:35}", {1429782155000, 1429825355000},
      GRANULARITY_SECOND,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 0)
           .Add(DatetimeComponent::ComponentType::SECOND, 35)
           .Add(DatetimeComponent::ComponentType::MINUTE, 42)
           .Add(DatetimeComponent::ComponentType::HOUR, 11)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 23)
           .Add(DatetimeComponent::ComponentType::MONTH, 4)
           .Add(DatetimeComponent::ComponentType::YEAR, 2015)
           .Build(),
       DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 1)
           .Add(DatetimeComponent::ComponentType::SECOND, 35)
           .Add(DatetimeComponent::ComponentType::MINUTE, 42)
           .Add(DatetimeComponent::ComponentType::HOUR, 11)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 23)
           .Add(DatetimeComponent::ComponentType::MONTH, 4)
           .Add(DatetimeComponent::ComponentType::YEAR, 2015)
           .Build()}));
  EXPECT_TRUE(ParsesCorrectlyGerman(
      "{19/apr/2010:06:36:15}", {1271651775000, 1271694975000},
      GRANULARITY_SECOND,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 0)
           .Add(DatetimeComponent::ComponentType::SECOND, 15)
           .Add(DatetimeComponent::ComponentType::MINUTE, 36)
           .Add(DatetimeComponent::ComponentType::HOUR, 6)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 19)
           .Add(DatetimeComponent::ComponentType::MONTH, 4)
           .Add(DatetimeComponent::ComponentType::YEAR, 2010)
           .Build(),
       DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 1)
           .Add(DatetimeComponent::ComponentType::SECOND, 15)
           .Add(DatetimeComponent::ComponentType::MINUTE, 36)
           .Add(DatetimeComponent::ComponentType::HOUR, 6)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 19)
           .Add(DatetimeComponent::ComponentType::MONTH, 4)
           .Add(DatetimeComponent::ComponentType::YEAR, 2010)
           .Build()}));
  EXPECT_TRUE(ParsesCorrectlyGerman(
      "{januar 1 2018 um 4:30}", {1514777400000, 1514820600000},
      GRANULARITY_MINUTE,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 0)
           .Add(DatetimeComponent::ComponentType::MINUTE, 30)
           .Add(DatetimeComponent::ComponentType::HOUR, 4)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 1)
           .Add(DatetimeComponent::ComponentType::MONTH, 1)
           .Add(DatetimeComponent::ComponentType::YEAR, 2018)
           .Build(),
       DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 1)
           .Add(DatetimeComponent::ComponentType::MINUTE, 30)
           .Add(DatetimeComponent::ComponentType::HOUR, 4)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 1)
           .Add(DatetimeComponent::ComponentType::MONTH, 1)
           .Add(DatetimeComponent::ComponentType::YEAR, 2018)
           .Build()}));
  EXPECT_TRUE(ParsesCorrectlyGerman(
      "{januar 1 2018 um 4:30 nachm}", 1514820600000, GRANULARITY_MINUTE,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 1)
           .Add(DatetimeComponent::ComponentType::MINUTE, 30)
           .Add(DatetimeComponent::ComponentType::HOUR, 4)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 1)
           .Add(DatetimeComponent::ComponentType::MONTH, 1)
           .Add(DatetimeComponent::ComponentType::YEAR, 2018)
           .Build()}));
  EXPECT_TRUE(ParsesCorrectlyGerman(
      "{januar 1 2018 um 4 nachm}", 1514818800000, GRANULARITY_HOUR,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 1)
           .Add(DatetimeComponent::ComponentType::HOUR, 4)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 1)
           .Add(DatetimeComponent::ComponentType::MONTH, 1)
           .Add(DatetimeComponent::ComponentType::YEAR, 2018)
           .Build()}));
  EXPECT_TRUE(ParsesCorrectlyGerman(
      "{14.03.2017}", 1489446000000, GRANULARITY_DAY,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 14)
           .Add(DatetimeComponent::ComponentType::MONTH, 3)
           .Add(DatetimeComponent::ComponentType::YEAR, 2017)
           .Build()}));

  EXPECT_TRUE(ParsesCorrectlyGerman(
      "{morgen 0:00}", {82800000, 126000000}, GRANULARITY_MINUTE,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 0)
           .Add(DatetimeComponent::ComponentType::MINUTE, 0)
           .Add(DatetimeComponent::ComponentType::HOUR, 0)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 0,
                DatetimeComponent::RelativeQualifier::TOMORROW, 1)
           .Build(),
       DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 1)
           .Add(DatetimeComponent::ComponentType::MINUTE, 0)
           .Add(DatetimeComponent::ComponentType::HOUR, 0)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 0,
                DatetimeComponent::RelativeQualifier::TOMORROW, 1)
           .Build()}));
  EXPECT_TRUE(ParsesCorrectlyGerman(
      "{morgen um 4:00}", {97200000, 140400000}, GRANULARITY_MINUTE,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 0)
           .Add(DatetimeComponent::ComponentType::MINUTE, 0)
           .Add(DatetimeComponent::ComponentType::HOUR, 4)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 0,
                DatetimeComponent::RelativeQualifier::TOMORROW, 1)
           .Build(),
       DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 1)
           .Add(DatetimeComponent::ComponentType::MINUTE, 0)
           .Add(DatetimeComponent::ComponentType::HOUR, 4)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 0,
                DatetimeComponent::RelativeQualifier::TOMORROW, 1)
           .Build()}));
  EXPECT_TRUE(ParsesCorrectlyGerman(
      "{morgen um 4 vorm}", 97200000, GRANULARITY_HOUR,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 0)
           .Add(DatetimeComponent::ComponentType::HOUR, 4)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 0,
                DatetimeComponent::RelativeQualifier::TOMORROW, 1)
           .Build()}));
}

TEST_F(RegexDatetimeParserTest, ParseChinese) {
  EXPECT_TRUE(ParsesCorrectlyChinese(
      "{明天 7 上午}", 108000000, GRANULARITY_HOUR,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 0)
           .Add(DatetimeComponent::ComponentType::HOUR, 7)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 0,
                DatetimeComponent::RelativeQualifier::TOMORROW, 1)
           .Build()}));
}

TEST_F(RegexDatetimeParserTest, ParseNonUs) {
  auto first_may_2015 =
      DatetimeComponentsBuilder()
          .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 1)
          .Add(DatetimeComponent::ComponentType::MONTH, 5)
          .Add(DatetimeComponent::ComponentType::YEAR, 2015)
          .Build();

  EXPECT_TRUE(ParsesCorrectly("{1/5/2015}", 1430431200000, GRANULARITY_DAY,
                              {first_may_2015},
                              /*anchor_start_end=*/false,
                              /*timezone=*/"Europe/Zurich",
                              /*locales=*/"en-GB"));
  EXPECT_TRUE(ParsesCorrectly("{1/5/2015}", 1430431200000, GRANULARITY_DAY,
                              {first_may_2015},
                              /*anchor_start_end=*/false,
                              /*timezone=*/"Europe/Zurich", /*locales=*/"en"));
}

TEST_F(RegexDatetimeParserTest, ParseUs) {
  auto five_january_2015 =
      DatetimeComponentsBuilder()
          .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 5)
          .Add(DatetimeComponent::ComponentType::MONTH, 1)
          .Add(DatetimeComponent::ComponentType::YEAR, 2015)
          .Build();

  EXPECT_TRUE(ParsesCorrectly("{1/5/2015}", 1420412400000, GRANULARITY_DAY,
                              {five_january_2015},
                              /*anchor_start_end=*/false,
                              /*timezone=*/"Europe/Zurich",
                              /*locales=*/"en-US"));
  EXPECT_TRUE(ParsesCorrectly("{1/5/2015}", 1420412400000, GRANULARITY_DAY,
                              {five_january_2015},
                              /*anchor_start_end=*/false,
                              /*timezone=*/"Europe/Zurich",
                              /*locales=*/"es-US"));
}

TEST_F(RegexDatetimeParserTest, ParseUnknownLanguage) {
  EXPECT_TRUE(ParsesCorrectly(
      "bylo to {31. 12. 2015} v 6 hodin", 1451516400000, GRANULARITY_DAY,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 31)
           .Add(DatetimeComponent::ComponentType::MONTH, 12)
           .Add(DatetimeComponent::ComponentType::YEAR, 2015)
           .Build()},
      /*anchor_start_end=*/false,
      /*timezone=*/"Europe/Zurich", /*locales=*/"xx"));
}

TEST_F(RegexDatetimeParserTest, WhenAlternativesEnabledGeneratesAlternatives) {
  LoadModel([](ModelT* model) {
    model->datetime_model->generate_alternative_interpretations_when_ambiguous =
        true;
  });

  EXPECT_TRUE(ParsesCorrectly(
      "{january 1 2018 at 4:30}", {1514777400000, 1514820600000},
      GRANULARITY_MINUTE,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 0)
           .Add(DatetimeComponent::ComponentType::MINUTE, 30)
           .Add(DatetimeComponent::ComponentType::HOUR, 4)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 1)
           .Add(DatetimeComponent::ComponentType::MONTH, 1)
           .Add(DatetimeComponent::ComponentType::YEAR, 2018)
           .Build(),
       DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 1)
           .Add(DatetimeComponent::ComponentType::MINUTE, 30)
           .Add(DatetimeComponent::ComponentType::HOUR, 4)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 1)
           .Add(DatetimeComponent::ComponentType::MONTH, 1)
           .Add(DatetimeComponent::ComponentType::YEAR, 2018)
           .Build()}));
  EXPECT_TRUE(ParsesCorrectly(
      "{monday 3pm}", 396000000, GRANULARITY_HOUR,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 1)
           .Add(DatetimeComponent::ComponentType::HOUR, 3)
           .Add(DatetimeComponent::ComponentType::DAY_OF_WEEK, 2,
                DatetimeComponent::RelativeQualifier::THIS, 0)
           .Build()}));
  EXPECT_TRUE(ParsesCorrectly(
      "{monday 3:00}", {352800000, 396000000}, GRANULARITY_MINUTE,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 0)
           .Add(DatetimeComponent::ComponentType::MINUTE, 0)
           .Add(DatetimeComponent::ComponentType::HOUR, 3)
           .Add(DatetimeComponent::ComponentType::DAY_OF_WEEK, 2,
                DatetimeComponent::RelativeQualifier::THIS, 0)
           .Build(),
       DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 1)
           .Add(DatetimeComponent::ComponentType::MINUTE, 0)
           .Add(DatetimeComponent::ComponentType::HOUR, 3)
           .Add(DatetimeComponent::ComponentType::DAY_OF_WEEK, 2,
                DatetimeComponent::RelativeQualifier::THIS, 0)
           .Build()}));
}

TEST_F(RegexDatetimeParserTest,
       WhenAlternativesDisabledDoesNotGenerateAlternatives) {
  LoadModel([](ModelT* model) {
    model->datetime_model->generate_alternative_interpretations_when_ambiguous =
        false;
    model->datetime_grammar_model.reset(nullptr);
  });

  EXPECT_TRUE(ParsesCorrectly(
      "{january 1 2018 at 4:30}", 1514777400000, GRANULARITY_MINUTE,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MINUTE, 30)
           .Add(DatetimeComponent::ComponentType::HOUR, 4)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 1)
           .Add(DatetimeComponent::ComponentType::MONTH, 1)
           .Add(DatetimeComponent::ComponentType::YEAR, 2018)
           .Build()}));
}

class ParserLocaleTest : public testing::Test {
 public:
  void SetUp() override;
  bool HasResult(const std::string& input, const std::string& locales);

 protected:
  std::unique_ptr<UniLib> unilib_;
  std::unique_ptr<CalendarLib> calendarlib_;
  flatbuffers::FlatBufferBuilder builder_;
  std::unique_ptr<DatetimeParser> parser_;
};

void AddPattern(const std::string& regex, int locale,
                std::vector<std::unique_ptr<DatetimeModelPatternT>>* patterns) {
  patterns->emplace_back(new DatetimeModelPatternT);
  patterns->back()->regexes.emplace_back(new DatetimeModelPattern_::RegexT);
  patterns->back()->regexes.back()->pattern = regex;
  patterns->back()->regexes.back()->groups.push_back(
      DatetimeGroupType_GROUP_UNUSED);
  patterns->back()->locales.push_back(locale);
}

void ParserLocaleTest::SetUp() {
  DatetimeModelT model;
  model.use_extractors_for_locating = false;
  model.locales.clear();
  model.locales.push_back("en-US");
  model.locales.push_back("en-CH");
  model.locales.push_back("zh-Hant");
  model.locales.push_back("en-*");
  model.locales.push_back("zh-Hant-*");
  model.locales.push_back("*-CH");
  model.locales.push_back("default");
  model.default_locales.push_back(6);

  AddPattern(/*regex=*/"en-US", /*locale=*/0, &model.patterns);
  AddPattern(/*regex=*/"en-CH", /*locale=*/1, &model.patterns);
  AddPattern(/*regex=*/"zh-Hant", /*locale=*/2, &model.patterns);
  AddPattern(/*regex=*/"en-all", /*locale=*/3, &model.patterns);
  AddPattern(/*regex=*/"zh-Hant-all", /*locale=*/4, &model.patterns);
  AddPattern(/*regex=*/"all-CH", /*locale=*/5, &model.patterns);
  AddPattern(/*regex=*/"default", /*locale=*/6, &model.patterns);

  builder_.Finish(DatetimeModel::Pack(builder_, &model));
  const DatetimeModel* model_fb =
      flatbuffers::GetRoot<DatetimeModel>(builder_.GetBufferPointer());
  ASSERT_TRUE(model_fb);

  unilib_ = CreateUniLibForTesting();
  calendarlib_ = CreateCalendarLibForTesting();
  parser_ =
      RegexDatetimeParser::Instance(model_fb, unilib_.get(), calendarlib_.get(),
                                    /*decompressor=*/nullptr);
  ASSERT_TRUE(parser_);
}

bool ParserLocaleTest::HasResult(const std::string& input,
                                 const std::string& locales) {
  StatusOr<std::vector<DatetimeParseResultSpan>> results = parser_->Parse(
      input, /*reference_time_ms_utc=*/0,
      /*reference_timezone=*/"", LocaleList::ParseFrom(locales),
      ModeFlag_ANNOTATION, AnnotationUsecase_ANNOTATION_USECASE_SMART, false);
  EXPECT_TRUE(results.ok());
  return results.ValueOrDie().size() == 1;
}

TEST_F(ParserLocaleTest, English) {
  EXPECT_TRUE(HasResult("en-US", /*locales=*/"en-US"));
  EXPECT_FALSE(HasResult("en-CH", /*locales=*/"en-US"));
  EXPECT_FALSE(HasResult("en-US", /*locales=*/"en-CH"));
  EXPECT_TRUE(HasResult("en-CH", /*locales=*/"en-CH"));
  EXPECT_TRUE(HasResult("default", /*locales=*/"en-CH"));
}

TEST_F(ParserLocaleTest, TraditionalChinese) {
  EXPECT_TRUE(HasResult("zh-Hant-all", /*locales=*/"zh-Hant"));
  EXPECT_TRUE(HasResult("zh-Hant-all", /*locales=*/"zh-Hant-TW"));
  EXPECT_TRUE(HasResult("zh-Hant-all", /*locales=*/"zh-Hant-SG"));
  EXPECT_FALSE(HasResult("zh-Hant-all", /*locales=*/"zh-SG"));
  EXPECT_FALSE(HasResult("zh-Hant-all", /*locales=*/"zh"));
  EXPECT_TRUE(HasResult("default", /*locales=*/"zh"));
  EXPECT_TRUE(HasResult("default", /*locales=*/"zh-Hant-SG"));
}

TEST_F(ParserLocaleTest, SwissEnglish) {
  EXPECT_TRUE(HasResult("all-CH", /*locales=*/"de-CH"));
  EXPECT_TRUE(HasResult("all-CH", /*locales=*/"en-CH"));
  EXPECT_TRUE(HasResult("en-all", /*locales=*/"en-CH"));
  EXPECT_FALSE(HasResult("all-CH", /*locales=*/"de-DE"));
  EXPECT_TRUE(HasResult("default", /*locales=*/"de-CH"));
  EXPECT_TRUE(HasResult("default", /*locales=*/"en-CH"));
}

}  // namespace
}  // namespace libtextclassifier3
