/*
 * Copyright 2020 The Android Open Source Project
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

#pragma once

#include <chrono>
#include <string>

namespace bluetooth {
namespace common {

typedef struct {
  std::chrono::system_clock::time_point timestamp;
  std::chrono::high_resolution_clock::time_point start_timestamp;
  std::chrono::high_resolution_clock::time_point end_timestamp;
  std::string message;
} StopWatchLog;

class StopWatch {
 public:
  static void DumpStopWatchLog(void);
  StopWatch(std::string text);
  ~StopWatch();

 private:
  std::string text_;
  std::chrono::system_clock::time_point timestamp_;
  std::chrono::high_resolution_clock::time_point start_timestamp_;
  void RecordLog(StopWatchLog log);
};

}  // namespace common
}  // namespace bluetooth
