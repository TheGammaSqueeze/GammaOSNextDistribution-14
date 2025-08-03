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

namespace bluetooth {
namespace common {

class ILoggable {
 public:
  // the interface for
  // converting an object to a string for feeding to loggers
  // e.g.. logcat
  virtual std::string ToStringForLogging() const = 0;
  virtual ~ILoggable() = default;
};

class IRedactableLoggable : public ILoggable {
 public:
  // the interface for
  // converting an object to a string with sensitive info redacted
  // to avoid violating privacy
  virtual std::string ToRedactedStringForLogging() const = 0;
  virtual ~IRedactableLoggable() = default;
};

}  // namespace common
}  // namespace bluetooth
