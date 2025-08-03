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

#include <algorithm>
#include <limits>
#include <unordered_map>
#include <vector>

#include "annotator/datetime/datetime_generated.h"
#include "annotator/datetime/utils.h"
#include "annotator/types.h"
#include "utils/base/integral_types.h"
#include "utils/base/status.h"
#include "utils/base/status_macros.h"

using ::libtextclassifier3::grammar::datetime::AbsoluteDateTime;
using ::libtextclassifier3::grammar::datetime::ComponentType;
using ::libtextclassifier3::grammar::datetime::Meridiem;
using ::libtextclassifier3::grammar::datetime::RelativeDateTime;
using ::libtextclassifier3::grammar::datetime::RelativeDatetimeComponent;
using ::libtextclassifier3::grammar::datetime::UngroundedDatetime;
using ::libtextclassifier3::grammar::datetime::RelativeDatetimeComponent_::
    Modifier;

namespace libtextclassifier3 {

namespace {

const std::unordered_map<int, int> kMonthDefaultLastDayMap(
    {{/*no_month*/ 0, 31},
     {/*January*/ 1, 31},
     {/*Febuary*/ 2, 29},
     {/*March*/ 3, 31},
     {/*April*/ 4, 30},
     {/*May*/ 5, 31},
     {/*June*/ 6, 30},
     {/*July*/ 7, 31},
     {/*August*/ 8, 31},
     {/*September*/ 9, 30},
     {/*October*/ 10, 31},
     {/*November*/ 11, 30},
     {/*December*/ 12, 31}});

bool IsValidDatetime(const AbsoluteDateTime* absolute_datetime) {
  // Sanity Checks.
  if (absolute_datetime->minute() > 59 || absolute_datetime->second() > 59 ||
      absolute_datetime->hour() > 23 || absolute_datetime->month() > 12 ||
      absolute_datetime->month() == 0) {
    return false;
  }
  if (absolute_datetime->day() >= 0) {
    int min_day_value = 1;
    int max_day_value = 31;
    if (absolute_datetime->month() >= 0 && absolute_datetime->month() <= 12) {
      max_day_value = kMonthDefaultLastDayMap.at(absolute_datetime->month());
      if (absolute_datetime->day() < min_day_value ||
          absolute_datetime->day() > max_day_value) {
        return false;
      }
    }
  }
  return true;
}

bool IsValidDatetime(const RelativeDateTime* relative_datetime) {
  if (relative_datetime->base()) {
    return IsValidDatetime(relative_datetime->base());
  }
  return true;
}

StatusOr<DatetimeComponent::RelativeQualifier> ToRelativeQualifier(
    const Modifier& modifier) {
  switch (modifier) {
    case Modifier::Modifier_THIS:
      return DatetimeComponent::RelativeQualifier::THIS;
    case Modifier::Modifier_LAST:
      return DatetimeComponent::RelativeQualifier::LAST;
    case Modifier::Modifier_NEXT:
      return DatetimeComponent::RelativeQualifier::NEXT;
    case Modifier::Modifier_NOW:
      return DatetimeComponent::RelativeQualifier::NOW;
    case Modifier::Modifier_TOMORROW:
      return DatetimeComponent::RelativeQualifier::TOMORROW;
    case Modifier::Modifier_YESTERDAY:
      return DatetimeComponent::RelativeQualifier::YESTERDAY;
    case Modifier::Modifier_PAST:
      return DatetimeComponent::RelativeQualifier::PAST;
    case Modifier::Modifier_FUTURE:
      return DatetimeComponent::RelativeQualifier::FUTURE;
    case Modifier::Modifier_UNSPECIFIED:
      return DatetimeComponent::RelativeQualifier::UNSPECIFIED;
    default:
      return Status(StatusCode::INTERNAL,
                    "Couldn't parse the Modifier to RelativeQualifier.");
  }
}

StatusOr<DatetimeComponent::ComponentType> ToComponentType(
    const grammar::datetime::ComponentType component_type) {
  switch (component_type) {
    case grammar::datetime::ComponentType_YEAR:
      return DatetimeComponent::ComponentType::YEAR;
    case grammar::datetime::ComponentType_MONTH:
      return DatetimeComponent::ComponentType::MONTH;
    case grammar::datetime::ComponentType_WEEK:
      return DatetimeComponent::ComponentType::WEEK;
    case grammar::datetime::ComponentType_DAY_OF_WEEK:
      return DatetimeComponent::ComponentType::DAY_OF_WEEK;
    case grammar::datetime::ComponentType_DAY_OF_MONTH:
      return DatetimeComponent::ComponentType::DAY_OF_MONTH;
    case grammar::datetime::ComponentType_HOUR:
      return DatetimeComponent::ComponentType::HOUR;
    case grammar::datetime::ComponentType_MINUTE:
      return DatetimeComponent::ComponentType::MINUTE;
    case grammar::datetime::ComponentType_SECOND:
      return DatetimeComponent::ComponentType::SECOND;
    case grammar::datetime::ComponentType_MERIDIEM:
      return DatetimeComponent::ComponentType::MERIDIEM;
    case grammar::datetime::ComponentType_UNSPECIFIED:
      return DatetimeComponent::ComponentType::UNSPECIFIED;
    default:
      return Status(StatusCode::INTERNAL,
                    "Couldn't parse the DatetimeComponent's ComponentType from "
                    "grammar's datetime ComponentType.");
  }
}

void FillAbsoluteDateTimeComponents(
    const grammar::datetime::AbsoluteDateTime* absolute_datetime,
    DatetimeParsedData* datetime_parsed_data) {
  if (absolute_datetime->year() >= 0) {
    datetime_parsed_data->SetAbsoluteValue(
        DatetimeComponent::ComponentType::YEAR,
        GetAdjustedYear(absolute_datetime->year()));
  }
  if (absolute_datetime->month() >= 0) {
    datetime_parsed_data->SetAbsoluteValue(
        DatetimeComponent::ComponentType::MONTH, absolute_datetime->month());
  }
  if (absolute_datetime->day() >= 0) {
    datetime_parsed_data->SetAbsoluteValue(
        DatetimeComponent::ComponentType::DAY_OF_MONTH,
        absolute_datetime->day());
  }
  if (absolute_datetime->week_day() >= 0) {
    datetime_parsed_data->SetAbsoluteValue(
        DatetimeComponent::ComponentType::DAY_OF_WEEK,
        absolute_datetime->week_day());
  }
  if (absolute_datetime->hour() >= 0) {
    datetime_parsed_data->SetAbsoluteValue(
        DatetimeComponent::ComponentType::HOUR, absolute_datetime->hour());
  }
  if (absolute_datetime->minute() >= 0) {
    datetime_parsed_data->SetAbsoluteValue(
        DatetimeComponent::ComponentType::MINUTE, absolute_datetime->minute());
  }
  if (absolute_datetime->second() >= 0) {
    datetime_parsed_data->SetAbsoluteValue(
        DatetimeComponent::ComponentType::SECOND, absolute_datetime->second());
  }
  if (absolute_datetime->meridiem() != grammar::datetime::Meridiem_UNKNOWN) {
    datetime_parsed_data->SetAbsoluteValue(
        DatetimeComponent::ComponentType::MERIDIEM,
        absolute_datetime->meridiem() == grammar::datetime::Meridiem_AM ? 0
                                                                        : 1);
  }
  if (absolute_datetime->time_zone()) {
    datetime_parsed_data->SetAbsoluteValue(
        DatetimeComponent::ComponentType::ZONE_OFFSET,
        absolute_datetime->time_zone()->utc_offset_mins());
  }
}

StatusOr<DatetimeParsedData> FillRelativeDateTimeComponents(
    const grammar::datetime::RelativeDateTime* relative_datetime) {
  DatetimeParsedData datetime_parsed_data;
  for (const RelativeDatetimeComponent* relative_component :
       *relative_datetime->relative_datetime_component()) {
    TC3_ASSIGN_OR_RETURN(const DatetimeComponent::ComponentType component_type,
                         ToComponentType(relative_component->component_type()));
    datetime_parsed_data.SetRelativeCount(component_type,
                                          relative_component->value());
    TC3_ASSIGN_OR_RETURN(
        const DatetimeComponent::RelativeQualifier relative_qualifier,
        ToRelativeQualifier(relative_component->modifier()));
    datetime_parsed_data.SetRelativeValue(component_type, relative_qualifier);
  }
  if (relative_datetime->base()) {
    FillAbsoluteDateTimeComponents(relative_datetime->base(),
                                   &datetime_parsed_data);
  }
  return datetime_parsed_data;
}

}  // namespace

DatetimeGrounder::DatetimeGrounder(const CalendarLib* calendarlib)
    : calendarlib_(*calendarlib) {}

StatusOr<std::vector<DatetimeParseResult>> DatetimeGrounder::Ground(
    const int64 reference_time_ms_utc, const std::string& reference_timezone,
    const std::string& reference_locale,
    const grammar::datetime::UngroundedDatetime* ungrounded_datetime) const {
  DatetimeParsedData datetime_parsed_data;
  if (ungrounded_datetime->absolute_datetime()) {
    FillAbsoluteDateTimeComponents(ungrounded_datetime->absolute_datetime(),
                                   &datetime_parsed_data);
  } else if (ungrounded_datetime->relative_datetime()) {
    TC3_ASSIGN_OR_RETURN(datetime_parsed_data,
                         FillRelativeDateTimeComponents(
                             ungrounded_datetime->relative_datetime()));
  }
  std::vector<DatetimeParsedData> interpretations;
  FillInterpretations(datetime_parsed_data,
                      calendarlib_.GetGranularity(datetime_parsed_data),
                      &interpretations);
  std::vector<DatetimeParseResult> datetime_parse_result;

  for (const DatetimeParsedData& interpretation : interpretations) {
    std::vector<DatetimeComponent> date_components;
    interpretation.GetDatetimeComponents(&date_components);
    DatetimeParseResult result;
    // Text classifier only provides ambiguity limited to “AM/PM” which is
    // encoded in the pair of DatetimeParseResult; both corresponding to the
    // same date, but one corresponding to “AM” and the other one corresponding
    //  to “PM”.
    if (!calendarlib_.InterpretParseData(
            interpretation, reference_time_ms_utc, reference_timezone,
            reference_locale, /*prefer_future_for_unspecified_date=*/true,
            &(result.time_ms_utc), &(result.granularity))) {
      return Status(
          StatusCode::INTERNAL,
          "Couldn't parse the UngroundedDatetime to DatetimeParseResult.");
    }

    // Sort the date time units by component type.
    std::stable_sort(date_components.begin(), date_components.end(),
                     [](DatetimeComponent a, DatetimeComponent b) {
                       return a.component_type > b.component_type;
                     });
    result.datetime_components.swap(date_components);
    datetime_parse_result.push_back(result);
  }
  return datetime_parse_result;
}

bool DatetimeGrounder::IsValidUngroundedDatetime(
    const UngroundedDatetime* ungrounded_datetime) const {
  if (ungrounded_datetime->absolute_datetime()) {
    return IsValidDatetime(ungrounded_datetime->absolute_datetime());
  } else if (ungrounded_datetime->relative_datetime()) {
    return IsValidDatetime(ungrounded_datetime->relative_datetime());
  }
  return false;
}

}  // namespace libtextclassifier3
