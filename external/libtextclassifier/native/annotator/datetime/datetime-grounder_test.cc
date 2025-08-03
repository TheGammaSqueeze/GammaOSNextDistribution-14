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

#include "annotator/datetime/datetime-grounder.h"

#include "annotator/datetime/datetime_generated.h"
#include "utils/flatbuffers/flatbuffers.h"
#include "utils/jvm-test-utils.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"

using ::libtextclassifier3::grammar::datetime::AbsoluteDateTimeT;
using ::libtextclassifier3::grammar::datetime::ComponentType;
using ::libtextclassifier3::grammar::datetime::Meridiem;
using ::libtextclassifier3::grammar::datetime::RelativeDatetimeComponentT;
using ::libtextclassifier3::grammar::datetime::RelativeDateTimeT;
using ::libtextclassifier3::grammar::datetime::TimeZoneT;
using ::libtextclassifier3::grammar::datetime::UngroundedDatetime;
using ::libtextclassifier3::grammar::datetime::UngroundedDatetimeT;
using ::libtextclassifier3::grammar::datetime::RelativeDatetimeComponent_::
    Modifier;
using ::testing::SizeIs;

namespace libtextclassifier3 {

class DatetimeGrounderTest : public testing::Test {
 public:
  void SetUp() override {
    calendarlib_ = CreateCalendarLibForTesting();
    datetime_grounder_.reset(new DatetimeGrounder(calendarlib_.get()));
  }

 protected:
  OwnedFlatbuffer<UngroundedDatetime, std::string> BuildAbsoluteDatetime(
      const int year, const int month, const int day, const int hour,
      const int minute, const int second, const Meridiem meridiem) {
    grammar::datetime::UngroundedDatetimeT ungrounded_datetime;
    ungrounded_datetime.absolute_datetime.reset(new AbsoluteDateTimeT);

    // Set absolute datetime value.
    ungrounded_datetime.absolute_datetime->year = year;
    ungrounded_datetime.absolute_datetime->month = month;
    ungrounded_datetime.absolute_datetime->day = day;
    ungrounded_datetime.absolute_datetime->hour = hour;
    ungrounded_datetime.absolute_datetime->minute = minute;
    ungrounded_datetime.absolute_datetime->second = second;
    ungrounded_datetime.absolute_datetime->meridiem = meridiem;

    return OwnedFlatbuffer<UngroundedDatetime, std::string>(
        PackFlatbuffer<UngroundedDatetime>(&ungrounded_datetime));
  }

  OwnedFlatbuffer<UngroundedDatetime, std::string> BuildRelativeDatetime(
      const ComponentType component_type, const Modifier modifier,
      const int relative_count) {
    UngroundedDatetimeT ungrounded_datetime;
    ungrounded_datetime.relative_datetime.reset(new RelativeDateTimeT);
    ungrounded_datetime.relative_datetime->relative_datetime_component
        .emplace_back(new RelativeDatetimeComponentT);
    ungrounded_datetime.relative_datetime->relative_datetime_component.back()
        ->modifier = modifier;
    ungrounded_datetime.relative_datetime->relative_datetime_component.back()
        ->component_type = component_type;
    ungrounded_datetime.relative_datetime->relative_datetime_component.back()
        ->value = relative_count;
    ungrounded_datetime.relative_datetime->base.reset(new AbsoluteDateTimeT);
    ungrounded_datetime.relative_datetime->base->year = 2020;
    ungrounded_datetime.relative_datetime->base->month = 6;
    ungrounded_datetime.relative_datetime->base->day = 30;

    return OwnedFlatbuffer<UngroundedDatetime, std::string>(
        PackFlatbuffer<UngroundedDatetime>(&ungrounded_datetime));
  }

  void VerifyValidUngroundedDatetime(
      const UngroundedDatetime* ungrounded_datetime) {
    EXPECT_TRUE(
        datetime_grounder_->IsValidUngroundedDatetime(ungrounded_datetime));
  }

  void VerifyInValidUngroundedDatetime(
      const UngroundedDatetime* ungrounded_datetime) {
    EXPECT_FALSE(
        datetime_grounder_->IsValidUngroundedDatetime(ungrounded_datetime));
  }

  std::unique_ptr<DatetimeGrounder> datetime_grounder_;
  std::unique_ptr<CalendarLib> calendarlib_;
};

TEST_F(DatetimeGrounderTest, AbsoluteDatetimeTest) {
  const OwnedFlatbuffer<UngroundedDatetime, std::string> datetime =
      BuildAbsoluteDatetime(/*year=*/2000, /*month=*/03, /*day=*/30,
                            /*hour=*/11, /*minute=*/59, /*second=*/59,
                            grammar::datetime::Meridiem_AM);
  const std::vector<DatetimeParseResult> data =
      datetime_grounder_
          ->Ground(
              /*reference_time_ms_utc=*/0, "Europe/Zurich", "en-US",
              datetime.get())
          .ValueOrDie();

  EXPECT_THAT(data, SizeIs(1));
  EXPECT_EQ(data[0].granularity, DatetimeGranularity::GRANULARITY_SECOND);

  // Meridiem
  EXPECT_EQ(data[0].datetime_components[0].component_type,
            DatetimeComponent::ComponentType::MERIDIEM);
  EXPECT_EQ(data[0].datetime_components[0].value, 0);

  EXPECT_EQ(data[0].datetime_components[1].component_type,
            DatetimeComponent::ComponentType::SECOND);
  EXPECT_EQ(data[0].datetime_components[1].component_type,
            DatetimeComponent::ComponentType::SECOND);

  EXPECT_EQ(data[0].datetime_components[2].component_type,
            DatetimeComponent::ComponentType::MINUTE);
  EXPECT_EQ(data[0].datetime_components[2].value, 59);

  EXPECT_EQ(data[0].datetime_components[3].component_type,
            DatetimeComponent::ComponentType::HOUR);
  EXPECT_EQ(data[0].datetime_components[3].value, 11);

  EXPECT_EQ(data[0].datetime_components[4].component_type,
            DatetimeComponent::ComponentType::DAY_OF_MONTH);
  EXPECT_EQ(data[0].datetime_components[4].value, 30);

  EXPECT_EQ(data[0].datetime_components[5].component_type,
            DatetimeComponent::ComponentType::MONTH);
  EXPECT_EQ(data[0].datetime_components[5].value, 3);

  EXPECT_EQ(data[0].datetime_components[6].component_type,
            DatetimeComponent::ComponentType::YEAR);
  EXPECT_EQ(data[0].datetime_components[6].value, 2000);
}

TEST_F(DatetimeGrounderTest, InterpretDatetimeTest) {
  const OwnedFlatbuffer<UngroundedDatetime, std::string> datetime =
      BuildAbsoluteDatetime(/*year=*/2000, /*month=*/03, /*day=*/30,
                            /*hour=*/11, /*minute=*/59, /*second=*/59,
                            grammar::datetime::Meridiem_UNKNOWN);
  const std::vector<DatetimeParseResult> data =
      datetime_grounder_
          ->Ground(
              /*reference_time_ms_utc=*/0, "Europe/Zurich", "en-US",
              datetime.get())
          .ValueOrDie();

  EXPECT_THAT(data, SizeIs(2));
  EXPECT_EQ(data[0].granularity, DatetimeGranularity::GRANULARITY_SECOND);
  EXPECT_EQ(data[1].granularity, DatetimeGranularity::GRANULARITY_SECOND);

  // Check Meridiem's values
  EXPECT_EQ(data[0].datetime_components[0].component_type,
            DatetimeComponent::ComponentType::MERIDIEM);
  EXPECT_EQ(data[0].datetime_components[0].value, 0);
  EXPECT_EQ(data[1].datetime_components[0].component_type,
            DatetimeComponent::ComponentType::MERIDIEM);
  EXPECT_EQ(data[1].datetime_components[0].value, 1);
}

TEST_F(DatetimeGrounderTest, RelativeDatetimeTest) {
  const OwnedFlatbuffer<UngroundedDatetime, std::string> datetime =
      BuildRelativeDatetime(ComponentType::ComponentType_DAY_OF_MONTH,
                            Modifier::Modifier_NEXT, 1);
  const std::vector<DatetimeParseResult> data =
      datetime_grounder_
          ->Ground(
              /*reference_time_ms_utc=*/0, "Europe/Zurich", "en-US",
              datetime.get())
          .ValueOrDie();

  EXPECT_THAT(data, SizeIs(1));
  EXPECT_EQ(data[0].granularity, DatetimeGranularity::GRANULARITY_DAY);

  EXPECT_EQ(data[0].datetime_components[0].component_type,
            DatetimeComponent::ComponentType::DAY_OF_MONTH);
  EXPECT_EQ(data[0].datetime_components[0].relative_qualifier,
            DatetimeComponent::RelativeQualifier::NEXT);
  EXPECT_EQ(data[0].datetime_components[0].relative_count, 1);
  EXPECT_EQ(data[0].datetime_components[1].component_type,
            DatetimeComponent::ComponentType::MONTH);
  EXPECT_EQ(data[0].datetime_components[2].component_type,
            DatetimeComponent::ComponentType::YEAR);
}

TEST_F(DatetimeGrounderTest, TimeZoneTest) {
  grammar::datetime::UngroundedDatetimeT ungrounded_datetime;
  ungrounded_datetime.absolute_datetime.reset(new AbsoluteDateTimeT);
  ungrounded_datetime.absolute_datetime->time_zone.reset(new TimeZoneT);
  ungrounded_datetime.absolute_datetime->time_zone->utc_offset_mins = 120;
  const OwnedFlatbuffer<UngroundedDatetime, std::string> timezone(
      PackFlatbuffer<UngroundedDatetime>(&ungrounded_datetime));

  const std::vector<DatetimeParseResult> data =
      datetime_grounder_
          ->Ground(
              /*reference_time_ms_utc=*/0, "Europe/Zurich", "en-US",
              timezone.get())
          .ValueOrDie();

  EXPECT_THAT(data, SizeIs(1));
  EXPECT_EQ(data[0].granularity, DatetimeGranularity::GRANULARITY_UNKNOWN);
  EXPECT_EQ(data[0].datetime_components[0].component_type,
            DatetimeComponent::ComponentType::ZONE_OFFSET);
  EXPECT_EQ(data[0].datetime_components[0].value, 120);
}

TEST_F(DatetimeGrounderTest, InValidUngroundedDatetime) {
  VerifyInValidUngroundedDatetime(
      BuildAbsoluteDatetime(/*year=*/2000, /*month=*/23, /*day=*/30,
                            /*hour=*/11, /*minute=*/59, /*second=*/59,
                            grammar::datetime::Meridiem_AM)
          .get());

  VerifyInValidUngroundedDatetime(
      BuildAbsoluteDatetime(/*year=*/2000, /*month=*/03, /*day=*/33,
                            /*hour=*/11, /*minute=*/59, /*second=*/59,
                            grammar::datetime::Meridiem_AM)
          .get());

  VerifyInValidUngroundedDatetime(
      BuildAbsoluteDatetime(/*year=*/2000, /*month=*/02, /*day=*/30,
                            /*hour=*/11, /*minute=*/59, /*second=*/59,
                            grammar::datetime::Meridiem_AM)
          .get());

  VerifyInValidUngroundedDatetime(
      BuildAbsoluteDatetime(/*year=*/2000, /*month=*/07, /*day=*/31,
                            /*hour=*/24, /*minute=*/59, /*second=*/59,
                            grammar::datetime::Meridiem_AM)
          .get());

  VerifyInValidUngroundedDatetime(
      BuildAbsoluteDatetime(/*year=*/2000, /*month=*/02, /*day=*/28,
                            /*hour=*/24, /*minute=*/59, /*second=*/59,
                            grammar::datetime::Meridiem_AM)
          .get());

  VerifyInValidUngroundedDatetime(
      BuildAbsoluteDatetime(/*year=*/2000, /*month=*/02, /*day=*/28,
                            /*hour=*/11, /*minute=*/69, /*second=*/59,
                            grammar::datetime::Meridiem_AM)
          .get());

  VerifyInValidUngroundedDatetime(
      BuildAbsoluteDatetime(/*year=*/2000, /*month=*/02, /*day=*/28,
                            /*hour=*/11, /*minute=*/59, /*second=*/99,
                            grammar::datetime::Meridiem_AM)
          .get());

  VerifyInValidUngroundedDatetime(
      BuildAbsoluteDatetime(/*year=*/2000, /*month=*/00, /*day=*/28,
                            /*hour=*/11, /*minute=*/59, /*second=*/99,
                            grammar::datetime::Meridiem_AM)
          .get());
}

TEST_F(DatetimeGrounderTest, ValidUngroundedDatetime) {
  VerifyValidUngroundedDatetime(
      BuildAbsoluteDatetime(/*year=*/2000, /*month=*/2, /*day=*/29,
                            /*hour=*/23, /*minute=*/59, /*second=*/59,
                            grammar::datetime::Meridiem_AM)
          .get());

  VerifyValidUngroundedDatetime(
      BuildAbsoluteDatetime(/*year=*/2000, /*month=*/7, /*day=*/31,
                            /*hour=*/23, /*minute=*/59, /*second=*/59,
                            grammar::datetime::Meridiem_AM)
          .get());

  VerifyValidUngroundedDatetime(
      BuildAbsoluteDatetime(/*year=*/2000, /*month=*/10, /*day=*/31,
                            /*hour=*/23, /*minute=*/59, /*second=*/59,
                            grammar::datetime::Meridiem_AM)
          .get());
}

}  // namespace libtextclassifier3
