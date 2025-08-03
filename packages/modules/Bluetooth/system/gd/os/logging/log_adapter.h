/******************************************************************************
 *
 *  Copyright 2022 Google, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

#pragma once

#include <string>

#include "os/logging/log_redaction.h"

// as RawAddress does not implement IRedactableLoggable
// here we use a template function as a tricky workaround.
// in the future. we want to convert it into a function
// that takes a reference to IRedactableLoggable
template <typename Loggable>
std::string ToLoggableStr(const Loggable& loggable) {
  if (bluetooth::os::should_log_be_redacted()) {
    return loggable.ToRedactedStringForLogging();
  } else {
    return loggable.ToStringForLogging();
  }
}

#define ADDRESS_TO_LOGGABLE_STR(addr) ToLoggableStr(addr)
#define ADDRESS_TO_LOGGABLE_CSTR(addr) ADDRESS_TO_LOGGABLE_STR(addr).c_str()

#define PRIVATE_CELL(number)                            \
  (number                                               \
       .replace(                                        \
           0,                                           \
           (number.size() > 2) ? number.size() - 2 : 0, \
           (number.size() > 2) ? number.size() - 2 : 0, \
           '*')                                         \
       .c_str())

#define PRIVATE_NAME(name) (name)
