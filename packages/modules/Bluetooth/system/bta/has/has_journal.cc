/*
 * Copyright 2021 HIMSA II K/S - www.himsa.com.
 * Represented by EHIMA - www.ehima.com
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

#include "has_journal.h"

#include "internal_include/bt_trace.h"

namespace le_audio {
namespace has {

std::ostream& operator<<(std::ostream& os, const HasJournalRecord& r) {
  os << "{";

  char eventtime[20];
  char temptime[20];
  struct tm* tstamp = localtime(&r.timestamp.tv_sec);
  strftime(temptime, sizeof(temptime), "%H:%M:%S", tstamp);
  snprintf(eventtime, sizeof(eventtime), "%s.%03ld", temptime,
           r.timestamp.tv_nsec / 1000000);
  os << "\"time\": \"" << eventtime << "\", ";

  if (r.is_operation) {
    os << std::get<HasCtpOp>(r.event);
    os << ", \"status\": \"" << loghex(r.op_status) << "\"";

  } else if (r.is_notification) {
    os << std::get<HasCtpNtf>(r.event) << ", ";

  } else if (r.is_active_preset_change) {
    os << "\"Active preset changed\": {\"active_preset_idx\": "
       << +std::get<uint8_t>(r.event) << "}";

  } else {
    os << "\"Features changed\": {\"features\": \""
       << loghex(std::get<uint8_t>(r.event)) << "\"}";
  }

  os << "}";
  return os;
}
}  // namespace has
}  // namespace le_audio
