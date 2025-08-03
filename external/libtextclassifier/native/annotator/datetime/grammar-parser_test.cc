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

#include "annotator/datetime/grammar-parser.h"

#include <memory>
#include <string>

#include "annotator/datetime/datetime-grounder.h"
#include "annotator/datetime/testing/base-parser-test.h"
#include "annotator/datetime/testing/datetime-component-builder.h"
#include "annotator/model_generated.h"
#include "utils/grammar/analyzer.h"
#include "utils/jvm-test-utils.h"
#include "utils/test-data-test-utils.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"

using ::libtextclassifier3::grammar::Analyzer;
using ::libtextclassifier3::grammar::RulesSet;

namespace libtextclassifier3 {
namespace {
std::string GetModelPath() { return GetTestDataPath("annotator/test_data/"); }

std::string ReadFile(const std::string& file_name) {
  std::ifstream file_stream(file_name);
  return std::string(std::istreambuf_iterator<char>(file_stream), {});
}

class GrammarDatetimeParserTest : public DateTimeParserTest {
 public:
  void SetUp() override { ResetParser(ModeFlag_ALL); }

  // Exposes the date time parser for tests and evaluations.
  const DatetimeParser* DatetimeParserForTests() const override {
    return parser_.get();
  }

 protected:
  void ResetParser(ModeFlag enabled_modes) {
    grammar_buffer_ = ReadFile(GetModelPath() + "datetime.fb");
    unilib_ = CreateUniLibForTesting();
    calendarlib_ = CreateCalendarLibForTesting();
    analyzer_ = std::make_unique<Analyzer>(
        unilib_.get(), flatbuffers::GetRoot<RulesSet>(grammar_buffer_.data()));
    datetime_grounder_ = std::make_unique<DatetimeGrounder>(calendarlib_.get());
    parser_.reset(new GrammarDatetimeParser(*analyzer_, *datetime_grounder_,
                                            /*target_classification_score=*/1.0,
                                            /*priority_score=*/1.0,
                                            enabled_modes));
  }

 private:
  std::string grammar_buffer_;
  std::unique_ptr<UniLib> unilib_;
  std::unique_ptr<CalendarLib> calendarlib_;
  std::unique_ptr<Analyzer> analyzer_;
  std::unique_ptr<DatetimeGrounder> datetime_grounder_;
  std::unique_ptr<DatetimeParser> parser_;
};

TEST_F(GrammarDatetimeParserTest, ParseShort) {
  EXPECT_TRUE(ParsesCorrectly(
      "{01/02/2020}", 1580511600000, GRANULARITY_DAY,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 1)
           .Add(DatetimeComponent::ComponentType::MONTH, 2)
           .Add(DatetimeComponent::ComponentType::YEAR, 2020)
           .Build()},
      /*anchor_start_end=*/false,
      /*timezone=*/"Europe/Zurich",
      /*locales=*/"en-GB"));

  EXPECT_TRUE(ParsesCorrectly(
      "{01/02/2020}", 1577919600000, GRANULARITY_DAY,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 2)
           .Add(DatetimeComponent::ComponentType::MONTH, 1)
           .Add(DatetimeComponent::ComponentType::YEAR, 2020)
           .Build()}));

  EXPECT_TRUE(ParsesCorrectly(
      "{January 1, 1988}", 567990000000, GRANULARITY_DAY,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 1)
           .Add(DatetimeComponent::ComponentType::MONTH, 1)
           .Add(DatetimeComponent::ComponentType::YEAR, 1988)
           .Build()}));
}

TEST_F(GrammarDatetimeParserTest, Parse) {
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
      "foo {1 january 2018} bar", 1514761200000, GRANULARITY_DAY,
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
      "last seen {today at 9:01 PM}", 72060000, GRANULARITY_MINUTE,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 1)
           .Add(DatetimeComponent::ComponentType::MINUTE, 1)
           .Add(DatetimeComponent::ComponentType::HOUR, 9)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 0,
                DatetimeComponent::RelativeQualifier::NOW, 0)
           .Build()}));
  EXPECT_TRUE(
      ParsesCorrectly("set an alarm for {7 a.m}", 21600000, GRANULARITY_HOUR,
                      {DatetimeComponentsBuilder()
                           .Add(DatetimeComponent::ComponentType::MERIDIEM, 0)
                           .Add(DatetimeComponent::ComponentType::HOUR, 7)
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
}

TEST_F(GrammarDatetimeParserTest, NotEnabledModeHasNoResult) {
  ResetParser(ModeFlag_SELECTION);
  // `DateTimeParserTest` implementation parses the input under the ANNOTATION
  // mode.
  EXPECT_TRUE(HasNoResult("{January 1, 1988}"));
}

TEST_F(GrammarDatetimeParserTest, DateValidation) {
  EXPECT_TRUE(ParsesCorrectly(
      "{01/02/2020}", 1577919600000, GRANULARITY_DAY,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 2)
           .Add(DatetimeComponent::ComponentType::MONTH, 1)
           .Add(DatetimeComponent::ComponentType::YEAR, 2020)
           .Build()}));
  EXPECT_TRUE(ParsesCorrectly(
      "{21/02/2020}", 1582239600000, GRANULARITY_DAY,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 21)
           .Add(DatetimeComponent::ComponentType::MONTH, 2)
           .Add(DatetimeComponent::ComponentType::YEAR, 2020)
           .Build()}));
}

TEST_F(GrammarDatetimeParserTest, OnlyRelativeDatetime) {
  EXPECT_TRUE(
      ParsesCorrectly("{in 3 hours}", 10800000, GRANULARITY_HOUR,
                      {DatetimeComponentsBuilder()
                           .Add(DatetimeComponent::ComponentType::HOUR, 0,
                                DatetimeComponent::RelativeQualifier::FUTURE, 3)
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
      "set an alarm for {7am tomorrow}", 108000000, GRANULARITY_HOUR,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::MERIDIEM, 0)
           .Add(DatetimeComponent::ComponentType::HOUR, 7)
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 0,
                DatetimeComponent::RelativeQualifier::TOMORROW, 1)
           .Build()}));
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
}

TEST_F(GrammarDatetimeParserTest, NamedMonthDate) {
  EXPECT_TRUE(ParsesCorrectly(
      "{march 1, 2017}", 1488355200000, GRANULARITY_DAY,
      {DatetimeComponentsBuilder()
           .Add(DatetimeComponent::ComponentType::DAY_OF_MONTH, 1)
           .Add(DatetimeComponent::ComponentType::MONTH, 3)
           .Add(DatetimeComponent::ComponentType::YEAR, 2017)
           .Build()},
      false, "America/Los_Angeles", "en-US",
      AnnotationUsecase_ANNOTATION_USECASE_SMART));
}

}  // namespace
}  // namespace libtextclassifier3
