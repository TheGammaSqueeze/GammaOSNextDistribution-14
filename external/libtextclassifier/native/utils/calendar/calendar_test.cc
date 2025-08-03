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

#include "utils/jvm-test-utils.h"
#include "gtest/gtest.h"

namespace libtextclassifier3 {
namespace {

class CalendarTest : public ::testing::Test {
 protected:
  CalendarTest()
      : calendarlib_(libtextclassifier3::CreateCalendarLibForTesting()) {}

  static constexpr int kWednesday = 4;
  std::unique_ptr<CalendarLib> calendarlib_;
};

TEST_F(CalendarTest, Interface) {
  int64 time;
  DatetimeGranularity granularity;
  std::string timezone;
  DatetimeParsedData data;
  bool result = calendarlib_->InterpretParseData(
      data, /*reference_time_ms_utc=*/0L, /*reference_timezone=*/"Zurich",
      /*reference_locale=*/"en-CH",
      /*prefer_future_for_unspecified_date=*/false, &time, &granularity);
  TC3_LOG(INFO) << result;
}

TEST_F(CalendarTest, SetsZeroTimeWhenNotRelative) {
  int64 time;
  DatetimeGranularity granularity;
  DatetimeParsedData data;
  data.SetAbsoluteValue(DatetimeComponent::ComponentType::YEAR, 2018);

  ASSERT_TRUE(calendarlib_->InterpretParseData(
      data,
      /*reference_time_ms_utc=*/0L, /*reference_timezone=*/"Europe/Zurich",
      /*reference_locale=*/"en-CH",
      /*prefer_future_for_unspecified_date=*/false, &time, &granularity));
  EXPECT_EQ(time, 1514761200000L /* Jan 01 2018 00:00:00 */);

  ASSERT_TRUE(calendarlib_->InterpretParseData(
      data,
      /*reference_time_ms_utc=*/1L, /*reference_timezone=*/"Europe/Zurich",
      /*reference_locale=*/"en-CH",
      /*prefer_future_for_unspecified_date=*/false, &time, &granularity));
  EXPECT_EQ(time, 1514761200000L /* Jan 01 2018 00:00:00 */);
}

TEST_F(CalendarTest, SetsTimeZone) {
  int64 time;
  DatetimeGranularity granularity;
  DatetimeParsedData data;
  data.SetAbsoluteValue(DatetimeComponent::ComponentType::YEAR, 2018);
  data.SetAbsoluteValue(DatetimeComponent::ComponentType::HOUR, 7);
  data.SetAbsoluteValue(DatetimeComponent::ComponentType::MINUTE, 30);
  data.SetAbsoluteValue(DatetimeComponent::ComponentType::SECOND, 10);

  ASSERT_TRUE(calendarlib_->InterpretParseData(
      data,
      /*reference_time_ms_utc=*/0L, /*reference_timezone=*/"Europe/Zurich",
      /*reference_locale=*/"en-CH",
      /*prefer_future_for_unspecified_date=*/false, &time, &granularity));
  EXPECT_EQ(time, 1514788210000L /* Jan 01 2018 07:30:10 GMT+01:00 */);

  data.SetAbsoluteValue(DatetimeComponent::ComponentType::ZONE_OFFSET,
                        60);  // GMT+01:00
  ASSERT_TRUE(calendarlib_->InterpretParseData(
      data,
      /*reference_time_ms_utc=*/0L, /*reference_timezone=*/"Europe/Zurich",
      /*reference_locale=*/"en-CH",
      /*prefer_future_for_unspecified_date=*/false, &time, &granularity));
  EXPECT_EQ(time, 1514788210000L /* Jan 01 2018 07:30:10 GMT+01:00 */);

  // Now the hour is in terms of GMT+02:00 which is one hour ahead of
  // GMT+01:00.
  data.SetAbsoluteValue(DatetimeComponent::ComponentType::ZONE_OFFSET,
                        120);  // GMT+02:00
  ASSERT_TRUE(calendarlib_->InterpretParseData(
      data,
      /*reference_time_ms_utc=*/0L, /*reference_timezone=*/"Europe/Zurich",
      /*reference_locale=*/"en-CH",
      /*prefer_future_for_unspecified_date=*/false, &time, &granularity));
  EXPECT_EQ(time, 1514784610000L /* Jan 01 2018 06:30:10 GMT+01:00 */);
}

TEST_F(CalendarTest, RoundingToGranularityBasic) {
  int64 time;
  DatetimeGranularity granularity;
  DatetimeParsedData data;

  data.SetAbsoluteValue(DatetimeComponent::ComponentType::YEAR, 2018);
  ASSERT_TRUE(calendarlib_->InterpretParseData(
      data,
      /*reference_time_ms_utc=*/0L, /*reference_timezone=*/"Europe/Zurich",
      /*reference_locale=*/"en-CH",
      /*prefer_future_for_unspecified_date=*/false, &time, &granularity));
  EXPECT_EQ(time, 1514761200000L /* Jan 01 2018 00:00:00 */);

  data.SetAbsoluteValue(DatetimeComponent::ComponentType::MONTH, 4);
  ASSERT_TRUE(calendarlib_->InterpretParseData(
      data,
      /*reference_time_ms_utc=*/0L, /*reference_timezone=*/"Europe/Zurich",
      /*reference_locale=*/"en-CH",
      /*prefer_future_for_unspecified_date=*/false, &time, &granularity));
  EXPECT_EQ(time, 1522533600000L /* Apr 01 2018 00:00:00 */);

  data.SetAbsoluteValue(DatetimeComponent::ComponentType::DAY_OF_MONTH, 25);
  ASSERT_TRUE(calendarlib_->InterpretParseData(
      data,
      /*reference_time_ms_utc=*/0L, /*reference_timezone=*/"Europe/Zurich",
      /*reference_locale=*/"en-CH",
      /*prefer_future_for_unspecified_date=*/false, &time, &granularity));
  EXPECT_EQ(time, 1524607200000L /* Apr 25 2018 00:00:00 */);

  data.SetAbsoluteValue(DatetimeComponent::ComponentType::HOUR, 9);
  ASSERT_TRUE(calendarlib_->InterpretParseData(
      data,
      /*reference_time_ms_utc=*/0L, /*reference_timezone=*/"Europe/Zurich",
      /*reference_locale=*/"en-CH",
      /*prefer_future_for_unspecified_date=*/false, &time, &granularity));
  EXPECT_EQ(time, 1524639600000L /* Apr 25 2018 09:00:00 */);

  data.SetAbsoluteValue(DatetimeComponent::ComponentType::MINUTE, 33);
  ASSERT_TRUE(calendarlib_->InterpretParseData(
      data,
      /*reference_time_ms_utc=*/0L, /*reference_timezone=*/"Europe/Zurich",
      /*reference_locale=*/"en-CH",
      /*prefer_future_for_unspecified_date=*/false, &time, &granularity));
  EXPECT_EQ(time, 1524641580000 /* Apr 25 2018 09:33:00 */);

  data.SetAbsoluteValue(DatetimeComponent::ComponentType::SECOND, 59);
  ASSERT_TRUE(calendarlib_->InterpretParseData(
      data,
      /*reference_time_ms_utc=*/0L, /*reference_timezone=*/"Europe/Zurich",
      /*reference_locale=*/"en-CH",
      /*prefer_future_for_unspecified_date=*/false, &time, &granularity));
  EXPECT_EQ(time, 1524641639000 /* Apr 25 2018 09:33:59 */);
}

TEST_F(CalendarTest, RoundingToGranularityWeek) {
  int64 time;
  DatetimeGranularity granularity;
  // Prepare data structure that means: "next week"
  DatetimeParsedData data;
  data.SetRelativeValue(DatetimeComponent::ComponentType::WEEK,
                        DatetimeComponent::RelativeQualifier::NEXT);
  data.SetRelativeCount(DatetimeComponent::ComponentType::WEEK, 1);

  ASSERT_TRUE(calendarlib_->InterpretParseData(
      data,
      /*reference_time_ms_utc=*/0L, /*reference_timezone=*/"Europe/Zurich",
      /*reference_locale=*/"de-CH",
      /*prefer_future_for_unspecified_date=*/false, &time, &granularity));
  EXPECT_EQ(time, 342000000L /* Mon Jan 05 1970 00:00:00 */);

  ASSERT_TRUE(calendarlib_->InterpretParseData(
      data,
      /*reference_time_ms_utc=*/0L, /*reference_timezone=*/"Europe/Zurich",
      /*reference_locale=*/"en-US",
      /*prefer_future_for_unspecified_date=*/false, &time, &granularity));
  EXPECT_EQ(time, 255600000L /* Sun Jan 04 1970 00:00:00 */);
}

TEST_F(CalendarTest, RelativeTime) {
  const int64 ref_time = 1524648839000L; /* 25 April 2018 09:33:59 */
  int64 time;
  DatetimeGranularity granularity;

  // Two Weds from now.
  DatetimeParsedData future_wed_parse;
  future_wed_parse.SetRelativeValue(
      DatetimeComponent::ComponentType::DAY_OF_WEEK,
      DatetimeComponent::RelativeQualifier::FUTURE);
  future_wed_parse.SetRelativeCount(
      DatetimeComponent::ComponentType::DAY_OF_WEEK, 2);
  future_wed_parse.SetAbsoluteValue(
      DatetimeComponent::ComponentType::DAY_OF_WEEK, kWednesday);

  ASSERT_TRUE(calendarlib_->InterpretParseData(
      future_wed_parse, ref_time, /*reference_timezone=*/"Europe/Zurich",
      /*reference_locale=*/"en-US",
      /*prefer_future_for_unspecified_date=*/false, &time, &granularity));
  EXPECT_EQ(time, 1525858439000L /* Wed May 09 2018 11:33:59 */);
  EXPECT_EQ(granularity, GRANULARITY_DAY);

  // Next Wed.
  DatetimeParsedData next_wed_parse;
  next_wed_parse.SetAbsoluteValue(DatetimeComponent::ComponentType::DAY_OF_WEEK,
                                  kWednesday);
  next_wed_parse.SetRelativeValue(DatetimeComponent::ComponentType::DAY_OF_WEEK,
                                  DatetimeComponent::RelativeQualifier::NEXT);
  next_wed_parse.SetRelativeCount(DatetimeComponent::ComponentType::DAY_OF_WEEK,
                                  1);

  ASSERT_TRUE(calendarlib_->InterpretParseData(
      next_wed_parse, ref_time, /*reference_timezone=*/"Europe/Zurich",
      /*reference_locale=*/"en-US",
      /*prefer_future_for_unspecified_date=*/false, &time, &granularity));
  EXPECT_EQ(time, 1525212000000L /* Wed May 02 2018 00:00:00 */);
  EXPECT_EQ(granularity, GRANULARITY_DAY);

  // Same Wed.
  DatetimeParsedData same_wed_parse;
  same_wed_parse.SetRelativeValue(DatetimeComponent::ComponentType::DAY_OF_WEEK,
                                  DatetimeComponent::RelativeQualifier::THIS);
  same_wed_parse.SetAbsoluteValue(DatetimeComponent::ComponentType::DAY_OF_WEEK,
                                  kWednesday);
  same_wed_parse.SetRelativeCount(DatetimeComponent::ComponentType::DAY_OF_WEEK,
                                  1);

  ASSERT_TRUE(calendarlib_->InterpretParseData(
      same_wed_parse, ref_time, /*reference_timezone=*/"Europe/Zurich",
      /*reference_locale=*/"en-US",
      /*prefer_future_for_unspecified_date=*/false, &time, &granularity));
  EXPECT_EQ(time, 1524607200000L /* Wed Apr 25 2018 00:00:00 */);
  EXPECT_EQ(granularity, GRANULARITY_DAY);

  // Previous Wed.
  DatetimeParsedData last_wed_parse;
  last_wed_parse.SetRelativeValue(DatetimeComponent::ComponentType::DAY_OF_WEEK,
                                  DatetimeComponent::RelativeQualifier::LAST);
  last_wed_parse.SetAbsoluteValue(DatetimeComponent::ComponentType::DAY_OF_WEEK,
                                  kWednesday);
  last_wed_parse.SetRelativeCount(DatetimeComponent::ComponentType::DAY_OF_WEEK,
                                  1);

  ASSERT_TRUE(calendarlib_->InterpretParseData(
      last_wed_parse, ref_time, /*reference_timezone=*/"Europe/Zurich",
      /*reference_locale=*/"en-US",
      /*prefer_future_for_unspecified_date=*/false, &time, &granularity));
  EXPECT_EQ(time, 1524002400000L /* Wed Apr 18 2018 00:00:00 */);
  EXPECT_EQ(granularity, GRANULARITY_DAY);

  // Two Weds ago.
  DatetimeParsedData past_wed_parse;
  past_wed_parse.SetRelativeValue(DatetimeComponent::ComponentType::DAY_OF_WEEK,
                                  DatetimeComponent::RelativeQualifier::PAST);
  past_wed_parse.SetAbsoluteValue(DatetimeComponent::ComponentType::DAY_OF_WEEK,
                                  kWednesday);
  past_wed_parse.SetRelativeCount(DatetimeComponent::ComponentType::DAY_OF_WEEK,
                                  -2);

  ASSERT_TRUE(calendarlib_->InterpretParseData(
      past_wed_parse, ref_time, /*reference_timezone=*/"Europe/Zurich",
      /*reference_locale=*/"en-US",
      /*prefer_future_for_unspecified_date=*/false, &time, &granularity));
  EXPECT_EQ(time, 1523439239000L /* Wed Apr 11 2018 11:33:59 */);
  EXPECT_EQ(granularity, GRANULARITY_DAY);

  // In 3 hours.
  DatetimeParsedData in_3_hours_parse;
  in_3_hours_parse.SetRelativeValue(
      DatetimeComponent::ComponentType::HOUR,
      DatetimeComponent::RelativeQualifier::FUTURE);
  in_3_hours_parse.SetRelativeCount(DatetimeComponent::ComponentType::HOUR, 3);

  ASSERT_TRUE(calendarlib_->InterpretParseData(
      in_3_hours_parse, ref_time, /*reference_timezone=*/"Europe/Zurich",
      /*reference_locale=*/"en-US",
      /*prefer_future_for_unspecified_date=*/false, &time, &granularity));
  EXPECT_EQ(time, 1524659639000L /* Wed Apr 25 2018 14:33:59 */);
  EXPECT_EQ(granularity, GRANULARITY_HOUR);

  // In 5 minutes.
  DatetimeParsedData in_5_minutes_parse;
  in_5_minutes_parse.SetRelativeValue(
      DatetimeComponent::ComponentType::MINUTE,
      DatetimeComponent::RelativeQualifier::FUTURE);
  in_5_minutes_parse.SetRelativeCount(DatetimeComponent::ComponentType::MINUTE,
                                      5);

  ASSERT_TRUE(calendarlib_->InterpretParseData(
      in_5_minutes_parse, ref_time, /*reference_timezone=*/"Europe/Zurich",
      /*reference_locale=*/"en-US",
      /*prefer_future_for_unspecified_date=*/false, &time, &granularity));
  EXPECT_EQ(time, 1524649139000L /* Wed Apr 25 2018 14:33:59 */);
  EXPECT_EQ(granularity, GRANULARITY_MINUTE);

  // In 10 seconds.
  DatetimeParsedData in_10_seconds_parse;
  in_10_seconds_parse.SetRelativeValue(
      DatetimeComponent::ComponentType::SECOND,
      DatetimeComponent::RelativeQualifier::FUTURE);
  in_10_seconds_parse.SetRelativeCount(DatetimeComponent::ComponentType::SECOND,
                                       10);

  ASSERT_TRUE(calendarlib_->InterpretParseData(
      in_10_seconds_parse, ref_time, /*reference_timezone=*/"Europe/Zurich",
      /*reference_locale=*/"en-US",
      /*prefer_future_for_unspecified_date=*/false, &time, &granularity));
  EXPECT_EQ(time, 1524648849000L /* Wed Apr 25 2018 14:33:59 */);
  EXPECT_EQ(granularity, GRANULARITY_SECOND);
}

TEST_F(CalendarTest, AddsADayWhenTimeInThePastAndDayNotSpecified) {
  int64 time;
  DatetimeGranularity granularity;
  DatetimeParsedData data;
  data.SetAbsoluteValue(DatetimeComponent::ComponentType::HOUR, 7);
  data.SetAbsoluteValue(DatetimeComponent::ComponentType::MINUTE, 10);

  ASSERT_TRUE(calendarlib_->InterpretParseData(
      data,
      /*reference_time_ms_utc=*/1567317600000L /* Sept 01 2019 00:00:00 */,
      /*reference_timezone=*/"Europe/Zurich",
      /*reference_locale=*/"en-CH", /*prefer_future_for_unspecified_date=*/true,
      &time, &granularity));
  EXPECT_EQ(time, 1567401000000L /* Sept 02 2019 07:10:00 */);
}

TEST_F(CalendarTest,
       DoesntAddADayWhenTimeInThePastAndDayNotSpecifiedAndDisabled) {
  int64 time;
  DatetimeGranularity granularity;
  DatetimeParsedData data;
  data.SetAbsoluteValue(DatetimeComponent::ComponentType::HOUR, 7);
  data.SetAbsoluteValue(DatetimeComponent::ComponentType::MINUTE, 10);

  ASSERT_TRUE(calendarlib_->InterpretParseData(
      data,
      /*reference_time_ms_utc=*/1567317600000L /* Sept 01 2019 00:00:00 */,
      /*reference_timezone=*/"Europe/Zurich",
      /*reference_locale=*/"en-CH",
      /*prefer_future_for_unspecified_date=*/false, &time, &granularity));
  EXPECT_EQ(time, 1567314600000L /* Sept 01 2019 07:10:00 */);
}

TEST_F(CalendarTest, DoesntAddADayWhenTimeInTheFutureAndDayNotSpecified) {
  int64 time;
  DatetimeGranularity granularity;
  DatetimeParsedData data;
  data.SetAbsoluteValue(DatetimeComponent::ComponentType::HOUR, 9);
  data.SetAbsoluteValue(DatetimeComponent::ComponentType::MINUTE, 10);

  ASSERT_TRUE(calendarlib_->InterpretParseData(
      data,
      /*reference_time_ms_utc=*/1567317600000L /* Sept 01 2019 00:00:00 */,
      /*reference_timezone=*/"Europe/Zurich",
      /*reference_locale=*/"en-CH", /*prefer_future_for_unspecified_date=*/true,
      &time, &granularity));
  EXPECT_EQ(time, 1567321800000L /* Sept 01 2019 09:10:00 */);

  ASSERT_TRUE(calendarlib_->InterpretParseData(
      data,
      /*reference_time_ms_utc=*/1567317600000L /* Sept 01 2019 00:00:00 */,
      /*reference_timezone=*/"Europe/Zurich",
      /*reference_locale=*/"en-CH",
      /*prefer_future_for_unspecified_date=*/false, &time, &granularity));
  EXPECT_EQ(time, 1567321800000L /* Sept 01 2019 09:10:00 */);
}

}  // namespace
}  // namespace libtextclassifier3
