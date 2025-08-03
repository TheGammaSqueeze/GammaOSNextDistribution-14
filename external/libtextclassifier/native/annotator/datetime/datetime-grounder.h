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

#ifndef LIBTEXTCLASSIFIER_ANNOTATOR_DATETIME_DATETIME_GROUNDER_H_
#define LIBTEXTCLASSIFIER_ANNOTATOR_DATETIME_DATETIME_GROUNDER_H_

#include <vector>

#include "annotator/datetime/datetime_generated.h"
#include "annotator/types.h"
#include "utils/base/statusor.h"
#include "utils/calendar/calendar.h"

namespace libtextclassifier3 {

// Utility class to resolve and complete an ungrounded datetime specification.
class DatetimeGrounder {
 public:
  explicit DatetimeGrounder(const CalendarLib* calendarlib);

  // Resolves ambiguities and produces concrete datetime results from an
  // ungrounded datetime specification.
  StatusOr<std::vector<DatetimeParseResult>> Ground(
      const int64 reference_time_ms_utc, const std::string& reference_timezone,
      const std::string& reference_locale,
      const grammar::datetime::UngroundedDatetime* ungrounded_datetime) const;

  bool IsValidUngroundedDatetime(
      const grammar::datetime::UngroundedDatetime* ungrounded_datetime) const;

 private:
  const CalendarLib& calendarlib_;
};

}  // namespace libtextclassifier3

#endif  // LIBTEXTCLASSIFIER_ANNOTATOR_DATETIME_DATETIME_GROUNDER_H_
