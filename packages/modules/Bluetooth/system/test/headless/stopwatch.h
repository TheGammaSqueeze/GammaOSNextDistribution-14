/*
 * Copyright 2022 The Android Open Source Project
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

#include <base/strings/stringprintf.h>

#include <chrono>
#include <cstdint>
#include <string>

class Stopwatch {
 public:
  Stopwatch(std::string name)
      : name_(std::move(name)),
        start_(std::chrono::duration_cast<std::chrono::milliseconds>(
                   std::chrono::system_clock::now().time_since_epoch())
                   .count()) {}

  uint64_t LapMs() const {
    uint64_t now = std::chrono::duration_cast<std::chrono::milliseconds>(
                       std::chrono::system_clock::now().time_since_epoch())
                       .count();
    return now - start_;
  }

  std::string ToString() { return ToString(""); }

  std::string ToString(const std::string& comment) {
    return base::StringPrintf("%s: %lu ms %s", name_.c_str(),
                              static_cast<unsigned long>(LapMs()),
                              comment.c_str());
  }

 private:
  std::string name_;
  uint64_t start_;
};
